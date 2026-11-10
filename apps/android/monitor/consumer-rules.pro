# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Every rule below holds only while SystemAccess is in the app: the full build passes systemui.SystemUiRoot to
# MonitorModule; the Google Play build doesn't, so R8 leaves the root and LSPosed code out of it (RootFeatures).
#
# The root slot daemon is started by name through app_process (systemui/RootStatusBar): keep its main.
-if class app.mauri.sukrara.monitor.systemui.SystemAccess
-keep class app.mauri.sukrara.monitor.systemui.SlotDaemon { public static void main(java.lang.String[]); }
# LSPosed loads the module by the name in META-INF/xposed/java_init.list and calls its no-argument constructor.
# Its overrides of the compileOnly XposedModule (onPackageReady) have no visible caller: keep every member.
-if class app.mauri.sukrara.monitor.systemui.SystemAccess
-keep class app.mauri.sukrara.monitor.systemui.SystemUiHook { *; }
# LSPosed reaches the app's side of the service by name (XposedProvider, the service binder).
-if class app.mauri.sukrara.monitor.systemui.SystemAccess
-keep class io.github.libxposed.service.** { *; }
# The libxposed API is provided by LSPosed at run time (compileOnly).
-dontwarn io.github.libxposed.api.**
