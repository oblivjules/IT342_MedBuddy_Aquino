import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Clock, MapPin, Search, Star } from 'lucide-react'
import DashboardLayout from '../../components/DashboardLayout'
import UserAvatar from '../../components/UserAvatar'
import { getDoctors } from '../../api/userApi'
import { getDoctorAverageRating } from '../../api/ratingApi'
import { getSpecializations } from '../../api/specializationApi'

export default function PatientFindDoctor() {
  const [doctors, setDoctors] = useState([])
  const [specializations, setSpecializations] = useState([])
  const [averageRatingsByDoctorId, setAverageRatingsByDoctorId] = useState({})
  const [search, setSearch] = useState('')
  const [selectedSpec, setSelectedSpec] = useState('ALL')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function load() {
      try {
        const [doctorData, specData] = await Promise.all([getDoctors(), getSpecializations()])
        const safeDoctors = Array.isArray(doctorData) ? doctorData : []

        setDoctors(safeDoctors)
        setSpecializations(Array.isArray(specData) ? specData : [])

        const ratingEntries = await Promise.all(
          safeDoctors.map(async (doctor) => {
            try {
              const average = await getDoctorAverageRating(doctor.id)
              return [doctor.id, Number.isFinite(average) ? average : 0]
            } catch {
              return [doctor.id, 0]
            }
          }),
        )

        setAverageRatingsByDoctorId(Object.fromEntries(ratingEntries))
      } finally {
        setLoading(false)
      }
    }

    load()
  }, [])

  function renderStars(average) {
    const rating = Number(average || 0)
    const filled = Math.round(rating)

    return (
      <span className="flex items-center gap-0.5" aria-hidden="true">
        {[1, 2, 3, 4, 5].map((star) => (
          <Star
            key={star}
            className={`h-3.5 w-3.5 ${star <= filled ? 'fill-accent text-accent' : 'text-muted-foreground/30'}`}
          />
        ))}
      </span>
    )
  }

  const filtered = useMemo(() => {
    return doctors.filter((doctor) => {
      const fullName = `${doctor.firstName || ''} ${doctor.lastName || ''}`.trim().toLowerCase()
      const email = (doctor.email || '').toLowerCase()
      const matchesSearch = !search || fullName.includes(search.toLowerCase()) || email.includes(search.toLowerCase())
      const matchesSpec = selectedSpec === 'ALL' || (doctor.specializations || []).includes(selectedSpec)
      return matchesSearch && matchesSpec
    })
  }, [doctors, search, selectedSpec])

  return (
    <DashboardLayout title="Find a Doctor" subtitle="Search and book from available specialists">
      <div className="space-y-6">
        <div className="flex flex-col gap-3 sm:flex-row">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search doctor name or email..."
              className="h-10 w-full rounded-md border border-input bg-card pl-9 pr-3 text-sm"
            />
          </div>
          <select
            value={selectedSpec}
            onChange={(e) => setSelectedSpec(e.target.value)}
            className="h-10 rounded-md border border-input bg-card px-3 text-sm sm:w-64"
          >
            <option value="ALL">All Specializations</option>
            {specializations.map((spec) => (
              <option key={spec.id} value={spec.name}>
                {spec.name}
              </option>
            ))}
          </select>
        </div>

        {loading ? (
          <p className="text-sm text-muted-foreground">Loading doctors...</p>
        ) : filtered.length === 0 ? (
          <p className="text-sm text-muted-foreground">No doctors found.</p>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {filtered.map((doctor, index) => {
              const name = [doctor.firstName, doctor.lastName].filter(Boolean).join(' ') || doctor.email
              const specialties = (doctor.specializations || []).join(', ') || 'Not specified'
              const initials = name
                .replace('Dr. ', '')
                .split(' ')
                .filter(Boolean)
                .slice(0, 2)
                .map((part) => part[0])
                .join('')
                .toUpperCase()
              const tone = ['bg-primary', 'bg-teal', 'bg-accent'][index % 3]
              return (
                <div key={doctor.id} className="rounded-2xl border border-border bg-card p-6 shadow-card transition-all hover:-translate-y-1 hover:shadow-elevated">
                  <div className="mb-4 flex items-center gap-4">
                    <UserAvatar
                      imageUrl={doctor.profileImageUrl}
                      name={name}
                      fallback={initials || 'DR'}
                      className="h-12 w-12"
                      textClassName="text-sm"
                      toneClassName={`${tone} text-primary-foreground`}
                      alt={`Dr. ${name}`}
                    />
                    <div>
                      <h3 className="font-semibold text-lg">Dr. {name}</h3>
                      <p className="text-xs text-muted-foreground">{specialties}</p>
                    </div>
                  </div>
                  <div className="mb-4 space-y-2">
                    {(averageRatingsByDoctorId[doctor.id] ?? 0) > 0 ? (
                      <p className="flex items-center gap-1 text-xs text-muted-foreground">
                        <span className="inline-flex items-center gap-1 text-accent">
                          {renderStars(averageRatingsByDoctorId[doctor.id] ?? 0)}
                        </span>
                        <span className="font-medium text-foreground">
                          {(averageRatingsByDoctorId[doctor.id] ?? 0).toFixed(1)}
                        </span>
                        <span>average rating</span>
                      </p>
                    ) : (
                      <p className="text-xs text-muted-foreground">No ratings and reviews yet</p>
                    )}
                    <p className="flex items-center gap-1 text-xs text-muted-foreground">
                      <Clock className="h-3.5 w-3.5" />
                      Experienced clinician
                    </p>
                    <p className="flex items-center gap-1 text-xs text-muted-foreground">
                      <MapPin className="h-3.5 w-3.5" />
                      MedBuddy Clinic
                    </p>
                  </div>
                  <div className="grid gap-2 sm:grid-cols-2">
                    <Link
                      to={`/patient/doctor/${doctor.id}`}
                      className="inline-flex h-9 w-full items-center justify-center rounded-md border border-border bg-card px-4 text-sm font-semibold text-foreground hover:bg-muted"
                    >
                      View Profile
                    </Link>
                    <Link
                      to={`/patient/book/${doctor.id}`}
                      className="inline-flex h-9 w-full items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground hover:bg-primary/90"
                    >
                      Book Now
                    </Link>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </DashboardLayout>
  )
}

