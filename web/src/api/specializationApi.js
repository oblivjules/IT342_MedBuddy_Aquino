import axiosInstance from './axiosInstance'

export async function getSpecializations() {
  const { data } = await axiosInstance.get('/api/specializations')
  return data
}

