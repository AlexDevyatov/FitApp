package ru.fitapp.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.fitapp.R
import ru.fitapp.ui.factoryOf
import ru.fitapp.ui.fitApp

enum class KeyScreenMode { ONBOARDING, SETTINGS }

/**
 * Онбординг: ключ (§9). Поле ввода + вставка из буфера + импорт из файла (SAF, текстовый MIME),
 * «Проверить» — probeKeyDetailed(); неверный ключ дальше не пускает, ошибка понятная,
 * для TLS — отдельная подсказка про сертификат.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyScreen(
    mode: KeyScreenMode,
    onBack: (() -> Unit)?,
    onValid: () -> Unit,
) {
    val app = fitApp()
    val vm: KeyViewModel = viewModel(factory = factoryOf { KeyViewModel(app) })
    val keyText by vm.keyText.collectAsState()
    val keyVisible by vm.keyVisible.collectAsState()
    val checking by vm.checking.collectAsState()
    val result by vm.result.collectAsState()
    val message by vm.message.collectAsState()

    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFromUri(uri)
    }

    // Ключ прошёл проверку — короткий показ успеха и переход дальше.
    LaunchedEffect(result) {
        if (result is KeyCheckUiResult.Valid) {
            delay(600)
            onValid()
        }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (mode == KeyScreenMode.ONBOARDING) R.string.key_title_onboarding
                            else R.string.key_title_settings,
                        ),
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (mode == KeyScreenMode.ONBOARDING) {
                Text(
                    stringResource(R.string.key_caption),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = keyText,
                onValueChange = vm::onKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.key_field_label)) },
                singleLine = true,
                enabled = !checking,
                visualTransformation = if (keyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = vm::toggleKeyVisible) {
                        Icon(
                            imageVector = if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(R.string.cd_key_visibility),
                        )
                    }
                },
            )

            OutlinedButton(
                onClick = {
                    scope.launch {
                        val clipText = clipboard.getClipEntry()
                            ?.clipData
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                        vm.pasteFromClipboard(clipText)
                    }
                },
                enabled = !checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.key_paste))
            }

            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("text/*")) },
                enabled = !checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.key_import_file))
            }

            Button(
                onClick = vm::check,
                enabled = !checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.key_checking))
                } else {
                    Text(stringResource(R.string.key_check))
                }
            }

            when (val r = result) {
                null -> Unit
                KeyCheckUiResult.EmptyField -> KeyResultCard(
                    success = false,
                    text = stringResource(R.string.key_error_empty),
                )
                KeyCheckUiResult.Valid -> KeyResultCard(
                    success = true,
                    text = stringResource(R.string.key_valid),
                )
                is KeyCheckUiResult.InvalidKey -> KeyResultCard(
                    success = false,
                    text = stringResource(R.string.key_error_invalid, r.message),
                )
                KeyCheckUiResult.ModelMissing -> KeyResultCard(
                    success = false,
                    text = stringResource(R.string.key_error_model_missing),
                )
                is KeyCheckUiResult.TlsError -> KeyResultCard(
                    success = false,
                    text = stringResource(R.string.key_error_tls, r.detail),
                )
                is KeyCheckUiResult.NetworkError -> KeyResultCard(
                    success = false,
                    text = stringResource(R.string.key_error_network, r.detail),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun KeyResultCard(success: Boolean, text: String) {
    val scheme = MaterialTheme.colorScheme
    val container: Color
    val content: Color
    val icon: ImageVector
    if (success) {
        container = scheme.primaryContainer
        content = scheme.onPrimaryContainer
        icon = Icons.Filled.CheckCircle
    } else {
        container = scheme.errorContainer
        content = scheme.onErrorContainer
        icon = Icons.Filled.ErrorOutline
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = content)
    }
}
