// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.spike.cdmprobe

import android.app.Activity
import android.bluetooth.le.ScanFilter
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.os.ParcelUuid
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.regex.Pattern

/**
 * Starts an association (the system's chooser / confirmation dialog) and shows the log. adb:
 * `adb shell am start -n dev.spike.cdmprobe/.MainActivity --es associate addr:F0:...|name:<regex>|uuid:<uuid> --ez single true
 *  [--es profile medical] [--es extra android.permission.X,android.permission.Y]`
 */
class MainActivity : Activity() {
    private lateinit var filter: EditText
    private lateinit var single: CheckBox
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filter = EditText(this).apply { hint = "addr:AA:BB:… · name:<regex> · uuid:<uuid>" }
        single = CheckBox(this).apply { text = "single device (confirmation dialog, no chooser)" }
        logView = TextView(this).apply { textSize = 11f }
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 96, 32, 32)
                addView(filter)
                addView(single)
                addView(Button(context).apply {
                    text = "Associate"
                    setOnClickListener { associate(filter.text.toString(), single.isChecked) }
                })
                addView(Button(context).apply {
                    text = "List associations"
                    setOnClickListener {
                        Probe.list(context)
                        refresh()
                    }
                })
                addView(ScrollView(context).apply { addView(logView) })
            }
        setContentView(root)
        handle(intent)
        refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun handle(intent: Intent) {
        val spec = intent.getStringExtra("associate") ?: return
        intent.removeExtra("associate")
        associate(spec, intent.getBooleanExtra("single", false), intent.getStringExtra("profile"), intent.getStringExtra("extra"))
    }

    private fun refresh() {
        logView.text = Probe.readLog(this).lines().takeLast(60).joinToString("\n")
    }

    private fun associate(
        spec: String,
        singleDevice: Boolean,
        profile: String? = null,
        extra: String? = null,
    ) {
        val ble = BluetoothLeDeviceFilter.Builder()
        when {
            spec.startsWith("addr:") -> ble.setScanFilter(ScanFilter.Builder().setDeviceAddress(spec.removePrefix("addr:")).build())
            spec.startsWith("name:") -> ble.setNamePattern(Pattern.compile(spec.removePrefix("name:")))
            spec.startsWith("uuid:") ->
                ble.setScanFilter(ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(spec.removePrefix("uuid:"))).build())
        }
        val request =
            AssociationRequest
                .Builder()
                .addDeviceFilter(ble.build())
                .setSingleDevice(singleDevice)
                .apply {
                    if (profile == "medical") setDeviceProfile(AssociationRequest.DEVICE_PROFILE_MEDICAL)
                    if (extra != null) setExtraPermissions(extra.split(',').toSet())
                }.build()
        Probe.log(this, "associate $spec single=$singleDevice profile=$profile extra=$extra")
        Probe.cdm(this).associate(
            request,
            mainExecutor,
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    Probe.log(this@MainActivity, "association pending: showing the system dialog")
                    startIntentSenderForResult(intentSender, REQUEST, null, 0, 0, 0)
                }

                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    Probe.log(this@MainActivity, "association created: ${Probe.describe(associationInfo)}")
                    refresh()
                }

                override fun onFailure(error: CharSequence?) {
                    Probe.log(this@MainActivity, "association failed: $error")
                    refresh()
                }
            },
        )
    }

    @Deprecated("startIntentSenderForResult is the CDM sample's way; the probe has no AndroidX")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST) Probe.log(this, "dialog result=${if (resultCode == RESULT_OK) "OK" else "cancelled ($resultCode)"}")
        refresh()
    }

    private companion object {
        const val REQUEST = 1
    }
}
