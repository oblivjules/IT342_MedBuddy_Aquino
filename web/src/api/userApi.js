import axiosInstance from './axiosInstance'

const DOCTORS_CACHE_KEY = 'medbuddy_doctors_cache'

/**
 * All user-related API calls.
 */

/** Fetch all registered doctors — used by the "Find a Doctor" patient feature. */
export async function getDoctors() {
    try {
        const { data } = await axiosInstance.get('/api/users/doctors', { timeout: 5000 })
        localStorage.setItem(DOCTORS_CACHE_KEY, JSON.stringify(data))
        return data
    } catch (error) {
        const cached = localStorage.getItem(DOCTORS_CACHE_KEY)
        if (cached) {
            return JSON.parse(cached)
        }
        return []
    }
}

export async function getDoctorById(doctorId) {
    const doctors = await getDoctors()
    const match = doctors.find((doctor) => String(doctor.id) === String(doctorId))
    return match || null
}
