package ru.fitapp.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.concurrent.Executors
import ru.fitapp.R
import ru.fitapp.core.LogTags
import ru.fitapp.ui.capture.CaptureViewModel.CaptureState
import ru.fitapp.ui.factoryOf
import ru.fitapp.ui.fitApp

/** Тег съёмки: сюда же пишут CaptureViewModel и MealCreator (tag `FitAppUI`/`FitAppPhoto`). */
private const val TAG = LogTags.UI

/**
 * Съёмка (§9): CameraX PreviewView в AndroidView, ImageCapture → JPEG,
 * подсказка «вся тарелка в кадре», runtime-разрешение CAMERA
 * (при отказе — объяснение и возврат). Без диалогов после снимка.
 *
 * Логи (tag `FitAppUI`): выдача разрешения, сбои камеры и привязки превью,
 * ошибка takePicture с исключением, размер полученного кадра.
 */
@Composable
fun CaptureScreen(onBack: () -> Unit) {
    val app = fitApp()
    val vm: CaptureViewModel = viewModel(factory = factoryOf { CaptureViewModel(app) })
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionRequested = true
        Log.i(TAG, "camera: разрешение CAMERA granted=$granted")
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            Log.i(TAG, "camera: запрашиваем разрешение CAMERA")
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    // Снимок сохранён и поставлен в очередь — сразу назад в ленту, без диалогов (§9).
    LaunchedEffect(state) {
        if (state is CaptureState.Saved) onBack()
    }

    when {
        hasPermission -> CameraUi(
            state = state,
            onJpeg = vm::onJpegCaptured,
            onCaptureError = vm::onCaptureError,
            onBack = onBack,
        )
        permissionRequested -> PermissionExplanation(
            onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onBack = onBack,
        )
        else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraUi(
    state: CaptureState,
    onJpeg: (ByteArray) -> Unit,
    onCaptureError: (Throwable?) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraError by remember { mutableStateOf(false) }

    LaunchedEffect(previewView, lifecycleOwner) {
        val view = previewView ?: return@LaunchedEffect
        val owner = lifecycleOwner
        if (owner == null) {
            cameraError = true
            Log.e(TAG, "camera: контекст не LifecycleOwner — превью не запустить")
            return@LaunchedEffect
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                val provider = runCatching { future.get() }
                    .onFailure { Log.e(TAG, "camera: ProcessCameraProvider недоступен", it) }
                    .getOrNull()
                if (provider == null) {
                    cameraError = true
                    return@addListener
                }
                val preview = Preview.Builder().build()
                    .also { it.setSurfaceProvider(view.surfaceProvider) }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        owner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                    )
                }.onSuccess {
                    cameraError = false
                    Log.i(TAG, "camera: превью привязано к жизненному циклу")
                }.onFailure {
                    cameraError = true
                    Log.e(TAG, "camera: не удалось привязать превью и захват", it)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun takePhoto() {
        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // Дефолтный формат in-memory колбэка — JPEG (§3: PhotoStore дожмёт до ≤1024 px).
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()
                    Log.i(TAG, "camera: кадр снят bytes=${bytes.size}")
                    onJpeg(bytes)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "camera: takePicture не удался", exception)
                    onCaptureError(exception)
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capture_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> PreviewView(ctx) },
                update = { view -> previewView = view },
            )

            // Подсказка «вся тарелка в кадре» (§9)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.55f),
            ) {
                Text(
                    stringResource(R.string.capture_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (state is CaptureState.Error) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 140.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        stringResource(R.string.capture_failed),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            // Кнопка затвора
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp),
            ) {
                val enabled = state is CaptureState.Idle || state is CaptureState.Error
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(CircleShape)
                        .background(if (enabled) Color.White else Color.LightGray)
                        .clickable(enabled = enabled) { takePhoto() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = stringResource(R.string.capture_shoot),
                        tint = Color(0xFF212121),
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            if (state is CaptureState.Saving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            if (cameraError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.capture_no_camera),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionExplanation(onGrant: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.PhotoCamera,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.capture_permission_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.capture_permission_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) {
            Text(stringResource(R.string.capture_permission_grant))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack) {
            Text(stringResource(R.string.action_back))
        }
    }
}
