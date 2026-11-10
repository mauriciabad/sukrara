// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore

/// The widget's own words in the two languages the app speaks (the card's words are the renderer's `CardWords`):
/// like the renderer, the extension carries no string catalog.
struct WidgetWords: Sendable {
    let valueName, valueDescription, graphName, graphDescription, combinedName, combinedDescription: String
    /// No snapshot to show: the app isn't running or set up yet, or wrote a format this widget doesn't read.
    let openApp: String
    let openAppSpoken: String
    /// "Glucose: no data." before a reason, for VoiceOver.
    let noData: String
    /// A no-data reason (`NoDataReason`, #72) in fewer words than the panel's: what it is, and where to go (the
    /// click opens the panel, which has the action).
    let reason: @Sendable (NoDataReason) -> (title: String, next: String)

    static let en = WidgetWords(
        valueName: "Glucose", valueDescription: "Current glucose, trend and time in range.",
        graphName: "Glucose graph", graphDescription: "The graph with time in range beside it.",
        combinedName: "Glucose + graph", combinedDescription: "Value, time-in-range rings and the graph.",
        openApp: "Open Sukrara", openAppSpoken: "Glucose: no data. Open Sukrara.", noData: "Glucose: no data.",
        reason: { r in
            let follow = "Open Sukrara to follow with an invite"
            let why = "Open Sukrara to see why"
            switch r {
            case .offlineBuild: return ("Not monitoring", "Development build")
            case .notFollowing: return ("Not following anyone yet", follow)
            case .waitingApproval: return ("Waiting for the wearer to allow this Mac", "They allow it on their phone")
            case .declined: return ("The wearer declined", follow)
            case .inviteUsed: return ("The invite was used by another device", follow)
            case .revoked: return ("The wearer stopped sharing", follow)
            case .broken: return ("Following stopped", why)
            case .wearerSilent: return ("No readings from the wearer yet", "Their phone may be off or offline")
            case .juggluco(.looking): return ("Looking for Juggluco", "On this network")
            case .juggluco(.firstReading): return ("Waiting for Juggluco's first reading", "On this network")
            case .juggluco: return ("No reading from Juggluco", why)
            }
        })

    static let es = WidgetWords(
        valueName: "Glucosa", valueDescription: "Glucosa actual, tendencia y tiempo en rango.",
        graphName: "Gráfica de glucosa", graphDescription: "La gráfica con el tiempo en rango al lado.",
        combinedName: "Glucosa + gráfica", combinedDescription: "Valor, anillos de tiempo en rango y la gráfica.",
        openApp: "Abre Sukrara", openAppSpoken: "Glucosa: sin datos. Abre Sukrara.", noData: "Glucosa: sin datos.",
        reason: { r in
            let follow = "Abre Sukrara para seguir con una invitación"
            let why = "Abre Sukrara para ver por qué"
            switch r {
            case .offlineBuild: return ("Sin monitorizar", "Versión de desarrollo")
            case .notFollowing: return ("Aún no sigues a nadie", follow)
            case .waitingApproval:
                return ("Esperando a que quien lleva el sensor permita este Mac", "Lo permite en su móvil")
            case .declined: return ("Quien lleva el sensor lo rechazó", follow)
            case .inviteUsed: return ("Otro dispositivo usó la invitación", follow)
            case .revoked: return ("Quien lleva el sensor dejó de compartir", follow)
            case .broken: return ("Se dejó de seguir", why)
            case .wearerSilent:
                return ("Aún no hay lecturas de quien lleva el sensor", "Su móvil puede estar apagado o sin conexión")
            case .juggluco(.looking): return ("Buscando Juggluco", "En esta red")
            case .juggluco(.firstReading): return ("Esperando la primera lectura de Juggluco", "En esta red")
            case .juggluco: return ("Sin lecturas de Juggluco", why)
            }
        })

    static var current: WidgetWords {
        Bundle.preferredLocalizations(from: ["en", "es"]).first == "es" ? .es : .en
    }
}
