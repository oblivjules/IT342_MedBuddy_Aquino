import { useState } from 'react'
import { useEffect } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Eye, EyeOff, Stethoscope, User } from 'lucide-react'
import { useAuth } from './useAuth'
import { useToast } from '../../hooks/useToast'
import axiosInstance from '../../api/axiosInstance'
import { getSpecializations } from '../findDoctor/specializationApi'
import logo from '../../assets/medbuddy-logo-removebg-preview.png'

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
  const { success } = useToast()
  const [specializations, setSpecializations] = useState([])

  const [form, setForm] = useState({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    confirmPassword: '',
    role: 'PATIENT',
    phoneNumber: '',
    specializationIds: [],
  })
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)

  useEffect(() => {
    getSpecializations()
      .then((data) => setSpecializations(Array.isArray(data) ? data : []))
      .catch(() => setSpecializations([]))
  }, [])

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
    const id = Number(e.target.value)

    setForm((prev) => {
      const selected = prev.specializationIds.includes(id)
        ? prev.specializationIds.filter((s) => s !== id)
        : [...prev.specializationIds, id]
      return { ...prev, specializationIds: selected }
    })
  }

  function getApiErrorMessage(err) {
    const fieldErrors = err.response?.data?.errors

    if (fieldErrors && typeof fieldErrors === 'object') {
      const firstFieldMessage = Object.values(fieldErrors).find(
        (message) => typeof message === 'string' && message.trim().length > 0,
      )

      if (firstFieldMessage) {
        return firstFieldMessage
      }
    }

    return (
      err.response?.data?.detail ||
      err.response?.data?.message ||
      'Registration failed. Please try again.'
    )
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)

    const firstName = form.firstName.trim()
    const lastName = form.lastName.trim()
    const email = form.email.trim().toLowerCase()
    const password = form.password
    const confirmPassword = form.confirmPassword
    const phoneNumber = form.phoneNumber.trim()

    if (!firstName || !lastName) {
      setError('First name and last name are required.')
      return
    }

    if (!email) {
      setError('Email is required.')
      return
    }

    // Require an email with a top-level domain (e.g., .com, .ph)
    const emailRegex = /^[^\s@]+@[^\s@]+\.[A-Za-z]{2,}$/
    if (!emailRegex.test(email)) {
      setError('Please enter a valid email address including a domain (for example: you@example.com).')
      return
    }

    if (password.length < 8) {
      setError('Password must be at least 8 characters long.')
      return
    }

    const passwordRegex = /^(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*(),.?":{}|<>]).+$/
    if (!passwordRegex.test(password)) {
      setError(
        'Password must include an uppercase letter, a number, and a special character.',
      )
      return
    }

    if (password !== confirmPassword) {
      setError('Passwords do not match.')
      return
    }

    if (phoneNumber.length !== 10) {
      setError('Phone number must contain exactly 10 digits after +63.')
      return
    }

    const specializationIds = [...new Set(form.specializationIds)].filter(
      (id) => Number.isInteger(id) && id > 0,
    )

    if (form.role === 'DOCTOR' && specializationIds.length === 0) {
      setError('Please select at least one specialization.')
      return
    }

    setLoading(true)

    try {
      const payload = {
        firstName,
        lastName,
        email,
        password,
        role: form.role,
        phoneNumber: `+63${phoneNumber}`,
      }

      if (form.role === 'DOCTOR') {
        payload.specializationIds = specializationIds
      }

      const { data } = await axiosInstance.post('/api/auth/register', payload)

      // Do not auto-login. Redirect user to login page after successful registration.
      success('Account created. Please sign in to continue.')
      navigate('/login', { replace: true })
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  const selectedSpecializationNames = specializations
    .filter((spec) => form.specializationIds.includes(spec.id))
    .map((spec) => spec.name)

  return (
    <div className="flex min-h-screen items-center justify-center bg-hero-gradient p-4 py-8">
      <div className="w-full max-w-md space-y-6 rounded-2xl border border-border bg-card p-8 shadow-elevated opacity-0 animate-fade-in">
        <div className="text-center">
          <img src={logo} alt="MedBuddy" className="mx-auto mb-1 h-20 w-auto" />
          <h2 className="text-2xl font-bold mt-2">Create Account</h2>
          <p className="text-sm text-muted-foreground font-body mt-1">Join MedBuddy to manage your healthcare</p>
        </div>

        <div className="flex rounded-lg border border-border bg-muted p-1">
          <button
            type="button"
            onClick={() => setForm((prev) => ({ ...prev, role: 'PATIENT', specializationIds: [] }))}
            className={`flex flex-1 items-center justify-center gap-2 rounded-md py-2.5 text-sm font-medium transition-colors ${
              form.role === 'PATIENT'
                ? 'bg-primary text-primary-foreground shadow-sm'
                : 'text-muted-foreground hover:text-foreground'
            }`}
          >
            <User className="h-4 w-4" /> Patient
          </button>
          <button
            type="button"
            onClick={() => setForm((prev) => ({ ...prev, role: 'DOCTOR' }))}
            className={`flex flex-1 items-center justify-center gap-2 rounded-md py-2.5 text-sm font-medium transition-colors ${
              form.role === 'DOCTOR'
                ? 'bg-primary text-primary-foreground shadow-sm'
                : 'text-muted-foreground hover:text-foreground'
            }`}
          >
            <Stethoscope className="h-4 w-4" /> Doctor
          </button>
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
            <div className="relative">
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                name="password"
                value={form.password}
                onChange={handleChange}
                required
                autoComplete="new-password"
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 pr-10 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                placeholder="Enter password"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
                aria-label={showPassword ? 'Hide password' : 'Show password'}
                tabIndex={-1}
              >
                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
            <p className="text-[10px] text-muted-foreground leading-tight">
              Must include 8+ characters, a capital letter, a number, and a symbol.
            </p>
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium leading-none" htmlFor="confirmPassword">Confirm Password</label>
            <div className="relative">
              <input
                id="confirmPassword"
                type={showConfirmPassword ? 'text' : 'password'}
                name="confirmPassword"
                value={form.confirmPassword}
                onChange={handleChange}
                required
                autoComplete="new-password"
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 pr-10 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                placeholder="Re-enter your password"
              />
              <button
                type="button"
                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
                aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
                tabIndex={-1}
              >
                {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
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

          {form.role === 'DOCTOR' && (
            <div className="space-y-2">
              <label className="text-sm font-medium leading-none">
                Specialization(s) <span className="text-destructive">*</span>
              </label>
              <div className="max-h-48 overflow-y-auto rounded-md border border-input bg-background p-3 space-y-2">
                {specializations.map((spec) => (
                  <label key={spec.id} className="flex items-center gap-2 text-sm cursor-pointer">
                    <input
                      type="checkbox"
                      value={spec.id}
                      checked={form.specializationIds.includes(spec.id)}
                      onChange={handleSpecializationChange}
                      className="h-4 w-4 rounded border-input accent-primary"
                    />
                    {spec.name}
                  </label>
                ))}
                {specializations.length === 0 && (
                  <p className="text-xs text-muted-foreground">No specializations loaded from backend.</p>
                )}
              </div>
              {form.specializationIds.length > 0 && (
                <p className="text-xs text-muted-foreground">
                  Selected: {selectedSpecializationNames.join(', ')}
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
