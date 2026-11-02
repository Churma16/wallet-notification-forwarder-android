# Wallet Forwarder (Android Companion App)

Ultra-lightweight, 100% private, and battery-friendly Android companion application for **Wallet MCP**. It captures status bar notifications from banking apps, e-wallets, and commerce platforms in real-time and securely forwards them to your self-hosted `wallet-mcp` webhook receiver.

[![Build Android APK](https://github.com/Churma16/wallet-notification-forwarder-android/actions/workflows/build-apk.yml/badge.svg)](https://github.com/Churma16/wallet-notification-forwarder-android/actions/workflows/build-apk.yml)

---

## Why This App?
Third-party automation apps (like MacroDroid or Tasker) introduce external app coupling, background battery restrictions, and feature paywalls. **Wallet Forwarder** provides:
- **100% Privacy & Ownership**: Transaction notifications go directly from your phone to your self-hosted VPS endpoint without intermediate cloud services or third-party tracking.
- **Ultra-Lightweight**: Minimal native Kotlin app (~2 MB APK) with negligible memory footprint and near-zero battery impact.
- **Reliable Background Execution**: Native `NotificationListenerService` lifecycle management with battery optimization whitelist guidance.

---

## Architecture & Data Flow

```
[Bank / E-Wallet Notification]
            │
            ▼
[Android NotificationListenerService]
            │
            ├─► Non-ongoing filter (`!sbn.isOngoing`)
            ├─► Package whitelist check (Jago, Mandiri, BCA, GoPay, etc.)
            │
            ▼
[OkHttp Client (TLS 1.3 / Bearer Auth)]
            │  POST /webhook/notification
            ▼
[wallet-mcp Webhook Server on VPS]
            │
            ├─► Gate 1 & 2 Rule Evaluation
            ├─► Dual-Channel Bank Correlation (Email + Push)
            │
            ▼
[BudgetBakers Wallet & Telegram Confirmation]
```

---

## Supported Apps & Whitelist

| Institution / Platform | Package Name | Category |
| :--- | :--- | :--- |
| **Bank Jago** | `com.jago.digitalbanking` | Bank |
| **Livin' by Mandiri** | `id.bmri.livin`, `id.co.bankmandiri` | Bank |
| **BCA / myBCA / BCA mobile** | `com.bca`, `com.bca.mobile`, `com.bca.mybca.omni.android` | Bank |
| **BRI (BRImo)** | `id.co.bri.brimo` | Bank |
| **BNI (BNI Mobile / wondr / TapCash)** | `id.bni.wondr`, `id.co.bni.tapcashgo`, `com.bni` | Bank |
| **Blu by BCA Digital** | `com.bcadigital.blu` | Bank |
| **Jenius** | `com.btpn.jenius.dc`, `com.btpn.jenius` | Bank |
| **SeaBank** | `com.seabank.seabankid`, `com.seabank.id` | Bank |
| **CIMB Niaga (OCTO Mobile)** | `id.co.cimbniaga.mobile.android`, `id.co.cimbniaga.octomobile` | Bank |
| **Flip** | `id.flip` | Bank |
| **Gojek / GoPay** | `com.gojek.app`, `com.gojek.gopay` | E-Wallet |
| **Shopee / ShopeePay** | `com.shopeepay.id`, `com.shopee.id` | E-Wallet |
| **DANA** | `id.dana` | E-Wallet |
| **OVO** | `ovo.id` | E-Wallet |
| **LinkAja** | `com.telkom.mwallet` | E-Wallet |
| **Tokopedia** | `com.tokopedia.tkpd` | Merchant |
| **Blibli** | `blibli.mobile.commerce` | Merchant |
| **TikTok / TikTok Shop** | `com.zhiliaoapp.musically`, `com.ss.android.ugc.trill` | Merchant |
| **Grab** | `com.grabtaxi.passenger` | Merchant |

---

## Getting Started

### 1. Download & Install APK
1. Go to the [Releases page](https://github.com/Churma16/wallet-notification-forwarder-android/releases) or the [GitHub Actions tab](https://github.com/Churma16/wallet-notification-forwarder-android/actions).
2. Download the latest `wallet-forwarder-v*.apk` release package.
3. Install the APK on your Android device (Android 8.0+ / API 26+).

### 2. Configure Permissions & Endpoint
1. Open **Wallet Forwarder**.
2. **Grant Notification Access**: Tap **Grant** next to *Notification Access* and enable the toggle for Wallet Forwarder in Android system settings.
3. **Allow Background Execution**: Tap **Exempt** to disable battery optimization for this app (ensuring notifications are received even when your phone is locked).
4. **Enter Webhook Details**:
   - **Webhook URL**: Your VPS endpoint, e.g. `https://your-domain.com/webhook/notification` or `http://vps-ip:3000/webhook/notification`.
   - **Bearer Secret Token**: The token configured in `NOTIFICATION_WEBHOOK_SECRET` on your VPS.
5. Tap **Save Configuration**.
6. Tap **Test Ping Webhook** to send a test payload and confirm that your server returns `200 OK`.

---

## Release & Versioning Process

This repository enforces automated versioning and changelog management via GitHub Actions:

1. **Development & PRs**:
   - Do **NOT** modify `versionCode` or `versionName` in `app/build.gradle.kts` within feature or bugfix PRs.
   - Use [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `chore:`, `perf:`, etc.).
   - CI executes `Verify No Manual Version Bumps in PR` to ensure version fields remain decoupled from daily development branches.

2. **Official Releases**:
   - Trigger the **Manual Release** workflow on GitHub Actions.
   - The workflow analyzes git commit history since the last release tag:
     - `fix:` commits trigger a **patch** bump (e.g. `1.0.7` -> `1.0.8`).
     - `feat:` commits trigger a **minor** bump (e.g. `1.0.7` -> `1.1.0`).
     - `BREAKING CHANGE:` or `!` triggers a **major** bump.
   - `versionCode` is monotonically incremented (+1), `CHANGELOG.md` is appended, release notes are compiled, signed APKs are built, and an official GitHub Release is published with an AI-generated descriptive title.

3. **Snapshot Builds**:
   - Every merge to `main` automatically triggers the **Snapshot Pre-Release** workflow, compiling and publishing a preview APK tagged `v<version>-snapshot.<sha>` for testing.

---

## Tech Stack
- **Language**: Kotlin 1.9.23
- **Platform**: Android SDK 34 (Min SDK 26)
- **Networking**: OkHttp 4.12.0
- **Serialization**: Gson 2.10.1
- **Concurrency**: Kotlin Coroutines
- **CI/CD**: GitHub Actions (`gradle/actions/setup-gradle@v3`)

---

## License
MIT License. Made for [Wallet MCP BudgetBakers Bot](https://github.com/Churma16/wallet-mcp-budgetbakers-bot).
