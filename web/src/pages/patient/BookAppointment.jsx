import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  ArrowLeft,
  Calendar,
  ChevronLeft,
  ChevronRight,
  Clock,
  FileText,
  Paperclip,
  Star,
  Upload,
} from 'lucide-react'
import DashboardLayout from '../../components/DashboardLayout'
import UserAvatar from '../../components/UserAvatar'
import { bookAppointment } from '../../api/appointmentApi'
import { getDoctorAppointmentSlotsByDate, getDoctorAvailability } from '../../api/availabilityApi'
// import { uploadMyMedicalRecordFile } from '../../api/medicalRecordFileApi'
import { getDoctorById } from '../../api/userApi'
import { useToast } from '../../hooks/useToast'

const weekdayLabels = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa']
const RESERVATION_FEE_PHP = 100

function todayIso() {
  // Get local date in YYYY-MM-DD format (NOT UTC)
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function buildCalendarDays(viewMonth) {
  const today = todayIso()
  const year = viewMonth.getFullYear()
  const month = viewMonth.getMonth()
  const firstOfMonth = new Date(year, month, 1)
  const firstWeekday = firstOfMonth.getDay()
  const gridStart = new Date(year, month, 1 - firstWeekday)

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(gridStart)
    date.setDate(gridStart.getDate() + index)

    // Use local date (NOT UTC) to avoid timezone shifts
    const dateYear = date.getFullYear()
    const dateMonth = String(date.getMonth() + 1).padStart(2, '0')
    const dateDay = String(date.getDate()).padStart(2, '0')
    const iso = `${dateYear}-${dateMonth}-${dateDay}`

    return {
      iso,
      day: date.getDate(),
      month: date.getMonth(),
      year: date.getFullYear(),
      inCurrentMonth: date.getMonth() === month,
      isToday: iso === today,
      isPast: iso < today,
    }
  })
}

function formatTimeLabel(timeValue) {
  const [hourRaw, minuteRaw] = String(timeValue || '').split(':')
  const hour = Number(hourRaw)
  const minute = Number(minuteRaw)
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) {
    return String(timeValue || '')
  }

  const period = hour >= 12 ? 'PM' : 'AM'
  const twelveHour = hour % 12 === 0 ? 12 : hour % 12
  return `${twelveHour}:${String(minute).padStart(2, '0')} ${period}`
}

function extractSlots(entries) {
  if (!Array.isArray(entries)) {
    return []
  }

  return entries
    .filter((entry) => {
      if (!entry) return false
      // Check if status is AVAILABLE (handle string and enum)
      const status = String(entry.status || '').toUpperCase()
      return status === 'AVAILABLE'
    })
    .map((entry) => {
      if (!entry?.id) return null

      // Parse start time - handle both string and array formats
      let rawTime = ''
      const startTime = entry.slotStartTime

      if (Array.isArray(startTime)) {
        // Format array [9, 30, 0] to "09:30"
        const [hours, minutes] = startTime
        rawTime = `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`
      } else if (typeof startTime === 'string') {
        // Already a string: "09:30:00" or "09:30" -> take first 5 chars "09:30"
        rawTime = startTime.slice(0, 5)
      } else if (typeof startTime === 'number') {
        // Unlikely but handle it: could be seconds since midnight
        const hours = Math.floor(startTime / 3600)
        const minutes = Math.floor((startTime % 3600) / 60)
        rawTime = `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`
      }

      // Validate the time format
      if (!rawTime.match(/^\d{2}:\d{2}$/)) {
        return null
      }

      return {
        id: entry.id,
        time: rawTime,
        label: formatTimeLabel(rawTime),
      }
    })
    .filter(Boolean)
    .sort((a, b) => a.time.localeCompare(b.time))
}

function formatDoctorName(doctor) {
  const fullName = [doctor?.firstName, doctor?.lastName].filter(Boolean).join(' ')
  return fullName || doctor?.email || 'Doctor'
}

function doctorInitials(name) {
  return (
    name
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((chunk) => chunk[0])
      .join('')
      .toUpperCase() || 'DR'
  )
}

export default function PatientBookAppointment() {
  const { doctorId } = useParams()
  const navigate = useNavigate()
  const { success } = useToast()
  const [doctor, setDoctor] = useState(null)
  const [doctorAvailability, setDoctorAvailability] = useState([])
  const [selectedDate, setSelectedDate] = useState(todayIso())
  const [viewMonth, setViewMonth] = useState(() => new Date(`${todayIso()}T00:00:00`))
  const [availableSlots, setAvailableSlots] = useState([])
  const [selectedSlotId, setSelectedSlotId] = useState('')
  const [notes, setNotes] = useState('')
  const [stagedFiles, setStagedFiles] = useState([])
  const [uploadedFiles, setUploadedFiles] = useState([])
  const [uploadingFiles, setUploadingFiles] = useState(false)
  const [error, setError] = useState('')
  const [doctorLoading, setDoctorLoading] = useState(true)
  const [slotsLoading, setSlotsLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let mounted = true

    setDoctorLoading(true)

    getDoctorById(doctorId)
      .then((doctorData) => {
        if (!mounted) return
        setDoctor(doctorData || null)
      })
      .catch(() => {
        if (mounted) setDoctor(null)
      })
      .finally(() => {
        if (mounted) setDoctorLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [doctorId])

  // Load doctor's availability settings (to check for UNAVAILABLE dates)
  useEffect(() => {
    let mounted = true

    getDoctorAvailability(doctorId)
      .then((availData) => {
        if (!mounted) return
        console.log('[BookAppointment] Loaded doctor availability:', availData)
        setDoctorAvailability(Array.isArray(availData) ? availData : [])
      })
      .catch((err) => {
        console.error('[BookAppointment] Failed to load doctor availability:', err)
        if (mounted) setDoctorAvailability([])
      })

    return () => {
      mounted = false
    }
  }, [doctorId])

  useEffect(() => {
    let mounted = true

    setSlotsLoading(true)
    setError('')

    // Check if the selected date is marked as UNAVAILABLE
    const unavailableForDate = doctorAvailability.find(
      (avail) =>
        String(avail.availableDate || '') === selectedDate &&
        String(avail.status || '').toUpperCase() === 'UNAVAILABLE',
    )

    if (unavailableForDate) {
      console.log('[BookAppointment] Date %s is marked as UNAVAILABLE (holiday/day-off)', selectedDate)
      setAvailableSlots([])
      setSlotsLoading(false)
      return
    }

    console.log('[BookAppointment] Requesting slots for doctorId=%s, date=%s', doctorId, selectedDate)
    
    getDoctorAppointmentSlotsByDate(doctorId, selectedDate)
      .then((slotData) => {
        if (!mounted) return

        console.log('[BookAppointment] Slot data received for date %s:', selectedDate, slotData)
        const slots = extractSlots(slotData)
        console.log('[BookAppointment] Extracted %d slots for date %s:', slots.length, selectedDate, slots)
        setAvailableSlots(slots)
        
        if (slots.length === 0) {
          console.warn('[BookAppointment] No available slots found for date:', selectedDate)
        }
      })
      .catch((err) => {
        if (mounted) {
          console.error('[BookAppointment] Failed to load slots for date %s:', selectedDate, err)
          setError('Failed to load doctor schedule.')
          setAvailableSlots([])
        }
      })
      .finally(() => {
        if (mounted) setSlotsLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [doctorId, selectedDate, doctorAvailability])

  const hasNoSlots = availableSlots.length === 0
  const calendarDays = useMemo(() => buildCalendarDays(viewMonth), [viewMonth])
  const monthLabel = useMemo(
    () => viewMonth.toLocaleDateString([], { month: 'long', year: 'numeric' }),
    [viewMonth],
  )

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')

    if (!selectedSlotId) {
      setError('Please select date and time.')
      return
    }

    setSubmitting(true)
    try {
      await bookAppointment(Number(doctorId), Number(selectedSlotId), notes)

      if (stagedFiles.length > 0) {
        try {
          setUploadingFiles(true)
          const uploaded = await Promise.all(
            stagedFiles.map((file) => uploadMyMedicalRecordFile(file, notes || 'Uploaded from appointment booking')),
          )

          setUploadedFiles((prev) => [...prev, ...uploaded])
          setStagedFiles([])
          setUploadingFiles(false)
        } catch (uploadErr) {
          setUploadingFiles(false)
          setError(
            uploadErr.response?.data?.detail ||
              uploadErr.response?.data?.message ||
              'Appointment was booked, but one or more attachments failed to upload.',
          )
        }
      }

      success('Appointment booked successfully.')
      navigate('/patient/appointments', { replace: true })
    } catch (err) {
      setError(
        err.response?.data?.detail ||
          err.response?.data?.message ||
          'Booking failed. Please select a valid future slot.',
      )
    } finally {
      setUploadingFiles(false)
      setSubmitting(false)
    }
  }

  async function handleFileUpload(event) {
    const files = Array.from(event.target.files || [])
    if (files.length === 0) return

    setError('')
    setStagedFiles((prev) => [...prev, ...files])
    event.target.value = ''
  }

  const doctorName = formatDoctorName(doctor)
  const doctorSpecialty = (doctor?.specializations || []).join(', ') || 'Not specified'
  const doctorRating = doctor?.averageRating
  const reservationFee = `₱${RESERVATION_FEE_PHP.toLocaleString()}`

  return (
    <DashboardLayout>
      <div className="mx-auto max-w-4xl space-y-6 px-1 py-1 sm:px-0">
        <button
          type="button"
          onClick={() => navigate(-1)}
          className="flex items-center gap-1 text-sm text-muted-foreground transition-colors hover:text-foreground"
        >
          <ArrowLeft className="h-4 w-4" /> Back
        </button>

        <div>
          <h1 className="text-3xl font-bold tracking-tight">Book Appointment</h1>
          <p className="mt-1 text-muted-foreground">Select your preferred date, time, and attach any records</p>
        </div>

        <div className="flex items-center gap-3">
          <div className="inline-flex items-center gap-2 rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground shadow-sm">
            <span className="flex h-6 w-6 items-center justify-center rounded-full bg-primary-foreground/20 text-xs">1</span>
            Details
          </div>
          <div className="h-px w-10 bg-border" />
          <div className="inline-flex items-center gap-2 rounded-full bg-muted px-4 py-2 text-sm text-muted-foreground">
            <span className="flex h-6 w-6 items-center justify-center rounded-full bg-background text-xs">2</span>
            Payment
          </div>
        </div>

        <div className="rounded-2xl border border-border bg-card p-5 shadow-card sm:p-6">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-4">
              <UserAvatar
                imageUrl={doctor?.profileImageUrl}
                name={doctorName}
                fallback={doctorInitials(doctorName)}
                className="h-16 w-16 shrink-0"
                textClassName="text-xl"
                toneClassName="bg-primary-soft text-primary"
                alt={`Dr. ${doctorName}`}
              />
              <div>
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="text-xl font-bold text-foreground">Dr. {doctorName}</h2>
                  <Link to={`/patient/doctor/${doctorId}`} className="text-sm text-primary hover:underline">
                    View Profile
                  </Link>
                </div>
                <p className="text-muted-foreground">{doctorSpecialty}</p>
                <div className="mt-1 flex items-center gap-1.5 text-sm">
                  <Star className="h-4 w-4 fill-primary text-primary" />
                  <span className="font-semibold text-foreground">{doctorRating != null ? doctorRating : 'N/A'}</span>
                </div>
              </div>
            </div>

            <div className="text-right">
              <p className="text-2xl font-bold text-primary">{reservationFee}</p>
              <p className="text-sm text-muted-foreground">reservation fee</p>
            </div>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">
          <div className="space-y-3">
            <h3 className="text-base font-medium text-foreground">Select Date</h3>
            <div className="rounded-2xl border border-border bg-card p-5 shadow-card sm:p-6">
              <div className="flex items-center justify-between">
                <button
                  type="button"
                  onClick={() => setViewMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() - 1, 1))}
                  className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-border text-muted-foreground hover:bg-muted"
                  aria-label="Previous month"
                >
                  <ChevronLeft className="h-4 w-4" />
                </button>

                <p className="text-lg font-medium">{monthLabel}</p>

                <button
                  type="button"
                  onClick={() => setViewMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() + 1, 1))}
                  className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-border text-muted-foreground hover:bg-muted"
                  aria-label="Next month"
                >
                  <ChevronRight className="h-4 w-4" />
                </button>
              </div>

              <div className="mt-4 grid grid-cols-7 gap-y-2 text-center text-sm text-muted-foreground">
                {weekdayLabels.map((label) => (
                  <span key={label}>{label}</span>
                ))}
              </div>

              <div className="mt-2 grid grid-cols-7 gap-y-2 text-center">
                {calendarDays.map((cell) => {
                  const isSelected = selectedDate === cell.iso
                  return (
                    <button
                      key={cell.iso}
                      type="button"
                      onClick={() => {
                        setSelectedDate(cell.iso)
                        setViewMonth(new Date(`${cell.iso}T00:00:00`))
                        setSelectedSlotId('')
                      }}
                      disabled={cell.isPast}
                      className={`mx-auto inline-flex h-10 w-10 items-center justify-center rounded-xl text-sm transition-colors ${
                        isSelected
                          ? 'bg-primary text-primary-foreground shadow-sm'
                          : cell.inCurrentMonth
                            ? 'text-foreground hover:bg-primary-soft'
                            : 'text-muted-foreground/40'
                      } ${cell.isToday && !isSelected ? 'ring-1 ring-border' : ''} ${cell.isPast ? 'cursor-not-allowed opacity-40 hover:bg-transparent' : ''}`}
                    >
                      {cell.day}
                    </button>
                  )
                })}
              </div>
            </div>
          </div>

          <div className="space-y-3">
            <h3 className="text-base font-medium text-foreground">Select Time Slot</h3>
            <div className="rounded-2xl border border-border bg-card p-5 shadow-card sm:p-6">
              {slotsLoading ? (
                <p className="text-sm text-muted-foreground">Loading schedule...</p>
              ) : doctorAvailability.some(
                  (avail) =>
                    String(avail.availableDate || '') === selectedDate &&
                    String(avail.status || '').toUpperCase() === 'UNAVAILABLE',
                ) ? (
                <p className="text-sm text-yellow-600">
                  ⚠️ Doctor is unavailable on this date (holiday or day-off). Please select another date.
                </p>
              ) : !hasNoSlots ? (
                <div className="flex flex-wrap gap-2">
                  {availableSlots.map((slot) => (
                    <button
                      key={slot.id}
                      type="button"
                      onClick={() => setSelectedSlotId(String(slot.id))}
                      className={`h-11 rounded-xl border px-4 text-base font-medium transition-colors ${
                        String(selectedSlotId) === String(slot.id)
                          ? 'border-primary bg-primary text-primary-foreground'
                          : 'border-border bg-background text-foreground hover:border-primary hover:text-primary'
                      }`}
                    >
                      {slot.label}
                    </button>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">No available time slots for this date.</p>
              )}
            </div>
          </div>

          <div className="space-y-3">
            <h3 className="text-base font-medium text-foreground">Reason for Visit / Notes</h3>
            <textarea
              id="notes"
              placeholder="Describe your symptoms or reason for the visit..."
              rows={4}
              value={notes}
              onChange={(event) => setNotes(event.target.value)}
              maxLength={500}
              className="w-full rounded-2xl border border-input bg-background px-4 py-3 text-sm shadow-sm outline-none ring-offset-background placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
            />
          </div>

          <div className="space-y-3">
            <h3 className="text-base font-medium text-foreground">Upload Medical Records (optional)</h3>
            <p className="text-sm text-muted-foreground">Attach previous lab results or medical records for the doctor to review before your visit.</p>
            <div className="rounded-2xl border border-dashed border-border bg-muted/20 p-6 text-center">
              <Upload className="mx-auto mb-3 h-7 w-7 text-muted-foreground" />
              <p className="text-sm text-muted-foreground">Drag and drop files or click to upload</p>
              <p className="text-sm text-muted-foreground">Supported formats: PDF, PNG, JPG, JPEG</p>
              <input
                id="medical-record-upload-input"
                type="file"
                multiple
                accept=".pdf,.png,.jpg,.jpeg"
                onChange={handleFileUpload}
                className="hidden"
              />
              <label
                htmlFor="medical-record-upload-input"
                className="mt-4 inline-flex h-10 cursor-pointer items-center justify-center rounded-xl border border-border bg-background px-4 text-sm font-medium text-foreground hover:bg-muted"
              >
                <Paperclip className="mr-2 h-4 w-4" />
                {uploadingFiles ? 'Uploading...' : 'Choose Files'}
              </label>
            </div>
            {stagedFiles.length > 0 && (
              <div className="space-y-2">
                {stagedFiles.map((file, index) => (
                  <div key={`${file.name}-${index}`} className="flex items-center gap-2 rounded-lg bg-primary-soft/40 px-3 py-2 text-xs text-foreground">
                    <FileText className="h-3.5 w-3.5 text-primary" />
                    <span>{file.name}</span>
                  </div>
                ))}
              </div>
            )}
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}

          <button
            type="submit"
            disabled={submitting || doctorLoading || slotsLoading}
            className="inline-flex h-12 w-full items-center justify-center rounded-xl bg-primary px-4 text-sm font-semibold text-primary-foreground shadow-md hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {submitting ? 'Booking...' : 'Proceed to Payment'}
          </button>
        </form>
      </div>
    </DashboardLayout>
  )
}
