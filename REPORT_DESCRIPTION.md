# Report Text: ECS Surveillance Robot Controller

## Project description

The ECS Surveillance Robot Controller is an Android application developed in Java to provide a mobile control interface for an ESP32-based surveillance robot. It allows an operator to send movement commands, view the robot's camera stream, control the infrared function when supported by the firmware, and monitor the robot's network connection status. The robot's IP address can be configured from the application and saved for later sessions.

## Implementation overview

The application is organised into three principal Java components. `MainActivity` manages the user interface and screen navigation. `RobotNetworkManager` sends HTTP requests for movement and IR actions and periodically checks the control server's status. `CameraManager` connects to the camera stream and displays incoming MJPEG video in the app. The Android manifest declares internet access, and the project uses Gradle for dependency and build configuration.

## Communication interface

The app expects the ESP32 firmware to provide a camera stream and IR routes on port 80, and a status endpoint plus movement-command route on port 81. Therefore, successful operation requires the Android phone and robot to be connected to a network that allows communication between them, and the firmware endpoints must match the routes expected by the app.

## Current limitation

The current recording screen implements a recording-status timer and destination selection interface. It does not yet implement actual video recording or saving footage. The ESP32 firmware is also maintained separately from this Android application.

## Repository availability

The source code, Android resources, Gradle configuration, and setup instructions are organised in a public GitHub repository so reviewers can inspect the implementation and reproduce the Android build. A public repository URL should be inserted into the project report after the repository has been created and verified as public.
