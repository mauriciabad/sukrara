// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;

// The app's background: the shared link's (LinkService: the push, the 5-minute fetch, the hand-off), plus the
// complications, published from here so faces update with the app closed (docs/47 §3: the background is the reported
// way).
(:background)
class AppService extends LinkService {
    function initialize() {
        LinkService.initialize();
    }

    // every run's result, before it goes to the foreground (Background.exit ends the run)
    function hand(data as Dictionary) as Void {
        AppComplications.publish(data.get("d"));
        LinkService.hand(data);
    }
}

// THE COMPLICATION HOOK (docs/47 §3, contracts/watchface/payload): called with every payload a background run brings
// (null when a fetch failed). Not published yet: the next step adds the ComplicationPublisher permission, the
// complications' resources (ids 0–3, labels, icons, the glance preview) and Complications.updateComplication here.
(:background)
module AppComplications {
    function publish(payload) as Void {}
}
