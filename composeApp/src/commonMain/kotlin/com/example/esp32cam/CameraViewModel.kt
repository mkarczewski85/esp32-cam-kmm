package com.example.esp32cam

import androidx.compose.ui.graphics.ImageBitmap
import com.example.esp32cam.camera.CameraStatus
import com.example.esp32cam.camera.Esp32CameraClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.StateFlow

class CameraViewModel(httpClient: HttpClient = createHttpClient()) {

    private val client = Esp32CameraClient(httpClient)

    val frame: StateFlow<ImageBitmap?> = client.frame
    val status: StateFlow<CameraStatus> = client.status
    val errorMessage: StateFlow<String?> = client.errorMessage
    val fps: StateFlow<Float> = client.fps

    fun connect(url: String) = client.startStream(url)

    fun disconnect() = client.stop()

    suspend fun capture(url: String): ImageBitmap? = client.captureSnapshot(url)
}
