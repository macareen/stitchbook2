# Stitchbook Product Specification

## 1. Product summary

Stitchbook is a private, local-first Android companion for fibre crafts. It helps a user organize active work and long-term records without making the application, a vendor cloud, or a subscription the sole gateway to their content.

The application will be an original product. References to other craft applications describe useful categories of functionality only and do not authorize copying proprietary code, branding, prompts, text, or UI.

## 2. Product goals

1. Make active crafting easier through reliable counters, notes, and project context.
2. Provide a durable personal record of projects, yarn, tools, patterns, photos, and time.
3. Keep essential workflows fully useful offline.
4. Give the user direct access to original files and portable exports.
5. Support multiple fibre crafts without forcing knitting concepts onto every workflow.
6. Build a foundation for optional integrations while keeping local records authoritative.

## 3. Product status labels

- **MVP:** required for the first coherent, useful release. The initial MVP is the application shell, settings, local project records, configurable counters, and a basic versioned export of those durable records described in Roadmap Phases 1–3.
- **Later:** planned after the MVP and sequenced in the roadmap.
- **Experimental:** exploratory and must be optional, reviewable, and safe to disable.
- **Out of scope:** deliberately excluded from the product direction.

## 4. Users and use contexts

The primary user is an individual crafter who wants a private, dependable companion during and between projects. The product should remain understandable for people who use more than one craft, keep a substantial pattern or stash library, or need accessible low-friction counter controls.

Essential actions should remain usable without connectivity. Active crafting controls should minimize distraction and prevent accidental loss of progress.

## 5. Cross-cutting requirements

### 5.1 Data ownership and portability — MVP foundation, expanded later

- The user owns all application data.
- The app must not become the sole way to access imported patterns, photos, notes, inventory, or project records.
- Essential functions must work offline.
- No subscription, paid cloud service, or mandatory account may be required.
- A user-selected, accessible library folder is the intended home for original PDFs, photos, exports, and portable metadata.
- Room may provide indexes, relationships, transactions, and application state, but must not be the sole irreplaceable source of user content.
- Before a durable record type is considered release-ready, the user must be able to export it to a user-selected document in a documented, versioned format. This early safety export is not a substitute for Phase 10's complete library backup and restore.
- Portable formats should include JSON, CSV, Markdown, PDF, and PNG where appropriate.
- Complete library backup and restore are required in a later phase.
- The app must never silently overwrite an imported original.

### 5.2 Privacy and control — all phases

- Purchased PDFs, private notes, photos, and generated guides are private by default.
- External uploads or write-back require explicit user action and clear scope.
- Exports must allow the user to choose included fields and photos.
- Recorded and estimated values must be visibly distinguished.
- Optional integrations must be replaceable and must not become the master source of truth.

### 5.3 First-class crafts — MVP foundation, expanded later

The craft model must represent:

- Knitting
- Crochet
- Tunisian crochet
- Loom knitting
- Other fibre crafts

Craft-specific terminology, counters, gauge calculations, tools, parsing rules, and defaults may differ. “Other” should support user-defined labels without erasing the broader craft category. Craft is structured data, not merely a display label.

At minimum:

- Knitting may distinguish stitches, rows, rounds, needle types, and constructions such as raglan or circular yoke.
- Crochet may distinguish stitches, rows, rounds, motifs, joined versus continuous rounds, and hook-based gauge without inheriting needle-only fields.
- Tunisian crochet may use forward/return passes, row definitions, long hooks or interchangeable hooks with cables, and its own gauge terminology.
- Loom knitting may use pegs, wraps or stitches, rows or rounds, and loom gauge/spacing.
- Craft-specific defaults must remain editable; they must not prevent mixed-technique or unusual projects.

## 6. Functional requirements

### 6.1 Application shell and settings — MVP

- Provide Compose/Material 3 navigation between the dashboard, projects, active counter experience, and settings.
- Support theme and basic preference settings.
- Establish accessible typography, touch targets, focus order, and content descriptions.
- Present empty, loading, success, and error states explicitly.
- Avoid requiring sign-in or network access at launch.

### 6.2 Projects — MVP core, enriched later

**Implementation status:** Basic local CRUD is implemented for UUID identity, name, craft, project type, status, optional notes, and created/updated timestamps. Construction method, project dates, custom project-type labels, relationships, richer fields, and portable export remain deferred. This is an implementation milestone, not completion of the full MVP requirement.

Each project should support:

- Name and description
- Craft
- Project type, including custom types
- Construction method
- Status: planned, active, paused, completed, or abandoned
- Start and completion dates
- Pattern, yarn, and tool relationships
- Selected pattern size
- Gauge
- Progress percentage
- Notes and modifications
- Milestones
- Progress photos
- Journal entries
- Counters
- Timed work sessions
- Yarn consumption
- Statistics
- Exportable project summaries

Initial MVP project CRUD requires identity, name, craft, status, optional description, relevant dates, project type, and construction method. Remaining fields are added with the dependent feature phases.

The MVP must provide a basic versioned JSON export of project records to a user-selected destination. Export failure must be visible, and the export must not require a cloud provider.

Suggested project types include sweaters, cardigans, tops, socks, hats, scarves, shawls, blankets, bags, amigurumi, homeware, accessories, and custom types.

Suggested construction methods include top-down, bottom-up, flat, in the round, seamed, seamless, raglan, circular yoke, drop shoulder, motif-based, modular, and amigurumi spiral. These values must be extensible and may have craft-specific relevance.

### 6.3 Counters and active crafting — MVP

Support project-specific and standalone counters for:

- Main rows or rounds
- Pattern repeats
- Cable repeats
- Increase or decrease events
- Motifs completed
- Panels completed
- Squares joined
- Border rounds
- Sleeves or other garment parts
- User-defined purposes

A counter may have:

- A current value and optional goal
- Increment and decrement actions
- Reset with confirmation or undo appropriate to the risk
- Notes attached to particular values
- Automatic reset rules
- Linked behavior between counters
- Repeating schedules
- Persistent Android notifications with actions

Counter changes must persist promptly. Rule processing must be deterministic, testable, resistant to cycles, and clear to the user. Notification behavior may follow after basic on-screen counters if platform constraints require staged delivery within the phase.

Counter labels and schedules must support craft language such as rows, rounds, motifs, squares, forward/return passes, pegs, panels, and user-defined terms. Counters and value-specific notes must be included in the MVP's versioned export before the counter feature is release-ready.

### 6.4 Sessions and statistics — later

Track timed work sessions and produce:

- Total crafting time
- Time by project, craft, day, week, month, and year
- Session count and average session length
- Rows or rounds completed
- Estimated stitches completed
- Average time per row or round
- Current and longest streak
- Projects started and completed
- Project duration
- Yarn consumed by weight and length
- Gauge history
- Tool usage
- Progress trends

Session correction and deletion must be possible. Statistics must identify their source window and distinguish recorded measurements from estimates or derived values.

### 6.5 Pattern library — later

Patterns may be:

- Imported PDFs
- Web references
- Book or magazine references
- Personal designs
- Manual instructions

Pattern metadata should support tags, categories, designer, source, purchase information, gauge, sizes, yardage, recommended tools, notes, and Ravelry identifiers. One pattern may link to multiple projects.

For imported PDFs, the app must:

- Keep the untouched original PDF.
- Store or reference it in a user-accessible location selected through SAF.
- Allow opening it in another application.
- Never overwrite it.
- Never make the app the only way to access it.
- Track durable access or clearly explain when access must be re-granted.

The app must not redistribute pattern content.

### 6.6 Pattern parsing — deterministic later; local AI experimental

A future parser should:

- Extract PDF text and layout.
- Detect sizes and size-specific values.
- Ask the user to select a size.
- Produce a row-by-row or step-by-step guide.
- Handle sections, repeats, abbreviations, simultaneous instructions, and conditional instructions.
- Show source references for generated steps.
- Allow manual correction.
- Require user review rather than silently trusting generated output.
- Prefer deterministic parsing before optional local AI assistance.
- Preserve the original pattern privately and never redistribute its contents.

Parsing is explicitly excluded from early phases. Local AI assistance is experimental, optional, on-device where feasible, and must not upload private pattern contents automatically.

A reviewed manual or generated guide should eventually run through the deterministic execution engine specified in [docs/EXECUTION_ENGINE_SPEC.md](docs/EXECUTION_ENGINE_SPEC.md). **Focus mode** presents the current executable instruction with its section, range, and repeat context for low-distraction crafting. **Pattern Map** presents the same guide and progress as a navigable hierarchy, derives container progress from executable steps, and supports explicit jumps without treating skipped work as complete. Both are later features and must share one persisted execution state rather than maintaining competing progress models.

### 6.7 Yarn stash — later

Support:

- Brand and yarn line
- Colourway and dye lot
- Fibre composition
- Weight category
- Metres or yards per skein
- Skein weight
- Full and partial skein quantities
- Remaining measured weight
- Estimated remaining length
- Purchase information
- Storage location
- Photos
- Allocated and unallocated quantities
- Associated projects
- Notes and care instructions
- Ravelry yarn ID

Calculations must preserve original measurement units or record conversions, use explicit rounding, and mark estimated length as estimated. Allocation and consumption operations should be transactional and must prevent impossible negative quantities unless the user deliberately records a correction.

### 6.8 Tools inventory — later

The model must support individual components and grouped sets for:

- Straight needle pairs
- Circular needles
- Double-pointed needle sets
- Interchangeable tip pairs
- Interchangeable cables
- Crochet hooks
- Tunisian crochet hooks
- Looms
- Cable needles
- Stitch markers
- Connectors
- End stoppers
- Tightening keys
- Other notions

Bulk creation should support size ranges, custom size selections, quantity per size, manufacturer-set templates, and reusable user templates.

Interchangeable systems should track tip or hook size, tip or hook length, the manufacturer's stated cable length, how that stated length is defined, approximate assembled length, connector family, compatibility, adapters, quantities, storage, and project assignments. Canonical dimensions should be stored in explicit units and displayed in the user's preferred units without relying on ambiguous size labels.

A complete commercial set, such as a ChiaoGoo five-inch interchangeable set, should appear as a grouped set while retaining separately identifiable underlying components. Group membership must not duplicate component quantities: availability and project assignment are calculated from the underlying inventory. A component may participate in a set definition while still being individually searchable. Templates describe what to create; they are not the authoritative inventory after creation.

### 6.9 Photos, journal, and milestones — later

Projects should support:

- Progress photos
- Captions and dates
- Milestone associations
- Before-and-after comparisons
- Private journal entries

Photo originals must remain user-accessible. Long-lived originals must not be kept only in application-private files. Deleting an app record must not delete an external original; deletion of a user-owned file requires a separate, explicit action naming the file.

### 6.10 Exportable visual cards — later

Generate aesthetic PNG cards without operating a built-in social network. Potential types are:

- Progress update
- Completed project
- Milestone
- Weekly summary
- Yarn card
- Annual summary
- Before and after

The user chooses fields and photos, previews the result, and exports or shares through Android mechanisms. Export should not publish automatically and should avoid exposing private metadata unintentionally.

### 6.11 Backup, restore, and portable metadata — later

- Export a versioned manifest plus portable records and referenced user files.
- Validate backups before reporting success.
- Restore into an empty library and support a reviewed merge into an existing library.
- Never silently overwrite conflicts.
- Preserve unknown fields where practical for forward compatibility.
- Provide recovery information for missing or inaccessible external files.

Potential future storage providers include local storage, Google Drive, OneDrive, Dropbox, Nextcloud, and user-controlled servers. All remote providers remain optional.

### 6.12 Ravelry integration — later

Potential features:

- Import library metadata
- Import yarn stash
- Import projects
- Link local records to Ravelry IDs
- Controlled synchronization
- Conflict resolution
- Optional write-back where officially supported

Ravelry is an external integration, not the master source of truth. Purchased PDFs and private generated guides remain local and are never uploaded automatically. Integration must follow official APIs and terms.

## 7. Quality requirements

- **Reliability:** counter and session changes survive normal lifecycle events and process recreation.
- **Accessibility:** scalable text, adequate contrast, meaningful semantics, large counter controls, and screen-reader-friendly status.
- **Performance:** common local queries and counter actions should feel immediate with realistically sized personal libraries.
- **Testability:** nontrivial calculations, counter rules, storage decisions, migrations, import/export, and conflict logic require automated tests.
- **Maintainability:** use understandable data/domain/UI boundaries and avoid unnecessary frameworks or modules.
- **Migration safety:** database and portable schema evolution must be explicit and tested against representative prior data.

## 8. Out of scope

- A built-in community, social feed, follower system, messaging system, or social network
- Automatic public posting
- Pattern marketplace, resale, or redistribution
- Copying proprietary application code, branding, prompts, wording, or UI
- Mandatory cloud storage, paid services, subscriptions, or accounts
- Treating Ravelry or another provider as the authoritative datastore
- Automatic uploading of purchased PDFs or private generated guides
- Silent, unreviewed AI-generated pattern instructions
- Early-phase PDF parsing

## 9. Release boundary

The first useful MVP is complete when the application shell and settings work offline, local project CRUD is reliable, configurable counters can be used during a project or standalone, project and counter records can be exported in a documented versioned format to a user-selected destination, core behavior is tested, and the project can be built and linted reproducibly. Inventories, pattern storage, sessions, sharing, complete-library backup/restore, integrations, and parsing remain later work even though the data model should avoid blocking them.
