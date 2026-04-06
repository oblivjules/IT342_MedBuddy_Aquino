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

  if (imageUrl) {
    return (
      <img
        src={imageUrl}
        alt={alt}
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
