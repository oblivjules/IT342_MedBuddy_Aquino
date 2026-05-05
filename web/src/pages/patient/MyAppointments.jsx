import { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, ChevronLeft, ChevronRight } from 'lucide-react';
import { getMyAppointments } from '../api/appointmentApi';
import AppointmentCard from '../components/AppointmentCard';
import { useAuth } from '../hooks/useAuth';

export default function MyAppointments() {
    const { user } = useAuth();
    const navigate = useNavigate();
    const [appointments, setAppointments] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [search, setSearch] = useState('');
    const [doctorFilter, setDoctorFilter] = useState('');
    const [statusFilter, setStatusFilter] = useState('ALL');
    const [currentPages, setCurrentPages] = useState({});
    const ITEMS_PER_PAGE = 5;

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

    const uniqueDoctors = useMemo(() => {
        const doctors = appointments.map(a => {
            const name = `${a.doctor?.firstName || ''} ${a.doctor?.lastName || ''}`.trim() || 'Unknown';
            return { id: a.doctor?.id, name };
        });
        return Array.from(new Map(doctors.map(d => [d.id, d])).values()).sort((a, b) => a.name.localeCompare(b.name));
    }, [appointments]);

    const filtered = useMemo(() => {
        return appointments.filter(apt => {
            const docName = `${apt.doctor?.firstName || ''} ${apt.doctor?.lastName || ''}`.toLowerCase();
            const searchLower = search.toLowerCase();
            const matchesSearch = docName.includes(searchLower) || (apt.doctor?.email || '').toLowerCase().includes(searchLower);
            const matchesDoctor = !doctorFilter || apt.doctor?.id === parseInt(doctorFilter);
            const matchesStatus = statusFilter === 'ALL' || apt.status === statusFilter;
            return matchesSearch && matchesDoctor && matchesStatus;
        });
    }, [appointments, search, doctorFilter, statusFilter]);

    const grouped = useMemo(() => {
        const result = {
            PENDING: filtered.filter(a => a.status === 'PENDING'),
            CONFIRMED: filtered.filter(a => a.status === 'CONFIRMED'),
            COMPLETED: filtered.filter(a => a.status === 'COMPLETED'),
            CANCELLED: filtered.filter(a => a.status === 'CANCELLED'),
        };
        return result;
    }, [filtered]);

    const paginatedGrouped = useMemo(() => {
        const result = {};
        Object.entries(grouped).forEach(([status, items]) => {
            const page = currentPages[status] || 0;
            const start = page * ITEMS_PER_PAGE;
            const end = start + ITEMS_PER_PAGE;
            result[status] = {
                items: items.slice(start, end),
                totalPages: Math.ceil(items.length / ITEMS_PER_PAGE),
                currentPage: page,
                totalItems: items.length,
            };
        });
        return result;
    }, [grouped, currentPages]);

    function goToPage(status, page) {
        setCurrentPages(prev => ({ ...prev, [status]: page }));
    }

    return (
        <div className="min-h-screen bg-background">
            <div className="container max-w-4xl py-12">
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

                {/* Filters */}
                <div className="mb-6 space-y-3">
                    <div className="flex flex-col gap-3 sm:flex-row">
                        <div className="relative flex-1">
                            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                            <input
                                placeholder="Search doctor name or email..."
                                className="h-10 w-full rounded-md border border-input bg-card pl-9 pr-3 text-sm"
                                value={search}
                                onChange={(e) => {
                                    setSearch(e.target.value);
                                    setCurrentPages({});
                                }}
                            />
                        </div>
                        <select
                            value={doctorFilter}
                            onChange={(e) => {
                                setDoctorFilter(e.target.value);
                                setCurrentPages({});
                            }}
                            className="h-10 rounded-md border border-input bg-card px-3 text-sm sm:w-40"
                        >
                            <option value="">All Doctors</option>
                            {uniqueDoctors.map(doc => (
                                <option key={doc.id} value={doc.id}>{doc.name}</option>
                            ))}
                        </select>
                        <select
                            value={statusFilter}
                            onChange={(e) => {
                                setStatusFilter(e.target.value);
                                setCurrentPages({});
                            }}
                            className="h-10 rounded-md border border-input bg-card px-3 text-sm sm:w-40"
                        >
                            <option value="ALL">All Status</option>
                            <option value="PENDING">Pending</option>
                            <option value="CONFIRMED">Confirmed</option>
                            <option value="COMPLETED">Completed</option>
                            <option value="CANCELLED">Cancelled</option>
                        </select>
                    </div>
                </div>

                {loading && <p className="text-muted-foreground text-sm">Loading...</p>}
                {error && <p className="text-destructive text-sm">{error}</p>}
                {!loading && appointments.length === 0 && (
                    <p className="text-muted-foreground text-sm">No appointments yet — book one now.</p>
                )}
                {!loading && appointments.length > 0 && filtered.length === 0 && (
                    <p className="text-muted-foreground text-sm">No appointments match your filters.</p>
                )}

                {Object.entries(paginatedGrouped).map(([status, data]) =>
                    data.totalItems > 0 ? (
                        <section key={status} className="mb-8">
                            <h3 className="mb-3 text-xs font-bold uppercase tracking-widest text-muted-foreground">{status} ({data.totalItems})</h3>
                            {data.items.map(appt => (
                                <AppointmentCard
                                    key={appt.id}
                                    appointment={appt}
                                    userRole={user?.role}
                                    onStatusChange={handleStatusChange}
                                />
                            ))}
                            {data.totalPages > 1 && (
                                <div className="mt-4 flex items-center justify-center gap-2">
                                    <button
                                        onClick={() => goToPage(status, data.currentPage - 1)}
                                        disabled={data.currentPage === 0}
                                        className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-input bg-card disabled:opacity-50 disabled:cursor-not-allowed hover:bg-muted"
                                    >
                                        <ChevronLeft className="h-4 w-4" />
                                    </button>
                                    <span className="text-xs text-muted-foreground">
                                        Page {data.currentPage + 1} of {data.totalPages}
                                    </span>
                                    <button
                                        onClick={() => goToPage(status, data.currentPage + 1)}
                                        disabled={data.currentPage >= data.totalPages - 1}
                                        className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-input bg-card disabled:opacity-50 disabled:cursor-not-allowed hover:bg-muted"
                                    >
                                        <ChevronRight className="h-4 w-4" />
                                    </button>
                                </div>
                            )}
                        </section>
                    ) : null
                )}
            </div>
        </div>
    );
}
