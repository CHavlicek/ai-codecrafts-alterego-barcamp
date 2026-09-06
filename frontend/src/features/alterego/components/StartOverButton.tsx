interface Props {
  /** Current blob-URL preview; will be revoked on reset to avoid leaks. */
  photoPreviewUrl: string | null
  onStartOver: () => void
}

/**
 * Start-over button — FR-013. Revokes the active blob-URL before
 * dispatching the reset so the browser can reclaim the photo memory.
 *
 * Revocation happens synchronously here; the reducer then clears the
 * state in O(1). The SC-006 200 ms target is exercised by the Playwright
 * keyboard-walkthrough spec in T046.
 */
export function StartOverButton({ photoPreviewUrl, onStartOver }: Props) {
  const handleClick = () => {
    if (photoPreviewUrl) {
      URL.revokeObjectURL(photoPreviewUrl)
    }
    onStartOver()
  }

  return (
    <button type="button" className="start-over-button" onClick={handleClick}>
      Start over
    </button>
  )
}
