import { useEffect, useMemo, useState } from 'react'
import {
  CheckCircle,
  Clock,
  RotateCcw,
  TrendingUp,
} from 'lucide-react'
import DashboardLayout from '../../components/DashboardLayout'
import { getMyAppointments } from '../../api/appointmentApi'
import { getPaymentByAppointment, initiatePayment, handlePaymentError } from '../../api/paymentApi'
import { useToast } from '../../hooks/useToast'

const RESERVATION_FEE = 100

function normalizeStatus(status) {
  const value = String(status || '').toUpperCase()
  if (value === 'PAID' || value === 'COMPLETED') return 'Paid'
  if (value === 'PARTIAL') return 'Partial'
  if (value === 'REFUNDED') return 'Refunded'
  return 'Pending'
}

function formatDoctorName(doctor) {
  const fullName = [doctor?.firstName, doctor?.lastName].filter(Boolean).join(' ')
  return fullName || doctor?.email || 'Doctor'
}

export default function PatientBilling() {
  const { success } = useToast()
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let mounted = true

    async function loadBilling() {
      try {
        const appointments = await getMyAppointments()
        const appointmentList = Array.isArray(appointments) ? appointments : []
        const responses = []

        // Fetch payment records sequentially to avoid request storms
        for (const appointment of appointmentList) {
          try {
            const payment = await getPaymentByAppointment(appointment.id)
            responses.push({
              ...payment,
              appointmentId: appointment.id,
              appointmentDateTime: appointment.dateTime,
              doctor: appointment.doctor,
            })
          } catch {
            // If no payment record exists yet (404), create a pending payment entry
            responses.push({
              id: null,
              appointmentId: appointment.id,
              appointmentDateTime: appointment.dateTime,
              doctor: appointment.doctor,
              feeAmount: RESERVATION_FEE,
              paymentStatus: 'PENDING',
            })
          }
        }

        if (mounted) {
          setItems(responses)
        }
      } catch {
        if (mounted) setError('Failed to load billing records.')
      } finally {
        if (mounted) setLoading(false)
      }
    }

    loadBilling()
    return () => {
      mounted = false
    }
  }, [])

  const invoiceRows = useMemo(
    () => items.map((item) => {
      const total = Number(item.feeAmount || RESERVATION_FEE)
      const paid = Number(item.paidAmount || 0)
      const isRefunded = String(item.paymentStatus || '').toUpperCase() === 'REFUNDED'
      const remainingRaw = Number(item.remainingAmount ?? (total - paid))
      const remaining = isRefunded ? 0 : (remainingRaw > 0 ? remainingRaw : 0)

      return {
        ...item,
        total,
        paid,
        remaining,
        refunded: isRefunded ? total : 0,
        amount: remaining,
        status: normalizeStatus(item.paymentStatus),
        date: item.appointmentDateTime ? new Date(item.appointmentDateTime).toLocaleDateString() : 'N/A',
        doctorName: formatDoctorName(item.doctor),
        description: item.description || 'Consultation Bill',
      }
    }),
    [items],
  )

  const totalPending = invoiceRows
    .reduce((sum, invoice) => sum + invoice.remaining, 0)
  const totalPaid = invoiceRows
    .reduce((sum, invoice) => sum + invoice.paid, 0)
  const totalRefunded = invoiceRows
    .reduce((sum, invoice) => sum + invoice.refunded, 0)
  const totalAmount = invoiceRows
    .reduce((sum, invoice) => sum + invoice.total, 0)
  const paidPercent = totalAmount > 0 ? Math.round((totalPaid / totalAmount) * 100) : 0

  async function handlePayNow(item) {
    setError('')
    console.log('[DEBUG] handlePayNow called with item:', item)
    try {
      // Store appointmentId in session storage so cancel/failed pages can check payment status
      sessionStorage.setItem('currentPaymentAppointmentId', item.appointmentId)
      
      // Initiate PayMongo hosted checkout and redirect
      console.log('[DEBUG] Calling initiatePayment with appointmentId:', item.appointmentId, 'amount:', item.total, 'description:', item.description)
      const initResp = await initiatePayment({
        appointmentId: item.appointmentId,
        amount: item.total,
        description: item.description
      })
      const checkoutUrl = initResp?.checkoutUrl || initResp?.checkout_url
      if (checkoutUrl) {
        window.location.href = checkoutUrl
        return
      }

      success('Payment flow started.')
    } catch (err) {
      console.error('[DEBUG] Payment initiation error:', err)
      sessionStorage.removeItem('currentPaymentAppointmentId')
      
      // Handle the error with better messaging
      const errorResult = await handlePaymentError(err, item.appointmentId)
      
      if (errorResult.isError && errorResult.shouldRetry) {
        // Show more detailed error message to user
        const errorMessage = err?.message || 'Unable to start payment. Please try again.'
        setError(errorMessage)
        
        // Optionally redirect to failed page after a delay
        setTimeout(() => {
          window.location.href = errorResult.redirectTo
        }, 3000)
      } else {
        setError('Unable to start payment right now. Please try again.')
      }
    }
  }

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold">Billing &amp; Payments</h1>
          <p className="text-muted-foreground font-body">Track invoices and manage payments</p>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <div className="rounded-2xl border border-border bg-card p-5 shadow-card">
            <div className="mb-3 inline-flex rounded-xl bg-accent-soft p-3">
              <Clock className="h-5 w-5 text-accent" />
            </div>
            <p className="text-sm text-muted-foreground font-body">Total Pending</p>
            <p className="mt-1 text-3xl font-bold text-accent">PHP {totalPending.toFixed(2)}</p>
          </div>

          <div className="rounded-2xl border border-border bg-card p-5 shadow-card">
            <div className="mb-3 inline-flex rounded-xl bg-primary-soft p-3">
              <CheckCircle className="h-5 w-5 text-primary" />
            </div>
            <p className="text-sm text-muted-foreground font-body">Total Paid</p>
            <p className="mt-1 text-3xl font-bold text-primary">PHP {totalPaid.toFixed(2)}</p>
          </div>

          <div className="rounded-2xl border border-border bg-card p-5 shadow-card">
            <div className="mb-3 inline-flex rounded-xl bg-destructive/10 p-3">
              <RotateCcw className="h-5 w-5 text-destructive" />
            </div>
            <p className="text-sm text-muted-foreground font-body">Total Refunded</p>
            <p className="mt-1 text-3xl font-bold text-destructive">PHP {totalRefunded.toFixed(2)}</p>
          </div>

          <div className="rounded-2xl border border-border bg-card p-5 shadow-card">
            <div className="mb-3 inline-flex rounded-xl bg-teal-soft p-3">
              <TrendingUp className="h-5 w-5 text-teal" />
            </div>
            <p className="text-sm text-muted-foreground font-body">Payment Progress</p>
            <p className="mt-1 text-xl font-bold">{paidPercent}% paid</p>
            <div className="mt-2 h-2 overflow-hidden rounded-full bg-muted">
              <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${paidPercent}%` }} />
            </div>
          </div>
        </div>

        <section className="rounded-2xl border border-border bg-card shadow-card">
          <div className="border-b border-border p-5">
            <h2 className="font-semibold">Invoice History</h2>
          </div>

          {loading && <p className="p-5 text-sm text-muted-foreground">Loading billing data...</p>}
          {error && <p className="px-5 pb-4 text-sm text-destructive">{error}</p>}
          {!loading && invoiceRows.length === 0 && (
            <p className="p-5 text-sm text-muted-foreground">No payment records.</p>
          )}

          <div className="divide-y divide-border">
            {invoiceRows.map((invoice) => (
              <div key={invoice.id || `appt-${invoice.appointmentId}`} className="flex flex-col justify-between gap-4 p-4 transition-colors hover:bg-muted/30 sm:flex-row sm:items-center sm:p-5">
                <div className="flex items-center gap-3">
                  <div className={`rounded-xl p-2.5 ${invoice.status === 'Paid' ? 'bg-primary-soft' : 'bg-accent-soft'}`}>
                      {invoice.status === 'Paid'
                      ? <CheckCircle className="h-4 w-4 text-primary" />
                        : invoice.status === 'Refunded'
                          ? <RotateCcw className="h-4 w-4 text-destructive" />
                          : <Clock className="h-4 w-4 text-accent" />}
                  </div>
                  <div>
                    <p className="font-medium">{invoice.description}</p>
                    <p className="text-sm text-muted-foreground font-body">
                      {invoice.id ? `Payment #${invoice.id}` : `Appointment #${invoice.appointmentId}`} • Dr. {invoice.doctorName} • {invoice.date}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-3">
                  <div className="text-right">
                    <p className="font-semibold">
                      {invoice.status === 'Refunded'
                        ? `Refunded: PHP ${invoice.refunded.toFixed(2)}`
                        : `Remaining: PHP ${invoice.remaining.toFixed(2)}`}
                    </p>
                    <p className="text-xs text-muted-foreground">Total: PHP {invoice.total.toFixed(2)} • Paid: PHP {invoice.paid.toFixed(2)}</p>
                    <span className={`text-xs font-medium ${invoice.status === 'Paid' ? 'text-primary' : invoice.status === 'Refunded' ? 'text-destructive' : 'text-accent'}`}>
                      {invoice.status}
                    </span>
                  </div>
                  {invoice.status !== 'Paid' && invoice.status !== 'Refunded' && (
                    <button
                      type="button"
                      onClick={() => handlePayNow(invoice)}
                      className="inline-flex h-9 items-center justify-center rounded-md bg-primary px-3 text-sm font-semibold text-primary-foreground shadow-sm hover:bg-primary/90"
                    >
                      Pay Now
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        </section>
      </div>
    </DashboardLayout>
  )
}

