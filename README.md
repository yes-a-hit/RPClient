<div align="center">
  <img src="app/src/main/res/drawable/app_logo.png" alt="RPClient Logo" width="112" />

  # RPClient Unofficial Version (RPClient UV)

  简体中文见 [README_ZH.md](README_ZH.md) | English

  An unofficial customization branch of [KafuuNeko/RPClient](https://github.com/KafuuNeko/RPClient), forked from upstream `2026.3.1` (commit `c3ed325`).

  A local-first AI role-playing chat client for Android.

  Supports character cards, lorebooks, one-on-one chats, group chats, long-term summary memory, prompt inspection, and multiple LLM APIs — plus **scenario-driven adventures, a sandboxed assistant, whole-novel import, and cost tracking**.

  [Overview](#overview) · [Branding](#branding-and-packaging) · [New in This Fork](#new-in-this-fork) · [Screenshots](#screenshots) · [Features](#features) · [Development Status](#development-status) · [Known Issues](#known-issues) · [Quick Start](#quick-start) · [Build](#build)
</div>

## Overview

RPClient is built with Kotlin and Jetpack Compose to provide a complete and controllable AI role-playing experience on Android devices. Chat histories, characters, lorebooks, and app settings are stored locally by default, while model requests are sent directly to the model service selected by the user.

The project draws on SillyTavern's ecosystem for character cards, lorebooks, prompts, and Regex scripts, and provides corresponding import, export, and compatibility features. RPClient is not an Android port of SillyTavern, and some advanced fields and runtime behaviors may differ.

**About this branch.** `RPClient UV` ("UV" = Unofficial Version) is a personal fork maintained and signed by `yes a hit`. Everything upstream does still works here; on top of it the fork adds the four feature families described in [New in This Fork](#new-in-this-fork). Upstream files are kept as close to the original as possible, with only the minimal edits required, so that upstream updates can keep being merged in.

> [!WARNING]
> This project is still under development. Data structures, the user interface, and compatibility behavior may continue to change. Keep the original files when importing important character cards, lorebooks, or Regex scripts, and back up your data regularly.

> [!IMPORTANT]
> RPClient UV is distributed as a **debug-signed** build and is not intended for store publication. The upstream app on Google Play and F-Droid is a different package and is not maintained by this branch.

## Branding and Packaging

- The app display name is `RPClient UV`; `app_name` and the `about_brand_name` shown on the About screen were both changed, and debug builds no longer append `(Dev)`.
- `applicationId` is `me.kafuuneko.rpclient.uv` (debug builds: `me.kafuuneko.rpclient.uv.dev`), fully isolated from the official `me.kafuuneko.rpclient`. The two can be installed side by side and never overwrite each other.
- Because the package name changed, in-app data is **not** inherited from the official app or from earlier custom builds. Upgrading from either requires re-importing characters, lorebooks, and model configurations.
- The Kotlin `namespace` stays `me.kafuuneko.rpclient`; the source tree layout is unchanged.
- The About screen has a new "Custom Version" section showing the maintainer `yes a hit`, the branch's own version, and the upstream version it is based on, in the form `UV <custom version> · upstream <upstream version>`. It also exports the custom-branch notes through the system file picker (`ACTION_CREATE_DOCUMENT`), so no storage permission is needed; the document itself ships as `assets/README_UV.md`.

## Default Language

The default resource set (`res/values/strings.xml`) was replaced wholesale with Simplified Chinese, so the UI shows Chinese whenever the system language is outside the supported list — including on English devices. The other 12 locale directories keep their upstream content and still apply for their respective languages.

Every format placeholder was verified one by one during the move: all 1,263 entries that align with the Chinese translation match exactly. XML well-formedness and entry counts (1,265 `string` entries and 3 `plurals`) were checked.

`tools/localize-default-zh.py` merges the `values-b+zh+Hans` translations back into the default resource set — re-run it once after each upstream string update.

## New in This Fork

### Scenario & Adventure System (branch-only)

- Scenarios are described in JSON: scenes, conditions, checkpoints, multiple entry points, unlock relationships, character allow-lists and banned lists, a timeline (turn cap, `0` = unlimited), and GM / dice switches. A scenario set can hold several scenarios on top of them.
- The format specification lives in [doc/剧本格式.md](doc/剧本格式.md).
- Authoring is table-based: tap two cells to link a successor or an unlock relationship; scenes can be renamed, given a turn cap, marked as the opening scene or an entry point, and deleted.
- Runtime resolution uses keywords, regular expressions, and the turn cap — **failure is checked before victory**, and after a win the run continues in free mode if the scenario says so. Outcomes are resolved by the app rather than interpreted by the model, so the same transcript always produces the same ending.
- Adventure extras: pick which characters are present at the start (injected into the "player-specified present" section of the prompt), GM mute / forced-speaker for one turn (auto-cleared afterwards), draft the player's action for you (filled into the input box for confirmation, never sent directly), skip your turn with "silent observation" as an implicit input, and auto-advance that takes the first candidate up to 5 turns in a row.

### Assistant

A separate entry in the home screen's quick-action area opens an independent conversation page, alongside character chats, group chats, and story writing. It does not occupy character cards or session data.

- **Independent sessions**: stored in `filesDir/assistant/`, up to 8 concurrent sessions with the least recently updated one auto-archived over the limit. Sessions are one-tap clearable and never touch Room, so no database migration is involved.
- **Sandboxed files**: the assistant can only touch `filesDir/assistant/sandbox` through `list_files`, `read_file`, `write_file`, `move_file`, and `delete_file`. Paths are normalized first and anything escaping the sandbox is refused.
- **Character-card editing**: `list_characters`, `get_character`, and `update_character` read and write cards, but only the eight fields `name`, `description`, `personality`, `scenario`, `firstMessages`, `postHistoryInstructions`, `creatorNotes`, and `systemPrompt`. Deleting characters or changing bindings is not supported.
- **Lorebook building**: `list_worldbooks`, `create_worldbook`, `add_worldbook_entry`, and `get_worldbook_entries` turn loose setting files in the sandbox into a lorebook — either a new one or appended to an existing one. Entries without keywords are written as constant entries.
- **Scenario tools**: the fourth tool family, covering scenario files alongside files, cards, and lorebooks.
- **Tool protocol**: instead of the mutually incompatible function-calling APIs of each vendor, the model is asked to emit `<tool name="...">{...}</tool>` in the body. The app parses and executes it and feeds the result back as `<tool_result>`, running at most 5 consecutive calls per reply. Works on OpenAI Compatible, Gemini, and Anthropic alike.
- **Prompt modes**: the top-right switch picks between inheriting the global Main Prompt (with `{{char}}` resolved to Assistant and `{{user}}` to the current user name) and using a separately stored prompt. Both modes append the assistant's identity boundary and tool description.
- **Attachments**: images (sent as multimodal blocks) and text files. A picked text file is imported into the sandbox rather than dumped into the message body.
- **Model dependency**: the assistant uses the current global model configuration, so enable one under "Settings > Model Configurations" first.

### Whole-Novel Import

The "new story" page gained "Import a local novel", complementing the single-file import already inside the story editor.

- **Automatic chaptering**: `NovelChapterParser` splits on `第X章/回/节/篇/卷/话`, `Chapter N`, Markdown `#` headings, and fixed markers such as `序章`, `楔子`, `番外`. Number recognition covers Chinese numerals (`第十二章`), Arabic numerals (`7`), decimals (`1.5`), and bare numeric prefixes (`003`).
- **Loose multi-file chapters**: select several files at once, merged by natural sort order (`chapter2` before `chapter10`); files with no heading fall back to their file name. Loose files are ordered by numeric chapter index, and `第X卷` / `Volume N` headings are detected to split and regroup volumes.
- **No false headings**: a title line may be at most 40 characters, so a long sentence in the prose that merely mentions "chapter three" is not mistaken for a heading.
- **Encoding fallback**: read as UTF-8, falling back to GB18030 when too many replacement characters appear, which keeps Chinese TXT dumps readable.
- **Hooks into continuation writing**: the import builds the chapter skeleton as-is and writes the body text, so you can continue straight away in the story editor with summaries and lorebooks.
- **Manual reordering**: a sort button in the top bar reorders chapters by index, by title, or reversed.
- Import mode coexists with character and lorebook selection; both are attached on creation. Optionally, the model can be asked to extract character cards and a lorebook from the imported text.

### Cost Tracking

On top of the existing token statistics, the usage page adds cost estimation and a balance lookup, with costs settled in CNY.

- **CNY pricing**: domestic providers use their official CNY price lists; foreign models use their USD price multiplied by an exchange rate (default 7.2, editable in preferences). The UI shows `¥` throughout.
- **Off-peak / peak tiers**: official tiers are built in for SiliconFlow DeepSeek V4 Flash (0731) — 02:00–08:00 at ¥0.150 cache-hit / ¥1.500 input / ¥4.500 output per million tokens, all other hours at ¥0.300 / ¥3.000 / ¥9.000. Each request is priced by its own timestamp, because an aggregated total cannot separate peak from off-peak.
- **Cache hits priced separately**: cached input uses the cache-hit rate instead of being mixed into ordinary input.
- **Site binding**: built-in price lists are keyed to a site hostname (e.g. `siliconflow.cn`) and apply only when the request actually reached that site — the same model is priced differently at different providers, so prices are never borrowed across sites. Unmatched sites or models fall back to live OpenRouter pricing converted at the configured rate.
- **Live quotes**: uncatalogued models are looked up in OpenRouter's public model list (`https://openrouter.ai/api/v1/models`), cached locally, with the last snapshot reused offline. The card also shows coverage and the models left unpriced.
- **Model-name normalization**: request strings and price-list keys rarely match (`openai/gpt-4o-2024-08-06`, `claude-3-5-sonnet@20240620`). Matching strips the vendor prefix first, then an explicit version tail — only version markers, so the `-flash` in `gemini-2.5-flash` is never mistaken for one.
- **Account balance**: official balance endpoints are wired up. OpenRouter uses `GET /api/v1/credits` (balance = total top-ups − total usage); DeepSeek uses `GET /user/balance` (available, granted, and topped-up balances directly). Providers with no public endpoint show an explanation rather than an error.
- **Distinct states**: the balance distinguishes not queried / querying / available / not supported by the provider / query failed, and never blurs them into `0` or a placeholder.

> Zhipu and Moonshot AI, among others, have no built-in CNY price list: public sources only offer third-party USD conversions that may not match the official rate. Rather than print a number that could be wrong, those fall back to live OpenRouter quotes. For an exact bill, use the provider's own console.

### Upstream Build Fix

- `feature/characteredit/ui/CharacterEditLayout.kt` was missing the `androidx.compose.foundation.layout.ExperimentalLayoutApi` import while using `FlowRow`, which fails to compile against upstream's current Compose BOM.

## Screenshots

<p align="center">
  <img src="metadata/images/home.jpg" alt="RPClient home screen" width="45%" />
  <img src="metadata/images/chat.jpg" alt="RPClient chat screen" width="45%" />
</p>
<p align="center">
  <img src="metadata/images/story.jpg" alt="RPClient story editor screen" width="45%" />
  <img src="metadata/images/settings.jpg" alt="RPClient settings screen" width="45%" />
</p>

## Features

### Conversations

- One-on-one character chats and multi-character group chats
- Streaming responses, stop generation, regenerate, and continue generation
- Impersonate generation from the user's perspective
- Create conversation branches from a selected message
- Markdown message rendering and collapsible reasoning content
- Automatic or manual long-term summary memory generation
- Per-chat lorebook entry selection

### Characters and Lorebooks

- Create, edit, search, and manage characters
- Import Character Card V1/V2 JSON and PNG character cards
- Export Character Card V2 JSON or PNG files with embedded metadata
- Character avatars, multiple greetings, example dialogues, and extension fields
- Create, edit, import, and export SillyTavern-style lorebooks
- Keyword scanning, recursive activation, probability, priority, depth, Token budget, and other settings

### Models and Prompts

- OpenAI Compatible API
- Google Gemini API
- Anthropic Messages API
- Built-in configuration templates for ChatGPT, Gemini, Claude, DeepSeek, Grok, and OpenRouter
- Custom service URLs, models, request headers, and generation parameters
- Prompt presets, macro expansion, and protocol-specific message post-processing
- Prompt Inspector for reviewing final messages, Token budgets, lorebook matches, Regex processing, and omitted content
- Debug mode for locally recording and viewing raw request and response JSON

### Story Writing

- Continuous manuscript editor with debounced local autosave and revision conflict protection
- End-of-document continuation with persistent per-chapter guidance and multi-step session undo/redo
- Story Memory, Author's Note, per-story character activation, lorebook matching, and Prompt Inspector
- Streaming generation with stop, recoverable partial output, and multi-step session undo/redo
- TXT/Markdown import and export, plus portable `.rpstory.json` archives
- Whole-novel import with automatic chaptering and volume detection (see [New in This Fork](#new-in-this-fork))

### Regex Scripts

- Compatible with commonly used SillyTavern Regex script JSON
- Global and Character scopes
- Source, Markdown, and Prompt execution modes
- Script ordering, duplication, testing, import, and export
- Scripts embedded in character cards are not automatically granted permission to run

### Other

- Local-first data storage
- Material 3 and dynamic colors
- User interface available in Simplified Chinese, Traditional Chinese, English, Japanese, Korean, German, French, Russian, Portuguese (Brazil), Italian, Vietnamese, and Indonesian, with Simplified Chinese as the built-in default
- Android 8.0 (API 26) or later

## Development Status

Legend: **Working** = verified on a real device; **Limited** = usable but dependent on external conditions; **WIP** = the UI has a placeholder, the feature is not wired through.

| Feature | Status | Notes |
| --- | --- | --- |
| Scenario JSON import and validation | Working | Refuses orphan scenes, dead loops, and cast/banned-cast conflicts |
| Mainline / loop / free-mode resolution | Working | Keywords, regex, turn cap; failure checked before victory |
| Failure and victory notices | Working | Offers "restart this scene" and "leave" at the end |
| Suggested actions (option block) | Limited | Parsing tolerates a missing closing marker; whether the model emits the block at all depends on prompt adherence |
| Narration and per-character segments | Limited | Parser is in place and character avatars are looked up from cards; relies on the model splitting with `【旁白】/【角色名】` |
| Choosing present characters at adventure start | Working | Selection is injected as the "player-specified present" prompt section |
| GM mute / forced speaker | Working | Applies for one turn; the forced speaker clears automatically after it |
| Drafting the player's action | Working | Result lands in the input box for confirmation, never sent directly |
| Skipping a turn | Working | "Silent observation" acts as implicit input so the situation develops on its own |
| Auto-advance | Limited | Takes the first candidate, up to 5 turns in a row; stops in place when there is no candidate |
| Scene transitions | WIP | A scene change only inserts an "entering X" line; no transition prose is generated yet |
| Checkpoint rollback | WIP | The format accepts a `checkpoint` field; the runtime saves no snapshots yet |
| Per-scene lorebook filtering | WIP | Global and scene lorebooks are both injected; entry-level filtering per scene is not implemented |
| Assistant image attachments | Limited | Images are sent as multimodal blocks; vision-unaware models answer with HTTP 400 and the error is written into the transcript |
| Assistant text attachments | Working | Picked text files are imported into the sandbox instead of inlining the full body |
| Assistant multi-session | Working | Up to 8, oldest auto-archived beyond the limit |
| Novel import and volume grouping | Working | Chinese numerals, decimals, bare numeric prefixes; volumes grouped from title text |
| Manual chapter reordering | Working | Top-bar sort by index, by title, or reversed |

## Known Issues

- "Continue adventure" on the adventure page still lists runs that already failed or already won; they are neither labelled nor collapsed.
- On narrow screens the five filter chips in the home screen's filter bar have to be scrolled sideways to reach the last one.
- Scenario generation depends entirely on the model filling things in properly: if it leaves out `cast`, `bannedCast`, or `failConditions`, the runtime can only treat them as empty lists. Validation catches structural errors, but not fields that are present and meaningless.
- A missing `>>>` closing marker on the option block is tolerated, but when the model emits no option block at all nothing is generated in its place.
- Narration / character segmentation and the suggested-action block rely on the model obeying the prompt; adherence is uneven and the fork currently needs repeated prodding to write them reliably. Further prompt engineering is needed.
- The assistant truncates its own replies, and the page lacks the editing, regenerate, and export affordances of a mature single-session chat; the reasoning chain cannot be expanded either. There is no "retry / whip the assistant" button. Porting the mature single-chat feature set across is the intended fix.
- The assistant cannot write the added non-official character-card fields (such as relationship history, likes and dislikes) when authoring a card, and the corresponding JSON templates — especially for those new fields — are missing.
- Skills cannot be imported or read: a manual import is not recognized, and an automatic import fails.
- Cost tracking is not fully dependable: price lists are sometimes not fetched, in which case automatic pricing does not happen.
- The two large additions, scenarios and adventures, still need further polishing before they are comfortably playable.

## Quick Start

1. Install. Upstream RPClient is available on [Google Play](https://play.google.com/store/apps/details?id=me.kafuuneko.rpclient), [F-Droid](https://f-droid.org/packages/me.kafuuneko.rpclient/), and [GitHub Releases](https://github.com/KafuuNeko/RPClient/releases); RPClient UV comes from this repository's Releases and installs alongside the official app.
2. Go to "Settings > Model Configurations" and select an existing template or create a new configuration.
3. Enter the API key, model name, and service URL. Test the connection, then enable the configuration. Scenarios, the assistant, and novel extraction all reuse this global configuration.
4. Create a character or import an existing JSON/PNG character card.
5. Import lorebooks and associate them with characters as needed.
6. Create a one-on-one chat or group chat, open "Story Writing" to start or import a manuscript, open "Assistant" for sandboxed tool work, or load a scenario to start an adventure.

API keys are stored only on the device, but they are sent to the configured model service with model requests. Only use API endpoints and proxy services that you trust. This repository contains no keys of any kind.

## Build

Prerequisites: JDK 17 and an Android SDK (`compileSdk` 36.1, `minSdk` 26). The branch has been verified with AGP 9.0.1, Gradle 9.5, Kotlin 2.0.21, platform 36.1, and build-tools 36.1.0.

```bash
# Locally
./gradlew assembleDebug testDebugUnitTest

# Publish the artifact into dist/ with a versioned name
pwsh -File tools/publish-apk.ps1 -Install
```

Verification status: `assembleDebug` passes, and `testDebugUnitTest` is green — all 279 pre-existing tests plus 23 new ones (8 for the assistant tool protocol parser, 8 for price matching and costing, 7 for novel chapter parsing).

Pushing `master` triggers CI automatically: unit tests run, a debug build is produced, and it is published to Releases tagged `v<custom version>` under a name such as `rpclient-2026.3.1-dev-debug-v0.17.apk`.

Maintenance scripts that take no part in the app build:

- `tools/localize-default-zh.py` — merges the `values-b+zh+Hans` translations back into the default resource set.
- `tools/backup/` — the original `strings.xml` from before localization.
- `tools/run-emulator.ps1` — starts an AVD and places its window on a visible desktop, optionally installing the APK. The emulator sizes its window from device pixel height, which overflows the screen under high-DPI scaling; this script corrects that.
- `dist/` — debug APKs and screenshots. Local artifacts, excluded via `.gitignore`.

## AI Coding Guide

AI-assisted changes must start from [doc/coding-guidelines.md](doc/coding-guidelines.md). The guide is tailored for RPClient's Feature + MVI structure, Room data layer, Koin/Kotpref usage, and domain rules for character cards, lorebooks, prompts, Regex scripts, LLM requests, and request logs.

Read only the relevant topic guides for the task before editing code, then run the smallest verification that matches the risk of the change.

## Contributing

Issues and pull requests are welcome. Before making changes, please review the [AI Coding Guide](doc/coding-guidelines.md) when using AI-assisted coding.

Before submitting code, please ensure that the project can at least be built successfully.

When reporting compatibility issues, include sanitized character cards, lorebooks, or Regex JSON where possible, along with the API protocol, model name, and reproduction steps. Please state whether the issue concerns this fork or upstream, and which `UV` version you are on. Do not disclose API keys, private conversations, or character resources without authorization in an issue.

## Privacy and Disclaimer

- RPClient does not provide model services. Users are responsible for API usage fees and generated content.
- Request content is sent to the model service selected by the user. Review the service provider's privacy policy before use.
- Token usage statistics are stored locally as metadata and may be estimated; they are not billing statements and do not contain request or response bodies. The cost estimate is an approximation for personal reference only — use the provider's console for anything that needs to be exact.
- RPClient UV is a debug-signed personal fork and is **not suitable for store distribution**.
- This project is not affiliated with or officially partnered with KafuuNeko/RPClient, SillyTavern, OpenAI, Google, Anthropic, DeepSeek, xAI, or OpenRouter.
- Copyright and usage permissions for character cards, lorebooks, and other imported content are the responsibility of their providers and users.

## Contact

- This fork: maintained by `yes a hit`
- Upstream: [KafuuNeko/RPClient](https://github.com/KafuuNeko/RPClient), kafuuneko@gmail.com
