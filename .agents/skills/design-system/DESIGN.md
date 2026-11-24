# DESIGN.md - Neocatecumenal Manager App

Reference: VoltAgent/awesome-design-md `design-md/claude/DESIGN.md`. Chosen for its warm cream canvas, serif headlines and restrained single accent: calm, readable, nothing flashy. Structure and mood are reused; values are re-derived for WCAG AA and for a 14 to 90 age range. Brand marks and proprietary fonts are not reused.

## Principles

- Mobile first; the same tokens drive web.
- Body text 18 px minimum; never below 16 px except legal captions.
- Touch targets 48 px minimum, 8 px apart.
- Contrast AA: 4.5:1 text, 3:1 borders of controls, focus rings and icons.
- One accent colour. State is never conveyed by colour alone (icon + text).
- Respect system font scaling (up to 200 percent) and reduced motion.
- Light and dark themes are both first class.

## Colour tokens

Defined as CSS variables in `global.css`, consumed by NativeWind through `tailwind.config.js` as `var(--token)`.

| Token | Light | Dark | Use |
|---|---|---|---|
| background | #FAF9F5 | #181715 | screen floor |
| foreground | #141413 | #FAF9F5 | body text |
| card | #F5F0E8 | #1F1E1B | cards, sheets |
| card-foreground | #141413 | #FAF9F5 | text on card |
| muted | #EFE9DE | #252320 | quiet surfaces, tabs |
| muted-foreground | #5F5C55 | #A8A59D | secondary text |
| primary | #A9583E | #E08A6B | main action, links |
| primary-foreground | #FFFFFF | #181715 | text on primary |
| secondary | #EFE9DE | #252320 | secondary action |
| secondary-foreground | #141413 | #FAF9F5 | text on secondary |
| accent | #E8E0D2 | #33302B | selected row, hover |
| accent-foreground | #141413 | #FAF9F5 | text on accent |
| border | #D9D1C3 | #3A3733 | decorative dividers |
| input | #8A8579 | #7A766D | control outlines (3:1) |
| ring | #A9583E | #E08A6B | focus indicator |
| destructive | #B3261E | #F2877F | delete, errors |
| destructive-foreground | #FFFFFF | #181715 | text on destructive |
| success | #2E6B45 | #6FCF8E | confirmed, paid |
| warning | #8A5A00 | #E3B341 | pending, attention |

Measured contrast (WCAG): foreground/background 17.5 / 17.0; muted-foreground/background 6.3 / 7.3; primary-foreground/primary 5.1 / 6.8; destructive-foreground/destructive 6.5 / 7.3; input/background 3.5 / 4.0; success 6.0 / 9.4; warning 5.6 / 9.2. All pass AA.

## Typography

- Display: `Source Serif 4` (Google Fonts via `@expo-google-fonts/source-serif-4`), weight 400-600, for screen titles only.
- Text: `Inter` (via `@expo-google-fonts/inter`), weights 400/500/600.
- System fallback: `serif` / `System`.

| Token | Size / line | Weight | Use |
|---|---|---|---|
| display | 32 / 40 | serif 600 | screen title |
| title | 24 / 32 | serif 600 | section title |
| heading | 20 / 28 | sans 600 | card title |
| body | 18 / 28 | sans 400 | default text |
| body-strong | 18 / 28 | sans 600 | emphasis, labels |
| small | 16 / 24 | sans 400 | helper text, metadata |
| caption | 14 / 20 | sans 500 | badges only |

NativeWind: `text-display`, `text-title`, `text-heading`, `text-body`, `text-small`, `text-caption` defined in `fontSize` with `[size, { lineHeight }]`. Use `allowFontScaling` (default) and avoid fixed-height text containers.

## Spacing, radii, elevation

- Base unit 4 px. Scale: 4, 8, 12, 16, 24, 32, 48, and 13 = 52 for the control height (`min-h-13`). Screen padding 16 (compact) / 24 (regular). Gap between sections 32.
- Border widths: `hairline` (device hairline), `field` 1.5 px (field outline, `border-field`).
- Radii: `sm` 6, `md` 10 (buttons, inputs), `lg` 14 (cards), `xl` 20 (sheets), `full` (avatars, badges).
- Elevation by tone and hairline border, not heavy shadows; one soft shadow for sheets only.

## Components (React Native Reusables)

Components are copied into `components/ui/` and restyled only through the tokens above.

| Component | Rule |
|---|---|
| Button | height 52, `md` radius, label body-strong. Variants: default (primary), secondary, outline (input border), ghost, destructive. Pressed state darkens 8 percent; disabled keeps 4.5:1 label |
| Input / Textarea | height 52, input border 1.5 px, label above (never placeholder-only), error text with icon under field |
| Select, Checkbox, Radio, Switch | hit area 48 minimum, visible label, selected state shows check icon |
| Card | `card` surface, `lg` radius, 16-24 padding, hairline border, no nested cards |
| Tabs / bottom nav | max 5 items, icon + text label always, active uses `accent` surface and primary text |
| Dialog / Alert dialog | for destructive confirmation only; sheets for forms |
| Badge | caption, icon plus text, colour from success/warning/destructive |
| Avatar | 40 / 56, initials fallback |
| Toast | 5 s minimum, dismissible, never the only feedback |
| Skeleton, Progress | respect reduced motion |

## Screens and states

Every data screen implements loading, empty, error and content states with plain-language messages (Spanish copy in i18n files). Forms validate on blur and submit, never on every keystroke.

## Motion

150 to 250 ms ease-out for feedback; no parallax or auto-playing loops; disable when reduce-motion is on.

## Do and don't

- Do keep one primary action per screen.
- Do use real labels, not icon-only buttons.
- Don't rely on hover; this is touch first.
- Don't use pure black or pure white backgrounds.
- Don't put text over images without a scrim that keeps 4.5:1.

## NativeWind wiring

```css
:root { --background: #FAF9F5; --foreground: #141413; --primary: #A9583E; --primary-foreground: #FFFFFF; }
.dark:root { --background: #181715; --foreground: #FAF9F5; --primary: #E08A6B; --primary-foreground: #181715; }
```

```js
colors: {
  background: 'var(--background)',
  foreground: 'var(--foreground)',
  primary: { DEFAULT: 'var(--primary)', foreground: 'var(--primary-foreground)' },
}
```

Repeat the pair for every token in the table. If the Reusables setup generates HSL triplets, convert the hex values above to HSL once and keep this file as the source.
