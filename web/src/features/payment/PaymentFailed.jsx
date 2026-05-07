import React, { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import { AlertTriangle, RefreshCw, Home } from 'lucide-react'

export default function PaymentFailed() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [errorDetails, setErrorDetails] = useState('')
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    const checkPaymentStatus = async () => {
      try {
        // Get error details from URL params if available
        const reason = searchParams.get('reason')
        const message = searchParams.get('message')
        
        if (message) {
          setErrorDetails(message)
        } else if (reason) {
          setErrorDetails(reason)
        }
        
        // Clean up session storage
        sessionStorage.removeItem('currentPaymentAppointmentId')
      } catch (error) {
        console.error('[DEBUG] Error loading payment failure details:', error)
      } finally {
        setIsLoading(false)
      }
    }

    checkPaymentStatus()
  }, [searchParams])

  const handleRetry = () => {
    navigate('/patient/billing')
  }

  const handleGoHome = () => {
    navigate('/patient/dashboard')
  }

  if (isLoading) {
    return (
      <DashboardLayout>
        <div className="mx-auto max-w-xl space-y-6 p-6 text-center">
          <div className="flex justify-center">
            <div className="h-12 w-12 animate-spin rounded-full border-4 border-destructive/20 border-t-destructive" />
          </div>
          <p className="text-muted-foreground">Loading payment details...</p>
        </div>
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout>
      <div className="mx-auto max-w-xl space-y-6 p-6">
        <div className="text-center">
          <div className="mb-4 flex justify-center">
            <div className="rounded-full bg-destructive/10 p-4">
              <AlertTriangle className="h-8 w-8 text-destructive" />
            </div>
          </div>
          <h1 className="text-2xl font-bold text-destructive">Payment Failed</h1>
          <p className="mt-2 text-muted-foreground">
            We were unable to process your payment. Please try again or contact support if the problem persists.
          </p>
          
          {errorDetails && (
            <div className="mt-4 rounded-lg border border-destructive/20 bg-destructive/5 p-3">
              <p className="text-sm text-destructive">
                <span className="font-semibold">Error: </span>
                {errorDetails}
              </p>
            </div>
          )}

          <div className="mt-8 space-y-3">
            <button
              onClick={handleRetry}
              className="flex w-full items-center justify-center gap-2 rounded-lg bg-primary px-6 py-3 font-medium text-primary-foreground hover:bg-primary/90 transition-colors"
            >
              <RefreshCw className="h-4 w-4" />
              Retry Payment
            </button>
            <button
              onClick={handleGoHome}
              className="flex w-full items-center justify-center gap-2 rounded-lg border border-border bg-card px-6 py-3 font-medium hover:bg-muted transition-colors"
            >
              <Home className="h-4 w-4" />
              Go to Dashboard
            </button>
          </div>

          <div className="mt-8 space-y-2 rounded-lg bg-muted p-4 text-left">
            <p className="text-sm font-semibold">Troubleshooting:</p>
            <ul className="space-y-2 text-sm text-muted-foreground">
              <li>• Check your internet connection and try again</li>
              <li>• Verify your card details are correct</li>
              <li>• Ensure your card has sufficient funds</li>
              <li>• Check if your card is not blocked by your bank</li>
              <li>• Contact our support team if the issue persists</li>
            </ul>
          </div>
        </div>
      </div>
    </DashboardLayout>
  )
}
