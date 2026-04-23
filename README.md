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
**I hate MyFitnessPal, I hate ads, I hate pay-walling brain dead features (barcode scanner)**

**Open source. No account. No ads. Your data never leaves your phone.**

A lightweight Android app for tracking daily nutrition and weight — built for people who just want the basics.  Plus AI to fill in food diary gaps

---


## Features

- [+] **Daily Diary** — log meals, track calories & macros, navigate by date
- [+] **Barcode Scanner** — scan packaged food to pull nutrition data automatically
- [+] **AI Entry** — describe any food in plain English [BYOK, gemeni and Github Models supported]
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


## Screenshots

<table>
  <tr>
    <td align="center"><b>Daily Diary</b></td>
    <td align="center"><b>New Food Entry</b></td>
    <td align="center"><b>Weight Logging</b></td>
  </tr>
  <tr>
    <td><img width="420"  alt="Screenshot 2026-04-22 204617" src="https://github.com/user-attachments/assets/6b956bd1-f526-447d-aa84-2d3a4a7bd8eb" /></td>
    <td><img width="420" src="https://github.com/user-attachments/assets/9fdb264c-e3c8-4cd8-a2e9-5b3dc8eda213" /></td>
    <td><img width="420"  alt="weight" src="https://github.com/user-attachments/assets/afa5ac9b-4148-435c-bca7-548994b817b9" />
</td>
  </tr>
  <tr>
    <td align="center"><b>Goals</b></td>
    <td align="center"><b>AI Entry</b></td>
    <td align="center"><b>Github Models and Gemeni Support</b></td>
  </tr>
  <tr>
    <td><img width="420" alt="goals" src="https://github.com/user-attachments/assets/487adda5-6c1c-4a2d-8fd9-7068e76d4e84"/></td>
    <td><img width="420" src="https://github.com/user-attachments/assets/89c370c8-5f95-4694-8ed1-72932a9da706" /></td>
    <td><img width="420"  alt="waterfalled ai support" src="https://github.com/user-attachments/assets/d25928ad-401f-43c7-ab81-698048e4e9de" /></td>
  </tr>
</table>

---

## Contributing / Adding Features

Feel free to fork this repo and do whatever you want. Pull requests are also welcome.

---

## License

Open source -- do whatever you want with it.

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



