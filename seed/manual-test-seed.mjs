#!/usr/bin/env node
/**
 * Seed data cho kiểm thử tay toàn hệ thống (mọi role, mọi luồng).
 *
 * Chạy: node seed/manual-test-seed.mjs
 * Base URL: http://localhost:8080 (override bằng SEED_BASE_URL).
 *
 * Script idempotent một phần: tiến trình đã tạo được ghi vào seed/state.json và
 * lần chạy sau sẽ dùng lại; các thực thể khác được dò lại qua API trước khi tạo.
 *
 * Mật khẩu chung cho toàn bộ tài khoản seed: Test@12345
 */
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const BASE = process.env.SEED_BASE_URL ?? 'http://localhost:8080';
const PASSWORD = 'Test@12345';
const STATE_PATH = path.join(__dirname, 'state.json');
const REPORT_PATH = path.join(__dirname, 'seed-report.json');
const BOOTSTRAP = { username: 'e2e.admin@example.com', password: 'E2ePass123!' };

const state = fs.existsSync(STATE_PATH) ? JSON.parse(fs.readFileSync(STATE_PATH, 'utf8')) : {};
const sessions = new Map();

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const todayIso = () => new Date().toISOString().slice(0, 10);
const DAY_MS = 24 * 60 * 60 * 1000;
// Mọi mốc thời gian của seed đều tính tương đối so với hôm nay để bộ dữ liệu
// vẫn hợp lệ (lịch hẹn tương lai, kỳ hóa đơn đã qua, cửa sổ gia hạn...) dù chạy bất kỳ ngày nào.
const isoDayFromNow = (days) => new Date(Date.now() + days * DAY_MS).toISOString().slice(0, 10);
const isoAtFromNow = (days, hourUtc = 2) => {
  const d = new Date(Date.now() + days * DAY_MS);
  d.setUTCHours(hourUtc, 0, 0, 0);
  return d.toISOString();
};
const isoDayMonthsFromNow = (months) => {
  const now = new Date();
  const day = now.getUTCDate();
  const target = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + months, 1));
  const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  target.setUTCDate(Math.min(day, lastDay));
  return target.toISOString().slice(0, 10);
};
const monthOffset = (offset) => {
  const now = new Date();
  const d = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + offset, 1));
  return { month: d.getUTCMonth() + 1, year: d.getUTCFullYear() };
};
const monthTag = ({ month, year }) => `${year}-${String(month).padStart(2, '0')}`;
const monthDay = ({ month, year }, day) =>
  `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
const monthEndDay = (m) => monthDay(m, new Date(Date.UTC(m.year, m.month, 0)).getUTCDate());
const BILLING_PREV = monthOffset(-1);
const BILLING_PREV2 = monthOffset(-2);
const CURRENT_MONTH = monthOffset(0);
const s = (v) => (v === undefined || v === null ? v : String(v));
const same = (a, b) => s(a) === s(b);

function saveState() {
  fs.writeFileSync(STATE_PATH, JSON.stringify(state, null, 2), 'utf8');
}
function setNs(ns, key, value) {
  state[ns] ??= {};
  state[ns][key] = value;
  saveState();
  return value;
}
function ns(nsName) {
  state[nsName] ??= {};
  return state[nsName];
}

function log(step, message) {
  console.log(`[${new Date().toISOString().slice(11, 19)}] ${step.padEnd(8)} ${message}`);
}

// JSON của các service trả ID Snowflake dạng số; Number của JS mất chính xác.
// Quote mọi số nguyên >= 16 chữ số trước khi JSON.parse (ID hiện tại 18 chữ số).
function parseJson(text) {
  if (!text) return null;
  try {
    const safe = text.replace(/([:,\[]\s*)(-?\d{16,})(?=\s*[,\]}])/g, '$1"$2"');
    return JSON.parse(safe);
  } catch {
    return text;
  }
}

class ApiError extends Error {
  constructor(key, method, pathname, res) {
    const detail = typeof res.data === 'object' ? JSON.stringify(res.data) : String(res.text).slice(0, 800);
    super(`${method} ${pathname} -> ${res.status} (as ${key}): ${detail}`);
    this.status = res.status;
    this.data = res.data;
  }
}

async function request(method, pathname, { token, body, retries = 24 } = {}) {
  let lastError;
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      const headers = { Accept: 'application/json' };
      if (token) headers.Authorization = `Bearer ${token}`;
      if (body !== undefined) headers['Content-Type'] = 'application/json; charset=utf-8';
      const res = await fetch(BASE + pathname, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: AbortSignal.timeout(30000),
      });
      const text = await res.text();
      // Eureka load balancer trả 503 vài chục giây đầu sau khi service vừa restart.
      if ([502, 503, 504].includes(res.status) && attempt < retries) {
        await sleep(1000);
        continue;
      }
      return { status: res.status, data: parseJson(text), text };
    } catch (e) {
      lastError = e;
      if (attempt < 3) await sleep(1000);
      else throw e;
    }
  }
  throw lastError;
}

async function getToken(key) {
  const cached = sessions.get(key);
  if (cached && Date.now() - cached.at < 12 * 60 * 1000) return cached.token;
  const cred = key === '__bootstrap' ? BOOTSTRAP : ns('accounts')[key];
  if (!cred) throw new Error(`Không có tài khoản cho khóa ${key}`);
  const res = await request('POST', '/api/v1/auth/login', {
    body: { username: cred.username, password: cred.password ?? PASSWORD },
  });
  if (res.status !== 200) {
    throw new Error(`Đăng nhập thất bại cho ${cred.username}: ${res.status} ${res.text.slice(0, 300)}`);
  }
  sessions.set(key, { token: res.data.accessToken, at: Date.now() });
  return res.data.accessToken;
}

async function call(key, method, pathname, body, expect = [200]) {
  for (let attempt = 0; attempt < 2; attempt++) {
    const token = await getToken(key);
    const res = await request(method, pathname, { token, body });
    if (res.status === 401) {
      sessions.delete(key);
      continue;
    }
    if (!expect.includes(res.status)) throw new ApiError(key, method, pathname, res);
    return res.data;
  }
  throw new Error(`Xác thực thất bại sau 2 lần cho ${key}`);
}

async function poll(label, fn, { timeout = 60000, interval = 1000 } = {}) {
  const start = Date.now();
  let last;
  while (Date.now() - start < timeout) {
    try {
      const value = await fn();
      if (value) return value;
    } catch (e) {
      last = e;
    }
    await sleep(interval);
  }
  throw new Error(`Hết thời gian chờ ${label}${last ? `: ${last.message}` : ''}`);
}

const acc = (key) => ns('accounts')[key];
const accId = (key) => s(acc(key).id);
const bid = (key) => s(ns('buildings')[key]);
const rid = (key) => s(ns('rooms')[key]);
const lid = (key) => s(ns('listings')[key]);

// Thông tin định danh dùng chung cho hồ sơ thuê / CCCD / hợp đồng.
// Backend bắt buộc các trường này khi gửi yêu cầu thuê, ứng tuyển ở ghép và tạo nháp hợp đồng.
const IDENTITY = {
  active: { dob: '1995-03-12', gender: 'Nam', cccd: '079095001234', issueDate: '2021-07-15', issuePlace: 'Cục Cảnh sát QLHC về TTXH', address: '45 Nguyễn Trãi, Phường 3, Quận 5, TP. Hồ Chí Minh', occupation: 'Nhân viên văn phòng' },
  renew: { dob: '1993-08-21', gender: 'Nữ', cccd: '079093002345', issueDate: '2020-11-02', issuePlace: 'Cục Cảnh sát QLHC về TTXH', address: '102 Lê Văn Sỹ, Phường 13, Quận 3, TP. Hồ Chí Minh', occupation: 'Kế toán' },
  terminate: { dob: '1990-01-09', gender: 'Nam', cccd: '079090003456', issueDate: '2019-05-20', issuePlace: 'Cục Cảnh sát QLHC về TTXH', address: '77 Cách Mạng Tháng 8, Phường 5, Quận 3, TP. Hồ Chí Minh', occupation: 'Kỹ sư xây dựng' },
  moveout: { dob: '1997-06-30', gender: 'Nữ', cccd: '079097004567', issueDate: '2022-02-14', issuePlace: 'Cục Cảnh sát QLHC về TTXH', address: '9 Phan Xích Long, Phường 2, Quận Phú Nhuận, TP. Hồ Chí Minh', occupation: 'Nhân viên kinh doanh' },
  mate: { dob: '1996-12-05', gender: 'Nam', cccd: '079096005678', issueDate: '2021-09-09', issuePlace: 'Cục Cảnh sát QLHC về TTXH', address: '31 Hoàng Diệu, Phường 6, Quận 4, TP. Hồ Chí Minh', occupation: 'Lập trình viên' },
  old: { dob: '1988-04-17', gender: 'Nữ', cccd: '079088006789', issueDate: '2018-12-01', issuePlace: 'Cục Cảnh sát QLHC về TTXH', address: '210 Điện Biên Phủ, Phường 7, Quận 3, TP. Hồ Chí Minh', occupation: 'Giáo viên' },
  draft: { dob: '1999-09-27', gender: 'Nam', cccd: '079099007890', issueDate: '2023-03-18', issuePlace: 'Cục Cảnh sát QLHC về TTXH', address: '56 Trần Hưng Đạo, Phường 2, Quận 1, TP. Hồ Chí Minh', occupation: 'Sinh viên' },
  accept: { dob: '1994-11-11', gender: 'Nữ', cccd: '079094008901', issueDate: '2021-01-25', issuePlace: 'Cục Cảnh sát QLHC về TTXH', address: '88 Nguyễn Thị Minh Khai, Phường 6, Quận 3, TP. Hồ Chí Minh', occupation: 'Chuyên viên nhân sự' },
  pending: { dob: '2000-02-08', gender: 'Nam', cccd: '079000009012', issueDate: '2023-08-30', issuePlace: 'Cục Cảnh sát QLHC về TTXH', address: '15 Võ Văn Tần, Phường 6, Quận 3, TP. Hồ Chí Minh', occupation: 'Nhân viên marketing' },
  reject: { dob: '1998-07-19', gender: 'Nữ', cccd: '079098010123', issueDate: '2022-06-06', issuePlace: 'Cục Cảnh sát QLHC về TTXH', address: '3 Nguyễn Bỉnh Khiêm, Phường Đa Kao, Quận 1, TP. Hồ Chí Minh', occupation: 'Thiết kế đồ họa' },
  appt: { dob: '1992-05-23', gender: 'Nam', cccd: '079092011234', issueDate: '2020-10-12', issuePlace: 'Cục Cảnh sát QLHC về TTXH', address: '47 Pasteur, Phường 8, Quận 3, TP. Hồ Chí Minh', occupation: 'Bác sĩ' },
  compete: { dob: '1991-10-01', gender: 'Nữ', cccd: '079091012345', issueDate: '2019-04-04', issuePlace: 'Cục Cảnh sát QLHC về TTXH', address: '66 Hai Bà Trưng, Phường Tân Định, Quận 1, TP. Hồ Chí Minh', occupation: 'Luật sư' },
  applicant: { dob: '1996-03-14', gender: 'Nam', cccd: '079096013456', issueDate: '2021-12-20', issuePlace: 'Cục Cảnh sát QLHC về TTXH', address: '29 Nguyễn Đình Chiểu, Phường 5, Quận 3, TP. Hồ Chí Minh', occupation: 'Nhân viên IT' },
  applicant2: { dob: '1997-08-02', gender: 'Nữ', cccd: '079097014567', issueDate: '2022-07-07', issuePlace: 'Cục Cảnh sát QLHC về TTXH', address: '91 Lý Tự Trọng, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh', occupation: 'Biên tập viên' },
  applicant3: { dob: '1995-01-31', gender: 'Nam', cccd: '079095015678', issueDate: '2020-09-15', issuePlace: 'Cục Cảnh sát QLHC về TTXH', address: '12 Nguyễn Du, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh', occupation: 'Nhiếp ảnh gia' },
  applicant4: { dob: '1998-12-24', gender: 'Nữ', cccd: '079098016789', issueDate: '2023-02-11', issuePlace: 'Cục Cảnh sát QLHC về TTXH', address: '35 Tôn Đức Thắng, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh', occupation: 'Chuyên viên tư vấn' },
  // Khách mới chuyển vào đầu tháng hiện tại — dùng riêng cho kịch bản 7A (chốt số bàn giao).
  handover: { dob: '1996-09-18', gender: 'Nam', cccd: '079096017890', issueDate: '2022-04-22', issuePlace: 'Cục Cảnh sát QLHC về TTXH', address: '24 Ung Văn Khiêm, Phường 25, Quận Bình Thạnh, TP. Hồ Chí Minh', occupation: 'Nhân viên logistics' },
};

const LESSORS = {
  A: {
    name: 'Ông Nguyễn Văn An',
    phone: '0903123456',
    idNumber: '079080001234',
    dob: '1980-05-15',
    issueDate: '2021-06-10',
    issuePlace: 'Cục Cảnh sát QLHC về TTXH',
    address: '12 Đường Seed A, Phường 25, Quận Bình Thạnh, TP. Hồ Chí Minh',
  },
  B: {
    name: 'Ông Trần Đức Bình',
    phone: '0912345678',
    idNumber: '079085005678',
    dob: '1985-08-20',
    issueDate: '2022-03-12',
    issuePlace: 'Cục Cảnh sát QLHC về TTXH',
    address: '18 Đường Seed B, Phường 25, Quận Bình Thạnh, TP. Hồ Chí Minh',
  },
};

const identityOf = (key) => IDENTITY[key] ?? {
  dob: '1995-01-01',
  gender: 'Nam',
  cccd: `079${String(acc(key)?.phone ?? '0900000000').slice(-9)}`.slice(0, 12),
  issueDate: '2021-01-01',
  issuePlace: 'Cục Cảnh sát QLHC về TTXH',
  address: 'TP. Hồ Chí Minh',
  occupation: 'Nhân viên',
};

// ---------------------------------------------------------------- accounts

async function ensureAccount(key, def) {
  const existing = ns('accounts')[key];
  if (existing?.id) return existing;

  const loginRes = await request('POST', '/api/v1/auth/login', {
    body: { username: def.username, password: PASSWORD },
  });
  if (loginRes.status === 200) {
    const record = {
      id: s(loginRes.data.account.id),
      username: def.username,
      fullName: def.fullName,
      email: def.email,
      phone: def.phone,
      accountType: def.accountType,
      roles: def.roles ?? [],
    };
    log('account', `đã tồn tại: ${def.username} (id=${record.id})`);
    return setNs('accounts', key, record);
  }

  let id;
  if (def.accountType === 'CUSTOMER') {
    const res = await request('POST', '/api/v1/auth/register', {
      body: {
        username: def.username,
        password: PASSWORD,
        fullName: def.fullName,
        email: def.email,
        phone: def.phone,
      },
    });
    if (res.status === 201) {
      id = s(res.data.accountId);
    } else {
      const retry = await request('POST', '/api/v1/auth/login', {
        body: { username: def.username, password: PASSWORD },
      });
      if (retry.status === 200) id = s(retry.data.account.id);
      else throw new ApiError(key, 'POST', '/api/v1/auth/register', res);
    }
  } else {
    const res = await call(
      '__bootstrap',
      'POST',
      '/api/v1/auth/accounts',
      {
        username: def.username,
        password: PASSWORD,
        fullName: def.fullName,
        email: def.email,
        phone: def.phone,
        accountType: def.accountType,
        roles: def.roles,
      },
      [201],
    );
    id = s(res.accountId ?? res.id);
  }

  const record = {
    id,
    username: def.username,
    fullName: def.fullName,
    email: def.email,
    phone: def.phone,
    accountType: def.accountType,
    roles: def.roles ?? [],
  };
  log('account', `tạo ${def.accountType} ${def.username} (id=${id})`);
  return setNs('accounts', key, record);
}

// Ảnh CCCD seed: PNG 1x1 hợp lệ, đủ để upload và mở lại qua endpoint tải hồ sơ.
const CCCD_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
);

async function ensureIdentityFiles(key) {
  const cached = ns('identity')[key];
  if (cached?.front && cached?.back) return cached;
  const token = await getToken(key);
  const upload = async (side) => {
    const form = new FormData();
    form.append('file', new Blob([CCCD_PNG], { type: 'image/png' }), `cccd-${side.toLowerCase()}.png`);
    const res = await fetch(`${BASE}/api/v1/rental-requests/identity-files?side=${side}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: form,
      signal: AbortSignal.timeout(30000),
    });
    const text = await res.text();
    if (res.status !== 201) {
      throw new Error(`Upload CCCD ${side} cho ${key} -> ${res.status}: ${text.slice(0, 300)}`);
    }
    return parseJson(text).objectKey;
  };
  const front = await upload('FRONT');
  const back = await upload('BACK');
  return setNs('identity', key, { front, back });
}

async function waitUserProfile(id, accountType, timeout = 60000) {
  const token = await getToken('__bootstrap');
  if (accountType === 'CUSTOMER') {
    return poll(`profile khách ${id}`, async () => {
      const res = await request('GET', `/api/v1/users/${id}`, { token });
      return res.status === 200 ? res.data : null;
    }, { timeout });
  }
  // STAFF/ADMIN: /users/{id} chỉ tra customer, phải dò trong danh sách nhân sự.
  return poll(`profile nhân sự ${id}`, async () => {
    for (let page = 0; page < 5; page++) {
      const res = await request('GET', `/api/v1/staff?page=${page}&size=100`, { token });
      if (res.status !== 200) return null;
      const found = (res.data.content ?? []).find((u) => same(u.id, id));
      if (found) return found;
      if ((res.data.content ?? []).length < 100) break;
    }
    return null;
  }, { timeout });
}

async function assignBuildings(staffKey, buildingIds) {
  const id = accId(staffKey);
  await waitUserProfile(id, 'STAFF');
  await poll(`staff ${id} profile`, async () => {
    const res = await request('GET', `/api/v1/staff/${id}/buildings`, {
      token: await getToken('__bootstrap'),
    });
    return res.status === 200 ? res.data : null;
  });
  await call('__bootstrap', 'PUT', `/api/v1/staff/${id}/buildings`, { buildingIds }, [204]);
  log('staff', `gán tòa nhà ${buildingIds.join(',')} cho ${acc(staffKey).username}`);
}

// ---------------------------------------------------------------- properties

async function ensureRoomType(key, typeCode, name, description) {
  if (ns('roomTypes')[key]) return s(ns('roomTypes')[key]);
  const page = await call('__bootstrap', 'GET', '/api/v1/room-types?size=100');
  const found = (page.content ?? []).find((t) => t.typeCode === typeCode);
  if (found) return setNs('roomTypes', key, s(found.id));
  const created = await call('__bootstrap', 'POST', '/api/v1/room-types', { typeCode, name, description }, [201]);
  log('property', `tạo loại phòng ${typeCode} (id=${created.id})`);
  return setNs('roomTypes', key, s(created.id));
}

async function ensureBuilding(key, def) {
  if (ns('buildings')[key]) return s(ns('buildings')[key]);
  const found = await call(
    '__bootstrap',
    'GET',
    `/api/v1/buildings/search?keyword=${encodeURIComponent(def.name)}&size=100`,
  );
  const match = (found.content ?? []).find((b) => b.name === def.name);
  if (match) {
    log('property', `đã có tòa nhà ${def.name} (id=${match.buildingId})`);
    return setNs('buildings', key, s(match.buildingId));
  }
  const created = await call('__bootstrap', 'POST', '/api/v1/buildings', def.body, [201]);
  log('property', `tạo tòa nhà ${def.name} (id=${created.buildingId})`);
  return setNs('buildings', key, s(created.buildingId));
}

/**
 * Thêm phòng khi tòa chưa có khách làm building tự chuyển INACTIVE (BuildingService.refreshOccupancyStatus).
 * Sau khi seed xong hợp đồng ACTIVE, đưa tòa về ACTIVE để dashboard/báo cáo phản ánh đúng.
 */
async function reactivateBuilding(buildingId) {
  const current = await call('__bootstrap', 'GET', `/api/v1/buildings/${buildingId}`);
  if (current.buildingStatus === 'ACTIVE') return;
  await call('__bootstrap', 'PUT', `/api/v1/buildings/${buildingId}`, {
    name: current.name,
    address: current.address,
    wardId: current.wardId,
    provinceId: current.provinceId,
    latitude: current.latitude,
    longitude: current.longitude,
    description: current.description,
    buildingStatus: 'ACTIVE',
    lessorName: current.lessorName,
    lessorDateOfBirth: current.lessorDateOfBirth,
    lessorIdNumber: current.lessorIdNumber,
    lessorIdIssueDate: current.lessorIdIssueDate,
    lessorIdIssuePlace: current.lessorIdIssuePlace,
    lessorPhone: current.lessorPhone,
    lessorAddress: current.lessorAddress,
  }, [200]);
  log('property', `kích hoạt lại tòa nhà ${current.name}`);
}

/** Danh mục tỉnh/phường được reset script nạp từ CSV; seed chỉ cần chọn 1 phường của TP.HCM. */
async function ensureDefaultLocation() {
  if (ns('locations').default) return ns('locations').default;
  const provinces = await call('__bootstrap', 'GET', '/api/v1/locations/provinces');
  const province = (provinces ?? []).find((p) => p.code === '79') ?? (provinces ?? [])[0];
  if (!province) throw new Error('Chưa có dữ liệu tỉnh/thành. Hãy chạy seed/reset-and-seed.ps1 để nạp danh mục.');
  const wards = await call('__bootstrap', 'GET', `/api/v1/locations/provinces/${province.provinceId}/wards`);
  const ward = (wards ?? [])[0];
  if (!ward) throw new Error(`Tỉnh ${province.name} chưa có phường/xã.`);
  const location = { provinceId: province.provinceId, wardId: ward.wardId };
  log('property', `dùng địa chỉ seed: ${ward.name}, ${province.name}`);
  return setNs('locations', 'default', location);
}

async function waitListing(roomId) {
  return poll(`listing phòng ${roomId}`, async () => {
    const res = await request('GET', `/api/v1/listings/room/${roomId}`, {
      token: await getToken('__bootstrap'),
    });
    return res.status === 200 ? res.data : null;
  });
}

async function ensureRoom(code, buildingKey, spec) {
  if (ns('rooms')[code]) {
    const listing = await waitListing(rid(code));
    setNs('listings', code, s(listing.id));
    return { roomId: rid(code), listingId: s(listing.id) };
  }
  const buildingId = bid(buildingKey);
  const page = await call('__bootstrap', 'GET', `/api/v1/rooms/building/${buildingId}?size=100`);
  let room = (page.content ?? []).find((r) => r.roomCode === code);
  if (!room) {
    room = await call('__bootstrap', 'POST', '/api/v1/rooms', {
      buildingId,
      roomTypeId: spec.roomTypeId,
      roomCode: code,
      maxCapacity: spec.maxCapacity,
      area: spec.area,
      bedroomCount: spec.bedroomCount ?? 1,
      bathroomCount: spec.bathroomCount ?? 1,
      basePrice: spec.basePrice,
      status: 'VACANT',
    }, [201]);
    log('property', `tạo phòng ${code} (id=${room.id}, giá ${spec.basePrice})`);
  } else {
    log('property', `đã có phòng ${code} (id=${room.id})`);
  }
  setNs('rooms', code, s(room.id));
  const listing = await waitListing(room.id);
  setNs('listings', code, s(listing.id));
  log('listing', `phòng ${code} có tin đăng ${listing.id} trạng thái ${listing.status}`);
  return { roomId: s(room.id), listingId: s(listing.id) };
}

async function setRoomStatus(roomId, status) {
  const room = await call('__bootstrap', 'GET', `/api/v1/rooms/${roomId}`);
  if (room.status === status) return;
  await call('__bootstrap', 'PUT', `/api/v1/rooms/${roomId}/status`, { status });
  log('property', `đổi trạng thái phòng ${room.roomCode} -> ${status}`);
}

/** Tin đăng phải có đủ điều khoản thì khách mới gửi được yêu cầu thuê. */
async function ensureListingTerms(code, managerKey, spec, availableFrom) {
  const listing = await waitListing(rid(code));
  if (listing.depositAmount != null && listing.electricityPrice != null) return listing;
  const updated = await call(managerKey, 'PUT', `/api/v1/listings/${listing.id}`, {
    title: listing.title,
    description: listing.description ?? undefined,
    depositAmount: spec.basePrice,
    electricityPrice: 3500,
    waterPrice: 12000,
    waterUnit: 'PERSON',
    paymentMethod: 'BANK_TRANSFER',
    paymentDueDay: 5,
    minTermMonths: 12,
    availableFrom,
  });
  log('listing', `bổ sung điều khoản thuê cho ${code} (cọc ${spec.basePrice})`);
  return updated;
}

// ---------------------------------------------------------------- booking

async function ensureRequest(key, customerKey, roomKey, { moveInDate, note }) {
  if (ns('requests')[key]) return s(ns('requests')[key]);
  const listingId = lid(roomKey);
  const customerId = accId(customerKey);
  const page = await call(customerKey, 'GET', '/api/v1/rental-requests/mine?size=100');
  let found = (page.content ?? []).find((r) => same(r.listingId, listingId));
  if (!found) {
    const identity = identityOf(customerKey);
    const files = await ensureIdentityFiles(customerKey);
    const customer = acc(customerKey);
    found = await call(customerKey, 'POST', '/api/v1/rental-requests', {
      listingId,
      fullName: customer.fullName,
      dateOfBirth: identity.dob,
      gender: identity.gender,
      phone: customer.phone,
      email: customer.email,
      permanentAddress: identity.address,
      occupation: identity.occupation,
      cccdNumber: identity.cccd,
      cccdIssueDate: identity.issueDate,
      cccdIssuePlace: identity.issuePlace,
      cccdFrontKey: files.front,
      cccdBackKey: files.back,
      moveInDate,
      note,
      termsAccepted: true,
    }, [201]);
    log('booking', `yêu cầu thuê ${roomKey} bởi ${acc(customerKey).username} (id=${found.id})`);
  } else {
    log('booking', `đã có yêu cầu thuê ${roomKey} bởi ${acc(customerKey).username} (id=${found.id})`);
  }
  if (!same(found.customerId, customerId)) throw new Error(`Yêu cầu ${key} không thuộc khách dự kiến`);
  return setNs('requests', key, s(found.id));
}

async function ensureAppointment(key, requestKey, staffKey, appointmentDate) {
  const request = await call('__bootstrap', 'GET', `/api/v1/rental-requests/${ns('requests')[requestKey]}`);
  const stored = ns('appointments')[key];
  if (stored) {
    const current = await call('__bootstrap', 'GET', `/api/v1/appointments/${stored}`);
    if (current.status !== 'CANCELLED') return s(stored);
    if (request.status === 'REJECTED' || request.status === 'CANCELLED') return s(stored);
    log('booking', `lịch hẹn ${stored} đã bị hủy, tạo lịch mới cho ${requestKey}`);
  }
  const buildingId = s(request.buildingId);
  const page = await call('__bootstrap', 'GET', `/api/v1/appointments?buildingId=${buildingId}&size=100`);
  let found = (page.content ?? []).find(
    (a) => same(a.rentalRequestId, ns('requests')[requestKey]) && a.status !== 'CANCELLED',
  );
  if (!found) {
    found = await call('__bootstrap', 'POST', '/api/v1/appointments', {
      rentalRequestId: ns('requests')[requestKey],
      staffId: accId(staffKey),
      appointmentDate,
    }, [201]);
    log('booking', `lịch hẹn cho yêu cầu ${requestKey} (id=${found.id})`);
  }
  return setNs('appointments', key, s(found.id));
}

async function setAppointmentStatus(appointmentId, status) {
  try {
    const data = await call('__bootstrap', 'GET', `/api/v1/appointments/${appointmentId}`);
    if (data.status === status) return data;
    if (data.status === 'CANCELLED' && status !== 'CANCELLED') {
      log('booking', `lịch hẹn ${appointmentId} đang là CANCELLED, bỏ qua chuyển ${status}`);
      return data;
    }
    const updated = await call('__bootstrap', 'PUT', `/api/v1/appointments/${appointmentId}/status`, { status });
    log('booking', `lịch hẹn ${appointmentId}: ${data.status} -> ${status}`);
    return updated;
  } catch (err) {
    log('booking', `lịch hẹn ${appointmentId} không thể chuyển sang ${status}: ${err.message}`);
    return null;
  }
}

async function cancelAppointmentByCustomer(appointmentId, customerKey) {
  const data = await call('__bootstrap', 'GET', `/api/v1/appointments/${appointmentId}`);
  if (data.status === 'CANCELLED') return;
  await call(customerKey, 'PUT', `/api/v1/appointments/${appointmentId}/cancel`, undefined, [204]);
  log('booking', `khách hủy lịch hẹn ${appointmentId}`);
}

async function acceptRequest(requestKey, managerKey) {
  const requestId = ns('requests')[requestKey];
  const data = await call('__bootstrap', 'GET', `/api/v1/rental-requests/${requestId}`);
  if (data.status === 'ACCEPTED') return;
  if (data.status !== 'PENDING') {
    log('booking', `yêu cầu ${requestKey} (id=${requestId}) đang là ${data.status}, bỏ qua duyệt`);
    return;
  }
  if (!data.verifiedAt) {
    try {
      await call(managerKey, 'PUT', `/api/v1/rental-requests/${requestId}/verify`);
    } catch (e) {
      log('booking', `xác minh hồ sơ ${requestId} thất bại: ${e.message}`);
    }
  }
  await call(managerKey, 'PUT', `/api/v1/rental-requests/${requestId}/accept`);
  log('booking', `duyệt yêu cầu ${requestKey} (id=${requestId})`);
}

async function rejectRequest(requestKey, managerKey, reason) {
  const requestId = ns('requests')[requestKey];
  const data = await call('__bootstrap', 'GET', `/api/v1/rental-requests/${requestId}`);
  if (data.status === 'REJECTED') return;
  await call(managerKey, 'PUT', `/api/v1/rental-requests/${requestId}/reject`, { reason });
  log('booking', `từ chối yêu cầu ${requestKey}`);
}

// ---------------------------------------------------------------- contracts

async function findDraft(roomId, customerId) {
  const page = await call('__bootstrap', 'GET', `/api/v1/contracts?roomId=${roomId}&status=DRAFT&size=100`);
  return (page.content ?? []).find((c) => same(c.customerId, customerId)) ?? null;
}

async function ensureManualDraft(key, def) {
  if (ns('contracts')[key]) return s(ns('contracts')[key]);
  const roomId = rid(def.roomKey);
  const customerId = accId(def.customerKey);
  let found = await findDraft(roomId, customerId);
  if (!found) {
    const identity = identityOf(def.customerKey);
    const customer = acc(def.customerKey);
    const lessor = LESSORS[def.buildingKey];
    found = await call('__bootstrap', 'POST', '/api/v1/contracts/drafts', {
      customerId,
      roomId,
      buildingId: bid(def.buildingKey),
      startDate: def.startDate,
      endDate: def.endDate,
      rentAmount: def.rentAmount,
      depositAmount: def.depositAmount,
      electricityPrice: 3500,
      waterPrice: 12000,
      waterUnit: 'PERSON',
      paymentMethod: 'BANK_TRANSFER',
      paymentDueDay: 5,
      minTermMonths: 12,
      availableFrom: def.startDate,
      lessorName: lessor.name,
      lessorDateOfBirth: lessor.dob,
      lessorIdNumber: lessor.idNumber,
      lessorIdIssueDate: lessor.issueDate,
      lessorIdIssuePlace: lessor.issuePlace,
      lessorPhone: lessor.phone,
      lessorAddress: lessor.address,
      tenantFullName: customer.fullName,
      tenantDateOfBirth: identity.dob,
      tenantGender: identity.gender,
      tenantPhone: customer.phone,
      tenantEmail: customer.email,
      tenantPermanentAddress: identity.address,
      tenantOccupation: identity.occupation,
      tenantCccdNumber: identity.cccd,
      tenantCccdIssueDate: identity.issueDate,
      tenantCccdIssuePlace: identity.issuePlace,
    }, [201]);
    log('contract', `tạo nháp hợp đồng ${def.roomKey}/${acc(def.customerKey).username} (id=${found.id})`);
  } else {
    log('contract', `đã có nháp hợp đồng ${def.roomKey} (id=${found.id})`);
  }
  return setNs('contracts', key, s(found.id));
}

async function waitRequestDraft(roomKey, customerKey) {
  const roomId = rid(roomKey);
  const customerId = accId(customerKey);
  const draft = await poll(`nháp hợp đồng từ yêu cầu ${roomKey}`, () => findDraft(roomId, customerId));
  return s(draft.id);
}

async function updateDraft(contractId, body) {
  const updated = await call('__bootstrap', 'PUT', `/api/v1/contracts/${contractId}`, body);
  log('contract', `cập nhật nháp ${contractId}: ${body.startDate} -> ${body.endDate}`);
  return updated;
}

async function ensureSigned(contractId) {
  const contract = await call('__bootstrap', 'GET', `/api/v1/contracts/${contractId}`);
  if (contract.status === 'ACTIVE') return contract;
  if (contract.status !== 'DRAFT') throw new Error(`Hợp đồng ${contractId} ở trạng thái ${contract.status}`);
  const signed = await call('__bootstrap', 'POST', `/api/v1/contracts/${contractId}/sign`);
  log('contract', `ký hợp đồng ${contractId} -> ACTIVE`);
  return signed;
}

async function waitRoomStatus(roomKey, status) {
  return poll(`phòng ${roomKey} -> ${status}`, async () => {
    const room = await call('__bootstrap', 'GET', `/api/v1/rooms/${rid(roomKey)}`);
    return room.status === status ? room : null;
  });
}

async function addTenantIfMissing(contractId, customerKey, joinedAt) {
  const customerId = accId(customerKey);
  const contract = await call('__bootstrap', 'GET', `/api/v1/contracts/${contractId}`);
  const existing = (contract.tenants ?? []).find((t) => same(t.customerId, customerId) && !t.leftAt);
  if (existing) return s(existing.id);
  const created = await call('__bootstrap', 'POST', `/api/v1/contracts/${contractId}/tenants`, {
    customerId,
    joinedAt,
  }, [201]);
  log('contract', `thêm người ở ${acc(customerKey).username} vào hợp đồng ${contractId}`);
  return setNs('tenants', `${contractId}:${customerId}`, s(created.id));
}

async function ensureMoveOut(key, customerKey, contractId, expectedMoveOutDate, reason) {
  if (ns('moveOuts')[key]) return s(ns('moveOuts')[key]);
  const contract = await call('__bootstrap', 'GET', `/api/v1/contracts/${contractId}`);
  const page = await call(
    '__bootstrap',
    'GET',
    `/api/v1/move-out-requests?buildingId=${s(contract.buildingId)}&size=100`,
  );
  let found = (page.content ?? []).find((m) => same(m.contractId, contractId));
  if (!found) {
    found = await call(customerKey, 'POST', '/api/v1/move-out-requests', {
      contractId,
      expectedMoveOutDate,
      reason,
    }, [201]);
    log('contract', `yêu cầu trả phòng hợp đồng ${contractId} (id=${found.id})`);
  }
  return setNs('moveOuts', key, s(found.id));
}

async function approveMoveOut(key) {
  const id = ns('moveOuts')[key];
  const page = await call('__bootstrap', 'GET', '/api/v1/move-out-requests?size=100');
  const current = (page.content ?? []).find((m) => same(m.id, id));
  if (current?.status === 'APPROVED') return current;
  const updated = await call('__bootstrap', 'PUT', `/api/v1/move-out-requests/${id}/approve`);
  log('contract', `duyệt trả phòng ${id}`);
  return updated;
}

// ---------------------------------------------------------------- roommates

async function ensurePost(key, ownerKey, roomKey, def) {
  if (ns('posts')[key]) return s(ns('posts')[key]);
  const mine = await call(ownerKey, 'GET', '/api/v1/roommate-posts/my');
  let found = (mine ?? []).find((p) => p.title === def.title);
  if (!found) {
    found = await call(ownerKey, 'POST', '/api/v1/roommate-posts', {
      roomId: rid(roomKey),
      title: def.title,
      description: def.description,
      budgetShare: def.budgetShare,
    }, [201]);
    log('roommate', `tạo tin ở ghép "${def.title}" (id=${found.id})`);
  }
  return setNs('posts', key, s(found.id));
}

async function ensureApplication(key, postKey, applicantKey, message) {
  if (ns('applications')[key]) return s(ns('applications')[key]);
  const post = await call('__bootstrap', 'GET', `/api/v1/roommate-posts/${ns('posts')[postKey]}`);
  const ownerKey = post.customerId;
  const ownerEntry = Object.entries(ns('accounts')).find(([, a]) => same(a.id, ownerKey));
  if (!ownerEntry) throw new Error(`Không tìm thấy owner của tin ${postKey}`);
  const page = await call(ownerEntry[0], 'GET', `/api/v1/roommate-posts/${post.id}/applications?size=100`);
  let found = (page.content ?? []).find((a) => same(a.customerId, accId(applicantKey)));
  if (!found) {
    const identity = identityOf(applicantKey);
    const files = await ensureIdentityFiles(applicantKey);
    const customer = acc(applicantKey);
    found = await call(applicantKey, 'POST', `/api/v1/roommate-posts/${post.id}/apply`, {
      fullName: customer.fullName,
      phone: customer.phone,
      cccdNumber: identity.cccd,
      message,
      dateOfBirth: identity.dob,
      gender: identity.gender,
      cccdIssueDate: identity.issueDate,
      cccdIssuePlace: identity.issuePlace,
      permanentAddress: identity.address,
      occupation: identity.occupation,
      moveInDate: todayIso(),
      cccdFrontKey: files.front,
      cccdBackKey: files.back,
    }, [201]);
    log('roommate', `${acc(applicantKey).username} ứng tuyển tin ${postKey} (id=${found.id})`);
  }
  return setNs('applications', key, s(found.id));
}

async function applicationStatus(applicationId, applicantKey) {
  const my = await call(applicantKey, 'GET', `/api/v1/roommate-applications/my`);
  const found = (my ?? []).find((a) => same(a.id, applicationId));
  return found?.status ?? null;
}

async function actApplication(key, ownerKey, applicantKey, action, expectedStatus) {
  const applicationId = ns('applications')[key];
  const current = await applicationStatus(applicationId, applicantKey);
  if (current === expectedStatus) return;
  if (current !== 'PENDING') throw new Error(`Đơn ${applicationId} đang ${current}, không thể ${action}`);
  await call(ownerKey, 'PUT', `/api/v1/roommate-applications/${applicationId}/${action}`);
  log('roommate', `${action} đơn ${applicationId}`);
}

// ---------------------------------------------------------------- billing

async function ensureUtilityType(key, name, unit, unitPrice) {
  if (ns('utilityTypes')[key]) return s(ns('utilityTypes')[key]);
  const list = await call('__bootstrap', 'GET', '/api/v1/utility-types');
  const found = (list ?? []).find((t) => t.name === name);
  if (found) return setNs('utilityTypes', key, s(found.id));
  const created = await call('__bootstrap', 'POST', '/api/v1/utility-types', { name, unit, unitPrice }, [201]);
  log('billing', `tạo loại tiện ích ${name}`);
  return setNs('utilityTypes', key, s(created.id));
}

async function ensureBuildingService(key, buildingKey, name, unitPrice) {
  if (ns('services')[key]) return s(ns('services')[key]);
  const list = await call('__bootstrap', 'GET', `/api/v1/services?buildingId=${bid(buildingKey)}`);
  const found = (list ?? []).find((v) => v.name === name);
  if (found) return setNs('services', key, s(found.id));
  const created = await call('__bootstrap', 'POST', '/api/v1/services', {
    buildingId: bid(buildingKey),
    name,
    unit: 'month',
    unitPrice,
  }, [201]);
  log('billing', `tạo dịch vụ ${name} cho tòa ${buildingKey}`);
  return setNs('services', key, s(created.id));
}

async function attachService(key, contractId, serviceId, usageType, contractTenantId = null) {
  if (ns('contractServices')[key]) return s(ns('contractServices')[key]);
  const list = await call('__bootstrap', 'GET', `/api/v1/contract-services?contractId=${contractId}`);
  const found = (list ?? []).find(
    (cs) => same(cs.serviceId, serviceId) && cs.usageType === usageType
      && (usageType === 'SHARED' || same(cs.contractTenantId, contractTenantId)),
  );
  if (found) return setNs('contractServices', key, s(found.id));
  const created = await call('__bootstrap', 'POST', '/api/v1/contract-services', {
    contractId,
    serviceId,
    quantity: 1,
    usageType,
    contractTenantId,
  }, [201]);
  log('billing', `gắn dịch vụ ${key} vào hợp đồng ${contractId} (${usageType})`);
  return setNs('contractServices', key, s(created.id));
}

async function ensureMeter(key, roomKey, utilityTypeKey, meterCode, initialIndex) {
  if (ns('meters')[key]) return s(ns('meters')[key]);
  const list = await call('__bootstrap', 'GET', `/api/v1/meters?roomId=${rid(roomKey)}`);
  const found = (list ?? []).find((m) => same(m.utilityTypeId, ns('utilityTypes')[utilityTypeKey]));
  if (found) return setNs('meters', key, s(found.id));
  const created = await call('__bootstrap', 'POST', '/api/v1/meters', {
    roomId: rid(roomKey),
    utilityTypeId: ns('utilityTypes')[utilityTypeKey],
    meterCode,
    initialIndex,
  }, [201]);
  log('billing', `tạo đồng hồ ${meterCode} phòng ${roomKey}`);
  return setNs('meters', key, s(created.id));
}

async function ensureReading(key, meterId, month, year, currentIndex, readingDate) {
  if (ns('readings')[key]) return s(ns('readings')[key]);
  const res = await request('POST', '/api/v1/meter-readings', {
    token: await getToken('__bootstrap'),
    body: { meterId, month, year, currentIndex, readingDate },
  });
  if (res.status === 201) {
    log('billing', `nhập chỉ số ${key}=${currentIndex}`);
    return setNs('readings', key, s(res.data.id));
  }
  if (res.status === 409 || res.status === 422) {
    log('billing', `bỏ qua chỉ số ${key}: ${res.status}`);
    return null;
  }
  throw new ApiError('__bootstrap', 'POST', '/api/v1/meter-readings', res);
}

async function generateInvoices(month, year, buildingKey) {
  const res = await call('__bootstrap', 'POST', '/api/v1/invoices/generate', {
    month,
    year,
    buildingId: bid(buildingKey),
  });
  log('billing', `chốt sổ ${month}/${year} tòa ${buildingKey}: tạo ${res.generatedCount}, bỏ qua ${res.skipped?.length ?? 0}`);
  return res;
}

async function invoicesOf(buildingKey, month, year) {
  const page = await call(
    '__bootstrap',
    'GET',
    `/api/v1/invoices?buildingId=${bid(buildingKey)}&month=${month}&year=${year}&size=100`,
  );
  return page.content ?? [];
}

function findInvoice(list, roomKey, customerKey) {
  return list.find((i) => same(i.roomId, rid(roomKey)) && same(i.customerId, accId(customerKey)));
}

async function payInvoice(invoice, amount) {
  if (!invoice) throw new Error('Không tìm thấy hóa đơn để thanh toán');
  const paid = Number(invoice.paidAmount ?? 0);
  const total = Number(invoice.totalAmount ?? 0);
  if (paid >= total || invoice.status === 'PAID') return;
  const remaining = total - paid;
  const pay = Math.min(amount, remaining);
  if (pay <= 0) return;
  await call('__bootstrap', 'POST', '/api/v1/payments', {
    invoiceId: invoice.id,
    amountPaid: pay,
    note: 'Seed thanh toán kiểm thử',
  }, [201]);
  log('billing', `thanh toán ${pay} cho hóa đơn ${invoice.id}`);
}

function execSql(db, user, sql) {
  execFileSync('docker', ['exec', 'rental-postgres', 'psql', '-U', user, '-d', db, '-v', 'ON_ERROR_STOP=1', '-c', sql], {
    stdio: 'pipe',
  });
}

function setInvoiceOverdue(invoiceId) {
  const dueDate = monthEndDay(BILLING_PREV2);
  const sql = `UPDATE invoice SET status='OVERDUE', due_date=DATE '${dueDate}', overdue_notified=true, updated_at=now() ` +
    `WHERE id=${Number(invoiceId)} AND status IN ('UNPAID','PARTIALLY_PAID')`;
  execSql('billing_db', 'billing_svc', sql);
  log('billing', `set OVERDUE cho hóa đơn ${invoiceId}`);
}

// ---------------------------------------------------------------- maintenance

async function ensureAppliance(key, roomKey, name, status) {
  if (ns('appliances')[key]) return s(ns('appliances')[key]);
  const page = await call('__bootstrap', 'GET', `/api/v1/appliances?roomId=${rid(roomKey)}&size=100`);
  const found = (page.content ?? []).find((a) => a.name === name);
  if (found) return setNs('appliances', key, s(found.id));
  const created = await call('__bootstrap', 'POST', '/api/v1/appliances', {
    roomId: rid(roomKey),
    name,
    status,
  }, [200, 201]);
  log('maintenance', `tạo thiết bị ${name} phòng ${roomKey}`);
  return setNs('appliances', key, s(created.id));
}

async function ensureReport(key, customerKey, roomKey, title, description) {
  if (ns('reports')[key]) return s(ns('reports')[key]);
  const page = await call(customerKey, 'GET', '/api/v1/reports/mine?size=100');
  const found = (page.content ?? []).find((r) => r.title === title);
  if (found) {
    log('maintenance', `đã có báo cáo "${title}" (id=${found.id})`);
    return setNs('reports', key, s(found.id));
  }
  const created = await call(customerKey, 'POST', '/api/v1/reports', {
    roomId: rid(roomKey),
    title,
    description,
    imageUrls: [],
  }, [200, 201]);
  log('maintenance', `tạo báo cáo "${title}" (id=${created.id})`);
  return setNs('reports', key, s(created.id));
}

async function getReport(reportId) {
  return call('__bootstrap', 'GET', `/api/v1/reports/${reportId}`);
}

async function findMaintRequestByReport(reportId, buildingId) {
  const page = await call('__bootstrap', 'GET', `/api/v1/maintenance-requests?buildingId=${buildingId}&size=100`);
  return (page.content ?? []).find((r) => same(r.reportId, reportId)) ?? null;
}

async function approveReport(key, body, buildingId) {
  const reportId = ns('reports')[key];
  const report = await getReport(reportId);
  if (report.status === 'APPROVED' || report.status === 'REJECTED') {
    return await findMaintRequestByReport(reportId, buildingId);
  }
  const request = await call('__bootstrap', 'PUT', `/api/v1/reports/${reportId}/approve`, body);
  log('maintenance', `duyệt báo cáo ${reportId} -> phiếu ${request.id}`);
  setNs('maintRequests', key, s(request.id));
  return request;
}

async function rejectReport(key, reason) {
  const reportId = ns('reports')[key];
  const report = await getReport(reportId);
  if (report.status === 'REJECTED') return;
  if (report.status !== 'PENDING') throw new Error(`Báo cáo ${reportId} đang ${report.status}`);
  await call('__bootstrap', 'PUT', `/api/v1/reports/${reportId}/reject`, { reason });
  log('maintenance', `từ chối báo cáo ${reportId}`);
}

async function startMaintRequest(requestId, techKey) {
  const data = await call('__bootstrap', 'GET', `/api/v1/maintenance-requests/${requestId}`);
  if (data.status === 'IN_PROGRESS' || data.status === 'COMPLETED') return data;
  const updated = await call(techKey, 'PUT', `/api/v1/maintenance-requests/${requestId}/start`);
  log('maintenance', `kỹ thuật bắt đầu phiếu ${requestId}`);
  return updated;
}

async function completeMaintRequest(requestId, techKey, body) {
  const data = await call('__bootstrap', 'GET', `/api/v1/maintenance-requests/${requestId}`);
  if (data.status === 'COMPLETED') return data;
  if (data.status !== 'IN_PROGRESS') throw new Error(`Phiếu ${requestId} đang ${data.status}`);
  const updated = await call(techKey, 'POST', `/api/v1/maintenance-requests/${requestId}/complete`, body);
  log('maintenance', `hoàn tất phiếu ${requestId}`);
  return updated;
}

async function ensureSchedule(key, buildingKey, staffKey, def) {
  if (ns('schedules')[key]) return s(ns('schedules')[key]);
  const list = await call('__bootstrap', 'GET', `/api/v1/maintenance-schedules?buildingId=${bid(buildingKey)}`);
  const found = (list ?? []).find((x) => x.title === def.title);
  if (found) return setNs('schedules', key, s(found.id));
  const created = await call('__bootstrap', 'POST', '/api/v1/maintenance-schedules', {
    buildingId: bid(buildingKey),
    staffId: accId(staffKey),
    title: def.title,
    description: def.description,
    intervalMonths: def.intervalMonths,
    firstDate: def.firstDate,
  }, [201]);
  log('maintenance', `tạo lịch bảo trì "${def.title}"`);
  return setNs('schedules', key, s(created.id));
}

async function completeSchedule(key, techKey) {
  const id = ns('schedules')[key];
  if (ns('actions')[`schedule-complete:${key}`]) return;
  await call(techKey, 'POST', `/api/v1/maintenance-schedules/${id}/complete`);
  setNs('actions', `schedule-complete:${key}`, true);
  log('maintenance', `hoàn tất lịch bảo trì ${key}`);
}

async function deactivateSchedule(key) {
  const id = ns('schedules')[key];
  if (ns('actions')[`schedule-deactivate:${key}`]) return;
  await call('__bootstrap', 'DELETE', `/api/v1/maintenance-schedules/${id}`, undefined, [204]);
  setNs('actions', `schedule-deactivate:${key}`, true);
  log('maintenance', `ngừng lịch bảo trì ${key}`);
}

// ---------------------------------------------------------------- main

async function main() {
  log('start', `seed vào ${BASE}, state=${STATE_PATH}`);
  await getToken('__bootstrap');

  // 1. Tài khoản
  const accounts = [
    ['admin', { username: 'seed.admin@test.local', fullName: 'Seed Admin', email: 'seed.admin@test.local', phone: '0900000001', accountType: 'ADMIN' }],
    ['managerA', { username: 'seed.manager.a@test.local', fullName: 'Quản Lý Tòa A', email: 'seed.manager.a@test.local', phone: '0900000002', accountType: 'STAFF', roles: ['QUAN_LY'] }],
    ['managerB', { username: 'seed.manager.b@test.local', fullName: 'Quản Lý Tòa B', email: 'seed.manager.b@test.local', phone: '0900000003', accountType: 'STAFF', roles: ['QUAN_LY'] }],
    ['saleA', { username: 'seed.sale.a@test.local', fullName: 'Sale Tòa A', email: 'seed.sale.a@test.local', phone: '0900000004', accountType: 'STAFF', roles: ['SALE'] }],
    ['saleB', { username: 'seed.sale.b@test.local', fullName: 'Sale Tòa B', email: 'seed.sale.b@test.local', phone: '0900000005', accountType: 'STAFF', roles: ['SALE'] }],
    ['techA', { username: 'seed.tech.a@test.local', fullName: 'Kỹ Thuật Tòa A', email: 'seed.tech.a@test.local', phone: '0900000006', accountType: 'STAFF', roles: ['KY_THUAT'] }],
    ['techB', { username: 'seed.tech.b@test.local', fullName: 'Kỹ Thuật Tòa B', email: 'seed.tech.b@test.local', phone: '0900000007', accountType: 'STAFF', roles: ['KY_THUAT'] }],
    ['active', { username: 'seed.active@test.local', fullName: 'Nguyễn Văn Active', email: 'seed.active@test.local', phone: '0910000001', accountType: 'CUSTOMER' }],
    ['renew', { username: 'seed.renew@test.local', fullName: 'Trần Thị Renew', email: 'seed.renew@test.local', phone: '0910000002', accountType: 'CUSTOMER' }],
    ['terminate', { username: 'seed.terminate@test.local', fullName: 'Lê Văn Terminate', email: 'seed.terminate@test.local', phone: '0910000003', accountType: 'CUSTOMER' }],
    ['moveout', { username: 'seed.moveout@test.local', fullName: 'Phạm Thị Moveout', email: 'seed.moveout@test.local', phone: '0910000004', accountType: 'CUSTOMER' }],
    ['mate', { username: 'seed.mate@test.local', fullName: 'Hoàng Văn Mate', email: 'seed.mate@test.local', phone: '0910000005', accountType: 'CUSTOMER' }],
    ['old', { username: 'seed.old@test.local', fullName: 'Vũ Thị Old', email: 'seed.old@test.local', phone: '0910000006', accountType: 'CUSTOMER' }],
    ['draft', { username: 'seed.draft@test.local', fullName: 'Đặng Văn Draft', email: 'seed.draft@test.local', phone: '0910000007', accountType: 'CUSTOMER' }],
    ['accept', { username: 'seed.accept@test.local', fullName: 'Bùi Thị Accept', email: 'seed.accept@test.local', phone: '0910000008', accountType: 'CUSTOMER' }],
    ['pending', { username: 'seed.pending@test.local', fullName: 'Ngô Văn Pending', email: 'seed.pending@test.local', phone: '0910000009', accountType: 'CUSTOMER' }],
    ['reject', { username: 'seed.reject@test.local', fullName: 'Đỗ Thị Reject', email: 'seed.reject@test.local', phone: '0910000010', accountType: 'CUSTOMER' }],
    ['appt', { username: 'seed.appt@test.local', fullName: 'Lý Văn Appt', email: 'seed.appt@test.local', phone: '0910000011', accountType: 'CUSTOMER' }],
    ['compete', { username: 'seed.compete@test.local', fullName: 'Mai Thị Compete', email: 'seed.compete@test.local', phone: '0910000012', accountType: 'CUSTOMER' }],
    ['applicant', { username: 'seed.applicant@test.local', fullName: 'Tạ Văn Applicant', email: 'seed.applicant@test.local', phone: '0910000013', accountType: 'CUSTOMER' }],
    ['applicant2', { username: 'seed.applicant2@test.local', fullName: 'Phan Thị Applicant 2', email: 'seed.applicant2@test.local', phone: '0910000014', accountType: 'CUSTOMER' }],
    ['applicant3', { username: 'seed.applicant3@test.local', fullName: 'Dương Văn Applicant 3', email: 'seed.applicant3@test.local', phone: '0910000015', accountType: 'CUSTOMER' }],
    ['applicant4', { username: 'seed.applicant4@test.local', fullName: 'Hồ Thị Applicant 4', email: 'seed.applicant4@test.local', phone: '0910000016', accountType: 'CUSTOMER' }],
    ['new', { username: 'seed.new@test.local', fullName: 'Nguyễn Văn New', email: 'seed.new@test.local', phone: '0910000017', accountType: 'CUSTOMER' }],
    ['handover', { username: 'seed.handover@test.local', fullName: 'Đinh Văn Handover', email: 'seed.handover@test.local', phone: '0910000018', accountType: 'CUSTOMER' }],
  ];
  for (const [key, def] of accounts) await ensureAccount(key, def);

  // 2. Tòa nhà + loại phòng
  const location = await ensureDefaultLocation();
  const buildingA = await ensureBuilding('A', {
    name: 'Seed House A',
    body: {
      name: 'Seed House A',
      address: '12 Đường Seed A, Phường 25',
      wardId: location.wardId,
      provinceId: location.provinceId,
      latitude: 10.8031,
      longitude: 106.714,
      description: 'Tòa nhà seed cho kiểm thử tay — khu A',
      lessorName: LESSORS.A.name,
      lessorPhone: LESSORS.A.phone,
      lessorIdNumber: LESSORS.A.idNumber,
      lessorDateOfBirth: LESSORS.A.dob,
      lessorIdIssueDate: LESSORS.A.issueDate,
      lessorIdIssuePlace: LESSORS.A.issuePlace,
      lessorAddress: LESSORS.A.address,
    },
  });
  const buildingB = await ensureBuilding('B', {
    name: 'Seed House B',
    body: {
      name: 'Seed House B',
      address: '18 Đường Seed B, Phường 25',
      wardId: location.wardId,
      provinceId: location.provinceId,
      latitude: 10.8035,
      longitude: 106.715,
      description: 'Tòa nhà seed cho kiểm thử tay — khu B',
      lessorName: LESSORS.B.name,
      lessorPhone: LESSORS.B.phone,
      lessorIdNumber: LESSORS.B.idNumber,
      lessorDateOfBirth: LESSORS.B.dob,
      lessorIdIssueDate: LESSORS.B.issueDate,
      lessorIdIssuePlace: LESSORS.B.issuePlace,
      lessorAddress: LESSORS.B.address,
    },
  });
  const standardType = await ensureRoomType('standard', 'SEED_STANDARD', 'Seed Standard', 'Loại phòng tiêu chuẩn cho seed');
  const studioType = await ensureRoomType('studio', 'SEED_STUDIO', 'Seed Studio', 'Loại phòng studio cho seed');

  // 3. Hồ sơ user + phân công tòa nhà
  for (const [key] of accounts) await waitUserProfile(accId(key), acc(key).accountType);
  await assignBuildings('managerA', [buildingA]);
  await assignBuildings('managerB', [buildingB]);
  await assignBuildings('saleA', [buildingA]);
  await assignBuildings('saleB', [buildingB]);
  await assignBuildings('techA', [buildingA]);
  await assignBuildings('techB', [buildingB]);

  // 4. Phòng + tin đăng
  const roomSpecs = [
    ['A101', 'A', { roomTypeId: standardType, maxCapacity: 2, area: 25, basePrice: 5000000 }],
    ['A102', 'A', { roomTypeId: standardType, maxCapacity: 2, area: 25, basePrice: 4800000 }],
    ['A103', 'A', { roomTypeId: standardType, maxCapacity: 2, area: 25, basePrice: 5200000 }],
    ['A104', 'A', { roomTypeId: standardType, maxCapacity: 3, area: 35, basePrice: 6000000 }],
    ['A105', 'A', { roomTypeId: standardType, maxCapacity: 2, area: 25, basePrice: 5500000 }],
    ['A106', 'A', { roomTypeId: standardType, maxCapacity: 2, area: 25, basePrice: 5000000 }],
    ['A107', 'A', { roomTypeId: standardType, maxCapacity: 2, area: 30, basePrice: 5800000 }],
    ['A108', 'A', { roomTypeId: standardType, maxCapacity: 2, area: 25, basePrice: 5000000 }],
    ['B201', 'B', { roomTypeId: standardType, maxCapacity: 2, area: 35, basePrice: 7000000 }],
    ['B202', 'B', { roomTypeId: standardType, maxCapacity: 2, area: 30, basePrice: 6500000 }],
    ['B203', 'B', { roomTypeId: standardType, maxCapacity: 2, area: 30, basePrice: 6800000 }],
    ['B204', 'B', { roomTypeId: standardType, maxCapacity: 2, area: 30, basePrice: 6500000 }],
    ['B205', 'B', { roomTypeId: studioType, maxCapacity: 3, area: 40, basePrice: 8000000 }],
  ];
  for (const [code, building, spec] of roomSpecs) await ensureRoom(code, building, spec);
  const availableFrom = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
  for (const [code, building, spec] of roomSpecs) {
    await ensureListingTerms(code, building === 'A' ? 'managerA' : 'managerB', spec, availableFrom);
  }
  await setRoomStatus(rid('A103'), 'MAINTENANCE');

  // 5. Yêu cầu thuê + lịch hẹn
  await ensureRequest('A101-pending', 'pending', 'A101', {
    moveInDate: isoDayFromNow(8),
    note: 'Muốn xem phòng trước khi cọc',
  });
  await ensureAppointment('A101-appt', 'A101-pending', 'saleA', isoAtFromNow(1, 2));

  await ensureRequest('A106-reject', 'reject', 'A106', {
    moveInDate: isoDayFromNow(10),
    note: 'Cần phòng gần trung tâm',
  });
  await ensureAppointment('A106-appt', 'A106-reject', 'saleA', isoAtFromNow(2, 3));
  await cancelAppointmentByCustomer(ns('appointments')['A106-appt'], 'reject');
  await rejectRequest('A106-reject', 'managerA', 'Phòng đang ưu tiên khách ở dài hạn');

  await ensureRequest('B202-appt', 'appt', 'B202', {
    moveInDate: isoDayFromNow(13),
    note: 'Hẹn xem phòng buổi chiều',
  });
  await ensureAppointment('B202-appt', 'B202-appt', 'managerB', isoAtFromNow(3, 7));
  await setAppointmentStatus(ns('appointments')['B202-appt'], 'CONFIRMED');

  await ensureRequest('A104-active', 'active', 'A104', {
    moveInDate: isoDayFromNow(3),
    note: 'Thuê chính thức, cần hợp đồng dài hạn',
  });
  await ensureRequest('A104-compete', 'compete', 'A104', {
    moveInDate: isoDayFromNow(4),
    note: 'Cũng muốn thuê phòng này',
  });
  await ensureAppointment('A104-active-appt', 'A104-active', 'saleA', isoAtFromNow(1, 8));
  await setAppointmentStatus(ns('appointments')['A104-active-appt'], 'VIEWED');
  await acceptRequest('A104-active', 'managerA');

  await ensureRequest('B204-accept', 'accept', 'B204', {
    moveInDate: isoDayFromNow(6),
    note: 'Đã xem phòng, đồng ý thuê',
  });
  await acceptRequest('B204-accept', 'managerB');

  // 6. Hợp đồng thủ công + hợp đồng từ yêu cầu
  let contractA104 = ns('contracts')['A104'];
  if (!contractA104) {
    contractA104 = await waitRequestDraft('A104', 'active');
    await updateDraft(contractA104, {
      startDate: isoDayMonthsFromNow(-5),
      endDate: isoDayMonthsFromNow(7),
      rentAmount: 6000000,
      depositAmount: 12000000,
    });
  }
  await ensureSigned(contractA104);
  setNs('contracts', 'A104', contractA104);

  const contractB204 = ns('contracts')['B204'] ?? (await waitRequestDraft('B204', 'accept'));
  setNs('contracts', 'B204', contractB204);
  log('contract', `giữ nháp B204 (${contractB204}) để test sửa + ký`);

  const contractA105 = await ensureManualDraft('A105', {
    customerKey: 'renew',
    roomKey: 'A105',
    buildingKey: 'A',
    startDate: isoDayMonthsFromNow(-11),
    endDate: isoDayFromNow(23),
    rentAmount: 5500000,
    depositAmount: 11000000,
  });
  await ensureSigned(contractA105);

  const contractA107 = await ensureManualDraft('A107', {
    customerKey: 'terminate',
    roomKey: 'A107',
    buildingKey: 'A',
    startDate: isoDayMonthsFromNow(-11),
    endDate: isoDayFromNow(8),
    rentAmount: 5800000,
    depositAmount: 11600000,
  });
  await ensureSigned(contractA107);

  // 7A — Khách mới chuyển vào HÔM NAY. Hợp đồng ACTIVE từ hôm nay, đồng hồ chỉ có mốc
  // bàn giao (initialIndex) và chưa có chỉ số chốt tháng nào. Ký hợp đồng sẽ phát
  // MeterReadingHandoverDue để chuông của kỹ thuật có thông báo ghi chỉ số bàn giao.
  const todayForSeed = isoDayFromNow(0);
  const contractA108 = await ensureManualDraft('A108', {
    customerKey: 'handover',
    roomKey: 'A108',
    buildingKey: 'A',
    startDate: todayForSeed,
    endDate: isoDayMonthsFromNow(12),
    rentAmount: 5000000,
    depositAmount: 10000000,
  });
  const beforeSignA108 = await call('__bootstrap', 'GET', `/api/v1/contracts/${contractA108}`);
  if (beforeSignA108.status === 'DRAFT' && beforeSignA108.startDate !== todayForSeed) {
    await updateDraft(contractA108, { startDate: todayForSeed });
  }
  await ensureSigned(contractA108);
  // Chạy lại seed trên DB cũ vẫn phải giữ "khách chuyển vào hôm nay" để test ngày kế tiếp.
  execSql('contract_db', 'contract_svc',
    `UPDATE contract SET start_date = DATE '${todayForSeed}' WHERE id = ${contractA108}; ` +
    `UPDATE contract_tenant SET joined_at = DATE '${todayForSeed}' WHERE contract_id = ${contractA108} AND left_at IS NULL;`);

  const contractB201 = await ensureManualDraft('B201', {
    customerKey: 'moveout',
    roomKey: 'B201',
    buildingKey: 'B',
    startDate: isoDayMonthsFromNow(-3),
    endDate: isoDayMonthsFromNow(9),
    rentAmount: 7000000,
    depositAmount: 14000000,
  });
  await ensureSigned(contractB201);

  const contractB203 = await ensureManualDraft('B203', {
    customerKey: 'old',
    roomKey: 'B203',
    buildingKey: 'B',
    startDate: isoDayMonthsFromNow(-13),
    endDate: isoDayMonthsFromNow(11),
    rentAmount: 6800000,
    depositAmount: 13600000,
  });
  await ensureSigned(contractB203);

  const contractA102 = await ensureManualDraft('A102', {
    customerKey: 'draft',
    roomKey: 'A102',
    buildingKey: 'A',
    startDate: isoDayFromNow(13),
    endDate: isoDayMonthsFromNow(12),
    rentAmount: 4800000,
    depositAmount: 9600000,
  });

  await waitRoomStatus('A104', 'OCCUPIED');
  await waitRoomStatus('A105', 'OCCUPIED');
  await waitRoomStatus('A107', 'OCCUPIED');
  await waitRoomStatus('A108', 'OCCUPIED');
  await waitRoomStatus('B201', 'OCCUPIED');
  if (!ns('moveOuts')['B203-approved']) await waitRoomStatus('B203', 'OCCUPIED');

  await addTenantIfMissing(contractB201, 'mate', isoDayMonthsFromNow(-2));

  await ensureMoveOut('A105-pending', 'renew', contractA105, isoDayFromNow(10), 'Chuyển về gần gia đình');
  await ensureMoveOut('B201-pending', 'moveout', contractB201, isoDayFromNow(59), 'Chuyển công tác cuối năm');
  await ensureMoveOut('B203-approved', 'old', contractB203, isoDayFromNow(13), 'Đã tìm được chỗ ở mới');
  await approveMoveOut('B203-approved');
  await waitRoomStatus('B203', 'UPCOMING_VACANT');
  const b203Listing = await waitListing(rid('B203'));
  if (b203Listing.status !== 'ACTIVE') {
    await poll('listing B203 mở lại', async () => {
      const res = await request('GET', `/api/v1/listings/room/${rid('B203')}`, { token: await getToken('__bootstrap') });
      return res.status === 200 && res.data.status === 'ACTIVE' ? res.data : null;
    });
  }

  // 7. Ở ghép
  const postA104 = await ensurePost('A104', 'active', 'A104', {
    title: 'A104 tìm bạn ở ghép (seed)',
    description: 'Phòng 35m2, gần trung tâm, có ban công. Ưu tiên người đi làm.',
    budgetShare: 2500000,
  });
  const postA105 = await ensurePost('A105', 'renew', 'A105', {
    title: 'A105 cần thêm 1 bạn ở ghép (seed)',
    description: 'Phòng 25m2, đầy đủ nội thất, chia sẻ chi phí.',
    budgetShare: 2700000,
  });
  const postB201 = await ensurePost('B201', 'moveout', 'B201', {
    title: 'B201 tìm bạn ở ghép (seed)',
    description: 'Phòng đã đủ 2 người, đăng để test trường hợp phòng full.',
    budgetShare: 3000000,
  });
  const postB203 = await ensurePost('B203', 'old', 'B203', {
    title: 'Pass chỗ ở B203 (seed)',
    description: 'Tin đã đóng, dùng để test trạng thái CLOSED.',
    budgetShare: 3000000,
  });

  await ensureApplication('A104-app2', 'A104', 'applicant2', 'Mình muốn chuyển vào từ tháng 11.');
  await ensureApplication('A104-app3', 'A104', 'applicant3', 'Cho mình xem phòng cuối tuần này nhé.');
  await actApplication('A104-app2', 'active', 'applicant2', 'reject', 'REJECTED');
  await ensureApplication('A105-app1', 'A105', 'applicant', 'Mình ở một mình, đi làm văn phòng.');
  // Giữ A105-app1 ở trạng thái PENDING để phòng A105 là phòng đơn của Trần Thị Renew (seed.renew),
  // tránh việc tách lẻ hóa đơn theo ngày cuối tháng 10 gây số tiền lẻ. Người dùng kiểm thử duyệt ở ghép tại phòng A104 (Luồng 4).

  // Đảm bảo PDF hợp đồng đã được sinh sẵn và lưu trong MinIO cho toàn bộ hợp đồng seed
  for (const cid of [contractA104, contractA105, contractA107, contractA108, contractB201, contractB203]) {
    try {
      await call('__bootstrap', 'GET', `/api/v1/contracts/${cid}/document`);
    } catch (e) {
      // bỏ qua nếu đã sinh
    }
  }

  await ensureApplication('B201-app4', 'B201', 'applicant4', 'Mình muốn vào ở ghép.');
  await actApplication('B201-app4', 'moveout', 'applicant4', 'accept', 'REJECTED_FULL');
  await poll('đơn B201 REJECTED_FULL', async () => {
    const status = await applicationStatus(ns('applications')['B201-app4'], 'applicant4');
    return status === 'REJECTED_FULL' ? status : null;
  });

  await call('old', 'PUT', `/api/v1/roommate-posts/${postB203}/close`, undefined, [204]);
  log('roommate', 'đóng tin B203');

  // 8. Điện nước, dịch vụ, hóa đơn
  const electricity = await ensureUtilityType('electricity', 'Điện (Seed)', 'kWh', 3500);
  const water = await ensureUtilityType('water', 'Nước (Seed)', 'm3', 12000);

  const internetA = await ensureBuildingService('internetA', 'A', 'Internet (Seed)', 220000);
  const cleaningA = await ensureBuildingService('cleaningA', 'A', 'Vệ sinh (Seed)', 100000);
  const parkingA = await ensureBuildingService('parkingA', 'A', 'Gửi xe (Seed)', 120000);
  const internetB = await ensureBuildingService('internetB', 'B', 'Internet (Seed)', 220000);
  const cleaningB = await ensureBuildingService('cleaningB', 'B', 'Vệ sinh (Seed)', 100000);

  // Chờ projection contract_info của billing trước khi gắn dịch vụ.
  for (const contractId of [contractA104, contractA105, contractA107, contractA108, contractB201, contractB203]) {
    await poll(`contract_info ${contractId}`, async () => {
      const res = await request('GET', `/api/v1/contract-services?contractId=${contractId}`, {
        token: await getToken('__bootstrap'),
      });
      return res.status === 200 ? true : null;
    });
  }

  // Bỏ toàn bộ các phí phụ trội tòa nhà (Internet, Vệ sinh, Gửi xe) trong seed data
  // để hóa đơn và hợp đồng chỉ phát sinh tiền phòng + điện nước theo đúng công tơ và thỏa thuận hợp đồng.
  /*
  await attachService('A104-internet', contractA104, internetA, 'SHARED');
  await attachService('A104-cleaning', contractA104, cleaningA, 'SHARED');
  await attachService('A104-parking', contractA104, parkingA, 'PERSONAL', a104Tenant);
  await attachService('A105-internet', contractA105, internetA, 'SHARED');
  await attachService('A107-internet', contractA107, internetA, 'SHARED');
  await attachService('B201-internet', contractB201, internetB, 'SHARED');
  await attachService('B201-cleaning', contractB201, cleaningB, 'SHARED');
  await attachService('B203-internet', contractB203, internetB, 'SHARED');
  */

  const meterRooms = [
    ['A104', contractA104],
    ['A105', contractA105],
    ['A107', contractA107],
    ['B201', contractB201],
    ['B203', contractB203],
  ];
  for (const [roomKey] of meterRooms) {
    await ensureMeter(`${roomKey}-elec`, roomKey, 'electricity', `SEED-${roomKey}-E`, 1000);
    await ensureMeter(`${roomKey}-water`, roomKey, 'water', `SEED-${roomKey}-W`, 100);
  }
  // 7A — A108 là phòng bàn giao mới: đồng hồ chỉ có mốc bàn giao, KHÔNG có chỉ số tháng trước.
  // Chỉ số hiện tại do kỹ thuật nhập khi test; tiền = (chỉ số mới − mốc bàn giao) × đơn giá.
  await ensureMeter('A108-elec', 'A108', 'electricity', 'SEED-A108-E', 2500);
  await ensureMeter('A108-water', 'A108', 'water', 'SEED-A108-W', 300);
  // Reset mốc bàn giao để lần seed nào A108 cũng ở trạng thái "chưa ghi chỉ số bàn giao".
  execSql('billing_db', 'billing_svc',
    `UPDATE meter SET initial_index = 2500, initial_index_date = NULL ` +
    `WHERE room_id = ${rid('A108')} AND utility_type_id = ${ns('utilityTypes').electricity}; ` +
    `UPDATE meter SET initial_index = 300, initial_index_date = NULL ` +
    `WHERE room_id = ${rid('A108')} AND utility_type_id = ${ns('utilityTypes').water};`);

  const prev2Tag = monthTag(BILLING_PREV2);
  const prevTag = monthTag(BILLING_PREV);
  const prevReadingDate = monthDay(BILLING_PREV, 28);

  // 7B — A104 có chỉ số chốt tháng trước (1400 kWh / 160 m³) để minh họa hóa đơn đã đủ chỉ số.
  // A105/A107 CỐ TÌNH chưa nhập chỉ số tháng trước: kỹ thuật thấy dòng đỏ "Yêu cầu nhập",
  // chuông có thông báo nhắc, và khi nhập xong hóa đơn của tháng đó tự sinh ngay.
  const closingMissingRooms = new Set(['A105', 'A107']);
  for (const [roomKey] of meterRooms) {
    await ensureReading(`${roomKey}-elec-${prev2Tag}`, ns('meters')[`${roomKey}-elec`], BILLING_PREV2.month, BILLING_PREV2.year, 1200, monthEndDay(BILLING_PREV2));
    await ensureReading(`${roomKey}-water-${prev2Tag}`, ns('meters')[`${roomKey}-water`], BILLING_PREV2.month, BILLING_PREV2.year, 130, monthEndDay(BILLING_PREV2));
    if (closingMissingRooms.has(roomKey)) continue;
    await ensureReading(`${roomKey}-elec-${prevTag}`, ns('meters')[`${roomKey}-elec`], BILLING_PREV.month, BILLING_PREV.year, 1400, prevReadingDate);
    await ensureReading(`${roomKey}-water-${prevTag}`, ns('meters')[`${roomKey}-water`], BILLING_PREV.month, BILLING_PREV.year, 160, prevReadingDate);
  }

  await generateInvoices(BILLING_PREV2.month, BILLING_PREV2.year, 'A');
  await generateInvoices(BILLING_PREV2.month, BILLING_PREV2.year, 'B');
  // Chờ billing nhận TenantAdded của A105 trước khi chốt tháng gần nhất.
  await sleep(2500);
  await generateInvoices(BILLING_PREV.month, BILLING_PREV.year, 'A');
  await generateInvoices(BILLING_PREV.month, BILLING_PREV.year, 'B');

  const sepA = await invoicesOf('A', BILLING_PREV2.month, BILLING_PREV2.year);
  const sepB = await invoicesOf('B', BILLING_PREV2.month, BILLING_PREV2.year);
  const octA = await invoicesOf('A', BILLING_PREV.month, BILLING_PREV.year);
  const octB = await invoicesOf('B', BILLING_PREV.month, BILLING_PREV.year);

  const sepActive = findInvoice(sepA, 'A104', 'active');
  const sepRenew = findInvoice(sepA, 'A105', 'renew');
  const sepTerminate = findInvoice(sepA, 'A107', 'terminate');
  const sepMoveout = findInvoice(sepB, 'B201', 'moveout');
  const sepMate = findInvoice(sepB, 'B201', 'mate');
  const sepOld = findInvoice(sepB, 'B203', 'old');

  if (sepActive && sepActive.status !== 'PAID') await payInvoice(sepActive, Number(sepActive.totalAmount));
  if (sepRenew && sepRenew.status !== 'PARTIALLY_PAID' && sepRenew.status !== 'PAID') await payInvoice(sepRenew, 2000000);
  if (sepTerminate && sepTerminate.status !== 'OVERDUE' && sepTerminate.status !== 'PAID') {
    setInvoiceOverdue(sepTerminate.id);
  }
  for (const inv of [...sepA, ...sepB, ...octA, ...octB]) {
    setNs('invoices', `${inv.roomId}:${inv.customerId}:${inv.month}:${inv.year}`, s(inv.id));
  }

  // 8B. Thông báo cho kỹ thuật — Luồng 7.
  // Chèn thẳng vào outbox (idempotent) để chuông luôn có thông báo ngay sau khi seed, kể cả khi
  // service chưa kịp phát event lúc ký hợp đồng hoặc scheduler chưa chạy tới giờ nhắc.
  const seedNowUtc = new Date().toISOString().replace(/\.\d{3}Z$/, 'Z');
  const handoverEventId = crypto.randomUUID();
  const handoverPayload =
    `{"eventId":"${handoverEventId}","eventType":"MeterReadingHandoverDue","version":1,` +
    `"occurredAt":"${seedNowUtc}","correlationId":"seed-flow7-handover","payload":{` +
    `"contractId":${contractA108},"roomId":${rid('A108')},"buildingId":${bid('A')},` +
    `"roomNumber":"A108","buildingName":"Seed House A","moveInDate":"${todayForSeed}"}}`;
  execSql('contract_db', 'contract_svc',
    `INSERT INTO outbox_event (id, topic, event_key, event_type, payload) ` +
    `SELECT '${handoverEventId}', 'contract.events', '${contractA108}', 'MeterReadingHandoverDue', '${handoverPayload}'::jsonb ` +
    `WHERE NOT EXISTS (SELECT 1 FROM outbox_event WHERE event_type = 'MeterReadingHandoverDue' AND event_key = '${contractA108}')`);

  const reminderEventId = crypto.randomUUID();
  const reminderPayload =
    `{"eventId":"${reminderEventId}","eventType":"MeterReadingReminder","version":1,` +
    `"occurredAt":"${seedNowUtc}","correlationId":"seed-flow7-reminder","payload":{` +
    `"buildingId":${bid('A')},"buildingName":"Seed House A","month":${BILLING_PREV.month},` +
    `"year":${BILLING_PREV.year},"roomNumbers":["A105","A107"],"roomCount":2}}`;
  execSql('billing_db', 'billing_svc',
    `INSERT INTO outbox_event (id, topic, event_key, event_type, payload) ` +
    `SELECT '${reminderEventId}', 'billing.events', '${bid('A')}', 'MeterReadingReminder', '${reminderPayload}'::jsonb ` +
    `WHERE NOT EXISTS (SELECT 1 FROM outbox_event WHERE event_type = 'MeterReadingReminder' ` +
    `AND event_key = '${bid('A')}' AND created_at >= date_trunc('day', now()))`);

  for (const [label, keyword] of [
    ['thông báo ghi chỉ số bàn giao', 'bàn giao'],
    ['thông báo nhắc chỉ số cuối tháng', 'Nhắc nhập chỉ số'],
  ]) {
    await poll(label, async () => {
      const res = await request('GET', '/api/v1/notifications?page=0&size=100&unreadOnly=false', {
        token: await getToken('techA'),
      });
      if (res.status !== 200) return null;
      return (res.data.content ?? []).some((n) => String(n.title ?? '').includes(keyword)) ? true : null;
    });
  }
  log('notify', 'kỹ thuật A đã nhận thông báo bàn giao A108 + nhắc chỉ số tháng trước A105/A107');

  // 9. Thiết bị + báo cáo + phiếu bảo trì + lịch
  const acA104 = await ensureAppliance('A104-ac', 'A104', 'Điều hòa Daikin', 'GOOD');
  await ensureAppliance('A104-sink', 'A104', 'Bồn rửa chén', 'GOOD');
  const heaterA105 = await ensureAppliance('A105-heater', 'A105', 'Bình nóng lạnh Ariston', 'NEEDS_REPAIR');
  const fridgeA107 = await ensureAppliance('A107-fridge', 'A107', 'Tủ lạnh Samsung', 'BROKEN');
  const washerB201 = await ensureAppliance('B201-washer', 'B201', 'Máy giặt LG', 'GOOD');
  if (!ns('actions')['warranty:A104-ac']) {
    await call('__bootstrap', 'PUT', `/api/v1/appliances/${acA104}/warranty`, {
      provider: 'Daikin Việt Nam',
      startDate: isoDayMonthsFromNow(-10),
      endDate: isoDayMonthsFromNow(26),
    });
    setNs('actions', 'warranty:A104-ac', true);
    log('maintenance', 'cập nhật bảo hành điều hòa A104');
  }

  await ensureReport('r1-A104', 'active', 'A104', 'Điều hòa kêu to (seed)', 'Máy chạy có tiếng ồn lớn vào ban đêm.');
  await ensureReport('r2-A105', 'renew', 'A105', 'Bình nóng lạnh rò nước (seed)', 'Nước chảy ra sàn phòng tắm.');
  await ensureReport('r3-A104', 'active', 'A104', 'Rò rỉ đường ống dưới bồn rửa (seed)', 'Nước rỉ xuống tủ bếp.');
  await ensureReport('r4-A107', 'terminate', 'A107', 'Tủ lạnh không lạnh (seed)', 'Ngăn mát vẫn có đá, thực phẩm hỏng.');
  await ensureReport('r5-B201', 'moveout', 'B201', 'Máy giặt rung mạnh (seed)', 'Khi vắt máy rung và chạy lệch một chỗ.');
  await ensureReport('r6-A104', 'active', 'A104', 'Thay bóng đèn hành lang (seed)', 'Bóng đèn trước cửa phòng đã hỏng.');
  await ensureReport('r7-B203', 'old', 'B203', 'Vòi nước nhỏ giọt (seed)', 'Vòi lavabo bị nhỏ giọt liên tục.');

  await rejectReport('r2-A105', 'Không thuộc phạm vi bảo trì của tòa nhà');
  await approveReport('r3-A104', {
    applianceId: ns('appliances')['A104-sink'],
    staffId: accId('techA'),
    priority: 'HIGH',
    scheduledAt: isoAtFromNow(1),
  }, buildingA);
  await approveReport('r4-A107', {
    applianceId: fridgeA107,
    staffId: accId('techA'),
    priority: 'URGENT',
    scheduledAt: isoAtFromNow(3),
  }, buildingA);
  await approveReport('r5-B201', {
    applianceId: washerB201,
    staffId: accId('techB'),
    priority: 'MEDIUM',
    scheduledAt: isoAtFromNow(4),
  }, buildingB);

  if (ns('maintRequests')['r4-A107']) await startMaintRequest(ns('maintRequests')['r4-A107'], 'techA');
  if (ns('maintRequests')['r5-B201']) {
    await startMaintRequest(ns('maintRequests')['r5-B201'], 'techB');
    await completeMaintRequest(ns('maintRequests')['r5-B201'], 'techB', {
      actionTaken: 'Thay cao su giảm chấn và cân bằng máy',
      cost: 250000,
      applianceStatus: 'GOOD',
    });
  }

  await ensureSchedule('A-pccc', 'A', 'techA', {
    title: 'Kiểm tra PCCC định kỳ (seed)',
    description: 'Kiểm tra bình chữa cháy và lối thoát hiểm toàn tòa A.',
    intervalMonths: 3,
    firstDate: isoDayFromNow(3),
  });
  await ensureSchedule('A-elevator', 'A', 'techA', {
    title: 'Bảo trì thang máy (seed)',
    description: 'Bảo trì thang máy định kỳ hằng tháng.',
    intervalMonths: 1,
    firstDate: isoDayFromNow(8),
  });
  await ensureSchedule('B-tank', 'B', 'techB', {
    title: 'Vệ sinh bồn nước (seed)',
    description: 'Vệ sinh bồn nước mái tòa B.',
    intervalMonths: 6,
    firstDate: isoDayFromNow(29),
  });
  await ensureSchedule('B-roof', 'B', 'techB', {
    title: 'Kiểm tra mái và chống thấm (seed)',
    description: 'Lịch đã ngừng áp dụng để test trạng thái inactive.',
    intervalMonths: 12,
    firstDate: isoDayFromNow(74),
  });
  await completeSchedule('A-elevator', 'techA');
  await deactivateSchedule('B-roof');

  // 10. Nhắc A105: roommate được duyệt làm thay đổi số người ở; kiểm tra tenant list lần cuối.
  const a105Final = await call('__bootstrap', 'GET', `/api/v1/contracts/${contractA105}`);
  log('verify', `A105 có ${a105Final.tenants.length} người ở`);

  // 10b. Tòa đã có khách nhưng từng bị tự chuyển INACTIVE khi thêm phòng — kích hoạt lại.
  await reactivateBuilding(buildingA);
  await reactivateBuilding(buildingB);

  // 11. Tổng hợp
  const report = {
    generatedAt: new Date().toISOString(),
    baseUrl: BASE,
    password: PASSWORD,
    accounts: ns('accounts'),
    buildings: { A: buildingA, B: buildingB },
    rooms: ns('rooms'),
    listings: ns('listings'),
    keyContracts: {
      A104: contractA104,
      A105: contractA105,
      A106: 'chỉ có yêu cầu bị từ chối',
      A107: contractA107,
      A108_handover: contractA108,
      A102_draft: contractA102,
      B201: contractB201,
      B203: contractB203,
      B204_draft: contractB204,
    },
    moveOuts: ns('moveOuts'),
    roommatePosts: ns('posts'),
    roommateApplications: ns('applications'),
    invoices: { sepA, sepB, octA, octB },
    // Dữ liệu nền cho Luồng 7 — tách rõ 7A (bàn giao khách mới) và 7B (chốt cuối tháng).
    flow7: {
      handover: {
        customer: 'seed.handover@test.local',
        room: 'A108',
        contractId: contractA108,
        moveInDate: todayForSeed,
        meterBaseline: { electricity: 2500, water: 300 },
        previousReadings: null,
        notification: 'MeterReadingHandoverDue → chuông của seed.tech.a@test.local',
        note: 'Ngày bàn giao luôn là hôm nay. Kỹ thuật nhận thông báo ghi chỉ số bàn giao; chưa có chỉ số tháng trước nên chỉ số cũ hiển thị là mốc 2500 kWh / 300 m³.',
      },
      periodic: {
        staff: 'seed.tech.a@test.local',
        manager: 'seed.manager.a@test.local',
        closingMonth: monthTag(BILLING_PREV),
        rooms: ['A104', 'A105', 'A107'],
        completedRooms: ['A104'],
        missingRooms: ['A105', 'A107'],
        previousReadings: { electricity: 1400, water: 160 },
        notification: 'MeterReadingReminder → chuông của seed.tech.a@test.local',
        note: 'A105/A107 chưa nhập chỉ số tháng trước nên hiện dòng đỏ "Yêu cầu nhập" và có nhắc trên chuông; nhập chỉ số xong hóa đơn tự sinh ngay, không cần admin bấm.',
      },
    },
    maintenance: {
      reports: ns('reports'),
      requests: ns('maintRequests'),
      schedules: ns('schedules'),
    },
  };
  fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), 'utf8');

  console.log('\n================ SEED HOÀN TẤT ================');
  console.log(`Mật khẩu chung: ${PASSWORD}`);
  console.log(`ADMIN:     seed.admin@test.local`);
  console.log(`QUẢN LÝ A: seed.manager.a@test.local   (Seed House A)`);
  console.log(`QUẢN LÝ B: seed.manager.b@test.local   (Seed House B)`);
  console.log(`SALE A/B:  seed.sale.a@test.local / seed.sale.b@test.local`);
  console.log(`KỸ THUẬT:  seed.tech.a@test.local / seed.tech.b@test.local`);
  console.log('KHÁCH:     seed.active / renew / terminate / moveout / mate / old / draft / accept / pending / reject / appt / compete / applicant / applicant2 / applicant3 / applicant4 / new / handover @test.local');
  console.log('LUỒNG 7A:  seed.handover@test.local (A108) + seed.tech.a@test.local  — bàn giao HÔM NAY, chuông đã có thông báo, mốc 2500 kWh / 300 m³');
  console.log(`LUỒNG 7B:  seed.tech.a@test.local + seed.manager.a@test.local  — chốt ${monthTag(BILLING_PREV)}: A104 đủ chỉ số, A105/A107 còn thiếu (dòng đỏ + nhắc chuông), nhập xong hóa đơn tự sinh`);
  console.log(`Báo cáo JSON: ${REPORT_PATH}`);
  console.log('==============================================');
}

main().catch((e) => {
  console.error('\nSEED THẤT BẠI:', e.stack ?? e.message);
  process.exitCode = 1;
});
