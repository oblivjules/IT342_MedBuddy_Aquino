import { useEffect, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

/**
 * OAuthCallback
 *
 * Landing page after an OAuth2 (Google) login succeeds on the backend.
 *
 * The backend's OAuth2SuccessHandler redirects here with:
 *   /oauth-callback?token=<JWT>&user=<URL-encoded JSON>
 *
 * Steps:
 *   1. Parse token + user from the URL search params.
 *   2. Call login() from AuthContext to persist them.
 *   3. Redirect to the appropriate role dashboard.
 *   4. On any error, redirect to /login with an error flag.
 */
function OAuthCallback() {
    const [searchParams] = useSearchParams()
    const { login } = useAuth()
    const navigate = useNavigate()
    const handled = useRef(false)

    useEffect(() => {
        // Guard against React StrictMode double-invocation
        if (handled.current) return
        handled.current = true

        const token = searchParams.get('token')
        const userRaw = searchParams.get('user')

        if (!token || !userRaw) {
            navigate('/login?error=oauth_failed', { replace: true })
            return
        }

        try {
            // URLSearchParams.get() already URL-decodes the value
            const user = JSON.parse(userRaw)
            login(token, user)
            sessionStorage.setItem('medbuddy_last_auth_method', 'GOOGLE')

            if (user.role === 'DOCTOR') {
                navigate('/doctor/dashboard', { replace: true })
            } else {
                navigate('/patient/dashboard', { replace: true })
            }
        } catch {
            navigate('/login?error=oauth_failed', { replace: true })
        }
    }, [searchParams, login, navigate])

    return (
        <div className="flex min-h-screen items-center justify-center bg-hero-gradient">
            <div className="text-center space-y-3">
                <div className="inline-block h-8 w-8 animate-spin rounded-full border-4 border-primary border-r-transparent" />
                <p className="text-sm text-muted-foreground">Completing sign-in…</p>
            </div>
        </div>
    )
}

export default OAuthCallback
