import axiosInstance from './axiosInstance'

export async function getFilesByAppointment(appointmentId) {
  const { data } = await axiosInstance.get(`/api/files/appointment/${appointmentId}`, {
    skipCache: true,
  })
  return data
}

export async function uploadAppointmentFile(recordId, file) {
  const formData = new FormData()
  formData.append('recordId', String(recordId))
  formData.append('file', file)

  const { data } = await axiosInstance.post('/api/files/upload', formData, {
    timeout: 120000,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })

  return data
}

export async function getFileAccessUrl(fileId) {
  const { data } = await axiosInstance.get(`/api/files/item/${fileId}/url`)
  return data?.url
}