import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Menu, X } from 'lucide-react'
import logo from '../assets/medbuddy-logo-removebg-preview.png'

const navItems = [
    { label: 'Home', href: '#' },
    { label: 'Features', href: '#features' },
    { label: 'About', href: '#cta' },
]

export default function LandingNavbar() {
    const [mobileOpen, setMobileOpen] = useState(false)

    return (
        <header className="sticky top-0 z-50 border-b border-border/50 bg-background/80 backdrop-blur-lg">
            <div className="container flex h-16 items-center justify-between">
                {/* Logo */}
                <Link to="/" className="flex items-center gap-2">
                    <img src={logo} alt="MedBuddy" className="h-16 md:h-18 w-auto" />
                </Link>

                {/* Desktop nav */}
                <nav className="hidden items-center gap-1 md:flex">
                    {navItems.map((item) => (
                        <a
                            key={item.label}
                            href={item.href}
                            className="rounded-lg px-4 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-primary-soft hover:text-primary"
                        >
                            {item.label}
                        </a>
                    ))}
                </nav>

                {/* Desktop CTA */}
                <div className="hidden items-center gap-3 md:flex">
                    <Link
                        to="/login"
                        className="inline-flex h-9 items-center justify-center rounded-md px-4 text-sm font-semibold text-foreground transition-colors hover:bg-muted"
                    >
                        Log In
                    </Link>
                    <Link
                        to="/register"
                        className="inline-flex h-9 items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
                    >
                        Sign Up
                    </Link>
                </div>

                {/* Mobile toggle */}
                <button
                    className="rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-muted md:hidden"
                    onClick={() => setMobileOpen(!mobileOpen)}
                    aria-label="Toggle menu"
                >
                    {mobileOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
                </button>
            </div>

            {/* Mobile menu */}
            {mobileOpen && (
                <div className="border-t border-border bg-background p-4 md:hidden">
                    <nav className="flex flex-col gap-2">
                        {navItems.map((item) => (
                            <a
                                key={item.label}
                                href={item.href}
                                onClick={() => setMobileOpen(false)}
                                className="rounded-lg px-4 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-primary-soft hover:text-primary"
                            >
                                {item.label}
                            </a>
                        ))}
                        <div className="mt-2 flex flex-col gap-2 border-t border-border pt-3">
                            <Link
                                to="/login"
                                className="inline-flex h-9 items-center justify-center rounded-md border border-border px-4 text-sm font-semibold text-foreground"
                                onClick={() => setMobileOpen(false)}
                            >
                                Log In
                            </Link>
                            <Link
                                to="/register"
                                className="inline-flex h-9 items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground"
                                onClick={() => setMobileOpen(false)}
                            >
                                Sign Up
                            </Link>
                        </div>
                    </nav>
                </div>
            )}
        </header>
    )
}
