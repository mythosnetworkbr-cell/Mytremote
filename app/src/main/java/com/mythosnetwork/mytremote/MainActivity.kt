package com.mythosnetwork.mytremote

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val projectionRequest = 9001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RemoteHome(onStartCapture = {
                        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        startActivityForResult(manager.createScreenCaptureIntent(), projectionRequest)
                    })
                }
            }
        }
    }

    @Deprecated("Activity result API migration can be done in the next iteration")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == projectionRequest && resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java)
                .putExtra("resultCode", resultCode)
                .putExtra("data", data)
            startForegroundService(intent)
        }
    }
}

@Composable
private fun RemoteHome(onStartCapture: () -> Unit) {
    var deviceId by remember { mutableStateOf("MYT-000000") }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("MYTHØS REMOTE", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Acesso remoto seguro entre dispositivos Android")
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(value = deviceId, onValueChange = { deviceId = it }, label = { Text("ID do dispositivo") })
        Spacer(Modifier.height(12.dp))
        Button(onClick = { /* sinalização/WebRTC será ligada aqui */ }) { Text("Conectar") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onStartCapture) { Text("Ativar compartilhamento de tela") }
        Spacer(Modifier.height(24.dp))
        Text("O controle remoto exige autorização explícita no aparelho remoto.", style = MaterialTheme.typography.bodySmall)
    }
}
