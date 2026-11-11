# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# R8 (AGP 9 full mode) keep rules for code reached by name from native code or by reflection. A module's or a
# library's own rules travel with it: nostr-sdk + JNA in :share's consumer-rules.pro, the Libre 3 core's JNI class in
# :sensor's, the System UI module in :monitor's; zxing-cpp's AAR ships its own.

# WorkManager (Glance) and our Room database are instantiated reflectively.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Glance keys its widget bookkeeping by class name: keep each widget distinct and named (R8 once merged two).
-keep,allowoptimization class * extends androidx.glance.appwidget.GlanceAppWidget { <init>(); }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { <init>(); }

# Verbose and debug logs never reach a release build.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
