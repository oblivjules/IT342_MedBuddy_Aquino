import { useEffect, useMemo, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import FileOpenerModal from '../../components/FileOpenerModal'
import { Search, User, Activity, Calendar, Hash, ChevronRight } from 'lucide-react'
import { getMyAppointments } from '../../api/appointmentApi'
import { getFileAccessUrl, getFilesByAppointment } from '../../api/fileUploadApi'
import { getUnifiedRecordFilesForPatient } from '../../api/recordFilesApi'
import {
  createMedicalRecord,
  getMedicalRecordByAppointment,
  updateMedicalRecord,
} from '../../api/medicalRecordApi'

const avatarColors = ['bg-primary', 'bg-teal', 'bg-accent', 'bg-primary', 'bg-teal', 'bg-accent']

function buildPrescriptionSummary(details) {
  const parts = []

  if (details.medicineName) parts.push(`Medicine: ${details.medicineName}`)
  if (details.dosage) parts.push(`Dosage: ${details.dosage}`)
  if (details.route) parts.push(`Route: ${details.route}`)
  if (details.frequency) parts.push(`Frequency: ${details.frequency}`)
  if (details.duration) parts.push(`Duration: ${details.duration}`)
  if (details.prescriptionNotes) parts.push(`Notes: ${details.prescriptionNotes}`)

  return parts.join(' | ')
}

export default function DoctorPatientRecords() {
  const [appointments, setAppointments] = useState([])
  const [recordsByAppointment, setRecordsByAppointment] = useState({})
  const [unifiedFilesByPatient, setUnifiedFilesByPatient] = useState({})
  const [selectedAppointmentId, setSelectedAppointmentId] = useState('')
  const [diagnosis, setDiagnosis] = useState('')
  const [medicineName, setMedicineName] = useState('')
  const [dosage, setDosage] = useState('')
  const [route, setRoute] = useState('')
  const [frequency, setFrequency] = useState('')
  const [duration, setDuration] = useState('')
  const [prescriptionNotes, setPrescriptionNotes] = useState('')
  const [selectedFile, setSelectedFile] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')

  useEffect(() => {
    let mounted = true

    async function loadData() {
      try {
        const appointmentData = await getMyAppointments()
        const completed = (Array.isArray(appointmentData) ? appointmentData : []).filter(
          (item) => item.status === 'COMPLETED',
        )
        if (!mounted) return
        setAppointments(completed)

        const patientIds = [...new Set(completed.map((item) => item.patient?.id).filter(Boolean))]
        const fetchedPatientFiles = await Promise.all(
          patientIds.map(async (patientId) => {
            try {
              const files = await getUnifiedRecordFilesForPatient(patientId)
              return [patientId, Array.isArray(files) ? files : []]
            } catch {
              return [patientId, []]
            }
          }),
        )

        const fetchedRecords = await Promise.all(
          completed.map(async (item) => {
            try {
              const record = await getMedicalRecordByAppointment(item.id)
              const files = await getFilesByAppointment(item.id).catch(() => [])
              return [item.id, { ...record, files: Array.isArray(files) ? files : [] }]
            } catch {
              return [item.id, null]
            }
          }),
        )

        if (mounted) {
          setUnifiedFilesByPatient(Object.fromEntries(fetchedPatientFiles))
          setRecordsByAppointment(Object.fromEntries(fetchedRecords.filter((entry) => entry[1])))
        }
      } catch {
        if (mounted) setError('Failed to load patient records.')
      } finally {
        if (mounted) setLoading(false)
      }
    }

    loadData()
    return () => {
      mounted = false
    }
  }, [])

  const selectedRecord = selectedAppointmentId ? recordsByAppointment[selectedAppointmentId] : null

  useEffect(() => {
    if (!selectedRecord) {
      setDiagnosis('')
      setMedicineName('')
      setDosage('')
      setRoute('')
      setFrequency('')
      setDuration('')
      setPrescriptionNotes('')
      return
    }
    setDiagnosis(selectedRecord.diagnosis || '')
    setMedicineName(selectedRecord.medicineName || '')
    setDosage(selectedRecord.dosage || '')
    setRoute(selectedRecord.route || '')
    setFrequency(selectedRecord.frequency || '')
    setDuration(selectedRecord.duration || '')
    setPrescriptionNotes(selectedRecord.prescriptionNotes || '')
  }, [selectedRecord])

  const appointmentsWithoutRecord = useMemo(
    () => appointments.filter((item) => !recordsByAppointment[item.id]),
    [appointments, recordsByAppointment],
  )

  const filteredAppointments = useMemo(() => {
    return appointments.filter((apt) => {
      const patientName = `${apt.patient?.firstName || ''} ${apt.patient?.lastName || ''}`.toLowerCase()
      return patientName.includes(search.toLowerCase()) || (apt.patient?.email || '').toLowerCase().includes(search.toLowerCase())
    })
  }, [appointments, search])

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    if (!selectedAppointmentId) {
      setError('Please choose an appointment.')
      return
    }

    const payload = {
      appointmentId: Number(selectedAppointmentId),
      diagnosis,
      prescriptionDetails: buildPrescriptionSummary({
        medicineName,
        dosage,
        route,
        frequency,
        duration,
        prescriptionNotes,
      }),
      medicineName,
      dosage,
      route,
      frequency,
      duration,
      prescriptionNotes,
    }

    try {
      if (selectedRecord?.id) {
        const updated = await updateMedicalRecord(selectedRecord.id, payload)
        setRecordsByAppointment((prev) => ({
          ...prev,
          [selectedAppointmentId]: {
            ...updated,
            files: prev[selectedAppointmentId]?.files || [],
          },
        }))
      } else {
        const created = await createMedicalRecord(payload)
        setRecordsByAppointment((prev) => ({
          ...prev,
          [selectedAppointmentId]: {
            ...created,
            files: prev[selectedAppointmentId]?.files || [],
          },
        }))
      }
    } catch {
      setError('Unable to save medical record.')
    }
  }

  async function openAppointmentFile(file) {
    try {
      if (file.locked) {
        setError('This file exists but is locked until payment is completed.')
        return
      }

      const accessUrl = file.fileUrl || await getFileAccessUrl(file.id)
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

  const selectedAppointment = appointments.find((a) => String(a.id) === String(selectedAppointmentId))
  const selectedPatient = selectedAppointment?.patient
  const selectedPatientFiles = selectedPatient?.id ? (unifiedFilesByPatient[selectedPatient.id] || []) : []

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold">Patient Records</h1>
          <p className="text-muted-foreground font-body">Browse and manage patient information</p>
        </div>

        {/* Summary */}
        <div className="grid gap-3 sm:grid-cols-3">
          <div className="rounded-xl border border-border bg-card p-4 shadow-card">
            <div className="mb-2 inline-flex rounded-lg p-2 bg-primary-soft">
              <User className="h-4 w-4 text-primary" />
            </div>
            <p className="text-sm text-muted-foreground font-body">Total Patients</p>
            <p className="text-xl font-bold">{appointments.length}</p>
          </div>
          <div className="rounded-xl border border-border bg-card p-4 shadow-card">
            <div className="mb-2 inline-flex rounded-lg p-2 bg-teal-soft">
              <Hash className="h-4 w-4 text-teal" />
            </div>
            <p className="text-sm text-muted-foreground font-body">Total Visits</p>
            <p className="text-xl font-bold">{appointments.length}</p>
          </div>
          <div className="rounded-xl border border-border bg-card p-4 shadow-card">
            <div className="mb-2 inline-flex rounded-lg p-2 bg-accent-soft">
              <Activity className="h-4 w-4 text-accent" />
            </div>
            <p className="text-sm text-muted-foreground font-body">Records Created</p>
            <p className="text-xl font-bold">{Object.keys(recordsByAppointment).length}</p>
          </div>
        </div>

        <div className="grid gap-6 lg:grid-cols-5">
          {/* Patient list */}
          <div className="lg:col-span-2 space-y-3">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <input
                placeholder="Search patients..."
                className="h-10 w-full rounded-md border border-input bg-card pl-9 pr-3 text-sm"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
            <div className="rounded-2xl border border-border bg-card shadow-card divide-y divide-border max-h-[600px] overflow-y-auto">
              {loading ? (
                <div className="p-4 text-sm text-muted-foreground">Loading...</div>
              ) : filteredAppointments.length === 0 ? (
                <div className="p-4 text-sm text-muted-foreground">No patients found.</div>
              ) : (
                filteredAppointments.map((apt, i) => {
                  const patientName = [apt.patient?.firstName, apt.patient?.lastName].filter(Boolean).join(' ') || 'Patient'
                  return (
                    <button
                      key={apt.id}
                      onClick={() => setSelectedAppointmentId(String(apt.id))}
                      className={`flex w-full items-center justify-between p-4 text-left transition-colors hover:bg-muted/50 ${
                        String(selectedAppointmentId) === String(apt.id) ? 'bg-primary-soft' : ''
                      }`}
                    >
                      <div className="flex items-center gap-3">
                        <div className={`flex h-10 w-10 items-center justify-center rounded-full text-sm font-bold text-primary-foreground ${avatarColors[i % avatarColors.length]}`}>
                          {patientName.split(' ').map((n) => n[0]).join('')}
                        </div>
                        <div>
                          <p className="font-medium">{patientName}</p>
                          <p className="text-xs text-muted-foreground">{new Date(apt.dateTime).toLocaleDateString()}</p>
                        </div>
                      </div>
                      <ChevronRight className="h-4 w-4 text-muted-foreground" />
                    </button>
                  )
                })
              )}
            </div>
          </div>

          {/* Patient detail and record editor */}
          <div className="lg:col-span-3">
            {selectedPatient ? (
              <div className="space-y-4">
                <div className="rounded-2xl border border-border bg-card p-6 shadow-card">
                  <div className="flex items-center gap-4">
                    <div className="flex h-16 w-16 items-center justify-center rounded-full bg-primary text-xl font-bold text-primary-foreground">
                      {(selectedPatient.firstName?.[0] || '') + (selectedPatient.lastName?.[0] || '')}
                    </div>
                    <div>
                      <h2 className="text-xl font-bold">{[selectedPatient.firstName, selectedPatient.lastName].filter(Boolean).join(' ') || 'Patient'}</h2>
                      <p className="text-sm text-muted-foreground font-body">{selectedPatient.email}</p>
                      {selectedPatientFiles.length > 0 && (
                        <p className="mt-1 text-xs text-muted-foreground">{selectedPatientFiles.length} uploaded file(s) available</p>
                      )}
                    </div>
                  </div>
                </div>

                {selectedPatientFiles.length > 0 && (
                  <div className="rounded-2xl border border-border bg-card shadow-card p-5">
                    <h3 className="mb-3 text-sm font-semibold">Patient Files</h3>
                    <div className="overflow-x-auto">
                      <table className="min-w-full text-sm">
                        <thead>
                          <tr className="border-b border-border text-left text-muted-foreground">
                            <th className="px-3 py-2 font-medium">File Name</th>
                            <th className="px-3 py-2 font-medium">Uploaded At</th>
                          </tr>
                        </thead>
                        <tbody>
                          {selectedPatientFiles.map((file, index) => (
                            <tr key={`${file.fileName}-${file.uploadedAt}-${index}`} className="border-b border-border/60">
                              <td className="px-3 py-2">
                                <button
                                  type="button"
                                  onClick={() => openAppointmentFile(file)}
                                  className="text-primary hover:underline"
                                >
                                  {file.fileName}
                                </button>
                              </td>
                              <td className="px-3 py-2">{file.uploadedAt ? new Date(file.uploadedAt).toLocaleString() : 'N/A'}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}

                <form onSubmit={handleSubmit} className="rounded-2xl border border-border bg-card shadow-card p-5 space-y-4">
                  <h3 className="text-lg font-semibold">Medical Record</h3>
                  
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Diagnosis *</label>
                    <textarea
                      value={diagnosis}
                      onChange={(e) => setDiagnosis(e.target.value)}
                      placeholder="Enter diagnosis..."
                      rows={4}
                      maxLength={2000}
                      required
                      className="w-full rounded-md border border-input bg-muted/50 px-3 py-2 text-sm resize-none"
                    />
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-medium">Prescription Details</label>
                    <div className="grid gap-3 md:grid-cols-2">
                      <input
                        value={medicineName}
                        onChange={(e) => setMedicineName(e.target.value)}
                        placeholder="Medicine Name"
                        maxLength={255}
                        className="h-10 rounded-md border border-input bg-muted/50 px-3 text-sm"
                      />
                      <input
                        value={dosage}
                        onChange={(e) => setDosage(e.target.value)}
                        placeholder="Dosage"
                        maxLength={255}
                        className="h-10 rounded-md border border-input bg-muted/50 px-3 text-sm"
                      />
                      <input
                        value={route}
                        onChange={(e) => setRoute(e.target.value)}
                        placeholder="Route"
                        maxLength={255}
                        className="h-10 rounded-md border border-input bg-muted/50 px-3 text-sm"
                      />
                      <input
                        value={frequency}
                        onChange={(e) => setFrequency(e.target.value)}
                        placeholder="Frequency"
                        maxLength={255}
                        className="h-10 rounded-md border border-input bg-muted/50 px-3 text-sm"
                      />
                      <input
                        value={duration}
                        onChange={(e) => setDuration(e.target.value)}
                        placeholder="Duration"
                        maxLength={255}
                        className="h-10 rounded-md border border-input bg-muted/50 px-3 text-sm"
                      />
                      <textarea
                        value={prescriptionNotes}
                        onChange={(e) => setPrescriptionNotes(e.target.value)}
                        placeholder="Notes (optional)"
                        rows={2}
                        maxLength={500}
                        className="rounded-md border border-input bg-muted/50 px-3 py-2 text-sm resize-none md:col-span-2"
                      />
                    </div>
                  </div>

                  {error && <div className="fixed top-4 left-4 right-4 z-[100] max-w-md rounded-md border border-destructive/20 bg-destructive/10 p-3 text-sm text-destructive shadow-lg"><p>{error}</p></div>}

                  <button
                    type="submit"
                    className="inline-flex h-10 items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground hover:bg-primary/90 w-full"
                  >
                    {selectedRecord?.id ? 'Update Record' : 'Create Record'}
                  </button>
                </form>

                {selectedRecord && (
                  <div className="rounded-2xl border border-border bg-card shadow-card p-5">
                    <h3 className="text-lg font-semibold mb-4">Current Record</h3>
                    <div className="space-y-3">
                      <div>
                        <p className="text-xs font-medium text-muted-foreground">DIAGNOSIS</p>
                        <p className="mt-1 text-sm">{selectedRecord.diagnosis}</p>
                      </div>
                      <div>
                        <p className="text-xs font-medium text-muted-foreground">PRESCRIPTION</p>
                        <div className="mt-2 grid gap-3 sm:grid-cols-2">
                          <div>
                            <p className="text-xs font-medium text-muted-foreground">Medicine Name</p>
                            <p className="mt-1 text-sm">{selectedRecord.medicineName || 'N/A'}</p>
                          </div>
                          <div>
                            <p className="text-xs font-medium text-muted-foreground">Dosage</p>
                            <p className="mt-1 text-sm">{selectedRecord.dosage || 'N/A'}</p>
                          </div>
                          <div>
                            <p className="text-xs font-medium text-muted-foreground">Route</p>
                            <p className="mt-1 text-sm">{selectedRecord.route || 'N/A'}</p>
                          </div>
                          <div>
                            <p className="text-xs font-medium text-muted-foreground">Frequency</p>
                            <p className="mt-1 text-sm">{selectedRecord.frequency || 'N/A'}</p>
                          </div>
                          <div>
                            <p className="text-xs font-medium text-muted-foreground">Duration</p>
                            <p className="mt-1 text-sm">{selectedRecord.duration || 'N/A'}</p>
                          </div>
                          <div className="sm:col-span-2">
                            <p className="text-xs font-medium text-muted-foreground">Notes</p>
                            <p className="mt-1 text-sm">{selectedRecord.prescriptionNotes || 'N/A'}</p>
                          </div>
                        </div>
                      </div>
                      {Array.isArray(selectedRecord.files) && selectedRecord.files.length > 0 && (
                        <div>
                          <p className="text-xs font-medium text-muted-foreground">ATTACHED FILES</p>
                          <div className="mt-2 flex flex-wrap gap-2">
                            {selectedRecord.files.map((file) => (
                              <button
                                key={file.id}
                                type="button"
                                onClick={() => openAppointmentFile(file)}
                                className="inline-flex items-center rounded-full border border-primary/20 bg-primary/5 px-3 py-1 text-xs font-medium text-primary hover:bg-primary/10"
                              >
                                {file.fileName}
                              </button>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                )}

                {selectedFile && (
                  <FileOpenerModal
                    file={selectedFile}
                    onClose={() => setSelectedFile(null)}
                  />
                )}
              </div>
            ) : (
              <div className="flex h-96 items-center justify-center rounded-2xl border border-border bg-card shadow-card">
                <div className="text-center text-muted-foreground">
                  <User className="mx-auto h-12 w-12 mb-3 text-muted-foreground/40" />
                  <p className="font-medium">Select a patient</p>
                  <p className="text-sm font-body">Click on a patient to view and manage their records</p>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </DashboardLayout>
  )
}

