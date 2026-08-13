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
import com.mythosnetwork.mytremote.remote.RemoteApi
import java.net.Socket

class MainActivity : ComponentActivity() {
    private val projectionRequest = 9001
    private lateinit var api: RemoteApi
    private lateinit var local: LocalRemoteManager
    private var pendingSocket: Socket? = null
    private var showRequest = mutableStateOf(false)
    private var requester = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = RemoteApi(this)
        api.registerDevice()
        local = LocalRemoteManager(this) { name, socket ->
            pendingSocket = socket
            requester.value = name
            showRequest.value = true
        }
        local.start()
        setContent {
            MaterialTheme {
                RemoteHome(api, local, ::startCapture)
                if (showRequest.value) {
                    AlertDialog(
                        onDismissRequest = {
                            pendingSocket?.let { local.reject(it) }
                            pendingSocket = null
                            showRequest.value = false
                        },
                        title = { Text("Solicitação de acesso") },
                        text = { Text("O aparelho ${requester.value} quer acessar seu dispositivo pela rede Wi‑Fi.\n\nAceite somente se reconhecer este aparelho.") },
                        confirmButton = {
                            Button(onClick = {
                                pendingSocket?.let { local.accept(it) }
                                pendingSocket = null
                                showRequest.value = false
                                startCapture()
                            }) { Text("ACEITAR") }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = {
                                pendingSocket?.let { local.reject(it) }
                                pendingSocket = null
                                showRequest.value = false
                            }) { Text("RECUSAR") }
                        }
                    )
                }
            }
        }
    }

    private fun startCapture() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), projectionRequest)
    }

    override fun onDestroy() {
        if (::local.isInitialized) local.stop()
        super.onDestroy()
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
private fun RemoteHome(api: RemoteApi, local: LocalRemoteManager, onStartCapture: () -> Unit) {
    var remoteAddress by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Servidor Wi‑Fi ativo na porta ${LocalRemoteManager.PORT}") }
    val myCode = api.deviceCode.orEmpty()

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("MYTHØS REMOTE", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Controle remoto autorizado pela rede Wi‑Fi")
        Spacer(Modifier.height(24.dp))
        Text("SEU ID", style = MaterialTheme.typography.labelLarge)
        Text(myCode, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = remoteAddress,
            onValueChange = { remoteAddress = it },
            label = { Text("IP do outro aparelho") },
            placeholder = { Text("Ex.: 192.168.1.20") },
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            if (remoteAddress.isBlank()) message = "Digite o IP do aparelho remoto."
            else {
                message = "Solicitação enviada. Aguardando ACEITAR..."
                local.request(remoteAddress, myCode) { response ->
                    message = when {
                        response == "ACCEPT" -> "Conexão autorizada pelo aparelho remoto."
                        response == "REJECT" -> "O aparelho remoto recusou a conexão."
                        response.startsWith("ERROR:") -> "Não foi possível conectar. Verifique Wi‑Fi e IP."
                        else -> "Resposta: $response"
                    }
                }
            }
        }) { Text("Solicitar conexão") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onStartCapture) { Text("Autorizar minha tela") }
        Spacer(Modifier.height(18.dp))
        Text(message, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        Text("Os dois aparelhos precisam estar na mesma rede Wi‑Fi.", style = MaterialTheme.typography.bodySmall)
    }
}
