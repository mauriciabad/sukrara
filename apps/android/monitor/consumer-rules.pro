# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Every rule below holds only while SystemAccess is in the app: both builds pass systemui.SystemUiRoot to
# MonitorModule; a build that doesn't (Play's fallback) gets no root or LSPosed code from R8 (RootFeatures).
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
# The hooks are lambdas implementing XposedInterface.Hooker; LSPosed calls their intercept. R8 keeps it only when it
# sees the API: the app gives it as a library (app/build.gradle.kts compileOnly). A keep rule can't do it (R8
# synthesizes the lambda classes after the rules match; tried). 0.12.0 shipped them empty: AbstractMethodError in every
# hook on the owner's phone. tools/release/verify-apk.sh fails an APK with an empty hook class.
# HookCodeTest fingerprints this file: a change here asks for a System UI restart.
