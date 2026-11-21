/**
 * Feedback Counter secure receiver / Google Sheets bound Apps Script.
 * Configure script properties DEVICE_TOKEN_<device UUID> = <random secret>.
 * Web app: Execute as me, accessible to anyone (authentication enforced in doPost).
 */
const HEADER = [
  '受信日時', '端末送信日時', 'よかった', '普通', '悪かった',
  '左→右', '右→左', '回答合計', '通過合計', '端末送信時刻(ms)',
  '送信ID', '端末ID'
];
const OLD_HEADER = HEADER.slice(0, 10);
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function doGet() {
  return jsonResponse_({ status: 'ok', service: 'feedback-counter' });
}

function doPost(e) {
  let lock = null;
  let acquired = false;
  try {
    if (!e || !e.postData || !e.postData.contents) throw new Error('Missing body');
    const data = JSON.parse(e.postData.contents);
    if (!data || Array.isArray(data) || typeof data !== 'object') throw new Error('Invalid JSON');
    const deviceId = validateUuid_(data.deviceId, 'deviceId');
    const requestId = validateUuid_(data.requestId, 'requestId');
    // Apps Script web app doPost does not reliably expose custom HTTP headers.
    // Credentials are transported only in the HTTPS JSON request body.
    const supplied = data.authToken;
    const expected = PropertiesService.getScriptProperties().getProperty('DEVICE_TOKEN_' + deviceId);
    if (!expected || typeof supplied !== 'string' ||
        supplied.length < 24 || supplied !== expected) throw new Error('Unauthorized');

    const title = validateSurveyTitle_(data.surveyTitle);
    for (const key of ['good', 'normal', 'bad', 'passL2R', 'passR2L', 'ts']) {
      if (!Object.prototype.hasOwnProperty.call(data, key) ||
          typeof data[key] !== 'number' ||
          !Number.isSafeInteger(data[key]) || data[key] < 0) {
        throw new Error('Invalid or missing field: ' + key);
      }
    }
    const sentAt = new Date(data.ts);
    if (!Number.isFinite(sentAt.getTime())) throw new Error('Invalid timestamp');

    lock = LockService.getScriptLock();
    acquired = lock.tryLock(20000);
    if (!acquired) throw new Error('Lock timeout');

    const ss = SpreadsheetApp.getActiveSpreadsheet();
    if (!ss) throw new Error('No bound spreadsheet');
    let sheet = ss.getSheetByName(title);
    const created = !sheet;
    if (!sheet) sheet = ss.insertSheet(title);

    if (sheet.getLastRow() === 0) {
      sheet.getRange(1, 1, 1, HEADER.length).setValues([HEADER]);
      sheet.setFrozenRows(1);
      sheet.getRange(1, 1, 1, HEADER.length).setFontWeight('bold');
      sheet.getRange('A:B').setNumberFormat('yyyy/MM/dd HH:mm:ss');
      sheet.getRange('C:J').setNumberFormat('0');
      sheet.getRange('K:L').setNumberFormat('@');
    } else {
      const first = sheet.getRange(1, 1, 1, HEADER.length).getValues()[0];
      if (HEADER.every((h, i) => first[i] === h)) {
        // New schema.
      } else if (OLD_HEADER.every((h, i) => first[i] === h) &&
                 first[10] === '' && first[11] === '') {
        // Migrate sheets previously created by this app without deleting any data.
        sheet.getRange(1, 11, 1, 2).setValues([HEADER.slice(10)]);
        sheet.getRange('K:L').setNumberFormat('@');
        sheet.getRange(1, 11, 1, 2).setFontWeight('bold');
      } else {
        throw new Error('Existing sheet has incompatible columns');
      }
    }

    // Check in the same lock as append to prevent concurrent duplicate insertion.
    // Only compare request IDs in K, then device ID in L.
    const lastRow = sheet.getLastRow();
    if (lastRow >= 2) {
      const matches = sheet.getRange(2, 11, lastRow - 1, 1)
        .createTextFinder(requestId).matchEntireCell(true).findAll();
      for (const match of matches) {
        if (String(sheet.getRange(match.getRow(), 12).getValue()) === deviceId) {
          return jsonResponse_({ status: 'ok', saved: true, duplicate: true,
                                 requestId: requestId, sheet: title, created: false });
        }
      }
    }

    sheet.appendRow([
      new Date(), sentAt, data.good, data.normal, data.bad,
      data.passL2R, data.passR2L,
      data.good + data.normal + data.bad,
      data.passL2R + data.passR2L,
      data.ts, requestId, deviceId
    ]);
    SpreadsheetApp.flush();
    return jsonResponse_({ status: 'ok', saved: true, duplicate: false,
                           requestId: requestId, sheet: title, created: created });
  } catch (err) {
    console.error(String(err && err.message || err));
    return jsonResponse_({ status: 'error', message: String(err && err.message || err) });
  } finally {
    if (acquired && lock) lock.releaseLock();
  }
}

function validateUuid_(value, name) {
  if (typeof value !== 'string' || !UUID_RE.test(value)) throw new Error('Invalid ' + name);
  return value;
}

function validateSurveyTitle_(raw) {
  if (typeof raw !== 'string') throw new Error('Missing surveyTitle');
  const title = raw.trim();
  if (!title || title.length > 100 || /[\\\/\?\*\[\]:\x00-\x1f\x7f]/.test(title)) {
    throw new Error('Invalid surveyTitle');
  }
  return title;
}

function jsonResponse_(data) {
  return ContentService.createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}

/** Run manually in Apps Script editor after editing the placeholders. */
function registerDeviceToken() {
  const deviceId = 'PASTE-ANDROID-DEVICE-UUID-HERE';
  const token = 'PASTE-RANDOM-TOKEN-AT-LEAST-24-CHARACTERS';
  validateUuid_(deviceId, 'deviceId');
  if (token.length < 24) throw new Error('Token must be at least 24 chars');
  PropertiesService.getScriptProperties().setProperty('DEVICE_TOKEN_' + deviceId, token);
  Logger.log('Device token registered for ' + deviceId);
}

/** Local test: replace placeholders first; run twice and confirm the second is duplicate. */
function testWrite() {
  const deviceId = 'PASTE-ANDROID-DEVICE-UUID-HERE';
  const token = 'PASTE-RANDOM-TOKEN-AT-LEAST-24-CHARACTERS';
  const payload = {
    deviceId: deviceId, authToken: token,
    requestId: Utilities.getUuid(), surveyTitle: 'テストアンケート',
    good: 10, normal: 5, bad: 2, passL2R: 25, passR2L: 18,
    ts: Date.now()
  };
  const mock = { postData: { contents: JSON.stringify(payload) } };
  Logger.log(doPost(mock).getContent());
  Logger.log(doPost(mock).getContent());
}
