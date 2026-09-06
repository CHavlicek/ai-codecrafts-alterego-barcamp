import verbundLogo from '../../../assets/brand/verbund-logo.svg'
import fifty1Logo from '../../../assets/brand/fifty1-logo-black.png'

/**
 * Verbund rebrand — top-of-page brand bar for the "AI @ Verbund 2026"
 * event. Replaces the previous SQUER / Code-Crafts branding.
 *
 * <p>Left: the official VERBUND wordmark (the customer running the event).
 * Right: the fifty1 wordmark (the format operator / licensee). Both are
 * rendered from the vendors' own logo assets under `assets/brand/`.
 *
 * <p>The visible event name "AI @ Verbund 2026" lives in the page title
 * (AlterEgoPage) — this bar carries only the two partner logos so the
 * customer + operator identity sits above every screen.
 */
export function BrandHeader() {
  return (
    <header className="brand-header" aria-label="AI @ Verbund 2026">
      <img
        className="brand-header__verbund"
        src={verbundLogo}
        alt="VERBUND"
        width={117}
        height={24}
      />
      <span className="brand-header__partner">
        <span className="brand-header__partner-label">Powered by</span>
        <img className="brand-header__fifty1" src={fifty1Logo} alt="fifty1" />
      </span>
    </header>
  )
}
