import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ChevronRight,
  Download,
  FileText,
  Pill,
  Stethoscope,
  X,
} from 'lucide-react'
import DashboardLayout from '../../components/DashboardLayout'
import FileOpenerModal from '../../components/FileOpenerModal'
import { getMyAppointments } from '../../api/appointmentApi'
import { getMedicalRecordByAppointment } from '../../api/medicalRecordApi'
import { getFileAccessUrl, getFilesByAppointment, uploadAppointmentFile } from '../../api/fileUploadApi'
import { getMyUnifiedRecordFiles } from '../../api/recordFilesApi'
import { getDrugInfo } from '../../api/drugInfoApi'
import { getPaymentByAppointment } from '../../api/paymentApi'
import { useAuth } from '../../hooks/useAuth'


function formatDoctorName(doctor) {
  const fullName = [doctor?.firstName, doctor?.lastName].filter(Boolean).join(' ')
  return fullName || doctor?.email || 'Doctor'
}

function inferType(record) {
  const diagnosis = String(record?.diagnosis || '').toLowerCase()
  const prescription = String(record?.prescriptionDetails || '').toLowerCase()
  const combined = `${diagnosis} ${prescription}`

  if (prescription.trim()) return 'Prescription'
  if (combined.includes('x-ray') || combined.includes('mri') || combined.includes('scan') || combined.includes('imaging')) return 'Imaging'
  if (combined.includes('blood') || combined.includes('lab') || combined.includes('test') || combined.includes('panel')) return 'Lab Results'
  return 'Consultation'
}

function hasDrugInfo(drugInfo) {
  return Boolean(
    drugInfo?.indicationsAndUsage
      || drugInfo?.warnings
      || drugInfo?.dosageAndAdministration
      || drugInfo?.description,
  )
}

function buildPrescriptionSummary(record) {
  const parts = []

  if (record?.medicineName) parts.push(`Medicine: ${record.medicineName}`)
  if (record?.dosage) parts.push(`Dosage: ${record.dosage}`)
  if (record?.route) parts.push(`Route: ${record.route}`)
  if (record?.frequency) parts.push(`Frequency: ${record.frequency}`)
  if (record?.duration) parts.push(`Duration: ${record.duration}`)
  if (record?.prescriptionNotes) parts.push(`Notes: ${record.prescriptionNotes}`)

  return parts.join(' | ')
}

function buildTitle(record, type, index) {
  if (record?.diagnosis) return record.diagnosis
  return `${type} Record ${index + 1}`
}

function getExportTitleLabel(type, formattedDate) {
  const normalizedType = String(type || '').trim()
  const labels = {
    Prescription: 'Prescription Record',
    'Lab Results': 'Laboratory Result',
    'Medical Certificate': 'Medical Certificate',
    Consultation: 'Consultation Record',
  }

  const label = labels[normalizedType] || 'Medical Record'
  return formattedDate ? `${label} - ${formattedDate}` : label
}

function capitalizeFrequency(value) {
  const text = String(value || '').trim()
  if (!text) return 'N/A'
  return text.charAt(0).toUpperCase() + text.slice(1)
}

function buildMedicalRecordExportText(record) {
  const type = String(record?.type || '').trim()
  const lines = [
    'Medical Record',
    '==============',
    `Title: ${getExportTitleLabel(type, record?.formattedDate)}`,
    `Type: ${type || 'N/A'}`,
    `Doctor: ${record?.doctorName || 'N/A'}`,
    `Date: ${record?.formattedDate || 'N/A'}`,
    '',
    `Diagnosis: ${record?.diagnosis || 'N/A'}`,
    '',
  ]

  if (type === 'Prescription') {
    lines.push(
      'Prescription Details:',
      `  Medicine: ${record?.medicineName || 'N/A'}`,
      `  Dosage: ${record?.dosage || 'N/A'}`,
      `  Route: ${record?.route || 'N/A'}`,
      `  Frequency: ${capitalizeFrequency(record?.frequency)}`,
      `  Duration: ${record?.duration || 'N/A'}`,
      '',
    )
  }

  lines.push(`Notes: ${record?.prescriptionNotes || 'N/A'}`)
  return lines.join('\n')
}

function buildMedicalRecordFilename(record) {
  const type = String(record?.type || '').trim()
  const title = getExportTitleLabel(type)
  const datePart = String(record?.formattedDate || 'N/A').replaceAll('/', '-')
  return `${title} - ${datePart}.txt`
}

function getUnifiedFileId(file) {
  return file?.fileId ?? file?.id ?? null
}

function getUnifiedFileUploaderRole(file) {
  return String(file?.uploadedBy ?? file?.uploaderRole ?? file?.uploadedByRole ?? '').trim().toUpperCase()
}

function isPatientUpload(file) {
  return getUnifiedFileUploaderRole(file) === 'PATIENT'
}

function normalizeUnifiedFilesResponse(response) {
  return Array.isArray(response) ? response : (response?.data ?? response?.files ?? [])
}

export default function PatientMedicalRecords() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const hasLoadedFilesRef = useRef(false)
  const hasLoadedRecordsRef = useRef(false)
  const [records, setRecords] = useState([])
  const [unifiedFiles, setUnifiedFiles] = useState([])
  const [doctorFilter, setDoctorFilter] = useState('All')
  const [selectedFile, setSelectedFile] = useState(null)
  const [uploadingRecordId, setUploadingRecordId] = useState(null)
  const [filesLoading, setFilesLoading] = useState(true)
  const [recordsLoading, setRecordsLoading] = useState(true)
  const [error, setError] = useState('')
  const [pastUploadsError, setPastUploadsError] = useState('')
  const [selectedRecord, setSelectedRecord] = useState(null)
  const [drugInfo, setDrugInfo] = useState(null)
  const [drugInfoLoading, setDrugInfoLoading] = useState(false)
  const [drugInfoError, setDrugInfoError] = useState('')

  // Compute directly in render - bypasses any stale memo closure
  const patientUploads = unifiedFiles.filter((file) => {
    const role = String(file?.uploadedBy ?? '').trim().toUpperCase()
    console.log('[DEBUG RENDER] file:', file?.fileName, 'uploadedBy:', file?.uploadedBy, 'role:', role) // DEBUG
    return role === 'PATIENT'
  })
  console.log('[DEBUG RENDER] unifiedFiles.length:', unifiedFiles.length, 'patientUploads.length:', patientUploads.length, 'filesLoading:', filesLoading) // DEBUG

  useEffect(() => {
    const profileId = user?.profileId ?? user?.profile?.id ?? user?.patientProfileId ?? user?.doctorProfileId ?? null
    if (!profileId || hasLoadedFilesRef.current) {
      setFilesLoading(false)
      return
    }

    console.log('[DEBUG MedicalRecords] Profile ID detected, loading unified files:', profileId)
    hasLoadedFilesRef.current = true
    let mounted = true

    async function loadUnifiedFiles() {
      if (!mounted) return
      try {
        console.log('[DEBUG MedicalRecords] Starting to load unified files...')
        const mergedFiles = await getMyUnifiedRecordFiles()

        console.log('[DEBUG MedicalRecords] getMyUnifiedRecordFiles() raw response:', mergedFiles)
        const files = Array.isArray(mergedFiles) ? mergedFiles : (mergedFiles?.data ?? mergedFiles?.files ?? [])
        console.log('[DEBUG MedicalRecords] Files to set in state:', files)
        console.log('[DEBUG MedicalRecords] Files array details:', files)
        // DEBUG - log exact keys of first file to find uploader field name
        if (files.length > 0) {
          console.log('[DEBUG MedicalRecords] First file keys:', Object.keys(files[0]))
          console.log('[DEBUG MedicalRecords] First file full object:', JSON.stringify(files[0]))
        }

        setUnifiedFiles(Array.isArray(files) ? files : [])
        setPastUploadsError('')
      } catch (err) {
        console.error('[DEBUG MedicalRecords] unified files fetch error:', err)
        setPastUploadsError(err.response?.data?.detail || err.response?.data?.message || 'Failed to load uploaded files.')
        setUnifiedFiles([])
      } finally {
        setFilesLoading(false)
        setRecordsLoading(false)
      }
    }

    loadUnifiedFiles()
    return () => {
      mounted = false
    }
  }, [user?.profileId])

  useEffect(() => {
    const profileId = user?.profileId ?? user?.profile?.id ?? user?.patientProfileId ?? user?.doctorProfileId ?? null
    if (!profileId || hasLoadedRecordsRef.current) {
      setRecordsLoading(false)
      return
    }
    
    console.log('[DEBUG MedicalRecords] Profile ID detected, loading medical records:', profileId)
    hasLoadedRecordsRef.current = true
    let mounted = true

    async function loadMedicalRecords() {
      try {
        const appointments = await getMyAppointments()

        const completed = (Array.isArray(appointments) ? appointments : []).filter(
          (apt) => apt.status === 'COMPLETED',
        )

        const recordResults = await Promise.all(
          completed.map(async (apt) => {
            const payment = await getPaymentByAppointment(apt.id).catch(() => null)
            const paymentStatus = String(payment?.paymentStatus || '').toUpperCase()
            const isPaid = paymentStatus === 'PAID'
            const files = await getFilesByAppointment(apt.id).catch(() => [])

            try {
              const record = await getMedicalRecordByAppointment(apt.id)
              return {
                ...record,
                appointmentDateTime: apt.dateTime,
                doctor: apt.doctor,
                files: Array.isArray(files) ? files : [],
                isLocked: false,
                paymentStatus,
                appointmentId: apt.id,
              }
            } catch (err) {
              if (err?.response?.status === 403 && !isPaid) {
                return {
                  id: `locked-${apt.id}`,
                  appointmentId: apt.id,
                  diagnosis: '',
                  prescriptionDetails: '',
                  medicineName: '',
                  dosage: '',
                  route: '',
                  frequency: '',
                  duration: '',
                  prescriptionNotes: '',
                  appointmentDateTime: apt.dateTime,
                  doctor: apt.doctor,
                  files: Array.isArray(files) ? files : [],
                  isLocked: true,
                  paymentStatus,
                }
              }

              return null
            }
          }),
        )

        const normalized = recordResults
          .filter(Boolean)
          .sort((left, right) => new Date(right.appointmentDateTime || 0) - new Date(left.appointmentDateTime || 0))
          .map((record, index) => {
            const type = inferType(record)
            const lockedLabel = record.isLocked ? 'Locked Medical Record' : null
            return {
              ...record,
              type,
              title: lockedLabel || buildTitle(record, type, index),
              doctorName: formatDoctorName(record.doctor),
              formattedDate: record.appointmentDateTime
                ? new Date(record.appointmentDateTime).toLocaleDateString()
                : 'N/A',
            }
          })
        setRecords(normalized)
        setError('')
      } catch (err) {
        console.error('[DEBUG MedicalRecords] medical records fetch error:', err)
        setError(err.response?.data?.detail || err.response?.data?.message || 'Failed to load medical records.')
        setRecords([])
      } finally {
        setFilesLoading(false)
        setRecordsLoading(false)
      }
    }

    loadMedicalRecords()
    return () => {
      mounted = false
    }
  }, [user?.profileId])

  const uniqueDoctors = useMemo(
    () => {
      const doctorMap = {}
      records.forEach((record) => {
        if (record.doctor?.id) {
          const key = record.doctor.id
          if (!doctorMap[key]) {
            doctorMap[key] = formatDoctorName(record.doctor)
          }
        }
      })
      return Object.entries(doctorMap).map(([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name))
    },
    [records],
  )

  const filteredRecords = useMemo(
    () => {
      if (doctorFilter === 'All') return records
      return records.filter((record) => String(record.doctor?.id) === String(doctorFilter))
    },
    [records, doctorFilter],
  )


  function handleDownload(record) {
    const blob = new Blob([buildMedicalRecordExportText(record)], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = buildMedicalRecordFilename(record)
    link.click()
    URL.revokeObjectURL(url)
  }

  async function openAppointmentFile(file) {
    try {
      const fileId = getUnifiedFileId(file)
      if (!fileId) {
        throw new Error('Missing file identifier.')
      }

      if (file.locked) {
        setError('This file exists but is locked until payment is completed.')
        navigate('/patient/billing')
        return
      }

      const accessUrl = file.fileUrl || await getFileAccessUrl(fileId)
      if (!accessUrl) throw new Error('Missing file URL')
      setSelectedFile({
        fileName: file.fileName,
        fileUrl: accessUrl,
        fileType: file.fileType,
        fileExtension: file.fileExtension,
      })
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to open file right now.')
    }
  }

  async function handleRecordFileUpload(record, event) {
    const file = event.target.files?.[0]
    if (!file) return

    setError('')
    setUploadingRecordId(record.id)

    try {
      const uploaded = await uploadAppointmentFile(record.id, file)
      setRecords((prev) =>
        prev.map((entry) =>
          entry.id === record.id
            ? { ...entry, files: [...(entry.files || []), uploaded] }
            : entry,
        ),
      )
    } catch (err) {
      setError(err.response?.data?.detail || err.response?.data?.message || 'Failed to upload file to this appointment record.')
    } finally {
      setUploadingRecordId(null)
      event.target.value = ''
    }
  }

  async function openRecordDetail(record) {
    setSelectedRecord(record)
    setDrugInfo(null)
    setDrugInfoError('')

    if (record.isLocked) {
      setDrugInfoLoading(false)
      return
    }

    setDrugInfoLoading(true)

    try {
      if (!record.medicineName) {
        setDrugInfoError('Drug information currently unavailable.')
        setDrugInfoLoading(false)
        return
      }

      const response = await getDrugInfo(record.id)
      if (response.available && hasDrugInfo(response.data)) {
        setDrugInfo(response.data)
      } else {
        setDrugInfo(null)
        setDrugInfoError('Drug information currently unavailable.')
      }
    } catch (err) {
      setDrugInfoError('Unable to fetch drug information.')
      setDrugInfo(null)
    } finally {
      setDrugInfoLoading(false)
    }
  }

  function closeRecordDetail() {
    setSelectedRecord(null)
    setDrugInfo(null)
    setDrugInfoError('')
  }

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold">Medical Records</h1>
          <p className="text-muted-foreground font-body">Access and download your health documents</p>
        </div>

        <section className="rounded-2xl border border-border bg-card p-5 shadow-card">
          <div className="mb-4 flex items-center justify-between gap-3">
            <div>
              <h2 className="text-lg font-semibold">Your Past Uploads</h2>
              <p className="text-sm text-muted-foreground">Files you uploaded from appointment booking and records shared by your doctor.</p>
            </div>
            <span className="rounded-full bg-primary-soft px-3 py-1 text-xs font-semibold text-primary">
              {patientUploads.length} file{patientUploads.length === 1 ? '' : 's'}
            </span>
          </div>

          {filesLoading && <p className="text-sm text-muted-foreground">Loading past uploads...</p>}
          {pastUploadsError && <p className="rounded-md border border-destructive/20 bg-destructive/10 p-3 text-sm text-destructive">{pastUploadsError}</p>}
          {!filesLoading && !pastUploadsError && patientUploads.length === 0 && (
            <p className="text-sm text-muted-foreground">No uploads found yet.</p>
          )}

          {patientUploads.length > 0 && (
            <div className="overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead>
                  <tr className="border-b border-border text-left text-muted-foreground">
                    <th className="px-3 py-2 font-medium">File Name</th>
                    <th className="px-3 py-2 font-medium">Uploaded By</th>
                    <th className="px-3 py-2 font-medium">Uploaded At</th>
                  </tr>
                </thead>
                <tbody>
                  {patientUploads.map((file, index) => (
                    <tr key={`${file.fileName}-${file.uploadedAt}-${index}`} className="border-b border-border/60 last:border-b-0">
                      <td className="px-3 py-2">
                        <button
                          type="button"
                          onClick={() => openAppointmentFile(file)}
                          className={`text-left ${file.locked || !file.fileUrl ? 'text-muted-foreground' : 'text-primary hover:underline'}`}
                        >
                          {file.fileName}
                        </button>
                        {(file.locked || !file.fileUrl) && (
                          <div className="mt-1 text-xs text-muted-foreground">Locked until payment is completed</div>
                        )}
                      </td>
                      <td className="px-3 py-2">{file.uploadedBy}</td>
                      <td className="px-3 py-2">{file.uploadedAt ? new Date(file.uploadedAt).toLocaleString() : 'N/A'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <div className="grid gap-3 sm:grid-cols-4">
        </div>

        <div className="flex gap-3">
          <select
            value={doctorFilter}
            onChange={(event) => setDoctorFilter(event.target.value)}
            className="h-10 w-full rounded-md border border-input bg-card px-3 text-sm sm:w-56"
          >
            <option value="All">All Doctors</option>
            {uniqueDoctors.map((doc) => (
              <option key={doc.id} value={doc.id}>{doc.name}</option>
            ))}
          </select>
        </div>

        <section className="rounded-2xl border border-border bg-card shadow-card">
          {recordsLoading && <p className="p-5 text-sm text-muted-foreground">Loading records...</p>}
          {error && <p className="px-5 pb-4 text-sm text-destructive">{error}</p>}
          {!recordsLoading && !error && filteredRecords.length === 0 && (
            <p className="p-8 text-center text-muted-foreground font-body">No records found</p>
          )}

          <div className="divide-y divide-border">
            {filteredRecords.map((record) => {
              return (
                <article key={record.id} className="flex cursor-pointer items-center justify-between p-4 transition-colors hover:bg-muted/30 sm:p-5" onClick={() => openRecordDetail(record)}>
                  <div className="flex items-center gap-3">
                    <div className="rounded-xl p-2.5 bg-primary-soft">
                      <FileText className="h-4 w-4 text-primary" />
                    </div>
                    <div>
                      <p className="font-medium">{record.title}</p>
                      <p className="text-sm text-muted-foreground font-body">
                        {record.type} • Dr. {record.doctorName} • {record.formattedDate}
                      </p>
                      {record.isLocked && (
                        <div className="mt-2 inline-flex items-center rounded-full border border-accent/40 bg-accent-soft/40 px-3 py-1 text-xs font-medium text-accent">
                          Locked until payment is completed
                        </div>
                      )}
                      {Array.isArray(record.files) && record.files.length > 0 && (
                        <div className="mt-2 flex flex-wrap gap-2">
                          {record.files.map((file) => (
                            <button
                              key={file.id}
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation()
                                if (!record.isLocked) {
                                  openAppointmentFile(file)
                                }
                              }}
                              className={`inline-flex items-center rounded-full px-3 py-1 text-xs font-medium ${
                                record.isLocked
                                  ? 'cursor-not-allowed border border-border bg-muted text-muted-foreground'
                                  : 'border border-primary/20 bg-primary/5 text-primary hover:bg-primary/10'
                              }`}
                              title={record.isLocked ? 'File exists but is locked until payment is completed.' : 'Open file'}
                            >
                              {file.fileName}
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                  <ChevronRight className="h-5 w-5 text-muted-foreground" />
                </article>
              )
            })}
          </div>
        </section>

        {selectedFile && (
          <FileOpenerModal
            file={selectedFile}
            onClose={() => setSelectedFile(null)}
          />
        )}

        {selectedRecord && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
            <div className="relative max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-2xl border border-border bg-card p-6 shadow-2xl">
              <button
                type="button"
                onClick={closeRecordDetail}
                className="absolute right-4 top-4 rounded-md p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
                aria-label="Close"
              >
                <X className="h-5 w-5" />
              </button>

              <div className="space-y-6 pr-8">
                {/* Record Header */}
                <div>
                  <h2 className="text-2xl font-bold">{selectedRecord.title}</h2>
                  <p className="text-sm text-muted-foreground">
                    {selectedRecord.type} • Dr. {selectedRecord.doctorName} • {selectedRecord.formattedDate}
                  </p>
                </div>

                {selectedRecord.isLocked && (
                  <div className="rounded-lg border border-accent/30 bg-accent-soft/30 p-4">
                    <p className="text-sm text-muted-foreground">
                      This medical record exists but is currently locked until your appointment balance is fully paid.
                    </p>
                    <button
                      type="button"
                      onClick={() => navigate('/patient/billing')}
                      className="mt-3 inline-flex h-9 items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground hover:bg-primary/90"
                    >
                      Pay Now
                    </button>
                  </div>
                )}

                {/* Diagnosis Section */}
                <div className="rounded-lg border border-border bg-muted/30 p-4">
                  <h3 className="mb-2 font-semibold text-foreground">Diagnosis</h3>
                  <p className="text-sm text-foreground/80">{selectedRecord.isLocked ? 'Locked until payment is completed.' : (selectedRecord.diagnosis || 'N/A')}</p>
                </div>

                {/* Prescription Details Section */}
                <div className="rounded-lg border border-border bg-muted/30 p-4">
                  <h3 className="mb-2 font-semibold text-foreground">Prescription Details</h3>
                  {selectedRecord.isLocked ? (
                    <p className="text-sm text-foreground/80">Locked until payment is completed.</p>
                  ) : (
                    <div className="grid gap-3 sm:grid-cols-2">
                      <div>
                        <p className="text-xs font-medium text-muted-foreground">Medicine Name</p>
                        <p className="mt-1 text-sm text-foreground/80">{selectedRecord.medicineName || 'N/A'}</p>
                      </div>
                      <div>
                        <p className="text-xs font-medium text-muted-foreground">Dosage</p>
                        <p className="mt-1 text-sm text-foreground/80">{selectedRecord.dosage || 'N/A'}</p>
                      </div>
                      <div>
                        <p className="text-xs font-medium text-muted-foreground">Route</p>
                        <p className="mt-1 text-sm text-foreground/80">{selectedRecord.route || 'N/A'}</p>
                      </div>
                      <div>
                        <p className="text-xs font-medium text-muted-foreground">Frequency</p>
                        <p className="mt-1 text-sm text-foreground/80">{selectedRecord.frequency || 'N/A'}</p>
                      </div>
                      <div>
                        <p className="text-xs font-medium text-muted-foreground">Duration</p>
                        <p className="mt-1 text-sm text-foreground/80">{selectedRecord.duration || 'N/A'}</p>
                      </div>
                      <div className="sm:col-span-2">
                        <p className="text-xs font-medium text-muted-foreground">Notes</p>
                        <p className="mt-1 text-sm text-foreground/80">{selectedRecord.prescriptionNotes || 'N/A'}</p>
                      </div>
                    </div>
                  )}
                </div>

                {/* About Your Medication Section */}
                <div className="rounded-lg border border-border p-4">
                  <h3 className="mb-4 font-semibold text-foreground">About Your Medication</h3>
                  
                  {drugInfoLoading && (
                    <div className="space-y-3">
                      <div className="h-4 w-3/4 animate-pulse rounded bg-muted"></div>
                      <div className="h-4 w-full animate-pulse rounded bg-muted"></div>
                      <div className="h-4 w-2/3 animate-pulse rounded bg-muted"></div>
                    </div>
                  )}

                  {!drugInfoLoading && hasDrugInfo(drugInfo) && (
                    <div className="space-y-4">
                      {drugInfo.indicationsAndUsage && (
                        <div>
                          <h4 className="mb-1 text-xs font-semibold uppercase tracking-wider text-primary">Indications & Usage</h4>
                          <p className="text-sm text-foreground/80">{drugInfo.indicationsAndUsage}</p>
                        </div>
                      )}

                      {drugInfo.warnings && (
                        <div>
                          <h4 className="mb-1 text-xs font-semibold uppercase tracking-wider text-destructive">Warnings</h4>
                          <p className="text-sm text-foreground/80">{drugInfo.warnings}</p>
                        </div>
                      )}

                      {drugInfo.dosageAndAdministration && (
                        <div>
                          <h4 className="mb-1 text-xs font-semibold uppercase tracking-wider text-primary">Dosage & Administration</h4>
                          <p className="text-sm text-foreground/80">{drugInfo.dosageAndAdministration}</p>
                        </div>
                      )}

                      {drugInfo.description && (
                        <div>
                          <h4 className="mb-1 text-xs font-semibold uppercase tracking-wider text-primary">Description</h4>
                          <p className="text-sm text-foreground/80">{drugInfo.description}</p>
                        </div>
                      )}
                    </div>
                  )}

                  {!drugInfoLoading && !hasDrugInfo(drugInfo) && (
                    <p className="text-sm text-muted-foreground">{drugInfoError || 'Drug information currently unavailable.'}</p>
                  )}
                </div>

                {/* Files Section */}
                {Array.isArray(selectedRecord.files) && selectedRecord.files.length > 0 && (
                  <div>
                    <h3 className="mb-3 font-semibold text-foreground">Attached Files</h3>
                    <div className="space-y-2">
                      {selectedRecord.files.map((file) => (
                        <button
                          key={file.id}
                          type="button"
                          onClick={() => {
                            if (!selectedRecord.isLocked) {
                              openAppointmentFile(file)
                            }
                          }}
                          className={`flex w-full items-center justify-between rounded-lg border border-border p-3 transition-colors ${
                            selectedRecord.isLocked
                              ? 'cursor-not-allowed bg-muted/20'
                              : 'bg-muted/30 hover:bg-muted'
                          }`}
                          title={selectedRecord.isLocked ? 'File exists but is locked until payment is completed.' : 'Open file'}
                        >
                          <span className={`text-sm font-medium ${selectedRecord.isLocked ? 'text-muted-foreground' : 'text-primary'}`}>{file.fileName}</span>
                          <FileText className="h-4 w-4 text-muted-foreground" />
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                {/* Action Buttons */}
                <div className="flex gap-3 border-t border-border pt-4">
                  {!selectedRecord.isLocked && (
                    <button
                      type="button"
                      onClick={() => {
                        handleDownload(selectedRecord)
                        closeRecordDetail()
                      }}
                      className="flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
                    >
                      <Download className="h-4 w-4" />
                      Download
                    </button>
                  )}
                  {selectedRecord.isLocked && (
                    <button
                      type="button"
                      onClick={() => navigate('/patient/billing')}
                      className="flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
                    >
                      Pay Now
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={closeRecordDetail}
                    className="rounded-lg border border-border bg-muted/30 px-4 py-2 text-sm font-medium transition-colors hover:bg-muted"
                  >
                    Close
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </DashboardLayout>
  )
}

