import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Calendar, ChevronLeft, ChevronRight, Clock, Eye, FileText, Plus, Search, X, XCircle } from 'lucide-react'
import DashboardLayout from '../../components/DashboardLayout'
import FileOpenerModal from '../medicalrecords/FileOpenerModal'
import UserAvatar from '../user/UserAvatar'
import { getMyAppointments, updateAppointmentStatus } from './appointmentApi'
import { getFileAccessUrl, getFilesByAppointment } from '../medicalrecords/fileUploadApi'
import { getMedicalRecordByAppointment } from '../medicalrecords/medicalRecordApi'
import { getPaymentByAppointment, createPayment } from '../payment/paymentApi'
import { getDrugInfo } from '../prescription/drugInfoApi'
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

function summarizeDrugText(text) {
  return String(text || '').trim()
}

function hasDrugInfo(drugInfo) {
  return Boolean(
    drugInfo?.indicationsAndUsage
      || drugInfo?.warnings
      || drugInfo?.dosageAndAdministration
      || drugInfo?.description,
  )
}

function inferRecordType(record) {
  const diagnosis = String(record?.diagnosis || '').toLowerCase()
  const prescription = String(record?.prescriptionDetails || '').toLowerCase()
  const combined = `${diagnosis} ${prescription}`

  if (prescription.trim()) return 'Prescription'
  if (combined.includes('x-ray') || combined.includes('mri') || combined.includes('scan') || combined.includes('imaging')) return 'Imaging'
  if (combined.includes('blood') || combined.includes('lab') || combined.includes('test') || combined.includes('panel')) return 'Lab Results'
  return 'Consultation'
}

export default function PatientAppointments() {
  const navigate = useNavigate()
  const { success } = useToast()
  const [appointments, setAppointments] = useState([])
  const [detailsModalId, setDetailsModalId] = useState(null)
  const [detailsByAppointmentId, setDetailsByAppointmentId] = useState({})
  const [detailsLoadingByAppointmentId, setDetailsLoadingByAppointmentId] = useState({})
  const [selectedFile, setSelectedFile] = useState(null)
  const [cancelConfirmationId, setCancelConfirmationId] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [search, setSearch] = useState('')
  const [doctorFilter, setDoctorFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [currentPages, setCurrentPages] = useState({})
  const ITEMS_PER_PAGE = 5

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

  const uniqueDoctors = useMemo(() => {
    const doctors = appointments.map(a => {
      const name = getDoctorName(a.doctor)
      return { id: a.doctor?.id, name }
    })
    return Array.from(new Map(doctors.map(d => [d.id, d])).values()).sort((a, b) => a.name.localeCompare(b.name))
  }, [appointments])

  const filtered = useMemo(() => {
    return appointments.filter(apt => {
      const docName = getDoctorName(apt.doctor).toLowerCase()
      const searchLower = search.toLowerCase()
      const matchesSearch = docName.includes(searchLower) || (apt.doctor?.email || '').toLowerCase().includes(searchLower)
      const matchesDoctor = !doctorFilter || apt.doctor?.id === parseInt(doctorFilter)
      const matchesStatus = statusFilter === 'ALL' || apt.status === statusFilter
      return matchesSearch && matchesDoctor && matchesStatus
    })
  }, [appointments, search, doctorFilter, statusFilter])

  const grouped = useMemo(
    () => statusOrder
      .map((status) => ({
        status,
        items: filtered
          .filter((appointment) => appointment.status === status)
          .slice()
          .sort((left, right) => new Date(right.dateTime) - new Date(left.dateTime)),
      }))
      .filter((group) => group.items.length > 0),
    [filtered],
  )

  const paginatedGrouped = useMemo(() => {
    const result = {}
    grouped.forEach((group) => {
      const page = currentPages[group.status] || 0
      const start = page * ITEMS_PER_PAGE
      const end = start + ITEMS_PER_PAGE
      result[group.status] = {
        items: group.items.slice(start, end),
        totalPages: Math.ceil(group.items.length / ITEMS_PER_PAGE),
        currentPage: page,
        totalItems: group.items.length,
      }
    })
    return result
  }, [grouped, currentPages])

  function goToPage(status, page) {
    setCurrentPages(prev => ({ ...prev, [status]: page }))
  }

  async function handleCancel(id) {
    setError('')
    try {
      await updateAppointmentStatus(id, 'CANCELLED')
      setAppointments((prev) => prev.map((appointment) => (appointment.id === id ? { ...appointment, status: 'CANCELLED' } : appointment)))
      success('Appointment cancelled successfully.')
      setCancelConfirmationId(null)
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

  async function viewAppointmentFile(file) {
    try {
      const accessUrl = await getFileAccessUrl(file.id)
      if (!accessUrl) throw new Error('Missing file URL')
      window.open(accessUrl, '_blank', 'noopener,noreferrer')
    } catch (err) {
      setError(err.response?.data?.detail || err.response?.data?.message || 'Unable to open file right now.')
    }
  }

  async function openDetailsModal(appointmentId) {
    setDetailsModalId(appointmentId)
    const appointment = appointments.find((apt) => apt.id === appointmentId)
    setDetailsLoadingByAppointmentId((prev) => ({ ...prev, [appointmentId]: true }))

    try {
      const [fetchedRecord, files] = await Promise.all([
        getMedicalRecordByAppointment(appointmentId).catch(() => null),
        getFilesByAppointment(appointmentId).catch(() => []),
      ])
      const record = fetchedRecord
      let drugInfo = null
      let drugInfoError = ''
      let accessNote = ''
      let isLocked = false
      let paymentStatus = ''

      if (appointment?.status === 'COMPLETED' || record?.id) {
        const payment = await getPaymentByAppointment(appointmentId).catch(() => null)
        paymentStatus = String(payment?.paymentStatus || '').toUpperCase()
        const isPaid = paymentStatus === 'PAID'

        try {
          if (record?.id && record?.medicineName) {
            try {
              const response = await getDrugInfo(record.id)
              if (response?.available && response?.data) {
                drugInfo = response.data
              }
            } catch {
              drugInfoError = 'Drug information currently unavailable.'
            }
          } else {
            drugInfoError = 'Drug information currently unavailable.'
          }
        } catch (err) {
          if (err?.response?.status === 403 && !isPaid) {
            isLocked = true
            accessNote = 'Medical record exists but is currently locked until your appointment balance is fully paid.'
          }
        }
      }

      setDetailsByAppointmentId((prev) => ({
        ...prev,
        [appointmentId]: {
          type: inferRecordType(record),
          diagnosis: record?.diagnosis || '',
          prescriptionDetails: record?.prescriptionDetails || '',
          medicineName: record?.medicineName || '',
          dosage: record?.dosage || '',
          route: record?.route || '',
          frequency: record?.frequency || '',
          duration: record?.duration || '',
          prescriptionNotes: record?.prescriptionNotes || '',
          files: Array.isArray(files) ? files : [],
          drugInfo,
          drugInfoError,
          accessNote,
          isLocked,
          paymentStatus,
        },
      }))
    } catch {
      setDetailsByAppointmentId((prev) => ({
        ...prev,
        [appointmentId]: {
          type: 'Consultation',
          diagnosis: '',
          prescriptionDetails: '',
          medicineName: '',
          dosage: '',
          route: '',
          frequency: '',
          duration: '',
          prescriptionNotes: '',
          files: [],
          drugInfo: null,
          drugInfoError: 'Drug information currently unavailable.',
          accessNote: '',
          isLocked: false,
          paymentStatus: '',
        },
      }))
    } finally {
      setDetailsLoadingByAppointmentId((prev) => ({ ...prev, [appointmentId]: false }))
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
            const count = filtered.filter((appointment) => appointment.status === status).length
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

        {/* Filters */}
        <div className="mb-6 space-y-3">
          <div className="flex flex-col gap-3 sm:flex-row">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <input
                placeholder="Search doctor name or email..."
                className="h-10 w-full rounded-md border border-input bg-card pl-9 pr-3 text-sm"
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value)
                  setCurrentPages({})
                }}
              />
            </div>
            <select
              value={doctorFilter}
              onChange={(e) => {
                setDoctorFilter(e.target.value)
                setCurrentPages({})
              }}
              className="h-10 rounded-md border border-input bg-card px-3 text-sm sm:w-40"
            >
              <option value="">All Doctors</option>
              {uniqueDoctors.map(doc => (
                <option key={doc.id} value={doc.id}>{doc.name}</option>
              ))}
            </select>
            <select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value)
                setCurrentPages({})
              }}
              className="h-10 rounded-md border border-input bg-card px-3 text-sm sm:w-40"
            >
              <option value="ALL">All Status</option>
              <option value="PENDING">Pending</option>
              <option value="CONFIRMED">Confirmed</option>
              <option value="COMPLETED">Completed</option>
              <option value="CANCELLED">Cancelled</option>
            </select>
          </div>
        </div>

        {!loading && appointments.length === 0 && (
          <p className="text-sm text-muted-foreground">No appointments yet — book one now.</p>
        )}
        {!loading && appointments.length > 0 && filtered.length === 0 && (
          <p className="text-sm text-muted-foreground">No appointments match your filters.</p>
        )}

        {Object.entries(paginatedGrouped).map(([status, data]) =>
          data.totalItems > 0 ? (
          <section key={status} className="space-y-3">
            <h2 className="flex items-center gap-2 text-lg font-semibold">
              <span className={`inline-block h-2.5 w-2.5 rounded-full ${statusDot[status]}`} />
              {statusLabel[status]}
              <span className="ml-1 rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                {data.totalItems}
              </span>
            </h2>

            <div className="space-y-3">
              {data.items.map((appointment, index) => {
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

                      <button
                        type="button"
                        onClick={() => openDetailsModal(appointment.id)}
                        className="inline-flex items-center gap-1 rounded-md border border-input bg-background px-3 py-1.5 text-sm font-semibold text-foreground transition-colors hover:bg-muted"
                        title="View appointment details"
                      >
                        View Details
                      </button>

                      {(appointment.status === 'PENDING' || appointment.status === 'CONFIRMED') && (
                        <button
                          type="button"
                          onClick={() => setCancelConfirmationId(appointment.id)}
                          className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-sm font-medium text-destructive hover:bg-destructive/10"
                        >
                          <XCircle className="h-4 w-4" /> Cancel
                        </button>
                      )}
                    </div>


                  </div>
                )
              })}
              {data.totalPages > 1 && (
                <div className="mt-4 flex items-center justify-center gap-2">
                  <button
                    onClick={() => goToPage(status, data.currentPage - 1)}
                    disabled={data.currentPage === 0}
                    className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-input bg-card disabled:opacity-50 disabled:cursor-not-allowed hover:bg-muted"
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </button>
                  <span className="text-xs text-muted-foreground">
                    Page {data.currentPage + 1} of {data.totalPages}
                  </span>
                  <button
                    onClick={() => goToPage(status, data.currentPage + 1)}
                    disabled={data.currentPage >= data.totalPages - 1}
                    className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-input bg-card disabled:opacity-50 disabled:cursor-not-allowed hover:bg-muted"
                  >
                    <ChevronRight className="h-4 w-4" />
                  </button>
                </div>
              )}

              {selectedFile && (
                <FileOpenerModal
                  file={selectedFile}
                  onClose={() => setSelectedFile(null)}
                />
              )}
            </div>
          </section>
        ) : null
        )}

        {detailsModalId && (() => {
          const appointment = appointments.find((apt) => apt.id === detailsModalId)
          if (!appointment) return null
          const doctorName = getDoctorName(appointment.doctor)
          const formattedDate = new Date(appointment.dateTime).toLocaleDateString()
          const formattedTime = new Date(appointment.dateTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
          const modalDetails = detailsByAppointmentId[detailsModalId] || {}
          const detailsLoading = Boolean(detailsLoadingByAppointmentId[detailsModalId])
          const modalFiles = modalDetails.files || []

          return (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4 py-8 backdrop-blur-sm">
              <div className="w-full max-w-2xl overflow-hidden rounded-2xl border border-border bg-card shadow-2xl">
                <div className="flex items-center justify-between border-b border-border px-5 py-4">
                  <div className="min-w-0">
                    <h3 className="truncate text-lg font-semibold">Appointment Details</h3>
                    <p className="text-sm text-muted-foreground">Dr. {doctorName} • {formattedDate} {formattedTime}</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setDetailsModalId(null)}
                    className="rounded-full p-2 text-muted-foreground hover:bg-muted"
                    aria-label="Close details"
                  >
                    <X className="h-5 w-5" />
                  </button>
                </div>

                <div className="max-h-[70vh] overflow-auto p-5">
                  {detailsLoading ? (
                    <div className="flex min-h-[320px] items-center justify-center">
                      <div className="flex flex-col items-center gap-3">
                        <div className="h-10 w-10 animate-spin rounded-full border-4 border-primary/20 border-t-primary" />
                        <p className="text-sm text-muted-foreground">Loading appointment details...</p>
                      </div>
                    </div>
                  ) : (
                    <div className="space-y-4">
                      <div className="grid gap-3 sm:grid-cols-3">
                        <div className="rounded-xl border border-border/50 bg-muted/50 px-4 py-3">
                          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Type</p>
                          <p className="mt-1 text-sm text-foreground">{modalDetails.type || 'Consultation'}</p>
                        </div>
                        <div className="rounded-xl border border-border/50 bg-muted/50 px-4 py-3">
                          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Doctor</p>
                          <p className="mt-1 text-sm text-foreground">Dr. {doctorName}</p>
                        </div>
                        <div className="rounded-xl border border-border/50 bg-muted/50 px-4 py-3">
                          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Date</p>
                          <p className="mt-1 text-sm text-foreground">{formattedDate} {formattedTime}</p>
                        </div>
                      </div>

                      {appointment.notes && (
                        <div className="rounded-xl border border-border/50 bg-muted/50 px-4 py-3">
                          <p className="text-sm font-semibold text-foreground">Patient Notes</p>
                          <p className="mt-1 text-sm text-muted-foreground">{appointment.notes}</p>
                        </div>
                      )}

                      {modalDetails.isLocked && (
                        <div className="rounded-lg border border-accent/30 bg-accent-soft/30 p-4">
                          <p className="text-sm text-muted-foreground">
                            This medical record exists but is currently locked until your appointment balance is fully paid.
                          </p>
                          <button
                            type="button"
                            onClick={async () => {
                              try {
                                const resp = await createPayment({ appointmentId: appointment.id })
                                const checkoutUrl = resp?.checkoutUrl || resp?.checkout_url
                                if (checkoutUrl) {
                                  window.location.href = checkoutUrl
                                  return
                                }

                                // No checkout session returned — redirect to billing
                                navigate('/patient/billing')
                              } catch (err) {
                                console.error(err)

                                // If backend responded with 400, redirect to billing instead
                                if (err?.response?.status === 400) {
                                  navigate('/patient/billing')
                                  return
                                }

                                setError(err.response?.data?.message || err.message || 'Unable to start payment. Please try again.')
                              }
                            }}
                            className="mt-3 inline-flex h-9 items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground hover:bg-primary/90"
                          >
                            Pay Now
                          </button>
                        </div>
                      )}

                      {appointment.status === 'COMPLETED' && (
                        <>
                          <div className="rounded-xl border border-border/50 bg-muted/50 px-4 py-3">
                            <p className="text-sm font-semibold text-foreground">Diagnosis</p>
                            <p className="mt-1 text-sm text-muted-foreground">
                              {modalDetails.isLocked ? 'Locked until payment is completed.' : (modalDetails.diagnosis || 'No diagnosis recorded.')}
                            </p>
                          </div>
                          <div className="rounded-xl border border-border/50 bg-muted/50 px-4 py-3">
                            <p className="text-sm font-semibold text-foreground">Prescription Details</p>
                            {modalDetails.isLocked ? (
                              <p className="mt-1 text-sm text-muted-foreground">Locked until payment is completed.</p>
                            ) : (
                              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                                <div className="rounded-lg bg-background/70 p-3">
                                  <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Medicine Name</p>
                                  <p className="mt-1 text-sm text-foreground">{modalDetails.medicineName || 'N/A'}</p>
                                </div>
                                <div className="rounded-lg bg-background/70 p-3">
                                  <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Dosage</p>
                                  <p className="mt-1 text-sm text-foreground">{modalDetails.dosage || 'N/A'}</p>
                                </div>
                                <div className="rounded-lg bg-background/70 p-3">
                                  <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Route</p>
                                  <p className="mt-1 text-sm text-foreground">{modalDetails.route || 'N/A'}</p>
                                </div>
                                <div className="rounded-lg bg-background/70 p-3">
                                  <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Frequency</p>
                                  <p className="mt-1 text-sm text-foreground">{modalDetails.frequency || 'N/A'}</p>
                                </div>
                                <div className="rounded-lg bg-background/70 p-3">
                                  <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Duration</p>
                                  <p className="mt-1 text-sm text-foreground">{modalDetails.duration || 'N/A'}</p>
                                </div>
                                <div className="rounded-lg bg-background/70 p-3 sm:col-span-2">
                                  <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Notes</p>
                                  <p className="mt-1 text-sm text-foreground">{modalDetails.prescriptionNotes || 'N/A'}</p>
                                </div>
                              </div>
                            )}
                          </div>

                          <div className="rounded-xl border border-border/50 bg-muted/50 px-4 py-3">
                            <p className="text-sm font-semibold text-foreground">About Your Medication</p>

                            {modalDetails.isLocked ? (
                              <p className="mt-1 text-sm text-muted-foreground">Locked until payment is completed.</p>
                            ) : hasDrugInfo(modalDetails.drugInfo) ? (
                              <div className="mt-2 max-h-[400px] overflow-y-auto pr-1">
                                <div className="space-y-4">
                                  {modalDetails.drugInfo.indicationsAndUsage && (
                                    <div>
                                      <p className="text-xs font-semibold uppercase tracking-wide text-primary">Indications & Usage</p>
                                      <p className="mt-1 whitespace-pre-wrap text-sm leading-6 text-muted-foreground">{summarizeDrugText(modalDetails.drugInfo.indicationsAndUsage)}</p>
                                      <hr className="mt-3 border-border/60" />
                                    </div>
                                  )}
                                  {modalDetails.drugInfo.warnings && (
                                    <div>
                                      <p className="text-xs font-semibold uppercase tracking-wide text-primary">Warnings</p>
                                      <p className="mt-1 whitespace-pre-wrap text-sm leading-6 text-muted-foreground">{summarizeDrugText(modalDetails.drugInfo.warnings)}</p>
                                      <hr className="mt-3 border-border/60" />
                                    </div>
                                  )}
                                  {modalDetails.drugInfo.dosageAndAdministration && (
                                    <div>
                                      <p className="text-xs font-semibold uppercase tracking-wide text-primary">Dosage & Administration</p>
                                      <p className="mt-1 whitespace-pre-wrap text-sm leading-6 text-muted-foreground">{summarizeDrugText(modalDetails.drugInfo.dosageAndAdministration)}</p>
                                      <hr className="mt-3 border-border/60" />
                                    </div>
                                  )}
                                  {modalDetails.drugInfo.description && (
                                    <div>
                                      <p className="text-xs font-semibold uppercase tracking-wide text-primary">Description</p>
                                      <p className="mt-1 whitespace-pre-wrap text-sm leading-6 text-muted-foreground">{summarizeDrugText(modalDetails.drugInfo.description)}</p>
                                    </div>
                                  )}
                                </div>
                              </div>
                            ) : (
                              <p className="mt-1 text-sm text-muted-foreground">{modalDetails.drugInfoError || 'Drug information currently unavailable.'}</p>
                            )}
                          </div>
                        </>
                      )}

                      {appointment.status === 'CANCELLED' && (
                        <div className="rounded-xl border border-border/50 bg-muted/50 px-4 py-3">
                          <p className="text-sm font-semibold text-foreground">Cancellation Reason</p>
                          <p className="mt-1 text-sm text-muted-foreground">{appointment.rejectionReason || modalDetails.rejectionReason || 'No reason provided by the doctor.'}</p>
                        </div>
                      )}

                      <div className="rounded-xl border border-border/50 bg-muted/50 px-4 py-3">
                        <p className="mb-3 text-sm font-semibold text-foreground">Attached Files</p>
                        {modalFiles.length === 0 ? (
                          <p className="text-sm text-muted-foreground">No files attached.</p>
                        ) : (
                          <div className="space-y-2">
                            {modalFiles.map((file, index) => (
                              <div
                                key={file.id || `${file.fileName}-${index}`}
                                className="flex items-center justify-between gap-3 rounded-lg border border-border bg-background/70 px-3 py-2"
                              >
                                <div className="flex min-w-0 items-center gap-2">
                                  <FileText className={`h-3 w-3 shrink-0 ${modalDetails.isLocked ? 'text-muted-foreground' : 'text-primary'}`} />
                                  <span className={`truncate text-xs font-medium ${modalDetails.isLocked ? 'text-muted-foreground' : 'text-foreground'}`}>
                                    {file.fileName}
                                  </span>
                                </div>
                                <button
                                  type="button"
                                  onClick={() => {
                                    if (!modalDetails.isLocked) {
                                      viewAppointmentFile(file)
                                    }
                                  }}
                                  className={`inline-flex items-center rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
                                    modalDetails.isLocked
                                      ? 'cursor-not-allowed bg-muted text-muted-foreground'
                                      : 'bg-primary-soft/50 text-primary hover:bg-primary-soft'
                                  }`}
                                  title={modalDetails.isLocked ? 'File exists but is locked until payment is completed.' : 'View file'}
                                >
                                  View File
                                </button>
                              </div>
                            ))}
                          </div>
                        )}
                        {modalDetails.isLocked && modalFiles.length > 0 && (
                          <p className="mt-2 text-xs text-muted-foreground">These files exist but are locked until payment is completed.</p>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )
        })()}

        {cancelConfirmationId && (() => {
          const appointment = appointments.find((apt) => apt.id === cancelConfirmationId)
          if (!appointment) return null
          const doctorName = getDoctorName(appointment.doctor)
          const formattedDate = new Date(appointment.dateTime).toLocaleDateString()
          const formattedTime = new Date(appointment.dateTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })

          return (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4 py-8 backdrop-blur-sm">
              <div className="w-full max-w-lg overflow-hidden rounded-2xl border border-border bg-card shadow-2xl">
                <div className="border-b border-border px-5 py-4">
                  <h3 className="text-lg font-semibold">Cancel Appointment?</h3>
                  <p className="mt-1 text-sm text-muted-foreground">
                    This will permanently cancel your appointment with Dr. {doctorName} on {formattedDate} at {formattedTime}.
                  </p>
                </div>

                <div className="space-y-4 px-5 py-4 text-sm text-muted-foreground">
                  <p>Are you sure you want to continue?</p>
                  <p className="rounded-xl border border-border bg-muted/30 px-4 py-3 text-foreground">
                    You can book a new appointment afterward, but this one will be marked as cancelled.
                  </p>
                </div>

                <div className="flex justify-end gap-3 border-t border-border px-5 py-4">
                  <button
                    type="button"
                    onClick={() => setCancelConfirmationId(null)}
                    className="inline-flex h-10 items-center justify-center rounded-xl border border-input bg-background px-4 text-sm font-medium hover:bg-muted"
                  >
                    Keep Appointment
                  </button>
                  <button
                    type="button"
                    onClick={() => handleCancel(cancelConfirmationId)}
                    className="inline-flex h-10 items-center justify-center rounded-xl bg-destructive px-4 text-sm font-semibold text-destructive-foreground hover:bg-destructive/90"
                  >
                    Yes, Cancel
                  </button>
                </div>
              </div>
            </div>
          )
        })()}
      </div>
    </DashboardLayout>
  )
}

