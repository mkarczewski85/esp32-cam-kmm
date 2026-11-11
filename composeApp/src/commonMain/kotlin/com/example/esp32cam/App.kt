package com.example.esp32cam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.esp32cam.camera.CameraStatus
import kotlinx.coroutines.launch

private const val DEFAULT_URL = "http://192.168.1.100:81"

@Composable
fun App(viewModel: CameraViewModel = remember { CameraViewModel() }) {
    MaterialTheme {
        CameraScreen(viewModel)
    }
}

@Composable
private fun CameraScreen(viewModel: CameraViewModel) {
    val frame by viewModel.frame.collectAsState()
    val status by viewModel.status.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val fps by viewModel.fps.collectAsState()

    var url by remember { mutableStateOf(DEFAULT_URL) }
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "ESP32-S3 Kamera",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Adres ESP32 (http://adres-ip:81)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    snapshot = null
                    viewModel.connect(url)
                },
                enabled = status == CameraStatus.Disconnected || status == CameraStatus.Error
            ) {
                Text("Połącz")
            }
            OutlinedButton(
                onClick = { viewModel.disconnect() },
                enabled = status == CameraStatus.Streaming || status == CameraStatus.Connecting
            ) {
                Text("Rozłącz")
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        snapshot = viewModel.capture(url)
                    }
                },
                enabled = status == CameraStatus.Disconnected || status == CameraStatus.Error
            ) {
                Text("Zrzut")
            }
        }

        val statusText = when (status) {
            CameraStatus.Disconnected -> "Rozłączono"
            CameraStatus.Connecting -> "Łączenie z $url…"
            CameraStatus.Streaming -> "Podgląd na żywo"
            CameraStatus.Error -> "Błąd: ${errorMessage ?: "nieznany"}"
        }
        val statusColor = when (status) {
            CameraStatus.Streaming -> Color(0xFF2E7D32)
            CameraStatus.Connecting -> Color(0xFFEF6C00)
            CameraStatus.Error -> MaterialTheme.colorScheme.error
            CameraStatus.Disconnected -> MaterialTheme.colorScheme.onBackground
        }
        Text(text = statusText, color = statusColor, style = MaterialTheme.typography.bodyMedium)

        val image = snapshot ?: frame
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(Color.Black, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (image != null) {
                androidx.compose.foundation.Image(
                    bitmap = image,
                    contentDescription = "Obraz z kamery ESP32",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "Brak obrazu.\nWpisz adres ESP32 i naciśnij „Połącz”.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        if (status == CameraStatus.Streaming) {
            Text(
                text = "FPS: ${(fps * 10).toInt() / 10.0}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
