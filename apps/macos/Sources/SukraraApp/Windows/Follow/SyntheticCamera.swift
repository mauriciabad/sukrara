// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
#if DEBUG
    import AppKit
    import CoreImage
    import SukraraShare

    /// A made-up camera, debug builds only: never the Mac's. The snapshots' stand-in picture (a phone showing an invite
    /// QR code), and in a dev or fixture run, which may not open the real camera, a scripted feed (`feed()`).
    final class SyntheticCamera: QRCamera {
        var access: CameraAccess
        var cameras: [CameraChoice]
        var current: String?
        var changed: (() -> Void)?
        /// What the camera "sees" once started: each step's QR texts after its delay.
        private let script: [(Duration, [String])]
        private var feed: Task<Void, Never>?

        init(
            access: CameraAccess = .allowed,
            cameras: [CameraChoice] = [CameraChoice(id: "synthetic", name: "Synthetic Camera")],
            script: [(Duration, [String])] = []
        ) {
            self.access = access
            self.cameras = cameras
            self.current = cameras.first?.id
            self.script = script
        }

        /// A dev run's feed: a QR code that isn't an invite after 1.5 s (the warning), then the synthetic invite.
        static func feed() -> SyntheticCamera {
            SyntheticCamera(script: [(.milliseconds(1500), ["https://example.com/menu"]), (.seconds(3), [invite])])
        }

        /// A well-formed invite link no wearer made (looking it up finds nothing).
        static let invite = Wire.inviteBase + Base64URL.encode(Array("Sukrara-synthetic-invite".utf8))

        func requestAccess() async -> Bool {
            access = .allowed
            return true
        }

        func select(_ id: String) { current = id }

        func start(
            reading: @escaping @MainActor @Sendable ([String]) -> Void,
            failed: @escaping @MainActor @Sendable () -> Void
        ) {
            guard feed == nil, !script.isEmpty else { return }
            let script = script
            feed = Task {
                for (delay, texts) in script {
                    try? await Task.sleep(for: delay)
                    if Task.isCancelled { return }
                    reading(texts)
                }
            }
        }

        func stop() {
            feed?.cancel()
            feed = nil
        }

        func makePreview() -> NSView { StandInPreview(image: Self.standIn) }

        /// A dim room with a phone held up, its screen showing the synthetic invite's QR code.
        static let standIn: CGImage? = {
            let size = CGSize(width: 960, height: 540)
            guard
                let ctx = CGContext(
                    data: nil, width: Int(size.width), height: Int(size.height), bitsPerComponent: 8, bytesPerRow: 0,
                    space: CGColorSpace(name: CGColorSpace.sRGB)!,
                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
            else { return nil }
            let colors = [
                CGColor(srgbRed: 0.33, green: 0.36, blue: 0.42, alpha: 1),
                CGColor(srgbRed: 0.12, green: 0.13, blue: 0.16, alpha: 1),
            ]
            if let gradient = CGGradient(colorsSpace: nil, colors: colors as CFArray, locations: [0, 1]) {
                ctx.drawLinearGradient(gradient, start: CGPoint(x: 0, y: size.height), end: .zero, options: [])
            }
            let phone = CGRect(x: size.width / 2 - 125, y: 20, width: 250, height: 500)
            ctx.setFillColor(CGColor(srgbRed: 0.05, green: 0.05, blue: 0.06, alpha: 1))
            ctx.addPath(CGPath(roundedRect: phone, cornerWidth: 34, cornerHeight: 34, transform: nil))
            ctx.fillPath()
            let screen = phone.insetBy(dx: 10, dy: 10)
            ctx.setFillColor(CGColor(srgbRed: 1, green: 1, blue: 1, alpha: 1))
            ctx.addPath(CGPath(roundedRect: screen, cornerWidth: 26, cornerHeight: 26, transform: nil))
            ctx.fillPath()
            if let qr = QRImport.image(of: invite), let cg = CIContext().createCGImage(qr, from: qr.extent) {
                ctx.interpolationQuality = .none
                ctx.draw(cg, in: CGRect(x: screen.midX - 100, y: screen.midY - 100, width: 200, height: 200))
            }
            return ctx.makeImage()
        }()
    }

    /// The stand-in picture, filling the viewfinder like the camera's preview (drawn, so snapshots capture it).
    final class StandInPreview: NSView {
        private let image: CGImage?

        init(image: CGImage?) {
            self.image = image
            super.init(frame: .zero)
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }

        override func draw(_ dirtyRect: NSRect) {
            NSColor.black.setFill()
            bounds.fill()
            guard let image, let ctx = NSGraphicsContext.current?.cgContext else { return }
            let w = CGFloat(image.width)
            let h = CGFloat(image.height)
            let scale = max(bounds.width / w, bounds.height / h)
            let size = CGSize(width: w * scale, height: h * scale)
            ctx.draw(
                image,
                in: CGRect(
                    x: bounds.midX - size.width / 2, y: bounds.midY - size.height / 2, width: size.width,
                    height: size.height))
        }
    }
#endif
