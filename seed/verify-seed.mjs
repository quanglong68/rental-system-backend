import fs from 'node:fs';
import path from 'node:path';

const BASE = 'http://localhost:8080';
const PASSWORD = 'Test@12345';
const statePath = path.resolve('seed/state.json');
const state = JSON.parse(fs.readFileSync(statePath, 'utf8'));
const ns = (k) => state[k] ?? {};

const failures = [];
const checks = [];
function check(name, actual, expected) {
  const ok = JSON.stringify(actual) === JSON.stringify(expected);
  checks.push({ name, actual, expected, ok });
  if (!ok) failures.push({ name, actual, expected });
}

function parseJson(text) {
  return JSON.parse(text.replace(/([:,\[]\s*)(-?\d{16,})(?=\s*[,\]}])/g, '$1"$2"'));
}

async function login(username, password = PASSWORD) {
  for (let i = 0; i < 4; i += 1) {
    const res = await fetch(`${BASE}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });
    if (res.status === 200) return parseJson(await res.text());
    if ([502, 503, 504].includes(res.status)) {
      await new Promise((r) => setTimeout(r, 700));
      continue;
    }
    throw new Error(`login ${username} -> ${res.status} ${await res.text()}`);
  }
  throw new Error(`login ${username} -> 504`);
}

async function api(token, method, p, body) {
  const res = await fetch(`${BASE}${p}`, {
    method,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  const data = text ? parseJson(text) : null;
  if (!res.ok) throw new Error(`${method} ${p} -> ${res.status} ${text}`);
  return data;
}

const main = async () => {
  // 1. Login mọi tài khoản seed
  const accountReport = {};
  let adminToken = null;
  for (const [key, acc] of Object.entries(ns('accounts'))) {
    try {
      const res = await login(acc.username);
      accountReport[key] = { username: acc.username, ok: true, type: res.account?.accountType ?? acc.accountType };
      if (key === 'admin') adminToken = res.accessToken;
    } catch (e) {
      accountReport[key] = { username: acc.username, ok: false, error: e.message };
      failures.push({ name: `login ${key}`, actual: e.message, expected: 'ok' });
    }
  }

  const admin = { token: adminToken };

  // 2. Phòng
  const roomsExpected = {
    A101: 'VACANT', A102: 'VACANT', A103: 'MAINTENANCE', A104: 'OCCUPIED',
    A105: 'OCCUPIED', A106: 'VACANT', A107: 'OCCUPIED', A108: 'VACANT',
    B201: 'OCCUPIED', B202: 'VACANT', B203: 'UPCOMING_VACANT', B204: 'VACANT', B205: 'VACANT',
  };
  const roomStatus = {};
  for (const [code, id] of Object.entries(ns('rooms'))) {
    const room = await api(admin.token, 'GET', `/api/v1/rooms/${id}`);
    roomStatus[code] = { status: room.status, listingId: room.listingId ?? null };
    check(`room ${code}.status`, room.status, roomsExpected[code]);
  }

  // 3. Tin đăng
  const listingStatus = {};
  for (const [code, id] of Object.entries(ns('rooms'))) {
    const res = await fetch(`${BASE}/api/v1/listings/room/${id}`, {
      headers: { Authorization: `Bearer ${admin.token}` },
    });
    if (res.status === 404) {
      listingStatus[code] = null;
      continue;
    }
    if (!res.ok) throw new Error(`listing ${code} -> ${res.status}`);
    const listing = parseJson(await res.text());
    listingStatus[code] = listing.status;
  }
  check('listing A103 (MAINTENANCE) đóng', listingStatus.A103, 'CLOSED');
  check('listing B203 mở lại sau trả phòng', listingStatus.B203, 'ACTIVE');
  check('listing A104 (đang thuê) đóng', listingStatus.A104, 'CLOSED');
  check('listing A101 mở', listingStatus.A101, 'ACTIVE');

  // 4. Hợp đồng
  const contracts = {};
  for (const [key, id] of Object.entries(ns('contracts'))) {
    const c = await api(admin.token, 'GET', `/api/v1/contracts/${id}`);
    contracts[key] = { id: c.id, status: c.status, startDate: c.startDate, endDate: c.endDate, tenants: (c.tenants ?? []).length };
  }
  check('A104 ACTIVE', contracts.A104?.status, 'ACTIVE');
  check('A105 ACTIVE', contracts.A105?.status, 'ACTIVE');
  check('A107 ACTIVE', contracts.A107?.status, 'ACTIVE');
  check('B201 ACTIVE', contracts.B201?.status, 'ACTIVE');
  check('B203 ACTIVE (chờ scheduler kết thúc)', contracts.B203?.status, 'ACTIVE');
  check('A102 DRAFT', contracts.A102?.status, 'DRAFT');
  check('B204 DRAFT', contracts.B204?.status, 'DRAFT');
  check('B201 có 2 người ở', contracts.B201?.tenants, 2);
  check('A105 có 2 người ở', contracts.A105?.tenants, 2);

  // 5. Trả phòng
  const moveOuts = {};
  const moveOutPage = await api(admin.token, 'GET', '/api/v1/move-out-requests?size=100');
  for (const [key, id] of Object.entries(ns('moveOuts'))) {
    const m = (moveOutPage.content ?? []).find((x) => String(x.id) === String(id));
    moveOuts[key] = m ? { status: m.status, expectedMoveOutDate: m.expectedMoveOutDate } : { status: 'MISSING' };
  }
  check('B201 move-out PENDING', moveOuts['B201-pending']?.status, 'PENDING');
  check('A107 move-out PENDING', moveOuts['A107-pending']?.status, 'PENDING');
  check('B203 move-out APPROVED', moveOuts['B203-approved']?.status, 'APPROVED');

  // 6. Ở ghép
  const posts = {};
  const postStatuses = { A104: 'OPEN', A105: 'OPEN', B201: 'OPEN', B203: 'CLOSED' };
  for (const [key, id] of Object.entries(ns('posts'))) {
    const p = await api(admin.token, 'GET', `/api/v1/roommate-posts/${id}`);
    posts[key] = p.status;
    check(`post ${key} status`, p.status, postStatuses[key]);
  }
  const appStatuses = { 'A104-app2': 'REJECTED', 'A104-app3': 'PENDING', 'A105-app1': 'ACCEPTED', 'B201-app4': 'REJECTED_FULL' };
  const postOwners = { A104: 'active', A105: 'renew', B201: 'moveout', B203: 'old' };
  const ownerTokens = {};
  for (const ownerKey of new Set(Object.values(postOwners))) {
    ownerTokens[ownerKey] = (await login(ns('accounts')[ownerKey].username)).accessToken;
  }
  const appActual = {};
  for (const [key, id] of Object.entries(ns('applications'))) {
    const postKey = key.split('-app')[0];
    const p = await api(ownerTokens[postOwners[postKey]], 'GET', `/api/v1/roommate-posts/${ns('posts')[postKey]}/applications?size=100`);
    const found = (p.content ?? []).find((a) => String(a.id) === String(id));
    appActual[key] = found?.status ?? 'MISSING';
    check(`application ${key}`, appActual[key], appStatuses[key]);
  }

  // 7. Hóa đơn
  const invoiceIds = { 22: 'PAID', 24: 'PARTIALLY_PAID', 23: 'OVERDUE', 28: 'UNPAID', 29: 'UNPAID', 30: 'UNPAID', 31: 'UNPAID', 32: 'UNPAID', 33: 'UNPAID', 25: 'UNPAID', 26: 'UNPAID', 27: 'UNPAID' };
  const invoices = {};
  for (const [id, expected] of Object.entries(invoiceIds)) {
    const inv = await api(admin.token, 'GET', `/api/v1/invoices/${id}`);
    invoices[id] = { status: inv.status, paidAmount: inv.paidAmount, totalAmount: inv.totalAmount };
    check(`invoice ${id} status`, inv.status, expected);
  }
  check('invoice 22 thanh toán đủ', invoices[22]?.paidAmount, invoices[22]?.totalAmount);
  check('invoice 24 thanh toán một phần', invoices[24]?.paidAmount > 0 && invoices[24]?.paidAmount < invoices[24]?.totalAmount, true);
  const octA = await api(admin.token, 'GET', `/api/v1/invoices?buildingId=${ns('buildings').A}&month=10&year=2026&size=100`);
  const a107Oct = (octA.content ?? []).filter((i) => String(i.roomId) === String(ns('rooms').A107));
  check('A107 tháng 10 không có hóa đơn (thiếu chỉ số nước)', a107Oct.length, 0);

  // 8. Bảo trì
  const reports = {};
  for (const [key, id] of Object.entries(ns('reports'))) {
    const r = await api(admin.token, 'GET', `/api/v1/reports/${id}`);
    reports[key] = r.status;
  }
  const reportExpected = { 'r1-A104': 'PENDING', 'r2-A105': 'REJECTED', 'r3-A104': 'APPROVED', 'r4-A107': 'APPROVED', 'r5-B201': 'APPROVED', 'r6-A104': 'PENDING', 'r7-B203': 'PENDING' };
  for (const [key, expected] of Object.entries(reportExpected)) check(`report ${key}`, reports[key], expected);

  const maintRequests = {};
  for (const [key, id] of Object.entries(ns('maintRequests'))) {
    const m = await api(admin.token, 'GET', `/api/v1/maintenance-requests/${id}`);
    maintRequests[key] = m.status;
  }
  check('Yêu cầu từ báo cáo 32 ASSIGNED', maintRequests['r3-A104'], 'ASSIGNED');
  check('Yêu cầu từ báo cáo 33 IN_PROGRESS', maintRequests['r4-A107'], 'IN_PROGRESS');
  check('Yêu cầu từ báo cáo 34 COMPLETED', maintRequests['r5-B201'], 'COMPLETED');

  const schedules = {};
  for (const b of ['A', 'B']) {
    const list = await api(admin.token, 'GET', `/api/v1/maintenance-schedules?buildingId=${ns('buildings')[b]}`);
    for (const s of list) schedules[s.title] = { active: s.active, nextDate: s.nextDate ?? null };
  }
  check('lịch PCCC active', schedules['Kiểm tra PCCC định kỳ (seed)']?.active, true);
  check('lịch thang máy active + dời ngày sau khi hoàn tất', schedules['Bảo trì thang máy (seed)']?.nextDate, '2026-12-10');
  check('lịch mái đã ngừng (không còn trong danh sách)', schedules['Kiểm tra mái và chống thấm (seed)'], undefined);

  // 9. Kết quả
  const output = {
    generatedAt: new Date().toISOString(),
    accounts: accountReport,
    rooms: roomStatus,
    listings: listingStatus,
    contracts,
    moveOuts,
    posts,
    applications: appActual,
    invoices,
    reports,
    maintenanceRequests: maintRequests,
    schedules,
    totalChecks: checks.length,
    failedChecks: failures,
  };
  fs.writeFileSync('seed/verify-report.json', JSON.stringify(output, null, 2), 'utf8');

  console.log(`Đã chạy ${checks.length} kiểm tra, thất bại ${failures.length}`);
  for (const f of failures) console.log('FAIL:', f.name, '| actual =', JSON.stringify(f.actual), '| expected =', JSON.stringify(f.expected));
  const badLogins = Object.entries(accountReport).filter(([, v]) => !v.ok);
  console.log(`Đăng nhập: ${Object.keys(accountReport).length - badLogins.length}/${Object.keys(accountReport).length} tài khoản OK`);
  console.log('Chi tiết: seed/verify-report.json');
};

main().catch((e) => {
  console.error('VERIFY LỖI:', e.stack ?? e.message);
  process.exitCode = 1;
});
