/**
 * UI option arrays for the five Setup-tab selection pickers.
 *
 * <p>Pairs each wire enum value with its display label and a Lucide icon
 * (005 cinematic overhaul — replaces the original OS-emoji glyphs so the
 * setup grid renders identically on every platform). Grids read
 * {@code accentVar} to select one CSS custom property per sub-group
 * rather than hard-coding a hex — see research.md §R4.
 *
 * <p>006 delta: fifth category — Art Style — added. Nine Lucide icons
 * chosen to evoke each rendering style.
 *
 * <p>Kept separate from {@code types.ts} so pure type imports don't pull
 * in runtime arrays (small TS / bundler hygiene).
 */
import type { ComponentType, SVGProps } from 'react'
import {
  BrainCog,
  Briefcase,
  Brush,
  Castle,
  CircleDot,
  ClipboardList,
  Cloud,
  Code2,
  Compass,
  Cpu,
  Database,
  Droplets,
  Film,
  Frame,
  Gauge,
  MessageCircle,
  PenTool,
  Rocket,
  Sparkles,
  Star,
  Sword,
  Terminal,
  Users,
  Waves,
  Zap,
} from 'lucide-react'
import type { Archetype, ArtStyle, Universe } from './types'

export type IconComponent = ComponentType<SVGProps<SVGSVGElement>>

export interface EnumOption<T extends string> {
  value: T
  label: string
  /** Lucide icon component rendered inside an aria-hidden wrapper so the
   *  accessible name is the label alone. Sized at 18px via the
   *  `.selection-grid__icon` CSS rule. */
  icon: IconComponent
}

// 020 — POSE_OPTIONS and VIBE_OPTIONS were removed (closes issue #51).
// The server rolls Pose and Vibe per request; neither category is exposed
// to the user on the Setup tab any more.

export const ARCHETYPE_OPTIONS: ReadonlyArray<EnumOption<Archetype>> = [
  { value: 'cloud-architect', label: 'Cloud Architect', icon: Cloud },
  { value: 'backend-dev', label: 'Backend Developer', icon: Terminal },
  { value: 'frontend-dev', label: 'Frontend Developer', icon: PenTool },
  { value: 'ai-engineer', label: 'AI Engineer', icon: BrainCog },
  { value: 'platform-eng', label: 'Platform Engineer', icon: Gauge },
  { value: 'data-engineer', label: 'Data Engineer', icon: Database },
  // 022 (issue #50): three non-engineering prefab options. Surprise Me
  // draws uniformly from this nine-element list once they're appended.
  { value: 'hr', label: 'People Operations', icon: Users },
  { value: 'administration', label: 'Administration', icon: ClipboardList },
  { value: 'customer-relations', label: 'Customer Relations', icon: MessageCircle },
]

export const UNIVERSE_OPTIONS: ReadonlyArray<EnumOption<Universe>> = [
  { value: 'marvel', label: 'Marvel', icon: Sparkles },
  { value: 'star-wars', label: 'Star Wars', icon: Rocket },
  { value: 'cyberpunk', label: 'Cyberpunk', icon: Cpu },
  { value: 'the-office', label: 'The Office', icon: Briefcase },
  { value: 'indiana-jones', label: 'Indiana Jones', icon: Compass },
  { value: 'lord-of-the-rings', label: 'Lord of the Rings', icon: Castle },
]

/**
 * 006 Art Style options. Wire values are kebab-case public contract from first
 * merge (FR-310).
 *
 * 019 delta: `pixel-art`, `low-poly-3d`, `line-art` retired
 * (specs/019-remove-art-styles, closes #49). Six surviving members; surviving
 * wire values, labels, and relative order are unchanged from 006.
 *
 * Both `ArtStyleGrid` (manual selection) and `randomSelections.ts:62`
 * (Surprise Me) read this array as the single source of truth — trimming it
 * removes the retired values from both surfaces simultaneously.
 */
export const ART_STYLE_OPTIONS: ReadonlyArray<EnumOption<ArtStyle>> = [
  { value: 'oil-painting', label: 'Oil Painting', icon: Brush },
  { value: 'watercolor', label: 'Watercolor', icon: Droplets },
  { value: 'pop-art', label: 'Pop Art', icon: CircleDot },
  { value: 'renaissance-portrait', label: 'Renaissance Portrait', icon: Frame },
  { value: 'japanese-woodblock', label: 'Japanese Woodblock', icon: Waves },
  { value: 'cel-shaded', label: 'Cel-Shaded', icon: Film },
]

/**
 * Per-sub-group accent-colour CSS variable names. Components pass these
 * through to their `SelectionPill` renderer; the actual hex values live
 * in `styles/tokens.css` (semantic aliases) so the mapping can be
 * re-themed centrally.
 */
// 020 — `pose` and `vibe` entries removed; the grids that consumed them
// no longer exist (closes issue #51).
export const ACCENT_VARS = {
  archetype: '--color-accent-role',
  universe: '--color-accent-universe',
  artstyle: '--color-accent-artstyle',
} as const

// Re-export a handful of icons that consumers outside the selection grids
// want without forcing them to import lucide-react directly.
export { Camera, Aperture, RotateCcw, Check, X } from 'lucide-react'

// Stable decorative icon set used by atmospheric components.
export const DECORATIVE_ICONS = {
  spark: Sparkles,
  star: Star,
  zap: Zap,
  code: Code2,
  sword: Sword,
}
