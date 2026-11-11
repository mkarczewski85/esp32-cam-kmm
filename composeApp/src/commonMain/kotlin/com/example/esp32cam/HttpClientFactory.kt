package com.example.esp32cam

import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
