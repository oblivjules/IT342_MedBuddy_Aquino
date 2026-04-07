import { getMyAppointments, updateAppointmentStatus } from '../api/appointmentApi'

export async function loadMyAppointments() {
  const data = await getMyAppointments()
  return Array.isArray(data) ? data : []
}

export async function cancelAppointment(appointmentId) {
  return updateAppointmentStatus(appointmentId, 'CANCELLED')
}

export async function confirmAppointment(appointmentId) {
  return updateAppointmentStatus(appointmentId, 'CONFIRMED')
}

export async function completeAppointment(appointmentId) {
  return updateAppointmentStatus(appointmentId, 'COMPLETED')
}
