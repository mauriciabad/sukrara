// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraTesting
import Testing

/// `GlucoseUnit` against `contracts/settings/units.json` (Android's UnitsFixtureTest runs the same file): the factor,
/// values and rates as written (one decimal / two, the language's separator), typed values back to stored mg/dL, and
/// every mmol/L variant of the settings schema (every value of its grid comes back unchanged).
@Suite struct GlucoseUnitTests {
    func root() throws -> [String: Any] {
        let data = try Data(contentsOf: Contracts.url("settings/units.json"))
        return try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    func cases(_ key: String) throws -> [[Any]] {
        try #require(try root()[key] as? [[Any]], "no \(key) cases")
    }

    func unit(_ c: Any) -> GlucoseUnit { GlucoseUnit(rawValue: c as? String ?? "") ?? .mgdl }

    func num(_ c: Any) -> Double { (c as? NSNumber)?.doubleValue ?? .nan }

    @Test func theFactorIsTheContracts() throws {
        #expect(num(try root()["factor"] as Any) == GlucoseUnit.mgdlPerMmol)
    }

    @Test func valuesAreWrittenAsTheContractSays() throws {
        let list = try cases("value")
        #expect(list.count >= 10)
        for c in list {
            let got = unit(c[1]).format(Int(num(c[0])), locale: Locale(identifier: c[2] as? String ?? ""))
            #expect(got == c[3] as? String, "\(c)")
        }
    }

    @Test func ratesAreWrittenAsTheContractSays() throws {
        for c in try cases("rate") {
            let got = unit(c[1]).rateText(num(c[0]), locale: Locale(identifier: c[2] as? String ?? ""))
            #expect(got == c[3] as? String, "\(c)")
        }
    }

    @Test func typedMmolBecomesTheStoredMgdlOnTheStep() throws {
        for c in try cases("input") {
            #expect(abs(GlucoseUnit.mmol.stored(num(c[0]), step: num(c[1])) - num(c[2])) < 1e-9, "\(c)")
        }
    }

    @Test func theSymbolsAreTheContracts() throws {
        let symbols = try #require(try root()["symbol"] as? [String: [String]])
        for u in GlucoseUnit.allCases {
            #expect(symbols[u.rawValue] == [u.symbol, u.rateSymbol])
        }
    }

    /// Every glucose format has its mmol/L variant, and every value of it is stored as a valid value and shown back.
    @Test func everyMmolVariantComesBackUnchanged() throws {
        let glucose = SettingsSpec.numbers.values.filter { $0.unit.hasPrefix("mg/dL") }
        #expect(glucose.count >= 10)
        for f in glucose {
            let m = try #require(f.mmol, "\(f.id) has no mmol variant")
            let steps = Int(((m.max - m.min) / m.step).rounded())
            let grid = (0...steps).map { m.min + Double($0) * m.step } + m.presets
            for x in grid {
                let stored = f.stored(x, .mmol)
                #expect(f.valid(stored), "\(f.id): \(x) mmol/L → \(stored)")
                #expect(abs(f.shown(stored, .mmol) - x) < 1e-9, "\(f.id): \(x) mmol/L → \(stored)")
                #expect(m.valid(f.shown(stored, .mmol)), "\(f.id): \(x) is on the variant's grid")
            }
            #expect(abs(m.default - f.shown(f.default, .mmol)) < 1e-9, "\(f.id): the variant's default")
            #expect(f.inUnit(.mgdl) == f)
            #expect(f.stored(f.default, .mgdl) == f.default)
        }
    }
}
