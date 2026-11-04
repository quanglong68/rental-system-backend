-- Hoa don sinh truoc ban sua message da luu mo ta khong dau vao DB; chuan hoa lai du lieu cu.
UPDATE invoice_item
SET description = replace(
        replace(
            replace(
                replace(
                    replace(description, 'Tien phong thang ', 'Tiền phòng tháng '),
                    'Tien dien thang ', 'Tiền điện tháng '),
                'Tien nuoc thang ', 'Tiền nước tháng '),
            '(ca nhan) thang ', '(cá nhân) tháng '),
        '(chung) thang ', '(chung) tháng ')
WHERE description LIKE 'Tien phong thang %'
   OR description LIKE 'Tien dien thang %'
   OR description LIKE 'Tien nuoc thang %'
   OR description LIKE '%(ca nhan) thang %'
   OR description LIKE '%(chung) thang %';
