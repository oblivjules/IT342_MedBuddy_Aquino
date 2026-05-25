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
 *     updateUser: (nextUser: object) => void,
 *     logout: () => void,
 *     isAuthenticated: boolean,
 *   }
 */
export const AuthContext = createContext(null)

const TOKEN_KEY = 'medbuddy_token'
const USER_KEY = 'medbuddy_user'

function readStoredToken() {
  const value = localStorage.getItem(TOKEN_KEY)
  return value && value !== 'null' && value !== 'undefined' ? value : null
}

function readStoredUser() {
  try {
    const stored = localStorage.getItem(USER_KEY)
    return stored ? JSON.parse(stored) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(readStoredToken)
  const [user, setUser] = useState(readStoredUser)

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

  // Keep auth state in sync when another browser tab logs in/out.
  useEffect(() => {
    function handleStorage(event) {
      if (event.key && event.key !== TOKEN_KEY && event.key !== USER_KEY) {
        return
      }
      setToken(readStoredToken())
      setUser(readStoredUser())
    }

    window.addEventListener('storage', handleStorage)
    return () => window.removeEventListener('storage', handleStorage)
  }, [])

  /**
   * Call this after a successful login / register API response.
   * @param {string} newToken - JWT returned by the backend
   * @param {object} newUser  - User object returned by the backend
   */
  const login = useCallback((newToken, newUser) => {
    setToken(newToken)
    setUser(newUser)
  }, [])

  const updateUser = useCallback((nextUser) => {
    setUser(nextUser)
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
      updateUser,
      logout,
      isAuthenticated: Boolean(token && user),
    }),
    [user, token, login, updateUser, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
