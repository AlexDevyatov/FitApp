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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.fitapp.R
import ru.fitapp.ui.common.BrandMark
import ru.fitapp.ui.common.PrimaryButton
import ru.fitapp.ui.common.ScreenTopBar
import ru.fitapp.ui.common.SecondaryButton
import ru.fitapp.ui.factoryOf
import ru.fitapp.ui.fitApp

enum class KeyScreenMode { ONBOARDING, SETTINGS }

/**
 * Онбординг: ключ. Первый экран — обещание, а не форма: логотип, объяснение,
 * шаг 1 из 3, поле ключа и два равноправных способа вставки.
 */
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
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (mode == KeyScreenMode.SETTINGS) {
                ScreenTopBar(
                    title = stringResource(R.string.key_title_settings),
                    onBack = onBack,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = if (mode == KeyScreenMode.ONBOARDING) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (mode == KeyScreenMode.ONBOARDING) {
                Spacer(Modifier.height(12.dp))
                BrandMark()
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.key_onboarding_title),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.key_onboarding_caption),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.key_onboarding_step),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
            } else {
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
                shape = RoundedCornerShape(16.dp),
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

            SecondaryButton(
                text = stringResource(R.string.key_paste),
                icon = Icons.Filled.ContentPaste,
                enabled = !checking,
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
            )

            SecondaryButton(
                text = stringResource(R.string.key_import_file),
                icon = Icons.Filled.FolderOpen,
                enabled = !checking,
                onClick = { filePicker.launch(arrayOf("text/*")) },
            )

            Spacer(Modifier.height(4.dp))
            if (checking) {
                PrimaryButton(text = stringResource(R.string.key_checking), onClick = {}, enabled = false)
            } else {
                PrimaryButton(text = stringResource(R.string.key_check), onClick = vm::check)
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

            if (checking) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.key_checking),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
            .background(container, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = content)
    }
}
