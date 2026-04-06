import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useEffect } from 'react'
import { AuthProvider } from './context/AuthContext'
import { ToastProvider } from './context/ToastContext'
import ProtectedRoute from './components/ProtectedRoute'
import axiosInstance from './api/axiosInstance'
import Login from './pages/Login'
import Register from './pages/Register'
import PatientDashboard from './pages/patient/Dashboard'
import DoctorDashboard from './pages/doctor/Dashboard'
import PatientFindDoctor from './pages/patient/FindDoctor'
import PatientBookAppointment from './pages/patient/BookAppointment'
import PatientDoctorProfile from './pages/patient/DoctorProfile'
import PatientAppointments from './pages/patient/Appointments'
import DoctorAppointments from './pages/doctor/Appointments'
import DoctorSchedule from './pages/doctor/Schedule'
import LandingPage from './pages/LandingPage'

function App() {
  // Ping backend on load so cold starts happen before first user action.
  useEffect(() => {
    axiosInstance.get('/api/auth/health').catch(() => { })
  }, [])

  return (
    <BrowserRouter>
      <AuthProvider>
        <ToastProvider>
        <Routes>
          {/* Public routes */}
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />

          {/* Protected routes — role-based */}
          <Route
            path="/patient/dashboard"
            element={
              <ProtectedRoute allowedRoles={['PATIENT']}>
                <PatientDashboard />
              </ProtectedRoute>
            }
          />
          <Route
            path="/doctor/dashboard"
            element={
              <ProtectedRoute allowedRoles={['DOCTOR']}>
                <DoctorDashboard />
              </ProtectedRoute>
            }
          />

          {/* Patient feature routes */}
          <Route
            path="/patient/find-doctor"
            element={
              <ProtectedRoute allowedRoles={['PATIENT']}>
                <PatientFindDoctor />
              </ProtectedRoute>
            }
          />
          <Route
            path="/patient/book/:doctorId"
            element={
              <ProtectedRoute allowedRoles={['PATIENT']}>
                <PatientBookAppointment />
              </ProtectedRoute>
            }
          />
          <Route
            path="/patient/doctor/:doctorId"
            element={
              <ProtectedRoute allowedRoles={['PATIENT']}>
                <PatientDoctorProfile />
              </ProtectedRoute>
            }
          />
          <Route
            path="/patient/appointments"
            element={
              <ProtectedRoute allowedRoles={['PATIENT']}>
                <PatientAppointments />
              </ProtectedRoute>
            }
          />

          {/* Doctor feature routes */}
          <Route
            path="/doctor/appointments"
            element={
              <ProtectedRoute allowedRoles={['DOCTOR']}>
                <DoctorAppointments />
              </ProtectedRoute>
            }
          />
          <Route
            path="/doctor/schedule"
            element={
              <ProtectedRoute allowedRoles={['DOCTOR']}>
                <DoctorSchedule />
              </ProtectedRoute>
            }
          />

          {/* Landing page */}
          <Route path="/" element={<LandingPage />} />

          {/* Catch-all */}
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
