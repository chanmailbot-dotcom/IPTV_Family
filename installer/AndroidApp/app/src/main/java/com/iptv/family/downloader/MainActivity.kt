package com.iptv.family.downloader

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filed.Check
import androidx.compose.material.icons.filed.Info
import androidx.compose.material.icons.filed.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        setContent { DownloaderScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderScreen() {
    val context = LocalContext.current
    var state by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var progress by remember { mutableStateOf<Float>(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    val downloadApk = {
        scope.launch(Dispatchers.IO) {
            state = DownloadState.Downloading
            progress = 0f
            errorMessage = null
            
            try {
                val url = URL("https://github.com/chanmailbot-dotcom/IPTV_Family/releases/latest/download/app-release.apk")
                val connection = url.openConnection()
                connection.connect()
                
                val fileLength = connection.contentLength
                val input = connection.getInputStream()
                
                val outputFile = File(context.cacheDir, "iptv-family.apk")
                val output = FileOutputStream(outputFile)
                
                val buffer = ByteArray(8192)
                var downloaded = 0
                var len: Int
                
                while (input.read(buffer).also { len = it } != -1) {
                    output.write(buffer, 0, len)
                    downloaded += len
                    if (fileLength > 0) {
                        progress = downloaded.toFloat() / fileLength
                    }
                }
                
                output.flush()
                output.close()
                input.close()
                
                // Instalar APK
                installApk(context, outputFile)
                
            } catch (e: Exception) {
                errorMessage = e.message
                state = DownloadState.Error
            }
        }
    }
    
    val checkInstallPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasPermission = context.packageManager.canRequestPackageInstalls()
            if (!hasPermission) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
                Toast.makeText(context, "Permite la instalación de apps desconocidas", Toast.LENGTH_LONG).show()
            } else {
                downloadApk()
            }
        } else {
            downloadApk()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_launcher),
            contentDescription = "IPTV Family",
            modifier = Modifier.size(120.dp)
        )
        
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
        
        // Título
        Text(
            text = "IPTV Family",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Descargar e instalar APK",
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(40.dp))
        
        // Card de estado
        Card(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (state) {
                    DownloadState.Idle -> {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF1E88E5), modifier = Modifier.size(48.dp))
                        Text("Listo para descargar", fontSize = 18.sp, color = Color.White)
                        Text("Versión 1.0.0 • ~25 MB", fontSize = 14.sp, color = Color.Gray)
                    }
                    DownloadState.Downloading -> {
                        CircularProgressIndicator(
                            progress = progress,
                            modifier = Modifier.size(64.dp),
                            strokeWidth = 6.dp,
                            color = Color(0xFF1E88E5)
                        )
                        Text("Descargando... ${(progress * 100).toInt()}%", fontSize = 18.sp, color = Color.White)
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxWidth().height(8.dp).background(Color(0xFF333333))
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(progress)
                                    .height(8.dp)
                                    .background(Color(0xFF1E88E5))
                            )
                        }
                    }
                    DownloadState.Installing -> {
                        CircularProgressIndicator(modifier = Modifier.size(64.dp), color = Color(0xFF1E88E5))
                        Text("Instalando...", fontSize = 18.sp, color = Color.White)
                    }
                    DownloadState.Success -> {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Green, modifier = Modifier.size(48.dp))
                        Text("¡Instalado correctamente!", fontSize = 18.sp, color = Color.Green)
                        Text("Abre IPTV Family desde tu launcher", fontSize = 14.sp, color = Color.Gray)
                    }
                    DownloadState.Error -> {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                        Text("Error en la descarga", fontSize = 18.sp, color = Color.Red)
                        Text(errorMessage ?: "Error desconocido", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
                
                // Botón principal
                if (state == DownloadState.Idle || state == DownloadState.Error) {
                    Button(
                        onClick = checkInstallPermission,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E88E5),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (state == DownloadState.Error) "Reintentar" else "Descargar e Instalar",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
        
        // Info
        Text(
            text = "Fuente: github.com/chanmailbot-dotcom/IPTV_Family",
            fontSize = 12.sp,
            color = Color(0xFF666666),
            textAlign = TextAlign.Center
        )
    }
}

enum class DownloadState {
    Idle, Downloading, Installing, Success, Error
}

private fun installApk(context: android.content.Context, file: File) {
    val intent = Intent(Intent.ACTION_VIEW)
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    intent.setDataAndType(uri, "application/vnd.android.package-archive")
    intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    intent.flags = intent.flags or Intent.FLAG_ACTIVITY_NEW_TASK
    context.startActivity(intent)
}