// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import SukraraCore
import SwiftUI

/// Another app or service that a row stands for, drawn as its app icon: the Mac counterpart of Android's
/// `:core:designsystem` `icons/AppIcons` (`Service`, `ServiceIcon`; `Service` here is LaunchMode's), with the same marks
/// from the same sources. Each file in `Resources/ServiceIcons` is the icon's square layer (what macOS masks: HIG "App
/// icons"); sources and licences in docs/10-showcase.md "Service and app icons". Only the services the Mac works with:
/// xDrip+, Health Connect, Tidepool and LibreView are Android's.
enum AppService: String, CaseIterable {
    /// Juggluco's launcher icon (GPL-3.0, as Sukrara): its two layers, the visible part.
    case juggluco
    /// Nightscout's owl, black on white (vector).
    case nightscout
    /// LibreLinkUp's app icon, as its App Store and Google Play listings show it.
    case libreLinkUp

    /// The file in `Resources/ServiceIcons`.
    var file: (name: String, ext: String) {
        switch self {
        case .juggluco: ("juggluco", "png")
        case .nightscout: ("nightscout", "svg")
        case .libreLinkUp: ("librelinkup", "png")
        }
    }

    /// The service's square art (nil only if the resource is missing: the place stays empty, never a crash).
    var image: NSImage? {
        if let cached = Self.cache[self] { return cached }
        guard
            let url = Bundle.module.url(
                forResource: file.name, withExtension: file.ext, subdirectory: "ServiceIcons"),
            let image = NSImage(contentsOf: url)
        else { return nil }
        Self.cache[self] = image
        return image
    }

    private static var cache: [AppService: NSImage] = [:]

    /// The service a source reads from (Android `SourceIcon`); nil for another Sukrara (Sukrara's own drop).
    static func of(_ kind: SourceKind) -> AppService? {
        switch kind {
        case .sukrara: nil
        case .lanJuggluco: .juggluco
        case .nightscout: .nightscout
        case .libreLinkUp: .libreLinkUp
        }
    }
}

/// How big a [ServiceIcon] is, by the row it sits in, as System Settings draws app icons: 20 pt in a one-line row
/// (Login Items & Extensions, Privacy & Security), 32 pt in a row with a second line (Notifications' app list; also
/// Android's 32 dp). Both grow with the text size.
enum ServiceIconSize: CGFloat {
    case row = 20
    case twoLines = 32
}

/// A service's icon as macOS draws app icons in lists: the square art masked to the app icon's rounded square (macOS's
/// icon grid: an 824-of-1024 square with 185.4 corners, continuous curvature) over the soft shadow the system puts
/// under it, so a service sits in a row like an installed app in System Settings. The same shape in light and dark
/// (app icons don't change with the appearance); decorative: the row's title names the service.
struct ServiceIcon: View {
    let service: AppService
    var size: ServiceIconSize = .row
    @ScaledMetric(relativeTo: .body) private var scale: CGFloat = 1

    var body: some View {
        let side = size.rawValue * scale
        let tile = side * 824 / 1024
        ZStack {
            if let image = service.image {
                Image(nsImage: image)
                    .resizable()
                    .interpolation(.high)
                    .frame(width: tile, height: tile)
                    .clipShape(RoundedRectangle(cornerRadius: tile * 185.4 / 824, style: .continuous))
                    .shadow(color: .black.opacity(0.28), radius: max(0.5, side / 64), y: max(0.5, side / 96))
            }
        }
        .frame(width: side, height: side)
        .accessibilityHidden(true)
    }
}

extension EnvironmentValues {
    /// Whether [ServiceLabel]s show their icon: off in setup's connect steps, whose title carries the service's icon
    /// (the rows would repeat it, and a column form's right-aligned labels take no icons).
    @Entry var serviceRowIcons = true
}

/// A one-line row's label that names a service's account or address: its icon, then the title (Android's site and
/// account rows: `ServiceIcon` as the row's leading icon). The rows next to it (token, password, person) take a symbol
/// in the same slot, so every title in the section lines up (as Android's rows beside a service icon).
struct ServiceLabel: View {
    let title: String
    var service: AppService?
    var symbol: String?
    @ScaledMetric(relativeTo: .body) private var scale: CGFloat = 1

    init(_ title: String, service: AppService) {
        self.title = title
        self.service = service
    }

    init(_ title: String, symbol: String) {
        self.title = title
        self.symbol = symbol
    }

    @Environment(\.serviceRowIcons) private var icons

    var body: some View {
        if icons {
            label
        } else {
            Text(title)
        }
    }

    private var label: some View {
        Label {
            Text(title)
        } icon: {
            if let service {
                ServiceIcon(service: service)
            } else if let symbol {
                // in the text's colour, as the status line's symbol above it
                Image(systemName: symbol)
                    .frame(width: ServiceIconSize.row.rawValue * scale)
                    .accessibilityHidden(true)
            }
        }
    }
}

/// A source's icon at the start of its row (Android `SourceIcon`): the app's or service's own icon, and Sukrara's drop
/// in the text's secondary colour for another Sukrara (Android's `ds_ic_status_sukrara`).
struct SourceIcon: View {
    let kind: SourceKind
    var size: ServiceIconSize = .twoLines
    @ScaledMetric(relativeTo: .body) private var scale: CGFloat = 1

    var body: some View {
        if let service = AppService.of(kind) {
            ServiceIcon(service: service, size: size)
        } else {
            let side = size.rawValue * scale
            Image(systemName: "drop.fill")
                .resizable()
                .scaledToFit()
                .frame(width: side * 0.42, height: side * 0.56)
                .foregroundStyle(.secondary)
                .frame(width: side, height: side)
                .accessibilityHidden(true)
        }
    }
}
