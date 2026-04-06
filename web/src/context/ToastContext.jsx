import { createContext, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { CheckCircle2, Info, X } from 'lucide-react'

export const ToastContext = createContext(null)

const toneByType = {
  success: {
    container: 'border-emerald-700 bg-emerald-600 text-white',
    icon: CheckCircle2,
  },
  error: {
    container: 'border-destructive/40 bg-destructive/10 text-destructive',
    icon: X,
  },
  info: {
    container: 'border-primary/40 bg-primary-soft text-primary',
    icon: Info,
  },
}

function nextId() {
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])
  const timeoutsRef = useRef({})

  const removeToast = useCallback((id) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id))

    if (timeoutsRef.current[id]) {
      clearTimeout(timeoutsRef.current[id])
      delete timeoutsRef.current[id]
    }
  }, [])

  const showToast = useCallback(({ message, type = 'success', durationMs = 2600 }) => {
    const id = nextId()
    const toast = { id, message, type }

    setToasts((prev) => [toast, ...prev].slice(0, 3))

    timeoutsRef.current[id] = setTimeout(() => {
      removeToast(id)
    }, durationMs)

    return id
  }, [removeToast])

  const success = useCallback((message, durationMs) => {
    showToast({ message, type: 'success', durationMs })
  }, [showToast])

  const error = useCallback((message, durationMs) => {
    showToast({ message, type: 'error', durationMs })
  }, [showToast])

  useEffect(() => () => {
    Object.values(timeoutsRef.current).forEach((timeoutId) => clearTimeout(timeoutId))
  }, [])

  const value = useMemo(
    () => ({ showToast, success, error, removeToast }),
    [removeToast, showToast, success, error],
  )

  return (
    <ToastContext.Provider value={value}>
      {children}

      <div className="pointer-events-none fixed right-4 top-4 z-[100] flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2">
        {toasts.map((toast) => {
          const tone = toneByType[toast.type] || toneByType.info
          const Icon = tone.icon

          return (
            <div
              key={toast.id}
              className={`pointer-events-auto flex items-start gap-2 rounded-xl border px-3 py-2 shadow-elevated ${tone.container}`}
              role="status"
              aria-live="polite"
            >
              <Icon className="mt-0.5 h-4 w-4 shrink-0" />
              <p className="flex-1 text-sm font-medium">{toast.message}</p>
              <button
                type="button"
                onClick={() => removeToast(toast.id)}
                className="rounded p-0.5 opacity-70 transition hover:opacity-100"
                aria-label="Dismiss notification"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          )
        })}
      </div>
    </ToastContext.Provider>
  )
}

