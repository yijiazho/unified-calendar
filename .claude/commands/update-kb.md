Update the code review knowledge base at `.claude/agent-memory/code-review-engineer/project_codebase_patterns.md` to reflect the code changes made in this session.

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
