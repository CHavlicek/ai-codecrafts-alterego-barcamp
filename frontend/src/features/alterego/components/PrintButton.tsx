import { printPosterImage } from '../lib/printPosterImage'

/**
 * Print button — 010 FR-902 / FR-913 / FR-914.
 *
 * <p>Zero local state, zero dispatch, zero HTTP. The entire job is to
 * delegate to the browser's native print machinery when the user
 * activates the control. Rendered only inside the poster branch of
 * {@code AlterEgoPanel} (FR-901 / FR-904) — this component does not
 * carry its own gating logic.
 *
 * <p>Visible text "Print" mirrors Start Over's length/weight; the
 * screen-reader accessible name is the richer "Print my alter ego"
 * per research.md §R9. Both `Enter` and `Space` activate natively
 * because we render a real {@code <button type="button">}.
 *
 * <p>The actual print path is delegated to {@link printPosterImage},
 * which creates a hidden same-origin iframe containing only the poster
 * and calls {@code print()} on that iframe once the image has loaded.
 * This replaces the earlier {@code display:none}-portal approach that
 * intermittently produced an empty print preview on some machines.
 */
interface Props {
  posterDataUrl: string
}

export function PrintButton({ posterDataUrl }: Props) {
  return (
    <button
      type="button"
      className="print-button"
      aria-label="Print my alter ego"
      onClick={() => printPosterImage(posterDataUrl)}
    >
      Print
    </button>
  )
}
