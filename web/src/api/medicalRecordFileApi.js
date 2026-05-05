import axiosInstance from './axiosInstance'

export async function uploadMyMedicalRecordFile(file, description = '') {
  const formData = new FormData()
  formData.append('file', file)
  if (description) {
    formData.append('description', description)
  }

  const { data } = await axiosInstance.post('/api/medical-record-files/my', formData, {
    timeout: 120000,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })

  return data
}

export async function uploadMedicalRecordFileForPatient(patientId, file, description = '') {
  const formData = new FormData()
  formData.append('file', file)
  if (description) {
    formData.append('description', description)
  }

  const { data } = await axiosInstance.post(`/api/medical-record-files/patients/${patientId}`, formData, {
    timeout: 120000,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })

  return data
}

export async function getMedicalRecordFilesForPatient(patientId) {
  const { data } = await axiosInstance.get(`/api/medical-record-files/patients/${patientId}`, {
    skipCache: true,
  })
  return data
}

export async function getMedicalRecordFileAccessUrl(fileId) {
  const { data } = await axiosInstance.get(`/api/medical-record-files/${fileId}/url`)
  return data?.url
}
