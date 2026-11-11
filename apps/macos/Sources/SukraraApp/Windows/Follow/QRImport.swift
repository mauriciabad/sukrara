// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import CoreImage
import CoreImage.CIFilterBuiltins
import Foundation
import SukraraShare

/// Reads a Sukrara invite from a QR code in an image (a screenshot of the phone's invite, a photo). Offline: Core
/// Image's QR detector, nothing leaves the Mac. The camera scanner (`QRScanner`) picks its invite the same way.
public enum QRImport {
    /// The first QR code in the image whose text is a Sukrara invite link, or nil.
    public static func inviteText(in image: CIImage) -> String? {
        let detector = CIDetector(
            ofType: CIDetectorTypeQRCode, context: nil, options: [CIDetectorAccuracy: CIDetectorAccuracyHigh])
        return invite(in: (detector?.features(in: image) ?? []).compactMap { ($0 as? CIQRCodeFeature)?.messageString })
    }

    /// The first of the QR codes' texts that is a Sukrara invite link (`InviteLink.parse`, the pasted link's parser),
    /// trimmed; nil when none is.
    public static func invite(in texts: [String]) -> String? {
        texts.lazy.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.first { InviteLink.parse($0) != nil }
    }

    /// The image file at `url` (png, jpeg, heic…), or nil when it isn't an image.
    public static func inviteText(atURL url: URL) -> String? {
        CIImage(contentsOf: url).flatMap(inviteText(in:))
    }

    /// A QR image of `text` (tests and the snapshot of the import step).
    public static func image(of text: String) -> CIImage? {
        let f = CIFilter.qrCodeGenerator()
        f.message = Data(text.utf8)
        return f.outputImage?.transformed(by: CGAffineTransform(scaleX: 8, y: 8))
    }
}
