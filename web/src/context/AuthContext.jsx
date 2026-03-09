import { createContext, useCallback, useEffect, useMemo, useState } from 'react'

/**
 * AuthContext
 *
 * Provides the current authenticated user and token to the entire app.
 * Persists the token and user data to localStorage so the session survives
 * page refreshes.
 *
 * Shape of `user` object (decoded from the JWT payload or returned by the API):
 *   { id, email, role: 'PATIENT' | 'DOCTOR' }
 *
 * Shape of `AuthContextValue`:
 *   {
 *     user: object | null,
 *     token: string | null,
 *     login: (token: string, user: object) => void,
 *     logout: () => void,
 *     isAuthenticated: boolean,
 *   }
 */
export const AuthContext = createContext(null)

const TOKEN_KEY = 'medbuddy_token'
const USER_KEY = 'medbuddy_user'

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY))
  const [user, setUser] = useState(() => {
    try {
      const stored = localStorage.getItem(USER_KEY)
      return stored ? JSON.parse(stored) : null
    } catch {
      return null
    }
  })

  // Keep localStorage in sync whenever token or user changes
  useEffect(() => {
    if (token) {
      localStorage.setItem(TOKEN_KEY, token)
    } else {
      localStorage.removeItem(TOKEN_KEY)
    }
  }, [token])

  useEffect(() => {
    if (user) {
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    } else {
      localStorage.removeItem(USER_KEY)
    }
  }, [user])

  /**
   * Call this after a successful login / register API response.
   * @param {string} newToken - JWT returned by the backend
   * @param {object} newUser  - User object returned by the backend
   */
  const login = useCallback((newToken, newUser) => {
    setToken(newToken)
    setUser(newUser)
  }, [])

  /** Clear session and redirect to /login */
  const logout = useCallback(() => {
    setToken(null)
    setUser(null)
  }, [])

  const value = useMemo(
    () => ({
      user,
      token,
      login,
      logout,
      isAuthenticated: Boolean(token),
    }),
    [user, token, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
