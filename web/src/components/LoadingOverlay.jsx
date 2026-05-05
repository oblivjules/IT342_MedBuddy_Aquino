import { useEffect, useState } from 'react'

export default function LoadingOverlay() {
  const [active, setActive] = useState(false)

  useEffect(() => {
    function handler(e) {
      setActive(Boolean(e?.detail?.active))
    }
    window.addEventListener('medbuddy:loading', handler)
    return () => window.removeEventListener('medbuddy:loading', handler)
  }, [])

  if (!active) return null

  return (
    <div aria-hidden className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
      <div className="rounded-full bg-white/90 p-4 shadow-lg">
        <svg className="h-8 w-8 animate-spin text-primary" viewBox="0 0 24 24" fill="none" stroke="currentColor">
          <circle cx="12" cy="12" r="10" strokeWidth="4" strokeOpacity="0.25"></circle>
          <path d="M4 12a8 8 0 018-8" strokeWidth="4" strokeLinecap="round"></path>
        </svg>
      </div>
    </div>
  )
}
