// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Lang;
import Toybox.Test;

// The watch app's outbox against contracts/watchface/payload entries.json and mutes.json (WriteCases, generated): the
// signed text and the HMAC of every case, the fields as sent, the amounts as the phone stores them, and what every
// answer does to the outbox. apps/garmin/tools/link-test.sh app.
(:test)
module OutboxCases {
    // the outbox item a case's request was made from
    function item(body as Dictionary, mute as Boolean) as Dictionary {
        if (!body.hasKey("k") && !body.hasKey("do")) {
            return {"w" => "u", "id" => body.get("id"), "st" => "new", "c" => WriteCases.NOW}; // an undo
        }
        var it = {"w" => mute ? "m" : "e", "id" => body.get("id"), "st" => "new", "c" => WriteCases.NOW};
        var fields = mute ? ["do", "side", "min"] : ["k", "a", "t", "f"];
        for (var i = 0; i < fields.size(); i++) {
            it.put(fields[i], body.get(fields[i]));
        }
        return it;
    }

    function all() as Array {
        var out = [] as Array;
        var e = WriteCases.entries();
        for (var i = 0; i < e.size(); i++) {
            out.add([e[i], false]);
        }
        var m = WriteCases.mutes();
        for (var i = 0; i < m.size(); i++) {
            out.add([m[i], true]);
        }
        var u = WriteCases.undos();
        for (var i = 0; i < u.size(); i++) {
            out.add([u[i], false]);
        }
        return out;
    }

    // a fresh outbox holding [it], with the fixtures' key
    function start(it as Dictionary) as Void {
        Outbox.reset();
        Application.Storage.setValue(Outbox.KEY, WriteCases.KEY);
        Outbox.save([it] as Array<Dictionary>);
    }

    // the first case of [list] the phone answers [r]
    function answered(list as Array, r as String) as Dictionary {
        for (var i = 0; i < list.size(); i++) {
            if (r.equals(((list[i] as Dictionary).get("answer") as Dictionary).get("r"))) {
                return list[i] as Dictionary;
            }
        }
        throw new Lang.InvalidValueException(r);
    }
}

// every case's signed text is the watch's, and every case the phone accepts is signed as the watch signs it
(:test)
function outboxSignsEveryCase(logger as Logger) as Boolean {
    var ok = true;
    var all = OutboxCases.all();
    for (var i = 0; i < all.size(); i++) {
        var c = all[i][0] as Dictionary;
        var body = c.get("body") as Dictionary;
        var it = OutboxCases.item(body, all[i][1] as Boolean);
        var text = Outbox.signed(it, body.get("s") as String);
        if (!text.equals(c.get("signed"))) {
            logger.error(c.get("name") + ": signed " + text + ", the case says " + c.get("signed"));
            ok = false;
        }
        if ("sig".equals((c.get("answer") as Dictionary).get("r"))) {
            continue;
        }
        var sent = Outbox.params(it, (body.get("s") as String).toNumber(), WriteCases.KEY);
        var keys = body.keys();
        for (var k = 0; k < keys.size(); k++) {
            if (!body.get(keys[k]).equals(sent.get(keys[k]))) {
                logger.error(c.get("name") + ": " + keys[k] + " = " + sent.get(keys[k]) + ", the case sends " + body.get(keys[k]));
                ok = false;
            }
        }
        if (sent.size() != body.size()) {
            logger.error(c.get("name") + ": " + sent.size() + " fields, the case sends " + body.size());
            ok = false;
        }
    }
    return ok;
}

// what the phone stored is the amount as the watch writes it (insulin one decimal at most, whole grams)
(:test)
function outboxAmountsAsThePhoneStores(logger as Logger) as Boolean {
    var ok = true;
    var e = WriteCases.entries();
    for (var i = 0; i < e.size(); i++) {
        var c = e[i] as Dictionary;
        var s = c.get("stores");
        if (!(s instanceof Dictionary)) {
            continue;
        }
        var a = Outbox.amount(s.get("k") as String, (s.get("a") as Numeric).toFloat());
        if (a.toFloat() != (s.get("a") as Numeric).toFloat() || a.find(".0") != null) {
            logger.error(c.get("name") + ": " + a);
            ok = false;
        }
    }
    Test.assertEqual(Outbox.amount("rapid", 4.5), "4.5");
    Test.assertEqual(Outbox.amount("long", 14.0), "14");
    Test.assertEqual(Outbox.amount("carbs", 30.0), "30");
    return ok;
}

// THE ANSWER RULE, one answer of each kind from the fixtures
(:test)
function outboxEveryAnswer(logger as Logger) as Boolean {
    var now = WriteCases.NOW;
    var e = WriteCases.entries();
    var m = WriteCases.mutes();
    // [r] from [list]: the outbox after the phone's answer, and the rule's go-on
    var gone = ["stored", "known", "id", "kind", "amount", "when", "off"];
    for (var i = 0; i < gone.size(); i++) {
        var c = OutboxCases.answered(e, gone[i]);
        var it = OutboxCases.item(c.get("body") as Dictionary, false);
        OutboxCases.start(it);
        Test.assertMessage(Outbox.answer(it.get("id"), 200, c.get("answer"), now), gone[i] + ": goes on");
        Test.assertEqualMessage(Outbox.items().size(), 0, gone[i] + ": leaves the outbox");
    }
    var mgone = ["done", "known", "action", "id"];
    for (var i = 0; i < mgone.size(); i++) {
        var c = OutboxCases.answered(m, mgone[i]);
        var it = OutboxCases.item(c.get("body") as Dictionary, true);
        OutboxCases.start(it);
        Outbox.answer(it.get("id"), 200, c.get("answer"), now);
        Test.assertEqualMessage(Outbox.items().size(), 0, "mute " + mgone[i] + ": leaves the outbox");
    }
    // stored: Sent, and the person hears Logged
    var c = OutboxCases.answered(e, "stored");
    var it = OutboxCases.item(c.get("body") as Dictionary, false);
    OutboxCases.start(it);
    Outbox.answer(it.get("id"), 200, c.get("answer"), now);
    Test.assertEqualMessage(Sending.state(now + 60), "sent", "Sent after stored");
    Test.assertMessage(Sending.state(now + Outbox.SENT_SHOWN_S) == null, "then nothing");
    Test.assertEqualMessage((Application.Storage.getValue(Outbox.NOTE) as Dictionary).get("r"), "stored", "Logged");
    // when / off: dropped, and the person is told why
    var told = ["when", "off"];
    for (var i = 0; i < told.size(); i++) {
        c = OutboxCases.answered(e, told[i]);
        it = OutboxCases.item(c.get("body") as Dictionary, false);
        OutboxCases.start(it);
        Outbox.answer(it.get("id"), 200, c.get("answer"), now);
        Test.assertEqualMessage((Application.Storage.getValue(Outbox.NOTE) as Dictionary).get("r"), told[i], told[i] + ": told");
    }
    // sig: kept, not sent again until a push brings a key
    c = OutboxCases.answered(e, "sig");
    it = OutboxCases.item(c.get("body") as Dictionary, false);
    OutboxCases.start(it);
    Outbox.answer(it.get("id"), 200, c.get("answer"), now);
    Test.assertEqualMessage(Outbox.items().size(), 1, "sig: kept");
    Test.assertMessage(Outbox.next(now) == null, "sig: waits");
    Test.assertEqualMessage(Sending.state(now), "wait", "Waiting for the phone");
    Outbox.received({"sent" => now, "key" => WriteCases.KEY}, now);
    Test.assertMessage(Outbox.next(now) == null, "the same key: still waits");
    Outbox.received({"sent" => now, "key" => "ff" + WriteCases.KEY.substring(2, 64)}, now);
    Test.assertEqualMessage((Outbox.next(now) as Dictionary).get("id"), it.get("id"), "a new key: sent again");
    // clock: the phone's now becomes the lead, and it goes again
    c = OutboxCases.answered(e, "clock");
    it = OutboxCases.item(c.get("body") as Dictionary, false);
    OutboxCases.start(it);
    Outbox.flush(null);
    var phone = (c.get("answer") as Dictionary).get("now") as Number;
    Test.assertMessage(Outbox.answer(it.get("id"), 200, c.get("answer"), now - 400), "clock: again");
    Test.assertEqualMessage(Outbox.lead(), phone - (now - 400), "clock: the lead from the phone's now");
    Test.assertEqualMessage((Outbox.next(now) as Dictionary).get("id"), it.get("id"), "clock: kept, sent again");
    Outbox.answer(it.get("id"), 200, c.get("answer"), now);
    Test.assertMessage(!Outbox.answer(it.get("id"), 200, c.get("answer"), now), "clock: twice at most per flush");
    // pen: waits for the person; Log anyway sends it again with f = 1, Cancel drops it
    c = OutboxCases.answered(e, "pen");
    it = OutboxCases.item(c.get("body") as Dictionary, false);
    OutboxCases.start(it);
    Outbox.answer(it.get("id"), 200, c.get("answer"), now);
    Test.assertEqualMessage(Sending.state(now), "ask", "pen: the person is asked");
    Test.assertMessage(Outbox.next(now) == null, "pen: not sent meanwhile");
    Outbox.pen(it.get("id") as String, true);
    var again = Outbox.next(now) as Dictionary;
    Test.assertEqualMessage(again.get("f"), "1", "Log anyway: f = 1");
    Test.assertMessage(Outbox.signed(again, "1").find("|1|1") != null, "Log anyway: f signed");
    Outbox.answer(it.get("id"), 200, c.get("answer"), now);
    Outbox.pen(it.get("id") as String, false);
    Test.assertEqualMessage(Outbox.items().size(), 0, "Cancel: dropped");
    // no phone, an old phone, another entry's answer: kept, stop for now
    c = OutboxCases.answered(e, "stored");
    it = OutboxCases.item(c.get("body") as Dictionary, false);
    var codes = [-104, -300, 404, -400];
    for (var i = 0; i < codes.size(); i++) {
        OutboxCases.start(it);
        Test.assertMessage(!Outbox.answer(it.get("id"), codes[i], null, now), codes[i] + ": stop");
        Test.assertEqualMessage(Outbox.items().size(), 1, codes[i] + ": kept");
    }
    OutboxCases.start(it);
    Test.assertMessage(!Outbox.answer(it.get("id"), 200, {"id" => "another", "r" => "stored"}, now), "another id: stop");
    Test.assertEqualMessage(Outbox.items().size(), 1, "another id: kept");
    Outbox.reset();
    return true;
}

// a payload whose tx has the entry's id (its first 8 characters): the answer was lost after the phone stored it
(:test)
function outboxSeenInThePayload(logger as Logger) as Boolean {
    var now = WriteCases.NOW;
    var it = Outbox.entry("rapid", 4.5, now);
    OutboxCases.start(it);
    Outbox.received({"sent" => now + 30, "tx" => {"t" => [now], "k" => ["rapid"], "a" => [4.5], "i" => ["0b6c1f5e"]}}, now);
    Test.assertEqualMessage(Outbox.items().size(), 1, "another entry's id: kept");
    Test.assertEqualMessage(Outbox.lead(), 30, "the lead from the payload's sent");
    Outbox.received({"sent" => now, "tx" => {"i" => [(it.get("id") as String).substring(0, 8)]}}, now);
    Test.assertEqualMessage(Outbox.items().size(), 0, "its id: gone");
    Outbox.reset();
    return true;
}

// the watch's ids: lowercase UUID v4, new each time; an entry's time on the phone's clock
(:test)
function outboxIds(logger as Logger) as Boolean {
    Outbox.reset();
    var a = Outbox.uuid();
    var b = Outbox.uuid();
    Test.assertEqual(a.length(), 36);
    Test.assertMessage(!a.equals(b), "new each time");
    Test.assertEqual(a.substring(14, 15), "4");
    Test.assertMessage("89ab".find(a.substring(19, 20)) != null, "the variant");
    Test.assertEqual(a, a.toLower());
    Test.assertEqual(a.substring(8, 9) + a.substring(13, 14) + a.substring(18, 19) + a.substring(23, 24), "----");
    Outbox.received({"sent" => 1000 + 90}, 1000);
    var e = Outbox.entry("carbs", 45.0, 900);
    Test.assertEqual(e.get("t"), "990");
    Test.assertEqual(e.get("a"), "45");
    Outbox.reset();
    return true;
}

// Undo (undos.json): what every answer does to the undo, and an undo waits for its entry's stored / known
(:test)
function outboxUndo(logger as Logger) as Boolean {
    var now = WriteCases.NOW;
    var u = WriteCases.undos();
    // done / gone: Undone; off: Not undone; unknown / foreign: dropped silently
    var said = {"done" => "done", "gone" => "done", "off" => "off", "unknown" => null, "foreign" => null};
    var rs = said.keys();
    for (var i = 0; i < rs.size(); i++) {
        var c = OutboxCases.answered(u, rs[i] as String);
        var it = OutboxCases.item(c.get("body") as Dictionary, false);
        OutboxCases.start(it);
        Application.Storage.deleteValue(Outbox.NOTE);
        Test.assertMessage(Outbox.answer(it.get("id"), 200, c.get("answer"), now), rs[i] + ": goes on");
        Test.assertEqualMessage(Outbox.items().size(), 0, rs[i] + ": leaves the outbox");
        var note = Application.Storage.getValue(Outbox.NOTE);
        var r = note instanceof Dictionary ? note.get("r") : null;
        Test.assertMessage(said.get(rs[i]) == null ? r == null : (said.get(rs[i]) as String).equals(r), rs[i] + ": told " + r);
    }
    // sig: kept until a key
    var c = OutboxCases.answered(u, "sig");
    var it = OutboxCases.item(c.get("body") as Dictionary, false);
    OutboxCases.start(it);
    Outbox.answer(it.get("id"), 200, c.get("answer"), now);
    Test.assertEqualMessage(Outbox.items().size(), 1, "undo sig: kept");
    // the undo waits while its entry is in the outbox, and goes once the entry left it
    var e = OutboxCases.answered(WriteCases.entries(), "stored");
    var entry = OutboxCases.item(e.get("body") as Dictionary, false);
    OutboxCases.start(entry);
    Outbox.add(Outbox.undo(entry.get("id") as String));
    Test.assertEqualMessage((Outbox.next(now) as Dictionary).get("w"), "e", "the entry first");
    Outbox.answer(entry.get("id"), 200, e.get("answer"), now);
    var next = Outbox.next(now) as Dictionary;
    Test.assertEqualMessage(next.get("w"), "u", "then its undo");
    Test.assertEqualMessage(next.get("id"), entry.get("id"), "by the entry's id");
    var sent = Outbox.params(next, now, WriteCases.KEY);
    Test.assertEqualMessage(sent.size(), 3, "id, s, mac");
    Outbox.reset();
    return true;
}
