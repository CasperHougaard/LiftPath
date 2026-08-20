# Google Drive backup — one-time OAuth client registration

LiftPath's Drive backup destination (`DriveAuthHelper` + `DriveBackupHelper`) uses Play
Services' Authorization API to get a Drive access token straight from the device — there is no
backend, no client secret, and nothing to store in the app. The OAuth 2.0 client is an
**Android**-type client in Google Cloud Console, matched to LiftPath by package name and signing
certificate. This only has to be done once per signing key (debug and release each need their
own client), and it does not need to be done again when the app is rebuilt.

## 1. Create (or select) a Google Cloud project

Console → [console.cloud.google.com](https://console.cloud.google.com) → create a project (or
reuse one) for LiftPath.

## 2. Enable the Drive API

APIs & Services → Library → search "Google Drive API" → Enable.

## 3. Configure the OAuth consent screen

APIs & Services → OAuth consent screen.

- User type: External (unless everyone testing has a Google Workspace account tied to this
  project's org).
- Scopes: add `https://www.googleapis.com/auth/drive.file`. This is a *sensitive* scope, not a
  *restricted* one — it needs Google's standard verification before general release, but not
  the heavier restricted-scope security assessment, because it only grants access to files the
  app itself created.
- While the app is unverified, add your own Google account under **Test users** so you can use
  Drive backup during development without waiting on verification.

## 4. Create an Android OAuth client for each signing key

APIs & Services → Credentials → Create Credentials → OAuth client ID → Application type
**Android**.

- Package name: `com.liftpath`
- SHA-1 certificate fingerprint: from the keystore that will sign the build.
  - Debug: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`
  - Release: `keytool -list -v -keystore <path from keystore.properties>` (password in
    `keystore.properties`, not committed — see `KEYSTORE_CREDENTIALS.txt`)

Repeat this step for both the debug and release keystores; each is a separate client entry, but
neither produces a client ID or secret you need to paste into the app — the client is looked up
by Google Play services from the signing certificate at runtime.

## 5. Verify

Build and run, open Settings → Backup & Sync → Google Drive, and connect. On an unverified
consent screen you'll see an "unverified app" warning — this is expected until Google reviews
the `drive.file` scope for production release; test users bypass it entirely.

If connecting fails immediately, double check:

- The SHA-1 used for the OAuth client matches the keystore that signed the installed APK.
- The Drive API is enabled on the same Cloud project as the OAuth client.
- `drive.file` is listed under the consent screen's scopes.
