
# ChimeraCompanion Development Rules

## Project

ChimeraCompanion is a lightweight companion plugin
for Paper 26.2.

Prioritize simplicity, readability, and minimal changes.

## Implementation Rules

1. Implement only the explicitly requested feature.
2. Prefer the simplest working implementation.
3. Follow the existing project's coding style.
4. Do not introduce speculative functionality.
5. Do not create interfaces with only one implementation
   unless a concrete need has been demonstrated.
6. Do not introduce factories, registries, event buses,
   or generic frameworks without a clear requirement.
7. Avoid unnecessary null checks, broad exception
   handling, retries, and fallback mechanisms.
8. Keep necessary lifecycle cleanup, input validation,
   and API correctness checks.
9. Do not refactor unrelated code.
10. Do not add dependencies without approval.
11. Do not introduce concurrency or asynchronous
    execution unless necessary.
12. Prefer Paper's existing APIs over custom systems.
13. Avoid creating utility classes for one-time operations.
14. Keep configuration minimal.
15. Write tests for meaningful logic, not trivial methods.

## Development Workflow

Before editing:
- Inspect only the relevant files.
- Explain the proposed changes briefly.
- Identify which files need modification.

During implementation:
- Work on one feature at a time.
- Keep the diff small and focused.
- Do not change unrelated functionality.

After implementation:
- Run the relevant build or tests.
- Report changed files and their purpose.
- Explain any remaining limitations.
- Do not automatically commit or push.

## Review Policy

Do not add code solely to satisfy hypothetical edge cases.

If additional complexity seems necessary, explain
the concrete failure it prevents before implementing it.

When uncertain, prefer reporting the issue rather
than implementing an unrequested solution.
