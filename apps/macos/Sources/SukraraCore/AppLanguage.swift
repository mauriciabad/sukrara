// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// The language Sukrara speaks where no String Catalog reaches (the card's words, the widget's, the mute lines):
/// Spanish when the system prefers it over English, else English, the same choice the catalog makes for the windows.
public enum AppLanguage: String, Sendable {
    case en, es

    public static var current: AppLanguage {
        #if DEBUG
            if let forced { return forced }
        #endif
        return Bundle.preferredLocalizations(from: ["en", "es"]).first == "es" ? .es : .en
    }

    #if DEBUG
        /// Snapshots render one language whatever the Mac's (`sukrara-snap …-es`). Debug builds only.
        nonisolated(unsafe) public static var forced: AppLanguage?
    #endif
}
