import { useEffect, useRef, useState } from 'react'

export default function UserAvatar({
  imageUrl,
  name,
  fallback,
  className = 'h-10 w-10',
  textClassName = 'text-sm',
  toneClassName = 'bg-primary text-primary-foreground',
  alt = 'User avatar',
}) {
  const resolvedFallback = fallback || (name
    ? name
        .split(' ')
        .filter(Boolean)
        .slice(0, 2)
        .map((part) => part[0])
        .join('')
        .toUpperCase()
    : 'U')

  // Derive stable key - the path without token never changes for the same file
  const stableKey = imageUrl ? imageUrl.split('?')[0] : null
  const prevStableKeyRef = useRef(stableKey)

  const [resolvedImageUrl, setResolvedImageUrl] = useState(() => {
    // If imageUrl is already a signed URL (contains /sign/), use it directly
    // This prevents re-fetching already-signed URLs
    if (imageUrl && imageUrl.includes('/object/sign/')) {
      return imageUrl
    }
    return imageUrl
  })
  const [hasImageError, setHasImageError] = useState(false)

  // Only update resolvedImageUrl when the actual file path changes (not when query params/tokens change)
  // This prevents infinite re-render loops caused by constantly regenerating signed URLs
  useEffect(() => {
    const prevKey = prevStableKeyRef.current
    if (prevKey !== stableKey) {
      console.log('[PROFILE_IMAGE][AVATAR] Stable image key changed from', prevKey, 'to', stableKey, 'updating state')
      // The imageUrl prop is already a signed URL from the backend - use it directly as-is
      // Do not call any backend API to re-sign it
      setResolvedImageUrl(imageUrl)
      setHasImageError(false)
      prevStableKeyRef.current = stableKey
    }
    // IMPORTANT: Only depend on stableKey, not imageUrl
    // The imageUrl contains signed tokens that change on every render
    // We only want to update when the actual file path changes
  }, [stableKey]) // eslint-disable-line react-hooks/exhaustive-deps

  if (resolvedImageUrl && !hasImageError) {
    return (
      <img
        src={resolvedImageUrl}
        alt={alt}
        onError={() => {
          console.log('[PROFILE_IMAGE][AVATAR] image load failed for url:', resolvedImageUrl)
          setHasImageError(true)
        }}
        onLoad={() => {
          console.log('[PROFILE_IMAGE][AVATAR] image loaded successfully')
        }}
        className={`${className} rounded-full object-cover border border-border/60`}
      />
    )
  }

  return (
    <div className={`flex items-center justify-center rounded-full font-bold ${toneClassName} ${className} ${textClassName}`}>
      {resolvedFallback}
    </div>
  )
}
