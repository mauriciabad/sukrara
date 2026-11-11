// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import Foundation
import SukraraShare
import Testing

@testable import SukraraApp

/// A camera that records what the scanner asks of it (no AVFoundation: tests never open the Mac's camera).
final class FakeCamera: QRCamera {
    var access: CameraAccess
    var cameras: [CameraChoice]
    var current: String?
    var changed: (() -> Void)?
    var grants = true
    var asked = 0
    var starts = 0
    var stops = 0
    var running = false
    var selected: [String] = []
    var reading: (@MainActor @Sendable ([String]) -> Void)?
    var failed: (@MainActor @Sendable () -> Void)?

    init(access: CameraAccess = .allowed, cameras: [String] = ["built-in"]) {
        self.access = access
        self.cameras = cameras.map { CameraChoice(id: $0, name: $0) }
        current = cameras.first
    }

    func requestAccess() async -> Bool {
        asked += 1
        access = grants ? .allowed : .denied
        return grants
    }

    func select(_ id: String) {
        selected.append(id)
        current = id
    }

    func start(
        reading: @escaping @MainActor @Sendable ([String]) -> Void, failed: @escaping @MainActor @Sendable () -> Void
    ) {
        starts += 1
        running = true
        self.reading = reading
        self.failed = failed
    }

    func stop() {
        stops += 1
        running = false
        reading = nil
    }

    func makePreview() -> NSView { NSView() }

    /// Cameras came or went.
    func connect(_ ids: [String]) {
        cameras = ids.map { CameraChoice(id: $0, name: $0) }
        current = ids.first
        changed?()
    }
}

@Suite struct QRScannerTests {
    let invite = QRImportTests.link

    @Test func eachStateIsKnownBeforeTheCameraStarts() {
        #expect(QRScanner(camera: nil) { _ in }.phase == .offline)
        #expect(QRScanner(camera: FakeCamera(access: .notAsked)) { _ in }.phase == .notAsked)
        #expect(QRScanner(camera: FakeCamera(access: .denied)) { _ in }.phase == .denied)
        #expect(QRScanner(camera: FakeCamera(cameras: [])) { _ in }.phase == .noCamera)
        let camera = FakeCamera()
        let scanner = QRScanner(camera: camera) { _ in }
        #expect(scanner.phase == .scanning)
        // nothing runs until the scanner is on screen
        #expect(camera.starts == 0)
    }

    @Test func theCameraRunsOnlyWhileTheScannerIsOnScreen() {
        let camera = FakeCamera()
        let scanner = QRScanner(camera: camera) { _ in }
        scanner.appear()
        #expect(camera.running && camera.starts == 1)
        scanner.disappear()
        #expect(!camera.running && camera.changed == nil)
        // a camera change while off screen starts nothing
        camera.connect(["built-in", "iphone"])
        scanner.refresh()
        #expect(!camera.running)
    }

    @Test func theCameraIsOffWhileTheWindowCantBeSeen() {
        let camera = FakeCamera()
        let scanner = QRScanner(camera: camera) { _ in }
        scanner.appear()
        scanner.windowVisible(false)  // minimized, hidden or covered
        #expect(!camera.running && scanner.phase == .scanning)
        camera.connect(["built-in", "iphone"])
        #expect(!camera.running)
        scanner.windowVisible(true)
        #expect(camera.running)
    }

    @Test func withoutPermissionTheCameraNeverStarts() async {
        let camera = FakeCamera(access: .notAsked)
        let scanner = QRScanner(camera: camera) { _ in }
        scanner.appear()
        #expect(camera.starts == 0 && scanner.phase == .notAsked)
        camera.grants = false
        await scanner.allow()
        #expect(camera.asked == 1 && scanner.phase == .denied && camera.starts == 0)
        // allowed later in System Settings: back in Sukrara it scans
        camera.access = .allowed
        scanner.refresh()
        #expect(scanner.phase == .scanning && camera.running)
    }

    @Test func allowingStartsScanning() async {
        let camera = FakeCamera(access: .notAsked)
        let scanner = QRScanner(camera: camera) { _ in }
        scanner.appear()
        await scanner.allow()
        #expect(scanner.phase == .scanning && camera.running)
    }

    @Test func aCameraThatArrivesStartsTheScanAndOneThatGoesStopsIt() {
        let camera = FakeCamera(cameras: [])
        let scanner = QRScanner(camera: camera) { _ in }
        scanner.appear()
        #expect(scanner.phase == .noCamera && !camera.running)
        camera.connect(["iphone"])  // Continuity Camera
        #expect(scanner.phase == .scanning && camera.running && scanner.current == "iphone")
        camera.connect([])
        #expect(scanner.phase == .noCamera && !camera.running)
    }

    @Test func aCameraThatFailsSaysSo() {
        let camera = FakeCamera()
        let scanner = QRScanner(camera: camera) { _ in }
        scanner.appear()
        camera.failed?()
        #expect(scanner.phase == .noCamera && !camera.running)
    }

    @Test func pickingAnotherCameraSwitchesToIt() {
        let camera = FakeCamera(cameras: ["built-in", "iphone"])
        let scanner = QRScanner(camera: camera) { _ in }
        scanner.appear()
        scanner.select("iphone")
        #expect(camera.selected == ["iphone"] && scanner.current == "iphone" && camera.running)
        scanner.select("iphone")
        #expect(camera.selected == ["iphone"])
    }

    @Test func anotherQRCodeSaysSoAndScanningGoesOn() {
        let camera = FakeCamera()
        var read: [String] = []
        let scanner = QRScanner(camera: camera) { read.append($0) }
        scanner.appear()
        scanner.read(["https://example.com/menu"])
        #expect(scanner.wrongCode && scanner.phase == .scanning && camera.running && read.isEmpty)
        // a picture with no code changes nothing
        scanner.read([])
        #expect(scanner.wrongCode && scanner.phase == .scanning)
    }

    @Test func theWarningGoesAfterTheWrongCodeLeaves() async throws {
        let scanner = QRScanner(camera: FakeCamera()) { _ in }
        scanner.wrongFor = .milliseconds(20)
        scanner.appear()
        scanner.read(["hello"])
        #expect(scanner.wrongCode)
        for _ in 0..<100 where scanner.wrongCode { try await Task.sleep(for: .milliseconds(10)) }
        #expect(!scanner.wrongCode)
    }

    @Test func theInviteEndsTheScanOnce() {
        let camera = FakeCamera()
        var read: [String] = []
        let scanner = QRScanner(camera: camera) { read.append($0) }
        scanner.appear()
        scanner.read(["https://example.com/menu"])
        // the invite wins over another code in the same picture, with the pasted link's parser (trimmed)
        scanner.read(["https://example.com", " \(invite)\n"])
        #expect(read == [invite] && scanner.phase == .found && !scanner.wrongCode && !camera.running)
        scanner.read([invite])
        scanner.refresh()
        #expect(read == [invite] && scanner.phase == .found && !camera.running)
    }

    @Test func theSharedParserPicksTheInvite() {
        #expect(QRImport.invite(in: ["x", "sukrara:join#short", " \(invite) "]) == invite)
        #expect(QRImport.invite(in: ["https://sukrara.mauri.app/join#nope"]) == nil)
    }
}

/// The follow step's way into the scanner and out of it.
@Suite struct FollowScanTests {
    @Test func scanningOpensTheScannerAndCancelTurnsTheCameraOff() throws {
        let camera = FakeCamera()
        let m = FollowModel(status: .notFollowing, actions: nil, macName: "Mac")
        m.makeCamera = { camera }
        m.startScanning()
        try #require(m.scanner).appear()
        #expect(camera.running)
        m.stopScanning()
        #expect(m.scanner == nil && !camera.running)
    }

    @Test func noScannerWhileFollowingOrWithAnInviteShown() {
        let following = FollowModel(
            status: .following(wearer: "Marta", lastReading: nil, canWrite: false), actions: nil, macName: "Mac")
        following.makeCamera = { FakeCamera() }
        following.startScanning()
        #expect(following.scanner == nil)
        let invited = FollowModel(status: .notFollowing, actions: nil, macName: "Mac")
        invited.invite = InviteConfirmTests.marta
        invited.startScanning()
        #expect(invited.scanner == nil)
    }

    @Test func aRunWithoutACameraSaysSo() {
        let m = FollowModel(status: .notFollowing, actions: nil, macName: "Mac")
        m.startScanning()
        #expect(m.scanner?.phase == .offline)
    }

    @Test func theScannedInviteShowsWhoIsInvitingAndNothingIsSent() async throws {
        let a = InviteConfirmTests.Recorder()
        let camera = FakeCamera()
        let m = FollowModel(status: .notFollowing, actions: a, macName: "Mac")
        m.makeCamera = { camera }
        m.scanHandoff = .milliseconds(5)
        m.startScanning()
        m.scanner?.appear()
        m.scanner?.read([QRImportTests.link])
        // the scanner shows the check first; then the same lookup as a pasted link
        #expect(m.scanner?.phase == .found && a.lookups.isEmpty)
        for _ in 0..<200 where m.invite == nil { try await Task.sleep(for: .milliseconds(10)) }
        #expect(a.lookups == [QRImportTests.link] && m.invite == InviteConfirmTests.marta && a.joined.isEmpty)
        #expect(m.scanner == nil && !camera.running && m.linkText == QRImportTests.link)
    }

    @Test func aScanCancelledBeforeTheLookupLooksNothingUp() async throws {
        let a = InviteConfirmTests.Recorder()
        let m = FollowModel(status: .notFollowing, actions: a, macName: "Mac")
        m.makeCamera = { FakeCamera() }
        m.scanHandoff = .milliseconds(5)
        m.startScanning()
        m.scanner?.read([QRImportTests.link])
        m.stopScanning()
        try await Task.sleep(for: .milliseconds(100))
        #expect(a.lookups.isEmpty && m.invite == nil)
    }

    @Test func aPastedOrOpenedLinkClosesTheScanner() async {
        let a = InviteConfirmTests.Recorder()
        let camera = FakeCamera()
        let m = FollowModel(status: .notFollowing, actions: a, macName: "Mac")
        m.makeCamera = { camera }
        m.startScanning()
        m.scanner?.appear()
        await m.open(link: QRImportTests.link)
        #expect(m.scanner == nil && !camera.running && m.invite == InviteConfirmTests.marta)
    }
}
