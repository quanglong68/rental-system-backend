import fs from 'node:fs';
import path from 'node:path';

const BASE = 'http://localhost:8080';
const USERNAME = 'seed.admin@test.local';
const PASSWORD = 'Test@12345';
const state = JSON.parse(fs.readFileSync(path.resolve('seed/state.json'), 'utf8'));
const reportPath = path.resolve('seed/room-images-report.json');
const report = fs.existsSync(reportPath) ? JSON.parse(fs.readFileSync(reportPath, 'utf8')) : { rooms: {} };

const picturesDir = path.resolve('pictures');
const studiosDir = path.join(picturesDir, 'studios');

const allFiles = fs.readdirSync(picturesDir)
  .filter((f) => /\.(jpe?g|png|webp)$/i.test(f) && fs.statSync(path.join(picturesDir, f)).isFile())
  .sort();
const studioFiles = fs.readdirSync(studiosDir)
  .filter((f) => /\.(jpe?g|png|webp)$/i.test(f))
  .sort();

const standardRooms = ['A101', 'A102', 'A103', 'A104', 'A105', 'A106', 'A107', 'A108', 'B201', 'B202', 'B203', 'B204'];
const pool = allFiles.slice(0, standardRooms.length * 3);
// Ảnh ngoại cảnh tòa nhà (mặt tiền/ngoại thất) chọn theo tên tệp.
const buildingFiles = allFiles.filter((f) => /nha-tro|thiet-ke-nha-tro|mau-thiet-ke/i.test(f));
if (buildingFiles.length < 6) throw new Error('Không đủ ảnh ngoại cảnh cho 2 tòa nhà');
const buildingPlans = {
  A: buildingFiles.slice(0, 3),
  B: buildingFiles.slice(3, 6),
};
if (pool.length < standardRooms.length * 3) throw new Error('Không đủ ảnh cho các phòng tiêu chuẩn');

function mimeFor(file) {
  const ext = path.extname(file).toLowerCase();
  if (ext === '.png') return 'image/png';
  if (ext === '.webp') return 'image/webp';
  return 'image/jpeg';
}

function planFor(roomCode) {
  if (roomCode === 'B205') return studioFiles.slice(0, 3).map((f) => path.join('studios', f));
  const idx = standardRooms.indexOf(roomCode);
  return pool.slice(idx * 3, idx * 3 + 3);
}

async function login() {
  const res = await fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USERNAME, password: PASSWORD }),
  });
  if (!res.ok) throw new Error(`Login thất bại: ${res.status} ${await res.text()}`);
  return (await res.json()).accessToken;
}

async function listImages(token, kind, ownerId) {
  const res = await fetch(`${BASE}/api/v1/images/${kind}/${ownerId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`GET ${kind} images ${ownerId} -> ${res.status}`);
  return res.json();
}

async function uploadImage(token, kind, ownerId, relFile) {
  const abs = path.join(picturesDir, relFile);
  const buf = fs.readFileSync(abs);
  const form = new FormData();
  form.append('file', new Blob([buf], { type: mimeFor(relFile) }), path.basename(relFile));
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    const res = await fetch(`${BASE}/api/v1/images/${kind}/${ownerId}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: form,
    });
    if (res.ok) return res.json();
    if ([502, 503, 504].includes(res.status) && attempt < 3) {
      await new Promise((r) => setTimeout(r, 800));
      continue;
    }
    throw new Error(`Upload ${kind}/${ownerId} ${relFile} -> ${res.status} ${await res.text()}`);
  }
}

async function main() {
  const token = await login();
  let uploaded = 0;
  let skipped = 0;
  const failures = [];

  for (const [roomCode, roomId] of Object.entries(state.rooms)) {
    const plan = planFor(roomCode);
    const existing = await listImages(token, 'rooms', roomId);
    const roomReport = report.rooms[roomCode] ?? { roomId, uploadedFiles: [], imageUrls: [] };
    report.rooms[roomCode] = roomReport;
    for (const file of plan) {
      if (roomReport.uploadedFiles.includes(file)) {
        skipped += 1;
        continue;
      }
      if (existing.length + roomReport.uploadedFiles.length >= 3) break;
      try {
        const image = await uploadImage(token, 'rooms', roomId, file);
        roomReport.uploadedFiles.push(file);
        roomReport.imageUrls.push(image.imageUrl);
        uploaded += 1;
        console.log(`[${roomCode}] + ${file} -> ${image.imageUrl}`);
      } catch (e) {
        failures.push({ roomCode, file, error: e.message });
        console.error(`[${roomCode}] LỖI ${file}: ${e.message}`);
      }
    }
  }

  const buildingReport = report.buildings ?? {};
  report.buildings = buildingReport;
  for (const [code, buildingId] of Object.entries(state.buildings)) {
    const plan = buildingPlans[code] ?? [];
    const existing = await listImages(token, 'buildings', buildingId);
    const entry = buildingReport[code] ?? { buildingId, uploadedFiles: [], imageUrls: [] };
    buildingReport[code] = entry;
    for (const file of plan) {
      if (entry.uploadedFiles.includes(file)) {
        skipped += 1;
        continue;
      }
      if (existing.length + entry.uploadedFiles.length >= 3) break;
      try {
        const image = await uploadImage(token, 'buildings', buildingId, file);
        entry.uploadedFiles.push(file);
        entry.imageUrls.push(image.imageUrl);
        uploaded += 1;
        console.log(`[Tòa ${code}] + ${file} -> ${image.imageUrl}`);
      } catch (e) {
        failures.push({ building: code, file, error: e.message });
        console.error(`[Tòa ${code}] LỖI ${file}: ${e.message}`);
      }
    }
  }

  // Kiểm tra lại qua API và truy cập công khai từng URL
  const verify = [];
  for (const [roomCode, roomId] of Object.entries(state.rooms)) {
    const images = await listImages(token, 'rooms', roomId);
    const checks = [];
    for (const img of images) {
      const res = await fetch(img.imageUrl, { method: 'GET' });
      checks.push({ imageUrl: img.imageUrl, status: res.status, contentType: res.headers.get('content-type') });
      if (!res.ok) failures.push({ roomCode, imageUrl: img.imageUrl, error: `public GET ${res.status}` });
    }
    verify.push({ roomCode, count: images.length, primary: images.find((i) => i.primaryImage)?.imageUrl ?? null, checks });
  }

  const verifyBuildings = [];
  for (const [code, buildingId] of Object.entries(state.buildings)) {
    const images = await listImages(token, 'buildings', buildingId);
    const checks = [];
    for (const img of images) {
      const res = await fetch(img.imageUrl, { method: 'GET' });
      checks.push({ imageUrl: img.imageUrl, status: res.status, contentType: res.headers.get('content-type') });
      if (!res.ok) failures.push({ building: code, imageUrl: img.imageUrl, error: `public GET ${res.status}` });
    }
    verifyBuildings.push({ buildingCode: code, buildingId, count: images.length, primary: images.find((i) => i.primaryImage)?.imageUrl ?? null, checks });
  }

  fs.writeFileSync(reportPath, JSON.stringify({ generatedAt: new Date().toISOString(), uploaded, skipped, failures, rooms: report.rooms, buildings: report.buildings, verify, verifyBuildings }, null, 2), 'utf8');
  const bad = verify.filter((v) => v.count < 3);
  const badBuildings = verifyBuildings.filter((v) => v.count < 3);
  console.log(`\nUpload ${uploaded} ảnh, bỏ qua ${skipped}, lỗi ${failures.length}`);
  console.log(`Phòng đủ 3 ảnh: ${verify.length - bad.length}/${verify.length}`);
  console.log(`Tòa nhà đủ 3 ảnh: ${verifyBuildings.length - badBuildings.length}/${verifyBuildings.length}`);
  if (bad.length) console.log('Thiếu ảnh phòng:', bad.map((b) => `${b.roomCode}=${b.count}`).join(', '));
  if (badBuildings.length) console.log('Thiếu ảnh tòa nhà:', badBuildings.map((b) => `${b.buildingCode}=${b.count}`).join(', '));
}

main().catch((e) => {
  console.error('UPLOAD ẢNH THẤT BẠI:', e.cause ?? e.stack ?? e.message);
  process.exitCode = 1;
});
