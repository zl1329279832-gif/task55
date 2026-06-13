-- ============================================================
-- Migration: Add room_inventory table
-- No changes to existing tables.
-- ============================================================

CREATE TABLE `room_inventory` (
  `id`             INT            NOT NULL AUTO_INCREMENT,
  `type_id`        INT            NOT NULL COMMENT '关联 room_type.type_id',
  `inv_date`       DATE           NOT NULL COMMENT '库存日期',
  `total`          INT            NOT NULL DEFAULT 0 COMMENT '该房型当天总房间数',
  `ordered`        INT            NOT NULL DEFAULT 0 COMMENT '已付款待入住',
  `occupied`       INT            NOT NULL DEFAULT 0 COMMENT '已入住',
  `reserved`       INT            NOT NULL DEFAULT 0 COMMENT '操作员保留',
  `maintenance`    INT            NOT NULL DEFAULT 0 COMMENT '维修锁房',
  `price`          DECIMAL(10,2)  DEFAULT NULL COMMENT '当日价格覆盖，NULL=用房型基础价',
  `version`        INT            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `create_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_date` (`type_id`, `inv_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按房型-日期的房态库存';
