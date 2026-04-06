import { useEffect, useMemo, useState } from 'react'
import { Calendar, CheckCircle, Clock, Search, XCircle } from 'lucide-react'
import DashboardLayout from '../../components/DashboardLayout'
import { getMyAppointments, updateAppointmentStatus } from '../../api/appointmentApi'
import { useToast } from '../../hooks/useToast'

const statuses = ['PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED']
const avatarColors = ['bg-primary', 'bg-teal', 'bg-accent', 'bg-primary', 'bg-teal', 'bg-accent']

const statusBadgeClass = {
  PENDING: 'bg-accent/10 text-accent',
  CONFIRMED: 'bg-primary/10 text-primary',
  COMPLETED: 'bg-teal/10 text-teal',
  CANCELLED: 'bg-destructive/10 text-destructive',
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

export default function DoctorAppointments() {
  const { success } = useToast()
  const [appointments, setAppointments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')

  useEffect(() => {
    async function load() {
      try {
        const data = await getMyAppointments()
        setAppointments(Array.isArray(data) ? data : [])
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

  async function changeStatus(id, status) {
    setError('')

    try {
      await updateAppointmentStatus(id, status)
      setAppointments((prev) => prev.map((apt) => (apt.id === id ? { ...apt, status } : apt)))
      success(`Appointment ${status.toLowerCase()} successfully.`)
    } catch {
      setError('Unable to update status. Please try again.')
    }
  }

  const summaryItems = [
    { label: 'Pending', value: counts.PENDING || 0, tone: 'bg-accent/10 text-accent' },
    { label: 'Confirmed', value: counts.CONFIRMED || 0, tone: 'bg-primary/10 text-primary' },
    { label: 'Completed', value: counts.COMPLETED || 0, tone: 'bg-teal/10 text-teal' },
    { label: 'Cancelled', value: counts.CANCELLED || 0, tone: 'bg-muted text-muted-foreground' },
  ]

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold md:text-3xl">Appointment Management</h1>
            <p className="mt-1 text-muted-foreground font-body">View and manage patient appointments</p>
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
              <option key={status} value={status}>{status}</option>
            ))}
          </select>
        </div>

        {error && <p className="rounded-md border border-destructive/20 bg-destructive/10 p-3 text-sm text-destructive">{error}</p>}

        <div className="space-y-3">
          {loading ? (
            <p className="py-8 text-center text-sm text-muted-foreground">Loading appointments...</p>
          ) : filtered.length === 0 ? (
            <div className="py-12 text-center text-muted-foreground">
              <p className="font-medium">No appointments found</p>
            </div>
          ) : (
            filtered.map((apt, i) => {
              const patientName = getPatientName(apt.patient)
              const formattedDateTime = toDateTime(apt.dateTime)

              return (
                <div key={apt.id} className="rounded-2xl border border-border bg-card shadow-card transition-all hover:-translate-y-0.5 hover:shadow-elevated">
                  <div className="p-5">
                    <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                      <div className="flex items-center gap-4">
                        <div className={`flex h-11 w-11 items-center justify-center rounded-full text-sm font-bold text-primary-foreground ${avatarColors[i % avatarColors.length]}`}>
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
                        <span className={`rounded-full px-3 py-1 text-xs font-medium ${statusBadgeClass[apt.status]}`}>{apt.status}</span>

                        {apt.status === 'PENDING' && (
                          <>
                            <button
                              type="button"
                              onClick={() => changeStatus(apt.id, 'CONFIRMED')}
                              className="inline-flex items-center gap-1 rounded-md bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
                            >
                              <CheckCircle className="h-3.5 w-3.5" />
                              Confirm
                            </button>
                            <button
                              type="button"
                              onClick={() => changeStatus(apt.id, 'CANCELLED')}
                              className="inline-flex items-center gap-1 rounded-md bg-destructive px-3 py-1.5 text-xs font-semibold text-destructive-foreground transition-colors hover:bg-destructive/90"
                            >
                              <XCircle className="h-3.5 w-3.5" />
                              Cancel
                            </button>
                          </>
                        )}

                        {apt.status === 'CONFIRMED' && (
                          <button
                            type="button"
                            onClick={() => changeStatus(apt.id, 'COMPLETED')}
                            className="inline-flex items-center gap-1 rounded-md bg-teal px-3 py-1.5 text-xs font-semibold text-teal-foreground transition-colors hover:bg-teal/90"
                          >
                            <CheckCircle className="h-3.5 w-3.5" />
                            Complete
                          </button>
                        )}
                      </div>
                    </div>

                    {apt.notes && (
                      <div className="mt-3 rounded-xl border border-border/50 bg-muted/50 px-4 py-3 text-sm text-muted-foreground font-body">
                        <span className="font-medium text-foreground">Patient Notes:</span> {apt.notes}
                      </div>
                    )}
                  </div>
                </div>
              )
            })
          )}
        </div>
      </div>
    </DashboardLayout>
  )
}

