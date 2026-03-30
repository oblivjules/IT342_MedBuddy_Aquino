import axios from 'axios'

const PUBLIC_PATHS = [
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/health',
  '/api/specializations',
]

function isPublicRequest(url = '') {
  return PUBLIC_PATHS.some((path) => url === path || url.startsWith(`${path}/`))
}

// Base URL is read from Vite's environment variable.
// Set VITE_API_BASE_URL in your .env file.
const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request interceptor — attach JWT Bearer token to every outgoing request
axiosInstance.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('medbuddy_token')
    if (token && !isPublicRequest(config.url)) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// Response interceptor — handle 401 Unauthorized globally
axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Token expired or invalid — clear storage and redirect to login
      localStorage.removeItem('medbuddy_token')
      localStorage.removeItem('medbuddy_user')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export default axiosInstance
