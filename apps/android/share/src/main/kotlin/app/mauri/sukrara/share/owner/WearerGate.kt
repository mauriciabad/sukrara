// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.owner

import app.mauri.sukrara.core.model.SourceSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Sharing runs only where your glucose is read first-hand ([SourceSpec.owner], docs/44): started whenever the source
 * becomes yours and first-hand (without an identity the engine just shows *No identity* and runs nothing until one is
 * created or restored), stopped for someone else's glucose, a Sukrara stream or no source.
 */
suspend fun OwnerEngine.followSource(specs: Flow<SourceSpec?>, scope: CoroutineScope) {
    specs.distinctUntilChanged().collect { spec -> if (spec?.owner == true) start(scope) else stop() }
}
