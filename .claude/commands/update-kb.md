Update all living documentation to reflect the code changes made in this session. This covers three targets:

---

## 1. Code Review Knowledge Base

**File**: `.claude/agent-memory/code-review-engineer/project_codebase_patterns.md`

Steps:
1. Read the current knowledge base file in full.
2. Identify what code was written or modified in this session (use git diff or the conversation context).
3. For each relevant change, decide:
   - **Remove**: entries that describe a bug or risk that was just fixed — they are now stale and will mislead future reviewers.
   - **Update**: entries that are partially stale (e.g., a pattern that changed but the section header is still correct).
   - **Add**: new patterns, architectural decisions, known risks, or watch points introduced by the code written this session.
4. Write the updated file. Keep the existing section structure. Add new sub-sections only when the changes belong to a clearly distinct feature area (e.g., a new task like TASK-008).
5. For each added entry, follow the existing style: one bullet per finding, bold the risk/pattern name, then a dash and the explanation. Include *why* something is a risk and *when* to flag it so future reviewers can apply judgment rather than just pattern-matching.
6. Do not add entries for things already derivable from reading the code (e.g., "Repository uses JdbcTemplate"). Only record non-obvious contracts, recurring failure modes, architecture invariants, and decisions made for non-obvious reasons.
7. Update the section header date to today's date when any entry in that section changes.

---

## 2. README

**File**: `README.md`

Update only when the session introduced changes visible to an external developer (new API endpoints, new features, changed environment variables, changed ports or startup steps). Do not touch sections that are accurate.

Sections to check and update as needed:
- **Features** — add or move items between Phase 1 / Phase 2 when a feature ships or is descoped.
- **API Overview** (Admin APIs / Public APIs) — add any new routes; remove routes that were deleted. Keep the one-line format (`METHOD /path`); do not add descriptions.
- **Environment Variables** — add any new required keys introduced in `application-local.properties`. Remove keys that were dropped.
- **Architecture** / **Tech Stack** — update only if a technology or architectural decision changed (e.g., a new external dependency was added).

Do not rewrite prose, reformat sections, or add commentary — only the minimal diff needed to keep the file accurate.

---

## 3. Execution Plan

**File**: `doc/execution-plan.md`

Read the file, then mark any tasks completed this session as done (follow whatever checkbox or status convention the file already uses). Do not alter task descriptions, reorder items, or add new tasks — the execution plan is an authoritative roadmap, not a scratchpad.
