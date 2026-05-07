import React, { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import { CheckCircle, Download, Home, FileText } from 'lucide-react'
import { useToast } from '../../hooks/useToast'

export default function PaymentSuccess() {
  const navigate = useNavigate()
  const { success } = useToast()
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    // Simulate receipt retrieval or processing
    const timer = setTimeout(() => {
      setIsLoading(false)
      success('Payment confirmed! Receipt sent to your email.')
    }, 1500)

    return () => clearTimeout(timer)
  }, [success])

  const handleViewBilling = () => {
    navigate('/patient/billing')
  }

  const handleViewAppointments = () => {
    navigate('/patient/appointments')
  }

  const handleDashboard = () => {
    navigate('/patient/dashboard')
  }

  if (isLoading) {
    return (
      <DashboardLayout>
        <div className="mx-auto max-w-xl space-y-6 p-6 text-center">
          <div className="flex justify-center">
            <div className="h-12 w-12 animate-spin rounded-full border-4 border-primary/20 border-t-primary" />
          </div>
          <p className="text-muted-foreground">Processing your payment...</p>
        </div>
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout>
      <div className="mx-auto max-w-xl space-y-6 p-6">
        <div className="text-center">
          <div className="mb-4 flex justify-center">
            <div className="rounded-full bg-primary/10 p-4">
              <CheckCircle className="h-8 w-8 text-primary" />
            </div>
          </div>
          <h1 className="text-2xl font-bold text-primary">Payment Successful!</h1>
          <p className="mt-2 text-muted-foreground">
            Your payment has been processed successfully.
          </p>
        </div>

        <div className="rounded-lg border border-border bg-muted/30 p-4 space-y-2">
          <div className="flex items-center gap-2">
            <FileText className="h-5 w-5 text-primary" />
            <p className="text-sm font-medium">Receipt Details</p>
          </div>
          <p className="text-xs text-muted-foreground ml-7">
            A receipt has been sent to your registered email address.
          </p>
        </div>

        <div className="space-y-3">
          <button
            onClick={handleViewAppointments}
            className="w-full rounded-lg bg-primary px-6 py-3 font-medium text-primary-foreground hover:bg-primary/90 transition-colors"
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

        <div className="space-y-2 rounded-lg bg-muted p-4 text-left">
          <p className="text-sm font-semibold">What's Next?</p>
          <ul className="space-y-2 text-sm text-muted-foreground">
            <li>• Check your email for a receipt and confirmation</li>
            <li>• Your appointment is now confirmed</li>
            <li>• You'll receive a reminder before your appointment</li>
            <li>• Access your medical records anytime</li>
          </ul>
        </div>
      </div>
    </DashboardLayout>
  )
}
