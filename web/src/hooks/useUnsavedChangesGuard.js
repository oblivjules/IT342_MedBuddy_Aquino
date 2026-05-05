import { useEffect } from 'react'
import { useBeforeUnload } from 'react-router-dom'

export function useUnsavedChangesGuard(when, message) {
  useBeforeUnload((event) => {
    if (!when) return
    event.preventDefault()
    event.returnValue = ''
  })

  useEffect(() => {
    if (!when) return undefined

    const confirmLeave = () => window.confirm(message)

    const onDocumentClick = (event) => {
      const anchor = event.target.closest('a[href]')
      if (!anchor) return

      if (anchor.target === '_blank' || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
        return
      }

      const href = anchor.getAttribute('href')
      if (!href || href.startsWith('#')) return

      const destination = new URL(href, window.location.origin)
      const current = new URL(window.location.href)
      const isSameRoute =
        destination.pathname === current.pathname
        && destination.search === current.search
        && destination.hash === current.hash

      if (isSameRoute) return

      if (!confirmLeave()) {
        event.preventDefault()
        event.stopPropagation()
      }
    }

    const onPopState = () => {
      if (confirmLeave()) return
      window.history.pushState(null, '', window.location.href)
    }

    window.history.pushState(null, '', window.location.href)
    document.addEventListener('click', onDocumentClick, true)
    window.addEventListener('popstate', onPopState)

    return () => {
      document.removeEventListener('click', onDocumentClick, true)
      window.removeEventListener('popstate', onPopState)
    }
  }, [message, when])
}
