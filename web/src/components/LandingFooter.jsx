import { Link } from 'react-router-dom'
import { Heart } from 'lucide-react'
import logo from '../assets/medbuddy-logo.png'

const quickLinks = ['Appointments', 'Doctors', 'Services']
const supportLinks = ['Contact', 'FAQ', 'Privacy Policy']

export default function LandingFooter() {
    return (
        <footer className="border-t border-border bg-muted/30">
            <div className="container py-12">
                <div className="grid gap-8 md:grid-cols-4">
                    {/* Brand */}
                    <div className="space-y-4">
                        <img src={logo} alt="MedBuddy" className="h-8" />
                        <p className="text-sm text-muted-foreground font-body">
                            Your trusted partner for seamless hospital appointments and healthcare management.
                        </p>
                    </div>

                    {/* Quick Links */}
                    <div>
                        <h4 className="mb-3 text-sm font-semibold">Quick Links</h4>
                        <nav className="flex flex-col gap-2">
                            {quickLinks.map((item) => (
                                <Link
                                    key={item}
                                    to="/login"
                                    className="text-sm text-muted-foreground transition-colors hover:text-primary"
                                >
                                    {item}
                                </Link>
                            ))}
                        </nav>
                    </div>

                    {/* Support */}
                    <div>
                        <h4 className="mb-3 text-sm font-semibold">Support</h4>
                        <nav className="flex flex-col gap-2">
                            {supportLinks.map((item) => (
                                <span key={item} className="text-sm text-muted-foreground cursor-default">
                                    {item}
                                </span>
                            ))}
                        </nav>
                    </div>

                    {/* Contact */}
                    <div>
                        <h4 className="mb-3 text-sm font-semibold">Contact</h4>
                        <div className="space-y-2 text-sm text-muted-foreground font-body">
                            <p>info@medbuddy.com</p>
                            <p>+63 (912) 345-6789</p>
                            <p>MedBuddy Health Hub</p>
                        </div>
                    </div>
                </div>

                {/* Bottom bar */}
                <div className="mt-8 flex items-center justify-center gap-1 border-t border-border pt-6 text-sm text-muted-foreground">
                    <span>Made with</span>
                    <Heart className="h-3.5 w-3.5 fill-primary text-primary" />
                    <span>by MedBuddy Team</span>
                </div>
            </div>
        </footer>
    )
}
