# ESP32-S3 Camera — aplikacja Kotlin Multiplatform + Compose

Aplikacja KMM (Compose Multiplatform) na **Androida i iOS**, która odczytuje i wyświetla
obraz z kamery podpiętej do ESP32-S3 przez Wi-Fi (strumień MJPEG po HTTP).

## Jak to działa

ESP32-S3 z kamerą (np. OV2640) uruchamia serwer HTTP udostępniający:

| Endpoint   | Opis                                   |
|------------|----------------------------------------|
| `/stream`  | strumień MJPEG (`multipart/x-mixed-replace`) |
| `/capture` | pojedyncza klatka JPEG                 |

Aplikacja łączy się z wybranym adresem (np. `http://192.168.1.100:81`), parsuje strumień
MJPEG w kodzie współdzielonym (`MjpegParser` — wykrywanie markerów SOI/EOI w JPEG),
dekoduje klatki przez Skia (`Image.makeFromEncoded`) i wyświetla w Compose wraz z FPS i statusem.

## Struktura

```
composeApp/
  src/commonMain/kotlin/com/example/esp32cam/
    App.kt                          # UI Compose (ekran kamery)
    CameraViewModel.kt              # ViewModel (StateFlow)
    HttpClientFactory.kt            # expect — fabryka Ktor HttpClient
    camera/Esp32CameraClient.kt     # klient /stream i /capture, licznik FPS
    camera/MjpegParser.kt           # parser strumienia MJPEG
  src/androidMain/                  # MainActivity + engine OkHttp
  src/iosMain/                      # MainViewController + engine Darwin
esp32-firmware/CameraStream/        # przykładowy szkic Arduino dla ESP32-S3
```

## Uruchomienie firmware

1. Zainstaluj rdzeń **esp32** w Arduino IDE (Boards Manager) — zawiera bibliotekę `esp_camera`.
2. Otwórz `esp32-firmware/CameraStream/CameraStream.ino`, ustaw SSID/hasło Wi-Fi
   oraz piny kamery dla swojej płytki (domyślnie Freenove ESP32-S3 WROOM CAM,
   w komentarzu gotowe wartości dla XIAO ESP32S3 Sense).
3. Płytka: **ESP32S3 Dev Module**, włącz PSRAM. Wgraj szkic i odczytaj adres IP z Serial Monitora.

Zamiast własnego firmware można użyć gotowego przykładu **CameraWebServer**
(File → Examples → ESP32 → Camera → CameraWebServer) — wtedy w aplikacji podaj
adres strumienia, np. `http://<ip-esp32>:81`.

## Uruchomienie aplikacji

### Android
```bash
./gradlew :composeApp:installDebug
```
Wymagany Android SDK (API 35) i JDK 17. Aplikacja korzysta z `http://`, więc
w manifeście ustawione jest `usesCleartextTraffic="true"`.

### iOS
Cel iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`) jest skonfigurowany i eksportuje
framework `ComposeApp`. Najprościej wygenerować projekt Xcode kreatorem
Kotlin Multiplatform (https://kmp.jetbrains.com) i podmienić katalog źródłowy
`composeApp/src`, albo podpiąć framework wg
https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-ios-dependencies.html.
Kompilacja samego kodu iOS: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`.

## Użycie

1. Telefon i ESP32 muszą być w tej samej sieci Wi-Fi.
2. Wpisz adres, np. `http://192.168.1.100:81` (port wg firmware).
3. **Połącz** — podgląd na żywo (MJPEG), **Zrzut** — pojedyncza klatka z `/capture`,
   **Rozłącz** — zamknięcie strumienia.
