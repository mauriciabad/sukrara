// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import SwiftUI

/// The follow step's QR scanner: the live camera with a frame to fit the code in, or why it can't look yet, with the
/// way on: *Allow Camera* (not asked yet), *Open System Settings* (refused), connect a camera or use the iPhone (none),
/// and *Cancel* (Escape) in every state. The frame breathes while it looks (not with Reduce Motion), turns orange with
/// *That QR code isn't a Sukrara invite* while another code is in view, and green with a check and a trackpad tap when
/// the invite is read; VoiceOver hears each change. Several cameras (the built-in one, an iPhone as Continuity Camera)
/// get a picker.
struct QRScannerView: View {
    let scanner: QRScanner
    var cancel: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 10) {
                header
                switch scanner.phase {
                case .scanning, .found:
                    Viewfinder(scanner: scanner)
                    Text(
                        L10n.t(
                            "Hold the wearer's phone up to the camera with their invite QR code showing (Sukrara › Share › Invite). It's read on this Mac: nothing is recorded or sent."
                        )
                    )
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                case .notAsked:
                    note(
                        "camera",
                        L10n.t(
                            "Sukrara uses the camera to read the invite QR code on the wearer's phone. It's read on this Mac: nothing is recorded or sent."
                        ))
                case .denied:
                    note(
                        "hand.raised",
                        L10n.t(
                            "Camera access is off for Sukrara. Turn it on in System Settings › Privacy & Security › Camera, then come back here."
                        ))
                case .noCamera:
                    note(
                        "video.slash",
                        L10n.t(
                            "No camera found. Connect one, or use your iPhone as this Mac's camera (Continuity Camera: the iPhone nearby with Wi-Fi and Bluetooth on). Scanning starts as soon as a camera is there."
                        ))
                case .offline:
                    note("video.slash", L10n.t("This offline run doesn't use the camera."))
                }
                if scanner.phase != .scanning && scanner.phase != .found {
                    Text(L10n.t("You can also paste the link or import a picture of the code."))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                HStack {
                    Spacer()
                    Button(L10n.t("Cancel"), action: cancel)
                        .keyboardShortcut(.cancelAction)
                    switch scanner.phase {
                    case .notAsked:
                        Button(L10n.t("Allow Camera")) { Task { await scanner.allow() } }
                            .keyboardShortcut(.defaultAction)
                            .buttonStyle(.borderedProminent)
                    case .denied:
                        Button(L10n.t("Open System Settings…")) { Self.openCameraSettings() }
                            .keyboardShortcut(.defaultAction)
                            .buttonStyle(.borderedProminent)
                    default:
                        EmptyView()
                    }
                }
            }
            .padding(4)
        }
        .accessibilityElement(children: .contain)
        .background(WindowVisibility { scanner.windowVisible($0) })
        .onAppear { scanner.appear() }
        .onDisappear { scanner.disappear() }
        // back from System Settings (or anywhere): the permission and the cameras may have changed
        .onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in
            scanner.refresh()
        }
        .onChange(of: scanner.wrongCode) { _, wrong in
            guard wrong else { return }
            NSHapticFeedbackManager.defaultPerformer.perform(.generic, performanceTime: .now)
            AccessibilityNotification.Announcement(L10n.t("That QR code isn't a Sukrara invite")).post()
        }
        .onChange(of: scanner.phase) { _, phase in
            guard phase == .found else { return }
            NSHapticFeedbackManager.defaultPerformer.perform(.levelChange, performanceTime: .now)
            AccessibilityNotification.Announcement(L10n.t("Invite read")).post()
        }
        .animation(reduceMotion ? .easeInOut(duration: 0.2) : .spring(duration: 0.35), value: scanner.phase)
    }

    private var header: some View {
        HStack(alignment: .firstTextBaseline) {
            Label(L10n.t("Scan the invite QR code"), systemImage: "qrcode.viewfinder")
                .font(.headline)
            Spacer()
            if scanner.cameras.count > 1 && scanner.phase == .scanning {
                Picker(
                    L10n.t("Camera"),
                    selection: Binding(get: { scanner.current ?? "" }, set: { scanner.select($0) })
                ) {
                    ForEach(scanner.cameras) { Text($0.name).tag($0.id) }
                }
                .labelsHidden()
                .fixedSize()
                .accessibilityLabel(L10n.t("Camera"))
                .help(L10n.t("Camera"))
            }
        }
    }

    private func note(_ symbol: String, _ text: String) -> some View {
        Label {
            Text(text).fixedSize(horizontal: false, vertical: true)
        } icon: {
            Image(systemName: symbol).foregroundStyle(.secondary)
        }
    }

    /// System Settings › Privacy & Security › Camera.
    static func openCameraSettings() {
        if let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Camera") {
            NSWorkspace.shared.open(url)
        }
    }
}

/// The camera's picture with the frame to fit the code in and a line under it saying what the scanner sees.
private struct Viewfinder: View {
    let scanner: QRScanner
    @State private var breathe = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let found = scanner.phase == .found
        let tint: Color = found ? .green : scanner.wrongCode ? .orange : .white
        ZStack {
            CameraPreview(scanner: scanner)
            if found { Color.black.opacity(0.35) }
            ScanFrame()
                .stroke(tint, style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round))
                .frame(width: 170, height: 170)
                .scaleEffect(breathe && !found && !reduceMotion ? 1.04 : 1)
                .shadow(color: .black.opacity(0.4), radius: 3)
            if found {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 52, weight: .semibold))
                    .symbolRenderingMode(.palette)
                    .foregroundStyle(.white, .green)
                    .transition(reduceMotion ? .opacity : .scale(scale: 0.4).combined(with: .opacity))
            }
            VStack {
                Spacer()
                Text(caption)
                    .font(.callout.weight(.medium))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(.black.opacity(0.6), in: Capsule())
                    .contentTransition(.opacity)
                    .padding(.bottom, 10)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 260)
        .background(.black)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .animation(reduceMotion ? .easeInOut(duration: 0.2) : .spring(duration: 0.3), value: scanner.wrongCode)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L10n.t("Camera view for the QR code"))
        .accessibilityValue(caption)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) { breathe = true }
        }
    }

    private var caption: String {
        if scanner.phase == .found { return L10n.t("Invite read") }
        return scanner.wrongCode
            ? L10n.t("That QR code isn't a Sukrara invite") : L10n.t("Fit the QR code inside the frame")
    }
}

/// The camera's live picture (`QRCamera.makePreview`).
private struct CameraPreview: NSViewRepresentable {
    let scanner: QRScanner

    func makeNSView(context: Context) -> NSView { scanner.makePreview() ?? NSView() }
    func updateNSView(_ view: NSView, context: Context) {}
}

/// Reports whether the window it's in can be seen (`NSWindow.occlusionState`): minimized, hidden or covered → false.
private struct WindowVisibility: NSViewRepresentable {
    var changed: (Bool) -> Void

    func makeNSView(context: Context) -> Watcher { Watcher(changed: changed) }
    func updateNSView(_ view: Watcher, context: Context) { view.changed = changed }

    final class Watcher: NSView {
        var changed: (Bool) -> Void
        private var observer: NSObjectProtocol?

        init(changed: @escaping (Bool) -> Void) {
            self.changed = changed
            super.init(frame: .zero)
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }

        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            if let observer { NotificationCenter.default.removeObserver(observer) }
            observer = nil
            guard let window else { return }
            observer = NotificationCenter.default.addObserver(
                forName: NSWindow.didChangeOcclusionStateNotification, object: window, queue: .main
            ) { @Sendable [weak self] _ in
                MainActor.assumeIsolated { self?.report() }
            }
            report()
        }

        private func report() {
            guard let window else { return }
            changed(window.occlusionState.contains(.visible))
        }

        isolated deinit {
            if let observer { NotificationCenter.default.removeObserver(observer) }
        }
    }
}

/// The four corners of a square: where the code goes.
nonisolated private struct ScanFrame: Shape {
    func path(in rect: CGRect) -> Path {
        let arm = rect.width * 0.22
        var p = Path()
        for (corner, dx, dy) in [
            (CGPoint(x: rect.minX, y: rect.minY), 1.0, 1.0), (CGPoint(x: rect.maxX, y: rect.minY), -1.0, 1.0),
            (CGPoint(x: rect.minX, y: rect.maxY), 1.0, -1.0), (CGPoint(x: rect.maxX, y: rect.maxY), -1.0, -1.0),
        ] {
            p.move(to: CGPoint(x: corner.x, y: corner.y + dy * arm))
            p.addLine(to: corner)
            p.addLine(to: CGPoint(x: corner.x + dx * arm, y: corner.y))
        }
        return p
    }
}

/// *Scan QR Code…* where following starts outside the follow step (Settings › Following): opens the follow step with
/// the scanner.
struct ScanQRButton: View {
    let follow: FollowModel
    var openFollow: () -> Void

    var body: some View {
        Button(L10n.t("Scan QR Code…")) {
            follow.startScanning()
            openFollow()
        }
    }
}
