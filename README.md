# SMS Demon 📨

An Android application that sends SMS messages automatically at a configurable interval while running as a persistent Foreground Service.

---

## Features

| Feature | Detail |
|---|---|
| **Periodic SMS** | Sends one SMS every N minutes (default 5), configurable at runtime |
| **SMS Templates** | Supports `{random}`, `{timestamp}`, `{counter}` placeholders |
| **Secure Random** | Uses Java `SecureRandom` for the `{random}` 6-digit value |
| **Foreground Service** | Survives app minimisation; shown as a persistent notification |
| **Boot Receiver** | Automatically restarts the service after device reboot |
| **Send Log** | Room-backed, reactive log screen showing each send attempt |
| **Material Design** | Full Material Components UI with light/dark theme support |
| **MVVM + Coroutines** | Clean architecture with ViewModel, LiveData, Flow, and Coroutines |

---

## Screenshots

_Build the project and run on a device or emulator to see the UI._

---

## Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android device / emulator running **Android 10 (API 29)+**

---

## Build Instructions

### 1. Clone the repository

```bash
git clone https://github.com/your-org/sms-demon.git
cd sms-demon
```

### 2. Open in Android Studio

`File → Open` → select the `sms-demon` folder.  
Android Studio will sync Gradle automatically.

### 3. Build from the command line

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (requires a signing config)
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run lint
./gradlew lint
```

The debug APK will be at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### 4. Install on a connected device

```bash
./gradlew installDebug
```

---

## Installation (sideloading the APK)

1. Enable **Settings → Developer options → Install unknown apps** for your file manager.
2. Copy `app-debug.apk` to the device.
3. Open the APK and follow the on-screen installer.
4. Grant **Send SMS** permission when prompted on first launch.

---

## Permissions

| Permission | Why |
|---|---|
| `SEND_SMS` | Required to send SMS messages |
| `RECEIVE_BOOT_COMPLETED` | Restart the service after reboot |
| `FOREGROUND_SERVICE` | Run the sender as a foreground service |
| `POST_NOTIFICATIONS` | Show the persistent notification (Android 13+) |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Required on Android 14+ for this FGS type |

---

## SMS Template Placeholders

| Placeholder | Resolved to |
|---|---|
| `{random}` | Cryptographically random 6-digit number |
| `{timestamp}` | Current date-time: `yyyy-MM-dd HH:mm:ss` |
| `{counter}` | Monotonically incrementing send count |

**Example template:**
```
OTP Test ID:{random} Time:{timestamp} Count:{counter}
```

**Resolved example:**
```
OTP Test ID:847291 Time:2024-07-15 14:32:05 Count:3
```

---

## Project Structure

```
app/src/main/java/com/smsdemon/
├── model/
│   ├── SmsLog.kt            # Room entity for send log entries
│   └── ServiceState.kt      # Sealed class: Running / Stopped
├── repository/
│   ├── AppDatabase.kt       # Room database singleton
│   ├── SmsLogDao.kt         # DAO with Flow queries
│   └── SmsRepository.kt     # SMS sending + log persistence
├── service/
│   ├── SmsSenderService.kt  # Foreground service — the core loop
│   └── BootReceiver.kt      # Restarts service after reboot
├── ui/
│   ├── MainActivity.kt      # Main screen
│   ├── MainViewModel.kt     # VM for main screen
│   ├── LogActivity.kt       # Send log screen
│   ├── LogViewModel.kt      # VM for log screen
│   └── LogAdapter.kt        # RecyclerView adapter
└── util/
    ├── Constants.kt          # App-wide string constants
    ├── NotificationHelper.kt # Notification channel + builder
    ├── PhoneValidator.kt     # Phone number validation
    └── TemplateResolver.kt  # Placeholder substitution
```

---

## CI / GitHub Actions

Every push and pull-request to `main` or `develop` triggers:

1. Checkout
2. JDK 17 setup with Gradle cache
3. `./gradlew lint`
4. `./gradlew test`
5. `./gradlew assembleDebug`
6. Upload APK, lint report, and test report as artifacts

See `.github/workflows/android.yml`.

---

## Architecture

```
UI (Activity + ViewModel)
        │  LiveData / StateFlow
        ▼
  Repository  ←──────── SmsRepository
        │  suspend fun / Flow
        ▼
  Room Database (SmsLogDao / AppDatabase)

  SmsSenderService  ──uses──▶  SmsRepository
        │  Coroutine loop
        ▼
  SmsManager (Android OS)
```

---

## License

[MIT](LICENSE)
