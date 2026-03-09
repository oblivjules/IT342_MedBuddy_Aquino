import { useContext } from 'react'
import { AuthContext } from '../context/AuthContext'

/**
 * useAuth — convenience hook for consuming AuthContext.
 *
 * Usage:
 *   const { user, token, login, logout, isAuthenticated } = useAuth()
 *
 * Must be used inside a component wrapped by <AuthProvider>.
 */
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an <AuthProvider>')
  }
  return context
}
