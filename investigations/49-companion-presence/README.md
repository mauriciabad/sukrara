# Investigation 49: CompanionDeviceManager presence for the sensor

Standalone proof of concept for [docs/32](../../docs/32-companion-presence.md) (issue #49). Nothing here is part of
Sukrara; it is a separate app, `dev.spike.cdmprobe`, run on an emulator only.

```
probe/                 CdmProbe: an Android app with no libraries (minSdk 36, targetSdk 37)
  MainActivity.kt        starts an association (the system's chooser / confirmation dialog), shows the log
  PresenceService.kt     the CompanionDeviceService the system binds while the device is present; on "appeared" or
                         "Bluetooth connected" it starts the foreground service, as Sukrara's monitor would
  LinkService.kt         a connectedDevice foreground service standing for Sukrara's MonitoringService
  ProbeReceiver.kt       adb commands (DUMP-protected): list · observe · unobserve · disassociate · fgs · stop
  Probe.kt               the log (logcat tag CdmProbe + files/probe.log, with the pid) and the CDM calls
  src/fgsOnly/           a flavor without REQUEST_COMPANION_RUN_IN_BACKGROUND / USE_DATA_IN_BACKGROUND
emulator-run.sh        the whole run: exemptions, process wake, Doze, force-stop, removal, the medical profile
allowlist-run.sh       does the association touch the battery exemption the person set by hand?
```

## Build and run

```sh
export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=~/Library/Android/sdk
apps/android/gradlew -p investigations/49-companion-presence/probe assembleDebug     # Sukrara's Gradle wrapper
APK=investigations/49-companion-presence/probe/build/outputs/apk
investigations/49-companion-presence/emulator-run.sh emulator-5580 $APK/full/debug/cdmprobe-full-debug.apk
investigations/49-companion-presence/allowlist-run.sh emulator-5580 $APK/full/debug/cdmprobe-full-debug.apk
investigations/49-companion-presence/allowlist-run.sh emulator-5580 $APK/fgsOnly/debug/cdmprobe-fgsOnly-debug.apk
```

The scripts refuse anything that isn't an `emulator-*` serial, use `adb root` (a userdebug image) to kill the process
the way the low-memory killer does, and need no Bluetooth device: the association is made by
`cmd companiondevice associate 0 <package> <MAC>` (as if the person had accepted the dialog) and presence is driven by
`cmd companiondevice simulate-device-event <id> <0 BLE appeared | 1 BLE disappeared | 2 BT connected | 3 BT
disconnected>`. On Android 17 the MAC must be positional: the help text's `--mac-address` doesn't parse.

By hand:

```sh
adb shell am broadcast -a dev.spike.cdmprobe.CMD -p dev.spike.cdmprobe --es cmd observe --ei id 1
adb shell am start -n dev.spike.cdmprobe/.MainActivity --es associate addr:F0:F1:F2:F3:F4:F5 --ez single true --es profile medical
adb logcat -s CdmProbe CDM_DevicePresenceProcessor CDM_CompanionAppBinder CDM_CompanionExemptionProcessor
```

What the simulation can't show (needs a phone and a Libre 3): the real BLE scan's latency, whether a GATT link to the
sensor reports "Bluetooth connected", the sensor's advertising while connected, and battery. Those are the follow-up's
acceptance criteria in the report.
