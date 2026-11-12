// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Draws the Mac installer's two icons (#181: the disk image used the app's own icon) from the app icon's glyph:
//   swift assets/brand/sukrara/macos/dmg/icons.swift      (from the repo root) → Volume.icns, DiskImage.icns + previews
// Volume.icns: the mounted volume (Finder's sidebar, the desktop, the window's title): a drive in the macOS shape (a
// body over a silver base with its light) whose body is the app icon (the brand gradient and the cube glyph with its
// lime dot). DiskImage.icns: the .dmg file itself: a document page with that drive on it, as macOS draws a disk image.
// The glyph is read from ../AppIcon.svg, so the icons follow the brand; colours from ../../brand.json. Re-run after
// the app icon changes and commit the results (Bundle/dmg.sh uses them).
import AppKit

let here = URL(fileURLWithPath: CommandLine.arguments[0]).deletingLastPathComponent()
let brand = here.deletingLastPathComponent().deletingLastPathComponent()

// MARK: - The glyph from AppIcon.svg

struct Shape {
    var path: CGPath
    var fill: CGColor?
    var stroke: (CGColor, CGFloat)?
}

func color(_ hex: String, _ alpha: Double) -> CGColor {
    let v = Int(hex.dropFirst(), radix: 16)!
    return CGColor(
        srgbRed: CGFloat((v >> 16) & 255) / 255, green: CGFloat((v >> 8) & 255) / 255, blue: CGFloat(v & 255) / 255,
        alpha: alpha)
}

/// The SVG path commands AppIcon.svg uses: M, L, Q, Z and A (only half circles, two per circle).
func parse(_ d: String) -> CGPath {
    let p = CGMutablePath()
    let tokens = d.replacingOccurrences(of: ",", with: " ")
        .replacingOccurrences(of: "([MLQZA])", with: " $1 ", options: .regularExpression)
        .split(separator: " ").map(String.init)
    var i = 0
    var cur = CGPoint.zero
    func num() -> CGFloat { defer { i += 1 }; return CGFloat(Double(tokens[i])!) }
    var cmd = ""
    while i < tokens.count {
        if Double(tokens[i]) == nil { cmd = tokens[i]; i += 1 }
        switch cmd {
        case "M": cur = CGPoint(x: num(), y: num()); p.move(to: cur)
        case "L": cur = CGPoint(x: num(), y: num()); p.addLine(to: cur)
        case "Q":
            let c = CGPoint(x: num(), y: num())
            cur = CGPoint(x: num(), y: num())
            p.addQuadCurve(to: cur, control: c)
        case "A":
            let r = num()
            _ = num(); _ = num(); _ = num()  // ry, rotation, large arc (half circles only)
            let sweep = num()
            let end = CGPoint(x: num(), y: num())
            let mid = CGPoint(x: (cur.x + end.x) / 2, y: (cur.y + end.y) / 2)
            precondition(abs(hypot(end.x - cur.x, end.y - cur.y) - 2 * r) < 0.01, "only half circles")
            let a0 = atan2(cur.y - mid.y, cur.x - mid.x)
            // SVG's y points down: sweep 0 runs counter-clockwise on screen = clockwise in these coordinates' maths
            p.addArc(center: mid, radius: r, startAngle: a0, endAngle: a0 + .pi, clockwise: sweep == 0)
            cur = end
        case "Z": p.closeSubpath()
        default: fatalError("path command \(cmd)")
        }
    }
    return p
}

func glyph() -> (shapes: [Shape], box: CGRect) {
    let svg = try! String(contentsOf: brand.appending(path: "macos/AppIcon.svg"), encoding: .utf8)
    let group = svg.components(separatedBy: "<g transform=")[1].components(separatedBy: "</g>")[0]
    func attr(_ tag: String, _ name: String) -> String? {
        guard let r = tag.range(of: " \(name)=\"([^\"]*)\"", options: .regularExpression) else { return nil }
        return String(tag[r].split(separator: "\"")[1])
    }
    var shapes: [Shape] = []
    var box = CGRect.null
    for tag in group.components(separatedBy: "<path").dropFirst() {
        let path = parse(attr(tag, "d")!)
        var s = Shape(path: path, fill: nil, stroke: nil)
        if let f = attr(tag, "fill"), f != "none" { s.fill = color(f, Double(attr(tag, "fill-opacity") ?? "1")!) }
        if let st = attr(tag, "stroke") {
            let w = CGFloat(Double(attr(tag, "stroke-width")!)!)
            s.stroke = (color(st, Double(attr(tag, "stroke-opacity") ?? "1")!), w)
            box = box.union(path.boundingBox.insetBy(dx: -w / 2, dy: -w / 2))
        } else {
            box = box.union(path.boundingBox)
        }
        shapes.append(s)
    }
    return (shapes, box)
}

let (glyphShapes, glyphBox) = glyph()
let top = color("#1BB5C9", 1), bottom = color("#0B2E5E", 1), accent = color("#C6F432", 1)  // brand.json

// MARK: - Drawing (1024 units, y down like the SVG)

func gradient(_ ctx: CGContext, _ colors: [CGColor], from a: CGPoint, to b: CGPoint) {
    let g = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors as CFArray, locations: nil)!
    ctx.drawLinearGradient(g, start: a, end: b, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
}

/// The glyph `w` units wide centred at `c`.
func drawGlyph(_ ctx: CGContext, center c: CGPoint, width w: CGFloat) {
    let k = w / glyphBox.width
    ctx.saveGState()
    ctx.translateBy(x: c.x - glyphBox.midX * k, y: c.y - glyphBox.midY * k)
    ctx.scaleBy(x: k, y: k)
    for s in glyphShapes {
        ctx.addPath(s.path)
        if let f = s.fill { ctx.setFillColor(f); ctx.fillPath() }
        if let (col, lw) = s.stroke {
            ctx.addPath(s.path)
            ctx.setStrokeColor(col)
            ctx.setLineWidth(lw)
            ctx.setLineCap(.round)
            ctx.strokePath()
        }
    }
    ctx.restoreGState()
}

/// The drive in `r`: a body in the brand gradient with the glyph, over a silver base with the activity light.
func drawDrive(_ ctx: CGContext, in r: CGRect) {
    let u = r.width / 704  // proportions drawn at 704 wide
    let baseH = 120 * u, radius = 92 * u
    let body = CGRect(x: r.minX, y: r.minY, width: r.width, height: r.height - baseH * 0.5)
    let base = CGRect(x: r.minX, y: r.maxY - baseH, width: r.width, height: baseH)

    // shadow under the whole drive
    ctx.saveGState()
    ctx.setShadow(offset: CGSize(width: 0, height: 14 * u), blur: 30 * u, color: CGColor(gray: 0, alpha: 0.32))
    ctx.addPath(CGPath(roundedRect: body.union(base), cornerWidth: radius, cornerHeight: radius, transform: nil))
    ctx.setFillColor(bottom)
    ctx.fillPath()
    ctx.restoreGState()

    // the body
    ctx.saveGState()
    ctx.addPath(CGPath(roundedRect: body, cornerWidth: radius, cornerHeight: radius, transform: nil))
    ctx.clip()
    gradient(ctx, [top, bottom], from: CGPoint(x: body.minX, y: body.minY), to: CGPoint(x: body.minX, y: body.maxY))
    // a soft sheen across the top
    gradient(
        ctx, [CGColor(gray: 1, alpha: 0.22), CGColor(gray: 1, alpha: 0)], from: CGPoint(x: body.minX, y: body.minY),
        to: CGPoint(x: body.minX, y: body.minY + body.height * 0.45))
    ctx.restoreGState()
    drawGlyph(
        ctx, center: CGPoint(x: body.midX, y: body.minY + (body.height - baseH * 0.5) * 0.5), width: r.width * 0.5)

    // the silver base with its light
    let capsule = CGPath(roundedRect: base, cornerWidth: baseH / 2, cornerHeight: baseH / 2, transform: nil)
    ctx.saveGState()
    ctx.addPath(capsule)
    ctx.clip()
    gradient(
        ctx, [CGColor(gray: 0.97, alpha: 1), CGColor(gray: 0.86, alpha: 1), CGColor(gray: 0.74, alpha: 1)],
        from: CGPoint(x: base.minX, y: base.minY), to: CGPoint(x: base.minX, y: base.maxY))
    ctx.restoreGState()
    ctx.addPath(capsule)
    ctx.setStrokeColor(CGColor(gray: 0, alpha: 0.18))
    ctx.setLineWidth(2 * u)
    ctx.strokePath()
    let led = CGRect(x: base.maxX - 76 * u, y: base.midY - 11 * u, width: 22 * u, height: 22 * u)
    ctx.saveGState()
    ctx.setShadow(offset: .zero, blur: 14 * u, color: accent)
    ctx.setFillColor(accent)
    ctx.fillEllipse(in: led)
    ctx.restoreGState()
}

/// Volume.icns: the drive alone on the Big Sur icon grid.
func drawVolume(_ ctx: CGContext) {
    drawDrive(ctx, in: CGRect(x: 160, y: 132, width: 704, height: 776))
}

/// DiskImage.icns: a document page (folded top-right corner) with the drive on it.
func drawDiskImage(_ ctx: CGContext) {
    let page = CGRect(x: 192, y: 84, width: 640, height: 856), fold: CGFloat = 176, rad: CGFloat = 36
    let outline = CGMutablePath()
    outline.move(to: CGPoint(x: page.minX + rad, y: page.minY))
    outline.addLine(to: CGPoint(x: page.maxX - fold, y: page.minY))
    outline.addLine(to: CGPoint(x: page.maxX, y: page.minY + fold))
    outline.addArc(
        tangent1End: CGPoint(x: page.maxX, y: page.maxY), tangent2End: CGPoint(x: page.minX, y: page.maxY), radius: rad)
    outline.addArc(
        tangent1End: CGPoint(x: page.minX, y: page.maxY), tangent2End: CGPoint(x: page.minX, y: page.minY), radius: rad)
    outline.addArc(
        tangent1End: CGPoint(x: page.minX, y: page.minY), tangent2End: CGPoint(x: page.maxX, y: page.minY), radius: rad)
    outline.closeSubpath()
    ctx.saveGState()
    ctx.setShadow(offset: CGSize(width: 0, height: 10), blur: 24, color: CGColor(gray: 0, alpha: 0.28))
    ctx.addPath(outline)
    ctx.setFillColor(CGColor(gray: 1, alpha: 1))
    ctx.fillPath()
    ctx.restoreGState()
    ctx.saveGState()
    ctx.addPath(outline)
    ctx.clip()
    gradient(
        ctx, [CGColor(gray: 1, alpha: 1), CGColor(gray: 0.95, alpha: 1)], from: CGPoint(x: 0, y: page.minY),
        to: CGPoint(x: 0, y: page.maxY))
    ctx.restoreGState()
    ctx.addPath(outline)
    ctx.setStrokeColor(CGColor(gray: 0, alpha: 0.12))
    ctx.setLineWidth(2)
    ctx.strokePath()
    // the folded corner
    let corner = CGMutablePath()
    corner.move(to: CGPoint(x: page.maxX - fold, y: page.minY))
    corner.addLine(to: CGPoint(x: page.maxX - fold, y: page.minY + fold - 24))
    corner.addQuadCurve(
        to: CGPoint(x: page.maxX - fold + 24, y: page.minY + fold),
        control: CGPoint(x: page.maxX - fold, y: page.minY + fold))
    corner.addLine(to: CGPoint(x: page.maxX, y: page.minY + fold))
    corner.closeSubpath()
    ctx.saveGState()
    ctx.setShadow(offset: CGSize(width: -4, height: 6), blur: 14, color: CGColor(gray: 0, alpha: 0.18))
    ctx.addPath(corner)
    ctx.setFillColor(CGColor(gray: 0.93, alpha: 1))
    ctx.fillPath()
    ctx.restoreGState()
    ctx.addPath(corner)
    ctx.setStrokeColor(CGColor(gray: 0, alpha: 0.10))
    ctx.setLineWidth(2)
    ctx.strokePath()
    drawDrive(ctx, in: CGRect(x: 316, y: 330, width: 392, height: 432))
}

// MARK: - Output

func render(_ px: Int, _ draw: (CGContext) -> Void) -> NSBitmapImageRep {
    let rep = NSBitmapImageRep(
        bitmapDataPlanes: nil, pixelsWide: px, pixelsHigh: px, bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true,
        isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    let ctx = NSGraphicsContext(bitmapImageRep: rep)!.cgContext
    let k = CGFloat(px) / 1024
    ctx.translateBy(x: 0, y: CGFloat(px))
    ctx.scaleBy(x: k, y: -k)  // y down, 1024 units
    draw(ctx)
    return rep
}

func png(_ rep: NSBitmapImageRep, _ url: URL) {
    try! rep.representation(using: .png, properties: [:])!.write(to: url)
}

let tmp = FileManager.default.temporaryDirectory.appending(path: "sukrara-dmg-icons-\(getpid())")
for (name, draw) in [("Volume", drawVolume), ("DiskImage", drawDiskImage)] as [(String, (CGContext) -> Void)] {
    let set = tmp.appending(path: "\(name).iconset")
    try! FileManager.default.createDirectory(at: set, withIntermediateDirectories: true)
    for pt in [16, 32, 128, 256, 512] {
        png(render(pt, draw), set.appending(path: "icon_\(pt)x\(pt).png"))
        png(render(pt * 2, draw), set.appending(path: "icon_\(pt)x\(pt)@2x.png"))
    }
    let out = here.appending(path: "\(name).icns")
    let p = Process()
    p.executableURL = URL(fileURLWithPath: "/usr/bin/iconutil")
    p.arguments = ["-c", "icns", set.path, "-o", out.path]
    try! p.run()
    p.waitUntilExit()
    precondition(p.terminationStatus == 0, "iconutil failed for \(name)")
    png(render(512, draw), here.appending(path: "\(name)-512.png"))
    print("wrote \(out.path)")
}
try? FileManager.default.removeItem(at: tmp)
