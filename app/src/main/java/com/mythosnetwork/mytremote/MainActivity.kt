package com.mythosnetwork.mytremote

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mythosnetwork.mytremote.remote.RemoteApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val projectionRequest = 9001
    private lateinit var api: RemoteApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = RemoteApi(this)
        api.registerDevice()
        setContent { MaterialTheme { RemoteHome(api, ::startCapture) } }
    }

    private fun startCapture() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), projectionRequest)
    }

    @Deprecated("Activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == projectionRequest && resultCode == Activity.RESULT_OK && data != null) {
            startForegroundService(Intent(this, ScreenCaptureService::class.java)
                .putExtra("resultCode", resultCode).putExtra("data", data))
        }
    }
}

@Composable
private fun RemoteHome(api: RemoteApi, onStartCapture: () -> Unit) {
    var remoteCode by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val myCode = api.deviceCode.orEmpty()

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("MYTHØS REMOTE", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Acesso remoto autorizado entre Androids")
        Spacer(Modifier.height(28.dp))

        Text("SEU ID", style = MaterialTheme.typography.labelLarge)
        Text(myCode, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = remoteCode,
            onValueChange = { remoteCode = it },
            label = { Text("ID do outro aparelho") },
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))

        Button(onClick = {
            val target = api.findDevice(remoteCode)
            message = if (target == null) {
                "ID não encontrado. A sinalização pela internet será adicionada ao próximo backend."
            } else {
                "ID localizado. Autorize o compartilhamento da tela no aparelho remoto."
            }
        }) { Text("Solicitar conexão") }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onStartCapture) {
            Text("Autorizar compartilhamento da tela")
        }

        Spacer(Modifier.height(18.dp))
        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "O controle remoto exige autorização explícita do aparelho remoto.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
