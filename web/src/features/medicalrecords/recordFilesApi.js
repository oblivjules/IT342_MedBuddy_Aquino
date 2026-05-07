import axiosInstance from '../../api/axiosInstance'

export async function getMyUnifiedRecordFiles() {
  const { data } = await axiosInstance.get('/api/record-files/my', {
    skipCache: true,
  })
  return data
}

export async function getUnifiedRecordFilesForPatient(patientId) {
  const { data } = await axiosInstance.get(`/api/record-files/patients/${patientId}`, {
    skipCache: true,
  })
  return data
}
