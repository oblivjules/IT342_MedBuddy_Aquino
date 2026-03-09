import { useState } from 'react';
import { updateAppointmentStatus } from '../api/appointmentApi';

const STATUS_CLASSES = {
    PENDING: 'bg-amber-500',
    CONFIRMED: 'bg-accent',
    CANCELLED: 'bg-destructive',
    COMPLETED: 'bg-emerald-500',
};

export default function AppointmentCard({ appointment, userRole, onStatusChange }) {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const { id, doctor, patient, dateTime, status, notes } = appointment;

    function fullName(profile) {
        if (!profile) return 'Unknown';
        const name = [profile.firstName, profile.lastName].filter(Boolean).join(' ');
        return name || profile.email || 'Unknown';
    }

    const displayName = userRole === 'PATIENT'
        ? `Dr. ${fullName(doctor)}`
        : fullName(patient);
    const label = userRole === 'PATIENT' ? 'Doctor' : 'Patient';

    async function handleAction(newStatus) {
        setError('');
        setLoading(true);
        try {
            await updateAppointmentStatus(id, newStatus);
            onStatusChange(id, newStatus);
        } catch (err) {
            setError(err.response?.data?.message || 'Action failed');
        } finally {
            setLoading(false);
        }
    }

    const isTerminal = status === 'CANCELLED' || status === 'COMPLETED';

    return (
        <div className="rounded-xl border border-border bg-card px-5 py-4 mb-3 shadow-card hover:shadow-elevated transition-shadow opacity-0 animate-slide-in-right">
            <div className="flex items-center gap-3 mb-2">
                <span className={`${STATUS_CLASSES[status] ?? 'bg-muted'} text-white text-xs font-bold tracking-wide px-2.5 py-0.5 rounded-full uppercase`}>
                    {status}
                </span>
                <span className="text-sm text-muted-foreground">{new Date(dateTime).toLocaleString()}</span>
            </div>

            <p className="my-1 text-sm text-foreground"><strong>{label}:</strong> {displayName}</p>
            {notes && <p className="my-1.5 text-sm text-muted-foreground italic">{notes}</p>}

            {error && <p className="text-destructive text-sm mt-1.5 mb-0">{error}</p>}

            {!isTerminal && (
                <div className="flex gap-2 mt-3">
                    {userRole === 'DOCTOR' && status === 'PENDING' && (
                        <button
                            onClick={() => handleAction('CONFIRMED')}
                            disabled={loading}
                            className="bg-accent text-accent-foreground border-none rounded-md px-4 py-1.5 font-semibold text-sm cursor-pointer hover:opacity-90 transition-opacity disabled:opacity-60"
                        >
                            Confirm
                        </button>
                    )}
                    {userRole === 'DOCTOR' && status === 'CONFIRMED' && (
                        <button
                            onClick={() => handleAction('COMPLETED')}
                            disabled={loading}
                            className="bg-emerald-500 text-white border-none rounded-md px-4 py-1.5 font-semibold text-sm cursor-pointer hover:opacity-90 transition-opacity disabled:opacity-60"
                        >
                            Complete
                        </button>
                    )}
                    <button
                        onClick={() => handleAction('CANCELLED')}
                        disabled={loading}
                        className="bg-destructive text-destructive-foreground border-none rounded-md px-4 py-1.5 font-semibold text-sm cursor-pointer hover:opacity-90 transition-opacity disabled:opacity-60"
                    >
                        Cancel
                    </button>
                </div>
            )}
        </div>
    );
}
