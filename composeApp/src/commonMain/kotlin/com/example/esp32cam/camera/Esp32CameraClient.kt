package com.example.esp32cam.camera

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.readBytes
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class Esp32CameraClient(private val httpClient: HttpClient) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _frame = MutableStateFlow<ImageBitmap?>(null)
    val frame: StateFlow<ImageBitmap?> = _frame.asStateFlow()

    private val _status = MutableStateFlow<CameraStatus>(CameraStatus.Disconnected)
    val status: StateFlow<CameraStatus> = _status.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps.asStateFlow()

    private var streamJob: Job? = null

    fun startStream(baseUrl: String) {
        val url = normalizeBaseUrl(baseUrl)
        stop()
        _errorMessage.value = null
        _status.value = CameraStatus.Connecting
        streamJob = scope.launch {
            try {
                httpClient.prepareGet("$url$STREAM_PATH").execute { response ->
                    if (!response.status.isSuccess()) {
                        error("Serwer odpowiedział HTTP ${response.status.value}")
                    }
                    _status.value = CameraStatus.Streaming
                    val channel = response.bodyAsChannel()
                    val parser = MjpegParser { bytes ->
                        decodeFrame(bytes)?.let { bitmap ->
                            _frame.value = bitmap
                            reportFrame()
                        }
                    }
                    val chunk = ByteArray(CHUNK_SIZE)
                    while (isActive && !channel.isClosedForRead) {
                        val read = channel.readAvailable(chunk, 0, chunk.size)
                        if (read == -1) break
                        if (read > 0) parser.feed(chunk, read)
                    }
                }
                if (_status.value != CameraStatus.Error) {
                    _status.value = CameraStatus.Disconnected
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _status.value = CameraStatus.Error
                _errorMessage.value = e.message ?: "Nie udało się połączyć z ESP32"
            }
        }
    }

    suspend fun stop() {
        streamJob?.cancelAndJoin()
        streamJob = null
        _status.value = CameraStatus.Disconnected
        _fps.value = 0f
    }

    suspend fun captureSnapshot(baseUrl: String): ImageBitmap? = withContext(Dispatchers.Default) {
        val url = normalizeBaseUrl(baseUrl)
        val response = httpClient.get("$url$CAPTURE_PATH")
        if (!response.status.isSuccess()) {
            _errorMessage.value = "Serwer odpowiedział HTTP ${response.status.value}"
            return@withContext null
        }
        decodeFrame(response.readBytes())
    }

    private fun decodeFrame(bytes: ByteArray): ImageBitmap? = try {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (_: Throwable) {
        null
    }

    private var windowStart: TimeSource.Monotonic.ValueTimeMark? = null
    private var framesInWindow = 0

    private fun reportFrame() {
        val now = TimeSource.Monotonic.markNow()
        val start = windowStart
        if (start == null) {
            windowStart = now
            framesInWindow = 1
            return
        }
        framesInWindow++
        val elapsed = now - start
        if (elapsed >= 1.seconds) {
            _fps.value = framesInWindow / elapsed.inWholeSeconds.coerceAtLeast(0.001)
            windowStart = now
            framesInWindow = 0
        }
    }

    companion object {
        const val STREAM_PATH = "/stream"
        const val CAPTURE_PATH = "/capture"
        private const val CHUNK_SIZE = 64 * 1024

        fun normalizeBaseUrl(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                "Adres musi zaczynać się od http:// lub https://"
            }
            return trimmed
        }
    }
}
