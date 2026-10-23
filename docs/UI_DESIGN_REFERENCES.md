# UI design references for Stitchbook

Reviewed 2026-10-23. These are references for future UI work, not a mandate to imitate another product. Stitchbook is a native Android/Compose app; preserve its own visual identity, craft-neutral workflows, accessibility, offline behavior, and Material 3 implementation. Web/React components are inspiration only unless deliberately reimplemented with native components.

## Recommended skills and references

### 1. UI Skills — useful, install selectively

- Source: [ibelick/ui-skills](https://github.com/ibelick/ui-skills), [catalog](https://www.ui-skills.com/), [playbook](https://www.ui-skills.com/playbook).
- Fit: strongest general-purpose source for reviewing existing screens, accessibility, hierarchy, interaction polish, and design consistency. The catalog includes `improve-ui`, `better-ui`, `improve-animations`, and `ui-skills` routing; the playbook has practical guidance on large touch targets, labels, contrast, empty states, status not conveyed by color alone, and restrained motion.
- Install/use: inspect `npx ui-skills` / `npx ui-skills list` and fetch only the relevant skill (for example `improve-ui`) using the current CLI instructions. The upstream repo also documents an MCP catalog at `https://www.ui-skills.com/mcp`.
- Caveat: much of the catalog assumes web/front-end stacks. For Stitchbook, retain the design/review principles and translate implementation guidance to Compose, Android accessibility semantics, and Material 3.

### 2. Apple Design — useful interaction reference, adapt to Android

- Source: [apple-design/SKILL.md](https://github.com/emilkowalski/skills/blob/main/skills/apple-design/SKILL.md), [skills repository](https://github.com/emilkowalski/skills).
- Fit: worthwhile for motion and direct-manipulation review: immediate feedback, continuous touch tracking, interruptible movement, sensible spring behavior, and restraint. Could inform Focus controls, sheets, photo crop/calibration, and navigation gestures.
- Install/use: upstream documents `npx skills@latest add emilkowalski/skills`; request/use the `apple-design` skill from that repository when working on interaction or motion.
- Caveat: the skill explicitly translates Apple/WWDC ideas to web technologies. Do not transplant web APIs, Apple platform conventions, or visual styling into Compose. Use the underlying interaction principles only, alongside Android conventions and reduced-motion/accessibility support.

## Visual references (not app dependencies)

### Refero Styles

- Source: [styles.refero.design](https://styles.refero.design/).
- Fit: searchable library of AI-readable DESIGN.md systems with colors, typography, spacing, and component patterns. Useful when a task needs visual alternatives or examples for Stitchbook's own design language.
- Use selectively: choose a relevant example per task, record what is being learned (e.g. information hierarchy or spacing), and adapt it. Do not import a website's brand or whole design system into Stitchbook.

### Cult UI

- Source: [cult-ui.com](https://www.cult-ui.com/).
- Fit: the site presents animated UI components and states that its components are built for shadcn/ui. Potentially useful for visual/interaction inspiration for cards, progressive disclosure, and animation.
- Caveat: React/shadcn code is not reusable in this Kotlin/Compose app. Avoid adding web dependencies or copying components verbatim; recreate only a useful interaction with native components and test touch/accessibility behavior.

### Watermelon UI

- Source: [ui.watermelon.sh](https://ui.watermelon.sh/), [developer catalog](https://ui.watermelon.sh/developers).
- Fit: open-source catalog with animated components, blocks, dashboards, templates, and machine-readable discovery resources. Better for broad page-composition ideas than Android implementation.
- Caveat: explicitly React-oriented. Treat it as a visual reference; do not add its React platform/MCP to the Android app. Consider its catalog only when comparing layout ideas.

## Not recommended for this purpose

### Manus

- Source: [manus.im](https://manus.im/).
- Assessment: broad AI product/creation platform with design and website-building offerings, but the reviewed landing page did not expose a focused, reusable UI skill or Android design system. Low value as an installable design reference for Stitchbook. Reassess only if a future task specifically needs product/website inspiration.

## Suggested use in future sessions

1. For a screen audit or polish task, fetch/use one relevant UI Skills review skill and keep its findings specific to the existing Compose screen.
2. For gesture/motion work, consult `apple-design` for principles, then implement with Android-native APIs and respect system motion/accessibility settings.
3. For visual exploration, browse Refero, Cult UI, or Watermelon for a narrowly stated pattern; document the adaptation rather than importing a brand or web implementation.
4. Keep these sources advisory. Product requirements, existing architecture, Android platform behavior, and Stitchbook's design identity take precedence.

No third-party skill or dependency was installed into the application in this review. Installation should be an agent/user-level skill setup when a later task needs it, not an app runtime dependency.
