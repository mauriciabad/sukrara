// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import SwiftUI

/// About Sukrara (the app menu's first item, the right-click menu, Settings › General), laid out like the standard
/// macOS About panel: the app icon, the name, the version with its build (selectable, for a bug report) and which
/// version it is, the copyright and licence; then what only Sukrara needs there: the reminder that it isn't a medical
/// device (App Review 1.4.1), the privacy policy (App Review 5.1.1), the website and the source, and Sukrara on other
/// devices (the website's page per app). A small window without title text, minimize or zoom (`AboutWindow`).
struct AboutView: View {
    let version: String
    let build: String
    let distribution: Distribution
    /// The app's icon (the bundle's; a render passes the brand icon).
    var icon: NSImage = NSApp.applicationIconImage

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown = false

    var body: some View {
        VStack(spacing: 0) {
            Image(nsImage: icon)
                .resizable()
                .interpolation(.high)
                .frame(width: 96, height: 96)
                .scaleEffect(shown || reduceMotion ? 1 : 0.85)
                .opacity(shown || reduceMotion ? 1 : 0)
                .accessibilityHidden(true)
                .padding(.bottom, 10)
            Text(verbatim: "Sukrara")
                .font(.title2.weight(.semibold))
                .accessibilityAddTraits(.isHeader)
            Text(Self.versionLine(version, build))
                .font(.callout).foregroundStyle(.secondary)
                .textSelection(.enabled)
                .padding(.top, 2)
            Text(Self.distributionName(distribution))
                .font(.callout).foregroundStyle(.secondary)
            Label {
                Text(
                    L10n.t(
                        "Sukrara isn't a medical device: values can be wrong, late or missing, and alerts may not ring. Check your sensor's app or a meter, and ask your doctor, before you decide on a treatment."
                    )
                )
                .fixedSize(horizontal: false, vertical: true)
            } icon: {
                // the colour only on the symbol: the words keep full contrast
                Image(systemName: "stethoscope").foregroundStyle(.tint)
            }
            .font(.callout)
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.quaternary.opacity(0.5), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            .accessibilityElement(children: .combine)
            .padding(.top, 16)
            HStack(spacing: 16) {
                link(L10n.t("Website"), Self.websiteURL)
                link(L10n.t("Privacy Policy"), Self.privacyURL)
                link(L10n.t("Source Code"), Self.sourceURL)
            }
            .padding(.top, 14)
            VStack(spacing: 2) {
                ForEach(OtherDevice.all) { device in
                    Link(destination: device.url) {
                        Label(device.name, systemImage: "smartphone")
                    }
                    .help(device.url.absoluteString)
                    .accessibilityHint(L10n.t("Opens \(device.name)'s page on the website"))
                    Text(device.summary).font(.caption).foregroundStyle(.secondary)
                }
            }
            .padding(.top, 12)
            VStack(spacing: 2) {
                Text(verbatim: "© 2026 Maurici Abad")
                Text(L10n.t("Free software under the GNU GPL 3.0 or later"))
            }
            .font(.caption).foregroundStyle(.secondary)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityElement(children: .combine)
            .padding(.top, 16)
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, 28)
        .padding(.top, 8)
        .padding(.bottom, 22)
        .frame(width: 360)
        .onAppear {
            withAnimation(.spring(response: 0.45, dampingFraction: 0.7)) { shown = true }
        }
    }

    private func link(_ title: String, _ url: URL) -> some View {
        Link(title, destination: url).help(url.absoluteString)
    }

    static let websiteURL = URL(string: "https://sukrara.mauri.app/")!
    static let privacyURL = URL(string: "https://sukrara.mauri.app/privacy")!
    static let sourceURL = URL(string: "https://github.com/mauriciabad/sukrara")!

    /// "Version 0.14.5 (14005)" as the standard About panel writes it; a build equal to the version isn't repeated.
    static func versionLine(_ version: String, _ build: String) -> String {
        build.isEmpty || build == version ? L10n.t("Version \(version)") : L10n.t("Version \(version) (\(build))")
    }

    /// "GitHub version" / "App Store version" (Android: "Google Play version").
    static func distributionName(_ distribution: Distribution) -> String {
        switch distribution {
        case .github: L10n.t("GitHub version")
        case .appStore: L10n.t("App Store version")
        }
    }
}

/// The About window's frame, like the standard About panel: no title text over a transparent title bar (the content
/// runs under it), only the close button, centred on the screen each time it opens.
enum AboutWindow {
    static func configure(_ window: NSWindow) {
        window.styleMask = [.titled, .closable, .fullSizeContentView]
        window.titleVisibility = .hidden
        window.titlebarAppearsTransparent = true
        window.isMovableByWindowBackground = true
        window.center()
    }
}
