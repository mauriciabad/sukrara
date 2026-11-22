// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

import Toybox.Application;
import Toybox.Background;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;

// Every Sukrara Garmin app's background (docs/43, docs/47 §2): wakes on the phone's push (a Connect IQ message through
// Garmin Connect, to the app's own id) and every 5 minutes fetches the same payload from the phone (the app's
// LinkBackground.FETCH_URL on http://127.0.0.1:17582, Garmin Connect's web proxy). Either way the payload goes to the app's
// foreground as it came (Background.exit); the foreground checks and keeps it (Link.receive).
// contracts/watchface/payload. The storage keys are the face's first names: storage is per app, so each app has its own.
(:background :glance)
module LinkBackground {
    // each app's own fetch: its jungle excludes the other apps' annotations (a module per app would cost the face
    // 44 bytes, #217)
    (:aura)
    const FETCH_URL = "http://127.0.0.1:17582/sukrara/aura";
    (:app)
    const FETCH_URL = "http://127.0.0.1:17582/sukrara/app";
    (:field)
    const FETCH_URL = "http://127.0.0.1:17582/sukrara/field";
    const FETCH_EVERY_S = 300;
    // Every background run's result, also left in the app's storage (LinkService.hand): the foreground picks it up at
    // its next draw when Garmin doesn't call onBackgroundData (2026-11-22, #244: after a watch restart it didn't for 16
    // min while this background kept running; only the face's next start brought the data).
    const HANDOFF = "auraHandoff";

    // The newer of two payloads by `sent` (an invalid one loses).
    function newer(a, b) {
        var sa = a instanceof Dictionary ? a.get("sent") : null;
        var sb = b instanceof Dictionary ? b.get("sent") : null;
        if (!(sb instanceof Number)) {
            return a;
        }
        if (!(sa instanceof Number)) {
            return b;
        }
        return sb > sa ? b : a;
    }

    // Called by the app on every start: a fresh registration each time (a sideload or an update can drop it, and a
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
class LinkService extends System.ServiceDelegate {
    function initialize() {
        ServiceDelegate.initialize();
    }

    var _newest = null;
    var _waiting as Number = 0;

    // The push. The watch keeps the messages that arrived while the face couldn't take them (another face, USB, a
    // restart) and wakes the background once per new message with the OLDEST one: taking one per run, the face stayed
    // as many readings behind as it had queued (device check 2026-11-16, docs/43). So every run takes all waiting
    // messages and hands the face the newest.
    function onPhoneAppMessage(msg as Communications.PhoneAppMessage) as Void {
        _newest = msg.data;
        _waiting = 0;
        if (Communications has :registerForPhoneAppMessages) {
            // called at once for each waiting message
            Communications.registerForPhoneAppMessages(method(:onWaiting));
        }
        hand({"k" => "push", "d" => _newest, "n" => _waiting});
    }

    // Hands [data] to the face both ways: in its storage ([LinkBackground.HANDOFF], read at every draw) and through
    // Garmin (Background.exit → onBackgroundData, at once). [id] tells the face it is the same run when both arrive.
    function hand(data as Dictionary) as Void {
        keep(data);
        Background.exit(data);
    }

    // [data] with its run id, in the app's storage
    function keep(data as Dictionary) as Void {
        data.put("id", Time.now().value().toString() + "." + System.getTimer().toString());
        try {
            Application.Storage.setValue(LinkBackground.HANDOFF, data);
        } catch (e) {
            // a watch whose background can't write storage: Garmin's hand-over only
        }
    }

    function onWaiting(msg as Communications.PhoneAppMessage) as Void {
        _waiting += 1;
        _newest = LinkBackground.newer(_newest, msg.data);
    }

    // The fetch, every 5 minutes.
    function onTemporalEvent() as Void {
        // the app's report of what it got and draws (Link.saveReport), so the phone can show and log it
        var report = null;
        var w = null;
        try {
            report = Application.Storage.getValue("auraReport");
            w = Application.Storage.getValue(Link.WATCH);
        } catch (e) {
            report = null;
        }
        // and which watch asks (the app's last push's w): the phone answers with that watch's settings
        var q = report instanceof String ? report : "";
        if (w instanceof String) {
            q += (q.length() > 0 ? "&" : "") + "w=" + w;
        }
        Communications.makeWebRequest(
            LinkBackground.FETCH_URL + (q.length() > 0 ? "?" + q : ""),
            null,
            {
                :method => Communications.HTTP_REQUEST_METHOD_GET,
                :responseType => Communications.HTTP_RESPONSE_CONTENT_TYPE_JSON
            },
            method(:onFetched)
        );
    }

    function onFetched(code as Number, data as Dictionary or String or Null) as Void {
        hand({"k" => "fetch", "c" => code, "d" => code == 200 ? data : null});
    }
}
