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

The React Navigation themes in `src/lib/theme/navigation-theme.ts` mirror these tokens (background, card, foreground, border, primary); a test keeps them equal to `global.css`.

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

Measured contrast (WCAG), light / dark. All pass AA (4.5:1 text, 3:1 controls).

| Pair | Light | Dark |
|---|---|---|
| foreground / background | 17.5 | 17.0 |
| primary-foreground / primary | 5.1 | 6.8 |
| destructive-foreground / destructive | 6.5 | 7.3 |
| input / background (control outline, 3:1) | 3.5 | 4.0 |
| muted-foreground / background | 6.3 | 7.3 |
| muted-foreground / card | 5.9 | 6.8 |
| muted-foreground / accent | 5.1 | 5.3 |
| destructive / background | 6.2 | 7.3 |
| destructive / card | 5.8 | 6.8 |
| destructive / accent | 5.0 | 5.3 |
| success / background | 6.0 | 9.4 |
| success / card | 5.6 | 8.7 |
| success / accent | 4.9 | 6.9 |
| warning / background | 5.6 | 9.2 |
| warning / card | 5.2 | 8.6 |
| warning / accent | 4.5 | 6.8 |

The tightest pair is warning on accent in light (4.52, a pass with almost no margin): keep warning text off pressed or selected rows, or darken warning if the palette changes.

## Typography

- Display: `Source Serif 4` (Google Fonts via `@expo-google-fonts/source-serif-4`), weight 400-600, for screen titles only. `font-serif` is 600; `font-serif-regular` (400, `SourceSerif4_400Regular`) is available but currently unused.
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
| caption | 14 / 20 | sans 500 | not used for badges, subtitles, headers or footers; reserved for legal captions |

NativeWind: `text-display`, `text-title`, `text-heading`, `text-body`, `text-small`, `text-caption` defined in `fontSize` with `[size, { lineHeight }]`. Use `allowFontScaling` (default) and avoid fixed-height text containers.

## Spacing, radii, elevation

- Base unit 4 px. Scale: 4, 8, 12, 16, 24, 32, 48, and 13 = 52 for the input height (`min-h-13`). The scale extends the Tailwind default, so the intermediate steps (5, 6, 10, 14...) stay available. Screen padding 16 (compact) / 24 (regular). Gap between sections 32.
- Border widths: `hairline` (device hairline) for cards, list rows and separators; `field` 1.5 px (input outline, `border-field`); the Button outline variant is 2 px (`border-2`). Only controls keep the 3:1 outline; decorative borders use `border-hairline`, never 2 px.
- Radii: `sm` 6, `md` 10 (buttons, inputs), `lg` 14 (cards), `xl` 20 (sheets), `full` (avatars).
- Elevation by tone and hairline border, not heavy shadows; one soft shadow for sheets only.

## Components (React Native Reusables)

Components are copied into `components/ui/` and restyled only through the tokens above.

| Component | Rule |
|---|---|
| Button | min height 48 (`min-h-12`), `md` radius, label body-strong. Variants: default (primary), secondary, outline (input colour, 2 px `border-2`), ghost, destructive. Pressed state darkens 8 percent; disabled keeps 4.5:1 label |
| Input / Textarea | min height 52, input border 1.5 px, label above (never placeholder-only), error text with icon under field |
| Select, Checkbox, Radio, Switch | hit area 48 minimum, visible label, selected state shows check icon |
| Card | `card` surface, `lg` radius, 16-24 padding, hairline border, no nested cards |
| Tabs / bottom nav | max 5 items, icon + text label always, active uses `accent` surface and primary text |
| Dialog / Alert dialog | for destructive confirmation only; forms open as `formSheet` routes (`formSheetOptions`: new booking, new event, new and edit user), not as dialogs; the other forms are screens inside their tab stack |
| Badge (`StatusBadge`) | body-strong 18 px, icon plus text, colour from success/warning/destructive, neutral for muted states; never colour alone |
| ListSection | grouped list: `card` surface, `lg` radius, hairline border, optional header above and muted footer below, both 18 px body text |
| ListRow | whole row pressable, min height 48 (`min-h-12`), hairline separator between rows, optional leading symbol, title (body-strong), subtitle (muted, 18 px body), trailing value (shrinks, right aligned, stacked under the subtitle above 1.3 font scale) and a chevron on pressable rows unless destructive or `accessory="none"`; radio rows expose `aria-checked`; symbols come from `expo-symbols` with a text glyph fallback on web |
| RadioSection | single choice (room, event type, role): a `ListSection` of radio `ListRow`s inside a `radiogroup` labelled with the section title; the check mark sits at the trailing edge of the selected row; replaces full-width buttons with a leading check |
| DateField | date or time choice with the value format of the API (`AAAA-MM-DD`, `HH:MM`) converted at the boundary (`lib/bookings/date-value`). Field outline and height like Input (`min-h-13`, `border-field`), label as accessibility label, chosen value as accessibility value (long date, time spoken as hours and minutes), `accessibilityState.expanded`, placeholder "Elegir" when empty on native only. `minimumDate` and `maximumDate` limit the choice (native picker and web `min` / `max`): event and booking dates start today; "Repetir hasta" runs from the chosen date to one year after it and opens on that date. iOS: pressing opens an inline calendar (date) or spinner (time) from `@react-native-community/datetimepicker`, themed with the colour scheme and given accessibility focus, with a Listo button; an empty field stays empty until the user changes the picker or presses Listo, which takes the shown date. Android: native dialog that writes only when confirmed, never when dismissed. Web (`date-field.web.tsx`): `input type=date` or `time` with the hint as `aria-description`, no placeholder; `:root { color-scheme: light dark }` in `global.css` makes native web inputs follow dark mode |
| FormField, FormScroll | `FormField` is one cell of a form `ListSection` (optional label and description above the control, `p-4`); `FormScroll` is the form container: `ScrollView` with `automaticallyAdjustKeyboardInsets` and taps kept working while the keyboard is open, wrapped in a `KeyboardAvoidingView` (`padding`) on Android |
| StateView | one component for loading (`ActivityIndicator` + text), empty (icon + text) and error (card with message and a Retry button); lists add pull-to-refresh |
| Avatar | 40 / 56, initials fallback |
| Toast | 5 s minimum, dismissible, never the only feedback |
| Skeleton, Progress | respect reduced motion |

## Forms

- Fields are grouped in `ListSection` cards with a header; inputs inside keep `min-h-13` and the 1.5 px `border-field` outline.
- Single-line text fields set `returnKeyType`: `next` and focus the following field when one follows, `done` on the last, which submits where sensible. Multiline fields (the booking reason) keep the return key for new lines and set none.
- Date and time never use free-text inputs: `DateField`.

## Screens and states

Every data screen implements loading, empty, error and content states with plain-language messages (Spanish copy in i18n files). Forms validate on blur and submit, never on every keystroke.

## Destructive actions and feedback

- Rows open their detail or edit on tap. Deleting is a long-press on the row, also exposed as a `delete` accessibility action and announced by an accessibility hint, with a hint in the section footer. Both are offered only when `canManageAccount(me, target)` (`src/lib/users`) allows it: never on the own account, and a moderator only on registered users.
- Confirmation is a native `Alert.alert` on iOS and Android (`src/lib/confirm.ts`); on web `confirm.ts` reports that it showed nothing and the caller (`users/list.tsx`, `users/delete-user.tsx`, `rooms/rooms-admin.tsx`) opens the `AlertDialog`.
- Haptics (`src/lib/haptics.ts`): light impact on successful save, error notification on failure; no-op on web. Every form gives error feedback on a validation or API failure and success feedback on save.

## Motion

150 to 250 ms ease-out for feedback; no parallax or auto-playing loops; disable when reduce-motion is on.

## Do and don't

- Do keep one primary action per screen.
- Do use real labels, not icon-only buttons.
- Don't rely on hover; this is touch first.
- Don't use pure black or pure white backgrounds.
- Don't put text over images without a scrim that keeps 4.5:1.

## NativeWind wiring

`app/src/global.css` is the single source of the colour values: `:root` holds the light tokens and `@media (prefers-color-scheme: dark)` redefines them for dark. `tailwind.config.js` exposes every token as `var(--token)`.

```js
colors: {
  background: 'var(--background)',
  primary: { DEFAULT: 'var(--primary)', foreground: 'var(--primary-foreground)' },
}
```

`app/src/lib/theme/colors.ts` mirrors the values that React Navigation and `Icon` need, and `navigation-theme.test.ts` keeps it equal to `global.css`. Do not copy hex values anywhere else; the table above documents them and `global.css` wins on any difference.
