package com.example.esp32cam

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createHttpClient(): HttpClient = HttpClient(Darwin) {
    configureRequest {
        timeoutInterval = 300.0
    }
}
