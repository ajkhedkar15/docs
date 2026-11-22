# Copilot instructions

Copy into the app repos:

| File here | Goes to | Used for |
|---|---|---|
| [`backend-copilot-instructions.md`](backend-copilot-instructions.md) | `aur_map_api_svc/.github/copilot-instructions.md` | Backend: Copilot restates its understanding and plan, waits for "go", then builds code + 3–5 tests |
| [`prompts/feature.prompt.md`](prompts/feature.prompt.md) | `aur_map_api_svc/.github/prompts/feature.prompt.md` | Fill-in request for a new feature (business logic in, plan out) |
| [`../plan.md`](../plan.md) | `aur_map_api_svc/docs/plan.md` | The full backend build plan the instructions point to |
| [`ui-copilot-instructions.md`](ui-copilot-instructions.md) | UI repo `.github/copilot-instructions.md` | UI: same understand → plan → "go" → build flow, 3–5 tests |
| [`prompts/ui-feature.prompt.md`](prompts/ui-feature.prompt.md) | UI repo `.github/prompts/ui-feature.prompt.md` | Fill-in request for a UI feature (behaviour in, plan out) |
| [`../ui-plan.md`](../ui-plan.md) | UI repo `docs/ui-plan.md` | The full UI build plan |

Keep them in step with the API spec in [`../api`](../api) and with `plan.md`.
