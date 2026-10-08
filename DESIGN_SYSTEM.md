# Stitchbook Design System (v1)

## 1. Status

This document covers the visual-foundation decisions introduced alongside Focus Mode's visual refinement (PR 9.1, §§2–9) and the subsequent webapp-to-Compose visual migration across Home/Projects/Library/Stash/Settings and the navigation shell (§§10–11), then the shared surfaces, share-card palette, and statistics chart added with Phases 7–10 (§§12–14). It is not a complete design system and does not document speculative components, screens, or interactions that do not exist yet. Extend it incrementally as future phases add UI, the same way `ARCHITECTURE.md` and `ROADMAP.md` are extended.

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

## 10. List/grid card language (Home, Projects, Guide list, Library, Stash)

Extends §7's component set to cover the card-based screens ported from the approved webapp reference. Cards on these screens consistently use `shapes.large` or `shapes.extraLarge` (never the bare Material default) on `surfaceContainerLowest`, so they read as raised surfaces against the app's warm ivory background the same way the webapp's white-card-on-stone-50 relationship does.

- **`Typography.cardTitle`** (`ui/theme/Type.kt`) — a semantic alias for `titleLarge` (already serif), applied consistently to every card's own title (a project, guide, pattern, or stash item) so list/grid card titles share one editorial treatment rather than each screen picking its own size/weight.
- **`LabelPill`** (`ui/components/LabelPill.kt`) — the small rounded chip used for a card's craft/category/status tag. Colors are assigned by reusing the three existing accent-container roles rather than inventing new raw colors (per §3.2's "add or reuse a role instead" rule): Projects' Active status and Home's Projects quick-nav chip use `primaryContainer` (rose); Stash items and Home's Stash quick-nav chip use `secondaryContainer` (plum); Library items, Projects' Completed status, and Home's Library quick-nav chip use `tertiaryContainer` (teal). This gives each of the app's three main content categories one consistent accent color everywhere it appears, using colors `Color.kt` had already earmarked for "future categories" rather than adding a fourth/fifth raw hue.
- Notes and other secondary detail blocks (a project's notes, a stash item's yarn details) render inside a `surfaceContainer`-tinted `Surface` callout rather than as plain colored body text, matching the webapp's `bg-stone-50` note boxes.
- **Not ported**: the webapp's decorative radial-dot hero texture and any box-shadow/gradient — both are explicitly ruled out by §2's anti-patterns ("decorative gradients or textures").

## 11. Navigation shell

Top-level destinations (Home/Projects/Library/Stash/Settings) render with no `TopAppBar` at all — each already carries its own in-content headline, and a static app-name bar above it would be redundant administrative chrome the webapp itself doesn't show on mobile (`Navigation.tsx`'s header is desktop-only). Every other destination gets a minimal, title-less bar whose only job is a visible back arrow, since some screens (Project detail in particular) have no other in-UI way back besides the system back gesture/button.

## 12. Shared surfaces and fields

The Phase 7–10 screens (Materials, Journal, Sessions, Statistics, Cards, Settings restore) use one shared set of building blocks so they read as one product. Prefer these over re-implementing the pattern privately in a screen.

- **`ContentCard`** (`ui/components/StitchbookSurfaces.kt`): the standard raised card, `surfaceContainerLowest` with the `extraLarge` radius (§10).
- **`SectionHeader`**: a quiet serif heading with an optional trailing action and a hairline divider. It is the editorial separator §2 prefers over boxing every section in its own card.
- **`EmptyState`**: a soft icon tile, a short title, and one sentence of guidance, small enough to sit inside a section.
- **`ChoiceChipRow`**: a single-choice row of filter chips for small enums (theme, units, card templates, statistics range).
- **`DetailLine`**: a label/value pair merged into one TalkBack stop.
- **`DateField`** (`ui/components/DateField.kt`): an ISO local-date text field (`yyyy-MM-dd`) with a calendar button. Typing stays possible for keyboard and accessibility users; conversion to and from the Material picker uses UTC on both sides, so the date never shifts by a day in negative-offset zones.
- **`PhotoThumbnail`** (`ui/components/PhotoThumbnail.kt`): a rounded, cropped tile for a referenced photo. A missing original shows a clear placeholder instead of failing, so the screen can offer relinking.
- **`formatDuration` / `formatStopwatch`** (`ui/components/DurationText.kt`): "1 h 05 min" for totals, rounded down to whole minutes, and "1:02:03" for a live timer.
- **`LocalMeasurementSystem`**: the user's display-only imperial/metric preference, provided once at the activity root.

Destructive choices in dialogs (Replace on restore, Reset) use the `error` content colour on a text button and always sit beside a plain Cancel. An action that deletes records the user can't see asks a second time and names what it will remove.

## 13. Share-card palette

Exported PNG cards (`ui/cards/ShareCardRenderer.kt`, 1080 × 1350 px) use a fixed light palette. They don't follow the app's dark mode, because a shared image should look the same wherever it is viewed:

| Role | Value | Use |
| --- | --- | --- |
| Background | `#FBF7F2` | Warm ivory page |
| Ink | `#2E2A27` | Title and primary facts |
| Muted | `#6F665F` | Labels and secondary facts |
| Accent | `#9C4A5E` | One dusty-rose accent: the template label above the title |
| Tile | `#F2EAE2` | Photo frame behind a card photo |

They match the light theme's warm roles and are fixed so the renderer needs no Compose theme. Text contrast on Background: Ink 13.3:1, Muted 5.3:1, Accent 5.5:1, all above WCAG AA's 4.5:1. Cards use the serif/sans pairing of §4, no textures or gradients (§2), and carry no location or camera metadata.

## 14. Statistics chart

The Statistics screen's eight-week trend (`WeeklyBars` in `feature/statistics/StatisticsScreen.kt`) is the app's only chart. It follows these rules:

- Plain bars in `primary` on a 1dp `outlineVariant` baseline, with only the data end rounded (4dp). No gridlines, gradients, or 3D effects.
- Only the first and last week are labelled on the axis; a quiet line above names the peak week and its total.
- The whole chart is one TalkBack stop whose description reads every week and its minutes, so the data never depends on seeing the bars.
- Every figure beside the chart is labelled recorded, derived, or estimated (ARCHITECTURE.md "Current crafting sessions and statistics").

## 15. Project hub

A project screen leads with the work, not with buttons:

- **Header:** the project name, then one quiet line of craft · type · status, then the description. Edit, share card, exports, and delete sit behind one ⋮ menu; delete is the only item in the error colour, below a divider.
- **Next step:** one full-width button for the single obvious action: *Continue* an in-progress guide (primary), *Start* a published one (primary), or *Finish writing* a draft / *Add a guide* (secondary).
- **Node map** (`ui/components/ProjectNodeMap.kt`): the project type in a `primaryContainer` pill at the centre, and seven nodes (Guides, Patterns, Yarn, Tools, Counters, Journal, Time) evenly spaced on a ring from the top, joined to the centre by 1dp `outlineVariant` hairlines. Each node is a `surfaceContainerLowest` tile with a hairline border, an icon, a label, and its count. A node with nothing linked keeps its place but drops to secondary text with no count, so the map always shows what *could* connect. The hairlines are decorative; each node is one TalkBack stop ("Yarn, 2" or "Yarn, nothing linked yet").
- **Sheets:** Guides, Tools, and Counters open in a bottom sheet; Patterns and Yarn open Materials, Journal opens Journal, and Time opens Sessions. Anything added from a sheet is added to the shared toolbox or counters too, so the project and the global lists never disagree.

## 16. Calm navigation and Home

- **Five places:** the bottom bar holds Home, Projects, Library, Stash, and Settings. Tools sit inside Stash behind a two-segment `SingleChoiceSegmentedButtonRow` (Yarn & materials | Tools); Counters is a back-arrow child screen reached from Home; project counters stay in the hub sheet.
- **Home** is the app name with a single + action, a `primaryContainer` *Continue* card only when a guide is in progress, the active projects as plain `surfaceContainerLowest` rows (name, then "craft · updated date"), and two quiet text links (Counters, Statistics). No hero banner, stat tiles, or feature tour: Home answers "what was I doing?" and nothing else.
- **Review lists show only non-zero counts.** A restore review row reads "3 new", not "3 new · 0 identical · 0 conflicting · 0 only on this device".
- **Occasional actions live in an overflow menu.** Inventory screens (Stash, Tools) open on their content: `ScreenHeader` (`ui/components/ScreenHeader.kt`) puts the title beside one ⋮ menu holding bulk creation, sets, and CSV import, export, and template. Rows of text buttons above the content are avoided.

## 17. App icon

The launcher icon is an adaptive icon drawn as vectors (`res/drawable/ic_launcher_*.xml`): a berry yarn ball (primary `#9C3A56`, with rose strands) on two crossed wooden needles over the cream light surface (`#F6F0EA`). The whole drawing is scaled to 84% about the centre, so round, squircle and square masks never clip the needles or the loose end. A separate monochrome layer gives Android 13+ themed icons the same silhouette. The app name is plain "Stitchbook".

## 18. Screen headers and voice

- Every top-level tab opens on one short serif title: *Stitchbook* (Home), *Your projects*, *Library*, *Stash*, *Settings*. Stash shows its title once, above the Yarn & materials / Tools toggle; each tab underneath keeps only its one-line context and overflow menu (`ScreenHeader` with a null title).
- Copy is plain and warm: "Add pattern", "Add to stash", "No tools yet", "Your records stay yours". Avoid inventory or compliance words ("references", "inventory", "guardrails", "data ownership").
- Facts that say nothing stay hidden. A plain "Other" project type or pattern craft shows no label; the hub's centre names the craft instead.
- The privacy note in Settings is a soft primary-container card, not an inverse (dark) slab.
- Without a pattern folder, Library shows a soft card with the explanation and a tonal *Choose pattern folder* button. With one, it collapses to a single line with *Check folder*.
- The Draft editor's step cards use `surfaceContainerLow`, and spans read "Rows 1–10". Imported steps note "(p.N)" only where the page changes, and Focus hides that note.

