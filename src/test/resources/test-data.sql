-- Seed data for tests

-- Room types
INSERT INTO room_type (type_id, room_type, remark, price, discount, area, bed_num, bed_size, window, rest)
VALUES (1, 'single', 'Single room', 200.0, 1.0, 15, 1, '1.2m', 1, 10);

INSERT INTO room_type (type_id, room_type, remark, price, discount, area, bed_num, bed_size, window, rest)
VALUES (2, 'double', 'Double room', 350.0, 1.0, 20, 1, '1.8m', 1, 10);

-- Physical rooms (3 single + 3 double = 6 rooms)
INSERT INTO room_info (room_id, room_number, type_id, room_type, room_price, room_discount, room_status) VALUES (1, '101', 1, 'single', 200.0, 1.0, 1);
INSERT INTO room_info (room_id, room_number, type_id, room_type, room_price, room_discount, room_status) VALUES (2, '102', 1, 'single', 200.0, 1.0, 1);
INSERT INTO room_info (room_id, room_number, type_id, room_type, room_price, room_discount, room_status) VALUES (3, '103', 1, 'single', 200.0, 1.0, 1);
INSERT INTO room_info (room_id, room_number, type_id, room_type, room_price, room_discount, room_status) VALUES (4, '201', 2, 'double', 350.0, 1.0, 1);
INSERT INTO room_info (room_id, room_number, type_id, room_type, room_price, room_discount, room_status) VALUES (5, '202', 2, 'double', 350.0, 1.0, 1);
INSERT INTO room_info (room_id, room_number, type_id, room_type, room_price, room_discount, room_status) VALUES (6, '203', 2, 'double', 350.0, 1.0, 1);

-- Order types
INSERT INTO order_type (type_id, type, remark) VALUES (1, 'phone', 'Phone booking');
INSERT INTO order_type (type_id, type, remark) VALUES (2, 'online', 'Online booking');

-- Test user
INSERT INTO user_info (user_id, username, password, name, phone) VALUES (1, 'testuser', 'e10adc3949ba59abbe56e057f20f883e', 'TestUser', '13800000001');
