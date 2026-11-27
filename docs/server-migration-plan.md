# Plan: Simple Macro Tracking → server + offline-first PWA

Status as of 2026-11-27. This repo (the Android app) stays as it is, except for one last feature: the
full-fidelity export (step 1). Everything else happens in a new repo.

## Decisions

- **Architecture:** a server on `laptop-server` holds the real database; a PWA is the client on the
  phone and desktop. Built and deployed the same way as Chess Coach (Docker in
  `/srv/services/<name>`, `tailscale serve` for HTTPS, push to `main` = deploy, rollback on a failed
  health check, server-watch alert on deploy failure, `snapshot.conf` with a DB dump in `pre()`).
- **Offline-first.** Everything except AI requests works with no connection:
  - The PWA keeps a full copy of the data in IndexedDB, and the UI reads and writes only that local
    copy. The server is the sync target, not something the UI waits on.
  - Every change is recorded in a local outbox and pushed when online. Pulls fetch everything changed
    since the last sync.
  - Rows get client-generated IDs (UUIDs), so a device can create records offline without
    colliding. Every row carries `updated_at` and a `deleted` tombstone so deletes sync too.
  - Conflicts: last-write-wins per row. With one user and two devices this is enough. Recurring
    overrides and recipe ingredients are separate rows, so edits rarely collide.
  - The app shell is cached by a service worker, so the app opens with no connection.
  - Works offline: diary, foods, recipes, recurring entries and overrides, weight, goals, graphs,
    barcode lookup **of foods already in your database**.
  - Needs a connection: AI entry, OpenFoodFacts lookup for a barcode not seen before (can be queued:
    "look this up when back online").
- **Voice input is dropped** (Vosk goes away).
- **Barcode scanning** in the PWA, using the camera through a JS barcode library (the page is HTTPS on
  `*.ts.net`, so the camera is allowed).
- **AI: Claude on the server, through the Claude subscription, not an API key.**
  - The server calls Claude Code headless (`claude -p` with a JSON schema for the output, or the
    Claude Agent SDK) using the subscription sign-in. Model is chosen in the app's settings and sent
    with each request: Haiku (default), Sonnet or Opus.
  - Requests: free text ("2 eggs and toast with butter") → several entries at once; photo of a
    nutrition label or a meal; your own food library and recipes sent as context so it reuses your
    saved foods instead of inventing macros.
  - Keep the prompt small and the work single-shot (no tools), so it's fast and uses little of the
    subscription's usage limits.
- **One Claude sign-in for the whole server (as far as possible):**
  - `claude remote-control` (claude-rc) only works with a full claude.ai login, which lasts about a
    month. That one can't be shared or extended.
  - Headless use (`claude -p`, the Agent SDK) can instead use a long-lived token from
    `claude setup-token` (valid about a year), passed as `CLAUDE_CODE_OAUTH_TOKEN`. Plan: create one
    token, keep it in a root-only file on the server (e.g. `/etc/claude-token.env`), and give it to
    both the macro app's container and Chess Coach's. Then the macro app adds no new login, Chess
    Coach's separate monthly login goes away, and only claude-rc's monthly renewal remains.
  - Don't share `~/.claude/.credentials.json` between programs: each one refreshing the same login
    can invalidate the others' refresh token.
  - Moving Chess Coach to the shared token is a separate change in the Chess Coach repo.
  - server-watch gets a check for the token's expiry, like `coach_login`.
- **Fresh repo, not a git fork.** Almost none of the Android code carries over. What carries over
  is the data model, the business rules (recipe expansion into per-ingredient diary entries,
  recurring entries with SKIP/EDIT overrides, scaling by `baseAmount`, `IMPORT` foods hidden from
  the food list) and the OpenFoodFacts lookup. Port those with tests.

## Steps

1. **Full-fidelity export in the Android app (this repo).** One JSON file with every table as stored
   (raw rows, original IDs) plus settings. Done in this repo; see below.
2. **Last APK release** with the export. Run it on the phone and keep the file somewhere safe (it
   also goes onto the server for the import).
3. **New repo: server.** Data model with UUIDs, `updated_at` and tombstones; sync API (push outbox,
   pull changes since a cursor); pairing/token auth like Chess Coach; OpenFoodFacts proxy;
   importer for the export file (maps old integer IDs to new UUIDs and keeps every link).
4. **Import check.** Import the export, then compare row counts and per-day calorie/macro totals
   with the phone. Doesn't move on until they match.
5. **PWA, feature parity:** diary (with recurring entries and overrides), food database, recipes,
   barcode scan, manual entry, weight log and graph, goals, settings. Offline from the start,
   since retrofitting sync later is much harder.
6. **Deploy on laptop-server**, Chess Coach pattern, plus `/srv/README.md` and server-watch updates.
7. **Claude integration:** the shared `setup-token` token, model setting, multi-item text parsing,
   photo input, your food library as context.
8. **Run both side by side** for a week or two, then uninstall the APK.

## The export file (step 1)

- Settings → **Export All Data (JSON)** opens a save dialog; default name
  `macro-backup-YYYY-MM-DD.json`.
- Contents:
  - `format`, `formatVersion`, `exportedAt`, `appVersionName`/`appVersionCode`,
    `databaseVersion` (Room schema version, 4 today).
  - `tables`: every table in the Room database, read generically from `sqlite_master`, so nothing
    is missed (including `IMPORT`-source foods hidden in the UI, and any column added later).
    Each table has its `columns` and `rows` with the raw stored values (dates as ISO strings,
    enums as names, booleans as 0/1).
  - `counts`: rows per table, for checking the import.
  - `settings`: goals, weight unit, graph settings, and the AI provider list **without API keys**
    (keys are left out on purpose; the new app doesn't use them).
