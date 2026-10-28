# ECS Surveillance Robot Controller

An Android application for controlling and monitoring an ESP32-based surveillance robot over a local Wi-Fi network. The app provides a control interface for robot movement, a live camera-stream view, an infrared (IR) control, connection status, and a recording-status timer interface.

> **Project note:** The recording screen in this source currently manages a recording timer/status UI; it does not itself implement video capture or save a recording. The robot firmware must provide the HTTP endpoints described below.

## Features

- **Robot movement controls:** Forward, backward, left, right, and stop.
- **Live camera view:** Displays the ESP32 camera's MJPEG stream.
- **IR control:** Sends commands to switch the IR function on or off, when supported by the robot firmware.
- **Connection status:** Checks whether the robot's control endpoint is reachable.
- **Robot IP configuration:** Enter and save the robot's network address in the app.
- **Recording-status interface:** Start/stop timer and destination selection UI (not actual video recording in the current source).
- **Android UI:** Native Java application with Android resource-based layouts.

## Technology

- Android application written in **Java**
- Android Studio and Gradle Kotlin DSL build files
- AndroidX Activity and AppCompat
- Material Components
- HTTP communication with the ESP32 controller

## Requirements

- Android Studio compatible with the project's Android Gradle Plugin and Gradle versions
- Android SDK installed through Android Studio
- Java 11 language compatibility is configured in the app module; use the JDK recommended by your installed Android Gradle Plugin/Android Studio version
- An Android phone running **Android 7.0 (API 24) or later**
- Compatible ESP32/ESP32-CAM robot firmware connected to the same local network as the phone

## Open and run the project

1. Download or clone this repository.
2. Open the project root (`ECSRobotController`) in Android Studio. Select the folder containing `settings.gradle.kts`.
3. Allow Android Studio to sync Gradle and install any required Android SDK components.
4. Connect an Android phone with USB debugging enabled, or start an Android emulator.
5. Run the `app` configuration.
6. Connect the phone to the Wi-Fi network used by the ESP32 robot.
7. In the app, open the robot IP/network settings and enter the ESP32's reachable IP address. Save/connect, then open the control screen.

**Important:** The address `192.168.137.251` in the source is a default value from the development setup, not a universal ESP32 address. Use the IP address assigned to your own ESP32. Both the phone and ESP32 must be able to reach each other on the network.

## Expected ESP32 HTTP interface

The Android app expects the robot firmware to expose these HTTP routes:

| Purpose | Port | Request |
|---|---:|---|
| Connection check | `81` | `GET /status` (expects HTTP 200) |
| Movement/stop commands | `81` | `GET /command?move=FORWARD` |
| Movement/stop commands | `81` | `GET /command?move=BACKWARD` |
| Movement/stop commands | `81` | `GET /command?move=LEFT` |
| Movement/stop commands | `81` | `GET /command?move=RIGHT` |
| Movement/stop commands | `81` | `GET /command?move=STOP` |
| Camera stream | `80` | `GET /stream` (MJPEG stream) |
| IR on/off | `80` | `GET /ir/on` and `GET /ir/off` |

These routes must match the firmware running on your robot. The Android project alone does not include the ESP32 firmware.

## Repository structure

```text
ECSRobotController/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/sathwik/ecsrobotcontroller/
│   │   │   ├── MainActivity.java
│   │   │   ├── RobotCommand.java
│   │   │   ├── RobotNetworkManager.java
│   │   │   └── CameraManager.java
│   │   └── res/                 # Layouts, icons, colours, and themes
│   └── build.gradle.kts
├── gradle/                      # Gradle wrapper and version catalog
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Network and security notes

- This project uses plain HTTP for local communication with the ESP32 (`usesCleartextTraffic="true"`). Use it only on a trusted network and do not expose the control endpoints directly to the public internet.
- The app needs the Android `INTERNET` permission.
- Do not commit passwords, access tokens, private signing keys, or personal machine configuration files. The local Android SDK path (`local.properties`) is intentionally excluded from this repository.

## Limitations

- The ESP32 firmware is maintained separately and is not included here.
- Camera, movement, and IR functions depend on the firmware implementing the expected routes.
- The recording screen currently shows a timer and a selected destination label; actual video recording/storage needs to be implemented separately.
- No release APK is included. Build and install the app from Android Studio.

## Credits / project context

This app is the Android controller interface for the ECS surveillance robot prototype. Add contributor names and a demonstration-video link here if all contributors agree and the video is publicly accessible.

## License

No license has been selected for this repository yet. Unless you add a license, others can view the public source on GitHub but do not automatically receive permission to reuse, modify, or redistribute it. Choose a license before inviting others to reuse the code.
