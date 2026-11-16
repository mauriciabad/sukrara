// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/** The made-up day, ready to play (day.json made by scripts/showcase-day.mjs; day.ts reads it). */
import data from './day.json';
import { dayScenario, type DayData } from './day.ts';

export const DAY = dayScenario(data as DayData);
