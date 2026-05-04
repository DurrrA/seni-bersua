package com.durrr.first

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.durrr.first.features.setup.presentation.LocalFirstSetupScreen
import com.durrr.first.ui.design.AppTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null
    private val viewModel: MainViewModel by viewModels()
    private var onImagePickedCallback: ((String?) -> Unit)? = null

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data?.getStringExtra(QrScannerActivity.EXTRA_RESULT)
        if (!data.isNullOrBlank()) {
            viewModel.setScannedToken(data)
        }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        onImagePickedCallback?.invoke(uri?.toString())
        onImagePickedCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashLogger()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AndroidAppContent(viewModel = viewModel, onLaunchScanner = {
                scannerLauncher.launch(Intent(this, QrScannerActivity::class.java))
            }, onPickImage = { onPicked ->
                onImagePickedCallback = onPicked
                imagePickerLauncher.launch("image/*")
            })
        }
    }

    private fun installCrashLogger() {
        if (defaultExceptionHandler != null) return
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val crashFile = File(cacheDir, "last_crash.txt")
                crashFile.parentFile?.mkdirs()
                crashFile.writeText(
                    buildString {
                        appendLine("thread=${thread.name}")
                        appendLine("time=${System.currentTimeMillis()}")
                        appendLine()
                        appendLine(Log.getStackTraceString(throwable))
                    },
                )
            }
            Log.e("SuCashCrash", "Uncaught exception in thread=${thread.name}", throwable)
            defaultExceptionHandler?.uncaughtException(thread, throwable)
                ?: run { throw throwable }
        }
    }
}

@Composable
private fun AndroidAppContent(
    viewModel: MainViewModel,
    onLaunchScanner: () -> Unit,
    onPickImage: ((String?) -> Unit) -> Unit,
) {
    val dependencies = rememberAppDependencies(
        context = LocalContext.current,
        launchScanner = onLaunchScanner,
        pickImage = onPickImage,
    )
    var localSetupComplete by remember {
        mutableStateOf(dependencies.settingsRepository.isLocalSetupComplete())
    }
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.material3.MaterialTheme.colorScheme.background
        ) {
            if (!localSetupComplete) {
                LocalFirstSetupScreen(
                    settingsRepository = dependencies.settingsRepository,
                    menuRepository = dependencies.menuRepository,
                    cashSessionRepository = dependencies.cashSessionRepository,
                    nowIso = dependencies.nowIso,
                    onComplete = { localSetupComplete = true },
                )
            } else {
                AndroidAppScaffold(
                    dependencies = dependencies,
                    viewModel = viewModel,
                    onRequireLocalSetup = { localSetupComplete = false },
                )
            }
        }
    }
}
