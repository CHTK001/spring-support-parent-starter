-- ============================================================
-- 设备分组管理
-- 树形分组 + 设备归属
-- ============================================================

-- 分组表
CREATE TABLE IF NOT EXISTS device_group (
    group_id       VARCHAR(36) PRIMARY KEY COMMENT '主键',
    group_name     VARCHAR(64) NOT NULL COMMENT '分组名称',
    group_parent   VARCHAR(36) COMMENT '上级分组ID；顶级为空',
    group_order    INT DEFAULT 0 COMMENT '排序序号',
    group_remark   VARCHAR(512) COMMENT '备注',
    created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备分组表';

CREATE INDEX idx_group_parent ON device_group(group_parent);
CREATE INDEX idx_group_order  ON device_group(group_order);

-- 设备-分组归属表（一台设备可属于多个分组）
CREATE TABLE IF NOT EXISTS device_group_device (
    id           VARCHAR(36) PRIMARY KEY COMMENT '主键',
    group_id     VARCHAR(36) NOT NULL COMMENT '分组ID',
    device_id    VARCHAR(36) NOT NULL COMMENT '设备ID',
    device_order INT DEFAULT 0 COMMENT '分组内排序',
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_gd (group_id, device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备-分组归属表';

CREATE INDEX idx_gd_group  ON device_group_device(group_id);
CREATE INDEX idx_gd_device ON device_group_device(device_id);
