-- V2: seed role co dinh (docs muc 7.3 + Q3: clone ve chay la co san, khong lenh phu tro).
-- ON CONFLICT DO NOTHING de seed lai an toan, khong sua migration da merge (docs muc 12).
INSERT INTO role (id, code) VALUES
    (1, 'ADMIN'),
    (2, 'QUAN_LY'),
    (3, 'KY_THUAT'),
    (4, 'SALE'),
    (5, 'CUSTOMER')
ON CONFLICT (id) DO NOTHING;
