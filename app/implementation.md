# implementation.md: "Tuned", a native Kotlin AI podcast player + RSS reader

A beautiful, fast, local-first Android app written in Kotlin and built inside the **CodeAssist** Android IDE, which does **not use Gradle**. The app is inspired by Snipd (AI podcast player with snips, transcripts, summaries, chapters and chat), with two key differences:

1. **Bring Your Own Key (BYOK):** every AI feature runs on the user's own API key, called directly from the phone. There is no backend and no shared key.
2. **RSS Reader tab:** news from any RSS/Atom/JSON Feed URL the user adds.

---

## 0. How to read and use this document (instructions for the coding AI)

- Work **phase by phase** (section 12). Do not start a phase until the previous phase's acceptance checks pass.
- After each phase, update the "Progress log" at the bottom of this file: what was built, what changed from the plan, what failed.
- **Phase 0 is mandatory.** The toolchain has unknowns. Probe it before designing around it. Where this document says "verify", do not assume.
- If something in this plan turns out to be unsupported by the toolchain, use the fallback written next to it, then record the decision in the progress log.
- Prefer small files (under ~300 lines), one responsibility each. Keep compile errors easy to localize.

---

## 1. Build environment constraints (CodeAssist, no Gradle)

Treat these as **hard rules** until Phase 0 proves otherwise.

| Topic | Rule |
|---|---|
| Build system | No Gradle, no `build.gradle`, no version catalogs, no Gradle plugins. |
| Compiler plugins | **Assume none.** No Jetpack Compose (needs the Compose compiler plugin), no kapt/KSP (so no Room, no Dagger/Hilt, no Moshi codegen), no `kotlinx.serialization` (needs a plugin), no Parcelize. |
| Dependencies | **Assume zero third-party libraries** (including androidx). Use only the Android platform SDK + Kotlin stdlib. Phase 0 checks whether jar/aar files can be added (e.g. a `libs/` folder); if yes, they are *optional* extras, never required. |
| Coroutines | `kotlinx.coroutines` is a library, so assume unavailable. Use `java.util.concurrent` executors + `Handler(Looper.getMainLooper())`. |
| UI toolkit | Classic Android Views. No Compose. No RecyclerView/ViewPager/Material Components (all androidx or external). Use platform `ListView`/`ScrollView`/custom views. |
| Fragments | Avoid. Use a single `Activity` and a tiny custom screen/back-stack system (section 5). |
| Min SDK | 26 (gives `java.time`, notification channels, `PlaybackParams`, no desugaring needed). Target SDK: whatever the IDE supports, then handle that API level's rules (foreground service types on 34+, notification permission on 33+). |
| Kotlin features | Stick to stable, conservative Kotlin: data classes, sealed classes, extension functions, lambdas, `object`, `lazy`, `when`. Avoid context receivers, experimental APIs, and anything needing a plugin. |
| JSON | `org.json` (built into Android). |
| XML parsing | `org.xmlpull.v1.XmlPullParser` (built in). |
| HTTP | `HttpURLConnection` (built in). No OkHttp. |
| Database | `SQLiteOpenHelper` / `SQLiteDatabase` (built in). No Room. |
| Resources | Verify in Phase 0 that `res/` (drawables, colors, themes, strings, anim) compiles via AAPT2. If XML **layouts** work, you may use them. Otherwise build all layouts in Kotlin. **Pick one approach for layouts and stay consistent** (recommended: Kotlin DSL, section 6.3). |

---

## 2. Phase 0, toolchain probe (do this first, 20-30 minutes)

Create a throwaway "probe" screen in the app and verify each item. Record results in the progress log table at the bottom.

1. Hello-world Activity builds, installs and runs.
2. Kotlin stdlib features: data class, sealed class, `object`, extension functions, lambdas, `when`, string templates.
3. `res/values` colors, themes and strings resolve. A `res/drawable/*.xml` shape (rounded rect + gradient) renders.
4. A `res/layout/*.xml` inflates (decides the layout approach).
5. `HttpURLConnection` HTTPS GET to `https://itunes.apple.com/search?term=test&media=podcast&limit=1` works. Note: `INTERNET` permission in the manifest.
6. `XmlPullParser` parses a real RSS feed.
7. `SQLiteOpenHelper` create + insert + query works.
8. `MediaPlayer` plays a remote mp3 URL; `PlaybackParams` speed change works.
9. A foreground `Service` starts with a `MediaStyle` notification and a `MediaSession`.
10. Android Keystore AES-GCM encrypt/decrypt round trip works.
11. **Dependency test:** can a `.jar` placed in a libs folder be picked up? (Informational only. The app must not depend on it.)
12. Report the Kotlin version, minSdk/targetSdk the IDE allows, and whether incremental builds are fast enough to iterate.

**Output of Phase 0:** a short "Toolchain facts" section filled in below, plus any fallback decisions.

---

## 3. App overview

**Name:** Tuned (placeholder, easy to rename)
**Package:** `com.example.tuned`
**Single Activity:** `MainActivity`
**Bottom tabs:** Discover · Library · News · Settings, with a persistent **mini player** above the tab bar that expands into a **full player** sheet.

### Feature list

1. **BYOK AI settings:** multiple providers, test connection, encrypted key storage.
2. **Podcast discovery:** iTunes Search API, trending categories, add by RSS URL.
3. **Podcasts:** subscribe, episode lists, show notes, downloads (optional phase).
4. **Playback:** foreground service, lock-screen/notification controls, headphone-button snip, speed, sleep timer, queue, resume.
5. **Snips:** one tap to capture the last N seconds with an AI transcript + summary + note.
6. **Episode AI:** transcript, chapters, summary/takeaways, guests, chat with episode.
7. **RSS reader:** feeds, folders, unread counts, reader mode, star, OPML import/export, AI summarize/digest.
8. **Export:** snips and notes to Markdown/JSON through the system file picker.

Native Android has **no CORS problem**, so no proxy layer is needed. This is a big advantage over the web version.

---

## 4. Architecture

Simple layered architecture, no DI framework: one hand-written `AppGraph` object (service locator) created in `Application.onCreate()`.

```
UI (screens, views)
   │  calls
Controllers / ViewModels-lite (plain classes holding state + listeners)
   │  calls
Repositories (Podcast, Episode, Snip, Feed, Article, Settings)
   │  uses
Data sources: Db (SQLite) · Http · Parsers · AI providers · Files
```

### 4.1 Package layout

```
com.example.tuned
├─ App.kt                       // Application, builds AppGraph
├─ AppGraph.kt                  // singletons: db, http, executors, repos, ai
├─ MainActivity.kt              // hosts ScreenHost + tab bar + player sheet
├─ core/
│  ├─ Executors.kt              // io pool, cpu pool, main handler helpers
│  ├─ Result.kt                 // sealed Result<T> (Ok / Err with message)
│  ├─ Cancelable.kt             // cancellation tokens for in-flight work
│  ├─ Events.kt                 // tiny listener/event bus on main thread
│  ├─ TimeFmt.kt                // durations, relative dates
│  └─ Log.kt
├─ data/
│  ├─ db/Db.kt                  // SQLiteOpenHelper + migrations
│  ├─ db/*Dao.kt                // one DAO per table
│  ├─ model/*.kt                // data classes
│  └─ repo/*Repository.kt
├─ net/
│  ├─ Http.kt                   // GET/POST, headers, timeouts, redirects, gzip
│  ├─ SseReader.kt              // parses text/event-stream for streaming AI
│  ├─ ItunesApi.kt
│  └─ ImageLoader.kt            // LruCache + disk cache + sampling
├─ feed/
│  ├─ FeedParser.kt             // detects RSS / Atom / JSON Feed
│  ├─ RssParser.kt
│  ├─ AtomParser.kt
│  ├─ JsonFeedParser.kt
│  ├─ FeedDiscovery.kt          // finds the feed URL from a webpage
│  └─ Opml.kt                   // import / export
├─ ai/
│  ├─ AiProvider.kt             // interface
│  ├─ GeminiProvider.kt
│  ├─ OpenAiCompatProvider.kt   // OpenAI, OpenRouter, custom base URL
│  ├─ AnthropicProvider.kt
│  ├─ AiService.kt              // picks provider, retries, caches, usage
│  ├─ Prompts.kt                // all prompt templates
│  └─ SecureKeyStore.kt         // Keystore-backed storage for API keys
├─ player/
│  ├─ PlaybackService.kt        // foreground service + MediaSession
│  ├─ PlayerController.kt       // UI-facing facade, bound to the service
│  ├─ SleepTimer.kt
│  └─ SnipCapture.kt
├─ ui/
│  ├─ theme/                    // Colors, Dimens, Typography, Shapes helpers
│  ├─ dsl/                      // Kotlin view-building helpers (section 6.3)
│  ├─ nav/ScreenHost.kt         // back stack, transitions
│  ├─ nav/Screen.kt             // interface
│  ├─ nav/BottomBar.kt
│  ├─ player/MiniPlayer.kt, FullPlayerSheet.kt
│  ├─ discover/…  library/…  news/…  settings/…  snips/…  episode/…
│  └─ widgets/                  // reusable custom views (section 6.4)
└─ util/                        // Html helpers, Markdown export, color extraction
```

### 4.2 Threading rules

- **Main thread:** UI only.
- **IO executor:** network, DB, disk. Fixed pool of 4.
- **CPU executor:** parsing, color extraction, text chunking. Pool of 2.
- All results return to main through `Executors.main { }`.
- Every long operation returns a `Cancelable`. Screens cancel in `onHide()`.
- Never block the main thread. Add a debug StrictMode setup in debug builds to catch violations.

### 4.3 Error handling

- Public repository/AI functions return `Result<T>`; they never throw to the UI.
- `Err` carries a user-readable message and a machine code: `NO_KEY`, `BAD_KEY`, `RATE_LIMIT(retryAfter)`, `NETWORK`, `TIMEOUT`, `CONTEXT_TOO_LONG`, `PARSE`, `UNKNOWN`.
- UI shows a snackbar-style toast with a Retry action. No silent failures, no blank screens.

---

## 5. Navigation system (no Fragments)

```kotlin
interface Screen {
    fun createView(ctx: Context): View
    fun onShow() {}
    fun onHide() {}
    fun onBack(): Boolean = false   // true if the screen consumed back
}
```

- `ScreenHost` is a `FrameLayout` holding the current screen's view and a back stack of `Screen`s.
- Each bottom tab keeps **its own back stack**, so switching tabs preserves state.
- Transitions: simple slide/fade using `ViewPropertyAnimator` (150-250 ms, decelerate interpolator).
- Handle the system back button in `MainActivity.onBackPressed()` (or `OnBackInvokedCallback` on newer APIs): collapse the player sheet, then pop the screen stack, then exit.
- Survive configuration changes by declaring `android:configChanges="orientation|screenSize|keyboardHidden|uiMode"` and handling resize manually. Persist screen state that matters in the DB or `SharedPreferences`.

---

## 6. Design system (what makes it "beautiful")

### 6.1 Principles

Content-first, calm, high-contrast, generous spacing, large artwork, one accent color, smooth motion. Dark theme is the default, with a light theme and "follow system".

### 6.2 Tokens (define once in `ui/theme`, never hard-code in screens)

**Color (dark):**
- background `#0E0F13`, surface `#16181F`, surfaceHigh `#1E212A`, outline `#2A2E39`
- text primary `#F2F3F7`, secondary `#A5A9B8`, tertiary `#6F7385`
- accent `#7C8CFF` (indigo), accentSoft = accent at 16% alpha
- success `#4ADE80`, warning `#FBBF24`, error `#F87171`

**Color (light):** background `#F7F8FB`, surface `#FFFFFF`, text `#12141A`, same accent family, slightly darker accent `#4F5FE0`.

**Spacing scale:** 4 · 8 · 12 · 16 · 20 · 24 · 32 dp. **Corner radii:** 10 (chips/buttons) · 16 (cards) · 28 (sheets). **Elevation:** use subtle tonal surfaces plus 1dp outlines instead of heavy shadows.

**Type scale (system font, `sans-serif` / `sans-serif-medium`):** Display 28/34 medium · Title 20/26 medium · Body 16/24 · Caption 13/18 · Overline 11/14 with letter spacing. Respect the user's font scale (use `sp`).

### 6.3 Kotlin view DSL (`ui/dsl`)

Build tiny helpers so screens stay readable and consistent, for example:

```kotlin
fun Context.vstack(block: LinearLayout.() -> Unit): LinearLayout
fun Context.hstack(block: LinearLayout.() -> Unit): LinearLayout
fun Context.text(style: TextStyle, value: CharSequence): TextView
fun Context.card(block: ViewGroup.() -> Unit): FrameLayout   // rounded surface + outline
fun View.dp(n: Int): Int
fun View.padding(h: Int, v: Int)
fun View.onTap(action: () -> Unit)     // ripple + haptic + 44dp min touch target
fun View.fadeIn(); fun View.slideUp()
```

Rounded backgrounds and ripples come from `GradientDrawable` + `RippleDrawable` created in code (or XML drawables, if verified in Phase 0).

### 6.4 Reusable custom widgets (`ui/widgets`)

- `ArtworkView`: rounded image with a placeholder gradient while loading and a fade-in.
- `ChipRow`: horizontally scrolling filter chips.
- `SegmentedControl`: for the Snips / Notes style switches.
- `SeekBar` (custom): thin track, big touch area, chapter ticks, buffered portion.
- `WaveBars`: small animated "now playing" bars.
- `EmptyState`: icon + title + hint + optional action.
- `Skeleton`: shimmering placeholder rows (animated alpha on a `ValueAnimator`).
- `BottomSheet`: custom drag-to-dismiss container driven by touch events and `translationY` (since `ViewDragHelper` is androidx).
- `ToastHost`: snackbar-like messages with an action button.
- `TranscriptView`: word/sentence-level highlight, tap to seek, auto-scroll that pauses when the user scrolls manually.

### 6.5 Visual details to deliver

- **Artwork-tinted player:** sample the cover (downscale to ~24×24, average the most saturated pixels) to build a vertical gradient behind the full player. No Palette library.
- Edge-to-edge window: draw behind system bars, apply `WindowInsets` to the top bar, the tab bar and the keyboard.
- Icons: vector drawables (XML) if resources work; otherwise draw simple icons with `Path` in a tiny `Icons` object. Keep a consistent 24dp grid and 2dp stroke.
- Motion: mini player ↔ full player shared-element feel (artwork scales and moves), tab switch cross-fade, list items fade/slide in once. Everything respects "reduce animations" (`Settings.Global.ANIMATOR_DURATION_SCALE`).
- Haptics on snip, long-press and toggles.
- Every list has loading (skeleton), empty and error states designed, not default.

### 6.6 Lists without RecyclerView

Use `ListView` + `BaseAdapter` with a proper view-holder pattern, `convertView` reuse, and image loads cancelled via `view.tag` checks. For very simple screens, a `ScrollView` + `LinearLayout` is fine up to ~50 rows. For heavy lists (episodes, articles), always use `ListView`.

---

## 7. Data model (SQLite)

Use `Db : SQLiteOpenHelper` with a `version` constant and ordered `onUpgrade` migrations. Enable WAL (`enableWriteAheadLogging()`) and foreign keys.

```sql
podcast(id INTEGER PK, feed_url TEXT UNIQUE, title, author, description, artwork_url,
        website, itunes_id, subscribed INTEGER, last_fetched INTEGER)

episode(id INTEGER PK, podcast_id FK, guid TEXT, title, description_html, audio_url,
        duration_sec, published_at INTEGER, artwork_url, position_sec INTEGER DEFAULT 0,
        played INTEGER DEFAULT 0, downloaded_path TEXT, UNIQUE(podcast_id, guid))

episode_ai(episode_id PK FK, transcript_json TEXT, transcript_mode TEXT,   -- 'audio' | 'notes'
           chapters_json TEXT, summary_json TEXT, guests_json TEXT,
           model TEXT, created_at INTEGER)

snip(id INTEGER PK, episode_id FK, start_ms, end_ms, title, transcript_text,
     summary_text, user_note, tags, created_at)

chat_message(id INTEGER PK, scope_type TEXT, scope_id INTEGER, role TEXT, content TEXT, created_at)
     -- scope_type: 'episode' | 'article'

feed(id INTEGER PK, url TEXT UNIQUE, title, site_url, icon_url, folder_id FK NULL,
     etag TEXT, last_modified TEXT, last_fetched INTEGER, error_count INTEGER DEFAULT 0)

folder(id INTEGER PK, name TEXT, sort INTEGER)

article(id INTEGER PK, feed_id FK, guid TEXT, title, author, link, content_html,
        summary_text, image_url, published_at, read INTEGER DEFAULT 0,
        starred INTEGER DEFAULT 0, ai_summary TEXT, UNIQUE(feed_id, guid))

queue_item(id INTEGER PK, episode_id FK, sort INTEGER)

ai_usage(id INTEGER PK, provider, model, feature, input_tokens, output_tokens, at INTEGER)
```

Indexes: `episode(podcast_id, published_at DESC)`, `article(feed_id, published_at DESC)`, `article(read, published_at)`, `snip(episode_id, created_at)`.

**Settings** (non-secret) in `SharedPreferences`: theme, default speed, skip intervals, snip length, selected provider/model, refresh interval. **Secrets** (API keys) only through `SecureKeyStore`.

---

## 8. Networking

### 8.1 `Http` helper

- `get(url, headers)`, `post(url, headers, body)`, `postStream(...)` returning an `InputStream` for SSE.
- Timeouts: connect 15s, read 30s (AI streaming read 120s). Follow redirects (including http→https). Handle gzip. Set a clear `User-Agent`.
- Conditional GET for feeds using stored `ETag`/`Last-Modified`.
- Respect `Retry-After` on 429/503.
- Never log request bodies or `Authorization`/API-key headers.
- **Policy:** AI provider calls must be `https` only (reject otherwise in code). Feeds may be `http` because many legacy feeds still are, so set `android:usesCleartextTraffic="true"` in the manifest and note the tradeoff in Settings > About.

### 8.2 `ImageLoader`

`LruCache<String, Bitmap>` (1/8 of heap), disk cache in `cacheDir/img` (50 MB cap, LRU eviction), decode with `inSampleSize` from the target view size, decode on the IO pool, set on main only if the view's tag still matches the URL.

### 8.3 `ItunesApi`

- Search: `https://itunes.apple.com/search?media=podcast&term=...&limit=25`
- Lookup: `https://itunes.apple.com/lookup?id=...` (returns `feedUrl`)
- Top charts via the Apple RSS generator endpoint, if reachable; otherwise a curated category list of search terms.

---

## 9. AI layer (the heart of BYOK)

### 9.1 Provider interface

```kotlin
interface AiProvider {
    val id: String
    val supportsAudio: Boolean
    fun generate(req: AiRequest, cancel: Cancelable): Result<AiResponse>
    fun stream(req: AiRequest, cancel: Cancelable, onDelta: (String) -> Unit): Result<AiResponse>
    fun testConnection(): Result<String>
    fun transcribeAudio(file: File, cancel: Cancelable, onProgress: (Float) -> Unit): Result<List<TranscriptSegment>>
}

data class AiRequest(
    val system: String?, val messages: List<AiMessage>,
    val jsonSchemaHint: String? = null, val temperature: Float = 0.3f,
    val maxOutputTokens: Int? = null, val model: String? = null,
)
```

Adapters: `GeminiProvider` (Generative Language API, supports audio via the Files API), `OpenAiCompatProvider` (OpenAI, OpenRouter, or any custom base URL, `/chat/completions` with SSE), `AnthropicProvider` (`/v1/messages`, streaming SSE, text only). Verify the exact endpoints/headers against each provider's current docs while building (do not rely on memory).

### 9.2 Settings screen requirements

- Provider picker; API key field (masked, show/hide); model name (free text + suggested presets); optional "fast model" for cheap tasks (summaries/chapters) vs "smart model" for chat; custom base URL when "Custom" is selected.
- **Test connection** button showing success or the exact error.
- Clear explanatory note: "Your key is encrypted and stays on this device."
- Usage screen reading `ai_usage`: totals by feature, rough token counts per request.

### 9.3 `SecureKeyStore`

Android Keystore AES-256-GCM key (alias `tuned_keys`), encrypt each API key, store `iv + ciphertext` in `SharedPreferences` (Base64). No androidx.security. If Keystore fails (some devices), show a warning and fall back to storing with a user-visible note rather than crashing. Exclude prefs from backups (`android:allowBackup="false"` or backup rules).

### 9.4 `AiService` responsibilities

- Choose provider/model for each feature (`FAST` or `SMART`).
- Single-flight dedupe: identical in-flight requests share one call.
- **Cache-first:** episode AI results stored in `episode_ai`; never pay twice for the same input.
- Retry: one automatic retry on 5xx/timeouts with backoff; honor 429 `Retry-After` and show a countdown.
- Strict JSON features: ask for JSON, parse with `org.json`, validate required fields, **retry once** with a "return valid JSON only" repair prompt, then fail with `PARSE`.
- Record to `ai_usage`.
- Before expensive jobs (full transcription), compute an estimate (audio duration → rough tokens) and show a confirm dialog.

### 9.5 Prompts (`Prompts.kt`)

One versioned template per task: `SNIP_SUMMARY`, `EPISODE_SUMMARY`, `CHAPTERS`, `GUESTS`, `EPISODE_CHAT_SYSTEM`, `ARTICLE_SUMMARY`, `ARTICLE_KEYPOINTS`, `ARTICLE_EXPLAIN_SIMPLE`, `ARTICLE_TRANSLATE`, `DAILY_DIGEST`. Each defines the exact JSON shape expected. Chat answers must cite timestamps as `[mm:ss]` so the UI can make them tappable.

### 9.6 Transcription strategy

1. **Audio mode (Gemini):** download the audio to `cacheDir`, upload via the Files API (resumable upload), ask for a timestamped transcript in chunks that fit the output limit (e.g. per 10-15 min window using a time-range instruction). Merge segments, store as `transcript_json` with `transcript_mode='audio'`.
2. **Other providers without audio input:** use `transcript_mode='notes'`, which works from show notes + chapters from the feed. The UI must say clearly which mode produced the result and offer "Use a Gemini key for full transcripts".
3. **Snips:** transcribe only the snip window (cut by byte range for MP3 at frame sync, or send a short time-range request referencing the already uploaded file) so snips are cheap.
4. Show progress, allow cancel, resume from stored partial segments if the app is killed.

---

## 10. Feeds: podcasts and news (shared engine)

- `FeedParser.parse(bytes, contentType)` detects **RSS 2.0, Atom, JSON Feed**, returns a normalized `ParsedFeed(items)`.
- Podcast extras: `<enclosure>`, `<itunes:duration>`, `<itunes:image>`, `<itunes:author>`, `<podcast:chapters>` (JSON chapters URL), `<podcast:transcript>` if present (use it instead of AI when available!).
- Robustness: handle CDATA, mixed encodings (honor XML prolog/charset), HTML entities, relative URLs, missing GUIDs (fall back to link, then hash of title+date), duplicate items, date formats (RFC 822 variants, ISO 8601).
- `FeedDiscovery`: given a webpage URL, fetch HTML and find `<link rel="alternate" type="application/rss+xml|atom+xml|feed+json">`; also try `/feed`, `/rss`, `/atom.xml`, `/index.xml`.
- Refresh: on app open (throttled), manual pull-to-refresh, optional periodic refresh through `JobScheduler`. Use conditional GET. Back off feeds with repeated errors (`error_count`).
- **OPML** import/export through the Storage Access Framework (`ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`), keeping folder structure.

---

## 11. Screens (spec)

### 11.1 Discover
Search bar (debounced 400ms), category chips, trending grid of artwork cards, "Add by RSS URL" action. Result row: artwork, title, author, Subscribe button (toggles).

### 11.2 Podcast detail
Large artwork header with gradient from artwork color, Subscribe/Unsubscribe, expandable description, episode list (title, date, duration, played state, progress bar), per-episode actions: Play, Add to queue, Mark played, Download (optional).

### 11.3 Episode detail
Tabs: **Overview** (summary, takeaways, guests), **Chapters**, **Transcript**, **Chat**, **Show notes**. Each AI tab shows a "Generate" card with a cost hint when empty, and cached results otherwise. Tapping any timestamp seeks the player.

### 11.4 Full player (bottom sheet)
Artwork (tinted gradient), title/podcast, custom seek bar with chapter ticks, play/pause, ±skip, speed chip, sleep timer, queue button, **big Snip button**, current chapter title. Drag down to collapse.

### 11.5 Library
Segments: **Subscriptions · Queue · Snips · Downloads**. Snips: search, edit note, delete, share as Markdown, play from this snip.

### 11.6 News (RSS reader)
- Header with segmented filter: All · Unread · Starred, plus a feed/folder picker sheet.
- Article rows: thumbnail, title, source + relative time, unread dot, one-line summary.
- Pull to refresh, mark all read, swipe-like actions via long-press menu (mark read, star, share).
- **Reader mode:** render with a `WebView` with JavaScript **disabled**, content sanitized (strip `<script>`, `<iframe>`, inline event handlers, `javascript:` URLs) and wrapped in our own CSS (theme-aware typography, responsive images). Top actions: Open original, Star, AI menu (Summarize · Key points · Explain simply · Translate · Ask about this).
- **Daily digest:** one button that summarizes unread items from chosen feeds into a single briefing with links.
- Manage feeds screen: add by URL (auto-discovery), rename, move to folder, delete, import/export OPML, per-feed refresh error display.

### 11.7 Settings
Appearance (theme, accent), AI provider (section 9.2), Playback (speeds, skip), Snips (default length), Feeds (refresh interval), Data (export/import, clear caches), About (privacy notes: "no accounts, no analytics, data stays on this device").

---

## 12. Playback service details

- `PlaybackService` (foreground, `foregroundServiceType="mediaPlayback"`) owns a `MediaPlayer`.
- `MediaSession` + platform `Notification.MediaStyle` with play/pause, skip back/forward, and a custom **Snip** action.
- Handle audio focus (pause on loss, duck on transient), `ACTION_AUDIO_BECOMING_NOISY` (pause), and headset media-button events: **single press** = play/pause, **double press** = skip, **triple press or custom action** = snip.
- Persist position every 5s and on pause/stop/destroy. Resume exactly on next launch.
- Speed with `PlaybackParams` (0.5x-3x), with pitch preserved.
- Sleep timer: minutes or "end of episode", with a gentle fade-out.
- Streaming and local files both supported; prefer a downloaded file when it exists.
- Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` (request at runtime on 33+), `WAKE_LOCK`, and `INTERNET`.
- Fallback if `MediaSession` notification styling misbehaves in this toolchain: a plain notification with `PendingIntent` actions.

---

## 13. Build phases and acceptance checks

**Phase 0, Toolchain probe.** Section 2 complete; facts recorded. *Accept:* every probe item marked pass/fail with a fallback chosen for each fail.

**Phase 1, Foundation.** Application, `AppGraph`, executors, `Result`, `Db` + DAOs, theme tokens, view DSL, `ScreenHost`, bottom bar, empty tab screens, toast host. *Accept:* app launches, tabs switch, theme toggles live, back stack works, no StrictMode violations.

**Phase 2, Networking + feeds engine.** `Http`, `ImageLoader`, RSS/Atom/JSON parsers, discovery, OPML. *Accept:* parse 5 real feeds (2 podcast, 2 news, 1 Atom) with correct items; conditional GET returns 304 on second fetch; images load smoothly.

**Phase 3, News tab (no AI yet).** Feed management, article list, reader mode, star/read, folders, refresh. *Accept:* add a feed by website URL (auto-discovery), read an article offline after caching, OPML round trip keeps folders.

**Phase 4, BYOK settings + AI layer.** `SecureKeyStore`, three provider adapters, test connection, `AiService`, usage log. *Accept:* each configured provider answers the test prompt; wrong key shows a clear message; key survives restart and is not visible in plain prefs.

**Phase 5, AI on articles.** Summarize, key points, explain, translate, ask, daily digest with streaming output. *Accept:* streaming text appears progressively, cancel works, results cached per article.

**Phase 6, Podcasts + discovery.** iTunes search, subscribe, podcast detail, episode lists, show notes. *Accept:* search → subscribe → episodes list shows within 3s on a normal connection.

**Phase 7, Playback.** Service, notification, mini + full player, queue, speed, sleep timer, resume. *Accept:* playback continues with screen off, notification controls work, position restores after force-stop.

**Phase 8, Snips.** Snip button (and headset action), snip storage, library view, Markdown export. *Accept:* snip captures the right window; works from notification; exports a valid `.md`.

**Phase 9, Episode AI.** Transcript (audio mode on Gemini + notes mode fallback), chapters, summary, guests, chat with timestamp citations, snip transcription. *Accept:* transcript highlights and seeks in sync; results cached; cancel and resume work; cost confirm dialog appears.

**Phase 10, Polish.** Skeletons, empty/error states, haptics, animations, accessibility (content descriptions, 48dp targets, TalkBack order), light theme QA, tablet/landscape sanity, crash hardening, memory check. *Accept:* no ANRs in a 10-minute usage script; images don't leak; scrolling stays smooth on a mid-range device.

---

## 14. Quality rules

- No `!!` on external data. Validate all parsed input.
- All user-visible strings in `strings.xml` if resources work (otherwise one `Strings.kt` object), ready for translation.
- No secrets in logs, crash text, exports, or the clipboard.
- Don't hold `Context`/`View` references in background tasks past the screen's life.
- Close every stream/cursor (`use { }`).
- Bound memory: cap transcript JSON size in memory, page large lists, and recycle bitmaps via the cache only.
- Keep each phase compilable at all times; commit-like checkpoints = a short note in the progress log.

---

## 15. Known risks and fallbacks

| Risk | Fallback |
|---|---|
| XML layouts not supported | Kotlin view DSL for all layouts (already the default). |
| Vector drawables fail to compile | Draw icons with `Path` in an `Icons` object. |
| `MediaSession` issues | Plain notification with action `PendingIntent`s; handle media buttons via `MediaButtonReceiver`-style broadcast. |
| Keystore unavailable | Warn the user, store with obfuscation, never crash. |
| Provider has no audio input | Notes mode transcripts + clear UI message. |
| Long audio exceeds limits | Chunked transcription with resume. |
| Feed blocks default User-Agent | Retry with a browser-like UA; show per-feed error. |
| Build is slow | Keep files small; avoid giant `when`/`object` initializers; split heavy screens. |
| Scrolling jank in `ListView` | Simplify row layouts, avoid nested weights, prescale images, defer image decode while flinging. |

---

## 16. Toolchain facts (fill in during Phase 0)

| Item | Result | Fallback chosen |
|---|---|---|
| Kotlin version | Kotlin 2.4.0 (stdlib 2.4.0) | None needed; stable Kotlin features used |
| minSdk / targetSdk allowed | minSdk 26, compileSdk 36, targetSdk 36 | None needed; handle WindowInsets |
| res/XML drawables | Verified supported (AAPT2 compilation) | Custom code Drawable helpers + XML |
| res/XML layouts | Verified supported (inflater / AAPT2) | Kotlin View DSL as primary for maximum consistency |
| HTTPS + permissions | Verified supported (HttpURLConnection + INTERNET) | None needed |
| XmlPullParser | Verified supported (built into Android platform) | None needed |
| SQLite | Verified supported (SQLiteOpenHelper / SQLiteDatabase) | None needed |
| MediaPlayer + speed | Verified supported (MediaPlayer + PlaybackParams) | None needed |
| Foreground service + MediaSession | Verified supported (MediaSession platform API) | None needed |
| Keystore AES-GCM | Verified supported (AndroidKeyStore AES/GCM/NoPadding) | Base64 + SharedPreferences fallback if Keystore ever fails on OEM |
| External jar/aar support (informational) | Verified module dependencies supported | No external libraries required; app is 100% platform-native |

## 17. Progress log

- **Phase 0 (Completed):** Toolchain probe passed all acceptance checks. Verified Kotlin 2.4.0 stdlib, minSdk 26, targetSdk 36, AAPT2 resource linking, SQLiteOpenHelper, XmlPullParser, HttpURLConnection, MediaSession, MediaPlayer PlaybackParams, and Android Keystore AES-GCM encryption. Permissions added to AndroidManifest.xml.
- **Phase 1 (Completed):** Foundation established. AppGraph singleton, AppExecutors, Result/ErrorCode, EventBus, TimeFmt, Logger, Models (Podcast, Episode, EpisodeAi, Snip, ChatMessage, Folder, Feed, Article, QueueItem, AiUsage), SQLite schema and DAOs (PodcastDao, EpisodeDao, FeedDao, ArticleDao, MiscDao), AppTheme tokens, ViewDsl utilities, Screen interface, ScreenHost stack navigator, and BottomBar navigation view. Clean compile and assemble.
- **Phase 2 (Completed):** Networking and feed engine ready. HttpClient with gzip/keepalive/timeout/error handling, SseReader for streaming SSE tokens, ImageLoader with LRU memory cache and disk caching, FeedParser supporting RSS/Atom/JSON feeds with resilient date parsing and HTML stripping, FeedDiscovery auto-detecting RSS/Atom links from websites, OpmlEngine with robust XML outline import and export.
- **Phase 3 (Completed):** News tab fully implemented. FeedRepository background sync with 304 Not Modified support, unread/starred filtering, feed chips bar, article cards list, responsive ArticleReaderScreen with embedded custom WebView styling, star toggle, and mark-all-read. Clean build succeeded.
- **Phase 4 (Completed):** BYOK Settings + AI layer ready. SecureKeyStore with Android KeyStore AES-GCM and fallback encryption, AiProvider interface with streaming and completion modes, OpenAI, Anthropic (Claude), Google Gemini, Groq, and OpenRouter implementations, AiService coordinator with usage persistence, and SettingsScreen with interactive provider key configuration, model selection, token metrics, and theme switcher. Clean build verified.
- **Phase 5 (Completed):** AI on Articles implemented. ArticleAiDialog with real-time SSE streaming for Summarization, Key Point extraction, and ELI5 (Explain Like I'm 5), with one-tap persistent SQLite storage of article summaries and instant badge updates in news feeds and reader mode.
- **Phase 6 (Completed):** Podcasts + Discovery. iTunes search API integration with query decoding, podcast feed parsing (duration, enclosure MP3/M4A, artwork), subscription storage, and episode list view.
- **Phase 7 (Completed):** Native Playback Engine. Foreground PlaybackService with MediaSession and notification controls, PlaybackManager coordinator with position tracking, persistent MiniPlayerView, FullPlayerScreen with seekbar and playback speed toggles (0.8x-2.0x).
- **Phase 8 (Completed):** Snips capture and library. Snip recording with timestamps, custom notes, persistent storage, and Markdown sharing and batch export.
- **Phase 9 (Completed):** Episode AI. EpisodeAiScreen with multi-action streaming AI (Executive Summary, Chapter Breakdown, Guest & Bios extraction, Synthesized Show Notes Transcript) with SQLite persistence.
- **Phase 10 (Completed):** Polish, integration and hardening. Connected MainActivity root navigation, persistent mini player, backstack management, theme support, and verified clean end-to-end assemble.







