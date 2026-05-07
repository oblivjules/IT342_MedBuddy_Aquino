import { useEffect, useMemo, useState } from 'react'
import { Star } from 'lucide-react'
import DashboardLayout from '../../components/DashboardLayout'
import { getDoctorAverageRating, getRatingsByDoctor } from './ratingApi'
import { useAuth } from '../auth/useAuth'

function renderStars(value) {
  const rating = Number(value || 0)
  return [1, 2, 3, 4, 5].map((star) => (
    <Star
      key={star}
      className={`h-4 w-4 ${star <= Math.round(rating) ? 'fill-accent text-accent' : 'text-muted-foreground/30'}`}
    />
  ))
}

export default function DoctorRatingsFeedback() {
  const { user } = useAuth()
  const [ratings, setRatings] = useState([])
  const [average, setAverage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [starFilter, setStarFilter] = useState('ALL')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')

  useEffect(() => {
    if (!user?.profileId) {
      setLoading(false)
      return
    }

    async function loadRatings() {
      try {
        const [ratingItems, averageRating] = await Promise.all([
          getRatingsByDoctor(user.profileId),
          getDoctorAverageRating(user.profileId),
        ])

        setRatings(Array.isArray(ratingItems) ? ratingItems : [])
        setAverage(Number(averageRating || 0))
      } catch {
        setError('Failed to load ratings and feedback.')
      } finally {
        setLoading(false)
      }
    }

    loadRatings()
  }, [user?.profileId])

  const averageDisplay = useMemo(() => average.toFixed(1), [average])

  const filteredRatings = useMemo(() => {
    return ratings.filter((item) => {
      const ratingScore = Number(item.rating ?? item.ratingScore ?? 0)
      if (starFilter !== 'ALL' && ratingScore !== Number(starFilter)) return false
      if (dateFrom) {
        const itemDate = new Date(item.createdAt)
        const fromDate = new Date(dateFrom)
        if (itemDate < fromDate) return false
      }
      if (dateTo) {
        const itemDate = new Date(item.createdAt)
        const toDate = new Date(dateTo)
        toDate.setHours(23, 59, 59, 999)
        if (itemDate > toDate) return false
      }
      return true
    })
  }, [ratings, starFilter, dateFrom, dateTo])

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold md:text-3xl">Ratings &amp; Feedback</h1>
          <p className="mt-1 text-muted-foreground font-body">See your patient ratings and submitted feedback.</p>
        </div>

        <section className="rounded-2xl border border-border bg-card p-6 shadow-card">
          <p className="text-sm text-muted-foreground">Average Rating</p>
          <div className="mt-2 flex items-center gap-3">
            <p className="text-4xl font-bold text-foreground">{averageDisplay}</p>
            <div className="flex items-center gap-1">{renderStars(average)}</div>
          </div>
          <p className="mt-2 text-sm text-muted-foreground">Based on {ratings.length} review(s)</p>
        </section>

        <section className="rounded-2xl border border-border bg-card shadow-card">
          <div className="border-b border-border p-5">
            <h2 className="text-lg font-semibold">Patient Reviews</h2>
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

          {loading && <p className="p-5 text-sm text-muted-foreground">Loading ratings...</p>}
          {!loading && error && <p className="p-5 text-sm text-destructive">{error}</p>}
          {!loading && !error && filteredRatings.length === 0 && (
            <p className="p-5 text-sm text-muted-foreground">No reviews here.</p>
          )}

          <div className="divide-y divide-border">
            {filteredRatings.map((item, index) => (
              <article key={`${item.id || index}`} className="p-5">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-semibold">{item.patientName || item.patient?.firstName || 'Patient'}</p>
                    <p className="text-xs text-muted-foreground">
                      {item.createdAt ? new Date(item.createdAt).toLocaleString() : 'N/A'}
                    </p>
                  </div>
                  <div className="flex items-center gap-1">{renderStars(item.rating ?? item.ratingScore)}</div>
                </div>
                <p className="mt-3 text-sm text-muted-foreground font-body">{item.feedback || item.feedbackComment || 'No feedback provided.'}</p>
              </article>
            ))}
          </div>
        </section>
      </div>
    </DashboardLayout>
  )
}
