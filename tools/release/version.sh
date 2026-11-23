#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Prints "<version> <versionCode>" of one app: tools/release/version.sh android | mac (its VERSION file,
# contracts/apps/apps.json; versionCode = major·1 000 000 + minor·1 000 + patch). The release scripts read it this way.
set -eu
. "$(dirname "$0")/lib.sh"
version_info "${1:-}"
