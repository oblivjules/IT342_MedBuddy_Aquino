import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Bell,
  Calendar as CalendarIcon,
  CheckCircle,
  ChevronLeft,
  ChevronRight,
  Clock,
  Plus,
} from 'lucide-react'
import DashboardLayout from '../../components/DashboardLayout'
import UserAvatar from '../../components/UserAvatar'
import { getMyAppointments } from '../../api/appointmentApi'
// import { getMedicalRecordByAppointment } from '../../api/medicalRecordApi'
import { useAuth } from '../../hooks/useAuth'

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

export default function PatientDashboard() {
  const { user } = useAuth()
  const [appointments, setAppointments] = useState([])
  const [records, setRecords] = useState([])
  const [selectedDate, setSelectedDate] = useState(new Date().toISOString().slice(0, 10))
  const [visibleMonth, setVisibleMonth] = useState(toMonthStart(new Date()))
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    getMyAppointments()
      .then(async (data) => {
        const list = Array.isArray(data) ? data : []
        setAppointments(list)

        const completed = list.filter((apt) => apt.status === 'COMPLETED').slice(0, 8)
        const recentRecords = await Promise.all(
          completed.map(async (apt) => {
            try {
              const record = await getMedicalRecordByAppointment(apt.id)
              return { ...record, appointmentDateTime: apt.dateTime, doctor: apt.doctor }
            } catch {
              return null
            }
          }),
        )
        setRecords(recentRecords.filter(Boolean).slice(0, 5))
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
      { label: 'Notifications', value: pending, icon: Bell, tone: 'bg-secondary text-secondary-foreground' },
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
                {onSelectedDate.length} upcoming appointment(s) on this date
              </p>
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
                  <div key={record.id} className="flex items-center justify-between p-5 transition-colors hover:bg-muted/30">
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
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>
    </DashboardLayout>
  )
}

