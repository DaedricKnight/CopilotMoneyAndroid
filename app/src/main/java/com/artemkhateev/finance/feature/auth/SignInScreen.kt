package com.artemkhateev.finance.feature.auth

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artemkhateev.finance.R
import com.artemkhateev.finance.data.AppGraph
import com.artemkhateev.finance.ui.components.FinanceCard
import com.artemkhateev.finance.ui.components.PillButton
import com.artemkhateev.finance.ui.components.appBackgroundBrush
import com.artemkhateev.finance.ui.theme.FinanceTheme

@Composable
fun SignInScreen(
    viewModel: SignInViewModel = viewModel { SignInViewModel(checkNotNull(AppGraph.auth)) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val creating = state.mode == SignInMode.CreateAccount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackgroundBrush(colors))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Text(stringResource(R.string.app_name), style = typography.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (creating) "Create an account to keep your data in the cloud" else "Sign in to sync your finances",
            style = typography.bodySecondary,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        FinanceCard(
            hero = true,
            contentPadding = PaddingValues(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PillButton(
                text = "Continue with Google",
                enabled = !state.busy && activity != null,
                onClick = { activity?.let(viewModel::signInWithGoogle) },
            )
            OrDivider()
            AuthField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = "Email",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            )
            Spacer(Modifier.height(10.dp))
            AuthField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Password",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                secret = true,
                onDone = viewModel::submit,
            )
            state.error?.let { Message(it, colors.negativeText) }
            state.info?.let { Message(it, colors.positiveText) }
            Spacer(Modifier.height(16.dp))
            PillButton(
                text = if (creating) "Create account" else "Sign in",
                enabled = !state.busy,
                filled = true,
                onClick = viewModel::submit,
            )
            if (!creating) {
                Spacer(Modifier.height(4.dp))
                TextLink("Forgot password?", viewModel::resetPassword)
            }
        }
        Spacer(Modifier.height(12.dp))
        TextLink(
            text = if (creating) "Have an account? Sign in" else "New here? Create an account",
            onClick = viewModel::toggleMode,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OrDivider() {
    val colors = FinanceTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = colors.border)
        Text(
            text = "or",
            style = FinanceTheme.typography.caption,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = colors.border)
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    secret: Boolean = false,
    onDone: () -> Unit = {},
) {
    val colors = FinanceTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        textStyle = FinanceTheme.typography.body,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            cursorColor = colors.accent,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.border,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.textSecondary,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = FinanceTheme.typography.bodySecondary,
        color = FinanceTheme.colors.accent,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun Message(text: String, color: Color) {
    Text(
        text = text,
        style = FinanceTheme.typography.bodySecondary,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    )
}
