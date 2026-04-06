import { useCallback, useEffect, useMemo, useState } from 'react'
import { CalendarOff, CheckCircle, Clock, Plus, Save, X } from 'lucide-react'
import { useBeforeUnload } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import {
  deleteMyScheduleException,
  getDoctorAvailability,
  getMyScheduleTemplate,
  saveMyScheduleException,
  saveMyScheduleTemplate,
} from '../../api/availabilityApi'
import { useAuth } from '../../hooks/useAuth'
import { useToast } from '../../hooks/useToast'

const daysOfWeek = [
  { day: 'Monday', abbr: 'Mon', templateIndex: 0 },
  { day: 'Tuesday', abbr: 'Tue', templateIndex: 1 },
  { day: 'Wednesday', abbr: 'Wed', templateIndex: 2 },
  { day: 'Thursday', abbr: 'Thu', templateIndex: 3 },
  { day: 'Friday', abbr: 'Fri', templateIndex: 4 },
  { day: 'Saturday', abbr: 'Sat', templateIndex: 5 },
  { day: 'Sunday', abbr: 'Sun', templateIndex: 6 },
]

const initialSchedule = daysOfWeek.map((item) => ({
  day: item.day,
  abbr: item.abbr,
  templateIndex: item.templateIndex,
  enabled: !['Saturday', 'Sunday'].includes(item.day),
  startTime: '09:00',
  endTime: '17:00',
  slotDuration: 30,
}))

function normalizeTime(value, fallback) {
  return String(value || fallback).slice(0, 5)
}

function toIsoDate(date) {
  return date.toISOString().slice(0, 10)
}

function todayIso() {
  return toIsoDate(new Date())
}

function minutesBetween(start, end) {
  const [startH, startM] = String(start || '').split(':').map(Number)
  const [endH, endM] = String(end || '').split(':').map(Number)
  return endH * 60 + endM - (startH * 60 + startM)
}

function normalizeTemplateDays(data) {
  const templates = Array.isArray(data) ? data : []
  const byDay = new Map(templates.map((item) => [item.dayOfWeek, item]))

  return initialSchedule.map((day) => {
    const saved = byDay.get(day.templateIndex)
    if (!saved) {
      return { ...day, enabled: false }
    }

    return {
      ...day,
      enabled: true,
      startTime: normalizeTime(saved.startTime, day.startTime),
      endTime: normalizeTime(saved.endTime, day.endTime),
    }
  })
}

function isExceptionStatus(value) {
  return value === 'AVAILABLE' || value === 'UNAVAILABLE'
}

function normalizeExceptions(data) {
  return (Array.isArray(data) ? data : [])
    .filter((item) => item?.availableDate && isExceptionStatus(item.status))
    .map((item) => ({
      date: item.availableDate,
      status: item.status,
      startTime: normalizeTime(item.startTime, '09:00'),
      endTime: normalizeTime(item.endTime, '17:00'),
    }))
    .sort((left, right) => left.date.localeCompare(right.date))
}

export default function DoctorSchedule() {
  const { user } = useAuth()
  const { success, error: showError, showToast } = useToast()
  const [schedule, setSchedule] = useState(initialSchedule)
  const [exceptions, setExceptions] = useState([])
  const [savedExceptionDates, setSavedExceptionDates] = useState(new Set())
  const [savedExceptionsByDate, setSavedExceptionsByDate] = useState({})
  const [newException, setNewException] = useState({
    date: '',
    status: 'UNAVAILABLE',
    startTime: '09:00',
    endTime: '17:00',
  })
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false)

  const handleBeforeUnload = useCallback((event) => {
    if (hasUnsavedChanges) {
      event.preventDefault()
      event.returnValue = ''
    }
  }, [hasUnsavedChanges])

  useBeforeUnload(handleBeforeUnload)

  useEffect(() => {
    if (!hasUnsavedChanges) return undefined

    const confirmLeave = () => window.confirm("if you leave, your changes won't be saved")

    const onDocumentClick = (event) => {
      const anchor = event.target.closest('a[href]')
      if (!anchor) return

      if (anchor.target === '_blank' || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
        return
      }

      const href = anchor.getAttribute('href')
      if (!href || href.startsWith('#')) return

      const destination = new URL(href, window.location.origin)
      const current = new URL(window.location.href)

      const isSameRoute =
        destination.pathname === current.pathname &&
        destination.search === current.search &&
        destination.hash === current.hash

      if (isSameRoute) return

      if (!confirmLeave()) {
        event.preventDefault()
        event.stopPropagation()
      }
    }

    const onPopState = () => {
      if (confirmLeave()) return
      window.history.pushState(null, '', window.location.href)
    }

    window.history.pushState(null, '', window.location.href)
    document.addEventListener('click', onDocumentClick, true)
    window.addEventListener('popstate', onPopState)

    return () => {
      document.removeEventListener('click', onDocumentClick, true)
      window.removeEventListener('popstate', onPopState)
    }
  }, [hasUnsavedChanges])

  async function loadScheduleData() {
    if (!user?.profileId) {
      setLoading(false)
      setError('Doctor profile not available.')
      return
    }

    setLoading(true)
    setError('')

    try {
      const [templateData, availabilityData] = await Promise.all([
        getMyScheduleTemplate(),
        getDoctorAvailability(user.profileId),
      ])

      const normalizedTemplates = normalizeTemplateDays(templateData)
      const normalizedExceptions = normalizeExceptions(availabilityData)

      setSchedule(normalizedTemplates)
      setExceptions(normalizedExceptions)
      setSavedExceptionDates(new Set(normalizedExceptions.map((item) => item.date)))
      setSavedExceptionsByDate(
        Object.fromEntries(normalizedExceptions.map((item) => [item.date, item]))
      )

      setHasUnsavedChanges(false)
    } catch {
      setError('Failed to load schedule settings.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadScheduleData()
  }, [user?.profileId])

  const activeDays = useMemo(
    () => schedule.filter((item) => item.enabled).length,
    [schedule],
  )

  const avgHours = useMemo(() => {
    const enabled = schedule.filter((item) => item.enabled)
    if (enabled.length === 0) return 0
    const totalMinutes = enabled.reduce((sum, item) => {
      const duration = minutesBetween(item.startTime, item.endTime)
      return duration > 0 ? sum + duration : sum
    }, 0)
    return Math.round(totalMinutes / enabled.length / 60)
  }, [schedule])

  function toggleDay(index) {
    setHasUnsavedChanges(true)
    setSchedule((prev) => prev.map((item, i) => (i === index ? { ...item, enabled: !item.enabled } : item)))
  }

  function updateTime(index, field, value) {
    setHasUnsavedChanges(true)
    setSchedule((prev) => prev.map((item, i) => (i === index ? { ...item, [field]: value } : item)))
  }

  function updateExceptionField(field, value) {
    setHasUnsavedChanges(true)
    setNewException((prev) => ({ ...prev, [field]: value }))
  }

  function upsertExceptionToList(exception) {
    setExceptions((prev) => {
      const withoutDate = prev.filter((item) => item.date !== exception.date)
      return [...withoutDate, exception].sort((left, right) => left.date.localeCompare(right.date))
    })
  }

  function addException() {
    if (!newException.date) {
      setError('Please choose a date for the exception.')
      return
    }

    if (newException.date < todayIso()) {
      setError('You cannot add an exception in the past.')
      return
    }

    if (newException.status === 'AVAILABLE' && newException.endTime <= newException.startTime) {
      setError('Exception end time must be after start time.')
      return
    }

    setError('')
    setHasUnsavedChanges(true)

    upsertExceptionToList({
      date: newException.date,
      status: newException.status,
      startTime: newException.startTime,
      endTime: newException.endTime,
    })

    setNewException({
      date: '',
      status: 'UNAVAILABLE',
      startTime: '09:00',
      endTime: '17:00',
    })
  }

  function removeException(date) {
    setHasUnsavedChanges(true)
    setExceptions((prev) => prev.filter((item) => item.date !== date))
  }

  async function handleSave() {
    setError('')

    for (const day of schedule) {
      if (day.enabled && day.endTime <= day.startTime) {
        setError(`End time must be after start time for ${day.day}.`)
        return
      }
    }

    setSaving(true)
    showToast({ message: 'Saving schedule changes...', type: 'info', durationMs: 1800 })

    try {
      const activeTemplateDays = schedule
        .filter((day) => day.enabled)
        .map((day) => ({
          dayOfWeek: day.templateIndex,
          startTime: day.startTime,
          endTime: day.endTime,
        }))

      await saveMyScheduleTemplate(activeTemplateDays)

      const currentDates = new Set(exceptions.map((item) => item.date))
      const removedDates = [...savedExceptionDates].filter((date) => !currentDates.has(date))
      const changedOrNewExceptions = exceptions.filter((exception) => {
        const previous = savedExceptionsByDate[exception.date]
        if (!previous) return true

        return (
          previous.status !== exception.status ||
          previous.startTime !== exception.startTime ||
          previous.endTime !== exception.endTime
        )
      })

      for (const removedDate of removedDates) {
        await deleteMyScheduleException(removedDate)
      }

      for (const exception of changedOrNewExceptions) {
        const isAvailable = exception.status === 'AVAILABLE'
        await saveMyScheduleException({
          availableDate: exception.date,
          startTime: isAvailable ? exception.startTime : '00:00',
          endTime: isAvailable ? exception.endTime : '00:30',
          status: exception.status,
        })
      }

      success('Saved. Slot regeneration is running in the background.')
      setHasUnsavedChanges(false)
      loadScheduleData()
    } catch (err) {
      const message = err.response?.data?.detail || err.response?.data?.message || 'Failed to save schedule settings.'
      setError(message)
      showError(message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold md:text-3xl">Schedule Management</h1>
            <p className="mt-1 text-muted-foreground font-body">
              Set your weekly availability and time slots
            </p>
          </div>
          <button
            type="button"
            onClick={handleSave}
            disabled={saving || loading}
            className="inline-flex h-10 items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground shadow-md hover:bg-primary/90 disabled:opacity-60"
          >
            <Save className="mr-2 h-4 w-4" /> {saving ? 'Saving...' : 'Save Changes'}
          </button>
        </div>

        <div className="grid gap-3 sm:grid-cols-3">
          <div className="rounded-xl border border-border bg-card p-4 shadow-card">
            <div className="mb-2 inline-flex rounded-lg bg-primary-soft p-2">
              <CheckCircle className="h-4 w-4 text-primary" />
            </div>
            <p className="text-sm text-muted-foreground font-body">Active Days</p>
            <p className="text-3xl font-bold">{activeDays} / 7</p>
          </div>
          <div className="rounded-xl border border-border bg-card p-4 shadow-card">
            <div className="mb-2 inline-flex rounded-lg bg-teal-soft p-2">
              <Clock className="h-4 w-4 text-teal" />
            </div>
            <p className="text-sm text-muted-foreground font-body">Avg. Hours/Day</p>
            <p className="text-3xl font-bold">{avgHours}h</p>
          </div>
          <div className="rounded-xl border border-border bg-card p-4 shadow-card">
            <div className="mb-2 inline-flex rounded-lg bg-accent-soft p-2">
              <CalendarOff className="h-4 w-4 text-accent" />
            </div>
            <p className="text-sm text-muted-foreground font-body">Date Exceptions</p>
            <p className="text-3xl font-bold">{exceptions.length}</p>
          </div>
        </div>

        {error && <p className="rounded-md border border-destructive/20 bg-destructive/10 p-3 text-sm text-destructive">{error}</p>}

        <div className="rounded-2xl border border-border bg-card shadow-card">
          <div className="border-b border-border p-5">
            <h2 className="text-lg font-semibold">Recurring Weekly Template (Mon-Sun)</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Set your recurring clinic hours once. Slot generation runs asynchronously after save.
            </p>
          </div>
          {loading ? (
            <div className="p-5 text-sm text-muted-foreground">Loading schedule...</div>
          ) : (
            <div className="divide-y divide-border">
              {schedule.map((day, index) => (
                <div key={day.day} className={`flex flex-wrap items-center gap-4 p-4 sm:p-5 ${day.enabled ? '' : 'bg-muted/25'}`}>
                  <button
                    type="button"
                    onClick={() => toggleDay(index)}
                    className={`relative inline-flex h-7 w-12 shrink-0 items-center rounded-full transition-colors ${day.enabled ? 'bg-primary' : 'bg-muted'}`}
                    aria-label={`Toggle ${day.day}`}
                  >
                    <span className={`inline-block h-5 w-5 transform rounded-full bg-white transition-transform ${day.enabled ? 'translate-x-6' : 'translate-x-1'}`} />
                  </button>

                  <div className="w-20">
                    <p className={`text-sm font-medium ${day.enabled ? 'text-foreground' : 'text-muted-foreground'}`}>{day.day}</p>
                    <p className="text-xs text-muted-foreground">{day.abbr}</p>
                  </div>

                  {day.enabled ? (
                    <div className="flex flex-wrap items-center gap-2">
                      <input
                        type="time"
                        value={day.startTime}
                        onChange={(event) => updateTime(index, 'startTime', event.target.value)}
                        className="flex h-10 rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 w-32"
                      />
                      <span className="text-sm text-muted-foreground">to</span>
                      <input
                        type="time"
                        value={day.endTime}
                        onChange={(event) => updateTime(index, 'endTime', event.target.value)}
                        className="flex h-10 rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 w-32"
                      />
                      <span className="ml-2 inline-flex rounded-full bg-primary-soft px-3 py-1 text-xs font-medium text-primary">
                        {day.slotDuration}min slots
                      </span>
                    </div>
                  ) : (
                    <span className="inline-flex rounded-full bg-muted px-3 py-1 text-xs font-medium text-muted-foreground">
                      Unavailable
                    </span>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="rounded-2xl border border-border bg-card shadow-card">
          <div className="border-b border-border p-5">
            <h2 className="flex items-center gap-2 text-lg font-semibold">
              <CalendarOff className="h-5 w-5 text-accent" /> Exceptions Calendar
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Override a specific date as day off or custom hours.
            </p>
          </div>
          <div className="space-y-4 p-5">
            <div className="flex flex-wrap gap-2">
              <input
                type="date"
                value={newException.date}
                onChange={(event) => updateExceptionField('date', event.target.value)}
                min={todayIso()}
                className="h-10 w-48 rounded-xl border border-input bg-muted/40 px-3 text-sm"
              />
              <select
                value={newException.status}
                onChange={(event) => updateExceptionField('status', event.target.value)}
                className="h-10 rounded-xl border border-input bg-muted/40 px-3 text-sm"
              >
                <option value="UNAVAILABLE">Day Off</option>
                <option value="AVAILABLE">Custom Hours</option>
              </select>
              {newException.status === 'AVAILABLE' && (
                <>
                  <input
                    type="time"
                    value={newException.startTime}
                    onChange={(event) => updateExceptionField('startTime', event.target.value)}
                    className="h-10 w-32 rounded-xl border border-input bg-muted/40 px-3 text-sm"
                  />
                  <input
                    type="time"
                    value={newException.endTime}
                    onChange={(event) => updateExceptionField('endTime', event.target.value)}
                    className="h-10 w-32 rounded-xl border border-input bg-muted/40 px-3 text-sm"
                  />
                </>
              )}
              <button
                type="button"
                onClick={addException}
                className="inline-flex h-10 items-center justify-center rounded-xl border border-border bg-card px-4 text-sm font-medium hover:bg-muted"
              >
                <Plus className="mr-1 h-4 w-4" /> Add Exception
              </button>
            </div>

            <div className="flex flex-wrap gap-2">
              {exceptions.map((value) => (
                <span key={value.date} className="inline-flex items-center gap-1.5 rounded-full border border-accent/20 bg-accent-soft px-3 py-1.5 text-sm font-medium text-accent">
                  {value.date} • {value.status === 'UNAVAILABLE' ? 'Day Off' : `${value.startTime}-${value.endTime}`}
                  <button
                    type="button"
                    onClick={() => removeException(value.date)}
                    className="rounded-full p-0.5 transition-colors hover:bg-accent/20"
                    aria-label={`Remove ${value.date}`}
                  >
                    <X className="h-3 w-3" />
                  </button>
                </span>
              ))}
              {exceptions.length === 0 && <p className="text-sm text-muted-foreground">No exceptions added</p>}
            </div>
          </div>
        </div>
      </div>
    </DashboardLayout>
  )
}
