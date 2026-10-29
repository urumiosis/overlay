# Monika Supabase Android demo — 2 APKs

An Android Studio Java project with Admin and Receiver APKs. **Supabase is already configured in source** using the public Project URL and publishable API key supplied for this project. No Firebase configuration files, IP addresses or additional VPN are required.

## What the effect does

- Admin: SIGN IN, CREATE ACCOUNT, SHOW MONIKA, HIDE MONIKA.
- Receiver: SIGN IN, grant "Display over other apps", press START RECEIVER. A persistent notification lets you STOP the receiver whenever needed.
- Admin and Receiver can be on separate Wi-Fi networks/mobile data.
- A new SHOW command makes the user's transparent character PNG appear over apps and plays an original chime. The visible overlay absorbs touches on the underlying app area. Android's system controls continue to work.
- One press of the power button turns the screen off and removes the overlay. It **will not replay when the screen wakes**. A new SHOW press is required.
- The Receiver checks Supabase every 3 seconds using HTTPS; it uses **polling, not Realtime WebSockets**. Android may pause/stop foreground services under battery policies. No remote trigger is guaranteed while the Receiver is stopped/offline.

**Only install/enable on a device you own or whose user explicitly agrees.** There is no hidden enrollment, startup-on-boot, Accessibility Service, device admin, or device lock. An on-image exit button is not drawn, but system navigation and a persistent Stop control remain available.

## Already configured

- Supabase URL: `https://ieornugblzskjyhlifvk.supabase.co`
- Public client key: in `common/src/main/java/dev/project/monika/shared/SupabaseConfig.java`
- The publishable key is safe in mobile app source; **never replace it with a secret/service-role key**.
- Attached Monika artwork: `receiver/src/main/res/drawable-nodpi/monika.png` (960x960, transparent)
- Original selection chime: `receiver/src/main/res/raw/selection_chime.wav`

## Supabase: do Steps 1–5 first (if not already done)

1. Open https://supabase.com/dashboard and select this project.
2. Authentication > Providers > Email: enable Email. Leave Email confirmation on; your created account will need to verify its email before sign-in.
3. SQL Editor > New query: paste the contents of `supabase-setup.sql`, click **Run**. This creates `public.monika_commands` with secure per-user Row Level Security policies.
4. Table Editor > public > `monika_commands`: confirm the table exists. Do not add rows manually or disable RLS.
5. Supabase Realtime **does not need to be enabled** for this implementation because the Receiver checks the command row over HTTPS.

## Step 6 — Open the updated source in Android Studio (recommended)

1. Install Android Studio from https://developer.android.com/studio on a Windows/Mac/Linux computer.
2. Extract the `MonikaOverlaySupabase_Project.zip`. If using the one-file Python setup, execute `python monika_supabase_one_file_setup.py` to recreate this folder. Python alone **cannot** compile APKs.
3. Android Studio > **Open** > select the extracted `MonikaOverlaySupabase` folder (the one containing `settings.gradle.kts`), not `admin` or `receiver` by itself.
4. Wait for Gradle Sync. If asked to install Android SDK Platform 35, install it. Use JDK 17 (Android Studio's bundled JDK works).
5. If Gradle Sync fails, check the **Build** output and that internet is available to download Google Android Gradle Plugin `8.9.2` and Gradle `8.11.1`. The project does not ship a Gradle wrapper; Android Studio normally offers Gradle configuration. For fully automated builds use GitHub Actions below.

## Step 7 — Build APKs locally

1. Android Studio > **Build > Build Bundle(s) / APK(s) > Build APK(s)** (exact label varies), choose Admin module; repeat for Receiver module, OR use Gradle task `:admin:assembleDebug :receiver:assembleDebug`.
2. APK paths:
   - `admin/build/outputs/apk/debug/admin-debug.apk`
   - `receiver/build/outputs/apk/debug/receiver-debug.apk`
3. These are unsigned-for-distribution but **debug-signed** APKs, installable for testing. You may need Android's "Install unknown apps" permission on your test phones.

### Alternative: GitHub Actions build (no Android Studio needed on your computer)

1. Create a **private** GitHub repository.
2. Upload **the contents of the extracted project folder**, including `.github/workflows/build-apks.yml`, to the root of that repository. The root should contain `settings.gradle.kts`, `admin`, `receiver`, and `common`.
3. Open GitHub repository > **Actions** > **Build Monika Supabase APKs** > **Run workflow**. A push to `main` also triggers it.
4. Open the finished workflow run, locate Artifacts > **Monika-Supabase-Admin-and-Receiver** and download the ZIP containing both debug APKs.

## Step 8 — Install and pair

1. Install `admin-debug.apk` on Phone A and `receiver-debug.apk` on Phone B.
2. Open **Admin**. Tap CREATE ACCOUNT with a dedicated demo email and password, then click the verification email (if configured). Return and tap SIGN IN.
3. On **Receiver**, sign in with **the same** demo account. Tap GRANT OVERLAY PERMISSION; in Android settings allow **Display over other apps** for Monika Receiver.
4. Back in Receiver, allow notifications when prompted, then tap **START RECEIVER**. Its notification should say **Connected; listening for demo commands.**
5. Connect Phone A to Wi-Fi and Phone B to another Wi-Fi/mobile data network.
6. On Admin press **SHOW MONIKA**. Within about 3 seconds, the transparent PNG should appear with a chime. Press power once to turn Phone B's display off. On wake the image should be gone.
7. The notification's **Stop receiver** or Receiver > STOP RECEIVER disables listening. Admin HIDE MONIKA also clears a visible effect.

## Troubleshooting

- `Supabase HTTP 404` or `relation public.monika_commands does not exist`: Run `supabase-setup.sql` in **this exact Supabase project**.
- `Supabase HTTP 401`, `Invalid login credentials`, `Email not confirmed`: Confirm email, ensure both phones use the same account, then sign in again.
- `Supabase HTTP 403` / row-level security: Make sure you ran the full SQL and are signed in (do not disable RLS to solve it).
- START Receiver does nothing: Enable overlay and notification permissions; check battery optimization restrictions and Android's notification shade.
- SHOW was sent before Receiver was ready: Wait for Receiver notification to say **Connected** before pressing again. The first command seen at service startup is intentionally ignored to prevent stale command replay.
- Screen off does not clear: Confirm `Monika Receiver` foreground service is active. Android vendor-specific battery policies may interfere.
- No sound: Verify Phone B's media volume. The bundled sound is an original short chime, not the game's copyrighted recording.
- `gradle` command missing: Use Android Studio or the included GitHub Actions workflow; the Python setup only unpacks and optionally invokes installed Android build tools.

## Implementation notes

`common/src/main/java/dev/project/monika/shared/SupabaseClient.java` implements `/auth/v1/signup`, `/auth/v1/token?grant_type=password`, refresh-token flow, and `/rest/v1/monika_commands` authenticated upserts/reads. No service secret is bundled. Receiver polls 3 seconds, uses an Android overlay and foreground service. It consumes the existing command on initial start instead of executing it. On every detected new UUID, it dispatches SHOW or HIDE. `ACTION_SCREEN_OFF` removes the overlay locally without issuing a new cloud command. The next new UUID is required for it to appear again.

The source was reviewed and package-tested in this environment, but **actual APK compilation and device tests require an Android SDK/real device**. Do not treat the source as a guaranteed compiled, tested release.
