// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraShare
import Testing

/// The `alert` event's content (PROTOCOL-v2 "Alerts"), with #39's escalation: `esc` on a very low's `fire` and the
/// `answered` state.
struct AlertPayloadTests {
    @Test func anEscalatedFireAndItsAnswerAreRead() throws {
        let fire = try AlertPayload.decode(
            #"{"v":2,"type":"alert","id":"a1","k":"very_low","state":"fire","at":100,"t":90,"sgv":52,"th":55,"n":2,"tr":0,"esc":5}"#
        )
        #expect(fire.fires && fire.esc == 5 && !fire.answered && fire.count == 2)
        let plain = try AlertPayload.decode(
            #"{"v":2,"type":"alert","id":"a1","k":"very_low","state":"fire","at":100,"n":1}"#)
        #expect(plain.esc == nil)
        let answered = try AlertPayload.decode(
            #"{"v":2,"type":"alert","id":"a1","k":"very_low","state":"answered","at":160}"#)
        #expect(answered.answered && !answered.fires && answered.esc == nil)
        #expect(throws: PayloadError.self) {
            try AlertPayload.decode(#"{"v":2,"type":"alert","id":"a1","k":"very_low","state":"escalate","at":1}"#)
        }
    }
}
