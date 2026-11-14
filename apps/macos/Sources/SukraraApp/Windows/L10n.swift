// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// The windows' visible text, from the String Catalog (`Localizable.xcstrings`, en + es) in this module's bundle.
nonisolated enum L10n {
    static func t(_ key: String.LocalizationValue) -> String { String(localized: key, bundle: bundle) }

    /// The language the text is in, for what Foundation words itself ("12 hours" for VoiceOver).
    static var locale: Locale { Locale(identifier: language ?? Bundle.module.preferredLocalizations.first ?? "en") }

    #if DEBUG
        /// The table the text comes from: the module (the Mac's language), or one language's table for snapshots.
        nonisolated(unsafe) static var bundle: Bundle = .module
        nonisolated(unsafe) static var language: String?

        /// Snapshots render one language whatever the Mac's (`sukrara-snap windows-es`). Debug builds only.
        static func use(language: String) {
            guard let path = Bundle.module.path(forResource: language, ofType: "lproj"), let b = Bundle(path: path)
            else { return }
            bundle = b
            self.language = language
        }
    #else
        static var bundle: Bundle { .module }
        static var language: String? { nil }
    #endif
}
