import { useState, useEffect, useMemo } from 'react'
import { Link } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import { Calendar as CalendarIcon, Clock, Users, TrendingUp, ChevronLeft, ChevronRight } from 'lucide-react'
import { getMyAppointments } from '../../api/appointmentApi'
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
  const [selectedDate, setSelectedDate] = useState(new Date())
  const [displayMonth, setDisplayMonth] = useState(() => {
    const now = new Date()
    return new Date(now.getFullYear(), now.getMonth(), 1)
  })

  useEffect(() => {
    getMyAppointments()
      .then((data) => setAppointments(Array.isArray(data) ? data : []))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    setSelectedDate(new Date())
  }, [user?.profileId])

  const stats = useMemo(() => {
    const today = new Date()
    const yesterday = new Date()
    yesterday.setDate(today.getDate() - 1)

    const todayKey = today.toDateString()
    const yesterdayKey = yesterday.toDateString()
    const nonCancelled = appointments.filter((apt) => apt.status !== 'CANCELLED')

    const todaysPatients = nonCancelled.filter((apt) => toDayKey(apt.dateTime) === todayKey).length
    const yesterdaysPatients = nonCancelled.filter((apt) => toDayKey(apt.dateTime) === yesterdayKey).length

    const pendingTotal = appointments.filter((apt) => apt.status === 'PENDING').length
    const pendingToday = appointments.filter((apt) => apt.status === 'PENDING' && toDayKey(apt.dateTime) === todayKey).length
    const pendingYesterday = appointments.filter((apt) => apt.status === 'PENDING' && toDayKey(apt.dateTime) === yesterdayKey).length

    const completedToday = appointments.filter((apt) => apt.status === 'COMPLETED' && toDayKey(apt.dateTime) === todayKey).length
    const completedYesterday = appointments.filter((apt) => apt.status === 'COMPLETED' && toDayKey(apt.dateTime) === yesterdayKey).length
    const completedTotal = appointments.filter((apt) => apt.status === 'COMPLETED').length
    const completionRate = nonCancelled.length > 0 ? Math.round((completedTotal / nonCancelled.length) * 100) : 0

    return [
      {
        label: "Today's Patients",
        value: todaysPatients,
        icon: Users,
        color: 'bg-primary-soft text-primary',
        trend: buildDeltaText(todaysPatients, yesterdaysPatients, 'patients'),
      },
      {
        label: 'Pending Requests',
        value: pendingTotal,
        icon: Clock,
        color: 'bg-teal-soft text-teal',
        trend: buildDeltaText(pendingToday, pendingYesterday, 'requests'),
      },
      {
        label: 'Completed Today',
        value: completedToday,
        icon: TrendingUp,
        color: 'bg-accent-soft text-accent',
        trend: buildDeltaText(completedToday, completedYesterday, 'completions'),
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

  const todaysSchedule = useMemo(
    () => appointments
      .filter((apt) => apt.status !== 'CANCELLED' && toDayKey(apt.dateTime) === new Date().toDateString())
      .sort((a, b) => new Date(a.dateTime) - new Date(b.dateTime))
      .slice(0, 5),
    [appointments],
  )

  const calendarDays = useMemo(() => createCalendarDays(displayMonth), [displayMonth])

  return (
    <DashboardLayout>
      <div className="space-y-6">
        {/* Welcome */}
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold md:text-3xl">
              Hello, Dr. {user?.lastName || 'Doctor'}!
            </h1>
            <p className="mt-1 text-muted-foreground font-body">
              You have {todaysSchedule.length} appointments scheduled for today.
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
          {/* Today's Schedule */}
          <div className="lg:col-span-2 rounded-2xl border border-border bg-card shadow-card">
            <div className="flex items-center justify-between border-b border-border p-5">
              <h2 className="text-lg font-semibold">Today's Schedule</h2>
              <Link to="/doctor/appointments" className="text-sm font-medium text-primary hover:underline">
                View All
              </Link>
            </div>
            {loading ? (
              <div className="p-5 text-sm text-muted-foreground">Loading schedule...</div>
            ) : todaysSchedule.length === 0 ? (
              <div className="p-5 text-sm text-muted-foreground">No appointments yet.</div>
            ) : (
              <div className="divide-y divide-border">
                {todaysSchedule.map((apt, i) => {
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
                      <div className="text-right">
                        <p className={`text-xs font-medium ${
                          apt.status === 'CONFIRMED' ? 'text-primary' :
                          apt.status === 'PENDING' ? 'text-accent' : 'text-muted-foreground'
                        }`}>{apt.status}</p>
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

            <div className="border-t border-border p-4">
              <Link to="/doctor/schedule" className="flex items-center justify-between rounded-lg px-3 py-2 text-sm font-medium hover:bg-muted transition-colors">
                <span className="flex items-center gap-2"><CalendarIcon className="h-4 w-4 text-primary" /> Schedule Settings</span>
                <ChevronRight className="h-4 w-4 text-muted-foreground" />
              </Link>
            </div>
          </div>
        </div>
      </div>
    </DashboardLayout>
  )
}

