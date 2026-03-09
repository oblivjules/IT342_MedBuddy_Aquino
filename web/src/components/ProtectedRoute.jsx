import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

/**
 * ProtectedRoute
 *
 * Checks that the user is authenticated and, optionally, that their role is
 * included in `allowedRoles`. If either check fails, the user is redirected:
 *   - Not authenticated  → /login  (with `from` state so we can redirect back)
 *   - Wrong role         → their role-specific dashboard
 *
 * Usage:
 *   <ProtectedRoute allowedRoles={['PATIENT']}>
 *     <PatientDashboard />
 *   </ProtectedRoute>
 */
function ProtectedRoute({ children, allowedRoles = [] }) {
  const { isAuthenticated, user } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    // Preserve the attempted URL so Login can redirect back after success
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  if (allowedRoles.length > 0 && !allowedRoles.includes(user?.role)) {
    // Authenticated but wrong role — send to their own dashboard
    const redirect =
      user?.role === 'DOCTOR' ? '/doctor/dashboard' : '/patient/dashboard'
    return <Navigate to={redirect} replace />
  }

  return children
}

export default ProtectedRoute
