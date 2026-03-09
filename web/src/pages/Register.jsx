import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import axiosInstance from '../api/axiosInstance'

/**
 * Register page
 *
 * POSTs to POST /api/auth/register
 * Expected response: { token: string, user: { id, email, role } }
 *
 * On success users are redirected to login and shown a success message.
 */
const SPECIALIZATION_OPTIONS = [
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

const REGISTER_FIELD_MAP = {
  specialization: 'specializations',
}

function Register() {
  const navigate = useNavigate()

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
  const [fieldErrors, setFieldErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [success, setSuccess] = useState(null)
  const [loading, setLoading] = useState(false)

  function handleChange(e) {
    const { name, value } = e.target

    if (name === 'phoneNumber') {
      const digitsOnly = value.replace(/\D/g, '').slice(0, 10)
      setForm((prev) => ({ ...prev, phoneNumber: digitsOnly }))
      setFieldErrors((prev) => ({ ...prev, phoneNumber: null }))
      setFormError(null)
      return
    }

    if (name === 'role') {
      setForm((prev) => ({
        ...prev,
        role: value,
        ...(value === 'PATIENT' && { specializations: [] }),
      }))
      setFieldErrors((prev) => ({ ...prev, role: null, specializations: null }))
      setFormError(null)
      return
    }

    setForm((prev) => ({ ...prev, [name]: value }))
    setFieldErrors((prev) => ({ ...prev, [name]: null }))
    setFormError(null)
  }

  function handleSpecializationToggle(option) {
    setForm((prev) => {
      const alreadySelected = prev.specializations.includes(option)
      return {
        ...prev,
        specializations: alreadySelected
          ? prev.specializations.filter((item) => item !== option)
          : [...prev.specializations, option],
      }
    })
    setFieldErrors((prev) => ({ ...prev, specializations: null }))
    setFormError(null)
  }

  function validateForm() {
    const nextErrors = {}

    if (!form.firstName.trim()) {
      nextErrors.firstName = 'First name is required.'
    }

    if (!form.lastName.trim()) {
      nextErrors.lastName = 'Last name is required.'
    }

    if (!form.email.trim()) {
      nextErrors.email = 'Email is required.'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
      nextErrors.email = 'Please enter a valid email address.'
    }

    if (!form.password) {
      nextErrors.password = 'Password is required.'
    } else if (form.password.length < 8) {
      nextErrors.password = 'Password must be at least 8 characters.'
    }

    if (!form.confirmPassword) {
      nextErrors.confirmPassword = 'Please confirm your password.'
    } else if (form.password !== form.confirmPassword) {
      nextErrors.confirmPassword = 'Passwords do not match.'
    }

    if (form.phoneNumber.length !== 10) {
      nextErrors.phoneNumber = 'Phone number must contain exactly 10 digits.'
    }

    if (form.role === 'DOCTOR' && form.specializations.length === 0) {
      nextErrors.specializations = 'Select at least one specialization.'
    }

    return nextErrors
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setFormError(null)
    setSuccess(null)

    const nextErrors = validateForm()
    if (Object.keys(nextErrors).length > 0) {
      setFieldErrors(nextErrors)
      return
    }

    setFieldErrors({})

    setLoading(true)

    try {
      const payload = {
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        email: form.email.trim(),
        password: form.password,
        role: form.role,
        phoneNumber: `+63${form.phoneNumber}`,
        ...(form.role === 'DOCTOR' && {
          specialization: form.specializations.join(', '),
        }),
      }
      await axiosInstance.post('/api/auth/register', payload)

      setSuccess('Signup successful. Redirecting to login...')

      await new Promise((resolve) => setTimeout(resolve, 900))
      navigate('/login', {
        replace: true,
        state: { successMessage: 'Account created. Please sign in.' },
      })
    } catch (err) {
      const responseErrors = err.response?.data?.errors

      if (responseErrors && typeof responseErrors === 'object') {
        const mappedErrors = {}
        Object.entries(responseErrors).forEach(([key, message]) => {
          const mappedKey = REGISTER_FIELD_MAP[key] || key
          mappedErrors[mappedKey] = message
        })
        setFieldErrors(mappedErrors)
      } else {
        const detail =
          err.response?.data?.detail ||
          err.response?.data?.message ||
          'Registration failed. Please try again.'

        if (typeof detail === 'string' && detail.toLowerCase().includes('specialization')) {
          setFieldErrors({ specializations: detail })
        } else if (typeof detail === 'string' && detail.toLowerCase().includes('phone')) {
          setFieldErrors({ phoneNumber: detail })
        } else {
          setFormError(detail)
        }
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-hero-gradient p-4 py-8">
      <div className="w-full max-w-md space-y-6 rounded-2xl border border-border bg-card p-8 shadow-elevated opacity-0 animate-fade-in">
        <div className="text-center">
          <h1 className="text-3xl font-extrabold text-gradient mb-1">MedBuddy</h1>
          <h2 className="text-2xl font-bold mt-2">Create Account</h2>
          <p className="text-sm text-muted-foreground font-body mt-1">Join MedBuddy to manage your healthcare</p>
        </div>

        {success && (
          <p className="rounded-md bg-emerald-500/10 px-3 py-2 text-sm text-emerald-600">{success}</p>
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
                className={`flex h-10 w-full rounded-md border bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${fieldErrors.firstName ? 'border-destructive focus-visible:ring-destructive' : 'border-input'}`}
                placeholder="Juan"
              />
              {fieldErrors.firstName && (
                <p className="text-xs text-destructive">{fieldErrors.firstName}</p>
              )}
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
                className={`flex h-10 w-full rounded-md border bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${fieldErrors.lastName ? 'border-destructive focus-visible:ring-destructive' : 'border-input'}`}
                placeholder="Dela Cruz"
              />
              {fieldErrors.lastName && (
                <p className="text-xs text-destructive">{fieldErrors.lastName}</p>
              )}
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
              className={`flex h-10 w-full rounded-md border bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${fieldErrors.email ? 'border-destructive focus-visible:ring-destructive' : 'border-input'}`}
              placeholder="you@example.com"
            />
            {fieldErrors.email && (
              <p className="text-xs text-destructive">{fieldErrors.email}</p>
            )}
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
              className={`flex h-10 w-full rounded-md border bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${fieldErrors.password ? 'border-destructive focus-visible:ring-destructive' : 'border-input'}`}
              placeholder="Min. 8 characters"
            />
            {fieldErrors.password && (
              <p className="text-xs text-destructive">{fieldErrors.password}</p>
            )}
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
              className={`flex h-10 w-full rounded-md border bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${fieldErrors.confirmPassword ? 'border-destructive focus-visible:ring-destructive' : 'border-input'}`}
              placeholder="Re-enter your password"
            />
            {fieldErrors.confirmPassword && (
              <p className="text-xs text-destructive">{fieldErrors.confirmPassword}</p>
            )}
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium leading-none" htmlFor="phoneNumber">Phone Number</label>
            <div
              className={`flex h-10 w-full items-center rounded-md border bg-background ring-offset-background focus-within:ring-2 focus-within:ring-ring focus-within:ring-offset-2 ${fieldErrors.phoneNumber ? 'border-destructive focus-within:ring-destructive' : 'border-input'}`}
            >
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
            {fieldErrors.phoneNumber && (
              <p className="text-xs text-destructive">{fieldErrors.phoneNumber}</p>
            )}
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium leading-none" htmlFor="role">I am a...</label>
            <select
              id="role"
              name="role"
              value={form.role}
              onChange={handleChange}
              className={`flex h-10 w-full rounded-md border bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${fieldErrors.role ? 'border-destructive focus-visible:ring-destructive' : 'border-input'}`}
            >
              <option value="PATIENT">Patient</option>
              <option value="DOCTOR">Doctor</option>
            </select>
            {fieldErrors.role && (
              <p className="text-xs text-destructive">{fieldErrors.role}</p>
            )}
          </div>

          {form.role === 'DOCTOR' && (
            <fieldset className="space-y-2">
              <legend className="text-sm font-medium leading-none">Specializations</legend>
              <div
                className={`max-h-52 space-y-2 overflow-y-auto rounded-md border bg-background p-3 ${fieldErrors.specializations ? 'border-destructive' : 'border-input'}`}
              >
                {SPECIALIZATION_OPTIONS.map((specialization) => (
                  <label
                    key={specialization}
                    className="flex items-center gap-2 text-sm text-foreground"
                  >
                    <input
                      type="checkbox"
                      checked={form.specializations.includes(specialization)}
                      onChange={() => handleSpecializationToggle(specialization)}
                      className="h-4 w-4 rounded border-input text-primary focus:ring-ring"
                    />
                    <span>{specialization}</span>
                  </label>
                ))}
              </div>
              {fieldErrors.specializations && (
                <p className="text-xs text-destructive">{fieldErrors.specializations}</p>
              )}
            </fieldset>
          )}

          {formError && (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{formError}</p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="inline-flex h-10 w-full items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50"
          >
            {loading ? 'Creating account...' : 'Create Account'}
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
