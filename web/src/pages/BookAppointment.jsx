import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { bookAppointment } from '../api/appointmentApi';

export default function BookAppointment() {
    const { doctorId } = useParams();
    const navigate = useNavigate();

    const [dateTime, setDateTime] = useState('');
    const [notes, setNotes] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');

    async function handleSubmit(e) {
        e.preventDefault();
        setError('');
        setSubmitting(true);
        try {
            await bookAppointment(Number(doctorId), dateTime, notes);
            navigate('/patient/appointments');
        } catch (err) {
            const msg = err.response?.data?.detail
                || err.response?.data?.message
                || err.response?.data?.[Object.keys(err.response?.data || {})[0]]
                || 'Booking failed. Please try again.';
            setError(msg);
        } finally {
            setSubmitting(false);
        }
    }

    // Minimum datetime: now rounded up to the next minute
    const minDateTime = new Date(Date.now() + 60_000).toISOString().slice(0, 16);

    return (
        <div className="min-h-screen bg-background">
            <div className="container max-w-lg py-12">
                {/* Page header */}
                <div className="mb-8 text-center opacity-0 animate-fade-in">
                    <button
                        onClick={() => navigate(-1)}
                        className="mb-4 inline-flex items-center gap-1 text-sm font-semibold text-primary underline-offset-4 hover:underline"
                    >
                        ← Back
                    </button>
                    <h2 className="text-3xl font-extrabold text-foreground md:text-4xl">Book an Appointment</h2>
                    <p className="mt-1 text-muted-foreground font-body">Choose a date &amp; time and add any notes for your doctor.</p>
                </div>

                {/* Form card */}
                <div className="rounded-xl border border-border bg-card p-6 shadow-card opacity-0 animate-fade-in-delay md:p-8">
                    <form onSubmit={handleSubmit} className="space-y-5">
                        <div className="space-y-2">
                            <label className="text-sm font-medium leading-none text-foreground">Date &amp; Time</label>
                            <input
                                type="datetime-local"
                                value={dateTime}
                                min={minDateTime}
                                onChange={e => setDateTime(e.target.value)}
                                required
                                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                            />
                        </div>

                        <div className="space-y-2">
                            <label className="text-sm font-medium leading-none text-foreground">Notes <span className="text-muted-foreground">(optional)</span></label>
                            <textarea
                                value={notes}
                                onChange={e => setNotes(e.target.value)}
                                maxLength={500}
                                placeholder="Describe your symptoms or reason for visit..."
                                rows={4}
                                className="flex min-h-[120px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 resize-y"
                            />
                        </div>

                        {error && <p className="text-destructive text-sm">{error}</p>}

                        <button
                            type="submit"
                            disabled={submitting}
                            className="inline-flex h-10 w-full items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            {submitting ? 'Booking...' : 'Confirm Booking'}
                        </button>
                    </form>
                </div>
            </div>
        </div>
    );
}
