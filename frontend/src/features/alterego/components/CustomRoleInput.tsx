import { X } from '../options'

interface Props {
  /** Controlled value — typically {@code session.customRole}. */
  value: string
  /** Fires the {@code CustomRoleChanged} reducer action. */
  onChange: (next: string) => void
  /** When {@code true}, the input is read-only — used while a Generate
   *  request is in flight so the user cannot mutate the role of record
   *  mid-request. */
  disabled?: boolean
  /** Hard character cap (default 100, per spec FR-2204). */
  maxLength?: number
}

/**
 * 022 (issue #50) — single-line free-form role input rendered immediately
 * below the prefab Role grid. When the trimmed value is non-empty it takes
 * precedence over the prefab selection (FR-2205, FR-2211) — visual blurring
 * of the prefab pills is handled by {@code SetupLayout} threading
 * {@code disabled={customRoleActive}} through to {@code ArchetypeGrid}.
 *
 * <p>The trailing {@code X} clear button is rendered only when the input is
 * non-empty and is keyboard-operable (Tab to focus, Space/Enter to clear).
 * No confirmation prompt — clicking 'X' is a silent reset (FR-2208).
 */
export function CustomRoleInput({ value, onChange, disabled = false, maxLength = 100 }: Props) {
  const hasValue = value.length > 0
  return (
    <div className="custom-role-input">
      <label className="custom-role-input__label" htmlFor="custom-role-input">
        Or enter your own role
      </label>
      <div className="custom-role-input__field">
        <input
          id="custom-role-input"
          type="text"
          className="custom-role-input__control"
          aria-label="Custom role"
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
            aria-label="Clear custom role"
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
