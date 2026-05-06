import axiosInstance from './axiosInstance'

export async function getMyProfile() {
  const { data } = await axiosInstance.get('/api/users/me')
  return data
}

export async function updateMyProfile(payload) {
  const { data } = await axiosInstance.patch('/api/users/me', payload)
  return data
}

export async function uploadDoctorProfileImage(file) {
  const formData = new FormData()
  formData.append('file', file)

  const { data } = await axiosInstance.post('/api/users/me/profile-image', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
  return data
}

