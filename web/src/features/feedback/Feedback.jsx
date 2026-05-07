import { useEffect, useMemo, useState } from 'react'
import { MessageSquare, Send, Star, ThumbsUp, Trash2 } from 'lucide-react'
import DashboardLayout from '../../components/DashboardLayout'
import UserAvatar from '../user/UserAvatar'
import { getMyAppointments } from '../appointment/appointmentApi'
import { deleteRating, getRatingsByPatient, submitRating } from './ratingApi'
import { useAuth } from '../auth/useAuth'
import { useToast } from '../../hooks/useToast'

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
      .map((part) => part[0])
      .join('')
      .toUpperCase() || 'DR'
  )
}

export default function PatientFeedback() {
  const { user } = useAuth()
  const { success } = useToast()
  const [appointments, setAppointments] = useState([])
  const [ratings, setRatings] = useState([])
  const [selectedAppointmentId, setSelectedAppointmentId] = useState('')
  const [score, setScore] = useState(0)
  const [hoverScore, setHoverScore] = useState(0)
  const [comment, setComment] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [starFilter, setStarFilter] = useState('ALL')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')

  useEffect(() => {
    let mounted = true

    async function loadData() {
      try {
        const [appointmentData, ratingData] = await Promise.all([
          getMyAppointments(),
          user?.profileId ? getRatingsByPatient(user.profileId) : Promise.resolve([]),
        ])
        if (!mounted) return
        setAppointments(Array.isArray(appointmentData) ? appointmentData : [])
        setRatings(Array.isArray(ratingData) ? ratingData : [])
      } catch {
        if (mounted) setError('Failed to load feedback data.')
      } finally {
        if (mounted) setLoading(false)
      }
    }

    loadData()
    return () => {
      mounted = false
    }
  }, [user?.profileId])

  const appointmentsById = useMemo(
    () => Object.fromEntries(appointments.map((appointment) => [appointment.id, appointment])),
    [appointments],
  )

  const completedWithoutFeedback = useMemo(() => {
    const ratedIds = new Set(ratings.map((rating) => rating.appointmentId))
    return appointments.filter(
      (appointment) => appointment.status === 'COMPLETED' && !ratedIds.has(appointment.id),
    )
  }, [appointments, ratings])

  const avgRating = useMemo(() => {
    if (ratings.length === 0) return '0.0'
    const average = ratings.reduce((sum, rating) => sum + Number(rating.ratingScore || 0), 0) / ratings.length
    return average.toFixed(1)
  }, [ratings])

  const fiveStarCount = useMemo(
    () => ratings.filter((rating) => Number(rating.ratingScore) === 5).length,
    [ratings],
  )

  const filteredRatings = useMemo(() => {
    return ratings.filter((rating) => {
      const ratingScore = Number(rating.ratingScore || 0)
      if (starFilter !== 'ALL' && ratingScore !== Number(starFilter)) return false
      const ratingDate = rating.createdAt
        ? new Date(rating.createdAt)
        : (rating.appointmentId && appointmentsById[rating.appointmentId]?.dateTime
            ? new Date(appointmentsById[rating.appointmentId].dateTime)
            : null)
      if (!ratingDate) return true
      if (dateFrom) {
        const fromDate = new Date(dateFrom)
        if (ratingDate < fromDate) return false
      }
      if (dateTo) {
        const toDate = new Date(dateTo)
        toDate.setHours(23, 59, 59, 999)
        if (ratingDate > toDate) return false
      }
      return true
    })
  }, [ratings, starFilter, dateFrom, dateTo, appointmentsById])

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    if (!selectedAppointmentId || !score || !comment.trim()) {
      setError('Please complete appointment, rating, and review fields.')
      return
    }

    try {
      const created = await submitRating({
        appointmentId: Number(selectedAppointmentId),
        ratingScore: Number(score),
        feedbackComment: comment || null,
      })
      setRatings((prev) => [created, ...prev])
      setSelectedAppointmentId('')
      setScore(0)
      setComment('')
      success('Feedback submitted. Thank you!')
    } catch {
      setError('Unable to submit feedback.')
    }
  }

  async function handleDelete(id) {
    setError('')
    try {
      await deleteRating(id)
      setRatings((prev) => prev.filter((item) => item.id !== id))
    } catch {
      setError('Unable to delete feedback.')
    }
  }

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold">Feedback &amp; Reviews</h1>
          <p className="text-muted-foreground font-body">Rate your doctors and share your experience</p>
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <div className="rounded-2xl border border-border bg-card p-5 shadow-card">
            <div className="mb-3 inline-flex rounded-xl bg-accent-soft p-3">
              <Star className="h-5 w-5 text-accent" />
            </div>
            <p className="text-sm text-muted-foreground font-body">Average Rating</p>
            <p className="mt-1 text-3xl font-bold">{avgRating}</p>
          </div>

          <div className="rounded-2xl border border-border bg-card p-5 shadow-card">
            <div className="mb-3 inline-flex rounded-xl bg-primary-soft p-3">
              <MessageSquare className="h-5 w-5 text-primary" />
            </div>
            <p className="text-sm text-muted-foreground font-body">Reviews Given</p>
            <p className="mt-1 text-3xl font-bold">{ratings.length}</p>
          </div>

          <div className="rounded-2xl border border-border bg-card p-5 shadow-card">
            <div className="mb-3 inline-flex rounded-xl bg-teal-soft p-3">
              <ThumbsUp className="h-5 w-5 text-teal" />
            </div>
            <p className="text-sm text-muted-foreground font-body">5-Star Reviews</p>
            <p className="mt-1 text-3xl font-bold">{fiveStarCount}</p>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="rounded-2xl border border-border bg-card p-6 shadow-card">
          <h2 className="mb-4 text-lg font-semibold">Submit a Review</h2>

          <div className="space-y-4">
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="appointment">Select Appointment</label>
              <select
                id="appointment"
                value={selectedAppointmentId}
                onChange={(event) => setSelectedAppointmentId(event.target.value)}
                className="h-10 w-full rounded-md border border-input bg-muted/50 px-3 text-sm"
              >
                <option value="">Choose a completed appointment</option>
                {completedWithoutFeedback.map((appointment) => (
                  <option key={appointment.id} value={appointment.id}>
                    Dr. {formatDoctorName(appointment.doctor)} - {new Date(appointment.dateTime).toLocaleDateString()}
                  </option>
                ))}
              </select>
            </div>

            <div className="space-y-2">
              <p className="text-sm font-medium">Rating</p>
              <div className="flex gap-1">
                {[1, 2, 3, 4, 5].map((star) => (
                  <button
                    key={star}
                    type="button"
                    onClick={() => setScore(star)}
                    onMouseEnter={() => setHoverScore(star)}
                    onMouseLeave={() => setHoverScore(0)}
                    className="transition-transform hover:scale-125"
                  >
                    <Star
                      className={`h-7 w-7 ${
                        star <= (hoverScore || score)
                          ? 'fill-accent text-accent'
                          : 'text-muted-foreground/30'
                      }`}
                    />
                  </button>
                ))}
              </div>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="comment">Your Review</label>
              <textarea
                id="comment"
                placeholder="Share your experience..."
                rows={3}
                value={comment}
                onChange={(event) => setComment(event.target.value)}
                maxLength={1000}
                className="w-full rounded-md border border-input bg-muted/50 px-3 py-2 text-sm"
              />
            </div>

            {error && <p className="text-sm text-destructive">{error}</p>}

            <button
              type="submit"
              className="inline-flex h-10 items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground shadow-md hover:bg-primary/90"
            >
              <Send className="mr-2 h-4 w-4" /> Submit Review
            </button>
          </div>
        </form>

        <section className="rounded-2xl border border-border bg-card shadow-card">
          <div className="border-b border-border p-5">
            <h2 className="text-lg font-semibold">Your Reviews</h2>
          </div>

          <div className="border-b border-border p-4 space-y-3 bg-muted/30">
            <p className="text-sm font-medium">Filters</p>
            <div className="grid gap-3 sm:grid-cols-3">
              <div className="space-y-1.5">
                <label className="text-xs text-muted-foreground">Star Rating</label>
                <select
                  value={starFilter}
                  onChange={(e) => setStarFilter(e.target.value)}
                  className="h-9 w-full rounded-md border border-input bg-card px-3 text-sm"
                >
                  <option value="ALL">All Ratings</option>
                  <option value="5">5 Stars</option>
                  <option value="4">4 Stars</option>
                  <option value="3">3 Stars</option>
                  <option value="2">2 Stars</option>
                  <option value="1">1 Star</option>
                </select>
              </div>
              <div className="space-y-1.5">
                <label className="text-xs text-muted-foreground">From Date</label>
                <input
                  type="date"
                  value={dateFrom}
                  onChange={(e) => setDateFrom(e.target.value)}
                  className="h-9 w-full rounded-md border border-input bg-card px-3 text-sm"
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs text-muted-foreground">To Date</label>
                <input
                  type="date"
                  value={dateTo}
                  onChange={(e) => setDateTo(e.target.value)}
                  className="h-9 w-full rounded-md border border-input bg-card px-3 text-sm"
                />
              </div>
            </div>
            {(starFilter !== 'ALL' || dateFrom || dateTo) && (
              <button
                type="button"
                onClick={() => { setStarFilter('ALL'); setDateFrom(''); setDateTo('') }}
                className="text-xs text-primary hover:underline"
              >
                Clear filters
              </button>
            )}
          </div>

          {loading && <p className="p-5 text-sm text-muted-foreground">Loading reviews...</p>}
          {!loading && filteredRatings.length === 0 && (
            <p className="p-5 text-sm text-muted-foreground">No reviews here.</p>
          )}

          <div className="divide-y divide-border">
            {filteredRatings.map((rating) => {
              const appointment = appointmentsById[rating.appointmentId]
              const name = formatDoctorName(rating.doctor || appointment?.doctor)
              const profileImageUrl = rating.doctor?.profileImageUrl || appointment?.doctor?.profileImageUrl
              const reviewDate = rating.createdAt
                ? new Date(rating.createdAt).toLocaleDateString()
                : appointment?.dateTime
                  ? new Date(appointment.dateTime).toLocaleDateString()
                  : 'N/A'

              return (
                <article key={rating.id} className="p-5 transition-colors hover:bg-muted/30">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <UserAvatar
                        imageUrl={profileImageUrl}
                        name={name}
                        fallback={doctorInitials(name)}
                        className="h-8 w-8"
                        textClassName="text-xs"
                        toneClassName="bg-primary-soft text-primary"
                        alt={`Dr. ${name}`}
                      />
                      <span className="font-medium">Dr. {name}</span>
                    </div>

                    <div className="flex items-center gap-3">
                      <span className="text-sm text-muted-foreground">{reviewDate}</span>
                      <button
                        type="button"
                        onClick={() => handleDelete(rating.id)}
                        className="text-muted-foreground transition-colors hover:text-destructive"
                        aria-label="Delete review"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>

                  <div className="mt-2 flex gap-0.5">
                    {[1, 2, 3, 4, 5].map((star) => (
                      <Star
                        key={star}
                        className={`h-3.5 w-3.5 ${
                          star <= Number(rating.ratingScore)
                            ? 'fill-accent text-accent'
                            : 'text-muted-foreground/30'
                        }`}
                      />
                    ))}
                  </div>
                  <p className="mt-2 text-sm text-muted-foreground font-body">{rating.feedbackComment || 'No comment.'}</p>
                </article>
              )
            })}
          </div>
        </section>
      </div>
    </DashboardLayout>
  )
}

