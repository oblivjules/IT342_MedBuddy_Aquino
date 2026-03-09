import axiosInstance from './axiosInstance'

/**
 * All user-related API calls.
 */

/** Fetch all registered doctors — used by the "Find a Doctor" patient feature. */
export async function getDoctors() {
    const { data } = await axiosInstance.get('/api/users/doctors')
    return data
}
