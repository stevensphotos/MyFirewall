# MyFirewall

MyFirewall is a robust, open-source Android firewall and ad-blocker application. It provides users with granular control over their device's network traffic without requiring root access (though it offers enhanced features for rooted devices).

The app combines a local VPN interface for non-root traffic filtering with optional Root/Magisk integration for system-level ad blocking via the hosts file.

## 📱 Features

### 🛡️ Core Security
*   **No Root Required:** Uses Android's VpnService to intercept and filter traffic locally.
*   **Per-App Firewall:** Block internet access for specific applications (Wi-Fi, Mobile Data, or Roaming).
*   **DNS Filtering:** Block ads, trackers, and malware domains using blocklists.

### 🚀 Performance & Modes
*   **VPN Mode:** Filters traffic and DNS requests via a local VPN interface.
*   **Root Mode (Optional):** Integrates with Magisk Systemless Hosts for battery-efficient, system-wide ad blocking.
*   **Start on Boot:** Automatically enables protection when the device restarts.

### 🎨 User Interface
*   **Modern Design:** Built entirely with **Jetpack Compose** and Material 3.
*   **Theme Engine:** Supports Light Mode, Dark Mode, and a "Pure Black" OLED mode.
*   **Live Statistics:** Real-time dashboard showing blocked apps and active rules.
*   **Multi-Language Support:** Localized interface (English, French, Spanish, German, etc.).

### ⚙️ Advanced Tools
*   **Import/Export Rules:** Backup your firewall configuration to JSON.
*   **Scheduled Updates:** Automatically update blocklists via WorkManager (background tasks).
*   **Custom Rules:** Add your own domains to block or allow.

## 🛠️ Tech Stack

*   **Language:** [Kotlin](https://kotlinlang.org/)
*   **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
*   **Architecture:** MVVM (Model-View-ViewModel)
*   **Dependency Injection:** [Hilt](https://dagger.dev/hilt/)
*   **Database:** [Room](https://developer.android.com/training/data-storage/room) (SQLite)
*   **Background Tasks:** [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
*   **Network:** Android VpnService API
*   **Serialization:** Gson

## 📥 Installation

### Prerequisites
*   Android 8.0 (Oreo) or higher.
*   (Optional) Rooted device with Magisk for "Root Mode".

### Building from Source
1.  Clone the repository:
    