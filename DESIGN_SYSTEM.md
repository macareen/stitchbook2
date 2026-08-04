# Stitchbook Design System (v1)

## 1. Status

This document covers only the visual-foundation decisions introduced alongside Focus Mode's visual refinement (PR 9.1). It is not a complete design system and does not document speculative components, screens, or interactions that do not exist yet. Extend it incrementally as future phases add UI, the same way `ARCHITECTURE.md` and `ROADMAP.md` are extended.

## 2. Design personality

Stitchbook should feel like a **quiet-luxury reading and crafting companion**: calm, warm, elegant, personal, trustworthy, refined, reading-first, quietly expressive. It should not feel like a productivity dashboard, a fitness tracker, or an unstyled Material sample.

The visual direction blends:

1. A premium reading app (generous whitespace, an editorial type hierarchy, one dominant piece of content per screen).
2. A cozy craft journal — warm and personal, but without simulated paper, fabric, leather, or other textures.
3. A modern yarn boutique — restrained color and photography rather than literal craft iconography.

Pink (a muted dusty rose/berry) is a genuine brand accent, not a stereotype about knitting. It is used as *an* accent — primarily for the primary action and light container fills — never as the default surface color everywhere.

### Explicit anti-patterns

Do not introduce:

- Simulated paper, fabric, leather, stitching borders, or yarn-ball motifs.
- Decorative gradients or textures.
- Cold blue-grey "Material default" surfaces or accents.
- Every piece of content wrapped in a `Card` — prefer typography, spacing, and restrained separators (an editorial composition) over stacking boxes.
- Two equal-weight, same-style actions where only one is actually primary.
- Disabled controls that communicate their state through opacity alone.

## 3. Color

### 3.1 Principles

- Warm ivory/cream backgrounds and warm charcoal text — never cool grey.
- One muted dusty-rose/berry accent (`primary`) used deliberately, not on every surface.
- Restrained warm neutrals (oat/stone tones) for quieter surfaces and containers.
- Full light and dark schemes defined together so neither is an afterthought.

### 3.2 Roles

Color roles are the standard Material3 `ColorScheme` slots (`background`, `surface`, `surfaceVariant`, `primary`, `onPrimary`, `primaryContainer`, `onSurfaceVariant`, `outline`, `error`, …), fully populated for both light and dark in `ui/theme/Color.kt` and `ui/theme/Theme.kt` — no slot is left at its cool-grey Material default.

`ui/theme/SemanticColors.kt` adds a small set of semantic aliases over those roles, so call sites read by intent rather than by Material3 slot name:

| Semantic role | Backed by |
| --- | --- |
| `textPrimary` | `onBackground` |
| `textSecondary` | `onSurfaceVariant` |
| `surfaceSubtle` | `surfaceVariant` |

Prefer `MaterialTheme.colorScheme.textSecondary` over `MaterialTheme.colorScheme.onSurfaceVariant` at Focus Mode call sites; both compile to the same value, but the former states intent. Do not hardcode raw `Color(0x...)` values inside feature code — add or reuse a role instead.

### 3.3 Contrast

Every text-bearing color pair introduced here was checked against the WCAG contrast formula before being committed to the palette (background/text pairs land between 4.8:1 and 14:1; the one non-text UI pairing, outline-on-background, clears the 3:1 threshold for graphical/UI elements). See the PR's verification notes for the exact ratios checked.

## 4. Typography

Typography roles are defined in `ui/theme/Type.kt` as Material3 `Typography` slots, with a small set of semantic aliases (in the same file) layered on top:

| Semantic role | Backed by | Used for |
| --- | --- | --- |
| `screenTitle` | `titleLarge` | Guide name on Ready-to-start/Completed/message screens |
| `sectionLabel` | `labelLarge` | Quiet guide-name/context label during an active execution |
| `instruction` | `headlineMedium` | The current instruction — the dominant, reading-optimized element |
| `metadata` | `bodyMedium` | Range/repeat position, breadcrumbs, transient feedback |
| `buttonLabel` | `labelLarge` | Primary/secondary button text |

Headline/title roles use `FontFamily.Serif`; body/label roles use `FontFamily.Default` (system sans). Both are Compose's built-in generic font families resolved by the platform — no font file is bundled and no licensing decision was needed. This intentionally leaves room to swap in a specific licensed serif later by changing the two family constants at the top of `Type.kt`; no call site references a font family directly.

The `instruction` role is the one most exercised by accessibility settings: it uses `sp` units throughout (so it scales with the system font size setting) and is rendered inside a scrolling container with no fixed height, so it never clips at large font scales — see §7.

## 5. Spacing

`ui/theme/Spacing.kt` defines a small scale used in place of ad hoc `.dp` literals:

| Token | Value |
| --- | --- |
| `extraSmall` | 4dp |
| `small` | 8dp |
| `medium` | 16dp |
| `large` | 24dp |
| `extraLarge` | 32dp |
| `extraExtraLarge` | 48dp |

## 6. Corner radius and elevation

`Theme.kt` defines a custom Material3 `Shapes` scale (`extraSmall` 4dp … `extraLarge` 28dp; `medium` is 16dp, used by `Card` and similar containers). Buttons are unaffected by this scale — Material3's filled/outlined buttons already render fully rounded ("pill") regardless of the theme's `Shapes`, which already matches the calm, soft-rounded feel this product wants.

Elevation is kept minimal by default: surfaces rely on color and spacing to express hierarchy rather than drop shadows. Nothing in this PR adds elevation beyond each component's existing Material3 default.

## 7. Components introduced

Only components with an immediate consumer in Focus Mode were added — this is not a general component library.

- **`PrimaryActionButton`** (`ui/components/StitchbookButtons.kt`) — filled, full-accent-color button for the one action a screen wants next (`Complete`, `Start`, `Start new`). Minimum 48dp touch target. Disabled styling is Material3's default neutral-tone substitution, not merely a faded accent color, so it doesn't rely on opacity alone.
- **`SecondaryActionButton`** — outlined button for a clearly secondary action (`Previous`). Hierarchy against the primary button comes from fill-vs-outline, not color alone.
- **`QuietText`** (`ui/components/QuietText.kt`) — muted, secondary-weight text for guide/section context and structural position, so that styling isn't duplicated across the three places Focus Mode needs it.

## 8. Focus Mode's visual hierarchy

Focus Mode's active-execution view is now three fixed vertical regions:

1. A quiet header (guide name + section breadcrumbs, `sectionLabel`/`metadata` styles, merged into one accessibility node).
2. A scrollable body that holds the instruction (`instruction` style, dominant), range/repeat position lines, and any transient feedback — vertically centered when short, scrolling safely when long.
3. A pinned action row (`Previous` / `Complete`) below a subtle divider, always reachable regardless of instruction length or system font scale.

This keeps the current instruction as the unmistakable visual center, keeps supporting context quieter than the instruction, and keeps `Complete` and `Previous` reachable and hierarchically distinct without wrapping everything in cards. No ViewModel, repository, or execution-engine behavior changed — this was a rendering-only pass over the same `GuideFocusUiState`.

## 9. Accessibility baseline

- All interactive controls (`PrimaryActionButton`, `SecondaryActionButton`, the jump `TextButton`) enforce a 48dp minimum touch target.
- Text sizes are defined in `sp` so they scale with the system font size setting; the instruction sits in an unbounded scrolling container so large font scales don't clip it.
- Disabled states are distinguished by Material3's default neutral-tone container substitution, not opacity alone.
- Guide-name/breadcrumb context and range/repeat position lines are grouped with `Modifier.semantics(mergeDescendants = true)` so TalkBack traverses each group as one stop, in the same order they appear visually.
- Every color pair used for text was checked against WCAG contrast thresholds (see §3.3).
- Hierarchy between `Complete` and `Previous` is expressed through both fill/outline shape and color, never color alone.
