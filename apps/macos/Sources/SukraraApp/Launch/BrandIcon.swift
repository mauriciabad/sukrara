// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit

/// Sukrara's app icon for a run that isn't the app bundle (`swift run`, a bare dev executable, `sukrara-snap`, the
/// tests): macOS gives those a generic icon, so the Dock, About and the rows that draw Sukrara's own icon
/// (`AppService.sukrara`, e.g. *Their Sukrara* as a source) would show a folder. It takes the brand's `AppIcon.icns`
/// from the checkout the executable was built in, the file `Bundle/bundle.sh` puts in the bundle. A bundle keeps its
/// own icon.
enum BrandIcon {
    /// Where the icns is in the checkout.
    static let path = "assets/brand/sukrara/macos/AppIcon.icns"

    /// The icns of the checkout `executable` sits in (searched up from it: `apps/macos/.build/debug/Sukrara`), nil
    /// outside one.
    static func file(from executable: URL?) -> URL? {
        var dir = executable?.resolvingSymlinksInPath().deletingLastPathComponent()
        while let d = dir, d.path != "/", !d.path.isEmpty {
            let f = d.appending(path: path)
            if FileManager.default.fileExists(atPath: f.path) { return f }
            dir = d.deletingLastPathComponent()
        }
        return nil
    }

    /// The brand's icon, from this executable's checkout (a debug build: or this source file's, as the tests run
    /// from Xcode's runner), nil outside one.
    static var image: NSImage? {
        #if DEBUG
            let url = file(from: Bundle.main.executableURL) ?? file(from: URL(fileURLWithPath: #filePath))
        #else
            let url = file(from: Bundle.main.executableURL)
        #endif
        return url.flatMap(NSImage.init(contentsOf:))
    }

    /// An unbundled run takes the brand's icon (nothing changes for a bundle, or outside a checkout).
    static func applyUnbundled(isBundle: Bool) {
        guard !isBundle, let image else { return }
        NSApplication.shared.applicationIconImage = image
    }
}
