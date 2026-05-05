import { useEffect, useMemo, useState } from 'react'
import { Star } from 'lucide-react'
import DashboardLayout from '../../components/DashboardLayout'
import { getDoctorAverageRating, getRatingsByDoctor } from '../../api/ratingApi'
import { useAuth } from '../../hooks/useAuth'

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

          {loading && <p className="p-5 text-sm text-muted-foreground">Loading ratings...</p>}
          {!loading && error && <p className="p-5 text-sm text-destructive">{error}</p>}
          {!loading && !error && ratings.length === 0 && (
            <p className="p-5 text-sm text-muted-foreground">No ratings yet.</p>
          )}

          <div className="divide-y divide-border">
            {ratings.map((item, index) => (
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
