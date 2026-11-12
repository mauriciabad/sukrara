# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
#
# dmgbuild settings for the Sukrara installer window (dmg.sh passes -D app=… -D background=… -D volume_icon=…).
# dmgbuild writes the Finder layout (.DS_Store) itself, so no Finder scripting or GUI session is needed.
import os.path

app = defines["app"]
files = [app]
symlinks = {"Applications": "/Applications"}
# The volume's own icon in Finder's sidebar, the desktop and the window's title: a drive (#181: not the app's icon);
# dmgbuild copies it to .VolumeIcon.icns and sets the volume's custom icon flag (SetFile -a C).
icon = defines["volume_icon"]
background = defines["background"]                            # 640×400 pt, 1× + 2× in one TIFF

format = "UDZO"
window_rect = ((200, 140), (640, 400))
default_view = "icon-view"
show_status_bar = show_tab_view = show_toolbar = show_pathbar = show_sidebar = False
icon_size = 128
text_size = 13
# Centres line up with the arrow in background.swift (y from the top).
icon_locations = {os.path.basename(app): (170, 200), "Applications": (470, 200)}
