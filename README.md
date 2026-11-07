# UPSC 365 (personal Android study planner)

Offline, no account, no network permission. Bundles a 300-spine-day UPSC programme (Mathematics optional + GS, first pass) starting 1 Dec 2026.

## Build the APK with GitHub (no Android Studio needed)
1. Create a new GitHub repository (private is fine).
2. Upload everything in this folder to the repository root (keep the `.github/workflows` folder).
3. Open the repository's Actions tab. The "Build UPSC 365 APK" workflow runs on every push (or press "Run workflow").
4. When it finishes, open the run and download the artifact `upsc365-debug-apk`. Unzip it to get `app-debug.apk`.
5. Copy the APK to your phone and install it (allow "install unknown apps" for your file manager or browser).

The workflow first runs the scheduling-engine unit tests, then builds the debug APK. If any step fails, open the failed step, copy the error text and send it back.

## Notes
- Minimum Android 8.0 (API 26). Debug-signed, which is fine for personal use.
- Auto-backup is disabled on purpose (local only): use Settings > Backup > Export regularly.
- Programme data: `app/src/main/assets/programme.json`. Scheduling logic: `app/src/main/java/app/upsc365/engine/Engine.java`.
