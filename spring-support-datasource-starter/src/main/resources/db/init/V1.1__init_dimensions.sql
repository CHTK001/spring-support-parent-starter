-- ============================================
-- 教育 AI 维度表增量脚本
-- 版本：V1.1
-- 说明：新增 学科/年级/班级/学生/教师 五张维度表
-- ============================================

-- ----------------------------
-- 学科表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_subject (
    subject_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '学科ID',
    subject_code VARCHAR(64) NOT NULL COMMENT '学科编码',
    subject_name VARCHAR(100) NOT NULL COMMENT '学科名',
    subject_grade_levels VARCHAR(100) COMMENT '适用学段(逗号分隔)',
    subject_sort_order INT DEFAULT 0 COMMENT '排序',
    subject_status TINYINT DEFAULT 1 COMMENT '状态: 0-停用 1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_subject_code (subject_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学科维度';

-- ----------------------------
-- 年级表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_grade (
    grade_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '年级ID',
    grade_level VARCHAR(20) NOT NULL COMMENT '学段 PRIMARY/JUNIOR/SENIOR/UNIVERSITY',
    grade_name VARCHAR(20) NOT NULL COMMENT '年级名 1-12 或 大一-大四',
    grade_order INT DEFAULT 0 COMMENT '学段内顺序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_grade (grade_level, grade_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='年级维度';

-- ----------------------------
-- 班级表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_class (
    class_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '班级ID',
    class_code VARCHAR(64) NOT NULL COMMENT '班级编码',
    class_name VARCHAR(100) NOT NULL COMMENT '班级名',
    class_grade_level VARCHAR(20) COMMENT '学段',
    class_grade VARCHAR(20) COMMENT '年级',
    class_teacher_id BIGINT COMMENT '班主任 userId',
    class_status TINYINT DEFAULT 1 COMMENT '状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_class_code (class_code),
    INDEX idx_teacher (class_teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级维度';

-- ----------------------------
-- 学生表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_student (
    student_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '学生ID',
    student_user_id BIGINT COMMENT '关联系统 userId',
    student_no VARCHAR(64) NOT NULL COMMENT '学号',
    student_name VARCHAR(100) COMMENT '姓名',
    student_class_id BIGINT COMMENT '班级ID',
    student_grade_level VARCHAR(20) COMMENT '学段',
    student_grade VARCHAR(20) COMMENT '年级',
    student_enroll_year INT COMMENT '入学年份',
    student_status TINYINT DEFAULT 1 COMMENT '状态: 0-离校 1-在读',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_student_no (student_no),
    INDEX idx_user (student_user_id),
    INDEX idx_class (student_class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生档案';

-- ----------------------------
-- 教师表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_teacher (
    teacher_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '教师ID',
    teacher_user_id BIGINT COMMENT '关联系统 userId',
    teacher_no VARCHAR(64) NOT NULL COMMENT '工号',
    teacher_name VARCHAR(100) COMMENT '姓名',
    teacher_subject_code VARCHAR(64) COMMENT '任教学科编码',
    teacher_status TINYINT DEFAULT 1 COMMENT '状态: 0-离职 1-在职',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_teacher_no (teacher_no),
    INDEX idx_user (teacher_user_id),
    INDEX idx_subject (teacher_subject_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师档案';

-- ----------------------------
-- 初始数据 (种子)
-- ----------------------------
INSERT IGNORE INTO edu_subject (subject_code, subject_name, subject_grade_levels, subject_sort_order, subject_status) VALUES
    ('chinese', '语文', 'PRIMARY,JUNIOR,SENIOR', 1, 1),
    ('math',    '数学', 'PRIMARY,JUNIOR,SENIOR,UNIVERSITY', 2, 1),
    ('english', '英语', 'PRIMARY,JUNIOR,SENIOR,UNIVERSITY', 3, 1),
    ('physics', '物理', 'JUNIOR,SENIOR,UNIVERSITY', 4, 1),
    ('chemistry', '化学', 'JUNIOR,SENIOR,UNIVERSITY', 5, 1),
    ('biology', '生物', 'JUNIOR,SENIOR', 6, 1),
    ('history', '历史', 'JUNIOR,SENIOR', 7, 1),
    ('geography', '地理', 'JUNIOR,SENIOR', 8, 1),
    ('politics', '政治', 'JUNIOR,SENIOR,UNIVERSITY', 9, 1);

INSERT IGNORE INTO edu_grade (grade_level, grade_name, grade_order) VALUES
    ('PRIMARY', '1', 1),('PRIMARY', '2', 2),('PRIMARY', '3', 3),
    ('PRIMARY', '4', 4),('PRIMARY', '5', 5),('PRIMARY', '6', 6),
    ('JUNIOR', '初一', 1),('JUNIOR', '初二', 2),('JUNIOR', '初三', 3),
    ('SENIOR', '高一', 1),('SENIOR', '高二', 2),('SENIOR', '高三', 3),
    ('UNIVERSITY', '大一', 1),('UNIVERSITY', '大二', 2),('UNIVERSITY', '大三', 3),('UNIVERSITY', '大四', 4);