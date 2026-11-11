/*
 * ESP32-S3 Camera MJPEG Server
 * ----------------------------
 * Serwuje:
 *   GET /capture  -> pojedyncza klatka JPEG (endpoint "Zrzut" w aplikacji)
 *   GET /stream   -> strumień MJPEG multipart/x-mixed-replace (endpoint "Połącz")
 *
 * Wymagany pakiet esp32-camera (wbudowany w rdzeń Arduino-ESP32).
 * Płytka: ESP32S3 Dev Module, PSRAM: OPI PSRAM (lub wg płytki).
 */

#include "esp_camera.h"
#include <WiFi.h>
#include <WebServer.h>

const char* ssid = "NazwaSieciWiFi";
const char* password = "HasloWiFi";

// ---- Piny dla Freenove ESP32-S3 WROOM CAM ----
// Dla XIAO ESP32S3 Sense podmien wg komentarzy w setup().
#define PWDN_GPIO_NUM   -1
#define RESET_GPIO_NUM  -1
#define XCLK_GPIO_NUM   15
#define SIOD_GPIO_NUM    4
#define SIOC_GPIO_NUM    5
#define Y9_GPIO_NUM     16
#define Y8_GPIO_NUM     17
#define Y7_GPIO_NUM     18
#define Y6_GPIO_NUM     12
#define Y5_GPIO_NUM     10
#define Y4_GPIO_NUM      8
#define Y3_GPIO_NUM      9
#define Y2_GPIO_NUM     11
#define VSYNC_GPIO_NUM   6
#define HREF_GPIO_NUM    7
#define PCLK_GPIO_NUM   13

WebServer server(80);

void handleCapture() {
  camera_fb_t* fb = esp_camera_fb_get();
  if (!fb) {
    server.send(500, "text/plain", "Blad pobierania klatki");
    return;
  }
  server.send_P(200, "image/jpeg", (const char*)fb->buf, fb->len);
  esp_camera_fb_return(fb);
}

void handleStream() {
  WiFiClient client = server.client();
  server.setContentLength(CONTENT_LENGTH_UNKNOWN);
  server.send(200, "multipart/x-mixed-replace; boundary=frame", "");

  while (client.connected()) {
    camera_fb_t* fb = esp_camera_fb_get();
    if (!fb) break;
    client.printf("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: %u\r\n\r\n", fb->len);
    client.write(fb->buf, fb->len);
    client.print("\r\n");
    esp_camera_fb_return(fb);
    delay(50); // ~20 FPS, zwieksz dla stabilniejszego lacza
  }
}

void handleRoot() {
  String page = "<h2>ESP32-S3 Camera</h2>"
                "<p>Strumien: <a href='/stream'>/stream</a></p>"
                "<p>Zrzut: <a href='/capture'>/capture</a></p>";
  server.send(200, "text/html", page);
}

void setup() {
  Serial.begin(115200);

  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  config.fb_location = CAMERA_FB_IN_PSRAM;
  config.jpeg_quality = 12;
  config.fb_count = 2;
  config.grab_mode = CAMERA_GRAB_LATEST;

  // XIAO ESP32S3 Sense: XCLK=10, SIOD=40, SIOC=39, Y9=48, Y8=11, Y7=12,
  // Y6=14, Y5=16, Y4=18, Y3=17, Y2=15, VSYNC=38, HREF=47, PCLK=13

  if (!esp_camera_init(&config)) {
    Serial.println("Kamera OK");
  } else {
    Serial.println("Blad inicjalizacji kamery!");
    while (true) delay(1000);
  }

  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();
  Serial.print("Adres IP ESP32: http://");
  Serial.println(WiFi.localIP());

  server.on("/", handleRoot);
  server.on("/capture", HTTP_GET, handleCapture);
  server.on("/stream", HTTP_GET, handleStream);
  server.begin();
}

void loop() {
  server.handleClient();
}
