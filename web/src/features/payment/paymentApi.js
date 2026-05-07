import axiosInstance from '../../api/axiosInstance'

export async function getPaymentByAppointment(appointmentId) {
  const { data } = await axiosInstance.get(`/api/payments/appointment/${appointmentId}`, {
    skipCache: true,
  })
  return data
}

export async function getPaymentById(id) {
  const { data } = await axiosInstance.get(`/api/payments/payment/${id}`)
  return data
}

export async function getPaymentStatus(appointmentId) {
  try {
    const payment = await getPaymentByAppointment(appointmentId)
    return payment?.paymentStatus || null
  } catch (error) {
    console.error('[DEBUG] Error fetching payment status:', error)
    return null
  }
}

export async function createPayment(payload) {
  const { data } = await axiosInstance.post('/api/payments', payload)
  return data
}

export async function initiatePayment(payload) {
  console.log('[DEBUG] initiatePayment called with payload:', payload)
  try {
    const requestBody = {
      appointmentId: payload.appointmentId,
      amount: payload.amount,
      description: payload.description
    }
    console.log('[DEBUG] Sending request body:', requestBody)
    const { data } = await axiosInstance.post('/api/payments/initiate', requestBody)
    console.log('[DEBUG] initiatePayment response:', data)
    return data
  } catch (error) {
    const errorMessage = error.response?.data?.message || error.message || 'Failed to initiate payment'
    const errorDetails = {
      status: error.response?.status,
      statusText: error.response?.statusText,
      message: errorMessage,
      responseData: error.response?.data,
      requestPayload: payload
    }
    console.error('[DEBUG] initiatePayment error:', errorDetails)
    
    // Create a custom error with helpful details
    const customError = new Error(errorMessage)
    customError.details = errorDetails
    throw customError
  }
}

export async function updatePaymentStatus(id, status) {
  const { data } = await axiosInstance.patch(`/api/payments/${id}/status`, null, {
    params: { status },
  })
  return data
}

export async function updateAppointmentTotalBill(appointmentId, totalBillAmount) {
  const { data } = await axiosInstance.patch(`/api/payments/appointment/${appointmentId}/total`, {
    totalBillAmount,
  })
  return data
}

export async function handlePaymentError(error, appointmentId) {
  const message = error?.message || 'An error occurred during payment'
  const details = error?.details
  
  // Log error for debugging
  console.error('[DEBUG] Payment error handled:', { message, details, appointmentId })
  
  // Return error info that can be used for UI display
  return {
    isError: true,
    message,
    details,
    shouldRetry: true,
    redirectTo: `/payment/failed?message=${encodeURIComponent(message)}&appointmentId=${appointmentId}`
  }
}
