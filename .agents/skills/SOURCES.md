# Skill and process sources

Copied by hand after reading; no installers. Re-fetch from the same path to update.

| Item | Repo | SHA | Path | License |
|---|---|---|---|---|
| expo-design-system, expo-native-ui, expo-ui, expo-router, expo-animation, expo-project-structure, expo-data-fetching | expo/skills | 88d9bd7 | plugins/expo/skills/<name> | MIT (650 Industries); expo-animation also carries its own MIT LICENSE (Emil Kowalski) |
| frontend-design | anthropics/skills | e4bb5d9 | skills/frontend-design | Apache-2.0 (LICENSE.txt in the skill folder) |
| design-system/DESIGN.md (adapted reference) | VoltAgent/awesome-design-md | fcc7028 | design-md/claude/DESIGN.md | MIT |
| spec-kit templates, scripts and commands | github/spec-kit | a4d5712 | templates/, templates/commands/, scripts/bash/ | MIT (GitHub, Inc.) |
| hexagonal-ddd, design-system/SKILL.md | own | - | - | project |

Local changes to copied skills: the "Submitting Feedback" section (an npx call that sends data to a third party) was removed, `agents/openai.yaml` was not copied, and a Source line was appended. Spec-kit commands were rendered as the Claude integration does: script paths prefixed with `.specify/`, `{SCRIPT}`, `{ARGS}` and command tokens substituted, `scripts:` frontmatter removed; the PowerShell and Python variants were not copied.
