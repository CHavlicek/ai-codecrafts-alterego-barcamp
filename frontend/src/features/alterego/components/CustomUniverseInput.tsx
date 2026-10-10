import { X } from '../options'

interface Props {
  /** Controlled value — typically {@code session.customUniverse}. */
  value: string
  /** Fires the {@code CustomUniverseChanged} reducer action. */
  onChange: (next: string) => void
  /** When {@code true}, the input is read-only — used while a Generate
   *  request is in flight so the user cannot mutate the universe of record
   *  mid-request. */
  disabled?: boolean
  /** Hard character cap (default 100). */
  maxLength?: number
}

/**
 * 029 (verbund-rebrand) — single-line free-form universe input rendered
 * immediately below the prefab Universe grid. Mirrors {@code CustomRoleInput}:
 * when the trimmed value is non-empty it takes precedence over the prefab
 * selection — visual blurring of the prefab pills is handled by
 * {@code SetupLayout} threading {@code disabled={customUniverseActive}}
 * through to {@code UniverseGrid}.
 *
 * <p>The trailing {@code X} clear button is rendered only when the input is
 * non-empty and is keyboard-operable (Tab to focus, Space/Enter to clear).
 * No confirmation prompt — clicking 'X' is a silent reset.
 */
export function CustomUniverseInput({ value, onChange, disabled = false, maxLength = 100 }: Props) {
  const hasValue = value.length > 0
  return (
    <div className="custom-role-input">
      <label className="custom-role-input__label" htmlFor="custom-universe-input">
        Or enter your own universe
      </label>
      <div className="custom-role-input__field">
        <input
          id="custom-universe-input"
          type="text"
          className="custom-role-input__control"
          aria-label="Custom universe"
          autoComplete="off"
          spellCheck={false}
          value={value}
          maxLength={maxLength}
          disabled={disabled}
          onChange={(e) => onChange(e.target.value)}
        />
        {hasValue ? (
          <button
            type="button"
            className="custom-role-input__clear"
            aria-label="Clear custom universe"
            disabled={disabled}
            onClick={() => onChange('')}
          >
            <span aria-hidden="true" className="custom-role-input__clear-icon">
              <X width={16} height={16} strokeWidth={2} />
            </span>
          </button>
        ) : null}
      </div>
    </div>
  )
}
