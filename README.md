```
  _____ _                 _        __  __
 / ____(_)               | |      |  \/  |
| (___  _ _ __ ___  _ __ | | ___  | \  / | __ _  ___ _ __ ___
 \___ \| | '_ ` _ \| '_ \| |/ _ \ | |\/| |/ _` |/ __| '__/ _ \
 ____) | | | | | | | |_) | |  __/ | |  | | (_| | (__| | | (_) |
|_____/|_|_| |_| |_| .__/|_|\___| |_|  |_|\__,_|\___|_|  \___/
  T r a c k i n g  | |
                   |_|
```

**Open source. No account. No ads. Your data never leaves your phone.**

A lightweight Android app for tracking daily nutrition and weight — built for people who just want the basics.

---

## Features

- [+] **Daily Diary** — log meals, track calories & macros, navigate by date
- [+] **Barcode Scanner** — scan packaged food to pull nutrition data automatically
- [+] **AI Entry** — describe any food in plain English and let Gemini fill in the macros [you supply your own api key (you can generate one for free at https://developers.generativeai.google.dev/)]
- [+] **Voice Logging** — say "add chicken breast 150 grams" and it's logged
- [+] **Weight Tracker** — log weight, view a line chart, filter by time range
- [+] **Macro Progress Bars** — visual progress toward your daily goals
- [+] **CSV Export / Import** — your data, portable and yours
- [+] **Fully Private** — AES-256 encrypted storage, no accounts, no tracking, no internet required

---

## How to Get This on Your Phone

Head to the [Releases](../../releases) page, download the latest `.apk` file, and open it on your Android phone to install.

> You may need to allow installs from unknown sources under **Settings -> Install Unknown Apps**.
> Requires Android 8.0 or newer.

---

## Contributing / Adding Features

Feel free to fork this repo and add whatever features you want. Pull requests are welcome.

---

## Tech Stack

```
Language      Kotlin
Architecture  MVVM + StateFlow
DI            Hilt
Database      Room
Networking    Retrofit
Barcode       ZXing
Charts        MPAndroidChart
AI            Google Gemini API
Food Data     OpenFoodFacts
Security      EncryptedSharedPreferences (AES-256)
```

---

## License

Open source -- do whatever you want with it.

---

## Screenshots

---
Main Diary Screen

<img width="548" height="1051" alt="image" src="https://github.com/user-attachments/assets/e1628085-1304-4bef-8189-fbae3ac2c36b" />

---

New Food Entry into local DataBase

<img width="559" height="815" alt="image" src="https://github.com/user-attachments/assets/9fdb264c-e3c8-4cd8-a2e9-5b3dc8eda213" />

---

Weight Logging

<img width="555" height="1093" alt="image" src="https://github.com/user-attachments/assets/688baa85-767b-405e-83c0-e0876f9e4f4c" />

---

Settings

<img width="565" height="1060" alt="image" src="https://github.com/user-attachments/assets/a7966402-78a1-40d8-a5a1-a3205ac5dc51" />

---
AI

<img width="563" height="400" alt="image" src="https://github.com/user-attachments/assets/89c370c8-5f95-4694-8ed1-72932a9da706" />





