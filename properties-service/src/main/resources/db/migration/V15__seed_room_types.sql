INSERT INTO room_type (
    room_type_id,
    name,
    description,
    type_code,
    created_at,
    updated_at
)
VALUES
    (123456789012345601, 'Phòng tiêu chuẩn',
     'Phòng cơ bản, đáp ứng nhu cầu sinh hoạt thông thường',
     'STANDARD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    (123456789012345602, 'Phòng studio',
     'Không gian khép kín, kết hợp khu vực ngủ, sinh hoạt và bếp',
     'STUDIO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    (123456789012345603, 'Phòng loft',
     'Phòng có gác lửng hoặc thiết kế không gian thông tầng',
     'LOFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    (123456789012345604, 'Phòng có gác lửng',
     'Phòng có gác lửng để ngủ hoặc lưu trữ đồ đạc',
     'MEZZANINE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    (123456789012345605, 'Phòng riêng',
     'Phòng ngủ riêng, có thể sử dụng chung một số tiện ích',
     'PRIVATE_ROOM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    (123456789012345606, 'Phòng ở ghép',
     'Phòng có nhiều người ở chung',
     'SHARED_ROOM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    (123456789012345607, 'Phòng ký túc xá',
     'Phòng tập thể, thường có nhiều giường',
     'DORMITORY', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    (123456789012345608, 'Phòng khép kín',
     'Phòng có nhà vệ sinh riêng bên trong',
     'ENSUITE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    (123456789012345609, 'Căn hộ',
     'Không gian ở độc lập, thường có khu vực ngủ, bếp và phòng khách',
     'APARTMENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    (123456789012345610, 'Căn hộ mini',
     'Căn hộ diện tích nhỏ, phù hợp người độc thân hoặc cặp đôi',
     'MINI_APARTMENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    (123456789012345611, 'Căn hộ dịch vụ',
     'Căn hộ có thể đi kèm dịch vụ vệ sinh, giặt là hoặc tiện ích khác',
     'SERVICED_APARTMENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    (123456789012345612, 'Căn hộ thông tầng',
     'Căn hộ có hai tầng kết nối bên trong',
     'DUPLEX', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    (123456789012345613, 'Căn hộ áp mái cao cấp',
     'Căn hộ cao cấp ở tầng trên cùng hoặc các tầng trên cùng',
     'PENTHOUSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    (123456789012345614, 'Nhà nguyên căn',
     'Cho thuê toàn bộ căn nhà',
     'WHOLE_HOUSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    (123456789012345615, 'Phòng trong nhà riêng',
     'Phòng cho thuê nằm trong nhà của chủ sở hữu',
     'PRIVATE_HOUSE_ROOM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;