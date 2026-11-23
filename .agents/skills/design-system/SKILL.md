---
name: design-system
description: Project design tokens and component rules for the Expo client (NativeWind, React Native Reusables, light and dark, AA contrast, large readable type for ages 14 to 90). Use when creating or changing any screen, component, theme, colour, typography or spacing in the mobile and web client.
---

# Design system

Source of truth: `DESIGN.md` in this folder. Read it before touching UI.

## Rules

1. Colours, type sizes, spacing and radii come only from the tokens in `DESIGN.md`. No hardcoded hex, no arbitrary pixel values in `className`.
2. Tokens live as CSS variables in `global.css` and are mapped in `tailwind.config.js`; light and dark are defined together.
3. Base components come from React Native Reusables copied into `components/ui/`; screens compose them and hold no styling decisions of their own.
4. Logic (data fetching, mapping, validation) lives in hooks and `lib/`, never inside presentational components.
5. Accessibility is part of done: 48 px targets, labels on every control, AA contrast, state not by colour alone, font scaling to 200 percent, reduced motion honoured, screen reader labels and roles set.
6. Each data screen covers loading, empty, error and content states.
7. When a token changes, update `DESIGN.md` first, then `global.css` and `tailwind.config.js`, and re-check the contrast table.

## Related skills

`expo-design-system` (token and audit conventions), `expo-router`, `expo-project-structure`, `expo-data-fetching`, `frontend-design` (aesthetic direction; keep the calm, warm tone defined here).
