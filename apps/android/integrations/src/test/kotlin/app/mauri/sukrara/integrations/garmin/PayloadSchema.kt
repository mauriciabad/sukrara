// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import app.mauri.sukrara.core.testing.Contracts
import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.dialect.Dialects
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.fail

/** contracts/watchface/payload/schema.json (JSON Schema 2020-12), the payload's one description for every developer. */
object PayloadSchema {
    private val schema by lazy {
        SchemaRegistry
            .withDialect(Dialects.getDraft202012())
            .getSchema(Contracts.file("watchface/payload/schema.json").readText(), InputFormat.JSON)
    }

    /** The schema's errors for [payload], empty when it is valid. */
    fun errors(payload: JsonElement): List<String> =
        schema.validate(payload.toString(), InputFormat.JSON).map { "${it.instanceLocation}: ${it.message}" }

    /** Fails [name] when [payload] isn't valid. */
    fun assertValid(
        name: String,
        payload: JsonElement,
    ) {
        val errors = errors(payload)
        if (errors.isNotEmpty()) fail("$name doesn't match schema.json:\n  " + errors.joinToString("\n  "))
    }
}
