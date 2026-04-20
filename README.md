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

Feel free to fork this repo and do whatever you want. Pull requests are also welcome.

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

<table>
  <tr>
    <td align="center"><b>Daily Diary</b></td>
    <td align="center"><b>New Food Entry</b></td>
    <td align="center"><b>Weight Logging</b></td>
  </tr>
  <tr>
    <td><img width="220" src="https://github.com/user-attachments/assets/e1628085-1304-4bef-8189-fbae3ac2c36b" /></td>
    <td><img width="220" src="https://github.com/user-attachments/assets/9fdb264c-e3c8-4cd8-a2e9-5b3dc8eda213" /></td>
    <td><img width="220" src="https://github.com/user-attachments/assets/688baa85-767b-405e-83c0-e0876f9e4f4c" /></td>
  </tr>
  <tr>
    <td align="center"><b>Settings</b></td>
    <td align="center"><b>AI Entry</b></td>
    <td></td>
  </tr>
  <tr>
    <td><img width="220" src="https://github.com/user-attachments/assets/a7966402-78a1-40d8-a5a1-a3205ac5dc51" /></td>
    <td><img width="220" src="https://github.com/user-attachments/assets/89c370c8-5f95-4694-8ed1-72932a9da706" /></td>
    <td></td>
  </tr>
</table>





