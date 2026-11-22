// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;

// The debug case loader is a watch-app, which may not subscribe to complications: AuraComp (source/AuraComp.mc,
// (:complications)) is left out of its build (cases.jungle) and this takes its place. The cases set the fields' values themselves.
(:debug)
module AuraComp {
    var vals as Dictionary = {} as Dictionary;

    function sync(types as Array) as Void {}
}
