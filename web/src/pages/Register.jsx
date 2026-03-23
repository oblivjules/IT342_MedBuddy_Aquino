import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import axiosInstance from '../api/axiosInstance'
import logo from '../assets/medbuddy-logo-removebg-preview.png'

const SPECIALIZATIONS = [
  'Cardiology',
  'Dermatology',
  'Neurology',
  'Orthopedics',
  'Pediatrics',
  'General Medicine',
  'Obstetrics & Gynecology',
  'Ophthalmology',
  'Ear, Nose & Throat (ENT)',
  'Psychiatry',
  'Pulmonology',
  'Gastroenterology',
  'Endocrinology',
  'Nephrology',
  'Urology',
  'Oncology',
  'Rheumatology',
  'Hematology',
  'Infectious Disease',
  'Emergency Medicine',
  'Anesthesiology',
  'Radiology',
  'Pathology',
  'Physical Medicine & Rehabilitation',
  'Dentistry',
]

/**
 * Register page
 *
 * POSTs to POST /api/auth/register
 * Expected response: { token: string, user: { id, email, role } }
 *
 * On success the JWT and user object are stored via AuthContext and the user is
 * redirected to their role-appropriate dashboard.
 */
function Register() {
  const navigate = useNavigate()
  const { login } = useAuth()

  const [form, setForm] = useState({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    confirmPassword: '',
    role: 'PATIENT',
    phoneNumber: '',
    specializations: [],
  })
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  function handleChange(e) {
    const { name, value } = e.target

    if (name === 'phoneNumber') {
      const digitsOnly = value.replace(/\D/g, '').slice(0, 10)
      setForm((prev) => ({ ...prev, phoneNumber: digitsOnly }))
      return
    }

    setForm((prev) => ({ ...prev, [name]: value }))
  }

  function handleSpecializationChange(e) {
    const value = e.target.value
    setForm((prev) => {
      const selected = prev.specializations.includes(value)
        ? prev.specializations.filter((s) => s !== value)
        : [...prev.specializations, value]
      return { ...prev, specializations: selected }
    })
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)

    if (form.password !== form.confirmPassword) {
      setError('Passwords do not match.')
      return
    }

    if (form.phoneNumber.length !== 10) {
      setError('Phone number must contain exactly 10 digits after +63.')
      return
    }

    if (form.role === 'DOCTOR' && form.specializations.length === 0) {
      setError('Please select at least one specialization.')
      return
    }

    setLoading(true)

    try {
      const payload = {
        firstName: form.firstName,
        lastName: form.lastName,
        email: form.email,
        password: form.password,
        role: form.role,
        phoneNumber: `+63${form.phoneNumber}`,
        ...(form.role === 'DOCTOR' && { specializations: form.specializations }),
      }
      const { data } = await axiosInstance.post('/api/auth/register', payload)

      login(data.token, data.user)

      if (data.user.role === 'DOCTOR') {
        navigate('/doctor/dashboard', { replace: true })
      } else {
        navigate('/patient/dashboard', { replace: true })
      }
    } catch (err) {
      setError(
        err.response?.data?.detail ||
        err.response?.data?.message ||
        'Registration failed. Please try again.',
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-hero-gradient p-4 py-8">
      <div className="w-full max-w-md space-y-6 rounded-2xl border border-border bg-card p-8 shadow-elevated opacity-0 animate-fade-in">
        <div className="text-center">
          <img src={logo} alt="MedBuddy" className="mx-auto mb-1 h-20 w-auto" />
          <h2 className="text-2xl font-bold mt-2">Create Account</h2>
          <p className="text-sm text-muted-foreground font-body mt-1">Join MedBuddy to manage your healthcare</p>
        </div>

        {error && (
          <p className="text-destructive bg-destructive/10 rounded-md px-3 py-2 text-sm">{error}</p>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <label className="text-sm font-medium leading-none" htmlFor="firstName">First Name</label>
              <input
                id="firstName"
                type="text"
                name="firstName"
                value={form.firstName}
                onChange={handleChange}
                required
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                placeholder="Juan"
              />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium leading-none" htmlFor="lastName">Last Name</label>
              <input
                id="lastName"
                type="text"
                name="lastName"
                value={form.lastName}
                onChange={handleChange}
                required
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                placeholder="Dela Cruz"
              />
            </div>
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium leading-none" htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              name="email"
              value={form.email}
              onChange={handleChange}
              required
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              placeholder="you@example.com"
            />
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium leading-none" htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              name="password"
              value={form.password}
              onChange={handleChange}
              required
              minLength={8}
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              placeholder="Min. 8 characters"
            />
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium leading-none" htmlFor="confirmPassword">Confirm Password</label>
            <input
              id="confirmPassword"
              type="password"
              name="confirmPassword"
              value={form.confirmPassword}
              onChange={handleChange}
              required
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              placeholder="Re-enter your password"
            />
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium leading-none" htmlFor="phoneNumber">Phone Number</label>
            <div className="flex h-10 w-full items-center rounded-md border border-input bg-background ring-offset-background focus-within:ring-2 focus-within:ring-ring focus-within:ring-offset-2">
              <span className="inline-flex h-full items-center border-r border-input px-3 text-sm text-muted-foreground">
                +63
              </span>
              <input
                id="phoneNumber"
                type="tel"
                name="phoneNumber"
                value={form.phoneNumber}
                onChange={handleChange}
                required
                inputMode="numeric"
                pattern="[0-9]{10}"
                maxLength={10}
                className="h-full w-full rounded-r-md bg-transparent px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none"
                placeholder="9123456789"
              />
            </div>
            <p className="text-xs text-muted-foreground">Enter exactly 10 digits after +63.</p>
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium leading-none" htmlFor="role">I am a…</label>
            <select
              id="role"
              name="role"
              value={form.role}
              onChange={handleChange}
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
            >
              <option value="PATIENT">Patient</option>
              <option value="DOCTOR">Doctor</option>
            </select>
          </div>

          {form.role === 'DOCTOR' && (
            <div className="space-y-2">
              <label className="text-sm font-medium leading-none">
                Specialization(s) <span className="text-destructive">*</span>
              </label>
              <div className="max-h-48 overflow-y-auto rounded-md border border-input bg-background p-3 space-y-2">
                {SPECIALIZATIONS.map((spec) => (
                  <label key={spec} className="flex items-center gap-2 text-sm cursor-pointer">
                    <input
                      type="checkbox"
                      value={spec}
                      checked={form.specializations.includes(spec)}
                      onChange={handleSpecializationChange}
                      className="h-4 w-4 rounded border-input accent-primary"
                    />
                    {spec}
                  </label>
                ))}
              </div>
              {form.specializations.length > 0 && (
                <p className="text-xs text-muted-foreground">
                  Selected: {form.specializations.join(', ')}
                </p>
              )}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="inline-flex h-10 w-full items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50"
          >
            {loading ? 'Creating account…' : 'Create Account'}
          </button>
        </form>

        <p className="text-center text-sm text-muted-foreground">
          Already have an account?{' '}
          <Link to="/login" className="font-semibold text-primary underline-offset-4 hover:underline">Sign in</Link>
        </p>
      </div>
    </div>
  )
}

export default Register
