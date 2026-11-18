// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Synchronization

/// Settings kept in memory only: snapshots (`sukrara-snap`) and tests. A UserDefaults suite of their own would leave
/// its plist in ~/Library/Preferences after every run (cfprefsd writes it at the first value, and a run that stops
/// early never removes it); this leaves nothing.
public final class InMemorySettingsStore: SettingsStoring {
    private let values = Mutex<[String: any SettingValue]>([:])

    public init() {}

    public func value<Value>(_ spec: SettingSpec<Value>) -> Value {
        values.withLock { $0[spec.key] as? Value } ?? spec.defaultValue
    }

    public func set<Value>(_ value: Value, for spec: SettingSpec<Value>) {
        values.withLock { $0[spec.key] = value }
    }
}
