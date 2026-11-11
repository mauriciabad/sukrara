// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.rows

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.settingslib.spa.framework.compose.LocalNavController
import com.android.settingslib.spa.framework.compose.NavControllerWrapper
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.android.settingslib.spa.widget.scaffold.SettingsScaffold

/**
 * The look of every settings page and its dialogs: SPA's SettingsTheme (the device's dynamic colours, the Settings
 * typography in the device's fonts, onSurface content). The glucose card inside a page keeps the app's own theme
 * (`SukraraTheme` around it): the one renderer draws it as everywhere else.
 */
@Composable
fun SettingsChrome(content: @Composable () -> Unit) = SettingsTheme(content)

/**
 * One settings page, SPA's SettingsScaffold in [SettingsChrome]: the large [title] (it matches the row that opened
 * it) expanded on open and collapsing into the top bar as the page scrolls, the expressive round back button
 * ([onBack]), the page in surfaceContainer. [content] scrolls in a column laid out like SPA's RegularScaffold: SPA's
 * widgets bring their own 16 dp side margin, other content adds it. [scroll] false: the content scrolls itself (a page
 * from another feature) and fills the space below the bar. [snackbar]: shown above the navigation bar.
 */
@Composable
fun SettingsScaffoldPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    snackbar: SnackbarHostState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsChrome {
        val back by rememberUpdatedState(onBack)
        // SPA's back button asks its navigation for "back": ours is the caller's. Its rows' HighlightBox asks it once
        // per row whether that row is the one a search result opened: the request this page took when it opened.
        val nav =
            remember {
                object : NavControllerWrapper {
                    private var highlight = SettingsHighlight.take()

                    override fun navigate(
                        route: String,
                        popUpCurrent: Boolean,
                    ) = Unit

                    override fun navigateBack() = back()

                    override fun isHighLightItem(highlightItemKey: String): Boolean =
                        (highlight == highlightItemKey).also { if (it) highlight = null }
                }
            }
        CompositionLocalProvider(LocalNavController provides nav) {
            Box(modifier.fillMaxSize()) {
                SettingsScaffold(title) { padding ->
                    if (scroll) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Spacer(Modifier.height(padding.calculateTopPadding()))
                            content()
                            Spacer(Modifier.height(padding.calculateBottomPadding()))
                        }
                    } else {
                        // the bar and the system bars are already padded: the page's own inset padding adds nothing twice
                        Column(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize(), content = content)
                    }
                }
                snackbar?.let {
                    SnackbarHost(it, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
                }
            }
        }
    }
}

/**
 * The row a settings search result opens, the way stock Settings' search does (SPA's highlight): the result asks for
 * its row by title ([request]) just before its page opens, and the next settings page that opens takes the request;
 * on it SPA's HighlightBox scrolls that row into view and flashes it. A result that is a page itself asks for none.
 * Main thread only.
 */
object SettingsHighlight {
    private var pending: String? = null

    /** The row titled [rowTitle] on the page about to open (null: none). */
    fun request(rowTitle: String?) {
        pending = rowTitle
    }

    /** The request, once: a page takes it when it opens. */
    internal fun take(): String? = pending.also { pending = null }
}
