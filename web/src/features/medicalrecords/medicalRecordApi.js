import axiosInstance from '../../api/axiosInstance'

export async function getMedicalRecordByAppointment(appointmentId) {
  const { data } = await axiosInstance.get(`/api/medical-records/appointment/${appointmentId}`, {
    skipCache: true,
  })
  return data
}

export async function createMedicalRecord(payload) {
  const { data } = await axiosInstance.post('/api/medical-records', payload)
  return data
}

export async function updateMedicalRecord(id, payload) {
  const { data } = await axiosInstance.put(`/api/medical-records/${id}`, payload)
  return data
}

export async function deleteMedicalRecord(id) {
  await axiosInstance.delete(`/api/medical-records/${id}`)
}

