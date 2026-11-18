// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Investigation 45b (docs/45): the Daily Log report with the website session of a signed-in libreview.com tab.
// Paste into that tab's console (or run it with a browser tool). Read-only: reportSettings, one report, its channel.
// The website's own login needs the SMS step (`status 4`, `2faVerify`), so this uses the session the person opened.
// Nothing leaves the tab: the token stays in it; the result is a summary without names or ids.
// `DAYS` days ending with the account's `endDate` (the newest day with data).
(async (DAYS = 2) => {
  const BASE = 'https://api-eu.libreview.io'; // the account's region (the website's own calls say which)
  const token = JSON.parse(sessionStorage.getItem('token')).token;
  const user = JSON.parse(sessionStorage.getItem('accountId'));
  const sha = async (s) => Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s))))
    .map((b) => b.toString(16).padStart(2, '0')).join('');
  const H = { Authorization: 'Bearer ' + token, 'Account-Id': await sha(user), Accept: 'application/json',
    'Content-Type': 'application/json', product: 'lv' };
  const get = async (url, init) => (await fetch(url, Object.assign({ headers: H }, init))).json();

  // the account's devices: LibreView's own id → upload DeviceId (sn), type (40068 Libre 3 app, 40066 LibreLink, 9000x pens)
  const rs = (await get(BASE + '/reportSettings')).data;
  const src = rs.dataSources;
  const ids = Object.keys(src).filter((k) => (src[k].daysData || []).length);
  ids.sort((a, b) => (src[b].uploadDate || 0) - (src[a].uploadDate || 0));
  const prim = ids[0];
  const body = { PrimaryDeviceId: prim, PrimaryDeviceTypeId: src[prim].type,
    SecondaryDeviceIds: Object.keys(src).filter((k) => k !== prim), PrintReportsWithPatientInformation: false,
    ReportIds: [500000 + src[prim].type], ClientReportIDs: [5], StartDates: [rs.endDate - (DAYS - 1) * 86400],
    EndDate: rs.endDate, PatientId: user, CultureCode: 'en-US', GlucoseUnits: 1, DateFormat: 1, TimeFormat: 1 };
  const gen = await get(BASE + '/reports', { method: 'POST', body: JSON.stringify(body) });
  const lp = (await get(gen.data.url)).data.lp; // the channel: {ws, lp}
  let link = null;
  for (let i = 0; i < 10 && !link; i++) {
    const ev = await get(lp); // {operation: 'update', args: {urls: {'5': 'https://lrs-eu.libreview.io/report/540068/…'}}}
    link = ((ev.args || {}).urls || {})['5'];
    if (!link) await new Promise((r) => setTimeout(r, 2000));
  }
  const u = new URL(link);
  u.searchParams.set('session', token);
  const html = await (await fetch(u, { headers: { Accept: 'text/html' } })).text();
  const at = html.indexOf('{', html.search(/window\.report(?:Data)?\s*=\s*/));
  let depth = 0, quoted = false, escaped = false, end = -1;
  for (let i = at; i < html.length && end < 0; i++) {
    const c = html[i];
    if (quoted) { if (escaped) escaped = false; else if (c === '\\') escaped = true; else if (c === '"') quoted = false; }
    else if (c === '"') quoted = true; else if (c === '{') depth++; else if (c === '}' && --depth === 0) end = i + 1;
  }
  const D = JSON.parse(html.slice(at, end)).Data;
  // times are the device's wall clock as Unix-shaped seconds (00:00 of the day = Date)
  const hm = (t, day) => new Date((t - day) * 1000).toISOString().slice(11, 16);
  return D.Days.map((d) => ({
    day: new Date(d.Date * 1000).toISOString().slice(0, 10),
    glucose: d.Glucose.map((s) => s.length + ' points ' + hm(s[0].Timestamp, d.Date) + '–' + hm(s[s.length - 1].Timestamp, d.Date)),
    rapidTimes: d.RapidActingInsulinLogged.map((t) => hm(t, d.Date)),
    carbsTimes: (d.CarbsLogged || []).map((t) => (typeof t === 'number' ? hm(t, d.Date) : t)),
    hours: d.Hours.filter((h) => h.IsRapidActingInsulinLogged || h.IsLongActingInsulinLogged || h.IsCarbsLogged || (h.notes || []).length)
      .map((h) => ({ hour: h.Hour, rapid: h.RapidActingInsulinTotal || h.RapidActingInsulinManual, long: h.LongActingInsulin,
        carbs: h.Carbs, notes: (h.notes || []).map((n) => hm(n.timestamp, d.Date) + ' ' + n.type + ' ' + n.note) })),
  }));
})();
