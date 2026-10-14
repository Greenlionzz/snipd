# ui.md: Professional UI redesign for "Tuned"

This file **overrides all UI decisions** in `implementation.md` (sections 6 and 11) wherever they conflict. The app already builds and runs. The job now is to make it look and feel like a polished, shipping product (think Spotify, Pocket Casts, Snipd, Reeder), not a default-widget prototype.

Do **not** change business logic, database, networking, or AI code. Only touch `ui/`, theme tokens, resources, and the view code of screens.

Constraints stay the same: Kotlin, platform Android SDK only, no Compose, no androidx, no Material Components, no Gradle plugins. Everything below is achievable with plain `View`, `GradientDrawable`, `RippleDrawable`, `Canvas`, `ValueAnimator`.

---

## 0. Working method (follow in order)

1. **Audit first.** Read every screen file. Write a short "UI audit" at the bottom of this file listing: hard-coded colors/sizes, default widgets (stock `Button`, `ActionBar`, `ListView` dividers), inconsistent padding, missing states. Do not skip this.
2. **Build the foundation** (sections 2-4): tokens, helpers, icons, components. Nothing else yet.
3. **Rebuild the shell** (section 5): window, insets, bottom nav, mini player.
4. **Rebuild screens one at a time** (section 6), in this order: Settings → News → Discover → Podcast detail → Episode detail → Library → Full player. After each one, run the checklist in section 9 and tick it in the progress log.
5. **Delete** dead code and old styles once replaced. No two ways of building the same UI element may coexist.

**Golden rule:** no screen may contain a raw color, raw dp number, raw text size, or a stock-styled widget. Everything comes from tokens and components. If a needed token or component doesn't exist, add it to the foundation first.

---

## 1. What makes UI look "unprofessional" (fix every one of these)

| Symptom | Fix |
|---|---|
| Default purple/teal Material 2 colors, pure `#000`/`#FFF` | Use the palette in 2.1 only. Text is never pure white/black. |
| Stock `Button` with square corners or grey look | Use `PrimaryButton` / `SecondaryButton` / `IconButton` components (4.2). |
| System `ActionBar` / title bar visible | Disable it. Draw our own `ScreenHeader`. |
| Content under status bar or gesture bar (no insets) | Apply insets in the shell (5.1). |
| Cramped layouts, edge-to-edge text | 20dp screen gutters, 8dp grid, generous row heights. |
| Tiny or all-same-size text | Use the type scale (2.3). Max 3-4 distinct sizes per screen. |
| Everything is bordered boxes | Use tonal surfaces; outlines only at 1dp and low alpha. |
| Mixed corner radii | Only the radii in 2.4. |
| Emoji or letters used as icons | Use `IconView` (3) only. |
| `ListView` dividers, selectors flashing orange/grey | Disable dividers, `listSelector` transparent, use ripple on rows. |
| Blank screen while loading / on error | Skeletons, `EmptyState`, `ErrorState` (4.9). |
| Jumpy, instant state changes | Short animations (8). |
| Text colors that don't change in light theme | All colors come from `Theme.c`, repainted on theme change. |
| Images pop in abruptly, wrong aspect | `ArtworkView` with placeholder + fade-in + center-crop + rounded clip. |

---

## 2. Design tokens (single source of truth: `ui/theme/Theme.kt`)

### 2.1 Colors

```kotlin
class Palette(
    val bg: Int, val surface: Int, val surfaceHigh: Int, val outline: Int,
    val text: Int, val textSecondary: Int, val textTertiary: Int,
    val accent: Int, val onAccent: Int, val accentSoft: Int,
    val success: Int, val warning: Int, val error: Int,
    val scrim: Int, val ripple: Int,
)

object Theme {
    val dark = Palette(
        bg = 0xFF0E0F13.toInt(), surface = 0xFF16181F.toInt(), surfaceHigh = 0xFF1E212A.toInt(),
        outline = 0x1FFFFFFF, // white at ~12%
        text = 0xFFF2F3F7.toInt(), textSecondary = 0xFFA5A9B8.toInt(), textTertiary = 0xFF6F7385.toInt(),
        accent = 0xFF7C8CFF.toInt(), onAccent = 0xFF0E0F13.toInt(), accentSoft = 0x297C8CFF,
        success = 0xFF4ADE80.toInt(), warning = 0xFFFBBF24.toInt(), error = 0xFFF87171.toInt(),
        scrim = 0x99000000.toInt(), ripple = 0x22FFFFFF,
    )
    val light = Palette(
        bg = 0xFFF7F8FB.toInt(), surface = 0xFFFFFFFF.toInt(), surfaceHigh = 0xFFEEF0F6.toInt(),
        outline = 0x1F12141A,
        text = 0xFF12141A.toInt(), textSecondary = 0xFF5B6070.toInt(), textTertiary = 0xFF8D92A3.toInt(),
        accent = 0xFF4F5FE0.toInt(), onAccent = 0xFFFFFFFF.toInt(), accentSoft = 0x1F4F5FE0,
        success = 0xFF16A34A.toInt(), warning = 0xFFB45309.toInt(), error = 0xFFDC2626.toInt(),
        scrim = 0x66000000, ripple = 0x1A12141A,
    )
    var c: Palette = dark
        private set
    fun apply(isDark: Boolean) { c = if (isDark) dark else light }
}
```

Rules:
- Text on accent uses `onAccent` (dark text on the light indigo in dark mode: it has the right contrast).
- Text on `bg`/`surface` must be `text`, `textSecondary` or `textTertiary`. Nothing else.
- Use **one** accent color. Status colors are only for status.
- Min contrast: body text 4.5:1, secondary 3:1+. Do not put `textTertiary` on `surfaceHigh` for anything important.

### 2.2 Spacing (8dp grid)

`s4=4, s8=8, s12=12, s16=16, s20=20, s24=24, s32=32, s48=48`. Screen horizontal gutter = **20dp**. Vertical gap between sections = **24dp**. Gap between related items = **8-12dp**.

### 2.3 Typography (system font, tight and consistent)

| Token | Size | Weight | Line height | Use |
|---|---|---|---|---|
| `Display` | 28sp | medium | 34 | screen titles |
| `Title` | 20sp | medium | 26 | section titles, sheet titles |
| `Headline` | 17sp | medium | 22 | row titles, card titles |
| `Body` | 15sp | regular | 22 | descriptions, article text |
| `Label` | 14sp | medium | 18 | buttons, chips |
| `Caption` | 12.5sp | regular | 17 | meta lines (author, date, duration) |
| `Overline` | 11sp | medium, +0.8 letterSpacing, ALL CAPS | 14 | section labels, tab labels |

Implementation: `Typeface.create("sans-serif-medium", Typeface.NORMAL)` for medium, `"sans-serif"` for regular. Apply `setLineSpacing(extraPx, 1f)` from the table. Always set `setIncludeFontPadding(false)`. Use `maxLines` + `ellipsize=END` on titles (2 lines max for row titles, 1 for meta).

### 2.4 Shape and elevation

Radii: **8** (small chips/thumbs), **12** (artwork in rows, buttons), **16** (cards), **28** (bottom sheets top corners), **full** (pills, circular buttons). Nothing else.

Elevation: do not use shadows for cards. Use `surface` on `bg` plus an optional 1dp `outline` stroke. Only the mini player and bottom sheets get a soft shadow/scrim.

---

## 3. Icons: `ui/widgets/IconView.kt`

Android has no free icon set without resources/libraries, so build a tiny, consistent icon system. `IconView` is a custom `View` that draws an `Icon` enum value with `Canvas` on a **24-unit grid**, scaled to the view size, **2-unit stroke**, round caps and joins, `Paint.Style.STROKE`, color from tokens. Default size 24dp, tint settable.

Required icons (draw each with `Path` lines/arcs, keep them simple and consistent):

`HOME, SEARCH, LIBRARY, NEWS (rss: dot + 2 arcs), SETTINGS (gear: circle + 8 ticks), PLAY, PAUSE, SKIP_BACK_15, SKIP_FWD_30, NEXT, PREV, SNIP (scissors-free: bookmark with a spark/plus), QUEUE (3 lines + small play), CHEVRON_DOWN, CHEVRON_RIGHT, BACK, CLOSE, PLUS, CHECK, STAR, STAR_FILLED, MORE (3 dots), SHARE, DOWNLOAD, TRASH, SEARCH_CLEAR, SPEED, MOON (sleep timer), SPARKLE (AI), CHAT, LINK, REFRESH, FOLDER, KEY, EYE, EYE_OFF`

Geometry examples (24-grid):
- `PLAY`: filled triangle `(8,5) → (19,12) → (8,19)`, `Paint.Style.FILL`, rounded via `CornerPathEffect(2)`.
- `PAUSE`: two filled rounded rects `(6,5,10,19)` and `(14,5,18,19)`.
- `SEARCH`: circle center `(10.5,10.5)` radius `6.5` + line `(15.5,15.5) → (20,20)`.
- `PLUS`: lines `(12,5)-(12,19)` and `(5,12)-(19,12)`.
- `CHEVRON_RIGHT`: polyline `(9,5) → (16,12) → (9,19)`.
- `SKIP_BACK_15`: 3/4 circle arc with arrow head + the text "15" drawn in 7sp inside.

No other icon source. No emoji. Icons in navigation are 24dp; in rows 20dp; in big player controls 28-36dp.

---

## 4. Helpers and components (build these before any screen work)

### 4.1 Helpers (`ui/dsl/Ui.kt`)

```kotlin
fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density
fun View.dp(v: Int) = context.dp(v)

fun roundRect(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0) =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusPx
        setColor(color)
        if (strokePx > 0) setStroke(strokePx, strokeColor)
    }

fun ripple(base: Drawable?, radiusPx: Float, rippleColor: Int = Theme.c.ripple): Drawable {
    val mask = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = radiusPx }
    return RippleDrawable(ColorStateList.valueOf(rippleColor), base, mask)
}

/** Press feedback for any tappable: ripple + subtle scale, 44dp min touch target. */
fun View.onTap(scale: Float = 0.97f, action: () -> Unit) {
    isClickable = true
    setOnClickListener { action() }
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(scale).scaleY(scale).setDuration(80).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        }
        false // let the click/ripple still happen
    }
}

fun surfaceBg(ctx: Context, radiusDp: Int = 16, outlined: Boolean = true) =
    roundRect(Theme.c.surface, ctx.dpf(radiusDp.toFloat()), Theme.c.outline, if (outlined) 1 else 0)
```

Also provide `text(style, value)`, `vstack{}`, `hstack{}`, `spacer(dp)`, `divider()` (1dp `outline`, inset 20dp), `View.padding(h, v)`, `View.fadeIn()`, `View.slideUp()`.

**Theme switching:** components must read `Theme.c` at construction and also expose `fun applyTheme()` so the shell can rebuild/refresh when the theme changes. Simplest reliable approach: on theme change, call `recreate()` on the Activity and re-inflate the current tab.

### 4.2 Button components

- **`PrimaryButton`**: height 52dp, radius 14dp (or full pill for hero actions), background `accent`, text `Label` in `onAccent`, horizontal padding 24dp, optional leading `IconView` 20dp, 8dp gap. Disabled: 40% alpha. Loading: replace text with a 20dp spinner (custom arc view).
- **`SecondaryButton`**: same size, background `surfaceHigh`, text `text`.
- **`TextButton`**: no background, text `accent`, ripple.
- **`IconButton`**: 44×44dp circle (or 48), icon 24dp, background transparent or `surfaceHigh`, ripple.
- **`Chip`**: height 36dp, pill radius, horizontal padding 14dp, `Label`. Unselected: `surfaceHigh` bg + `textSecondary`; selected: `accentSoft` bg + `accent` text with 1dp `accent` stroke at 40%.
- All buttons: `onTap` feedback, 44dp minimum touch target, `contentDescription` set.

### 4.3 `ScreenHeader`

56dp tall, 20dp gutters. Left: optional back `IconButton`. Title in `Title` (or `Display` for top-level tabs, left-aligned, 28sp, with 16dp top padding and a 4dp subtitle `Caption` if useful). Right: up to 2 `IconButton`s. Large-title tabs collapse into a compact title with a 1dp `outline` bottom border after scrolling 24dp (fade in the border, don't jump).

### 4.4 `ArtworkView`

Square or fixed-ratio `View`, rounded corners via `outlineProvider` + `clipToOutline` (radius per use), center-crop, placeholder = vertical gradient of `surfaceHigh` → `surface` with a faint `LIBRARY` icon, **fade-in 200ms** when the bitmap arrives, 1dp inner `outline` stroke at low alpha so light artwork doesn't bleed into light backgrounds. Sizes: 56dp (rows), 96dp (cards), full-width-minus-gutters (player), 160dp (detail header).

### 4.5 Row components

- **`PodcastRow`**: 72dp min height, 20dp gutters, `ArtworkView` 56dp radius 12, 14dp gap, text column (`Headline` max 2 lines, `Caption` `textSecondary` author, optional `Caption` `textTertiary` meta), trailing action (subscribe/check). Ripple over the entire row.
- **`EpisodeRow`**: top: `Caption` date·duration (`textTertiary`), `Headline` title (2 lines), `Body` one-line `textSecondary` snippet; bottom: play pill ("Play · 42 min") 36dp + progress bar (3dp, `accent` on `outline`) if partially played + overflow `IconButton`. Played episodes: title in `textSecondary`.
- **`ArticleRow`**: title `Headline` (3 lines max), meta `Caption` ("Source · 3h"), optional 80×80dp thumbnail at the right radius 12, unread = 8dp accent dot before meta (read articles dim to `textSecondary`). 16dp vertical padding, divider inset.
- **`SettingsRow`**: 56dp, leading icon in a 36dp `surfaceHigh` circle, `Headline` title, `Caption` subtitle, trailing value or chevron/switch. Group rows inside a single `surface` card with radius 16 and inset dividers; group title is an `Overline` label 8dp above.
- **`SnipCard`**: `surface` card radius 16, 16dp padding: header (episode title `Caption` + time range `Label` in `accent`), summary `Body`, transcript quote `Body` italic in `textSecondary` with a 3dp `accent` left bar, footer actions (play, share, delete as `IconButton`s).

Lists: `ListView` with `divider = null`, `dividerHeight = 0`, `selector = transparent`, `overScrollMode = OVER_SCROLL_NEVER`, `clipToPadding = false`, bottom padding = tab bar + mini player + inset (so the last row is never hidden), `isVerticalScrollBarEnabled = false`. Rows built via the components above with proper view holders.

### 4.6 `SectionHeader`

`Title` text on the left, optional `TextButton` "See all" on the right, 20dp gutters, 24dp top margin, 12dp bottom margin.

### 4.7 `SearchField`

48dp tall, radius full, `surfaceHigh` background, leading `SEARCH` icon 20dp `textTertiary`, hint `Body` `textTertiary`, text `Body` `text`, clear `IconButton` appears when non-empty. Focus: 1.5dp `accent` stroke fade-in. No underline, no default EditText background (`background = null` then set ours), cursor color = `accent`.

### 4.8 `BottomSheet` (custom)

Top corners radius 28, `surface` bg, 40×4dp drag handle in `outline` at the top with 12dp margin, scrim behind (`scrim`, fade-in). Drag-to-dismiss via touch + `translationY`, velocity fling, tap-scrim to dismiss, back button dismisses. Used for: feed picker, speed picker, sleep timer, article AI menu, confirm dialogs. **Do not use `AlertDialog`/`PopupMenu`/`Toast`.**

### 4.9 State components (every list/screen must use these)

- **`Skeleton`**: grey rounded blocks (`surfaceHigh`) with a shimmer (animated alpha 0.5↔1.0, 900ms, repeat reverse) that match the real row layout, 6-8 rows.
- **`EmptyState`**: 72dp circular `accentSoft` badge with a 32dp icon in `accent`, `Title` (centered), `Body` `textSecondary` centered max 280dp wide, optional `PrimaryButton`. Vertically centered, slightly above true center.
- **`ErrorState`**: same layout, `error` color icon, human message, "Try again" `SecondaryButton`.
- **`ToastHost`**: snackbar at the bottom above the mini player/nav: `surfaceHigh` pill/card radius 14, 16dp padding, `Body` text, optional `TextButton` action, slide-up + fade, auto-dismiss 3.5s, swipe to dismiss. Replaces every `Toast.makeText`.

---

## 5. App shell

### 5.1 Window and insets

- Theme: `Theme.Material.NoActionBar`-equivalent platform theme (`android:Theme.DeviceDefault.NoActionBar` family) with `windowBackground` = `bg` color, transparent status and navigation bars (`android:statusBarColor`/`navigationBarColor` = transparent, set in code too).
- Edge-to-edge: `window.setDecorFitsSystemWindows(false)` on API 30+; on 26-29 use `SYSTEM_UI_FLAG_LAYOUT_STABLE | LAYOUT_FULLSCREEN | LAYOUT_HIDE_NAVIGATION`. Disable the forced nav-bar contrast scrim where available (`isNavigationBarContrastEnforced = false` on API 29+).
- Status/nav bar icon colors: light icons on dark theme, dark icons on light theme (`WindowInsetsController.setSystemBarsAppearance` API 30+, `SYSTEM_UI_FLAG_LIGHT_STATUS_BAR` / `LIGHT_NAVIGATION_BAR` below).
- Apply insets once at the root with `setOnApplyWindowInsetsListener`: top inset → `ScreenHeader` padding; bottom inset → bottom nav padding; IME inset → push sheets/inputs up. Never hard-code a status bar height.

### 5.2 Bottom navigation

Height **64dp + bottom inset**, `surface` background with a 1dp top `outline` border, 4 items evenly spaced. Each item: 24dp `IconView`, 4dp gap, `Overline`-style label at 11sp (not all caps here). Active item: icon + label in `accent`, with a 56×32dp pill (`accentSoft`) behind the icon that animates width/alpha in 200ms. Inactive: `textTertiary`. Tap feedback with ripple (bounded to the pill) and a tiny icon scale bounce (1 → 0.9 → 1). Optional unread badge on News (16dp `accent` circle, `onAccent` 10sp text).

### 5.3 Mini player

Floating card above the nav: horizontal margin 12dp, bottom margin 8dp, height 64dp, radius 16, `surfaceHigh` background tinted subtly with the artwork color at 12% alpha, soft shadow (elevation 6dp). Contents: `ArtworkView` 44dp radius 10, title (`Label`, 1 line) + podcast (`Caption` `textSecondary`), play/pause `IconButton` 44dp (filled accent circle when playing is *not* required; keep it simple: icon only), and a **2dp progress hairline** along the bottom edge in `accent`. Slide-up/fade when playback starts, slide-down when cleared. Tap anywhere → expand full player. Swipe up expands too.

### 5.4 Screen transitions

Push: new screen slides in from the right 32dp + fade, old screen fades to 0.9 alpha (220ms, `FastOutSlowIn`). Pop: reverse. Tab switch: cross-fade 150ms, no slide. Respect `ANIMATOR_DURATION_SCALE`.

---

## 6. Screen specs

All screens: bg `bg`, 20dp gutters, header per 4.3, content bottom-padded for nav + mini player. Pull-to-refresh where noted: a custom 32dp spinner arc that drops from the top (do not use stock `SwipeRefreshLayout`).

### 6.1 Discover
1. `ScreenHeader` large title "Discover".
2. `SearchField` (16dp below title).
3. Horizontal `Chip` row of categories (Technology, News, Business, Comedy, Science, Health, Society, Arts…), 8dp gaps, scroll without scrollbars, 20dp start padding.
4. **Hero carousel** (horizontal scroll, snap by 16dp peek): 2-3 large cards 280×160dp, artwork as background with a bottom gradient scrim (transparent → 80% black), title `Headline` white, author `Caption` 70% white. (White text is correct here because it's always on a scrim.)
5. `SectionHeader` "Trending" + vertical list of `PodcastRow`s.
6. While typing: results replace the sections (crossfade), with skeletons while loading and `EmptyState` for no results.
7. "Add by RSS URL" as a `TextButton` under the search field → opens a `BottomSheet` with a `SearchField`-styled URL input + `PrimaryButton` "Add feed" with inline validation messages.

### 6.2 Podcast detail
- Header: tinted gradient (dominant artwork color at 35% → `bg`), 160dp artwork centered with radius 20, title `Display`-ish 24sp centered, author `Caption`, then two buttons side-by-side: `PrimaryButton` "Subscribe" (becomes `SecondaryButton` "Subscribed ✓" using `CHECK` icon, not an emoji), `IconButton` more.
- Description: `Body` `textSecondary`, 4 lines then "More" `TextButton` expanding smoothly.
- `SectionHeader` "Episodes" with count and a sort `Chip`.
- `EpisodeRow` list.

### 6.3 Episode detail
- Compact header: 96dp artwork + title (`Title`, 3 lines) + `Caption` meta.
- Action row: `PrimaryButton` "Play" (full width minus a secondary icon cluster: queue, download, share).
- Segmented tabs (custom `SegmentedControl` 40dp, `surfaceHigh` track, `surface`/`accentSoft` thumb sliding with animation): **Summary · Chapters · Transcript · Chat · Notes**. Scrollable if they don't fit.
- AI tabs with no data: "generate" card: 56dp `SPARKLE` badge, `Headline` "Generate summary", `Caption` cost hint, `PrimaryButton`. While generating: `Skeleton` text lines + progress `Caption`.
- Summary: takeaways as a numbered list with 28dp `accentSoft` circles holding the number, text `Body`.
- Chapters: rows with `Label` time in `accent` (monospace digits, fixed 56dp width) + title; current chapter highlighted by `accentSoft` background.
- Transcript: `Body` 17sp text, 1.5 line spacing, current sentence in `text` bold-ish with `accentSoft` highlight, others `textSecondary`, tap seeks.
- Chat: bubbles. User: `accent` bg, `onAccent` text, radius 18 (bottom-right 6). Assistant: `surfaceHigh` bg, `text`, radius 18 (bottom-left 6), timestamps `[12:34]` rendered as `accent` pill links. Input bar: `SearchField`-style 48dp field + 44dp circular send button, pinned above the IME with insets. A typing indicator of three pulsing dots while streaming.

### 6.4 Full player (bottom sheet, 28dp top radius, covers full height)
- Background: vertical gradient from artwork dominant color (darkened to ~35% lightness) to `bg`. Cross-fade when the episode changes.
- Top row: `CHEVRON_DOWN` `IconButton` (collapse), `Overline` "NOW PLAYING" centered, `MORE` `IconButton`.
- Artwork: square, width = screen width − 64dp, radius 24dp, soft shadow; scales to 0.88 when paused (spring-like `OvershootInterpolator(1.2)`, 250ms).
- Title `Title` 2 lines centered/left (pick left-aligned with `SHARE`/`STAR` on the right), podcast `Body` `textSecondary`, current chapter `Caption` in `accent`.
- Seek bar: custom, 4dp track (`outline` bg, `accent` progress, buffered at `textTertiary` 40%), 16dp thumb circle that grows to 20dp while dragging, chapter ticks as 2dp gaps, times under it in `Caption` with tabular figures, 24dp vertical touch padding.
- Controls row (center-aligned, 24dp apart): `SKIP_BACK_15` 32dp, `PREV` 28dp optional, **play/pause** 72dp filled `accent` circle with `onAccent` 32dp icon (animate morph or crossfade 150ms), `NEXT` 28dp optional, `SKIP_FWD_30` 32dp.
- Secondary row: speed pill (`Chip` "1.0×" opens sheet), sleep timer `IconButton`, **Snip** as a wide `SecondaryButton` with `SNIP` icon and `accentSoft` bg ("Snip" + haptic + a brief ring pulse animation), queue `IconButton`.
- Snip feedback: success → `ToastHost` "Snip saved" with "Edit" action.

### 6.5 Library
- Large title; `SegmentedControl`: **Subscriptions · Queue · Snips · Downloads**.
- Subscriptions: 3-column artwork grid (square, radius 14, title `Caption` 2 lines below, unplayed count badge top-right 18dp `accent` circle). 12dp gaps, 20dp gutters.
- Queue: reorder handle (drag via long-press, with the row lifting 4dp), swipe-style remove via trailing `TRASH` `IconButton`.
- Snips: `SnipCard` list with a search field at the top.
- Empty states for each segment with relevant copy and action.

### 6.6 News (RSS reader)
- Large title "News"; header actions: `FOLDER`/filter `IconButton` (opens feed picker sheet), `PLUS` `IconButton` (add feed sheet), `REFRESH` while loading shows a spinner.
- Top horizontal `Chip` row: All, Unread, Starred, then folders/feeds with a 24dp round source icon (favicon, fallback to colored initial circle: deterministic color from the title hash).
- `ArticleRow` list, grouped by day with sticky-looking `Overline` day headers ("TODAY", "YESTERDAY", "MON 12 OCT").
- Long-press menu → `BottomSheet`: Mark read/unread, Star, Share, Open original, Summarize with AI.
- **Reader:** `ScreenHeader` with back, source name (`Caption`), `STAR` and `SPARKLE` (AI menu) buttons. Below: title `Display` 26sp 3-4 lines, byline `Caption`, optional lead image (full width, radius 16, 16:9), then article body in the `WebView` (JS off, transparent background) with this CSS injected so it matches the app:
  - `body{font:17px/1.7 sans-serif;color:<text>;background:transparent;margin:0;padding:0 20px 120px}`
  - `h1,h2,h3{line-height:1.3;margin:1.6em 0 .6em}`, `a{color:<accent>;text-decoration:none}`, `img,video{max-width:100%;height:auto;border-radius:12px}`, `blockquote{border-left:3px solid <accent>;margin:1em 0;padding:.2em 1em;color:<textSecondary>}`, `pre,code{background:<surfaceHigh>;border-radius:8px;padding:2px 6px;overflow:auto;font-size:14px}`, `figure{margin:1.2em 0}`, `figcaption{font-size:13px;color:<textTertiary>}`.
  - Inject colors from `Theme.c` as `#RRGGBB` strings, regenerate on theme change.
- AI summary appears as a collapsible card **above** the body: `surface` card radius 16, `SPARKLE` + "AI summary" `Overline`, streaming text `Body`, with `Copy` and `Close` actions.

### 6.7 Settings
Grouped `SettingsRow` cards: **AI** (Provider, API key, Model, Test connection, Usage), **Playback** (default speed, skip intervals), **Appearance** (Theme: System/Light/Dark as a `SegmentedControl`, accent color swatches), **Feeds** (refresh interval, OPML import/export), **Data** (clear cache, export snips), **About**.
- **AI provider screen:** provider chosen via 2-column selectable cards (icon, name, `Caption`), selected card = `accentSoft` bg + 1.5dp `accent` stroke. API key field: `SearchField`-style with `KEY` leading icon, masked, `EYE`/`EYE_OFF` trailing toggle. A `PrimaryButton` "Test connection" with inline result row: `success`/`error` colored dot + message. Privacy note as a `Caption` `textTertiary` with a `LINK`-style icon: "Your key is encrypted and stays on this device."

---

## 7. Content and microcopy rules

- Sentence case everywhere, short and human ("Nothing here yet", not "No data available").
- Empty state formula: icon + what this place is for + the one action to fill it.
- Error formula: what happened + what to do. No stack traces, no raw HTTP codes unless in an expandable "Details".
- Durations: "42 min", "1 h 12 min". Dates: relative up to 7 days ("3h", "Yesterday", "Mon"), then "12 Oct".
- Numbers like unread counts cap at "99+".

---

## 8. Motion spec

| Interaction | Duration | Interpolator |
|---|---|---|
| Press feedback | 80ms down / 140ms up | linear / decelerate |
| Fade in (images, content) | 200ms | decelerate |
| Screen push/pop | 220ms | `PathInterpolator(0.4f, 0f, 0.2f, 1f)` |
| Sheet open | 280ms | `PathInterpolator(0.05f, 0.7f, 0.1f, 1f)` |
| Sheet dismiss | 200ms | accelerate-decelerate |
| Tab pill | 200ms | fast-out-slow-in |
| Artwork pause/play scale | 250ms | `OvershootInterpolator(1.2f)` |
| Skeleton shimmer | 900ms loop | linear reverse |

First-appearance list items fade + translate 12dp up, staggered 20ms (max 8 items, once per screen load). No animation longer than 350ms. Honor reduced animations.

---

## 9. Per-screen checklist (tick before moving on)

- [ ] No raw colors/dp/sp literals in the screen file; only tokens and components
- [ ] 20dp gutters, 8dp grid, correct section spacing
- [ ] Insets handled; nothing under status bar or gesture bar; last item clears nav + mini player
- [ ] Typography only from the scale; titles ellipsize correctly; no text clipped at 1.3× font scale
- [ ] All tappables ≥ 44dp with ripple and `contentDescription`
- [ ] Loading skeleton, empty state, error state all implemented and reachable
- [ ] Works in dark **and** light theme (toggle and verify visually)
- [ ] Works on a small phone (360×640dp) and in landscape without overlap
- [ ] No stock `Button`, `AlertDialog`, `Toast`, `ActionBar`, `ListView` dividers/selectors
- [ ] Images fade in, correct crop, rounded, placeholder shown
- [ ] Animations match section 8; nothing janky when scrolling (no layout in `getView` beyond holder binding)
- [ ] Performance: rows reuse `convertView`; images decode off main thread; no overdraw from stacked opaque backgrounds (set window background once; avoid redundant `setBackground` on parents)

---

## 10. Definition of done

The app passes this test: take screenshots of Discover, Podcast detail, Player, Library, News, Reader, Settings in dark and light. A stranger should not be able to tell it wasn't designed by a professional: consistent spacing, one accent, crisp type hierarchy, real icons, smooth states, nothing default-looking.

---

## 11. UI audit (fill in during step 0)

_(List findings here: hard-coded values, default widgets, inconsistent components, missing states.)_

## 12. Progress log

_(After each step/screen: date, what changed, checklist results, anything that couldn't be done and why.)_
