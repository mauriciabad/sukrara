// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Gives a file its own Finder icon (the .dmg: assets/brand/sukrara/macos/dmg/DiskImage.icns):
//   swift dmg/file-icon.swift <icon.icns> <file>
// The icon lives in the file's resource fork and Finder info (extended attributes), outside the signed data: run it
// after signing and stapling. A download over HTTP drops it (Finder then shows its generic disk image icon); the
// volume's own icon (.VolumeIcon.icns) is inside the image and always shows once it is opened.
import AppKit

let args = Array(CommandLine.arguments.dropFirst())
guard args.count == 2, let icon = NSImage(contentsOfFile: args[0]) else {
    FileHandle.standardError.write("usage: file-icon.swift <icon.icns> <file>\n".data(using: .utf8)!)
    exit(2)
}
exit(NSWorkspace.shared.setIcon(icon, forFile: args[1], options: []) ? 0 : 1)
