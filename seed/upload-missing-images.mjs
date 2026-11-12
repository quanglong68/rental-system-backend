import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.dirname(__dirname);
const BASE = 'http://localhost:8080';
const USERNAME = 'seed.admin@test.local';
const PASSWORD = 'Test@12345';
const reportPath = path.join(__dirname, 'missing-images-report.json');
const candidateDirs = [
  path.join(repoRoot, 'pictures'),
  path.join(repoRoot, 'rental-system-backend', 'pictures'),
  path.join(path.dirname(repoRoot), 'pictures'),
  path.join(path.dirname(repoRoot), 'rental-system-backend', 'pictures'),
];
const picturesDir = candidateDirs.find((d) => fs.existsSync(d) && fs.existsSync(path.join(d, 'studios'))) ?? path.join(repoRoot, 'pictures');
const studiosDir = path.join(picturesDir, 'studios');

const allFiles = fs.readdirSync(picturesDir)
  .filter((f) => /\.(jpe?g|png|webp)$/i.test(f) && fs.statSync(path.join(picturesDir, f)).isFile())
  .sort();
const studioFiles = fs.readdirSync(studiosDir)
  .filter((f) => /\.(jpe?g|png|webp)$/i.test(f))
  .sort()
  .map((f) => path.join('studios', f));
const interiorPool = [...allFiles, ...studioFiles];
const exteriorPool = allFiles.filter((f) => /nha-tro|thiet-ke-nha-tro|mau-thiet-ke/i.test(f));
if (exteriorPool.length < 3 || interiorPool.length < 3) throw new Error('Thiếu ảnh nguồn');

function mimeFor(file) {
  const ext = path.extname(file).toLowerCase();
  if (ext === '.png') return 'image/png';
  if (ext === '.webp') return 'image/webp';
  return 'image/jpeg';
}

const RETRY_STATUSES = new Set([502, 503, 504]);

async function fetchWithRetry(url, makeOptions, label, maxAttempts = 12) {
  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    try {
      const res = await fetch(url, makeOptions());
      if (RETRY_STATUSES.has(res.status) && attempt < maxAttempts) {
        console.log(`[retry ${attempt}/${maxAttempts}] ${label} -> HTTP ${res.status}, chờ Gateway/Eureka...`);
        await new Promise((r) => setTimeout(r, 2500));
        continue;
      }
      return res;
    } catch (err) {
      if (attempt < maxAttempts) {
        console.log(`[retry ${attempt}/${maxAttempts}] ${label} -> ${err.message}, thử lại sau 2.5s...`);
        await new Promise((r) => setTimeout(r, 2500));
        continue;
      }
      throw err;
    }
  }
}

async function login() {
  const res = await fetchWithRetry(
    `${BASE}/api/v1/auth/login`,
    () => ({
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: USERNAME, password: PASSWORD }),
    }),
    'login',
  );
  if (!res.ok) throw new Error(`Login thất bại: ${res.status} ${await res.text()}`);
  return (await res.json()).accessToken;
}

async function listImages(token, kind, ownerId) {
  const res = await fetchWithRetry(
    `${BASE}/api/v1/images/${kind}/${ownerId}`,
    () => ({ headers: token ? { Authorization: `Bearer ${token}` } : {} }),
    `GET ${kind}/${ownerId}`,
  );
  if (!res.ok) throw new Error(`GET ${kind} ${ownerId} -> ${res.status}`);
  const body = await res.json();
  return Array.isArray(body) ? body : [];
}

async function uploadImage(token, kind, ownerId, relFile) {
  const buf = fs.readFileSync(path.join(picturesDir, relFile));
  const mime = mimeFor(relFile);
  const fileName = path.basename(relFile);
  const res = await fetchWithRetry(
    `${BASE}/api/v1/images/${kind}/${ownerId}`,
    () => {
      const form = new FormData();
      form.append('file', new Blob([buf], { type: mime }), fileName);
      return {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: form,
      };
    },
    `POST ${kind}/${ownerId} (${fileName})`,
  );
  if (!res.ok) {
    throw new Error(`${kind}/${ownerId} ${relFile} -> ${res.status} ${await res.text()}`);
  }
  return res.json();
}

function pick(pool, offset, count) {
  return Array.from({ length: count }, (_, i) => pool[(offset + i) % pool.length]);
}

async function fill(token, kind, ownerId, pool, offset, report, key) {
  const existing = await listImages(token, kind, ownerId);
  const entry = report[key] ?? { ownerId, uploadedFiles: [], imageUrls: [], before: existing.length };
  report[key] = entry;
  if (existing.length >= 3) return { uploaded: 0, skipped: 3 };
  const plan = pick(pool, offset, 3 - Math.min(existing.length, 2));
  let uploaded = 0;
  for (const file of plan) {
    if (existing.length + uploaded >= 3) break;
    const image = await uploadImage(token, kind, ownerId, file);
    entry.uploadedFiles.push(file);
    entry.imageUrls.push(image.imageUrl);
    uploaded += 1;
    console.log(`[${kind}] ${key} + ${file}`);
  }
  return { uploaded, skipped: 0 };
}

async function main() {
  const token = await login();
  const headers = { Authorization: `Bearer ${token}` };
  const buildingsRes = await fetchWithRetry(
    `${BASE}/api/v1/buildings/search?page=0&size=100`,
    () => ({ headers }),
    'GET buildings',
  );
  if (!buildingsRes.ok) throw new Error(`GET buildings -> ${buildingsRes.status}`);
  const buildings = (await buildingsRes.json()).content ?? [];
  const report = fs.existsSync(reportPath) ? JSON.parse(fs.readFileSync(reportPath, 'utf8')) : {};
  report.buildings ??= {};
  report.rooms ??= {};

  let uploaded = 0;
  let skipped = 0;
  const failures = [];

  for (const [index, building] of buildings.entries()) {
    try {
      const result = await fill(token, 'buildings', building.buildingId, exteriorPool, index, report.buildings, building.name);
      uploaded += result.uploaded;
      skipped += result.skipped;
    } catch (e) {
      failures.push({ building: building.name, error: e.message });
      console.error(`[building] ${building.name} LỖI: ${e.message}`);
    }
  }

  let roomOffset = 0;
  for (const building of buildings) {
    const roomsRes = await fetchWithRetry(
      `${BASE}/api/v1/rooms?buildingId=${building.buildingId}&page=0&size=100`,
      () => ({ headers }),
      `GET rooms building=${building.buildingId}`,
    );
    if (!roomsRes.ok) {
      failures.push({ building: building.name, error: `GET rooms -> ${roomsRes.status}` });
      continue;
    }
    const rooms = (await roomsRes.json()).content ?? [];
    for (const room of rooms) {
      const key = `${building.name} / ${room.roomCode}`;
      try {
        const result = await fill(token, 'rooms', room.id, interiorPool, roomOffset, report.rooms, key);
        uploaded += result.uploaded;
        skipped += result.skipped;
      } catch (e) {
        failures.push({ room: key, error: e.message });
        console.error(`[room] ${key} LỖI: ${e.message}`);
      }
      roomOffset += 1;
    }
  }

  // Kiểm tra công khai (không token) cho mọi tòa/phòng + xác minh URL MinIO trả HTTP 200
  const verify = [];
  let checkedUrls = 0;
  for (const building of buildings) {
    const bImages = await listImages(null, 'buildings', building.buildingId);
    const roomRes = await fetchWithRetry(
      `${BASE}/api/v1/rooms?buildingId=${building.buildingId}&page=0&size=100`,
      () => ({ headers }),
      `verify GET rooms building=${building.buildingId}`,
    );
    const rooms = roomRes.ok ? (await roomRes.json()).content ?? [] : [];
    const roomChecks = [];
    for (const room of rooms) {
      const images = await listImages(null, 'rooms', room.id);
      roomChecks.push({ roomCode: room.roomCode, count: images.length });
      if (images.length < 3) failures.push({ room: `${building.name}/${room.roomCode}`, error: `chỉ có ${images.length} ảnh` });
      for (const img of images) {
        if (img.imageUrl && img.imageUrl.startsWith('http://localhost:')) {
          const check = await fetchWithRetry(img.imageUrl, () => ({ method: 'HEAD' }), `HEAD ${img.imageUrl}`, 3);
          checkedUrls += 1;
          if (!check.ok) failures.push({ url: img.imageUrl, error: `HTTP ${check.status}` });
        }
      }
    }
    verify.push({ building: building.name, buildingImages: bImages.length, rooms: roomChecks });
    if (bImages.length < 3) failures.push({ building: building.name, error: `chỉ có ${bImages.length} ảnh` });
    for (const img of bImages) {
      if (img.imageUrl && img.imageUrl.startsWith('http://localhost:')) {
        const check = await fetchWithRetry(img.imageUrl, () => ({ method: 'HEAD' }), `HEAD ${img.imageUrl}`, 3);
        checkedUrls += 1;
        if (!check.ok) failures.push({ url: img.imageUrl, error: `HTTP ${check.status}` });
      }
    }
  }

  const anonRoom = verify.flatMap((b) => b.rooms).find((r) => r.count > 0);
  fs.writeFileSync(reportPath, JSON.stringify({ generatedAt: new Date().toISOString(), uploaded, skipped, checkedUrls, failures, report, verify }, null, 2), 'utf8');
  console.log(`\nUpload thêm ${uploaded} ảnh, bỏ qua ${skipped} chỗ đã đủ, kiểm tra ${checkedUrls} URL ảnh, lỗi ${failures.length}`);
  console.log(`Tòa đủ 3 ảnh: ${verify.filter((b) => b.buildingImages >= 3).length}/${verify.length}`);
  console.log(`Phòng đủ 3 ảnh: ${verify.flatMap((b) => b.rooms).filter((r) => r.count >= 3).length}/${verify.flatMap((b) => b.rooms).length}`);
  if (anonRoom) console.log(`Mẫu phòng: ${anonRoom.roomCode} = ${anonRoom.count} ảnh`);
  if (failures.length > 0) {
    console.error('CÓ LỖI UPLOAD/KIỂM TRA ẢNH:', JSON.stringify(failures.slice(0, 10), null, 2));
    process.exitCode = 1;
  }
}

main().catch((e) => {
  console.error('UPLOAD ẢNH THẤT BẠI:', e.cause ?? e.stack ?? e.message);
  process.exitCode = 1;
});
