import axios from 'axios'

const DEFAULT_API_WAIT_MS = 10000
const GET_CACHE_TTL_MS = 20000
const getCache = new Map()

function buildCacheKey(config) {
  const url = config.url || ''
  const params = config.params ? JSON.stringify(config.params) : ''
  return `${url}?${params}`
}

function shouldUseGetCache(config) {
  return !config?.skipCache
}

function makeCachedResponse(config, data) {
  return {
    data,
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
    request: { fromCache: true },
  }
}

// Base URL from Vite env (fallback to localhost for dev)
const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: DEFAULT_API_WAIT_MS,
  headers: {
    'Content-Type': 'application/json',
  },
})

// ── Request Interceptor ─────────────────────────────────────────
// Attaches JWT token ONLY when appropriate
axiosInstance.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('medbuddy_token')

    // Keep default timeout, but allow callers to request longer/shorter durations per call.
    if (typeof config.timeout !== 'number' || Number.isNaN(config.timeout)) {
      config.timeout = DEFAULT_API_WAIT_MS
    }

    const requestUrl = config.url || ''

    const isAuthRoute =
      requestUrl.includes('/api/auth/register') ||
      requestUrl.includes('/api/auth/login') ||
      requestUrl.includes('/api/auth/health') ||
      requestUrl.includes('/api/auth/oauth2')

    const method = String(config.method || 'get').toLowerCase()
    if (method === 'get' && shouldUseGetCache(config)) {
      const cacheKey = buildCacheKey(config)
      const cached = getCache.get(cacheKey)
      if (cached && Date.now() - cached.at <= GET_CACHE_TTL_MS) {
        config.adapter = async () => makeCachedResponse(config, cached.data)
      }
    }

    // Only attach token if it's valid AND not an auth route
    if (
      token &&
      token !== 'null' &&
      token !== 'undefined' &&
      !isAuthRoute
    ) {
      config.headers.Authorization = `Bearer ${token}`
    } else {
      delete config.headers.Authorization
    }

    return config

  },
  (error) => Promise.reject(error),
)

// ── Response Interceptor ────────────────────────────────────────
// Handles 401 globally (except auth endpoints)
axiosInstance.interceptors.response.use(
  (response) => {
    const method = String(response.config?.method || 'get').toLowerCase()
    if (method === 'get' && shouldUseGetCache(response.config)) {
      const cacheKey = buildCacheKey(response.config)
      getCache.set(cacheKey, { data: response.data, at: Date.now() })
    }
    return response
  },
  (error) => {
    if (error.code === 'ECONNABORTED') {
      const timeoutMs = error.config?.timeout || DEFAULT_API_WAIT_MS
      const seconds = Math.max(1, Math.round(timeoutMs / 1000))
      error.message = `Request timed out after ${seconds} seconds. Please try again.`
    }

    if (error.response?.status === 401) {
      const requestUrl = error.config?.url || ''

      const isPublicAuthEndpoint =
        requestUrl.includes('/api/auth/register') ||
        requestUrl.includes('/api/auth/login') ||
        requestUrl.includes('/api/auth/health') ||
        requestUrl.includes('/api/auth/oauth2')

      // Allow auth endpoints to handle their own errors
      if (isPublicAuthEndpoint) {
        return Promise.reject(error)
      }

      // Token expired or invalid → clear and redirect
      localStorage.removeItem('medbuddy_token')
      localStorage.removeItem('medbuddy_user')

      window.location.href = '/login'
    }

    return Promise.reject(error)

  },
)

export default axiosInstance
