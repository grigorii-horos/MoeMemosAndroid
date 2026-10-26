# Moe Memos — Coffer fork

This fork (`me.mudkip.moememos.coffer`, installs beside upstream) adds a **Coffer** account: the
notes of a [Coffer](https://github.com/grigorii-horos/coffer) instance (`notes:notes`) as memos,
over Coffer's own REST API — no Coffer plugin.

- **Log in** on "Add Memos Account" with the Coffer address and an API token. A host whose
  `/health` answers `{"status":"ok","startedAt":…}` is taken as Coffer.
- **One memo = one note.** The memo text is `# title`, the text, then `#tags`; on save it is split
  back (`data/coffer/CofferCompose.kt`). A tag written mid-text moves to the end after a save.
- **Attachments** go to `/api/upload`; images land in the note's `gallery`, other files in `files`.
- **Delete** moves the note to Coffer's trash. Pin, archive, visibility and location are not stored.
- A self-signed home CA must be installed on the phone as a user certificate.

Branch `coffer` carries the fork; `upstream` is mudkipme/MoeMemosAndroid.

---

# Moe Memos

[![justforfunnoreally.dev badge](https://img.shields.io/badge/justforfunnoreally-dev-9ff)](https://justforfunnoreally.dev)

<img alt="Moe Memos" src="https://memos.moe/memos.png" width="160" height="160" />

**Moe Memos** is an app to help you capture thoughts and ideas.

You can use Moe Memos with either a self-hosted [✍️memos](https://github.com/usememos/memos) server or locally on your device (no server required).

**Note: Current Moe Memos version supports Memos 0.21.0 and Memos 0.27.0 to 0.31.0. Memos update may introduce breaking API changes. If you are using a version higher than 0.31.0, it is recommended to use [Mortis](https://github.com/mudkipme/mortis) to convert the newer Memos API to the Memos 0.21.0 API and re-login in Moe Memos.**

## Installation

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/me.mudkip.moememos/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=me.mudkip.moememos)

Or download and install the APK package from the [Releases section](https://github.com/mudkipme/MoeMemosAndroid/releases/latest).

## Features

- Write memos like tweeting to yourself
- Use Moe Memos locally on your device (with backup and restore) or sync with your own ✍️memos server
- Offline-first experience with automatic sync when you are back online
- Material You design with dynamic themes and themed icon
- Rich memo content: Markdown editor and renderer, images, non-image attachments, and to-do items
- Organize and find memos with tags, pinning, and search
- Home screen widget and share sheet integration (text, images, and webpages)
- View your memo activity with a progress graph
- Full privacy protection, no data collection

Moe Memos is a third-party client for [✍️memos](https://github.com/usememos/memos) and both projects aren't affiliated with each other.

## Development

Moe Memos tends to keep minimal and optimized for best native experience. The Android version is developed with Kotlin and Jetpack Compose.

Any contributions are greatly appreciated.

## License

The Android version of Moe Memos is under [GPLv3](LICENSE).
