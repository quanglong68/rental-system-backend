# ImageStorageController — Tải ảnh

Base path: `/api/v1/images`. Các endpoint dùng `multipart/form-data`.

| Method | Path | Multipart field | Data payload |
|---|---|---|---|
| POST | `/api/v1/images/buildings/{buildingId}` | `file` (một ảnh) | `ImageRecord` |
| POST | `/api/v1/images/rooms/{roomId}` | `file` (một ảnh) | `ImageRecord` |
| GET | `/api/v1/images/buildings/{buildingId}` | — | `ImageRecord[]` |
| GET | `/api/v1/images/rooms/{roomId}` | — | `ImageRecord[]` |
| PATCH | `/api/v1/images/buildings/{buildingId}/{imageId}` | JSON | `ImageRecord` |
| PATCH | `/api/v1/images/rooms/{roomId}/{imageId}` | JSON | `ImageRecord` |
| DELETE | `/api/v1/images/buildings/{buildingId}/{imageId}` | — | — |
| DELETE | `/api/v1/images/rooms/{roomId}/{imageId}` | — | — |
| POST | `/api/v1/images/{folderName}` | `files` (một/nhiều ảnh) | `string[]` URL |

Ví dụ:

```bash
curl -X POST "http://localhost:8083/api/v1/images/buildings/898785309339156480" -F "file=@./building.png"
```

Chỉ hỗ trợ JPEG (`image/jpeg`), PNG (`image/png`) và WebP (`image/webp`); chữ ký file phải khớp MIME type. Kích thước mỗi ảnh tối đa mặc định 5 MiB (`STORAGE_IMAGE_MAX_SIZE`, byte); toàn request tối đa 50 MB. Owner ID phải dương. `folderName` chỉ gồm chữ/số ASCII, `_`, `-`, dài 1–80 ký tự.

Upload một ảnh lưu metadata vào `building_images` hoặc `room_images` và trả `data` dạng:

```json
{"imageId":"898785309339156481","ownerId":"898785309339156480","imageUrl":"https://storage.example/image.png","displayOrder":0,"primaryImage":true,"createdAt":"2026-10-21T00:00:00Z"}
```

Upload nhiều ảnh trả mảng URL string. File rỗng, MIME type không hỗ trợ, nội dung không khớp hoặc vượt giới hạn có thể trả `400`.

PATCH nhận `{"displayOrder": 1, "primaryImage": true}`; có thể chỉ gửi một trường. Chọn ảnh đại diện sẽ bỏ cờ đại diện của các ảnh khác trong cùng room/building. Xóa ảnh đại diện sẽ chọn ảnh còn lại đầu tiên theo thứ tự hiển thị. DELETE xóa bản ghi metadata; đối tượng đã upload trên R2 hiện vẫn được giữ lại.
