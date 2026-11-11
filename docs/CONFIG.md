# POS / kasa bağlantısı — configuration

Counter Eyes reads **closed sales** and turns each fresh one into one QR display. It never writes to
the POS. During setup choose a POS type; you can change it later from the ⚙ button.

## 1. Simpra

| Field | Value |
| --- | --- |
| POS type | `Simpra` |
| API address | empty → `https://api.simpra.app/checks` |
| Access token | your Simpra access token |

Request the app performs (unchanged from Simpra's documented endpoint):

```
GET https://api.simpra.app/checks?status=closed&sort_by=desc&per_page=100&page=1
    &closed_at_since=<epoch>&closed_at_until=<epoch>
Simpra-Access-Token: <token>
Accept: application/json
```

Paging stops when `meta.has_next_page` is false (max 25 pages, 8 MB per response, 7 s connect /
9 s read timeout). Records are de-duplicated by `id:closed_at`; only sales closed within the last
35 seconds trigger the QR.

## 2. Generic HTTP/JSON — any other POS

Point the app at **your own endpoint that returns the latest closed sales** (a report URL, a small
script next to your POS, an automation tool, a cloud function…). The response must be JSON.

| Field | Example | Notes |
| --- | --- | --- |
| API address | `http://192.168.1.50:8080/last-closed` | `http://` allowed for LAN systems |
| Header name / value | `Authorization` / `Bearer abc123` | optional, value stored encrypted |
| Items path | `data.checks` | dotted path to the array; empty = root array |
| Closed-at field | `closed_at` | epoch seconds, epoch ms or ISO-8601 |
| Id field | `id` | must be unique per sale |
| Amount field | `total_amount` | optional; used for the 0-value switch |

Accepted shapes:

```json
{"data": {"checks": [
  {"id": "1042", "closed_at": 1762771200, "total_amount": 240},
  {"id": 1043, "closed_at": "2026-11-10T18:20:11Z", "total_amount": 0}
]}}
```

```json
[
  {"id": "1042", "closed_at": 1762771200123, "amount": 240}
]
```

Rules:

- Every item must have an id and a parseable closed-at value, otherwise it is ignored.
- Only items closed within the last 35 seconds are used; each `id:closed_at` triggers at most once.
- Nothing is written back; no auth is required by the app beyond the optional header.
- Polling every 2–60 s (default 5 s). Use a static or cheap endpoint — the tablet is the only client.

### Tiny shim example

If your POS has no JSON API, put a 20-line service in front of it (read-only database query, exported
CSV, message queue…) and return the same shape:

```js
// GET /last-closed  →  {"items":[...]}
app.get('/last-closed', (req, res) => {
  res.json({ items: db.closedSalesLastMinutes(5) }); // [{id, closed_at, total_amount}]
});
```

## 3. Demo

No POS. The QR can be triggered manually from the ⚙ button ("QR'ı dene"), which is also handy for
testing the review link and the counter setup.

## Review QR

- `Review link` = your Google review URL. Empty → the QR step is skipped entirely.
- The QR is shown for 30 s (touch to dismiss) after a closed sale.
- QR priority over idle gestures and conversations is built in.

## Privacy and secrets

- The Simpra token and the generic header value are stored with AES/GCM in the Android Keystore
  (alias `counter-eyes-secret`); they are not in the APK, not in the repo and not in backups.
- `adb install -r` keeps them. Uninstall / clear data deletes them.
- The activity hides the setup dialog from screenshots (`FLAG_SECURE`) — keep this in mind when
  taking documentation screenshots.
