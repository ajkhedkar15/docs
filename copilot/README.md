# Copilot instructions

Copy into the app repos:

| File here | Goes to | Used for |
|---|---|---|
| [`backend-copilot-instructions.md`](backend-copilot-instructions.md) | `aur_map_api_svc/.github/copilot-instructions.md` | Backend: Copilot restates its understanding and plan, waits for "go", then builds code + 3–5 tests |
| [`prompts/feature.prompt.md`](prompts/feature.prompt.md) | `aur_map_api_svc/.github/prompts/feature.prompt.md` | Fill-in request for a new feature (business logic in, plan out) |
| [`../plan.md`](../plan.md) | `aur_map_api_svc/docs/plan.md` | The full backend build plan the instructions point to |
| [`ui-copilot-instructions.md`](ui-copilot-instructions.md) | UI repo `.github/copilot-instructions.md` | React UI |

Keep them in step with the API spec in [`../api`](../api) and with `plan.md`.
