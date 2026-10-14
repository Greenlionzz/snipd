# Notes & Engineering Log: UI Redesign & Discover System76

This document outlines all architecture, design systems, UI components, bug fixes, and feature additions implemented across the codebase inside **CodeAssist**.

---

## 1. Design System & Token Architecture

### 1.1 Palette & Color Tokens (`ui/theme/Theme.kt`)
- Created a single source of truth `Palette` class supporting OLED Dark and Crisp Light modes.
- Core tokens:
  - `bg`: Deep OLED black (`#0E0F13`)
  - `surface`: Elevated cards (`#16181F`)
  - `surfaceHigh`: Highlighted cards and input controls (`#1E212A` / `#20242D`)
  - `outline`: 1dp subtle borders with low alpha (`0x1FFFFFFF`)
  - `accent`: Electric Indigo (`#7C8CFF` / `#4F5FE0`)
  - `accentSoft`: Low alpha accent tint (`#297C8CFF`)
  - `text`, `textSecondary`, `textTertiary`: Hierarchical readable text (`#F2F3F7`, `#A5A9B8`, `#6F7385`)
  - `success`, `warning`, `error`: Semantic status colors (`#4ADE80`, `#FBBF24`, `#F87171`)
- Added `Theme.tint(base, alpha)` for blending artwork colors into background surfaces.
- Updated `AppTheme.kt` as a backward-compatible facade so existing calls continue to compile.

### 1.2 Canvas Vector Icon Engine (`ui/theme/IconView.kt`)
- Completely eliminated hardcoded emojis and letter fallbacks in favor of programmatic canvas-drawn vector icons (`Paint`, `Path`, `Canvas`).
- Supported icon set:
  - `DISCOVER`, `LIBRARY`, `NEWS`, `SETTINGS`
  - `PLAY`, `PAUSE`, `SKIP_FORWARD`, `SKIP_BACK`, `FORWARD_30`, `BACK_15`
  - `CHEVRON_LEFT`, `CHEVRON_DOWN`, `CHEVRON_RIGHT`
  - `SEARCH`, `ADD`, `CLOSE`, `DELETE`, `SHARE`, `REFRESH`
  - `STAR`, `STAR_FILLED`, `RSS`, `AI_SPARK`, `SNIP`, `CHECK`
  - `SORT`, `FILTER`, `SPEED`, `SLEEP`, `MORE_VERT`
  - `PODCAST`, `EPISODE`, `HEADPHONE`, `HEART`, `WAVEFORM`, `PLAYLIST`, `DOWNLOAD`, `ERROR`, `EMPTY_INBOX`
- Dynamic properties `icon` and `tint` allow real-time color and shape mutations.

### 1.3 Design DSL Components (`ui/dsl/ViewDsl.kt`)
- **Micro-Interactions**: Added `onTapFeedback` providing iOS/Spotify-style press feedback (scales down to `0.96f` on touch down and bounces back with `OvershootInterpolator` on release).
- **Typography Scale**: `displayText` (28sp bold), `titleText` (20sp bold), `headlineText` (17sp bold), `bodyText` (15sp regular), `labelText` (13sp-15sp medium), `captionText` (11sp-13sp), `overlineText` (11sp uppercase).
- **Buttons**:
  - `primaryButton`: Accent filled, rounded ripple pill button.
  - `secondaryButton`: Tonal surface button with subtle 1dp outline.
  - `textButton`: Borderless clickable text.
  - `iconButton`: Centered `IconView` with circular ripple bound.
  - `chipButton`: Pill category filter with selected/unselected states.
- **State Components**:
  - `SkeletonView` & `skeletonRows`: Shimmering rounded boxes with animated alpha (0.45 ↔ 1.0) for zero-layout-shift loading.
  - `emptyState`: 72dp accent badge, title, subtitle, and action button.
  - `errorState`: Error badge, descriptive message, and retry button.
  - `ToastHost`: Floating bottom snackbar pill with slide-and-fade animation replacing system Toasts.
  - `showBottomSheet`: Custom slide-up bottom modal with drag handle and backdrop scrim.
  - `segmentedControl`: Tab switchers with smooth active background transitions.

---

## 2. Shell & Inset Architecture (`MainActivity.kt`, `BottomBar.kt`, `MiniPlayerView.kt`)

### 2.1 Window Edge-to-Edge & Navigation Insets
- Enabled transparent status and navigation bars via window flags (`setDecorFitsSystemWindows(false)`).
- Implemented `setOnApplyWindowInsetsListener` at the root layout:
  - Top insets applied to headers.
  - Bottom insets applied to the bottom navigation bar.
  - **Fixed dead space**: `ScreenHost` bottom margin is strictly set to `dp(64) + navBottom`, ensuring content scrolls fully without leaving large empty gaps at the bottom.

### 2.2 Bottom Navigation Bar (`ui/nav/BottomBar.kt`)
- 64dp standard height with a top 1dp hairline border.
- 4 navigation tabs: Discover, Library, News, Settings.
- Active state displays an animated `accentSoft` pill with bouncy icon scale feedback (`OvershootInterpolator`) and colored overline labels.

### 2.3 Persistent Mini Player (`ui/player/MiniPlayerView.kt`)
- 64dp floating card above the bottom navigation bar.
- Thin hairline `ProgressBar` across the top.
- Left-aligned 44×44dp rounded artwork with fade-in image loader.
- Single-line title and artist text stack.
- Direct inline "Capture Snip" icon and circular accent Play/Pause toggle.
- Hides automatically when playback stops, and tap expands into the `FullPlayerScreen`.

---

## 3. Discover Screen Architecture (`ui/discover/DiscoverScreen.kt`)

Rebuilt according to the provided reference UI, tailored for mobile and tablet landscape:

1. **Top Search Bar**:
   - 50dp height rounded pill (`#20242D`) with vertical alignment and `includeFontPadding = false`.
   - Vector search icon (`#8E95A5`) + hint `"Search podcasts or add RSS URL"`.
   - Live search execution on IME search or submit.

2. **Horizontal Category Filters**:
   - Smooth `HorizontalScrollView` with chips: `"All categories ▾"`, `"True Crime"`, `"Comedy"`, `"Society & Culture"`, `"History"`, `"Fiction"`, `"Technology"`, `"News"`.
   - Selecting a category immediately queries the iTunes podcast directory and filters results.

3. **Horizontally Swiping Hero Spotlight Carousel**:
   - Paging `HorizontalScrollView` hosting prominent spotlight cards.
   - Tablet-responsive width calculations (up to 640dp).
   - Deep gradient background tints (`#4A1515`, `#1B3B2B`, `#1C2E4A`, `#3E204A`, etc.).
   - Large 92×92dp rounded artwork, overline badges (`SPONSORED`, `FEATURED`, `MUST LISTEN`), titles, and real-time subscription toggle buttons (`+` / `✓`).
   - Centered 7-dot pagination indicators with active cyan glow (`#38BDF8`).

4. **Trending Section**:
   - Headline title with a cyan `"SHOW ALL"` action.
   - Clean compact rows featuring 54×54dp artwork, bold title, secondary publisher, and real-time subscription button.

5. **Mini Hero Promo Card**:
   - Styled banner for featured sponsorships (*"The Perfect Scam"*).

6. **Horizontal Podcast Shelves (Enlarged Cards for Tablet Landscape)**:
   - Sections: *"You Might Like"*, *"British Podcast Awards Winners 2026"*, *"History in One Sitting"*, *"Made in Australia"*, *"Guest Curator"*, *"Staff Picks"*.
   - **Enlarged Card Size**: Increased from 112dp to **148dp** width and height.
   - Integrated floating circular `+` / `✓` button in the bottom-right corner of each artwork.
   - Tapping opens the podcast details; tapping the icon toggles subscription.

7. **Popular Section & Content Region**:
   - Popular podcasts with check indicators.
   - Bottom centered `"Worldwide"` region selector matching the design.

---

## 4. "SHOW ALL" Screen (`ui/discover/SeeAllListScreen.kt`)

- Tapping any `"SHOW ALL"` button pushes `SeeAllListScreen` onto the `ScreenHost` stack.
- Top bar contains a cyan back chevron button (`←`) and the section title.
- Scrollable list with 52×52dp rounded artwork, title, author, and subscription toggle buttons.
- Real-time subscribe/unsubscribe reactivity.

---

## 5. Real-Time Subscription Engine (`SubscriptionManager.kt`)

### 5.1 The Problem
Previously, tapping `+` added the podcast in the background, but the icon did not change to a green checkmark until the user navigated away and returned.

### 5.2 The Solution
1. **`SubscriptionManager` singleton**:
   - Holds an in-memory cache of subscribed podcast feed URLs and IDs.
   - Synchronizes with SQLite on app launch and database update events.
2. **Instant UI State Toggle**:
   - When the user taps `+`:
     - The icon immediately flips to `Icon.CHECK` in green (`#4ADE80`).
     - In shelf cards, the background badge changes to green-tinted `#E6152A1D`.
   - When the user taps `✓`:
     - The icon immediately flips back to `Icon.ADD` in grey (`#94A3B8`).
     - Background badge resets to `#CC121418`.
3. **`SubscriptionChangedEvent` Broadcasting**:
   - `SubscriptionManager.setSubscribedLocally(feedUrl, isSubscribed)` fires an instant event project-wide.
   - Registered icon views in `DiscoverScreen` and `SeeAllListScreen` update simultaneously across all views displaying that feed URL.
4. **Safe Asynchronous Fallback**:
   - Network fetch and SQLite inserts run asynchronously in `AppExecutors.executeIo`.
   - If an error occurs, the subscription state is rolled back and the icon reverts automatically.

### 5.3 Decoupling Preview from Subscription
- Previously, tapping any podcast card automatically called `PodcastRepository.subscribe()`, which forced the podcast into the user's followed library.
- **Fixed**: Added `PodcastRepository.fetchAndSavePodcast(podcast, subscribed = false)` which fetches metadata, show notes, and the episode list without marking the podcast as subscribed or following.
- Tapping a card now simply opens the podcast details page for browsing and playback. The podcast only gets added to following when the user explicitly taps the `Follow` / `+` button.

### 5.4 Screen State Retention & Discover In-Memory Cache (`DiscoverCache.kt`)
- **Problem**: Whenever the user switched bottom tabs or navigated back from a details or player screen, `DiscoverScreen` threw away its view hierarchy and re-fetched the entire network catalog, showing blank skeletons each time.
- **Solution**:
  - Implemented `ScreenHost` view caching: `ScreenHost` now retains instantiated `View` hierarchies for screens on the back stack and top-level navigation tabs. Switching back to the Discover tab re-attaches the existing view instantly with zero delay, preserved scroll offset, and no re-rendering.
  - Built `DiscoverCache.kt`: Stores hero spotlight podcasts, trending rows, shelf items (*"You Might Like"*, *"British Podcast Awards"*, etc.), and popular podcasts in memory.
  - The initial network fetch now runs **only once when the app starts**. When returning to the Discover screen, the catalog renders immediately from memory cache without showing loading spinners or refetching from the network.
  - Passed `initialPodcast` directly to `PodcastDetailScreen` so podcast titles, authors, and artwork render immediately on tap without waiting for network responses.

---

## 6. Other Screens Updated in the Redesign

- **`SettingsScreen.kt`**:
  - Encrypted BYOK key management for OpenAI, Anthropic, Gemini, Groq, Ollama.
  - Active provider status indicators and token consumption diagnostics.
  - Light/Dark theme toggle using `segmentedControl`.
- **`NewsScreen.kt` & `ArticleReaderScreen.kt`**:
  - Segmented filters (`All`, `Unread`, `Starred`), horizontal RSS feed chips.
  - Rich typography reader view with custom HTML styling for OLED dark mode.
  - `ArticleAiDialog` streaming summaries and takeaways via user API keys.
- **`PodcastDetailScreen.kt`**:
  - Completely redesigned to mirror the reference UI:
    - **Radiant Gradient Hero Banner**: Rich `#991B1B` to deep `#0F1115` background with back navigation arrow and share button.
    - **Centered Presentation**: 130×130dp elevated center artwork, category/author subtitle, title with chevron dropdown, star rating (`★★★★☆ 4.4 (97.3K)`), and interactive rounded `Follow` / `Following` pill button synced with `SubscriptionManager`.
    - **Metadata Information Card**: Surface card displaying dynamic Host/Author, clickable official website URL, calculated publishing frequency (e.g. `Released weekly`, `Released daily`, etc.) based on historical episode gaps, and projected next episode date.
    - **Segmented Section Tabs**: Tab switcher between `Episodes`, `Bookmarks`, and `You might like`.
    - **Episode Search Bar**: Integrated filter box with live query searching through episode titles and descriptions, accompanied by a more options action button.
    - **Minimalist Episode List**: Overline red/coral date and episode numbers (e.g. `E427 • OCTOBER 12`), 2-line bold titles, formatted duration, circular red outlined Play button, and 1dp dividers. Tapping any episode or play button starts playback immediately.

- **`EpisodeAiScreen.kt`**:
  - 4 AI analysis modes (Summary, Deep Dive, Discussion, Key Quotes) streaming via `AiService.stream`.
- **`LibraryScreen.kt` & `SnipsLibraryScreen.kt`**:
  - Clean subscription management, timestamped audio snips with user notes, and text export.
- **`FullPlayerScreen.kt`**:
  - Large rounded artwork with soft shadow, progress scrubbing, speed selector (0.8x - 2.0x), and audio snip capture dialog.

---

## 7. Build Verification
- All code compiles natively via CodeAssist's internal build engine:
  - Kotlin compilation: OK
  - Java compilation: OK
  - D8 Dexing: OK
  - APK Packaging & Signing: OK
  - Status: **BUILD SUCCESSFUL**
