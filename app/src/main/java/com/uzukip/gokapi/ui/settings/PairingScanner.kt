package com.uzukip.gokapi.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.uzukip.gokapi.data.server.parsePairingLink
import com.uzukip.gokapi.ui.components.BouncyIconButton
import com.uzukip.gokapi.ui.components.BouncyTonalButton
import com.uzukip.gokapi.ui.components.MciIcon
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import zxingcpp.BarcodeReader

/**
 * Full-screen camera that reads a pairing QR code, such as the one the Devices page of
 * the files web UI shows. Codes that aren't pairing links are ignored with a hint, so a
 * stray QR in view doesn't end the scan. The link holds secrets: it is never logged and
 * only reaches [onScanned].
 */
@Composable
internal fun PairingScanner(onScanned: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        denied = !it
    }
    LaunchedEffect(Unit) { if (!granted) askCamera.launch(Manifest.permission.CAMERA) }
    var hint by remember { mutableStateOf("Point the camera at the pairing QR code.") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            Box(Modifier.fillMaxSize()) {
                when {
                    granted -> CameraFeed(
                        onScanned = onScanned,
                        onNotPairing = { hint = "That QR code isn't a pairing link. Use the one from the Devices page." },
                        onFailed = { hint = it },
                    )
                    denied -> Column(
                        Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        MciIcon("camera-off-outline", 48.dp, LocalContentColor.current)
                        Text(
                            "Scanning needs the camera. Allow it, or paste the pairing link instead.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        BouncyTonalButton(onClick = { askCamera.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                    }
                }
                if (granted) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(260.dp)
                            .border(3.dp, Color.White.copy(alpha = 0.85f), MaterialTheme.shapes.extraLarge),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().safeDrawingPadding().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BouncyIconButton(onClick = onDismiss) {
                        MciIcon("close", 26.dp, Color.White, contentDescription = "Close scanner")
                    }
                    Text("Scan pairing QR code", style = MaterialTheme.typography.titleLarge)
                }
                if (granted) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White,
                        modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(24.dp),
                    ) {
                        Text(hint, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraFeed(onScanned: (String) -> Unit, onNotPairing: () -> Unit, onFailed: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanned by rememberUpdatedState(onScanned)
    val notPairing by rememberUpdatedState(onNotPairing)
    val failed by rememberUpdatedState(onFailed)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        val analyzerThread = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val reader = BarcodeReader(BarcodeReader.Options(formats = setOf(BarcodeReader.Format.QR_CODE)))
        val done = AtomicBoolean(false)
        var lastRejected: String? = null
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(analyzerThread) { image ->
            image.use {
                if (done.get()) return@use
                val text = runCatching { reader.read(it) }.getOrNull()
                    ?.firstNotNullOfOrNull { result -> result.text?.takeIf(String::isNotBlank) }
                    ?: return@use
                if (runCatching { parsePairingLink(text) }.isSuccess) {
                    if (done.compareAndSet(false, true)) main.execute { scanned(text) }
                } else if (text != lastRejected) {
                    lastRejected = text
                    main.execute { notPairing() }
                }
            }
        }
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                provider = providerFuture.get().also {
                    it.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            } catch (e: Exception) {
                failed("The camera couldn't start. Paste the pairing link instead.")
            }
        }, main)
        onDispose {
            disposed = true
            provider?.unbind(preview, analysis)
            analysis.clearAnalyzer()
            analyzerThread.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}
