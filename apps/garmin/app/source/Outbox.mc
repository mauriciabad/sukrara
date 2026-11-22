// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Communications;
import Toybox.Cryptography;
import Toybox.Lang;
import Toybox.StringUtil;
import Toybox.Time;

// THE OUTBOX (contracts/watchface/payload README "Entries from the watch", "Mute from the watch"): every entry and mute
// the person confirms waits in storage with the watch's own id until the phone answers for it; sent from the foreground
// at once and from the background on every run (a callback there isn't guaranteed: the outbox, not the callback, is the
// truth). Each try is signed with the key only Garmin Connect's push carries.
(:background)
module Outbox {
    const STORE = "sukOutbox";
    const KEY = "sukKey"; // the phone's signing key, hex (push only; never sent back, never shown)
    const LEAD = "sukLead"; // the phone's clock minus the watch's, s (signed)
    const SENT = "sukSent"; // when the phone last stored one (watch clock): "Sent"
    const NOTE = "sukNote"; // what the person should hear about at the next look: {r, k, at}
    const ENTRIES = "http://127.0.0.1:17582/sukrara/entries";
    const MUTE = "http://127.0.0.1:17582/sukrara/mute";
    const MAX = 24;
    const SENT_SHOWN_S = 600;
    // THE LATE RULE (SPEC §13.1, open with the owner): true = an entry or mute waits for the phone however long it is
    // away (the outbox sends it when the phone is back); false = one not sent within NEVER_LATE_S is dropped and the
    // person told "Not sent, nothing saved".
    const SEND_LATE = true;
    const NEVER_LATE_S = 120;

    var _done as Method? = null;
    var _sending as String? = null;
    var _clockTries as Number = 0;

    function items() as Array<Dictionary> {
        var v = Application.Storage.getValue(STORE);
        return v instanceof Array ? v as Array<Dictionary> : [] as Array<Dictionary>;
    }

    function save(list as Array<Dictionary>) as Void {
        Application.Storage.setValue(STORE, list);
    }

    function key() as String? {
        var k = Application.Storage.getValue(KEY);
        return k instanceof String && k.length() == 64 ? k : null;
    }

    function lead() as Number {
        var l = Application.Storage.getValue(LEAD);
        return l instanceof Number ? l : 0;
    }

    // A payload arrived at the watch time [now]: its lead, its key (a new key retries what the phone couldn't check),
    // and the entries it already has (tx.i: an answer lost after the phone stored it).
    function received(p as Dictionary, now as Number) as Void {
        Application.Storage.setValue(LEAD, (p.get("sent") as Number) - now);
        var k = p.get("key");
        var list = items();
        var changed = false;
        if (k instanceof String && k.length() == 64 && !k.equals(Application.Storage.getValue(KEY))) {
            Application.Storage.setValue(KEY, k);
            for (var i = 0; i < list.size(); i++) {
                if ("sig".equals(list[i].get("st"))) {
                    list[i].put("st", "new");
                    changed = true;
                }
            }
        }
        var tx = p.get("tx");
        var ids = tx instanceof Dictionary ? tx.get("i") : null;
        if (ids instanceof Array) {
            for (var i = list.size() - 1; i >= 0; i--) {
                var id = list[i].get("id") as String;
                if ("e".equals(list[i].get("w")) && ids.indexOf(id.substring(0, 8)) >= 0) {
                    list.remove(list[i]);
                    changed = true;
                }
            }
        }
        if (changed) {
            save(list);
        }
    }

    // The amount as the phone stores it: insulin with at most one decimal ("4.5", "14"), carbs in whole grams.
    function amount(k as String, a as Float) as String {
        if (k.equals("carbs")) {
            return a.toNumber().toString();
        }
        var tenths = (a * 10).toNumber();
        return tenths % 10 == 0 ? (tenths / 10).toString() : (tenths / 10) + "." + (tenths % 10);
    }

    // An entry the person confirmed: [k] rapid / long / carbs, [a] the amount, [at] when it was taken (watch clock).
    function entry(k as String, a as Float, at as Number) as Dictionary {
        return {"w" => "e", "id" => uuid(), "k" => k, "a" => amount(k, a), "t" => (at + lead()).toString(), "f" => "0",
            "st" => "new", "c" => Time.now().value()};
    }

    // A mute action: [act] mute / unmute / less / usual, [side] all / low / high, [min] 15 / 30 / 60 / 120 / 0.
    function mute(act as String, side as String, min as Number) as Dictionary {
        return {"w" => "m", "id" => uuid(), "do" => act, "side" => side, "min" => min.toString(), "st" => "new",
            "c" => Time.now().value()};
    }

    function add(item as Dictionary) as Void {
        var list = items();
        list.add(item);
        if (list.size() > MAX) {
            list = list.slice(list.size() - MAX, null);
        }
        save(list);
    }

    function find(list as Array<Dictionary>, id) as Dictionary? {
        for (var i = 0; i < list.size(); i++) {
            if (list[i].get("id").equals(id)) {
                return list[i];
            }
        }
        return null;
    }

    // The person answered the phone's pen question for [id]: [anyway] = Log anyway (sent again with f = 1), else Cancel.
    function pen(id as String, anyway as Boolean) as Void {
        var list = items();
        var it = find(list, id);
        if (it == null) {
            return;
        }
        if (anyway) {
            it.put("f", "1");
            it.put("st", "new");
            it.remove("pen");
        } else {
            list.remove(it);
        }
        save(list);
    }

    // What the HMAC covers: id|k|a|t|s|f (an entry) or id|do|side|min|s (a mute), the strings exactly as sent.
    function signed(it as Dictionary, s as String) as String {
        if ("m".equals(it.get("w"))) {
            return it.get("id") + "|" + it.get("do") + "|" + it.get("side") + "|" + it.get("min") + "|" + s;
        }
        return it.get("id") + "|" + it.get("k") + "|" + it.get("a") + "|" + it.get("t") + "|" + s + "|" + it.get("f");
    }

    // The request's fields, every value a string, signed with [keyHex] at the phone's time [s].
    function params(it as Dictionary, s as Number, keyHex as String) as Dictionary {
        var out = {"id" => it.get("id"), "s" => s.toString()} as Dictionary;
        var fields = "m".equals(it.get("w")) ? ["do", "side", "min"] : ["k", "a", "t", "f"];
        for (var i = 0; i < fields.size(); i++) {
            out.put(fields[i], it.get(fields[i]));
        }
        out.put("mac", mac(keyHex, signed(it, s.toString())));
        return out;
    }

    function hex(b as ByteArray) as String {
        return StringUtil.convertEncodedString(b, {
            :fromRepresentation => StringUtil.REPRESENTATION_BYTE_ARRAY,
            :toRepresentation => StringUtil.REPRESENTATION_STRING_HEX
        }) as String;
    }

    // HMAC-SHA256 of [text] with the key [keyHex] (its 32 bytes), lowercase hex
    function mac(keyHex as String, text as String) as String {
        var key = StringUtil.convertEncodedString(keyHex, {
            :fromRepresentation => StringUtil.REPRESENTATION_STRING_HEX,
            :toRepresentation => StringUtil.REPRESENTATION_BYTE_ARRAY
        }) as ByteArray;
        var h = new Cryptography.HashBasedMessageAuthenticationCode({
            :algorithm => Cryptography.HASH_SHA256,
            :key => key
        });
        h.update(StringUtil.convertEncodedString(text, {
            :fromRepresentation => StringUtil.REPRESENTATION_STRING_PLAIN_TEXT,
            :toRepresentation => StringUtil.REPRESENTATION_BYTE_ARRAY
        }) as ByteArray);
        return hex(h.digest()).toLower();
    }

    // a lowercase UUID v4 (the version and variant bits set on 16 random bytes)
    function uuid() as String {
        var b = Cryptography.randomBytes(16);
        b[6] = (b[6] & 0x0f) | 0x40;
        b[8] = (b[8] & 0x3f) | 0x80;
        var h = hex(b).toLower();
        return h.substring(0, 8) + "-" + h.substring(8, 12) + "-" + h.substring(12, 16) + "-" + h.substring(16, 20) + "-"
            + h.substring(20, 32);
    }

    // the next item to send at the watch time [now]: a new one (a sig waits for a new key, a pen for the person);
    // without SEND_LATE, one confirmed more than NEVER_LATE_S ago is dropped first
    function next(now as Number) as Dictionary? {
        var list = items();
        for (var i = 0; i < list.size(); i++) {
            var it = list[i];
            if (!SEND_LATE && now - (it.get("c") as Number) > NEVER_LATE_S) {
                list.remove(it);
                save(list);
                tell("late", it, now);
                return next(now);
            }
            if ("new".equals(it.get("st"))) {
                return it;
            }
        }
        return null;
    }

    // Sends the outbox one item at a time; [done] (no arguments) once nothing more goes now. Without a key nothing is
    // sent: a write needs one delivered push.
    function flush(done as Method?) as Void {
        _done = done;
        _clockTries = 0;
        sendNext();
    }

    function sendNext() as Void {
        var it = next(Time.now().value());
        var k = key();
        if (it == null || k == null) {
            finish();
            return;
        }
        _sending = it.get("id") as String;
        var s = Time.now().value() + lead();
        Communications.makeWebRequest("m".equals(it.get("w")) ? MUTE : ENTRIES, params(it, s, k), {
            :method => Communications.HTTP_REQUEST_METHOD_POST,
            :headers => {"Content-Type" => Communications.REQUEST_CONTENT_TYPE_JSON},
            :responseType => Communications.HTTP_RESPONSE_CONTENT_TYPE_JSON
        }, new Lang.Method(Outbox, :onAnswer));
    }

    function onAnswer(code as Number, data as Dictionary or String or Null) as Void {
        var go = answer(_sending, code, data, Time.now().value());
        _sending = null;
        if (go) {
            sendNext();
        } else {
            finish();
        }
    }

    function finish() as Void {
        var d = _done;
        _done = null;
        if (d != null) {
            d.invoke();
        }
    }

    // THE ANSWER RULE: the phone's answer [data] (HTTP [code]) for the item [id] at the watch time [now]; true = go on
    // with the next item. README's table: stored / known / done leave the outbox; sig waits for a key; clock takes the
    // phone's now and goes again (twice at most per flush); pen waits for the person; id / kind / amount / when / off /
    // action / bad drop it (when and off tell the person); anything else (no phone, a timeout, an old phone) keeps it.
    function answer(id, code as Number, data, now as Number) as Boolean {
        var list = items();
        var it = id != null ? find(list, id) : null;
        if (it == null || code != 200 || !(data instanceof Dictionary) || !id.equals(data.get("id"))) {
            return false;
        }
        var r = data.get("r");
        if ("sig".equals(r)) {
            it.put("st", "sig");
            save(list);
            return true;
        }
        if ("clock".equals(r)) {
            var phone = data.get("now");
            if (phone instanceof Number && _clockTries < 2) {
                _clockTries += 1;
                Application.Storage.setValue(LEAD, phone - now);
                return true;
            }
            return false;
        }
        if ("pen".equals(r)) {
            it.put("st", "pen");
            it.put("pen", data.get("pen"));
            save(list);
            tell("pen", it, now);
            return true;
        }
        list.remove(it);
        save(list);
        if ("stored".equals(r)) {
            Application.Storage.setValue(SENT, now);
            tell("stored", it, now);
        } else if ("done".equals(r) || "when".equals(r) || "off".equals(r)) {
            tell(r as String, it, now);
        }
        return true;
    }

    function tell(r as String, it as Dictionary, now as Number) as Void {
        Application.Storage.setValue(NOTE, {"r" => r, "k" => it.get("k"), "do" => it.get("do"), "id" => it.get("id"),
            "at" => now});
    }

    // Forgets everything (the tests).
    function reset() as Void {
        Application.Storage.deleteValue(STORE);
        Application.Storage.deleteValue(KEY);
        Application.Storage.deleteValue(LEAD);
        Application.Storage.deleteValue(SENT);
        Application.Storage.deleteValue(NOTE);
        _sending = null;
        _done = null;
        _clockTries = 0;
    }
}

// What the screen and the glance say about the outbox at the watch time [now]: "ask" (the phone asked about a pen),
// "wait" (Waiting for the phone), "sent" (the phone stored the last one in the last 10 minutes), or null.
(:glance)
module Sending {
    function state(now as Number) as String? {
        var v = Application.Storage.getValue(Outbox.STORE);
        if (v instanceof Array && v.size() > 0) {
            for (var i = 0; i < v.size(); i++) {
                if ("pen".equals((v[i] as Dictionary).get("st"))) {
                    return "ask";
                }
            }
            return "wait";
        }
        var s = Application.Storage.getValue(Outbox.SENT);
        return s instanceof Number && now - s < Outbox.SENT_SHOWN_S ? "sent" : null;
    }
}
