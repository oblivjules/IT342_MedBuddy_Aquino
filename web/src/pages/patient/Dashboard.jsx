import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Calendar as CalendarIcon,
  CheckCircle,
  ChevronLeft,
  ChevronRight,
  Clock,
  Download,
  FileText,
  Plus,
  X,
} from 'lucide-react'
import DashboardLayout from '../../components/DashboardLayout'
import FileOpenerModal from '../../components/FileOpenerModal'
import UserAvatar from '../../components/UserAvatar'
import { getMyAppointments } from '../../api/appointmentApi'
import { getFileAccessUrl, getFilesByAppointment } from '../../api/fileUploadApi'
import { getMedicalRecordByAppointment } from '../../api/medicalRecordApi'
import { useAuth } from '../../hooks/useAuth'
import { getDrugInfo } from '../../api/drugInfoApi'

const avatarColors = ['bg-primary', 'bg-teal', 'bg-accent']
const weekdayLabels = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa']

function toIsoDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function toMonthLabel(date) {
  return date.toLocaleDateString('en-US', { month: 'long', year: 'numeric' })
}

function toMonthStart(value) {
  const date = new Date(value)
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

function buildCalendarCells(visibleMonth) {
  const firstDayOfMonth = new Date(visibleMonth.getFullYear(), visibleMonth.getMonth(), 1)
  const monthStartsAt = firstDayOfMonth.getDay()
  const gridStart = new Date(firstDayOfMonth)
  gridStart.setDate(firstDayOfMonth.getDate() - monthStartsAt)

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(gridStart)
    date.setDate(gridStart.getDate() + index)
    return {
      iso: toIsoDate(date),
      label: date.getDate(),
      inCurrentMonth: date.getMonth() === visibleMonth.getMonth(),
    }
  })
}

function doctorName(doctor) {
  const fullName = [doctor?.firstName, doctor?.lastName].filter(Boolean).join(' ')
  return fullName || doctor?.email || 'Doctor'
}

function initials(name) {
  return (
    name
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((piece) => piece[0])
      .join('')
      .toUpperCase() || 'DR'
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

function inferType(record) {
  const diagnosis = String(record?.diagnosis || '').toLowerCase()
  const prescription = String(record?.prescriptionDetails || '').toLowerCase()
  const combined = `${diagnosis} ${prescription}`

  if (prescription.trim()) return 'Prescription'
  if (combined.includes('x-ray') || combined.includes('mri') || combined.includes('scan') || combined.includes('imaging')) return 'Imaging'
  if (combined.includes('blood') || combined.includes('lab') || combined.includes('test') || combined.includes('panel')) return 'Lab Results'
  return 'Consultation'
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

function hasDrugInfo(drugInfo) {
  return Boolean(
    drugInfo?.indicationsAndUsage
      || drugInfo?.warnings
      || drugInfo?.dosageAndAdministration
      || drugInfo?.description,
  )
}

export default function PatientDashboard() {
  const { user } = useAuth()
  const [appointments, setAppointments] = useState([])
  const [records, setRecords] = useState([])
  const [selectedRecord, setSelectedRecord] = useState(null)
  const [selectedFile, setSelectedFile] = useState(null)
  const [drugInfo, setDrugInfo] = useState(null)
  const [drugInfoLoading, setDrugInfoLoading] = useState(false)
  const [drugInfoError, setDrugInfoError] = useState('')
  const [selectedDate, setSelectedDate] = useState(new Date().toISOString().slice(0, 10))
  const [visibleMonth, setVisibleMonth] = useState(toMonthStart(new Date()))
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    getMyAppointments()
      .then(async (data) => {
        const list = Array.isArray(data) ? data : []
        setAppointments(list)

        const completed = list.filter((apt) => apt.status === 'COMPLETED').slice(0, 5)
        const recentRecords = []

        for (const appointment of completed) {
          try {
            const record = await getMedicalRecordByAppointment(appointment.id)
            const files = await getFilesByAppointment(appointment.id).catch(() => [])
            if (record) {
              recentRecords.push({
                ...record,
                type: inferType(record),
                title: buildTitle(record, inferType(record), recentRecords.length),
                appointmentDateTime: appointment.dateTime,
                doctor: appointment.doctor,
                files: Array.isArray(files) ? files : [],
                doctorName: doctorName(appointment.doctor),
                formattedDate: appointment.dateTime ? new Date(appointment.dateTime).toLocaleDateString() : 'N/A',
              })
            }
          } catch {
            // Ignore individual record failures so the dashboard can still render.
          }
        }

        setRecords(recentRecords)
      })
      .catch(() => setError('Failed to load dashboard data.'))
      .finally(() => setLoading(false))
  }, [])

  const stats = useMemo(() => {
    const now = new Date()
    const total = appointments.length
    const upcoming = appointments.filter(
      (apt) => ['PENDING', 'CONFIRMED'].includes(apt.status) && new Date(apt.dateTime) > now,
    ).length
    const completed = appointments.filter((apt) => apt.status === 'COMPLETED').length
    const pending = appointments.filter((apt) => apt.status === 'PENDING').length
    return [
      { label: 'Total Appointments', value: total, icon: CalendarIcon, tone: 'bg-primary-soft text-primary' },
      { label: 'Upcoming Visits', value: upcoming, icon: Clock, tone: 'bg-teal-soft text-teal' },
      { label: 'Completed Visits', value: completed, icon: CheckCircle, tone: 'bg-accent-soft text-accent' },
    ]
  }, [appointments])

  const upcoming = useMemo(
    () => appointments
      .filter((apt) => ['PENDING', 'CONFIRMED'].includes(apt.status))
      .sort((a, b) => new Date(a.dateTime) - new Date(b.dateTime))
      .slice(0, 5),
    [appointments],
  )

  const onSelectedDate = useMemo(
    () => upcoming.filter((apt) => apt.dateTime.slice(0, 10) === selectedDate),
    [upcoming, selectedDate],
  )

  const calendarCells = useMemo(
    () => buildCalendarCells(visibleMonth),
    [visibleMonth],
  )

  function moveVisibleMonth(direction) {
    setVisibleMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() + direction, 1))
  }

  function handlePickDate(iso) {
    setSelectedDate(iso)
    setVisibleMonth(toMonthStart(iso))
  }

  async function openRecordDetail(record) {
    setSelectedRecord(record)
    setDrugInfo(null)
    setDrugInfoError('')

    if (!record?.medicineName) {
      setDrugInfoLoading(false)
      return
    }

    setDrugInfoLoading(true)

    try {
      const response = await getDrugInfo(record.id)
      if (response?.available && hasDrugInfo(response.data)) {
        setDrugInfo(response.data)
      } else {
        setDrugInfo(null)
        setDrugInfoError('Drug information currently unavailable.')
      }
    } catch {
      setDrugInfo(null)
      setDrugInfoError('Drug information currently unavailable.')
    } finally {
      setDrugInfoLoading(false)
    }
  }

  function closeRecordDetail() {
    setSelectedRecord(null)
    setDrugInfo(null)
    setDrugInfoError('')
  }

  async function openRecordFile(file) {
    try {
      const accessUrl = file.fileUrl || await getFileAccessUrl(file.id)
      if (!accessUrl) throw new Error('Missing file URL')

      setSelectedFile({
        fileName: file.fileName,
        fileUrl: accessUrl,
        fileType: file.fileType,
        fileExtension: file.fileExtension,
      })
    } catch {
      setError('Unable to open file right now.')
    }
  }

  function handleDownload(record) {
    const blob = new Blob([buildMedicalRecordExportText(record)], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = buildMedicalRecordFilename(record)
    link.click()
    URL.revokeObjectURL(url)
  }

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold md:text-3xl">
              Good day, {user?.firstName || 'Patient'}
            </h1>
            <p className="mt-1 text-muted-foreground font-body">Here is your health overview</p>
          </div>
          <Link
            to="/patient/find-doctor"
            className="inline-flex h-10 items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground shadow-md hover:bg-primary/90"
          >
            <Plus className="mr-2 h-4 w-4" /> Book Appointment
          </Link>
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {stats.map((stat) => (
            <div
              key={stat.label}
              className="group rounded-2xl border border-border bg-card p-5 shadow-card transition-all hover:-translate-y-0.5 hover:shadow-elevated"
            >
              <div className={`mb-3 inline-flex rounded-xl p-3 ${stat.tone}`}>
                <stat.icon className="h-5 w-5" />
              </div>
              <p className="text-sm text-muted-foreground font-body">{stat.label}</p>
              <p className="mt-1 text-3xl font-bold">{stat.value}</p>
            </div>
          ))}
        </div>

        <div className="grid gap-6 lg:grid-cols-3">
          <div className="lg:col-span-2 rounded-2xl border border-border bg-card shadow-card">
            <div className="flex items-center justify-between border-b border-border p-5">
              <h2 className="text-lg font-semibold">Upcoming Appointments</h2>
              <Link to="/patient/appointments" className="text-sm font-medium text-primary hover:underline">
                View All
              </Link>
            </div>
            {loading ? (
              <p className="p-5 text-sm text-muted-foreground">Loading appointments...</p>
            ) : upcoming.length === 0 ? (
              <p className="p-5 text-sm text-muted-foreground">No upcoming appointments.</p>
            ) : (
              <div className="divide-y divide-border">
                {upcoming.map((apt, index) => {
                  const name = doctorName(apt.doctor)
                  const spec = apt.doctor?.specializations?.[0]
                  return (
                    <div key={apt.id} className="flex items-center justify-between p-5 transition-colors hover:bg-muted/30">
                      <div className="flex items-center gap-4">
                        <UserAvatar
                          imageUrl={apt.doctor?.profileImageUrl}
                          name={name}
                          fallback={initials(name)}
                          className="h-11 w-11"
                          textClassName="text-sm"
                          toneClassName={`${avatarColors[index % avatarColors.length]} text-primary-foreground`}
                          alt={`Dr. ${name}`}
                        />
                        <div>
                          <p className="font-semibold">Dr. {name}</p>
                          <p className="text-sm text-muted-foreground font-body">{spec || 'Not specified'}</p>
                        </div>
                      </div>
                      <div className="text-right text-sm">
                        <p className="font-medium">{new Date(apt.dateTime).toLocaleDateString()}</p>
                        <p className="text-muted-foreground">{new Date(apt.dateTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</p>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>

          <div className="rounded-2xl border border-border bg-card shadow-card">
            <div className="border-b border-border p-5">
              <h2 className="text-lg font-semibold">Calendar</h2>
            </div>
            <div className="p-5">
              <div className="mx-auto max-w-xs">
                <div className="mb-3 flex items-center justify-between">
                  <button
                    type="button"
                    onClick={() => moveVisibleMonth(-1)}
                    className="inline-flex h-9 w-9 items-center justify-center rounded-xl border border-border text-muted-foreground transition-colors hover:bg-muted"
                    aria-label="Previous month"
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </button>
                  <p className="text-2xl font-semibold tracking-tight">{toMonthLabel(visibleMonth)}</p>
                  <button
                    type="button"
                    onClick={() => moveVisibleMonth(1)}
                    className="inline-flex h-9 w-9 items-center justify-center rounded-xl border border-border text-muted-foreground transition-colors hover:bg-muted"
                    aria-label="Next month"
                  >
                    <ChevronRight className="h-4 w-4" />
                  </button>
                </div>

                <div className="grid grid-cols-7 gap-y-2 text-center text-sm text-muted-foreground">
                  {weekdayLabels.map((label) => (
                    <span key={label}>{label}</span>
                  ))}
                </div>

                <div className="mt-2 grid grid-cols-7 gap-y-2 text-center">
                  {calendarCells.map((cell) => {
                    const isSelected = selectedDate === cell.iso
                    return (
                      <button
                        key={cell.iso}
                        type="button"
                        onClick={() => handlePickDate(cell.iso)}
                        className={`mx-auto inline-flex h-10 w-10 items-center justify-center rounded-xl text-base transition-colors ${
                          isSelected
                            ? 'bg-primary text-primary-foreground'
                            : cell.inCurrentMonth
                              ? 'text-foreground hover:bg-muted'
                              : 'text-muted-foreground/40 hover:bg-muted/60'
                        }`}
                        aria-label={`Select ${cell.iso}`}
                      >
                        {cell.label}
                      </button>
                    )
                  })}
                </div>
              </div>

              <p className="mt-4 text-xs text-muted-foreground font-body">
                {onSelectedDate.length > 0 ? (
                  <Link to="/patient/appointments" className="text-primary font-medium hover:underline">
                    {onSelectedDate.length} upcoming appointment(s) on this date
                  </Link>
                ) : (
                  `${onSelectedDate.length} upcoming appointment(s) on this date`
                )}
              </p>

              {onSelectedDate.length > 0 && (
                <div className="mt-4 space-y-3 border-t border-border pt-4">
                  {onSelectedDate.map((apt) => {
                    const name = doctorName(apt.doctor)
                    return (
                      <div key={apt.id} className="rounded-lg border border-border/50 bg-muted/30 p-3 text-sm">
                        <p className="font-medium">Dr. {name}</p>
                        <p className="text-xs text-muted-foreground">{apt.doctor?.specializations?.[0]}</p>
                        <p className="mt-1 text-xs text-muted-foreground">
                          {new Date(apt.dateTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                        </p>
                        {apt.notes && <p className="mt-1 text-xs italic text-muted-foreground">{apt.notes}</p>}
                        <span className={`mt-2 inline-block text-xs font-medium px-2 py-1 rounded ${
                          apt.status === 'PENDING' ? 'bg-accent/20 text-accent' : 'bg-primary/20 text-primary'
                        }`}>{apt.status}</span>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>
          </div>
        </div>

        <div className="rounded-2xl border border-border bg-card shadow-card">
          <div className="flex items-center justify-between border-b border-border p-5">
            <h2 className="text-lg font-semibold">Recent Medical Records</h2>
            <Link to="/patient/medical-records" className="text-sm font-medium text-primary hover:underline">
              View All
            </Link>
          </div>
          {loading ? (
            <p className="p-5 text-sm text-muted-foreground">Loading records...</p>
          ) : records.length === 0 ? (
            <p className="p-5 text-sm text-muted-foreground">No records found.</p>
          ) : (
            <div className="divide-y divide-border">
              {records.map((record) => {
                const name = doctorName(record.doctor)
                return (
                  <button key={record.id} type="button" onClick={() => openRecordDetail(record)} className="flex w-full items-center justify-between p-5 text-left transition-colors hover:bg-muted/30">
                    <div className="flex items-center gap-3">
                      <div className="rounded-lg bg-primary-soft p-2.5">
                        <CalendarIcon className="h-4 w-4 text-primary" />
                      </div>
                      <div>
                        <p className="font-medium">Dr. {name}</p>
                        <p className="text-sm text-muted-foreground font-body">
                          {record.diagnosis || 'No diagnosis details'}
                        </p>
                      </div>
                    </div>
                    <ChevronRight className="h-4 w-4 text-muted-foreground" />
                  </button>
                )
              })}
            </div>
          )}
        </div>

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
                <div>
                  <h2 className="text-2xl font-bold">{selectedRecord.title}</h2>
                  <p className="text-sm text-muted-foreground">
                    {selectedRecord.type} • Dr. {selectedRecord.doctorName} • {selectedRecord.formattedDate}
                  </p>
                </div>

                <div className="rounded-lg border border-border bg-muted/30 p-4">
                  <h3 className="mb-2 font-semibold text-foreground">Diagnosis</h3>
                  <p className="text-sm text-foreground/80">{selectedRecord.diagnosis || 'No diagnosis recorded.'}</p>
                </div>

                <div className="rounded-lg border border-border bg-muted/30 p-4">
                  <h3 className="mb-2 font-semibold text-foreground">Prescription Details</h3>
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
                      <p className="mt-1 text-sm text-foreground/80">{selectedRecord.prescriptionNotes || buildPrescriptionSummary(selectedRecord) || 'N/A'}</p>
                    </div>
                  </div>
                </div>

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

                {Array.isArray(selectedRecord.files) && selectedRecord.files.length > 0 && (
                  <div>
                    <h3 className="mb-3 font-semibold text-foreground">Attached Files</h3>
                    <div className="space-y-2">
                      {selectedRecord.files.map((file) => (
                        <button
                          key={file.id}
                          type="button"
                          onClick={() => openRecordFile(file)}
                          className="flex w-full items-center justify-between rounded-lg border border-border bg-muted/30 p-3 transition-colors hover:bg-muted"
                        >
                          <span className="text-sm font-medium text-primary">{file.fileName}</span>
                          <FileText className="h-4 w-4 text-muted-foreground" />
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                <div className="flex gap-3 border-t border-border pt-4">
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

        {selectedFile && (
          <FileOpenerModal file={selectedFile} onClose={() => setSelectedFile(null)} />
        )}
      </div>
    </DashboardLayout>
  )
}

