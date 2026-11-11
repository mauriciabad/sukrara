// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sensor.libreview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.Swap
import app.mauri.sukrara.feature.sensor.R

/**
 * THE LibreView account screen (the new-sensor flow and setup's sensor step, Settings › Sensor, Share with apps ›
 * LibreView): the sign-in form, or who is signed in with *Done* and *Sign out*. [onBack] leaves it.
 */
@Composable
fun LibreViewSignInRoute(
    viewModel: LibreViewSignInViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    BackHandler(onBack = onBack)
    LibreViewSignInContent(
        ui,
        modifier = modifier,
        canSubmit = viewModel.canSubmit,
        onSignIn = viewModel::signIn,
        onSignOut = {
            haptics.confirm()
            viewModel.signOut()
        },
        onBack = onBack,
    )
}

/**
 * Stateless (screenshots render it). The password lives only in this form (never saved state) and the one call; the
 * account keeps it encrypted once LibreView accepted it.
 */
@Composable
fun LibreViewSignInContent(
    ui: SignInUi,
    canSubmit: (String, String) -> Boolean,
    onSignIn: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.libreview_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        Swap(ui.page) { page ->
            when {
                page == Page.Loading -> CircularProgressIndicator()
                page == Page.SignedIn && ui is SignInUi.SignedIn -> SignedIn(ui.email, onSignOut, onBack)
                else -> Form(ui, canSubmit, onSignIn, onSignOut, onBack)
            }
        }
    }
}

@Composable
private fun SignedIn(
    email: String,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    var asking by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.libreview_signed_in_as, email),
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.libreview_uses),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onBack, Modifier.fillMaxWidth()) { Text(stringResource(R.string.newsensor_action_done)) }
        OutlinedButton({ asking = true }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.libreview_sign_out)) }
    }
    if (asking) {
        // signing out stops more than this screen shows: the upload, the next sensor's start
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.libreview_sign_out_title)) },
            text = { Text(stringResource(R.string.libreview_sign_out_note)) },
            confirmButton = {
                TextButton({
                    asking = false
                    onSignOut()
                }) { Text(stringResource(R.string.libreview_sign_out)) }
            },
            dismissButton = { TextButton({ asking = false }) { Text(stringResource(R.string.libreview_cancel)) } },
        )
    }
}

@Composable
private fun Form(
    ui: SignInUi,
    canSubmit: (String, String) -> Boolean,
    onSignIn: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    val shown = ui as? SignInUi.SignedOut
    val prefill = shown?.email.orEmpty()
    var email by rememberSaveable { mutableStateOf(prefill) }
    // the account LibreView stopped accepting: its email, unless something is typed already
    LaunchedEffect(prefill) { if (email.isEmpty() && prefill.isNotEmpty()) email = prefill }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val working = ui == SignInUi.Working
    val submit = {
        if (canSubmit(email, password)) {
            onSignIn(email, password)
            password = ""
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (shown?.again == true) {
            Text(
                stringResource(R.string.libreview_again),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            stringResource(R.string.libreview_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            email,
            { email = it },
            Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
            enabled = !working,
            label = { Text(stringResource(R.string.libreview_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            password,
            { password = it },
            Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
            enabled = !working,
            label = { Text(stringResource(R.string.libreview_password)) },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        // under the field, not inside it: the largest font would squeeze the field's label
        TextButton({ showPassword = !showPassword }) {
            Text(stringResource(if (showPassword) R.string.libreview_hide_password else R.string.libreview_show_password))
        }
        shown?.error?.let { error ->
            Text(
                stringResource(error.message),
                Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(submit, Modifier.fillMaxWidth(), enabled = !working && canSubmit(email, password)) {
            Text(stringResource(if (working) R.string.libreview_signing_in else R.string.libreview_sign_in))
        }
        TextButton(onBack, Modifier.fillMaxWidth()) { Text(stringResource(R.string.libreview_back)) }
        // an account LibreView stopped accepting can also just be forgotten (nothing to confirm: it does nothing now)
        if (shown?.again == true) {
            TextButton(onSignOut, Modifier.fillMaxWidth(), enabled = !working) { Text(stringResource(R.string.libreview_sign_out)) }
        }
    }
}

private val SignInError.message: Int
    get() =
        when (this) {
            SignInError.WrongPassword -> R.string.libreview_error_wrong_password
            SignInError.AcceptTerms -> R.string.libreview_error_accept_terms
            SignInError.Failed -> R.string.libreview_error_failed
        }

/** One page per kind of state (the form keeps its fields across Working and an error). */
private enum class Page { Loading, Form, SignedIn }

private val SignInUi.page: Page
    get() =
        when (this) {
            SignInUi.Loading -> Page.Loading
            is SignInUi.SignedIn -> Page.SignedIn
            else -> Page.Form
        }
