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
import FindDoctor from './pages/patient/FindDoctor'
import BookAppointment from './pages/patient/BookAppointment'
import PatientDoctorProfile from './pages/patient/DoctorProfile'
import PatientAppointments from './pages/patient/Appointments'
import PatientMedicalRecords from './pages/patient/MedicalRecords'
import PatientBilling from './pages/patient/Billing'
import PatientFeedback from './pages/patient/Feedback'
import PatientSettings from './pages/patient/Settings'
import DoctorAppointments from './pages/doctor/Appointments'
import DoctorSchedule from './pages/doctor/Schedule'
import DoctorPatientRecords from './pages/doctor/PatientRecords'
import DoctorSettings from './pages/doctor/Settings'
import LandingPage from './pages/LandingPage'
import OAuthCallback from './pages/OAuthCallback'

function App() {
  // Ping the backend on load so Render's free-tier instance wakes up
  // before the user makes their first real request.
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
                <FindDoctor />
              </ProtectedRoute>
            }
          />
          <Route
            path="/patient/book/:doctorId"
            element={
              <ProtectedRoute allowedRoles={['PATIENT']}>
                <BookAppointment />
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
          <Route
            path="/patient/medical-records"
            element={
              <ProtectedRoute allowedRoles={['PATIENT']}>
                <PatientMedicalRecords />
              </ProtectedRoute>
            }
          />
          <Route
            path="/patient/billing"
            element={
              <ProtectedRoute allowedRoles={['PATIENT']}>
                <PatientBilling />
              </ProtectedRoute>
            }
          />
          <Route
            path="/patient/feedback"
            element={
              <ProtectedRoute allowedRoles={['PATIENT']}>
                <PatientFeedback />
              </ProtectedRoute>
            }
          />
          <Route
            path="/patient/settings"
            element={
              <ProtectedRoute allowedRoles={['PATIENT']}>
                <PatientSettings />
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
          <Route
            path="/doctor/patient-records"
            element={
              <ProtectedRoute allowedRoles={['DOCTOR']}>
                <DoctorPatientRecords />
              </ProtectedRoute>
            }
          />
          <Route
            path="/doctor/settings"
            element={
              <ProtectedRoute allowedRoles={['DOCTOR']}>
                <DoctorSettings />
              </ProtectedRoute>
            }
          />

          {/* Landing page */}
          <Route path="/" element={<LandingPage />} />

          {/* Google OAuth2 callback — public, no auth required */}
          <Route path="/oauth-callback" element={<OAuthCallback />} />

          {/* Catch-all */}
          <Route path="*" element={<Navigate to="/login" replace />} />
          </Routes>
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
