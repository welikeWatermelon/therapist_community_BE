# AI-DLC Policy

## Purpose

AI-DLC is a team workflow asset for structured AI-assisted planning and design.
It is not the default development workflow for this repository.

## Invocation

AI-DLC applies only when one of the following is true:

- A user explicitly asks to use AI-DLC.
- A tool-specific AI-DLC mode is intentionally selected.
- A team member states in a task or PR that the work is using AI-DLC.

For ordinary development, review, debugging, documentation, and maintenance
work, use the repository's existing operating rules and tool-specific guidance.

## Priority

AI-DLC does not override repository-specific operating rules by default.
If tool-specific guidance such as `AGENTS.md`, local harness notes, or local
Claude instructions exists, those rules govern their own tools unless the user
explicitly invokes AI-DLC for the current task.

When AI-DLC is invoked, apply it only to the current task. Do not convert
unrelated work into AI-DLC stages.

## Artifacts

AI-DLC planning artifacts may be used as drafts or references. Promote only
reviewed, stable decisions into `docs/`.

Do not commit local execution state, personal paths, tokens, MCP settings, or
AI tool runtime state. In particular:

- Do not commit `.mcp.json`.
- Do not commit `.omc/`.
- Do not commit `aidlc-docs/aidlc-state.md`.
- Do not commit `aidlc-docs/audit.md`.

Generated design or reverse-engineering documents may be committed only when
they are intentionally reviewed as team documentation.

## PR Expectations

PRs using AI-DLC should state that AI-DLC was used and summarize the artifacts
that informed the implementation. PRs should not include raw session logs or
local tool configuration.
