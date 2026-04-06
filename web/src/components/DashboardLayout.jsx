import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import {
  Bell,
  ChevronDown,
  Calendar,
  Clock,
  CreditCard,
  FileText,
  LayoutDashboard,
  Menu,
  MessageSquare,
  Search,
  Stethoscope,
  Users,
  X,
} from 'lucide-react'
import { useAuth } from '../hooks/useAuth'
import logo from '../assets/medbuddy-logo-removebg-preview.png'
import UserAvatar from './UserAvatar'

const patientLinks = [
  { label: 'Dashboard', path: '/patient/dashboard', icon: LayoutDashboard },
  { label: 'Find Doctor', path: '/patient/find-doctor', icon: Stethoscope },
  { label: 'Appointments', path: '/patient/appointments', icon: Calendar },
  { label: 'Medical Records', path: '/patient/medical-records', icon: FileText },
  { label: 'Billing', path: '/patient/billing', icon: CreditCard },
  { label: 'Feedback', path: '/patient/feedback', icon: MessageSquare },
]

const doctorLinks = [
  { label: 'Dashboard', path: '/doctor/dashboard', icon: LayoutDashboard },
  { label: 'Appointments', path: '/doctor/appointments', icon: Calendar },
  { label: 'Schedule', path: '/doctor/schedule', icon: Clock },
  { label: 'Patient Records', path: '/doctor/patient-records', icon: Users },
]

function initials(user) {
  const first = user?.firstName?.[0] || ''
  const last = user?.lastName?.[0] || ''
  if (first || last) return `${first}${last}`.toUpperCase()
  return user?.email?.[0]?.toUpperCase() || 'U'
}

export default function DashboardLayout({ title, subtitle, actions, children }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [profileMenuOpen, setProfileMenuOpen] = useState(false)
  const profileMenuRef = useRef(null)

  const links = useMemo(
    () => (user?.role === 'DOCTOR' ? doctorLinks : patientLinks),
    [user?.role],
  )

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  function openSettings() {
    setProfileMenuOpen(false)
    if (user?.role === 'DOCTOR') {
      navigate('/doctor/settings')
      return
    }
    navigate('/patient/settings')
  }

  useEffect(() => {
    function onDocumentPointerDown(event) {
      if (!profileMenuRef.current) return
      if (!profileMenuRef.current.contains(event.target)) {
        setProfileMenuOpen(false)
      }
    }

    function onDocumentKeyDown(event) {
      if (event.key === 'Escape') {
        setProfileMenuOpen(false)
      }
    }

    document.addEventListener('mousedown', onDocumentPointerDown)
    document.addEventListener('keydown', onDocumentKeyDown)

    return () => {
      document.removeEventListener('mousedown', onDocumentPointerDown)
      document.removeEventListener('keydown', onDocumentKeyDown)
    }
  }, [])

  return (
    <div className="flex min-h-screen bg-muted/30">
      <aside
        className={`fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r border-border bg-card transition-transform lg:static lg:translate-x-0 ${
          sidebarOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="flex h-16 items-center justify-between px-6">
          <Link to="/" className="flex items-center gap-2">
            <img src={logo} alt="MedBuddy" className="ml-8 mb-1 h-20 w-auto" />
          </Link>
          <button
              className="rounded-md p-1 text-muted-foreground hover:bg-muted lg:hidden"
            onClick={() => setSidebarOpen(false)}
            aria-label="Close sidebar"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <nav className="flex-1 space-y-1 px-3 py-4">
          {links.map((link) => {
            const isActive = location.pathname === link.path
            return (
              <Link
                key={link.path}
                to={link.path}
                onClick={() => setSidebarOpen(false)}
                className={`flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-medium transition-all ${
                  isActive
                    ? 'bg-primary text-primary-foreground shadow-md'
                    : 'text-muted-foreground hover:bg-muted hover:text-foreground'
                }`}
              >
                <link.icon className="h-[18px] w-[18px]" />
                {link.label}
              </Link>
            )
          })}
        </nav>

        <div className="border-t border-border p-4 text-xs text-muted-foreground">
          Logged in as {user?.role === 'DOCTOR' ? 'Doctor' : 'Patient'}
        </div>
      </aside>

      {sidebarOpen && (
        <div
          className="fixed inset-0 z-30 bg-foreground/30 lg:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex h-16 items-center justify-between border-b border-border bg-card px-4 sm:px-6">
          <div className="flex items-center gap-3">
            <button
              onClick={() => setSidebarOpen(true)}
              className="rounded-md p-1 text-muted-foreground hover:bg-muted lg:hidden"
              aria-label="Open sidebar"
            >
              <Menu className="h-5 w-5" />
            </button>
            {user?.role !== 'DOCTOR' && (
              <div className="relative hidden sm:block">
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <input
                  disabled
                  value=""
                  placeholder="Search"
                  className="h-10 w-72 rounded-md border border-input bg-muted/50 pl-9 pr-3 text-sm text-muted-foreground"
                />
              </div>
            )}
          </div>

          <div className="flex items-center gap-2 sm:gap-3">
            <button className="relative rounded-full p-2 hover:bg-muted" aria-label="Notifications">
              <Bell className="h-5 w-5 text-muted-foreground" />
              <span className="absolute right-1.5 top-1.5 h-2 w-2 rounded-full bg-primary" />
            </button>
            <div className="relative" ref={profileMenuRef}>
              <button
                type="button"
                onClick={() => setProfileMenuOpen((prev) => !prev)}
                className="flex items-center gap-2 rounded-full px-1 py-1 sm:pl-1.5 sm:pr-3 hover:bg-muted"
              >
                <UserAvatar
                  imageUrl={user?.profileImageUrl}
                  name={[user?.firstName, user?.lastName].filter(Boolean).join(' ') || user?.email}
                  fallback={initials(user)}
                  className="h-9 w-9"
                  textClassName="text-sm"
                  alt="Profile image"
                />
                <div className="hidden sm:block text-left">
                  <p className="text-sm font-semibold leading-tight">
                    {[user?.firstName, user?.lastName].filter(Boolean).join(' ') || user?.email}
                  </p>
                  <p className="text-xs text-muted-foreground">{user?.role === 'DOCTOR' ? 'Doctor' : 'Patient'}</p>
                </div>
                <ChevronDown className="hidden h-4 w-4 text-muted-foreground sm:block" />
              </button>

              {profileMenuOpen && (
                <div className="absolute right-0 z-30 mt-2 w-40 rounded-lg border border-border bg-card p-1 shadow-elevated">
                  <button
                    type="button"
                    onClick={openSettings}
                    className="w-full rounded-md px-3 py-2 text-left text-sm hover:bg-muted"
                  >
                    Settings
                  </button>
                  <button
                    type="button"
                    onClick={handleLogout}
                    className="w-full rounded-md px-3 py-2 text-left text-sm text-destructive hover:bg-destructive/10"
                  >
                    Sign Out
                  </button>
                </div>
              )}
            </div>
          </div>
        </header>

        <main className="flex-1 p-4 sm:p-6">
          {(title || subtitle || actions) && (
            <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                {title && <h1 className="text-2xl font-bold md:text-3xl">{title}</h1>}
                {subtitle && <p className="mt-1 text-muted-foreground font-body">{subtitle}</p>}
              </div>
              {actions ? <div>{actions}</div> : null}
            </div>
          )}
          {children}
        </main>
      </div>
    </div>
  )
}

