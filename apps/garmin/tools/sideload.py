# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Copies an app onto the Garmin watch on USB (MTP) as GARMIN/Apps/<NAME>.PRG; the watch installs it when it's
unplugged (docs/DEVELOPMENT.md › Garmin). Python stdlib (ctypes) over libmtp (brew install libmtp).

  python3 apps/garmin/tools/sideload.py apps/garmin/bin/SukraraAura.prg SUKAURA.PRG [--dry-run]

<NAME>.PRG: 8.3, upper case, like the watch's own files; each app its own (tools/install.sh watch: SUKAURA, SUKRARA,
SUKFIELD). Touches only that one file: an older copy of it in GARMIN/Apps is replaced; nothing else on the watch is read beyond
the folder and file lists (names only), nothing else is written. libmtp's mtp-sendfile can't send to this
watch (it leaves the storage unset: "Error sending file"), hence the explicit storage here. Close Garmin Express /
OpenMTP first (one MTP client at a time).
"""
import ctypes
import ctypes.util
import os
import re
import sys

UNKNOWN = 44  # LIBMTP_FILETYPE_UNKNOWN


class File(ctypes.Structure):
    pass


File._fields_ = [("item_id", ctypes.c_uint32), ("parent_id", ctypes.c_uint32), ("storage_id", ctypes.c_uint32),
                 ("filename", ctypes.c_char_p), ("filesize", ctypes.c_uint64), ("modificationdate", ctypes.c_long),
                 ("filetype", ctypes.c_int), ("next", ctypes.POINTER(File))]


class Folder(ctypes.Structure):
    pass


Folder._fields_ = [("folder_id", ctypes.c_uint32), ("parent_id", ctypes.c_uint32), ("storage_id", ctypes.c_uint32),
                   ("name", ctypes.c_char_p), ("sibling", ctypes.POINTER(Folder)), ("child", ctypes.POINTER(Folder))]


def libmtp():
    for p in (ctypes.util.find_library("mtp"), "/opt/homebrew/lib/libmtp.dylib", "/usr/local/lib/libmtp.dylib"):
        if p and (os.path.exists(p) or not p.startswith("/")):
            try:
                return ctypes.CDLL(p)
            except OSError:
                pass
    raise SystemExit("sideload: no libmtp (brew install libmtp)")


def main(argv):
    args = [a for a in argv if a != "--dry-run"]
    if len(args) != 2 or not re.fullmatch(r"[A-Z0-9]{1,8}\.PRG", args[1]):
        print(__doc__)
        return 2
    prg, name, dry = args[0], args[1].encode(), "--dry-run" in argv
    if not os.path.isfile(prg):
        raise SystemExit("sideload: no %s" % prg)
    lib = libmtp()
    lib.LIBMTP_Init()
    lib.LIBMTP_Get_First_Device.restype = ctypes.c_void_p
    dev = lib.LIBMTP_Get_First_Device()
    if not dev:
        raise SystemExit("sideload: no Garmin watch on USB (MTP)")
    try:
        lib.LIBMTP_Get_Manufacturername.restype = ctypes.c_char_p
        lib.LIBMTP_Get_Manufacturername.argtypes = [ctypes.c_void_p]
        maker = lib.LIBMTP_Get_Manufacturername(dev) or b""
        if b"Garmin" not in maker:
            raise SystemExit("sideload: the MTP device is %r, not a Garmin" % maker)
        # GARMIN/Apps: its id and storage
        lib.LIBMTP_Get_Folder_List.restype = ctypes.POINTER(Folder)
        lib.LIBMTP_Get_Folder_List.argtypes = [ctypes.c_void_p]
        root = lib.LIBMTP_Get_Folder_List(dev)
        apps = None
        f = root
        while f:
            if f.contents.name == b"GARMIN":
                c = f.contents.child
                while c:
                    if c.contents.name in (b"Apps", b"APPS"):
                        apps = (c.contents.folder_id, c.contents.storage_id)
                    c = c.contents.sibling
            f = f.contents.sibling
        if apps is None:
            raise SystemExit("sideload: no GARMIN/Apps folder on the watch")
        # an older copy of our file, in that folder only
        lib.LIBMTP_Get_Filelisting_With_Callback.restype = ctypes.POINTER(File)
        lib.LIBMTP_Get_Filelisting_With_Callback.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p]
        old = []
        f = lib.LIBMTP_Get_Filelisting_With_Callback(dev, None, None)
        while f:
            if f.contents.parent_id == apps[0] and f.contents.filename == name:
                old.append(f.contents.item_id)
            f = f.contents.next
        if dry:
            print("sideload: would replace %s and send %s to GARMIN/Apps/%s" % (old or "nothing", prg, name.decode()))
            return 0
        lib.LIBMTP_Delete_Object.argtypes = [ctypes.c_void_p, ctypes.c_uint32]
        for item in old:
            print("sideload: removing the older %s (object %d)" % (name.decode(), item))
            if lib.LIBMTP_Delete_Object(dev, item) != 0:
                raise SystemExit("sideload: couldn't remove the older copy")
        new = File()
        new.filename = name
        new.filesize = os.path.getsize(prg)
        new.parent_id, new.storage_id = apps
        new.filetype = UNKNOWN
        lib.LIBMTP_Send_File_From_File.argtypes = [ctypes.c_void_p, ctypes.c_char_p, ctypes.POINTER(File), ctypes.c_void_p, ctypes.c_void_p]
        if lib.LIBMTP_Send_File_From_File(dev, prg.encode(), ctypes.byref(new), None, None) != 0:
            lib.LIBMTP_Dump_Errorstack.argtypes = [ctypes.c_void_p]
            lib.LIBMTP_Dump_Errorstack(dev)
            raise SystemExit("sideload: sending failed")
        print("sideload: %s is on the watch (object %d); it installs when the watch is unplugged" % (name.decode(), new.item_id))
        return 0
    finally:
        lib.LIBMTP_Release_Device.argtypes = [ctypes.c_void_p]
        lib.LIBMTP_Release_Device(dev)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
