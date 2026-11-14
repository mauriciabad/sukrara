# Investigation 195: one glucose notification that rings

Standalone prototype for [docs/36](../../docs/36-glucose-notification.md) (follow-up of #195). A throwaway app
(`dev.spike.onenotif`) with one foreground-service notification. It posts the glucose value on a silent channel. When
an alert fires, it posts the **same id** again on an alert channel so that it rings, and then goes back to silent.
Nothing here is part of Sukrara. The values are made up. Run it only on an emulator, never on the owner's phone.

```
app/src/main/kotlin/dev/spike/onenotif/OneService.kt   the service, its channels and every command (KDoc lists them)
```

## Build and run

The build uses the main app's Gradle wrapper, and uses its own Gradle home when another build holds the shared cache.

```sh
export ANDROID_HOME=~/Library/Android/sdk JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home
apps/android/gradlew -p investigations/195-one-notification :app:assembleDebug
adb install -r investigations/195-one-notification/app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant dev.spike.onenotif android.permission.POST_NOTIFICATIONS
adb shell cmd notification allow_dnd dev.spike.onenotif     # before the first start: the alarm channel's DND bypass
```

Each step is one command:

```sh
s() { adb shell am start-foreground-service -n dev.spike.onenotif/.OneService --es cmd "$@"; }
s start --ei v 138            # silent, promoted (the chip)
s ring --ei v 62              # same id, alarm channel: sound, vibration, heads-up, full-screen intent
s update --ei v 60            # same id, same channel, setOnlyAlertOnce: silent
s quiet                       # back to the silent channel (stops a sound still playing)
s ring --es ch notif          # the notification-volume channel instead
s separate                    # today's design: a second notification for the alert
s move ; s back               # the foreground service moved to the alert's id and back
s update --es metric sukrara  # Android 17: 0.14.5's MetricStyle card; --es metric fixed: the value + arrow + unit
adb logcat -s OneNotif RingtonePlayer   # every post's channel, flags, promotion; the system's sound player
adb shell dumpsys notification --noredact | grep -A40 dev.spike.onenotif
```

The results are in docs/36, "The prototype".
