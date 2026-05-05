import axiosInstance from './axiosInstance'

export async function getRatingsByDoctor(doctorId) {
  const { data } = await axiosInstance.get(`/api/ratings/doctor/${doctorId}`)
  return data
}

export async function getDoctorAverageRating(doctorId) {
  const { data } = await axiosInstance.get(`/api/ratings/doctor/${doctorId}/average`)
  return Number(data?.average || 0)
}

export async function getRatingsByDoctorLegacy(doctorId) {
  const { data } = await axiosInstance.get(`/api/feedback/doctor/${doctorId}`)
  return data
}

export async function getRatingsByPatient(patientId) {
  const { data } = await axiosInstance.get(`/api/ratings/patient/${patientId}`)
  return data
}

export async function getFeedbackByAppointment(appointmentId) {
  const { data } = await axiosInstance.get(`/api/ratings/appointment/${appointmentId}`)
  return data
}

export async function submitRating(payload) {
  const { data } = await axiosInstance.post('/api/ratings', payload)
  return data
}

export async function deleteRating(id) {
  await axiosInstance.delete(`/api/ratings/${id}`)
}

