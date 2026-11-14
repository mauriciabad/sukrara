// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// What the running build is (docs/DEVELOPMENT.md › Development and release builds): a published release, or a
/// development build (`tools/install.sh`, CI, every local build) of a commit after the release its version names.
/// `SukraraBuild` in the Info.plist carries it, written by `Bundle/bundle.sh` from `tools/release/build-info.sh`;
/// anything else (a bare `swift build` executable has no Info.plist) is a development build, so nothing claims to be a
/// release by accident. Android: `AppBuild` in `:core:model`.
public enum AppBuild: Equatable, Sendable {
    case release
    /// `commits` since the release commit, `commit` HEAD's short hash, `date` its commit date (yyyy-mm-dd); nil when
    /// unknown.
    case development(commits: Int?, commit: String?, date: String?)

    /// The Info.plist value: "release", or "development <commits> <commit> <yyyy-mm-dd>" ("?" = unknown).
    public init(infoValue: Any?) {
        let parts = ((infoValue as? String) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            .split(separator: " ", omittingEmptySubsequences: false).map(String.init)
        if parts == ["release"] {
            self = .release
            return
        }
        func part(_ i: Int) -> String? {
            guard parts.first == "development", i < parts.count, parts[i] != "?", !parts[i].isEmpty else { return nil }
            return parts[i]
        }
        self = .development(
            commits: part(1).flatMap { Int($0) },
            commit: part(2).flatMap { $0.allSatisfy { $0.isLetter || $0.isNumber } ? $0 : nil },
            date: part(3).flatMap { Self.day($0) == nil ? nil : $0 })
    }

    /// This app's: the main bundle's Info.plist.
    public static var current: AppBuild {
        AppBuild(infoValue: Bundle.main.object(forInfoDictionaryKey: "SukraraBuild"))
    }

    public var isRelease: Bool { self == .release }

    /// For reports (Copy Report): "0.14.5", or "0.14.5 development build +23 d744fd74 2026-11-14" (what is known). The
    /// same as Android's.
    public func report(_ version: String) -> String {
        switch self {
        case .release: return version
        case .development(let commits, let commit, let date):
            return (["\(version) development build", commits.map { "+\($0)" }, commit, date] as [String?])
                .compactMap { $0 }.joined(separator: " ")
        }
    }

    /// What a development build says where the version shows (About, Settings › General): "Development build · 0.14.5
    /// + 23 commits · d744fd74 · 14 Nov", whatever is known. A release says its version and distribution instead.
    public func developmentText(_ version: String, locale: Locale = .current) -> String? {
        guard case .development(let commits, let commit, let date) = self else { return nil }
        let base: String
        switch commits {
        case .some(1): base = L10n.t("\(version) + 1 commit")
        case .some(let n) where n > 1: base = L10n.t("\(version) + \(n) commits")
        default: base = version
        }
        let day = date.flatMap(Self.day).map {
            $0.formatted(
                Date.FormatStyle(locale: locale, timeZone: TimeZone(identifier: "UTC")!).day().month(.abbreviated))
        }
        return ([L10n.t("Development build"), base, commit, day] as [String?]).compactMap { $0 }.joined(
            separator: " · ")
    }

    /// yyyy-mm-dd at noon UTC (formatted in UTC: the day stays the commit's).
    private static func day(_ text: String) -> Date? {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        f.dateFormat = "yyyy-MM-dd"
        return f.date(from: text).map { $0.addingTimeInterval(12 * 3600) }
    }
}
