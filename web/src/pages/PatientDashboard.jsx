import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

/**
 * PatientDashboard
 *
 * Accessible only to users with role = 'PATIENT'.
 * This is a placeholder — replace the content sections with real API-driven
 * components (appointments, doctors list, etc.) as features are built out.
 */
function PatientDashboard() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-background">
      {/* Navbar */}
      <header className="sticky top-0 z-50 border-b border-border/50 bg-background/80 backdrop-blur-lg">
        <div className="container flex h-16 items-center justify-between">
          <span className="text-xl font-extrabold text-gradient">MedBuddy</span>
          <div className="flex items-center gap-3">
            <span className="hidden text-sm text-muted-foreground sm:block">{user?.email}</span>
            <span className="inline-flex items-center rounded-full border border-primary/30 bg-primary/10 px-2.5 py-0.5 text-xs font-semibold text-primary">Patient</span>
            <button
              onClick={handleLogout}
              className="inline-flex h-9 items-center justify-center rounded-md border border-border bg-background px-3 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            >
              Sign out
            </button>
          </div>
        </div>
      </header>

      {/* Hero welcome */}
      <section className="bg-hero-gradient">
        <div className="container py-10 opacity-0 animate-fade-in">
          <h2 className="text-3xl font-extrabold md:text-4xl">Welcome back!</h2>
          <p className="mt-1 text-muted-foreground font-body">{user?.email}</p>
        </div>
      </section>

      {/* Dashboard grid */}
      <main className="container py-10">
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          <DashboardCard title="My Appointments" description="View and manage your upcoming appointments." to="/patient/appointments" />
          <DashboardCard title="Find a Doctor" description="Search for available doctors by specialty." to="/patient/find-doctor" />
          <DashboardCard title="Medical Records" description="Access your consultation history." to="/patient/appointments" />
          <DashboardCard title="Profile Settings" description="Update your personal information." to="/patient/appointments" />
        </div>
      </main>
    </div>
  )
}

function DashboardCard({ title, description, to }) {
  return (
    <div className="rounded-xl border border-border bg-card p-6 shadow-card hover:shadow-elevated transition-shadow opacity-0 animate-fade-in">
      <h3 className="text-base font-semibold text-card-foreground mb-2">{title}</h3>
      <p className="text-sm text-muted-foreground font-body mb-5">{description}</p>
      <Link
        to={to}
        className="inline-flex h-9 items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground no-underline transition-colors hover:bg-primary/90"
      >
        Open &rarr;
      </Link>
    </div>
  )
}

export default PatientDashboard
