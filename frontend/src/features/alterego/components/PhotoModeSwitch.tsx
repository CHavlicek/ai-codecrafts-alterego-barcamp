import { useId } from 'react'
import { User, Users } from 'lucide-react'
import type { PhotoMode } from '../types'

/**
 * 011 — Composition-mode toggle. A single sliding-thumb switch (one
 * control, not two separate options) that flips the session between
 * "single subject" (default) and "group photo".
 *
 * <p>ARIA: a native {@code <button role="switch">} with
 * {@code aria-checked} reflecting the {@code group} state. The visible
 * Single / Group labels inside the track are decorative
 * ({@code aria-hidden}); the accessible name comes from
 * {@code aria-labelledby} pointing at the section heading. A live
 * {@code role="status"} hint announces the current mode for AT users
 * after each toggle.
 *
 * <p>Keyboard: native button contract — Enter / Space toggle.
 * {@code prefers-reduced-motion} disables the thumb-slide animation
 * (handled by the global token override on {@code --motion-duration-*}).
 */

interface Props {
  value: PhotoMode
  onChange: (value: PhotoMode) => void
}

export function PhotoModeSwitch({ value, onChange }: Props) {
  const labelId = useId()
  const statusId = useId()
  const checked = value === 'group'

  const handleToggle = () => {
    onChange(checked ? 'single' : 'group')
  }

  return (
    <div className="photo-mode-switch">
      <div className="photo-mode-switch__heading">
        <span id={labelId} className="photo-mode-switch__title">
          Photo Mode
        </span>
        <span className="photo-mode-switch__subtitle">Single subject or a group portrait</span>
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-labelledby={labelId}
        aria-describedby={statusId}
        className="photo-mode-switch__control"
        data-state={value}
        onClick={handleToggle}
      >
        <span aria-hidden="true" className="photo-mode-switch__track">
          <span className="photo-mode-switch__option photo-mode-switch__option--single">
            <User width={14} height={14} strokeWidth={1.75} />
            <span>Single</span>
          </span>
          <span className="photo-mode-switch__option photo-mode-switch__option--group">
            <Users width={14} height={14} strokeWidth={1.75} />
            <span>Group</span>
          </span>
          <span className="photo-mode-switch__thumb" />
        </span>
      </button>
      <p id={statusId} role="status" className="photo-mode-switch__hint">
        {checked
          ? 'Group photo — every person in the frame becomes an alter ego.'
          : 'Single person — your face becomes the alter ego.'}
      </p>
    </div>
  )
}
