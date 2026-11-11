// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import SukraraApp
import SukraraUpdater

// The GitHub version. Everything, including the decision what this launch may touch, lives in SukraraApp (LaunchPlan,
// AppDelegate); the updater (Sparkle) starts only when the launch plan allows it, never in a dev or snapshot run.
AppMain.run(updater: { SparkleUpdater() })
