import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  ArrowLeft,
  Calendar,
  Clock,
  Mail,
  MapPin,
  Phone,
  Users,
} from 'lucide-react'
import DashboardLayout from '../../components/DashboardLayout'
import UserAvatar from '../../components/UserAvatar'
import { getDoctorAvailability } from '../../api/availabilityApi'
import { getDoctorById } from '../../api/userApi'

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

export default function PatientDoctorProfile() {
  const { doctorId } = useParams()
  const navigate = useNavigate()
  const [doctor, setDoctor] = useState(null)
  const [availability, setAvailability] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let mounted = true

    async function load() {
      try {
        const [doctors, slots] = await Promise.all([
          getDoctorById(doctorId),
          getDoctorAvailability(doctorId),
        ])

        if (!mounted) return

        console.log('[DoctorProfile] Loaded doctor:', doctors)
        console.log('[DoctorProfile] Loaded availability slots:', slots)
        setDoctor(doctors || null)
        setAvailability(Array.isArray(slots) ? slots : [])
      } catch (err) {
        console.error('[DoctorProfile] Error loading data:', err)
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

  const upcomingSlots = useMemo(() => {
    if (!Array.isArray(availability) || availability.length === 0) {
      console.log('[DoctorProfile] No availability data')
      return []
    }

    console.log('[DoctorProfile] Processing %d availability slots', availability.length)

    // Filter and sort slots
    const filtered = availability
      .filter((slot) => {
        // Ensure slot has required fields
        const hasRequired = slot?.availableDate && slot?.startTime && slot?.endTime
        const statusOk = String(slot?.status || '').toUpperCase() === 'AVAILABLE'
        
        if (!hasRequired) {
          console.log('[DoctorProfile] Filtered out slot (missing required fields):', slot)
        }
        if (!statusOk) {
          console.log('[DoctorProfile] Filtered out slot (status not AVAILABLE):', slot?.status)
        }
        
        return hasRequired && statusOk
      })
      .sort((left, right) => {
        const dateA = String(left.availableDate || '')
        const dateB = String(right.availableDate || '')
        const dateDiff = dateA.localeCompare(dateB)
        
        if (dateDiff !== 0) return dateDiff
        
        const timeA = String(left.startTime || '').slice(0, 5)
        const timeB = String(right.startTime || '').slice(0, 5)
        return timeA.localeCompare(timeB)
      })
      .slice(0, 5)

    console.log('[DoctorProfile] Filtered to %d upcoming slots:', filtered.length, filtered)
    return filtered
  }, [availability])

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
          </div>

          <div className="space-y-6">
            <div className="rounded-2xl border border-border bg-card shadow-card">
              <div className="border-b border-border p-5">
                <h2 className="font-semibold">Upcoming Availability</h2>
              </div>
              {upcomingSlots.length === 0 ? (
                <p className="p-5 text-sm text-muted-foreground">No schedule published yet.</p>
              ) : (
                <div className="divide-y divide-border">
                  {upcomingSlots.map((slot, idx) => {
                    const dateStr = formatDate(slot.availableDate)
                    const startTime = formatTime(slot.startTime)
                    const endTime = formatTime(slot.endTime)
                    const key = `${slot.availableDate}-${slot.startTime}-${endTime}-${idx}`
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
