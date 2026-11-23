---
mode: agent
description: Build a UI feature from a behaviour description, plan first, build after approval
---

# New UI feature

Read `docs/ui-plan.md` and `api/openapi.yml` first.

## What the user should be able to do
<!-- Plain words: what the user sees, clicks or types, what should happen, and what happens on errors. -->

{{behaviour}}

## Screen and API (if you know them)
<!-- e.g. Attribute Tagging, searchAEs (POST /aes/search). Leave empty if unsure. -->

{{screen / operationId}}

## Instructions
1. Reply with **Understanding** (behaviour as a numbered list, API calls and when, loading/empty/error states, permissions, questions). If the data isn't in the spec, stop and say what's missing.
2. Reply with **Plan** (files by folder, props and hook signatures, query keys, the 3–5 tests).
3. Wait for my "go". No code before that.
4. After "go": the code, the tests, and a short summary with how to try it.
