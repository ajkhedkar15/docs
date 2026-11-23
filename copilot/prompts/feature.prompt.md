---
mode: agent
description: Implement a backend feature from business logic, plan first, build after approval
---

# New backend feature

Read `docs/plan.md` and `api/openapi.yml` first.

## What I want to build
<!-- Describe the business logic in plain words: who does what, the rules, what happens in each error case. -->

{{business logic}}

## Spec operation(s), if you know them
<!-- e.g. getAE (GET /api/v1/attribute-tagging/aes/{aeId}). Leave empty if unsure. -->

{{operationId}}

## Instructions
1. Reply with **Understanding** (rules as a numbered list, the spec operation, inputs/outputs/errors, questions and assumptions). If it's not in the spec, stop and propose the spec change instead.
2. Reply with **Plan** (files to create/change by package, queries, DB scripts, the 3–5 tests you'll write).
3. Wait for my "go". No code before that.
4. After "go": the code, the tests, and a short summary with a curl to try it.
