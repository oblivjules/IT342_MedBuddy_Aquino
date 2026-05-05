import axiosInstance from './axiosInstance'

const DOCTORS_CACHE_KEY = 'medbuddy_doctors_cache'

/**
 * All user-related API calls.
 */

/** Clear the cached doctors list — called when doctor profile updates */
export function clearDoctorsCache() {
    localStorage.removeItem(DOCTORS_CACHE_KEY)
}

/** Fetch all registered doctors — used by the "Find a Doctor" patient feature. */
export async function getDoctors() {
    try {
        const { data } = await axiosInstance.get('/api/doctors', { timeout: 5000 })
        localStorage.setItem(DOCTORS_CACHE_KEY, JSON.stringify(data))
        return data
    } catch (error) {
        const cached = localStorage.getItem(DOCTORS_CACHE_KEY)
        if (cached) {
            return JSON.parse(cached)
        }
        throw error
    }
}

export async function getDoctorById(doctorId) {
    const { data } = await axiosInstance.get(`/api/doctors/${doctorId}`, { timeout: 5000 })
    return data
}
