/**
 * Print the poster image by writing it into a hidden same-origin
 * iframe and triggering {@code print()} on the iframe's window.
 *
 * <p>This replaces the earlier print-artefact approach (a
 * {@code display:none} portal in the main document, gated by
 * {@code @media print}). That approach was intermittently empty on
 * some machines because hidden images decode at low priority and the
 * print snapshot was sometimes taken before the data URL had been
 * rasterised. By giving the image its own document, we let the
 * browser fully load and decode it before we call {@code print()} —
 * the same path the native "right-click → Print image" uses.
 */
export function printPosterImage(dataUrl: string): void {
  if (typeof document === 'undefined') return

  const iframe = document.createElement('iframe')
  iframe.setAttribute('aria-hidden', 'true')
  iframe.setAttribute('title', 'print preview')
  iframe.style.position = 'fixed'
  iframe.style.right = '0'
  iframe.style.bottom = '0'
  iframe.style.width = '0'
  iframe.style.height = '0'
  iframe.style.border = '0'
  iframe.style.visibility = 'hidden'

  const escapedSrc = String(dataUrl).replace(/"/g, '&quot;')
  iframe.srcdoc = `<!doctype html>
<html><head><meta charset="utf-8"><title>Print Alter Ego</title>
<style>
  @page { size: A4 portrait; margin: 0; }
  html, body { margin: 0; padding: 0; height: 100%; background: white; }
  img { display: block; width: 100%; height: 100%; object-fit: contain; }
</style></head>
<body><img alt="" src="${escapedSrc}"></body></html>`

  const cleanup = () => {
    if (iframe.parentNode) iframe.parentNode.removeChild(iframe)
  }

  const triggerPrint = () => {
    const win = iframe.contentWindow
    if (!win) {
      cleanup()
      return
    }
    try {
      win.focus()
      win.print()
    } catch {
      // Best-effort: some sandboxed contexts disallow print(); fall
      // through and tear down rather than leaving the iframe behind.
    }
    setTimeout(cleanup, 1000)
  }

  iframe.addEventListener('load', () => {
    const win = iframe.contentWindow
    if (!win) {
      cleanup()
      return
    }
    const innerImg = win.document.querySelector('img')
    if (!innerImg) {
      triggerPrint()
      return
    }
    if (innerImg.complete && innerImg.naturalWidth > 0) {
      innerImg.decode().then(triggerPrint, triggerPrint)
      return
    }
    innerImg.addEventListener('load', triggerPrint, { once: true })
    innerImg.addEventListener('error', triggerPrint, { once: true })
  })

  document.body.appendChild(iframe)
}
