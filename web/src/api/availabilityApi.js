import axiosInstance from './axiosInstance'

export async function getDoctorAvailability(doctorId) {
  const { data } = await axiosInstance.get(`/api/availability/doctor/${doctorId}`)
  return data
}

export async function getDoctorAvailabilityByDate(doctorId, date) {
  const { data } = await axiosInstance.get(`/api/availability/doctor/${doctorId}/date/${date}`)
  return data
}

export async function getMyScheduleTemplate() {
  const { data } = await axiosInstance.get('/api/doctor/schedule/template')
  return data
}

export async function saveMyScheduleTemplate(days) {
  const { data } = await axiosInstance.post('/api/doctor/schedule/template', days)
  return data
}

export async function saveMyScheduleException(payload) {
  const { data } = await axiosInstance.post('/api/doctor/schedule/exception', payload)
  return data
}

export async function deleteMyScheduleException(date) {
  await axiosInstance.delete(`/api/doctor/schedule/exception/${date}`)
}

export async function getDoctorAppointmentSlotsByDate(doctorId, slotDate) {
  const { data } = await axiosInstance.get(`/api/appointment-slots/by-doctor/${doctorId}`, {
    params: { slotDate },
    skipCache: true,
  })
  return data
}

export async function upsertMyAvailability(payload) {
  const { data } = await axiosInstance.post('/api/availability', payload, {
    timeout: 120000,
  })
  return data
}

export async function deleteMyAvailability(date) {
  await axiosInstance.delete(`/api/availability/${date}`)
}

