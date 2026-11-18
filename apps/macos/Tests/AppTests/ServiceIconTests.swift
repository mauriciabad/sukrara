// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import Foundation
import SukraraCore
import Testing

@testable import SukraraApp

/// The Mac's service icons (Android's `AppIconsTest`): every service's art ships in the resource bundle as a square
/// layer that macOS-style masking can shape, and every source that reads another app or service shows that one's icon.
@MainActor @Suite struct ServiceIconTests {
    @Test func everyServiceHasSquareArt() throws {
        for service in AppService.allCases {
            let image = try #require(service.image, "\(service): no \(service.file.name).\(service.file.ext)")
            #expect(image.size.width > 0 && image.size.width == image.size.height, "\(service): \(image.size)")
            // raster art is big enough for a 32 pt icon at 2x with the largest text
            if let bitmap = image.representations.first as? NSBitmapImageRep {
                #expect(bitmap.pixelsWide >= 128, "\(service): \(bitmap.pixelsWide) px")
            }
        }
    }

    /// Android's `SourceIcon`: Juggluco, Nightscout and LibreLinkUp show their own icon; another Sukrara the drop.
    @Test func sourcesShowTheirService() {
        #expect(AppService.of(.lanJuggluco) == .juggluco)
        #expect(AppService.of(.nightscout) == .nightscout)
        #expect(AppService.of(.libreLinkUp) == .libreLinkUp)
        #expect(AppService.of(.sukrara) == nil)
        #expect(Set(SourceKind.allCases.compactMap(AppService.of)) == Set(AppService.allCases))
    }
}
