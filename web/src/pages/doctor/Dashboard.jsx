import { useState, useEffect, useMemo } from 'react'
import { Link } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import { Calendar as CalendarIcon, Clock, Users, Star, TrendingUp, ChevronLeft, ChevronRight, AlertCircle, X } from 'lucide-react'
import { getMyAppointments } from '../../api/appointmentApi'
import { getRatingsByDoctor } from '../../api/ratingApi'
import { useAuth } from '../../hooks/useAuth'

const avatarColors = ['bg-primary', 'bg-teal', 'bg-accent', 'bg-primary', 'bg-teal', 'bg-accent']
const weekDays = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa']

function toDayKey(value) {
  return new Date(value).toDateString()
}

function buildDeltaText(current, previous, unit) {
  const diff = current - previous
  if (diff === 0) return `No change vs yesterday`
  if (previous === 0 && current > 0) return `${current} ${unit} today`
  const direction = diff > 0 ? 'more' : 'less'
  return `${Math.abs(diff)} ${unit} ${direction} than yesterday`
}

function createCalendarDays(baseMonth) {
  const year = baseMonth.getFullYear()
  const month = baseMonth.getMonth()
  const firstOfMonth = new Date(year, month, 1)
  const firstWeekday = firstOfMonth.getDay()
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const prevMonthDays = new Date(year, month, 0).getDate()
  const items = []

  for (let i = firstWeekday - 1; i >= 0; i -= 1) {
    items.push({
      date: new Date(year, month - 1, prevMonthDays - i),
      inCurrentMonth: false,
    })
  }

  for (let day = 1; day <= daysInMonth; day += 1) {
    items.push({
      date: new Date(year, month, day),
      inCurrentMonth: true,
    })
  }

  let nextDay = 1
  while (items.length < 35) {
    items.push({
      date: new Date(year, month + 1, nextDay),
      inCurrentMonth: false,
    })
    nextDay += 1
  }

  return items
}

export default function DoctorDashboard() {
  const { user } = useAuth()
  const [appointments, setAppointments] = useState([])
  const [loading, setLoading] = useState(true)
  const [feedbackLoading, setFeedbackLoading] = useState(true)
  const [recentFeedback, setRecentFeedback] = useState([])
  const [showGoogleSpecializationReminder, setShowGoogleSpecializationReminder] = useState(false)
  const [selectedDate, setSelectedDate] = useState(new Date())
  const [displayMonth, setDisplayMonth] = useState(() => {
    const now = new Date()
    return new Date(now.getFullYear(), now.getMonth(), 1)
  })
  const [scheduleFilter, setScheduleFilter] = useState('upcoming')

  useEffect(() => {
    getMyAppointments()
      .then((data) => setAppointments(Array.isArray(data) ? data : []))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    if (!user?.profileId) {
      setFeedbackLoading(false)
      return
    }

    setFeedbackLoading(true)
    getRatingsByDoctor(user.profileId)
      .then((data) => {
        const items = Array.isArray(data) ? data : []
        const sorted = [...items].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
        setRecentFeedback(sorted.slice(0, 5))
      })
      .catch(() => setRecentFeedback([]))
      .finally(() => setFeedbackLoading(false))
  }, [user?.profileId])

  useEffect(() => {
    const lastAuthMethod = sessionStorage.getItem('medbuddy_last_auth_method')
    const hasSpecializations = Array.isArray(user?.specializations) && user.specializations.length > 0

    setShowGoogleSpecializationReminder(
      user?.role === 'DOCTOR' &&
      lastAuthMethod === 'GOOGLE' &&
      !hasSpecializations,
    )
  }, [user?.role, user?.specializations])

  const stats = useMemo(() => {
    const startOfToday = new Date()
    startOfToday.setHours(0, 0, 0, 0)

    const today = new Date()
    const yesterday = new Date()
    yesterday.setDate(today.getDate() - 1)

    const todayKey = today.toDateString()
    const yesterdayKey = yesterday.toDateString()
    const nonCancelled = appointments.filter((apt) => apt.status !== 'CANCELLED')

    // Get week bounds for "This Week" stat
    const dayOfWeek = startOfToday.getDay()
    const diffToMonday = dayOfWeek === 0 ? 6 : dayOfWeek - 1
    const weekStart = new Date(startOfToday)
    weekStart.setDate(startOfToday.getDate() - diffToMonday)
    const weekEnd = new Date(weekStart)
    weekEnd.setDate(weekStart.getDate() + 6)
    weekEnd.setHours(23, 59, 59, 999)

    // Upcoming Appointments: non-CANCELLED where dateTime >= startOfToday
    const upcomingAppointments = nonCancelled.filter((apt) => new Date(apt.dateTime) >= startOfToday)
    const upcomingCount = upcomingAppointments.length
    const earliestUpcoming = upcomingAppointments.length > 0
      ? upcomingAppointments.sort((a, b) => new Date(a.dateTime) - new Date(b.dateTime))[0]
      : null
    const upcomingTrend = earliestUpcoming
      ? `Next: ${new Date(earliestUpcoming.dateTime).toLocaleDateString()}`
      : 'None scheduled'

    // Pending Requests: all appointments with status === 'PENDING'
    const pendingTotal = appointments.filter((apt) => apt.status === 'PENDING').length
    const pendingToday = appointments.filter((apt) => apt.status === 'PENDING' && toDayKey(apt.dateTime) === todayKey).length
    const pendingYesterday = appointments.filter((apt) => apt.status === 'PENDING' && toDayKey(apt.dateTime) === yesterdayKey).length

    // This Week: non-CANCELLED appointments within Mon-Sun of current week
    const thisWeekCount = nonCancelled.filter((apt) => {
      const aptDate = new Date(apt.dateTime)
      return aptDate >= weekStart && aptDate <= weekEnd
    }).length
    const thisWeekTrend = `${thisWeekCount} appointment${thisWeekCount !== 1 ? 's' : ''} this week`

    // Completion Rate
    const completedTotal = appointments.filter((apt) => apt.status === 'COMPLETED').length
    const completionRate = nonCancelled.length > 0 ? Math.round((completedTotal / nonCancelled.length) * 100) : 0

    return [
      {
        label: 'Upcoming Appointments',
        value: upcomingCount,
        icon: Users,
        color: 'bg-primary-soft text-primary',
        trend: upcomingTrend,
      },
      {
        label: 'Pending Requests',
        value: pendingTotal,
        icon: Clock,
        color: 'bg-teal-soft text-teal',
        trend: buildDeltaText(pendingToday, pendingYesterday, 'requests'),
      },
      {
        label: 'This Week',
        value: thisWeekCount,
        icon: TrendingUp,
        color: 'bg-accent-soft text-accent',
        trend: thisWeekTrend,
      },
      {
        label: 'Completion Rate',
        value: `${completionRate}%`,
        icon: CalendarIcon,
        color: 'bg-secondary text-secondary-foreground',
        trend: `${completedTotal} completed of ${nonCancelled.length} active`,
      },
    ]
  }, [appointments])

  const scheduleList = useMemo(() => {
    const startOfToday = new Date()
    startOfToday.setHours(0, 0, 0, 0)

    // Get week bounds
    const dayOfWeek = startOfToday.getDay()
    const diffToMonday = dayOfWeek === 0 ? 6 : dayOfWeek - 1
    const weekStart = new Date(startOfToday)
    weekStart.setDate(startOfToday.getDate() - diffToMonday)
    const weekEnd = new Date(weekStart)
    weekEnd.setDate(weekStart.getDate() + 6)
    weekEnd.setHours(23, 59, 59, 999)

    const todayStr = startOfToday.toDateString()

    let filtered = appointments.filter((apt) => apt.status !== 'CANCELLED')

    if (scheduleFilter === 'upcoming') {
      filtered = filtered.filter((apt) => new Date(apt.dateTime) >= startOfToday)
    } else if (scheduleFilter === 'today') {
      filtered = filtered.filter((apt) => toDayKey(apt.dateTime) === todayStr)
    } else if (scheduleFilter === 'week') {
      filtered = filtered.filter((apt) => {
        const aptDate = new Date(apt.dateTime)
        return aptDate >= weekStart && aptDate <= weekEnd
      })
    }

    return filtered.sort((a, b) => new Date(a.dateTime) - new Date(b.dateTime))
  }, [appointments, scheduleFilter])

  const calendarDays = useMemo(() => createCalendarDays(displayMonth), [displayMonth])

  return (
    <DashboardLayout>
      <div className="space-y-6">
        {showGoogleSpecializationReminder && (
          <div className="flex items-start justify-between gap-3 rounded-xl border border-accent/30 bg-accent-soft p-4">
            <div className="flex items-start gap-2">
              <AlertCircle className="mt-0.5 h-5 w-5 text-accent" />
              <div>
                <p className="text-sm font-semibold text-foreground">Welcome to MedBuddy.</p>
                <p className="text-sm text-muted-foreground font-body">
                  You signed in with Google. Please add your specialization(s) to complete your doctor profile.
                </p>
                <Link to="/doctor/settings" className="mt-2 inline-block text-sm font-medium text-primary hover:underline">
                  Add specialization now
                </Link>
              </div>
            </div>
            <button
              type="button"
              aria-label="Dismiss reminder"
              onClick={() => setShowGoogleSpecializationReminder(false)}
              className="rounded-md p-1 text-muted-foreground hover:bg-muted"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        )}

        {/* Welcome */}
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold md:text-3xl">
              Hello, Dr. {user?.lastName || 'Doctor'}!
            </h1>
            <p className="mt-1 text-muted-foreground font-body">
              You have {scheduleList.length} appointments in your schedule.
            </p>
          </div>
          <Link
            to="/doctor/schedule"
            className="inline-flex h-10 items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground shadow-md hover:bg-primary/90"
          >
            Manage Schedule
          </Link>
        </div>

        {/* Stats */}
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {stats.map((stat) => (
            <div
              key={stat.label}
              className="group rounded-2xl border border-border bg-card p-5 shadow-card transition-all hover:shadow-elevated hover:-translate-y-0.5"
            >
              <div className={`mb-3 inline-flex rounded-xl p-3 ${stat.color}`}>
                <stat.icon className="h-5 w-5" />
              </div>
              <p className="text-sm text-muted-foreground font-body">{stat.label}</p>
              <p className="mt-1 text-3xl font-bold">{stat.value}</p>
              <p className="mt-1 text-xs text-muted-foreground">{stat.trend}</p>
            </div>
          ))}
        </div>

        <div className="grid gap-6 lg:grid-cols-3">
          {/* Upcoming Appointments */}
          <div className="lg:col-span-2 rounded-2xl border border-border bg-card shadow-card">
            <div className="flex items-center justify-between border-b border-border p-5">
              <h2 className="text-lg font-semibold">Upcoming Appointments</h2>
              <Link to="/doctor/appointments" className="text-sm font-medium text-primary hover:underline">
                View All
              </Link>
            </div>
            <div className="flex gap-2 p-4 border-b border-border">
              <button
                type="button"
                onClick={() => setScheduleFilter('upcoming')}
                className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
                  scheduleFilter === 'upcoming'
                    ? 'bg-primary text-primary-foreground'
                    : 'border border-border text-muted-foreground hover:bg-muted'
                }`}
              >
                All Upcoming
              </button>
              <button
                type="button"
                onClick={() => setScheduleFilter('today')}
                className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
                  scheduleFilter === 'today'
                    ? 'bg-primary text-primary-foreground'
                    : 'border border-border text-muted-foreground hover:bg-muted'
                }`}
              >
                Today
              </button>
              <button
                type="button"
                onClick={() => setScheduleFilter('week')}
                className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
                  scheduleFilter === 'week'
                    ? 'bg-primary text-primary-foreground'
                    : 'border border-border text-muted-foreground hover:bg-muted'
                }`}
              >
                This Week
              </button>
            </div>
            {loading ? (
              <div className="p-5 text-sm text-muted-foreground">Loading schedule...</div>
            ) : scheduleList.length === 0 ? (
              <div className="p-5 text-sm text-muted-foreground">No appointments available.</div>
            ) : (
              <div className="divide-y divide-border">
                {scheduleList.map((apt, i) => {
                  const patientName = [apt.patient?.firstName, apt.patient?.lastName].filter(Boolean).join(' ') || apt.patient?.email || 'Patient'
                  return (
                    <div key={apt.id} className="flex items-center justify-between p-5 hover:bg-muted/30 transition-colors">
                      <div className="flex items-center gap-4">
                        <div className={`flex h-11 w-11 items-center justify-center rounded-full text-sm font-bold text-primary-foreground ${avatarColors[i % avatarColors.length]}`}>
                          {patientName.split(' ').map((n) => n[0]).join('')}
                        </div>
                        <div>
                          <p className="font-semibold">{patientName}</p>
                          <p className="text-sm text-muted-foreground font-body">
                            <CalendarIcon className="inline h-3 w-3 mr-1" />{new Date(apt.dateTime).toLocaleDateString()} • <Clock className="inline h-3 w-3 mr-1" />{new Date(apt.dateTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                          </p>
                        </div>
                      </div>
                      <div className="flex items-center gap-4">
                        <p className={`text-xs font-medium ${
                          apt.status === 'CONFIRMED' ? 'text-primary' :
                          apt.status === 'PENDING' ? 'text-accent' : 'text-muted-foreground'
                        }`}>{apt.status}</p>
                        <Link to="/doctor/appointments" className="text-xs font-medium text-primary hover:underline">
                          View
                        </Link>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>

          {/* Calendar */}
          <div className="rounded-2xl border border-border bg-card shadow-card">
            <div className="border-b border-border p-5">
              <h2 className="text-lg font-semibold">Calendar</h2>
            </div>
            <div className="p-5">
              <div className="mb-4 flex items-center justify-between">
                <button
                  type="button"
                  aria-label="Previous month"
                  onClick={() => setDisplayMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() - 1, 1))}
                  className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-border text-muted-foreground hover:bg-muted"
                >
                  <ChevronLeft className="h-4 w-4" />
                </button>
                <p className="text-lg font-medium">
                  {displayMonth.toLocaleString('en-US', { month: 'long', year: 'numeric' })}
                </p>
                <button
                  type="button"
                  aria-label="Next month"
                  onClick={() => setDisplayMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() + 1, 1))}
                  className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-border text-muted-foreground hover:bg-muted"
                >
                  <ChevronRight className="h-4 w-4" />
                </button>
              </div>

              <div className="mb-2 grid grid-cols-7 text-center text-sm text-muted-foreground">
                {weekDays.map((day) => (
                  <span key={day} className="py-1">{day}</span>
                ))}
              </div>

              <div className="grid grid-cols-7 gap-y-1 text-center text-base">
                {calendarDays.map((item) => {
                  const isSelected = toDayKey(item.date) === toDayKey(selectedDate)
                  return (
                    <button
                      key={item.date.toISOString()}
                      type="button"
                      onClick={() => setSelectedDate(item.date)}
                      className={`mx-auto h-10 w-10 rounded-md transition-colors ${
                        isSelected
                          ? 'bg-primary text-primary-foreground'
                          : item.inCurrentMonth
                            ? 'text-foreground hover:bg-muted'
                            : 'text-muted-foreground/50 hover:bg-muted'
                      }`}
                    >
                      {item.date.getDate()}
                    </button>
                  )
                })}
              </div>
            </div>

            <div className="border-t border-border p-4 space-y-2">
              {useMemo(() => {
                const selectedDateStr = toDayKey(selectedDate)
                const aptsOnDate = appointments
                  .filter((apt) => apt.status !== 'CANCELLED' && toDayKey(apt.dateTime) === selectedDateStr)
                  .sort((a, b) => new Date(a.dateTime) - new Date(b.dateTime))
                
                if (aptsOnDate.length === 0) {
                  return <p className="text-xs text-muted-foreground px-3 py-2">No appointments on this date</p>
                }

                return (
                  <div className="space-y-2 px-3 py-2">
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-xs font-medium text-muted-foreground">{aptsOnDate.length} appointment(s)</span>
                      <Link to="/doctor/appointments" className="text-xs font-medium text-primary hover:underline">
                        View All
                      </Link>
                    </div>
                    {aptsOnDate.slice(0, 3).map((apt) => {
                      const patientName = [apt.patient?.firstName, apt.patient?.lastName].filter(Boolean).join(' ') || 'Patient'
                      return (
                        <div key={apt.id} className="rounded-lg border border-border/50 bg-muted/30 p-2 text-xs">
                          <p className="font-medium">{patientName}</p>
                          <p className="text-muted-foreground">{new Date(apt.dateTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</p>
                          <span className={`mt-1 inline-block text-xs px-1.5 py-0.5 rounded ${
                            apt.status === 'PENDING' ? 'bg-accent/20 text-accent' : 'bg-primary/20 text-primary'
                          }`}>{apt.status}</span>
                        </div>
                      )
                    })}
                  </div>
                )
              }, [selectedDate, appointments])}
              <Link to="/doctor/patient-records" className="flex items-center justify-between rounded-lg px-3 py-2 text-sm font-medium hover:bg-muted transition-colors">
                <span className="flex items-center gap-2"><Users className="h-4 w-4 text-primary" /> Patient Records</span>
                <ChevronRight className="h-4 w-4 text-muted-foreground" />
              </Link>
              <Link to="/doctor/settings" className="flex items-center justify-between rounded-lg px-3 py-2 text-sm font-medium hover:bg-muted transition-colors">
                <span className="flex items-center gap-2"><Star className="h-4 w-4 text-accent" /> Profile & Settings</span>
                <ChevronRight className="h-4 w-4 text-muted-foreground" />
              </Link>
            </div>
          </div>
        </div>

        {/* Recent Feedback */}
        <div className="rounded-2xl border border-border bg-card shadow-card">
          <div className="border-b border-border p-5">
            <h2 className="text-lg font-semibold">Recent Patient Feedback</h2>
          </div>
          <div className="divide-y divide-border">
            {feedbackLoading && <p className="p-5 text-sm text-muted-foreground">Loading feedback...</p>}
            {!feedbackLoading && recentFeedback.length === 0 && (
              <p className="p-5 text-sm text-muted-foreground">No patient feedback yet.</p>
            )}
            {!feedbackLoading && recentFeedback.map((fb) => {
              const patientName = [fb.patient?.firstName, fb.patient?.lastName].filter(Boolean).join(' ') || fb.patient?.email || 'Patient'
              return (
                <div key={fb.id} className="p-5 hover:bg-muted/30 transition-colors">
                  <div className="flex items-center justify-between">
                    <p className="font-medium">{patientName}</p>
                    <span className="text-xs text-muted-foreground">{new Date(fb.createdAt).toLocaleDateString()}</span>
                  </div>
                  <div className="mt-1 flex gap-0.5">
                    {[1, 2, 3, 4, 5].map((s) => (
                      <Star key={s} className={`h-3.5 w-3.5 ${s <= (fb.ratingScore || 0) ? 'fill-accent text-accent' : 'text-muted-foreground/30'}`} />
                    ))}
                  </div>
                  <p className="mt-2 text-sm text-muted-foreground font-body">{fb.feedbackComment || 'No written feedback.'}</p>
                </div>
              )
            })}
          </div>
        </div>
      </div>
    </DashboardLayout>
  )
}

