# CLAUDE.md — MoeMemos, Coffer fork

This repository is a fork of [mudkipme/MoeMemosAndroid](https://github.com/mudkipme/MoeMemosAndroid)
that adds a **Coffer** account: the notes of a Coffer instance (`notes:notes`) as memos, over
Coffer's existing REST API. Coffer itself lives next door, `../coffer` (its `CLAUDE.md`, and
`docs/decisions.md` 2026-10-26 "Notes on Android: a MoeMemos fork").

## Branches and remotes

- Work on **`coffer`**. `main` mirrors upstream and is never committed to.
- `origin` = `grigorii-horos/MoeMemosAndroid`, `upstream` = `mudkipme/MoeMemosAndroid`.
- Commit your own work without asking; push `coffer` to `origin` when the owner asked for the
  work. A tag is a release (see below) — push one only when asked.

## How we change code here

The fork must stay cheap to merge with upstream. That decides where a change goes.

1. **Coffer logic lives in our own files**: `app/src/main/java/me/mudkip/moememos/data/coffer/`
   (`CofferApi.kt` — routes, wire types and the `CofferNode` constants; `CofferRepository.kt` —
   MoeMemos' `RemoteRepository` over Coffer; `CofferCompose.kt` — memo text ↔ note fields and
   bare file names ↔ URLs) and `ui/component/CofferAccount.kt`. Tests next to them under
   `app/src/test/java/me/mudkip/moememos/data/coffer/`.
2. **Prefer the backend over the UI.** Map a MoeMemos concept onto Coffer inside
   `CofferRepository` (pinned → favorite, visibility → `_visibility`, bare names → URLs) before
   touching a MoeMemos screen. A new idea is first asked "can the backend do it alone?" — the open
   list is `../coffer/docs/tasks/2026-10-26-moememos-fork-coffer-integration/task.md`.
3. **An upstream file gets the smallest possible edit.** A `when` branch for `Account.Coffer`, one
   added condition on an existing line (`if (!isCoffer) item {`), one optional parameter. Never
   re-indent or move an upstream block — wrap with a one-line condition instead, so a merge
   conflicts on one line, not twenty.
4. **Keep the list of touched upstream files current** in `README.md` § Upstream sync whenever a
   commit adds one. That list is what the next upstream merge checks.
5. **A Coffer capability with no MoeMemos counterpart is hidden for a Coffer account**, not
   faked; a MoeMemos control with no Coffer counterpart likewise (archive, "protected"
   visibility, location).
6. **Coffer's REST shape is a contract.** Measure it against a live instance (`curl` with an API
   token) before relying on it, and record what was measured in the `CofferNode` KDoc. Coffer's
   `packages/plugin-notes/src/notes-shape.test.ts` pins the node keys and kinds the fork reads;
   a change on either side moves both together. Writes to a live instance for a probe go to a
   note named `moememos probe …` that is trashed right after.
7. **Stay compatible with the Coffer version on prod and the one in `main`.** A field the older
   server does not know must be harmless to it (it ignores `_visibility`, `node`/`id` upload
   params) — check by probing, not by reading code.

## Build and test

- `ANDROID_HOME=/opt/android-sdk` (gradle, adb, JDK on the machine).
- `./gradlew :app:testDebugUnitTest` — every Coffer change comes with a unit test (fake
  `CofferApi`, no network). Read results from `app/build/test-results/testDebugUnitTest/*.xml`.
- `./gradlew :app:assembleDebug` for a quick device check; the phone runs a **release** build, so
  a debug APK cannot be installed over it (different signature).

## Release

- Signing key: `~/Sync/Configs/Things/android/signing/release.jks` (alias `grigorii`, password in
  `release.pass`, same for the key). Never print or commit it.
- Bump `versionName` in `app/build.gradle` to `<upstream version>-coffer.<n>`, commit, push a tag
  with exactly that name. `.github/workflows/build-signed-release-apk.yml` signs it with the
  repository secrets (`ANDROID_KEYSTORE_BASE64`, `ANDROID_SIGNING_STORE_PASSWORD`,
  `ANDROID_SIGNING_KEY_ALIAS`, `ANDROID_SIGNING_KEY_PASSWORD` — set by the owner) and attaches the
  APK to a GitHub release (not a pre-release) that Obtainium follows.
- A local signed build: export those four values as `ANDROID_SIGNING_*` env vars (store file =
  the `.jks` path) and run `./gradlew :app:assembleRelease`.

## Device

- The owner's phone is reachable over wireless adb (`adb devices`). If it shows a PIN lock, stop
  and ask — never enter a PIN. Dismiss password-manager autofill prompts with "No thanks".
- Prod is `https://coffer.grigorii.link`. An emulator does not trust the home LAN CA, so test
  against that public host.

## Upstream sync

`git fetch upstream && git merge upstream/main` on `coffer`, resolve the conflicts in the files
listed in `README.md`, run the tests, build, push.
