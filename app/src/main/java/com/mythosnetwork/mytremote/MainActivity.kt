package com.mythosnetwork.mytremote

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.mythosnetwork.mytremote.remote.RemoteApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val projectionRequest = 9001
    private lateinit var api: RemoteApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = RemoteApi(this)
        setContent { MaterialTheme { RemoteHome(api, ::startCapture, lifecycleScope) } }
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
private fun RemoteHome(api: RemoteApi, onStartCapture: () -> Unit, scope: kotlinx.coroutines.CoroutineScope) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var remoteCode by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loggedIn by remember { mutableStateOf(api.isLoggedIn()) }
    var myCode by remember { mutableStateOf(api.deviceCode ?: "") }
    var connecting by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("MYTHØS REMOTE", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Acesso remoto autorizado entre Androids")
        Spacer(Modifier.height(24.dp))

        if (!loggedIn) {
            OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(password, { password = it }, label = { Text("Senha") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        try { withContext(Dispatchers.IO) { api.login(email.trim(), password) }; loggedIn = true; myCode = api.deviceCode.orEmpty(); message = "Dispositivo registrado" }
                        catch (e: Exception) { message = e.message ?: "Falha no login" }
                    }
                }) { Text("Entrar") }
                OutlinedButton(onClick = {
                    scope.launch {
                        try { withContext(Dispatchers.IO) { api.signup(email.trim(), password) }; loggedIn = api.isLoggedIn(); myCode = api.deviceCode.orEmpty(); message = if (loggedIn) "Conta criada" else "Confirme o e-mail e entre" }
                        catch (e: Exception) { message = e.message ?: "Falha no cadastro" }
                    }
                }) { Text("Criar conta") }
            }
        } else {
            Text("Seu ID", style = MaterialTheme.typography.labelLarge)
            Text(myCode.ifBlank { "registrando..." }, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(remoteCode, { remoteCode = it }, label = { Text("ID do outro aparelho") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            Button(enabled = !connecting, onClick = {
                scope.launch {
                    connecting = true
                    try {
                        val device = withContext(Dispatchers.IO) { api.findDevice(remoteCode) }
                        if (device == null) message = "Dispositivo não encontrado"
                        else { withContext(Dispatchers.IO) { api.createSession(device.getString("id")) }; message = "Solicitação enviada. Aguarde a autorização." }
                    } catch (e: Exception) { message = e.message ?: "Falha na conexão" }
                    connecting = false
                }
            }) { Text(if (connecting) "Conectando..." else "Solicitar conexão") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onStartCapture) { Text("Autorizar compartilhamento da tela") }
        }
        Spacer(Modifier.height(18.dp))
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Text("O controle remoto exige autorização explícita do aparelho remoto.", style = MaterialTheme.typography.bodySmall)
    }
}
