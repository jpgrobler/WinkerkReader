# WinkerkReader

WinkerkReader is an Android application designed for church administration and pastoral care. It helps pastors manage member information, track birthdays and events, and organise pastoral follow-up work – all from one place.

![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
![Android](https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)

---

## 📖 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Screenshots](#-screenshots)
- [Tech Stack](#️-tech-stack)
- [Prerequisites](#-prerequisites)
- [Installation & Build](#-installation--build)
- [Configuration](#️-configuration)
- [Permissions](#-permissions)
- [Project Structure](#-project-structure)
- [Contributing](#-contributing)
- [License](#-license)

---

## 📱 Overview

WinkerkReader is a practical tool for pastors and church administrators. It connects to a WinKerk‑compatible member database and provides:

- Member list with search, filter, and sorting capabilities
- Pastoral reminder system with templates for common follow‑up scenarios
- Pastoral notes with optional confidentiality and biometric protection
- Call monitoring and automatic logging (phone and VoIP)
- Birthday and event management with automated SMS reminders
- Multi‑congregation support (up to three congregations)
- Google Tasks integration via Apps Script
- Calendar synchronisation for reminders and calls
- Widgets for home screen and keyguard
- Automated backup and restore

---

## ✨ Features

### 👥 Member Management

- Full member list with search, filter, and sort (by family, address, age, surname, birthday, ward, wedding date)
- Member detail view with photo, contact details, and quick actions
- Inline editing of member information
- Family member display
- Milestone tracking (baptism, confession, marriage)

### 🙏 Pastoral Care

Follow‑up reminders with system templates:

- † Bereavement
- 🏥 Hospitalisation
- ⚠️ Trauma
- 💊 Illness
- 🤗 New member
- 💬 General follow‑up

Custom template management – create, edit, activate/deactivate

Pastoral notes with categories:

- 🏠 House visit · 📞 Phone call · 💬 WhatsApp
- ✉️ Email · ⛪ At church · 🙏 Prayer
- 🤝 Consultation · 📝 Other

- Confidential notes – biometric/PIN protection
- Google Tasks synchronisation (via your own Apps Script deployment)
- Calendar synchronisation (automatic or manual)
- Home screen widget showing active reminders

### 🎂 Birthdays & Events

- Upcoming birthdays, baptisms, weddings, and confessions
- Personalised SMS templates (`<<<naam>>>` is automatically replaced)
- Daily scheduled reminder time

### 🏛️ Multi‑Congregation

- Support for up to three congregations
- Each with its own name, colour, and email address
- Filter members by congregation

### ⚙️ Display & Preferences

- Light, dark, or system theme (Material You)
- Toggle which icons appear in the member list
- Choose default layout (Surname, Family, Ward, Age, Birthday, Address, Wedding)
- Congregation indicator style (background colour or photo ring)

### 📦 Backup & Restore

- Automatic backup of pastoral data (reminders, notes, templates)
- Optional call log backup (off by default for privacy)
- Daily scheduled backups (option to export to Downloads)
- Version‑aware restore
- Share backup file

### ☎️ Call Monitoring & Logging

- Automatic logging of phone calls
- VoIP calls (WhatsApp, Skype, Teams) – only contact name is logged (Android limitation)
- Floating overlay showing caller information
- Export call log to CSV

> ⚠️ VoIP call logging may be removed in future Android versions due to increasing privacy restrictions.

### 🖼️ Widgets

- **Birthday widget** – upcoming birthdays, baptisms, weddings, confessions, and memorial dates
- **Pastoral reminders widget** – lists all pending reminders with due dates
- **Keyguard widget** – shows pastoral reminders on the lock screen

---

## 📷 Screenshots

| Member List | Member Detail | Pastoral Reminders |
|-------------|---------------|-------------------|
| Coming soon | Coming soon   | Coming soon       |

---

## 🛠️ Tech Stack

| Component            | Technology                              |
|----------------------|-----------------------------------------|
| Language             | Kotlin                                  |
| UI                   | Material Design 3, ViewBinding, DataBinding |
| Architecture         | MVVM with Repository pattern            |
| Database             | Room (SQLite)                           |
| Paging               | Android Paging 3                        |
| Networking           | Retrofit, OkHttp                        |
| Image Loading        | Glide                                   |
| Dependency Injection | Manual DI (no Dagger/Hilt)             |
| Concurrency          | Kotlin Coroutines, Flow                 |
| Background Tasks     | WorkManager                             |
| Biometric            | AndroidX Biometric                      |
| Secure Storage       | AndroidX Security Crypto                |

---

## 📋 Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17 or later
- Android SDK (minimum API 28, target API 36)
- A WinKerk‑compatible SQLite database (`.sqlite` or `.db` file)

---

## 🔧 Installation & Build

### 1. Clone the repository

```bash
git clone https://github.com/yourusername/winkerkreader.git
cd winkerkreader
```

### 2. Open in Android Studio

Open the project in Android Studio and let it sync dependencies.

### 3. Configure signing (for release builds)

Create a `keystore.properties` file in the project root (or set environment variables):

```properties
RELEASE_STORE_FILE=/path/to/your/keystore.jks
RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_ALIAS=your_key_alias
RELEASE_KEY_PASSWORD=your_key_password
```

### 4. Build

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

The APK will be in `app/build/outputs/apk/`.

---

## ⚙️ Configuration

### First‑time Setup

1. **Grant permissions** – the app will request the necessary permissions on first launch.
2. **Import member database** – from the main menu (⋮ → ADMIN → Load New Database).
3. **Set congregation colours** – in Settings → Display.
4. **Set up birthday SMS templates** – in Admin → Send birthday SMS.
5. **Enable call monitoring** – in Settings → Functions.
6. **Set up pastoral reminders** – go to ❤️ Bediening and choose a template or create an ad‑hoc reminder.

For detailed setup instructions, refer to the *Kitsgids*.

### Database Import

WinkerkReader can import databases from:

- Local file (SAF file picker)
- Internet (Dropbox, Google Drive, OneDrive)
- WiFi from the WinkerkReader‑PC companion app
- USB from a connected PC

---

## 🔐 Permissions

The app requires the following permissions (each with a specific purpose):

| Permission | Purpose |
|---|---|
| `READ_CONTACTS` / `WRITE_CONTACTS` | Detect WhatsApp profiles; add members as contacts |
| `READ_PHONE_STATE` / `READ_CALL_LOG` | Caller identification and call logging |
| `SEND_SMS` / `READ_SMS` | Send birthday/event SMS messages |
| `READ_CALENDAR` / `WRITE_CALENDAR` | Log calls and reminders to calendar |
| `POST_NOTIFICATIONS` (Android 13+) | Display notification for pastoral reminders |
| `SYSTEM_ALERT_WINDOW` | Floating caller‑ID overlay during calls |
| `SCHEDULE_EXACT_ALARM` (Android 12+) | Schedule daily reminders at precise times |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Detect VoIP calls (WhatsApp, Skype) |

Without these permissions, the affected features will simply not work – the rest of the app remains usable.

---

## 📁 Project Structure

```
app/src/main/
├── kotlin/za/co/jpsoft/winkerkreader/
│   ├── data/                    # Data layer
│   │   ├── models/              # Data models
│   │   ├── room/                # Room entities and DAOs
│   │   ├── pastoral/            # Pastoral database and repositories
│   │   ├── calllog/             # Call log database
│   │   └── repositories/        # Data repositories
│   ├── ui/                      # UI layer
│   │   ├── activities/          # Activities
│   │   ├── fragments/           # Fragments
│   │   ├── adapters/            # RecyclerView adapters
│   │   ├── viewmodels/          # ViewModels
│   │   ├── bottomsheets/        # Bottom sheet dialogs
│   │   ├── controllers/         # UI controllers
│   │   └── helpers/             # UI helpers
│   ├── services/                # Background services
│   │   ├── receivers/           # Broadcast receivers
│   │   └── widgets/             # App widgets
│   ├── utils/                   # Utility classes
│   └── workers/                 # WorkManager workers
├── res/                         # Resources
└── AndroidManifest.xml          # App manifest
```

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Keep classes focused and cohesive

---

## 📄 License

This project is licensed under the **Apache License 2.0** – see the [LICENSE](LICENSE) file for details.

```
Copyright 2024 Pieter Grobler

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## 🙏 Acknowledgements

- [WinKerk](https://www.infokerk.co.za) – the church management system that provides the database format
- [Material Design](https://m3.material.io) – for the UI components and design system
- All open‑source libraries used in this project

---

## 📞 Contact

**Author:** Pieter Grobler
**Email:** 
**Phone:** 

---

> ⚠️ **Disclaimer:** WinkerkReader is not a product of INFOKERK / WinKerk. It does not modify any WinKerk data – it only reads from and displays it.
