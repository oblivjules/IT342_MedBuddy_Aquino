import React, { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import DashboardLayout from '../components/DashboardLayout'
import { XCircle, RotateCcw, Home } from 'lucide-react'
import { useToast } from '../hooks/useToast'
import { getPaymentStatus } from '../api/paymentApi'

export default function PaymentCancel() {
  const navigate = useNavigate()
  const { warning } = useToast()
  const [showDetails, setShowDetails] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [isFailed, setIsFailed] = useState(false)

  useEffect(() => {
    const checkPaymentStatus = async () => {
      try {
        // Get appointmentId from sessionStorage (set during payment initiation)
        const appointmentId = sessionStorage.getItem('currentPaymentAppointmentId')
        
        if (appointmentId) {
          const status = await getPaymentStatus(appointmentId)
          
          // If payment failed (expired, declined, etc), redirect to failure page
          if (status === 'FAILED') {
            setIsFailed(true)
            warning('Your payment failed. Please try again.')
            setTimeout(() => {
              sessionStorage.removeItem('currentPaymentAppointmentId')
              navigate('/payment/failed?reason=Payment%20expired%20or%20was%20declined')
            }, 1500)
            return
          }
        }
        
        // Otherwise, it's a normal cancellation
        warning('Payment was cancelled. Your appointment is still available for payment.')
        setIsFailed(false)
      } catch (error) {
        console.error('[DEBUG] Error checking payment status:', error)
        // Default to cancel message on error
        warning('Payment was cancelled. Your appointment is still available for payment.')
        setIsFailed(false)
      } finally {
        setIsLoading(false)
      }
    }

    checkPaymentStatus()
  }, [navigate, warning])

  if (isLoading) {
    return (
      <DashboardLayout>
        <div className="mx-auto max-w-xl space-y-6 p-6 text-center">
          <div className="flex justify-center">
            <div className="h-12 w-12 animate-spin rounded-full border-4 border-amber-200 border-t-amber-600" />
          </div>
          <p className="text-muted-foreground">Checking payment status...</p>
        </div>
      </DashboardLayout>
    )
  }

  if (isFailed) {
    return null // Will redirect to failed page
  }

  const handleRetry = () => {
    navigate('/patient/billing')
  }

  const handleViewAppointments = () => {
    navigate('/patient/appointments')
  }

  const handleDashboard = () => {
    navigate('/patient/dashboard')
  }

  return (
    <DashboardLayout>
      <div className="mx-auto max-w-xl space-y-6 p-6">
        <div className="text-center">
          <div className="mb-4 flex justify-center">
            <div className="rounded-full bg-amber-50 p-4">
              <XCircle className="h-8 w-8 text-amber-600" />
            </div>
          </div>
          <h1 className="text-2xl font-bold">Payment Cancelled</h1>
          <p className="mt-2 text-muted-foreground">
            You cancelled the payment. Your appointment and invoice remain pending.
          </p>
        </div>

        <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
          <p className="text-sm text-amber-900">
            <span className="font-semibold">Note: </span>
            Your appointment will be held temporarily. To complete your booking, please complete the payment.
          </p>
        </div>

        <div className="space-y-3">
          <button
            onClick={handleRetry}
            className="flex w-full items-center justify-center gap-2 rounded-lg bg-primary px-6 py-3 font-medium text-primary-foreground hover:bg-primary/90 transition-colors"
          >
            <RotateCcw className="h-4 w-4" />
            Retry Payment
          </button>
          <button
            onClick={handleViewAppointments}
            className="w-full rounded-lg border border-border bg-card px-6 py-3 font-medium hover:bg-muted transition-colors"
          >
            View My Appointments
          </button>
          <button
            onClick={handleDashboard}
            className="flex w-full items-center justify-center gap-2 rounded-lg border border-border bg-card px-6 py-3 font-medium hover:bg-muted transition-colors"
          >
            <Home className="h-4 w-4" />
            Back to Dashboard
          </button>
        </div>

        <div>
          <button
            onClick={() => setShowDetails(!showDetails)}
            className="text-sm font-semibold text-primary hover:underline"
          >
            {showDetails ? 'Hide' : 'Show'} Payment Options
          </button>
          {showDetails && (
            <div className="mt-3 space-y-2 rounded-lg bg-muted p-4 text-left">
              <p className="text-sm font-semibold">Payment Options:</p>
              <ul className="space-y-2 text-sm text-muted-foreground">
                <li>• Try the payment again with the same or different card</li>
                <li>• Use a different payment method</li>
                <li>• Contact your bank if you're experiencing issues</li>
                <li>• Contact support if you need assistance</li>
              </ul>
            </div>
          )}
        </div>
      </div>
    </DashboardLayout>
  )
}
