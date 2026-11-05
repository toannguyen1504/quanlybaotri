---
name: project-skill-authoring
description: Add or substantially revise a repository-local Codex skill under .agents/skills for a repeatable workflow specific to this project.
---

# Project skill authoring

Create a narrow, discoverable workflow that adds project knowledge without duplicating generic coding guidance or bloating `AGENTS.md`.

## Discover

1. Read `AGENTS.md`, `.agents/SKILLS.md`, existing skill metadata, and the repository files that are authoritative for the proposed workflow.
2. Identify realistic requests that should trigger the skill and nearby requests that should not.
3. Reuse or update an existing skill when the proposed capability overlaps substantially. Prefer one clear routing contract over several near-duplicates.

## Create or update

- Put the skill at `.agents/skills/<lowercase-hyphenated-name>/SKILL.md`; keep the folder name and frontmatter `name` identical and under 64 characters.
- Write a concise `description` that states both the capability and its trigger. Keep automatic discovery enabled unless the user explicitly requests explicit-only invocation.
- Put only shared purpose, essential workflow, project invariants, and resource routing in `SKILL.md`.
- Add `references/` for conditional detail, `scripts/` for deterministic repeated mechanics, and `assets/` only for files copied into output. Link each resource and test scripts that are added.
- Add `agents/openai.yaml` with quoted interface strings and a `default_prompt` that explicitly mentions `$<skill-name>`.
- Update `AGENTS.md` only when the skill is mandatory for a precise class of repository changes. Update `.agents/SKILLS.md` when the catalog or authoring convention changes.
- Do not leave template markers, speculative rules, empty resource directories, or a skill-local README.

## Validate

Use the installed `skill-creator` validator when available. Otherwise check frontmatter delimiters, required fields, folder/name equality, YAML syntax, resource links, unfinished placeholders, and `git diff` manually. Summarize the trigger, files created, and how to invoke the skill.

