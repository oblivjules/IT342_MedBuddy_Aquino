import { ExternalLink, X } from 'lucide-react'

function lower(value) {
  return String(value || '').toLowerCase()
}

function isImage(file) {
  const type = lower(file?.fileType)
  const ext = lower(file?.fileExtension || file?.fileName?.split('.').pop())
  return type.startsWith('image/') || ['png', 'jpg', 'jpeg', 'webp', 'gif'].includes(ext)
}

function isPdf(file) {
  const type = lower(file?.fileType)
  const ext = lower(file?.fileExtension || file?.fileName?.split('.').pop())
  return type === 'application/pdf' || ext === 'pdf'
}

export default function FileOpenerModal({ file, onClose }) {
  if (!file) return null

  const canPreviewImage = isImage(file)
  const canPreviewPdf = isPdf(file)

  return (
    <div className="fixed inset-0 z-[80] flex items-center justify-center bg-black/70 px-4 py-8 backdrop-blur-sm">
      <div className="w-full max-w-4xl overflow-hidden rounded-2xl border border-border bg-card shadow-2xl">
        <div className="flex items-center justify-between border-b border-border px-5 py-4">
          <div className="min-w-0">
            <h3 className="truncate text-lg font-semibold">{file.fileName || 'File preview'}</h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-2 text-muted-foreground hover:bg-muted"
            aria-label="Close file viewer"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="max-h-[70vh] overflow-auto p-5">
          {canPreviewImage && (
            <img
              src={file.fileUrl}
              alt={file.fileName || 'Uploaded file'}
              className="mx-auto max-h-[60vh] rounded-xl object-contain"
            />
          )}

          {canPreviewPdf && (
            <div className="rounded-xl border border-border bg-muted/30 p-6 text-sm text-muted-foreground">
              Open in New Tab to view or download the file.
            </div>
          )}

          {!canPreviewImage && !canPreviewPdf && (
            <div className="rounded-xl border border-border bg-muted/30 p-4 text-sm text-muted-foreground">
              Open in New Tab to view or download it.
            </div>
          )}
        </div>

        <div className="flex justify-end gap-3 border-t border-border px-5 py-4">
          <button
            type="button"
            onClick={onClose}
            className="inline-flex h-10 items-center justify-center rounded-md border border-input bg-background px-4 text-sm font-medium hover:bg-muted"
          >
            Close
          </button>
          <button
            type="button"
            onClick={() => window.open(file.fileUrl, '_blank', 'noopener,noreferrer')}
            className="inline-flex h-10 items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground hover:bg-primary/90"
          >
            <ExternalLink className="mr-2 h-4 w-4" /> Open in New Tab
          </button>
        </div>
      </div>
    </div>
  )
}
