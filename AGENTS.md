# Elderly Voice Assist ("עזרה - העוזר האישי שלך") — Engineering & Quality Gate Rules

You MUST follow all rules in this file unconditionally whenever modifying, reviewing, or building this project.

## 1. Communication & Delivery
- Always respond to the user in Hebrew.
- Whenever changes affect the Android app, increment `versionCode` and `versionName` in `app/build.gradle.kts`, update the version label in `Screen.SETTINGS` in `Ui.kt`, run `.\scripts\build.ps1` (which runs `:app:assembleDebug`, `:app:testDebugUnitTest`, and `:app:lintDebug`), and commit + push to GitHub.

## 2. Mandatory Pre-Build Adversarial Self-Review (Quality Gate)
Before running `.\scripts\build.ps1` or pushing any code change, you MUST audit the entire `git diff` against all 7 of the following Android & Elderly-UX failure modes. For non-trivial changes, invoke the `research` subagent (with `Model = "pro"`) as an adversarial code reviewer on the `git diff` before building.

1. **Android Activity Lifecycle (`onStop` / `onResume` / External Intents):**
   - Launching any external Activity or dialog (e.g., `RecognizerIntent`, permission prompt, file picker) triggers `onStop()`.
   - Never wipe pending user state (such as `model.confirmation`, `pendingCall`, or active voice results) inside `onStop()` when an external intent/dialog is in flight (`awaitingSpeechDialog`).

2. **SpeechRecognizer Session & Instance Isolation (`Speech.kt`):**
   - Never call `SpeechRecognizer.destroy()` synchronously on the UI thread immediately before `createSpeechRecognizer()`.
   - When a recognition session is cancelled while `inFlight == true`, detach its listener (`setRecognitionListener(null)`), cancel it, retire the instance (`recognizer = null`), and destroy it asynchronously so late IPC events from Android's `SpeechRecognizerImpl` can never leak into a subsequent session.
   - Every `RecognitionListener` callback must verify both `session == generation` and `rec === recognizer`.

3. **Cancellable Background Coroutines (`CompanionModel.kt`):**
   - Never launch untracked coroutines (`viewModelScope.launch`) for searches or commands that mutate screen state (`screen`, `callChoices`, `confirmation`, `answer`).
   - Always store async search/command jobs (e.g., `searchJob`) and cancel them inside `stop()`, `quietStop()`, `cancel()`, and `navigate()`.
   - Settings/Profile saves (`saveProfile`) must use a dedicated conflated/cancellable job (`profileSaveJob`) rather than `runTask`, so rapid toggle clicks are never dropped when `busy == true`.

4. **Main Thread Responsiveness (Zero ANR):**
   - Contact loading, phonetic matching (`matchPeople`), file I/O, and network calls must run off the main thread (`Dispatchers.Default` or `Dispatchers.IO`).
   - Text search inputs must use debounce (`debouncedQuery`) so typing never lags on large contact lists.

5. **Pagination & Unbounded Lists (`Ui.kt`):**
   - Never silently truncate user-facing lists with `.take(50)` without providing a clear "Show More" (`הצגת עוד...`) button so all items remain reachable.

6. **Contact & Phone Filtering (`Services.kt` / `Domain.kt`):**
   - Strip invisible Unicode BiDi/control characters (`\u200B-\u200F`, `\u202A-\u202E`, `\u2060-\u206F`, `\uFEFF`) in `normalized()`.
   - Filter carrier/service codes (`*` or `#`) by inspecting the **phone number** (`rawPhone`), never the contact's display name.
   - Always sort favorite (`starred`) contacts first in both general lists and search results.

7. **Elderly-Accessible UI & Font Scaling (`Ui.kt`):**
   - All text must use `AssistantFont`.
   - Buttons and tiles side-by-side (`Row`) must support multi-line wrapping (`maxLines = 2`) and at most 2 tiles per row (`chunked(2)`) so Hebrew labels never clip under large font scales (`fontScale`).
   - Always provide an explicit, large button to dismiss the software keyboard (`סיום הקלדה / סגירת מקלדת`) in addition to auto-hiding on scroll.
