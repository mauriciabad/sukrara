// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AVFoundation
import AppKit
import Synchronization
import Vision

/// The Mac's cameras for the QR scanner: the built-in one, an iPhone as Continuity Camera, an external one. AVFoundation
/// shows the picture and Vision reads the QR codes in it, on the Mac: nothing is recorded, kept or sent. Made only by
/// `AppServices` when the launch plan allows `.camera` (never in a dev, fixture, snapshot or test run); the capture
/// session runs only between `start` and `stop`, i.e. while the scanner is on screen. Needs `NSCameraUsageDescription`
/// and, under the hardened runtime, the `com.apple.security.device.camera` entitlement (Bundle/, verify-app.sh).
final class SystemCamera: QRCamera {
    private let capture = CaptureSession()
    private let discovery = AVCaptureDevice.DiscoverySession(
        deviceTypes: [.builtInWideAngleCamera, .continuityCamera, .external], mediaType: .video,
        position: .unspecified)
    private var observers: [NSObjectProtocol] = []
    private var preferred: PreferredCameraObserver?

    var changed: (() -> Void)? {
        didSet { changed == nil ? unobserve() : observe() }
    }

    var access: CameraAccess {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .notDetermined: .notAsked
        case .authorized: .allowed
        default: .denied
        }
    }

    func requestAccess() async -> Bool { await AVCaptureDevice.requestAccess(for: .video) }

    var cameras: [CameraChoice] {
        discovery.devices.map { CameraChoice(id: $0.uniqueID, name: $0.localizedName) }
    }

    var current: String? {
        let devices = discovery.devices
        if let preferred = AVCaptureDevice.systemPreferredCamera, devices.contains(preferred) {
            return preferred.uniqueID
        }
        return devices.first?.uniqueID
    }

    /// Recommended by AVFoundation: the pick becomes the user-preferred camera, so `systemPreferredCamera` puts it first
    /// (until an Apple camera such as a Continuity Camera iPhone appears) and macOS remembers it for Sukrara.
    func select(_ id: String) {
        guard let device = discovery.devices.first(where: { $0.uniqueID == id }) else { return }
        AVCaptureDevice.userPreferredCamera = device
    }

    func start(
        reading: @escaping @MainActor @Sendable ([String]) -> Void, failed: @escaping @MainActor @Sendable () -> Void
    ) {
        guard access == .allowed, let id = current else {
            failed()
            return
        }
        capture.start(deviceID: id, reading: reading, failed: failed)
    }

    func stop() { capture.stop() }

    func makePreview() -> NSView { CameraPreviewView(session: capture.session) }

    /// Cameras connecting and going, and the camera macOS prefers (an iPhone placed as Continuity Camera).
    private func observe() {
        guard observers.isEmpty else { return }
        let center = NotificationCenter.default
        for name in [AVCaptureDevice.wasConnectedNotification, AVCaptureDevice.wasDisconnectedNotification] {
            observers.append(
                center.addObserver(forName: name, object: nil, queue: .main) { @Sendable [weak self] _ in
                    MainActor.assumeIsolated { self?.changed?() }
                })
        }
        preferred = PreferredCameraObserver { @Sendable [weak self] in
            Task { @MainActor in self?.changed?() }
        }
    }

    private func unobserve() {
        observers.forEach(NotificationCenter.default.removeObserver)
        observers = []
        preferred = nil
    }

    isolated deinit {
        unobserve()
        capture.stop()
    }
}

/// Key-value observes `AVCaptureDevice.systemPreferredCamera` (a class property) for as long as it lives.
nonisolated private final class PreferredCameraObserver: NSObject, Sendable {
    private let fire: @Sendable () -> Void

    init(fire: @escaping @Sendable () -> Void) {
        self.fire = fire
        super.init()
        AVCaptureDevice.self.addObserver(self, forKeyPath: "systemPreferredCamera", options: [.new], context: nil)
    }

    override func observeValue(
        forKeyPath keyPath: String?, of object: Any?, change: [NSKeyValueChangeKey: Any]?,
        context: UnsafeMutableRawPointer?
    ) {
        fire()
    }

    deinit {
        AVCaptureDevice.self.removeObserver(self, forKeyPath: "systemPreferredCamera")
    }
}

/// The capture session and its frames, on their own queues: configured and started on `queue`, each frame read on
/// `frames` (late frames dropped, at most about eight reads a second). Only the texts of the QR codes leave it.
nonisolated final class CaptureSession: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate, @unchecked Sendable {
    struct Handlers: Sendable {
        var reading: @MainActor @Sendable ([String]) -> Void
        var failed: @MainActor @Sendable () -> Void
    }

    let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "app.mauri.sukrara.camera")
    private let frames = DispatchQueue(label: "app.mauri.sukrara.camera.frames")
    private let output = AVCaptureVideoDataOutput()
    private let handlers = Mutex<Handlers?>(nil)
    /// On `queue` only.
    private var inputID: String?
    /// On `frames` only.
    private var lastRead = CMTime.invalid
    private var runtimeError: NSObjectProtocol?

    override init() {
        super.init()
        runtimeError = NotificationCenter.default.addObserver(
            forName: AVCaptureSession.runtimeErrorNotification, object: session, queue: nil
        ) { @Sendable [weak self] _ in self?.fail() }
    }

    deinit {
        if let runtimeError { NotificationCenter.default.removeObserver(runtimeError) }
    }

    func start(
        deviceID: String, reading: @escaping @MainActor @Sendable ([String]) -> Void,
        failed: @escaping @MainActor @Sendable () -> Void
    ) {
        handlers.withLock { $0 = Handlers(reading: reading, failed: failed) }
        queue.async { [self] in
            if inputID != deviceID {
                guard configure(deviceID) else { return fail() }
            }
            if !session.isRunning { session.startRunning() }
        }
    }

    func stop() {
        handlers.withLock { $0 = nil }
        queue.async { [self] in
            if session.isRunning { session.stopRunning() }
        }
    }

    /// The camera `id` as the session's only input, its frames to Vision.
    private func configure(_ id: String) -> Bool {
        guard let device = AVCaptureDevice(uniqueID: id), let input = try? AVCaptureDeviceInput(device: device) else {
            return false
        }
        session.beginConfiguration()
        defer { session.commitConfiguration() }
        for old in session.inputs { session.removeInput(old) }
        inputID = nil
        guard session.canAddInput(input) else { return false }
        session.addInput(input)
        if session.outputs.isEmpty, session.canAddOutput(output) {
            output.alwaysDiscardsLateVideoFrames = true
            output.setSampleBufferDelegate(self, queue: frames)
            session.addOutput(output)
        }
        // enough for a phone's QR code at arm's length, without the cost of 4K frames
        if session.canSetSessionPreset(.hd1280x720) { session.sessionPreset = .hd1280x720 }
        inputID = id
        return true
    }

    private func fail() {
        guard let failed = handlers.withLock({ $0?.failed }) else { return }
        Task { @MainActor in failed() }
    }

    func captureOutput(
        _ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection
    ) {
        let time = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
        if lastRead.isValid, time.isValid, CMTimeGetSeconds(CMTimeSubtract(time, lastRead)) < 0.12 { return }
        lastRead = time
        guard let pixels = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let request = VNDetectBarcodesRequest()
        request.symbologies = [.qr]
        try? VNImageRequestHandler(cvPixelBuffer: pixels, options: [:]).perform([request])
        let texts = (request.results ?? []).compactMap(\.payloadStringValue)
        guard !texts.isEmpty, let reading = handlers.withLock({ $0?.reading }) else { return }
        Task { @MainActor in reading(texts) }
    }
}

/// The live picture, mirrored like FaceTime's so the phone moves on screen the way the hand moves it.
final class CameraPreviewView: NSView {
    private let preview: AVCaptureVideoPreviewLayer

    init(session: AVCaptureSession) {
        preview = AVCaptureVideoPreviewLayer(session: session)
        super.init(frame: .zero)
        wantsLayer = true
        layer?.backgroundColor = NSColor.black.cgColor
        preview.videoGravity = .resizeAspectFill
        preview.setAffineTransform(CGAffineTransform(scaleX: -1, y: 1))
        layer?.addSublayer(preview)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }

    override func layout() {
        super.layout()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        preview.bounds = bounds
        preview.position = CGPoint(x: bounds.midX, y: bounds.midY)
        CATransaction.commit()
    }
}
