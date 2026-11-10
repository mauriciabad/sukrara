// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.spike.cdmprobe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * adb commands (only the shell can send them: DUMP):
 * `adb shell am broadcast -a dev.spike.cdmprobe.CMD -p dev.spike.cdmprobe --es cmd <cmd> [--ei id N] [--es uuid U]`
 *
 * cmd: list · observe (id or uuid) · unobserve (id or uuid) · disassociate (id) · fgs (start the foreground service
 * from here, the background) · stop (stop it) · standing · clear (empty the log)
 */
class ProbeReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val id = if (intent.hasExtra("id")) intent.getIntExtra("id", -1) else null
        val uuid = intent.getStringExtra("uuid")
        when (val cmd = intent.getStringExtra("cmd")) {
            "list" -> Probe.list(context)
            "observe" -> Probe.observe(context, id, uuid)
            "unobserve" -> Probe.unobserve(context, id, uuid)
            "disassociate" -> Probe.disassociate(context, checkNotNull(id) { "--ei id" })
            "fgs" -> Probe.startLink(context, "adb broadcast")
            "stop" -> Probe.stopLink(context)
            "standing" -> Probe.log(context, "standing ${Probe.standing(context)}")
            "clear" -> context.filesDir.resolve("probe.log").delete()
            else -> Probe.log(context, "unknown cmd $cmd")
        }
    }
}
