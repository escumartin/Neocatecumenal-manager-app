# Neocatecumenal Manager App Constitution

## Core Principles

### I. Pure domain
The `domain` package of every context is plain Java: no Spring, no JPA, no Jackson imports. Verified by an ArchUnit rule that fails the build.

### II. Ports and adapters
Ports (in and out) are interfaces in `application`; adapters (REST, JPA, messaging) are in `infrastructure`. `application` never depends on `infrastructure`. Verified by ArchUnit.

### III. One Modulith module per bounded context
Each bounded context is one Spring Modulith module. Modules depend on each other only through the public module API or published domain events. `ApplicationModules.verify()` runs in the test suite and must pass.

### IV. Test-first
TDD for domain and application code: the failing test comes first. Domain and use case tests run without Spring. Persistence and other adapters are tested with Testcontainers against real PostgreSQL. No feature is done with failing or skipped tests.

### V. Contract first
`api/openapi.yaml` is the single source of the HTTP contract. Server interfaces and the TypeScript client are generated from it; generated code is never edited by hand. A change to an endpoint starts as a change to the contract.

### VI. Client separation and design tokens
In the Expo client, logic (data access, mapping, validation, state) lives in hooks and `lib/`, not in components. Visual values come only from the tokens in `.agents/skills/design-system/DESIGN.md`, exposed through NativeWind; no hardcoded colours or sizes.

### VII. Accessible for all ages
Users range from 14 to 90 years old and the app is mobile first. Minimum 18 px body text, 48 px touch targets, WCAG AA contrast, labels on every control, support for system font scaling and reduced motion, light and dark themes. Accessibility checks are part of the definition of done.

### VIII. Privacy by design
Personal data is minimised: collect only what a feature needs, justify each field in the spec, avoid sensitive categories unless a spec documents the legal basis, never log personal data, and support access, rectification and deletion. GDPR applies.

### IX. Code hygiene
Zero comments in code and configuration; names carry the meaning. Code, tests, identifiers and commit messages are in English. User-facing copy is localised through resource files.

## Additional Constraints

- Stack: Java 21, Spring Boot 3, Spring Modulith, PostgreSQL with Flyway migrations, Maven; Expo, Expo Router, TypeScript strict, NativeWind, React Native Reusables.
- Secrets never enter the repository.

## Development Workflow

- Spec-driven: constitution, specify, clarify, plan, tasks, analyze, implement.
- Every plan has a Constitution Check against the principles above; violations need a recorded justification.
- A change is done when build, unit tests, ArchUnit and Modulith verification, adapter tests and client type-check and lint pass.

## Governance

This constitution supersedes other practices. Amendments are made through `/speckit.constitution`, with a version bump and a Sync Impact Report. Reviews check compliance with every principle.

**Version**: 1.0.0 | **Ratified**: 2026-11-23 | **Last Amended**: 2026-11-23
