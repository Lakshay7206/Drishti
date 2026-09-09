# 👁️ Drishti

**Drishti** is an AI-powered assistive navigation application built using **Kotlin Multiplatform (KMP)** to help visually impaired users navigate safely and independently. The app combines real-time object detection, obstacle awareness, GPS-based navigation, and voice guidance to make everyday travel more accessible.

---

## ✨ Features

### 🚧 Obstacle Detection

* Detects obstacles in real time using an on-device AI model.
* Alerts users through Text-to-Speech before potential collisions.
* Prioritizes important obstacles to reduce unnecessary audio.

### 🔍 Finding Mode

* Allows users to search for everyday objects.
* Uses AI-based object detection to locate the requested item.
* Provides voice instructions to guide the user toward the object.

### 🗣️ Voice Assistance

* Hands-free interaction through Text-to-Speech.
* Announces navigation instructions, detected obstacles, and found objects.

---

## 🛠️ Tech Stack

* **Kotlin Multiplatform (KMP)**
* **Jetpack Compose**
* **TensorFlow Lite (YOLO)**
* **Google Maps / Maps SDK**
* **Text-to-Speech (TTS)**
* **MVVM Architecture**
* **Coroutines & Flow**

---

## 🏗️ Architecture

Drishti follows the **MVVM (Model–View–ViewModel)** architecture to maintain a clean, scalable, and testable codebase.

```
UI (Compose)
      │
ViewModel
      │
Repository
      │
──────────────────────────────
AI Detection (YOLO)
Maps & Navigation
Text-to-Speech
Device Sensors
```

---

## 🎯 Goal

The goal of Drishti is to improve independence and confidence for visually impaired users by providing intelligent navigation assistance and contextual awareness through AI and voice guidance.

---

## 🚀 Future Improvements

* Indoor navigation using BLE beacons.
* OCR for reading signs and documents.
* Currency recognition.
* Emergency SOS with live location sharing.
* Wearable device integration.
* Multilingual voice support.

---

## 📄 License

This project is intended for educational and research purposes.
