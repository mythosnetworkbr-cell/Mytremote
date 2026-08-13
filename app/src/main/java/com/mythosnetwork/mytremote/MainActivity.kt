package com.mythosnetwork.mytremote

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.mythosnetwork.mytremote.remote.RemoteApi
import java.net.Socket

class MainActivity : ComponentActivity() {
    private val projectionRequest = 9001
    private lateinit var api: RemoteApi
    private lateinit var local: LocalRemoteManager
    private lateinit var viewerServer: ScreenViewerServer
    private var pendingSocket: Socket? = null
    private var pendingViewerIp: String? = null
    private var showRequest = mutableStateOf(false)
    private var requester = mutableStateOf("")
    private var remoteFrame = mutableStateOf<Bitmap?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = RemoteApi(this)
        api.registerDevice()
        viewerServer = ScreenViewerServer { bitmap -> runOnUiThread { remoteFrame.value = bitmap } }
        viewerServer.start()
        local = LocalRemoteManager { name, socket ->
            pendingSocket = socket
            pendingViewerIp = socket.inetAddress.hostAddress
            requester.value = name
            showRequest.value = true
        }
        local.setDeviceCode(api.deviceCode.orEmpty())
        local.start()
        setContent {
            MaterialTheme {
                RemoteHome(api, local, remoteFrame.value, ::startCapture) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                if (showRequest.value) {
                    AlertDialog(
                        onDismissRequest = {
                            pendingSocket?.let { local.reject(it) }
                            pendingSocket = null
                            pendingViewerIp = null
                            showRequest.value = false
                        },
                        title = { Text("Solicitação de suporte") },
                        text = { Text("O aparelho ${requester.value} quer acessar seu dispositivo pela rede Wi‑Fi.\n\nAceite somente se reconhecer este aparelho.") },
                        confirmButton = {
                            Button(onClick = {
                                val viewerIp = pendingViewerIp
                                pendingSocket?.let { local.accept(it) }
                                pendingSocket = null
                                pendingViewerIp = null
                                showRequest.value = false
                                startCapture(viewerIp)
                            }) { Text("ACEITAR") }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = {
                                pendingSocket?.let { local.reject(it) }
                                pendingSocket = null
                                pendingViewerIp = null
                                showRequest.value = false
                            }) { Text("RECUSAR") }
                        }
                    )
                }
            }
        }
    }

    private fun startCapture(viewerIp: String? = null) {
        pendingViewerIp = viewerIp
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), projectionRequest)
    }

    override fun onDestroy() {
        if (::local.isInitialized) local.stop()
        if (::viewerServer.isInitialized) viewerServer.stop()
        super.onDestroy()
    }

    @Deprecated("Activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == projectionRequest && resultCode == Activity.RESULT_OK && data != null) {
            startForegroundService(Intent(this, ScreenCaptureService::class.java)
                .putExtra("resultCode", resultCode)
                .putExtra("data", data)
                .putExtra("viewerIp", pendingViewerIp ?: ""))
        }
    }
}

@Composable
private fun RemoteHome(api: RemoteApi, local: LocalRemoteManager, frame: Bitmap?, onStartCapture: () -> Unit, onAccessibilitySettings: () -> Unit) {
    var remoteAddress by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Pronto. Use o ID MYT do outro aparelho na mesma rede Wi‑Fi.") }
    val myCode = api.deviceCode.orEmpty()

    Column(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("SUPORTE MYTHØS", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text("Suporte remoto autorizado")
        Spacer(Modifier.height(10.dp))
        if (frame != null) {
            Image(frame.asImageBitmap(), contentDescription = "Tela remota", modifier = Modifier.fillMaxWidth().height(300.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.height(8.dp))
        }
        Text("SEU ID", style = MaterialTheme.typography.labelLarge)
        Text(myCode, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = remoteAddress, onValueChange = { remoteAddress = it }, label = { Text("ID do outro aparelho") }, placeholder = { Text("Ex.: MYT-70172A43") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            if (remoteAddress.isBlank()) message = "Digite o ID MYT do aparelho remoto."
            else {
                message = "Localizando o aparelho ${remoteAddress.trim()}..."
                local.request(remoteAddress, myCode) { response ->
                    message = when (response) {
                        "ACCEPT" -> "Conexão autorizada. Controle liberado."
                        "REJECT" -> "O aparelho remoto recusou a conexão."
                        "ERROR:DEVICE_NOT_FOUND" -> "ID não encontrado. Os dois aparelhos precisam estar na mesma rede Wi‑Fi."
                        else -> if (response.startsWith("ERROR:")) "Não foi possível conectar. Confira o Wi‑Fi e o ID." else response
                    }
                }
            }
        }) { Text("Solicitar conexão") }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { local.sendCommand("BACK") }) { Text("Voltar") }
            OutlinedButton(onClick = { local.sendCommand("HOME") }) { Text("Início") }
            OutlinedButton(onClick = { local.sendCommand("RECENTS") }) { Text("Recentes") }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { local.sendCommand("SWIPE|360|1100|360|300") }) { Text("↑") }
            OutlinedButton(onClick = { local.sendCommand("SWIPE|360|300|360|1100") }) { Text("↓") }
            OutlinedButton(onClick = { local.sendCommand("SWIPE|100|700|620|700") }) { Text("→") }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = onAccessibilitySettings) { Text("Ativar controle de acessibilidade") }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onStartCapture) { Text("Autorizar minha tela") }
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        Text("A captura e o controle só funcionam após autorização explícita no aparelho remoto.", style = MaterialTheme.typography.bodySmall)
    }
}
