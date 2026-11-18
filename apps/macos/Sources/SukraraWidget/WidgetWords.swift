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
            case .waitingApproval: return ("Waiting for them to allow this Mac", "They allow it on their phone")
            case .declined: return ("They declined", follow)
            case .inviteUsed: return ("The invite was used by another device", follow)
            case .inviteExpired: return ("The invite expired before they answered", follow)
            case .revoked: return ("They stopped sharing", follow)
            case .broken: return ("Following stopped", why)
            case .wearerSilent: return ("No readings from their phone yet", "Their phone may be off or offline")
            case .juggluco(.looking): return ("Looking for Juggluco", "On this network")
            case .juggluco(.firstReading): return ("Waiting for Juggluco's first reading", "On this network")
            case .juggluco: return ("No reading from Juggluco", why)
            case .nightscout(.connecting): return ("Connecting to Nightscout", "Over the internet")
            case .nightscout(.offline): return ("No internet", "Sukrara catches up when you're back")
            case .nightscout(.signedOut): return ("Nightscout refused the token", "Open Sukrara to sign in again")
            case .nightscout: return ("No reading from Nightscout", why)
            case .libreLinkUp(.notSignedIn): return ("Not signed in to LibreLinkUp", "Open Sukrara to sign in")
            case .libreLinkUp(.connecting): return ("Connecting to LibreLinkUp", "Over the internet")
            case .libreLinkUp(.offline): return ("No internet", "Sukrara catches up when you're back")
            case .libreLinkUp(.signedOut):
                return ("LibreLinkUp refused the password", "Open Sukrara to sign in again")
            case .libreLinkUp: return ("No reading from LibreLinkUp", why)
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
            case .offlineBuild: return ("Sin vigilancia", "Versión de desarrollo")
            case .notFollowing: return ("Aún no sigues a nadie", follow)
            case .waitingApproval:
                return ("Esperando a que permita este Mac", "Lo permite en su móvil")
            case .declined: return ("Lo ha rechazado", follow)
            case .inviteUsed: return ("Otro dispositivo usó la invitación", follow)
            case .inviteExpired: return ("La invitación caducó sin respuesta", follow)
            case .revoked: return ("Ha dejado de compartir", follow)
            case .broken: return ("Se dejó de seguir", why)
            case .wearerSilent:
                return ("Aún no llegan lecturas de su móvil", "Su móvil puede estar apagado o sin conexión")
            case .juggluco(.looking): return ("Buscando Juggluco", "En esta red")
            case .juggluco(.firstReading): return ("Esperando la primera lectura de Juggluco", "En esta red")
            case .juggluco: return ("Sin lecturas de Juggluco", why)
            case .nightscout(.connecting): return ("Conectando con Nightscout", "Por internet")
            case .nightscout(.offline): return ("Sin internet", "Sukrara se pone al día cuando vuelva")
            case .nightscout(.signedOut):
                return ("Nightscout rechazó el token", "Abre Sukrara para volver a iniciar sesión")
            case .nightscout: return ("Sin lecturas de Nightscout", why)
            case .libreLinkUp(.notSignedIn):
                return ("No has iniciado sesión en LibreLinkUp", "Abre Sukrara para iniciar sesión")
            case .libreLinkUp(.connecting): return ("Conectando con LibreLinkUp", "Por internet")
            case .libreLinkUp(.offline): return ("Sin internet", "Sukrara se pone al día cuando vuelva")
            case .libreLinkUp(.signedOut):
                return ("LibreLinkUp rechazó la contraseña", "Abre Sukrara para volver a iniciar sesión")
            case .libreLinkUp: return ("Sin lecturas de LibreLinkUp", why)
            }
        })

    static var current: WidgetWords {
        Bundle.preferredLocalizations(from: ["en", "es"]).first == "es" ? .es : .en
    }
}
