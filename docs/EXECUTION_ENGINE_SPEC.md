# Stitchbook Execution Engine Specification

## 1. Status

This document defines the intended behavior of Stitchbook's future execution engine. It is a product and domain specification, not an implementation plan or a claim that the engine currently exists.

The execution engine will power reviewed manual and generated guides during active crafting. It must remain deterministic, offline, testable, craft-neutral, and independent of PDF parsing or optional AI assistance.

## 2. Purpose and philosophy

A written pattern describes work hierarchically: sections contain ranges, repeats, and individual instructions. A crafter needs that hierarchy translated into a reliable answer to two questions:

1. What should I do now?
2. What has already been completed?

The engine should make progress easy to record without hiding the source structure. Its core principles are:

- **The definition is not the progress.** Editing or importing a guide and executing that guide are separate concerns.
- **Hierarchy remains visible.** Sections, ranges, and repeats retain their meaning rather than being flattened permanently into anonymous steps.
- **Execution is deterministic.** Given the same definition and persisted state, traversal produces the same current instruction and progress.
- **Only executable work receives focus.** Containers organize work; they are never the current executable target.
- **Progress is durable immediately.** A successful action is not acknowledged until its state is committed locally.
- **Derived facts are not duplicated.** Container progress is calculated from descendant execution state.
- **Craft language is data.** A range may describe rows, rounds, motifs, passes, panels, or another user-defined unit.
- **Parsing is optional input.** Manual guides and deterministic parser output use the same reviewed definition model.

## 3. Definition and execution state

### 3.1 Guide definition

A guide definition is the immutable or revisioned description of what work exists and in what order. It contains:

- A stable guide ID
- A definition revision ID
- An ordered tree of nodes
- Stable node IDs
- Node-specific configuration
- Display text and optional source references

The definition does not contain mutable completion flags, the current pointer, or timestamps describing the user's progress.

Once execution has begun, structural edits should create a new definition revision or use a deliberate reconciliation flow. V1 must not silently reinterpret saved progress against a changed tree.

### 3.2 Execution state

Execution state belongs to one execution of one definition revision. It contains:

- A stable execution ID
- The guide ID and exact definition revision ID
- The current executable address, or no address when complete
- Completion state for executable occurrences
- Created and updated timestamps
- Completion timestamp when the execution is finished

Execution state must never contain a private copy of an imported pattern or make Stitchbook the only place where source instructions can be accessed.

## 4. Stable IDs and execution addresses

Every guide, definition revision, node, and execution receives an application-generated stable ID. UUID strings are appropriate unless a later implementation documents an equally stable alternative.

IDs must not be derived from:

- List positions
- Row numbers
- Display labels
- Array indexes
- Parent titles
- Parsed source text

Moving a node within a draft should not change its node ID. Duplicating a node creates a new node ID. Replacing a node with materially different work should create a new node ID.

A node ID identifies a definition node, not one runtime occurrence. A node inside a range or repeat can execute multiple times. An executable occurrence is identified by an **execution address** consisting of:

- The definition revision ID
- The executable Instruction node ID
- The ordered ancestry frames needed to identify the occurrence

An ancestry frame pairs a container node ID with its current range value or repeat iteration. For example:

```text
definition: revision-7
frames:
  - repeat-node: iteration 2
  - range-node: round 4
instruction: knit-node
```

Addresses must be compared structurally. A formatted breadcrumb is display data, not the persisted identity.

## 5. Node types

The v1 definition tree has four node types.

### 5.1 Section

A Section is an ordered organizational container.

Examples:

- Body
- Heel turn
- Border
- Finishing

A Section:

- Has a stable ID and title
- Contains zero or more ordered child nodes while being edited
- Must contain at least one executable descendant before a definition is accepted for execution
- Does not add an iteration dimension
- Is never an executable target

### 5.2 Range

A Range repeats its ordered child subtree once for every value in a bounded, inclusive ordinal span.

Examples:

- Rounds 1–10
- Rows 5–12
- Motifs 1–6

A Range defines:

- A unit label, such as row, round, motif, pass, or panel
- An inclusive start value
- An inclusive end value
- A positive direction in v1
- One or more ordered child nodes

For each range value, the complete child subtree executes before traversal advances to the next value.

### 5.3 Repeat

A Repeat repeats its ordered child subtree a configured number of times.

Examples:

- Repeat the next two rows 6 times
- Work the increase sequence 4 times

A Repeat defines:

- A positive repeat count
- An optional user-facing label
- One or more ordered child nodes

For each iteration, the complete child subtree executes before traversal advances to the next iteration.

### 5.4 Instruction

An Instruction is an executable leaf.

Examples:

- Knit all stitches
- Yarn over, knit two together
- Work one Tunisian simple stitch forward pass and return pass
- Join the next square

An Instruction:

- Has a stable node ID
- Contains the reviewed instruction text
- May contain optional source references and craft-neutral display metadata
- Has no children
- Produces exactly one executable occurrence for each active ancestry combination

V1 completion is binary at the occurrence level. Partial completion within an Instruction is not represented.

## 6. Range versus Repeat semantics

Range and Repeat both cause a subtree to execute more than once, but they express different user intent and must not be treated as interchangeable labels.

| Concern | Range | Repeat |
| --- | --- | --- |
| Meaning | Work associated with named ordinal units | Repetition of a sequence |
| Runtime frame | Current unit value | Current iteration number |
| Example context | Round 7 of rounds 1–10 | Repeat 3 of 6 |
| Start | Explicit inclusive ordinal | Always iteration 1 in v1 |
| End | Explicit inclusive ordinal | Configured repeat count |
| Typical source wording | “Rounds 1–10” | “Repeat these two rows 6 times” |

Changing a Range into a Repeat, or a Repeat into a Range, changes execution addresses and user-visible meaning. It is a structural definition change, not a cosmetic edit.

## 7. Hierarchical execution model

Traversal is deterministic depth-first traversal over executable occurrences:

1. Visit children in their stored order.
2. A Section visits each child once.
3. A Range visits its complete child subtree for each inclusive range value.
4. A Repeat visits its complete child subtree for each repeat iteration.
5. An Instruction yields one executable occurrence.

Containers may nest. Cycles are invalid. An accepted executable definition must produce at least one Instruction occurrence and must have finite bounds.

The engine should calculate traversal lazily from the definition and address frames. It should not require a permanently expanded copy of every repeated Instruction.

### 7.1 Current-pointer invariant

While an execution is active, the current pointer always targets an executable Instruction occurrence.

The pointer must never target:

- A Section
- A Range
- A Repeat
- A missing node
- An address from a different definition revision

When every executable occurrence is complete, the execution is complete and has no current pointer. This terminal absence is the only exception to the leaf-pointer rule.

If persisted state contains an invalid address, the engine must fail safely and request recovery or reconciliation. It must not guess a new position and silently change progress.

## 8. Derived container progress

Section, Range, and Repeat progress is derived from their descendant executable occurrences. It is not stored independently.

- **Not started:** no descendant executable occurrence is complete.
- **In progress:** at least one, but not all, descendant executable occurrences are complete.
- **Complete:** every descendant executable occurrence is complete.

Definitions with containers that have no executable descendants are invalid for execution, so container progress never needs an “empty” state.

For a Range, the UI may additionally derive completed values and the current value. For a Repeat, it may derive completed iterations and the current iteration. These are projections of leaf completion, not separate authoritative counters.

## 9. Execution actions

### 9.1 Complete

Completing the current occurrence is one atomic domain action:

1. Validate that the execution and definition revision match.
2. Validate that the current address resolves to an Instruction occurrence.
3. Mark that occurrence complete if it is not already complete.
4. Find the next incomplete executable occurrence after the current occurrence in traversal order, skipping later occurrences that are already complete.
5. If no incomplete occurrence exists after the current occurrence, wrap to the earliest incomplete executable occurrence in the definition.
6. If an incomplete occurrence exists, set it as current and keep the execution active.
7. Only when every executable occurrence is complete, clear the current address and mark the execution complete.
8. Commit the update before reporting success.

The action is idempotent for the current occurrence. Repeating the same command must not create duplicate progress records. If the current occurrence was already complete because the user jumped to it, Complete still advances to the next incomplete occurrence. Wrapping changes only the current pointer; it does not infer that skipped work was completed.

### 9.2 Previous

Previous is the deliberate rewind action:

- From an active current occurrence, select the immediately preceding executable occurrence.
- From a completed execution, select the final executable occurrence.
- Mark the selected occurrence incomplete.
- Set it as the current pointer.
- Persist both changes atomically.

At the first occurrence, Previous is a no-op and must not underflow the guide.

Previous does not recursively erase all later completion. A guide may therefore contain completed occurrences after the current pointer following a rewind or jump. Derived container progress must continue to use actual leaf completion rather than assuming a contiguous prefix.

### 9.3 Jump

Jump moves the current pointer directly to a selected executable occurrence.

Jump:

- Accepts a validated execution address, not a list index
- Never targets a container
- Does not complete or uncomplete any occurrence
- Does not infer that skipped work was completed
- Persists the new pointer before the UI reports success

The UI must show whether the target is already complete and should confirm jumps that move far from the current position. Invalid or stale addresses fail visibly.

## 10. Persistence and atomicity

### 10.1 Immediate Room persistence

Every Complete, Previous, and Jump action must be persisted to Room immediately. Debounced or on-exit-only persistence is not acceptable for active crafting progress.

The UI may show an in-flight state, but it must not permanently present an action as successful before Room confirms the write. Persistence failures must remain visible and recoverable without losing the last committed state.

### 10.2 Atomic progress updates

Each progress command must use one Room transaction for all affected state, including:

- Occurrence completion changes
- Current-pointer changes
- Execution completion state
- Relevant timestamps

A crash must not leave an occurrence marked complete while the pointer still represents the pre-action state, or move the pointer without the corresponding completion change.

The exact Room entities and tables are deliberately not specified here. A later persistence design should follow this behavior and include migration tests; this document does not change the current database schema.

## 11. Crash and process-death recovery

After a crash, process death, or device restart:

- Reopening an execution restores the last fully committed definition revision, leaf completion state, and current address.
- No in-memory event is treated as committed progress.
- An interrupted transaction leaves the previous committed state intact.
- The engine revalidates the persisted address against the referenced definition revision.
- A completed execution reopens as complete with no current pointer.
- A persistence error never advances progress silently.

The engine is not required to reconstruct taps that occurred after the last successful transaction. Immediate persistence minimizes that window.

## 12. Focus view

Focus view is the low-distraction active-crafting presentation of the execution engine.

It should show:

- The current Instruction prominently
- Section breadcrumbs
- Current Range value and bounds where applicable
- Current Repeat iteration and total where applicable
- Clear Complete and Previous actions
- Access to Jump and Pattern Map
- Persistence/error state without exposing database details
- Source references when the guide provides them

Focus view must use accessible labels, scalable text, predictable focus order, adequate touch targets, and craft-appropriate terminology. It must not hide ambiguity introduced by a parser or imply that generated instructions are authoritative without review.

## 13. Pattern Map view

Pattern Map is the hierarchical overview of the same guide definition and execution state.

It should:

- Display Sections, Ranges, Repeats, and Instructions as a navigable tree
- Show derived Not started, In progress, and Complete status for containers
- Highlight the current executable occurrence
- Show range values and repeat iterations without permanently flattening the definition
- Allow expansion and collapse without changing progress
- Allow a user to select an executable occurrence as a Jump target
- Distinguish completed and incomplete occurrences accessibly
- Retain source references and issue markers where available

Pattern Map is not a second progress model. It reads and commands the same engine state as Focus view.

## 14. Examples

### 14.1 Knit 10 rounds

Definition:

```text
Section: Body
└── Range: rounds 1–10
    └── Instruction: Knit all stitches
```

The Instruction has one stable node ID and produces ten occurrences. At round 4, its execution address contains the Range node ID with value 4 and the Instruction node ID. Completing it advances to round 5.

### 14.2 Repeated two-row lace sequence

Definition:

```text
Section: Lace panel
└── Repeat: 6 times
    ├── Instruction: Lace row A — yarn over, knit two together across
    └── Instruction: Lace row B — purl across
```

Traversal executes row A then row B for repeat 1, followed by row A then row B for repeat 2, through repeat 6. The Repeat is In progress whenever some but not all twelve occurrences are complete.

### 14.3 Repeat containing a range

Definition:

```text
Section: Textured band
└── Repeat: 3 times
    └── Range: rounds 1–4
        └── Instruction: Work texture round
```

This produces twelve executable occurrences. An address identifies both the repeat iteration and the round value. Completing repeat 2, round 4 advances to repeat 3, round 1.

### 14.4 Sleeves worked two at a time — deferred

“Work both sleeves two at a time” represents simultaneous progress across distinct garment parts. Modeling it as two independent sequential repeats would be misleading because one physical action advances both parts together.

V1 does not model this case. A future design may add Part nodes, shared progress groups, or simultaneous-work semantics. Until that behavior is formally specified, the editor and parser must surface the instruction as unsupported or preserve it as a reviewed single Instruction without inventing independent sleeve progress.

### 14.5 Completing after a forward jump

Consider a four-occurrence guide where only occurrence 1 is complete:

```text
1: Complete
2: Incomplete
3: Incomplete
4: Current, incomplete
```

The user has jumped forward to occurrence 4. Completing it marks only occurrence 4 complete. Because there is no later incomplete occurrence, Complete wraps the current pointer to occurrence 2, the earliest remaining incomplete occurrence:

```text
1: Complete
2: Current, incomplete
3: Incomplete
4: Complete
```

Completing occurrence 2 advances to occurrence 3. Completing occurrence 3 leaves no incomplete occurrences, so the engine clears the current pointer and marks the execution complete. At no point does the forward jump or wraparound mark skipped occurrences complete.

## 15. Explicit v1 limitations

V1 is intentionally limited to:

- The four node types defined here
- Finite, ascending Range bounds
- Finite, positive Repeat counts
- Deterministic depth-first traversal
- Binary completion per Instruction occurrence
- One current executable pointer per execution
- One definition revision per execution
- Manual guide creation and correction
- Sequential work, even though containers may nest

V1 does not include:

- Parts or garment-component coordination
- Conditions or branching
- Measurement-based completion
- Simultaneous work
- Partial completion within one Instruction
- Arbitrary scripts or formulas
- Cross-guide jumps
- Automatic reconciliation after structural definition edits
- PDF parsing
- AI-generated execution decisions

## 16. Future extensions

Future revisions may add:

- **Parts:** separately identifiable garment or project components with coordinated progress
- **Conditions:** reviewed branches based on size, craft choice, prior result, or user selection
- **Measurement-based steps:** completion based on a recorded length, circumference, weight, or other typed measurement
- **Simultaneous work:** one action intentionally advancing multiple parts or execution tracks
- **Linked counters:** deterministic counter effects associated with an Instruction
- **Source-aware corrections:** definition revisions that reconcile accepted parser corrections with existing progress
- **Optional parser integration:** deterministic text/layout parsing that proposes definition nodes with provenance

Each extension must preserve stable identity, deterministic traversal, atomic persistence, explainable progress, and explicit user review.

## 17. Required test categories for implementation

The eventual implementation should include:

- Pure traversal tests for every node type and supported nesting combination
- Stable-address tests across non-structural edits
- Complete, Previous, and Jump transition tests
- Derived container-progress tests, including non-contiguous completion
- Empty, cyclic, invalid-bound, missing-node, and revision-mismatch validation tests
- Room transaction and rollback tests
- Process-death restoration tests from committed state
- Focus view and Pattern Map accessibility tests
- Regression fixtures for the examples in this specification
