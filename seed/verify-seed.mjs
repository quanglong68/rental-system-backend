import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Kiểm tra hậu seed. Mọi ID/ngày/kỳ đều đọc từ seed-report.json do manual-test-seed.mjs sinh,
// nhờ vậy script không lệch khi chạy seed vào tháng khác hoặc sau khi reset database.
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const BASE = process.env.SEED_BASE_URL ?? 'http://localhost:8080';
const PASSWORD = process.env.SEED_PASSWORD ?? 'Test@12345';
const REPORT_PATH = path.join(__dirname, 'seed-report.json');

if (!fs.existsSync(REPORT_PATH)) {
  console.error('Không tìm thấy seed-report.json — hãy chạy manual-test-seed.mjs trước.');
  process.exit(1);
}
const report = JSON.parse(fs.readFileSync(REPORT_PATH, 'utf8'));
const accounts = report.accounts ?? {};
const rooms = report.rooms ?? {};
const buildings = report.buildings ?? {};
const contracts = report.keyContracts ?? {};
const flow7 = report.flow7 ?? {};

const failures = [];
const checks = [];
function check(name, actual, expected) {
  const ok = JSON.stringify(actual) === JSON.stringify(expected);
  checks.push({ name, actual, expected, ok });
  if (!ok) failures.push({ name, actual, expected });
}
function checkTrue(name, condition, detail) {
  checks.push({ name, actual: condition, expected: true, ok: Boolean(condition) });
  if (!condition) failures.push({ name, actual: detail ?? condition, expected: true });
}

function parseJson(text) {
  if (!text) return null;
  try {
    return JSON.parse(text.replace(/([:,\[]\s*)(-?\d{16,})(?=\s*[,\]}])/g, '$1"$2"'));
  } catch {
    return text;
  }
}

async function login(username, password = PASSWORD) {
  for (let i = 0; i < 6; i += 1) {
    const res = await fetch(`${BASE}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });
    if (res.status === 200) return parseJson(await res.text());
    if ([502, 503, 504].includes(res.status)) {
      await new Promise((r) => setTimeout(r, 1000));
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
  if (!res.ok) throw new Error(`${method} ${p} -> ${res.status} ${text.slice(0, 300)}`);
  return text ? parseJson(text) : null;
}

const roomId = (key) => String(rooms[key]);
const invoiceOf = (list, roomKey, customerKey) => (list ?? []).find(
  (i) => String(i.roomId) === roomId(roomKey) && String(i.customerId) === String(accounts[customerKey]?.id),
);

async function main() {
  // 1. Đăng nhập toàn bộ tài khoản seed
  const accountReport = {};
  let adminToken = null;
  const tokens = {};
  for (const [key, acc] of Object.entries(accounts)) {
    try {
      const res = await login(acc.username);
      tokens[key] = res.accessToken;
      accountReport[key] = { username: acc.username, ok: true, type: res.account?.accountType ?? acc.accountType };
      if (key === 'admin') adminToken = res.accessToken;
    } catch (e) {
      accountReport[key] = { username: acc.username, ok: false, error: e.message };
      failures.push({ name: `login ${key}`, actual: e.message, expected: 'ok' });
    }
  }
  if (!adminToken) throw new Error('Không đăng nhập được tài khoản admin seed');

  // 2. Phòng
  const roomsExpected = {
    A101: 'VACANT', A102: 'VACANT', A103: 'MAINTENANCE', A104: 'OCCUPIED',
    A105: 'OCCUPIED', A106: 'VACANT', A107: 'OCCUPIED', A108: 'OCCUPIED',
    B201: 'OCCUPIED', B202: 'VACANT', B203: 'UPCOMING_VACANT', B204: 'VACANT', B205: 'VACANT',
  };
  const roomStatus = {};
  for (const [code, id] of Object.entries(rooms)) {
    const room = await api(adminToken, 'GET', `/api/v1/rooms/${id}`);
    roomStatus[code] = { status: room.status, listingId: room.listingId ?? null };
    check(`room ${code}.status`, room.status, roomsExpected[code]);
  }

  // 3. Tin đăng
  const listingStatus = {};
  for (const [code, id] of Object.entries(rooms)) {
    const res = await fetch(`${BASE}/api/v1/listings/room/${id}`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    if (res.status === 404) {
      listingStatus[code] = null;
      continue;
    }
    if (!res.ok) throw new Error(`listing ${code} -> ${res.status}`);
    listingStatus[code] = parseJson(await res.text()).status;
  }
  check('listing A103 (MAINTENANCE) đóng', listingStatus.A103, 'CLOSED');
  check('listing B203 mở lại sau trả phòng', listingStatus.B203, 'ACTIVE');
  check('listing A104 (đang thuê) đóng', listingStatus.A104, 'CLOSED');
  check('listing A101 mở', listingStatus.A101, 'ACTIVE');

  // 4. Hợp đồng + chữ ký điện tử + PDF
  const contractInfo = {};
  for (const [label, id] of Object.entries(contracts)) {
    if (label === 'A106') continue;
    const c = await api(adminToken, 'GET', `/api/v1/contracts/${id}`);
    contractInfo[label] = {
      id: c.id,
      status: c.status,
      signatureStatus: c.signatureStatus,
      depositPaid: c.depositPaid,
      startDate: c.startDate,
      endDate: c.endDate,
      tenants: (c.tenants ?? []).length,
    };
  }
  for (const key of ['A104', 'A105', 'A107', 'A108_handover', 'B201', 'B203']) {
    check(`${key} ACTIVE`, contractInfo[key]?.status, 'ACTIVE');
    check(`${key} ký đủ hai bên`, contractInfo[key]?.signatureStatus, 'COMPLETED');
    check(`${key} đã ghi nhận cọc`, contractInfo[key]?.depositPaid, true);
  }
  check('A102 DRAFT', contractInfo.A102_draft?.status, 'DRAFT');
  check('B204 DRAFT', contractInfo.B204_draft?.status, 'DRAFT');
  check('B201 có 2 người ở', contractInfo.B201?.tenants, 2);
  check('A105 có 1 người ở', contractInfo.A105?.tenants, 1);

  for (const key of ['A104', 'A105', 'A107', 'A108_handover', 'B201', 'B203']) {
    const id = contracts[key];
    const res = await fetch(`${BASE}/api/v1/contracts/${id}/document`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    const type = res.headers.get('content-type') ?? '';
    const body = Buffer.from(await res.arrayBuffer());
    checkTrue(`PDF ${key} tải được`, res.status === 200 && type.includes('pdf') && body.length > 1000,
      `status=${res.status}, type=${type}, bytes=${body.length}`);
  }

  // 5. Trả phòng
  const moveOuts = {};
  const moveOutPage = await api(adminToken, 'GET', '/api/v1/move-out-requests?size=100');
  for (const [key, id] of Object.entries(report.moveOuts ?? {})) {
    const m = (moveOutPage.content ?? []).find((x) => String(x.id) === String(id));
    moveOuts[key] = m ? { status: m.status, expectedMoveOutDate: m.expectedMoveOutDate } : { status: 'MISSING' };
  }
  check('A105 move-out PENDING', moveOuts['A105-pending']?.status, 'PENDING');
  check('B201 move-out PENDING', moveOuts['B201-pending']?.status, 'PENDING');
  check('B203 move-out APPROVED', moveOuts['B203-approved']?.status, 'APPROVED');

  // 6. Ở ghép
  const posts = {};
  const postStatuses = { A104: 'OPEN', A105: 'OPEN', B201: 'OPEN', B203: 'CLOSED' };
  for (const [key, id] of Object.entries(report.roommatePosts ?? {})) {
    const p = await api(adminToken, 'GET', `/api/v1/roommate-posts/${id}`);
    posts[key] = p.status;
    check(`post ${key} status`, p.status, postStatuses[key]);
  }
  const appStatuses = {
    'A104-app2': 'REJECTED', 'A104-app3': 'PENDING', 'A105-app1': 'PENDING', 'B201-app4': 'REJECTED_FULL',
  };
  const postOwners = { A104: 'active', A105: 'renew', B201: 'moveout', B203: 'old' };
  const appActual = {};
  for (const [key, id] of Object.entries(report.roommateApplications ?? {})) {
    const postKey = key.split('-app')[0];
    const p = await api(tokens[postOwners[postKey]], 'GET',
      `/api/v1/roommate-posts/${report.roommatePosts[postKey]}/applications?size=100`);
    appActual[key] = (p.content ?? []).find((a) => String(a.id) === String(id))?.status ?? 'MISSING';
    check(`application ${key}`, appActual[key], appStatuses[key]);
  }

  // 7. Hóa đơn theo kỳ động trong report
  const invoices = report.invoices ?? {};
  const sepAll = [...(invoices.sepA ?? []), ...(invoices.sepB ?? [])];
  const octAll = [...(invoices.octA ?? []), ...(invoices.octB ?? [])];
  check('số hóa đơn kỳ trước-2', sepAll.length, 6);
  check('số hóa đơn kỳ trước', octAll.length, 4);

  const invoiceDetails = {};
  const expectedStatuses = [
    ['A104 tháng trước-2', invoiceOf(invoices.sepA, 'A104', 'active'), 'PAID'],
    ['A105 tháng trước-2', invoiceOf(invoices.sepA, 'A105', 'renew'), 'PARTIALLY_PAID'],
    ['A107 tháng trước-2', invoiceOf(invoices.sepA, 'A107', 'terminate'), 'OVERDUE'],
    ['B203 tháng trước (khóa PAID)', invoiceOf(invoices.octB, 'B203', 'old'), 'PAID'],
    ['A104 tháng trước (VNPay)', invoiceOf(invoices.octA, 'A104', 'active'), 'UNPAID'],
  ];
  for (const [label, inv, expected] of expectedStatuses) {
    checkTrue(`có hóa đơn ${label}`, Boolean(inv), 'không tìm thấy trong seed-report.json');
    if (!inv) continue;
    const detail = await api(adminToken, 'GET', `/api/v1/invoices/${inv.id}`);
    invoiceDetails[label] = { id: detail.id, status: detail.status, paidAmount: detail.paidAmount, totalAmount: detail.totalAmount };
    check(`invoice ${label}`, detail.status, expected);
  }
  if (invoiceDetails['A104 tháng trước-2'] && invoiceDetails['A105 tháng trước-2']) {
    const paid = invoiceDetails['A104 tháng trước-2'];
    check('A104 tháng trước-2 thu đủ', Number(paid.paidAmount), Number(paid.totalAmount));
    const partial = invoiceDetails['A105 tháng trước-2'];
    checkTrue('A105 tháng trước-2 thu một phần',
      Number(partial.paidAmount) > 0 && Number(partial.paidAmount) < Number(partial.totalAmount),
      JSON.stringify(partial));
  }

  // Trang Tài chính đọc hóa đơn tổng (room_invoice); trạng thái tổng phải khớp hóa đơn chia.
  const sepPeriod = invoices.sepA?.[0];
  if (sepPeriod) {
    const masters = await api(adminToken, 'GET',
      `/api/v1/room-invoices?buildingId=${buildings.A}&month=${sepPeriod.month}&year=${sepPeriod.year}&size=100`);
    const a107Master = (masters.content ?? []).find((i) => String(i.roomId) === roomId('A107'));
    check('room-invoice A107 tháng trước-2 (Finance UI)', a107Master?.status, 'OVERDUE');
  }
  const octPeriod = invoices.octB?.[0];
  if (octPeriod) {
    const masters = await api(adminToken, 'GET',
      `/api/v1/room-invoices?buildingId=${buildings.B}&month=${octPeriod.month}&year=${octPeriod.year}&size=100`);
    const b203Master = (masters.content ?? []).find((i) => String(i.roomId) === roomId('B203'));
    check('room-invoice B203 tháng trước (Finance UI)', b203Master?.status, 'PAID');
  }

  const closing = flow7.periodic?.closingMonth; // YYYY-MM
  if (closing) {
    const [year, month] = closing.split('-').map(Number);
    const prevMonthInvoices = await api(adminToken, 'GET',
      `/api/v1/invoices?buildingId=${buildings.A}&month=${month}&year=${year}&size=100`);
    const a107 = (prevMonthInvoices.content ?? []).filter((i) => String(i.roomId) === roomId('A107'));
    check('A107 kỳ trước không có hóa đơn (thiếu chỉ số)', a107.length, 0);
  }

  // 8. Điện nước — đúng 2 đồng hồ/phòng, mốc bàn giao A108, chỉ số đủ/thiếu
  const meterState = {};
  for (const key of ['A104', 'A105', 'A107', 'A108', 'B201', 'B203']) {
    const list = await api(adminToken, 'GET', `/api/v1/meters?roomId=${roomId(key)}`);
    meterState[key] = (list ?? []).map((m) => ({ code: m.meterCode, initialIndex: Number(m.initialIndex) }));
    check(`phòng ${key} có đúng 2 đồng hồ`, (list ?? []).length, 2);
    checkTrue(`phòng ${key} không còn mã AUTO-*`,
      !(list ?? []).some((m) => /^AUTO-/.test(m.meterCode ?? '')),
      JSON.stringify((list ?? []).map((m) => m.meterCode)));
  }
  const a108Elec = (meterState.A108 ?? []).find((m) => m.code === 'SEED-A108-E');
  const a108Water = (meterState.A108 ?? []).find((m) => m.code === 'SEED-A108-W');
  check('A108 mốc bàn giao điện', a108Elec?.initialIndex, flow7.handover?.meterBaseline?.electricity);
  check('A108 mốc bàn giao nước', a108Water?.initialIndex, flow7.handover?.meterBaseline?.water);

  const readingCounts = {};
  for (const key of ['A104', 'A105', 'A107', 'A108', 'B201', 'B203']) {
    const page = await api(adminToken, 'GET', `/api/v1/meter-readings?roomId=${roomId(key)}&size=100`);
    readingCounts[key] = (page.content ?? []).length;
  }
  check('A108 chưa có chỉ số tháng nào', readingCounts.A108, 0);
  for (const room of flow7.periodic?.rooms ?? []) {
    const page = await api(adminToken, 'GET',
      `/api/v1/meter-readings?roomId=${roomId(room)}&month=${Number(closing.split('-')[1])}&year=${Number(closing.split('-')[0])}&size=100`);
    const count = (page.content ?? []).length;
    if ((flow7.periodic?.missingRooms ?? []).includes(room)) {
      check(`phòng ${room} thiếu chỉ số kỳ ${closing}`, count, 0);
    } else if ((flow7.periodic?.completedRooms ?? []).includes(room)) {
      check(`phòng ${room} đủ chỉ số kỳ ${closing}`, count, 2);
    }
  }

  // 9. Bảo trì
  const reports = {};
  for (const [key, id] of Object.entries(report.maintenance?.reports ?? {})) {
    reports[key] = (await api(adminToken, 'GET', `/api/v1/reports/${id}`)).status;
  }
  const reportExpected = {
    'r1-A104': 'PENDING', 'r2-A105': 'REJECTED', 'r3-A104': 'APPROVED', 'r4-A107': 'APPROVED',
    'r5-B201': 'APPROVED', 'r6-A104': 'PENDING', 'r7-B203': 'PENDING',
  };
  for (const [key, expected] of Object.entries(reportExpected)) check(`report ${key}`, reports[key], expected);

  const maintRequests = {};
  for (const [key, id] of Object.entries(report.maintenance?.requests ?? {})) {
    maintRequests[key] = (await api(adminToken, 'GET', `/api/v1/maintenance-requests/${id}`)).status;
  }
  check('Yêu cầu từ báo cáo r3 ASSIGNED', maintRequests['r3-A104'], 'ASSIGNED');
  check('Yêu cầu từ báo cáo r4 IN_PROGRESS', maintRequests['r4-A107'], 'IN_PROGRESS');
  check('Yêu cầu từ báo cáo r5 COMPLETED', maintRequests['r5-B201'], 'COMPLETED');

  const schedules = {};
  for (const b of ['A', 'B']) {
    const list = await api(adminToken, 'GET', `/api/v1/maintenance-schedules?buildingId=${buildings[b]}`);
    for (const s of list) schedules[s.title] = { active: s.active, nextDate: s.nextDate ?? null };
  }
  check('lịch PCCC active', schedules['Kiểm tra PCCC định kỳ (seed)']?.active, true);
  check('lịch thang máy active + đã dời ngày', schedules['Bảo trì thang máy (seed)']?.active, true);
  checkTrue('lịch thang máy có ngày kế tiếp trong tương lai',
    (schedules['Bảo trì thang máy (seed)']?.nextDate ?? '') > new Date().toISOString().slice(0, 10),
    schedules['Bảo trì thang máy (seed)']?.nextDate);
  check('lịch mái đã ngừng (không còn trong danh sách)', schedules['Kiểm tra mái và chống thấm (seed)'], undefined);

  // 10. Thông báo kỹ thuật — không trùng bàn giao A108
  const notes = await api(tokens.techA, 'GET', '/api/v1/notifications?page=0&size=100&unreadOnly=false');
  const handoverNotes = (notes.content ?? []).filter((n) => String(n.title ?? '').includes('bàn giao'));
  check('techA có đúng 1 thông báo bàn giao A108', handoverNotes.length, 1);
  checkTrue('techA có nhắc nhập chỉ số cuối tháng',
    (notes.content ?? []).some((n) => String(n.title ?? '').includes('Nhắc nhập chỉ số')));

  const output = {
    generatedAt: new Date().toISOString(),
    seedGeneratedAt: report.generatedAt,
    accounts: accountReport,
    rooms: roomStatus,
    listings: listingStatus,
    contracts: contractInfo,
    moveOuts,
    posts,
    applications: appActual,
    invoices: invoiceDetails,
    meterState,
    readingCounts,
    reports,
    maintenanceRequests: maintRequests,
    schedules,
    totalChecks: checks.length,
    failedChecks: failures,
  };
  fs.writeFileSync(path.join(__dirname, 'verify-report.json'), JSON.stringify(output, null, 2), 'utf8');

  console.log(`Đã chạy ${checks.length} kiểm tra, thất bại ${failures.length}`);
  for (const f of failures) {
    console.log('FAIL:', f.name, '| actual =', JSON.stringify(f.actual), '| expected =', JSON.stringify(f.expected));
  }
  const badLogins = Object.entries(accountReport).filter(([, v]) => !v.ok);
  console.log(`Đăng nhập: ${Object.keys(accountReport).length - badLogins.length}/${Object.keys(accountReport).length} tài khoản OK`);
  console.log(`Chi tiết: ${path.join(__dirname, 'verify-report.json')}`);
  if (failures.length > 0) process.exitCode = 1;
}

main().catch((e) => {
  console.error('VERIFY LỖI:', e.stack ?? e.message);
  process.exitCode = 1;
});
