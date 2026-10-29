# Repository-local skills

Codex discovers this repository's skills from `.agents/skills/<skill-name>/SKILL.md`. The `name` and `description` in YAML frontmatter are always visible for routing; the body and supporting files are loaded only after a skill is selected.

## Included skills

| Skill | Use it for |
| --- | --- |
| `backend-service-change` | Spring Boot service behavior and service-local tests |
| `angular-feature-change` | Angular pages, components, client services, and UI tests |
| `cross-service-contract-change` | Public/internal APIs, gateway routes, and RabbitMQ contracts |
| `flyway-schema-change` | Service-owned Flyway migrations |
| `project-change-verification` | Selecting and running checks before handoff |
| `project-skill-authoring` | Adding or refining repository-local skills |

`AGENTS.md` contains the routing rules that make important workflows mandatory. Keep that file compact; put workflow detail inside the matching skill.

## Add a skill

1. Copy `.agents/templates/basic-skill` to `.agents/skills/<skill-name>`.
2. Use a lowercase, hyphenated name under 64 characters and make the folder name match the frontmatter `name`.
3. Replace the template description with a narrow routing contract that says both what the skill does and when it applies.
4. Replace the body with project-specific decisions, constraints, and observable output. Remove every template marker.
5. Add `references/`, `scripts/`, or `assets/` only when the workflow genuinely needs them. Link every reference from `SKILL.md`; test every script.
6. Add an `AGENTS.md` trigger only if the workflow must run for a clearly defined class of changes.
7. Validate with the `skill-creator` `quick_validate.py` helper when Python is available, then inspect `git diff` for unfinished placeholders.

Invoke a skill explicitly with `$skill-name`, for example: `Use $project-change-verification to verify this branch.` Normal implicit discovery remains enabled.

