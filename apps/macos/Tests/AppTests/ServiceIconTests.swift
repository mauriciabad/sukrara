// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import Foundation
import SukraraCore
import Testing

@testable import SukraraApp

/// The Mac's service icons (Android's `AppIconsTest`): every service's art ships in the resource bundle as a square
/// layer that macOS-style masking can shape, another Sukrara is the app's own icon (never a copy), and every source
/// shows the icon of the app or service it reads from.
@MainActor @Suite struct ServiceIconTests {
    @Test func everyServiceHasSquareArt() throws {
        for service in AppService.allCases {
            guard let file = service.file else { continue }
            let image = try #require(service.image, "\(service): no \(file.name).\(file.ext)")
            #expect(image.size.width > 0 && image.size.width == image.size.height, "\(service): \(image.size)")
            // raster art is big enough for a 32 pt icon at 2x with the largest text
            if let bitmap = image.representations.first as? NSBitmapImageRep {
                #expect(bitmap.pixelsWide >= 128, "\(service): \(bitmap.pixelsWide) px")
            }
        }
    }

    /// The owner: "why not use own sukrara icon for sukrara?": Sukrara is the running app's own icon, no bundled copy.
    @Test func sukraraIsTheAppsOwnIcon() {
        #expect(AppService.allCases.filter { $0.file == nil } == [.sukrara])
        #expect(AppService.sukrara.image === NSApplication.shared.applicationIconImage)
    }

    /// Android's `SourceIcon`: Juggluco, Nightscout, LibreLinkUp and another Sukrara show their own app icon.
    @Test func sourcesShowTheirService() {
        #expect(AppService.of(.lanJuggluco) == .juggluco)
        #expect(AppService.of(.nightscout) == .nightscout)
        #expect(AppService.of(.libreLinkUp) == .libreLinkUp)
        #expect(AppService.of(.sukrara) == .sukrara)
        #expect(Set(SourceKind.allCases.map(AppService.of)) == Set(AppService.allCases))
    }
}
