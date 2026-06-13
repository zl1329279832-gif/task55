-- Hotel schema for H2 (MySQL mode)
-- Drop all tables first for clean state between tests

SET REFERENTIAL_INTEGRITY FALSE;

DROP TABLE IF EXISTS check_in;
DROP TABLE IF EXISTS order_info;
DROP TABLE IF EXISTS room_inventory;
DROP TABLE IF EXISTS room_info;
DROP TABLE IF EXISTS room_type;
DROP TABLE IF EXISTS order_type;
DROP TABLE IF EXISTS user_info;
DROP TABLE IF EXISTS worker_info;
DROP TABLE IF EXISTS hotel_info;

SET REFERENTIAL_INTEGRITY TRUE;

CREATE TABLE room_type (
  type_id INT AUTO_INCREMENT PRIMARY KEY,
  room_type VARCHAR(16) UNIQUE,
  remark VARCHAR(128),
  price DOUBLE,
  discount DOUBLE,
  area INT DEFAULT 12,
  bed_num INT DEFAULT 1,
  bed_size VARCHAR(16),
  window INT DEFAULT 0,
  rest INT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE room_info (
  room_id INT AUTO_INCREMENT PRIMARY KEY,
  room_number VARCHAR(8) UNIQUE,
  type_id INT,
  room_type VARCHAR(16),
  room_price DOUBLE,
  room_discount DOUBLE,
  room_status INT DEFAULT 1,
  remark VARCHAR(255),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_type (
  type_id INT AUTO_INCREMENT PRIMARY KEY,
  type VARCHAR(16),
  remark VARCHAR(128),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_info (
  user_id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(16) UNIQUE,
  password VARCHAR(64),
  name VARCHAR(16),
  gender VARCHAR(4),
  phone VARCHAR(16),
  email VARCHAR(32),
  address VARCHAR(64),
  idcard VARCHAR(18),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_info (
  order_id INT AUTO_INCREMENT PRIMARY KEY,
  order_type_id INT DEFAULT 2,
  order_type VARCHAR(8),
  user_id INT DEFAULT 0,
  name VARCHAR(16),
  phone VARCHAR(16),
  room_type_id INT,
  room_type VARCHAR(16),
  order_date DATE,
  order_days INT DEFAULT 1,
  order_status INT DEFAULT 0,
  order_cost DOUBLE,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE check_in (
  check_in_id INT AUTO_INCREMENT PRIMARY KEY,
  order_id INT,
  room_id INT,
  room_number VARCHAR(8),
  peo_count INT DEFAULT 1,
  persons VARCHAR(255),
  ids VARCHAR(255),
  check_in_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  check_out_time TIMESTAMP NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE room_inventory (
  id INT AUTO_INCREMENT PRIMARY KEY,
  type_id INT,
  inv_date DATE,
  total INT DEFAULT 0,
  ordered INT DEFAULT 0,
  occupied INT DEFAULT 0,
  reserved INT DEFAULT 0,
  maintenance INT DEFAULT 0,
  price DECIMAL(10,2),
  version INT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (type_id, inv_date)
);

CREATE TABLE worker_info (
  worker_id INT AUTO_INCREMENT PRIMARY KEY,
  role VARCHAR(16),
  username VARCHAR(16) UNIQUE,
  password VARCHAR(64),
  name VARCHAR(16),
  gender VARCHAR(4),
  phone VARCHAR(16),
  email VARCHAR(32),
  address VARCHAR(64),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE hotel_info (
  hotel_id INT AUTO_INCREMENT PRIMARY KEY,
  hotel_name VARCHAR(16),
  phone VARCHAR(16),
  telephone VARCHAR(16),
  email VARCHAR(32),
  address VARCHAR(32),
  website VARCHAR(32),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
