// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

import Toybox.Application;
import Toybox.Background;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;

// Sukrara Aura's background (docs/43): wakes on the phone's push (a Connect IQ message through Garmin Connect) and
// every 5 minutes fetches the same payload from the phone (http://127.0.0.1:17582/sukrara/aura, Garmin Connect's
// web proxy). Either way the payload goes to the face as it came (Background.exit); the face checks and keeps it
// (AuraLink.receive). contracts/watchface/payload.
(:background)
module AuraLinkBackground {
    const FETCH_URL = "http://127.0.0.1:17582/sukrara/aura";
    const FETCH_EVERY_S = 300;

    // Called by the face on every start: a fresh registration each time (a sideload or an update can drop it, and a
    // plain register sometimes does nothing).
    function register() as Void {
        if (Background has :registerForPhoneAppMessageEvent) {
            Background.deletePhoneAppMessageEvent();
            Background.registerForPhoneAppMessageEvent();
        }
        // every 5 min (Garmin's floor); a registration that stands is left alone so the rhythm doesn't restart
        if (Background.getTemporalEventRegisteredTime() == null) {
            Background.registerForTemporalEvent(new Time.Duration(FETCH_EVERY_S));
        }
    }
}

(:background)
class AuraLinkService extends System.ServiceDelegate {
    function initialize() {
        ServiceDelegate.initialize();
    }

    // The push: the message's data is the payload.
    function onPhoneAppMessage(msg as Communications.PhoneAppMessage) as Void {
        Background.exit({"k" => "push", "d" => msg.data});
    }

    // The fetch, every 5 minutes.
    function onTemporalEvent() as Void {
        // the face's report of what it got and draws (AuraLink.saveReport), so the phone can show and log it
        var report = null;
        try {
            report = Application.Storage.getValue("auraReport");
        } catch (e) {
            report = null;
        }
        Communications.makeWebRequest(
            AuraLinkBackground.FETCH_URL + (report instanceof String ? "?" + report : ""),
            null,
            {
                :method => Communications.HTTP_REQUEST_METHOD_GET,
                :responseType => Communications.HTTP_RESPONSE_CONTENT_TYPE_JSON
            },
            method(:onFetched)
        );
    }

    function onFetched(code as Number, data as Dictionary or String or Null) as Void {
        Background.exit({"k" => "fetch", "c" => code, "d" => code == 200 ? data : null});
    }
}
