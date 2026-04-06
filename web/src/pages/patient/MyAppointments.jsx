import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getMyAppointments } from '../api/appointmentApi';
import AppointmentCard from '../components/AppointmentCard';
import { useAuth } from '../hooks/useAuth';

export default function MyAppointments() {
    const { user } = useAuth();
    const navigate = useNavigate();
    const [appointments, setAppointments] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        getMyAppointments()
            .then(data => setAppointments(Array.isArray(data) ? data : []))
            .catch(() => setError('Failed to load appointments.'))
            .finally(() => setLoading(false));
    }, []);

    function handleStatusChange(id, newStatus) {
        setAppointments(prev =>
            prev.map(a => a.id === id ? { ...a, status: newStatus } : a)
        );
    }

    const grouped = {
        PENDING: appointments.filter(a => a.status === 'PENDING'),
        CONFIRMED: appointments.filter(a => a.status === 'CONFIRMED'),
        COMPLETED: appointments.filter(a => a.status === 'COMPLETED'),
        CANCELLED: appointments.filter(a => a.status === 'CANCELLED'),
    };

    return (
        <div className="min-h-screen bg-background">
            <div className="container max-w-2xl py-12">
                {/* Page header */}
                <div className="mb-8 flex items-center justify-between opacity-0 animate-fade-in">
                    <div>
                        <h2 className="text-3xl font-extrabold text-foreground md:text-4xl">My Appointments</h2>
                        <p className="mt-1 text-muted-foreground font-body">Track and manage all your appointments.</p>
                    </div>
                    {user?.role === 'PATIENT' && (
                        <button
                            onClick={() => navigate('/patient/find-doctor')}
                            className="inline-flex h-10 items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
                        >
                            + New
                        </button>
                    )}
                </div>

                {loading && <p className="text-muted-foreground text-sm">Loading...</p>}
                {error && <p className="text-destructive text-sm">{error}</p>}
                {!loading && appointments.length === 0 && (
                    <p className="text-muted-foreground text-sm">No appointments yet.</p>
                )}

                {Object.entries(grouped).map(([status, items]) =>
                    items.length > 0 ? (
                        <section key={status} className="mb-8">
                            <h3 className="mb-3 text-xs font-bold uppercase tracking-widest text-muted-foreground">{status}</h3>
                            {items.map(appt => (
                                <AppointmentCard
                                    key={appt.id}
                                    appointment={appt}
                                    userRole={user?.role}
                                    onStatusChange={handleStatusChange}
                                />
                            ))}
                        </section>
                    ) : null
                )}
            </div>
        </div>
    );
}
