import { useState } from 'react'
import { useNavigate, useLocation, Link } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import axiosInstance from '../api/axiosInstance'

/**
 * Login page
 *
 * POSTs to POST /api/auth/login
 * Expected response: { token: string, user: { id, email, role } }
 *
 * On success the JWT and user object are stored via AuthContext and the user is
 * redirected to their role-appropriate dashboard.
 */
function Login() {
  const navigate = useNavigate()
  const location = useLocation()
  const { login } = useAuth()

  const [form, setForm] = useState({ email: '', password: '' })
  const [fieldErrors, setFieldErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [success, setSuccess] = useState(location.state?.successMessage || null)
  const [loading, setLoading] = useState(false)

  const from = location.state?.from?.pathname || null

  function handleChange(e) {
    const { name, value } = e.target
    setForm((prev) => ({ ...prev, [name]: value }))
    setFieldErrors((prev) => ({ ...prev, [name]: null }))
    setFormError(null)
  }

  function validateForm() {
    const nextErrors = {}

    if (!form.email.trim()) {
      nextErrors.email = 'Email is required.'
    }

    if (!form.password) {
      nextErrors.password = 'Password is required.'
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
      const { data } = await axiosInstance.post('/api/auth/login', {
        email: form.email.trim(),
        password: form.password,
      })

      login(data.token, data.user)
      setSuccess('Login successful. Redirecting...')

      // Keep success feedback visible briefly before route transition.
      await new Promise((resolve) => setTimeout(resolve, 700))

      // Redirect: back to the page they tried, or role dashboard
      if (from) {
        navigate(from, { replace: true })
      } else if (data.user.role === 'DOCTOR') {
        navigate('/doctor/dashboard', { replace: true })
      } else {
        navigate('/patient/dashboard', { replace: true })
      }
    } catch (err) {
      const responseErrors = err.response?.data?.errors

      if (responseErrors && typeof responseErrors === 'object') {
        setFieldErrors(responseErrors)
      } else {
        const detail =
          err.response?.data?.detail ||
          err.response?.data?.message ||
          'Login failed. Please check your credentials.'

        if (typeof detail === 'string' && detail.toLowerCase().includes('password')) {
          setFieldErrors({ password: detail })
        } else {
          setFormError(detail)
        }
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-hero-gradient p-4">
      <div className="w-full max-w-md space-y-6 rounded-2xl border border-border bg-card p-8 shadow-elevated opacity-0 animate-fade-in">
        <div className="text-center">
          <h1 className="text-3xl font-extrabold text-gradient mb-1">MedBuddy</h1>
          <h2 className="text-2xl font-bold mt-2">Welcome Back</h2>
          <p className="text-sm text-muted-foreground font-body mt-1">Sign in to your MedBuddy account</p>
        </div>

        {success && (
          <p className="rounded-md bg-emerald-500/10 px-3 py-2 text-sm text-emerald-600">{success}</p>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
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
              className={`flex h-10 w-full rounded-md border bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${fieldErrors.password ? 'border-destructive focus-visible:ring-destructive' : 'border-input'}`}
              placeholder="********"
            />
            {fieldErrors.password && (
              <p className="text-xs text-destructive">{fieldErrors.password}</p>
            )}
          </div>

          {formError && (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{formError}</p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="inline-flex h-10 w-full items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50"
          >
            {loading ? 'Signing in...' : 'Sign In'}
          </button>
        </form>

        <p className="text-center text-sm text-muted-foreground">
          Don&apos;t have an account?{' '}
          <Link to="/register" className="font-semibold text-primary underline-offset-4 hover:underline">Register</Link>
        </p>
      </div>
    </div>
  )
}

export default Login
