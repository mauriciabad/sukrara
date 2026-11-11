// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import Foundation
import Observation

/// What macOS lets Sukrara do with the camera.
enum CameraAccess: Equatable, Sendable {
    /// Never asked: macOS asks on *Allow Camera*.
    case notAsked
    /// Refused in the prompt or in System Settings, or restricted by a profile / Screen Time.
    case denied
    case allowed
}

/// A camera the person can pick (the built-in one, an iPhone as Continuity Camera, an external one).
struct CameraChoice: Equatable, Identifiable, Sendable {
    var id: String
    var name: String
}

/// The camera behind the QR scanner: `SystemCamera` (AVFoundation + Vision) in the installed app, `SyntheticCamera`
/// in debug runs and snapshots, a fake in the tests. Nothing runs until `start`; `stop` ends it.
protocol QRCamera: AnyObject {
    var access: CameraAccess { get }
    /// Asks macOS (the system prompt); true = allowed.
    func requestAccess() async -> Bool
    /// The cameras connected now.
    var cameras: [CameraChoice] { get }
    /// The camera `start` uses: the person's pick, else the one macOS prefers (an iPhone that just became a Continuity
    /// Camera, the built-in one…).
    var current: String? { get }
    /// The person picked another camera: it becomes `current` (and macOS remembers it for Sukrara).
    func select(_ id: String)
    /// Shows `current` in the preview and hands the text of every QR code it sees to `reading` (main actor); `failed`
    /// when it can't start or stops by itself. Again while running: switches to the new `current`, nothing else.
    func start(
        reading: @escaping @MainActor @Sendable ([String]) -> Void, failed: @escaping @MainActor @Sendable () -> Void)
    /// Turns the camera off (the picture stops, nothing is read).
    func stop()
    /// The live picture for the scanner's viewfinder.
    func makePreview() -> NSView
    /// Called on the main actor when cameras connect or go, or the camera macOS prefers changes.
    var changed: (() -> Void)? { get set }
}

/// The follow step's QR scanner: the camera's state for the view and what it read. The camera runs only while the
/// scanner is on screen (`appear` / `disappear`) in a window that can be seen (`windowVisible`: not minimized, hidden or
/// covered), is allowed and has a camera; the first QR code that is a Sukrara
/// invite (`QRImport.invite`, the pasted link's parser) ends the scan and goes to `onInvite`; any other QR code says
/// so (`wrongCode`) and the scan goes on.
@Observable
final class QRScanner {
    enum Phase: Equatable, Sendable {
        /// This run doesn't use the camera (a dev copy or a render).
        case offline
        /// macOS hasn't asked yet: why, and *Allow Camera*.
        case notAsked
        /// Refused or restricted: how to turn it on, and *Open System Settings*.
        case denied
        /// No camera (or it couldn't start): connect one or use the iPhone; it starts as soon as one appears.
        case noCamera
        /// The camera is on, looking for the invite QR code.
        case scanning
        /// The invite was read (the camera is off); the follow step looks it up.
        case found
    }

    private(set) var phase: Phase
    /// A QR code that isn't a Sukrara invite is in view (cleared `wrongFor` after the last one).
    private(set) var wrongCode = false
    private(set) var cameras: [CameraChoice]
    private(set) var current: String?
    @ObservationIgnored private let camera: (any QRCamera)?
    @ObservationIgnored private let onInvite: (String) -> Void
    @ObservationIgnored private var visible = false
    @ObservationIgnored private var windowShown = true
    @ObservationIgnored private var running = false
    @ObservationIgnored private var lastWrong: ContinuousClock.Instant?
    @ObservationIgnored private var wrongTimer: Task<Void, Never>?
    /// How long a QR code that isn't an invite keeps the warning after it leaves the picture.
    @ObservationIgnored var wrongFor: Duration = .seconds(2)

    /// `camera` nil: this run doesn't use one. The phase is known at once (no camera started), so the first frame on
    /// screen is already the right one.
    init(camera: (any QRCamera)?, onInvite: @escaping (String) -> Void) {
        self.camera = camera
        self.onInvite = onInvite
        phase = Self.phase(of: camera)
        cameras = camera?.cameras ?? []
        current = camera?.current
    }

    static func phase(of camera: (any QRCamera)?) -> Phase {
        guard let camera else { return .offline }
        switch camera.access {
        case .notAsked: return .notAsked
        case .denied: return .denied
        case .allowed: return camera.cameras.isEmpty ? .noCamera : .scanning
        }
    }

    /// The scanner is on screen: the camera starts when it may, and follows cameras connecting and going.
    func appear() {
        guard !visible else { return }
        visible = true
        camera?.changed = { [weak self] in self?.refresh() }
        refresh()
    }

    /// Off screen (cancelled, the window closed, another step): the camera goes off.
    func disappear() {
        visible = false
        camera?.changed = nil
        stopCamera()
        wrongTimer?.cancel()
        wrongTimer = nil
    }

    /// The scanner's window can be seen, or not (minimized, Sukrara hidden, covered by other windows, the screen
    /// asleep): the camera follows.
    func windowVisible(_ shown: Bool) {
        guard shown != windowShown else { return }
        windowShown = shown
        refresh()
    }

    /// Reads the permission and the cameras again (a camera came or went, Sukrara is active again after System
    /// Settings) and starts or stops the camera to match.
    func refresh() {
        guard phase != .found, let camera else { return }
        cameras = camera.cameras
        current = camera.current
        phase = Self.phase(of: camera)
        if phase == .scanning && visible && windowShown { startCamera() } else { stopCamera() }
    }

    /// *Allow Camera*: macOS asks; allowed → scanning, refused → how to turn it on.
    func allow() async {
        guard phase == .notAsked, let camera else { return }
        _ = await camera.requestAccess()
        refresh()
    }

    /// The person picked another camera.
    func select(_ id: String) {
        guard id != current else { return }
        camera?.select(id)
        refresh()
    }

    /// The texts of the QR codes in one picture.
    func read(_ texts: [String]) {
        guard phase == .scanning else { return }
        if let link = QRImport.invite(in: texts) {
            phase = .found
            wrongCode = false
            wrongTimer?.cancel()
            wrongTimer = nil
            stopCamera()
            onInvite(link)
        } else if !texts.isEmpty {
            lastWrong = .now
            wrongCode = true
            if wrongTimer == nil { wrongTimer = Task { [weak self] in await self?.clearWrongCode() } }
        }
    }

    /// The live picture (nil without a camera).
    func makePreview() -> NSView? { camera?.makePreview() }

    private func startCamera() {
        guard let camera else { return }
        running = true
        camera.start(
            reading: { [weak self] texts in self?.read(texts) },
            failed: { [weak self] in self?.cameraFailed() })
    }

    private func stopCamera() {
        guard running else { return }
        running = false
        camera?.stop()
    }

    private func cameraFailed() {
        stopCamera()
        if phase == .scanning { phase = .noCamera }
    }

    /// The warning stays while wrong codes keep coming, and goes `wrongFor` after the last one.
    private func clearWrongCode() async {
        while wrongCode, let last = lastWrong {
            let due = last + wrongFor
            if ContinuousClock.now >= due {
                wrongCode = false
                break
            }
            try? await Task.sleep(until: due)
            if Task.isCancelled { return }
        }
        wrongTimer = nil
    }
}

extension FollowModel {
    /// *Scan QR Code…*: opens the scanner in the follow step (its camera starts once it's on screen).
    func startScanning() {
        clearFailure()
        guard scanner == nil, canFollow, invite == nil else { return }
        scanner = QRScanner(camera: makeCamera?()) { [weak self] link in
            Task { await self?.scanned(link) }
        }
    }

    /// *Cancel* in the scanner, or the follow step left the screen: the camera goes off and the scanner closes.
    func stopScanning() {
        scanner?.disappear()
        scanner = nil
    }

    /// The invite the scanner read: a moment to show it was read, then the same way as a pasted link (who is
    /// inviting, with *Follow* / *Cancel*). Nothing happens when the scanner was closed meanwhile.
    func scanned(_ link: String) async {
        if scanHandoff > .zero { try? await Task.sleep(for: scanHandoff) }
        guard scanner?.phase == .found else { return }
        await lookUp(qrText: link)
    }
}
