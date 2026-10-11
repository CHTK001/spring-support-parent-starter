-- V1.14 老师端数据范围收敛：教师班级配置 + 应用配置表
--
-- 背景：老师端定位为「布置等」教学功能端，数据范围 = 后台配置的班级；
--       老师上传题目需管理员审批（管理员可配「自动通过」）。
-- 此前老师自建班级（edu_class.class_owner_user_id = 教师账号），
-- 本版起班级由管理员统一维护，老师的班级列表完全来自 edu_teacher_class。

CREATE TABLE IF NOT EXISTS edu_teacher_class (
    teacher_class_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关联ID',
    teacher_class_teacher_id BIGINT NOT NULL COMMENT '教师ID（edu_teacher.teacher_id）',
    teacher_class_class_id BIGINT NOT NULL COMMENT '班级ID（edu_class.class_id）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '配置时间',
    UNIQUE KEY uk_edu_teacher_class (teacher_class_teacher_id, teacher_class_class_id),
    KEY idx_edu_teacher_class_class (teacher_class_class_id)
) COMMENT='教师班级配置（老师端数据范围）';

-- 迁移：教师自建班级自动转为配置关系，保证现有老师端数据不丢；
-- 管理员后续可在「学生管理 → 教师 → 配置班级」增删。
INSERT IGNORE INTO edu_teacher_class (teacher_class_teacher_id, teacher_class_class_id)
SELECT t.teacher_id, c.class_id
FROM edu_class c
JOIN edu_teacher t ON t.teacher_account = c.class_owner_user_id
WHERE c.class_owner_user_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS edu_app_config (
    config_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '配置ID',
    config_key VARCHAR(100) NOT NULL COMMENT '配置键（如 teacher-question-auto-approve）',
    config_value VARCHAR(500) COMMENT '配置值',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_edu_app_config_key (config_key)
) COMMENT='应用配置（老师上传自动通过等）';
