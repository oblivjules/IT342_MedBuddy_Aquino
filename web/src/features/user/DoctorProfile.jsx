import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  ArrowLeft,
  Calendar,
  Clock,
  Mail,
  MapPin,
  Phone,
  Star,
  Users,
} from 'lucide-react'
import DashboardLayout from '../../components/DashboardLayout'
import UserAvatar from './UserAvatar'
import { getDoctorAppointmentSlotsByDate } from '../schedule/availabilityApi'
import { getRatingsByDoctor } from '../feedback/ratingApi'
import { getDoctorById } from './userApi'

const AVAILABILITY_PAGE_SIZE = 5
const UPCOMING_SLOT_LOOKAHEAD_DAYS = 30

function initials(name) {
  return (
    name
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0])
      .join('')
      .toUpperCase() || 'DR'
  )
}

function formatDate(value) {
  // Handle both string and Date formats
  if (!value) return ''
  
  try {
    const dateObj = typeof value === 'string' ? new Date(`${value}T00:00:00`) : value
    return dateObj.toLocaleDateString([], {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
    })
  } catch {
    return String(value)
  }
}

function formatTime(value) {
  if (!value) return ''
  
  // Handle LocalTime serialized as array [hours, minutes, seconds]
  if (Array.isArray(value)) {
    const [hours, minutes] = value
    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`
  }
  
  // Handle string format "09:30:00"
  const timeStr = String(value || '')
  return timeStr.slice(0, 5)
}

function stars(value) {
  const rounded = Math.max(1, Math.min(5, Math.round(value || 0)))
  return [1, 2, 3, 4, 5].map((index) => ({
    index,
    active: index <= rounded,
  }))
}

function normalizeReview(review) {
  return {
    ...review,
    rating: Number(review?.rating ?? review?.ratingScore ?? 0),
    comment:
      review?.comment
      || review?.feedback
      || review?.feedbackComment
      || review?.message
      || '',
  }
}

function toIsoDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function makeUpcomingDateWindow(days) {
  const dates = []
  const base = new Date()
  base.setHours(0, 0, 0, 0)

  // Booking policy starts from tomorrow.
  base.setDate(base.getDate() + 1)

  for (let i = 0; i < days; i += 1) {
    const date = new Date(base)
    date.setDate(base.getDate() + i)
    dates.push(toIsoDate(date))
  }

  return dates
}

export default function PatientDoctorProfile() {
  const { doctorId } = useParams()
  const navigate = useNavigate()
  const [doctor, setDoctor] = useState(null)
  const [upcomingSlots, setUpcomingSlots] = useState([])
  const [reviews, setReviews] = useState([])
  const [loading, setLoading] = useState(true)
  const [slotsPage, setSlotsPage] = useState(1)

  useEffect(() => {
    let mounted = true

    async function load() {
      try {
        const [doctors, feedback] = await Promise.all([
          getDoctorById(doctorId),
          getRatingsByDoctor(doctorId).catch(() => []),
        ])

        const dateWindow = makeUpcomingDateWindow(UPCOMING_SLOT_LOOKAHEAD_DAYS)
        const slotBuckets = []

        for (const slotDate of dateWindow) {
          const slots = await getDoctorAppointmentSlotsByDate(doctorId, slotDate).catch(() => [])
          slotBuckets.push(slots)
        }

        if (!mounted) return

        const flattenedSlots = slotBuckets
          .flat()
          .filter((slot) => String(slot?.status || '').toUpperCase() === 'AVAILABLE')
          .sort((left, right) => {
            const leftKey = `${left?.slotDate || ''} ${String(left?.slotStartTime || '')}`
            const rightKey = `${right?.slotDate || ''} ${String(right?.slotStartTime || '')}`
            return leftKey.localeCompare(rightKey)
          })

        setDoctor(doctors || null)
        setUpcomingSlots(flattenedSlots)
        setReviews(Array.isArray(feedback) ? feedback.map(normalizeReview) : [])
      } catch (err) {
        if (mounted) {
          setDoctor(null)
          setUpcomingSlots([])
          setReviews([])
        }
      } finally {
        if (mounted) setLoading(false)
      }
    }

    load()

    return () => {
      mounted = false
    }
  }, [doctorId])

  const doctorName = useMemo(() => {
    if (!doctor) return 'Doctor'
    return [doctor.firstName, doctor.lastName].filter(Boolean).join(' ') || doctor.email || 'Doctor'
  }, [doctor])

  const avgRating = useMemo(() => {
    if (!reviews.length) return null
    const total = reviews.reduce((sum, item) => sum + Number(item.rating || item.ratingScore || 0), 0)
    return Number((total / reviews.length).toFixed(1))
  }, [reviews])

  const totalSlotPages = Math.max(1, Math.ceil(upcomingSlots.length / AVAILABILITY_PAGE_SIZE))

  const paginatedSlots = useMemo(() => {
    const start = (slotsPage - 1) * AVAILABILITY_PAGE_SIZE
    return upcomingSlots.slice(start, start + AVAILABILITY_PAGE_SIZE)
  }, [slotsPage, upcomingSlots])

  useEffect(() => {
    setSlotsPage(1)
  }, [doctorId, upcomingSlots.length])

  if (loading) {
    return (
      <DashboardLayout>
        <p className="text-sm text-muted-foreground">Loading doctor profile...</p>
      </DashboardLayout>
    )
  }

  if (!doctor) {
    return (
      <DashboardLayout>
        <div className="space-y-3">
          <p className="text-sm text-muted-foreground">Doctor profile not found.</p>
          <Link to="/patient/find-doctor" className="text-sm font-medium text-primary hover:underline">
            Back to Find Doctor
          </Link>
        </div>
      </DashboardLayout>
    )
  }

  const specialty = (doctor.specializations || []).join(', ') || 'Not specified'

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <button
          type="button"
          onClick={() => navigate(-1)}
          className="flex items-center gap-1 text-sm text-muted-foreground transition-colors hover:text-foreground"
        >
          <ArrowLeft className="h-4 w-4" /> Back to Doctors
        </button>

        <div className="rounded-2xl border border-border bg-card p-6 shadow-card">
          <div className="flex flex-col gap-6 sm:flex-row sm:items-start">
            <UserAvatar
              imageUrl={doctor?.profileImageUrl}
              name={doctorName}
              fallback={initials(doctorName)}
              className="h-24 w-24 shrink-0"
              textClassName="text-3xl"
              toneClassName="bg-primary text-primary-foreground"
              alt={`Dr. ${doctorName}`}
            />
            <div className="flex-1 space-y-3">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h1 className="text-2xl font-bold">Dr. {doctorName}</h1>
                  <p className="text-muted-foreground font-body">{specialty}</p>
                </div>
                <span className="inline-flex w-fit rounded-full bg-primary-soft px-3 py-1 text-xs font-semibold text-primary">
                  Available for booking
                </span>
              </div>

              <div className="flex flex-wrap gap-4 text-sm">
                <span className="flex items-center gap-1.5 text-muted-foreground">
                  <Star className="h-4 w-4 fill-accent text-accent" />
                  <span className="font-semibold text-foreground">{avgRating ?? 'N/A'}</span> ({reviews.length || 'No'} reviews)
                </span>
                <span className="flex items-center gap-1.5 text-muted-foreground">
                  <Users className="h-4 w-4" /> Verified specialist
                </span>
                <span className="flex items-center gap-1.5 text-muted-foreground">
                  <Clock className="h-4 w-4" /> Flexible schedule
                </span>
              </div>

              <div className="flex gap-3 pt-1">
                <Link
                  to={`/patient/book/${doctorId}`}
                  className="inline-flex h-10 items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground shadow-md hover:bg-primary/90"
                >
                  <Calendar className="mr-2 h-4 w-4" /> Book Appointment
                </Link>
              </div>
            </div>
          </div>
        </div>

        <div className="grid gap-6 lg:grid-cols-3">
          <div className="space-y-6 lg:col-span-2">
            <div className="rounded-2xl border border-border bg-card shadow-card">
              <div className="border-b border-border p-5">
                <h2 className="text-lg font-semibold">Clinic Information</h2>
              </div>
              <div className="space-y-3 p-5 text-sm text-muted-foreground">
                <p className="flex items-center gap-2"><MapPin className="h-4 w-4 text-primary" /> MedBuddy Clinic</p>
                <p className="flex items-center gap-2"><Phone className="h-4 w-4 text-primary" /> {doctor.phoneNumber || 'Not provided'}</p>
                <p className="flex items-center gap-2"><Mail className="h-4 w-4 text-primary" /> {doctor.email || 'Not provided'}</p>
              </div>
            </div>

            <div className="rounded-2xl border border-border bg-card shadow-card">
              <div className="border-b border-border p-5">
                <h2 className="text-lg font-semibold">Recent Reviews</h2>
              </div>
              <div className="divide-y divide-border">
                {reviews.length === 0 && (
                  <p className="p-5 text-sm text-muted-foreground">No reviews yet.</p>
                )}
                {reviews.slice(0, 4).map((review) => (
                  <div key={review.id} className="p-5">
                    <div className="mb-2 flex items-center gap-1">
                      {stars(Number(review.rating || review.ratingScore || 0)).map((entry) => (
                        <Star key={entry.index} className={`h-3.5 w-3.5 ${entry.active ? 'fill-accent text-accent' : 'text-muted-foreground/30'}`} />
                      ))}
                    </div>
                    <p className="text-sm text-muted-foreground font-body">{review.comment || 'No comment provided.'}</p>
                    {review.createdAt && (
                      <p className="mt-1 text-xs text-muted-foreground">{new Date(review.createdAt).toLocaleDateString()}</p>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div className="space-y-6">
            <div className="rounded-2xl border border-border bg-card shadow-card">
              <div className="border-b border-border p-5">
                <h2 className="font-semibold">Upcoming Availability</h2>
              </div>
              {upcomingSlots.length === 0 ? (
                <p className="p-5 text-sm text-muted-foreground">No schedule published yet.</p>
              ) : (
                <div>
                  <div className="divide-y divide-border">
                  {paginatedSlots.map((slot, idx) => {
                    const dateStr = formatDate(slot.slotDate)
                    const startTime = formatTime(slot.slotStartTime)
                    const endTime = formatTime(slot.slotEndTime)
                    const key = `${slot.slotDate}-${slot.slotStartTime}-${slot.id || idx}`
                    return (
                      <div key={key} className="flex items-center justify-between px-5 py-3 text-sm">
                        <span className="text-muted-foreground">{dateStr}</span>
                        <span className="font-medium">
                          {startTime}
                          {endTime ? ` - ${endTime}` : ''}
                        </span>
                      </div>
                    )
                  })}
                  </div>

                  <div className="flex items-center justify-between border-t border-border px-5 py-3 text-xs text-muted-foreground">
                    <span>
                      Page {slotsPage} of {totalSlotPages}
                    </span>
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => setSlotsPage((prev) => Math.max(1, prev - 1))}
                        disabled={slotsPage === 1}
                        className="inline-flex h-8 items-center justify-center rounded-md border border-input px-3 text-xs font-medium text-foreground hover:bg-muted disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        Previous
                      </button>
                      <button
                        type="button"
                        onClick={() => setSlotsPage((prev) => Math.min(totalSlotPages, prev + 1))}
                        disabled={slotsPage >= totalSlotPages}
                        className="inline-flex h-8 items-center justify-center rounded-md border border-input px-3 text-xs font-medium text-foreground hover:bg-muted disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        Next
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </div>

            <Link
              to={`/patient/book/${doctorId}`}
              className="inline-flex h-10 w-full items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground shadow-md hover:bg-primary/90"
            >
              <Calendar className="mr-2 h-4 w-4" /> Book Appointment
            </Link>
          </div>
        </div>
      </div>
    </DashboardLayout>
  )
}
