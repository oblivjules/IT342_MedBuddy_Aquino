import { Link } from 'react-router-dom'
import {
    ArrowRight,
    CalendarPlus,
    Calendar,
    FileText,
    CreditCard,
    MessageSquare,
    Shield,
    Smartphone,
} from 'lucide-react'
import LandingNavbar from '../components/LandingNavbar'
import LandingFooter from '../components/LandingFooter'
import heroImage from '../assets/hero-image.jpg'

/* ─────────────────────────────────────────────── */
/*  Features data                                   */
/* ─────────────────────────────────────────────── */
const features = [
    {
        Icon: Calendar,
        title: 'Easy Scheduling',
        description: 'Book and manage appointments with your preferred doctors in just a few clicks.',
    },
    {
        Icon: FileText,
        title: 'Medical Records',
        description: 'Access your complete medical history and records securely from anywhere.',
    },
    {
        Icon: CreditCard,
        title: 'Billing & Payments',
        description: 'Track your bills, view invoices, and manage payments all in one place.',
    },
    {
        Icon: MessageSquare,
        title: 'Patient Feedback',
        description: 'Share your experience and help us improve our healthcare services.',
    },
    {
        Icon: Shield,
        title: 'Secure & Private',
        description: 'Your health data is encrypted and protected with enterprise-grade security.',
    },
    {
        Icon: Smartphone,
        title: 'Multi-Platform',
        description: 'Access MedBuddy from the web or our Android app — anytime, anywhere.',
    },
]

/* ─────────────────────────────────────────────── */
/*  Landing Page                                    */
/* ─────────────────────────────────────────────── */
export default function LandingPage() {
    return (
        <div className="min-h-screen bg-background">
            <LandingNavbar />

            {/* ── Hero ───────────────────────────────── */}
            <section className="relative overflow-hidden bg-hero-gradient">
                <div className="container grid items-center gap-12 py-20 md:grid-cols-2 md:py-28">
                    {/* Text side */}
                    <div className="space-y-6 opacity-0 animate-fade-in">
                        <div className="inline-flex items-center gap-2 rounded-full bg-primary-soft px-4 py-1.5 text-sm font-medium text-primary">
                            <CalendarPlus className="h-4 w-4" />
                            Book Appointments Online
                        </div>
                        <h1 className="text-4xl font-extrabold leading-tight md:text-5xl lg:text-6xl">
                            Your Health,{' '}
                            <span className="text-gradient">Our Priority</span>
                        </h1>
                        <p className="max-w-md text-lg text-muted-foreground font-body">
                            Book hospital appointments, access medical records, and manage your healthcare — all from one convenient platform.
                        </p>
                        <div className="flex flex-wrap gap-3">
                            <Link
                                to="/register"
                                className="inline-flex h-11 items-center justify-center gap-2 rounded-md bg-primary px-6 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
                            >
                                Get Started Free
                                <ArrowRight className="h-4 w-4" />
                            </Link>
                            <Link
                                to="/login"
                                className="inline-flex h-11 items-center justify-center rounded-md border border-border px-6 text-sm font-semibold text-foreground transition-colors hover:bg-muted"
                            >
                                Log In
                            </Link>
                        </div>
                    </div>

                    {/* Image side */}
                    <div className="opacity-0 animate-fade-in-delay">
                        <div className="relative overflow-hidden rounded-2xl shadow-elevated">
                            <img
                                src={heroImage}
                                alt="Healthcare team at MedBuddy"
                                className="h-full w-full object-cover"
                            />
                            <div className="absolute inset-0 bg-gradient-to-t from-foreground/10 to-transparent" />
                        </div>
                    </div>
                </div>
            </section>

            {/* ── Features ───────────────────────────── */}
            <section id="features" className="py-20">
                <div className="container">
                    <div className="mx-auto mb-12 max-w-2xl text-center opacity-0 animate-fade-in">
                        <h2 className="mb-4 text-3xl font-bold md:text-4xl">
                            Everything You Need for{' '}
                            <span className="text-gradient">Better Healthcare</span>
                        </h2>
                        <p className="text-muted-foreground font-body">
                            MedBuddy brings together all the tools patients need to manage their health journey efficiently.
                        </p>
                    </div>

                    <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
                        {features.map(({ Icon, title, description }) => (
                            <div
                                key={title}
                                className="group rounded-xl border border-border bg-card p-6 shadow-card transition-all duration-300 hover:-translate-y-1 hover:shadow-elevated"
                            >
                                <div className="mb-4 inline-flex rounded-lg bg-primary-soft p-3">
                                    <Icon className="h-6 w-6 text-primary" />
                                </div>
                                <h3 className="mb-2 text-lg font-semibold">{title}</h3>
                                <p className="text-sm text-muted-foreground font-body">{description}</p>
                            </div>
                        ))}
                    </div>
                </div>
            </section>

            {/* ── CTA ────────────────────────────────── */}
            <section id="cta" className="py-20">
                <div className="container">
                    <div className="relative overflow-hidden rounded-2xl bg-primary px-8 py-16 text-center text-primary-foreground md:px-16">
                        <div className="relative z-10 mx-auto max-w-2xl space-y-6">
                            <h2 className="text-3xl font-bold md:text-4xl">
                                Ready to Take Control of Your Health?
                            </h2>
                            <p className="font-body text-primary-foreground/80">
                                Join thousands of patients who trust MedBuddy for their healthcare needs. Sign up today and book your first appointment.
                            </p>
                            <Link
                                to="/register"
                                className="inline-flex h-11 items-center justify-center gap-2 rounded-md bg-white px-6 text-sm font-semibold text-primary transition-colors hover:bg-white/90"
                            >
                                Get Started Free
                                <ArrowRight className="h-4 w-4" />
                            </Link>
                        </div>
                        {/* Decorative circles */}
                        <div className="absolute -right-10 -top-10 h-40 w-40 rounded-full bg-primary-foreground/10" />
                        <div className="absolute -bottom-10 -left-10 h-60 w-60 rounded-full bg-primary-foreground/5" />
                    </div>
                </div>
            </section>

            <LandingFooter />
        </div>
    )
}
