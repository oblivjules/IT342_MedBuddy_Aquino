import axiosInstance from './axiosInstance'

/**
 * Fetch drug information for a medical record.
 * 
 * @param {number} recordId - The medical record ID
 * @returns {Promise} Response containing { available: boolean, data: DrugInfoDto | null }
 */
export async function getDrugInfo(recordId) {
  const { data } = await axiosInstance.get(`/api/medical-records/${recordId}/drug-info`, {
    skipCache: true,
  })
  return data
}
