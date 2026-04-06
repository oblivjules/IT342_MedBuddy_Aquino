import axiosInstance from './axiosInstance'

/**
 * All appointment-related API calls.
 * Every function returns the Axios response `.data` directly.
 */

/** Patient: book a new appointment */
export async function bookAppointment(doctorId, slotId, notes = '') {
    const { data } = await axiosInstance.post('/api/appointments', {
        doctorId,
        slotId,
        notes,
    })
    return data
}

/** Retrieve all appointments for the authenticated user (PATIENT or DOCTOR) */
export async function getMyAppointments() {
    const { data } = await axiosInstance.get('/api/appointments', {
        skipCache: true,
    })
    return data
}

/**
 * Update the status of an appointment.
 * @param {number} id
 * @param {'CONFIRMED'|'CANCELLED'|'COMPLETED'} status
 */
export async function updateAppointmentStatus(id, status) {
    const { data } = await axiosInstance.patch(`/api/appointments/${id}/status`, {
        status,
    })
    return data
}
