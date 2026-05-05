import { useEffect, useMemo, useState } from 'react'
import {
  Calendar,
  CheckCircle,
  ClipboardCheck,
  Clock,
  Eye,
  FileText,
  Search,
  Upload,
  X,
  XCircle,
} from 'lucide-react'
import DashboardLayout from '../../components/DashboardLayout'
import FileOpenerModal from '../../components/FileOpenerModal'
import { getMyAppointments, updateAppointmentStatus } from '../../api/appointmentApi'
import { getFileAccessUrl, getFilesByAppointment, uploadAppointmentFile } from '../../api/fileUploadApi'
import { createMedicalRecord, getMedicalRecordByAppointment, updateMedicalRecord } from '../../api/medicalRecordApi'
import { getPaymentByAppointment, updateAppointmentTotalBill, updatePaymentStatus } from '../../api/paymentApi'
import { getDrugInfo } from '../../api/drugInfoApi'
import { useUnsavedChangesGuard } from '../../hooks/useUnsavedChangesGuard'
import { useToast } from '../../hooks/useToast'

const statuses = ['PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED']
const avatarColors = ['bg-primary', 'bg-teal', 'bg-accent', 'bg-primary', 'bg-teal', 'bg-accent']

const statusDot = {
  PENDING: 'bg-accent',
  CONFIRMED: 'bg-primary',
  COMPLETED: 'bg-teal',
  CANCELLED: 'bg-destructive',
}

const statusBadgeClass = {
  PENDING: 'bg-accent/10 text-accent',
  CONFIRMED: 'bg-primary/10 text-primary',
  COMPLETED: 'bg-teal/10 text-teal',
  CANCELLED: 'bg-destructive/10 text-destructive',
}

const paymentBadgeClass = {
  PAID: 'bg-primary/10 text-primary',
  PENDING: 'bg-accent/10 text-accent',
  PARTIAL: 'bg-teal/10 text-teal',
}

function getPatientName(patient) {
  const fullName = [patient?.firstName, patient?.lastName].filter(Boolean).join(' ')
  return fullName || patient?.email || 'Patient'
}

function getInitials(name) {
  return (
    name
      .replace('Dr.', '')
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0])
      .join('')
      .toUpperCase() || 'PT'
  )
}

function toDateTime(value) {
  return new Date(value)
}

function isAppointmentDayReached(dateTimeValue) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const appointmentDay = new Date(dateTimeValue)
  appointmentDay.setHours(0, 0, 0, 0)
  return appointmentDay <= today
}

function formatPaymentStatus(status) {
  const value = String(status || '').toUpperCase()
  if (!value) return 'No payment record'
  return value[0] + value.slice(1).toLowerCase()
}

function getFeeText(payment) {
  if (!payment) return 'No payment record'
  const total = Number(payment.feeAmount || 0)
  const paid = Number(payment.paidAmount || 0)
  const remaining = Number(payment.remainingAmount ?? (total - paid))

  if (remaining <= 0) {
    return `₱${total.toLocaleString()} paid in full`
  }

  return `₱${remaining.toLocaleString()} remaining (₱${paid.toLocaleString()} paid of ₱${total.toLocaleString()})`
}

function toStatusLabel(status) {
  if (status === 'CONFIRMED') return 'APPROVED'
  if (status === 'CANCELLED') return 'REJECTED'
  return status
}

function summarizeDrugText(text, maxLength = 180) {
  const value = String(text || '').replace(/\s+/g, ' ').trim()
  if (!value) return ''
  if (value.length <= maxLength) return value
  return `${value.slice(0, maxLength).trim()}...`
}

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

export default function DoctorAppointments() {
  const { success } = useToast()
  const [appointments, setAppointments] = useState([])
  const [filesByAppointmentId, setFilesByAppointmentId] = useState({})
  const [paymentsByAppointment, setPaymentsByAppointment] = useState({})
  const [detailsByAppointmentId, setDetailsByAppointmentId] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [completionModalId, setCompletionModalId] = useState(null)
  const [detailsModalId, setDetailsModalId] = useState(null)
  const [detailsLoadingByAppointmentId, setDetailsLoadingByAppointmentId] = useState({})
  const [diagnosisInput, setDiagnosisInput] = useState('')
  const [medicineNameInput, setMedicineNameInput] = useState('')
  const [dosageInput, setDosageInput] = useState('')
  const [routeInput, setRouteInput] = useState('')
  const [frequencyInput, setFrequencyInput] = useState('')
  const [durationInput, setDurationInput] = useState('')
  const [prescriptionNotesInput, setPrescriptionNotesInput] = useState('')
  const [billTotalInput, setBillTotalInput] = useState('')
  const [hasDraftChanges, setHasDraftChanges] = useState(false)
  const [selectedFile, setSelectedFile] = useState(null)
  const [uploadingForAppointment, setUploadingForAppointment] = useState(null)
  const [pendingStatusChange, setPendingStatusChange] = useState(null)
  const [statusChangeReason, setStatusChangeReason] = useState('')
  const [statusChangeError, setStatusChangeError] = useState('')
  const [completionFormErrors, setCompletionFormErrors] = useState({
    diagnosis: '',
    billTotal: '',
  })

  const hasUnsavedChanges = Boolean(completionModalId && hasDraftChanges)

  useUnsavedChangesGuard(hasUnsavedChanges, "Changes won't be saved. Are you sure you want to leave?")

  useEffect(() => {
    async function load() {
      try {
        const data = await getMyAppointments()
        const safeAppointments = (Array.isArray(data) ? data : [])
          .slice()
          .sort((left, right) => new Date(right.dateTime) - new Date(left.dateTime))
        setAppointments(safeAppointments)

        const appointmentFileEntries = await Promise.all(
          safeAppointments.map(async (appointment) => {
            try {
              const files = await getFilesByAppointment(appointment.id)
              return [appointment.id, Array.isArray(files) ? files : []]
            } catch {
              return [appointment.id, []]
            }
          }),
        )
        setFilesByAppointmentId(Object.fromEntries(appointmentFileEntries))

        const paymentEntries = await Promise.all(
          safeAppointments.map(async (appointment) => {
            try {
              const payment = await getPaymentByAppointment(appointment.id)
              return [appointment.id, payment]
            } catch {
              return [appointment.id, null]
            }
          }),
        )

        setPaymentsByAppointment(Object.fromEntries(paymentEntries))
      } catch {
        setError('Failed to load appointments.')
      } finally {
        setLoading(false)
      }
    }

    load()
  }, [])

  const counts = useMemo(() => {
    return statuses.reduce((accumulator, status) => {
      accumulator[status] = appointments.filter((appointment) => appointment.status === status).length
      return accumulator
    }, {})
  }, [appointments])

  const filtered = useMemo(() => {
    return appointments.filter((apt) => {
      const patientName = getPatientName(apt.patient).toLowerCase()
      const matchesSearch = !search || patientName.includes(search.toLowerCase()) || (apt.patient?.email || '').toLowerCase().includes(search.toLowerCase())
      const matchesStatus = statusFilter === 'ALL' || apt.status === statusFilter
      return matchesSearch && matchesStatus
    })
  }, [appointments, search, statusFilter])

  // Group appointments by status for the grouped view (only Pending, Confirmed, Completed)
  const groupedSections = useMemo(() => {
    const sections = ['PENDING', 'CONFIRMED', 'COMPLETED']
    return sections.map((status) => ({
      status,
      items: filtered
        .filter((apt) => apt.status === status)
        .slice()
        .sort((a, b) => new Date(a.dateTime) - new Date(b.dateTime)), // Ascending order (earliest first)
    })).filter((section) => section.items.length > 0)
  }, [filtered])

  const sectionLabels = {
    PENDING: 'Pending',
    CONFIRMED: 'Confirmed',
    COMPLETED: 'Completed',
  }

  async function changeStatus(id, status, rejectionReason = null) {
    setError('')

    try {
      const updated = await updateAppointmentStatus(id, status, rejectionReason)
      setAppointments((prev) => prev.map((apt) => (apt.id === id ? { ...apt, ...updated } : apt)))
      success(`Appointment moved to ${toStatusLabel(status).toLowerCase()} successfully.`)
      return true
    } catch (err) {
      setError(
        err.response?.data?.detail
          || err.response?.data?.message
          || 'Unable to update status. Please try again.',
      )
      return false
    }
  }

  function openStatusChangeModal(pendingChange) {
    setError('')
    setStatusChangeError('')
    setStatusChangeReason(pendingChange.rejectionReason || '')
    setPendingStatusChange(pendingChange)
  }

  function closeStatusChangeModal() {
    setPendingStatusChange(null)
    setStatusChangeReason('')
    setStatusChangeError('')
  }

  async function requestStatusChange(id, status, extras = {}) {
    const appointment = appointments.find((apt) => apt.id === id)
    openStatusChangeModal({
      id,
      status,
      appointment,
      ...extras,
    })
    return true
  }

  async function confirmPendingStatusChange() {
    if (!pendingStatusChange) return

    const { id, status, appointment, completePayload } = pendingStatusChange

    if (status === 'CANCELLED') {
      if (!statusChangeReason.trim()) {
        setStatusChangeError('Rejection reason is required.')
        return
      }

      const changed = await changeStatus(id, status, statusChangeReason.trim())
      if (changed) {
        closeStatusChangeModal()
      }
      return
    }

    if (status === 'COMPLETED') {
      try {
        const payload = completePayload || {}

        await ensureMedicalRecordForAppointment(id)
        const updatedPayment = await updateAppointmentTotalBill(id, payload.billAmount)
        setPaymentsByAppointment((prev) => ({
          ...prev,
          [id]: updatedPayment,
        }))

        const changed = await changeStatus(id, status)
        if (!changed) return

        setCompletionModalId(null)
        setDiagnosisInput('')
        setMedicineNameInput('')
        setDosageInput('')
        setRouteInput('')
        setFrequencyInput('')
        setDurationInput('')
        setPrescriptionNotesInput('')
        setBillTotalInput('')
        setHasDraftChanges(false)
        closeStatusChangeModal()
      } catch (err) {
        setStatusChangeError(
          err.response?.data?.detail
            || err.response?.data?.message
            || err.message
            || 'Unable to complete appointment. Please try again.',
        )
      }
      return
    }

    const changed = await changeStatus(id, status)
    if (changed) {
      closeStatusChangeModal()
    }
  }

  async function openDetailsModal(appointmentId) {
    setDetailsModalId(appointmentId)
    setHasDraftChanges(false)
    setDetailsLoadingByAppointmentId((prev) => ({ ...prev, [appointmentId]: true }))

    try {
      const [fetchedRecord, fetchedFiles] = await Promise.all([
        getMedicalRecordByAppointment(appointmentId).catch(() => null),
        getFilesByAppointment(appointmentId).catch(() => []),
      ])
      const record = fetchedRecord
      const files = fetchedFiles
      let drugInfo = null
      let drugInfoError = ''

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

      setDetailsByAppointmentId((prev) => ({
        ...prev,
        [appointmentId]: {
          recordId: record?.id || null,
          type: inferRecordType(record),
          diagnosis: record?.diagnosis || '',
          prescriptionDetails: record?.prescriptionDetails || '',
          medicineName: record?.medicineName || '',
          dosage: record?.dosage || '',
          route: record?.route || '',
          frequency: record?.frequency || '',
          duration: record?.duration || '',
          prescriptionNotes: record?.prescriptionNotes || '',
          drugInfo,
          drugInfoError,
          files: Array.isArray(files) ? files : [],
        },
      }))
    } catch {
      setDetailsByAppointmentId((prev) => ({
        ...prev,
        [appointmentId]: {
          recordId: null,
          type: 'Consultation',
          diagnosis: '',
          prescriptionDetails: '',
          medicineName: '',
          dosage: '',
          route: '',
          frequency: '',
          duration: '',
          prescriptionNotes: '',
          drugInfo: null,
          drugInfoError: 'Drug information currently unavailable.',
          files: [],
        },
      }))
    } finally {
      setDetailsLoadingByAppointmentId((prev) => ({ ...prev, [appointmentId]: false }))
    }
  }

  async function ensureMedicalRecordForAppointment(appointmentId) {
    try {
      const existing = await getMedicalRecordByAppointment(appointmentId)
      if (!existing?.id) {
        throw new Error('Medical record lookup returned no id.')
      }

      if (diagnosisInput.trim() || medicineNameInput.trim() || dosageInput.trim() || routeInput.trim() || frequencyInput.trim() || durationInput.trim() || prescriptionNotesInput.trim()) {
        const legacyPrescriptionDetails = buildPrescriptionSummary({
          medicineName: medicineNameInput.trim(),
          dosage: dosageInput.trim(),
          route: routeInput.trim(),
          frequency: frequencyInput.trim(),
          duration: durationInput.trim(),
          prescriptionNotes: prescriptionNotesInput.trim(),
        })

        const payload = {
          appointmentId,
          diagnosis: diagnosisInput.trim() || existing.diagnosis || 'Diagnosis pending',
          prescriptionDetails: legacyPrescriptionDetails || existing.prescriptionDetails || '',
          medicineName: medicineNameInput.trim() || existing.medicineName || null,
          dosage: dosageInput.trim() || existing.dosage || null,
          route: routeInput.trim() || existing.route || null,
          frequency: frequencyInput.trim() || existing.frequency || null,
          duration: durationInput.trim() || existing.duration || null,
          prescriptionNotes: prescriptionNotesInput.trim() || existing.prescriptionNotes || null,
        }
        return await updateMedicalRecord(existing.id, payload)
      }

      return existing
    } catch (err) {
      const status = err?.response?.status
      if (status !== 400 && status !== 404) {
        throw err
      }

      const payload = {
        appointmentId,
        diagnosis: diagnosisInput.trim() || 'Diagnosis pending',
        prescriptionDetails: buildPrescriptionSummary({
          medicineName: medicineNameInput.trim(),
          dosage: dosageInput.trim(),
          route: routeInput.trim(),
          frequency: frequencyInput.trim(),
          duration: durationInput.trim(),
          prescriptionNotes: prescriptionNotesInput.trim(),
        }),
        medicineName: medicineNameInput.trim() || null,
        dosage: dosageInput.trim() || null,
        route: routeInput.trim() || null,
        frequency: frequencyInput.trim() || null,
        duration: durationInput.trim() || null,
        prescriptionNotes: prescriptionNotesInput.trim() || null,
      }
      return await createMedicalRecord(payload)
    }
  }

  async function completeAppointment(id) {
    const diagnosis = diagnosisInput.trim()
    const billAmount = Number(billTotalInput)

    const nextErrors = {
      diagnosis: diagnosis ? '' : 'Diagnosis is required.',
      billTotal: Number.isFinite(billAmount) && billAmount > 0 ? '' : 'Total bill must be greater than 0.',
    }

    setCompletionFormErrors(nextErrors)

    if (nextErrors.diagnosis || nextErrors.billTotal) {
      setError('Please complete all required fields before submitting.')
      return
    }

    setError('')
    setCompletionFormErrors({ diagnosis: '', billTotal: '' })
    requestStatusChange(id, 'COMPLETED', {
      completePayload: {
        diagnosis,
        medicineName: medicineNameInput.trim(),
        dosage: dosageInput.trim(),
        route: routeInput.trim(),
        frequency: frequencyInput.trim(),
        duration: durationInput.trim(),
        prescriptionNotes: prescriptionNotesInput.trim(),
        prescriptionDetails: buildPrescriptionSummary({
          medicineName: medicineNameInput.trim(),
          dosage: dosageInput.trim(),
          route: routeInput.trim(),
          frequency: frequencyInput.trim(),
          duration: durationInput.trim(),
          prescriptionNotes: prescriptionNotesInput.trim(),
        }),
        billAmount,
      },
    })
  }

  function closeCompletionModal() {
    setCompletionModalId(null)
    setHasDraftChanges(false)
    setCompletionFormErrors({ diagnosis: '', billTotal: '' })
  }

  async function openCompletionModal(appointmentId) {
    setError('')
    setCompletionFormErrors({ diagnosis: '', billTotal: '' })
    setCompletionModalId(appointmentId)
    setHasDraftChanges(false)
    setBillTotalInput('')

    const existingDetails = detailsByAppointmentId[appointmentId]
    if (existingDetails) {
      setDiagnosisInput(existingDetails.diagnosis || '')
      setMedicineNameInput(existingDetails.medicineName || '')
      setDosageInput(existingDetails.dosage || '')
      setRouteInput(existingDetails.route || '')
      setFrequencyInput(existingDetails.frequency || '')
      setDurationInput(existingDetails.duration || '')
      setPrescriptionNotesInput(existingDetails.prescriptionNotes || '')
      return
    }

    try {
      const record = await getMedicalRecordByAppointment(appointmentId)
      const details = {
        diagnosis: record?.diagnosis || '',
        prescriptionDetails: record?.prescriptionDetails || '',
        medicineName: record?.medicineName || '',
        dosage: record?.dosage || '',
        route: record?.route || '',
        frequency: record?.frequency || '',
        duration: record?.duration || '',
        prescriptionNotes: record?.prescriptionNotes || '',
      }
      setDetailsByAppointmentId((prev) => ({
        ...prev,
        [appointmentId]: details,
      }))
      setDiagnosisInput(details.diagnosis)
      setMedicineNameInput(details.medicineName)
      setDosageInput(details.dosage)
      setRouteInput(details.route)
      setFrequencyInput(details.frequency)
      setDurationInput(details.duration)
      setPrescriptionNotesInput(details.prescriptionNotes)
    } catch {
      setDiagnosisInput('')
      setMedicineNameInput('')
      setDosageInput('')
      setRouteInput('')
      setFrequencyInput('')
      setDurationInput('')
      setPrescriptionNotesInput('')
    }
  }

                  async function handleLabResultUpload(appointment, event) {
    const file = event.target.files?.[0]
    if (!file) return

    setError('')
    setUploadingForAppointment(appointment.id)

    try {
      const medicalRecord = await ensureMedicalRecordForAppointment(appointment.id)
      if (!medicalRecord?.id) {
        throw new Error('No medical record found for this appointment.')
      }

      const uploaded = await uploadAppointmentFile(medicalRecord.id, file)

      setFilesByAppointmentId((prev) => {
        const previousFiles = prev[appointment.id] || []
        const alreadyExists = previousFiles.some((existing) => existing.id === uploaded?.id)

        if (alreadyExists) {
          return prev
        }

        return {
          ...prev,
          [appointment.id]: [...previousFiles, uploaded],
        }
      })

      setAppointments((prev) =>
        prev.map((apt) =>
          apt.id === appointment.id
            ? { ...apt, doctorFiles: [...(apt.doctorFiles || []), uploaded.fileName] }
            : apt,
        ),
      )

      success('Lab result uploaded successfully.')
    } catch (err) {
      setError(
        err.response?.data?.detail ||
          err.response?.data?.message ||
          err.message ||
          'Failed to upload lab result.',
      )
    } finally {
      setUploadingForAppointment(null)
      event.target.value = ''
    }
  }

  function removeUploadedFile(appointmentId, fileIdToRemove) {
    setFilesByAppointmentId((prev) => {
      const previousFiles = prev[appointmentId] || []
      return {
        ...prev,
        [appointmentId]: previousFiles.filter((file) => file.id !== fileIdToRemove),
      }
    })
  }

  async function openProtectedFile(file) {
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
      setError(err.response?.data?.message || 'Unable to open file right now.')
    }
  }

  const summaryItems = [
    { label: 'Pending', value: counts.PENDING || 0, tone: 'bg-accent/10 text-accent' },
    { label: 'Approved', value: counts.CONFIRMED || 0, tone: 'bg-primary/10 text-primary' },
    { label: 'Completed', value: counts.COMPLETED || 0, tone: 'bg-teal/10 text-teal' },
    { label: 'Rejected', value: counts.CANCELLED || 0, tone: 'bg-muted text-muted-foreground' },
  ]

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold md:text-3xl">Appointment Management</h1>
            <p className="mt-1 text-muted-foreground font-body">View, manage, and complete patient appointments</p>
          </div>
        </div>

        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {summaryItems.map((item) => (
            <div key={item.label} className="rounded-2xl border border-border bg-card p-4 shadow-card transition-all hover:-translate-y-0.5 hover:shadow-elevated">
              <div className={`mb-2 inline-flex rounded-lg px-2.5 py-1 text-xs font-medium ${item.tone}`}>{item.label}</div>
              <p className="text-3xl font-bold">{item.value}</p>
            </div>
          ))}
        </div>

        <div className="flex flex-col gap-3 sm:flex-row">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search patients..."
              className="h-10 w-full rounded-md border border-input bg-card pl-9 pr-3 text-sm shadow-sm"
            />
          </div>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="h-10 rounded-md border border-input bg-card px-3 text-sm sm:w-40 shadow-sm"
          >
            <option value="ALL">All Status</option>
            {statuses.map((status) => (
              <option key={status} value={status}>{toStatusLabel(status)}</option>
            ))}
          </select>
        </div>

        {error && <p className="rounded-md border border-destructive/20 bg-destructive/10 p-3 text-sm text-destructive">{error}</p>}

        {/* Grouped Appointments Sections */}
        {!loading && appointments.length === 0 && (
          <div className="py-12 text-center text-muted-foreground">
            <p className="font-medium">No appointments yet</p>
          </div>
        )}

        {!loading && appointments.length > 0 && filtered.length === 0 && (
          <div className="py-12 text-center text-muted-foreground">
            <p className="font-medium">No appointments match your filters</p>
          </div>
        )}

        {!loading && filtered.length > 0 && groupedSections.length === 0 && (
          <div className="py-12 text-center text-muted-foreground">
            <p className="font-medium">No appointments here</p>
          </div>
        )}

        {groupedSections.map((section) => (
          <section key={section.status} className="space-y-3">
            <h2 className="flex items-center gap-2 text-lg font-semibold">
              <span className={`inline-block h-2.5 w-2.5 rounded-full ${statusDot[section.status]}`} />
              {sectionLabels[section.status]}
              <span className="ml-1 rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                {section.items.length}
              </span>
            </h2>

            <div className="space-y-3">
              {section.items.map((apt) => {
                const patientName = getPatientName(apt.patient)
                const payment = paymentsByAppointment[apt.id]
                const paymentStatus = String(payment?.paymentStatus || '').toUpperCase()
                const formattedDateTime = toDateTime(apt.dateTime)
                // Find index in original appointments array for avatar color consistency
                const globalIndex = appointments.findIndex((a) => a.id === apt.id)

                return (
                  <div
                    key={apt.id}
                    className="rounded-2xl border border-border bg-card shadow-card transition-all hover:-translate-y-0.5 hover:shadow-elevated"
                  >
                    <div className="p-5">
                      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                        <div className="flex items-center gap-4">
                          <div className={`flex h-11 w-11 items-center justify-center rounded-full text-sm font-bold text-primary-foreground ${avatarColors[globalIndex % avatarColors.length]}`}>
                            {getInitials(patientName)}
                          </div>
                          <div>
                            <p className="font-semibold">{patientName}</p>
                            <p className="text-sm text-muted-foreground font-body">
                              <Calendar className="mr-1 inline h-3 w-3" />
                              {formattedDateTime.toLocaleDateString()} • <Clock className="mr-1 inline h-3 w-3" />
                              {formattedDateTime.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                            </p>
                          </div>
                        </div>
                        <div className="flex flex-wrap items-center gap-2">
                          <span className={`rounded-full px-3 py-1 text-xs font-medium ${statusBadgeClass[apt.status]}`}>
                            {toStatusLabel(apt.status)}
                          </span>
                          <span className={`rounded-full px-3 py-1 text-xs font-medium ${paymentBadgeClass[paymentStatus] || 'bg-muted text-muted-foreground'}`}>
                            Payment: {formatPaymentStatus(payment?.paymentStatus)}
                          </span>
                          {apt.status === 'PENDING' && (
                            <>
                              <button
                                type="button"
                                onClick={() => requestStatusChange(apt.id, 'CONFIRMED')}
                                className="inline-flex items-center gap-1 rounded-md bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
                              >
                                <CheckCircle className="h-3.5 w-3.5" />
                                Approve
                              </button>
                              <button
                                type="button"
                                onClick={() => requestStatusChange(apt.id, 'CANCELLED')}
                                className="inline-flex items-center gap-1 rounded-md bg-destructive px-3 py-1.5 text-xs font-semibold text-destructive-foreground transition-colors hover:bg-destructive/90"
                              >
                                <XCircle className="h-3.5 w-3.5" />
                                Reject
                              </button>
                              <button
                                type="button"
                                onClick={() => openDetailsModal(apt.id)}
                                className="inline-flex items-center gap-1 rounded-md border border-input bg-background px-3 py-1.5 text-xs font-semibold text-foreground transition-colors hover:bg-muted"
                                title="View appointment details"
                              >
                                View Details
                              </button>
                            </>
                          )}
                          {apt.status === 'CONFIRMED' && (
                            <>
                              {isAppointmentDayReached(apt.dateTime) && (
                                <button
                                  type="button"
                                  onClick={() => openCompletionModal(apt.id)}
                                  className="inline-flex items-center gap-1 rounded-md bg-teal px-3 py-1.5 text-xs font-semibold text-teal-foreground transition-colors hover:bg-teal/90 disabled:opacity-60"
                                >
                                  <ClipboardCheck className="h-3.5 w-3.5" />
                                  Complete
                                </button>
                              )}
                              <button
                                type="button"
                                onClick={() => requestStatusChange(apt.id, 'CANCELLED')}
                                className="inline-flex items-center gap-1 rounded-md bg-destructive px-3 py-1.5 text-xs font-semibold text-destructive-foreground transition-colors hover:bg-destructive/90"
                              >
                                <XCircle className="h-3.5 w-3.5" />
                                Reject
                              </button>
                              <button
                                type="button"
                                onClick={() => openDetailsModal(apt.id)}
                                className="inline-flex items-center gap-1 rounded-md border border-input bg-background px-3 py-1.5 text-xs font-semibold text-foreground transition-colors hover:bg-muted"
                                title="View appointment details"
                              >
                                View Details
                              </button>
                            </>
                          )}
                          {apt.status === 'COMPLETED' && (
                            <button
                              type="button"
                              onClick={() => openDetailsModal(apt.id)}
                              className="inline-flex items-center gap-1 rounded-md border border-input bg-background px-3 py-1.5 text-xs font-semibold text-foreground transition-colors hover:bg-muted"
                              title="View appointment details"
                            >
                              View Details
                            </button>
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>
          </section>
        ))}

        {selectedFile && (
          <FileOpenerModal
            file={selectedFile}
            onClose={() => setSelectedFile(null)}
          />
        )}

        {completionModalId && (() => {
          const appointment = appointments.find((apt) => apt.id === completionModalId)
          if (!appointment || appointment.status !== 'CONFIRMED') return null

          const patientName = getPatientName(appointment.patient)
          const formattedDate = new Date(appointment.dateTime).toLocaleDateString()
          const formattedTime = new Date(appointment.dateTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
          const modalFiles = filesByAppointmentId[completionModalId] || []

          return (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4 py-8 backdrop-blur-sm">
              <div className="w-full max-w-2xl overflow-hidden rounded-2xl border border-border bg-card shadow-2xl">
                <div className="flex items-center justify-between border-b border-border px-5 py-4">
                  <div className="min-w-0">
                    <h3 className="truncate text-lg font-semibold">Complete Appointment</h3>
                    <p className="text-sm text-muted-foreground">{patientName} • {formattedDate} {formattedTime}</p>
                  </div>
                  <button
                    type="button"
                    onClick={closeCompletionModal}
                    className="rounded-full p-2 text-muted-foreground hover:bg-muted"
                    aria-label="Close completion modal"
                  >
                    <X className="h-5 w-5" />
                  </button>
                </div>

                <div className="max-h-[70vh] overflow-auto space-y-4 p-5">
                  {appointment.notes && (
                    <div className="rounded-xl border border-border/50 bg-muted/50 px-4 py-3">
                      <p className="text-sm font-semibold text-foreground">Patient Notes</p>
                      <p className="mt-1 text-sm text-muted-foreground">{appointment.notes}</p>
                    </div>
                  )}

                  <div className="space-y-1.5">
                    <label className="text-xs font-medium text-muted-foreground">Diagnosis</label>
                    <textarea
                      rows={3}
                      required
                      value={diagnosisInput}
                      onChange={(event) => {
                        setDiagnosisInput(event.target.value)
                        setHasDraftChanges(true)
                        setCompletionFormErrors((prev) => ({ ...prev, diagnosis: '' }))
                      }}
                      placeholder="Enter diagnosis"
                      className={`w-full rounded-md border bg-card px-3 py-2 text-sm ${completionFormErrors.diagnosis ? 'border-destructive' : 'border-input'}`}
                    />
                    {completionFormErrors.diagnosis && <p className="text-xs text-destructive">{completionFormErrors.diagnosis}</p>}
                  </div>

                  <div className="space-y-1.5">
                    <label className="text-xs font-medium text-muted-foreground">Prescription Details</label>
                    <div className="grid gap-3 md:grid-cols-2">
                      <div className="space-y-1.5 md:col-span-2">
                        <input
                          value={medicineNameInput}
                          onChange={(event) => {
                            setMedicineNameInput(event.target.value)
                            setHasDraftChanges(true)
                          }}
                          placeholder="Medicine Name"
                          className="h-10 w-full rounded-md border border-input bg-card px-3 text-sm"
                        />
                      </div>
                      <input
                        value={dosageInput}
                        onChange={(event) => {
                          setDosageInput(event.target.value)
                          setHasDraftChanges(true)
                        }}
                        placeholder="Dosage"
                        className="h-10 rounded-md border border-input bg-card px-3 text-sm"
                      />
                      <input
                        value={routeInput}
                        onChange={(event) => {
                          setRouteInput(event.target.value)
                          setHasDraftChanges(true)
                        }}
                        placeholder="Route"
                        className="h-10 rounded-md border border-input bg-card px-3 text-sm"
                      />
                      <input
                        value={frequencyInput}
                        onChange={(event) => {
                          setFrequencyInput(event.target.value)
                          setHasDraftChanges(true)
                        }}
                        placeholder="Frequency"
                        className="h-10 rounded-md border border-input bg-card px-3 text-sm"
                      />
                      <input
                        value={durationInput}
                        onChange={(event) => {
                          setDurationInput(event.target.value)
                          setHasDraftChanges(true)
                        }}
                        placeholder="Duration"
                        className="h-10 rounded-md border border-input bg-card px-3 text-sm"
                      />
                      <textarea
                        rows={2}
                        value={prescriptionNotesInput}
                        onChange={(event) => {
                          setPrescriptionNotesInput(event.target.value)
                          setHasDraftChanges(true)
                        }}
                        placeholder="Notes (optional)"
                        className="min-h-20 rounded-md border border-input bg-card px-3 py-2 text-sm md:col-span-2"
                      />
                    </div>
                  </div>

                  <div className="grid gap-3 md:grid-cols-2">
                    <div className="space-y-1.5">
                      <label className="text-xs font-medium text-muted-foreground">Total Bill (PHP)</label>
                      <input
                        type="number"
                        required
                        min="0"
                        step="0.01"
                        value={billTotalInput}
                        onChange={(event) => {
                          setBillTotalInput(event.target.value)
                          setHasDraftChanges(true)
                          setCompletionFormErrors((prev) => ({ ...prev, billTotal: '' }))
                        }}
                        placeholder="0.00"
                        className={`h-10 w-full rounded-md border bg-card px-3 text-sm ${completionFormErrors.billTotal ? 'border-destructive' : 'border-input'}`}
                      />
                      {completionFormErrors.billTotal && <p className="text-xs text-destructive">{completionFormErrors.billTotal}</p>}
                    </div>

                    <div className="space-y-1.5">
                      <label className="text-xs font-medium text-muted-foreground">Upload Lab Result</label>
                      <div className="flex flex-col items-start gap-2">
                        <label className="inline-flex h-10 cursor-pointer items-center justify-center gap-2 rounded-md border border-input bg-card px-3 text-sm hover:bg-muted">
                          <Upload className="h-4 w-4" />
                          {uploadingForAppointment === appointment.id ? 'Uploading...' : 'Attach file'}
                          <input
                            type="file"
                            className="hidden"
                            onChange={(event) => handleLabResultUpload(appointment, event)}
                            disabled={uploadingForAppointment === appointment.id}
                          />
                        </label>
                      </div>
                      {error && <p className="text-xs text-destructive bg-destructive/10 border border-destructive/20 rounded-md p-2">{error}</p>}
                    </div>
                  </div>

                  <div className="space-y-2 rounded-xl border border-border/50 bg-muted/30 px-4 py-3">
                    <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Uploaded Files</p>
                    {modalFiles.length === 0 ? (
                      <p className="text-sm text-muted-foreground">No files uploaded yet.</p>
                    ) : (
                      <div className="space-y-2">
                        {modalFiles.map((file, index) => (
                          <div
                            key={file.id || `${file?.fileName || 'uploaded'}-${index}`}
                            className="flex items-center justify-between gap-3 rounded-lg bg-primary-soft/50 px-3 py-1.5"
                          >
                            <div className="flex min-w-0 items-center gap-1.5">
                              <FileText className="h-3 w-3 shrink-0 text-primary" />
                              <span className="truncate text-xs font-medium text-foreground">{file.fileName || `Attachment ${index + 1}`}</span>
                            </div>
                            {file?.id && (
                              <button
                                type="button"
                                onClick={() => removeUploadedFile(appointment.id, file.id)}
                                className="inline-flex h-5 w-5 items-center justify-center rounded-full hover:bg-background hover:text-destructive transition-colors"
                                aria-label={`Remove ${file.fileName}`}
                              >
                                <X className="h-3 w-3" />
                              </button>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>

                <div className="flex justify-end border-t border-border px-5 py-4">
                  <button
                    type="button"
                    onClick={() => completeAppointment(appointment.id)}
                    className="inline-flex items-center gap-1 rounded-md bg-teal px-4 py-2 text-sm font-semibold text-teal-foreground hover:bg-teal/90"
                  >
                    <ClipboardCheck className="h-4 w-4" />
                    Complete
                  </button>
                </div>
              </div>
            </div>
          )
        })()}

        {detailsModalId && (() => {
          const appointment = appointments.find((apt) => apt.id === detailsModalId)
          if (!appointment) return null
          const patientName = getPatientName(appointment.patient)
          const formattedDate = new Date(appointment.dateTime).toLocaleDateString()
          const formattedTime = new Date(appointment.dateTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
          const modalDetails = detailsByAppointmentId[detailsModalId] || {}
          const detailsLoading = Boolean(detailsLoadingByAppointmentId[detailsModalId])
          const modalFiles = detailsByAppointmentId[detailsModalId]?.files || filesByAppointmentId[detailsModalId] || []

          return (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4 py-8 backdrop-blur-sm">
              <div className="w-full max-w-2xl overflow-hidden rounded-2xl border border-border bg-card shadow-2xl">
                <div className="flex items-center justify-between border-b border-border px-5 py-4">
                  <div className="min-w-0">
                    <h3 className="truncate text-lg font-semibold">Appointment Details</h3>
                    <p className="text-sm text-muted-foreground">{patientName} • {formattedDate} {formattedTime}</p>
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

                <div className="max-h-[70vh] overflow-auto space-y-4 p-5">
                  {detailsLoading && (
                    <p className="text-sm text-muted-foreground">Loading appointment details...</p>
                  )}

                  {appointment.notes && (
                    <div className="rounded-xl border border-border/50 bg-muted/50 px-4 py-3">
                      <p className="text-sm font-semibold text-foreground">Patient Notes</p>
                      <p className="mt-1 text-sm text-muted-foreground">{appointment.notes}</p>
                    </div>
                  )}

                  {appointment.status === 'COMPLETED' && (
                    <>
                      <div className="rounded-xl border border-border/50 bg-muted/50 px-4 py-3">
                        <p className="text-sm font-semibold text-foreground">Diagnosis</p>
                        <p className="mt-1 text-sm text-muted-foreground">{modalDetails.diagnosis || 'No diagnosis recorded.'}</p>
                      </div>
                      <div className="rounded-xl border border-border/50 bg-muted/50 px-4 py-3">
                        <p className="text-sm font-semibold text-foreground">Prescription Details</p>
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
                      </div>

                      <div className="rounded-xl border border-border/50 bg-muted/50 px-4 py-3">
                        <p className="text-sm font-semibold text-foreground">About Prescribed Medication</p>

                        {modalDetails.isLocked ? (
                          <p className="mt-1 text-sm text-muted-foreground">Locked until payment is completed.</p>
                        ) : hasDrugInfo(modalDetails.drugInfo) ? (
                          <div className="mt-2 space-y-3">
                            {modalDetails.drugInfo.indicationsAndUsage && (
                              <div>
                                <p className="text-xs font-semibold uppercase tracking-wide text-primary">Indications & Usage</p>
                                <p className="mt-1 text-sm text-muted-foreground">{summarizeDrugText(modalDetails.drugInfo.indicationsAndUsage)}</p>
                              </div>
                            )}
                            {modalDetails.drugInfo.warnings && (
                              <div>
                                <p className="text-xs font-semibold uppercase tracking-wide text-destructive">Warnings</p>
                                <p className="mt-1 text-sm text-muted-foreground">{summarizeDrugText(modalDetails.drugInfo.warnings)}</p>
                              </div>
                            )}
                            {modalDetails.drugInfo.dosageAndAdministration && (
                              <div>
                                <p className="text-xs font-semibold uppercase tracking-wide text-primary">Dosage & Administration</p>
                                <p className="mt-1 text-sm text-muted-foreground">{summarizeDrugText(modalDetails.drugInfo.dosageAndAdministration)}</p>
                              </div>
                            )}
                            {modalDetails.drugInfo.description && (
                              <div>
                                <p className="text-xs font-semibold uppercase tracking-wide text-primary">Description</p>
                                <p className="mt-1 text-sm text-muted-foreground">{summarizeDrugText(modalDetails.drugInfo.description)}</p>
                              </div>
                            )}
                          </div>
                        ) : (
                          <p className="mt-1 text-sm text-muted-foreground">{modalDetails.drugInfoError || 'Drug information currently unavailable.'}</p>
                        )}
                      </div>
                    </>
                  )}

                  {(modalFiles || []).length > 0 && (
                    <div className="rounded-xl border border-border/50 bg-muted/50 px-4 py-3">
                      <p className="text-sm font-semibold text-foreground mb-3">Patient Files</p>
                      <div className="flex flex-wrap gap-2">
                        {(modalFiles || []).map((file) => (
                          <button
                            key={file.id}
                            type="button"
                            onClick={() => openProtectedFile(file)}
                            className="inline-flex items-center gap-1.5 rounded-lg bg-primary-soft/50 px-3 py-1.5 text-xs font-medium hover:bg-primary-soft transition-colors"
                          >
                            <FileText className="h-3 w-3 text-primary" />
                            {file.fileName}
                          </button>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )
        })()}

        {pendingStatusChange && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4 py-8 backdrop-blur-sm">
            <div className="w-full max-w-lg rounded-2xl border border-border bg-card shadow-2xl">
              <div className="border-b border-border px-6 py-4">
                <h3 className="text-lg font-semibold">Confirm Status Change</h3>
                <p className="mt-1 text-sm text-muted-foreground">
                  Review the details below before making this final update.
                </p>
              </div>

              <div className="space-y-4 px-6 py-5 text-sm">
                <div className="rounded-xl border border-border bg-muted/30 p-4">
                  <p className="text-xs uppercase tracking-wide text-muted-foreground">Appointment</p>
                  <p className="mt-1 font-medium text-foreground">
                    {getPatientName(pendingStatusChange.appointment?.patient)}
                  </p>
                  <p className="text-muted-foreground">
                    {pendingStatusChange.appointment?.dateTime
                      ? `${new Date(pendingStatusChange.appointment.dateTime).toLocaleDateString()} at ${new Date(pendingStatusChange.appointment.dateTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
                      : 'N/A'}
                  </p>
                </div>

                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="rounded-xl border border-border p-4">
                    <p className="text-xs uppercase tracking-wide text-muted-foreground">Current Status</p>
                    <p className="mt-1 font-medium text-foreground">{toStatusLabel(pendingStatusChange.appointment?.status || '')}</p>
                  </div>
                  {paymentsByAppointment[detailsModalId] && (
                    <div className="mt-4 rounded-xl border border-border/50 bg-muted/50 px-4 py-3">
                      <p className="text-sm font-semibold text-foreground">Payment</p>
                      <p className="mt-1 text-sm text-muted-foreground">Amount: ₱{Number(paymentsByAppointment[detailsModalId].feeAmount || 0).toLocaleString()}</p>
                      <p className="text-sm text-muted-foreground">Status: {String(paymentsByAppointment[detailsModalId].paymentStatus || 'PENDING')}</p>
                      <div className="mt-2 flex items-center gap-2">
                        <select
                          value={paymentsByAppointment[detailsModalId].paymentStatus}
                          onChange={async (e) => {
                            try {
                              const resp = await updatePaymentStatus(paymentsByAppointment[detailsModalId].id, e.target.value)
                              setPaymentsByAppointment((prev) => ({ ...prev, [detailsModalId]: resp }))
                              success('Payment status updated.')
                            } catch (err) {
                              setError(err.response?.data?.message || 'Unable to update payment status.')
                            }
                          }}
                          className="h-9 rounded-md border border-input bg-card px-3 text-sm"
                        >
                          <option value="PENDING">PENDING</option>
                          <option value="PAID">PAID</option>
                          <option value="FAILED">FAILED</option>
                          <option value="REFUNDED">REFUNDED</option>
                        </select>
                      </div>
                    </div>
                  )}
                  <div className="rounded-xl border border-border p-4">
                    <p className="text-xs uppercase tracking-wide text-muted-foreground">Next Status</p>
                    <p className="mt-1 font-medium text-foreground">{toStatusLabel(pendingStatusChange.status)}</p>
                  </div>
                </div>

                {pendingStatusChange.status === 'COMPLETED' && pendingStatusChange.completePayload && (
                  <div className="rounded-xl border border-border p-4">
                    <p className="text-xs uppercase tracking-wide text-muted-foreground">Completion Summary</p>
                    <div className="mt-2 space-y-1 text-foreground">
                      <p><span className="font-medium">Diagnosis:</span> {pendingStatusChange.completePayload.diagnosis || 'N/A'}</p>
                      <p><span className="font-medium">Medicine:</span> {pendingStatusChange.completePayload.medicineName || 'N/A'}</p>
                      <p><span className="font-medium">Dosage:</span> {pendingStatusChange.completePayload.dosage || 'N/A'}</p>
                      <p><span className="font-medium">Route:</span> {pendingStatusChange.completePayload.route || 'N/A'}</p>
                      <p><span className="font-medium">Frequency:</span> {pendingStatusChange.completePayload.frequency || 'N/A'}</p>
                      <p><span className="font-medium">Duration:</span> {pendingStatusChange.completePayload.duration || 'N/A'}</p>
                      <p><span className="font-medium">Notes:</span> {pendingStatusChange.completePayload.prescriptionNotes || 'N/A'}</p>
                      <p><span className="font-medium">Bill:</span> ₱{Number(pendingStatusChange.completePayload.billAmount || 0).toLocaleString()}</p>
                    </div>
                  </div>
                )}

                {pendingStatusChange.status === 'COMPLETED' && (
                  <div className="rounded-xl border border-border p-4">
                    <p className="text-xs uppercase tracking-wide text-muted-foreground">Attached Files</p>
                    {(filesByAppointmentId[pendingStatusChange.appointment?.id] || []).length === 0 ? (
                      <p className="mt-2 text-sm text-muted-foreground">No files attached.</p>
                    ) : (
                      <div className="mt-2 space-y-2">
                        {(filesByAppointmentId[pendingStatusChange.appointment?.id] || []).map((file, index) => (
                          <div key={file.id || `${file.fileName}-${index}`} className="inline-flex items-center gap-2 rounded-lg bg-primary-soft/50 px-3 py-1.5 text-xs font-medium text-foreground">
                            <FileText className="h-3 w-3 text-primary" />
                            <span className="truncate">{file.fileName || `Attachment ${index + 1}`}</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                {pendingStatusChange.status === 'CANCELLED' && (
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Rejection reason</label>
                    <textarea
                      rows={3}
                      value={statusChangeReason}
                      onChange={(event) => {
                        setStatusChangeReason(event.target.value)
                        setStatusChangeError('')
                      }}
                      placeholder="Provide a reason for rejecting this appointment..."
                      className={`w-full rounded-md border bg-card px-3 py-2 text-sm ${statusChangeError ? 'border-destructive' : 'border-input'}`}
                    />
                    {statusChangeError ? <p className="text-sm text-destructive">{statusChangeError}</p> : null}
                  </div>
                )}

                <div className="rounded-xl border border-border bg-muted/30 p-4 text-sm text-muted-foreground">
                  This action is final and cannot be undone.
                </div>
              </div>

              <div className="flex justify-end gap-3 border-t border-border px-6 py-4">
                <button
                  type="button"
                  onClick={closeStatusChangeModal}
                  className="inline-flex h-10 items-center justify-center rounded-xl border border-input bg-background px-4 text-sm font-medium hover:bg-muted"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={confirmPendingStatusChange}
                  className="inline-flex h-10 items-center justify-center rounded-xl bg-primary px-4 text-sm font-semibold text-primary-foreground hover:bg-primary/90"
                >
                  OK
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </DashboardLayout>
  )
}

