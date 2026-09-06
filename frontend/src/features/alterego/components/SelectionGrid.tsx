import {
  useRef,
  type ComponentType,
  type CSSProperties,
  type KeyboardEvent,
  type ReactNode,
  type SVGProps,
} from 'react'

/**
 * Keyboard-navigable radio-group primitive shared by the four picker
 * grids (Pose / Archetype / Universe / Vibe). Implements the WAI-ARIA
 * radio-group pattern: single-Tab-stop, arrow-key navigation between
 * options moves both focus and selection.
 *
 * <p>FR-020 + FR-021: works keyboard-only with a visible focus indicator
 * (provided by the global :focus-visible rule in tokens.css) and every
 * option carries a text label that's read out by screen readers.
 *
 * <p>002 delta:
 * <ul>
 *   <li>{@code allowDeselect} — when true, clicking the already-selected
 *       option re-dispatches with its value (the reducer toggles it to
 *       null). Used by the Vibe sub-group (FR-118). The role also changes
 *       from {@code radio} to {@code checkbox} per the WAI-ARIA pattern
 *       for single-selection deselectable groups.</li>
 *   <li>{@code accentVar} — name of a CSS custom property the pill uses
 *       for its selected-state outline (research.md §R4).</li>
 *   <li>Each option may carry an {@code emoji} glyph rendered inside an
 *       aria-hidden span so the accessible name stays the text label alone.</li>
 * </ul>
 */
export interface SelectionGridOption<T extends string> {
  value: T
  label: string
  /** Lucide icon component; rendered inside an aria-hidden wrapper. */
  icon?: ComponentType<SVGProps<SVGSVGElement>>
}

export interface SelectionGridProps<T extends string> {
  label: string
  options: ReadonlyArray<SelectionGridOption<T>>
  value: T | null
  /** Always called with the option's value — the reducer is responsible for
   *  toggling to null in the deselect case. */
  onChange: (value: T) => void
  /** When true, clicking the already-selected option still fires onChange
   *  (so the reducer can toggle it off). */
  allowDeselect?: boolean
  /** CSS custom property name for the per-sub-group accent colour. */
  accentVar?: string
  renderOption?: (option: SelectionGridOption<T>, selected: boolean) => ReactNode
  className?: string
  /**
   * 022 (issue #50) — when {@code true}, the grid renders blurred and is
   * non-interactive: clicks are no-ops, keyboard activation is a no-op,
   * each option button is removed from the tab order and marked
   * {@code aria-disabled}. Used by `ArchetypeGrid` when the user has typed
   * into the custom-role input (precedence rule, FR-2205).
   */
  disabled?: boolean
}

export function SelectionGrid<T extends string>({
  label,
  options,
  value,
  onChange,
  allowDeselect = false,
  accentVar,
  renderOption,
  className,
  disabled = false,
}: SelectionGridProps<T>) {
  const buttonRefs = useRef<Array<HTMLButtonElement | null>>([])

  const handleKeyDown = (e: KeyboardEvent<HTMLButtonElement>, index: number) => {
    if (disabled) return
    const forward = e.key === 'ArrowRight' || e.key === 'ArrowDown'
    const backward = e.key === 'ArrowLeft' || e.key === 'ArrowUp'
    if (!forward && !backward) return
    e.preventDefault()
    const delta = forward ? 1 : -1
    const next = (index + delta + options.length) % options.length
    const nextOption = options[next]
    if (!nextOption) return
    onChange(nextOption.value)
    buttonRefs.current[next]?.focus()
  }

  const firstSelectableIndex = (() => {
    const selectedIndex = value == null ? -1 : options.findIndex((o) => o.value === value)
    return selectedIndex >= 0 ? selectedIndex : 0
  })()

  const handleClick = (option: SelectionGridOption<T>) => {
    if (disabled) return
    const alreadySelected = value === option.value
    if (alreadySelected && !allowDeselect) return
    onChange(option.value)
  }

  // For the deselect-capable group (Vibe) we use the toggle-button pattern
  // (button + aria-pressed). Axe-core correctly flags role="checkbox" without
  // aria-checked, but conceptually Vibe isn't a checkbox — it's a single-
  // selection pill that can also be deselected, which is exactly what the
  // WAI-ARIA toggle-button idiom describes.
  const role = allowDeselect ? ('button' as const) : ('radio' as const)

  const style = accentVar
    ? ({ '--grid-accent': `var(${accentVar})` } as CSSProperties & Record<string, string>)
    : undefined

  const fieldsetClass = `selection-grid ${disabled ? 'is-disabled' : ''} ${className ?? ''}`
    .replace(/\s+/g, ' ')
    .trim()

  return (
    <fieldset className={fieldsetClass} style={style} aria-disabled={disabled || undefined}>
      <legend>{label}</legend>
      <div
        role={allowDeselect ? 'group' : 'radiogroup'}
        aria-label={label}
        aria-disabled={disabled || undefined}
        className="selection-grid__options"
      >
        {options.map((option, index) => {
          const selected = value === option.value
          const tabIndex = disabled ? -1 : index === firstSelectableIndex ? 0 : -1
          const ariaProps =
            role === 'radio' ? { 'aria-checked': selected } : { 'aria-pressed': selected }
          return (
            <button
              key={option.value}
              ref={(el) => {
                buttonRefs.current[index] = el
              }}
              type="button"
              role={role}
              {...ariaProps}
              aria-label={option.label}
              aria-disabled={disabled || undefined}
              tabIndex={tabIndex}
              data-selected={selected ? 'true' : 'false'}
              onClick={() => handleClick(option)}
              onKeyDown={(e) => handleKeyDown(e, index)}
              className={`selection-grid__option ${selected ? 'is-selected' : ''}`.trim()}
            >
              {renderOption ? (
                renderOption(option, selected)
              ) : (
                <>
                  {option.icon ? (
                    <span aria-hidden="true" className="selection-grid__icon">
                      <option.icon width={18} height={18} strokeWidth={1.75} />
                    </span>
                  ) : null}
                  <span className="selection-grid__label">{option.label}</span>
                </>
              )}
            </button>
          )
        })}
      </div>
    </fieldset>
  )
}
