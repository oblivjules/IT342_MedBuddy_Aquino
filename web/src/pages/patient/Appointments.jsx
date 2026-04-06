import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Calendar, ChevronDown, ChevronUp, Clock, FileText, Plus, RefreshCw, XCircle } from 'lucide-react'
import DashboardLayout from '../../components/DashboardLayout'
// import FileOpenerModal from '../../components/FileOpenerModal'
import UserAvatar from '../../components/UserAvatar'
import { getMyAppointments, updateAppointmentStatus } from '../../api/appointmentApi'
// import { getFileAccessUrl, getFilesByAppointment } from '../../api/fileUploadApi'
// import { getMedicalRecordByAppointment } from '../../api/medicalRecordApi'
import { useToast } from '../../hooks/useToast'

const statusOrder = ['PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED']
const statusLabel = {
  PENDING: 'Pending',
  CONFIRMED: 'Confirmed',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
}
const statusColors = {
  PENDING: 'bg-accent-soft text-accent',
  CONFIRMED: 'bg-primary-soft text-primary',
  COMPLETED: 'bg-teal-soft text-teal',
  CANCELLED: 'bg-muted text-muted-foreground',
}
const statusDot = {
  PENDING: 'bg-accent',
  CONFIRMED: 'bg-primary',
  COMPLETED: 'bg-teal',
  CANCELLED: 'bg-muted-foreground',
}
const avatarColors = ['bg-primary', 'bg-teal', 'bg-accent', 'bg-muted-foreground', 'bg-primary']

function getDoctorName(doctor) {
  const fullName = [doctor?.firstName, doctor?.lastName].filter(Boolean).join(' ')
  return fullName || doctor?.email || 'Unknown Doctor'
}

function getDoctorInitials(name) {
  return (
    name
      .replace('Dr.', '')
      .trim()
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0])
      .join('')
      .toUpperCase() || 'DR'
  )
}

export default function PatientAppointments() {
  const navigate = useNavigate()
  const { success } = useToast()
  const [appointments, setAppointments] = useState([])
  const [expandedId, setExpandedId] = useState(null)
  const [detailsByAppointmentId, setDetailsByAppointmentId] = useState({})
  const [selectedFile, setSelectedFile] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    async function load() {
      try {
        const data = await getMyAppointments()
        const safeAppointments = Array.isArray(data) ? data : []
        setAppointments(safeAppointments)
      } catch {
        setError('Failed to load appointments.')
      } finally {
        setLoading(false)
      }
    }

    load()
  }, [])

  const grouped = useMemo(
    () => statusOrder
      .map((status) => ({ status, items: appointments.filter((appointment) => appointment.status === status) }))
      .filter((group) => group.items.length > 0),
    [appointments],
  )

  async function handleCancel(id) {
    setError('')
    try {
      await updateAppointmentStatus(id, 'CANCELLED')
      setAppointments((prev) => prev.map((appointment) => (appointment.id === id ? { ...appointment, status: 'CANCELLED' } : appointment)))
      success('Appointment cancelled successfully.')
    } catch {
      setError('Unable to cancel appointment. Please try again.')
    }
  }

  async function openAppointmentFile(file) {
    try {
      const accessUrl = await getFileAccessUrl(file.id)
      if (!accessUrl) throw new Error('Missing file URL')
      setSelectedFile({
        fileName: file.fileName,
        fileUrl: accessUrl,
        fileType: file.fileType,
        fileExtension: file.fileExtension,
      })
    } catch (err) {
      setError(err.response?.data?.detail || err.response?.data?.message || 'Unable to open file right now.')
    }
  }

  async function toggleCompletedDetails(appointmentId) {
    if (expandedId === appointmentId) {
      setExpandedId(null)
      return
    }

    setExpandedId(appointmentId)

    if (detailsByAppointmentId[appointmentId]) {
      return
    }

    try {
      const [record, files] = await Promise.all([
        getMedicalRecordByAppointment(appointmentId).catch(() => null),
        getFilesByAppointment(appointmentId).catch(() => []),
      ])

      setDetailsByAppointmentId((prev) => ({
        ...prev,
        [appointmentId]: {
          diagnosis: record?.diagnosis || '',
          prescriptionDetails: record?.prescriptionDetails || '',
          files: Array.isArray(files) ? files : [],
        },
      }))
    } catch {
      setDetailsByAppointmentId((prev) => ({
        ...prev,
        [appointmentId]: {
          diagnosis: '',
          prescriptionDetails: '',
          files: [],
        },
      }))
    }
  }

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold">My Appointments</h1>
            <p className="text-muted-foreground font-body">View and manage your appointments</p>
          </div>
          <Link
            to="/patient/find-doctor"
            className="inline-flex h-10 items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground shadow-md hover:bg-primary/90"
          >
            <Plus className="mr-2 h-4 w-4" />
            New Appointment
          </Link>
        </div>

        <div className="grid gap-3 sm:grid-cols-4">
          {statusOrder.map((status) => {
            const count = appointments.filter((appointment) => appointment.status === status).length
            return (
              <div key={status} className="rounded-xl border border-border bg-card p-4 shadow-card">
                <div className="flex items-center gap-2">
                  <span className={`h-2.5 w-2.5 rounded-full ${statusDot[status]}`} />
                  <span className="text-sm text-muted-foreground font-body">{statusLabel[status]}</span>
                </div>
                <p className="mt-1 text-2xl font-bold">{count}</p>
              </div>
            )
          })}
        </div>

        {loading && <p className="text-sm text-muted-foreground">Loading appointments...</p>}
        {error && <p className="text-sm text-destructive">{error}</p>}

        {!loading && grouped.length === 0 && (
          <p className="text-sm text-muted-foreground">No appointments found.</p>
        )}

        {grouped.map((group) => (
          <section key={group.status} className="space-y-3">
            <h2 className="flex items-center gap-2 text-lg font-semibold">
              <span className={`inline-block h-2.5 w-2.5 rounded-full ${statusDot[group.status]}`} />
              {statusLabel[group.status]}
              <span className="ml-1 rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                {group.items.length}
              </span>
            </h2>

            <div className="space-y-3">
              {group.items.map((appointment, index) => {
                const doctorName = getDoctorName(appointment.doctor)
                const doctorSpec = (appointment.doctor?.specializations || []).join(', ') || 'Not specified'
                const formattedDate = new Date(appointment.dateTime).toLocaleDateString()
                const formattedTime = new Date(appointment.dateTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })

                return (
                  <div
                    key={appointment.id}
                    className="flex flex-col gap-3 rounded-2xl border border-border bg-card p-5 shadow-card transition-all hover:shadow-elevated sm:flex-row sm:items-center sm:justify-between"
                  >
                    <div className="flex items-center gap-4">
                      <UserAvatar
                        imageUrl={appointment.doctor?.profileImageUrl}
                        name={doctorName}
                        fallback={getDoctorInitials(doctorName)}
                        className="h-11 w-11"
                        textClassName="text-sm"
                        toneClassName={`${avatarColors[index % avatarColors.length]} text-primary-foreground`}
                        alt={`Dr. ${doctorName}`}
                      />
                      <div>
                        <p className="font-medium">Dr. {doctorName}</p>
                        <p className="text-sm text-muted-foreground font-body">{doctorSpec}</p>
                      </div>
                    </div>

                    <div className="flex items-center gap-4 text-sm">
                      <span className="flex items-center gap-1 text-muted-foreground">
                        <Calendar className="h-3.5 w-3.5" /> {formattedDate}
                      </span>
                      <span className="flex items-center gap-1 text-muted-foreground">
                        <Clock className="h-3.5 w-3.5" /> {formattedTime}
                      </span>
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                      <span className={`rounded-full px-3 py-1 text-xs font-medium ${statusColors[appointment.status]}`}>
                        {statusLabel[appointment.status]}
                      </span>

                      {appointment.status === 'COMPLETED' && (
                        <button
                          type="button"
                          onClick={() => toggleCompletedDetails(appointment.id)}
                          className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-sm font-medium text-primary hover:bg-primary/10"
                        >
                          {expandedId === appointment.id ? (
                            <ChevronUp className="h-4 w-4" />
                          ) : (
                            <ChevronDown className="h-4 w-4" />
                          )}
                          {expandedId === appointment.id ? 'Hide Details' : 'View Details'}
                        </button>
                      )}

                      {(appointment.status === 'PENDING' || appointment.status === 'CONFIRMED') && (
                        <>
                          <button
                            type="button"
                            onClick={() => navigate('/patient/find-doctor')}
                            className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-sm font-medium text-muted-foreground hover:bg-muted"
                          >
                            <RefreshCw className="h-4 w-4" /> Reschedule
                          </button>
                          <button
                            type="button"
                            onClick={() => handleCancel(appointment.id)}
                            className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-sm font-medium text-destructive hover:bg-destructive/10"
                          >
                            <XCircle className="h-4 w-4" /> Cancel
                          </button>
                        </>
                      )}

                      {expandedId === appointment.id && appointment.status === 'COMPLETED' && (
                          <div className="mt-3 space-y-2 rounded-xl border border-border bg-muted/30 p-4">
                            <p className="text-sm">
                              <span className="font-semibold">Diagnosis:</span>{' '}
                              {detailsByAppointmentId[appointment.id]?.diagnosis || 'No diagnosis yet.'}
                            </p>
                            <p className="text-sm">
                              <span className="font-semibold">Prescription:</span>{' '}
                              {detailsByAppointmentId[appointment.id]?.prescriptionDetails || 'No prescription details.'}
                            </p>

                            {(detailsByAppointmentId[appointment.id]?.files || []).length > 0 && (
                              <div className="pt-1">
                                <p className="mb-2 text-xs font-medium text-muted-foreground">Attached files:</p>
                                <div className="flex flex-wrap gap-2">
                                  {(detailsByAppointmentId[appointment.id]?.files || []).map((file) => (
                                    <button
                                      key={file.id}
                                      type="button"
                                      onClick={() => openAppointmentFile(file)}
                                      className="inline-flex items-center gap-1.5 rounded-lg bg-primary-soft/50 px-3 py-1.5 text-xs font-medium"
                                    >
                                      <FileText className="h-3 w-3 text-primary" />
                                      {file.fileName}
                                    </button>
                                  ))}
                                </div>
                              </div>
                            )}
                          </div>
                        )}
                      )
                    </div>
                  </div>
                )
              })}

              {selectedFile && (
                <FileOpenerModal
                  file={selectedFile}
                  onClose={() => setSelectedFile(null)}
                />
              )}
            </div>
          </section>
        ))}
      </div>
    </DashboardLayout>
  )
}

