import { useState } from 'react'
import { useNavigate, useLocation, Link } from 'react-router-dom'
import { Stethoscope, User } from 'lucide-react'
import { useAuth } from '../hooks/useAuth'
import axiosInstance from '../api/axiosInstance'
import logo from '../assets/medbuddy-logo-removebg-preview.png'

const BACKEND_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

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
  const [selectedRole, setSelectedRole] = useState('PATIENT')

  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  const from = location.state?.from?.pathname || null

  function handleChange(e) {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    setLoading(true)

    try {
      const { data } = await axiosInstance.post('/api/auth/login', {
        email: form.email,
        password: form.password,
        role: selectedRole,
      })

      login(data.token, data.user)

      // Redirect: back to the page they tried, or role dashboard
      if (from) {
        navigate(from, { replace: true })
      } else if (data.user.role === 'DOCTOR') {
        navigate('/doctor/dashboard', { replace: true })
      } else {
        navigate('/patient/dashboard', { replace: true })
      }
    } catch (err) {
      setError(
        err.response?.data?.detail ||
        err.response?.data?.message ||
        `Unable to sign in as ${selectedRole === 'DOCTOR' ? 'doctor' : 'patient'}. Please check your credentials.`,
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-hero-gradient p-4">
      <div className="w-full max-w-md space-y-6 rounded-2xl border border-border bg-card p-8 shadow-elevated opacity-0 animate-fade-in">
        <div className="text-center">
          <img src={logo} alt="MedBuddy" className="mx-auto mb-1 h-20 w-auto" />
          <h2 className="text-2xl font-bold mt-2">Welcome Back</h2>
          <p className="text-sm text-muted-foreground font-body mt-1">Sign in to your MedBuddy account</p>
        </div>

        <div className="flex rounded-lg border border-border bg-muted p-1">
          <button
            type="button"
            onClick={() => setSelectedRole('PATIENT')}
            className={`flex flex-1 items-center justify-center gap-2 rounded-md py-2.5 text-sm font-medium transition-colors ${
              selectedRole === 'PATIENT'
                ? 'bg-primary text-primary-foreground shadow-sm'
                : 'text-muted-foreground hover:text-foreground'
            }`}
          >
            <User className="h-4 w-4" /> Patient
          </button>
          <button
            type="button"
            onClick={() => setSelectedRole('DOCTOR')}
            className={`flex flex-1 items-center justify-center gap-2 rounded-md py-2.5 text-sm font-medium transition-colors ${
              selectedRole === 'DOCTOR'
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
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              placeholder="••••••••"
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="inline-flex h-10 w-full items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50"
          >
            {loading
              ? `Signing in as ${selectedRole === 'DOCTOR' ? 'Doctor' : 'Patient'}...`
              : `Sign In as ${selectedRole === 'DOCTOR' ? 'Doctor' : 'Patient'}`}
          </button>
        </form>

        {/* ── Divider ────────────────────────────────────────────── */}
        <div className="relative">
          <div className="absolute inset-0 flex items-center">
            <span className="w-full border-t border-border" />
          </div>
          <div className="relative flex justify-center text-xs uppercase">
            <span className="bg-card px-2 text-muted-foreground">or</span>
          </div>
        </div>

        {/* ── Google OAuth2 sign-in ──────────────────────────────── */}
        <a
          href={`${BACKEND_URL}/api/auth/oauth2/google?redirect=${encodeURIComponent(window.location.origin + '/oauth-callback')}`}
          className="inline-flex h-10 w-full items-center justify-center gap-3 rounded-md border border-input bg-background px-4 py-2 text-sm font-semibold transition-colors hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          {/* Google logo SVG */}
          <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true">
            <path fill="#4285F4" d="M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844c-.209 1.125-.843 2.078-1.796 2.717v2.258h2.908c1.702-1.567 2.684-3.874 2.684-6.615z" />
            <path fill="#34A853" d="M9 18c2.43 0 4.467-.806 5.956-2.184l-2.908-2.258c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332A8.997 8.997 0 0 0 9 18z" />
            <path fill="#FBBC05" d="M3.964 10.707A5.41 5.41 0 0 1 3.682 9c0-.593.102-1.17.282-1.707V4.961H.957A8.996 8.996 0 0 0 0 9c0 1.452.348 2.827.957 4.039l3.007-2.332z" />
            <path fill="#EA4335" d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 0 0 .957 4.961L3.964 7.293C4.672 5.163 6.656 3.58 9 3.58z" />
          </svg>
          Sign in with Google
        </a>

        <p className="text-center text-sm text-muted-foreground">
          Don&apos;t have an account?{' '}
          <Link to="/register" className="font-semibold text-primary underline-offset-4 hover:underline">Register</Link>
        </p>
      </div>
    </div>
  )
}

export default Login
