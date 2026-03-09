import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getDoctors } from '../api/userApi';

export default function FindDoctor() {
    const [doctors, setDoctors] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const navigate = useNavigate();

    useEffect(() => {
        getDoctors()
            .then(data => setDoctors(Array.isArray(data) ? data : []))
            .catch(() => setError('Failed to load doctors.'))
            .finally(() => setLoading(false));
    }, []);

    return (
        <div className="min-h-screen bg-background">
            <div className="container max-w-3xl py-12">
                {/* Page header */}
                <div className="mb-8 opacity-0 animate-fade-in">
                    <h2 className="text-3xl font-extrabold text-foreground md:text-4xl">Find a Doctor</h2>
                    <p className="mt-1 text-muted-foreground font-body">Browse available doctors and book your appointment.</p>
                </div>

                {loading && <p className="text-muted-foreground text-sm">Loading doctors...</p>}
                {error && <p className="text-destructive text-sm">{error}</p>}
                {!loading && doctors.length === 0 && (
                    <p className="text-muted-foreground text-sm">No doctors available.</p>
                )}

                <div className="flex flex-col gap-4">
                    {doctors.map(doc => (
                        <div key={doc.id} className="flex items-center gap-4 rounded-xl border border-border bg-card px-5 py-4 shadow-card hover:shadow-elevated transition-shadow opacity-0 animate-fade-in">
                            {/* Avatar */}
                            <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground font-bold text-lg">
                                {(doc.firstName || doc.email || '?')[0].toUpperCase()}
                            </div>

                            {/* Info */}
                            <div className="flex-1 min-w-0">
                                <p className="truncate font-semibold text-sm text-foreground">
                                    Dr. {[doc.firstName, doc.lastName].filter(Boolean).join(' ') || doc.email}
                                </p>
                                {doc.specialization && (
                                    <p className="text-xs text-muted-foreground font-body mt-0.5">{doc.specialization}</p>
                                )}
                                <span className="mt-1 inline-flex items-center rounded-full border border-accent/30 bg-accent/10 px-2 py-0.5 text-xs font-semibold text-accent">Doctor</span>
                            </div>

                            {/* Action */}
                            <button
                                className="inline-flex h-9 items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
                                onClick={() => navigate(`/patient/book/${doc.id}`)}
                            >
                                Book
                            </button>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
}
