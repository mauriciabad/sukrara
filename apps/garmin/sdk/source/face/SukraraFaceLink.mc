// SukraraFace — https://sukrara.mauri.app/developers/garmin-faces/
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: 0BSD
import Toybox.Application;
import Toybox.Background;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;

// A watch face's side of the link to Sukrara for Android: the fetch every 5 minutes from the face's background
// (Garmin Connect asks Sukrara on the phone, http://127.0.0.1:17582/sukrara/face), the answer kept in the face's
// storage when it is the face profile of this version and newer than the one kept, and the reading at every draw.
//
//   getInitialView:     SukraraFace.register();
//   getServiceDelegate: return [new SukraraFace.Service()];
//   onBackgroundData:   if (SukraraFace.receive(data)) { WatchUi.requestUpdate(); }
//   onUpdate:           var r = SukraraFace.reading(); // null until Sukrara first answers
(:background)
module SukraraFace {
    const URL = "http://127.0.0.1:17582/sukrara/face";
    // Connect IQ's shortest temporal event
    const EVERY_S = 300;
    // the face's storage keys: the payload, and its `sent` alone (the order rule reads only that)
    const STORE = "SukraraFace";
    const SENT = "SukraraFaceSent";

    var _payload as Dictionary? = null;
    var _skew as Number = 0;
    var _loaded as Boolean = false;

    // Asks for the fetch every 5 minutes (a registration that stands is kept, so the rhythm doesn't restart).
    function register() as Void {
        if (Background.getTemporalEventRegisteredTime() == null) {
            Background.registerForTemporalEvent(new Time.Duration(EVERY_S));
        }
    }

    // The fetch, from the background's onTemporalEvent: hands Sukrara's answer to the face (Background.exit), or null
    // when there is none (Sukrara not running or without a reading yet, the phone away).
    function fetch() as Void {
        Communications.makeWebRequest(
            URL,
            null,
            {
                :method => Communications.HTTP_REQUEST_METHOD_GET,
                :responseType => Communications.HTTP_RESPONSE_CONTENT_TYPE_JSON
            },
            new Lang.Method(SukraraFace, :onAnswer)
        );
    }

    function onAnswer(code as Number, data as Dictionary or String or Null) as Void {
        Background.exit(code == 200 ? data as Application.PropertyValueType : null);
    }

    // The face's background when Sukrara is all it fetches.
    class Service extends System.ServiceDelegate {
        function initialize() {
            ServiceDelegate.initialize();
        }

        function onTemporalEvent() as Void {
            fetch();
        }
    }

    // From AppBase.onBackgroundData: keeps [data] when it is the face profile of this version and no older than the
    // payload kept (the order rule). True when the face has something new to draw.
    function receive(data as Object?) as Boolean {
        return keep(data, Time.now().value());
    }

    // receive at the watch time [now]. It reads the kept payload's `sent` alone ([SENT]), never the kept payload beside
    // the new one: the face holds one payload at a time.
    function keep(data as Object?, now as Number) as Boolean {
        if (!valid(data, "face")) {
            return false;
        }
        var p = data as Dictionary;
        var stored = _loaded ? (_payload != null ? (_payload as Dictionary).get("sent") : null) : Application.Storage.getValue(SENT);
        if (!replaces(stored instanceof Number ? stored as Number : null, p, now)) {
            return false;
        }
        var sent = p.get("sent") as Number;
        _payload = p;
        _loaded = true;
        _skew = skewOf(sent, now);
        Application.Storage.setValue(STORE, {"p" => p, "skew" => _skew} as Dictionary<Application.Storage.KeyType, Application.Storage.ValueType>);
        Application.Storage.setValue(SENT, sent);
        return true;
    }

    // At every draw: what the newest payload says now (Reading), null until Sukrara first answers.
    function reading() as Reading? {
        return readingAt(Time.now().value());
    }

    // reading at the watch time [now]
    function readingAt(now as Number) as Reading? {
        load();
        return _payload != null ? new Reading(_payload as Dictionary, _skew, now) : null;
    }

    // The newest payload as it came, for a key Reading doesn't have; null until Sukrara first answers.
    function payload() as Dictionary? {
        load();
        return _payload;
    }

    // The payload kept from before the face (re)started, read once.
    function load() as Void {
        if (_loaded) {
            return;
        }
        _loaded = true;
        var s = Application.Storage.getValue(STORE);
        if (s instanceof Dictionary && valid(s.get("p"), "face")) {
            _payload = s.get("p") as Dictionary;
            var k = s.get("skew");
            _skew = k instanceof Number ? k : 0;
        }
    }

    // Forgets the kept payload (its tests).
    function forget() as Void {
        _payload = null;
        _skew = 0;
        _loaded = true;
        Application.Storage.deleteValue(STORE);
        Application.Storage.deleteValue(SENT);
    }
}
