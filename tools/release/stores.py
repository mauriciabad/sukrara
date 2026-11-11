# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""The store APIs the release uses (#187): Google Play (Android Publisher v3) and App Store Connect, Python 3.9 stdlib
plus `openssl` for the JWT signatures. The keys stay where they are (never printed, never copied into the repo):

- Play: the service account `~/.android/sukrara-play.json` (SUKRARA_PLAY_KEY), testing-track rights only.
- App Store Connect: `~/.apple-dev/AuthKey_<id>.p8` (ASC_KEY) and `~/.apple-dev/asc-issuer-id` (ASC_ISSUER_ID).

Read-only checks (nothing is changed; the Play edit is deleted, never committed):

    python3 tools/release/stores.py play-tracks    # edits.insert → tracks.list → edits.delete
    python3 tools/release/stores.py asc-builds     # the app's latest builds and their processing / compliance state
"""
import base64
import glob
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

PACKAGE = "app.mauri.sukrara"
BUNDLE_ID = "app.mauri.sukrara"
PLAY_API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" + PACKAGE
PLAY_UPLOAD = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/" + PACKAGE
PLAY_SCOPE = "https://www.googleapis.com/auth/androidpublisher"
ASC_API = "https://api.appstoreconnect.apple.com"
PLAY_NOTES_MAX = 500  # Play's limit per language


class ApiError(Exception):
    def __init__(self, status, message):
        Exception.__init__(self, "HTTP %s: %s" % (status, message))
        self.status = status
        self.message = message


def b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def jwt_signing_input(header, claims):
    return "%s.%s" % (b64url(json.dumps(header, separators=(",", ":")).encode()),
                      b64url(json.dumps(claims, separators=(",", ":")).encode()))


def openssl_sign(data, key_pem=None, key_file=None):
    """SHA-256 signature of `data` by openssl (RSA: PKCS#1 v1.5; EC: DER ECDSA). The key goes in on stdin (key_pem) or
    stays in its own file (key_file): never on a command line, never in a temporary file."""
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "in")
        with open(path, "wb") as f:
            f.write(data)
        cmd = ["openssl", "dgst", "-sha256", "-sign", key_file or "/dev/stdin", path]
        r = subprocess.run(cmd, input=key_pem.encode() if key_pem else None, capture_output=True)
    if r.returncode != 0 or not r.stdout:
        raise RuntimeError("openssl couldn't sign (exit %d)" % r.returncode)  # stderr could quote the key: not shown
    return r.stdout


def der_to_raw(der, size=32):
    """A DER ECDSA signature (SEQUENCE of two INTEGERs) → r ‖ s, each `size` bytes, as JWS ES256 wants it."""
    def read_len(i):
        n = der[i]
        if n < 0x80:
            return n, i + 1
        k = n & 0x7F
        return int.from_bytes(der[i + 1:i + 1 + k], "big"), i + 1 + k
    if der[0] != 0x30:
        raise ValueError("not a DER sequence")
    _, i = read_len(1)
    out = b""
    for _ in range(2):
        if der[i] != 0x02:
            raise ValueError("not a DER integer")
        n, i = read_len(i + 1)
        v = der[i:i + n].lstrip(b"\0")
        if len(v) > size:
            raise ValueError("integer longer than %d bytes" % size)
        out += v.rjust(size, b"\0")
        i += n
    return out


def google_claims(email, now, token_uri="https://oauth2.googleapis.com/token"):
    return {"iss": email, "scope": PLAY_SCOPE, "aud": token_uri, "iat": now, "exp": now + 3600}


def asc_claims(issuer, now):
    return {"iss": issuer, "iat": now, "exp": now + 1200, "aud": "appstoreconnect-v1"}  # 20 minutes at most


def http(method, url, headers=None, body=None, data=None, timeout=600):
    """JSON in, JSON out. `body` is sent as JSON, `data` as raw bytes (headers name the type)."""
    headers = dict(headers or {})
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read()
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            err = json.loads(raw)
            msg = err.get("error", {}).get("message") if isinstance(err.get("error"), dict) else None
            if not msg and err.get("errors"):
                msg = "; ".join("%s: %s" % (x.get("title", ""), x.get("detail", "")) for x in err["errors"])
            msg = msg or err.get("error_description") or raw.decode(errors="replace")[:500]
        except ValueError:
            msg = raw.decode(errors="replace")[:500]
        raise ApiError(e.code, msg)
    return json.loads(raw) if raw.strip() else {}


# --- Google Play -----------------------------------------------------------------------------------------------------

def play_track_body(track, version, code, notes, status="completed"):
    """The tracks.update body: one release of one version code on `track`, with its notes per language."""
    for lang, text in notes.items():
        if not text.strip():
            raise ValueError("empty Play notes for %s" % lang)
        if len(text) > PLAY_NOTES_MAX:
            raise ValueError("Play notes for %s are %d characters (at most %d)" % (lang, len(text), PLAY_NOTES_MAX))
    return {
        "track": track,
        "releases": [{
            "name": version,
            "versionCodes": [str(code)],
            "status": status,
            "releaseNotes": [{"language": lang, "text": notes[lang]} for lang in sorted(notes)],
        }],
    }


def track_has(track, code):
    """The release of `track` (a tracks.get / tracks.list item) that carries version code `code`, or None."""
    for rel in track.get("releases", []):
        if str(code) in [str(c) for c in rel.get("versionCodes", [])]:
            return rel
    return None


class Play:
    def __init__(self, key_path=None):
        self.key_path = key_path or os.environ.get("SUKRARA_PLAY_KEY") or os.path.expanduser("~/.android/sukrara-play.json")
        self._token = None

    def token(self):
        if self._token and self._token[1] > time.time() + 60:
            return self._token[0]
        with open(self.key_path) as f:
            sa = json.load(f)
        now = int(time.time())
        token_uri = sa.get("token_uri", "https://oauth2.googleapis.com/token")
        signing = jwt_signing_input({"alg": "RS256", "typ": "JWT"}, google_claims(sa["client_email"], now, token_uri))
        jwt = signing + "." + b64url(openssl_sign(signing.encode(), key_pem=sa["private_key"]))
        del sa
        form = urllib.parse.urlencode({"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion": jwt})
        r = http("POST", token_uri, {"Content-Type": "application/x-www-form-urlencoded"}, data=form.encode())
        self._token = (r["access_token"], now + int(r.get("expires_in", 3600)))
        return self._token[0]

    def call(self, method, path, body=None, data=None, upload=False, query=None):
        url = (PLAY_UPLOAD if upload else PLAY_API) + path
        if query:
            url += "?" + urllib.parse.urlencode(query)
        headers = {"Authorization": "Bearer " + self.token()}
        if data is not None:
            headers["Content-Type"] = "application/octet-stream"
        return http(method, url, headers, body=body, data=data)

    def insert_edit(self):
        return self.call("POST", "/edits", body={})["id"]

    def delete_edit(self, edit):
        self.call("DELETE", "/edits/%s" % edit)

    def tracks(self, edit):
        return self.call("GET", "/edits/%s/tracks" % edit).get("tracks", [])

    def track(self, edit, name):
        return self.call("GET", "/edits/%s/tracks/%s" % (edit, name))

    def bundles(self, edit):
        return self.call("GET", "/edits/%s/bundles" % edit).get("bundles", [])

    def upload_bundle(self, edit, path):
        with open(path, "rb") as f:
            data = f.read()
        return self.call("POST", "/edits/%s/bundles" % edit, data=data, upload=True, query={"uploadType": "media"})

    def update_track(self, edit, body):
        return self.call("PUT", "/edits/%s/tracks/%s" % (edit, body["track"]), body=body)

    def commit(self, edit, not_for_review=False):
        return self.call("POST", "/edits/%s:commit" % edit,
                         query={"changesNotSentForReview": "true"} if not_for_review else None)


# --- App Store Connect -----------------------------------------------------------------------------------------------

def asc_key_paths():
    key = os.environ.get("ASC_KEY") or next(iter(sorted(glob.glob(os.path.expanduser("~/.apple-dev/AuthKey_*.p8")))), "")
    issuer = os.environ.get("ASC_ISSUER_ID", "")
    if not issuer:
        try:
            with open(os.path.expanduser("~/.apple-dev/asc-issuer-id")) as f:
                issuer = f.read().strip()
        except OSError:
            pass
    return key, issuer


def asc_submission_bodies(app_id, version_id, platform="MAC_OS"):
    """The App Review submission (--production, from 1.0.0): a review submission for the platform, its one item (the
    App Store version), then the PATCH that submits it."""
    return (
        {"data": {"type": "reviewSubmissions", "attributes": {"platform": platform},
                  "relationships": {"app": {"data": {"type": "apps", "id": app_id}}}}},
        lambda submission_id: {"data": {"type": "reviewSubmissionItems", "relationships": {
            "reviewSubmission": {"data": {"type": "reviewSubmissions", "id": submission_id}},
            "appStoreVersion": {"data": {"type": "appStoreVersions", "id": version_id}}}}},
        lambda submission_id: {"data": {"type": "reviewSubmissions", "id": submission_id,
                                        "attributes": {"submitted": True}}},
    )


class AppStoreConnect:
    def __init__(self, key_path=None, issuer=None):
        k, i = asc_key_paths()
        self.key_path = key_path or k
        self.issuer = issuer or i
        if not self.key_path or not self.issuer:
            raise RuntimeError("no App Store Connect API key or issuer (ASC_KEY, ASC_ISSUER_ID or ~/.apple-dev)")
        self.key_id = os.path.basename(self.key_path)[len("AuthKey_"):-len(".p8")]

    def token(self):
        signing = jwt_signing_input({"alg": "ES256", "kid": self.key_id, "typ": "JWT"},
                                    asc_claims(self.issuer, int(time.time())))
        return signing + "." + b64url(der_to_raw(openssl_sign(signing.encode(), key_file=self.key_path)))

    def call(self, method, path, params=None, body=None):
        url = ASC_API + path + ("?" + urllib.parse.urlencode(params) if params else "")
        return http(method, url, {"Authorization": "Bearer " + self.token()}, body=body)

    def app_id(self, bundle_id=BUNDLE_ID):
        apps = self.call("GET", "/v1/apps", {"filter[bundleId]": bundle_id, "fields[apps]": "bundleId,name"})["data"]
        if not apps:
            raise RuntimeError("App Store Connect has no app %s" % bundle_id)
        return apps[0]["id"]

    def builds(self, app_id, version=None, build=None, limit=10):
        """The app's builds, newest upload first: [{id, version (CFBundleVersion), marketing version, processingState,
        usesNonExemptEncryption, uploadedDate, expired}]."""
        params = {"filter[app]": app_id, "sort": "-uploadedDate", "limit": str(limit), "include": "preReleaseVersion",
                  "fields[builds]": "version,processingState,usesNonExemptEncryption,uploadedDate,expired,preReleaseVersion",
                  "fields[preReleaseVersions]": "version,platform"}
        if version:
            params["filter[preReleaseVersion.version]"] = version
        if build:
            params["filter[version]"] = build
        r = self.call("GET", "/v1/builds", params)
        pre = {x["id"]: x["attributes"].get("version") for x in r.get("included", []) if x["type"] == "preReleaseVersions"}
        out = []
        for b in r["data"]:
            a = b["attributes"]
            rel = ((b.get("relationships") or {}).get("preReleaseVersion") or {}).get("data") or {}
            out.append({"id": b["id"], "build": a.get("version"), "version": pre.get(rel.get("id")),
                        "state": a.get("processingState"), "encryption": a.get("usesNonExemptEncryption"),
                        "uploaded": a.get("uploadedDate"), "expired": a.get("expired")})
        return out

    def submit_for_review(self, app_id, version, build_id, platform="MAC_OS"):
        """--production only (from 1.0.0; needs a key with App Manager or Admin access, the Developer key gets 403):
        the App Store version `version` with build `build_id`, submitted for App Review. Never run before 1.0.0."""
        found = self.call("GET", "/v1/apps/%s/appStoreVersions" % app_id,
                          {"filter[versionString]": version, "filter[platform]": platform})["data"]
        if found:
            version_id = found[0]["id"]
        else:
            version_id = self.call("POST", "/v1/appStoreVersions", body={"data": {
                "type": "appStoreVersions", "attributes": {"platform": platform, "versionString": version},
                "relationships": {"app": {"data": {"type": "apps", "id": app_id}}}}})["data"]["id"]
        self.call("PATCH", "/v1/appStoreVersions/%s/relationships/build" % version_id,
                  body={"data": {"type": "builds", "id": build_id}})
        create, item, submit = asc_submission_bodies(app_id, version_id, platform)
        submission_id = self.call("POST", "/v1/reviewSubmissions", body=create)["data"]["id"]
        self.call("POST", "/v1/reviewSubmissionItems", body=item(submission_id))
        self.call("PATCH", "/v1/reviewSubmissions/%s" % submission_id, body=submit(submission_id))
        return submission_id


# --- read-only checks ------------------------------------------------------------------------------------------------

def play_tracks():
    play = Play()
    edit = play.insert_edit()
    print("edits.insert: edit opened")
    try:
        for t in play.tracks(edit):
            rels = ["%s %s %s" % (r.get("name", "?"), "/".join(r.get("versionCodes", [])), r.get("status"))
                    for r in t.get("releases", [])]
            print("  %-12s %s" % (t["track"], "; ".join(rels) or "(no release)"))
        print("  bundles: %s" % ", ".join(sorted(str(b["versionCode"]) for b in play.bundles(edit))))
    finally:
        play.delete_edit(edit)
        print("edits.delete: edit discarded, nothing committed")


def asc_builds():
    asc = AppStoreConnect()
    app = asc.app_id()
    print("app %s (%s)" % (app, BUNDLE_ID))
    for b in asc.builds(app, limit=5):
        print("  %-8s %-24s %-10s encryption=%s uploaded=%s" % (b["version"], b["build"], b["state"], b["encryption"],
                                                                b["uploaded"]))


if __name__ == "__main__":
    cmds = {"play-tracks": play_tracks, "asc-builds": asc_builds}
    if len(sys.argv) != 2 or sys.argv[1] not in cmds:
        sys.exit(__doc__)
    try:
        cmds[sys.argv[1]]()
    except (ApiError, RuntimeError, OSError) as e:
        sys.exit("%s: %s" % (sys.argv[1], e))
