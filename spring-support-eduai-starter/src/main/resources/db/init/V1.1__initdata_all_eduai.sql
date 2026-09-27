-- =====================================================================
-- V1.0__initdata_all_eduai.sql - 种子数据（合并）
-- =====================================================================


-- ============================================================
-- merged from V1.1__initdata_eduai_questions.sql
-- ============================================================
-- =====================================================================
-- V1.1__initdata_eduai_questions.sql - 教育AI题库种子数据
-- 说明: 真实采集题目入库（语文诗词/数学口算/英语词汇/理科学科/成语），
--       与 Groovy 采集脚本产出对齐，source_type=CRAWL。
--       INSERT IGNORE 保证幂等，重复执行不会主键冲突。
-- =====================================================================

-- =====================================================================
-- 一、语文 · 唐诗（默写填空 + 作者归属单选）
-- =====================================================================

INSERT IGNORE INTO edu_question (question_type, question_subject, question_grade_level, question_grade, question_difficulty, question_stem, question_options, question_answer, question_analysis, question_source, question_source_type, question_audit_status, question_status) VALUES
    ('FILL', '语文', '小学', '一年级', 1, '《静夜思》的首句是“______”。', NULL, '床前明月光', '出自李白《静夜思》。', '内置示例', 'MANUAL', 0, 1),
    ('SINGLE_CHOICE', '语文', '小学', '一年级', 1, '《静夜思》的作者是哪位诗人？', '["A. 李白", "B. 杜甫", "C. 王维", "D. 白居易"]', 'A', '李白，唐代诗人。', '内置示例', 'MANUAL', 0, 1),
    ('JUDGE', '语文', '小学', '二年级', 1, '“春眠不觉晓”出自《春晓》。', '["对", "错"]', '对', '孟浩然《春晓》。', '内置示例', 'MANUAL', 0, 1),
    ('SINGLE_CHOICE', '语文', '初中', '初一', 2, '《江雪》的作者是？', '["A. 柳宗元", "B. 韩愈", "C. 苏轼", "D. 王安石"]', 'A', '柳宗元《江雪》。', '内置示例', 'MANUAL', 0, 1),
    ('FILL', '语文', '高中', '高一', 3, '《水调歌头》中表达对亲人思念的名句是“______，______”。', NULL, '但愿人长久，千里共婵娟', '苏轼《水调歌头》。', '内置示例', 'MANUAL', 0, 1),
    ('SHORT_ANSWER', '语文', '高中', '高二', 3, '简述《岳阳楼记》中“先天下之忧而忧”的含义。', NULL, '表达了以天下为己任、忧国忧民的情怀。', '范仲淹《岳阳楼记》。', '内置示例', 'MANUAL', 0, 1),
    ('FILL', '数学', '小学', '一年级', 1, '3 + 5 = ______', NULL, 8, '口算加法。', '内置示例', 'MANUAL', 0, 1),
    ('SINGLE_CHOICE', '数学', '小学', '三年级', 1, '下列哪个数是偶数？', '["A. 3", "B. 7", "C. 8", "D. 9"]', 'C', '8 能被 2 整除。', '内置示例', 'MANUAL', 0, 1),
    ('FILL', '数学', '初中', '初一', 2, '一元二次方程 x^2 - 5x + 6 = 0 的两个根是 ______。', NULL, '2 和 3', '因式分解 (x-2)(x-3)=0。', '内置示例', 'MANUAL', 0, 1),
    ('SINGLE_CHOICE', '数学', '高中', '高一', 3, '函数 y = sin x 的最小正周期是？', '["A. π/2", "B. π", "C. 2π", "D. 4π"]', 'C', '正弦函数周期 2π。', '内置示例', 'MANUAL', 0, 1),
    ('JUDGE', '英语', '初中', '初一', 1, '“apple”的中文意思是“苹果”。', '["对", "错"]', '对', 'apple 苹果。', '内置示例', 'MANUAL', 0, 1),
    ('FILL', '英语', '高中', '高一', 2, 'The past tense of “go” is ______.', NULL, 'went', 'go-went-gone。', '内置示例', 'MANUAL', 0, 1);

-- =====================================================================
-- 二、题库初始化真实数据（覆盖 9 学科 · 小学/初中/高中 · 填空/单选/判断/简答）
-- ---------------------------------------------------------------------
-- 说明：均为可核对的标准教材知识点（名句默写、公式定理、单词变形、
--       科学常识、史地事实等），用于全新安装后题库即可用。
-- 幂等：INSERT IGNORE；question_source='内置题库' 便于与采集数据区分。
-- audit_status=1 表示已通过审核，status=1 表示启用。
-- 注意：字符串内不出现半角单引号（SQL 字面量分隔符），引号一律用全角 “ ”。
-- =====================================================================

-- ---------- 语文 ----------
INSERT IGNORE INTO edu_question (question_type, question_subject, question_grade_level, question_grade, question_difficulty, question_stem, question_options, question_answer, question_analysis, question_source, question_source_type, question_audit_status, question_status) VALUES
    ('FILL', '语文', '小学', '二年级', 1, '“两个黄鹂鸣翠柳”的下一句是“______”。', NULL, '一行白鹭上青天', '杜甫《绝句》。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '语文', '小学', '三年级', 1, '《咏鹅》的首句是“______”。', NULL, '鹅，鹅，鹅', '骆宾王《咏鹅》。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '语文', '小学', '四年级', 1, '“白日依山尽，黄河入海流”的作者是？', '["A. 王之涣", "B. 李白", "C. 杜甫", "D. 王维"]', 'A', '王之涣《登鹳雀楼》。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '语文', '小学', '五年级', 1, '《登鹳雀楼》中“欲穷千里目”的下一句是“______”。', NULL, '更上一层楼', '王之涣《登鹳雀楼》。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '语文', '小学', '六年级', 2, '“粉骨碎身浑不怕，要留清白在人间”歌咏的对象是？', '["A. 石灰", "B. 梅花", "C. 竹子", "D. 青松"]', 'A', '于谦《石灰吟》，以石灰自喻。', '内置题库', 'MANUAL', 1, 1),
    ('JUDGE', '语文', '小学', '三年级', 1, '《春晓》的作者是孟浩然。', '["对", "错"]', '对', '孟浩然，唐代田园山水诗人。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '语文', '初中', '初一', 1, '“床前明月光”出自下列哪首诗？', '["A. 《静夜思》", "B. 《望庐山瀑布》", "C. 《早发白帝城》", "D. 《赠汪伦》"]', 'A', '李白《静夜思》。', '内置题库', 'MANUAL', 1, 1),
    ('JUDGE', '语文', '初中', '初一', 1, '《红楼梦》的作者是曹雪芹。', '["对", "错"]', '对', '曹雪芹著《红楼梦》（后四十回一般认为高鹗续）。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '语文', '初中', '初二', 2, '王维《使至塞上》中描写大漠的名句是“______，长河落日圆”。', NULL, '大漠孤烟直', '王维《使至塞上》。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '语文', '初中', '初二', 2, '《论语》中“学而不思则罔，______”。', NULL, '思而不学则殆', '《论语·为政》。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '语文', '初中', '初三', 2, '《岳阳楼记》的作者是？', '["A. 范仲淹", "B. 欧阳修", "C. 苏轼", "D. 王安石"]', 'A', '范仲淹《岳阳楼记》。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '语文', '高中', '高一', 3, '苏轼《赤壁赋》中“寄蜉蝣于天地，______”。', NULL, '渺沧海之一粟', '苏轼《赤壁赋》。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '语文', '高中', '高一', 2, '下列作品属于鲁迅小说集的是？', '["A. 《呐喊》", "B. 《朝花夕拾》", "C. 《野草》", "D. 《女神》"]', 'A', '《呐喊》为小说集；《朝花夕拾》是散文集，《野草》是散文诗集，《女神》是郭沫若诗集。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '语文', '高中', '高二', 2, '“无边落木萧萧下”的下一句是？', '["A. 不尽长江滚滚来", "B. 长使英雄泪满襟", "C. 潦倒新停浊酒杯", "D. 渚清沙白鸟飞回"]', 'A', '杜甫《登高》。', '内置题库', 'MANUAL', 1, 1),
    ('SHORT_ANSWER', '语文', '高中', '高三', 3, '简述《劝学》中“青，取之于蓝，而青于蓝”的含义。', NULL, '靛青从蓝草中提取却比蓝草更青，比喻学生经过学习可以超过老师。', '《荀子·劝学》。', '内置题库', 'MANUAL', 1, 1);

-- ---------- 数学 ----------
INSERT IGNORE INTO edu_question (question_type, question_subject, question_grade_level, question_grade, question_difficulty, question_stem, question_options, question_answer, question_analysis, question_source, question_source_type, question_audit_status, question_status) VALUES
    ('FILL', '数学', '小学', '一年级', 1, '7 + 8 = ______', NULL, 15, '进位加法。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '数学', '小学', '二年级', 1, '下列哪个数是奇数？', '["A. 4", "B. 6", "C. 9", "D. 10"]', 'C', '9 不能被 2 整除。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '数学', '小学', '三年级', 1, '一个正方形的边长是 5 厘米，它的周长是 ______ 厘米。', NULL, 20, '周长 = 边长 × 4 = 5 × 4 = 20。', '内置题库', 'MANUAL', 1, 1),
    ('JUDGE', '数学', '小学', '四年级', 1, '三角形的内角和是 180 度。', '["对", "错"]', '对', '三角形内角和定理。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '数学', '小学', '五年级', 1, '0.5 + 0.25 = ______', NULL, 0.75, '小数加法，对齐小数点。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '数学', '小学', '六年级', 1, '圆的周长公式是？', '["A. πr²", "B. 2πr", "C. πd²", "D. 2r"]', 'B', '周长 C = 2πr（也写作 πd）。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '数学', '初中', '初一', 1, '计算：(-3) + 5 = ______', NULL, 2, '异号两数相加，取绝对值较大的符号。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '数学', '初中', '初二', 2, '下列各数中是无理数的是？', '["A. 1/3", "B. √2", "C. 0.25", "D. -7"]', 'B', '√2 是无限不循环小数。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '数学', '初中', '初二', 2, '因式分解：x² - 9 = ______', NULL, '(x+3)(x-3)', '平方差公式 a²-b²=(a+b)(a-b)。', '内置题库', 'MANUAL', 1, 1),
    ('JUDGE', '数学', '初中', '初三', 2, '平行四边形的对角线互相平分。', '["对", "错"]', '对', '平行四边形对角线性质。', '内置题库', 'MANUAL', 1, 1),
    ('SHORT_ANSWER', '数学', '初中', '初三', 2, '简述勾股定理的内容。', NULL, '直角三角形两条直角边的平方和等于斜边的平方，即 a² + b² = c²。', '勾股定理。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '数学', '高中', '高一', 2, '函数 y = cos x 的最小正周期是？', '["A. π/2", "B. π", "C. 2π", "D. 4π"]', 'C', '余弦函数周期为 2π。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '数学', '高中', '高一', 2, 'log₂ 8 = ______', NULL, 3, '因为 2³ = 8。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '数学', '高中', '高二', 2, '等差数列 2, 5, 8, 11, … 的第 5 项是 ______', NULL, 14, '公差为 3，第 5 项 = 2 + 4×3 = 14。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '数学', '高中', '高三', 3, '若 f(x) = x²，则 f′(x) = ?', '["A. 2x", "B. x", "C. x²/2", "D. 2"]', 'A', '幂函数求导 (xⁿ)′ = n·xⁿ⁻¹。', '内置题库', 'MANUAL', 1, 1);

-- ---------- 英语 ----------
INSERT IGNORE INTO edu_question (question_type, question_subject, question_grade_level, question_grade, question_difficulty, question_stem, question_options, question_answer, question_analysis, question_source, question_source_type, question_audit_status, question_status) VALUES
    ('FILL', '英语', '小学', '三年级', 1, '“book”的中文意思是 ______。', NULL, '书', '基础词汇。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '英语', '小学', '三年级', 1, '下列哪个单词表示颜色？', '["A. red", "B. desk", "C. cat", "D. run"]', 'A', 'red 红色；其余分别是书桌、猫、跑。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '英语', '小学', '四年级', 1, '下列哪个单词表示“香蕉”？', '["A. apple", "B. banana", "C. orange", "D. pear"]', 'B', 'banana 香蕉。', '内置题库', 'MANUAL', 1, 1),
    ('JUDGE', '英语', '小学', '五年级', 1, '“cat”的复数是“cats”。', '["对", "错"]', '对', '一般名词加 -s 构成复数。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '英语', '小学', '六年级', 1, 'I ______ (be) a student. 用 be 动词的适当形式填空。', NULL, 'am', '主语 I 用 am。', '内置题库', 'MANUAL', 1, 1),
    ('JUDGE', '英语', '初中', '初一', 1, '“child”的复数是“childs”。', '["对", "错"]', '错', 'child 的复数是 children，属不规则变化。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '英语', '初中', '初一', 1, 'She ______ to school every day.', '["A. go", "B. goes", "C. going", "D. gone"]', 'B', '一般现在时，第三人称单数用 goes。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '英语', '初中', '初二', 1, 'The comparative form of “big” is ______.', NULL, 'bigger', '重读闭音节双写末尾辅音字母再加 -er。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '英语', '初中', '初二', 2, '“important”的名词形式是 ______。', NULL, 'importance', '形容词加 -ance 构成名词。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '英语', '初中', '初三', 2, 'I ______ my homework already.', '["A. finish", "B. finished", "C. have finished", "D. finishing"]', 'C', 'already 常与现在完成时连用。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '英语', '高中', '高一', 2, 'The past participle of “write” is ______.', NULL, 'written', 'write-wrote-written。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '英语', '高中', '高一', 2, '______ hard, and you will succeed.', '["A. Work", "B. Working", "C. To work", "D. Worked"]', 'A', '祈使句 + and + 陈述句结构。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '英语', '高中', '高二', 3, 'If I ______ you, I would accept the offer.', '["A. am", "B. was", "C. were", "D. be"]', 'C', '虚拟语气中 be 动词统一用 were。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '英语', '高中', '高三', 3, 'He suggested ______ (go) for a walk. 用括号内动词的适当形式填空。', NULL, 'going', 'suggest 后接动名词。', '内置题库', 'MANUAL', 1, 1);

-- ---------- 物理 ----------
INSERT IGNORE INTO edu_question (question_type, question_subject, question_grade_level, question_grade, question_difficulty, question_stem, question_options, question_answer, question_analysis, question_source, question_source_type, question_audit_status, question_status) VALUES
    ('JUDGE', '物理', '初中', '初二', 1, '声音在真空中不能传播。', '["对", "错"]', '对', '声音传播需要介质。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '物理', '初中', '初二', 1, '下列物体中属于导体的是？', '["A. 橡胶", "B. 铜", "C. 塑料", "D. 玻璃"]', 'B', '金属是导体，其余为绝缘体。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '物理', '初中', '初二', 1, '下列现象中属于光的折射的是？', '["A. 水中的筷子看起来变弯", "B. 平面镜成像", "C. 影子", "D. 日食"]', 'A', '光从水中斜射入空气发生折射。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '物理', '初中', '初三', 1, '我国家庭电路的电压是 ______ 伏。', NULL, 220, '家庭电路标准电压 220V。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '物理', '初中', '初三', 1, '电流的单位是 ______。', NULL, '安培', '国际单位制中电流单位为安培（A）。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '物理', '初中', '初三', 1, '电阻的单位是？', '["A. 安培", "B. 伏特", "C. 欧姆", "D. 瓦特"]', 'C', '电阻单位为欧姆（Ω）。', '内置题库', 'MANUAL', 1, 1),
    ('JUDGE', '物理', '高中', '高一', 2, '物体做匀速直线运动时，所受合外力为零。', '["对", "错"]', '对', '牛顿第一定律。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '物理', '高中', '高一', 2, '计算中重力加速度 g 通常取 ______ m/s²。', NULL, 9.8, '若取近似值则为 10 m/s²。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '物理', '高中', '高二', 2, '下列属于国际单位制基本单位的是？', '["A. 牛顿", "B. 千克", "C. 焦耳", "D. 瓦特"]', 'B', '力、功、功率都是导出单位。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '物理', '高中', '高二', 2, '波长、频率与波速的关系式为 v = ______。', NULL, 'λf', '波速 = 波长 × 频率。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '物理', '高中', '高三', 3, '楞次定律用于判定？', '["A. 感应电流的方向", "B. 电场强度", "C. 安培力大小", "D. 万有引力大小"]', 'A', '楞次定律：感应电流的磁场总要阻碍引起它的磁通量变化。', '内置题库', 'MANUAL', 1, 1);

-- ---------- 化学 ----------
INSERT IGNORE INTO edu_question (question_type, question_subject, question_grade_level, question_grade, question_difficulty, question_stem, question_options, question_answer, question_analysis, question_source, question_source_type, question_audit_status, question_status) VALUES
    ('FILL', '化学', '初中', '初三', 1, '水的化学式是 ______。', NULL, 'H2O', '由两个氢原子和一个氧原子构成。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '化学', '初中', '初三', 1, '空气中体积分数最大的气体是？', '["A. 氧气", "B. 氮气", "C. 二氧化碳", "D. 稀有气体"]', 'B', '氮气约占空气体积的 78%。', '内置题库', 'MANUAL', 1, 1),
    ('JUDGE', '化学', '初中', '初三', 1, '铁生锈属于物理变化。', '["对", "错"]', '错', '铁生锈生成新物质氧化铁，属化学变化。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '化学', '初中', '初三', 1, '二氧化碳的化学式是 ______。', NULL, 'CO2', '一个碳原子与两个氧原子构成。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '化学', '初中', '初三', 1, '下列物质中属于酸的是？', '["A. 盐酸", "B. 氢氧化钠", "C. 氯化钠", "D. 碳酸钠"]', 'A', '盐酸是酸；氢氧化钠是碱；氯化钠、碳酸钠是盐。', '内置题库', 'MANUAL', 1, 1),
    ('JUDGE', '化学', '初中', '初三', 2, '溶液一定是无色透明的。', '["对", "错"]', '错', '如硫酸铜溶液呈蓝色。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '化学', '初中', '初三', 1, '铁的元素符号是 ______。', NULL, 'Fe', '取自拉丁文 Ferrum。', '内置题库', 'MANUAL', 1, 1),
    ('JUDGE', '化学', '高中', '高一', 2, '同一周期从左到右，元素的原子半径逐渐减小。', '["对", "错"]', '对', '核电荷数增大，对外层电子吸引增强。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '化学', '高中', '高一', 2, '钠元素的原子序数是 ______。', NULL, 11, '钠 Na，原子序数 11。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '化学', '高中', '高一', 2, '下列化合物中属于共价化合物的是？', '["A. NaCl", "B. H2O", "C. MgO", "D. KCl"]', 'B', '水由共价键构成；其余三者是离子化合物。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '化学', '高中', '高二', 3, '下列反应属于氧化还原反应的是？', '["A. 盐酸与氢氧化钠中和", "B. 铁与硫酸铜溶液反应", "C. 氯化钠溶于水", "D. 碳酸钙高温分解生成氧化钙和二氧化碳"]', 'B', '铁与硫酸铜反应中铁、铜元素化合价发生变化。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '化学', '高中', '高二', 2, '摩尔质量的单位是 ______。', NULL, 'g/mol', '单位物质的量的物质所具有的质量。', '内置题库', 'MANUAL', 1, 1);

-- ---------- 生物 ----------
INSERT IGNORE INTO edu_question (question_type, question_subject, question_grade_level, question_grade, question_difficulty, question_stem, question_options, question_answer, question_analysis, question_source, question_source_type, question_audit_status, question_status) VALUES
    ('JUDGE', '生物', '初中', '初一', 1, '细胞是生物体结构和功能的基本单位。', '["对", "错"]', '对', '细胞学说基本观点。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '生物', '初中', '初一', 1, '绿色植物通过 ______ 作用制造有机物并释放氧气。', NULL, '光合', '光合作用在叶绿体中进行。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '生物', '初中', '初一', 1, '人体内最大的消化腺是？', '["A. 肝脏", "B. 胰腺", "C. 胃", "D. 唾液腺"]', 'A', '肝脏分泌胆汁，是最大的消化腺。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '生物', '初中', '初一', 1, '生物分类的最基本单位是 ______。', NULL, '种', '界、门、纲、目、科、属、种，种是最基本单位。', '内置题库', 'MANUAL', 1, 1),
    ('JUDGE', '生物', '初中', '初二', 1, '正常人体细胞中染色体有 23 对。', '["对", "错"]', '对', '其中 22 对常染色体，1 对性染色体。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '生物', '初中', '初二', 1, '血液中主要负责运输氧气的是 ______。', NULL, '红细胞', '红细胞内的血红蛋白与氧结合运输氧气。', '内置题库', 'MANUAL', 1, 1),
    ('JUDGE', '生物', '高中', '高一', 2, '线粒体被称为细胞的“动力车间”。', '["对", "错"]', '对', '线粒体是有氧呼吸的主要场所。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '生物', '高中', '高一', 2, 'ATP 的中文名称是 ______。', NULL, '三磷酸腺苷', 'ATP 是细胞的直接能源物质。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '生物', '高中', '高一', 2, '光合作用释放的氧气来自？', '["A. 二氧化碳", "B. 水", "C. 葡萄糖", "D. 叶绿素"]', 'B', '光反应阶段水被分解产生氧气。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '生物', '高中', '高二', 2, '生物体内遗传信息的携带者是？', '["A. 蛋白质", "B. DNA", "C. 糖类", "D. 脂肪"]', 'B', 'DNA 是主要的遗传物质。', '内置题库', 'MANUAL', 1, 1);

-- ---------- 历史 ----------
INSERT IGNORE INTO edu_question (question_type, question_subject, question_grade_level, question_grade, question_difficulty, question_stem, question_options, question_answer, question_analysis, question_source, question_source_type, question_audit_status, question_status) VALUES
    ('SINGLE_CHOICE', '历史', '初中', '初一', 1, '中国历史上第一个统一的中央集权制封建王朝是？', '["A. 夏朝", "B. 秦朝", "C. 汉朝", "D. 商朝"]', 'B', '公元前 221 年秦始皇统一六国，建立秦朝。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '历史', '初中', '初一', 1, '秦始皇统一六国的时间是公元前 ______ 年。', NULL, 221, '公元前 221 年。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '历史', '初中', '初一', 2, '商朝的甲骨文主要刻写在 ______ 上。', NULL, '龟甲和兽骨', '甲骨文因刻于龟甲、兽骨而得名。', '内置题库', 'MANUAL', 1, 1),
    ('JUDGE', '历史', '初中', '初二', 1, '《史记》的作者是司马迁。', '["对", "错"]', '对', '《史记》是我国第一部纪传体通史。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '历史', '初中', '初二', 1, '唐朝的开国皇帝是？', '["A. 李世民", "B. 李渊", "C. 武则天", "D. 李隆基"]', 'B', '李渊建立唐朝，李世民是唐太宗。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '历史', '初中', '初二', 2, '丝绸之路开通于哪个朝代？', '["A. 秦朝", "B. 汉朝", "C. 唐朝", "D. 宋朝"]', 'B', '西汉张骞通西域后开辟。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '历史', '初中', '初三', 1, '1919 年爆发的反帝反封建爱国运动是 ______ 运动。', NULL, '五四', '五四运动是中国新民主主义革命的开端。', '内置题库', 'MANUAL', 1, 1),
    ('JUDGE', '历史', '初中', '初三', 1, '中华人民共和国成立于 1949 年 10 月 1 日。', '["对", "错"]', '对', '开国大典在天安门广场举行。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '历史', '高中', '高一', 2, '鸦片战争爆发于？', '["A. 1839 年", "B. 1840 年", "C. 1842 年", "D. 1856 年"]', 'B', '1840 年英国发动鸦片战争。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '历史', '高中', '高一', 2, '中国近代史上第一个不平等条约是《______ 条约》。', NULL, '南京', '1842 年签订《南京条约》。', '内置题库', 'MANUAL', 1, 1);

-- ---------- 地理 ----------
INSERT IGNORE INTO edu_question (question_type, question_subject, question_grade_level, question_grade, question_difficulty, question_stem, question_options, question_answer, question_analysis, question_source, question_source_type, question_audit_status, question_status) VALUES
    ('FILL', '地理', '初中', '初一', 1, '世界上面积最大的大洋是 ______ 洋。', NULL, '太平', '太平洋面积约占地球海洋面积的一半。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '地理', '初中', '初一', 1, '中华人民共和国的首都是？', '["A. 上海", "B. 北京", "C. 广州", "D. 天津"]', 'B', '首都为北京。', '内置题库', 'MANUAL', 1, 1),
    ('JUDGE', '地理', '初中', '初一', 1, '地球自转产生昼夜交替现象。', '["对", "错"]', '对', '自转周期约 24 小时。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '地理', '初中', '初一', 1, '赤道的纬度是 ______ 度。', NULL, 0, '赤道为 0° 纬线。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '地理', '初中', '初二', 2, '长江发源于下列哪座山脉？', '["A. 唐古拉山", "B. 昆仑山", "C. 天山", "D. 喜马拉雅山"]', 'A', '长江发源于青藏高原唐古拉山脉。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '地理', '初中', '初二', 1, '我国面积最大的岛屿是 ______ 岛。', NULL, '台湾', '台湾岛是我国第一大岛。', '内置题库', 'MANUAL', 1, 1),
    ('JUDGE', '地理', '初中', '初二', 1, '我国地势总体特征是西高东低。', '["对", "错"]', '对', '呈三级阶梯状分布。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '地理', '初中', '初三', 1, '世界上海拔最高的山峰是？', '["A. 珠穆朗玛峰", "B. 乔戈里峰", "C. 阿空加瓜山", "D. 麦金利山"]', 'A', '珠穆朗玛峰海拔 8848.86 米。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '地理', '高中', '高一', 2, '季风气候形成的主要原因是？', '["A. 海陆热力性质差异", "B. 洋流影响", "C. 地形起伏", "D. 纬度位置"]', 'A', '海陆热力性质差异导致风向随季节变化。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '地理', '高中', '高一', 1, '地球公转一周的时间约为 ______ 天。', NULL, 365, '即一年，约 365 天。', '内置题库', 'MANUAL', 1, 1);

-- ---------- 道德与法治 ----------
INSERT IGNORE INTO edu_question (question_type, question_subject, question_grade_level, question_grade, question_difficulty, question_stem, question_options, question_answer, question_analysis, question_source, question_source_type, question_audit_status, question_status) VALUES
    ('JUDGE', '道德与法治', '初中', '初一', 1, '法律面前人人平等。', '["对", "错"]', '对', '法律面前一律平等是法治基本原则。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '道德与法治', '初中', '初一', 1, '我国的根本大法是？', '["A. 宪法", "B. 刑法", "C. 民法典", "D. 教育法"]', 'A', '宪法是国家的根本法，具有最高法律效力。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '道德与法治', '初中', '初二', 2, '我国的根本政治制度是 ______ 制度。', NULL, '人民代表大会', '人民代表大会制度是我国的根本政治制度。', '内置题库', 'MANUAL', 1, 1),
    ('JUDGE', '道德与法治', '初中', '初一', 1, '公民的权利和义务是统一的。', '["对", "错"]', '对', '权利与义务相互依存、不可分割。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '道德与法治', '初中', '初二', 1, '我国的国庆节是每年 ______ 月 1 日。', NULL, 10, '1949 年 10 月 1 日开国大典。', '内置题库', 'MANUAL', 1, 1),
    ('FILL', '道德与法治', '初中', '初一', 2, '依法 ______ 是公民的基本义务之一。', NULL, '纳税', '税收取之于民、用之于民。', '内置题库', 'MANUAL', 1, 1),
    ('JUDGE', '道德与法治', '高中', '高一', 2, '物质决定意识，意识是物质的反映。', '["对", "错"]', '对', '辩证唯物主义基本观点。', '内置题库', 'MANUAL', 1, 1),
    ('SINGLE_CHOICE', '道德与法治', '高中', '高一', 3, '“实践是检验真理的唯一标准”体现的是？', '["A. 辩证唯物主义认识论", "B. 唯心主义先验论", "C. 形而上学", "D. 不可知论"]', 'A', '实践是认识的来源、动力和检验标准。', '内置题库', 'MANUAL', 1, 1);


-- =====================================================================
-- 题库种子合计：12（上方「内置示例」，source_type=MANUAL）
--             + 106（上方「内置题库」，覆盖 9 学科 · 小学/初中/高中）
-- 均为可核对的标准教材知识点，全新安装后题库即可直接使用。
-- INSERT IGNORE 保证幂等，重复执行不会主键冲突。
-- =====================================================================

-- 成语库及学段映射种子（归位自 V1.8/V1.9/V1.11）。
-- initdata：学段/年级映射（幂等）
-- ----------------------------
INSERT INTO edu_idiom_grade (idiom_id, grade_level, grade)
SELECT i.idiom_id, '小学', '一年级' FROM edu_idiom i
WHERE i.idiom_content IN ('亡羊补牢','守株待兔','画蛇添足','掩耳盗铃','井底之蛙','拔苗助长','刻舟求剑','自相矛盾','三心二意','手不释卷','专心致志','聚沙成塔','百发百中','锲而不舍','悬梁刺股','闻鸡起舞','卧薪尝胆','一鼓作气','不耻下问','温故知新','名副其实','眼花缭乱','栩栩如生','念念不忘')
  AND NOT EXISTS (SELECT 1 FROM edu_idiom_grade g WHERE g.idiom_id = i.idiom_id AND g.grade_level = '小学');

INSERT INTO edu_idiom_grade (idiom_id, grade_level, grade)
SELECT i.idiom_id, '初中', '初一' FROM edu_idiom i
WHERE i.idiom_content IN ('循序渐进','融会贯通','举一反三','触类旁通','一丝不苟','精益求精','心无旁骛','孜孜不倦')
  AND NOT EXISTS (SELECT 1 FROM edu_idiom_grade g WHERE g.idiom_id = i.idiom_id AND g.grade_level = '初中');

-- 成语初始数据（常用成语 + 三方词库/API 采集样本）
-- 幂等：逐条 WHERE NOT EXISTS 去重；学段映射按 edu_idiom_grade 实际数据生成

INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '守株待兔','shǒu zhū dài tù','比喻死守狭隘经验，不知变通','','','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '守株待兔');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '亡羊补牢','wáng yáng bǔ láo','羊丢失后才修补羊圈；比喻出了问题及时补救，还不算晚。','《战国策·楚策四》','这次失误要吸取教训，亡羊补牢，为时未晚。','','',1,'寓言,学习' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '亡羊补牢');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '画蛇添足','huà shé tiān zú','画蛇时给蛇添上脚；比喻做了多余的事，反而把事情弄糟。','《战国策·齐策二》','文章结尾再添一段就成了画蛇添足。','','',1,'寓言,写作' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '画蛇添足');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '掩耳盗铃','yǎn ěr dào líng','捂住耳朵偷铃铛；比喻自己欺骗自己，明明掩盖不住。','《吕氏春秋·自知》','犯了错误要改正，掩耳盗铃只会更糟。','','',1,'寓言,品行' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '掩耳盗铃');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '井底之蛙','jǐng dǐ zhī wā','井底的青蛙；比喻见识狭窄、眼界不开阔的人。','《庄子·秋水》','多读书多旅行，才不会成为井底之蛙。','','',2,'寓言,见识' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '井底之蛙');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔苗助长','bá miáo zhù zhǎng','把禾苗拔高帮它生长；比喻违反事物规律，急于求成。','《孟子·公孙丑上》','学习要循序渐进，不能拔苗助长。','','',2,'寓言,学习' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔苗助长');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '刻舟求剑','kè zhōu qiú jiàn','在船上刻记号找剑；比喻拘泥成例，不知随情况变化。','《吕氏春秋·察今》','方法要随条件变化，刻舟求剑行不通。','','',2,'寓言,思维' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '刻舟求剑');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '自相矛盾','zì xiāng máo dùn','卖矛又卖盾，前后说法互相抵触。','《韩非子·难一》','他的说法自相矛盾，经不起推敲。','','',2,'寓言,逻辑' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '自相矛盾');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '三心二意','sān xīn èr yì','形容犹豫不决或意志不专一。','元·关汉卿《救风尘》','做事要专心，不能三心二意。','','',1,'品行,学习' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '三心二意');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '手不释卷','shǒu bù shì juàn','书本不离手；形容勤奋好学。','《三国志·吴书·吕蒙传》','他手不释卷，成绩自然优异。','','',2,'学习,励志' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '手不释卷');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '专心致志','zhuān xīn zhì zhì','一心一意，集中精神。','《孟子·告子上》','上课要专心致志，才能听懂要点。','','',1,'学习,品行' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '专心致志');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '聚沙成塔','jù shā chéng tǎ','把沙子聚起来堆成塔；比喻积少成多。','《妙法莲华经·方便品》','每天背五个单词，聚沙成塔。','','',2,'励志,学习' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '聚沙成塔');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百发百中','bǎi fā bǎi zhòng','每次射箭都命中目标；形容本领高强，也比喻料事准确。','《战国策·西周策》','他投篮百发百中，是校队主力。','','',2,'励志,技能' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百发百中');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '锲而不舍','qiè ér bù shě','不停地雕刻；比喻有恒心、有毅力。','《荀子·劝学》','只要锲而不舍，难题总能攻克。','','',3,'励志,学习' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '锲而不舍');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '悬梁刺股','xuán liáng cì gǔ','形容学习刻苦勤奋到极点。','《太平御览》','古人有悬梁刺股的精神，今天我们更要努力。','','',4,'学习,励志' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '悬梁刺股');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '闻鸡起舞','wén jī qǐ wǔ','听到鸡叫就起床舞剑；比喻有志者及时奋发。','《晋书·祖逖传》','他闻鸡起舞，坚持晨读。','','',3,'励志,品行' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '闻鸡起舞');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '卧薪尝胆','wò xīn cháng dǎn','睡柴草尝苦胆；形容刻苦自励，发愤图强。','《史记·越王勾践世家》','球队卧薪尝胆，终于夺冠。','','',4,'励志,历史' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '卧薪尝胆');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '一鼓作气','yī gǔ zuò qì','第一次击鼓时士气最盛；比喻趁劲头足时一口气完成。','《左传·庄公十年》','趁状态好一鼓作气把作业写完。','','',3,'励志,方法' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '一鼓作气');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '不耻下问','bù chǐ xià wèn','不以向地位低或学问不如自己的人请教为耻。','《论语·公冶长》','不懂就查、不会就问，不耻下问是美德。','','',3,'品行,学习' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '不耻下问');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '温故知新','wēn gù zhī xīn','温习旧知识能得到新体会。','《论语·为政》','复习时温故知新，常有新发现。','','',2,'学习,方法' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '温故知新');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '名副其实','míng fù qí shí','名称或名声与实际相符合。','《后汉书·黄琼传》','他被称为“学霸”，确实名副其实。','','',3,'品行,评价' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '名副其实');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '眼花缭乱','yǎn huā liáo luàn','眼睛看见复杂纷繁的东西而感到迷乱。','《红楼梦》','展厅里的作品令人眼花缭乱。','','',3,'描写,感受' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '眼花缭乱');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '栩栩如生','xǔ xǔ rú shēng','形容艺术形象非常逼真，像活的一样。','《庄子·齐物论》','这幅画上的骏马栩栩如生。','','',3,'描写,艺术' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '栩栩如生');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '念念不忘','niàn niàn bù wàng','反复思念，牢记于心。','《朱子全书》','老师的叮嘱让我念念不忘。','','',2,'情感,描写' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '念念不忘');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '循序渐进','xún xù jiàn jìn','按照一定次序、步骤逐步推进。','《论语·宪问》','学习要循序渐进，不可急于求成。','','',2,'学习,方法' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '循序渐进');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '融会贯通','róng huì guàn tōng','把各方面知识融合起来，得到全面透彻的理解。','《朱子全书》','把零散知识融会贯通，才能灵活运用。','','',4,'学习,方法' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '融会贯通');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '举一反三','jǔ yī fǎn sān','从一件事类推出许多同类的事。','《论语·述而》','会举一反三，才算真正理解。','','',3,'学习,思维' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '举一反三');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '触类旁通','chù lèi páng tōng','掌握了某一事物的规律，就能推知同类事物。','《周易·系辞上》','学通方法后触类旁通，效率倍增。','','',4,'学习,思维' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '触类旁通');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '一丝不苟','yī sī bù gǒu','形容办事认真，连最细微的地方也不马虎。','《儒林外史》','实验操作要一丝不苟。','','',3,'品行,态度' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '一丝不苟');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '精益求精','jīng yì qiú jīng','已经很好，还要求更好。','《论语·学而》','他对作品精益求精，反复打磨。','','',3,'品行,态度' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '精益求精');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '心无旁骛','xīn wú páng wù','心中没有另外的追求；形容专心致志。','《孟子·告子上》','复习时心无旁骛，效率最高。','','',3,'学习,品行' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '心无旁骛');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '孜孜不倦','zī zī bù juàn','勤奋努力，不知疲倦。','《尚书·君陈》','他孜孜不倦地钻研难题。','','',3,'学习,励志' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '孜孜不倦');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿鼻地狱','ā bí dì yù','阿鼻梵语的译音，意译为无间”，即痛苦无有间断之意。常用来比喻黑暗的社会和严酷的牢狱。又比喻无法摆脱的极其痛苦的境地。','语出《法华经·法师功德品》下至阿鼻地狱。”','但也有少数意志薄弱的……逐步上当，终至堕入～。★《上饶集中营·炼狱杂记》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿鼻地狱');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿党比周','ē dǎng bǐ zhōu','指相互勾结，相互偏袒，结党营私。','三国·魏·曹操《整齐风俗令》阿党比周，先圣所疾也。”','《论语·卫灵公》众恶之，必察焉；众好之，必察焉”何晏集解引三国魏王肃曰或众～，或其人特立不群，故好恶不可不察也。”','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿党比周');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿党相为','ē dǎng xiāng wéi','阿党偏袒、偏私一方。为了谋求私利相互偏袒、包庇。','《汉书·诸葛丰传》今以四海之大，曾无伏节死谊之臣，率尽苟合取容，阿党相为，念私门之利，忘国家之政。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿党相为');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿狗阿猫','ā gǒu ā māo','旧时人们常用的小名。引申为任何轻贱的，不值得重视的人或著作。','鲁迅《我们要批评家》然而新的批评家不开口，类似批评家之流便趁势一笔抹杀‘阿狗阿猫’。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿狗阿猫');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿姑阿翁','ā gū ā wēng','阿名词的前缀。姑丈夫的母亲。翁丈夫的父亲。指公公婆婆。','见阿家阿翁”。','既然如此，你我两个，便学个不痴不聋的～。★《儿女英雄传》二三回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿姑阿翁');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿家阿翁','ā gū ā wēng','阿名词的前缀。家通姑”，丈夫的母亲。翁丈夫的父亲。指公公婆婆。','唐·赵璾《因话录》卷一郭暖尝与升平公主琴瑟不调。尚父拘暖，自诣朝童结罪。上召而慰之曰‘谚云不痴不聋，不作阿家阿翁。’”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿家阿翁');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿娇金屋','ā jiāo jīn wū','阿娇指汉武帝刘彻的姑母（长公主刘娇）的女儿。原指汉武帝刘彻要用金屋接纳阿娇为妇。这里泛指美丽高贵的女子。','语出旧题汉·班固《汉武故事》汉武帝幼时曾对姑母长公主说若得阿娇(姑母之女)作妇，当以金屋贮之。”','～原相配，太其玉镜宁无时？★明·周履靖《锦笺记·诒婚》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿娇金屋');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿猫阿狗','ā māo ā gǒu','旧时人们常用的小名。引申为任何轻贱的，不值得重视的人或著作。','鲁迅《我的第一个师父》这和名孩子为阿猫阿狗，完全是一样的意思容易养大。”','在德国无论～见面，一律互相举手高喊希特勒万岁”。★邹韬奋《法西斯作风的罪恶》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿猫阿狗');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿弥陀佛','ē mí tuó fó','佛教语，信佛的人用作口头诵颂的佛号，表示祈祷祝福或感谢神灵的意思。','元·张国宾《合汗衫》第四折张寿友曰‘阿弥陀佛，这个是谁？’卜儿云‘这便是媳妇儿。’”','和尚念经，～。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿弥陀佛');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿毗地狱','ā pí dì yù','阿毗梵语的译音，意译为无间”，即痛苦无有间断之意。常用来比喻黑暗的社会和严酷的牢狱。又比喻无法摆脱的极其痛苦的境地。','见阿鼻地狱”。','生前不曾修福，死堕～。★《敦煌变文集·妙法莲花经讲经文》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿毗地狱');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿平绝倒','ā píng jué dǎo','以之比喻对对方的言论极为佩服。亦用为讥讽言论极为乖谬，常贻笑大方。','据《晋书·卫玠传》及《王澄传》载玠好言玄理。琅玡王澄字平子，兄昵称之曰阿平”。有高名，少所推许，每闻玠言，辄叹息绝倒。故时为之语曰卫玠谈道，平子绝倒。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿平绝倒');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿其所好','ē qí suǒ hào','阿曲从；其他的；好爱好。指为取得某人的好感而迎合他的爱好。','《孟子·公孙丑上》宰我、子贡、有若，智足以知圣人，污不至阿其所好。”','吾何能～为？★鲁迅《坟·摩罗诗力说》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿其所好');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿时趋俗','ē shí qū sú','阿迎合。迎合时尚和世俗。','恽代英《再复毕几庵书》故办杂志者，当研究社会心理。然此决非阿时趋俗，只以顺其势而利导之耳。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿时趋俗');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿世盗名','ē shì dào míng','阿世曲意博取世人的欢心。用博取世人的欢心的方法来窃取名誉。','清·梁启超《新民说》才智之士，既得此以为阿世盗名之一秘钥，于是名节闲检，荡然无所顾复。','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿世盗名');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿世媚俗','ē shì mèi sú','指曲意迎合当时社会的陈规陋俗。','鲁迅《坟·文化偏至论》如其《民敌》一书，谓有人宝守真理，不阿世媚俗，而不见容于人群。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿世媚俗');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿世取容','ē shì qǔ róng','指迎合世俗，取悦于人。','鲁迅《汉文学史纲要》第六篇至叔孙通，则正以曲学容，非重其能定朝仪，知典礼也。”','叙述西汉儒学，应该看到多数～的章名小儒，也应该看到少数同情人民的正统儒者。★范文澜蔡美彪等《中国通史》第二编第二章第九节','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿世取容');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿意取容','ē yì qǔ róng','指曲从其意，以取悦于人。','《南史·孝义传下·陶季直》季直不能阿意取容，明帝颇忌之，出为辅国长史、北海太守。”','当英景之间，国势初更，人心观望，执政任事之臣多～。★《明史·高穣胡濙等赞》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿意取容');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿谀谄媚','ē yú chǎn mèi','阿谀迎合别人的意思，向人讨好；谄媚巴结，奉承。说话做事迎合别人的心意，竭力向人讨好。','明·冯梦龙《喻世明言·裴晋公义还原配》只是这帮阿谀谄媚的，要博相国欢喜，自然重价购买。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿谀谄媚');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿谀逢迎','ē yú féng yíng','阿谀用言语恭维别人；逢迎迎合别人的心意。奉承，拍马，讨好别人。','《二程全书·伊川易传一》周之与否在君而已，不可阿谀奉迎求其比己也。”','其时京师有一流棍，名叫李光，专一～，谄事令孜。★明·凌濛初《初刻拍案惊奇》卷二十二','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿谀逢迎');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿谀奉承','ē yú fèng chéng','阿谀用言语恭维别人；奉承恭维，讨好。曲从拍马，迎合别人，竭力向人讨好。','明·东鲁古狂生《醉醒石》第八回他却小器易盈，况且是个小人，在人前不过一味阿谀奉承。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿谀奉承');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阿谀取容','ē yú qǔ róng','阿谀曲意逢迎；取容取悦于人。谄媚他人，以取得其喜悦。','汉·杨秉《奏劾侯览》而今猥受过宠，执政操权，其阿谀取容者，则因公褎举，以报私惠。”','且王髈等～，蔽贤窃位。★明·冯梦龙《东周列国志》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阿谀取容');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '哀哀父母','āi āi fù mǔ','可哀呀可哀，我的父母啊！原指古时在暴政下的人民终年在外服劳役，对父母病痛、老死不能照料而悲哀。','《诗经·小雅·蓼莪》蓼蓼者莪，匪莪伊蒿，哀哀父母，生我劬劳。”','咱人有子方知不孝娘，岂不问～情肠！★元·无名氏《小张屠》第一折','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '哀哀父母');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '哀哀欲绝','āi āi yù jué','哀哀极度悲哀；绝断气，死。形容极其悲痛。','清·曹雪芹《红楼梦》第十三回那宝珠按未嫁女之礼在灵前哀哀欲绝。”','那安太太进门，一眼便看见姑娘，～的跪在那里……便一直的奔了姑娘去。★清·文康《儿女英雄传》第二十回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '哀哀欲绝');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '哀兵必胜','āi bīng bì shèng','原意是力量相当的两军对阵，悲愤的一方获得胜利◇指因受欺侮而奋起抵抗的军队，必定能取胜。','《老子》第六十九章祸莫大于轻敌，轻敌几丧吾宝，故抗兵相加，哀者胜矣。”','不，～！不要乐，要哀，何小姐能弹《易水吟》的谱子吗？★张恨水《啼笑因缘续集》第十回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '哀兵必胜');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '哀而不伤','āi ér bù shāng','哀悲哀；伤伤害。忧愁而不悲伤，形容感情有节制；另形容诗歌、音乐优美雅致，感情适度。比喻做事没有过头也无不及。','《论语·八佾》子曰‘《关雎》乐而不淫，哀而不伤。’”','她喜欢打扮，愿意有朋友，可是这都不过是一些小小的、～的、青春的游戏。★老舍《四世同堂》四十三','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '哀而不伤');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '哀感天地','āi gǎn tiān dì','形容极其哀痛，使天地都为之感动。','宋·释普济《五灯会元》卷一王闻失声号恸，哀感天地。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '哀感天地');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '哀感顽艳','āi gǎn wán yàn','顽愚顽。原意是指内容凄切，文辞华丽，使愚笨和聪慧的人同样受感动◇多用来指艳情的小说、戏曲、电影中的感人情节。','三国·魏·繁钦《与魏文帝笺》咏北狄之遐征，奏胡马之长嘶，凄入肝脾，哀感顽艳。”','这封信却是骈四骈六的，足有三千多字，写得异常的～。★清·吴趼人《二十年目睹之怪现状》第七十一回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '哀感顽艳');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '哀感中年','āi gǎn zhōng nián','形容人到中年对亲友离别的伤感情绪。','南朝·宋·刘义庆《世说新语·言语》谢太傅（安）语王右军（王羲之）曰‘中年伤于哀乐，与亲友别，辄作数是恶。’”','神丹浪说解还童，～遇谢公。★清·丘逢甲《东北感春诗》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '哀感中年');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '哀告宾服','āi gào bīn fú','宾服古指诸侯或边远部落按时朝贡，表示依服、服从◇泛指归顺、服从。指哀求饶恕，愿意服从。','元·无名氏《延安府》第一折我见他慌悚踌躇，左支右吾，跪在街衢，哀告宾服。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '哀告宾服');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '哀鸿遍地','āi hóng biàn dì','哀鸿哀鸣的鸿雁。比喻啼饥号寒的灾民。比喻在天灾人祸中到处都是流离失所、呻吟呼号的饥民。','《诗经·小雅·鸿雁》鸿雁于飞，哀鸣嗷嗷。”','天涯吟望，～，都成愁侣。★黄侃《水龙吟·秋花》词','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '哀鸿遍地');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '哀鸿遍野','āi hóng biàn yě','哀鸿哀鸣的鸿雁。比喻啼饥号寒的灾民。比喻在天灾人祸中到处都是流离失所、呻吟呼号的饥民。','《诗经·小雅·鸿雁》鸿雁于飞，哀鸣嗷嗷。”','我记得历史上的罗马何等殷阗繁盛，怎么今日却是～，春燕无归，满眼凄惶。★清·梁启超《新罗马》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '哀鸿遍野');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '哀毁骨立','āi huǐ gǔ lì','哀悲哀；毁损坏身体；骨立形容极瘦，只剩下骨架。旧时形容在父母丧中因过度悲伤而瘦得只剩一把骨头。','《后汉书·韦彪传》孝行纯至，父母卒，哀毁三年，不出庐寝。服竟，羸瘠骨立异形，医疗数年乃起。”南朝宋·刘义庆《世说新语·德行》王戎虽不备礼，而哀毁骨立。”','众亲戚已到，商量在本族亲房立了一个儿子过来，然后大殓治丧。蘧公子～，极倦子之谊。★清·吴敬梓《儒林外史》第十二回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '哀毁骨立');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '哀毁瘠立','āi huǐ jī lì','旧时形容在父母丧中因过度悲伤而瘦得只剩一把骨头。','清·吴敬梓《儒林外史》第十二回众亲戚已到，商量在本族亲房立了一个儿子过来，然后大殓治丧。蘧公子哀毁瘠立，极倦子之谊。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '哀毁瘠立');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '哀梨蒸食','āi lí zhēng shí','将哀梨蒸着吃。比喻不识货↓里糊涂地糟堂东西。','南朝·宋·刘义庆《世说新语·轻诋》桓南郡每见人不快，辄嗔曰‘君得哀家梨，当复不蒸食不?’”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '哀梨蒸食');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '哀莫大于心死','āi mò dà yú xīn sǐ','心死指心像死灰的灰烬。指最可悲哀的事，莫过于思想顽钝，麻木不仁。','《庄子·田子方》夫哀莫大于心死，而人死亦次之。”','人说～。”我的心已经死了。我不该再到你们公馆里来，打扰你们。★巴金《家》二回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '哀莫大于心死');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '哀声叹气','āi shēng tàn qì','因伤感郁闷或悲痛而发出叹息的声音。','明·凌濛初《二刻拍案惊奇》卷三十八终日价没心没想，哀声叹气。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '哀声叹气');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '哀丝豪竹','āi sī háo zhú','丝指弦乐器；竹指管乐器；豪竹粗大的竹管制成的乐器。形容管弦乐声的悲壮动人。','唐·杜甫《醉为马坠诸公携酒相看》诗酒肉如山又一时，初筵哀丝动豪竹。”','～颂英烈，此是北昆跃进花。★茅盾《观北昆剧院初演〈红霞〉》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '哀丝豪竹');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '哀思如潮','āi sī rú cháo','哀伤的思绪如同潮涌一般。形容极度悲痛。','京剧《平原作战》第五场赵勇刚（唱）哀思如潮热泪洒，平川有边仇无涯。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '哀思如潮');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '哀天叫地','āi tiān jiào dì','哀悲哀。悲哀地呼天喊地。形容悲痛至极。','清·曹雪芹《红楼梦》第二十五回平儿丰儿等哭的哀天叫地，贾政心中也着了忙。”','平儿丰儿等哭的～，贾政心中也着了忙。★清·曹雪芹《红楼梦》第二十五回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '哀天叫地');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '哀痛欲绝','āi tòng yù jué','伤心得要死。形容悲痛到了极点。','清·曹雪芹《红楼梦》第十三回那宝珠按未嫁女之礼在灵前哀哀欲绝。”','宝庆给大哥唱了一曲挽歌，直唱得泣不成声，～。★老舍《鼓书艺人》十七','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '哀痛欲绝');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '唉声叹气','āi shēng tàn qì','因伤感郁闷或悲痛而发出叹息的声音。','明·凌濛初《二刻拍案惊奇》卷三十八终日价没心没想，哀声叹气。”','我看你脸上一团私欲愁闷气色！这会子又～，你那些还不足，还不自在？★清·曹雪芹《红楼梦》第三十三回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '唉声叹气');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '挨冻受饿','ái dòng shòu è','指无衣无食的穷困境况。','清·曹雪芹《红楼梦》第一百回妈妈这一辈子想来还不至挨冻受饿。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '挨冻受饿');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '挨风缉缝','āi fēng jī fèng','比喻为了达到某种目的而找门路。','明·冯梦龙《醒世恒言》卷七但有一二分才貌的，那一个不挨风缉缝，央媒说合。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '挨风缉缝');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '挨家挨户','āi ji āi hù','每家每户，户户不漏。挨，依次，顺次。','老舍《四世同堂》七四自从他作了副县长，随着白巡长挨家挨户地收铜铁，他的美誉便降落了许多。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '挨家挨户');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '挨肩并足','āi jiān bìng zú','形容人群拥挤。','明·冯梦龙《醒世恒言·钱秀才错占凤凰俦》那山中远近人家，都晓得高家新女婿才貌双全，竟来观看，挨肩并足，如看神会故事的一般热闹。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '挨肩并足');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '挨肩擦膀','āi jiān cā bǎng','指身体相贴近。也形容人群拥挤。','元·刘君锡《来生债》第一折你怎么偏要挨肩擦膀的，舒着手往我怀里摸甚么？”','那两边围看的，～，不知其数。★明·兰陵笑笑生《金瓶梅词话》第四二回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '挨肩擦膀');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '挨肩擦背','āi jiān cā bèi','肩挨肩，背擦背。形容人多拥挤。','明·冯梦龙《醒世恒言》第三十四卷常常走到厨房下，挨肩擦背，调嘴弄舌。”','引惹得那楼下看灯的人，～，仰望上瞧。★明·兰陵笑笑生《金瓶梅词话》第十五回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '挨肩擦背');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '挨肩擦脸','āi jiān cā liǎn','挨靠近。擦，接触。形容狎昵之状。','《脂砚斋重评石头记》六五回贾珍便和三姐挨肩擦脸，百般轻薄起来。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '挨肩擦脸');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '挨肩搭背','āi jiān dā bèi','挨肩肩和肩相靠。搭背手搭在别人背上。形容极其亲昵的样子。','《官场维新记》第九回小玉凤见是警察局的大人到了，连忙抛了各客，溜进房间里来，与袁伯珍挨肩搭背的坐在一块儿。”','看她俩挨肩搭背的样子，就知道是一对好朋友。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '挨肩搭背');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '挨肩迭背','āi jiān dié bèi','形容人群拥挤。','明·冯梦龙《醒世恒言·勘皮靴单证二郎神》当日看的是挨肩迭背。','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '挨肩迭背');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '挨肩叠背','āi jiān dié bèi','形容人群拥挤。','《醒世恒言·勘皮靴单证二郎神》当日看的真是挨户叠背。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '挨肩叠背');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '挨门挨户','āi mén āi hù','挨按照顺序。按照住户的顺序一家也不漏。同挨门逐户”。','李英儒《野火春风斗古城》第六章二她心生一计，挨门挨户问人家要不要水。”','她［一丈青大娘］又跑遍沿河几个村落，～乞讨零碎布头儿，给何满子缝了一件五光十色的百家衣。★刘绍棠《蒲柳人家》一','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '挨门挨户');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '挨门逐户','āi mén zhú hù','挨家挨户，一家也没有漏掉。','清·钱彩《说岳全传》第六十一回写出岳元帅父子受屈情节，挨门逐户的分派；约齐日子，共上民表，要替岳爷申冤。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '挨门逐户');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '挨三顶五','āi sān dǐng wǔ','形容人多，连接不断。','明·冯梦龙《平妖传》众人挨三顶四，簇拥将来，一个个伸出手来，求太医看脉。”','但见红尘滚滚，车马纷纷，许多商贩客人驮着货物，～的进店安歇。★明·冯梦龙《古今小说》卷五','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '挨三顶五');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '挨山塞海','āi shān sè hǎi','形容人极多而拥挤不堪。','明·凌濛初《初刻拍案惊奇》卷十七那日观看的人，何止挨山塞海。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '挨山塞海');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '捱风缉缝','āi fēng qī fèng','指多方钻营，找门路。','《醒世恒言·卢太学诗酒傲公侯》别个秀才要去结交知县，还要捱风缉缝，央人引进，拜在门下，认为老师。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '捱风缉缝');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '捱三顶四','āi sān dǐng sì','形容人群拥挤，接连不断。','《平妖传》第四回半仙到栅栏门首下马，也不进宅，径在堂中站着，众人捱三顶四，簇拥将来，一个个伸出手来，求太医看脉。”','覆帐之后，宾客如市，捱三顶五，不得空闲。★《醒世恒言·卖油郎独占花魁》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '捱三顶四');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '捱三顶五','āi sān dǐng wǔ','形容人群拥挤，接连不断。','明·冯梦龙《醒世恒言·卖油郎独占花魁》覆帐之后，宾客如市，捱三顶五，不得空闲。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '捱三顶五');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '嗳声叹气','ài shēng tàn qì','因伤感、烦闷或痛苦而发出叹息的声音。','无','刁迈彭一面让他起，一面故意做出～的样子，说‘这是怎么好！这是怎么好！叫我怎么对得起死的大哥！’★李宝嘉《官场现形记》第五十一回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '嗳声叹气');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '矮人观场','ǎi rén guān cháng','比喻只知道附和别人，自己没有主见。也比喻见识不广。','清·王士秅《香祖笔记》卷十予观宋景文近体，无一字无来历，而对仗精确，非读万卷者不能，迥非南渡以后所及。今人耳食，誉者毁者，皆矮人观场，未之或知也。”','最有识见之客，亦作～。★清·李渔《闲情偶寄·演习·选剧》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '矮人观场');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '矮人看场','ǎi rén kàn chǎng','比喻只知道附和别人，自己没有主见。也比喻见识不广。','《朱子语类》卷二十七正如矮人看戏一般，见前面人笑，他也笑，他虽眼不曾见，想必是好笑，便随他笑。”','是真是假，要弄个明白，不能～，人云亦云。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '矮人看场');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '矮人看戏','ǎi rén kàn xì','比喻只知道附和别人，自己没有主见。也比喻见识不广。','清·赵翼《论诗》诗矮人看戏何曾见，都是随人说短长。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '矮人看戏');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '矮子观场','ǎi zǐ guān cháng','比喻只知道附和别人，自己没有主见。也比喻见识不广。','明·徐复祚《一文钱》第三出全心即佛，全佛即人，直是饥人见饭；心不是佛，智不是道，何殊矮子观场。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '矮子观场');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '矮子看戏','ǎi zǐ kàn xì','比喻只知道附和别人，自己没有主见。也比喻见识不广。','《朱子语类》卷二十七正如矮人看戏一般，见前面人笑，他也笑，他虽眼不曾见，想必是好笑，便随他笑。”','～何曾见，都是随人说短长。★清·赵翼《诗论》诗','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '矮子看戏');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '蔼然可亲','ǎi rán kě qīn','蔼然对人和善的样子。形容态度和气，使人愿意接近。','明·李开先《贺邑令贺洪滨奖异序》迄今才八阅月，绝从生之文法，除苛细之科条，虽若凛不可犯，而实蔼然可亲。”','早有那般世谊同年，见他翩翩风度，～，都愿和他亲近进来了。★清·文康《儿女英雄传》第三十八回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '蔼然可亲');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '蔼然仁者','ǎi rán rén zhě','对人和善的有仁德的人。','唐·韩愈《答李翊书》仁义之人，其言蔼如也。”','子恺为他家华瞻写的文章，真是‘～’之言。★朱自清《儿女》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '蔼然仁者');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱别离苦','ài bié lí kǔ','佛家语，指亲爱的人离别的痛苦。','《瑜伽师地论》卷六十一云何爱别离苦？当知此苦，亦由五相。”宋·释道原《景德传灯录》卷二十三问‘如何是至极之谈？’曰‘爱别离苦。’”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱别离苦');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱博而情不专','ài bó ér qíng bù zhuān','对人或事物的喜爱很广泛，而感情不能专一。','唐·韩愈《与陈给事书》夫位益尊则贱者日隔，伺候于门墙者日益进，则爱博而情不专。”','在这三条路里，我将选择那一条呢？我惭愧自己是个‘～’的人；虽者想着只选定一条路，却总丢不下别的。★朱自清《那里走》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱博而情不专');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱不忍释','ài bù rěn shì','释放下。对所喜欢的物品，爱得拿在手里久久不肯放下。','南朝·梁·萧统《陶渊明集序》余爱嗜其文，不能释手。”','得见钱唐陈小云司马《香畹楼忆语》钞本一种，～，亟问假归，手录展玩。★清·王维鋆《香畹楼忆语序一》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱不忍释');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱不释手','ài bù shì shǒu','释放下。喜爱得舍不得放手。','南朝·梁·萧统《陶渊明集序》余爱嗜其文，不能释手。”','虽是不合他的路数，可奈文有定评，他看了也知道～。★清·文康《儿女英雄传》第三十五回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱不释手');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱才如渴','ài cái rú kě','才贤才，人才。爱慕贤才，急欲求得，就象口渴急于喝水一样。','清·叶燮《原诗·外篇上》嫉恶甚严，爱才若渴，此韩愈之面目也。”','宫保～，兄弟实在钦佩的。★清·刘鹗《老残游记》六回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱才如渴');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱才如命','ài cái rú mìng','才贤才，人才。爱慕贤才，急欲求得，就象生命一样重要。','语出清·叶燮《原诗·外篇上》嫉恶甚严，爱才若渴，此韩愈之面目也。”','本帅～，何必过谦？★《说岳全传》第三一回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱才如命');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱才若渴','ài cái ruò kě','才贤才，人才。爱慕贤才，急欲求得，就象口渴急于喝水一样。','清·叶燮《原诗·外篇上》嫉恶甚严，爱才若渴，此韩愈之面目也。”','他们发现了佳人并非因为‘～’而做婊子的，佳人只为的是钱。★鲁迅《上海文艺之一瞥》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱才若渴');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱财如命','ài cái rú mìng','把钱财看得跟生命一样重要。形容极端吝啬。','清·岭南羽衣女士《东欧女豪杰》第四回我想近来世界，不管什么英雄，什么豪杰，都是爱财如命，何况吃官司的，少不免要和那些狼官鼠吏交手，这更没钱不行了。”','那个人极端自私，～。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱财如命');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱富嫌贫','ài fù xián pín','嫌弃贫穷，喜爱富有。指对人的好恶以其贫富为准。','元·关汉卿《裴度还带》第二折有那等嫌贫爱富的儿曹辈，将俺这贫傲慢、把他那富追陪，那个肯恤孤念寡存仁义。”','方信道贫居闹市无人问，富在深山有远亲。如今人～。★明·无名氏《贫富兴衰》第二折','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱富嫌贫');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱国如家','ài guó rú jiā','指帝王像爱抚自己的家室一样爱国爱民。','汉·荀悦《汉纪·惠帝纪》封建诸侯各世其位，欲使亲民如子、爱国如家。”《晋书·刘聪传》臣闻古之圣王爱国如家，故皇天亦祐之如子。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱国如家');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱鹤失众','ài hè shī zhòng','比喻因小失大。','《左传·闵公二年》狄人伐魏，卫懿公好鹤，鹤有乘轩者。将战，国人受甲者皆曰‘使鹤，鹤有禄位，余焉能战？’”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱鹤失众');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱礼存羊','ài lǐ cún yáng','由于爱惜古礼，不忍使它废弛，因而保留古礼所需要的祭羊。比喻为维护根本而保留有关仪节。','《论语·八佾》子贡欲去告朔之饩羊，子曰‘赐也，尔爱其羊，我爱其礼。’”','本朝监学之法，虽参以天下之士，而于国子加厚矣。盖～，以有待也。★宋·陈亮《国子》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱礼存羊');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱毛反裘','ài máo fǎn qiú','反裘反穿皮衣，指皮毛朝里。古时穿皮毛衣服，毛的一面向外。比喻不重视根本，轻重倒置。','汉·刘向《新序·杂事》魏文侯出游，见路人反裘而负刍，文侯曰‘胡为反裘而负刍？’对曰‘臣爱其毛。’文侯曰‘若不知其里尽而毛无所恃邪？’”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱毛反裘');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱民如子','ài mín rú zǐ','旧时称赞某些统治者爱护百姓，就象爱护自己的子女一样。','《礼记·中庸》子，庶民也。”汉·刘向《新序·杂事一》良君将赏善而除民患，爱民如子，盖之如天，容之若地。”','一路地方官员馈送礼物，岳爷始终不受……只是吩咐他们学好做官，须要爱民如子，无负朝廷。★清·钱彩《说岳全传》第四八回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱民如子');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱莫能助','ài mò néng zhù','爱爱惜；莫不。虽然心中关切同情，却没有力量帮助。','《诗经·大雅·焌民》维仲山甫举之，爱莫助之。”','收到各处的回信，只是写些～”之类的抱歉话。★叶圣陶《得失》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱莫能助');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱莫之助','ài mò zhī zhù','爱爱惜；莫不。虽然心中关切同情，却没有力量帮助。','《诗经·大雅·焌民》维仲山甫举之，爱莫助之。”','晚虽家事不如初，而亲戚故旧之急难，族人子弟之美事，～，每致其倦倦之意，而人人常信之。★宋·陈亮《喻夏卿墓志铭》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱莫之助');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱钱如命','ài qián rú mìng','某些人贪爱钱财就像吝啬生命一样。形容十分贪婪、刻薄。','明·谢谠《四喜记·大宋毕姻》既称月老，又号冰人，爱钱如命，说谎能神，自家高媒婆是也。”','1942年我在成都重见她的时候，她已经成了一个～的可笑的胖女人。★巴金《谈》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱钱如命');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱人好士','ài rén hào shì','爱护、重视人才。','《三国志·蜀书·先主传》圣姿硕茂，神武在躬，仁覆积德，爱人好士，是以四方归心焉。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱人好士');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱人利物','ài rén lì wù','爱护人民，力求有利于人民。','《庄子·天地》爱人利物之谓仁。”','而终归于禁暴戢乱、～而不可掩者，其本领宏大开阔故也。★宋·陈亮《又乙巳春书之一（答朱熹）》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱人利物');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱人以德','ài rén yǐ dé','爱人爱护别人；德德行。按照道德标准去爱护人。泛指对人不偏私偏爱，不姑息迁就。','《礼记·檀弓上》君子之爱人也以德，细人之爱人也以姑息。”','轼闻琦语，曰公可谓～矣。”★《宋史·苏轼传》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱人以德');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱日惜力','ài rì xī lì','珍惜时间，不虚掷精力。','《三国志·吴志·孙和传》且志土爱日惜力 ，君子慕其大道，高山景行，耻非其次。”《周书·萧圆萧传》朗读百边，乙夜难寐，爱日惜力，寸阴无弃。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱日惜力');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱如己出','ài rú jǐ chū','像对待亲生子女那样地爱护。','明·周辑《西湖二集·假邻女诞生真子》果然方氏爱如己出，抚养成人长大。”','她出世半岁，就丧了父亲，一岁半时，就随母来到邓家。母亲自然是爱的，后父也～。★沈从文《死水微澜》第二部分','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱如己出');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱如珍宝','ài rú zhēn bǎo','形容十分喜欢。','明·天然痴叟《石点头·侯官县烈女歼仇》申屠娘子，已是身怀六甲，到得十月满足……生个儿子，爱如珍宝。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱如珍宝');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱生恶死','ài shēng wù sǐ','恶厌恶。喜爱生存，厌恶死亡。','清·李汝珍《镜花缘》第二十六回可见爱生恶死，亦是物之恒情。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱生恶死');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱素好古','ài sù hào gǔ','指爱好朴质，不趋时尚。','《老子》见素抱朴，少私寡欲。”《论语·述而》子曰述而不作，信而好古，窃比于我老彭。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱素好古');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱屋及乌','ài wū jí wū','因为爱一个人而连带爱他屋上的乌鸦。比喻爱一个人而连带地关心到与他有关的人或物。','《尚书大传·大战》爱人者，兼其屋上之乌。”','子存宠上了小老婆，未免～”，把他也看得同上客一般。★清·吴趼人《二十年目睹之怪现状》第十四回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱屋及乌');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱惜羽毛','ài xī yǔ máo','羽毛比喻人的声望。比喻为珍惜自己的名声，行事十分谨慎。','汉·刘向《说苑·杂言》夫君子爱口，孔雀爱羽，虎豹爱爪，此皆所以治身法也。”','灰鹤与他都是高傲的，～的，而他与它们的高傲只是一种姿态而已，没有用，没有任何的用。★老舍《四世同堂》五十','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱惜羽毛');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱则加诸膝，恶则坠诸渊','ài zé jiā zhū xī，wù zé zhuì zhū yuān','加诸膝放在膝盖上；坠诸渊推进深渊里。意指不讲原则，感情用事，对别人的爱憎态度，全凭自己的好恶来决定。','《礼记·檀弓下》今之君子，进入若将加诸膝，退人若将坠诸渊。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱则加诸膝，恶则坠诸渊');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱憎分明','ài zēng fēn míng','憎恨。爱和恨的立场和态度十分鲜明。','陈若曦《耿尔在北京》他很困惑，也很痛苦，想不到爱憎分明、坚强独立的小睛会怕人‘闲话’。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱憎分明');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱之欲其生','ài zhī yù qí shēng','指喜欢一个人时总想叫他活着。指极度地凭个人爱憎对待人。','春秋·孔丘《论语·颜渊》爱之欲其生，恶之欲其死，既欲其生，又欲其死，是惑也。”','‘～，恶之欲其死。’卿何不容一宦官耶？★明·罗贯中《三国演义》第115回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱之欲其生');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '爱之欲其生，恶之欲其死','ài zhī yù qí shēng，wù zhī','喜爱他时，总想叫他活着；讨厌他时，总想叫他死掉。指极度地凭个人爱憎对待人。','《论语·颜渊》爱之欲其生，恶之欲其死，既欲其生，又欲其死，是惑也。”','爱之欲其生，恶之欲其死。”卿何不容一宦官耶？（明·罗贯中《三国演义》第一百一十五回）','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '爱之欲其生，恶之欲其死');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '碍口识羞','ài kǒu shí xiū','碍口说不出口。指怕羞而不说话。','明·凌濛初《初刻拍案惊奇·转运汉巧遇洞庭红》文若虚终是碍口识羞，待说又止。”','这事关系你终身，肯与不肯，明白说出，莫要～，两相耽误。★《石点头·潘文子契合鸳鸯冢》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '碍口识羞');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '碍难从命','ài nán cóng mìng','碍妨碍；从听从。由于有所妨碍，难以遵从吩咐，是委婉拒绝对方要求的话。','《三打祝家庄》第一幕第一场你回去复李大官人，本当将时迁放回，怎奈他是梁山贼党，碍难从命。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '碍难从命');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '碍手碍脚','ài shǒu ài jiǎo','碍阻碍。妨碍别人做事。','明·凌濛初《初刻拍案惊奇》第三十二卷后边有些嫌忌起来，碍手碍脚，到底不妙。”','宝钗因说道咱们别在这里～。”说着，和宝玉等便往迎春房中来。★清·曹雪芹《红楼梦》第十八回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '碍手碍脚');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗室逢灯','àn shì féng dēng','比喻在危难或困惑中，忽然遇人援救或指点引导。','《野叟曝言》十回天幸遇著相公，如暗室逢灯，绝渡逢舟，从此读书作文，俱可望有门径矣!”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗室逢灯');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗室亏心','àn shì kuī xīn','在暗中做见不得人的亏心事。','元·张养浩《折桂令》曲暗室亏心，纵然致富，天意何如。”','我想来今生不报，来世偿还，正所谓～，神目如电。★明·范受益《寻亲记·托梦》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗室亏心');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗室欺心','àn shì qī xīn','在黑暗的屋子里昧着良心做坏事。指偷偷地做坏事。','宋·陈元靓《事林广记·卷九·警世格言》人间私语，天闻若雷，暗室欺心，神目如电。”','方信道暗室亏心，难逃他神目如电。★《元曲选·无名氏三》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗室欺心');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗室求物','àn shì qiú wù','在没有光亮的房间寻找东西。比喻读书不得要领，难见成效。','《朱子语类辑略·总论为学之方》要紧只是看大底道理分明，遍处自寻得。如暗室求物，把为来便照见；若只管去摸索，……”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗室求物');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗室私心','àn shì sī xīn','指在暗中做见不得人的亏心事。同暗室亏心”。','明·陆采《明珠记·却婚》丈夫然诺重如山，暗室私心鬼神见。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗室私心');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗室屋漏','àn shì wū lòu','指别人看不见的地方；隐私之室。','宋·张世南《游宦纪闻》卷四至于发人隐恶，虽亏雅道，亦使暗室屋漏之下有所警是亦小道之可观者。”','瓜田李下，明侠女之志；～，窥君子之心。★《好逑传》第六回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗室屋漏');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗送秋波','àn sòng qiū bō','旧时比喻美女的眼睛象秋天明净的水波一样。指暗中眉目传情。','宋·苏轼《百步洪》诗之二佳人未肯回秋波，幼舆欲语防飞梭。”','对张（作霖）则～，对曹（锟）尤密切勾结。★蔡东藩、许廑父《民国通俗演义》第一二四回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗送秋波');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗无天日','àn wú tiān rì','昏暗得看不到天上的日光。形容在反动势力统治下社会的黑暗。','清·蒲松龄《聊斋志异·老龙船户》剖腹沉石，惨冤已甚，而木雕之有司，绝不少关痛痒，岂特粤东之暗无天日哉！”','上自王后，下至弄臣，骇得凝结着的神色也应声活动起来，似乎感到～的悲哀。★鲁迅《故事新编·铸剑》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗无天日');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗香疏影','àn xiāng shū yǐng','暗香清幽的香气；疏影稀疏的枝影。原形容梅花的香味和姿态，后被用为梅花的代称。','宋·林逋《林和靖集·卷二·山园小梅》诗疏影横斜水清浅，暗香浮动月黄昏”','两个站在小桥上，疏影暗香，自甘清冷，屋后山冈积雪如银，背着手玩赏。★清·陈忱《水浒后传》第十四回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗香疏影');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗中摸索','àn zhōng mō suǒ','摸索寻求，探索。在黑暗中寻找◇比喻没有人指导，独自钻研。','唐·刘餸《隋唐嘉话》许敬宗性轻傲，见人多忘之。或谓其不聪。曰‘卿自难记，若遇何（何逊）、刘（刘孝绰）、沈（沈约）、谢（谢眺），暗中摸索者亦可识之。’”','先生有志于木刻，是极好的事，但访木刻家是无益的，因为就是已有成绩的木刻家，也还在～。★《鲁迅书信集·致沈振黄》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗中摸索');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '黯淡无光','àn dàn wú guāng','黯淡同暗淡”，不明亮，昏暗。形容失去光彩。','清·文康《儿女英雄传》第三十四回头上戴一个黯淡无光的亮蓝顶儿，那枝俏摆春风的孔雀瓴已经虫蛀的剩了光杆儿了。”','但是这个电报却又引起了张勋的不愉快，因为，他的十三省盟主地位因此而～。★陶菊隐《北洋军阀统治时期史话》第二十四章','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '黯淡无光');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '黯晦消沉','àn huì xiāo chén','指湮没消失。','蒋智由《序》凡有绝大之战争，往往赖有雄伟之文，淋漓之诗歌，而后其印象日留于国民心目之间。否则，不数年间而黯晦消沉以尽。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '黯晦消沉');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '黯然伤神','àn rán shāng shén','沮丧的样子。情绪低沉，心神忧伤。','梁·江淹《别赋》黯然销魂者，惟别而已矣。”','篝灯询母，如秋蝶～。★清·淮阳百一居士《壶天录》上卷','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '黯然伤神');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '黯然神伤','àn rán shén shāng','指心神悲沮的样子。','清·百一居士《壶天录》女更黯然神伤，泫然流涕。”','这个满脸胡须的中年人，扫头看见了船尾妇妇，～的目光突然发亮了。★从维煕《遗落在海滩上的脚印》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '黯然神伤');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '黯然失色','àn rán shī sè','黯然心里不舒服、情绪低落的样子；失色因惊恐而变以脸色。本指心怀不好，脸色难看◇多比喻相形之下很有差距，远远不如。','南朝·梁·江淹《别赋》黯然销魂者，惟别而已矣。”李善注黯然，失色貌”','所有同类题材的绘画和它比较起来，都～。★秦牧《艺海拾贝·〈最后的晚餐〉》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '黯然失色');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '黯然无色','àn rán wú sè','指事物失去原有光彩，变得暗淡无光。','清·郑燮《题画竹》昔东坡居士作枯木竹石，使有枯木石而无竹，则黯然无色矣。”','阳光孤零零地落在街心上，也觉～。★邓宁《红色故乡随笔》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '黯然无色');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '黯然销魂','àn rán xiāo hún','黯然心怀沮丧、面色难看的样子；销魂灵魂离开肉体。心怀沮丧得好象丢了魂似的。形容非常悲伤或愁苦。','南朝·梁·江淹《别赋》黯然销魂者，惟别而已矣。”','无言独上西楼”，深闺的空寥，弥漫在漠漠的冥色里，离情别绪，～！★郭枫《且饮一杯寂寞》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '黯然销魂');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '昂昂自若','áng áng zì roò','昂昂气概昂扬，大模大样；自若象平常一样。形容无所顾虑，从容自如。','明·冯梦龙《古今小说·晏平仲二桃杀三士》三士带剑立于殿下，昂昂自若。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '昂昂自若');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '昂藏七尺','áng zàng qī chǐ','昂藏登记表雄伟、气度不凡的样子；七尺七尺高的身躯。指轩昂伟岸的男子汉。','清·赵翼《放歌》徒负昂藏七尺身，实只太仓一稊米。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '昂藏七尺');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '昂然直入','áng rán zhí rù','昂首挺胸地径直走进去。形容傲然威武的样子。','明·罗贯中《三国演义》第三回何进昂然直入。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '昂然直入');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '昂然自得','áng rán zì dé','自得自己感到舒适。指大模大样，满不在乎的样子。','宋·欧阳修《与高司谏书》今乃不然，反昂然自得，了无愧畏。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '昂然自得');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '昂然自若','áng rán zì ruò','形容气宇轩昂满不在乎的样子。','蔡东藩《民国通俗演义》第31回昂然自若，并不畏缩。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '昂然自若');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '昂首阔步','áng shǒu kuò bù','昂仰，高抬。抬起头迈开大步向前。形容精神抖擞，意气风发。','老舍《鼓书艺人》二十四新郎昂首阔步，在他身边迈着鸭子步，为的是显摆他那马靴和银马刺。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '昂首阔步');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '昂首伸眉','áng shǒu shēn méi','伸扬。抬头扬眉。形容意气昂扬的样子。','汉·司马迁《报任少卿书》乃欲仰首伸眉，论列是非，不亦轻朝廷，羞当世之士耶！”','他们获得了比赛的胜利，～地回来了。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '昂首伸眉');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '昂首天外','áng shǒu tiān wài','抬起头望着天边。形容态度傲慢，或做事脱离实际。','清·归庄《王大痴像赞》昂其首，睅其目，举觞而望青天者，是为王大痴。”','某些人做事不从实际出发，往往～。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '昂首天外');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '昂首挺胸','áng shǒu tǐng xiōng','抬起头，挺起胸膛。形容斗志高，士气旺。','欧阳予情《小英姑娘》她伸开两手昂首挺胸，狂了似的往外跑。”','个个视死如归，坚贞不屈，～站在那里。王之燕同志领头高呼起口号。★《上饶集中营·浩气长存》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '昂首挺胸');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '昂首望天','áng shǒu wàng tiān','仰起头，望着天。喻指眼光向上，作风浮泛，不肯放下架子、深入基层。','清·归庄《王大痴像赞》昂其首，睅其目，举觞而望青天者，是为王大痴。”','要做这件事，第一是眼睛向下，不要只是～。★毛泽东《〈农村调查〉的序言和跋》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '昂首望天');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '昂头阔步','áng tóu kuò bù','昂仰，高抬。抬起头迈开大步向前。形容精神备发或态度傲慢。参见昂首阔步”。','方志敏《可爱的中国》他们昂头阔步，带着一种藐视中国人不屑与中国人为伍的神气，总引起我心里的愤愤不平。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '昂头阔步');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '昂头天外','áng tóu tiān wài','昂抬起。抬起头望着天边。形容态度傲慢或做事脱离实际。','鲁迅《书信集·致萧军、萧红》他们昂头天外，评论之后，不知那里去了。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '昂头天外');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '昂头挺胸','áng tóu tǐng xiōng','抬起头，挺起胸膛。形容斗志高，士气旺。参见昂首挺胸”。','语出欧阳予情《小英姑娘》她伸开两手昂首挺胸，狂了似的往外跑。”','你看！提到内战，就连妇人女子都反对啦……”～，老老师欣喜地叫出来。★沙汀《范老老师》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '昂头挺胸');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '昂霄耸壑','áng xiāo sǒng hè','昂高；壑峪。高出霄汉，耸立山壑。形容才能杰出，建树宏大功业。也形容志气高昂，胸怀广阔。','宋·陆游《剑南诗稿·凌霄花》卷三十四古来豪杰少人知，昂霄耸壑宁自期。”','昔余识夫人之子稚年，固已得其～之气。★宋·陈亮《祭叶正则母夫人文》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '昂霄耸壑');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '盎盂相击','àng yú xiāng jī','比喻一家人争吵。','清·文康《儿女英雄传》第三十一回只就他夫妻三个这番外面情形讲，此后自然该益发合成一片性情，加上几分伉俪，把午间那番盎盂相击化得水乳无痕。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '盎盂相击');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '盎盂相敲','àng yú xiāng qiāo','比喻一家人争吵。','清·蒲松龄《聊斋志异·卷十一·青蛙神》且盎盂相敲，皆臣所为，无所涉于父母。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '盎盂相敲');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '敖不可长','ào bù kě zhǎng','敖通傲”，傲慢。傲慢之心不可以滋长。','西汉·戴圣《礼记·曲礼上》敖不可长，欲不可从，志不可满，乐不可极。”','我们要教育他～的道理。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '敖不可长');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '敖世轻物','áo shì qīng wù','敖，通傲”。鄙弃世俗，看不起别人。也指高傲自大。','《淮南子·齐俗训》敖世轻物，不污于俗。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '敖世轻物');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '嗷嗷待哺','áo áo dài bǔ','嗷嗷哀鸣声；待等待；哺喂食。饥饿时急于求食的样子。形容受饥饿的悲惨情景。','《诗经·小雅·鸿雁》鸿雁于飞，哀鸣嗷嗷。”汉·荀悦《前汉纪·成帝纪三》作治数年，天下遍被其劳，国家疲弊，府库空虚，下至众庶，嗷嗷苦之。”','文章尽可在别的刊物上发表，对于～的创造社的几种刊物却一字也不肯飞来。★郭沫若《学生时代·创造十年续编》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '嗷嗷待哺');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '遨翔自得','áo xiáng zì dé','指自由自在的样子。','无','天空中那只老鹰～，真让人羡慕。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '遨翔自得');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '熬肠刮肚','áo cháng guā dù','指从事饮食方面尽力节俭。','陈登科《活人塘》三咱薛家祖上是锅碗一担挑到新河集的，落了户，靠两只手苦，可怜，熬肠刮肚的买了这几亩田。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '熬肠刮肚');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '熬更守夜','áo gēng shǒu yè','指熬夜。','《人民文学》1977年1第一期总理经常熬更守夜，废寝忘食地工作。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '熬更守夜');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '熬姜呷醋','áo jiāng xiā cù','比喻生活清苦。','清·李渔《怜香伴·毡集》下官知从选了这个穷教官，坐了这条冷板凳，终日熬姜呷醋，尚不能色问舍求田，哪里再经得进口添人。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '熬姜呷醋');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '熬枯受淡','áo kū shòu dàn','指忍受清苦的生活。','元·关汉卿《望江亭》第一折这出家无过草衣木食，熬枯受淡。”','[杜子春]渐渐卖了马骑驴，卖给了驴步走，～，度过日子。★《醒世恒言·杜子春三人长安》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '熬枯受淡');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '熬清守淡','áo qīng shǒu dàn','指忍受清苦乏味的生活。','《初刻拍案惊奇》卷十五陈某不肖，将家私荡尽，赖我贤妻熬清守淡。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '熬清守淡');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '熬清守谈','áo qīng shǒu tán','忍受清苦乏味的生活。','《初刻拍案惊奇》卷十五陈某不肖，将家么荡尽，赖我贤妻熬清守谈。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '熬清守谈');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '熬清受淡','áo qīng shòu dàn','指忍受清苦乏味的生活。','见熬清守淡”。','老母尚且～，若为人子者，反在房中与妻享用，真所谓儿彘不如了。★《天雨花》第二九回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '熬清受淡');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '熬心费力','áo xīn fèi lì','耗费心神和气力。','马烽《太阳刚刚出山》我熬心费力办农业，结果给组织上留下这么个印象。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '熬心费力');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '熬油费火','áo yóu fèi huǒ','指耗费灯油。多指日夜忙碌。','《红楼梦》第二五回此时贾赦、贾政又恐哭坏了贾母，日夜熬油费火，闹得上下不安。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '熬油费火');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '聱牙佶屈','áo yá jí qū','聱牙不顺口；佶屈曲折。指文章读起来不顺口。','唐·韩愈《进学解》周浩殷盘，佶屈聱牙。”','我今赠君亦无物，但有～村田诗。★元·戴元表《剡源集·寿陈贵白','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '聱牙佶屈');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '聱牙戟口','áo yá jǐ kǒu','形容文词艰涩，拗口难读。','《明史·文苑传三·李攀龙》所拟乐府，或更古数字为己作，文则聱牙戟口，读者至不能终篇。”','而不知只从形式上着眼，就必然走上～的道路。★郭绍虞《中国文学批评史》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '聱牙戟口');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '聱牙诘曲','áo yá jié qū','聱牙不顺口；诘曲曲折。指文章读起来不顺口。','清·郑相如《汉林四传·开明君传》有荐伏生工《尚书》，上诏中大夫晁错至其家，从七岁好口授书文，聱牙诘曲。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '聱牙诘曲');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '聱牙诘屈','áo yá jié qū','聱牙不顺口；诘屈曲折。形容文辞艰涩难读。','清·周亮工《书影》第二卷为诗好为聱牙诘屈之语，多不自解。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '聱牙诘屈');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '謷牙诘屈','áo yá jié qū','同诘屈聱牙”。謷，通聱”。诘屈同佶屈”，曲折，引申为不顺畅；謷牙读起来拗口、别扭。形容文字晦涩艰深，难懂难读。','语出唐·韩愈《进学解》周浩殷盘，佶屈聱牙。”','我今赠君亦无物，但有～村田诗。★元·戴元表《剡源集·寿陈贵白》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '謷牙诘屈');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '鳌愤龙愁','áo fèn lóng chóu','如鼍愤怒，如龙忧愁。比喻乐曲的情调悲愤。','无','这是一首～音乐，你想听吗？','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '鳌愤龙愁');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '鳌里夺尊','áo lǐ duó zūn','犹言出类拔萃。','老舍《骆驼祥子》十四大家给他祝寿，他大模大样的承受，仿佛觉出自己是鳌里夺尊的一位老英雄。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '鳌里夺尊');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '鳌鸣鳖应','áo míng biē yīng','比喻声气相通，互相感应。','无','这两只小动物在一起时间久了，表现得非常默契，严然是～。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '鳌鸣鳖应');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '鳌头独占','áo tóu dú zhàn','比喻占首位或第一名。','元·无名氏《陈州粜米》楔子殿前曾献升平策，独占鳌头第一名。”','我国目前蚕茧总产量已超过了日本，国际蚕丝市场上，我们已～了。★《新华文摘》1987年第5期','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '鳌头独占');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '鳌掷鲸呿','áo zhì jīng qù','比喻文辞气势磅礴，跌宕起伏。同鳌掷鲸吞”。','清·宋荦《漫堂说诗》三至于杜之涵地负，韩之鳌掷鲸呿，尚有所未逮。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '鳌掷鲸呿');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '鳌掷鲸吞','áo zhì jīng tūn','比喻文辞气势磅礴，跌宕起伏。亦作鳌掷鲸呿”。','明·陈汝元《金莲记·偕计》龙浮马负之图，已曾抉奥运会；鳌掷鲸吞之句，无不深研。”','至于杜之涵地负，韩之鳌掷鲸呿，尚有所未逮。★清宋荦《漫堂说诗》三','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '鳌掷鲸吞');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '傲不可长','ào bù kě zhǎng','傲慢之心不可以滋长。','《礼记·曲礼上》傲不可长，欲不可纵，志不可满，乐不可极。”','～，像他那样傲慢的孩子，家长应加强教育。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '傲不可长');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '傲骨嶙嶙','ào gǔ lín lín','傲骨指高傲不屈的性格。嶙嶙山崖突兀貌。比喻高傲不屈。','清·蒲松龄《聊斋志异·叶生》行踪落落，对影长愁；傲骨嶙嶙，搔头自爱。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '傲骨嶙嶙');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '傲慢不逊','ào màn bù xùn','逊谦逊。态度高傲自大，对人不讲谦让。','《汉书·萧望之传》有司奏乐君责使者礼，遇丞相无礼，廉声不闻，敖（傲）不逊。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '傲慢不逊');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '傲慢少礼','ào màn shǎo lǐ','态度傲慢，对人不讲礼节。','《三国志·吴志·三嗣主传》裴松之注引孙皓侍中李仁之言视人君相忤，是乃祀所谓傲慢；傲慢则无礼，无礼则不臣，不臣则犯罪，犯罪则陷不测矣。”','自襄阳赶刘玄德不着，来投韩玄；玄怪其～，不肯重用。★明·罗贯中《三国演义》第五十三回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '傲慢少礼');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '傲慢无礼','ào màn wú lǐ','态度傲慢，对人不讲礼节。','《三国志·吴志·三嗣主传》裴松之注引孙皓侍中李仁之言视人君相忤，是乃祀所谓傲慢；傲慢则无礼，无礼则不臣，不臣则犯罪，犯罪则陷不测矣。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '傲慢无礼');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '傲睨得志','ào nì dé zhì','傲睨傲慢地斜眼看。得志愿望实现。形容因实现愿望而看不起一切的神情。','明·罗贯中《三国演义》第六十回原来曹操自破马超回，傲睨得志，每日饮宴，无事少出，国政皆在相府商议。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '傲睨得志');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '傲睨万物','ào nì wàn wù','睨斜视。斜着眼看一切事物。形容目空一切，什么都瞧不起。','宋·黄庭坚《豫章文集·卷二十六·跋俞秀老清老诗颂》清老往与余共学于涟水，其傲睨万物，滑稽以玩世，白首不哀”。','……益自尊大，宝自有而～。★鲁迅《文化偏至论》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '傲睨万物');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '傲睨一切','ào nì yī qiè','睨斜视。斜着眼看一切事物。形容目空一切，什么都瞧不起。','宋·黄庭坚《豫章文集·卷二十六·跋俞秀老清老诗颂》清老往与余共学于涟水，其傲睨万物，滑稽以玩世，白首不哀”。','更有那放荡不羁，～，偏低首下心，作儿女之态。★清·魏秀仁《花月痕》第一回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '傲睨一切');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '傲睨一世','ào nì yī shì','睨斜视。高傲地旁观，对当代的一切都不看在眼里。形容傲慢自负，目空一切。','宋·朱熹《朱子语类》卷三十四便有傲睨一世，污浊朝廷之意。”宋·高斯得《耻堂存稿·留赵给事奏札》但其再来，遂负虚骄之气，傲睨一世，自以人莫能及。”','辽字睿达，幼挺拔不群，长而好学尚友，～。★《宋史·沈辽传》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '傲睨一世');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '傲睨自若','ào nì zì ruò','睨斜视；自若指满不在乎。形容自高自大、藐视一切的样子。','宋·王谠《唐语林·豪爽》郑太穆郎中为金州刺史，一日忽致书于襄阳于司空頴，其言恳切，而傲睨自若，似无郡僚之礼。”','简雍在城下唤门。（刘）璋令开门接入。雍坐在车中，傲睨自若。★明·罗贯中《三国演义》第六十五回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '傲睨自若');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '傲然挺立','ào rán tǐng lì','傲然坚强不屈的样子。挺立像山峰一样高高地耸立。形容坚强而不可动摇。','茅盾《白杨礼赞》难道你竟一点也不联想到，在敌后的广大土地上，到处有坚强不屈，就象这白杨树一样傲然挺立的守卫他们家乡的哨兵？”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '傲然挺立');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '傲然屹立','ào rán yì lì','形容坚定，不可动摇地站立着。','古华《芙蓉镇》抽茎了，长叶了，铁骨青枝，傲然屹立。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '傲然屹立');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '傲世轻物','ào shì qīng wù','傲世傲视当世；物他人或环境。鄙弃世俗，看不起别人。也指高傲自大。','《淮南子·齐俗训》敖（傲）世轻物，不污于俗。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '傲世轻物');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '傲霜斗雪','ào shuāng dòu xuě','傲视寒霜，抗击白雪。形容不畏严寒。比喻人身处逆境而不屈服。','元·吴昌龄《张天师》第三折玉骨冰肌谁可匹，傲雪欺霜夺第一。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '傲霜斗雪');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '傲头傲脑','ào tóu ào nǎo','形容倔强、桀骜不驯的样子。','《儒林外史》第二七回现今之小厮傲头傲脑，也要娶个辣燥些的媳妇来制着他才好。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '傲头傲脑');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '傲贤慢士','ào xián màn shì','贤有才德的人；士读书人和有才干的人。用傲慢的态度对待有德有才的人。','明·罗贯中《三国演义》第六十回何期逆贼恣逞奸雄，傲贤慢士，故特来见明公。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '傲贤慢士');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '傲雪凌霜','ào xuě líng shuāng','形容不畏霜雪严寒，外界条件越艰苦越有精神。比喻经过长期磨练，面对冷酷迫害或打击毫不示弱、无所畏惧。傲，傲慢、蔑视。','宋·杨无咎《柳梢青》傲雪凌霜，平欺寒力，搀借春光。”','如松柏之性，～。★清·钱泳《履园丛话·谭诗·总论》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '傲雪凌霜');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '傲雪欺霜','ào xuě qī shuāng','形容不畏霜雪严寒，外界条件越艰苦越有精神。比喻经过长期磨练，面对冷酷迫害或打击毫不示弱、无所畏惧。傲，傲慢、蔑视。','元·吴昌龄《张天师》梅花云我这梅花……玉骨冰肌谁可匹，傲雪凌霜夺第一。”','玉骨冰肌谁可匹，～夺第一。★元·吴昌龄《张天师》第三折','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '傲雪欺霜');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '奥妙无穷','ào miào wú qióng','奥深奥。指其中不易为人所知而奇妙有趣之处极多。','孙犁《小说杂谈·谈比》但文章一事，涉及意识形态，奥妙无穷，千变万化，众口纷纭，莫衷一是，要想比出个结果，使观众心服，就不是那么容易的事。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '奥妙无穷');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '奥援有灵','ào yuán yǒu líng','奥援暗中支持的力量；有灵有神通。多指旧时官场的钻营、勾结，暗通关节，很有神通。','明·文秉《先拨志始》卷下皆大慝巨奸，或燕处于园亭，或潜藏于京邸，奥援有灵，朝廷无法。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '奥援有灵');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '懊悔无及','ào huǐ wú jí','后悔已来不及了。','明·罗贯中《三国演义》第九十八回；令人往探之，果是虚营，只插着数十面旌旗，兵已去了二日也。曹真懊悔无及。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '懊悔无及');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八百孤寒','bā bǎi gū hán','八百形容很多；孤寒指贫寒的读书人。形容人数众多，处境贫寒的读书人。也比喻贫寒之士失去依靠。','五代·王定保《唐摭言·好放孤寒》八百孤寒齐下泪，一时南望李崖州。”','小谪陈芳现在身，人间何事敝精神。幔亭仙客休相待，～要此人。★清·黄景仁《两当轩集·十四·岁暮怀人》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八百孤寒');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八拜之交','bā bài zhī jiāo','八拜原指古代世交子弟谒见长辈的礼节；交友谊。旧时朋友结为兄弟的关系。','宋·邵伯温《闻见后录》丰稷谒潞公，公着道出，语之曰汝父吾客也，只八拜。稷不得已，只拜之。”','今日在圣贤炉前成了～，有福同享，有马同骑。★清·李绿园《歧路灯》第二十回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八拜之交');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八窗玲珑','bā chuāng líng lóng','①指四壁窗户轩敞，室内通彻明亮。②比喻通达明澈的修养境界。','唐·卢纶《赋得彭祖楼送杨德宗归徐州幕》诗四户八窗明，玲珑逼上清。”','而今须是要打破那黑底虚静，换做个白底虚静，则～，无不融通。★《朱子语类》卷一百','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八窗玲珑');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八斗之才','bā dǒu zhī cái','才才华。比喻人极有才华。','《南史·谢灵运传》天下才共一石，曹子建独得八斗，我得一斗，自古及今共用一斗。”','乃归来及一月，竟溘然长逝，年仅愈弱冠耳。怀～，饮万斛之恨。★清·梁起超《饮冰室诗话·二四》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八斗之才');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八方风雨','bā fāng fēng yǔ','四面八方风雨聚会。比喻形势骤然变幻，动荡不安。','唐·刘禹锡《郡内书情献裴度侍中留守》万乘旌旗分一半，八方风雨会中央。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八方风雨');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八方呼应','bā fāng hū yìng','呼应彼此声气相通。泛指周围、各地。形容各方面互通声气，互相配合。','余秋雨《寂寞天柱山》三我认为，天柱山之所以能给古人一种居家感，一个比较现实的原因是它地处江淮平原，四相钩连，八方呼应，水陆交通畅达，虽幽深而无登高之苦，虽奇丽而无柴米之匮，总而言之，既宁静又方便。”','中国申办奥运的计划一提出，立即八方呼应，群情激奋。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八方呼应');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八方支持','bā fāng zhī chí','形容各方面都支持、援助。','无','一方有难，～。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八方支持');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八方支援','bā fāng zhī yuán','形容各方面都支持、援助。','无','一方有难，八方支援','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八方支援');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八府巡按','bā fǔ xún àn','巡按之名，起于明代，非固定职官，临时由朝廷委派监察御史担任，分别巡视各省，考核吏治。八府巡按”常见于戏曲、小说，民间多视为清廉而有权势的大官。','无','你如今是～，为父的乃是个皂班头，诚恐砧辱于你，这也无妨紧要，待我回得衙去，写个辞条，将这个皂班头辞掉也就是了。★名家名剧之《胭脂宝褶》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八府巡按');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八公山上，草木皆兵','bā gōng shān shàng，cǎo mù jiē bīng','八公山在安徽淮西市西。将八公山上的草木，都当作是士兵。形容极度惊恐，疑神疑鬼。','《晋书·苻坚载记》坚与苻融登城而望王师，见部阵齐整，将士精锐；又北望八公山上草木皆类人形，顾谓融曰‘此亦劲敌也，何谓少乎？’怃然有惧色。”','～”，是错觉的一例。★毛泽东《论持久战》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八公山上，草木皆兵');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八纮同轨','bā hóng tóng guǐ','指天下一统。八纮，指八方极远之地。','《晋书·武帝纪》廓清梁、岷、包怀扬、越，八纮同轨，祥瑞屡臻。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八纮同轨');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八荒之外','bā huāng zhī wài','八面荒远的地方以外。形容极其旷远。','《列子·仲尼》虽远在八荒之外，近在眉睫之内。来干我者，我必知之。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八荒之外');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八街九陌','bā jiē jiǔ mò','形容城市的街道纵横，市面繁华。','《三辅旧事》长安城中，八街九陌。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八街九陌');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八九不离十','bā jiǔ bù lí shí','指与实际情况很接近。','老舍《柳屯的》可是比较的，我还算是他的熟人，自幼儿的同学。我不敢说是明白他，不过讲猜测的话，我或者能猜个八九不离十。”','不用你说，我一猜就能猜出个八九不离十。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八九不离十');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八面见光','bā miàn jiàn guāng','形容人非常世故，各方面都能应付得很周到。','清·文康《儿女英雄传》第十回张姑娘这几句话，说得软中带硬，八面见光，包罗万象。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八面见光');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八面玲珑','bā miàn líng lóng','玲珑精巧细致，指人灵活、敏捷。本指窗户明亮轩敞◇用来形容人处世圆滑，待人接物面面俱到。','唐·卢纶《赋得彭祖楼送杨宗德归徐州幕》诗四户八窗明，玲珑逼上清。”宋·夏元鼎《满庭芳》词虽是无为清静，依然要八面玲珑。”','原来宝廷的为人，是～，却十分落拓。★清·曾朴《孽海花》第七回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八面玲珑');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八面驶风','bā miàn shǐ fēng','形容善于见风使舵，各方面都兜得传。','老舍《神拳》第三幕喝，八面驶风，左右逢源，真乃诸葛亮也。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八面驶风');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八面受敌','bā miàn shòu dí','①谓功力深厚，能应付各种情况。②砚名。','五代·王保定《唐摭言海叙不遇》子华（吴融）才力浩大，八面受敌，以八韵著称。”','……若学成，～，与涉猎者不可同日而语也。★宋苏轼《又答王庠书》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八面受敌');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八面威风','bā miàn wēi fēng','威风令人敬畏的气势。各个方面都很威风。形容神气足，声势盛。','元·尚仲贤《单鞭夺槊》第四折圣天子百灵相助，大将军八面威风。”','年龄不到五十许，体态虽十分端丽，神情却八面威风。★清·曾朴《孽海花》第十二回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八面威风');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八面莹澈','bā miàn yíng chè','比喻精明练达，洞察一切。','清·昭槤《啸亭杂录·尹文端公》年三十馀即任封疆，遇事镜烛犀刻，八面莹澈。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八面莹澈');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八面圆通','bā miàn yuán tōng','形容为人处事圆滑，处处应付周全。','清·李宝嘉《官场现形记》三八回第二要嘴巴会说，见人说人话，见鬼说鬼话，见了官场说官场上的话，见了生意人说生意场中的话，真正要八面圆通，十二分周到，方能当得此任。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八面圆通');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八面张罗','bā miàn zhāng luo','形容各方面都应酬得好。','茅盾《手的故事》黄二姐则已过中年，却还是个八面张罗人人喜欢的角色。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八面张罗');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八难三灾','bā nàn sān zāi','比喻小孩生来多病。也形容经常遭遇不幸。','元·无名氏《小张屠》第三折但有些八难三灾，一心斋戒。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八难三灾');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八万四千','bā wàn sì qiān','本为佛教表示事物众多的数字，后用以形容极多。','《法华经·宝塔品》若持八万四千法藏，十二部经，为人演说。”','～颠倒想，与君同付醉眠中。★宋·陆游《醉歌》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八万四千');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八仙过海','bā xiān guò hǎi','相传八仙过海时不用舟船，各有一套法术。民间因有八仙过海，各显神通”的谚语◇以比喻各自拿出本领或办法，互相竞赛。','明·无名氏《八仙过海》第二折则俺这八仙过海神通大，方显这众圣归山道法强，端的万古名扬。”','说得好，咱们就～吧！★老舍《茶馆》第一幕','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八仙过海');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八仙过海，各显神通','bā xiān guò hǎi，gè xiǎn shén tōng','八仙道教传说中的八位神仙。比喻做事各有各的一套办法。也比喻各自拿出本领互相比赛。','明·吴承恩《西游记》第八十一回正是八仙同过海，独自显神通。”','我如今与舍弟分开，这弟兄们是～。我叫舍弟看看我的过法。★清·李绿园《歧路灯》第六十八回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八仙过海，各显神通');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八音迭奏','bā yīn dié zòu','八音古代对乐器的统称；迭交互，轮流。八类乐器轮番演奏。表示器乐齐全，演奏场面盛大。','《晋书·乐志下》八音迭奏，雅乐并作。”','又有谓大八音匣者，与衣厢等。触其机，～，恍如有十数人弹唱焉。★清·淮阴百一居士《壶天录》卷中','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八音迭奏');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八音遏密','bā yīn è mì','遏阻止；密寂静。各种乐器停止演奏，乐声寂静。旧指皇帝死后停乐举哀◇也用以形容国家元首之死。','《尚书·舜曲》三载，四海遏密八音。”','奉梓南归四载迟，～寄哀思。★柳亚子《中山先生奉安大典志哀》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八音遏密');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八珍玉食','bā zhēn yù shí','泛指精美的肴馔。','金·董解元《西厢记诸宫调》卷三八珍玉食邀郎餐，千言万语对生意。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八珍玉食');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八字打开','bā zì dǎ kāi','象八”字那样，撇、捺向两边分开。原意是放着正路不走，却走歪门邪道。也形容门路很宽。','宋·朱熹《与刘子澄书》圣贤已是八字打开了，但人自不领会，却向外狂走耳。”','这事已～，请大家多提建议。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八字打开');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八字没见一撇','bā zì méi jiàn yī piě','比喻事情毫无眉目，未见端绪。','孙锦标《通俗常言疏证》卷三《通俗编》‘朱子《与刘子澄书》圣贤已是八字打开了，人不自领会。’按今有‘八字不见两撇’之谚，似又因於此。凡事无端绪者，谓之八字不曾见两撇。”今通常作八字没见一撇”。','我们是来保卫延安的，～，延安就能放弃？★杜鹏程《保卫延安》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八字没见一撇');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '八字没一撇','bā zì méi yī piě','比喻事情还没有眉目。','孙锦标《通俗常言疏证》卷三《通俗编》‘朱子《与刘子澄书》圣贤已是八字打开了，人不自领会。’按今有‘八字不见两撇’之谚，似又因于此。凡事无端绪者，谓之八字不曾见两撇。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '八字没一撇');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '巴巴急急','bā bā jí jí','指勉强，凑合。同巴巴结结”。','《古今小说·沈小官一鸟害七命》父子三人，正是衣不遮身，食不充口，巴巴急急，食不敷。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '巴巴急急');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '巴巴劫劫','bā bā jié jié','指心情急切的样子。','元·狄君厚《介子推》第四折道他巴巴劫劫背着主公，破破碌碌践红尘。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '巴巴劫劫');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '巴巴结结','bā bā jiē jiē','①勉强，凑合。②勤恳；辛劳。③形容说话不流利。','①《京本通俗小说·错斩崔宁》光阴迅速，大娘子在家巴巴结结将近一年。”②《古今小说任·孝子烈性为神》任珪天明起来，辞了父亲入城去了。每日巴巴结结，早出晚回。”','他～地说不了几句。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '巴巴结结');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '巴高望上','bā gāo wàng shàng','指与社会地位高于自己的人结交或联姻。','清·曹雪芹《红楼梦》第四十六回别说是鸳鸯，凭他是谁，那一个不想巴高望上、不想出头的？”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '巴高望上');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '巴高枝儿','bā gāo zhī ér','比喻高攀。','文康《儿女英雄传》第三十二回我也不怕人笑话我奴才亲戚混巴高枝儿，我今日可算认定了干娘咧！”。','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '巴高枝儿');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '巴前算后','bā qián suàn hòu','思前想后，反复考虑。','明·凌濛初《二刻拍案惊奇》卷十九劳生忧忧，巴前算后，每怀不足之心，空白了头，没用处，不如随便度日的好。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '巴前算后');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '巴人下里','bā rén xià lǐ','①即下里巴人。古代楚国流行的民间歌曲。用以称流俗的音乐。巴，古国名，在今四川东部一带，古为楚地。下里，乡里。②泛指粗俗的。','南朝·梁简文帝《与湘东王书》故玉微金铣，反为拙目所嗤；《巴人下里》，更合郢中之听。”','张寅道晚生末学，‘～’之词只好涂饰闾里，怎敢陈于老太师山斗之下。”★《平山冷燕》第十七回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '巴人下里');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '巴三览四','bā sān lǎn sì','比喻说话拉扯，没有中心。','元·萧德祥《杀狗劝夫》我说的丁一确二，你说的巴三览四。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '巴三览四');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '巴三揽四','bā sān lǎn sì','形容说话东拉西扯。','元·萧德祥《杀狗劝夫》第四折我说的丁一确二，你说的巴三揽四。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '巴三揽四');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '巴山度岭','bā shān dù lǐng','指爬山越岭。','《水浒传》第一一六回只见解珍、解宝便道‘我兄弟两个，原是猎户出身，巴山度岭得惯。’”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '巴山度岭');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '巴山蜀水','bā shān shǔ shuǐ','巴、蜀指四川一带。四川一带的山山水水。','萧白《晚祷》多少思怀在这圆圆月中，一次偶然的月下促膝，常成一生追念，于是巴山蜀水近来，洞庭湘河近来，扬子江钟山也近来……”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '巴山蜀水');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '巴山夜雨','bā shān yè yǔ','指客居异地又逢夜雨缠绵的孤寂情景。','唐·李商隐《夜雨寄北》君问归期未有期，巴山夜雨涨秋池∥当共剪西窗烛，却说巴山夜雨时。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '巴山夜雨');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '巴山越岭','bā shān yuè lǐng','爬山越岭。形容善于登山行走。','元·关汉卿《单刀会》第二折人似巴山越岭彪，马跨翻江混海虬。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '巴山越岭');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '巴头探脑','bā tóu tàn nǎo','形容伸着头偷看。','无','乘客纷纷一字排，～费疑猜，东南西北车多少，不靠咱们这站台。★启功《鹧鸪天·乘公共交通车》八首','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '巴头探脑');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '扒耳搔腮','pá ěr sāo sāi','形容焦急而又无可奈何。','鲁迅《华盖集续编·不是信》还有一个小目的，是在对于以我去年的免官为‘痛快’者，给他一个不舒服，使他恨得扒耳搔腮，忍不住露出本相。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '扒耳搔腮');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '扒高踩低','bā gāo cǎi dī','比喻对上奉承攀附，对下欺侮压制。','《中国民间故事选·叛徒李四一》[李四一]为人就是扒高踩低，浮上水在行。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '扒高踩低');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '抜格不入','hàn gé bù rù','?壕；格坚硬；抜格相互抵触，格格不入。过于坚硬而难于深入。形容彼此意见完全不合。','《礼记·学记》发然后禁，则抜格而不胜。”郑玄注抜坚不可入之貌。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '抜格不入');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔本塞原','bá běn sè yuán','①比喻背弃根本。②铲除事物发生的根源。多用于坏事。亦作拔本塞源”。','《左传·昭公九年》伯父若裂冠毁冕，拔本塞原，专弃谋主，虽戎狄其何有余一人。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔本塞原');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔本塞源','bá běn sè yuán','本根本，根源。拔起树根，塞住水源。比喻防患除害要从根本上打主意。','《左传·昭公九年》伯父若裂冠毁冕，拔本塞原，专弃谋主，虽戎狄其何有余一人。”','这也是个好办法，而且简单易行，不过还不是～之计。★郭沫若《郑成功》第五章','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔本塞源');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔丛出类','bá cóng chū lèi','犹言拔萃出类。指高出众人。','《宋书·孝义传·潘综》二子微猷，弥久弥芳。拔丛出类，景行朝阳。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔丛出类');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔萃出类','bá cuì chū lèi','同出类拔萃”。指高出众人。','语出《孟子·公孙丑上》出于其类，拔乎其萃，自生民以来，未有盛于孔子也。”','夫宇宙绵邈，黎献纷杂，～，智术而已。★南朝·梁刘勰《文心雕龙序志》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔萃出类');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔萃出群','bá cuì chū qún','拔超出。萃原谓草丛生的样子，引伸为聚集，指聚集在一处的人或物。超出一般，在众人之上。','《孟子·公孙丑上》出于其类，拔乎其萃，自生民以来，未有盛于孔子也。”','曾不能～，扬芳飞文，登天庭，序彝伦，扫六合之秽。★汉·蔡邕《释诲》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔萃出群');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔刀相向','bá dāo xiāng xiàng','指动武。','《儿女英雄传》第十回自然就不能不闹到扬眉裂眦拔刀相向起来。”','但当他们一看到侵略者公然～的时候，他们丝毫也不动摇地采取了伟大的中国人民在这样场合所惟一可能而且必须采取的办法。★《人民日报》1951.1.11','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔刀相向');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔刀相助','bá dāo xiāng zhù','旧小说中多指打抱不平。','宋·释道元《景德传灯录》卷二十二师曰‘路见不平，所以按剑。’”','路见不平，～。★元·马致远《陈抟高卧》第一折','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔刀相助');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔地参天','bá dì cān tiān','拔地从地面上陡然耸立。参天高耸到空中。形容高大和气势雄伟。同拔地倚天”。','郭沫若《春天的信号》虽然只是一根嫩苗，只要它不遭遇斧斤，我们可以预言，它一定会拔地参天。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔地参天');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔地摇山','bá dì yáo shān','翻动大地，摇撼山岳。形容声势极大。','《英烈传》第四十九回乌龙镇上驻不赤龙舟，搅得翻江震海；大全港中做不得周全事，空教拔地摇山。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔地摇山');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔地倚天','bá dì yǐ tiān','拔突出，耸出。倚倚傍，贴近。从地面突兀而起，贴近天际。比喻高大突出，气势雄伟。','唐·孙樵《与王霖秀才书》譬玉川子《月蚀诗》、杨司城《华山赋》、韩吏部《进学解》……莫不拔地倚天，句句欲活。”','那悬崖之上，一株株苍松翠柏～，傲然屹立。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔地倚天');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔丁抽楔','bá dīng chōu xiē','拔出钉子，抽掉楔子。比喻解除困难。','元·无名氏《度柳翠》第四折大众恐有不能了达，心生疑惑者，请垂下问，我与他拔丁抽楔。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔丁抽楔');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔锅卷席','bá guō juǎn xí','①比喻带走全部家当。②犹言卷铺盖。比喻被解雇。','①孔厥袁静《新儿女英雄传》第一回日本飞机天天来头上转，城里掉了几个蛋；大官们携金带银，小官们拔锅卷席的，都跑光了。”②杨朔《阅微草堂”的真面目》稍微有点不顺心，地主就要拔锅卷席，撵你出去。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔锅卷席');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔剑论功','bá jiàn lùn gōng','指争论功劳激烈到几乎动武。','语出《史记·刘敬叔孙通列传》高帝悉去秦苛仪法，为简易。群臣饮酒争功，醉或妄呼，拔剑击柱，高帝患之。”','当时～者，矢口何人说纪生。★金·元好问《读纪信传》诗','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔剑论功');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔葵啖枣','bá kuí dàn zǎo','拔人家的菜蔬，偷吃人家的枣子。比喻小偷小摸。','唐·独孤及《唐丞相故江陵尹御史大夫吕謓谥议》阖境无拔葵啖枣之盗，而楚人到于今犹歌咏之。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔葵啖枣');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔葵去织','bá kuí qù zhī','比喻做官的不与人民争利。','《汉书·董仲舒传》故公仪子相鲁，之其见家织帛，怒而出其妻；食子舍而茹葵，愠而拔其葵。”','非谓～，且欲省息纷纭。★《梁书、徐勉传》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔葵去织');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔来报往','bá lái fù wǎng','报通赴”，迅速≤快地来，很快地去。形容频繁地奔来奔去。','《礼记少仪》毋拔来，毋报往。”','少顷，以足床来，置地上，促客坐；又入，携短足几至，～，蹀躞甚劳。★清·蒲松龄《聊斋志异·阿纤》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔来报往');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔类超群','bá lèi chāo qún','同拔萃出类”。指高出众人。','语出《孟子·公孙丑上》出于其类，拔乎其萃，自生民以来，未有盛于孔子也。”','任待要片时间～，则除是三般儿结果收因。★元汤式《一枝花·劝妓女从良》套曲','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔类超群');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔了萝卜地皮宽','bá le luó bo dì pí kuān','比喻为了行事方便而把碍眼的事物去掉。也比喻为了扩展地盘而排挤别人。','明·兰陵笑笑生《金瓶梅词话》第五十一回拔了萝卜地皮宽，交他去了，省得他在这里跑兔子一般。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔了萝卜地皮宽');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔毛济世','bá máo jì shì','指受轻微损失而能有利于世人。','语出《列子·杨朱》禽子问杨朱曰‘去子体之一毛以济一世，汝为之乎？’杨子曰‘世固非一毛之所济。’禽子曰‘假济，为之乎？’杨子弗应。”','只平生几两屐”细味之亦疏；而～”事，尤牵强可笑。以予观之，此乃俗子谜也，何足为诗哉！★金·王若虚《滹南诗话》卷三','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔毛济世');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔毛连茹','bá máo lián rú','比喻互相推荐，用一个人就连带引进许多人。','无','鬓角上发现几根白发，这一惊非同小可，平夙一毛不拔的人到这时候也不免要狠心的把它拔去，～，头发根上还许带着一颗鲜亮的肉珠。★梁实秋《中年》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔毛连茹');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔茅连茹','bá máo lián rú','茅白茅，一种多年生的草；茹植物根部互相牵连的样子。比喻互相推荐，用一个人就连带引进许多人。','《周易·泰》拔茅茹以其汇。”','袁既病死，黎、段登台，～，弹冠相庆，徐遂入任为院秘书长。★蔡东藩、许廑父《民国通俗演义》第七十九回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔茅连茹');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔群出萃','bá qún chū cuì','高出众人。多指才能。','《晋书·夏侯湛传》弱年而入公朝，蒙蔽而当显举，进不能拔群出萃，却不能抗排当世。','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔群出萃');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔山超海','bá shān chāo hǎi','拔起高山，超越大海。比喻力量极大。','北周·庾信《拟连珠》经天纬地之才，拔山超海之力。”','天经地纬之帝，求制礼乐之才；拨乱反正之君，资～之力。★唐·杨炯《唐右将军魏哲神道碑》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔山超海');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔山盖世','bá shān gài shì','盖世超越天下人，世上第一。力能拔掉大山，形容力大勇猛，当代无比。','《史记·项羽本纪》于是项王乃悲歌慷慨，自为诗曰‘力拔山兮气盖世，时不利兮骓不逝。’”','则～之雄，几乎其为若敖之鬼矣。★明·张岱《募造无主祠堂疏》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔山盖世');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔山举鼎','bá shān jǔ dǐng','形容力量超人或气势雄伟。','《秦并六国平话》卷下籍是时年方二十四，力敌万夫，有拔山举鼎之威。”','一个人被鸦片束缚住了，任你是～的英雄，铜浇铁铸的罗汉，只要烟瘾一发，顿时骨软精酥，连一些气力都没有。★《黑籍冤魂》第一回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔山举鼎');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔山扛鼎','bá shān gāng dǐng','扛双手举起。拔起大山，举起重鼎。形容力气很大。','《史记·项羽本纪》力能扛鼎，才气过人。”又于是项王乃悲歌慷慨，自为诗曰‘力拔山兮气盖世，时不利兮骓不逝。’”','汉祖入关破秦而楚后之～，力大过人。★宋·邵雍《皇极经世绪言》第二卷','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔山扛鼎');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔十得五','bá shí dé wǔ','拔盐，推荐。想盐十个，结果只选得五个。指盐人才不容易。','《新唐书·张九龄传》夫吏部尚书、侍郎，以贤而授者也，岂不能知人？如知之难，拔十得五，斯可矣。”','谓～而可得其半，故匿瑕含垢而求其长致。★宋陈师道《谢胡运使启》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔十得五');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔十失五','bá shí shī wǔ','指盐人才而失其半数。','《三国志·蜀书·庞统传》今拔十失五，犹得其半，而可以崇迈世数，使有志者自励，不亦可乎？”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔十失五');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔树撼山','bá shù hàn shān','拔起大树，摇动高山。形容声势极大。','无','爱情是不可羁绊的力量。它会使怯懦者勇敢，愚笨者聪明。使人霎那间产生～的力量，创造出无与伦比的美或精巧。没有了爱情便没有世界。★苏叔阳《榆棠院的罗曼斯','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔树撼山');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔树搜根','bá shù sōu gēn','比喻追究到底，彻底查问。','见拔树寻根”。','安爷恐有其中假，故此的，～要问明。★《再生缘》第四五回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔树搜根');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔树寻根','bá shù xún gēn','比喻追究到底。','元·无名氏《碧桃花》第一折俺那里有的是秦人晋人，你可也休将咱盘问，则管里絮叨叨拔树寻根。”','雪娥恐怕西门庆来家～，归罪于己，在上房打磨。★明·兰陵笑笑生《金瓶梅词话》第二十六回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔树寻根');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔犀擢象','bá xī zhuó xiàng','擢提升。比喻提拔才能出众的人。','宋·王洋《与丞相论郑武子状》敕局数人，其间固有拔犀擢象见称一时者，然而析理精微，旁通注意，鲜如克（郑武子）。”','既然要人任人唯亲，又怎能做到～呢？','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔犀擢象');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔辖投井','bá xiá tóu jǐng','辖，车轴两端的键。指殷勤留客饮酒。','《汉书·陈遵传》遵耆酒，每天饮，宾客满堂，辄关门，取客车辖投井中，虽有急，终不得去。”','～，于是口涌鼻溢，濡首及乱。★晋·葛洪《抱朴子·洒诫》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔辖投井');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔新领异','bá xīn lǐng yì','拔抽出；新新意；领具有；异指独特之处。指创立新意，提出独特的见解。','南朝·宋·刘义庆《世说新语·文学》支道林拔新领异，胸怀所及乃自佳，卿欲见不？”','公风神英迈，意气倾倒，～之谈，登峰造极之理，萧然如晋、宋间人物。★宋·杨万里《石湖先生大资参政范公文集序》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔新领异');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔宅飞升','bá zhái fēi shēng','拔拔起；宅住宅。古代传说修道的人全家同升仙界。参见拔宅上升”。','语出《太平广记》卷十四引《十二真君传·许真君》真君以东晋孝武帝太康二年八月一日，于洪州西山，举家四十二口，拔宅上升而去。”','生曰得卿如此，吾何忧！顾念一人得道，～。余将远逝，一切付之于卿。”★清薄松龄《聊斋志异·白于玉》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔宅飞升');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔宅上升','bá zhái shàng shēng','拔拔起；宅住宅。古代传说修道的人全家同升仙界。','《太平广记》卷十四引《十二真君传·许真君》真君以东晋孝武帝太康二年八月一日，于洪州西山，举家四十二口，拔宅上升而去。”','秋先，汝功行圆满，吾已申奏上帝，有旨封汝为护花使者，专管人间百花。令汝～。★明·冯梦龙《醒世恒言》卷四','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔宅上升');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔赵易汉','bá zhào yì hàn','用以比喻偷换取胜或战胜、胜利之典。','见拔赵帜易汉帜”。','所以他们的组织专争取同伍的人，埋头苦干，意欲将新军中除满人与官长外，全体运动成熟，一举而～。★杨玉如《辛亥革命先著记》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔赵易汉');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔赵帜立赤帜','bá zhào zhì lì chì zhì','用以比喻偷换取胜或战胜、胜利之典。','见拔赵帜易汉帜”。','惜伪古文罅漏太多，弥缝匪易，虽盛气强词，仍不能～也。★清·陈康祺《郎潜纪闻》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔赵帜立赤帜');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔赵帜易汉帜','bá zhào zhì yì hàn zhì','用以比喻偷换取胜或战胜、胜利之典。','据《史记·淮阴侯列传》载，信(韩信)所出奇兵二千骑，共候赵空壁逐利，则驰入赵壁，皆拔赵旗，立汉赤帜二千。”赵军进击不能胜，欲回营，见营中尽是汉军赤帜，大惊，以为汉皆已得赵王将矣”，于是溃不成军，终于','妻与生用～计，笑而行之。★清·薄松龄《聊斋志异·人妖》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔赵帜易汉帜');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔帜树帜','bá zhì shù zhì','用以比喻偷换取胜或战胜、胜利之典。','见拔赵帜易汉帜”。','～燃列炬，中丞盼断关山尘。★清·林寿图《谭军门以饥军三营至凤翔与犒一餐是夜报夺上关寄呈刘中丞》诗','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔帜树帜');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拔帜易帜','bá zhì yì zhì','帜旗帜；易换。比喻推翻别人，自己占有。','《史记·淮阴候列传》赵见我走，必空壁逐我，若疾入赵壁，拔赵帜，立汉赤帜。”','如今这里已～，另换新主了。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拔帜易帜');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '跋胡痜尾','bá hú zhì wěi','比喻进退两难。','《诗·豳风·狼跋》狼跋其胡，载痜其尾。”','惟信古太过，而欲为曲突徙薪之谋，故与物多违而每致～之患。★宋·李纲《谢复观文殿大学士表》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '跋胡痜尾');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '跋扈飞扬','bá hù fēi yáng','指骄横放肆。','宋·王安石《辞拜相表》百姓以安平无事之时，而未免流离饿莩；四夷以衰弱仅存之势，而犹能跋扈飞扬。”','纵容军人，使他们～、横暴无耻的，不正是日本资本家吗？郭沫若《归去来·到浦东去来》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '跋扈飞扬');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '跋扈自恣','bá hù zì zì','形容为所欲为，无所忌惮。','《明史·朵颜传》于是长昂益跋扈自恣，东勾土蛮，西结婚白洪大，以扰诸边。”','缘道设关隘，垄断公私。涡河、浍、颖之间，～。★《清史稿·袁甲三传》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '跋扈自恣');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '跋扈恣睢','bá hù zì suī','指专横强暴，为所欲为。','参见跋扈自恣”。','特别是进入残酷卑鄙的剥削阶级最上层的统治生活之后，早已经陶冶出一副狠毒乖戾，～的性格。★秦牧《长街灯语·逛东陵》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '跋扈恣睢');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '跋来报往','bá lái fù wǎng','匆匆地跑来跑去。跋，通拔”。','语出《礼记·少仪》毋拔来，毋报往。”郑玄注报读为赴疾之赴，拨、赴皆疾也。”','一众乡人，～的来领吃。★《情变》第一回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '跋来报往');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '跋履山川','bá lǚ shān chuān','形容远道奔波之苦。参见跋山涉水”。','《左传·成公十三年》文公躬擐甲胄，，逾越险阻，征东之诸之侯。”','夫齐之去周二千余里，而使其已化之骨，～，触冒寒暑，自东徂西，以葬于封守之外，于死者为不仁。”★清·顾炎武《日知录·太公五世反葬于周》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '跋履山川');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '跋前踕后','bá qián jié hòu','同跋胡痜尾”。比喻进退两难。','元·辛文房《唐才子传·罗邺》而跋前踕后，绝域无聊，独奈其命薄何？”一本作跋前痜后”。','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '跋前踕后');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '跋前痜后','bá qián zhì hòu','同跋胡痜尾”。比喻进退两难。','宋·陈亮《谢罗尚书启》直情径行，视毁誉如风而不恤；跋前痜后，方进退惟谷以堪惊。”','其稍畏名义者，则包羞闵默而有～之忧。《续资治通鉴·宋理宗嘉熙元年》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '跋前痜后');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '跋前踬后','bá qián zhì hòu','跋踩；踬被绊倒。本指狼向前进就踩住了自己的颈肉，向后退又会被自己的尾巴绊倒。比喻进退两难。','《诗经·豳风·狼跋》狼跋其胡，载痜其尾。”唐·韩愈《昌黎集·卷十二·近学解》然而公不见信于人，私不见助于友。跋前踬后，动辄得咎”。','无事之时，望影藏匿，～，日不聊生。★清·纪昀《阅微草堂笔记·滦阳续录','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '跋前踬后');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '跋山涉川','bá shān shè chuān','形容远道奔波之苦。参见跋山涉水”。','宋·吴曾《能改斋漫录·事实二》过家上冢，恳章尽得于哀荣；跋山涉川，之任敢辞于艰险。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '跋山涉川');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '跋山涉水','bá shān shè shuǐ','跋山翻过山岭；涉水用脚趟着水渡过大河。翻山越岭，趟水过河。形容走远路的艰苦。','《诗经·鄘风·载驰》大夫跋涉，我心则忧。”《左传·襄公二十八年》跋山涉水，蒙犯霜露。”','同样的青春，同样的年龄，有人～，为改变祖国一穷二白的面貌而感到莫大的幸福。★魏巍《路标》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '跋山涉水');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '跋涉山川','bá shè shān chuān','形容远道奔波之苦。参见跋山涉水”。','《左传·襄公二十八年》必使而君弃而封守，跋涉山川，蒙犯霜露，以逞君心。”','每岁十一月，天下贡举人于含元殿前，见四方馆舍人当直者，宣曰卿等学富雄词，远随乡荐，～，当甚劳止。”★宋·钱易《南部新书》丙','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '跋涉山川');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '把臂入林','bǎ bì rù lín','把指挽着；林山林。互挽手臂，表示亲热。旧指相偕归隐。','南朝·宋·刘义庆《世说新语·赏誉》谢公（安）道豫章（谢鲲）若遇七贤必自把臂入林。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '把臂入林');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '把臂徐去','bǎ bì xú qù','互相挽着胳膊慢慢离开。','无','欣赏好了满园的风景，老两口～。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '把臂徐去');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '把持不定','bǎ chí bù dìng','没有明确的主见，游移反复。','《野叟曝言》第二回但恐日后把持不定，为异端所惑，一时失足。”','他意志薄弱，一遇风浪就～。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '把持不定');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '把饭叫饥','bǎ fàn jiào jī','比喻多此一举。','宋·苏轼《答程彝仲推官二首》所示只是一篇高文，大似把饭叫饥，聊发千里一笑。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '把饭叫饥');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '把酒持螯','bǎ jiǔ chí áo','手持蟹螯饮酒。古人视为人生一大乐事。','语出《晋书·毕卓传》卓尝谓人曰‘得酒满数百斛船，四时甘味置两头，右手持酒杯，左手持蟹螯，拍浮酒船中，便足了一生矣。’”','～，问今日、不知何夕。★傅尃《满江红·八月五日联句》词','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '把酒持螯');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '把素持斋','bǎ sù chí zhāi','把遵守。斋斋戒。指信佛者遵守吃素，坚持戒律。','元·无名氏《女姑姑》三折念佛心把素持斋，不食荤啖柏。”','人人善念看经，个个～。★明·无名氏《万国来朝》第一折','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '把素持斋');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '把玩无厌','bǎ wán wǔ yàn','拿着赏玩，不觉厌倦。','汉·陈琳《为曹洪与世子书》得九月二十日书读之，喜笑把玩无厌。”','这件艺术品刻工精细，花草山石，无不玲珑，真是令人～。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '把玩无厌');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '把薪助火','bǎ xīn zhù huǒ','把握住。拿着柴草助长火势。比喻某种行动作法会增加对方的力量或助长事态的加剧。','明·罗贯中《三国演义》第六十二回今求军马钱粮，切不可与。如若相助，是把薪助火也。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '把薪助火');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '罢黜百家','bā chù bǎi jiā','罢黜废弃不用。原指排除诸子杂说，专门推行儒家学说。也比喻只要一种形式，不要其他形式。','《汉书·武帝纪赞》孝武初立，卓然罢黜百家，表章《六经》。”','……只认朗诵词和诗。笔者却不能够赞成这～”的作风★朱自清《论朗诵诗》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '罢黜百家');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '霸道横行','bà dào héng xíng','犹言横行霸道。指依仗权势为非作歹。','清·黄钧宰《金壶浪墨·漕弊》及到江南，则玩易官府，欺凌民船，霸道横行，莫敢正视。”','还有等刁民恶棍，结交官府，盘剥乡愚，仗着银钱～，无恶不作。★《儿女英雄伟》第八回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '霸道横行');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '霸陵醉尉','bà líng zuì wèi','形容失官之后受人侵辱。','《史记·李将军列传》尝夜从一骑出，从人田间饮。还至霸陵亭，霸陵醉尉，呵止广。……”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '霸陵醉尉');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '霸王别姬','bà wáng bié jī','姬指西楚霸王项羽的宠妾虞姬。形容英雄末路的悲壮情景。现多比喻独断专行，脱离群众，最终垮台。','《史记·项羽本纪》记载霸王项羽在和刘邦夺封建统治权的战争中，最后兵败，自知大势已去，在突围前夕，不得不和虞姬决别。','不是有一出戏叫《～》吗？这些同志如果总是不改，难免有一天要别姬”就是了。★毛泽东《在扩大的中央工作会议上的讲话》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '霸王别姬');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '霸王风月','bà wáng fēng yuè','比喻用粗暴的态度对待幽雅的事情。','清·李汝珍《镜花缘》第五回今主上催花，与众不同，纯用火攻，可谓霸王风月”了。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '霸王风月');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白白朱朱','bái bái zhū zhū','白的白，红的红。形容不同种类、色彩各异的花木。','唐·韩愈《感春三首》诗晨游百花林，朱朱兼白白。”','风风雨雨又春穷，～已眼空。★宋·杨万里《又和风雨二首》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白白朱朱');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白璧青蝇','bái bì qīng yíng','白璧洁白的玉，比喻清白的人。青蝇比喻佞人。比喻善恶忠佞。','唐·陈子昴《胡楚真禁所》诗青蝇一相点，白璧遂成冤。”','为臣子莫贵忠和孝，继美于今有凤毛，～何足较。★《群音类选》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白璧青蝇');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白璧三献','bái bì sān xiàn','楚人和氏得玉璞，两献楚王，两遭刖足。第三次王使治璞，得白玉，琢以为璧，世称和氏璧”。比喻不识良材或怀才不遇。','见《韩非子·和氏》。','明珠暗投反按剑，～还遭刖。★明·何景明《田子行》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白璧三献');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白璧微瑕','bái bì wēi xiá','洁白的玉上有些羞点。比喻很好的人或物有些小缺点，美中不足。','南朝·梁·萧统《陶渊明集序》白璧微瑕，惟在《闲情》一赋。”','智者千虑，必有一失，然而这小毛病只是～而已。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白璧微瑕');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白璧无瑕','bái bì wú xiá','洁白的美玉上面没有一点羞。比喻人或事物完美无缺。','宋·释道原《景德传灯录》卷十三问‘不曾博览空王教略，借玄机试道看。’师曰‘白玉无瑕，卞和刖足。’”','虽以小姐～，何畏乎青蝇，然青蝇日集亦可憎恨耳。★明·名教中人《好俅传》第七回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白璧无瑕');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白草黄云','bái cǎo huáng yún','形容边塞秋季的荒凉景象。','唐·权德舆《赠老将》诗白草黄云塞上秋，曾随骠骑出并州。”','急觅是人，不知何往，惟独立沙碛中，～，四边无际。★清·纪昀《阅微草堂笔记·滦阳续录五》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白草黄云');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白齿青眉','bái chǐ qīng méi','指青少年时期。','明·袁宏道《哭临漳令王子声》诗忆昨与君发长安，白齿青眉吐肺肝。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白齿青眉');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白刀子进，红刀子出','bái dāo zǐ jìn，hóng dāo zǐ chū','指要杀人见血、动手拼命§刀子带血的刀子。','清·曹雪芹《红楼梦》第七回不和我说别的还可；再说别的，咱们白刀子进去，红刀子出来！”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白刀子进，红刀子出');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白藋同心','bái diào tóng xīn','犹言心如野草。形容不羡荣华的品节。','北周·庾信《竹杖赋》秋藜促节，白藋同心。终堪荷蓧，自足驱禽。”','他的旁艺，如制缠枝杖，独运匠心，修短适度，且刻铭识，以贻朋友，大有庾子三所谓‘秋藜促节，～’之致。★郑逸梅《艺坛百影·陈从周与纽约明轩》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白藋同心');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白丁俗客','bái dīng sú kè','白丁没有功名的平民。泛指粗俗之辈。','明·无名氏《庞掠四郡》一折往来无白丁俗客，谈笑有上士高宾》”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白丁俗客');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白发苍苍','bái fà cāng cāng','苍苍灰白色。头发灰白。形容人的苍老。','唐·韩愈《祭十二郎文》吾年未四十，而视茫茫，而发苍苍，而齿牙动摇。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白发苍苍');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白发苍颜','bái fà cāng yán','头发已白，脸色灰暗。形容老人的容貌。','宋·洪迈《容斋五笔·白苏诗纪年岁》白发苍颜五十三，家人强遣试春衫。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白发苍颜');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白发丹心','bái fà dān xīn','丹心赤诚之心。形容年迈苍老，仍然怀有一颗赤诚之心。','《汉书·苏武传》记载苏武出使匈奴，被扣十九年，丹心一片，及还，须发皆白的故事。','牧羊驱马虽戎服，～尽汉臣。★唐·杜牧《河湟》诗','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白发丹心');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白发红颜','bái fà hóng yán','头发斑白而脸色红润。形容老年人容光焕发的样子。','宋·佚名《宣和画谱·道释四》（徐知常）旧尝有痼疾，遇异人得修炼之术，却药谢医，以至引年，白发红颜，真有所得。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白发红颜');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白发千丈','bái fà qiān zhàng','形容头发既白且长，表示人因愁思过重而容颜衰老。','唐·李白《秋浦歌》诗白发三千丈，缘愁似个长。不知明镜里，何处得秋霜。”','凡此诸人，今皆安往？我虽独存，～。★宋·刘克庄《后村全集·祭林寒斋文》第一百三十八卷','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白发千丈');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白发青衫','bái fà qīng shān','青衫无功名者的服饰。谓年老而功名未就。','宋·赵令畤《侯鲭录》第七卷……作诗曰‘白发青衫晚得官，琼林顿觉酒肠宽，平康夜过无人问，留得宫花醒后看。’”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白发青衫');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白发朱颜','bái fà zhū yán','颜脸色。头发斑白，脸色通红。形容老人容光焕发。','明·无名氏《渔樵闲话》第二折绿衣黄里颠倒用，白发朱颜喜怒看。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白发朱颜');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白饭青刍','bái fàn qīng chú','白饭供客，青草喂马。比喻主人招待周到。','唐·杜甫《入奏行赠西山检察使窦侍御诗》为君酤酒满眼酤，与奴白饭马青刍。”宋·辛弃疾《行香子》词白饭青刍。赤脚长须。客来时、酒尽重沽。”','～，待仆马之厚；炊金爨玉，谢款客之隆。★明·程登吉《幼学琼林·饮食》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白饭青刍');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白骨露野','bái gǔ lù yě','露暴露。死人的白骨暴露在野外。形容战争或灾难所赞成的悲剧景象。','三国·魏·曹操《蒿里行》诗白骨露于野，千里无鸡鸣。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白骨露野');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白骨再肉','bái gǔ zài ròu','白骨上再生出肉来。比喻起死回生。','唐·张文成《游仙窟》十娘怜悯客人，存其死命，可谓白骨再肉，枯树重花。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白骨再肉');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白圭之玷','bái guī zhī diàn','白玉圭上的一个斑点。比喻人或物大体很好，只是有些小缺点。','《诗经·大雅·抑》白圭之玷，尚可磨也；斯言之玷，不可为也。”','也不过～，并非晚节不终。★鲁迅《且介亭杂文末编·关于太炎先生二三事》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白圭之玷');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白黑不分','bái hēi bù fēn','比喻清浊混乱，是非不明。','东汉·班固《汉书·刘向传》今贤不肖浑淆，白黑不分，邪正杂糅，忠谗并进。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白黑不分');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白黑颠倒','bái hēi diān dǎo','颠倒错乱。指白黑不分、是非颠倒。','西汉·刘向《列女传·楚成郑瞀》王不明察，遂辜无罪，是白黑颠倒，上下错谬也。”','他经常是～、混淆是非。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白黑颠倒');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白黑分明','bái hēi fēn míng','比喻是非分得清楚。','汉·董仲舒《春秋繁露·保位权》黑白分明，然后民知所去就。”《汉书·薛宣传》所贬退称进，白黑分明。”','经过辩论，事情谁是谁非，已经～。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白黑分明');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白虹贯日','bái hóng guàn rì','白色的长虹穿日而过。古人认为人间有不平凡的事，就会引起这种天象的变化。实际上这不是虹而是晕，是一种大气光学现象。','《战国策·魏策四》聂政之刺韩傀也，白虹贯日。”《史记·鲁仲连邹阳列传》昔者荆轲慕燕丹之义，白虹贯日，太子畏之。”','且休说～，青龙藏池。★元·王实甫《丽春堂》第二折','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白虹贯日');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白华之怨','bái huá zhī yuàn','指女子失宠之哀怨。','《诗·小雅·白华》序白华，周人刺幽后也。幽王取申女以为后，故下国化之，以妾为妻，以孽代宗，而王弗能治。”','贱者咸得秩进，各得厥职，以广继嗣之统，息～。★《汉书·谷永传》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白华之怨');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白话八股','bái huà bā gǔ','用白话写的类似旧时八股式的文章。比喻指应命而写的刻板文章。','鲁迅《南腔北调·论语一年”》 说是《论语》办到一年了，语堂先生命令我做文章。这实在好像出了‘《学而》一章’的题目，叫我做一篇白话八股一样。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白话八股');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白袷蓝衫','bái jiá lán shān','旧时士人的服装。亦借指尚未取得功名的士人。','清·魏秀仁《花月痕》第五回大抵青天碧海，不少峨眉见嫉之伤；谁知白袷蓝衫，亦多鼠思难言之痛。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白袷蓝衫');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白驹过隙','bái jū guò xì','白驹白色骏马，比喻太阳；隙缝隙。象凶马在细小的缝隙前跑过一样。形容时间过得极快。','《庄子·知北游》人生天地之间，若白驹之过卻，忽然而已。”','～，日月穿梭，才见梅开腊底，又早天气回阳。★明·兰陵笑笑生《金瓶梅词话》第二回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白驹过隙');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白驹空谷','bái jū kōng gǔ','白驹白色骏马，比喻贤能者。比喻贤能之人在野而不能出仕◇也比喻贤能者出仕而谷空。','《诗经·小雅·白驹》皎皎白驹，在彼空谷。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白驹空谷');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白蜡明经','bái là míng jīng','白蜡比喻光秃空白。明经科举制度中科目之一。比喻屡试不中。','唐·张鷟《朝野佥载》张鷟号青钱学士，以其万选万中。时有明经董万九上不第，号白蜡明经，与鷟为对。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白蜡明经');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白浪掀天','bái làng xiān tiān','掀翻腾。形容风大浪高。','唐·白居易《风雨晚泊》诗青苔扑地连春雨，白浪掀天尽日风。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白浪掀天');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白龙鱼服','bái lóng yú fú','鱼服穿起鱼的外衣。白龙化为鱼在渊中游。比喻帝王或大官吏隐藏身分，改装出行。','汉·刘向《说苑·正谏》昔日龙下清冷之渊，化为鱼，渔者豫且射中其目。” 汉·张衡《东京赋》白龙鱼服，见困豫且。”','相公～，隐姓潜名。倘或途中小辈不识高低，有毁相公者，何以处之？★明·冯梦龙《警世通言》卷四','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白龙鱼服');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白旄黄钺','bái máo huáng yuè','比喻有关征战的事。','《尚书·牧誓》王左仗黄钺，右秉白旄以麾。”','某十八岁同父起义，～，平定两京。★明·无名氏《智降秦叔宝》第一折','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白旄黄钺');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白眉赤眼','bái méi chì yǎn','比喻平白无故。','明·沈德福《敝帚轩剩语·神名讹称》京师相詈，指其人曰白眉赤眼儿者，必大恨，其猥贱可知。”','～的，作什么去呢？倒底说句话儿，也象件事啊。★清·曹雪芹《红楼梦》第三十四回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白眉赤眼');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白面儒冠','bái miàn rú guān','犹白面书生。指只知读书，阅历少，见识浅的读书人。有时含贬义。亦泛指读书人。','清·龚自珍《己亥杂诗》之八四白面儒冠已问津，生涯只羡五侯宾。萧萧黄叶空村畔，可有摊书闭户人？”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白面儒冠');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白面儒生','bái miàn rú shēng','犹白面书生。指只知读书，阅历少，见识浅的读书人。有时含贬义。亦泛指读书人。','《资治通鉴·晋孝武帝太元十二年》温详之徒，皆白面儒生，乌合为群，徒恃长河以自固；若大军济河，必望旗震坏，不待战也。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白面儒生');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白面书郎','bái miàn shū láng','犹白面书生。指只知读书，阅历少，见识浅的读书人。有时含贬义。亦泛指读书人。','唐·白居易《重过秘书旧房因题长句》昔为白面书郎去，今作苍颜赞善来。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白面书郎');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白面书生','bái miàn shū shēng','指缺乏阅历经验的读书人。也指面孔白净的读书人。','《宋书·沈庆之传》陛下今欲伐国，而与白面书生辈谋之，事何由济。”','雪儿呵，偏则把～奚落。★明·汤显祖《牡丹亭》第二十二出','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白面书生');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白帢青衫','bái jiá qīng shān','旧时士人的服装。亦借指尚未取得功名的士人。同白袷蓝衫”。','宋·谢翱《送袁太初归剡原》诗风帆送客到夷州，白帢青衫谈不朽。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白帢青衫');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白日飞升','bái rì fēi shēng','犹白日升天。道教谓人修炼得道后，白昼飞升天界成仙◇喻指一朝显贵。','《全元散曲·喜春来》黄金转世人何在，白日飞升谁见来。”','左壁挂‘黄鹤楼～’，右壁悬‘洞庭湖三番渡过’。★明·兰陵笑笑生《金瓶梅词话》第三九回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白日飞升');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白日见鬼','bái rì jiàn guǐ','大白天看见鬼。指工部四曹无事可做，非常清闲◇泛指事情离奇古怪或无中生有。','宋·陆游《老学庵笔记》工、屯、虞、水，白日见鬼。”','八戒道哥啊，我们连日造化低了。这两日～！那个化风去的老儿是谁？”★明·吴承恩《西游记》第二十一回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白日见鬼');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白日青天','bái rì qīng tiān','白天。表示强调。','宋·杨万里《题太和宰卓士直寄新刻〈山谷快阁诗真迹〉》诗太史留题快阁诗，旧碑未必是真题。六丁搜出严家墨，白日青天横紫蝩。”','～，睽睽万目，欲加之罪，何患无辞。★彭芬《辛亥逊清政变记》第二编','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白日青天');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白日上升','bái rì shàng shēng','犹言白日升天。道教谓人修炼得道后，白昼飞升天界成仙。','唐·贾岛《赠丘先生》诗常言吃药全胜饭，华岳松边采茯神。不遣髭须一茎白，拟为白日上升人。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白日上升');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白日升天','bái rì shēng tiān','原是道教指白昼升天成为神仙◇比喻一下子富贵起来。','汉·应劭《风俗通·正失》俗说淮南王安招致宾客方术之士数千人，作《鸿宝苑祕》枕中之书，铸成黄白，白日升天。”','遂去，悉度其妻妾，于是金氏门中两代～。”★鲁迅《中国文学史略》第二十六篇','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白日升天');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白日绣衣','bái rì xiù yī','旧时比喻富贵后还乡，向乡亲们夸耀。','汉·应劭《风俗通·怪神·世间多有伐木血出以为怪者》（张辽）以二千石之尊过乡里，荐祝祖考。白日绣衣，荣羡如此。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白日绣衣');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白日衣绣','bái rì yì xiù','衣穿；绣五彩制绣的官服。白天身着华贵官服，使人看见。旧时比喻富贵后还乡，向乡亲们夸耀。','汉·应劭《风俗通·怪神》（张辽）以二千石之尊过乡里，，荐祝祖考。白日衣绣，荣羡如此。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白日衣绣');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白日作梦','bái rì zuò mèng','比喻幻想不可能实现。同白日做梦”。','刘绍棠《狼烟》二十二金镶玉走进舱去，嘻笑道‘老昏君白日作梦，自以为是九五之尊，不肯有失万岁爷的身份，迎接一位七品县令。’”','他们有时间顾得上你么？你这不是明明白白的痴心妄想，～么？★《小说选刊》1981年第4期','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白日作梦');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白日做梦','bái rì zuò mèng','大白天做梦。比喻根本不能实现的梦想。','明·豫章醉月子《精选雅笑·送匾》以为必中而遍问星相者，亦是白日做梦。”','换抠三县鬼迷心窍妄想回头！明摆着的，是你抠三县～。★谌容《万年青》五十七','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白日做梦');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白色恐怖','bái sè kǒng bù','白惨白。指反动派残酷镇压人民的恐怖气氛。','鲁迅《且介亭杂文·关于新文字》然而他们却深知道新文字对于劳苦大众有利，所以在弥漫着白色恐怖的地方，这新文字是一定要受摧残的。”','鲁迅先生对于白色恐怖毫不畏惧，一直把密信和文稿珍藏着。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白色恐怖');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白沙在涅，与之俱黑','bái shān zài niè，yǔ zhī jù hēi','涅黑土。白色的细沙混在黑土中，也会跟它一起变黑。比喻好的人或物处在污秽环境里，也会随着污秽环境而变坏。','《荀子·劝学》白沙在涅，与之俱黑。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白沙在涅，与之俱黑');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白山黑水','bái shān hēi shuǐ','长白山和黑龙江。泛指我国东北地区。','《金史·世纪》生女之地有混同江、长白山。混同江亦号黑龙江，所谓‘白山黑水’是也。”','～之间，是我大清发祥地，你们去看看体验一下也好。★唐明浩《曾国藩》第三部第四章','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白山黑水');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白手成家','bái shǒu chéng jiā','没有依恃，而独立兴起的家业或事业。','周而复《上海的早晨》第一部十二朱延年是商人的儿子，他的福佑药房是白手成家的。”','他过去家徒四壁，但由于肯努力，所以能～。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白手成家');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白手空拳','bái shǒu kōng quán','形容手中一无所有。','清·吴研人《二十年目睹之怪现状》第一百八回我从十几岁上，拿了一双白手空拳，和吴继之两个混；我们两个向没分家，挣到了一百多万。”','以我所见，我的那干儿子杭阿宝，我去年才荐他做一个洋布式拉夫，他一得了这件事，～的，先就做了两票小货，居然叫他赚了一千多。★《发财秘诀》第十回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白手空拳');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白手起家','bái shǒu qǐ jiā','白手空手；起家创建家业。形容在没有基础和条件很差的情况下自力更生，艰苦创业。','《朱子语类》卷一０七今士大夫白屋起家，以致荣显。”','你看，他发了多少财，～，靠的是谁？如今也忘了水源头了，墙上的草，两边倒着呢。★丁玲《太阳照在桑干河上》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白手起家');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白手兴家','bái shǒu xīng jiā','形容原来无基础或条件很差而创出一番事业。同白手起家”。','沙汀《记贺龙》十一联络参谋极力称赞这些同志都是白手兴家的能手。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白手兴家');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白首北面','bái shǒu běi miàn','北面古以南面为尊，北面为卑，此指学生敬师之礼。谓年老犹拜师受业。','《文中子·立命》夫子十五为人师焉，陈留王孝逸先达之傲者也，然白首北面岂以年乎？”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白首北面');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白首不渝','bái shǒu bù yú','渝改变。白头到老也不变。形容人一生忠诚、坚定。','《梁书·何点传》新除侍中何点，栖迟衡泌，白首不渝。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白首不渝');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白首空归','bái shǒu kōng guī','白了头发，空手回来。比喻年纪已老，学无成就。','《后汉书·献帝纪》今耆儒年逾六十，去离本土，营求粮资，不得专业。结童入学，白首空归。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白首空归');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白首齐眉','bái shǒu qí méi','犹白首相庄。指夫妇到老犹相敬爱。','清·纪昀《阅微草堂笔记·滦阳消夏录二》邹夫人白首齐眉，然晚岁丧明，家计亦薄。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白首齐眉');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白首穷经','bái shǒu qióng jīng','直至年纪老了还在钻研经籍，犹言活到老，学到老。','唐·韩偓《赠易卜崔江处士》白首穷经通秘义，青山养老度危时。”','兹予绩服之初，日思讲议之益，谓～之乐，尚可推以与人。★宋·苏辙《范镇可侍读太一宫使告词》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白首穷经');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白首如新','bái shǒu rú xīn','指相交虽久而并不知己，像新知一样。同白头如新”。','宋·苏轼《拟孙权答曹操书》古人有言曰‘白首如新，倾盖如故。’言以身托人，必择所安。孟德视仆，岂惜此尺寸之士哉，特以公非所托故也。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白首如新');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白首同归','bái shǒu tóng guī','归归向、归宿。一直到头发白了，志趣依然相投。形容友谊长久，始终不渝◇用以表示都是老人而同时去世。','晋·潘岳《金谷集作诗》春荣谁不慕，岁寒良独希；投分寄石友，白首同所归。”','当君～日，是我青山独往时。★唐·白居易《九年十一月十一日感事而作》诗','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白首同归');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白首为郎','bái shǒu wéi láng','慨叹虽有才能而至老不遇。','《汉武故事》载武帝尝至郎署，见郎官颜驷须鬓皓白，衣服不整”。武帝问他何时为郎？”答道以文帝时为郎。”武帝又问为什么老而不遇”？他回答说文帝好文而臣尚武，景帝好老而臣尚少，陛下好少而臣已老，是以三世不遇。','孤臣曾趣龙墀对，～只自伤。★宋·陆游《史院书事》诗','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白首为郎');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白首相知','bái shǒu xiāng zhī','白首白头发，引申为时间长。意谓老年知己。','明·孙仁儒《东郭记·顽夫廉》君家朋友何须道，翻腾云雨都常套，抵多少白发相知，按剑同袍。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白首相知');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白首相庄','bái shǒu xiāng zhuāng','指夫妇到老犹相敬爱。','明·沈德符《野获编·勋戚·惧内》近年吴中申王二相公，亦于夫人白首相庄，不敢有二色。”','承德公老于场屋，不事生产，朝齑暮盐，黾勉有无，～，有鸡鸣馌耕之德，此夫人之为妻也。★清·钱谦益《诰封安人熊母皮夫人墓志铭》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白首相庄');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白首一节','bái shǒu yī jié','指年虽老而志节不衰。','《后汉书·吴良传》窃见臣府西曹掾齐国吴良资质敦固，公方廉恪，躬俭安贫，白首一节。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白首一节');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白首之心','bái shǒu zhī xīn','老年时的壮志。','唐·王勃《滕王阁序》老当益壮，宁移白首之心？穷且益坚，不坠青云之志。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白首之心');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白水鉴心','bái shuǐ jiàn xīn','白水清水；鉴照。清澈的水能照见人的心。形容人心像明净的水一样纯洁。','无','这孩子朴实勤奋，白水鉴心，是个品学兼优的好孩子。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白水鉴心');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白叟黄童','bái sǒu huáng tóng','白头发的老人和黄头发的孩子。泛指老老少少。','明·无名氏《衣锦还乡》第四折仰赖圣德仁慈，白叟黄童，焚香顶礼，俺永享快乐也。”','城乡内外，红男绿女，～，联袂往观者，何止千万人！★清·壮者《扫迷帚》第九回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白叟黄童');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白天见鬼','bái tiān jiàn guǐ','比喻虚妄荒诞，不可能出现的事。同白日见鬼”。','老舍《四世同堂》二九他太注意老二的无聊了，而始终以为老二的不敢到学校去是白天见鬼。”','周炳一面说‘我的时运太低，～！’说罢回身想走。★欧阳山《苦斗》十二','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白天见鬼');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白头不终','bái tóu bù zhōng','指夫妇不能偕老。','清·沈复《浮生六记·闺房记乐》剔灯入帐，芸已寒热大作，余亦继之，困顿两旬。真所谓乐极灾生亦是白头不终之兆。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白头不终');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白头到老','bái tóu dào lǎo','白头头发白。夫妻相亲相爱，一直到老。','《诗经·卫风·氓》及尔偕老。”明·陆采《怀香记·奉诏班师》孩儿，我与你母亲白头偕老，富贵双全。”','小娘半老之际，风波历尽，刚好遇到个老成的孤老，两下志同道合，收绳卷索，～。★明·冯梦龙《醒世恒言》卷三','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白头到老');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白头而新','bái tóu ér xīn','指相交虽久而并不知己，像新知一样。同白头如新”。','明·杨慎《白头而新》《汉书》‘白头如新，倾盖如故。’《说苑》作‘白头而新，倾盖而故’。‘而’、‘如’，古字通用。白头而新，虽至老而交犹新。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白头而新');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白头如新','bái tóu rú xīn','白头头发白了；新新交。指交朋友彼此不能了解，时间虽久，仍跟刚认识一样。','汉·邹阳《狱中上书自明》语曰白头如新，倾盖如故，何则？”《史记·鲁仲连邹阳列传》谚曰‘有白头如新，倾盖如故。’何则？知与不知也。”','而八年之间，语言不接，吉凶不相问吊，反有～之嫌。★宋·陈亮《与应仲书》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白头如新');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白头相并','bái tóu xiāng bìng','犹言白头偕老。夫妻相亲相爱，一直到老。','明·冯梦龙《挂枝儿·木梳》木梳儿，我爱你齿牙干净……向妆台设个誓愿得白头相并！”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白头相并');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白头相守','bái tóu xiāng shǒu','指夫妻恩爱相守到老。','明·兰陵笑笑生《金瓶梅词话》第六十二回我的哥哥，奴承望和你白头相守，谁知奴今日死去也。”','我与李固～，便是我平生愿足。★明·无名氏《闹铜台》第一折','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白头相守');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白头偕老','bái tóu xié lǎo','白头头发白；偕共同。夫妻相亲相爱，一直到老。','《诗经·卫风·氓》及尔偕老。”明·陆采《怀香记·奉诏班师》孩儿，我与你母亲白头偕老，富贵双全。”','我原说过，她不会跟你～的。★巴金《寒夜》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白头偕老');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白头之叹','bái tóu zhī tàn','指妇女被遗弃而作晚景凄凉之叹。','元·王实甫《西厢记》第四本第一折此身皆托于足下，勿以他日见弃，使妾有白头之叹。”','妾蒙陛下宠眷，六宫无比，只怕日久恩疏，不免～。★清·洪昻《长生殿·密誓》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白头之叹');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白兔赤乌','bái tù chì wū','月亮和太阳的代称。多借指时间。','唐·白居易《劝酒》诗天地迢迢自长久，白兔赤乌相趁走。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白兔赤乌');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白往黑来','bái wǎng hēi lái','比喻变化极大。','《列子·说符》向者，使汝狗白而往，黑而来，岂能无怪哉？”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白往黑来');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白屋寒门','bái wū hán mén','白屋用白茅草盖的屋；寒门清贫人家。泛指贫士的住屋。形容出身贫寒。','元·高文秀《谇范叔》第三折未亨通，遭穷困，身居在白屋寒门。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白屋寒门');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白屋之士','bái wū zhī shì','指贫寒的士人。','汉·王充《论衡·语增》周公执贽下白屋之士。”','是所工也多，故传书甚少；其转徙也艰，故受毁甚易；其为费也不资，故～不能得书者甚众。★严复《论八股存亡之关系》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白屋之士');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白雪皑皑','bái xuě ái ái','皑皑洁白的样子，多用来形容霜雪。洁白的积雪银光耀眼。','秦牧《鲜荔枝和干荔枝》白雪皑皑的山峰，当阳光照耀的时候……大概总以为是灿烂得很，大可观赏的吧。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白雪皑皑');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白雪难和','bái xuě nán hé','白雪指楚国一首歌曲名。比喻高深的不通俗的艺术。','无','这幅抽象画，对一般人来说都是～。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白雪难和');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白雪阳春','bái xuě yáng chūn','原指战国时代楚国的一种较高级的歌曲。比喻高深的不通俗的文学艺术。','战国·楚·宋玉《对楚王问》其为《阳阿》、《薤露》，国中属而和者数百人，其为《阳春》、《白雪》，国中属而和者不过数十人而已。”','高山流水千年调，～万古情。★崔时佩《西厢记·琴心写恨》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白雪阳春');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白眼相看','bái yǎn xiāng kàn','看别人时眼睛朝上或旁边，现出白眼珠，表示轻蔑，不屑一顾，对人不礼貌。','宋·杨万里《诚斋集·都下和同舍李元老承信赠诗之韵》第四卷尽今俗客不妨来，白眼相看勿分剖。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白眼相看');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白衣苍狗','bái yī cāng gǒu','苍灰白色。浮云象白衣裳，顷刻又变得象苍狗。比喻事物变化不定。','唐·杜甫《可叹诗》天上浮云似白衣，斯须改变如苍狗。”','正是～多翻覆，沧海桑田几变更。★明·张景《飞丸记·梨园鼓吹》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白衣苍狗');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白衣公卿','bái yī gōng qīng','古时指进士。唐代人极看重进士，宰相多由进士出身，故推重进士为白衣卿相，是说虽是白衣之士，但享有卿相的资望。','五代·王定保《唐摭言·卷一·散序进士》缙绅虽位极人臣，不由进士者，终不为美，以至岁贡常不减八九百人，其推重谓之‘白衣公卿’，又曰‘一品白衫’。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白衣公卿');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白衣卿相','bái yī qīng xiàng','古时指进士。唐代人极看重进士，宰相多由进士出身，故推重进士为白衣卿相，是说虽是白衣之士，但享有卿相的资望。','五代·王定保《唐摭言·卷一·散序进士》缙绅虽位极人臣，不由进士者，终不为美，以至岁贡常不减八九百人，其推重谓之‘白衣公卿’，又曰‘一品白衫’。”','小生不才杀者波，也是国家～。★元·吴昌龄《张天师》第二折','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白衣卿相');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白衣秀士','bái yī xiù shì','指未曾获得功名的书生。','元·马致远《岳阳楼》第二折至如吕岩，当初是个白衣秀士、未遇书生，上朝求官。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白衣秀士');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白衣宰相','bái yī zǎi xiàng','指宰相家属中身无名位而仗势擅权的人。','《新唐书·令狐滈传》且滈居当时，谓之‘白衣宰相’。滈未尝举进士，而妄言已解，使天下谓无解及第，不已罔乎？”','令狐総为相，其子怙势，人称为‘～’。★明·郎瑛《七修类稿·辩证六·称相》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白衣宰相');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白蚁争穴','bái yǐ zhēng xué','比喻竞逐名利。','元·马谦斋《沉醉东风·自悟》曲取富贵青蝇竞血，进功名白蚁争穴。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白蚁争穴');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白鱼赤乌','bái yú chì wū','为祥瑞之兆。','《史记·周本纪》武王渡河，中流，白鱼跃入王舟中，武王俯取以祭。既渡，有火自上复于下，至于王屋，流为乌，其色赤，其声魄云。”','～之符，黄金紫玉之瑞，事丰奇伟，辞富膏腴，无益经典而有助文章。★南朝·梁·刘勰《文心雕龙·正纬》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白鱼赤乌');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白鱼登舟','bái yú dēng zhōu','为殷亡周兴之兆。同白鱼入舟”。','《汉书·终军传》盖六鶂退飞，逆也；白鱼登舟，顺也。”颜师古注引臣瓒曰谓武王伐殷而鱼入王舟，象征而必获，故曰顺也。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白鱼登舟');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白鱼入舟','bái yú rù zhōu','比喻用兵必胜的征兆。','《史记·周本纪》武王渡河，中流，白鱼跃入王舟中，武王俯取以祭……诸侯皆曰‘纣可伐矣。’”','武王～，君臣变色。★《三国志·魏书·杨阜传》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白鱼入舟');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白玉微瑕','bái yù wēi xiá','洁白的玉上有些羞点。比喻很好的人或物有些小缺点，美中不足。','南朝·梁·萧统《陶渊明集序》白璧微瑕，惟在《闲情》一赋。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白玉微瑕');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白玉无瑕','bái yù wú xiá','洁白的美玉上面没有一点羞。比喻人或事物完美无缺。','宋·释道原《景德传灯录》卷十三问‘不曾博览空王教略，借玄机试道看。’师曰‘白玉无瑕，卞和刖足。’”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白玉无瑕');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白云苍狗','bái yún cāng gǒu','苍灰白色。浮云象白衣裳，顷刻又变得象苍狗。比喻事物变化不定。','唐·杜甫《可叹诗》天上浮云似白衣，斯须改变如苍狗。”','真是世事～，不禁感慨系之矣！★鲁迅《华盖集后记》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白云苍狗');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白云孤飞','bái yún gū fēi','比喻客居他乡，思念父母。','《新唐书·狄仁杰传》荐授并州法曹参军，亲在河阳。仁杰登太行山，反顾，见白云孤飞，谓左右曰‘吾亲舍其下。’瞻怅久之。云移，乃得去。”','客中思亲云～。★宋·胡继宗《书言故事·天文类》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白云孤飞');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白云亲舍','bái yún qīn shè','亲指父母；舍居住。比喻思念父母的话。','《新唐书·狄仁杰传》荐授并州法曹参军，亲在河阳。仁杰登太行山，反顾，见白云孤飞，谓左右曰‘吾亲舍其下。’瞻怅久之。云移，乃得去。”','南望～在，故乡回首凄凄。★清·秋谨《临江仙·题李艺垣〈慕莱堂集〉》词','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白云亲舍');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '白纸黑字','bái zhǐ hēi zì','白纸上写下了黑字。比喻有确凿的文字凭据，不容抵赖或悔改。','元·无名氏《看钱奴买冤家债主》第二折不要闲说，白纸上写着黑字哩。若有反悔之人，罚宝钞一千贯与不反悔之人使用。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '白纸黑字');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百般刁难','bǎi bān diāo nàn','用各种手段使对方过不去。','郭戈《金瓶梅传奇》第十一回柔玉不便道出自己身世，编个话儿与她道‘奴家父母早逝，自幼跟哥嫂度日。只是嫂嫂容不得，百般刁难，与其受人凌辱，倒不如自寻清净，避开人世烦恼。’”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百般刁难');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百般奉承','bǎi bān fèng chéng','用各种方式来恭维讨好人。','元·关汉卿《金线池》第一折百般奉承他，常怕一个留他不住，怎么刚刚三日，便要赶他出门？”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百般奉承');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百般抚慰','bǎi bān fǔ wèi','用各种方式进行安慰。抚慰安慰。','《中国通史》(第九册)第十一章第三节代宗对于河北安史降将虽然一味姑息，百般抚慰，但他们并未感恩戴德，反而视为软弱可欺。”','老妇人于是～，把自己年轻时抚慰孩子的语句，一一背了出来。★《九夜》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百般抚慰');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百般折磨','bǎi bān zhé mó','用各种方式使人在肉体上、精神上遭受痛苦。','宋·李昿《太平广记·精察二·孟简》土豪又叫村里的二十多个妇女……然后揪着她的头发把头往地上碰，百般折磨污辱，包君的妻子本来就瘦弱多病，并且怀有身孕，回到船上就死了。”','她任凭酷刑～，紧咬着牙关，始终坚贞不屈。★《言行和心理》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百般折磨');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百宝万货','bǎi bǎo wàn huò','指大量珍宝财物。','清·龚自珍《尊隐》天下法宗礼，族归心，鬼归祀，大川归道，百宝万货，人功精英，不翼而飞，府于京师。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百宝万货');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百弊丛生','bǎi bì cóng shēng','百形容多；弊弊端，害处。各种弊害都产生出来了。','梁启超《政治学学理摭言》而此等异论，何以能容喙焉？何以能动人焉？则以智慧程度未达于大多数，而欲幸福之程度进于大多数，未有不百弊丛生，而贻反对之徒以口实者也。”','今以十当一，是反以四钱八分铜作一钱二分用也。民间私閒改铸，～。★柯劭忞《清史稿·袁希祖传》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百弊丛生');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百不当一','bǎi bù dāng yī','当抵挡。一百个抵挡不住一个。','汉·荀悦《汉纪·文帝纪下》平易相远，山谷幽涧，仰高临下，此弓弩之地也，短兵百不当一。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百不当一');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '碍足碍手','ài zú ài shǒu','碍阻碍。妨碍别人做事。同碍手碍脚”。','无','如果你不想好好干的话，就别在这儿～。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '碍足碍手');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暧昧不明','ài mèi bù míng','暧昧含糊，不清楚。指不清不楚，不明不白。形容态度不明朗，关系不鲜明。','清·文康《儿女英雄传》第八回又把你姑娘一片侠肠，埋没得暧昧不明，我安龙媒真真的愧悔无地！”','武汉国民政府和当权军人对于这叛变采取了～的态度，办求化大事为小事”。★陶菊隐《北洋军阀统治时期史话》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暧昧不明');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暧昧之情','ài mèi zhī qíng','行为不光明，内有不可告人的隐情。也指男女互相爱悦可能的私事。','明·冯梦龙《警世通言·三现身包龙图断冤》做知县时，便能剖人间暧昧之情，断天下狐疑之狱。”','而孤男寡女并外一室，不无～。★清·明教中人《好逑传》第十五回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暧昧之情');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安安稳稳','ān ān wěn wěn','形容十分安定稳当。','元·无名氏《抱妆盒》第四折小储君倒也安安稳稳守着妆盒做护身符，则是我陈琳兢兢战战抱着个天大闷葫芦。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安安稳稳');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安邦定国','ān bāng dìng guó','邦泛指国家。使国家安定巩固。','宋·无名氏《新编五代史平话·周史》卷上我这剑要卖与烈士，大则安邦定国，小则御侮捍身，您孩儿每识个什么？”','荐贤举善是吾心，～访知音。★明·无名氏《伐晋兴齐》第一折','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安邦定国');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安邦治国','ān bāng zhì guó','使国家安定太平。','宋·无名氏《新编五代史平话·周史》卷上我这剑要卖与烈士，大则安邦定国，小则御侮捍身，您孩儿每识个什么？”','～平天下，自有周公孔圣人。★冰心《斯人独憔悴》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安邦治国');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安不忘危','ān bù wàng wēi','在安全的时候不忘记危难。意思是时刻谨慎小心，提高警惕。','《周易·系辞下》是故君子安而不忘危，存而不忘亡，治而不忘礼，是以身安而国家可保也。”','何小姐是从苦境里过来的，如今得地身安，安不忘危，立志要成全起这份人家，立番事业。★清·文康《儿女英雄传》第三十回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安不忘危');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安步当车','ān bù dàng chē','安安详，不慌忙；安步缓缓步行。以从容的步行代替乘车。','《战国策·齐策四》晚食以当肉，安步以当车，无罪以当贵，清静贞正以自虞。”','每日到局只要把照例的公事办完，立刻回公馆吃饭，一到下午，便一个人～，出门逍遥自在去了。★清·李宝嘉《中国现在记》第七回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安步当车');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安常处顺','ān cháng chǔ shùn','安习惯于；处居住，居于；顺适合，如意。习惯于平稳的日子，处于顺利的境遇中。','《庄子·养生主》安时而处顺，哀乐不能入也。”','～，以为社会一健全分子，以徐徐发达，人尽能之，岂待我辈。★清·梁启超《中国前途之希望与国民责任》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安常处顺');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安常履顺','ān cháng lǚ shùn','习惯于平稳的日子，处于顺利的境遇中。同安常处顺”。','清·方苞《方任二贞妇传》凡士之安常履顺而自检其身，与所以施于家者，其事未若二妇人之艰难也，而乃苟于自恕，非所谓失其本心者与？”。','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安常履顺');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安常守分','ān cháng shǒu fèn','分本分，自己分内的。习惯于平稳的生活，安守本分。','宋·朱熹《朱子语类》卷二十四如有一般人，只安常守分，不恁求利，然有时意思亦是求利，察其所安，又看他心所安稳处，一节深一节”。','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安常守分');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安常守故','ān cháng shǒu gù','习惯于日常的平稳生活，保守旧的一套。指守旧不知变革。','明·海瑞《申军门吴尧便宜五事文》本院非安常守故人也。至任以来，千万人以鼓舞作兴，翻然改革望之矣。”','他长期以来～，缺乏锻炼，因而经不起挫折。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安常守故');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安常习故','ān cháng xí gù','习习惯；故旧例。安于常规，习惯旧例。形容按照常规和老套办事，守旧而不思变革。','宋·刘挈《论人才》有安常习故乐于无事之论，有变古更法喜于敢为之论。”','彼～之流。★清·魏源《治篇七》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安常习故');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安车蒲轮','ān chē pú lún','让被征请者坐在安车上，并用蒲叶包着车轮，以便行驶时车身更为安稳。表示皇帝对贤能者的优待。','《汉书·武帝纪》遣使者安车蒲轮，束帛加壁，征鲁申公。”','申公待聘，～。★明·张岱《寿周霞城八十》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安车蒲轮');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安堵乐业','ān dǔ lè yè','安堵相安，安定。犹言安居乐业。','三国·魏·钟会《檄蜀文》百姓士民，安堵乐业。农不易亩，市不回肆。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安堵乐业');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安堵如故','ān dǔ rú gù','堵墙壁，居所。故原来的。像原来一样相安无事。','《史记·高祖本纪》诸吏人皆案堵如故。”汉·荀悦《汉纪·高祖纪》吏人皆安堵如故，民争献牛洒。”','当涂、徽州、得以～，皆先生一谑之力也。★清·张岱《王谑庵先生传》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安堵如故');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安分守己','ān fèn shǒu jǐ','分本分。规矩老实，守本分，不做违法的事。','宋·袁文《翁牖闲评》八彼安分守己，恬于进取者，方且以道义自居，其肯如此侥幸乎？”','从此养好了，可要安分守己，再别胡行乱闹了。★清·曹雪芹《红楼梦》第七十二回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安分守己');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安分守已','ān fěn shǒu jǐ','规矩老实，守本分，不做违法的事。','《红楼梦》第七十二回从此养好了，可要安分守己，再别胡行乱闹了。”','他是个～的公民。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安分守已');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安分知足','ān fèn zhī zú','安于本分，对自己所得到的待遇知道满足。','宋·洪迈《容斋随笔·三笔·人当知足》其安分知足之意终身不渝。”','这吴推官若是～的人，这也尽叫是快活的了。★清·西周生《醒世姻缘传》第九十一回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安分知足');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安份守己','ān fèn shǒu jǐ','规矩老实，守本分，不做违法的事。','李劼人《天魔舞》第十七章所以我们对于空子，应该广劝他们安份守己的好。?','种种田，做个～的老百姓，不同样也可以过一辈子么？★聂绀弩《季氏将伐颛臾》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安份守己');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安富恤贫','ān fù xù pín','恤救济。使富有的人安定，贫穷的人得到救济。','《周礼·地官·大司徒》以保息六养万民一曰慈幼，二曰养老，三曰振穷，四曰恤贫，五曰宽疾，六曰安富。”','各国都应采取一些～的措施，以保障社会的安定。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安富恤贫');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安富恤穷','ān fù xù qióng','安定富有者，振济贫苦者。指统治者治国安民之道。','《周礼·地官·大司徒》以保息六养万民一曰慈幼，二曰养老，三曰振穷，四曰恤贫，五曰宽疾，六曰安富。”','损不失富，优可赈穷，此乃古者～之善经，不可舍也。★唐·陆贽《均节赋税恤百姓六条》六','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安富恤穷');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安富尊荣','ān fù zūn róng','安安逸；富富有；尊尊贵；荣荣耀。安定富足，尊贵荣华。也指安于富裕安乐的生活。','《孟子·尽心上》君子居是国也，其君用之，则安富尊荣。”','主仆上下都是～，运筹谋画的竟无一个。★清·曹雪芹《红楼梦》第二回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安富尊荣');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安故重迁','ān gù zhòng qiān','指安于旧俗，不轻易改变。','汉刘向《说苑·修文》触情从欲，谓之禽兽；苟可而行，谓之野人；安故重迁，谓之众庶；辨然否，通古今之道，谓之土。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安故重迁');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安国富民','ān guó fù mín','使国家安定，使人民富裕。','《汉书·汲黯传》公为正卿上不能褒先帝之功业，下不能化天下之邪心，安国富民，使囹圄空虚。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安国富民');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安国宁家','ān guó níng jiā','指治理国家。','南朝·宋·刘义庆《世说新语·方正》大丈夫将终，不问安国宁家之术，乃作儿女子相问。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安国宁家');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安魂定魄','ān hún dìng pò','魂、魄人的灵气、精神。指使人心安定。','明·汤显祖《牡丹亭》第三十四出不寻常，安魂定魄赛过反精香。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安魂定魄');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安家乐业','ān jiā lè yè','犹安居乐业。指安定愉快地生活和劳动。','《汉书·谷永传》薄收赋税，毋殚民财，使天下黎元咸安家乐业。”','俺在北京～，你来赚我上山。★《水浒传》第八五回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安家乐业');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安家立业','ān jiā lì yè','安置家庭，创立基业或事业。也指长期在一个地方劳动和生活。','染斌《红旗谱》第一卷你们父子帮助我安家立业，我一辈子也忘不了。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安家立业');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安家落户','ān jiā luò hù','安安置；落户指定居。到一个新地方安家，长期居住。','郭沫若《郑成功》第四章台湾每年可收二三季，我们去只要辛苦得几个月，就可以安家落户了。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安家落户');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安居乐业','ān jū lè yè','安安定；乐喜爱，愉快；业职业。指安定愉快地生活和劳动。','《老子》第八十章民各甘其食，美其服，安其俗，乐其业，至老死不相往来。”《汉书·货殖列传》各安其居而乐其业，甘其食而美其服。”','一家正～，不想安禄山兵乱，潼关失守，天子西幸。★明·冯梦龙《醒世恒言》卷六','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安居乐业');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安老怀少','ān lǎo huái shào','安安顿；怀关怀。尊重老人，使其安逸；关怀年轻人，使其信服。','《论语·公冶长》子路曰‘原闻子之志。’子曰‘老者安之，朋友信之，少者怀之。’”','诚为枉法营私，原王章所不宥；要知～，亦圣道之大同。★清·文康《儿女英雄传》第三十五回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安老怀少');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安乐窝','ān lè wō','泛指安静舒适的住处。','宋·邵雍《无名公传》所寝之室谓之安乐窝，不求过美，惟求冬暖夏凉。”','疏帘竹簟山茶碗，此是幽人～。★宋·辛弃疾《题鹤鸣亭》诗','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安乐窝');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安良除暴','ān liáng chú bào','安抚善良的人，铲除强暴的人。','宋·李昿《太平广记》舍之职责，在乎除暴安良。”','《三侠五义》间在叙勇侠之士，游行村市，～，为国立功。★鲁迅《中国小说史略》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安良除暴');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安眉带眼','ān méi dài yǎn','长了眉毛，有了眼睛。意思是同样是一个人。','明·施耐庵《水浒全传》第二十八回你也是安眉带眼的人，直须要我开口说。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安眉带眼');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安民告示','ān mín gào shì','安定人心的布告。现也指把要商量的或要办的事情预先通知大家。','清·金念劬《避兵十日记》嘱两县速出安民告示，谕令店铺照常开张。”','开会要事先通知，像出～一样，让大家知道要讨论什么问题，解决什么问题，并且早做准备。★毛泽东《党委会的工作方法》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安民告示');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安内攘外','ān nèi rǎng wài','攘排除。原就药的疗效而言◇多指安定内部，排除外患。','汉·张仲景《伤寒论·太阳病上》甘草甘平，有安内攘外之能。”','～之大经，富国强兵之远略。★孙中山《上李鸿章书》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安内攘外');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安贫乐道','ān pín lè dào','道主张，思想。安于贫穷，以坚持自己的信念为乐。旧时士大夫所主张的为人处世之道。','《后汉书·杨彪传》安贫乐道，恬于进趣，三辅诸儒莫不慕仰之。”','劝人～是古今治国平天下的大经络，开过的方子也很多，但都没有十全大补的功效。★鲁迅《花边文学·安贫乐道法》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安贫乐道');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安贫乐贱','ān pín lè jiàn','安于贫贱，并以此为乐。','《后汉书·蔡邕传》夫子生清穆之世，禀醇和之灵，覃思典籍，韫椟《六经》，安贫乐贱，与世无营。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安贫乐贱');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安贫守道','ān pín shǒu dào','安于贫穷，恪守信仰。','宋·苏轼《荐布衣陈师道状》臣等伏见徐州布衣陈师道文词高古，度越流辈，安贫守道。”','由张东白之事观之，非平日～之意彻乎表里，安能使朋友信之如是。★《明儒学案·诸儒学案上·布政陈克庵先生选》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安贫守道');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安然如故','ān rán rú gù','还象原来那样安安稳稳。','元·纪君祥《赵氏孤儿》第四折你只看这一个，那一个，都是为谁而卒？岂可我做儿的倒安然如故？”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安然如故');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安然无事','ān rán wú shì','犹言平安无事。','清·夏敬渠《野叟曝言》第三十二回四嫂不来聒噪，耳根清净，倒也安然无事，除非把你两家合成一家，我一个人就好照顾了。”','要得～，除非把你两家合成一家，我一个人儿就好照顾了。★清·文康《儿女英雄传》第九回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安然无事');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安然无恙','ān rán wú yàng','恙病。原指人平安没有疾病。现泛指事物平安未遭损害。','《战国策·齐策》岁亦无恙耶？民亦无恙耶？王亦无恙耶？。”','只求处士每岁元旦，作一朱幡，上图日月五星之文，立于苑东，吾辈则安然无恙矣。★明·冯梦龙《醒世恒言》第四卷','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安然无恙');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安忍无亲','ān rěn wú qīn','安忍习于残忍，不以为异。安心于做残忍的事情，因而无所谓亲人。','《左传·隐公四年》夫州吁阻兵而安忍。阻兵无众，安忍无亲。众叛亲离，难以济矣。”','李世绩之～也，置父于窦建德之刃下而不恤。★清·王夫之《读通鉴论·唐高宗》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安忍无亲');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安如盘石','ān rú pán shí','安安稳；盘石大石头。像盘石一样安稳不动。形容非常稳固，不可动摇。','荀况《荀子·富国》为名者否，为利者否，为忿者否，则国安于盘石，寿于旗翼。','立宪国皇室所以～者，道皆在是也。★清·梁启超《责任内阁释义》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安如盘石');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安如磐石','ān rú pán shí','磐石大石头。如同磐石一般安然不动。形容非常稳固。','《荀子·富国》为名者否，为利者否，为忿者否，则国安于磐石，寿于旗翼。”','立宪国皇室所以～，道皆在是也。★清·梁启超《责任内阁释义》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安如磐石');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安如太山','ān rú tài shān','形容极其平安稳固。泰，也写作太”。同安如泰山”。','汉·焦赣《易林·坤之中孚》安如太山，福禧屡臻。虽有豺虎，不致危身。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安如太山');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安如泰山','ān rú tài shān','形容象泰山一样稳固，不可动摇。','汉·枚乘《上书谏吴王》变所欲为，易于反掌，安于泰山。”','在敌人的强大攻势下，我军阵地～。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安如泰山');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安若泰山','ān ruò tài shān','形容极其平安稳固。同安如泰山”。','《南史·梁纪下论》自谓安若泰山，算无遗策。”','夜宿丈人之馆，～。★宋·周密《齐东野语》卷十七','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安若泰山');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安身乐业','ān shēn lè yè','指安稳快乐地过日子。','《红楼梦》第六五回倘或一日他知道了，岂肯干休？势必有一场大闹。你二人不知谁生谁死，这如何便当作安身乐业的去处？”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安身乐业');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安身立命','ān shēn lì mìng','安身在某处安下身来；立命精神有所寄托。指生活有着落，精神有所寄托。','宋·释道原《景德传灯录》卷十僧问‘学人不据地时如何？’师云‘汝向什么处安身立命？’”','那里是镇守边庭，用人之际，足可以～。★明·施耐庵《水浒全传》第二回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安身立命');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安身为乐','ān shēn wéi lè','身子安定就是快乐。','《三国志·蜀书·秦宓传》安身为乐，无忧为福。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安身为乐');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安身之处','ān shēn zhī chù','指得以立足容身的地方。','明·罗贯中《三国演义》第四十回近闻刘景升病在危笃，可乘此机会，取得荆州为安身之地。”','能够找到职业，找到～，该是多么令人高兴啊。★杨沫《青春之歌》第三章','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安身之处');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安身之地','ān shēn zhī dì','存身的地方。批在某地居住、生活，或以某地作为建业的根基。','明·罗贯中《三国演义》第四十回近闻刘景升病在危笃，可乘此机会，取得荆州为安身之地。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安身之地');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安神定魄','ān shén dìng pò','魄魂魄。安定心神，恢复神智。','明·吴承恩《西游记》第十一回自服了安神定魄之剂，……一夜稳睡。”','贾母叫鸳鸯找些～的药，按方吃了。★清·曹雪芹《红楼梦》第九十五回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安神定魄');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安生服业','ān shēng fú yè','指平稳地生活，安心地从事自己的生业。','梁斌《红旗谱》六她想到冯老兰，不一定肯让朱老忠安生服业地活下去，她的心情更加忧怀念不安，害怕有另一种更大的祸事降临家门。”又三十回败家子儿不讲安生服业地做活，只讲吃好的、穿好的、歇着。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安生服业');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安生乐业','ān shēng lè yè','犹安居乐业。指安定愉快地生活和劳动。','《汉书·严助传》使元元之民安生乐业，则泽被万世，传之子孙，使之无穷。”','以是黎民感德，皆～，实有太平之象。★明·张居正《谢召见疏》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安生乐业');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安时处顺','ān shí chǔ shùn','安于常分，顺其自然。形容满足于现状。','《庄子·养生主》安时而处顺，哀乐不能入也。”','他长期以来～，缺乏锻炼，因而经不起挫折。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安时处顺');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安室利处','ān shì lì chù','指安全便利的处所。','《庄子·徐无鬼》奎蹄曲隈，乳间股脚，自以为安室利处。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安室利处');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安适如常','ān shì rú cháng','安静而舒适，象往常一样。指经过某种变动后，恢复了正常，使人感到舒适。','叶圣陶《线下集·孤独》待交了春，天气渐见和暖，就安适如常了。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安适如常');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安土乐业','ān tǔ lè yè','安居本土，愉快地从事自己的职业。','汉扬雄《连珠》吏不苟暴，役赋不重，财力不伤，安土乐业，民之乐也。”','今天下不见兵革之具，而元元～，人致其力，以生天下之财。★宋··王安石《上仁宗皇帝言事书》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安土乐业');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安土重旧','ān tǔ zhòng jiù','指留恋故土，不轻易改变旧俗。','《后汉书·南蛮传》[蛮夷]外痴内黠，安土重旧。”','蛮夷者，外痴内黠，～，以其受异气于天命，故待以不常之律。★晋·干宝《搜神记》卷十四','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安土重旧');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安土重居','ān tǔ zhòng jū','犹安土重迁。指留恋故乡，不愿轻易迁居异地。','《后汉书·杨终传》传曰‘安土重居，谓之众庶。’”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安土重居');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安土重迁','ān tǔ zhòng qiān','土乡土；重看得重，不轻易。安于本乡本土，不愿轻易迁移。','《汉书·元帝纪》安土重迁，黎民之性；骨肉相附，人情所愿也。”','自古道～。”说了离乡背井，那一个不怕的。★明·冯梦龙《东周列国志》第七十八回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安土重迁');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安危相易','ān wēi xiāng yì','易变易，转变。平安与危难互为因果，相互转换。','《庄子·则阳》安危相易，祸福相生，缓解相摩，聚散以成。此名实之可纪，精微之可志也。”','～，祸福相生”，事之由妄想而成者，岂独胚胎然哉？★章炳麟《菌说》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安危相易');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安危与共','ān wēi yǔ gòng','共同享受安乐，共同承担危难。形容关系密切，利害相连。','三国·魏·曹植《求自试表》而臣敢陈闻于陛下者，诚与国分形同气，忧患共之者也。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安危与共');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安闲自得','ān xián zì dé','自得自己感到舒适。安静清闲，感到非常舒适。','明·冯梦龙《醒世恒言·蔡瑞虹忍辱报仇》不如在家安闲自在，快活过了日子，却去讨这样烦恼吃！”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安闲自得');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安闲自在','ān xián zì zài','安静清闲，自由自在。形容清闲无事。','明·李贽《焚书·预约·早晚礼仪》有问乃答，不问即默，安闲自在，从容应付，不敢慢之，不可敬之。”','贤契们不必介怀，只恐朝廷放不下我；若能休致，老夫倒得个～。★清·钱彩《说岳全传》第十三回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安闲自在');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安心定志','ān xīn dìng zhì','指安下心来。','明·吴承恩《西游记》第四回又差五斗星君送悟空去到任，外赐御酒一瓶，金花十朵，着他安心定志，再勿胡为。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安心定志');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安心乐业','ān xīn lè yè','指心绪安定，生活愉快。','《红楼梦》第六八回那二姐得了这个所在，以见园里姐妹个个相好，倒也安心乐业的，自为得所。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安心乐业');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安心乐意','ān xīn lè yì','原指心情安宁，情绪愉快◇多指内心情愿，十分愉快。','清·曹雪芹《红楼梦》第一百十四回我看二嫂子如今倒是安心乐意的孝敬我妈妈，比亲媳妇还强十倍呢。”','自己便～，在寺里住下。★清·吴趼人《九命奇冤》第二十五回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安心乐意');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安心落意','ān xīn luò yì','放心，无忧虑。','无','这就是我刚才对你说的，不要再去想起复做官，～守祖坟的原因所在。你明白吗？★唐浩明《黑雨》十','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安心落意');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安营下寨','ān yíng xià zhài','安、下建立，安置；寨防守用的栅栏。指部队驻扎下来。也比喻建立临时的劳动或工作基地。','元·无名氏《千里独行》杂剧楔子今领十万雄兵，来到这里，离徐州不远，清风岭安营下寨。”','操曰吾料吕布有勇无谋，不足虑也。”教且～，再做商议。★明·罗贯中《三国演义》第十一回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安营下寨');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安营扎寨','ān yíng zhā zhài','安、扎建立，安置；寨防守用的栅栏。指部队驻扎下来。也比喻建立临时的劳动或工作基地。','元·无名氏《两军师隔江斗智》第二折这周瑜匹夫，累累兴兵来索瘸荆州地面，如今在柴桑渡口安营扎寨，其意非小。”','打什么地方进兵，打什么地方退兵，什么地方可以～，什么地方可以埋伏，指手画脚的讲了一通。★清·李宝嘉《官场现形记》第十四回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安营扎寨');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安于故俗，溺于旧闻','ān yú gù sú，nì yú jiù wén','俗习俗。溺沉溺，陷入。拘守于老习惯，局限于旧见闻。形容因循守旧，安于现状。','《史记·商君列传》常人安于故俗，学者溺于所闻。以此两者居官守法可也，非所与论于法之外也。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安于故俗，溺于旧闻');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安于盘石','ān yú pán shí','象盘石一样安然不动。形容安定稳固。','《荀子·富国》为名者否，为利者否，为忿者否。则国安于盘石，寿于旗翼。”杨倞注磐石，盘薄，大石也。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安于盘石');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安于现状','ān yú xiàn zhuàng','对目前的情况习惯了，不愿改变。','刘少奇《论党》另有些同志在工作中疲蹋，老一套，安于现状。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安于现状');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安于一隅','ān yù yī yù','隅角落。安心在某一个角落。形容苟安一角，不求进取。','宋·陈亮《上孝宗皇帝第二书》臣恭惟皇帝陛下厉志复仇，不肯即安于一隅，是有功于社稷也。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安于一隅');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安宅正路','ān zhái zhèng lù','比喻仁义。指以仁居心，以义行事。','《孟子·离娄上》仁，人之安宅也；义，人之正路也。旷安宅而弗居，舍正路而不由，哀哉！”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安宅正路');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安枕而卧','ān zhěn ér wò','放好枕头睡大觉。比喻太平无事，不必担忧。','《史记·黥英列传》使布出于上计，山东非汉之有也；出于中计，胜败之数未可知也；出于下计，陛下安枕而卧矣。”','孔子见疏，必弃鲁国而适他国，君可～矣。★明·冯梦龙《东周列国志》第七十九回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安枕而卧');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安之若命','ān zhī ruò mìng','指人遭受的不幸看作命中注定，因而甘心承受。','《庄子·人间世》知其不可奈何而安之若命，德之至也。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安之若命');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安之若素','ān zhī ruò sù','安安然，坦然；之代词，指人或物；素平常。安然相处，和往常一样，不觉得有什么不合适。','清·范寅《越谚·附论·论堕贫》贪逸欲而逃勤苦，丧廉耻而习谄谀，甘居人下，安之若素。”','第二天宝小姐酒醒，很觉得过意不去◇来彼此熟了，见瞿太太常常如此，也就～了。★清·李宝嘉《官场现形记》第三十八回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安之若素');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '安坐待毙','ān zuò dài bì','坐着等死。指不积极想办法，坐等灭亡。','明·李贽《焚书·复邓鼎石》惟是世人无才无术，或有才术矣，又恐利害及身，百般趋避，故亦遂因循不理，安坐待毙。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '安坐待毙');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '鞍不离马，甲不离身','ān bù lí mǎ，jiǎ bù lí shēn','甲铠甲，古人打仗时穿的护身衣。马不卸鞍，人不解甲。处于高度警惕状态。','唐《敦煌变文集·卷一·汉将王陵变》鞍不离马背，甲不离将身。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '鞍不离马，甲不离身');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '鞍不离马背，甲不离将身','ān bù lí mǎ bèi，jiǎ bù lí jiàng shēn','甲铠甲。马不卸鞍，人不解甲。处于高度警惕状态。','《敦煌变文集·卷一·汉将王陵变》鞍不离马背，甲不离将身。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '鞍不离马背，甲不离将身');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '鞍马劳顿','ān mǎ láo dùn','顿困顿。骑马赶路过久，劳累疲困。形容旅途劳累。','元·杨显之《潇湘雨》第四折兴儿，我一路上鞍马劳顿，我权且歇息。”','恐王侄一路远来，～，故令王侄回营安歇。★清·钱彩《说岳全传》第四二回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '鞍马劳顿');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '鞍马劳倦','ān mǎ láo juàn','指长途跋涉或战斗中备尝困乏。','明·施耐庵《水浒传》第二回实不相瞒太公说老母鞍马劳倦，昨夜心痛病发。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '鞍马劳倦');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '鞍马劳困','ān mǎ láo kùn','指长途跋涉或战斗中备尝困乏。','元·关汉卿《窦娥冤》第四折不觉的一阵昏沉上来，皆因老夫年纪高大，鞍马劳困之故。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '鞍马劳困');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '鞍马劳神','ān mǎ láo shén','形容旅途劳累。同鞍马劳顿”。','无','祝龙答道也未见胜败。众位尊兄～不易。”★《水浒传》第四十九回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '鞍马劳神');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '鞍马之劳','ān mǎ zhī láo','鞍马鞍子和马，指长途跋涉或打仗。形容旅途或战斗的劳苦。','明·许仲琳《封神演义》第二十二回耳不闻兵戈之声，眼不见伐之事，身不受鞍马之劳，心不悬胜败之扰。”','唐铉说此话以后再说。请到那边入座，少饮几杯水酒，以解二位～。”★姚雪垠《李自成》第三卷第三十四章','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '鞍马之劳');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '鞍前马后','ān qián mǎ hòu','马前马后。指追随左右。','王树元《杜鹃山》第五嘲前马后跟你跑，出生入死为你干。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '鞍前马后');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '岸谷之变','àn gǔ zhī bìan','比喻政治上的重大变化。','《诗·小雅·十月之交》高岸为谷，深谷为陵。”毛传言易位也。”郑玄笺易位者，君子居下小人处上之谓也。”','属当岸谷之变，门户衰微，无能光其大业。★清顾炎武《贞烈堂记》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '岸谷之变');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '岸然道貌','àn rán dào mào','指严肃的神态。','清·蒲松龄《聊斋志异·成仙》又八九年，成忽自至，黄巾氅服，岸然道貌。”','中国士人，又有一种～，根器特异，别树一帜，以号于众者。★清·邹容《革命军》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '岸然道貌');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '按兵不动','àn bīng bù dòng','按止住。使军队暂不行动。现也比喻暂不开展工作。','《荀子·王制》偃然按兵不动。”《吕氏春秋·恃君览》赵简子按兵而不动。”','又听得寨前炮响，史文恭～，只要等他入来，塌了陷坑，山后伏兵齐起，接应捉人。★明·施耐庵《水浒全传》第六十八回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '按兵不动');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '按兵不举','àn bīng bù jǔ','犹按兵不动。','元·无名氏《开诏救忠》第三折你将那忠臣良将故赃谋，想着你按兵不举心更毒。”明·无名氏《精忠记·挂冠》岳家父子三人尽忠报国杀金人望风而走，岂有按兵不举之理？”','侯虎曰‘西伯侯姬昌好生可恶！今～，坐观成败。’★明·许仲琳《封神演义》第四回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '按兵不举');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '按兵束甲','àn bīng shù jiǎ','按兵军队停止行进。甲铠甲，泛指武器装备。止兵不前，捆起铠甲。指解除武装，停战言降。','《赤壁之战》何不按兵束甲，北面而事之！”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '按兵束甲');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '按步就班','àn bù jiù bān','指按照条理或遵循一定的程序。参见按部就班”。','语出晋·陆机《文赋》观古今于须臾，抚四海于一瞬。然后选义案部，考辞就班。”','学校却，没有这弊病。★鲁迅《书信集·致陶亢德》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '按步就班');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '按部就班','àn bù jiù bān','部、班门类，次序；就归于。按照一定的步骤、顺序进行。也指按老规矩办事，缺乏创新精神。','晋·陆机《文赋》然后选义案部，考辞就班。”','但黄公俊的祖父，他开始读了书，象一般读书人似的，他～的要将八股型的才学，货与帝王家”。★郑振铎《黄公俊之最后》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '按部就班');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '按堵如故','àn dǔ rú gù','形容秩序良好，百姓和原来一样安居乐业。','《汉书·高帝纪上》与父老约法三章耳杀人者死，伤人及盗抵罪。余悉除去秦法。吏民皆按堵如故。”颜师古注应劭曰‘按，按次第。堵，墙堵也。’言不迁动也。”','凡军民约三日入籴官米一斗，入昏钞二贯，民皆便之。由是～，而贼亦不敢犯其境。★《元史·道童传》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '按堵如故');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '按甲寝兵','àn jiǎ qǐn bīng','收拾起铠甲武器。比喻停止军事行动。','晋·司马彪《九州春秋·傅干》愚以为且按甲寝兵，息养军士，定土分封，论功行赏。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '按甲寝兵');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '按甲休兵','àn jiǎ xiū bīng','收拾起铠甲武器。比喻停止军事行动。','《汉书·韩信传》当今之计，不如按甲休兵，百里之内，牛洒日至，以飨士大夫，北首燕路，然而发一乘之使，奉咫尺之书以使燕，燕必不敢不听。”','将军可～，退一箭之地。★明·李日华《南西厢记·许婚借援》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '按甲休兵');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '按名责实','àn míng zé shí','按照事物名称，要求与实相符。','唐·陆贽《请许台省长官举荐属吏状》夫求才贵广，孝课贵精。求广在于各举所知，长吏之荐择是也；孝精在于按名责实，宰臣之序进是也。”','及～，台有评，省有核。★明·李东阳《漳州府进士提名记》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '按名责实');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '按捺不住','àn nà bù zhù','按捺压抑，忍耐。心里急燥，克制不住。','明·冯梦龙《警世通言·白娘子水镇雷峰塔》不想遇着许宣……按捺不住，一时冒范天条。”','他一听这话，一股无名火直往上窜，无论如何也～。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '按捺不住');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '按辔徐行','àn pèi xú xíng','辔马缰绳。轻轻按着缰绳，让马慢慢地走。','《史记·绛侯周勃世家》壁门士吏谓从属车骑曰‘将军约，军中不得驱驰。’于是天子乃按辔徐行。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '按辔徐行');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '按图索骥','àn tú suǒ jì','索找；骥良马。按照画像去寻求好马。比喻墨守成规办事；也比喻按照线索去寻求。','《汉书·梅福传》今不循伯者之道，乃欲以三代选举之法取当时之士，犹察伯乐之图求骐骥于市，而不可得，变已明矣。”','每见一班～者，多失于骊黄牝牡。★明·赵沺《葬书问对》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '按图索骥');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '按图索骏','àn tú suǒ jùn','指按照画像去寻求好马。比喻墨守成规办事；也比喻按照线索去寻求。参见按图索骥”。','语出《汉书·梅福传》今不循伯者之道，乃欲以三代选举之法取当时之士，犹察伯乐之图求骐骥于市，而不可得，变已明矣。”','虽部胥掌阙簿者，亦不过～。★宋·周密《癸辛杂识续集下·卖阙沈官人》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '按图索骏');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '按行自抑','àn xíng zì yì','按克制。约束自己的行为。','唐·柳宗元《梦赋》忽崩赛上下兮，聊按行以自抑。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '按行自抑');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '案兵束甲','àn bīng shù jiǎ','放下兵器，捆束铠甲。指停止作战。','《三国志·蜀志·诸葛亮传》若不能当，何不案兵束甲，北面而事之！”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '案兵束甲');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '案牍劳形','àn dú láo xíng','案牍公文。文书劳累身体。形容公事繁忙。','唐·刘禹锡《陋室铭》无丝竹之乱耳，无案牍之劳形。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '案牍劳形');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '案牍之劳','àn dú zhī láo','案牍公文。办理公文的劳累。','唐·刘禹锡《陋室铭》无丝竹之乱耳，无案牍之劳形。”','吾徒幸无～，且有休退之日，登高能赋，此其时乎？★明·李桢《长安夜行录》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '案牍之劳');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '案甲休兵','àn jiǎ xiū bīng','指停止战事，休养士卒。','《史记·淮阴侯列传》方今为将军计莫如案甲休兵，镇赵抚其孤。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '案甲休兵');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '案剑瞋目','àn jiàn chēn mù','案同按”，握住；瞋瞪眼。握住宝剑，瞪大双眼。','《庄子·盗跖》案剑瞋目，声如乳虎。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '案剑瞋目');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '案无留牍','àn wú liú dú','桌案上没有积压的公文。形容办理公务干练、及时。','清·朱彝遵《商丘宋氏家乘》序一十七年，国奢示之以俭，事烦行之以简，月要岁会，久而案无留牍。”','一应公事，张令都听他决断。真是～，狱无冤刑，民心大悦。★清·黄小配《洪秀全演义》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '案无留牍');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗锤打人','àn chuí dǎ rén','比喻暗中对人进行攻击。','《人民文学》1997年第7期哪个鬼东西敢出来跟我说说理？暗锤打人，是土行孙出身！”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗锤打人');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗淡无光','àn dàn wú guāng','暗淡不明亮，昏暗。形容失去光彩。','清·文康《儿女英雄传》第三十四回头上戴一个黯淡无光的亮蓝顶儿，那枝俏摆春风的孔雀瓴已经虫蛀的剩了光杆儿了。”','太阳过了，整个天空又显得～，北风在头项上呼呼地嚎叫着。（欧阳山《苦斗》五八）','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗淡无光');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗斗明争','àn dòu míng zhēng','暗里明里都在进行斗争。常形容尽力争权夺利。','李六如《六十年的变迁》第二卷第七章南方的桂滇黔等小军阀，暗斗明争，弄得全中国民不聊生！中国政治真糟啦！”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗斗明争');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗度陈仓','àn dù chén cāng','指正面迷惑敌人，而从侧翼进行突然袭击。亦比喻暗中进行活动。陈仓，古县名，在今陕西省宝鸡市东，为通向汉中的交通孔道。','《史记·高祖本纪》……项王使卒三万人从，楚与诸侯之慕从者数万人，从杜南入蚀中。去辄烧绝栈道，以备诸侯盗兵袭之，亦示项羽无东意……八月，汉王用韩信之计，从故道还，袭雍王章邯—迎击汉陈仓，雍兵败，…','臣已算定今番诸葛亮必效韩信～之计。★《三国演义》第九六回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗度陈仓');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗度金针','àn dù jīn zhēn','度通渡”，过渡，引申为传授◇用于比喻秘诀。又借指幕后交易。','见唐·冯翊子休《桂苑丛谈·史遗》。传说有名叫郑采珠的姑娘，七夕祭织女，织女送她一根金针，从此她刺绣的技能更为精巧。','日本政府，向来主张～，我国虽尚守中立，未曾加入协约团体，但日本如此举动，本使也很不赞成。★蔡东藩《民国演义》第六十回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗度金针');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗渡陈仓','àn dù chén cāng','渡越过；陈仓古县名，在今陕西省宝鸡市东。比喻用造假象的手段来达到某种目的。也隐喻男女私通。','元·无名氏《暗渡陈仓》第二折着樊哙明修栈道，俺可暗度陈仓古道。这楚兵不知是智，必然排兵在栈道守把。俺往陈仓古道抄截，杀他个措手不及也。”','孤家用韩信之计，明修栈道，～，攻定三秦，动取五国。★元·无名氏《气英布》第一折','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗渡陈仓');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗箭明枪','àn jiàn míng qiāng','比喻各种公开的攻击和暗地里的中伤。','《群英类选·清腔类·玉交枝》忽心痴撞入他迷魂阵图里，暗箭明枪难躲避。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗箭明枪');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗箭难防','àn jiàn nán fáng','冷箭最难防范。比喻阴谋诡计难以预防。','宋·刘炎《迩言》卷六暗箭中人，其深次骨，人之怨之，亦必次骨，以其掩人所不备也。”','孩儿也，一了说明枪好躲，～。★元·无名氏《独角牛》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗箭难防');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗箭伤人','àn jiàn shāng rén','放冷箭伤害人。比喻暗地里用某种手段伤害人。','宋·刘炎《迩言》卷六暗箭中人，其深次骨，人之怨之，亦必次骨，以其掩人所不备也。”','有荼毒生灵的强盗，有～的强盗。★清·李汝珍《镜花缘》第五十八回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗箭伤人');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗箭中人','àn jiàn zhòng rén','暗暗中；箭放箭；中中伤。放冷箭伤害人。比喻暗中用阴险的手段攻击或陷害别人。','宋·刘炎《迩言》卷六暗箭中人，其深次骨，人之怨之，亦必次骨，以其掩人所不备也。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗箭中人');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗绿稀红','àn lǜ xī hóng','犹言绿肥红瘦。形容叶盛花稀的暮春景象。','明·陈汝元《金莲记·归田》况当残春初夏之时，正值暗绿稀红之候，顿起愁绪，怎生消闷怀？”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗绿稀红');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗昧之事','àn mèi zhī shì','指见不得人的丑事。','清·石玉昆《三侠五义》第三十二回庞吉你乃堂堂国戚，如何行此小人暗昧之事。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗昧之事');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗气暗恼','àn qì àn nǎo','受了气闷在心里。','明·兰陵笑笑生《金瓶梅词话》第六十二回又着了那暗气暗恼在心里，就是铁石人也禁不的。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗气暗恼');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗弱无断','àn ruò wú duàn','愚昧软弱，没有决断。','《资治通鉴·卷九十一·晋元帝太兴三年》(司马保)好读书而暗弱无断，故及于难”。','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗弱无断');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '暗室不欺','àn shì bù qī','在没有人看见的地方，也不做见不得人的事。','唐·骆宾王《萤火赋》类君子之有道，入暗室而不欺。”','知县生平～，既读孔、孟之书，怎敢行盗跖之事？★明·冯梦龙《醒世恒言》卷十三','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '暗室不欺');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百不获一','bǎi bù huò yī','一百个里面也得不到一个。形容人或物的难得或所得极少。','清·李绿园《歧路灯》第一百五回人品自会端正，文移自会清顺，晓畅，然着实是百不获一的。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百不获一');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百不失一','bǎi bù shī yī','一百次中无一次失误。表示射箭或打枪命中率高，或做事有充分把握。','汉·王充《论衡·须颂》从门应庭，听堂室之言，什而失九，如升堂窥室，百不失一。”','他把算子一动，便晓得这人姓名，穷通寿夭，～。★明·凌濛初《初刻拍案惊奇》卷七','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百不失一');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百不为多，一不为少','bǎi bù wéi duō，yī bù wéi shǎo','指难得的好人或好东西。','《南史·任昿传》褚彦回尝谓遥曰‘闻卿有令子，相为喜之。所谓百不为多，一不为少。’由是闻声藉甚。”','然不难得则不足为佳物，古人亦云‘～’者，谓此也。★宋·赵彦卫《云麓漫钞》卷十四','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百不为多，一不为少');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百不一存','bǎi bù yī cún','一百个里面没有留下一个。表示丧失殆尽。','北周·宇文逌《〈庾信集〉序》昔在阳都，有集十四卷，值太清罹乱，百不一存。”《南史·蔡廓传附蔡兴宗》时士庶危惧，衣冠咸欲远徙，后皆流离外难，百不一存。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百不一存');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百不一贷','bǎi bù yī dài','犹言无一宽免。','《新唐书·酷吏传·来俊臣》后信之，诏于丽景门别置狱，敕俊臣等颛按事，百不一贷。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百不一贷');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百不一遇','bǎi bù yī yù','一百次中遇不到一次。形容极其难得。','汉·荀悦《汉纪·哀帝纪下》言立策成，终无咎悔，若此之事，百不一遇，其知之所见，万不及一也。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百不一遇');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百步穿杨','bǎi bù chuān yáng','在一百步远以外射中杨柳的叶子。形容箭法或枪法十分高明。','《史记·周本纪》楚有养由基者，善射者也，去柳叶百步而射之，百发而百中之。”《战国策·西周策》楚有养由基者，善射；去柳叶百步而射之，百发百中。”','（关羽）带箭回寨，方知黄忠有百步穿杨之能。★明·罗贯中《三国演义》第五十三回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百步穿杨');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百城之富','bǎi chéng zhī fù','形容藏书极多，似拥有许多城市那样富有。','语出《魏书·李谧传》丈夫拥书万卷，何假南面百城。”','强解事者，以数百金捆载坊书，便称～。★清·黄宗羲《天一阁藏书记》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百城之富');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百尺竿头','bǎi chǐ gān tóu','桅杆或杂技长竿的顶端。比喻极高的官位和功名，或学问、事业有很高的成就。','唐·吴融《商人》诗百尺竿头五两斜，此生何处不为家。”','功名～，自古及今，有几个干休。★元·张养浩《折桂令》曲','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百尺竿头');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百尺竿头，更进一步','bǎi chǐ gān tóu，gèng jìn yī bù','佛家语，比喻道行、造诣虽深，仍需修炼提高。比喻虽已达到很高的境地，但不能满足，还要进一步努力。','宋·释道原《景德传灯录》卷十师示一偈曰‘百丈竿头不动人，虽然得入未为真，百尺竿头须进步，十方世界是全身。’”','故聊复言之，恐或可以少助～之势也。★宋·朱熹《答巩仲至书》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百尺竿头，更进一步');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百尺无枝','bǎi chǐ wú zhī','比喻罕见的大而有用之材。','汉·枚乘《七发》龙门之桐，高百尺而无枝。”','其风韵遒正，神峰标映，千里绝迹，～。★《梁书·王规传》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百尺无枝');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百川朝海','bǎi chuān cháo hǎi','众水奔流趋向大海。多比喻无数分散的事物都汇集到一处。','汉·焦赣《易林·谦之无妄》百川朝海，流行不止，道虽辽远，无不到者。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百川朝海');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百川赴海','bǎi chuān fù hǎi','百表示多。川江河。所有江河都流归大海。比喻众望所归或大势所趋。也比喻许多事物由分散而汇集到一处。','《朱子语类·卷二·理气下》百川赴海而海不溢。”','许多文士在饱经战乱之苦后，如～，相继奔往邺城，归附到曹氏（曹操）周围。★《建安文学》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百川赴海');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百川归海','bǎi chuān guī hǎi','川江河。许多江河流入大海。比喻大势所趋或众望所归。也比喻许多分散的事物汇集到一个地方。','《淮南子·汜论训》百川异源，而皆归于海。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百川归海');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百辞莫辩','bǎi cí mò biàn','用任何话语都无法辩白。','鲁迅《准风月谈·后记》被责难者处于时势潮流之下，百辞莫辩，辩则反动更为证实。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百辞莫辩');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百代过客','bǎi dài guò kè','指时间永远流逝。','唐·李白《春夜宴从弟桃花园序》夫天地者，万物直逆旅也；光阴者，百代直过客也。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百代过客');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百代文宗','bǎi dài wén zōng','宗被人所效法的人物。在久远的年代里堪为文人楷模的人物。','《晋书·陆机传》制曰‘故足远超枚（枚乘）马（司马相如），……百代文宗，一人而已。’”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百代文宗');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百动不如一静','bǎi dòng bù rú yī jìng','指多动不如静待有效。','清·西周生《醒世姻缘传》第三十四回你去了，我又寻思，百动不如一静的。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百动不如一静');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百读不厌','bǎi dú bù yàn','厌厌烦，厌倦。读一百遍也不会感到厌烦。形容诗文或书籍写得非趁，不论读多少遍也不感到厌倦。','宋·苏轼《送安惇秀才失解西归》诗旧书不厌百回读，熟读深思子自知。”','为什么一些作品有人～”，另一些却有人不想读第二遍呢？★朱自清《论百读不厌》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百读不厌');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百堵皆作','bǎi dǔ jiē zuò','堵墙；作兴建。许多房屋同时建造。','《诗经·小雅·鸿雁》之子于垣，百堵皆作。虽则劬劳，其究安宅。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百堵皆作');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百端待举','bǎi duān dài jǔ','面端百种；举举办。有很多事情等着要兴办。','周恩来《人民政协共同纲领草案的要点》经济建设是百端待举，但须有缓急轻重之分。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百端待举');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百端交集','bǎi duān jiāo jí','无数感想交互汇集，形容感慨万千。','南朝·宋·刘义庆《世说新语·言语》卫洗马初欲渡江，形神惨悴，语左右云‘见此芒芒，不觉百端交集，苟未免有情，亦复谁能遣此！’”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百端交集');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百堕俱举','bǎi huī jù jǔ','堕荒废；废弃。指一切废置的事都兴办起来。同百废俱兴”。','钱基博《吴禄贞传》在边三年，百堕俱举，而刊有《延吉边务报告》四册，则其客江阴章钟祚搜辑禄贞在边之书牍也。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百堕俱举');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百二关河','bǎi èr guān hé','比喻山河险固之地。同百二山河”。','金·元好问《岐阳》诗之二百二关河草不横，十年戎马暗秦京。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百二关河');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百二河山','bǎi èr hé shān','百二以二敌百。指山河险固，可以二敌百◇指国力强盛，边防稳固的国家。','《史记·高祖本纪》秦，形胜之国带河山之险，悬隔千里，持戟百万，秦得百二焉。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百二河山');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百二金瓯','bǎi èr jīn ōu','比喻山河险固之地。同百二山河”。','宁调元《壬子感事》诗之三五千貂锦张旗鼓，百二金瓯资栋梁。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百二金瓯');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百二山川','bǎi èr shān chuān','比喻山河险固之地。同百二山河”。','《全元散曲·新水令》随人愿、照百二山川，照百二山川，一点金星瑞云里现。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百二山川');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百二山河','bǎi èr shān hé','百二以二敌百。比喻山河险固之地。','西汉·司马迁《史记·高祖本纪》秦，形胜之国，带河山之险，县隔千里，持戟百万，秦得百二马。”','～同败絮，两三亲友各飘萍。★宁调元《清明忆亡友姚宏业》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百二山河');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百凡待举','bǎi fán dài jǔ','无数事情都等待兴办。','平佚《临时政府成立记》而沪军陈都督，复以民军倡义伊始，百凡待举，无总机关以代表全国，外人疑虑，交涉为难。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百凡待举');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百废待举','bǎi fèi dài jǔ','废被废置的事情；待等待；举兴办。许多被搁置的事情等着要兴办。','吴晗《海瑞罢官》百废待举，他不出头做主，实在令人着急。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百废待举');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百废待兴','bǎi fèi dài xīng','废被废置的事情；待等待；兴兴办。许多被搁置的事情等着要兴办。','王颖《海外赤子》百废待兴，一下子怎么能抓得过来。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百废待兴');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百废具举','bǎi fèi jù jǔ','指许多被废置的事业都等着兴办。同百废俱举”。','宋·王明清《挥麈录》卷一吴明可帅会稽，百废具举，独不传书。”','其有能如水野越前、井伊直弼之鞠躬尽瘁～者乎？★梁启超《敬告当道者》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百废具举');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百废具兴','bǎi fèi jù xīng','指一切废置的事都兴办起来。同百废俱兴”。','宋·苏轼《谢量移汝州表》建原庙以安祖考，正六宫而修典刑，百废具兴，多士爰集。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百废具兴');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百废具作','bǎi fèi jù zuò','指一切废置的事都兴办起来。同百废俱兴”。','清·管同《恩县四女祠碑》阳湖孙使君督粮山东，起痿瞭矇，百废具作。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百废具作');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百废俱举','bǎi fèi jù jǔ','指一切废置的事都兴办起来。同百废俱兴”。','《续资治通鉴·宋理宗绍定五年》了翁（魏了翁）乃葺其城楼橹雉堞，增置器械，教习牌手，申严军律……居数月，百废俱举。”','‘～’的平均主义思想，以及与此相关联着的不合边区实际的‘正规化’思想，成了贯彻精简政策与集中力量于两大任务的绊脚石。★《解放日报》1942.12.23','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百废俱举');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百废俱兴','bǎi fèi jù xīng','俱全，都。许多已经荒废了的事情一下子都兴办起来。','宋·范仲淹《岳阳楼记》政通人和，百废具（俱）兴。”','四川屡经兵燹，廷相莅任，～，民不知扰。★《清史稿·郎廷佐传》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百废俱兴');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百废咸举','bǎi fèi xián jǔ','指一切废置的事都兴办起来。同百废俱兴”。','明·刘基《杭州路重修府治记》公受命来杭未及期月，威惠大行，百废咸举。吏民顺令，如臂使指。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百废咸举');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百福具臻','bǎi fú jù zhēn','形容各种福运一齐来到。','《旧唐书·李藩传》伏望陛下每以汉文孔子之意为准，则百福具臻。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百福具臻');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百感交集','bǎi gǎn jiāo jí','感感想；交同时发生。各种感触交织在一起。形容感触很多，心情复杂。','南朝·宋·刘义庆《世说新语·言语》见此茫茫，不觉百端交集，苟未免有情，亦复谁能遣此。”','我是上一个世纪出生的人，能参加这样的盛会，～，思绪万千。★郭沫若《科学的春天》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百感交集');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百怪千奇','bǎi guài qiān qí','形容花样繁多。','清·李渔《闲情偶寄·饮馔·蔬食》世人制菜之法，可称百怪千奇。自新鲜以至于腌糟酱腊，无一不曲尽奇能，务求至美。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百怪千奇');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百鬼众魅','bǎi guǐ zhòng mèi','各种妖魔鬼怪。','《后汉书·方术传下·解奴辜》章帝时有寿光侯者，能劾百鬼众魅，令自缚见形。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百鬼众魅');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百花齐放','bǎi huā qí fàng','形容百花盛开，丰富多彩。比喻各种不同形式和风格的艺术自由发展。也形容艺术界的繁荣景象。','清·李汝珍《镜花缘》第三回百花仙子只顾在此著棋，那知下界帝王忽有御旨命他百花齐放。”','美术展览会上展出了许多新作品，～，各具风格。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百花齐放');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百花齐放，百家争鸣','bǎi huā qí fàng，bǎi jiā zhēng míng','比喻艺术及科学的不同派别及风格自由发展与争论。','毛泽东《关于正确处理人民内部矛盾的问题》百花齐放、百家争鸣的方针，是促进艺术发展和科学进步的方针，是促进我国的社会主义文化繁荣的方针。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百花齐放，百家争鸣');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百花生日','bǎi huā shēng rì','旧指阴历二月十二日，即花朝。','清·秦味芸《月令粹编》卷五《陶朱公书》‘二月十二日为百花生日。无雨，百花熟。’”','每年二月十二日，相传是～。★清·吴趼人《情变》第八回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百花生日');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百花争妍','bǎi huā zhēng yàn','妍美、艳。形容繁华盛开，生气勃勃的景象。','无','春风一到，公园里百花争妍。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百花争妍');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百花争艳','bǎi huā zhēng yàn','各种花草树木竞相开放出艳丽的花朵。','无','北方的夏天绿树成荫，百花争艳。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百花争艳');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百卉千葩','bǎi huì qiān pā','常比喻事物丰富多彩，景象繁荣兴盛。','明·无名氏《紫微宫》第二折仲冬佳节景堪褒，百卉千葩逞艳妖。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百卉千葩');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百喙莫辩','bǎi huì mò biàn','喙嘴，借指人的嘴。指纵有众多的嘴也不能辩解。同百口莫辩”。','宋·陈亮《谢何正言启》谤出事情之外，百喙莫辩；变生意料之余，三肱并折。”','素臣一段议论，如老吏断狱，使刘邦～。★清·夏敬渠《野叟曝言》第七十一回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百喙莫辩');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百喙难辩','bǎi huì nán biàn','喙嘴，借指人的嘴。指纵有众多百口也很难辩解。同百口莫辩”。','蔡东藩《五代史演义》第三十六回此时证据显然，百喙难辩。荣复证成延广罪案十条，每服一事，即授一筹。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百喙难辩');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百喙难辞','bǎi huì nán cí','喙嘴，借指人的嘴。指纵有众多百口也很难解释。','清·夏燮《中西纪事·江上议款》江自以卖城之罪百喙难辞，因乘间自尽。”','刘棻等已被扳入，～，遂都连坐罔上不道的罪名，谳成死罪。★蔡东藩《后汉演义》第一回','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百喙难辞');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百喙如一','bǎi huì rú yī','犹言众口一辞。许多人都说同样的话，看法或意见一致。','清·王夫之《孝烈传》唯习闻湘人之言，百喙如一者若此。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百喙如一');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百喙一词','bǎi huì yī cí','犹言众口一辞。同百喙如一”。','清·龚自珍《大誓答问第一》儒者百喙一词，言伏生《尚书》二十八篇。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百喙一词');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百计千方','bǎi jì qiān fāng','想尽或用尽一切办法。','宋·辛弃疾《一枝花》百计千方久，似斗草儿童，赢箇他家偏有。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百计千方');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百计千谋','bǎi jì qiān móu','想尽或用尽一切办法。同千方百计”。','元·尚仲贤《单鞭夺槊》第二折据着他全忠尽孝真良将，怎肯做背义忘恩那死囚，干费了百计千谋。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百计千谋');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百计千心','bǎi jì qiān xīn','指想尽或用尽一切办法。','明·陆采《明珠记·授计》你爹怜母惜，你爹怜母惜，当时许下偕老鸳衾；到如今，用尽了百计千心，只落得泪珠儿罗衫湿浸。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百计千心');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百家争鸣','bǎi jiā zhēng míng','指各种学术流派的自由争论互相批评。也指不同意见的争论。百家，这种观点的人或各种学术派别。鸣，发表见解。','《汉书·艺文志》凡诸子百八十九家……蜂出并作，各引一端，崇其所说，以此弛说，取舍诸候。”','自春秋末年以来，中国的思想得到一个极大的开放，呈现出一个～的局面。★郭沫若《吕不韦与秦王政的批判》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百家争鸣');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百结愁肠','bǎi jié chóu cháng','指愁绪如结无法解开。','宋·蒋兴祖女《减字木兰花·题雄州驿》词飞鸿过也，百结愁肠无昼夜。”','可怜我神思恍然饮食废，我为你～解不开。★越剧《梁山泊与祝英台》第十场','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百结愁肠');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百结悬鹑','bǎi jié xuán chún','比喻衣衫破烂。鹑鸟尾秃，像补缀百结，故云。','《黑籍冤魂》第二十回乞丐身上，破衣褴褛，百结悬鹑，怎抵得住那一天风雪？”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百结悬鹑');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百举百捷','bǎi jǔ bǎi jié','做一百件事，成功一百件。指办事万无一失。同百举百全”。','《三国志·吴志·周鲂传》鲂生在江淮，长于时事，见其便利，百举百捷。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百举百捷');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百举百全','bǎi jǔ bǎi quán','每次行动都能完成其事，形容事事得心应手，都能取得好的效果。','《三国志·魏志·郭嘉传》百举百全，而功名可立也。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百举百全');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百孔千疮','bǎi kǒng qiān chuāng','比喻毛病很多，问题严重，已经到了难以收拾的地步。','唐·韩愈《与孟尚书书》汉室以来，群儒区区修补，百孔千疮，随乱随失，其危如一发引千钧。”','他身上的缺点虽然不少，但却不是～，不可救药。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百孔千疮');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百孔千创','bǎi kǒng qiān chuāng','比喻困苦不堪。同百孔千疮”。','宋·周必大《跋宋运判昞奏稿》黎庶凋瘵，百孔千创。”','一方赛会，万户供张，竟有勉强支持，百端借贷而入会者；亦有典衣粜米，～而入会者。★清·钱泳《履园丛话·笑柄·恶俗》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百孔千创');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百口莫辩','bǎi kǒu mò biàn','莫不能；辩辩白。即使有一百张嘴也辩白不清。形容不管怎样辩白也说不清楚。','宋·刘过《建康狱中上吴居父》虽有百口而莫辩其辜。”','玉林和忠和醒悟了，一打便是祸事，若是李三顺身上有了伤，便～，官司”非输不可。★高阳《清宫外史》上册','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百口莫辩');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百口难分','bǎi kǒu nán fēn','即使有一百张嘴也难分辩清楚。犹百口莫辩。','清·魏秀仁《花月痕》第三回这‘薄幸’两字，我也百口难分了！”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百口难分');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百口同声','bǎi kǒu tóng shēng','大家所说的都一样。','清·魏秀仁《花月痕》第五十一回时已亭午，小珠跌坐坟下，哭个不住。末后秃头与跟人劝止，大众百口同声，小珠方停了哭，谢了善人村父老，就到秃头家来。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百口同声');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百里挑一','bǎi lǐ tiāo yī','一百个当中就挑出这一个来。形容人才出众。','清·曹雪芹《红楼梦》第一百二十回姑爷年纪略大几岁，并没有娶过的，况且人物儿长的是百里挑一的。”','对报名参军的人挑选很严格，可以说是～。','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百里挑一');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百里之才','bǎi lǐ zhī cái','百里方圆百里之地。指能治理方圆百里地区的人才◇称才能平常的人。','《三国志·蜀志·蒋琬传》蒋琬，社稷之器，非百里之才也。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百里之才');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百炼成刚','bǎi liàn chéng gāng','刚通钢。铁经过反复锤炼便成为坚韧的钢。比喻人经过多次磨炼而成为有用之才。','郭小川《保卫我们的党》诗点起斗争的烈火，使人在烈火中百炼成刚。”','有人身经百战，～，炉火纯青，喜于心而不形于色。★徐迟《火中凤凰·凤翔》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百炼成刚');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百炼成钢','bǎi liàn chéng gāng','比喻经过长期锻炼，变得非常坚强。','汉·陈琳《武军赋》铠则东胡阙巩，百炼精刚。”','他周大勇的战士却在战争中～，精通了打击敌人的本领。★杜鹏程《保卫延安》第七章','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百炼成钢');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百了千当','bǎi liǎo qiān dāng','了了结；当妥当；百、千指一切事。比喻事事妥贴，有着落。','宋·释道原《景德传灯录》卷十三问‘百了千当时如何？’师曰‘不许夜行，投明须到。’”','不如屏净尘缘，竖起脊梁骨，著些精彩，究教七川八穴，～，向水边林下，长养圣胎，亦不枉受人天供养。★宋·释惟白《续传灯录》','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百了千当');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百伶百俐','bǎi líng bǎi lì','形容非常聪明乖巧。','明·马梦龙《醒世恒言》第二十七卷那焦氏生得有六七分颜色，女工针指，却也百伶百俐；只是心有些狠素。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百伶百俐');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百龄眉寿','bǎi líng méi shòu','眉寿长寿。祝人高寿的颂辞。','唐·虞世南《琵琶赋》愿百龄兮眉寿，重千金之巧笑。”','无','','',3,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百龄眉寿');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百虑一致','bǎi lǜ yī zhì','指使各种不同的思想归于一致。','语出《易·系辞下》天下何思何虑，天下同归而殊涂，一致而百虑。”','及躬总大政，一日万机，十许年间，曾不暇给，殊涂同归，～。★《北史·魏纪三·孝文帝纪论》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百虑一致');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百虑攒心','bǎi lǜ zǎn xīn','各种思虑一齐聚集在心头。','无','自从这匆匆一别，他～，久久不能平静。','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百虑攒心');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百媚千娇','bǎi mèi qiān jiāo','媚、娇美好。形容女子姿态美好。','南朝·陈·徐陵《杂曲》诗绿黛红颜两相发，千娇百态情无歇。”唐·张文成《游仙窟》千娇百媚，造次无可比方；弱体轻身，谈之不能备尽。”','倾国倾城，～。★元·孔文昻《折桂令·赠千金奴》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百媚千娇');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百密一疏','bǎi mì yī shū','在极周密的考虑中偶然出现了一点疏忽。','清·魏源《庸易通义》至道问学之有知无行，分温故为存心，知新为致知，而敦厚为存心，崇礼为致知，此皆百密一疏。”','深知杜竹斋为人的吴荪甫此时却～，竟没有看透竹斋的心曲。他一而再，再而三的，用鼓励，用反激。★茅盾《子夜》十','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百密一疏');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百谋千计','bǎi móu qiān jì','犹千方百计。','元·关汉卿《魔合罗》第四折这其间详细，索用心机，要搜寻百谋千计。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百谋千计');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百年不遇','bǎi nián bù yù','一百年也碰不到一次。形容很少见到过或少有的机会。','老舍《龙须沟》第三幕不是要开大会吗？百年不遇的事，我歇半天工，好开会去。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百年不遇');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百年大计','bǎi nián dà jì','大计长远的重要的计划。指关系到长远利益的计划或措施。','清·梁启超《论民族竞争之大势》数月之间，而其权力已深入巩固，而百年大计于以定矣。”','～，教育为本。','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百年大计');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百年大业','bǎi nián dà yè','指关系到长远利益的重要计划或措施。同百年大计”。','无','百年大业，教育为本。','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百年大业');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百年好合','bǎi nián hǎo hé','夫妻永远和好之意。','《粉妆楼》第一回百年和合，千载团圆恭喜！”','今日你们缔结了良缘，祝福你们～，万事如意。','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百年好合');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百年好事','bǎi nián hǎo shì','指婚姻喜事。','明·凌濛初《二刻拍案惊奇》卷九我与你姐姐百年好事，千金重担只在此两件上面了！万望龙香姐竭力周全，讨个回音则个。”','你还是牺牲一切，替浪子做撮合山，你还是使浪子少犯几层罪过，成就你自己的～呢？★郭沫若《卓文君》第三景','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百年好事');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百年难遇','bǎi nián nán yù','百年也碰不到。形容罕见或很不容易碰到。','清·文康《儿女英雄传》第九回若说一样的动心，把这等终身要紧的大事、百年难遇的良缘，倒扔开自己，双手送给我这样一个初次见面旁不相干的张金凤，尤其不是情理。”','这种怪事～，今天倒开了眼界。','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百年难遇');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百年树人','bǎi nián shù rén','树种植，栽培。比喻培养人才是长期而艰巨的事。','《管子·权修》一年之计，莫如树谷；十年之计，莫如树木，终身之计，莫如树人。”','古人都知道～的道理，我们更应该把培养人才这件大事抓好。','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百年树人');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百年偕老','bǎi nián xié lǎo','偕共同。指夫妻共同白头到老。','元·武汉臣《生金阁》第二折俺衙内大财大礼，娶将你来，指望百年偕老，你只是不肯随顺，可是为何？”','恩深义重，誓必图～。★明·冯梦龙《醒世恒》卷八','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百年偕老');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百年谐老','bǎi nián xié lǎo','指夫妻共同生活到老。','《全元散曲·集贤宾·皂罗袍》美满恩深情厚，原百年谐老，共守白头。”','总只是朝云暮潮。呀，总不如～。★明·顾大典《青衫记·坐湿青衫》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百年谐老');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百年之柄','bǎi nián zhī bǐng','柄权柄。形容长久的大权。','《后汉书·班彪传》主有专己之威，臣无百年之柄。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百年之柄');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百年之好','bǎi nián zhī hǎo','永久的好合。指男女结为夫妇。','宋·罗烨《醉翁谈录·张氏夜奔吕星哥》今宁随君远奔，以结百年之好。”明·凌濛初《初刻拍案惊奇》卷三十二我方壮年，未曾娶妻。倘蒙不弃，当与子缔百年之好。”','忙了几时，到了重阳吉期，小峰同红蕖成了～。★清·李汝珍《镜花缘》第九四回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百年之好');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百年之后','bǎi nián zhī hòu','死的讳称。','《曹操集·卷二·军谯令》为存者立庙，使视其先人。魂而有灵，吾百年之后何恨哉！”元·武汉臣《老生儿》第三折俺女儿百年之后，可往俺刘家坟里埋也，去他张家坟里埋？”','您～，安葬何处？★鲁讯《题未定”草·五》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百年之后');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百年之业','bǎi nián zhī yè','百年的事业。指长远的事业。','无','汝幸能出崔家妾，我遗汝五百缣，归乡足成～。★宋·李昿《太平广记》卷第四百九十四','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百年之业');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百年之约','bǎi nián zhī yuē','即婚约。','清·李渔《蜃中楼·双订》小生不揣，要与小姐订百年之约，不知可肯俯从。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百年之约');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百念皆灰','bǎi niàn jiē huī','种种念头都已消失成了灰。比喻心灰意冷。','清·魏子安《花月痕》第三十八回我如今百念皆灰，只求归见老母。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百念皆灰');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百鸟朝凤','bǎi niǎo cháo fèng','朝朝见；凤凤凰，古代传说中的鸟王。旧时喻指君主圣明而天下依附，后也比喻德高望重者众望所归。','宋·李昿等《太平御览》九百一十五卷引《唐书》海州言凤见于城上，群鸟数百随之，东北飞向苍梧山。”','那些玉斝中的长春露酒也都由站在身边侍候的宫女接过去倾入一只绘着～的大瓷缸中。★姚雪垠《李自成》第二卷第三十一章','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百鸟朝凤');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百品千条','bǎi pǐn qiān tiáo','指繁多的名目。','《南史·齐纪下·废帝东昏侯》又订出雄雉头、鹤氅、白鹭縗，百品千条，无复穷已。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百品千条');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百巧成穷','bǎi qiǎo chéng qióng','指有多种才能的人反而贫困不堪。同百巧千穷”。','宋·陈师道《寄单州张朝请》诗一言悟主心犹壮，百巧成穷发自新。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百巧成穷');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百巧千穷','bǎi qiǎo qiān qióng','指有才能者境遇反而不好。','宋·陈师道《早起》诗有家无食违高枕，百巧千穷只短檠。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百巧千穷');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百忍成金','bǎi rěn chéng jīn','形容忍耐的可贵。','《旧唐书·孝友传·张公艺》郓州寿张人张公艺，九代同居……麟德中，高宗有事泰山，路过郓州，亲幸其宅，问其义由。其人请纸笔，但书百余‘忍’字。”','如所谓～，所谓唾面自干，岂非世俗传为佳话者耶……今乃欲举全国人而为无骨无血无气之怪物，吾不知如之何而可也！★梁启超《新民说》八','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百忍成金');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百舌之声','bǎi shé zhī shēng','比喻唠叨、多嘴。','《淮南子·说山训》人有多言者，犹百舌之声；人有少言者，犹不脂之户。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百舌之声');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百舍重茧','bǎi shè chóng jiǎn','形容走远道的辛苦。','《战国策·宋策》公输盘为楚设机，将以攻宋。墨子闻之，百舍重茧，往见公输盘。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百舍重茧');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百舍重趼','bǎi shè chóng jiǎn','百里一舍，足底老皮上又生出硬皮。形容长途奔走，十分辛劳。亦作百舍重茧”。','《庄子·天道》吾闻夫子圣人也。吾固不辞远道而来愿见，百舍重趼而不敢息。”《淮南子·修务训》昔南荣畴耻圣道之独亡于己身……百舍重趼，不敢休息，南见老聃。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百舍重趼');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百身何赎','bǎi shēn hé shú','百身自身死一百次；赎抵。拿一百个我，也无法把你换回来了。表示极沉痛地悼念。','《诗经·秦风·黄鸟》如可赎兮，人百其身。”','一见无期，～。★南朝·梁·刘令娴《祭夫徐敬业文》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百身何赎');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百身莫赎','bǎi shēn mò shú','百身自身死一百次；赎抵。拿一百个我，也无法把你换回来了。表示极沉痛地悼念。','《诗经·秦风·黄鸟》如可赎兮，人百其身。”','旷官之罪，职～矣。★明·袁宏道《去吴七牍·乞改稿一》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百身莫赎');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百世不磨','bǎi shì bù mó','名垂久远，永不磨灭。','《后汉书·南匈奴传论》千里之差，兴自毫端，失得之源，百世不磨矣。”','吾立子名，百世不磨。★唐·韩愈《送穷文》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百世不磨');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百世不易','bǎi shì bù yì','千秋万代永不改变。','《汉书·邹阳传》积德累行，不知其善，有时而用；弃义背理，不知其恶，有时而亡。臣愿大王孰计而身行之，此百世不易之道也。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百世不易');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百世流芳','bǎi shì liú fāng','美名永传于后世。','明·徐霖《绣襦记·逼娃逢迎》贱人不思忖，良家且淫奔。你既落烟花寨，休思百世流芳也。”','红旗半下悼英雄，～建大功。★《杭州日报》1977.1.6','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百世流芳');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百世流芬','bǎi shì liú fēn','美名永传于后世。同百世流芳”。','清·李渔《闲情偶寄·词曲·结构》予以生花之笔，撰为倒峡之词，使人人赞美，百世流芬。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百世流芬');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百世师','bǎi shì shī','品德学问可以做为百代的表率。','《孟子·尽心下》圣人，百世之师也。”','匹夫而为～，一言而为天下法。★宋·苏轼《东坡后集·卷十五·潮州韩文公庙碑》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百世师');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百世一人','bǎi shì yī rén','指极难得的人才。','宋·苏轼《祭司马君实文》百世一人，千载一时，惟时与人，鲜偶常奇。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百世一人');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百世之利','bǎi shì zhī lì','世世代代长远的利益。','《吕氏春秋·义赏》文公曰‘雍季之言，百世之利也。”','然而为大王计者，皆为一时之说，不顾～。★《史记·张仪列传》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百世之利');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百世之师','bǎi shì zhī shī','世世代代的老师，指才德高尚而永远可为人师表的人。','先秦·孟轲《孟子·尽心下》圣人，百世之师也，伯夷、柳下惠是也。”','卓为～；杰作雄辞，散落四夷之远。★宋·陆游《贺薛安抚兼制置启》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百世之师');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百事大吉','bǎi shì dà jí','指一切顺利；平安无事。','宋·周密《癸辛杂识续集下·桃符获罪》宜入新年怎生呵，百事大吉那般者。”','然而献出了粟米麻线，果真就天下太平，～了么？★聂绀弩《鲁迅——思想革命与民族革命的创导者》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百事大吉');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百事无成','bǎi shì wú chéng','犹言一事无成。什么事情都没成功。','唐·刘禹锡《陪崔大尚书及诸阁老宴杏园》诗更将何面上春台，百事无成老又催。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百事无成');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百兽率舞','bǎi shòu shuài wǔ','各种野兽，相率起舞。旧指帝王修德，时代清平。','《尚书·舜典》於！予击石拊石，百兽率舞。”','大礼告成，伺候各官，循例三呼，国乐以外，杂以军乐，仿佛有凤凰来仪，～景象。★蔡东藩、许廑父《民国通俗演义》第三四回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百兽率舞');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百顺百依','bǎi shùn bǎi yī','形容什么都顺从。同百依百随”。','明·冯梦龙《醒世恒言》第二十七卷［焦氏］况兼正在妙龄，打扮得如花朵相似，枕席之间，曲意取媚。果然哄得李雄千欢万喜，百顺百依。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百顺百依');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百顺千随','bǎi shùn qiān suí','形容什么都顺从。同百依百随”。','明·凌濛初《二刻拍案惊奇》卷六李将军见他聪明伶俐，知书晓事，爱得他如珠似玉一般，十分抬举，百顺千随。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百顺千随');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百思不得其解','bǎi sī bù dé qí jiě','百多次；解理解。百般思索也无法理解。','清·纪昀《阅微草堂笔记》卷十三此真百思不得其故矣。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百思不得其解');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百思不解','bǎi sī bù jiě','百多次；解理解。百般思索也无法理解。','清·纪昀《阅微草堂笔记》卷十三此真百思不得其故矣。”','～，五夜踌蹰，故乘隙邀君一面，以决中疑。★清·无名氏《葛仙翁全传》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百思不解');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百思莫解','bǎi sī mò jiě','百思百般思索。形容反复思索也不能理解。','清·纪昀《阅微草堂笔记》卷十三此真百思不得其故矣。”','这真是令人～。★邹韬奋《抗战以来·审查老爷对文艺的贡献》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百思莫解');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百死一生','bǎi sǐ yī shēng','形容生命极其危险，处于死亡的边缘。','《北齐书·杜弼传》诸勋人身触锋刃，百死一生，纵其贪鄙，所取处大，不可同之循常例也。”','地无医巫，药石万里，病者有～之虑。★唐·元稹《叙诗寄乐天书》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百死一生');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百岁千秋','bǎi suì qiān qiū','一百年，一千载。形容岁月漫长，历时很久。','《韩非子·显学》今巫祝之祝人曰‘使若千秋万岁。’千秋万岁之声恬耳，而一日之寿无征于人，此人所以简巫祝也。”','明·康海《王兰卿》第三折便活到百岁千秋索一死，则不如另寻个身计。”','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百岁千秋');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百岁之好','bǎi suì zhī hǎo','好友爱。永久的好合。指男女结为夫妻。','明·屠隆《昙花记·士女私奔》亦雅慕其才藻，故将图百岁之好，非仅邀一夕之欢。”','他们历经磨难，终于结成～。','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百岁之好');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百岁之后','bǎi suì zhī hòu','死的讳称。','《诗·唐风·葛生》百岁之后，归于其居。”《史记·李不韦列传》夫百岁之后，所子者为王，终不失势。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百岁之后');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百岁之盟','bǎi suì zhī méng','同百年之约”。即婚约。','清·李渔《意中缘·先订》他要与小娘子订百岁之盟，不知可肯相许。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百岁之盟');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百万买宅，千万买邻','bǎ wàn mǎi zhái，qiān wàn mǎi lín','比喻好邻居千金难买。','《南史·吕僧珍传》宋季雅罢南康郡，市宅居僧珍宅侧。僧珍问宅价。曰‘一千一百万。’怪其贵。季雅曰‘一百万买宅，千万买邻。’”','～，人生孰若安居之乐？★宋·辛弃疾《新居上梁》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百万买宅，千万买邻');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百万雄兵','bǎi wàn xióng bīng','人数众多、威武雄壮的军队。同百万雄师”。','宋·赵葵《南乡子》词束发领西藩。百万雄兵掌握间，召到庙堂无一事，遭弹。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百万雄兵');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百万雄师','bǎi wàn xióng shī','为数众多、威武雄壮的军队。','宋·张载《庆州大顺城记》百万雄师，莫可以前。”','钟山风雨起苍茫，～过大江。★毛泽东《七律·人民解放军占领南京》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百万雄师');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百闻不如一见','bǎi wén bù rú yī jiàn','闻听见。听得再多，也不如亲眼见到一次。','《荀子·儒效》闻之不若见之。”《汉书·赵充国传》百闻不如一见，兵难遥度，臣愿驰至金城，图上方略。”','从前画师所绘的狮子形，统是全身有毛，我观现在这狮子并不是这么样子，所以～。★《慈禧太后演义》第三十六回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百闻不如一见');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百无禁忌','bǎi wú jìn jì','百所有的，不论什么；禁忌忌讳。什么都不忌讳。','清·范寅《越谚·名物·风俗》百无禁忌，诸邪回避。”','则知太公封神，古有此说。今人于门户每书姜太公在此，～，亦非无所本矣。★鲁迅《小说旧闻钞·封神传衍义》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百无禁忌');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百无聊赖','bǎi wú liáo lài','聊赖依赖。精神上无所寄托，感到什么都没意思。','汉·蔡琰《悲愤》诗为复强视息，虽生何聊赖。”','在～中，随手抓过一本书来，科学也好，文学也好，横竖什么都一样。★鲁迅《彷徨·伤逝》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百无聊赖');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百无是处','bǎi wú shì chù','犹言一无是处。形容全都是错的，没有一点对的地方。','宋·辛弃疾《浣溪沙·漫兴作》词一似旧时春意思，百无是处老形骸，也曾头上带花来。”','每日价茶不茶，饭不饭，～。★元·王氏《粉蝶儿·寄情人》套曲','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百无是处');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百无所成','bǎi wú suǒ chéng','犹言一事无成。','明·王守仁《教条示龙场诸生》今学者旷废隳惰，玩岁愒时而百无所成，皆由于志之未立耳。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百无所成');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百无一成','bǎi wú yī chéng','百一切。形容一切的努力和作为没有一样成功，即毫无成就。','清·曾国藩《圣哲画象记》志学不早……驽缓多病，百无一成。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百无一成');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百无一堪','bǎi wú yī kān','指百人中无一人能胜任。','《旧唐书·郭子仪传》盖以六军之兵，素非精练，皆市肆屠沽之人，务挂虚名，苟避征赋，及驱以就战，百无一堪。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百无一堪');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百无一能','bǎi wú yī néng','能能力，胜任。什么都不会做。','明·施耐庵《水浒全传》第三十二回宋江道‘我自百无一能，虽有忠心，不能得进步。’”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百无一能');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百无一失','bǎi wú yī shī','形容有充分把握，绝对不会出差错。','汉·王充《论衡·须颂》从门应庭，听堂室之言，什而失九，如升堂窥室，百不失一。”','这项任务很重要，不仅要完成，还要做到～。','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百无一失');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百无一是','bǎi wú yī shì','干一百件事，也没有做对一件。形容全都做错了，一无是处。','宋·袁采《袁氏世范》至于百无一是，且朝夕以此相临，极为难处。”','此人做事百无一是，如若重用应当三思而后行。','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百无一是');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百无一用','bǎi wú yī yòng','百样之中无一有用的。形容毫无用处。','清·黄景仁《杂感》十有九人堪白眼，百无一用是书生。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百无一用');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百下百全','bǎi xià bǎi quán','犹言百发百中，万无一失。','汉·班固《汉书·冯奉世传》以将军材质之美，奋精兵，诛不轨，百下百全之道也。”','虽孽竖跳梁，士力未完，宜厉兵粟马，乘无备，击不意，～。★《新唐书·刘仁轨传》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百下百全');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百下百着','bǎi xià bǎi zháo','犹言百发百中，万无一失。同百下百全”。','明·吴承恩《西游记》第九回我每日送他一尾金色鲤，他就与我袖传一课。依方位，百下百着。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百下百着');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百星不如一月','bǎi xīng bù rú yī yuè','一百颗星星发出的亮光不如一个月亮发出的光明亮。比喻量多不如质优。','《淮南子·说林训》百星之明，不如一月之光；十牖之开，不如一户之明。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百星不如一月');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百业萧条','bǎi yè xiāo tiáo','萧条冷落、凋敝。指各行各业都很冷落、不兴旺。形容社会的衰败。','无','由于金融危机的缘故，造成了经济动荡，百业萧条。','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百业萧条');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百衣百随','bǎi yī bǎi suí','什么都依从。形容一切都顺从别人。','明·凌濛初《初刻拍案惊奇》第十三卷做爷娘的百依百顺，没一事违拗了他。”','自此～的，不敢倔强了。★清·曹雪芹《红楼梦》第六十回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百衣百随');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百依百从','bǎi yī bǎi cóng','百比喻很多；依顺从；从跟从。什么都依从。形容一味顺从。','杨纤如《伞》讨厌他过于粗暴鲁莽，常办错事，喜爱他忠实听话，百依百从。”','对孩子～是不行的。','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百依百从');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百依百顺','bǎi yī bǎi shùn','依、顺顺从。什么都依从。形容一切都顺从别人。','明·凌濛初《初刻拍案惊奇》第十三卷做爷娘的百依百顺，没一事违拗了他。”','这安公子是那女孩儿一般～的人。★清·文康《儿女英雄传》第三回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百依百顺');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百依百随','bǎi yī bǎi suí','形容什么都顺从。','明·施耐庵《水浒传》第21回这颜婆惜被那张三小意儿百依百随，轻怜重惜，卖俏迎奸。”','茅大早摸透了七爷脾气，表面上各事～，且对金钱事尤其坦白分明。★沈从文《王谢子弟》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百依百随');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百载树人','bǎi zǎi shù rén','培育人才需要百年。指育才是百年大计，要付出艰苦的劳动。同百年树人”。','粤剧《搜书院》第五幕第二迟载树人是我一生抱负。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百载树人');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百战百败','bǎi zhàn bǎi bài','指每战必败。','《汉书·萧何传》汉王曰‘何为乃死也？’何曰‘今众弗如，百战百败，不死何为？’”','即论残明时局，亦已～，元气日凋，傥赫斯一怒，早著戎衣，势必如摧枯拉朽。★清·陈康祺《郎潜纪闻》卷十一','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百战百败');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百战百胜','bǎi zhàn bǎi shèng','每战必胜。形容所向无敌。','《孙子·谋攻》百战百胜，非善之善者也。”','项籍唯不能忍，是以～而轻用其锋。★宋·苏轼《留侯论》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百战百胜');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百战不殆','bǎi zhàn bù dài','经历许多次战役，都没有遭到危险。形容善于用兵。','《孙子·谋攻》知彼知己者百战不殆。”','自古名将料敌制胜，训练士兵，故可使赴汤蹈火，～。★《金史·刘炳传》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百战不殆');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百丈竿头','bǎi zhàng gān tóu','佛教语，百丈高的竿子，比喻道行达到很高境界。','宋·释道原《景德传灯录·景岑禅师》百丈竿头须进步，十方世界是全身。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百丈竿头');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百折不摧','bǎi zhé bù cuī','受到无数挫折都不屈服、动摇。形容意志坚强。','宋·苏辙《祭亡兄端明文》义气外强，道心内全，百折不摧，如有待然。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百折不摧');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百折不回','bǎi zhé bù huí','折挫折。比喻意志坚强，无论受到多少次挫折，毫不动摇退缩。','汉·蔡邕《太尉乔玄碑》其性庄，疾华尚朴，有百折不挠，临大节而不可夺之风。”','耿耿自始，～。★清·姚莹《复杨君论诗文书》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百折不回');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百折不挠','bǎi zhé bù náo','折挫折；挠弯曲。比喻意志坚强，无论受到多少次挫折，毫不动摇退缩。','汉·蔡邕《太尉乔玄碑》其性庄，疾华尚朴，有百折不挠，临大节而不可夺之风。”','汉阳民军总司令黄兴，系湖南长沙县人，向来主张革命，屡仆屡起，～。★蔡东藩、许廑父《民国通俗演义》第三回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百折不挠');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百折不屈','bǎi zhé bù qū','受到无数挫折都不屈服、动摇。形容意志坚强。','清·恽敬《〈卓忠毅公遗稿〉书后》一旦竟成其名，史书及府县志纪录则有之，必不能千百年之后，人人变色动容，有一百折不屈之人，如在其心，如出其口，如忠毅、方学士、铁尚书者也。”','要像总理那样……奋不顾身，～。★郭小川《痛悼敬爱的周总理》诗','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百折不屈');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百折不移','bǎi zhé bù yí','屡遭挫折亦不改变其操守。','明·吴承恩《赠郡伯古愚邵公报政序》即其可名状者言之，则本之一毫不染之真，而加之百折不移之守。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百折不移');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百折千回','bǎi zhé qiān huí','形容经过种种曲折和纡回。','赵朴初《与日本佛教友人谈时事为拈一偈》百折千回心不退，无畏方能施无畏。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百折千回');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百中百发','bǎi zhòng bǎi fā','中射中目标。发发射。形容射箭、投掷或射击准确，每次都命中目标。也比喻做事有充分把握，从不落空。同百发百中”。','明·许仲琳《封神演义》第三十一回陈桐闻脑后鸾铃响处，料是飞虎赶来，挂下画戟，取火龙标掌在手中，此标乃异人秘授，出手烟生，百中百发。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百中百发');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百啭千声','bǎi zhuàn qiān shēng','形容鸟鸣声婉转多样。','宋·欧阳修《画眉鸟》诗百啭千声随意移，山花红紫树高低。始知锁向金笼听，不及林间自在啼。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百啭千声');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百拙千丑','bǎi zhuō qiān chǒu','极言拙劣丑陋。','宋·晓莹《罗湖野录·辩禅师》黄龙鼻下无口，当然所见颟顸，至今百拙千丑。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百拙千丑');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百紫千红','bǎi zǐ qiān hóng','形容繁花似锦，色彩繁多。','宋·王安石《越人以幕养花因游其下》诗之一幕天无日地无尘，百紫千红占得春。”宋·辛弃疾《定风波·赋杜鹃花》词百紫千红过了春，杜鹃声苦不堪闻。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百紫千红');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百纵千随','bǎi zòng qiān suí','什么都顺从。形容一切都顺从别人。','元关汉卿《窦娥冤》第二折怎不记旧日夫妻情意，也曾有百纵千随。','卢郎，自招你在此，成了夫妇，和你朝欢暮乐，～，真人间得意之事也。★明·汤显祖《邯郸记》第六出','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百纵千随');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百足不僵','bǎi zú bù jiāng','比喻势力雄厚的集体或个人一时不易垮台。','章炳麟《政闻社社员大会破坏状》即观今日缅甸，已并于英，而本邦土司，以弹丸黑子之地，犹足自保，非人自为首、百足不僵之势耶！”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百足不僵');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百足之虫','bǎi zú zhī chóng','百足一种多节多足的爬虫，即马陆。比喻有实力基础的势力，虽一时败落，但不会很快彻底垮台。也指某一恶势力被摧垮后余孽尚存。','明·徐学谟《归有园尘谈》吝者自能致富，然一有事则为过街老鼠；侠者或致破家，然一有事则为百足之虫。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百足之虫');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百足之虫，断而不蹶','bǎi zú zhī chóng，duàn ér bù jué','比喻势力雄厚的集体或个人一时不易垮台。同百足之虫，至死不僵”。','唐·马总《意林》卷一百足之虫，断而不蹶。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百足之虫，断而不蹶');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百足之虫，死而不僵','bǎi zú zhī chóng，sǐ ér bù jiāng','百足虫名，又名马陆或马蚿，有十二环节，切断后仍能蠕动。比喻势家豪族，虽已衰败，但因势力大，基础厚，还不致完全破产。','三国·魏·曹冏《六代论》百足之虫，至死不僵，以扶之者众也。”','古人有言～”，如今虽说不似先年那样兴盛，较之平常仕宦人家，到底气象不同。★清·曹雪芹《红楼梦》第二回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百足之虫，死而不僵');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百足之虫，至断不蹶','bǎi zú zhī chóng，zhì duàn bù jué','比喻势力雄厚的集体或个人一时不易垮台。同百足之虫，至死不僵”。','《文选·曹冏〈六代论〉》百足之虫，至死不僵。”李善注引《鲁连子》百足之虫，至断不蹶者，持之者众也。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百足之虫，至断不蹶');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '百足之虫，至死不僵','bǎi zú zhī chóng，zhì sǐ bù jiāng','百足虫名，躯干计二十节，切断后仍能蠕动。僵肢体僵硬不能活动。比喻势力雄厚的集体或个人一时不易垮台。','三国·魏·曹冏《六代论》故语曰‘百足之虫，至死不僵’，以扶之者众也。此言虽小，可以譬大。”','又道是～，跌扑不多时，转眼就高官大禄，仍旧贵显。★明·凌濛初《初刻拍案惊奇》卷二十九','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '百足之虫，至死不僵');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '柏舟之节','bǎi zhōu zhī jié','指妇女丧夫后守节不嫁。同柏舟之誓”。','宋·朱熹《与陈师中书》朋友传说，令女弟甚贤，必能养老抚孤，以全柏舟之节。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '柏舟之节');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '柏舟之誓','bǎi zhōu zhī shì','指妇女丧夫后守节不嫁。亦作柏舟之节”。','《诗·鄘风·柏舟序》《柏舟》，共姜自誓也。卫世子共伯早死，其妻守义，父母欲夺而嫁之，誓而弗许，故作是诗以绝之。”','青松摧折，哀断女萝之心；丹节孤高，终守～。★唐陈子昂《唐故袁州参军李府君妻张氏墓志铭》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '柏舟之誓');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '捭阖纵横','bǎi hé zòng héng','捭阖，开合。纵横，合纵和连横的简称。以辞令探测、打动别人，在政治和外交上运用分化与争取的手段。','元胡祗遹《木兰花慢·赠歌妓》词看捭阖纵横，东强西弱，一转危机。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '捭阖纵横');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '摆尾摇头','bǎi wěi yáo tóu','摆动头尾，形容喜悦或悠然自得的样子。','《大唐三藏取经诗话》上卷只见麒麟迅速，狮子峥嵘，摆尾摇头，山林迎接。”','这鱼～在水内显，全不知深共浅。★元·无名氏《鱼篮记》第一折','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '摆尾摇头');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '摆袖却金','bài xiù què jīn','比喻为人廉洁，不受贿赂。','唐·韩愈《顺宗实录》执谊为翰林学士，受财为人求科第，夏卿不就应乃探囊中金以内夏卿袖，夏卿……摆袖引身而去。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '摆袖却金');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '败不旋踵','bài bù xuán zhǒng','形容很快就遭到失败。旋踵，转动一下脚后跟。','唐·卢照邻《三国论》然而丧师失律，败不旋踵，奔波谦瓒之间，羁旅袁曹之手，岂拙于用武，将遇非常敌乎？”','未议致身，先图问鼎，假称伊霍之事，将肆莽卓之心，人神同疾，～。★《北史·杨敷传》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '败不旋踵');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '败材伤锦','bài cái shāng jǐn','比喻用非其人，伤害国家。','《孟子·梁惠王下》工师得大木，则王喜，以为能胜其任也。匠人斫而小之，则王怒，以为不胜其任矣。”《左传·襄公三十一年》子有美锦，不使人学制焉。大官大邑，……而使学者制焉。其为美锦，不亦多乎？”','今公卿大位莫非戎陈，尚书显官皆出庸伍……臣非有憎疾以求进也，但为陛下惜此举厝。～，所宜至虑。★《后汉书·刘玄传》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '败材伤锦');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '败德辱行','bài dé rǔ xíng','败坏道德和操守。','无','从古到今，人们总是唾弃那些～的人。','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '败德辱行');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '败法乱纪','bài fǎ luàn jì','败坏法令，扰乱纪律。','南朝·宋·范晔《后汉书·袁绍传》便放志专行，威劫省禁，卑侮王僚，败法乱纪，坐召三台，专制朝政。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '败法乱纪');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '败鼓之皮','bài gǔ zhī pí','败破旧。破鼓皮可作药材。比喻虽微贱，却是有用的东西。','唐·韩愈《进学解》牛溲马勃，败鼓之皮，俱收并蓄，待用无遗者，医师之良也。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '败鼓之皮');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '败国丧家','bài guó sàng jiā','使国家沦亡，家庭败落。','《晋书·列女传·刘聪妻刘氏》自古败国丧家，未始不由妇人者也。”','盖三人皆元戚机臣，其残膏积侈，～，帝特恶焉。★陈衍《元诗纪事·黄菜叶谣》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '败国丧家');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '败国亡家','bài guó wáng jiā','使国家沦亡，家庭败落。','参见败国丧家”。','自古以来，因为奸臣陷害忠良，以致～，这种例子也不少。★欧阳予情《忠王李秀成》第一幕','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '败国亡家');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '败化伤风','bài huà shāng fēng','指败坏社会道德风气。','参见败俗伤化”。','笑你个齐人太妄，可正是～轻薄郎。★明·孙仁孺《东郭记·钻穴隙》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '败化伤风');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '败绩失据','bài jì shī jù','指事业失败而无所凭依。','宋·叶绍翁《四朝闻见录贤良续刻第三则》历数世，未尝有败绩失据之过。”','及今年内几经～，犹复漠然无所动于中。★梁启超《敬告国中之说实业者》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '败绩失据');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '败将残兵','bài jiàng cán bīng','指战败后剩余的兵将。同残兵败将”。','明·无名氏《开诏救忠》楔子你虽然杀了我一阵，你的军兵可也尽皆折损了，则剩下这一枝败将残兵。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '败将残兵');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '败井颓垣','bài jǐng tuí yuán','犹言房毁墙坍。形容村舍荒凉。','鲁迅《集外集·诗》奔霆飞熛歼人子，败井颓垣剩饿鸠。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '败井颓垣');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '败军之将','bài jūn zhī jiàng','打了败仗的将领。现多用于讽刺失败的人。','《吴越春秋·勾践入臣外传》臣闻……败军之将，不敢语勇。”《史记·淮阴侯列传》臣闻败军之将，不可以言勇；亡国之大夫，不可以图存。”','～，荷蒙厚恩，无可以报，愿施犬马之劳，不须张弓只箭，径取成都。★明·罗贯中《三国演义》第六十三回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '败军之将');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '败鳞残甲','bài lín cán jiǎ','败毁坏。残败零碎的鳞甲，比喻满空飞舞的雪花。','宋·张元《咏雪》战退玉龙三百万，败鳞残甲满空飞。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '败鳞残甲');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '败柳残花','bài liǔ cán huā','败衰败。残凋残。凋残的柳树，残败了的花。旧时用以比喻生活放荡或被蹂躏遗弃的女子。','元·白朴《墙头马上》三折休把似残花败柳冤仇结，我与你生男长女填还彻，指望生则同衾，死则共穴。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '败柳残花');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '败事有余，成事不足','bài shì yǒu yú，chéng shì bù zú','指非但办不好事情，反而常常把事情搞坏。','茅盾《子夜》九此等聚众骚扰的行径，分明是没有教育的人民一时间的冲动罢了＼事有余，成事不足。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '败事有余，成事不足');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '败俗伤风','bài sú shāng fēng','指败坏社会道德风气。','参见败俗伤化”。','行者道菩萨，虽是这般故事，奈何他玷污了皇后，～，坏伦乱法，却是该他死罪。”★《西游记》第七一回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '败俗伤风');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '败俗伤化','bài sú shāng huà','指败坏社会道德风气。','《汉书·叙传下》侯服玉食，败俗伤化。”','王夷甫方以盛名处大位，然～，必此人也。★《晋书·羊祜传》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '败俗伤化');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '败兴而归','bài xìng ér guī','败兴遇到和想象不一致的事而情绪低落。扫兴地归来。','明·冯梦龙《东周列国志》第一回各军士未及领赏，草草而散。正是乘兴而来，败兴而返。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '败兴而归');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '败于垂成','bài yú chuí chéng','指临近成功时遭到失败。参见功败垂成”。','宋·无名氏《释常谈·败于垂成》凡事欲成却不成，谓之败于垂成。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '败于垂成');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拜赐之师','bài cì zhī shī','用以讽刺为复仇而又失败的出兵。','《左传·文公二年》春秋时，秦晋殽之战，秦将孟明视被俘。获释时，孟谓晋君曰‘三年将拜君赐。’后三年，秦果然出兵伐晋复仇。又败，晋人讥之为拜赐之师”。','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拜赐之师');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拜倒辕门','bài dǎo yuán mén','辕门将帅行辕或军营的大门。形容对别人佩服之至，自愿认输。','清·李汝珍《镜花缘》第五十三回若非一部全史瞭然于中，何能如此。妹子唯有拜倒辕门了。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拜倒辕门');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拜恩私室','bài ēn sī shì','指感谢有权势的人的推荐提拔。','《北史·王晞传》受恩天朝，拜恩私室，自古以为干纪。”','你说得不错，于今受职公堂，～”者，比比皆是。★高阳《胭脂井》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拜恩私室');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拜鬼求神','bài guǐ qiú shén','向鬼神叩拜祈祷，求其保佑。','唐·王建《三台》诗扬州桥边小妇，长干市里商人，三年不得消息，各自拜鬼求神。”','他老相信那些～的方法会奏效的。','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拜鬼求神');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '拜将封侯','bài jiàng fēng hóu','拜为将领，封为列侯。古代形容功成名就，官至极品。','元·无名氏《暗度陈仓》第一折我也曾陋巷淹留，贫寒常受，红尘火，今日个拜将封侯，才得个名成就。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '拜将封侯');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '稗官小说','bài guān xiǎo shuō','即野史小说，街谈巷说之言。','明·唐顺之《仪宾立公墓志铭》盖自诸史百家星官堪舆道书释经，下至稗官小说，皆能陟其津涯而发之诗歌。”','又好为～而懒于著书。★清·江藩《汉学师承记·纪昀》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '稗官小说');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '稗官野史','bài guān yě shǐ','稗官古代的一种小官，专给帝王搜集街谈巷语，道听途说，以供省览，后称小说或小说家为稗官。指旧时的小说和私人编撰的史书。','《汉书·艺文志》小说家者流，盖出于稗官，街谈巷语，道听途说者之所造也。”','如今我要将这碑记付给文人墨士，做为～，流传海内。★清·李汝珍《镜花缘》第七十回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '稗官野史');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '扳龙附凤','bān lóng fù fèng','指依附龙凤而升空。','南朝·梁·沈约《宋书》志第十二乐四弹琴鼓瑟，聊以自娱。陵云登台，浮游太清。扳龙附凤，日望身轻。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '扳龙附凤');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '班班可考','bān bān kě kǎo','班班明显的样子。指事情源流始末清清楚楚，可以考证。','宋·陈亮《与韩无咎尚书》本朝二百年之间，学问文章、政事术业，各有家法，其本末源流，班班可考。”','亦尝有此言，明载简册，～。★清·钱彩《说岳全传》第七十三回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '班班可考');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '班驳陆离','bān bó lù lí','形容色彩杂沓。','参见斑驳陆离”。','此铃金质坚炼，制度浑朴，～。★清·韩泰华《无事为福斋随笔》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '班驳陆离');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '班功行赏','bān gōng xíng shǎng','按照功劳大小，依次给予赏赐。','南朝·宋·范晔《后汉书·李云传》举厝至重，不可不慎。班功行赏，宜应其实。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '班功行赏');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '班荆道故','bān jīng dào gù','班铺开；道叙说。用荆铺在地上坐在上面谈说过去的事情。形容老朋友在路上碰到了，坐下来谈谈别后的情况。','《左传·襄公二十六年》伍举奔郑，将遂奔晋。声子将如晋，遇之于郑郊，班荆相与食，而言复故。”','一次邂逅相逢，一次～，竟使一位艺术殉道者在冥冥里忽闻天籁，于混沌中顿悟禅机。”★李存葆《伏虎草堂主人》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '班荆道故');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '班荆道旧','bān jīng dào jiù','指朋友相遇于途，铺荆坐地，共叙情怀◇泛指朋友相遇，共叙离情。亦作班荆道故”。','典出《左传·襄公二十六年》楚伍参与蔡太师子朝友，其子伍举与声子相善……伍举奔郑，将遂奔晋。声子将如晋，遇之於郑郊，班荆相与食，而言复故。”杜预注班，布也。布荆坐地，共议归楚，事朋友世亲。”','鲍叔、管仲，分财无猜；归生、伍举，～。★晋·陶潜《与子俨等疏》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '班荆道旧');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '班马文章','bān mǎ wén zhāng','汉代史学家司马迁与班固的并称。泛指可与班固、司马迁相比美的文章。','明·无名氏《鸣凤记·邹林游学》夔龙礼乐承先范，班马文章勘墨铅。”','～由夙慧，庾鲍诗句实前缘。★清·刘璋《飞花艳想》第二回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '班马文章');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '班门弄斧','bān mén nòng fǔ','在鲁班门前舞弄斧子。比喻在行家面前卖弄本领，不自量力。','唐·柳宗元《王氏伯仲唱和诗序》操斧于班、郢之门，斯强颜耳。”宋·欧阳修《与梅圣俞书》昨在真定，有诗七八首，今录去，班门弄斧，可笑可笑。”','闻得亭亭姐姐学问渊博，妹子何敢～，同他乱谈。★清·李汝珍《镜花缘》第五十二回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '班门弄斧');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '班师得胜','bān shī dé shèng','班还。军队出征，得胜而归。','明·无名氏《破天阵》三折将颜洞宾活捉拿，把韩延寿首级剽，班师得胜人欢乐，三军踊跃。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '班师得胜');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '班师回朝','bān shī huí cháo','班调回。调动出征的军队返回首都，指出征的军队胜利返回朝廷。','元·乔孟符《两世姻缘》第三折你奉圣旨破吐蕃，定西夏，班师回朝，便当请功受赏。”','后主（刘禅）下诏，宣孔明班师回朝。★明·罗贯中《三国演义》第一百回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '班师回朝');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '班师回俯','bān shī huí fǔ','指出征的军队胜利返回朝廷。同班师回朝”。','无','我们的球队终于获得了甲a联赛冠军，队员们今天～。','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '班师回俯');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '班香宋艳','bān xiāng sòng yàn','班固和宋玉均善辞赋，以富丽见称，后以之泛称辞赋之美者。','清·孔尚任《桃花扇·听稗》早岁清词，吐出班香宋艳；中年浩气，流成苏韩潮。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '班香宋艳');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '班衣戏采','bān yī xì cǎi','相传老莱子七十岁时穿彩衣作儿戏以娱亲◇以之为老养父母的孝亲典故。','清·新广东武生《黄萧养回头》虽无儿，效老莱，班衣戏采。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '班衣戏采');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '班衣戏彩','bān yī xì cǎi','指身穿彩衣，作婴兒戏耍以娱父母◇以之为老养父母的孝亲典故。','参见斑衣戏彩”。','虽无儿，效老莱，～。★清·新广东武生《黄萧养回头》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '班衣戏彩');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '斑驳陆离','bān bó lù lí','斑驳色彩杂乱；陆离参差不一。形容色彩纷杂。','战国·楚·屈原《离骚》纷总总其离合兮，斑陆离其上下。”','铜器一，器大可合抱，重数十斤，侧有双环，不知何用，～，瓶亦古。★清·蒲松龄《聊斋志异·古瓶》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '斑驳陆离');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '斑衣戏彩','bān yī xì cǎi','指身穿彩衣，作婴兒戏耍以娱父母◇以之为老养父母的孝亲典故。','《北堂书钞》卷一二九引《孝子传》言老莱子年七十，父母尚在，因常服斑衣，为婴兒戏以娱父母。','那二十四孝上～，他们不能来戏彩引老祖宗笑一笑，我这里好容易引的老祖宗笑一笑……难道反笑我不成？★《红楼梦》第五四回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '斑衣戏彩');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '搬唇递舌','bān chún dì shé','挑拔是非。','元·无名氏《南珍珠马·情》平白地送暖偷寒，猛可的搬唇递舌。”','不似你这个两闲白面，～的歹弟子孩儿。★《古今杂剧·翠红乡儿女两团圆》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '搬唇递舌');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '搬唇弄舌','bān chún nòng shé','①挑拨是非。②卖弄口才。','①明·徐某《杀狗记·看书苦谏》搬唇弄舌，口是心非，到底有失，不可轻信。”②许地山《海角底孤星》你们念书人底能干只会在女人面前搬唇弄舌罢。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '搬唇弄舌');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '搬斤播两','bān jīn bō liǎng','专从细小的斤两上动脑筋。','明·凌濛初《初刻拍案惊奇》第十八卷如今这些贪人，拥着娇妻美妾，求田问舍，损人肥已，搬斤播两，何等肚肠。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '搬斤播两');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '搬口弄舌','bān kǒu nòng shé','挑拨是非。同搬唇递舌”。','明·施耐庵《水浒传》第四十四回必然嫂嫂见我做了这些衣裳，一定背后有说话；又见我两日不回，必有人搬口弄舌，想是疑心，不做买卖。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '搬口弄舌');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '搬弄是非','bān nòng shì fēi','搬弄挑拔。把别人的话传来传去，有意挑拔，或在背后乱加议论，引起纠纷。','《庄子·盗跖》摇唇鼓舌，擅生是非。”元·李寿卿《伍员吹箫》第一折他在平公面前，搬弄我许多事非。”','况三姑六婆，里外搬弄是非，何能不生事端。★清·李汝珍《镜花缘》第十二回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '搬弄是非');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '搬起石头打自己的脚','bān qǐ shí tóu dǎ zì jǐ de jiǎo','搬移动。比喻本来想害别人，结果害了自己。自食其果。','毛泽东《关于国际新形势对新华日报记者的谈话》我在1938年十月的中共六届六中全会上曾经说过‘搬起石头打自己的脚，这就是张伯伦政策的必然结果。’”','他这样做无疑是～。','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '搬起石头打自己的脚');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '搬石砸脚','bān shí zá jiǎo','比喻本来想害别人，结果害了自己。','无','刘婉蓉道我说了怕你赌气，拂袖而去，那岂不自己～，得不偿失。”★卧龙生《镖旗》第二十六回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '搬石砸脚');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '阪上走丸','bǎn shàng zǒu wán','阪斜坡；丸弹丸。象在斜坡上滚弹丸。比喻形势发展迅速或工作进行顺利。','《汉书·蒯通传》边城皆将相告曰‘范阳令先下而身富贵’，必相率而降，犹如阪上走丸也。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '阪上走丸');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '板板六十四','bǎn bǎn liù shí sì','宋时官铸铜钱，每板六十四文，不得增减。比喻刻板不知变通。板即版”，铸钱的模子。','清·范寅《越谚·数目之谚》板板六十四，铸钱定例也，喻不活。”','这个人～，不通人情。★王安忆《绕公社一周》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '板板六十四');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '板上钉钉','bǎn shàng dìng dīng','在石板上钉上铁钉。比喻事情已经决定，不能改变。','清·李绿园《歧路灯》第九十三回议亲之事，这三位老伯，并儿的外父一并说好，那就石板上钉钉，就如我爹订的一般。”','海云是已经定性、已经作了～的正式结论的阶级敌人。★王蒙《蝴蝶》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '板上钉钉');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '板上砸钉','bǎn shàng zá dīng','比喻事情已定，不容变更或说话算数。','参见板上钉钉”。','大英雄说话～，你既然还愿意给弟兄们办事，那就看你说的吧。★苗培时《矿山烈火》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '板上砸钉');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '版版六十四','bǎn bǎn liù shí sì','版宋代铸钱的模型。每块铸版都是铸出六十四文钱。形容做事死板，不知变通。','宋·周遵道《豹隐纪谈》记载宋代凡铸钱，每版六十四文。','～，铸钱定例也，喻不活。★清·范寅《越谚·数目之谚》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '版版六十四');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '版筑饭牛','bǎn zhù fàn niú','版筑，造土墙；饭牛，喂牛◇以之为贤臣出身微贱之典。','《书·说命上》载相传商代贤者傅说筑于傅岩，武丁用以为相。《吕氏春秋·举难》载春秋时卫国贤者宁戚饭牛车下，扣牛角而歌，桓公异之，拜为上卿。','古今相望，落落然如骑星辰，与夫起～者异矣。★唐·刘禹锡《唐故中书侍郎平章事韦公集纪》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '版筑饭牛');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半半拉拉','bàn bàn lā lā','不完整或未全部完成的。','朱自清《论说话的多少》最麻烦的是人多的时候，说得半半拉拉的，大家或者交头接耳说他们自己的私话，或者打盹儿，或者东看看西看看，轻轻敲着指头想别的，或者勉强打起精神对付着你。”','徐象谦读书的年头，不算少了，从私塾算起，按年头说，他比六年级生读的书还多。只是书读的～，很难说到了高小毕业。★张麟马长志《中国元帅徐向前》第四章','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半半拉拉');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半半路路','bàn bàn lù lù','事物进行的过程中间。','无','做任何事都要有恒心，不要～的。','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半半路路');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半壁河山','bàn bì hé shān','指国土的一部或大部分。','清·钮琹《觚剩·虎林军营唱和》昊《怀古》四首《咏岳忠武》云‘将军野战最知名，半壁河山一力撑。’”','～沉血海，几多知友化沙虫。★陈毅《三十五岁生日寄怀》诗','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半壁河山');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半壁江山','bàn bì jiāng shān','半壁半边；江山比喻国家疆土。指在敌人入侵后残存或丧失的部分国土。','清·蒋士诠《冬青树·提纲》半壁江山，比五季朝廷尤小。”','军声壮，阵法多，～全仗我。★清·吴梅《风洞山·阅兵》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半壁江山');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半壁山河','bàn bì shān hé','指国土的一部或大部分。','李光《集诗述感》半壁山河话战争，布衣空负鲁连名。”','从此，革命的烈焰，迅速蔓延开去，不公燃遍了祖国～，而且转战大江南北约二十年之久。★《前言》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半壁山河');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半部论语','bàn bù lún yǔ','旧时对儒学经典之一《论语》的夸赞之辞，掌握半部《论语》，人的能力就会提高，就能治理国家。','参见半部论语治天下”。','况且从前古人以～治天下，就是半部亦何妨。★清·李宝嘉《官场现形记》第六十四回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半部论语');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半部论语治天下','bàn bù lún yǔ zhì tiān xià','旧时用来强调学习儒家经典的重要。','典出宋·罗大经《鹤林玉露》卷七宋初宰相赵普，人言所读仅只《论语》而已。太宗赵匡义因此问他。他说臣平生所知，诚不出此，昔以其半辅太祖（赵匡胤）定天下，今欲以其半辅陛下致太平。”','每决大事，启文观书，乃《论语》也，此时称小官以～。★元·高文秀《遇上皇》第三折','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半部论语治天下');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半痴不颠','bàn chī bù diān','痴呆傻；颠同癲”，疯癲。装疯卖傻的意思。','明·冯梦龙《古今小说·蒋兴歌重会珍珠衫》这婆子俐齿伶牙，能言快语，又半痴不颠的惯与丫环们打诨，所以上下都喜欢他。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半痴不颠');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半筹不纳','bàn chóu bù nà','筹古代计算工具，引伸为计策；纳缴纳。半条计策也拿不出来。比喻一点办法也没有。','元·李文蔚《燕青博鱼》第一折往常时我习武艺学兵法，到如今半筹也不纳。”','万骑交驰，两军相见，咱手里～。★元·朱凯《昊天塔》第二折','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半筹不纳');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半筹莫展','bàn chóu mò chǎn','半条计策也拿不出来。比喻一点办法也没有。','明·罗贯中《三国演义》第五十四回周瑜虽能用计，岂能出诸葛亮之料乎？略用小谋，使周瑜半筹不展。”','惟自民国成立之日，则予三主张建设反致～，一败涂地。★孙中山《建国方略自序》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半筹莫展');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半低不高','bàn dī bù gāo','中等；不高不矮。','明·冯梦龙《醒世恒言》第三卷我们行户人家，到是养成个半低不高的丫头。”','她的身材～，还算不错。','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半低不高');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半懂不懂','bàn dǒng bù dǒng','不十分懂。','无','他对人说话，总是满口之乎者也，教人～的。★初三教案《孔乙己》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半懂不懂');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半饥半饱','bàn jī bàn bǎo','指不求甚饱或吃不饱饭。','明·冯梦龙《醒世恒言》第二十八卷若论起吴衙内，只算做半饥半饱，未能趁心像意。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半饥半饱');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半间半界','bàn gān bàn gà','①不彻底，不深透。②不明确。③犹言不上不下。④谓平庸。','参见半间不界”。','丘宗卿亦受群儿谤伤之言，～，州府卒归狱于赵穿，亮以此身既存而不复问矣。★宋·陈亮《又乙巳春书》之一','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半间半界');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半间不界','bàn gān bù gà','间、界今作尴尬”。比喻肤浅、不深刻，或做事不彻底。','《朱子语类·论语二九》便是世间有这一般半间不界底人，无见识，不顾理之是非一味漫人。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半间不界');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半截入土','bàn jié rù tǔ','截段。半段身子埋入土内。比喻人在世不久了。','宋·苏轼《东坡志林》卷十二汝已半截入土，犹争高下呼？”','差多了，差多了！人到三十五，就半截入土了。★老舍《谁先到了重庆》第一幕','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半截入土');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半解一知','bàn jiě yī zhī','指理解得不深，知道得很少。同一知半解”。','宁调元《燕京杂诗》半解一知天下士，千歧百辙釜中鱼。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半解一知');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半斤八两','bàn jīn bā liǎng','八两即半斤。一个半斤，一个八两。比喻彼此一样，不相上下。','宋·释普济《五灯会元》卷十一问‘来时无物去时空，二路俱迷，如何得不迷去？’师曰‘秤头半斤，秤尾八两。’”宋·释惟白《建中靖国续灯录》踏着秤锤硬似铁，八两元来是半斤。”','众将看他两个本事，都是～的，打扮也差不多。★明·施耐庵《水浒全传》第一百七回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半斤八两');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半斤八面','bàn jīn bā miàn','旧制一斤合十六两，半斤等于八两。一个半斤，一个八两，重量相等。比喻彼此一样，不分上下。亦作一个半斤，一个八两”、半斤对八两”。','无','林寒青仗着手中短剑锋利，把一个蓝衣武士手中长剑削去了一截，使两人不敢和他剑势硬接，保持个～之局。★李凉《天涯侠侣》第三十三章','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半斤八面');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半路出家','bàn lù chū jiā','原指成年后才出家做和尚或尼姑。比喻中途改行，从事另一工作。','《京本通俗小说·错斩崔宁》先前读书，后来看看不济，却去改业做生意。便是半路出家的一般。”','他不理会内行的嗤笑，写啊改啊，改啊写啊，终于写出了好作品，成了一个颇有希望的～的作家。★公今度《长短录》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半路出家');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半路修行','bàn lù xiū xíng','原指成年后才出家做和尚或尼姑。比喻中途改行，从事另一工作。','参见半路出家”。','茅鹿门先生，文章擅海内……晚喜作诗，自称～，语多率易。★明·朱国祯《晚幢小品·俚诗有本》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半路修行');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半梦半醒','bàn mèng bàn xǐng','指睡眠尚未清醒。','无','说来说去，我究竟在半梦半醒之间，做了些甚么呢？★倪匡《卖命·半梦半醒》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半梦半醒');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半面不忘','bàn miàn bù wàng','见过面就不遗忘。形容记忆力极强。','《后汉书·应奉传》李贤注引谢承《后汉书》造车匠于内开扇出半面视奉，奉即委去◇数十年于路见车匠，识而呼之。”《北齐书·杨愔传》其聪记强识，半面不忘。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半面不忘');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半面之交','bàn miàn zhī jiāo','同别人只见过一面的交情。意谓交情不深。','晋·袁彦伯《三国名人序赞》且观世人之相论也，徒以一面之交，定臧否之决。”','我与道友未～，此语从何而来。★明·许仲琳《封神演义》第六十二回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半面之交');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半面之旧','bàn miàn zhī jiù','意指只见过一面的人。','晋·袁彦伯《三国名人序赞》且观世人之相论也，徒以一面之交，定臧否之决。”','初应进士时，中朝无缌麻之亲，达官无～。★唐·白居易《与元九书》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半面之旧');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半青半黄','bàn qīng bàn huáng','农作物还没有长好，青黄相接。比喻时机还没有成熟。','《朱子全书·学》今既要理会，也要理会取透，莫要半青半黄，下梢都不济事。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半青半黄');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半三不四','bàn sān bù sì','指心神不定，无精打采。','《西湖二集·李凤娘酷妒遭天谴》勾引得官家一心在你身上，就在我身边，也是半三不四。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半三不四');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半上半下','bàn shàng bàn xià','指两可之间。','《朱子全书》卷十三年无改，谓是半上半下底事，在所当改者，但不可匆遽急改之。”又卷十九微子去却易，比干则索性死，他（箕子）在半上半下处，最是难。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半上半下');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半上落下','bàn shàng luò xià','犹半途而废。','《朱子全书》卷十五圣人不肯半上落下，直是做到底。”又卷九人各有个知识，须是推致而极其至，不然半上落下，终不济事。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半上落下');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半身不遂','bàn shēn bù suí','遂通随”，顺从。原为医学病症名，指半边身体不能随意转动。有时比喻诗文部分好，部分不好，不够匀称。','汉·张仲景《金匮要略·中风历节》无风之为病，当半身不遂，或但臂不遂者，此为痹脉微而数中风使然。”','蒋百里在宜山的南边路上，汽车坏了，他受了一下子凉，就有一点～的样子。★冯玉祥《我所认识的蒋介石》第三十一章','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半身不遂');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半生半熟','bàn shēng bàn shú','没有完全成熟或未烹煮至可食用的程度。比喻不熟悉；不熟练。','元·元怀《拊掌录》北都有妓女美色，而举止生梗，土人谓之生张八……野（魏野）赠之诗曰‘君为北道生张八，我是西州熟魏三，莫怪尊前无笑语，半生半熟未相谙。’座客大发一噱。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半生半熟');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半生不熟','bàn shēng bù shú','没有完全成熟或未烹煮至可食用的程度。比喻不熟悉；不熟练。','参见半生半熟”。','五个中间的老徐能说几句～的广府话。★茅盾《过封锁线》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半生不熟');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半丝半缕','bàn sī bàn lǜ','缕线。半根丝，半缕线。形容数量少，价值极其微小的东西。','清·朱柏庐《治家格言》一粥一饭，当思来处不易；半丝半缕，恒念物力维艰。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半丝半缕');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半死半生','bàn sǐ bàn shēng','①未全死。②比喻无意义、无生气的苟且的生活。','汉·枚乘《七发》龙门之桐，高百尺而无枝……其根半死半生。”','惟独～的苟活，是全盘失错的。因为他挂了生活的招牌，其实却引人到死路上去！”★鲁迅《华盖集·北京通信》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半死半生');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半死不活','bàn sǐ bù huó','指没有生机和活力。死又死不了，活着又受罪。','参见半死半生”。','想跑，水裹住他的脚，他就那么～的，低着头一步一步的往前曳。★老舍《骆驼祥子》十八','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半死不活');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半死辣活','bàn sǐ là huó','指死又死不了，活着又受罪。','参见半死半生”。','忙跑几步，好失了脚步掉得下去，好跌得烂酱如泥，免得～，常驻苦常驻罪。★《醒世姻缘传》第七五回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半死辣活');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半涂而罢','bàn tú ér bà','半路上终止。比喻做事情有始无终。同半涂而废”。','唐·韩愈《论淮西事宜状》陛下持之不坚，半涂而罢，伤威损费，为弊必深。”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半涂而罢');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半涂而废','bàn tú ér fèi','半路上终止。比喻做事情有始无终。','参见半途而废”。','臣等查该三厂开采，虽尚未见成效，然总须该地方官激励厂民，奋勉从事，不可任其～。★清·林则徐《查勘矿厂情形试行开采折》','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半涂而废');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半途而废','bàn tú ér fèi','废停止。指做事不能坚持到底，中途停顿，有始无终。','《礼记·中庸》君子遵道而行，半途而废，吾弗难已矣。”','如果就此请假回国，这里的事～，将来保举弄不到，白吃一趟辛苦。★清·李宝嘉《官场现形记》第五十六回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半途而废');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半吐半露','bàn tǔ bàn lù','形容说话含糊不清，不直截了当。','《红楼梦》第五十回因贾母尚未说明，自已也不好拟定，遂半吐半露告诉贾母道‘可惜了这孩子没福！前年他父亲就没了。’”','无','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半吐半露');
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_synonyms, idiom_antonyms, idiom_difficulty, idiom_tags) SELECT '半推半就','bàn tuī bàn jiù','推抵拒，推托；就靠拢，迎上去。一面推辞，一面靠拢上去。形容装腔作势假意推辞的样子。','元·王实甫《西厢记》第四本第一折半推半就，又惊又爱，檀口揾香腮。”','与其学那俗态，～，耽搁工夫；据俺主意，不如从实收了，倒也爽快。★清·李汝珍《镜花缘》第三回','','',1,'' WHERE NOT EXISTS (SELECT 1 FROM edu_idiom WHERE idiom_content = '半推半就');

-- ---------- 学段/年级映射 ----------
INSERT INTO edu_idiom_grade (idiom_id, grade_level, grade) SELECT i.idiom_id, '小学', '一年级' FROM edu_idiom i WHERE i.idiom_content IN ('守株待兔','亡羊补牢','画蛇添足','掩耳盗铃','井底之蛙','拔苗助长','刻舟求剑','自相矛盾','三心二意','手不释卷','专心致志','聚沙成塔','百发百中','锲而不舍','悬梁刺股','闻鸡起舞','卧薪尝胆','一鼓作气','不耻下问','温故知新','名副其实','眼花缭乱','栩栩如生','念念不忘','循序渐进','融会贯通','举一反三','触类旁通','一丝不苟','精益求精','心无旁骛','孜孜不倦','阿鼻地狱','阿党比周','阿党相为','阿狗阿猫','阿姑阿翁','阿家阿翁','阿娇金屋','阿猫阿狗','阿弥陀佛','阿毗地狱','阿平绝倒','阿其所好','阿时趋俗','阿世盗名','阿世媚俗','阿世取容','阿意取容','阿谀谄媚','阿谀逢迎','阿谀奉承','阿谀取容','哀哀父母','哀哀欲绝','哀兵必胜','哀而不伤','哀感天地','哀感顽艳','哀感中年','哀告宾服','哀鸿遍地','哀鸿遍野','哀毁骨立','哀毁瘠立','哀梨蒸食','哀莫大于心死','哀声叹气','哀丝豪竹','哀思如潮','哀天叫地','哀痛欲绝','唉声叹气','挨冻受饿','挨风缉缝','挨家挨户','挨肩并足','挨肩擦膀','挨肩擦背','挨肩擦脸','挨肩搭背','挨肩迭背','挨肩叠背','挨门挨户','挨门逐户','挨三顶五','挨山塞海','捱风缉缝','捱三顶四','捱三顶五','嗳声叹气','矮人观场','矮人看场','矮人看戏','矮子观场','矮子看戏','蔼然可亲','蔼然仁者','爱别离苦','爱博而情不专','爱不忍释','爱不释手','爱才如渴','爱才如命','爱才若渴','爱财如命','爱富嫌贫','爱国如家','爱鹤失众','爱礼存羊','爱毛反裘','爱民如子','爱莫能助','爱莫之助','爱钱如命','爱人好士','爱人利物','爱人以德','爱日惜力','爱如己出','爱如珍宝','爱生恶死','爱素好古','爱屋及乌','爱惜羽毛','爱则加诸膝，恶则坠诸渊','爱憎分明','爱之欲其生','爱之欲其生，恶之欲其死','碍口识羞','碍难从命','碍手碍脚') AND NOT EXISTS (SELECT 1 FROM edu_idiom_grade g WHERE g.idiom_id = i.idiom_id AND g.grade_level = '小学');
INSERT INTO edu_idiom_grade (idiom_id, grade_level, grade) SELECT i.idiom_id, '初中', '初一' FROM edu_idiom i WHERE i.idiom_content IN ('循序渐进','融会贯通','举一反三','触类旁通','一丝不苟','精益求精','心无旁骛','孜孜不倦') AND NOT EXISTS (SELECT 1 FROM edu_idiom_grade g WHERE g.idiom_id = i.idiom_id AND g.grade_level = '初中');
INSERT INTO edu_idiom_grade (idiom_id, grade_level, grade) SELECT i.idiom_id, '小学', '一年级' FROM edu_idiom i WHERE i.idiom_content IN ('阿鼻地狱','阿党比周','阿党相为','阿狗阿猫','阿姑阿翁','阿家阿翁','阿娇金屋','阿猫阿狗','阿弥陀佛','阿毗地狱','阿平绝倒','阿其所好','阿时趋俗','阿世盗名','阿世媚俗','阿世取容','阿意取容','阿谀谄媚','阿谀逢迎','阿谀奉承','阿谀取容','哀哀父母','哀哀欲绝','哀兵必胜','哀而不伤','哀感天地','哀感顽艳','哀感中年','哀告宾服','哀鸿遍地','哀鸿遍野','哀毁骨立','哀毁瘠立','哀梨蒸食','哀莫大于心死','哀声叹气','哀丝豪竹','哀思如潮','哀天叫地','哀痛欲绝','唉声叹气','挨冻受饿','挨风缉缝','挨家挨户','挨肩并足','挨肩擦膀','挨肩擦背','挨肩擦脸','挨肩搭背','挨肩迭背','挨肩叠背','挨门挨户','挨门逐户','挨三顶五','挨山塞海','捱风缉缝','捱三顶四','捱三顶五','嗳声叹气','矮人观场','矮人看场','矮人看戏','矮子观场','矮子看戏','蔼然可亲','蔼然仁者','爱别离苦','爱博而情不专','爱不忍释','爱不释手','爱才如渴','爱才如命','爱才若渴','爱财如命','爱富嫌贫','爱国如家','爱鹤失众','爱礼存羊','爱毛反裘','爱民如子','爱莫能助','爱莫之助','爱钱如命','爱人好士','爱人利物','爱人以德','爱日惜力','爱如己出','爱如珍宝','爱生恶死','爱素好古','爱屋及乌','爱惜羽毛','爱则加诸膝，恶则坠诸渊','爱憎分明','爱之欲其生','爱之欲其生，恶之欲其死','碍口识羞','碍难从命','碍手碍脚','暗室逢灯','暗室亏心','暗室欺心','暗室求物','暗室私心','暗室屋漏','暗送秋波','暗无天日','暗香疏影','暗中摸索','黯淡无光','黯晦消沉','黯然伤神','黯然神伤','黯然失色','黯然无色','黯然销魂','昂昂自若','昂藏七尺','昂然直入','昂然自得','昂然自若','昂首阔步','昂首伸眉','昂首天外','昂首挺胸','昂首望天','昂头阔步','昂头天外','昂头挺胸','昂霄耸壑','盎盂相击','盎盂相敲','敖不可长','敖世轻物','嗷嗷待哺','遨翔自得','熬肠刮肚','熬更守夜','熬姜呷醋','熬枯受淡','熬清守淡','熬清守谈','熬清受淡','熬心费力','熬油费火','聱牙佶屈','聱牙戟口','聱牙诘曲','聱牙诘屈','謷牙诘屈','鳌愤龙愁','鳌里夺尊','鳌鸣鳖应','鳌头独占','鳌掷鲸呿','鳌掷鲸吞','傲不可长','傲骨嶙嶙','傲慢不逊','傲慢少礼','傲慢无礼','傲睨得志','傲睨万物','傲睨一切','傲睨一世','傲睨自若','傲然挺立','傲然屹立','傲世轻物','傲霜斗雪','傲头傲脑','傲贤慢士','傲雪凌霜','傲雪欺霜','奥妙无穷','奥援有灵','懊悔无及','八百孤寒','八拜之交','八窗玲珑','八斗之才','八方风雨','八方呼应','八方支持','八方支援','八府巡按','八公山上，草木皆兵','八纮同轨','八荒之外','八街九陌','八九不离十','八面见光','八面玲珑','八面驶风','八面受敌','八面威风','八面莹澈','八面圆通','八面张罗','八难三灾','八万四千','八仙过海','八仙过海，各显神通','八音迭奏','八音遏密','八珍玉食','八字打开','八字没见一撇','八字没一撇','巴巴急急','巴巴劫劫','巴巴结结','巴高望上','巴高枝儿','巴前算后','巴人下里','巴三览四','巴三揽四','巴山度岭','巴山蜀水','巴山夜雨','巴山越岭','巴头探脑','扒耳搔腮','扒高踩低','抜格不入','拔本塞原','拔本塞源','拔丛出类','拔萃出类','拔萃出群','拔刀相向','拔刀相助','拔地参天','拔地摇山','拔地倚天','拔丁抽楔','拔锅卷席','拔剑论功','拔葵啖枣','拔葵去织','拔来报往','拔类超群','拔了萝卜地皮宽','拔毛济世','拔毛连茹','拔茅连茹','拔群出萃','拔山超海','拔山盖世','拔山举鼎','拔山扛鼎','拔十得五','拔十失五','拔树撼山','拔树搜根','拔树寻根','拔犀擢象','拔辖投井','拔新领异','拔宅飞升','拔宅上升','拔赵易汉','拔赵帜立赤帜','拔赵帜易汉帜','拔帜树帜','拔帜易帜','跋胡痜尾','跋扈飞扬','跋扈自恣','跋扈恣睢','跋来报往','跋履山川','跋前踕后','跋前痜后','跋前踬后','跋山涉川','跋山涉水','跋涉山川','把臂入林','把臂徐去','把持不定','把饭叫饥','把酒持螯','把素持斋','把玩无厌','把薪助火','罢黜百家','霸道横行','霸陵醉尉','霸王别姬','霸王风月','白白朱朱','白璧青蝇','白璧三献','白璧微瑕','白璧无瑕','白草黄云','白齿青眉','白刀子进，红刀子出','白藋同心','白丁俗客','白发苍苍','白发苍颜','白发丹心','白发红颜','白发千丈','白发青衫','白发朱颜','白饭青刍','白骨露野','白骨再肉','白圭之玷','白黑不分','白黑颠倒','白黑分明','白虹贯日','白华之怨','白话八股','白袷蓝衫','白驹过隙','白驹空谷','白蜡明经','白浪掀天','白龙鱼服','白旄黄钺','白眉赤眼','白面儒冠','白面儒生','白面书郎','白面书生','白帢青衫','白日飞升','白日见鬼','白日青天','白日上升','白日升天','白日绣衣','白日衣绣','白日作梦','白日做梦','白色恐怖','白沙在涅，与之俱黑','白山黑水','白手成家','白手空拳','白手起家','白手兴家','白首北面','白首不渝','白首空归','白首齐眉','白首穷经','白首如新','白首同归','白首为郎','白首相知','白首相庄','白首一节','白首之心','白水鉴心','白叟黄童','白天见鬼','白头不终','白头到老','白头而新','白头如新','白头相并','白头相守','白头偕老','白头之叹','白兔赤乌','白往黑来','白屋寒门','白屋之士','白雪皑皑','白雪难和','白雪阳春','白眼相看','白衣苍狗','白衣公卿','白衣卿相','白衣秀士','白衣宰相','白蚁争穴','白鱼赤乌','白鱼登舟','白鱼入舟','白玉微瑕','白玉无瑕','白云苍狗','白云孤飞','白云亲舍','白纸黑字','百般刁难','百般奉承','百般抚慰','百般折磨','百宝万货','百弊丛生','百不当一','碍足碍手','暧昧不明','暧昧之情','安安稳稳','安邦定国','安邦治国','安不忘危','安步当车','安常处顺','安常履顺','安常守分','安常守故','安常习故','安车蒲轮','安堵乐业','安堵如故','安分守己','安分守已','安分知足','安份守己','安富恤贫','安富恤穷','安富尊荣','安故重迁','安国富民','安国宁家','安魂定魄','安家乐业','安家立业','安家落户','安居乐业','安老怀少','安乐窝','安良除暴','安眉带眼','安民告示','安内攘外','安贫乐道','安贫乐贱','安贫守道','安然如故','安然无事','安然无恙','安忍无亲','安如盘石','安如磐石','安如太山','安如泰山','安若泰山','安身乐业','安身立命','安身为乐','安身之处','安身之地','安神定魄','安生服业','安生乐业','安时处顺','安室利处','安适如常','安土乐业','安土重旧','安土重居','安土重迁','安危相易','安危与共','安闲自得','安闲自在','安心定志','安心乐业','安心乐意','安心落意','安营下寨','安营扎寨','安于故俗，溺于旧闻','安于盘石','安于现状','安于一隅','安宅正路','安枕而卧','安之若命','安之若素','安坐待毙','鞍不离马，甲不离身','鞍不离马背，甲不离将身','鞍马劳顿','鞍马劳倦','鞍马劳困','鞍马劳神','鞍马之劳','鞍前马后','岸谷之变','岸然道貌','按兵不动','按兵不举','按兵束甲','按步就班','按部就班','按堵如故','按甲寝兵','按甲休兵','按名责实','按捺不住','按辔徐行','按图索骥','按图索骏','按行自抑','案兵束甲','案牍劳形','案牍之劳','案甲休兵','案剑瞋目','案无留牍','暗锤打人','暗淡无光','暗斗明争','暗度陈仓','暗度金针','暗渡陈仓','暗箭明枪','暗箭难防','暗箭伤人','暗箭中人','暗绿稀红','暗昧之事','暗气暗恼','暗弱无断','暗室不欺','百不获一','百不失一','百不为多，一不为少','百不一存','百不一贷','百不一遇','百步穿杨','百城之富','百尺竿头','百尺竿头，更进一步','百尺无枝','百川朝海','百川赴海','百川归海','百辞莫辩','百代过客','百代文宗','百动不如一静','百读不厌','百堵皆作','百端待举','百端交集','百堕俱举','百二关河','百二河山','百二金瓯','百二山川','百二山河','百凡待举','百废待举','百废待兴','百废具举','百废具兴','百废具作','百废俱举','百废俱兴','百废咸举','百福具臻','百感交集','百怪千奇','百鬼众魅','百花齐放','百花齐放，百家争鸣','百花生日','百花争妍','百花争艳','百卉千葩','百喙莫辩','百喙难辩','百喙难辞','百喙如一','百喙一词','百计千方','百计千谋','百计千心','百家争鸣','百结愁肠','百结悬鹑','百举百捷','百举百全','百孔千疮','百孔千创','百口莫辩','百口难分','百口同声','百里挑一','百里之才','百炼成刚','百炼成钢','百了千当','百伶百俐','百龄眉寿') AND NOT EXISTS (SELECT 1 FROM edu_idiom_grade g WHERE g.idiom_id = i.idiom_id AND g.grade_level = '小学');
INSERT INTO edu_idiom_grade (idiom_id, grade_level, grade) SELECT i.idiom_id, '小学', NULL FROM edu_idiom i WHERE i.idiom_content IN ('暗室逢灯','暗室亏心','暗室欺心','暗室求物','暗室私心','暗室屋漏','暗送秋波','暗无天日','暗香疏影','暗中摸索','黯淡无光','黯晦消沉','黯然伤神','黯然神伤','黯然失色','黯然无色','黯然销魂','昂昂自若','昂藏七尺','昂然直入','昂然自得','昂然自若','昂首阔步','昂首伸眉','昂首天外','昂首挺胸','昂首望天','昂头阔步','昂头天外','昂头挺胸','昂霄耸壑','盎盂相击','盎盂相敲','敖不可长','敖世轻物','嗷嗷待哺','遨翔自得','熬肠刮肚','熬更守夜','熬姜呷醋','熬枯受淡','熬清守淡','熬清守谈','熬清受淡','熬心费力','熬油费火','聱牙佶屈','聱牙戟口','聱牙诘曲','聱牙诘屈','謷牙诘屈','鳌愤龙愁','鳌里夺尊','鳌鸣鳖应','鳌头独占','鳌掷鲸呿','鳌掷鲸吞','傲不可长','傲骨嶙嶙','傲慢不逊','傲慢少礼','傲慢无礼','傲睨得志','傲睨万物','傲睨一切','傲睨一世','傲睨自若','傲然挺立','傲然屹立','傲世轻物','傲霜斗雪','傲头傲脑','傲贤慢士','傲雪凌霜','傲雪欺霜','奥妙无穷','奥援有灵','懊悔无及','八百孤寒','八拜之交','八窗玲珑','八斗之才','八方风雨','八方呼应','八方支持','八方支援','八府巡按','八公山上，草木皆兵','八纮同轨','八荒之外','八街九陌','八九不离十','八面见光','八面玲珑','八面驶风','八面受敌','八面威风','八面莹澈','八面圆通','八面张罗','八难三灾','八万四千','八仙过海','八仙过海，各显神通','八音迭奏','八音遏密','八珍玉食','八字打开','八字没见一撇','八字没一撇','巴巴急急','巴巴劫劫','巴巴结结','巴高望上','巴高枝儿','巴前算后','巴人下里','巴三览四','巴三揽四','巴山度岭','巴山蜀水','巴山夜雨','巴山越岭','巴头探脑','扒耳搔腮','扒高踩低','抜格不入','拔本塞原','拔本塞源','拔丛出类','拔萃出类','拔萃出群','拔刀相向','拔刀相助','拔地参天','拔地摇山','拔地倚天','拔丁抽楔','拔锅卷席','拔剑论功','拔葵啖枣','拔葵去织','拔来报往','拔类超群','拔了萝卜地皮宽','拔毛济世','拔毛连茹','拔茅连茹','拔群出萃','拔山超海','拔山盖世','拔山举鼎','拔山扛鼎','拔十得五','拔十失五','拔树撼山','拔树搜根','拔树寻根','拔犀擢象','拔辖投井','拔新领异','拔宅飞升','拔宅上升','拔赵易汉','拔赵帜立赤帜','拔赵帜易汉帜','拔帜树帜','拔帜易帜','跋胡痜尾','跋扈飞扬','跋扈自恣','跋扈恣睢','跋来报往','跋履山川','跋前踕后','跋前痜后','跋前踬后','跋山涉川','跋山涉水','跋涉山川','把臂入林','把臂徐去','把持不定','把饭叫饥','把酒持螯','把素持斋','把玩无厌','把薪助火','罢黜百家','霸道横行','霸陵醉尉','霸王别姬','霸王风月','白白朱朱','白璧青蝇','白璧三献','白璧微瑕','白璧无瑕','白草黄云','白齿青眉','白刀子进，红刀子出','白藋同心','白丁俗客','白发苍苍','白发苍颜','白发丹心','白发红颜','白发千丈','白发青衫','白发朱颜','白饭青刍','白骨露野','白骨再肉','白圭之玷','白黑不分','白黑颠倒','白黑分明','白虹贯日','白华之怨','白话八股','白袷蓝衫','白驹过隙','白驹空谷','白蜡明经','白浪掀天','白龙鱼服','白旄黄钺','白眉赤眼','白面儒冠','白面儒生','白面书郎','白面书生','白帢青衫','白日飞升','白日见鬼','白日青天','白日上升','白日升天','白日绣衣','白日衣绣','白日作梦','白日做梦','白色恐怖','白沙在涅，与之俱黑','白山黑水','白手成家','白手空拳','白手起家','白手兴家','白首北面','白首不渝','白首空归','白首齐眉','白首穷经','白首如新','白首同归','白首为郎','白首相知','白首相庄','白首一节','白首之心','白水鉴心','白叟黄童','白天见鬼','白头不终','白头到老','白头而新','白头如新','白头相并','白头相守','白头偕老','白头之叹','白兔赤乌','白往黑来','白屋寒门','白屋之士','白雪皑皑','白雪难和','白雪阳春','白眼相看','白衣苍狗','白衣公卿','白衣卿相','白衣秀士','白衣宰相','白蚁争穴','白鱼赤乌','白鱼登舟','白鱼入舟','白玉微瑕','白玉无瑕','白云苍狗','白云孤飞','白云亲舍','白纸黑字','百般刁难','百般奉承','百般抚慰','百般折磨','百宝万货','百弊丛生','百不当一','碍足碍手','暧昧不明','暧昧之情','安安稳稳','安邦定国','安邦治国','安不忘危','安步当车','安常处顺','安常履顺','安常守分','安常守故','安常习故','安车蒲轮','安堵乐业','安堵如故','安分守己','安分守已','安分知足','安份守己','安富恤贫','安富恤穷','安富尊荣','安故重迁','安国富民','安国宁家','安魂定魄','安家乐业','安家立业','安家落户','安居乐业','安老怀少','安乐窝','安良除暴','安眉带眼','安民告示','安内攘外','安贫乐道','安贫乐贱','安贫守道','安然如故','安然无事','安然无恙','安忍无亲','安如盘石','安如磐石','安如太山','安如泰山','安若泰山','安身乐业','安身立命','安身为乐','安身之处','安身之地','安神定魄','安生服业','安生乐业','安时处顺','安室利处','安适如常','安土乐业','安土重旧','安土重居','安土重迁','安危相易','安危与共','安闲自得','安闲自在','安心定志','安心乐业','安心乐意','安心落意','安营下寨','安营扎寨','安于故俗，溺于旧闻','安于盘石','安于现状','安于一隅','安宅正路','安枕而卧','安之若命','安之若素','安坐待毙','鞍不离马，甲不离身','鞍不离马背，甲不离将身','鞍马劳顿','鞍马劳倦','鞍马劳困','鞍马劳神','鞍马之劳','鞍前马后','岸谷之变','岸然道貌','按兵不动','按兵不举','按兵束甲','按步就班','按部就班','按堵如故','按甲寝兵','按甲休兵','按名责实','按捺不住','按辔徐行','按图索骥','按图索骏','按行自抑','案兵束甲','案牍劳形','案牍之劳','案甲休兵','案剑瞋目','案无留牍','暗锤打人','暗淡无光','暗斗明争','暗度陈仓','暗度金针','暗渡陈仓','暗箭明枪','暗箭难防','暗箭伤人','暗箭中人','暗绿稀红','暗昧之事','暗气暗恼','暗弱无断','暗室不欺','百不获一','百不失一','百不为多，一不为少','百不一存','百不一贷','百不一遇','百步穿杨','百城之富','百尺竿头','百尺竿头，更进一步','百尺无枝','百川朝海','百川赴海','百川归海','百辞莫辩','百代过客','百代文宗','百动不如一静','百读不厌','百堵皆作','百端待举','百端交集','百堕俱举','百二关河','百二河山','百二金瓯','百二山川','百二山河','百凡待举','百废待举','百废待兴','百废具举','百废具兴','百废具作','百废俱举','百废俱兴','百废咸举','百福具臻','百感交集','百怪千奇','百鬼众魅','百花齐放','百花齐放，百家争鸣','百花生日','百花争妍','百花争艳','百卉千葩','百喙莫辩','百喙难辩','百喙难辞','百喙如一','百喙一词','百计千方','百计千谋','百计千心','百家争鸣','百结愁肠','百结悬鹑','百举百捷','百举百全','百孔千疮','百孔千创','百口莫辩','百口难分','百口同声','百里挑一','百里之才','百炼成刚','百炼成钢','百了千当','百伶百俐','百龄眉寿','百虑一致','百虑攒心','百媚千娇','百密一疏','百谋千计','百年不遇','百年大计','百年大业','百年好合','百年好事','百年难遇','百年树人','百年偕老','百年谐老','百年之柄','百年之好','百年之后','百年之业','百年之约','百念皆灰','百鸟朝凤','百品千条','百巧成穷','百巧千穷','百忍成金','百舌之声','百舍重茧','百舍重趼','百身何赎','百身莫赎','百世不磨','百世不易','百世流芳','百世流芬','百世师','百世一人','百世之利','百世之师','百事大吉','百事无成','百兽率舞','百顺百依','百顺千随','百思不得其解','百思不解','百思莫解','百死一生','百岁千秋','百岁之好','百岁之后','百岁之盟','百万买宅，千万买邻','百万雄兵','百万雄师','百闻不如一见','百无禁忌','百无聊赖','百无是处','百无所成','百无一成','百无一堪','百无一能','百无一失','百无一是','百无一用','百下百全','百下百着','百星不如一月','百业萧条','百衣百随','百依百从','百依百顺','百依百随','百载树人','百战百败','百战百胜','百战不殆','百丈竿头','百折不摧','百折不回','百折不挠','百折不屈','百折不移','百折千回','百中百发','百啭千声','百拙千丑','百紫千红','百纵千随','百足不僵','百足之虫','百足之虫，断而不蹶','百足之虫，死而不僵','百足之虫，至断不蹶','百足之虫，至死不僵','柏舟之节','柏舟之誓','捭阖纵横','摆尾摇头','摆袖却金','败不旋踵','败材伤锦','败德辱行','败法乱纪','败鼓之皮','败国丧家','败国亡家','败化伤风','败绩失据','败将残兵','败井颓垣','败军之将','败鳞残甲','败柳残花','败事有余，成事不足','败俗伤风','败俗伤化','败兴而归','败于垂成','拜赐之师','拜倒辕门','拜恩私室','拜鬼求神','拜将封侯','稗官小说','稗官野史','扳龙附凤','班班可考','班驳陆离','班功行赏','班荆道故','班荆道旧','班马文章','班门弄斧','班师得胜','班师回朝','班师回俯','班香宋艳','班衣戏采','班衣戏彩','斑驳陆离','斑衣戏彩','搬唇递舌','搬唇弄舌','搬斤播两','搬口弄舌','搬弄是非','搬起石头打自己的脚','搬石砸脚','阪上走丸','板板六十四','板上钉钉','板上砸钉','版版六十四','版筑饭牛','半半拉拉','半半路路','半壁河山','半壁江山','半壁山河','半部论语','半部论语治天下','半痴不颠','半筹不纳','半筹莫展','半低不高','半懂不懂','半饥半饱','半间半界','半间不界','半截入土','半解一知','半斤八两','半斤八面','半路出家','半路修行','半梦半醒','半面不忘','半面之交','半面之旧','半青半黄','半三不四','半上半下','半上落下','半身不遂','半生半熟','半生不熟','半丝半缕','半死半生','半死不活','半死辣活','半涂而罢','半涂而废','半途而废','半吐半露','半推半就') AND NOT EXISTS (SELECT 1 FROM edu_idiom_grade g WHERE g.idiom_id = i.idiom_id AND g.grade_level = '小学');

-- 成语去重清理（修复：早期 V1.8 使用 INSERT IGNORE，而唯一约束 (owner, content) 含 NULL 归属时 NULL 互不相等，
-- 重复执行会插入重复行）。清理策略：同内容仅保留最早一条 + 清理指向重复行的学段映射。
-- 注意：不可按「无学段映射」删除成语——通过成语库新增未选学段属合法数据，会被误删。

-- 1) 学段映射：先删除指向重复成语的行（保留会被留下的最小 idiom_id）
DELETE FROM edu_idiom_grade
WHERE idiom_id IN (
    SELECT i.idiom_id FROM edu_idiom i
    WHERE i.idiom_id <> (SELECT MIN(x.idiom_id) FROM edu_idiom x WHERE x.idiom_content = i.idiom_content)
);

-- 2) 重复成语：同内容仅保留最小 idiom_id
DELETE FROM edu_idiom
WHERE idiom_id NOT IN (SELECT MIN(x.idiom_id) FROM edu_idiom x GROUP BY x.idiom_content);

-- 3) 同一条成语在同一学段/年级下的重复映射：仅保留最早一条
DELETE FROM edu_idiom_grade
WHERE map_id NOT IN (
    SELECT MIN(map_id) FROM edu_idiom_grade GROUP BY idiom_id, grade_level, COALESCE(grade, '')
);

-- 4) 「学段通用(grade IS NULL)」与「具体年级」重叠时，去掉通用行：否则消消乐按年级取数会重复命中同一条成语
DELETE FROM edu_idiom_grade
WHERE grade IS NULL
  AND EXISTS (
      SELECT 1 FROM edu_idiom_grade x
      WHERE x.idiom_id = edu_idiom_grade.idiom_id
        AND x.grade_level = edu_idiom_grade.grade_level
        AND x.grade IS NOT NULL
  );

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
    ('小学', '1', 1),('小学', '2', 2),('小学', '3', 3),
    ('小学', '4', 4),('小学', '5', 5),('小学', '6', 6),
    ('初中', '初一', 1),('初中', '初二', 2),('初中', '初三', 3),
    ('高中', '高一', 1),('高中', '高二', 2),('高中', '高三', 3),
    ('大学', '大一', 1),('大学', '大二', 2),('大学', '大三', 3),('大学', '大四', 4);


-- ============================================================
-- merged from V1.8__edu_idiom_seed.sql (DDL part)
-- ============================================================
-- 成语库初始化：内容去重索引（DDL）+ 常用成语与学段映射（initdata）
-- 说明：消消乐按 edu_idiom_grade 的 INNER JOIN 取数，故种子数据必须同时建立学段映射。

-- ----------------------------
-- DDL：同一归属下成语内容唯一（归属为 NULL 视为系统内置），保证采集去重可靠
-- ----------------------------

-- ============================================================
-- -- 昆虫图鉴种子数据（insect-world开源项目，63 个物种 / 14 个目）
-- 来源：https://github.com/xr843/insect-world 中文数据包（63 种 / 14 个目）
-- 说明：insect_facts / insect_hotspots / insect_lifecycle / insect_relatives
--       以 JSON 文本落库，由后端原样返回，前端按结构渲染。
--       insect_difficulty 由「目内物种数」排名推导：常见目简单，稀有目难。
-- ============================================================

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'rhinoceros-beetle', '双叉犀金龟', 'Trypoxylus dichotomus', '腐叶堆里长大的角斗士', 'coleoptera', '双叉犀金龟是东亚林地常见的大型金龟子，雄虫头部与前胸各生一支犄角，用于争夺树液与配偶而非用于飞行。它白天蛰伏于腐殖土或树皮缝隙，入夜后飞往栎树等阔叶树干舔食渗出的树液。', '[{"key":"体长","value":"不含角约30–55毫米，雄虫连角可达70毫米以上","icon":"size"},{"key":"负重","value":"可拖拽相当于自身体重数十倍的重物","icon":"weight"},{"key":"生活周期","value":"幼虫期长达8–10个月，成虫寿命却只有1–2个月","icon":"time"},{"key":"栖息环境","value":"阔叶林及次生林的腐殖土与树皮缝隙","icon":"place"},{"key":"食性","value":"幼虫食腐殖质与朽木碎屑，成虫舔食树干渗出的树液","icon":"food"},{"key":"飞行","value":"鞘翅掀起后露出膜质后翅，但体态笨重、飞行显得迟缓","icon":"ability"}]', '[{"id":"rhinoceros-beetle-horn","label":"头角","note":"雄虫头部前伸的分叉长角，打斗时用于挑起并掀翻对手","anchor":"horn","tone":"coral"},{"id":"rhinoceros-beetle-thoraxHorn","label":"胸角","note":"前胸背板上的短角，与头角对握形成钳状发力点","anchor":"thoraxHorn","tone":"sage"},{"id":"rhinoceros-beetle-elytra","label":"鞘翅","note":"坚硬的前翅特化成鞘翅，飞行时掀起以露出下方的膜质后翅","anchor":"elytra","tone":"amber"},{"id":"rhinoceros-beetle-eye","label":"复眼","note":"由许多小眼拼成，对移动的光影敏感，夜间趋光","anchor":"eye","tone":"lavender"},{"id":"rhinoceros-beetle-antenna","label":"鳃叶状触角","note":"末端呈薄片状可开合，展开后能扩大接收气味分子的面积","anchor":"antenna","tone":"coral"},{"id":"rhinoceros-beetle-foreleg","label":"前足","note":"末端具粗壮爪钩，用于攀附树皮和翻转对手","anchor":"foreleg","tone":"sage"}]', '成虫夜晚聚集在同一处树液伤口取食，与锹甲、蛾类共享这一有限资源；幼虫则在土壤中分解落叶与朽木，是森林枯落物循环的重要参与者。', '它一生中当幼虫的时间长达8~10个月，而顶着犄角活动的成虫阶段通常只有1~2个月，头角只是生命尾声的短暂装备。', 'complete', '["卵","幼虫（三龄，8–10个月）","蛹","成虫（1–2个月）"]', '中国、朝鲜半岛、日本及中南半岛北部', '常见，日本长期人工繁育作宠物及观赏昆虫', '["长戟大兜虫","高加索南洋大兜虫","日本锹甲"]', '#9c5a34', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'rhinoceros-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'monarch-butterfly', '帝王蝶', 'Danaus plexippus', '会认路的橙色候鸟', 'lepidoptera', '帝王蝶是北美洲最著名的长距离迁徙蝴蝶，翅膀橙黑相间的警戒色提醒天敌它体内积蓄的马利筋毒素。每年秋天，最后一代成虫会独自飞行数千公里，抵达自己从未去过的墨西哥冬眠地。', '[{"key":"翼展","value":"约9–10厘米，是北美体型最大的迁徙蝴蝶之一","icon":"size"},{"key":"体重","value":"成虫体重约0.5克，却能持续飞行数千公里","icon":"weight"},{"key":"世代寿命","value":"夏季世代成虫仅活2–6周，迁徙世代可存活8–9个月","icon":"time"},{"key":"栖息环境","value":"开阔草地、田野及马利筋分布区，越冬于墨西哥中部冷杉林","icon":"place"},{"key":"食性","value":"幼虫只取食马利筋叶片，成虫访花吸食多种植物花蜜","icon":"food"},{"key":"导航","value":"依靠太阳角度与体内生物钟校准方向，迁徙可达约4000公里","icon":"ability"}]', '[{"id":"monarch-butterfly-forewing","label":"前翅","note":"橙底黑脉的警戒色，提醒鸟类天敌体内藏有苦味毒素","anchor":"forewing","tone":"sage"},{"id":"monarch-butterfly-hindwing","label":"后翅","note":"内侧的黑色性标斑是区分雄虫与雌虫的关键部位","anchor":"hindwing","tone":"amber"},{"id":"monarch-butterfly-proboscis","label":"喙管","note":"可卷曲的细长口器，取食时伸直探入花冠吸食花蜜","anchor":"proboscis","tone":"lavender"},{"id":"monarch-butterfly-antenna","label":"触角","note":"棒状触角末端膨大，参与感知气味与导航节律","anchor":"antenna","tone":"coral"},{"id":"monarch-butterfly-eye","label":"复眼","note":"宽视角复眼，迁徙途中辅助识别地标与太阳角度","anchor":"eye","tone":"sage"},{"id":"monarch-butterfly-abdomen","label":"腹部","note":"储存幼虫期积累的马利筋毒素，形成对天敌的化学防御","anchor":"abdomen","tone":"amber"}]', '成虫是多种野花的传粉者之一；幼虫专食马利筋并把毒素积蓄在体内、保留至成虫阶段，形成对鸟类等天敌的化学防御。', '完成越冬迁徙的这一代帝王蝶从未去过墨西哥，靠的是依赖太阳位置与体内生物钟校准的方向感，阴天时还能切换到感知地磁场的备用系统。', 'complete', '["卵","幼虫（五龄）","蛹（悬蛹）","成虫"]', '北美洲（繁殖地加拿大南部至美国、墨西哥），越冬于墨西哥中部冷杉林及加州沿岸；引入种群见于大洋洲和伊比利亚半岛', '迁徙种群数量较20世纪末显著下降，2022年被IUCN列为濒危', '["副王蛱蝶","金斑蝶","女王斑蝶"]', '#cb7a2e', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'monarch-butterfly');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'honeybee', '西方蜜蜂', 'Apis mellifera', '用舞蹈画地图的建筑师', 'hymenoptera', '西方蜜蜂是社会性程度最高的传粉昆虫之一，蜂群由一只蜂后、数万只工蜂和少量雄蜂组成，靠信息素与舞蹈协调分工。工蜂在数周的一生里依次承担清巢、育幼、筑巢与采集等不同工种。', '[{"key":"体长","value":"工蜂约12–13毫米，蜂后可达18–20毫米","icon":"size"},{"key":"体重","value":"工蜂约90毫克，一生所能酿出的蜂蜜不足1克","icon":"weight"},{"key":"寿命","value":"夏季工蜂约5–6周，越冬工蜂可存活数月","icon":"time"},{"key":"栖息环境","value":"营巢于树洞、岩缝或人工蜂箱，蜂群终年不冬眠","icon":"place"},{"key":"食性","value":"采集花蜜与花粉为食，花蜜酿成蜂蜜作越冬储粮","icon":"food"},{"key":"通讯","value":"以摆尾舞的方向与时长，向同伴传递蜜源方位和距离","icon":"ability"}]', '[{"id":"honeybee-stinger","label":"螫针","note":"带倒钩不能拔出，蜇人后因内脏撕裂而死亡，仅限工蜂","anchor":"stinger","tone":"amber"},{"id":"honeybee-pollenBasket","label":"花粉篮","note":"后足胫节凹陷构造，用于携带压实的花粉团返巢","anchor":"pollenBasket","tone":"lavender"},{"id":"honeybee-wing","label":"翅","note":"前后翅以翅钩列连锁成一片，振翅频率约230次/秒","anchor":"wing","tone":"coral"},{"id":"honeybee-eye","label":"复眼","note":"能感知紫外光，辨认花朵上人眼看不见的导蜜纹","anchor":"eye","tone":"sage"},{"id":"honeybee-antenna","label":"触角","note":"感知气味与振动，是蜂群交流信息素信号的主要器官","anchor":"antenna","tone":"amber"},{"id":"honeybee-thorax","label":"胸部","note":"着生两对翅与三对足，飞行肌肉产热还能维持蜂巢温度","anchor":"thorax","tone":"lavender"}]', '作为全球最重要的商业传粉者之一，为大量粮食与经济作物授粉；蜂群健康也被视为农药污染与生态压力的敏感指示器。', '摆尾舞不只是「跳个舞」——角度对应太阳方位、时长对应蜜源距离，是已知非人类动物里少有的、能编码抽象空间信息的符号通讯系统。', 'complete', '["卵","幼虫","蛹","成虫（工蜂/蜂后/雄蜂）"]', '原产欧洲、非洲及中东，现随人工饲养遍布除南极洲外的各大洲', '人工饲养广泛，但野外种群受农药、寄生螨及栖息地丧失威胁', '["东方蜜蜂","大蜜蜂","熊蜂"]', '#c2941f', 2
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'honeybee');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'dragonfly', '碧伟蜓', 'Anax parthenope', '水陆两栖的空中猎手', 'odonata', '碧伟蜓是常见于池塘湖泊边的大型蜻蜓，体色以青绿与褐色为主，飞行敏捷且能悬停、瞬间转向。稚虫阶段生活在水中长达数月至一年，是水域中活跃的伏击型捕食者，羽化后转为空中猎手。', '[{"key":"体长","value":"约65–75毫米，翼展可达90–100毫米","icon":"size"},{"key":"生活史","value":"稚虫（水虿）水中生活数月至一年，成虫期约1–2个月","icon":"time"},{"key":"栖息环境","value":"池塘、湖泊、缓流水域及其周边草丛","icon":"place"},{"key":"食性","value":"稚虫捕食孑孓、小鱼虾，成虫在空中捕食蚊蝇等小型昆虫","icon":"food"},{"key":"飞行","value":"飞行时速可达30公里以上，两对翅可独立扇动实现悬停与急转","icon":"ability"},{"key":"视觉","value":"复眼由近3万个小眼拼成，视野接近360度","icon":"ability"}]', '[{"id":"dragonfly-forewing","label":"前翅","note":"翅脉网状纵横，可与后翅不同步扇动实现精细飞控","anchor":"forewing","tone":"lavender"},{"id":"dragonfly-hindwing","label":"后翅","note":"基部略宽于前翅，产生主要升力，助其长时间悬停","anchor":"hindwing","tone":"coral"},{"id":"dragonfly-eye","label":"复眼","note":"占头部大半，由近3万个小眼构成，视野接近360度","anchor":"eye","tone":"sage"},{"id":"dragonfly-abdomen","label":"腹部","note":"细长腹节末端具抱握器，交配时钳住雌虫头部","anchor":"abdomen","tone":"amber"},{"id":"dragonfly-thorax","label":"胸部","note":"倾斜生长，让四片翅与三对足的着生点错开便于扑食","anchor":"thorax","tone":"lavender"},{"id":"dragonfly-leg","label":"足","note":"密生刚毛，飞行时收拢成篮状用于兜住空中猎物","anchor":"leg","tone":"coral"}]', '成虫和稚虫都是所在水域及周边食物链中的重要捕食者，稚虫大量取食蚊虫幼体，也是鱼类和鸟类的重要食物来源。', '蜻蜓捕猎的空中拦截成功率可达约95%，高于狮子等大型猎食者；其神经元会预判猎物的飞行轨迹进行拦截，而不是单纯追逐目标。', 'incomplete', '["卵","稚虫（水虿，数月至一年）","成虫"]', '广泛分布于欧亚大陆及非洲北部的温带、亚热带水域', '常见', '["黄蜻","赤蜻","蓝额疏脉蜻"]', '#3f8a7a', 3
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'dragonfly');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'mantis', '中华大刀螳', 'Tenodera sinensis', '伪装成绿叶的伏击刀客', 'mantodea', '中华大刀螳是中国原产的大型螳螂，前足特化成带列刺的镰刀状捕捉足，行动隐蔽、多倚仗拟态静候猎物靠近。锁定目标后，它的前足突然合拢完成一击，整个过程快到人眼难以看清细节。', '[{"key":"体长","value":"雌虫可达90–105毫米，是本土常见螳螂中体型最大的类群之一","icon":"size"},{"key":"生活周期","value":"一年一代，若虫经6～9次蜕皮至成虫，成虫多于秋末死亡","icon":"time"},{"key":"栖息环境","value":"灌木丛、草地、农田边缘等能伏击猎物的植被环境","icon":"place"},{"key":"食性","value":"纯肉食性，捕食蝗虫、蝶蛾、蜜蜂等昆虫，偶捕小型脊椎动物","icon":"food"},{"key":"出击","value":"前足以极快速度合拢锁住猎物，出击瞬间几乎无法被肉眼分辨","icon":"ability"},{"key":"转头","value":"头部可转动近180度，是少数能「回头看」的昆虫之一","icon":"ability"}]', '[{"id":"mantis-raptorialLeg","label":"捕捉足","note":"内侧列生尖刺，合拢时能牢牢锁住猎物令其无法挣脱","anchor":"raptorialLeg","tone":"coral"},{"id":"mantis-head","label":"头部","note":"可转动近180度，几乎不用移动身体就能环顾四周","anchor":"head","tone":"sage"},{"id":"mantis-eye","label":"复眼","note":"眼中暗色的伪瞳孔看起来像会「跟随」视角转动，其实只是光学错觉","anchor":"eye","tone":"amber"},{"id":"mantis-wing","label":"翅","note":"雄虫翅长过腹部利于飞行求偶，雌虫翅相对较短","anchor":"wing","tone":"lavender"},{"id":"mantis-abdomen","label":"腹部","note":"内含卵巢，产卵前会明显膨大变粗","anchor":"abdomen","tone":"coral"},{"id":"mantis-prothorax","label":"前胸","note":"细长可伸缩，让捕捉足能突然弹向猎物难以预判的距离","anchor":"prothorax","tone":"sage"}]', '作为伏击型捕食者，中华大刀螳压制农林害虫种群，但也不加区分地捕食传粉昆虫；在被引入的北美等地，对本地物种构成一定生态压力。', '中华大刀螳1896年作为害虫天敌引入北美后迅速扩散；北美已有多起野外记录显示，这种螳螂偶尔会伏击并捕食体型远大于自己的蜂鸟。', 'incomplete', '["卵鞘（越冬）","若虫（6–9龄）","成虫"]', '原产中国及东亚，19世纪末引入北美后广泛归化', '原生地常见；北美为引入种，对本地昆虫群落有一定影响', '["广斧螳","薄翅螳螂","兰花螳螂"]', '#6b8f4e', 3
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'mantis');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'ladybird', '七星瓢虫', 'Coccinella septempunctata', '红衣示警的蚜虫猎手', 'coleoptera', '七星瓢虫是红底黑斑的小型甲虫，幼虫和成虫都以蚜虫为主食，是农田与果园里活跃的天然控害者。它靠鲜艳的体色警告天敌自己滋味不佳，受惊时还会分泌苦味体液自卫。', '[{"key":"体长","value":"约5–8毫米，是常见瓢虫中体型中等偏小的种类","icon":"size"},{"key":"世代","value":"温暖地区一年可发生多代，成虫常成群越冬","icon":"time"},{"key":"栖息环境","value":"农田、草地及林缘，蚜虫密集处最常见","icon":"place"},{"key":"食性","value":"幼虫和成虫都以蚜虫为主食，兼食蚧壳虫、粉虱等","icon":"food"},{"key":"捕食量","value":"幼虫发育期间可捕食数百只蚜虫，是重要的生物防治天敌","icon":"ability"},{"key":"防御","value":"受惊扰时腿关节会分泌黄色苦味血淋巴，兼具警戒与化学防御作用","icon":"ability"}]', '[{"id":"ladybird-elytra","label":"鞘翅","note":"红底黑斑的坚硬前翅，鲜艳配色是对天敌的警戒信号","anchor":"elytra","tone":"sage"},{"id":"ladybird-spot","label":"黑斑","note":"斑点数量与排列因个体略有差异，可用于识别种类","anchor":"spot","tone":"amber"},{"id":"ladybird-head","label":"头部","note":"藏于前胸背板下方，受惊时可整个缩入获得保护","anchor":"head","tone":"lavender"},{"id":"ladybird-leg","label":"足","note":"关节处能分泌苦味血淋巴，是主要的化学防御手段","anchor":"leg","tone":"coral"},{"id":"ladybird-antenna","label":"触角","note":"短棒状，用于近距离探测蚜虫分泌蜜露的气味","anchor":"antenna","tone":"sage"},{"id":"ladybird-pronotum","label":"前胸背板","note":"黑白相间的斑纹，是区分不同瓢虫种类的重要标志","anchor":"pronotum","tone":"amber"}]', '作为蚜虫的主要天敌之一，七星瓢虫在农田与果园生态系统中发挥重要的自然控害作用，长期被作为生物防治资源人工繁殖释放。', '七星瓢虫受到惊扰时会从腿关节渗出黄色血淋巴，气味苦涩，这才是它鲜艳配色真正对应的防御手段，而不只是好看。', 'complete', '["卵","幼虫（四龄）","蛹","成虫"]', '原产欧亚大陆及北非，20世纪引入北美用于生物防治并已归化', '常见，广泛用作农业生物防治资源', '["异色瓢虫","龟纹瓢虫","红点唇瓢虫"]', '#b3453c', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'ladybird');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'ant', '日本弓背蚁', 'Camponotus japonicus', '地下城邦的终身搬运工', 'hymenoptera', '日本弓背蚁是东亚常见的大型蚂蚁，工蚁分化出体型不同、分工各异的类型，靠触角接触与信息素协调分工。蚁群在地下或朽木中营建分室巢穴，蚁后深居巢穴中心，终生专事产卵。', '[{"key":"体长","value":"工蚁7–13毫米（大小工蚁分化），蚁后可达约17毫米","icon":"size"},{"key":"负重","value":"可搬运相当于自身体重数十倍的食物或巢材","icon":"weight"},{"key":"寿命","value":"蚁后寿命可达十余年，工蚁通常只能活几个月到两年","icon":"time"},{"key":"栖息环境","value":"营巢于土壤、石块下或朽木内部，形成分室的地下巢穴","icon":"place"},{"key":"食性","value":"杂食性，捕食小型昆虫、取食植物汁液，并「放牧」蚜虫采蜜露","icon":"food"},{"key":"协作","value":"靠触角敲击与信息素交流分工，集体行动效率远超单只个体","icon":"ability"}]', '[{"id":"ant-mandible","label":"上颚","note":"大型工蚁的上颚粗壮有力，用于切割食物和防御巢穴","anchor":"mandible","tone":"amber"},{"id":"ant-petiole","label":"腹柄节","note":"连接胸部与腹部的细腰状结构，让腹部能灵活摆动","anchor":"petiole","tone":"lavender"},{"id":"ant-gaster","label":"后腹部","note":"内含嗉囊，可暂存蜜露带回巢穴与同伴分享","anchor":"gaster","tone":"coral"},{"id":"ant-antenna","label":"触角","note":"膝状触角用于识别同伴气味、传递信息素信号","anchor":"antenna","tone":"sage"},{"id":"ant-eye","label":"复眼","note":"视力有限，蚁群主要依赖气味而非视觉定位","anchor":"eye","tone":"amber"},{"id":"ant-leg","label":"足","note":"跗节末端的爪与黏垫，让蚂蚁能攀爬垂直光滑表面","anchor":"leg","tone":"lavender"}]', '作为杂食性搬运者与捕食者，弓背蚁参与土壤翻动与养分循环，同时通过取食蚜虫蜜露与蚜虫维系互利关系，也是许多鸟类的食物来源。', '弓背蚁蚁后一旦完成婚飞、脱翅建巢，此后终生不再离巢，寿命却可达十余年，是昆虫中最长寿的类群之一，工蚁子女往往活不过两年。', 'complete', '["卵","幼虫","蛹（多结茧）","成虫（工蚁/蚁后/雄蚁）"]', '中国、日本、朝鲜半岛及东亚周边地区', '常见', '["黑蚁","红火蚁","举腹蚁"]', '#5c4a3a', 2
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'ant');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'cicada', '黑蚱蝉', 'Cryptotympana atrata', '地下蛰伏多年的夏日歌手', 'hemiptera', '黑蚱蝉是中国夏季最常见的大型鸣蝉，体色黑亮，雄虫腹部的发音器能发出持续而响亮的鸣声。它的若虫在地下靠吸食树根汁液生活数年，出土后蜕壳羽化为成虫，地面上的寿命却只有数周。', '[{"key":"体长","value":"约44–48毫米，翼展可达约120毫米","icon":"size"},{"key":"生活史","value":"若虫在地下生活约3–5年，成虫羽化后仅存活数周","icon":"time"},{"key":"栖息环境","value":"若虫栖于树根周围土壤，成虫栖息于树干及枝叶间","icon":"place"},{"key":"食性","value":"若虫与成虫都以刺吸式口器吸食树木木质部汁液为食","icon":"food"},{"key":"鸣声","value":"雄虫腹部发音器鸣叫响亮，近距离可测得90分贝以上","icon":"ability"},{"key":"羽化","value":"若虫出土后攀上树干完成最后一次蜕皮，羽化为有翅成虫","icon":"ability"}]', '[{"id":"cicada-tymbal","label":"鸣器","note":"腹部两侧的褶状发声膜，雄虫借快速振动发出响亮鸣声","anchor":"tymbal","tone":"lavender"},{"id":"cicada-wing","label":"翅","note":"透明膜质，翅脉清晰，停栖时呈屋脊状覆盖背部","anchor":"wing","tone":"coral"},{"id":"cicada-eye","label":"复眼","note":"两侧突出的大复眼间还有三个单眼，合力感知光线变化","anchor":"eye","tone":"sage"},{"id":"cicada-rostrum","label":"刺吸式口器","note":"细长如针插入木质部导管，吸取汁液为食","anchor":"rostrum","tone":"amber"},{"id":"cicada-head","label":"头部","note":"宽阔以容纳发达复眼与口器基部的肌肉群","anchor":"head","tone":"lavender"},{"id":"cicada-abdomen","label":"腹部","note":"内部近乎中空如共鸣箱，能放大鸣器发出的声音","anchor":"abdomen","tone":"coral"}]', '黑蚱蝉若虫多年生活在地下取食树根汁液，一定程度影响树木长势；成虫产卵会造成嫩枝干枯，其若虫和蜕壳也是许多鸟类与土壤生物的资源。', '一提蝉很多人会想到北美「十七年蝉」那样多年一遇的集中爆发，但黑蚱蝉并不是周期蝉——若虫的地下年限并不整齐同步，因此几乎每年夏天都能听到它的鸣叫。', 'incomplete', '["卵","若虫（地下，3–5年）","成虫"]', '中国大部分地区、朝鲜半岛及日本', '常见，偶因产卵损伤枝条被视为轻度林木害虫', '["蟪蛄","蒙古寒蝉","螗蝉"]', '#454f3d', 2
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'cicada');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'locust', '东亚飞蝗', 'Locusta migratoria', '拥挤到变身的旅行者', 'orthoptera', '东亚飞蝗是分布最广的蝗虫之一，同一物种会因种群密度不同呈现散居型与群居型两种截然不同的外观与习性。密度较低时它们独居、体色暗淡；一旦种群拥挤，便会转变为体色鲜艳、结群迁飞的类型。', '[{"key":"体长","value":"约35–55毫米，群居型与散居型在体形比例上略有差异","icon":"size"},{"key":"世代","value":"卵在土壤中越冬，一年可发生2～4代（因地区而异）","icon":"time"},{"key":"栖息环境","value":"草地、农田及河滩等开阔植被环境","icon":"place"},{"key":"食性","value":"植食性，取食禾本科牧草及小麦、玉米、水稻等作物","icon":"food"},{"key":"跳跃","value":"后足腿节发达如弹簧，跳跃距离可达体长十余倍","icon":"ability"},{"key":"迁飞","value":"群居型个体擅长持续飞行，蝗群可迁移数百公里造成蝗灾","icon":"ability"}]', '[{"id":"locust-hindleg","label":"后足","note":"腿节粗壮如压紧的弹簧，蹬地一跃可达体长十余倍","anchor":"hindleg","tone":"coral"},{"id":"locust-wing","label":"翅","note":"群居型翅长超过体长，能支撑数百公里的持续飞行","anchor":"wing","tone":"sage"},{"id":"locust-tympanum","label":"鼓膜听器","note":"位于腹部第一节两侧，用于接收同类翅膀摩擦发出的声音","anchor":"tympanum","tone":"amber"},{"id":"locust-eye","label":"复眼","note":"大而突出，对移动的同类个体和天敌都很敏感","anchor":"eye","tone":"lavender"},{"id":"locust-antenna","label":"触角","note":"丝状，反复接触同伴会促使体内神经递质变化","anchor":"antenna","tone":"coral"},{"id":"locust-pronotum","label":"前胸背板","note":"群居型与散居型的背板形状有细微差异，可用于区分两型","anchor":"pronotum","tone":"sage"}]', '东亚飞蝗在低密度时是草地生态系统里普通的植食性成员；一旦种群暴增转为群居型，则会大量取食农作物，是历史上造成蝗灾的主要物种之一。', '东亚飞蝗从温顺的散居型变成结群成灾的群居型，并非因为基因不同，而是拥挤本身——后足反复被同伴触碰会促使体内血清素水平上升，进而改变体色与行为。', 'incomplete', '["卵（土壤中越冬）","若虫（蝻，5–6龄）","成虫"]', '中国大部分地区，广布于欧亚大陆及非洲的温带、热带草原', '常见；种群密度骤增时可暴发区域性蝗灾，为重要防控对象', '["中华蚱蜢","笨蝗","稻蝗"]', '#94a13f', 3
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'locust');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'firefly', '山窗萤', 'Pyrocoelia pectoralis', '会打灯语的黑夜信使', 'coleoptera', '山窗萤是中国大陆广布的陆生萤火虫，成虫腹部末端的发光器能发出规律闪烁的冷光用于求偶。它的幼虫同样能发光，白天蛰伏在落叶与苔藓间，夜晚出没于潮湿林地捕食蜗牛与蛞蝓。', '[{"key":"体长","value":"雄虫约15–18毫米；雌虫鞘翅退化、无后翅，体似幼虫不能飞","icon":"size"},{"key":"生活史","value":"幼虫期可长达1年以上，成虫期通常只有1～2周","icon":"time"},{"key":"栖息环境","value":"潮湿的林地、草丛及溪流附近的落叶层","icon":"place"},{"key":"食性","value":"幼虫捕食蜗牛、蛞蝓等软体动物，成虫多不主动取食","icon":"food"},{"key":"发光","value":"腹部末端发光器几乎把化学能全部转化为光，本身不发热","icon":"ability"},{"key":"求偶","value":"以特定节奏的闪光信号求偶，不同种类的「灯语」节奏各不相同","icon":"ability"}]', '[{"id":"firefly-lantern","label":"发光器","note":"腹部末端的特化器官，靠荧光素酶催化反应发出几乎不产热的冷光","anchor":"lantern","tone":"sage"},{"id":"firefly-elytra","label":"鞘翅","note":"质地较软，颜色暗淡便于夜间隐蔽于草丛落叶间","anchor":"elytra","tone":"amber"},{"id":"firefly-eye","label":"复眼","note":"雄虫复眼相对发达，便于在黑暗中搜寻雌虫的闪光信号","anchor":"eye","tone":"lavender"},{"id":"firefly-antenna","label":"触角","note":"锯齿状或丝状，也能感知信息素辅助寻找配偶","anchor":"antenna","tone":"coral"},{"id":"firefly-thorax","label":"前胸背板","note":"扩大呈盾状延伸盖住头部，是萤科常见的识别特征","anchor":"thorax","tone":"sage"},{"id":"firefly-leg","label":"足","note":"行动缓慢，主要依靠爬行在低矮植被间移动","anchor":"leg","tone":"amber"}]', '幼虫是潮湿林地里蜗牛与蛞蝓的重要天敌，对软体动物种群有一定控制作用；成虫的发光求偶行为也让萤火虫成为反映夜间光污染程度的指示物种。', '萤火虫发光器把化学能转化成光的效率接近九成，几乎不产生热量，这种「冷光」的发光效率远超普通白炽灯（约一成），是已知最高效的生物发光系统之一。', 'complete', '["卵","幼虫（可发光，越冬）","蛹","成虫（1–2周）"]', '中国温带及亚热带山林、溪流附近的潮湿地带', '受光污染、栖息地丧失和农药使用威胁，近年种群明显减少', '["窗萤","熠萤","雷氏萤"]', '#a68a2f', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'firefly');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'longhorn-beetle', '星天牛', 'Anoplophora chinensis', '把树干当摇篮的长须客', 'coleoptera', '星天牛是身披黑底白斑鞘翅、触角长达体长两倍以上的大型天牛，因幼虫蛀食活树树干和根部而成为柑橘、杨柳等树木的重要蛀干害虫。雌虫在树皮上咬出产卵刻槽，孵化的幼虫钻入木质部取食一至两年后化蛹。', '[{"key":"体长","value":"约20–39毫米，雄虫触角可达体长的近两倍","icon":"size"},{"key":"生活史","value":"幼虫在树干内蛀食1～2年，成虫寿命约1个月左右","icon":"time"},{"key":"栖息环境","value":"阔叶林、果园及城市行道树，幼虫蛀居于活树木质部","icon":"place"},{"key":"食性","value":"幼虫蛀食树干与根部木质部，成虫啃食嫩枝树皮补充营养","icon":"food"},{"key":"危害","value":"幼虫在树干内部蛀出隧道，可致树势衰弱甚至整株死亡","icon":"ability"},{"key":"感知","value":"长鞭状触角遍布感受器，能感知气味与振动寻找寄主和配偶","icon":"ability"}]', '[{"id":"longhorn-beetle-antenna","label":"触角","note":"黑白相间成节的长鞭状触角，长度可达体长近两倍","anchor":"antenna","tone":"amber"},{"id":"longhorn-beetle-elytra","label":"鞘翅","note":"黑底缀有不规则白斑，斑纹排列因个体略有差异","anchor":"elytra","tone":"lavender"},{"id":"longhorn-beetle-mandible","label":"上颚","note":"粗壮有力，成虫啃食树皮补充营养，雌虫也用它咬开产卵刻槽","anchor":"mandible","tone":"coral"},{"id":"longhorn-beetle-eye","label":"复眼","note":"呈肾形环绕在触角基部，为触角活动让出空间","anchor":"eye","tone":"sage"},{"id":"longhorn-beetle-pronotum","label":"前胸背板","note":"两侧各具一枚尖锐侧刺，是天牛科常见的防御结构","anchor":"pronotum","tone":"amber"},{"id":"longhorn-beetle-leg","label":"足","note":"跗节具浓密黏毛，便于攀附光滑的树皮表面","anchor":"leg","tone":"lavender"}]', '星天牛幼虫蛀食活树木质部，是柑橘、杨树、柳树等经济与行道树种的重要蛀干害虫，长期为害可致树木风折甚至死亡，在许多国家被列为检疫性有害生物。', '星天牛原产东亚，但常随苗木和木质包装材料被无意间携带到欧美，近二十年已在意大利、法国等地建立起野外种群，靠人类贸易完成了跨大陆的「移民」。', 'complete', '["卵（产于树皮刻槽内）","幼虫（蛀干，1–2年）","蛹","成虫"]', '原产中国、朝鲜半岛及越南，已入侵欧洲多国并在北美多次被截获', '重要检疫性害虫，原生地常见，入侵地受官方防控监测', '["光肩星天牛","桑天牛","云斑天牛"]', '#45505f', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'longhorn-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'stick-insect', '棒䗛', 'Ramulus sp.', '把自己活成一根树枝', 'phasmatodea', '棒䗛是拟态成细枝的植食性昆虫，中国有八十余个特有种，体态修长，静止时几乎与栖息的植物枝条融为一体。受到惊扰时，它常靠轻微摇晃身体模仿风中摆动的枝叶，以此蒙混天敌的注意。', '[{"key":"体长","value":"因种类而异，多数在8～16厘米，属昆虫中最修长的类群之一","icon":"size"},{"key":"生活史","value":"卵期可长达数月甚至更久，若虫经多次蜕皮逐渐长成成虫","icon":"time"},{"key":"栖息环境","value":"灌木、竹丛及阔叶林间植被茂密处","icon":"place"},{"key":"食性","value":"植食性，夜间取食寄主植物的叶片","icon":"food"},{"key":"拟态","value":"静止时拟态成枯枝绿茎，受惊时以身体摇晃模拟风吹枝条","icon":"ability"},{"key":"断肢再生","value":"足被天敌钳住可自断逃生，幼体经数次蜕皮能部分再生断肢","icon":"ability"}]', '[{"id":"stick-insect-body","label":"体躯","note":"细长呈棍棒状，体节和体色高度模拟寄主植物的枝干","anchor":"body","tone":"lavender"},{"id":"stick-insect-leg","label":"足","note":"细长如枝条的分支，被天敌钳住时可自行断离逃生","anchor":"leg","tone":"coral"},{"id":"stick-insect-antenna","label":"触角","note":"丝状，长度因种类而异，用于探路和感知气味","anchor":"antenna","tone":"sage"},{"id":"stick-insect-head","label":"头部","note":"小而不显眼，复眼不发达，主要依靠触觉与嗅觉感知环境","anchor":"head","tone":"amber"},{"id":"stick-insect-thorax","label":"胸部","note":"延长的胸节撑起细长躯干，也是三对步足的着生处","anchor":"thorax","tone":"lavender"},{"id":"stick-insect-camouflage","label":"拟态特征","note":"体色和体表纹理随蜕皮变化，贴近所栖息植物的颜色","anchor":"camouflage","tone":"coral"}]', '棒䗛是林下植食性昆虫，取食量通常有限，对植被影响不大；其高度特化的拟态和断肢再生策略是应对天敌捕食压力演化而来，本身也是鸟类等捕食者的搜寻目标。', '不少竹节虫产下的卵在外形和气味上模拟植物种子，还带着蚂蚁喜欢的营养附属物，常被蚂蚁误当种子搬进蚁巢保护起来，若虫孵化后才从蚁巢中爬出。', 'incomplete', '["卵","若虫（多次蜕皮）","成虫"]', '中国南方及中部山地丘陵的灌丛与林地', '常见但因拟态极隐蔽而不易被发现，未见明显受威胁证据', '["中华大竹节虫","叶䗛","越南巨竹节虫"]', '#7c6a45', 4
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'stick-insect');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'swallowtail', '玉带凤蝶', 'Papilio polytes', '把毒蝶画在自己翅上', 'lepidoptera', '玉带凤蝶雄虫黑翅缀一道显眼白带，雌虫却常演化出多种拟态型，其中一型翅纹几乎复刻剧毒的红珠凤蝶。这种雌雄两态、雌性拟态他种的现象，是研究昆虫拟态与遗传机制的经典案例。', '[{"key":"体长","value":"翼展约90–100毫米，雌雄翅色差异明显","icon":"size"},{"key":"世代","value":"卵至成虫约需一个月，温暖地区一年可发生3代以上","icon":"time"},{"key":"栖息环境","value":"城市庭园、柑橘果园及低海拔次生林","icon":"place"},{"key":"食性","value":"幼虫取食柑橘、花椒等芸香科植物叶片，成虫访花吸食花蜜","icon":"food"},{"key":"拟态","value":"部分雌虫翅纹拟态剧毒的红珠凤蝶，属经典的贝氏拟态","icon":"ability"},{"key":"假头","value":"后翅尾突与斑纹组成假触角，把攻击引向翅缘而非头部","icon":"ability"}]', '[{"id":"swallowtail-forewing","label":"前翅","note":"黑底斑纹，是雌雄识别与个体差异的第一显著特征","anchor":"forewing","tone":"coral"},{"id":"swallowtail-hindwing","label":"后翅","note":"横贯一道玉带状白斑，雄虫此斑纹稳定不随个体变化","anchor":"hindwing","tone":"sage"},{"id":"swallowtail-tail","label":"尾突","note":"延伸如假触角，把天敌视线引向翅缘而非头部","anchor":"tail","tone":"amber"},{"id":"swallowtail-antenna","label":"触角","note":"棒状末端膨大，主要感知气味辨识寄主植物","anchor":"antenna","tone":"lavender"},{"id":"swallowtail-eye","label":"复眼","note":"硕大，飞行中辅助识别同类翅纹与花朵位置","anchor":"eye","tone":"coral"},{"id":"swallowtail-abdomen","label":"腹部","note":"雌虫末端具外生殖器官，产卵前紧贴叶背探测","anchor":"abdomen","tone":"sage"}]', '作为柑橘等芸香科植物常见的访花与取食昆虫，玉带凤蝶幼虫是果园中较易见的取食者；雌性拟态毒蝶的现象，也是观察物种间拟态关系的常见样本。', '凤蝶尾突常被鸟类误当作头部啄咬，这种「假头」策略能让它损失一角翅膀后照常飞行逃生，代价远小于真被啄中头部。', 'complete', '["卵","幼虫（五龄）","蛹","成虫"]', '中国南方、印度至东南亚、日本南部', '常见，城市及农业区适应力强', '["红珠凤蝶","柑橘凤蝶","美凤蝶"]', '#8a3f4a', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'swallowtail');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'silk-moth', '柞蚕蛾', 'Antheraea pernyi', '两千年丝绸背后的沉默者', 'lepidoptera', '柞蚕蛾是体形硕大的赤褐色蚕蛾，前后翅各有一枚透明眼斑，是中国传统柞蚕丝绸产业的核心昆虫。成虫羽化后口器已经退化，终生不再取食，全部气力都用于交配和产卵。', '[{"key":"体长","value":"翼展约120–150毫米，雌蛾大于雄蛾","icon":"size"},{"key":"寿命","value":"成虫口器退化不取食，寿命通常只有一周左右","icon":"time"},{"key":"栖息环境","value":"柞树（栎类）林地及人工柞园","icon":"place"},{"key":"食性","value":"幼虫专食柞树等壳斗科植物叶片，成虫不取食","icon":"food"},{"key":"吐丝","value":"老熟幼虫吐丝结茧，茧丝是柞蚕丝绸的原料","icon":"ability"},{"key":"觅偶","value":"雄蛾栉齿状触角对雌蛾性信息素极敏感，可远距离觅偶","icon":"ability"}]', '[{"id":"silk-moth-eyespot","label":"眼斑","note":"前后翅各具一枚透明眼斑，受惊时突然显露如睁眼","anchor":"eyespot","tone":"amber"},{"id":"silk-moth-antenna","label":"触角","note":"雄蛾呈宽大羽状，表面感受器密布用于搜寻雌蛾气味","anchor":"antenna","tone":"coral"},{"id":"silk-moth-forewing","label":"前翅","note":"赤褐色，前缘色深，停栖时紧贴身体形似枯叶","anchor":"forewing","tone":"sage"},{"id":"silk-moth-hindwing","label":"后翅","note":"色泽较浅，眼斑周围环绕一圈淡色晕纹","anchor":"hindwing","tone":"lavender"},{"id":"silk-moth-thorax","label":"胸部","note":"密生绒毛，飞行前需靠振翅摩擦生热预热肌肉","anchor":"thorax","tone":"amber"},{"id":"silk-moth-abdomen","label":"腹部","note":"雌蛾末端分泌性信息素，可招引数百米外的雄蛾","anchor":"abdomen","tone":"coral"}]', '柞蚕幼虫是柞树林中常见的植食者，也是中国传统柞蚕丝绸产业的核心物种，野生与放养种群长期与柞树林生态共存。', '柞蚕不像家蚕那样养在室内，而是成片放养在野外的柞树林里，靠人巡山看护；中国的柞蚕丝产量占到全世界的九成以上。', 'complete', '["卵","幼虫（五龄）","蛹（结茧）","成虫（约1周）"]', '中国东北、华北及山东柞蚕主产区，俄罗斯远东及朝鲜半岛', '半家养物种，中国传统柞蚕丝绸产业的主要蚕种', '["天蚕","樗蚕","绿尾大蚕蛾"]', '#9c6b52', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'silk-moth');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'hornet', '金环胡蜂', 'Vespa mandarinia', '蜂群头顶最沉的阴影', 'hymenoptera', '金环胡蜂是世界上体型最大的胡蜂，上颚粗壮如镰刀，猎食时能迅速咬断猎物头胸部。数十只工蜂集群突袭一处蜂巢，可在数小时内将整窝蜜蜂屠戮殆尽，是蜜蜂最致命的天敌之一。', '[{"key":"体长","value":"蜂后可达45–50毫米，是世界体型最大的胡蜂","icon":"size"},{"key":"世代","value":"蜂群一年一季，蜂后独立越冬后于春季建群","icon":"time"},{"key":"栖息环境","value":"低山丘陵林地，常在地下或树洞筑巢","icon":"place"},{"key":"食性","value":"成虫捕食其他蜂类和大型昆虫，也取食树液与熟果","icon":"food"},{"key":"猎杀","value":"数十只工蜂集群可在数小时内摧毁一整窝蜜蜂群","icon":"ability"},{"key":"蜇刺","value":"螫针长且可反复蜇刺，注入毒液量远超普通胡蜂","icon":"ability"}]', '[{"id":"hornet-mandible","label":"上颚","note":"粗大呈镰刀状，猎食蜜蜂时用于快速咬断头胸部","anchor":"mandible","tone":"coral"},{"id":"hornet-sting","label":"螫针","note":"光滑不带倒钩，可反复蜇刺且注入毒液量大","anchor":"sting","tone":"amber"},{"id":"hornet-wing","label":"翅膀","note":"振动频率高，飞行时速可达约40公里追击猎物","anchor":"wing","tone":"sage"},{"id":"hornet-eye","label":"复眼","note":"配合单眼，能在林间弱光下辨认猎物动向","anchor":"eye","tone":"lavender"},{"id":"hornet-antenna","label":"触角","note":"密布嗅觉感受器，用于定位蜂巢和猎物气味","anchor":"antenna","tone":"coral"},{"id":"hornet-waist","label":"细腰","note":"腹柄连接胸腹，转向灵活便于空中缠斗","anchor":"waist","tone":"sage"}]', '金环胡蜂是森林中体型最大的捕食性昆虫之一，集群猎杀蜜蜂等社会性蜂类会显著削弱蜂群数量，对养蜂业构成现实威胁。', '面对金环胡蜂突袭，日本蜜蜂会集体扑上入侵者并剧烈振翅，用蜂团中心逼近45摄氏度的高温将它活活热死。', 'complete', '["卵","幼虫","蛹","成虫（蜂后/工蜂/雄蜂）"]', '东亚温带至亚热带地区，包括中国、日本、朝鲜半岛及印度北部；已入侵北美太平洋西北地区', '原生地常见，2019年起在北美被列为入侵物种并遭重点扑杀', '["墨胸胡蜂","黑尾胡蜂","大胡蜂"]', '#8f4a1f', 2
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'hornet');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'tiger-beetle', '中华虎甲', 'Cicindela chinensis', '林间小径上的短跑冠军', 'coleoptera', '中华虎甲是泛着金绿色金属光泽的小型甲虫，成虫和幼虫都是活跃的肉食者。它奔跑速度极快，常在裸露的沙土小径上疾冲几步又骤然停下，等复眼重新捕捉清晰影像后再度出击。', '[{"key":"体长","value":"约20–22毫米，鞘翅具金属光泽","icon":"size"},{"key":"生活史","value":"成虫多见于春末至秋季，幼虫期长达一至两年","icon":"time"},{"key":"栖息环境","value":"向阳的林间小径、河滩及裸露沙土地","icon":"place"},{"key":"食性","value":"成虫和幼虫均为肉食性，捕食蚂蚁等小型节肢动物","icon":"food"},{"key":"奔跑","value":"奔跑速度极快，相对体长的移动速度居昆虫前列","icon":"ability"},{"key":"伏击","value":"幼虫蛰伏垂直洞穴，以腹部倒钩固定伏击路过猎物","icon":"ability"}]', '[{"id":"tiger-beetle-mandible","label":"上颚","note":"镰刀状交叉如剪，捕猎时瞬间夹住猎物","anchor":"mandible","tone":"coral"},{"id":"tiger-beetle-elytra","label":"鞘翅","note":"泛金绿色金属光泽，缀有醒目的白色斑纹","anchor":"elytra","tone":"sage"},{"id":"tiger-beetle-eye","label":"复眼","note":"硕大突出，视觉是捕猎时锁定猎物的主要依据","anchor":"eye","tone":"amber"},{"id":"tiger-beetle-leg","label":"步足","note":"细长，支撑其成为奔跑最快的昆虫类群之一","anchor":"leg","tone":"lavender"},{"id":"tiger-beetle-antenna","label":"触角","note":"丝状贴近上颚基部，辅助近距离感知猎物","anchor":"antenna","tone":"coral"},{"id":"tiger-beetle-pronotum","label":"前胸背板","note":"窄于鞘翅基部，泛红铜色金属光泽","anchor":"pronotum","tone":"sage"}]', '中华虎甲成虫和幼虫都是活跃的肉食性天敌，在林间步道和裸地生境中控制蚂蚁等小型节肢动物的数量，也是环境质量较好的指示物种。', '虎甲奔跑太快时复眼来不及处理连续影像，会短暂陷入「视觉失明」，因此常常冲刺几步就停下来重新定位猎物方位。', 'complete', '["卵","幼虫（穴居，1–2年）","蛹","成虫"]', '中国大部分地区、朝鲜半岛及日本', '常见', '["多型虎甲","云纹虎甲","台湾虎甲"]', '#2f8a6e', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'tiger-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'stag-beetle', '中华大锹甲', 'Dorcus hopei', '为一口树液较劲的角力士', 'coleoptera', '中华大锹甲雄虫头顶一对粗壮大颚，是同类间格斗争夺树液和配偶的武器。大颚长度深受幼虫期营养影响，同一窝兄弟羽化后颚型可能相差悬殊，个体命运也因此分出高下。', '[{"key":"体长","value":"雄虫约30–75毫米（含大颚），雌虫较小且大颚不发达","icon":"size"},{"key":"生活史","value":"幼虫在朽木中发育1–3年，成虫可存活数月以上","icon":"time"},{"key":"栖息环境","value":"阔叶林朽木及树洞周边，夜间常见于树干伤流处","icon":"place"},{"key":"食性","value":"幼虫蛀食腐朽木质纤维，成虫舔食树干渗出的树液","icon":"food"},{"key":"格斗","value":"雄虫以大颚夹持翻转对手，争夺树液取食点和配偶","icon":"ability"},{"key":"颚型","value":"大颚长度受幼虫期营养影响，个体差异悬殊","icon":"ability"}]', '[{"id":"stag-beetle-mandible","label":"大颚","note":"内侧具齿突，格斗时用于夹持并掀翻对手","anchor":"mandible","tone":"coral"},{"id":"stag-beetle-elytra","label":"鞘翅","note":"漆黑光滑坚硬，覆盖着折叠收纳的膜质后翅","anchor":"elytra","tone":"sage"},{"id":"stag-beetle-head","label":"头部","note":"宽扁，两侧肌肉发达为大颚提供强大咬合力","anchor":"head","tone":"amber"},{"id":"stag-beetle-antenna","label":"触角","note":"鳃叶状末端可开合，用于探测树液气味","anchor":"antenna","tone":"lavender"},{"id":"stag-beetle-leg","label":"足部","note":"跗节具锐爪，攀附树干时能承受打斗中的拉力","anchor":"leg","tone":"coral"},{"id":"stag-beetle-pronotum","label":"前胸背板","note":"边缘略隆起，与头部共同构成防御前盾","anchor":"pronotum","tone":"sage"}]', '中华大锹甲幼虫在朽木中取食分解木质纤维，是森林枯木循环的参与者；成虫聚集于树液伤口，与其他锹甲、独角仙共享有限的取食资源。', '同一窝锹甲幼虫若营养差异悬殊，羽化后雄虫大颚长度可能相差一倍以上，颚小的个体往往改走偷袭战术而非正面格斗。', 'complete', '["卵","幼虫（1–3年）","蛹","成虫"]', '中国大部分地区、朝鲜半岛及日本', '常见，部分大型个体因宠物贸易被人工繁育', '["中华鹿角锹甲","两点锯锹甲","安达锹甲"]', '#3a2e28', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'stag-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'jewel-beetle', '日本吉丁', 'Chrysochroa fulgidissima', '千年不褪色的虹彩铠甲', 'coleoptera', '日本吉丁鞘翅泛着金绿泛红的结构色光泽，色彩不是色素而是多层薄膜干涉光线所致。它的幼虫蛀食衰弱树木或枯木，成虫取食寄主叶片，鲜艳的鞘翅历史上还被用作工艺镶嵌材料。', '[{"key":"体长","value":"约30–41毫米，是常见吉丁虫中体型较大的种类","icon":"size"},{"key":"生活史","value":"幼虫蛀木发育需2–3年，成虫活动约一两个月","icon":"time"},{"key":"栖息环境","value":"低山阔叶林，常见于朴树、樟树等寄主树冠附近","icon":"place"},{"key":"食性","value":"幼虫蛀食衰弱树木或枯木木质部，成虫取食寄主叶片","icon":"food"},{"key":"结构色","value":"鞘翅色彩由多层微观薄膜干涉产生，角度不同色泽各异","icon":"ability"},{"key":"耐久","value":"色泽历经百年不易褪色，曾用作传统工艺镶嵌材料","icon":"ability"}]', '[{"id":"jewel-beetle-elytra","label":"鞘翅","note":"金属绿泛红光泽，色彩随观察角度而变化","anchor":"elytra","tone":"sage"},{"id":"jewel-beetle-stripe","label":"纵纹","note":"两道红紫色条纹贯穿鞘翅，结构色最集中的区域","anchor":"stripe","tone":"coral"},{"id":"jewel-beetle-eye","label":"复眼","note":"较小，主要依赖视觉锁定寄主树冠位置","anchor":"eye","tone":"amber"},{"id":"jewel-beetle-antenna","label":"触角","note":"锯齿状触角短小，平时贴伏于头部两侧不显眼","anchor":"antenna","tone":"lavender"},{"id":"jewel-beetle-pronotum","label":"前胸背板","note":"同具金属光泽，与鞘翅色泽相互呼应","anchor":"pronotum","tone":"sage"},{"id":"jewel-beetle-leg","label":"足部","note":"细长具金属光泽，便于在树皮和叶片间攀爬","anchor":"leg","tone":"coral"}]', '日本吉丁幼虫蛀食衰弱木或枯木，是森林朽木分解链条的一环；成虫鲜艳的结构色鞘翅，历史上还被用作寺庙工艺品的镶嵌材料。', '吉丁虫鞘翅的色彩不是色素而是薄膜干涉光线产生的结构色，标本历经百年仍不褪色，日本法隆寺古物「玉虫厨子」镶嵌的鞘翅至今仍见虹彩。', 'complete', '["卵","幼虫（蛀木，2–3年）","蛹","成虫（1–2个月）"]', '日本本州、四国、九州及朝鲜半岛、中国部分地区', '常见但因朽木栖息地减少局部数量下降', '["苹果吉丁","松吉丁","花梨吉丁"]', '#6e4a68', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'jewel-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'katydid', '优雅蝈螽', 'Gampsocleis gratiosa', '前足听雷、长须辨路的歌者', 'orthoptera', '优雅蝈螽体态粗壮，雄虫以左右前翅摩擦发出洪亮鸣声求偶占域，是中国民间笼养鸣虫文化的主角。它的「耳朵」开在前足胫节上，触角也远长于体长，是与蝗虫最好的区分点。', '[{"key":"体长","value":"约30–40毫米，绿色或褐色型均常见","icon":"size"},{"key":"世代","value":"一年一代，卵在土中越冬，成虫多见于夏秋","icon":"time"},{"key":"栖息环境","value":"农田、草地及灌丛，常栖于豆科、禾本科植物间","icon":"place"},{"key":"食性","value":"杂食性，取食植物嫩叶花果，也捕食蚜虫等小虫","icon":"food"},{"key":"鸣叫","value":"雄虫以左右前翅摩擦发声，用于求偶和占域","icon":"ability"},{"key":"辨识","value":"触角远长于体长，是区分蝈螽与蝗虫最直观的特征","icon":"ability"}]', '[{"id":"katydid-antenna","label":"触角","note":"丝状远长于体长，蝗虫触角短粗与之相反","anchor":"antenna","tone":"lavender"},{"id":"katydid-wing","label":"前翅","note":"硬化具发音锉，左右摩擦振动发声","anchor":"wing","tone":"coral"},{"id":"katydid-hindleg","label":"后足","note":"腿节发达，跳跃能力强于其飞行能力","anchor":"hindleg","tone":"sage"},{"id":"katydid-ovipositor","label":"产卵器","note":"形如军刀，用于把卵产入土壤深处越冬","anchor":"ovipositor","tone":"amber"},{"id":"katydid-eye","label":"复眼","note":"位于头部两侧，视觉不及触角和听器灵敏","anchor":"eye","tone":"coral"},{"id":"katydid-tympanum","label":"听器","note":"开口在前足胫节上而非腹部，本类群的关键特征","anchor":"tympanum","tone":"sage"}]', '优雅蝈螽是农田及草地生态系统中的杂食性成员，兼食植物与小型昆虫，也是螳螂、鸟类等天敌的捕食对象；其鸣声也是中国民间玩虫文化的重要组成部分。', '蝈蝈的「耳朵」其实长在前足胫节上而不是腹部，蝗虫的听器才长在腹部两侧——这是辨别两类相似昆虫最可靠也最少人知道的部位差异。', 'incomplete', '["卵（土中越冬）","若虫（多次蜕皮）","成虫"]', '中国华北、华东及东北南部', '常见，长期作为鸣虫被人工笼养', '["纺织娘","暗褐蝈螽","日本条螽"]', '#7a8a52', 3
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'katydid');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'mole-cricket', '东方蝼蛄', 'Gryllotalpa orientalis', '地道深处的挖掘工程师', 'orthoptera', '东方蝼蛄体表密被短绒毛，前足特化成宽扁如铲的挖掘足，专职破土而不擅跳跃。它常年在松软的农田土壤里凿穴取食植物根系与幼苗，是农人最熟悉的地下害虫之一。', '[{"key":"体长","value":"约30–35毫米，体表密被短绒毛","icon":"size"},{"key":"世代","value":"多为一至两年一代（因地区而异），若虫期长","icon":"time"},{"key":"栖息环境","value":"松软潮湿的农田、菜地及河岸土壤，掘穴栖居","icon":"place"},{"key":"食性","value":"杂食性，取食植物根茎幼苗，也捕食土中小型昆虫","icon":"food"},{"key":"挖掘","value":"前足特化成宽扁的挖掘足，掘土快但不擅跳跃","icon":"ability"},{"key":"扩散","value":"夜间偶尔借助后翅飞行扩散，趋光性强","icon":"ability"}]', '[{"id":"mole-cricket-foreleg","label":"前足","note":"宽扁如铲，胫节具齿突，专职挖掘而非跳跃","anchor":"foreleg","tone":"coral"},{"id":"mole-cricket-pronotum","label":"前胸背板","note":"膨大呈盾状，覆盖颈部利于在洞道中推进","anchor":"pronotum","tone":"sage"},{"id":"mole-cricket-wing","label":"后翅","note":"折叠收纳于短鞘翅下，仅夜间偶尔展开飞行","anchor":"wing","tone":"amber"},{"id":"mole-cricket-abdomen","label":"腹部","note":"柔软多毛，末端具尾须感知洞道内的震动","anchor":"abdomen","tone":"lavender"},{"id":"mole-cricket-eye","label":"复眼","note":"较小且退化，地下生活对视觉依赖有限","anchor":"eye","tone":"coral"},{"id":"mole-cricket-antenna","label":"触角","note":"丝状短于体长，与穴居生活的感知需求相适应","anchor":"antenna","tone":"sage"}]', '东方蝼蛄常年在土壤中掘穴取食植物根系和幼苗，是农田常见的地下害虫；其挖掘活动客观上也疏松了土壤、促进空气和水分渗透。', '蝼蛄前足是特化的挖掘足而非跳跃足，遇到危险时它靠钻入松土逃生而非跳跃，这一点常被误以为和蝗虫、蟋蟀一样擅长跳远。', 'incomplete', '["卵","若虫（多次蜕皮，穴居）","成虫"]', '中国大部分地区、东亚及东南亚', '常见，农田常见地下害虫之一', '["华北蝼蛄","台湾蝼蛄","非洲蝼蛄"]', '#6b5642', 3
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'mole-cricket');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'water-strider', '水黾', 'Aquarius elongatus', '踩着水面张力滑行的猎手', 'hemiptera', '水黾体形细长，中后足密生疏水微毛，借助水面张力得以站立并滑行取食，而非仅凭体重轻盈。它靠水面波纹定位落水昆虫，前足抓握、中后足划水，是水面上活跃的伏击型捕食者。', '[{"key":"体长","value":"约14–17毫米，体形细长呈流线型","icon":"size"},{"key":"世代","value":"温暖地区一年可发生多代，成虫可存活数月","icon":"time"},{"key":"栖息环境","value":"池塘、溪流及缓流水面","icon":"place"},{"key":"食性","value":"肉食性，捕食落水挣扎的昆虫，靠水面波纹定位猎物","icon":"food"},{"key":"站水","value":"中后足密生疏水微毛，配合表面张力使其立于水面不沉","icon":"ability"},{"key":"划行","value":"前足短小抓握猎物，中后足负责划水推进和转向","icon":"ability"}]', '[{"id":"water-strider-midleg","label":"中足","note":"最长，如船桨般划动，是主要的推进动力来源","anchor":"midleg","tone":"sage"},{"id":"water-strider-foreleg","label":"前足","note":"短而有力，专用于快速抓握落水的猎物","anchor":"foreleg","tone":"coral"},{"id":"water-strider-hindleg","label":"后足","note":"负责转向和刹车，与中足协同控制滑行方向","anchor":"hindleg","tone":"amber"},{"id":"water-strider-body","label":"体表","note":"覆蜡质疏水绒毛，短暂压入水面也不会被浸湿","anchor":"body","tone":"lavender"},{"id":"water-strider-eye","label":"复眼","note":"向两侧突出，便于侦测水面猎物的细微波纹","anchor":"eye","tone":"sage"},{"id":"water-strider-antenna","label":"触角","note":"细短，兼具触觉与感知水面振动的功能","anchor":"antenna","tone":"coral"}]', '水黾是水面食物链中活跃的捕食者，专门取食落水或羽化失败的昆虫，也是判断水体污染程度的敏感指示物种之一。', '水黾立于水面靠的不是体重轻，而是足上密布的疏水微毛把水面张力放大到足以撑起体重；剪掉这些绒毛，它同样会瞬间沉没。', 'incomplete', '["卵","若虫（多次蜕皮）","成虫"]', '中国大部分水域，广布于东亚', '常见', '["大水黾","宽跗塘黾","小水黾"]', '#5f7a86', 2
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'water-strider');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'hoverfly', '黑带食蚜蝇', 'Episyrphus balteatus', '不带针的蜂纹伪装客', 'diptera', '黑带食蚜蝇体色黑黄相间拟态蜂类，实则只有一对翅膀、没有螫针，是彻头彻尾的伪装。它能悬停、倒退、瞬间转向，飞行控制远比多数蜂类精细，幼虫更是蚜虫种群的重要天敌。', '[{"key":"体长","value":"约9–12毫米，是常见食蚜蝇中体型中等的种类","icon":"size"},{"key":"世代","value":"温暖地区一年可发生多代，世代周期短","icon":"time"},{"key":"栖息环境","value":"花园、农田及林缘开花植物附近","icon":"place"},{"key":"食性","value":"成虫访花取食花蜜和花粉，幼虫捕食蚜虫等软体小虫","icon":"food"},{"key":"飞行","value":"仅一对翅飞行，可悬停、倒退和急速转向","icon":"ability"},{"key":"拟态","value":"体色黑黄相间拟态蜂类警戒色，却不具螫针也不蜇人","icon":"ability"}]', '[{"id":"hoverfly-haltere","label":"平衡棒","note":"后翅退化成棒状，飞行时高速摆动感知身体姿态","anchor":"haltere","tone":"amber"},{"id":"hoverfly-wing","label":"翅膀","note":"仅一对膜质前翅，却能完成蜂类难以做到的悬停","anchor":"wing","tone":"sage"},{"id":"hoverfly-eye","label":"复眼","note":"几乎占满头部，雄虫两眼在头顶相接便于区分雌雄","anchor":"eye","tone":"coral"},{"id":"hoverfly-abdomen","label":"腹部","note":"黑黄相间的警戒色斑纹拟态蜂类，本身并无螫针","anchor":"abdomen","tone":"lavender"},{"id":"hoverfly-antenna","label":"触角","note":"触角短小不显眼，多半藏于头部前方两复眼间","anchor":"antenna","tone":"sage"},{"id":"hoverfly-thorax","label":"胸部","note":"结实，是唯一一对翅膀肌肉的着生之处","anchor":"thorax","tone":"coral"}]', '黑带食蚜蝇幼虫是蚜虫的重要天敌，一只幼虫一生可捕食数百只蚜虫，是农业和园艺常用的生物防治资源；成虫访花授粉，兼具传粉者角色。', '食蚜蝇黑黄相间的外表常让人误以为是蜂类，其实它只有一对翅膀且完全没有螫针，那身「警戒色」只是骗过天敌的伪装，纯属虚张声势。', 'complete', '["卵","幼虫（捕食蚜虫）","蛹","成虫"]', '广布于欧亚大陆及非洲北部，中国各地常见', '常见，被广泛研究和利用为生物防治资源', '["大灰食蚜蝇","细腹食蚜蝇","短尾管蚜蝇"]', '#c17a3a', 2
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'hoverfly');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'lacewing', '中华草蛉', 'Chrysoperla nipponensis', '背着猎物残骸潜行的绿衣客', 'neuroptera', '中华草蛉体态纤细，翅脉如网、复眼泛金铜色光泽，是花园和农田中常见的柔弱飞虫。它的幼虫却是凶猛的蚜虫杀手，捕食后常把猎物空壳背在身上作伪装，人称「蚜狮」。', '[{"key":"体长","value":"约10–15毫米，体态纤细，翅脉网状清晰可见","icon":"size"},{"key":"世代","value":"温暖地区一年可发生多代，成虫可存活数周","icon":"time"},{"key":"栖息环境","value":"农田、果园及林缘植被间","icon":"place"},{"key":"食性","value":"幼虫肉食性捕食蚜虫，成虫多取食花粉和蚜虫蜜露","icon":"food"},{"key":"伪装","value":"幼虫体背常驮负猎物残骸伪装自身，人称「蚜狮」","icon":"ability"},{"key":"趋光","value":"成虫复眼夜间可反射金色光泽，趋光性明显","icon":"ability"}]', '[{"id":"lacewing-wing","label":"翅膀","note":"翅脉纵横如网，透明质地在阳光下泛出淡淡虹彩","anchor":"wing","tone":"sage"},{"id":"lacewing-eye","label":"复眼","note":"呈金铜色光泽，是最醒目的识别特征","anchor":"eye","tone":"amber"},{"id":"lacewing-antenna","label":"触角","note":"丝状触角细长，静止时常向前方平伸不动","anchor":"antenna","tone":"coral"},{"id":"lacewing-thorax","label":"胸部","note":"胸部纤细，支撑着两对狭长的膜质翅膀","anchor":"thorax","tone":"lavender"},{"id":"lacewing-abdomen","label":"腹部","note":"腹部柔软修长，末端是繁殖器官所在的位置","anchor":"abdomen","tone":"sage"},{"id":"lacewing-leg","label":"足部","note":"细长多毛，幼虫阶段的足更适于追捕蚜虫","anchor":"leg","tone":"coral"}]', '中华草蛉幼虫是蚜虫、粉虱等农林害虫的重要天敌，被广泛用于农业生物防治；成虫访花取食花粉与蜜露，也参与一定程度的传粉。', '草蛉幼虫吸空蚜虫后，常把猎物的空壳一层层背在背上作伪装，混在蚜群残骸间悄悄靠近下一个目标，「蚜狮」的外号由此而来。', 'complete', '["卵（丝柄悬空）","幼虫（蚜狮）","蛹","成虫"]', '中国大部分地区', '常见，人工繁育用于农业生物防治', '["大草蛉","丽草蛉","亚非草蛉"]', '#9bb06a', 3
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'lacewing');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'earwig', '海滨蠼螋', 'Labidura riparia', '海礁石缝里的持铗守夜人', 'dermaptera', '海滨蠼螋栖息在海岸潮间带的石块与浮木下，腹末一对形态雌雄有别的尾铗是它的招牌武器，用于御敌和制服小型猎物。前翅退化成一对很短的革质鞘片，只盖住腹部前段，其余腹节完全裸露。', '[{"key":"体长","value":"约16–25毫米，体色深褐近黑，扁平细长","icon":"size"},{"key":"寿命","value":"若虫期数月，成虫可存活近一年，跨两季活动","icon":"time"},{"key":"栖息环境","value":"海岸潮间带石块下、漂浮木及腐殖质堆积处","icon":"place"},{"key":"食性","value":"杂食性，取食小型无脊椎动物、藻类碎屑及腐殖质","icon":"food"},{"key":"尾铗","value":"雌雄形态不同，雄虫尾铗弯曲不对称，雌虫较直","icon":"ability"},{"key":"御敌","value":"尾铗主要用于防御和制服小型猎物，并非取食人耳","icon":"ability"}]', '[{"id":"earwig-forceps","label":"尾铗","note":"腹末特化构造，用于防御天敌和制服猎物，雌雄形态不同","anchor":"forceps","tone":"coral"},{"id":"earwig-elytra","label":"短鞘翅","note":"前翅特化成极短的革质鞘片，只盖住腹部前段，后面各节全裸露","anchor":"elytra","tone":"sage"},{"id":"earwig-antenna","label":"触角","note":"念珠状细长，探测石缝间的湿度与气味","anchor":"antenna","tone":"amber"},{"id":"earwig-head","label":"头部","note":"扁平，便于钻入石块与浮木的狭窄缝隙藏身","anchor":"head","tone":"lavender"},{"id":"earwig-abdomen","label":"腹部","note":"柔韧可向上弯曲，方便尾铗从背后发起攻击","anchor":"abdomen","tone":"coral"},{"id":"earwig-leg","label":"足部","note":"短而有力，适应在碎石与湿沙间快速爬行","anchor":"leg","tone":"sage"}]', '海滨蠼螋栖息于海岸潮间带的石块与浮木下，取食小型无脊椎动物和有机碎屑，是滨海生境食物网中不起眼却常见的杂食环节。', '「蠼螋会钻进耳朵」是流传已久的民间讹传，蠼螋并无钻入耳道的习性，尾铗也只用来御敌和捕食，对人几乎没有威胁。', 'incomplete', '["卵（雌虫看护）","若虫（多次蜕皮）","成虫"]', '世界温带至热带海岸地区，中国沿海常见', '常见', '["日本蠼螋","大蠼螋","球螋"]', '#4a4038', 4
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'earwig');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'dung-beetle', '神农洁蜣螂', 'Catharsius molossus', '把粪球滚成摇篮的匠人', 'coleoptera', '神农洁蜣螂是中国常见的大型蜣螂，通体漆黑发亮，雄虫头顶生一根后弯的角状突起用于打斗。它在牛粪、猪粪堆下方掘穴筑巢，将粪便搓成球状供幼虫取食，是加速草地粪便分解、促进养分回归土壤的重要分解者。', '[{"key":"体长","value":"约22–32毫米，体态浑圆，通体漆黑发亮","icon":"size"},{"key":"寿命","value":"成虫可存活数月，幼虫在地下粪室中完成发育后就地化蛹","icon":"time"},{"key":"栖息环境","value":"牧场、农田及林缘的畜粪堆下方掘穴而居","icon":"place"},{"key":"食性","value":"成虫与幼虫都以哺乳动物粪便为食，幼虫尤其依赖粪球中的养分","icon":"food"},{"key":"筑巢","value":"以头部角状突起挖土掘穴，将粪便拖入洞穴深处搓成育幼球","icon":"ability"},{"key":"分解","value":"掘穴埋粪的行为能显著加快牧场粪便分解与养分回归土壤的速度","icon":"ability"}]', '[{"id":"dung-beetle-horn","label":"头角","note":"雄虫头部后弯的角状突起，掘穴松土与打斗时用于发力挑起对手","anchor":"horn","tone":"coral"},{"id":"dung-beetle-clypeus","label":"唇基","note":"前缘扁平扩宽如铲，是切割粪便和铲土时的主要工具","anchor":"clypeus","tone":"amber"},{"id":"dung-beetle-foreleg","label":"前足","note":"胫节外侧具粗齿如耙，用于扒拢粪便并向后蹬土掘穴","anchor":"foreleg","tone":"sage"},{"id":"dung-beetle-elytra","label":"鞘翅","note":"表面光滑漆黑，覆盖折叠收纳的膜质后翅","anchor":"elytra","tone":"lavender"},{"id":"dung-beetle-eye","label":"复眼","note":"相对较小，穴居生活对嗅觉和触觉的依赖高于视觉","anchor":"eye","tone":"sage"},{"id":"dung-beetle-pronotum","label":"前胸背板","note":"宽大隆起，雄虫争斗时用于抵住对方角状突起发力","anchor":"pronotum","tone":"amber"}]', '作为牧场与林缘常见的蜣螂之一，神农洁蜣螂通过掘穴埋粪把地表粪便迅速带入土壤，显著加快粪便分解和养分向土壤的回归，是草地生态系统里不可或缺的清道夫。', '雌虫产卵前会把粪球加工得极其光滑紧实，这颗球既是幼虫的产房也是全部口粮，孵化后的幼虫终生不必离开就能取食完成发育。', 'complete', '["卵（产于地下粪球内）","幼虫（粪球中取食发育）","蛹","成虫"]', '中国黄河以南大部分地区及东南亚', '常见，是中国传统中药材「蜣螂」的基原昆虫之一', '["污蜣螂","嗡蜣螂","蜉金龟"]', '#332e28', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'dung-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'weevil', '竹象', 'Cyrtotrachelus buquetii', '喙上挂着触角的钻笋客', 'coleoptera', '竹象是体形健壮的大型象甲，喙状口器细长弯曲，触角就长在喙的中段而非头部。雌虫用喙在嫩竹笋上钻孔产卵，孵化的幼虫蛀入笋心取食，是南方竹林常见的蛀笋害虫。', '[{"key":"体长","value":"约20–30毫米，是体形较大的象甲之一","icon":"size"},{"key":"生活周期","value":"一年一代，幼虫在竹笋内取食约1个月后入土化蛹","icon":"time"},{"key":"栖息环境","value":"南方竹林，尤其是新发嫩竹笋密集处","icon":"place"},{"key":"食性","value":"幼虫蛀食竹笋笋心，成虫啃食嫩竹笋表皮和嫩叶","icon":"food"},{"key":"钻孔","value":"雌虫以细长的喙在笋壁钻出产卵孔，深度可达笋心","icon":"ability"},{"key":"危害","value":"幼虫蛀空的竹笋常从内部腐烂枯死，是重要的竹林害虫","icon":"ability"}]', '[{"id":"weevil-rostrum","label":"喙管","note":"细长弯曲的管状喙，端部具咀嚼式口器，是钻孔产卵的主要工具","anchor":"rostrum","tone":"coral"},{"id":"weevil-antenna","label":"触角","note":"呈膝状弯曲，着生于喙的中段而非头部，是辨识象甲的关键特征","anchor":"antenna","tone":"amber"},{"id":"weevil-elytra","label":"鞘翅","note":"红棕色具纵向沟纹，质地坚硬覆盖膜质后翅","anchor":"elytra","tone":"sage"},{"id":"weevil-eye","label":"复眼","note":"位于喙基部两侧，视觉在觅偶和寻找嫩笋时起辅助作用","anchor":"eye","tone":"lavender"},{"id":"weevil-leg","label":"足部","note":"跗节具黏毛，便于攀附光滑的竹秆表面","anchor":"leg","tone":"sage"},{"id":"weevil-pronotum","label":"前胸背板","note":"窄长，前缘收缩以配合喙的活动空间","anchor":"pronotum","tone":"coral"}]', '竹象幼虫蛀食竹笋是南方竹林常见的农林害虫之一，成虫补充营养也会啃食嫩笋和叶片，严重时可导致大片新竹减产甚至枯死。', '竹象幼虫肥硕多脂，云南、广西等地民间将其视为传统食材，油炸后是颇受欢迎的高蛋白小吃。', 'complete', '["卵（产于笋壁钻孔内）","幼虫（蛀食笋心）","蛹（入土化蛹）","成虫"]', '中国南方及西南地区、中南半岛的竹林分布区', '常见，是南方竹林重要的蛀笋害虫', '["棕榈象甲","甘薯小象甲","栗实象"]', '#b06a35', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'weevil');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'click-beetle', '沟叩头虫', 'Agrypnus binodulus', '腹底藏着一副弹簧机关', 'coleoptera', '沟叩头虫是体态狭长的叩甲，鞘翅具规则纵沟，仰面朝天时能靠前胸腹突弹入中胸凹槽的机械储能猛然弹起翻正。幼虫栖息土中，即俗称的金针虫，是常见的地下害虫。', '[{"key":"体长","value":"约14–18毫米，体态狭长扁平，鞘翅具纵向沟纹","icon":"size"},{"key":"生活周期","value":"幼虫期长达2–3年，成虫寿命通常只有数周","icon":"time"},{"key":"栖息环境","value":"农田、草地及林缘的表土层与枯枝落叶下","icon":"place"},{"key":"食性","value":"幼虫（金针虫）取食植物根茎和种子，成虫多不取食","icon":"food"},{"key":"弹跳","value":"被翻倒时前胸腹突猛然弹入中胸凹槽，靠机械储能弹起并发出响声","icon":"ability"},{"key":"自翻","value":"这一弹跳兼具自我翻正与惊吓天敌两种作用","icon":"ability"}]', '[{"id":"click-beetle-pronotum","label":"前胸背板","note":"两后角尖锐后伸，与腹突共同构成弹跳装置的上半部分","anchor":"pronotum","tone":"sage"},{"id":"click-beetle-clickSpine","label":"弹跳腹突","note":"前胸下方伸出的长突起，弹跳时猛然嵌入中胸凹槽释放能量","anchor":"clickSpine","tone":"coral"},{"id":"click-beetle-elytra","label":"鞘翅","note":"表面具整齐的纵向沟槽，沟叩头虫因此得名","anchor":"elytra","tone":"amber"},{"id":"click-beetle-antenna","label":"触角","note":"锯齿状，静止时可折叠贴伏于头胸两侧凹槽内","anchor":"antenna","tone":"lavender"},{"id":"click-beetle-eye","label":"复眼","note":"位于头部两侧前方，负责侦测天敌靠近的动静","anchor":"eye","tone":"sage"},{"id":"click-beetle-leg","label":"足部","note":"较短小，遇险时更依赖弹跳逃生而非奔跑","anchor":"leg","tone":"coral"}]', '沟叩头虫幼虫（金针虫）长期生活于土壤中取食作物根茎和种子，是农田常见的地下害虫；成虫夜间活动，也是鸟类和其他捕食者的猎物。', '叩头虫被天敌扑倒后仰面朝天也不会束手就擒，前胸腹突弹入凹槽的瞬间能把整只虫弹到数厘米高的空中，趁机翻正逃生。', 'complete', '["卵","幼虫（金针虫，2–3年）","蛹","成虫"]', '中国大部分地区、朝鲜半岛及日本', '常见，幼虫是农田常见地下害虫之一', '["褐纹锹叩甲","细胸叩头虫","梳角叩甲"]', '#5a4a34', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'click-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'diving-beetle', '黄缘龙虱', 'Cybister chinensis', '背着一泡空气潜水的桨手', 'coleoptera', '黄缘龙虱是水生的大型龙虱，体态椭圆流线，鞘翅与前胸背板边缘镶一圈醒目黄边。它虽终生离不开水，靠的却是气管呼吸，需定期把腹部末端伸出水面，在鞘翅下补充一泡空气再潜回水中。', '[{"key":"体长","value":"约35–40毫米，是中国常见龙虱中体型较大的种类","icon":"size"},{"key":"寿命","value":"成虫寿命可达一年以上，冬季潜伏水底或淤泥中越冬","icon":"time"},{"key":"栖息环境","value":"池塘、稻田、沟渠等静水或缓流水域","icon":"place"},{"key":"食性","value":"肉食性，捕食蝌蚪、小鱼及水生昆虫，幼虫捕食量大","icon":"food"},{"key":"呼吸","value":"以气管呼吸，腹部末端伸出水面在鞘翅下储气，带着气泡潜水","icon":"ability"},{"key":"游泳","value":"后足扁平如桨，密生长毛，两足同步划水推进","icon":"ability"}]', '[{"id":"diving-beetle-hindleg","label":"后足","note":"扁平如桨，密生游泳毛，两足同步划动推进身体","anchor":"hindleg","tone":"coral"},{"id":"diving-beetle-elytra","label":"鞘翅","note":"橄榄黑色，边缘镶一圈黄边，下方是储存空气的夹层","anchor":"elytra","tone":"sage"},{"id":"diving-beetle-eye","label":"复眼","note":"较大，水面下也能感知光影变化搜寻猎物","anchor":"eye","tone":"lavender"},{"id":"diving-beetle-antenna","label":"触角","note":"丝状细长，浮出水面时用于感知空气与气味","anchor":"antenna","tone":"amber"},{"id":"diving-beetle-airStore","label":"储气层","note":"鞘翅下方与体背之间的空隙，潜水时储存随身携带的空气","anchor":"airStore","tone":"sage"},{"id":"diving-beetle-body","label":"体躯","note":"呈流线型椭圆，利于减少水中游动时的阻力","anchor":"body","tone":"coral"}]', '黄缘龙虱成虫和幼虫都是水域食物链中的顶端捕食者之一，大量取食蚊虫幼体和小型水生动物，对水质变化也较为敏感。', '龙虱幼虫因凶猛贪食被称作「水虎虫」，捕猎时用镰刀状上颚刺入猎物体内注入消化液，把内脏吸食一空只剩空壳。', 'complete', '["卵","幼虫（水虎虫，水生）","蛹（多在岸边土中）","成虫"]', '中国大部分地区、朝鲜半岛及日本', '常见', '["东方龙虱","锈色真龙虱","边纹龙虱"]', '#33454a', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'diving-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'rove-beetle', '梭毒隐翅虫', 'Paederus fuscipes', '身披警戒色的稻田卫士', 'coleoptera', '梭毒隐翅虫体形细长，前胸与腹部橙红相间，短鞘翅下露出可自由弯曲的长腹部。它本身不咬不螫，是稻田里的捕食性益虫，但体液含隐翅虫素，一旦被拍死在皮肤上就会引发线状皮炎。', '[{"key":"体长","value":"约7–10毫米，体形细长如蚁，橙黑相间十分醒目","icon":"size"},{"key":"世代","value":"温暖地区一年可发生多代，成虫寿命约数周至数月","icon":"time"},{"key":"栖息环境","value":"稻田、菜地及潮湿草丛，夜间趋光靠近人类居所","icon":"place"},{"key":"食性","value":"成虫与幼虫均捕食蚜虫、叶蝉等小型昆虫，是农田益虫","icon":"food"},{"key":"防御","value":"体液含隐翅虫素，被拍打挤压在皮肤上会引发线状红肿皮炎","icon":"ability"},{"key":"应对","value":"落在皮肤上应吹走或抖落，切勿直接拍打，可避免毒液接触","icon":"ability"}]', '[{"id":"rove-beetle-elytra","label":"鞘翅","note":"很短，只覆盖胸部，蓝黑色具金属光泽","anchor":"elytra","tone":"sage"},{"id":"rove-beetle-abdomen","label":"腹部","note":"橙红与黑色相间，柔韧可向上翘起，含隐翅虫素毒液","anchor":"abdomen","tone":"coral"},{"id":"rove-beetle-mandible","label":"上颚","note":"细长交叉，用于捕捉和咬碎蚜虫等小型猎物","anchor":"mandible","tone":"amber"},{"id":"rove-beetle-antenna","label":"触角","note":"念珠状，行走时不断摆动探测周围气味","anchor":"antenna","tone":"lavender"},{"id":"rove-beetle-eye","label":"复眼","note":"圆而凸出，对夜间灯光和移动物体都很敏感","anchor":"eye","tone":"sage"},{"id":"rove-beetle-leg","label":"足部","note":"细长善走，很少飞行，多在地表和叶片间快速爬行","anchor":"leg","tone":"coral"}]', '梭毒隐翅虫成虫和幼虫都以蚜虫、叶蝉等小型害虫为食，是稻田和菜地里活跃的捕食性天敌，对农业害虫有一定控制作用。', '隐翅虫从不主动叮咬，真正伤人的是被拍死时挤出的体液；正确做法是吹走或抖落它，而不是一巴掌拍上去。', 'complete', '["卵","幼虫","蛹","成虫"]', '中国大部分地区，广布于亚洲温带至热带地区', '常见，因夜间趋光常出现在居民区，是重要的医学昆虫', '["大隐翅虫","青翅隐翅虫","黑胸黄纹隐翅虫"]', '#bf5a2e', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'rove-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'flower-chafer', '白星花金龟', 'Protaetia brevitarsis', '鞘翅不掀也能起飞的访花客', 'coleoptera', '白星花金龟是常见的中型金龟子，铜黑色鞘翅上散布不规则白色斑点。鞘翅基部两侧各具一处凹缺，让后翅能不掀开鞘翅就直接展开飞行，这是它与犀金龟等近亲的关键区别。', '[{"key":"体长","value":"约17–24毫米，铜黑色鞘翅上散布白色斑点","icon":"size"},{"key":"生活周期","value":"一年一至二代，幼虫（蛴螬）在腐殖质中发育数月","icon":"time"},{"key":"栖息环境","value":"果园、林缘及堆肥腐殖质丰富处","icon":"place"},{"key":"食性","value":"成虫取食花蜜、花粉及成熟果实，幼虫取食腐殖质和朽木碎屑","icon":"food"},{"key":"飞行","value":"鞘翅基部具凹缺，不必掀开鞘翅即可展开后翅直接起飞","icon":"ability"},{"key":"养殖","value":"幼虫因取食腐殖质效率高，部分地区已人工养殖用作饲料","icon":"ability"}]', '[{"id":"flower-chafer-elytra","label":"鞘翅","note":"铜黑色具丝绒质感，散布形状不一的白色斑点","anchor":"elytra","tone":"amber"},{"id":"flower-chafer-notch","label":"翅缺刻","note":"鞘翅基部两侧的凹口，飞行时后翅由此伸出无需掀翅","anchor":"notch","tone":"coral"},{"id":"flower-chafer-pronotum","label":"前胸背板","note":"同具金属光泽，两侧边缘线条圆润","anchor":"pronotum","tone":"sage"},{"id":"flower-chafer-eye","label":"复眼","note":"位于头部两侧，飞行觅食时辅助定位花朵与果实","anchor":"eye","tone":"lavender"},{"id":"flower-chafer-antenna","label":"触角","note":"鳃叶状末节可开合，用于探测花香与成熟果实的气味","anchor":"antenna","tone":"amber"},{"id":"flower-chafer-leg","label":"足部","note":"跗节具爪，能牢牢抓附在光滑的花瓣和果皮上","anchor":"leg","tone":"sage"}]', '白星花金龟成虫访花吸蜜、取食成熟果实，兼具传粉与果园危害的双重角色；幼虫在腐殖质和堆肥中分解有机质，是重要的分解者。', '多数甲虫飞行前必须先掀起鞘翅露出后翅，白星花金龟却能靠鞘翅基部的凹缺直接伸出后翅起飞，鞘翅几乎不用张开。', 'complete', '["卵","幼虫（蛴螬）","蛹","成虫"]', '中国大部分地区、朝鲜半岛、日本及俄罗斯远东', '常见，部分地区人工养殖幼虫用作动物饲料', '["小青花金龟","斑青花金龟","褐花金龟"]', '#5e6b3a', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'flower-chafer');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'burying-beetle', '日本埋葬虫', 'Nicrophorus japonicus', '合力筑坟育幼的黑衣夫妻', 'coleoptera', '日本埋葬虫是黑底橙红斑纹的中型甲虫，一对成虫合作把小型动物尸体埋入地下做成育儿球。它们不仅照看虫卵孵化，幼虫孵出后双亲还会反刍消化过的肉糜直接喂食，这在昆虫里十分罕见。', '[{"key":"体长","value":"约14–22毫米，鞘翅黑底具两道橙红色波状横带","icon":"size"},{"key":"生活周期","value":"一年可发生多代，从发现尸体到幼虫离巢约需2–3周","icon":"time"},{"key":"栖息环境","value":"林地、草丛及农田边缘，靠气味搜寻新鲜小型尸体","icon":"place"},{"key":"食性","value":"成虫和幼虫都以小型脊椎动物尸体为食","icon":"food"},{"key":"埋葬","value":"夫妻协作在尸体下方掘土，把整具尸体沉入地下制成育儿球","icon":"ability"},{"key":"育幼","value":"双亲会反刍消化过的肉糜直接喂食幼虫，并驱赶闻讯而来的竞争者","icon":"ability"}]', '[{"id":"burying-beetle-elytra","label":"鞘翅","note":"黑底具两道橙红色波状横带，是常见的警戒色组合","anchor":"elytra","tone":"coral"},{"id":"burying-beetle-antenna","label":"触角","note":"末端膨大呈球杵状，对尸体腐败气味极其敏感","anchor":"antenna","tone":"amber"},{"id":"burying-beetle-abdomen","label":"腹部","note":"鞘翅较短，末端腹节外露，便于在土中翻动尸体","anchor":"abdomen","tone":"sage"},{"id":"burying-beetle-mandible","label":"上颚","note":"用于撕扯皮毛、切割尸体并搬运土壤","anchor":"mandible","tone":"coral"},{"id":"burying-beetle-eye","label":"复眼","note":"夜间活动时辅助锁定同伴与竞争对手的位置","anchor":"eye","tone":"lavender"},{"id":"burying-beetle-pronotum","label":"前胸背板","note":"圆盾状带细密绒毛，两侧常沾有掘土的泥屑","anchor":"pronotum","tone":"sage"}]', '日本埋葬虫夫妻协作掩埋小型动物尸体并合力育幼，加快尸体分解回归土壤养分循环，也压制了尸体上蝇蛆等竞争者的滋生。', '埋葬虫双亲会守在地下育儿室里，用触角轻碰幼虫乞食的信号后反刍喂食，这种双亲共同育幼的行为在昆虫中十分少见。', 'complete', '["卵（产于尸体旁土中）","幼虫（双亲喂养）","蛹","成虫"]', '中国大部分地区、朝鲜半岛及日本', '常见', '["黑负葬甲","大黑葬甲","横带葬甲"]', '#8a3a1f', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'burying-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'tortoise-beetle', '甘薯腊龟甲', 'Cassida circumdata', '鞘翅撑出一顶透明龟壳', 'coleoptera', '甘薯腊龟甲是外形酷似微型龟壳的小型叶甲，鞘翅与前胸背板向外扩展出一圈半透明裙边，把头和足都罩在下方。受惊时它紧贴叶面纹丝不动，扩展的裙边让天敌难以从边缘将它撬起。', '[{"key":"体长","value":"约6–8毫米，体形圆扁如缩小的龟壳","icon":"size"},{"key":"世代","value":"温暖地区一年可发生多代，成虫可存活数月","icon":"time"},{"key":"栖息环境","value":"甘薯地及其他旋花科植物叶片上","icon":"place"},{"key":"食性","value":"成虫和幼虫都取食甘薯等旋花科植物的叶片","icon":"food"},{"key":"防御","value":"扩展的半透明裙边把头足罩住，紧贴叶面时天敌难以撬动","icon":"ability"},{"key":"伪装","value":"幼虫会把蜕下的旧皮和粪便顶在背上尾叉处，举成遮蔽状的防御伞","icon":"ability"}]', '[{"id":"tortoise-beetle-margin","label":"裙边","note":"鞘翅与前胸背板向外扩展的半透明薄边，是龟甲得名的由来","anchor":"margin","tone":"coral"},{"id":"tortoise-beetle-elytra","label":"鞘翅","note":"拱起如壳，表面常有斑点或色斑变化","anchor":"elytra","tone":"sage"},{"id":"tortoise-beetle-head","label":"头部","note":"缩藏于前胸背板下方，受惊时几乎完全隐蔽不外露","anchor":"head","tone":"amber"},{"id":"tortoise-beetle-eye","label":"复眼","note":"小而不显眼，藏在扩展裙边投下的阴影里","anchor":"eye","tone":"lavender"},{"id":"tortoise-beetle-leg","label":"足部","note":"短小，紧贴叶面时连同头部一并被裙边遮住","anchor":"leg","tone":"sage"},{"id":"tortoise-beetle-pronotum","label":"前胸背板","note":"边缘同样扩展成裙，与鞘翅裙边前后相接完整罩住虫体","anchor":"pronotum","tone":"coral"}]', '甘薯腊龟甲成虫和幼虫都取食甘薯等旋花科植物叶片，是甘薯地里常见但为害较轻的植食性昆虫，也是龟甲亚科防御结构的典型代表。', '有些龟甲幼虫会把历次蜕下的旧皮和自己的粪便都顶在背后叉状的尾突上，举成一把遮住全身的「粪伞」，专门用来糊弄天敌的视线。', 'complete', '["卵","幼虫（背负蜕皮与粪便）","蛹","成虫"]', '中国南方及中部甘薯种植区，东亚至东南亚', '常见，甘薯地轻度害虫', '["甜菜龟甲","大龟甲","蓝翅龟甲"]', '#af9048', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'tortoise-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'hercules-beetle', '长戟大兜虫', 'Dynastes hercules', '以身量称霸雨林的双角巨人', 'coleoptera', '长戟大兜虫产于中南美洲热带雨林，雄虫连角体长可达约17厘米，是世界体长最长的甲虫。胸角与头角上下相对形成一副巨钳，格斗时用来夹起对手将其掀翻摔落；鞘翅颜色还会随湿度变化，干燥时泛黄绿，潮湿时转为深色。', '[{"key":"体长","value":"雄虫连角体长可达约17厘米，是已知体长最长的甲虫；雌虫无角且明显更小","icon":"size"},{"key":"生活周期","value":"幼虫期长达约1.5–2年，成虫可存活数月至一年","icon":"time"},{"key":"栖息环境","value":"中美洲至南美洲北部热带雨林的林冠层及腐木周围","icon":"place"},{"key":"食性","value":"幼虫蛀食朽木与腐殖质，成虫取食成熟落果与树干渗出的汁液","icon":"food"},{"key":"格斗","value":"胸角与头角上下相对形成钳状结构，夹住对手后将其举起摔落","icon":"ability"},{"key":"变色","value":"鞘翅颜色随湿度改变，干燥时呈黄绿色，潮湿或夜间转为深褐近黑","icon":"ability"}]', '[{"id":"hercules-beetle-thoracicHorn","label":"胸角","note":"前胸背板向前伸出的长角，与头角相对形成钳状结构","anchor":"thoracicHorn","tone":"coral"},{"id":"hercules-beetle-headHorn","label":"头角","note":"从头部向前平伸的长角，与胸角对握发力，用于夹起对手","anchor":"headHorn","tone":"amber"},{"id":"hercules-beetle-elytra","label":"鞘翅","note":"颜色随湿度改变，干燥时黄绿、潮湿转深，并非色素本身在变化","anchor":"elytra","tone":"sage"},{"id":"hercules-beetle-eye","label":"复眼","note":"位于头部两侧，对移动光影敏感，夜间常趋光飞行","anchor":"eye","tone":"lavender"},{"id":"hercules-beetle-leg","label":"步足","note":"末端具粗壮爪钩，用于攀附树干和翻转打斗中的对手","anchor":"leg","tone":"coral"},{"id":"hercules-beetle-pronotum","label":"前胸背板","note":"高高隆起并前伸成胸角基座，为打斗发力提供稳固支撑","anchor":"pronotum","tone":"amber"}]', '长戟大兜虫幼虫在朽木和腐殖质中取食分解，是热带雨林枯木循环的参与者；成虫聚集在树液和落果伤口取食，也是鸟类等大型天敌的猎物。', '鞘翅变色是因外层布满微孔的海绵状结构吸水后光学性质改变：干燥时孔隙中充满空气而呈黄绿色，吸湿后水分填满孔隙、透明度上升，转而显出深色的底色。', 'complete', '["卵","幼虫（1.5–2年）","蛹","成虫（数月至一年）"]', '中美洲至南美洲北部的热带雨林（洪都拉斯至厄瓜多尔、秘鲁等地），不产于中国', '原生地常见，因宠物贸易被大量人工繁育，野外种群受雨林砍伐威胁', '["双叉犀金龟","象兜虫","高加索南洋大兜虫"]', '#96a34a', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'hercules-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'whirligig-beetle', '豉甲', 'Gyrinus sp.', '水面画螺旋的分眼旋者', 'coleoptera', '豉甲是常聚集成群、在静水面高速打旋的小型甲虫，复眼分成上下两对，能同时看清水上与水下的动静。它划水时以极快频率转向却极少相撞，靠的是感知自身与同伴激起的水面波纹反射，如同声呐一般探测前方障碍。', '[{"key":"体长","value":"约5–8毫米，体态椭圆，背面具金属光泽","icon":"size"},{"key":"生活史","value":"幼虫水生阶段约数周，成虫可越冬存活数月至一年","icon":"time"},{"key":"栖息环境","value":"静水或缓流的池塘、湖泊表面，常成群聚集","icon":"place"},{"key":"食性","value":"成虫捕食落水昆虫并取食水面藻类碎屑，幼虫水下捕食小型水生动物","icon":"food"},{"key":"分眼","value":"复眼分成上下两部分，可同时看清水面上下的动静","icon":"ability"},{"key":"避障","value":"高速旋游时靠感知水面波纹反射避免碰撞，类似声呐定位","icon":"ability"}]', '[{"id":"whirligig-beetle-upperEye","label":"上眼","note":"露出水面之上的部分，专司观察空中与水面上方的动静","anchor":"upperEye","tone":"lavender"},{"id":"whirligig-beetle-lowerEye","label":"下眼","note":"没入水面之下的部分，与上眼分工独立探查水下情况","anchor":"lowerEye","tone":"sage"},{"id":"whirligig-beetle-midleg","label":"中足","note":"特化成桨状且能高频划动，是打旋前进的主要动力","anchor":"midleg","tone":"coral"},{"id":"whirligig-beetle-elytra","label":"鞘翅","note":"表面光滑并具金属光泽，紧贴体背减少滑水时的阻力","anchor":"elytra","tone":"amber"},{"id":"whirligig-beetle-antenna","label":"触角","note":"短粗贴近水面，推测能感知水面波纹的细微振动","anchor":"antenna","tone":"lavender"},{"id":"whirligig-beetle-body","label":"体躯","note":"呈流线型椭圆，腹面下常贮一层气泡辅助短暂潜水","anchor":"body","tone":"sage"}]', '豉甲成虫群聚在水面取食落水昆虫和藻类碎屑，是静水水域食物网里活跃的清道夫与捕食者；幼虫潜居水下捕食小型水生生物，也是鱼类等天敌的猎物。', '豉甲受惊或被鱼类叼住时，会从体表分泌一种带苹果香气的防御性液体，气味虽讨喜，却足以让大多数鱼类和天敌很快把它吐出来。', 'complete', '["卵","幼虫（水生）","蛹","成虫"]', '世界温带至热带的静水水域广泛分布，中国各地池塘湖泊常见', '常见', '["东方豉甲","宽豉甲","亮豉甲"]', '#293c4a', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'whirligig-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'ground-beetle', '中华金星步甲', 'Carabus sp.', '鞘翅嵌金星的夜行猎手', 'coleoptera', '中华金星步甲鞘翅密布金绿色圆点状刻纹如缀满金星，是行动敏捷的地面捕食者。它的后翅退化、鞘翅在中缝愈合无法飞行，终生只能疾走于地表猎捕蜗牛、昆虫幼虫等猎物，幼虫同样凶猛善捕。', '[{"key":"体长","value":"约25–35毫米，鞘翅密布金绿色圆点状刻纹","icon":"size"},{"key":"生活周期","value":"一年一代，幼虫期约1–2个月，成虫可存活一年以上","icon":"time"},{"key":"栖息环境","value":"林地及农田周边的落叶层、石块与朽木下","icon":"place"},{"key":"食性","value":"肉食性，捕食蜗牛、蛞蝓及多种昆虫幼虫，幼虫同样肉食","icon":"food"},{"key":"疾走","value":"后翅退化、鞘翅在中缝愈合无法飞行，只能依赖健脚疾走","icon":"ability"},{"key":"防御","value":"受惊扰时可从腹末腺体释放刺激性分泌物驱敌","icon":"ability"}]', '[{"id":"ground-beetle-elytra","label":"鞘翅","note":"密布金绿色圆形刻点如缀星，在中缝处愈合无法掀开飞行","anchor":"elytra","tone":"sage"},{"id":"ground-beetle-mandible","label":"上颚","note":"镰刀状且交叠有力，捕猎蜗牛与昆虫幼虫时用于夹持切割","anchor":"mandible","tone":"coral"},{"id":"ground-beetle-leg","label":"步足","note":"细长善走，是丧失飞行能力后主要依赖的捕猎与逃生方式","anchor":"leg","tone":"lavender"},{"id":"ground-beetle-eye","label":"复眼","note":"位于头部两侧，夜间活动时辅助锁定猎物的轮廓与动向","anchor":"eye","tone":"amber"},{"id":"ground-beetle-antenna","label":"触角","note":"丝状细长，行走时不断摆动探测地表气味与震动","anchor":"antenna","tone":"sage"},{"id":"ground-beetle-pronotum","label":"前胸背板","note":"边缘略呈心形，两侧脊线与鞘翅的金星纹路彼此呼应","anchor":"pronotum","tone":"coral"}]', '中华金星步甲成虫与幼虫都是地表活跃的肉食性天敌，大量捕食蜗牛、蛞蝓及多种昆虫的幼期个体，是农林生态系统中重要的自然控害者。', '金星步甲从孵化到寿终都无法飞行——鞘翅早已在中缝处愈合封死，无论成虫还是幼虫，捕猎和躲避天敌都只能依靠奔跑，是彻头彻尾的地面猎手。', 'complete', '["卵","幼虫（1–2个月）","蛹","成虫（可存活一年以上）"]', '中国大部分丘陵林地及农田周边', '常见', '["大步甲","婪步甲","广肩步甲"]', '#3f9a52', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'ground-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'blister-beetle', '中华豆芫菁', 'Epicauta chinensis', '以毒液护身的黑衣客', 'coleoptera', '中华豆芫菁是体态修长的黑色小甲虫，因幼虫期形态多次剧变而成为复变态昆虫的代表。成虫常成群取食豆科植物花叶，体内含有毒的斑蝥素，受惊时会从足关节渗出黄色血淋巴自卫。', '[{"key":"体长","value":"约10–20毫米，体态修长，全身黑色被覆细毛","icon":"size"},{"key":"生活周期","value":"幼虫期历经复变态、形态多变，成虫期约1–2个月","icon":"time"},{"key":"栖息环境","value":"农田、豆科作物地及草地，成虫常成群聚集取食","icon":"place"},{"key":"食性","value":"成虫取食大豆等豆科植物的花与叶片，幼虫寄生取食蝗虫卵块","icon":"food"},{"key":"化学防御","value":"受惊时从足关节反射性渗出黄色血淋巴，含有毒的斑蝥素","icon":"ability"},{"key":"复变态","value":"一龄幼虫（三爪蚴）主动寻找蝗虫卵块寄生，后期幼虫体态肥胖迟缓","icon":"ability"}]', '[{"id":"blister-beetle-elytra","label":"鞘翅","note":"质地柔软，覆盖腹部但末端略微分开，不能完全包裹","anchor":"elytra","tone":"lavender"},{"id":"blister-beetle-neck","label":"颈部","note":"前胸显著窄于头部收缩如颈，是芫菁科典型的体型特征","anchor":"neck","tone":"sage"},{"id":"blister-beetle-head","label":"头部","note":"明显宽于前胸，可自由转动，复眼位于两侧基部","anchor":"head","tone":"amber"},{"id":"blister-beetle-antenna","label":"触角","note":"丝状细长，取食时不断探测豆科植物花叶散出的气味","anchor":"antenna","tone":"coral"},{"id":"blister-beetle-leg","label":"足部","note":"关节薄弱处受惊时会渗出黄色血淋巴，是主要防御手段","anchor":"leg","tone":"amber"},{"id":"blister-beetle-abdomen","label":"腹部","note":"柔软膨大，内含大量斑蝥素，是入药部位也是毒性来源","anchor":"abdomen","tone":"sage"}]', '中华豆芫菁成虫群集取食豆科作物花叶，是大豆等作物常见的害虫；幼虫寄生取食蝗虫卵块，客观上又对蝗虫种群有一定抑制作用。', '芫菁科昆虫体内的斑蝥素自古便是中药斑蝥的来源，但其毒性强烈，接触量稍大即可致人皮肤起泡甚至中毒，绝非可自行采集使用之物。', 'complete', '["卵","幼虫（复变态，寄生蝗卵）","蛹","成虫（1–2个月）"]', '中国大部分农区，尤以华北、华东豆类产区多见', '常见，是大豆等豆科作物的农业害虫', '["大斑芫菁","眼斑芫菁","绿芫菁"]', '#332f2a', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'blister-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'hister-beetle', '阎甲', 'Hister sp.', '腐土里屏息装死的黑甲士', 'coleoptera', '阎甲是体壁坚硬、通体漆黑发亮的小型甲虫，常出没于腐尸、粪堆等腐败物中捕食蝇类幼虫。受到惊扰时，它能把足与触角完全收进体下的凹槽，蜷成一枚光滑硬壳装死，因此也是法医昆虫学观测尸体分解阶段的参考类群。', '[{"key":"体长","value":"约3–10毫米（因种而异），体壁坚硬呈卵圆或方形","icon":"size"},{"key":"生活周期","value":"完成一代约需数周，具体因食物与气温而异","icon":"time"},{"key":"栖息环境","value":"腐尸、粪堆、腐烂植物等腐败物及其周边土壤","icon":"place"},{"key":"食性","value":"成虫和幼虫均捕食蝇类幼虫等腐生性昆虫","icon":"food"},{"key":"装死","value":"受惊时将足与触角完全缩入体下凹槽，蜷成硬壳静止不动","icon":"ability"},{"key":"法医价值","value":"在腐尸上出现的时间与种类可提示尸体分解阶段，是法医昆虫学的参考类群","icon":"ability"}]', '[{"id":"hister-beetle-elytra","label":"鞘翅","note":"漆黑光滑坚硬，末端截平，露出其后一两节腹背板","anchor":"elytra","tone":"sage"},{"id":"hister-beetle-tuckedLeg","label":"缩足","note":"受惊时可完全折叠收进体下凹槽，是装死反应的一部分","anchor":"tuckedLeg","tone":"coral"},{"id":"hister-beetle-head","label":"头部","note":"可缩入前胸下方，装死时几乎完全隐没不外露","anchor":"head","tone":"lavender"},{"id":"hister-beetle-antenna","label":"触角","note":"膝状弯曲末端棒状，可收纳进头部下方的凹槽内","anchor":"antenna","tone":"amber"},{"id":"hister-beetle-pronotum","label":"前胸背板","note":"宽大坚硬，与鞘翅共同构成防御时的外层硬壳","anchor":"pronotum","tone":"sage"},{"id":"hister-beetle-abdomen","label":"腹部","note":"末节因鞘翅截短而外露，是阎甲科常见的识别特征","anchor":"abdomen","tone":"coral"}]', '阎甲成虫与幼虫都在腐尸、粪便等腐败环境中捕食蝇蛆，是分解者群落中重要的捕食性天敌，也因此成为法医昆虫学判断死亡时间的参考类群之一。', '阎甲遇险时不是逃跑而是把六足与触角全部收进体下凹槽，蜷成一枚光滑坚硬的椭圆小块装死，许多依赖动静捕食的天敌反而会因此对它失去兴趣。', 'complete', '["卵","幼虫","蛹","成虫"]', '全球广布，中国各地腐殖质丰富环境均有分布', '常见', '["四点阎甲","大阎甲","密纹阎甲"]', '#20211c', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'hister-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'treehopper', '角蝉', 'Centrotus sp.', '顶盔戴甲扮荆棘的微躯客', 'hemiptera', '角蝉是半翅目小型昆虫，头顶那顶造型夸张的盔状突起其实是前胸背板向后向上的极度延伸，并非头部或翅膀。多数种类与蚂蚁存在互利关系，靠分泌蜜露换取蚂蚁提供的保护，以刺吸式口器取食植物茎干汁液为生。', '[{"key":"体长","value":"约5–10毫米（因种而异），加上盔状突起后显得更大","icon":"size"},{"key":"世代","value":"温暖地区一年可发生多代，若虫期数周","icon":"time"},{"key":"栖息环境","value":"灌木、草本植物及树木的嫩茎处，常成群聚集","icon":"place"},{"key":"食性","value":"以刺吸式口器吸食植物茎干韧皮部汁液为食","icon":"food"},{"key":"盔状突起","value":"由前胸背板极度延伸而成，形似枝刺或驼峰，并非头部或翅膀","icon":"ability"},{"key":"互利共生","value":"多数种类分泌蜜露吸引蚂蚁，换取蚂蚁驱赶天敌的保护","icon":"ability"}]', '[{"id":"treehopper-helmet","label":"盔突","note":"由前胸背板向后上方极度延伸而成，形态因种类各异","anchor":"helmet","tone":"coral"},{"id":"treehopper-wing","label":"翅膀","note":"膜质前后翅平时收拢于盔突下方，飞行时才展开","anchor":"wing","tone":"sage"},{"id":"treehopper-eye","label":"复眼","note":"位于盔突基部两侧，体型虽小仍是警觉天敌的重要依据","anchor":"eye","tone":"lavender"},{"id":"treehopper-rostrum","label":"喙管","note":"细长的刺吸式口器，取食时插入嫩茎韧皮部吸取汁液","anchor":"rostrum","tone":"amber"},{"id":"treehopper-hindleg","label":"后足","note":"跳跃能力强，遇惊扰时能瞬间弹跳离开原栖枝条","anchor":"hindleg","tone":"coral"},{"id":"treehopper-abdomen","label":"腹部","note":"末端具分泌蜜露的腺体开口，是维系蚁蝉互利关系的关键","anchor":"abdomen","tone":"sage"}]', '角蝉以刺吸式口器取食植物汁液，是许多蚁蝉互利体系的一环——蚂蚁靠取食它分泌的蜜露获得营养，角蝉则借蚂蚁驱赶捕食者和寄生蜂换取保护。', '不少角蝉雌虫会守在卵块旁边，用身体或振动翅膀驱赶靠近的寄生蜂，这种护卵行为在体型微小的半翅目昆虫中并不多见。', 'incomplete', '["卵","若虫（多次蜕皮）","成虫"]', '中国大部分地区，广布于亚洲温带至热带植被茂密处', '常见', '["水牛角蝉","瘤角蝉","横脊角蝉"]', '#5f7a3f', 2
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'treehopper');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'ichneumon-wasp', '姬蜂', 'Megarhyssa sp.', '隔着树皮下针的寄生客', 'hymenoptera', '姬蜂是体态修长的寄生蜂，雌虫尾端拖着一根远长于体长的产卵器，能穿透数厘米厚的树干，把卵产在树蜂等蛀木昆虫的幼虫体内或附近。它并不蜇人——那根细长如针的构造是产卵器而非螫针，成虫本身对人几乎无害。', '[{"key":"体长","value":"体长约30–40毫米，雌虫产卵器可长达体长的2–3倍以上","icon":"size"},{"key":"生活史","value":"幼虫在寄主体内发育数月，成虫寿命约数周","icon":"time"},{"key":"栖息环境","value":"阔叶林中衰弱或新近死亡的树干周边","icon":"place"},{"key":"食性","value":"幼虫寄生取食树蜂等蛀木昆虫的幼虫，成虫可能取食花蜜或寄主分泌物","icon":"food"},{"key":"产卵","value":"雌虫以细长产卵器穿透数厘米厚的树干，把卵产在寄主幼虫体内或附近","icon":"ability"},{"key":"不蜇人","value":"尾端细长构造是产卵器而非螫针，不具攻击性、不会主动蜇人","icon":"ability"}]', '[{"id":"ichneumon-wasp-ovipositor","label":"产卵器","note":"细长如针，可穿透数厘米厚的木质部，是接触寄主的关键","anchor":"ovipositor","tone":"amber"},{"id":"ichneumon-wasp-antenna","label":"触角","note":"丝状细长，能感知木质部深处传出的振动与化学信号","anchor":"antenna","tone":"coral"},{"id":"ichneumon-wasp-wing","label":"翅膀","note":"膜质透明，飞行时振翅频率高，静止时收拢贴于背部","anchor":"wing","tone":"sage"},{"id":"ichneumon-wasp-waist","label":"细腰","note":"腹柄细长如线，让腹部能灵活弯曲对准产卵位置","anchor":"waist","tone":"lavender"},{"id":"ichneumon-wasp-eye","label":"复眼","note":"较大，辅助在林间弱光下定位适宜产卵的树干","anchor":"eye","tone":"coral"},{"id":"ichneumon-wasp-leg","label":"足部","note":"细长具爪，抓附树皮完成长时间的钻孔产卵过程","anchor":"leg","tone":"amber"}]', '姬蜂是森林中蛀木性昆虫种群的重要天敌，通过寄生控制树蜂等害虫数量，在维持林木健康和生态平衡中扮演调节者的角色。', '姬蜂并没有透视树干的能力，普遍认为它是靠触角和产卵器尖端感知寄主幼虫取食木质部时留下的振动或气味，才能隔着数厘米厚的木头精准定位猎物。', 'complete', '["卵","幼虫（寄生，数月）","蛹","成虫（数周）"]', '北半球温带阔叶林广布，中国东北、华北及西南山地可见', '常见但因隐蔽的生活史不易被观察到', '["大痣姬蜂","瘤姬蜂","长尾姬蜂"]', '#a15a2f', 2
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'ichneumon-wasp');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'dobsonfly', '中华齿蛉', 'Acanthacorydalis sp.', '颚如枯枝的溪涧夜客', 'megaloptera', '中华齿蛉是广翅目大型昆虫，雄虫具一对夸张延长如枯枝的上颚，主要用于同性间的求偶炫示与格斗，咬合力其实较弱；雌虫上颚短小却更有力。幼虫俗称水蜈蚣，水生肉食，是山涧水质清洁程度的指示生物。', '[{"key":"体长","value":"成虫体长约50–70毫米，雄虫连颚可显著超出体长","icon":"size"},{"key":"生活史","value":"幼虫水生阶段可长达2–3年，成虫寿命通常只有一两周","icon":"time"},{"key":"栖息环境","value":"山地清澈溪流及其沿岸植被，幼虫栖于水底石块下","icon":"place"},{"key":"食性","value":"幼虫肉食性，捕食水生昆虫及其他小型无脊椎动物；成虫极少取食","icon":"food"},{"key":"长颚炫示","value":"雄虫上颚延长如枯枝，主要用于争偶炫示与格斗，实际咬合力较弱","icon":"ability"},{"key":"水质指示","value":"幼虫对水质污染敏感，是山涧溪流水质清洁程度的指示生物","icon":"ability"}]', '[{"id":"dobsonfly-mandible","label":"上颚","note":"雄虫上颚延长如枯枝，格斗与炫示用途大于实际取食","anchor":"mandible","tone":"coral"},{"id":"dobsonfly-wing","label":"翅膀","note":"翅展宽大具网状翅脉，停栖时呈屋脊状覆盖背部","anchor":"wing","tone":"lavender"},{"id":"dobsonfly-eye","label":"复眼","note":"位于头部两侧，夜间飞行和寻偶时辅助定向","anchor":"eye","tone":"sage"},{"id":"dobsonfly-antenna","label":"触角","note":"丝状细长，用于近距离感知同类的气味与位置","anchor":"antenna","tone":"amber"},{"id":"dobsonfly-thorax","label":"胸部","note":"着生三对足与两对翅，肌肉发达支撑短暂而有力的飞行","anchor":"thorax","tone":"lavender"},{"id":"dobsonfly-abdomen","label":"腹部","note":"细长柔软，雌虫末端产卵器用于把卵产在近水的石壁","anchor":"abdomen","tone":"coral"}]', '中华齿蛉幼虫是山涧溪流中活跃的水生肉食者，捕食其他水生昆虫，也是鱼类的重要食物来源；其对水质敏感的特性，使它常被用作评估溪流水质的指示物种。', '中华齿蛉幼虫常被误称为水虿，但那其实是蜻蜓与豆娘若虫的专称；它正确的俗名是水蜈蚣或爬沙虫，体侧密生丝状鳃，常紧贴溪底石块生活。', 'complete', '["卵","幼虫（水蜈蚣，2–3年）","蛹（陆生）","成虫（一两周）"]', '中国长江流域及以南山地清澈溪流分布区', '对水质敏感，常见于水质良好的山涧，污染水域少见或消失', '["云南巨齿蛉","暗色多脉齿蛉","中华鱼蛉"]', '#4a3c2a', 5
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'dobsonfly');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'goliath-beetle', '大王花金龟', 'Goliathus goliatus', '顶着雕纹盾牌的雨林巨人', 'coleoptera', '大王花金龟产于非洲赤道雨林，是世界上最重的甲虫之一。雄虫头部生有一枚Y形分叉的扁角，用来撬开争食的对手；宽阔的前胸背板上是黑白相间的纵条纹，鞘翅则呈天鹅绒质感的栗褐色。', '[{"key":"体长","value":"雄虫体长约60–110毫米（含头胸角），雌虫略小且无角","icon":"size"},{"key":"体重","value":"幼虫可达80–100克，是世界上最重的昆虫之一","icon":"weight"},{"key":"生活周期","value":"幼虫期长达1–2年，成虫寿命通常只有数月","icon":"time"},{"key":"栖息环境","value":"非洲赤道地区热带雨林的林冠层及林缘","icon":"place"},{"key":"食性","value":"幼虫需高蛋白食物，成虫舔食树液并取食熟果","icon":"food"},{"key":"飞行","value":"体态硕大仍能振翅起飞，鞘翅掀起后露出膜质后翅","icon":"ability"}]', '[{"id":"goliath-beetle-headHorn","label":"头角","note":"Y形分叉的头角，雄虫间争夺食物与配偶时用来挑起对手","anchor":"headHorn","tone":"coral"},{"id":"goliath-beetle-elytra","label":"鞘翅","note":"鞘翅是天鹅绒质感的栗褐色，不反光，与前胸的黑白条纹形成对照","anchor":"elytra","tone":"sage"},{"id":"goliath-beetle-stripe","label":"条纹","note":"前胸背板上的黑白纵条纹，是这一属最醒目的招牌，各个体的宽窄不同","anchor":"stripe","tone":"amber"},{"id":"goliath-beetle-eye","label":"复眼","note":"复眼分布在头部两侧，对移动的光影变化较为敏感","anchor":"eye","tone":"lavender"},{"id":"goliath-beetle-leg","label":"足部","note":"跗节具锐利爪钩，攀附粗糙树皮时不易滑脱","anchor":"leg","tone":"coral"},{"id":"goliath-beetle-pronotum","label":"前胸背板","note":"前胸背板宽阔隆起，像一面盾牌，纵条纹就刻在它上面","anchor":"pronotum","tone":"sage"}]', '成虫在林冠层取食树液与熟果，客观上参与传粉与种子传播；幼虫则蛰伏地表分解富含蛋白质的腐殖质，是雨林物质循环的参与者。', '大王花金龟幼虫体重可达成虫的数倍，人工饲养者常需额外投喂猫粮等高蛋白食物，才能让它长成理想的体型和角型。', 'complete', '["卵","幼虫（1–2年，需高蛋白食物）","蛹","成虫（数月）"]', '非洲中西部赤道雨林地区（如喀麦隆、刚果盆地）', '原生地常见，因宠物贸易被大量人工繁育，野生种群受雨林砍伐威胁', '["皇冠花潜","细角疣犀金龟","长戟大兜虫"]', '#2b2420', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'goliath-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'bombardier-beetle', '屁步甲', 'Pheropsophus jessoensis', '灼热喷雾的地表狙击手', 'coleoptera', '屁步甲是头胸橙黄、鞘翅蓝黑的中型步甲，腹末藏着一间「爆炸室」。受到威胁时，两种前体物质在其中瞬间反应，喷出接近100°C的苯醌雾并能精准调整方向，是昆虫化学防御的经典案例。', '[{"key":"体长","value":"约18–22毫米，头胸橙黄，鞘翅蓝黑色具金属光泽","icon":"size"},{"key":"世代","value":"一年一至两代，成虫可越冬存活近一年","icon":"time"},{"key":"栖息环境","value":"农田、河岸及林缘湿润多石的地表","icon":"place"},{"key":"食性","value":"肉食性，捕食蚂蚁及其他小型节肢动物","icon":"food"},{"key":"喷射防御","value":"腹末爆炸室内过氧化氢与对苯二酚瞬间反应，喷出近100°C的灼热雾","icon":"ability"},{"key":"定向喷射","value":"腹部可旋转调整角度，精准将喷雾对准逼近的天敌","icon":"ability"}]', '[{"id":"bombardier-beetle-sprayTip","label":"喷射孔","note":"腹末爆炸室内两种化学物质瞬间反应，喷出近100°C的灼热雾","anchor":"sprayTip","tone":"coral"},{"id":"bombardier-beetle-elytra","label":"鞘翅","note":"蓝黑色鞘翅具金属光泽，与橙黄色头胸形成鲜明对比","anchor":"elytra","tone":"lavender"},{"id":"bombardier-beetle-mandible","label":"上颚","note":"上颚发达有力，捕食蚂蚁等猎物时用于快速夹击","anchor":"mandible","tone":"amber"},{"id":"bombardier-beetle-eye","label":"复眼","note":"复眼突出，对靠近的天敌动向十分敏感","anchor":"eye","tone":"sage"},{"id":"bombardier-beetle-antenna","label":"触角","note":"丝状触角不断摆动，探测地表猎物与天敌的气味","anchor":"antenna","tone":"coral"},{"id":"bombardier-beetle-leg","label":"足部","note":"步足细长善走，多在潮湿地表快速追袭猎物","anchor":"leg","tone":"lavender"}]', '屁步甲是农田及河岸地表活跃的肉食性天敌，捕食蚂蚁等小型节肢动物；其爆炸性喷雾防御也是研究昆虫化学生态与仿生喷射装置的经典案例。', '喷射不是一股连续气流，而是每秒数百次的脉冲；正是脉冲之间的间歇，让它自己不被这接近沸点的液体烫伤。', 'complete', '["卵","幼虫（捕食性）","蛹","成虫"]', '中国、朝鲜半岛及日本', '常见', '["中华金星步甲","金边大步甲","广肩步甲"]', '#c65a26', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'bombardier-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'darkling-beetle', '甘肃鳖甲', 'Anatolica sp.', '荒漠里锁住水分的黑甲', 'coleoptera', '甘肃鳖甲是中国西北荒漠常见的拟步甲，体态浑圆漆黑，左右鞘翅在中缝完全愈合、后翅退化，终生不能飞行。愈合的鞘翅下方形成密闭空腔，大幅减少体表水分蒸发，是应对极端干旱环境的适应结果。', '[{"key":"体长","value":"约15–25毫米，体态浑圆，通体黑色或黑褐色","icon":"size"},{"key":"寿命","value":"成虫寿命可达一年以上，幼虫期同样漫长","icon":"time"},{"key":"栖息环境","value":"中国西北荒漠及戈壁的砂砾地表","icon":"place"},{"key":"食性","value":"植食性，取食荒漠植物枯叶、种子及有机碎屑","icon":"food"},{"key":"鞘翅愈合","value":"左右鞘翅在中缝完全愈合，后翅退化，终生不能飞行","icon":"ability"},{"key":"保水","value":"愈合鞘翅下方形成密闭空腔，大幅减少体表水分蒸发","icon":"ability"}]', '[{"id":"darkling-beetle-fusedElytra","label":"愈合鞘翅","note":"左右鞘翅在中缝处完全愈合封死，后翅退化终生不能飞行","anchor":"fusedElytra","tone":"sage"},{"id":"darkling-beetle-leg","label":"足部","note":"细长步足支撑身体离地，减少与灼热地表的接触","anchor":"leg","tone":"coral"},{"id":"darkling-beetle-head","label":"头部","note":"头部小而坚硬，触角基部具保护性凹槽","anchor":"head","tone":"amber"},{"id":"darkling-beetle-antenna","label":"触角","note":"丝状触角探测地表湿度与食物气味，是荒漠中的重要感官","anchor":"antenna","tone":"lavender"},{"id":"darkling-beetle-pronotum","label":"前胸背板","note":"前胸背板宽扁，与愈合鞘翅共同构成坚硬的防御外壳","anchor":"pronotum","tone":"sage"},{"id":"darkling-beetle-abdomen","label":"腹部","note":"腹部藏于愈合鞘翅下的密闭空腔内，减少水分蒸发","anchor":"abdomen","tone":"coral"}]', '甘肃鳖甲以荒漠植物残体和有机碎屑为食，是荒漠地表物质循环中常见的分解者，也是荒漠昆虫适应极端干旱环境的典型代表。', '鞘翅愈合、终生不飞并非甘肃鳖甲独有：拟步甲科两万余种里大量荒漠成员都走上了同一条路——在缺水的旷野上，飞行这项能力并不划算。', 'complete', '["卵","幼虫","蛹","成虫"]', '中国甘肃、内蒙古及西北荒漠戈壁地区', '常见，荒漠及戈壁生境中数量稳定', '["网目拟步甲","琵甲","沙漠潜甲"]', '#3a3632', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'darkling-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'net-winged-beetle', '红萤', 'Lycostomus sp.', '穿警戒红衣的网翅使者', 'coleoptera', '红萤鞘翅鲜红柔软，表面纵横交错的脊纹如网格。它体内含有令天敌不悦的化学物质，鲜红体色是写给天敌的警戒信号，也是多种无毒昆虫争相模仿的贝氏拟态模板。', '[{"key":"体长","value":"约10–16毫米，全身鲜红或橙红色，鞘翅质地柔软","icon":"size"},{"key":"世代","value":"一年一代，成虫多见于春夏之交","icon":"time"},{"key":"栖息环境","value":"阔叶林缘、灌丛及朽木周边的潮湿处","icon":"place"},{"key":"食性","value":"成虫多访花或不取食，幼虫捕食朽木中的小型无脊椎动物","icon":"food"},{"key":"警戒色","value":"鲜红体色是对天敌的警戒信号，体内含有不良气味或毒素","icon":"ability"},{"key":"拟态原型","value":"是多种可食昆虫争相模仿的拟态模板，构成穆氏拟态环","icon":"ability"}]', '[{"id":"net-winged-beetle-elytra","label":"鞘翅","note":"鞘翅质地柔软扁平，鲜红体色是典型的警戒色","anchor":"elytra","tone":"coral"},{"id":"net-winged-beetle-ridge","label":"网纹","note":"鞘翅表面纵横交错的脊纹如网格，红萤科由此得名","anchor":"ridge","tone":"amber"},{"id":"net-winged-beetle-antenna","label":"触角","note":"锯齿状触角乌黑，与鲜红体色形成强烈反差","anchor":"antenna","tone":"sage"},{"id":"net-winged-beetle-eye","label":"复眼","note":"复眼乌黑突出，位于头部前侧两端","anchor":"eye","tone":"lavender"},{"id":"net-winged-beetle-pronotum","label":"前胸背板","note":"前胸背板同为红色，边缘略向外扩展","anchor":"pronotum","tone":"coral"},{"id":"net-winged-beetle-leg","label":"足部","note":"细长的足多为黑色，便于在枝叶间快速爬行","anchor":"leg","tone":"sage"}]', '红萤体内含有让天敌不悦的化学物质，鲜红警戒色使多种可食昆虫演化出与它相似的体色和斑纹，成为穆氏拟态环中的核心模板物种之一。', '红萤的鲜红并非用来吸引配偶，而是写给天敌看的警告牌；不少滋味鲜美的甲虫和蛾类演化出与它几乎相同的红色斑纹，借它的恶名蒙混过关。', 'complete', '["卵","幼虫（捕食性，栖朽木）","蛹","成虫"]', '中国南方及西南山地阔叶林区', '常见', '["橙红萤","黑角红萤","截脊长角红萤"]', '#c0392b', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'net-winged-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'leaf-beetle', '榆蓝叶甲', 'Pyrrhalta aenescens', '啃秃榆树梢的古铜小甲', 'coleoptera', '榆蓝叶甲是常见的小型叶甲，体短圆近半球形，鞘翅带蓝绿至古铜的金属光泽。成虫与幼虫都取食榆树叶片，是榆树的重要害虫；幼虫只啃食叶肉、留下网状叶脉，严重发生时可致整株榆树提早落叶。', '[{"key":"体长","value":"约5–7毫米，体色黄褐至古铜色，具光泽","icon":"size"},{"key":"世代","value":"温暖地区一年可发生2–3代，世代周期短","icon":"time"},{"key":"栖息环境","value":"榆树分布区的树冠及树干周边","icon":"place"},{"key":"食性","value":"成虫与幼虫都取食榆树叶片，是榆树的重要害虫","icon":"food"},{"key":"群集化蛹","value":"老熟幼虫常成群爬至树干裂缝或基部一同化蛹","icon":"ability"},{"key":"危害","value":"严重发生时可将整株榆树叶片啃食殆尽，造成早期落叶","icon":"ability"}]', '[{"id":"leaf-beetle-elytra","label":"鞘翅","note":"鞘翅带蓝绿至古铜的金属光泽，表面布满细密刻点，是它中文名的由来","anchor":"elytra","tone":"amber"},{"id":"leaf-beetle-head","label":"头部","note":"头部窄小，前口式便于啃食叶片边缘","anchor":"head","tone":"sage"},{"id":"leaf-beetle-antenna","label":"触角","note":"丝状触角约为体长一半，用于寻找新鲜榆叶","anchor":"antenna","tone":"coral"},{"id":"leaf-beetle-leg","label":"足部","note":"跗节具黏毛，能牢牢抓附在光滑叶面上取食","anchor":"leg","tone":"lavender"},{"id":"leaf-beetle-pronotum","label":"前胸背板","note":"前胸背板具光泽，色泽与鞘翅相近","anchor":"pronotum","tone":"amber"},{"id":"leaf-beetle-eye","label":"复眼","note":"复眼小而突出，位于头部两侧前方","anchor":"eye","tone":"sage"}]', '榆蓝叶甲成虫与幼虫都以榆树叶片为食，严重发生时可致整株榆树叶片啃光、生长受抑，是城市行道树与防护林常见的重要害虫。', '老熟幼虫化蛹前会成群结队沿树干爬行，密密麻麻聚集在树皮裂缝或基部一同化蛹，集体行动能大幅降低单只幼虫被天敌盯上的概率。', 'complete', '["卵","幼虫（群集取食）","蛹","成虫"]', '中国大部分榆树分布区，广布于东亚', '常见，是城市行道树及防护林榆树的重要害虫', '["柳蓝叶甲","黄守瓜","马铃薯甲虫"]', '#a3a83c', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'leaf-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'damselfly', '豆娘', 'Ischnura sp.', '水边穿针引线的细腰客', 'odonata', '豆娘体态纤细，腹部细长如针，静息时四片翅收拢竖立于背上，与蜻蜓平展停栖明显不同，左右复眼也分离而不相接。它飞行力较弱，多在水边植物间短距离穿梭，捕食蚊蝇等小型飞虫。', '[{"key":"体长","value":"约25–35毫米，体态纤细，腹部细长如针","icon":"size"},{"key":"世代","value":"温暖地区一年可发生多代，成虫期数周","icon":"time"},{"key":"栖息环境","value":"池塘、水沟及溪流沿岸的水生植物间","icon":"place"},{"key":"食性","value":"稚虫捕食水中小型无脊椎动物，成虫捕食蚊蝇等小型飞虫","icon":"food"},{"key":"停栖","value":"静息时四片翅收拢竖立于背上，与蜻蜓平展停栖明显不同","icon":"ability"},{"key":"飞行","value":"飞行力较弱，多短距离穿梭于水边植物间而非长距离巡航","icon":"ability"}]', '[{"id":"damselfly-wing","label":"翅膀","note":"静息时四片翅收拢竖立于背上，蜻蜓则习惯平展停栖","anchor":"wing","tone":"lavender"},{"id":"damselfly-eye","label":"复眼","note":"左右复眼分离不相接，蜻蜓的复眼则几乎连成一片","anchor":"eye","tone":"sage"},{"id":"damselfly-abdomen","label":"腹部","note":"腹部极细如针，是豆娘区别于蜻蜓最直观的特征","anchor":"abdomen","tone":"coral"},{"id":"damselfly-thorax","label":"胸部","note":"胸部倾斜，着生两对翅与三对细长的步足","anchor":"thorax","tone":"amber"},{"id":"damselfly-leg","label":"足部","note":"步足细长多刺，飞行时收拢用于捕捉小型飞虫","anchor":"leg","tone":"lavender"},{"id":"damselfly-antenna","label":"触角","note":"触角短小不显眼，感知功能远不及复眼重要","anchor":"antenna","tone":"sage"}]', '豆娘稚虫是水域中活跃的小型捕食者，成虫则在水边植被间捕食蚊蝇等飞虫，是水生与陆生食物链之间的重要连接环节。', '豆娘的稚虫住在水里，腹末拖着三片叶状尾鳃，既用来呼吸也当尾鳍划水；蜻蜓稚虫没有这三片，靠直肠里的鳃呼吸，还能喷水推进。', 'incomplete', '["卵","稚虫（水生）","成虫"]', '中国大部分水域，广布于亚洲温带至热带地区', '常见', '["尾蟌","扇蟌","色蟌"]', '#3d6d9c', 3
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'damselfly');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'orchid-mantis', '兰花螳螂', 'Hymenopus coronatus', '比真花更会骗蜂的伪装者', 'mantodea', '兰花螳螂粉白相间，中后足腿节扩展成花瓣状叶片，本身就「长成」一朵花，而非躲在真花上伏击。研究显示，它对访花昆虫的吸引力甚至超过附近真正的花朵，是攻击性拟态最典型的案例之一。', '[{"key":"体长","value":"雌虫可达60–70毫米，雄虫明显较小仅约25毫米","icon":"size"},{"key":"生活周期","value":"若虫期数月，成虫可存活数月","icon":"time"},{"key":"栖息环境","value":"东南亚热带雨林的开花灌木及林下植被","icon":"place"},{"key":"食性","value":"纯肉食性，伏击访花的蝇类、蜂类及蝴蝶等传粉昆虫","icon":"food"},{"key":"拟态","value":"粉白体色与花瓣状腿叶构成主动拟态，本身就是一朵花","icon":"ability"},{"key":"引诱力","value":"研究显示其对传粉昆虫的吸引力甚至超过附近的真花","icon":"ability"}]', '[{"id":"orchid-mantis-petalLeg","label":"花瓣足","note":"中后足腿节扩展成粉白色花瓣状叶片，是主动拟态的核心","anchor":"petalLeg","tone":"coral"},{"id":"orchid-mantis-raptorialLeg","label":"捕捉足","note":"前足特化成镰刀状，静止如待放花蕊，出击时才现原形","anchor":"raptorialLeg","tone":"amber"},{"id":"orchid-mantis-head","label":"头部","note":"头部小巧呈三角形，可灵活转动搜寻猎物动向","anchor":"head","tone":"sage"},{"id":"orchid-mantis-eye","label":"复眼","note":"复眼乌黑，与粉白体色形成鲜明对比","anchor":"eye","tone":"lavender"},{"id":"orchid-mantis-abdomen","label":"腹部","note":"腹部宽扁略上翘，边缘轮廓也融入整体花形","anchor":"abdomen","tone":"coral"},{"id":"orchid-mantis-wing","label":"翅膀","note":"翅膀收拢时贴合体侧，不破坏整体的拟花轮廓","anchor":"wing","tone":"sage"}]', '兰花螳螂并非借助真花隐蔽，而是自身外形和体色本身就构成一朵「假花」，主动引诱访花昆虫靠近，是攻击性拟态最典型的案例之一。', '刚孵化的兰花螳螂若虫并不是粉白色，而是红黑相间，模仿有毒的猎蝽若虫；要再蜕几次皮，才换上那身花瓣般的粉白装束。', 'incomplete', '["卵鞘","若虫（多次蜕皮，色泽渐变）","成虫"]', '东南亚热带雨林（马来西亚、印度尼西亚、泰国等地）', '原生地不算罕见，因宠物贸易被广泛人工繁育', '["魔花螳螂","幽灵螳螂","中华大刀螳"]', '#d98bab', 3
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'orchid-mantis');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'dead-leaf-butterfly', '枯叶蛱蝶', 'Kallima inachus', '翻个面就是一片枯叶', 'lepidoptera', '枯叶蛱蝶前翅正面亮橙色带斜贯黑边，翅反面却酷似一片枯叶，连主脉与霉斑都逼真复刻。停栖收拢双翅后几乎难以察觉，受惊振翅时忽艳忽枯的反差，常让追击的鸟类瞬间跟丢目标。', '[{"key":"体长","value":"翼展约85–100毫米，正反两面色彩反差极大","icon":"size"},{"key":"世代","value":"温暖地区一年可发生多代，成虫可越冬","icon":"time"},{"key":"栖息环境","value":"亚热带常绿阔叶林及林缘阴湿处","icon":"place"},{"key":"食性","value":"幼虫取食爵床科等植物叶片，成虫嗜食树液和腐果","icon":"food"},{"key":"拟态","value":"翅反面色斑纹理酷似枯叶，连主脉与霉斑都逼真复刻","icon":"ability"},{"key":"静止伪装","value":"停栖收拢双翅后如一片挂在枝头的枯叶，几乎难以察觉","icon":"ability"}]', '[{"id":"dead-leaf-butterfly-underwing","label":"翅反面","note":"翅反面色斑纹理酷似枯叶，连主脉与霉斑都逼真复刻","anchor":"underwing","tone":"amber"},{"id":"dead-leaf-butterfly-forewing","label":"前翅","note":"前翅正面亮橙色带斜贯黑边，与反面判若两虫","anchor":"forewing","tone":"coral"},{"id":"dead-leaf-butterfly-tail","label":"尾突","note":"后翅末端延伸出细尖尾突，静止时形似枯叶叶柄","anchor":"tail","tone":"sage"},{"id":"dead-leaf-butterfly-antenna","label":"触角","note":"棒状触角细长，收拢停栖时紧贴前翅前缘","anchor":"antenna","tone":"lavender"},{"id":"dead-leaf-butterfly-eye","label":"复眼","note":"复眼较小，藏于枯叶拟态的轮廓阴影之中","anchor":"eye","tone":"coral"},{"id":"dead-leaf-butterfly-abdomen","label":"腹部","note":"腹部细长，停栖时收于合拢的双翅之间不外露","anchor":"abdomen","tone":"sage"}]', '枯叶蛱蝶成虫嗜食树液和腐果，很少访花传粉；停栖时收拢双翅露出酷似枯叶的反面，是热带亚热带林下最经典的伪装拟态案例之一。', '每只枯叶蛱蝶的「叶片」都不一样：叶脉的走向、霉斑的多少、甚至像被虫蛀出的透明小孔，个体之间各不相同，鸟类因此学不会一套通用的搜索图像。', 'complete', '["卵","幼虫","蛹","成虫（可越冬）"]', '中国南方、东南亚至南亚喜马拉雅山麓', '常见', '["黄豹盛蛱蝶","琉璃蛱蝶","大红蛱蝶"]', '#6e4e2e', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'dead-leaf-butterfly');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'hawk-moth', '小豆长喙天蛾', 'Macroglossum stellatarum', '白日悬停吸蜜的伪蜂鸟', 'lepidoptera', '小豆长喙天蛾体态短粗如纺锤，白天活动，能像蜂鸟一样原地悬停取蜜，振翅频率每秒可达70–90次。喙管极细长，展开时可超过自身体长，中国本无蜂鸟，它常被误认成这种鸟类。', '[{"key":"体长","value":"约20毫米，翼展约40–45毫米，体态短粗如纺锤","icon":"size"},{"key":"世代","value":"温暖地区一年可发生多代，成虫可长期活动","icon":"time"},{"key":"栖息环境","value":"花园、田野及林缘开花植物丰富处，白天活动","icon":"place"},{"key":"食性","value":"成虫悬停吸食多种管状花蜜，幼虫取食茜草科植物","icon":"food"},{"key":"悬停","value":"振翅频率每秒可达70–90次，能像蜂鸟一样原地悬停取蜜","icon":"ability"},{"key":"喙管","value":"喙极细长，展开时长度可超过自身体长，探入深花冠取蜜","icon":"ability"}]', '[{"id":"hawk-moth-proboscis","label":"喙管","note":"喙管细长可卷曲，展开时长度可超过自身体长","anchor":"proboscis","tone":"coral"},{"id":"hawk-moth-forewing","label":"前翅","note":"前翅狭长灰褐色，高速振动时几乎化作一团虚影","anchor":"forewing","tone":"sage"},{"id":"hawk-moth-hindwing","label":"后翅","note":"后翅橙黄色，悬停时藏于前翅下方不易看清","anchor":"hindwing","tone":"amber"},{"id":"hawk-moth-antenna","label":"触角","note":"棒状触角短粗，飞行悬停时保持相对稳定","anchor":"antenna","tone":"lavender"},{"id":"hawk-moth-eye","label":"复眼","note":"复眼硕大，白天活动对光线变化十分敏锐","anchor":"eye","tone":"coral"},{"id":"hawk-moth-abdomen","label":"腹部","note":"腹部末端具扇形鳞毛，形似鸟尾辅助悬停平衡","anchor":"abdomen","tone":"sage"}]', '小豆长喙天蛾白天活动访花传粉，是温带地区重要的传粉昆虫之一；其幼虫专食茜草科植物，是该类植物食叶昆虫群落的一员。', '它悬停时并非靠气流托着不动，而是用视觉紧盯花朵、每秒做出上百次微调；风把花吹得摇晃，喙尖仍能一直咬住花心不放。', 'complete', '["卵","幼虫","蛹","成虫"]', '中国大部分地区，广布于欧亚大陆及北非', '常见', '["白薯天蛾","芝麻鬼脸天蛾","构月天蛾"]', '#a3651f', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'hawk-moth');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'termite-soldier', '黑翅土白蚁（兵蚁）', 'Odontotermes formosanus', '生来失明的持钳守门人', 'blattodea', '兵蚁是白蚁群体里的守卫，头部硕大坚硬得几乎抵得上胸腹之和，一对镰刀状上颚只能夹断不能咀嚼。它天生无翅、没有复眼，终生看不见，也无法自己进食，全靠工蚁口对口喂养。', '[{"key":"体长","value":"兵蚁约5–6毫米，头部因发达上颚而显得格外硕大","icon":"size"},{"key":"寿命","value":"兵蚁可存活数月至一年，蚁后寿命可长达十余年","icon":"time"},{"key":"栖息环境","value":"地下或树基蚁巢，与工蚁、蚁后共同营巢群居","icon":"place"},{"key":"食性","value":"不能自行取食，完全依赖工蚁反哺喂食","icon":"food"},{"key":"防御","value":"上颚粗大有力，专职堵塞蚁道缺口抵御天敌入侵","icon":"ability"},{"key":"感官","value":"无翅也无复眼（近乎全盲），主要依靠触角感知振动与信息素","icon":"ability"}]', '[{"id":"termite-soldier-head","label":"头部","note":"头部硕大坚硬，容纳发达的上颚肌肉，是兵蚁的标志","anchor":"head","tone":"amber"},{"id":"termite-soldier-mandible","label":"上颚","note":"上颚粗壮如钳，专职堵塞蚁道缺口、抵御天敌入侵","anchor":"mandible","tone":"coral"},{"id":"termite-soldier-abdomen","label":"腹部","note":"腹部柔软苍白，藏于地下蚁巢中很少暴露在外","anchor":"abdomen","tone":"sage"},{"id":"termite-soldier-antenna","label":"触角","note":"念珠状触角是主要感官，兵蚁没有复眼，全靠它触碰与嗅闻辨路","anchor":"antenna","tone":"lavender"},{"id":"termite-soldier-thorax","label":"胸部","note":"胸部窄于头部，三对步足由此伸出","anchor":"thorax","tone":"amber"},{"id":"termite-soldier-leg","label":"足部","note":"步足短小，兵蚁活动范围通常不离蚁巢通道太远","anchor":"leg","tone":"sage"}]', '兵蚁在白蚁社会中专职防御，靠发达上颚堵截入侵蚁道的天敌，自身却完全无法取食，需要工蚁反哺喂养，是高度分工社会性昆虫的缩影。', '白蚁并不是蚂蚁：蚂蚁属膜翅目，白蚁其实是蜚蠊目、和蟑螂血缘更近，两者的社会性是各自独立演化出来的趋同结果，亲缘关系相当遥远。', 'incomplete', '["卵","若虫（多次蜕皮，分化职别）","成虫（工蚁/兵蚁/蚁后）"]', '中国长江流域及以南地区，东亚至东南亚', '常见，是中国南方重要的白蚁害虫之一，可危害堤坝和木构建筑', '["家白蚁","黑翅大白蚁","散白蚁"]', '#b98a4f', 4
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'termite-soldier');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'water-scavenger', '大黑水龟虫', 'Hydrophilus acuminatus', '自带气瓶的水下清道夫', 'coleoptera', '大黑水龟虫是体型最大的水生甲虫之一，黑亮的体背圆滑呈流线型。它与凶猛的龙虱形似而性情迥异：成虫以水草和腐败有机质为主食，游泳时左右足交替划水，像在水底不紧不慢地踱步。', '[{"key":"体长","value":"约32–40毫米，是中国体型最大的水生甲虫之一","icon":"size"},{"key":"生活史","value":"一年一代，成虫寿命长，可在水底越冬","icon":"time"},{"key":"栖息环境","value":"池塘、稻田及流速缓慢的静水水域","icon":"place"},{"key":"食性","value":"成虫以水草腐屑为主食，幼虫肉食性捕食螺类","icon":"food"},{"key":"储气","value":"腹面绒毛层锁住一层空气，入水时泛出银光","icon":"ability"},{"key":"产卵","value":"雌虫吐丝织卵袋系于浮水植物下，随水漂浮","icon":"ability"}]', '[{"id":"water-scavenger-palp","label":"下颚须","note":"比触角还长的感觉器，负责探路与嗅察食物","anchor":"palp","tone":"coral"},{"id":"water-scavenger-keel","label":"腹脊","note":"中胸腹面的尖锐纵脊，遇袭时可扎伤鱼的口腔","anchor":"keel","tone":"amber"},{"id":"water-scavenger-elytra","label":"鞘翅","note":"黑亮圆滑呈流线型，潜行时减小水的阻力","anchor":"elytra","tone":"sage"},{"id":"water-scavenger-eye","label":"复眼","note":"位于头部两侧，兼顾水面与水下两个世界的光影","anchor":"eye","tone":"lavender"},{"id":"water-scavenger-leg","label":"游泳足","note":"中后足缘生游泳毛，左右交替划水如水下踱步","anchor":"leg","tone":"coral"},{"id":"water-scavenger-antenna","label":"触角","note":"短棒状，探出水面把新鲜空气导入腹面储气层","anchor":"antenna","tone":"sage"}]', '成虫取食水草与腐屑，参与静水水域的有机质分解；肉食性的幼虫则捕食椎实螺等水生螺类，对一些寄生虫的中间宿主有抑制作用，是稻田湿地食物网的一环。', '它的下颚须比触角还长，接管了探路感知的差事；短棒状的真触角则专司呼吸——探出水面，把新鲜空气顺着绒毛导入腹面的储气层。', 'complete', '["卵（丝质卵袋）","幼虫（肉食，水生）","蛹（岸边土室）","成虫"]', '中国大部、日本、朝鲜半岛及俄罗斯远东的静水水域', '常见，稻田水域减少的地区种群有所下降', '["红足水龟虫","欧洲大水龟虫","黄缘龙虱"]', '#232a24', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'water-scavenger');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'checkered-beetle', '中华郭公虫', 'Trichodes sinae', '蜂巢里长大的花间猎手', 'coleoptera', '中华郭公虫是鞘翅上横贯红黑相间宽带的小型甲虫，全身覆有细密绒毛。成虫白天流连花间，既取食花粉也顺势捕捉访花的小昆虫，一身醒目的警戒色让鸟类不敢轻易下口。', '[{"key":"体长","value":"约10–18毫米，体型虽小却极易辨认","icon":"size"},{"key":"世代","value":"一年一代，以老熟幼虫在寄主巢内越冬","icon":"time"},{"key":"栖息环境","value":"草原与林缘的开花草地，成虫常伏于花序","icon":"place"},{"key":"食性","value":"成虫食花粉兼捕小虫，幼虫寄食蜂类幼虫","icon":"food"},{"key":"警戒","value":"体色属典型警戒色，捕食者尝过一次便长记性","icon":"ability"},{"key":"传粉","value":"带毛的身体在花序间穿行，顺带传授花粉","icon":"ability"}]', '[{"id":"checkered-beetle-band","label":"色带","note":"鞘翅上红黑相间的宽横带，是醒目的警戒信号","anchor":"band","tone":"coral"},{"id":"checkered-beetle-fuzz","label":"绒毛","note":"全身直立的细密绒毛，访花时沾满花粉粒","anchor":"fuzz","tone":"sage"},{"id":"checkered-beetle-elytra","label":"鞘翅","note":"质地比多数甲虫柔软，末端圆润盖住整个腹部","anchor":"elytra","tone":"amber"},{"id":"checkered-beetle-eye","label":"复眼","note":"颗粒状复眼，白天活动全靠它搜寻盛开的花序","anchor":"eye","tone":"lavender"},{"id":"checkered-beetle-pronotum","label":"前胸背板","note":"钟形向后收窄，与宽阔的鞘翅之间界线分明","anchor":"pronotum","tone":"coral"},{"id":"checkered-beetle-leg","label":"步足","note":"灵活有力，便于在花序间攀爬和追捕小虫","anchor":"leg","tone":"sage"}]', '成虫访花取食花粉并捕食访花小虫，客串传粉者；幼虫寄居蜂巢取食蜂类幼虫，对独居蜂种群构成压力，也因此牵动着草地传粉网络的平衡。', '它的幼虫会潜入独居蜂和蜜蜂的巢房，把蜂类幼虫当作口粮吃到化蛹，令养蜂人头疼；郭公虫科的不少成员却专捕树皮小蠹，是护林功臣。', 'complete', '["卵（产于花序或蜂巢附近）","幼虫（寄居蜂巢）","蛹","成虫"]', '中国华北、西北及东北草原地带，蒙古与西伯利亚', '常见', '["蜂郭公虫","蚁形郭公虫","赤颈郭公虫"]', '#a02c35', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'checkered-beetle');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'shining-chafer', '铜绿丽金龟', 'Anomala corpulenta', '披铜绿铠甲的夜行客', 'coleoptera', '铜绿丽金龟通体闪着铜绿色金属光泽，是华北平原果园里最常见的食叶甲虫之一。成虫昼伏夜出、趋光性强，入夜后群集啃食苹果、杨树和榆树的叶片；幼虫就是土里咬根的蛴螬，两个阶段分头为害。', '[{"key":"体长","value":"约19–21毫米，椭圆形，背面强烈隆起","icon":"size"},{"key":"世代","value":"一年一代，以幼虫在土中越冬","icon":"time"},{"key":"栖息环境","value":"果园、苗圃、行道树及农田林带","icon":"place"},{"key":"食性","value":"成虫啃食果树林木叶片，幼虫咬食作物根系","icon":"food"},{"key":"趋光","value":"夜行性且趋光强，果园常以灯诱集中扑杀","icon":"ability"},{"key":"假死","value":"受惊便收足坠地装死，片刻后再翻身爬走","icon":"ability"}]', '[{"id":"shining-chafer-elytra","label":"鞘翅","note":"铜绿色光泽随视角流转，源自表皮的微结构","anchor":"elytra","tone":"sage"},{"id":"shining-chafer-pronotum","label":"前胸背板","note":"同样闪着铜绿光泽，两侧镶着淡黄褐色的边","anchor":"pronotum","tone":"amber"},{"id":"shining-chafer-clypeus","label":"唇基","note":"头部前缘的横板如小铲，啃食叶片时抵住叶缘","anchor":"clypeus","tone":"coral"},{"id":"shining-chafer-antenna","label":"触角","note":"鳃叶状触角末端三片可开合，嗅寻食物与配偶","anchor":"antenna","tone":"lavender"},{"id":"shining-chafer-leg","label":"步足","note":"前足胫节带齿便于扒土，夜里抱住叶缘取食","anchor":"leg","tone":"sage"},{"id":"shining-chafer-eye","label":"复眼","note":"适应夜行的复眼，对灯光和月光方位很敏感","anchor":"eye","tone":"amber"}]', '成虫食叶、幼虫食根，在华北果区是全年防治的主要对象；它同时也是夜行食虫鸟类与刺猬的食物来源，灯光诱杀正是利用其趋光习性的防治手段。', '那身铜绿并不是色素染出来的，而是表皮多层微结构对光的干涉——换个角度看色调便随之偏移，制成标本多年也不易褪色。', 'complete', '["卵","幼虫（蛴螬，土中越冬）","蛹","成虫"]', '中国华北、东北、华东及华中等地，朝鲜半岛', '常见，华北果园重点防治的食叶害虫', '["大绿丽金龟","深绿丽金龟","日本丽金龟"]', '#4c8a3f', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'shining-chafer');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'assassin-bug', '猎蝽', 'Sycanus croceovittatus', '揣着注射器的刺客', 'hemiptera', '猎蝽是伏击型的捕食性蝽类，弯刀状的刺吸喙平时折收在头胸下方的纵沟里。捕猎时它悄然逼近，一喙刺入猎物体内注入含酶唾液，先把组织在体外消化成汁，再从容吸回，猎物往往顷刻瘫软。', '[{"key":"体长","value":"约16–24毫米，黑色身躯缀有橙黄色横带","icon":"size"},{"key":"世代","value":"一年1–2代，以若虫或卵越冬","icon":"time"},{"key":"栖息环境","value":"灌丛、林缘及农田植被的枝叶间","icon":"place"},{"key":"食性","value":"捕食鳞翅目幼虫等昆虫，刺吸其体液","icon":"food"},{"key":"体外消化","value":"唾液含强力消化酶，先化猎物为汁再吸回","icon":"ability"},{"key":"产卵","value":"卵成束直立黏在叶面，如一排小瓶整齐排列","icon":"ability"}]', '[{"id":"assassin-bug-rostrum","label":"刺吸喙","note":"三节弯刀状，不用时折收进前胸腹面的纵沟","anchor":"rostrum","tone":"coral"},{"id":"assassin-bug-foreleg","label":"前足","note":"胫节生黏性毛垫，按住挣扎的猎物不松手","anchor":"foreleg","tone":"amber"},{"id":"assassin-bug-pronotum","label":"前胸背板","note":"前后两叶分明，后叶常带刺突或鲜明色斑","anchor":"pronotum","tone":"sage"},{"id":"assassin-bug-eye","label":"复眼","note":"球形外突，伏击前靠它锁定猎物的一举一动","anchor":"eye","tone":"lavender"},{"id":"assassin-bug-antenna","label":"触角","note":"细长分四节，轻点猎物试探大小与死活","anchor":"antenna","tone":"coral"},{"id":"assassin-bug-abdomen","label":"腹部","note":"侧接缘上翘外露，缀有警戒意味的斑纹","anchor":"abdomen","tone":"sage"}]', '猎蝽捕食毛虫、蝽象等多种昆虫，在果园与农田是有价值的捕食性天敌，东南亚一些地区已尝试人工繁育猎蝽释放于田间进行生物防治。', '有些猎蝽的若虫会把蚂蚁尸体和沙粒背在背上当「移动掩体」；成虫遇险时还会用喙尖在胸前的脊纹沟里摩擦，发出吱吱示警声。', 'incomplete', '["卵（成束直立）","若虫（5龄）","成虫"]', '中国南方及西南，中南半岛等热带亚热带地区', '常见', '["黑光猎蝽","中黑土猎蝽","锥蝽"]', '#7a3b28', 2
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'assassin-bug');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'bumblebee', '熊蜂', 'Bombus lucorum', '会发抖取暖的传粉员', 'hymenoptera', '熊蜂是圆滚滚、周身密披绒毛的社会性蜂类，飞行时嗡声低沉。它能靠飞行肌剧烈颤动给自己预热，在蜜蜂尚不能出勤的低温清晨与早春率先出巢访花，是高山草甸和北方春天最可靠的传粉者。', '[{"key":"体长","value":"蜂后约19–22毫米，工蜂约11–17毫米","icon":"size"},{"key":"世代","value":"蜂群一年一季，秋末仅新蜂后交配后独自越冬","icon":"time"},{"key":"栖息环境","value":"温带草地与高山草甸，常筑巢于废弃鼠洞","icon":"place"},{"key":"食性","value":"采食花蜜花粉，短舌种类会咬穿花冠底部盗蜜","icon":"food"},{"key":"产热","value":"起飞前靠飞行肌颤抖把胸温升到30摄氏度上下","icon":"ability"},{"key":"振翅","value":"每秒振翅约130–240次，嗡声因此格外低沉","icon":"ability"}]', '[{"id":"bumblebee-fuzz","label":"绒毛","note":"浓密如外套，保温之余还靠静电吸附花粉","anchor":"fuzz","tone":"coral"},{"id":"bumblebee-pollenBasket","label":"花粉篮","note":"后足胫节的光滑凹面，四周长毛围成货舱","anchor":"pollenBasket","tone":"amber"},{"id":"bumblebee-wing","label":"翅膀","note":"飞行肌不直接拉翅，靠胸腔高频形变间接驱动","anchor":"wing","tone":"sage"},{"id":"bumblebee-eye","label":"复眼","note":"能感知紫外与偏振光，阴天也能辨认方向","anchor":"eye","tone":"lavender"},{"id":"bumblebee-antenna","label":"触角","note":"嗅得出上一位访客留在花瓣上的气味标记","anchor":"antenna","tone":"coral"},{"id":"bumblebee-abdomen","label":"腹部","note":"末端螫针无倒钩，可反复使用但轻易不出手","anchor":"abdomen","tone":"sage"}]', '熊蜂耐低温、访花勤且忠于同种花，是高山草甸与温带早春的关键传粉者；多国已将其商业繁育供温室授粉，野生种群则受农药与草地退化威胁。', '番茄的花药像撒盐瓶，得靠高频振动才肯放粉：熊蜂咬住花药把胸腔振到特定频率「摇」出花粉，这门声波授粉的手艺蜜蜂学不会，温室因此专门租用熊蜂。', 'complete', '["卵","幼虫","蛹（茧）","成虫（蜂后/工蜂/雄蜂）"]', '欧洲至西伯利亚，中国北方山地及东北草甸', '常见，部分近缘种区域性衰退', '["地熊蜂","火红熊蜂","西方蜜蜂"]', '#caa43a', 2
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'bumblebee');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'cricket', '迷卡斗蟋', 'Velarifictorus micado', '秋夜擂台上的鸣琴斗士', 'orthoptera', '迷卡斗蟋俗称蛐蛐，体色黑褐、头圆牙利，是中国斗蟋民俗的主角。雄虫界域性极强，两雄相遇便张牙振翅一决高下；入夜后它举起前翅相互摩擦，奏出清亮的鸣声，既宣示领地也招引雌虫。', '[{"key":"体长","value":"约13–18毫米，体色黑褐有光泽","icon":"size"},{"key":"世代","value":"一年一代，以卵在土中越冬","icon":"time"},{"key":"栖息环境","value":"田野、墙根石缝与庭院草丛，善掘穴而居","icon":"place"},{"key":"食性","value":"杂食性，取食嫩叶、种子兼食小型昆虫","icon":"food"},{"key":"鸣声","value":"节奏随气温升降变快变慢，可当作粗略的温度计","icon":"ability"},{"key":"胜负","value":"获胜雄虫会奏起特有的凯歌，落败者数日避战","icon":"ability"}]', '[{"id":"cricket-stridulator","label":"音锉","note":"前翅腹面的锯齿列，与刮器相刮便发出乐声","anchor":"stridulator","tone":"coral"},{"id":"cricket-cercus","label":"尾须","note":"一对后伸的感毛，察觉背后袭来的气流异动","anchor":"cercus","tone":"amber"},{"id":"cricket-hindleg","label":"后足","note":"腿节粗壮善弹跳，搏斗时也用来蹬踹对手","anchor":"hindleg","tone":"sage"},{"id":"cricket-antenna","label":"触角","note":"丝状长过身体，黑暗洞穴里代替眼睛探路","anchor":"antenna","tone":"lavender"},{"id":"cricket-eye","label":"复眼","note":"半球形视野开阔，白天多躲在洞穴里少露面","anchor":"eye","tone":"coral"},{"id":"cricket-head","label":"头部","note":"圆大坚硬，两雄对决时正面相抵如同顶牛","anchor":"head","tone":"sage"}]', '迷卡斗蟋在田间取食杂草嫩叶与作物幼苗，偶成小害，同时也是蛙类、鸟类与蜘蛛的常见猎物；秋季的鸣声与斗性使它成为东亚玩赏昆虫文化的核心物种。', '发声时它右翅在上，以右翅音锉刮左翅刮器——螽斯恰好相反惯用左翅在上；听琴的「耳朵」也不长在头上，而是一对生在前足胫节上的鼓膜。', 'incomplete', '["卵（土中越冬）","若虫（多龄）","成虫（秋鸣）"]', '中国大部、日本及朝鲜半岛，已随货物引入北美', '常见，斗蟋之戏自唐代延续至今', '["油葫芦","中华树蟋","大扁头蟋"]', '#4a3626', 3
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'cricket');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'robber-fly', '食虫虻', 'Promachus yesonicus', '敢劫蜻蜓的空中大盗', 'diptera', '食虫虻是双翅目里最凶悍的空中猎手，浑身刚毛、复眼硕大，喜停在向阳的枝头守望。有飞虫掠过便腾空而起，从侧后方拦截，用多刺的长足一把抱住，连蜂类与蜻蜓这样的狠角色也敢下手。', '[{"key":"体长","value":"约20–28毫米，浑身覆灰黄色刚毛","icon":"size"},{"key":"生活史","value":"幼虫在土中生活1–2年，成虫多见于盛夏","icon":"time"},{"key":"栖息环境","value":"林缘、草地与河滩等开阔向阳地带","icon":"place"},{"key":"食性","value":"成虫空中捕食各类飞虫，幼虫捕食地下蛴螬","icon":"food"},{"key":"突袭","value":"自栖枝腾空拦截，得手后携猎物返枝吸食","icon":"ability"},{"key":"毒液","value":"唾液含神经毒素与消化酶，大猎物也迅速失抗","icon":"ability"}]', '[{"id":"robber-fly-mystax","label":"髭毛","note":"脸部浓密的硬鬃，捕猎时护住复眼与口器","anchor":"mystax","tone":"coral"},{"id":"robber-fly-foreleg","label":"前足","note":"粗壮多刺，空中合抱猎物时如同一把铁钳","anchor":"foreleg","tone":"amber"},{"id":"robber-fly-wing","label":"翅膀","note":"一对狭长膜翅，短距离冲刺的爆发力惊人","anchor":"wing","tone":"sage"},{"id":"robber-fly-eye","label":"复眼","note":"两眼间凹陷成谷，利于测距锁定移动目标","anchor":"eye","tone":"lavender"},{"id":"robber-fly-haltere","label":"平衡棒","note":"由后翅退化而来，急转弯时充当陀螺仪","anchor":"haltere","tone":"coral"},{"id":"robber-fly-abdomen","label":"腹部","note":"狭长分节，雄虫末端具抱器，雌虫具产卵器","anchor":"abdomen","tone":"sage"}]', '成虫不挑猎物，蜂、蝇、甲虫乃至蜻蜓都在食谱上，对昆虫群落施加顶层捕食压力；幼虫在土壤中捕食蛴螬等地下害虫，一生两个阶段都在「控虫」。', '它脸上那撮浓密的「胡子」不是装饰：到手的猎物常拼死挣扎，这丛硬鬃便像击剑面罩，替它挡开乱蹬的足和试图反咬的口器。', 'complete', '["卵（产于土面或植物上）","幼虫（土中，1–2年）","蛹","成虫"]', '中国东北至华北，日本及朝鲜半岛', '常见', '["中华盗虻","姬蜂虻","长足虻"]', '#6e6a55', 2
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'robber-fly');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'crane-fly', '大蚊', 'Tipula sp.', '被错当蚊子的长腿隐士', 'diptera', '大蚊像一只放大数倍的蚊子，六条细腿长得夸张，飞行摇摇晃晃，夏夜常出现在灯下与墙角。它其实与吸血无缘——口器柔弱得连皮肤都刺不破，多数成虫几乎不进食，短短一生只为交配产卵。', '[{"key":"体长","value":"体长15–35毫米，足展开可超过60毫米","icon":"size"},{"key":"寿命","value":"成虫约10–15天，幼虫期长达数月","icon":"time"},{"key":"栖息环境","value":"潮湿草地、林下及水边，成虫喜阴凉处","icon":"place"},{"key":"食性","value":"成虫至多舔水吸蜜，幼虫食腐殖质与草根","icon":"food"},{"key":"飞行","value":"振翅缓慢、悬垂着长腿，姿态摇晃似踩高跷","icon":"ability"},{"key":"危害","value":"幼虫俗称「皮夹克」，在欧美是草坪害根虫","icon":"ability"}]', '[{"id":"crane-fly-haltere","label":"平衡棒","note":"一对小锤肉眼清晰可见，是双翅目的活教材","anchor":"haltere","tone":"coral"},{"id":"crane-fly-leg","label":"长足","note":"六足细长而易断，特定关节遇险时自动脱落","anchor":"leg","tone":"amber"},{"id":"crane-fly-wing","label":"翅膀","note":"狭长的一对膜翅，振幅小，飞起来慢悠悠","anchor":"wing","tone":"sage"},{"id":"crane-fly-thorax","label":"胸部","note":"背面有一道V形横缝，是大蚊科的鉴定标志","anchor":"thorax","tone":"lavender"},{"id":"crane-fly-abdomen","label":"腹部","note":"雌虫末端尖如针，常被误当毒刺，实为产卵器","anchor":"abdomen","tone":"coral"},{"id":"crane-fly-antenna","label":"触角","note":"丝状而短小，在夸张的长腿旁毫不起眼","anchor":"antenna","tone":"sage"}]', '大蚊幼虫在土壤与落叶层中取食腐殖质和植物根，既是分解者也是草坪偶发的害根者；成虫与幼虫都是鸟类、蛙类和蝙蝠的重要食物，撑起湿地食物网的底层。', '那些长腿是它的「弃子」：被蛛网黏住或遭鸟喙啄中时，腿会在特定关节应声脱落，弃腿保命——成虫不再蜕皮，断掉的腿就再也补不回来。', 'complete', '["卵","幼虫（土中或水中）","蛹","成虫（约两周）"]', '世界性分布，中国各地湿润生境都常见', '常见，是最常被误认成「巨蚊」的无害昆虫', '["沼大蚊","冬大蚊","摇蚊"]', '#8c8468', 2
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'crane-fly');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'mantidfly', '螳蛉', 'Mantispa sp.', '长着螳臂的草蛉近亲', 'neuroptera', '螳蛉乍看像一只小螳螂：前足同样特化成带刺的捕捉足，前胸同样拉长如颈。但它两对翅上密布网状细脉，实为脉翅目成员，与草蛉的血缘远比与螳螂近，这身「螳螂装」是趋同演化的杰作。', '[{"key":"体长","value":"约10–20毫米，体态纤细如小蜂","icon":"size"},{"key":"生活史","value":"多为一年1–2代，幼虫发育随寄主而定","icon":"time"},{"key":"栖息环境","value":"林缘灌丛与草地，夜晚常飞向灯光","icon":"place"},{"key":"食性","value":"成虫捕食小型飞虫，幼虫多取食蜘蛛卵","icon":"food"},{"key":"捕捉足","value":"前足胫节与腿节可对折扣合，出击如螳臂","icon":"ability"},{"key":"复变态","value":"幼虫先活跃善爬，寄生后转为蛆状，属复变态","icon":"ability"}]', '[{"id":"mantidfly-raptorialLeg","label":"捕捉足","note":"镰刀状前足与螳螂如出一辙，却是各自演化","anchor":"raptorialLeg","tone":"coral"},{"id":"mantidfly-wing","label":"翅膀","note":"膜质透明布满网状细脉，泄露脉翅目的身份","anchor":"wing","tone":"sage"},{"id":"mantidfly-pronotum","label":"前胸","note":"拉长如颈，让捕捉足获得更大的挥击范围","anchor":"pronotum","tone":"amber"},{"id":"mantidfly-eye","label":"复眼","note":"半球形泛金属光泽，白日伏击全凭敏锐视觉","anchor":"eye","tone":"lavender"},{"id":"mantidfly-antenna","label":"触角","note":"短念珠状，与螳螂细长的丝状触角迥然不同","anchor":"antenna","tone":"coral"},{"id":"mantidfly-abdomen","label":"腹部","note":"不少种类具黄黑斑纹，拟态胡蜂吓退天敌","anchor":"abdomen","tone":"sage"}]', '成虫在花丛与灯下捕食蚜虫、蛾蚋等小虫；幼虫钻食蜘蛛卵囊，能抑制部分蜘蛛种群。这类「以蛛卵为食」的生活史在昆虫中相当罕见，也使其数量天然稀少。', '不少螳蛉的初龄幼虫会伏在路过的雌蛛身上「搭便车」，随之潜入卵囊，把蜘蛛卵一颗颗吸空后才化蛹羽化，一生成败全押在这次搭乘上。', 'complete', '["卵（具短柄成片）","幼虫（复变态）","蛹（茧内）","成虫"]', '中国南北各地，全球温暖地区广布', '不常见，种群密度天然较低', '["草蛉","蚁蛉","褐蛉"]', '#b8862d', 3
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'mantidfly');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'caddisfly', '石蛾', 'Limnephilus sp.', '用沙粒盖房的水中石匠', 'trichoptera', '石蛾外形似一只灰褐色的小蛾，翅面覆盖细毛而非鳞片，「毛翅目」之名正由此而来。幼虫俗称石蚕，栖息在溪流里，多数种类吐丝把沙粒枯枝缀成一节可背着走的巢筒，藏身其中缓缓爬行。', '[{"key":"体长","value":"翅展约40–55毫米（角石蛾类偏大型）","icon":"size"},{"key":"生活史","value":"一年1–2代，以幼虫越冬，成虫寿命数周","icon":"time"},{"key":"栖息环境","value":"幼虫栖于清凉溪流的石下，成虫傍晚溪畔群飞","icon":"place"},{"key":"食性","value":"幼虫滤食藻类与碎屑，成虫口器退化仅能舔水","icon":"food"},{"key":"筑巢","value":"幼虫吐丝黏缀沙石成筒巢，也有类群张网滤食","icon":"ability"},{"key":"化蛹","value":"老熟幼虫封住巢口在水下化蛹，羽化时破水而出","icon":"ability"}]', '[{"id":"caddisfly-hairyWing","label":"毛翅","note":"翅面密被细毛，停栖时合拢成屋脊状盖住背部","anchor":"hairyWing","tone":"coral"},{"id":"caddisfly-antenna","label":"触角","note":"丝状与体等长或更长，停栖时向前平伸并拢","anchor":"antenna","tone":"amber"},{"id":"caddisfly-palp","label":"下颚须","note":"分节屈曲如小鞭，是石蛾分科鉴定的重要依据","anchor":"palp","tone":"sage"},{"id":"caddisfly-eye","label":"复眼","note":"黄昏起飞，弱光下仍能引导溪畔的婚飞群舞","anchor":"eye","tone":"lavender"},{"id":"caddisfly-thorax","label":"胸部","note":"背面隆起生有毛瘤，着生两对翅与细长的足","anchor":"thorax","tone":"coral"},{"id":"caddisfly-abdomen","label":"腹部","note":"柔软细长，雌虫产卵时常贴着水面反复点水","anchor":"abdomen","tone":"sage"}]', '石蛾幼虫在溪流里滤食碎屑与藻类，把上游冲下的有机质重新纳入食物链，本身又是鱼类的主食之一，钓鱼人仿制的「毛钩」多半模仿的就是它。', '石蚕对水里的溶氧和污染十分挑剔，环保部门给溪流「体检」时，会清点水底石蛾、蜉蝣与石蝇三类幼虫的数量，合称EPT指数，数字越高水越干净。', 'complete', '["卵（产于水面或水边）","幼虫（石蚕，水生）","蛹（水下茧室）","成虫"]', '中国各地山地溪流，东亚温带广布', '常见，水质恶化的河段会迅速消失', '["长角石蛾","沼石蛾","纹石蛾"]', '#75634a', 5
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'caddisfly');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'house-fly', '家蝇', 'Musca domestica', '倒走天花板的不速之客', 'diptera', '家蝇是与人类同居最久的昆虫之一，胸背四条深色纵纹是它的认种标志。它的口器不叮不咬，只会像海绵一样把食物泡软再吸走；足端的爪垫让它能在玻璃与天花板上如履平地。', '[{"key":"体长","value":"体长6–7毫米，灰黑色，胸背具4条深色纵纹","icon":"size"},{"key":"寿命","value":"成虫约15–30天，温暖季节两周即可完成一代","icon":"time"},{"key":"栖息环境","value":"人居环境为主，垃圾、粪肥与厨余附近","icon":"place"},{"key":"食性","value":"舐吸式取食，先吐消化液把食物泡软再吸回","icon":"food"},{"key":"飞行","value":"振翅约200次每秒，受惊后瞬间弹射起飞","icon":"ability"},{"key":"卫生","value":"体表与消化道可携带多种病原体，是重要卫生害虫","icon":"ability"}]', '[{"id":"house-fly-stripe","label":"胸背纵纹","note":"灰底上4条前后贯通的深色纵纹，是家蝇的认种标志","anchor":"stripe","tone":"coral"},{"id":"house-fly-eye","label":"复眼","note":"红褐色大复眼几乎包住头两侧，雌虫两眼间距明显","anchor":"eye","tone":"sage"},{"id":"house-fly-proboscis","label":"舐吸式口器","note":"短粗下垂的喙，端部一对海绵状唇瓣，只能舔吸不能叮咬","anchor":"proboscis","tone":"amber"},{"id":"house-fly-antenna","label":"芒状触角","note":"触角短小，第三节上生一根细长芒毛，是蝇类的共同标志","anchor":"antenna","tone":"lavender"},{"id":"house-fly-wing","label":"翅","note":"仅一对膜翅，停歇时向后收拢平覆在腹背上","anchor":"wing","tone":"sage"},{"id":"house-fly-pulvillus","label":"爪垫","note":"足端的黏性肉垫，靠分泌的黏液倒挂在光滑天花板上","anchor":"pulvillus","tone":"coral"}]', '家蝇幼虫（蛆）能高速把粪肥与腐烂有机物转化为自身蛋白质，是腐屑食物链里高效的分解者；但成虫在垃圾与餐桌之间往返，也把病原体一路捎带，对人类而言防治意义远大于生态贡献。', '家蝇落脚后总用前足互相搓洗——那不是讲卫生，而是在擦拭足上的味觉感受器：它尝味道用的是脚而不是嘴，落点尝一口，合适才伸喙。', 'complete', '["卵","幼虫（蛆）","蛹","成虫"]', '随人类分布于全世界，两极之外几乎无处不在', '极常见，全球性卫生害虫', '["市蝇","厩腐蝇","丝光绿蝇"]', '#7a7168', 2
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'house-fly');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'mosquito', '淡色库蚊', 'Culex pipiens pallens', '耳边嗡鸣的夜行刺客', 'diptera', '淡色库蚊是中国北方最常见的「家蚊」，夜里在耳边嗡嗡作响的多半是它。只有雌蚊才吸血——为了让卵成熟；细长的刺吸喙、腹节基部的淡色横带和停下时与墙面平行的体态，是认出它的三把钥匙。', '[{"key":"体长","value":"体长4–5毫米，通体淡褐、细长纤弱","icon":"size"},{"key":"活动","value":"黄昏与夜间活动，白天躲在阴暗角落","icon":"time"},{"key":"栖息环境","value":"滞水中产卵：下水道、积水容器、雨水井","icon":"place"},{"key":"食性","value":"雌蚊吸血促卵成熟，雄蚊只取食花蜜与植物汁液","icon":"food"},{"key":"飞行","value":"振翅约500次每秒，恼人的嗡鸣正是振翅声","icon":"ability"},{"key":"医学","value":"可传播流行性乙型脑炎与丝虫病，是重要医学昆虫","icon":"ability"}]', '[{"id":"mosquito-proboscis","label":"刺吸喙","note":"细长针管与头胸之和等长，雌蚊用它刺入皮肤吸血","anchor":"proboscis","tone":"coral"},{"id":"mosquito-antenna","label":"轮毛触角","note":"雌蚊触角只有稀疏轮毛，雄蚊蓬松如羽，一眼分性别","anchor":"antenna","tone":"sage"},{"id":"mosquito-band","label":"腹部淡色横带","note":"每个腹节基部一圈浅色横带，「淡色」库蚊因此得名","anchor":"band","tone":"amber"},{"id":"mosquito-wing","label":"狭长翅","note":"一对窄翅停歇时平覆在腹背上，翅脉上覆有细鳞","anchor":"wing","tone":"lavender"},{"id":"mosquito-leg","label":"细长足","note":"六足细长如丝，落在皮肤上几乎没有触感","anchor":"leg","tone":"sage"},{"id":"mosquito-haltere","label":"平衡棒","note":"后翅退化成的小棒，飞行时高速振动感知姿态","anchor":"haltere","tone":"coral"}]', '库蚊幼虫（孑孓）滤食滞水中的微生物与有机碎屑，同时是鱼类和蜻蜓稚虫的重要饵料；成蚊则供养着蝙蝠、燕子与蜘蛛——吸血其实只占它生活史里很小的一环。', '锁定你的不是眼睛而是化学仪器：库蚊靠触角上的受体追踪人呼出的二氧化碳与皮肤气味，隔着十几米就能定位，汗味重的人确实更招蚊子。', 'complete', '["卵（结成卵筏）","幼虫（孑孓）","蛹","成虫"]', '中国北方广布，东亚温带地区常见', '极常见，夏秋居室内外的主要蚊种', '["致倦库蚊","白纹伊蚊","中华按蚊"]', '#a08a5f', 2
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'mosquito');

INSERT INTO edu_insect (insect_code, insect_name, insect_latin, insect_epithet, insect_order, insect_summary, insect_facts, insect_hotspots, insect_ecology, insect_trivia, insect_metamorphosis, insect_lifecycle, insect_range, insect_status, insect_relatives, insect_accent, insect_difficulty)
SELECT 'cockroach', '德国小蠊', 'Blattella germanica', '厨房缝隙里的夜行房客', 'blattodea', '德国小蠊是室内最常见的蟑螂，茶褐色的扁平身体只有指甲盖大小，前胸背板上两条深色纵纹是它的认种标志。它昼伏夜出，钻缝本领极强，一对超过体长的丝状触角替它在黑暗中探路。', '[{"key":"体长","value":"体长12–15毫米，是室内常见蟑螂中最小的","icon":"size"},{"key":"寿命","value":"成虫可活100–200天，室内温暖处全年繁殖","icon":"time"},{"key":"栖息环境","value":"厨房与管道缝隙，喜温暖潮湿的黑暗处","icon":"place"},{"key":"食性","value":"杂食，从食物残渣到纸张浆糊都能下咽","icon":"food"},{"key":"奔跑","value":"每秒可跑出体长20倍以上的距离，贴墙急转不减速","icon":"ability"},{"key":"繁殖","value":"雌虫把卵鞘拖带在腹端直到临孵化，一鞘30–40粒卵","icon":"ability"}]', '[{"id":"cockroach-stripe","label":"前胸双纹","note":"前胸背板上两条平行的深色纵条纹，是德国小蠊的认种特征","anchor":"stripe","tone":"coral"},{"id":"cockroach-head","label":"头","note":"大部分藏在前胸背板下方，从背面只能看到头顶一线","anchor":"head","tone":"sage"},{"id":"cockroach-antenna","label":"丝状触角","note":"比身体还长的触角不停摆动，靠触觉与气味在黑暗里导航","anchor":"antenna","tone":"amber"},{"id":"cockroach-wing","label":"前翅","note":"革质前翅盖住整个腹部，虽有完整翅膀却几乎不飞","anchor":"wing","tone":"lavender"},{"id":"cockroach-cercus","label":"尾须","note":"腹末一对分节尾须，感知背后的气流，预警天敌偷袭","anchor":"cercus","tone":"coral"},{"id":"cockroach-leg","label":"多刺的足","note":"足上密生硬刺，既防滑又护身，是疾走的本钱","anchor":"leg","tone":"sage"}]', '蜚蠊家族在野外本是落叶层的分解者，德国小蠊却已完全转入人居，以残渣与垃圾为生。它边吃边排泄的习性会污染食物、传播肠道致病菌，蜕皮与分泌物还是室内重要的过敏原之一。', '德国小蠊其实不来自德国——老家多半在东南亚，随航运搬进了全世界的厨房；如今它几乎丧失野外生存能力，离开有人供暖的建筑，冬天就活不下去。', 'incomplete', '["卵（卵鞘）","若虫","成虫"]', '随人类遍布全球，室内环境全年可见', '极常见，头号室内蟑螂', '["美洲大蠊","黑胸大蠊","日本大蠊"]', '#a9752f', 4
WHERE NOT EXISTS (SELECT 1 FROM edu_insect WHERE insect_code = 'cockroach');

-- ============================================================
-- 昆虫深度讲解与测验（insect-world 开源项目 guides.zh.ts，63 个物种）
-- 说明：guide_lesson 为分步讲解 JSON 数组，每步可带 anchor，
--       anchor 对应该物种 edu_insect.insect_hotspots 里的部位，
--       用于「读到哪里、镜头对到哪里」；guide_quiz 为单选题 JSON 数组。
--       每物种固定 4 步讲解 + 2 道单选题，anchor 已在生成时校验合法。
-- ============================================================

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'rhinoceros-beetle', '[{"title":"头角怎么打斗","body":"雄虫头顶那支分叉长角不是刺杀武器，而是格斗时的杠杆：把角尖探入对方腹下用力上挑，将其整个掀离树干摔下，胜负常在几秒内分出。","anchor":"horn"},{"title":"胸角的配合","body":"头角负责挑起，短小的胸角则从下方顶住对手身体，与头角对握形成钳状发力点，让整套动作更稳更有力，减少中途脱手的机会。","anchor":"thoraxHorn"},{"title":"触角怎么找树液","body":"鳃叶状触角末端由数片薄片组成，平时收拢，靠近气味源时像扇子一样张开，成倍扩大接收气味分子的面积，循着发酵树液的气味夜间飞抵栎树。","anchor":"antenna"},{"title":"短暂的成虫期","body":"从卵到成虫要经过八到十个月的幼虫期，在腐殖土里默默取食朽木碎屑；等真正顶角活动的成虫阶段，往往只剩一到两个月，角只是谢幕前的装备。"}]', '角斗的一击', '两只雄虫头角相抵，各自寻找机会将角尖探入对方腹下；一旦得手便猛力上挑，把对手连同六足一起掀离树皮抛出，整个动作往往不到一秒，落败一方多半知难而退。', '[{"question":"双叉犀金龟雄虫头上的分叉长角主要用来做什么？","options":["刺穿对手的鞘翅将其杀死","作为杠杆挑起并掀翻对手","在飞行时充当方向舵"],"answer":1,"explain":"犀金龟打斗靠杠杆式格斗，用角尖挑起对手摔下树干分胜负，角不是刺杀武器，也不参与飞行操控。"},{"question":"关于双叉犀金龟的一生，下列哪种说法正确？","options":["顶着角活动的成虫期只有一两个月，幼虫期反而长得多","成虫和幼虫寿命大致相当，都在数月左右","幼虫期很短，多数时间是长着角的成虫阶段"],"answer":0,"explain":"它的幼虫期长达八到十个月，在腐殖土中默默取食；等长出犄角的成虫阶段，往往只剩一到两个月寿命。"}]', '腐殖土与树液', '白天蛰伏于腐叶堆积成的腐殖土或粗糙树皮缝隙中，借潮湿阴暗躲避鸟类等天敌；入夜后循气味飞往栎树等阔叶树干，与锹甲、蛾类共享同一处树液伤口，是林地夜间取食活动的常客。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'rhinoceros-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'monarch-butterfly', '[{"title":"橙黑色是警告","body":"前翅橙底黑脉并非纯粹好看，而是明确的警戒色：幼虫期从马利筋叶片里积蓄的毒素会保留到成虫体内，鸟类啄食一次尝到苦味后，多半会记住这身配色并从此回避。","anchor":"forewing"},{"title":"怎么认出雌雄","body":"后翅内侧各有一枚黑色小斑，是雄虫特有的性标，由特化鳞片构成、释放求偶信息素；雌虫翅脉相对更粗更暗，没有这对斑点，是野外分辨雌雄最直接的标志。","anchor":"hindwing"},{"title":"没去过却认得路","body":"秋天南迁的这一代蝴蝶从未去过墨西哥，却能精确抵达祖辈越冬的同一片冷杉林；触角里的生物钟与复眼感知的太阳角度共同校准方向，阴天时还能切换到感知地磁场的备用系统。","anchor":"antenna"},{"title":"喙管怎么喝蜜","body":"喙管平时卷曲如钟表发条，落在花上时才伸直探入花冠深处吸食花蜜；这套细长构造是鳞翅目昆虫共同演化出的取食方案，替代了祖先原本的咀嚼式口器。","anchor":"proboscis"}]', '太阳罗盘导航', '迁徙蝴蝶飞行时持续追踪太阳方位，把方向信息和体内生物钟计算出的时间校正后换算成固定的地理航向；连续赶路数周、太阳角度逐日变化，它们仍能维持大致南向，最终收拢进墨西哥同一片山谷冷杉林。', '[{"question":"帝王蝶翅膀鲜艳的橙黑配色，最主要的作用是什么？","options":["吸引同类远距离识别群体","警告天敌体内积蓄的毒素","帮助在长途飞行中调节体温"],"answer":1,"explain":"幼虫取食马利筋积蓄的毒素会保留到成虫体内，橙黑配色是提醒鸟类天敌其滋味不佳、吃了会中毒的警戒色。"},{"question":"完成秋季南迁、抵达墨西哥越冬地的帝王蝶，依靠的是什么？","options":["跟随迁徙经验丰富的年长个体带路","体内的太阳罗盘与生物钟，阴天可切换地磁感知","沿途散发的信息素气味链"],"answer":1,"explain":"南迁的这一代此前从未去过墨西哥，全靠太阳角度与体内生物钟校准方向；阴天见不到太阳时，还能切换到感知地磁场的备用系统。"}]', '从马利筋到冷杉林', '繁殖季分散在北美开阔草地与田野间，幼虫离不开有毒的马利筋——这是它们唯一的食草；入秋后成虫却会离开这片零散栖地，集群飞往墨西哥中部固定的几处高山冷杉林越冬，把分散的种群收拢到极小的范围内。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'monarch-butterfly');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'honeybee', '[{"title":"摆尾舞怎么定位","body":"采到蜜源的工蜂回巢后，在垂直巢脾上跑成「8」字形反复摆动腹部，靠胸部飞行肌振动产生的声脉冲传递节奏；舞蹈的方向对应太阳方位偏角，持续时长则换算成蜜源的距离远近。","anchor":"thorax"},{"title":"花粉怎么带回家","body":"后足胫节外侧凹陷成小篮状构造，工蜂用前中足把身上沾附的花粉刷拢压实，一路飞行中越滚越紧成一个花粉团，卡在花粉篮里带回蜂巢喂养幼虫。","anchor":"pollenBasket"},{"title":"螫针为什么会致命","body":"工蜂螫针带有倒钩，刺入哺乳动物较厚的皮肤后无法抽回，挣扎逃离时连同毒腺和部分内脏一起被扯断留在原处，蜇人后不久便会死亡；蜂后的螫针光滑无倒钩，可反复使用。","anchor":"stinger"},{"title":"复眼怎么找花","body":"复眼能感知人眼看不见的紫外光，许多花瓣上藏着仅在紫外波段才显现的深色导蜜纹，像跑道灯一样引导蜜蜂径直飞向花蕊深处的蜜腺。","anchor":"eye"}]', '抖动产热过冬', '气温骤降时，工蜂并不冬眠，而是紧密抱团把蜂后护在中心，让胸部飞行肌在翅膀不动的情况下持续收缩产热；外层蜂轮流与内层交换位置，整团靠这种集体产热把核心温度维持在约35摄氏度。', '[{"question":"蜜蜂摆尾舞的舞蹈时长主要在传递什么信息？","options":["蜜源花朵的种类","蜜源与蜂巢之间的距离","当天的天气状况"],"answer":1,"explain":"摆尾舞里，舞蹈的方向对应太阳方位偏角，指出蜜源的方向；摆动持续的时长则对应蜜源与蜂巢的距离远近。"},{"question":"关于蜜蜂螫针，下列说法正确的是？","options":["工蜂和蜂后的螫针都带倒钩，蜇一次都会死","只有工蜂螫针带倒钩，蜇人后因内脏撕裂而死；蜂后螫针光滑可反复使用","蜜蜂的螫针其实不带毒，只有物理刺痛"],"answer":1,"explain":"工蜂螫针带倒钩，刺入后拔不出、挣扎时会扯断内脏而死；蜂后螫针光滑无倒钩，可以反复蜇刺而不致命。"}]', '巢里的恒温社会', '野生蜂群多筑巢于中空树干或岩缝，家养蜂群栖身人工蜂箱，巢内终年维持在约35摄氏度、从不冬眠；工蜂平均出巢采集半径可达两三公里，一路访花授粉，让蜂群健康与周边植被的开花状况紧密相连。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'honeybee');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'dragonfly', '[{"title":"复眼怎么看世界","body":"两只复眼几乎占满头部，由近三万个小眼拼接而成，视野接近360度，几乎没有盲区；每个小眼独立成像，对移动的光影格外敏感，这是空中拦截猎物的第一道感知优势。","anchor":"eye"},{"title":"两对翅怎么独立飞","body":"前后两对翅由各自独立的肌肉控制，可以不同步扇动：既能像直升机一样原地悬停，也能瞬间急转弯或倒退飞行，这种飞控精度在昆虫中并不多见。","anchor":"forewing"},{"title":"腿怎么变成猎物篮","body":"六足密生成排刚毛，飞行时向前收拢成一个漏斗状的「篮子」，蚊蝇等小型飞虫一头撞进足篮就难以脱身，蜻蜓不必用嘴主动追咬，靠腿就能拦下猎物。","anchor":"leg"},{"title":"从水底到空中","body":"稚虫阶段叫水虿，在水中蛰伏数月至一年，是塘底凶猛的伏击者；沿水草爬出水面完成最后一次蜕皮后，才换上双翅与复眼，从水下猎手变成空中猎手。"}]', '空中拦截猎物', '蜻蜓追猎时不会死盯着猎物本身，其视觉神经元会持续计算对方的飞行速度与方向，提前预判出拦截点，再调整航线径直扑向那个尚未到达的位置；这种拦截而非追逐的策略，让捕猎成功率能达到九成以上。', '[{"question":"蜻蜓能保持极高捕猎成功率，最关键的原因是什么？","options":["复眼视野接近360度不留死角","神经元预判猎物轨迹、提前拦截而非追逐","飞行速度远超所有猎物"],"answer":1,"explain":"蜻蜓的视觉神经元会计算猎物的飞行轨迹并预判拦截点，主动飞向猎物即将到达的位置，而不是被动追逐，这才是高命中率的核心。"},{"question":"关于碧伟蜓的发育过程，下列说法正确的是？","options":["要经过卵、幼虫、蛹、成虫四个阶段，属于完全变态","水虿直接蜕皮变为成虫，没有蛹期，属于不完全变态","水虿在陆地上发育，成虫才下水生活"],"answer":1,"explain":"蜻蜓是不完全变态昆虫，稚虫（水虿）在水中经多次蜕皮后直接羽化为成虫，中间没有蛹这一阶段。"}]', '巡弋领地的猎手', '成年雄虫常年占据一小段水岸空域来回巡逻，驱赶闯入的同类雄性，只为守住雌虫产卵所需的水域；稚虫则蛰伏在水底淤泥或水草间伏击猎物，同一片池塘因此被两个完全不同形态的自己分别占据。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'dragonfly');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'mantis', '[{"title":"捕捉足怎么锁猎物","body":"前足特化成两排内侧带尖刺的镰刀状结构，静止时收拢贴在胸前不易察觉；一旦猎物靠近有效距离，前足会在极短时间内弹出合拢，尖刺相互交错卡住猎物躯体，令其完全无法挣脱。","anchor":"raptorialLeg"},{"title":"前胸怎么帮它出手","body":"细长的前胸能像伸缩杆一样突然前探，把捕捉足送到猎物意想不到的距离外再出击，这段可伸缩的预备动作是螳螂命中率高的重要原因，也是猎物总来不及躲闪的关键。","anchor":"prothorax"},{"title":"头能转多远","body":"头部可转动接近180度，几乎不用挪动身体就能环顾周围动静，这在昆虫中相当少见；配合复眼中随视角移动的暗色「伪瞳孔」——其实只是光学错觉，并非真的眼球在转动。","anchor":"head"},{"title":"伪装的绿色刀客","body":"体色多为绿色或褐色，静伏在灌木或草丛间与植物融为一体，靠伪装而非速度取胜；它不主动追逐猎物，而是耐心等待对方靠近有效攻击范围，是典型的伏击型捕食者。"}]', '一击定胜负', '螳螂静伏不动等猎物进入有效范围后，前胸先突然前伸缩短距离，捕捉足几乎同时甩出合拢，从启动到锁死猎物往往不到零点一秒，人眼只能看到模糊的一晃；动作一旦发起便不能收回，因此它宁可多等，也很少贸然出击落空。', '[{"question":"中华大刀螳捕猎时，前足合拢的主要作用是什么？","options":["注入毒液麻痹猎物","用列刺锁住猎物令其无法挣脱","像剪刀一样直接切断猎物"],"answer":1,"explain":"螳螂前足特化成带列刺的捕捉足，出击后靠尖刺交错锁住猎物身体使其无法挣脱，它并无毒液，也不是靠切断猎物取胜。"},{"question":"关于中华大刀螳在北美的情况，下列哪项属实？","options":["因不适应当地气候，引入后很快消失","作为害虫天敌引入后广泛归化，野外已有伏击蜂鸟的记录","只能在温室人工环境下存活，未能在野外定居"],"answer":1,"explain":"中华大刀螳1896年作为害虫天敌引入北美后迅速扩散归化，北美已有多起野外记录显示它偶尔会伏击并捕食体型远大于自己的蜂鸟。"}]', '灌丛里的伏击点', '偏好栖身在灌木丛、高草或农田边缘等枝叶交错的环境，绿褐相间的体色能与背景融为一体；这类环境既方便它隐蔽等待猎物，也让传粉昆虫、蝗虫等猎物频繁经过，是天然的伏击选址。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'mantis');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'ladybird', '[{"title":"红黑配色的警告","body":"鞘翅上鲜红打底、缀着黑色圆斑，这种高对比配色在自然界通常代表「别吃我」；鸟类等天敌一旦尝过瓢虫的苦味，往往会牢牢记住这身红黑相间的样子并从此绕开。","anchor":"elytra"},{"title":"受惊时怎么防身","body":"真正的防御手段藏在腿关节里——受到惊扰时，瓢虫会从足的关节缝隙渗出一滴黄色血淋巴，气味苦涩、略带刺激性，这才是红黑配色背后对应的化学武器，颜色只是提前打的广告。","anchor":"leg"},{"title":"黑斑怎么数种类","body":"七星瓢虫因鞘翅上七枚黑斑得名，但同属近缘种的斑点数量和排列各不相同，从两点到二十多点都有，斑纹样式是野外快速区分瓢虫种类的重要依据。","anchor":"spot"},{"title":"一生都在吃蚜虫","body":"从幼虫到成虫，七星瓢虫几乎只以蚜虫为食，一只幼虫在发育期间能吃掉数百只蚜虫，这种旺盛的食量让它成为农田和果园里备受倚重的天然除虫工。"}]', '装死避险反射', '受到触碰或天敌逼近时，瓢虫常先猛地缩起六足和头部、从枝叶上直接坠落，短暂一动不动装死；若骚扰持续，腿关节才会渗出苦味血淋巴，这套先躲、再化学防御的两级反应能省下宝贵的体力。', '[{"question":"七星瓢虫红底黑斑的体色，最主要的作用是什么？","options":["帮助在花丛间伪装","警示天敌自己滋味不佳、体内有苦味物质","吸引同类聚集越冬"],"answer":1,"explain":"红黑相间是警戒色，对应它受惊时从腿关节分泌的苦味血淋巴；天敌记住这身配色后会主动回避，而不是用来伪装或单纯吸引同类。"},{"question":"关于瓢虫鞘翅上的黑斑，下列说法正确的是？","options":["所有瓢虫的黑斑数量都严格等于「七星」，即七个","黑斑的数量和排列因种类而异，是区分不同瓢虫种类的依据之一","黑斑数量会随年龄增长逐年增多"],"answer":1,"explain":"只有七星瓢虫恰好是七个黑斑，其近缘种斑点数量和排列各不相同，因此斑纹样式常被用作区分瓢虫种类的参考依据。"}]', '蚜虫聚集的地方', '哪里蚜虫密集，七星瓢虫就聚往哪里，农田、菜园、林缘的新梢嫩叶是它们最常出没的场所；入秋后成虫又会离开分散的取食地，成群迁往石缝、落叶堆抱团越冬，来年春天再分散寻找新的蚜虫聚落。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'ladybird');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'ant', '[{"title":"腰为什么这么细","body":"胸部与腹部之间收缩成一节细小的腹柄节，像一段灵活的关节，让沉重的后腹部能上下左右灵活摆动、对准目标弯曲蜇刺；这处细腰是蚂蚁区别于多数其他昆虫的标志性结构。","anchor":"petiole"},{"title":"上颚能做什么","body":"大型工蚁的上颚粗壮如钳，平时用来切割食物、搬运沙土碎屑，遇到入侵者时也是主要的防御武器；同一巢穴里体型较小的工蚁上颚相对纤细，更多承担采集与照料幼虫的工作。","anchor":"mandible"},{"title":"蜜露怎么带回家","body":"后腹部内有一个可膨胀的嗉囊，专门用来暂存采到的花蜜或蚜虫分泌的蜜露；工蚁把嗉囊装满带回巢穴后，会与同伴口对口分享，靠这种「社会胃」把食物迅速分配给全巢成员。","anchor":"gaster"},{"title":"分工靠的不是眼睛","body":"蚂蚁的视力普遍有限，蚁群协作主要依赖触角接触与信息素气味：一只工蚁发现食物后会在归巢路上留下气味痕迹，后续同伴循着气味就能找到同一条路线，无需直接看见彼此。"}]', '放牧蚜虫取蜜露', '弓背蚁会用触角有节奏地拍打蚜虫腹部，刺激其排出一滴富含糖分的蜜露并立刻舔食；作为回报，蚂蚁会驱赶瓢虫等蚜虫天敌，甚至把蚜虫群搬到嫩芽更多的枝条上，这种「饲养」关系能持续整个蚜虫世代。', '[{"question":"蚂蚁胸部和腹部之间那处明显收细的「腰」，主要作用是什么？","options":["减轻体重方便飞行","让沉重的后腹部能灵活转向弯曲","是专门用来呼吸的气孔集中处"],"answer":1,"explain":"这处细腰叫腹柄节，像一个灵活关节，让后腹部能大幅度摆动转向，便于精准蜇刺或喷酸，并不是为了减重或呼吸。"},{"question":"关于日本弓背蚁的寿命，下列说法正确的是？","options":["蚁后寿命可达十余年，多数工蚁活不过两年","工蚁和蚁后寿命都在一两年左右，相差不大","工蚁寿命比蚁后更长，因为蚁后产卵消耗大"],"answer":0,"explain":"蚁后完成婚飞、脱翅建巢后终生不再离巢，专事产卵，寿命可达十余年；工蚁在外辛劳搬运觅食，寿命通常只有几个月到两年。"}]', '地下的分室巢穴', '巢穴多建在土壤深处、石块下方或朽木内部，内部分隔成育幼室、储粮室等多个功能空间，彼此以通道相连；蚁后深居最核心的房间专事产卵，工蚁按分工进出巢穴，构成一座层次分明的地下城邦。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'ant');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'cicada', '[{"title":"鸣声怎么发出来","body":"雄虫腹部两侧各有一片薄而有褶皱的发音膜，靠内部肌肉每秒收缩数百次使其快速鼓凸又弹回，发出连续的「咔哒」声；单只鸣声就能测得90分贝以上，是昆虫界数一数二的音量。","anchor":"tymbal"},{"title":"腹部怎么当喇叭","body":"雄虫腹部内部近乎中空，构造类似乐器的共鸣箱，能把发音膜产生的原始声音成倍放大、让音色更响亮悠长；雌虫没有这套发音结构，腹部相对实心，因此不会鸣叫。","anchor":"abdomen"},{"title":"口器怎么吸树汁","body":"口器特化成细长如针的刺吸式结构，平时收折在头部下方，取食时刺入树木木质部的导管吸取汁液，既不撕咬也不咀嚼叶片，这点常被误解为「啃树叶」，其实完全不同。","anchor":"rostrum"},{"title":"地下多年只为几周","body":"若虫要在地下靠吸食树根汁液生活三到五年，历经多次蜕皮才发育成熟；出土后爬上树干完成最后一次蜕壳、长出双翅，而这段能鸣叫、能飞翔的成虫时光却只剩短短数周。"}]', '夜间羽化蜕壳', '老熟若虫多在傍晚出土，沿树干向上爬到合适高度后用足抓牢树皮，背部表皮从中线裂开，乳白色的成虫缓缓从旧壳中抽出身体、展开皱缩的双翅；整个过程持续一两个小时，翅膀变硬前它几乎无法防御。', '[{"question":"黑蚱蝉的口器主要用来做什么？","options":["咀嚼撕咬树叶获取养分","刺入树木木质部吸食汁液","捕食其他小型昆虫"],"answer":1,"explain":"蝉的口器特化成细长的刺吸式结构，插入树干木质部导管吸食汁液，既不咀嚼叶片，也不捕食其他昆虫，常被误以为是啃树叶的害虫。"},{"question":"黑蚱蝉是否像北美「十七年蝉」那样多年一遇集中爆发？","options":["是，它也要等固定年数才集中出现一次","不是，若虫地下年限不同步，几乎每年夏天都能见到","不是，它其实每年只在地下生活几周"],"answer":1,"explain":"黑蚱蝉若虫在地下要生活三到五年，但各个体年限并不整齐同步，因此种群里每年都有个体成熟出土，并非多年一遇的周期蝉。"}]', '树根与树干之间', '若虫阶段蛰居在树木根系周围的土壤中，靠吸食根部汁液缓慢发育；成虫则转移到树干与枝叶间活动，白天取食鸣叫求偶，二者虽是同一物种的不同阶段，却分别占据着地下和树冠两个几乎不重叠的空间。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'cicada');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'locust', '[{"title":"后足怎么跳这么远","body":"后足腿节格外粗壮，内部装满发达的跳跃肌肉，起跳前先像压紧弹簧一样蓄力，瞬间释放能把自己弹射到体长十余倍外的地方，这是蝗虫躲避天敌最直接的第一反应。","anchor":"hindleg"},{"title":"拥挤会怎么变身","body":"触角和后足反复被同类碰触，会刺激体内血清素水平持续升高；种群一旦拥挤到临界密度，蝗虫就会从散居型转变为体色鲜艳、爱结群的群居型，这并非换了基因，只是拥挤触发的生理变化。","anchor":"antenna"},{"title":"翅怎么撑住长途迁飞","body":"群居型个体的翅明显长过身体，飞行肌也更发达耐久，能支撑蝗群持续飞行数百公里而不落地；散居型翅相对短小，更依赖跳跃而非长途飞行来移动。","anchor":"wing"},{"title":"怎么听懂同伴的声音","body":"腹部第一节两侧各有一片薄膜状的鼓膜听器，能接收同类翅膀摩擦发出的声音信号；这套「耳朵」长在腹部而非头部附近，是直翅目中蝗虫科的共同特征。","anchor":"tympanum"}]', '蝗群随风迁飞', '群居型蝗虫结群升空后，个体并不各自乱飞，而是持续靠翅摩擦声与彼此的移动方向保持队形，顺着盛行风向节省体力飞行；蝗群可以借助上升气流爬升到数百米高空，一天之内转移数十公里。', '[{"question":"东亚飞蝗从散居型变成群居型，最直接的触发原因是什么？","options":["种群中出现了新的基因突变","个体间高密度下反复触碰引发血清素水平变化","气温骤降导致体色改变"],"answer":1,"explain":"散居型和群居型是同一套基因的不同表现：密度骤增后个体反复触碰会让血清素升高，进而改变体色和行为，并非基因突变。"},{"question":"东亚飞蝗的听觉器官（鼓膜听器）长在身体的什么位置？","options":["前足胫节上","腹部第一节两侧","触角基部"],"answer":1,"explain":"蝗虫的鼓膜听器位于腹部第一节两侧；这一点与螽斯、蟋蟀等听器长在前足胫节的直翅目近亲不同，是区分它们的一个特征。"}]', '草滩与农田之间', '散居型分散栖息在河滩、荒草地等取食禾本科杂草的环境里，与环境融为一体、不易察觉；一旦植被枯萎、种群被迫挤进有限的绿洲地带引发群居化，蝗群便会转向周边农田取食作物，闯入人类耕作的核心地带。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'locust');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'firefly', '[{"title":"冷光怎么发出来","body":"腹部末端的发光器内，荧光素在荧光素酶催化下与氧气反应释放能量，几乎全部以光的形式发出、极少转化为热，因此摸起来不会发烫；这种冷光的能量转化效率接近九成，远高于白炽灯不到一成的水平。","anchor":"lantern"},{"title":"闪光节奏是暗号","body":"不同种类萤火虫的闪光频率、持续时长和间隔各不相同，如同各自的专属密码；雄虫复眼格外发达，边飞边扫视黑暗草丛，专门用来分辨并锁定同种雌虫发出的那一套节奏。","anchor":"eye"},{"title":"触角怎么找同伴","body":"锯齿状或丝状的触角除了辅助辨别闪光节奏，还能感知空气中极微量的信息素分子；在灯语之外，气味是萤火虫寻找配偶的第二重保险，对翅退化不能远飞的雌虫尤其重要。","anchor":"antenna"},{"title":"幼虫也会发光","body":"发光能力从幼虫期就已具备，白天蛰伏在落叶与苔藓间，夜里外出捕食蜗牛与蛞蝓；多数研究认为，幼虫体表的微光更可能是警告天敌「我不好吃」的信号，与成虫用来求偶的灯语并不是一回事。"}]', '灯语一问一答', '雄虫在草丛上方低飞，按本种固定的节奏连续闪光；伏在草叶上的雌虫若识别出同种信号，就在雄虫每次闪光后间隔一小段固定时间回闪一次作为应答，雄虫循着回应的光点逐步靠近，完成定位与配对。', '[{"question":"萤火虫发光器发出的「冷光」，与白炽灯相比有什么特点？","options":["把化学能转化成光的效率远高于白炽灯，几乎不发热","发光原理和白炽灯相同，只是灯光更弱","需要先吸收阳光储存能量，夜间再释放"],"answer":0,"explain":"萤火虫发光器靠荧光素酶催化反应发光，能量转化效率接近九成、几乎不产生热量，远高于白炽灯不到一成的效率。"},{"question":"同一片草地上有多种萤火虫时，雌虫如何分辨闪光是不是本种的雄虫发出的？","options":["靠闪光的颜色，每个物种发光颜色都不同","靠闪光的频率与节奏，不同种类各有一套专属「灯语」","靠雄虫靠近时翅膀振动的声音"],"answer":1,"explain":"不同种类萤火虫的闪光频率、持续时长与间隔各不相同，如同专属密码；雌虫正是依靠这套节奏差异而非颜色或声音来识别同种配偶。"}]', '潮湿草叶间的夜巡', '幼虫和成虫都离不开潮湿环境，白天栖身溪流附近林地的落叶层或苔藓下保持体表湿润；夜幕降临后成虫才飞离草丛升空活动，一旦栖息地被过度硬化、排干或被路灯常年照亮，就很难再维持这套只在暗夜运作的生活方式。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'firefly');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'longhorn-beetle', '[{"title":"触角有多长","body":"黑白相间的鞭状触角一节接一节，雄虫触角长度可达体长近两倍，是它区别于多数甲虫最醒目的特征；触角表面密布感受器，既能嗅出寄主树种的气味，也能感知同类求偶信息素。","anchor":"antenna"},{"title":"上颚怎么用","body":"粗壮有力的上颚是多用途工具：成虫用它啃食嫩枝树皮补充营养，雌虫产卵前还会用上颚在树皮上咬出一道刻槽，把卵产在槽里再用分泌物封住，帮初孵幼虫顺利钻入树干。","anchor":"mandible"},{"title":"白斑鞘翅认得出吗","body":"黑色鞘翅上散布着不规则的白色斑点，斑纹形状和分布因个体而略有不同，很难找到完全相同的两只；这身醒目的黑白配色让它在树干上其实并不难被发现，与许多靠拟态隐身的甲虫策略正相反。","anchor":"elytra"},{"title":"幼虫在树里过冬","body":"卵孵化后，幼虫从树皮刻槽钻入木质部，在树干和根部内部蛀出隧道取食一到两年，期间几乎终年不出树干；正是这种深藏树体内部的习性，让它很难被早期发现，也让防治格外困难。"}]', '钻蛀树干的隧道', '初孵幼虫从产卵刻槽咬穿树皮进入木质部，靠强壮的上颚一点点啃咬前进，边吃边把蛀屑和粪便推出蛀道排出体外；隧道会顺着木纹蜿蜒延伸数十厘米，边取食边成长，直到一到两年后在蛀道尽头化蛹。', '[{"question":"星天牛雌虫在产卵前，为什么要先用上颚在树皮上咬出一道刻槽？","options":["刻槽用来标记领地驱赶其他雌虫","把卵产在刻槽内再封住，方便孵化后的幼虫钻入木质部","刻槽渗出的树液是雌虫自己的食物来源"],"answer":1,"explain":"雌虫咬刻槽是为产卵做准备，卵产在槽内并用分泌物封住保护，孵化后幼虫便能就近钻入树干蛀食。"},{"question":"星天牛原产东亚，为什么却在欧洲多国建立了野外种群？","options":["它本身具备远距离跨海飞行能力","随苗木和木质包装材料被人类贸易无意携带传播","候鸟迁徙时把幼虫带到了欧洲"],"answer":1,"explain":"星天牛幼虫深藏在树干木质部内不易被察觉，常随苗木或木质包装材料被人类贸易无意间携带到欧美，并非依靠自身飞行能力跨海扩散。"}]', '藏身树干的一生', '幼虫阶段深居活树的木质部内部，从树干基部到主根都可能是蛀道所在，几乎不见天日；成虫羽化后才咬破树皮爬出，转而活动在阔叶林、果园和城市行道树的枝叶间啃食树皮补充营养，寻找配偶。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'longhorn-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'stick-insect', '[{"title":"身体怎么像树枝","body":"细长的体躯分节明显，体色和纹理随蜕皮逐渐贴近所栖植物的颜色与粗细，静止不动时几乎与真实枝条难以区分；这种拟态不靠一次性伪装完成，而是随着每次蜕皮不断微调贴合环境。","anchor":"body"},{"title":"被咬住的腿怎么办","body":"六足细长如分叉的枝条，一旦被天敌钳住，竹节虫能主动在特定关节处自行断离逃生，牺牲一条腿保全性命；尚未成熟的若虫经过之后几次蜕皮，断肢往往能部分再生，成虫后便不再具备这种能力。","anchor":"leg"},{"title":"风一吹它也跟着摇","body":"光是保持静止还不够，微风吹动真实枝叶时会有轻微摆动；竹节虫受到惊扰或有风吹过时，会主动轻轻摇晃身体模仿这种自然摆动，用行为而非单纯的外形来强化伪装效果。","anchor":"camouflage"},{"title":"头小眼睛也不发达","body":"相比拟态精细的身体，头部显得又小又不起眼，复眼也不发达，视觉在它的感官里并不占主导；探路和寻找食物主要靠触角感知气味与触觉，这与许多依赖视觉捕食求偶的昆虫形成鲜明对比。","anchor":"head"}]', '卵伪装成种子', '雌虫产下的卵在形状和气味上模拟植物种子，卵壳一端还带着蚂蚁喜爱的营养附属物；蚂蚁把「种子」搬运进蚁巢，取食附属物后把卵壳留在废物堆里，卵在蚁巢庇护下安全孵化，若虫再从蚁巢中爬出。', '[{"question":"竹节虫在静止拟态之外，为什么还会在受惊或有风时轻轻摇晃身体？","options":["摇晃是求偶行为，用来吸引异性注意","模仿风吹枝叶的自然摆动，让静态伪装更逼真","摇晃能帮助消化刚吃下的叶片"],"answer":1,"explain":"单纯静止在有风时反而不自然，竹节虫主动模仿枝叶被风吹动的轻微摆动，用行为强化伪装效果，与求偶或消化无关。"},{"question":"一些竹节虫的卵为什么会被蚂蚁搬进蚁巢？","options":["蚂蚁把卵误认成同伴的幼虫加以照顾","卵在外形和气味上模拟种子，还带有蚂蚁喜欢的营养附属物","竹节虫成虫会主动把卵放进蚁巢寻求保护"],"answer":1,"explain":"这类竹节虫的卵外形、气味都模拟种子，还带营养附属物吸引蚂蚁；蚂蚁当种子搬进巢取食附属物后，卵便在巢内获得庇护直至孵化。"}]', '枝叶深处的隐身', '白天多一动不动地隐伏在灌木、竹丛或阔叶林茂密的枝叶间，靠拟态融入环境几乎不被发现；直到夜幕降临才开始活动、爬向枝头取食叶片，天亮前又退回原处，昼伏夜出的节律进一步降低了被天敌发现的概率。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'stick-insect');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'swallowtail', '[{"title":"白带是雄虫招牌","body":"后翅横贯一道玉带状白斑，雄虫的这道斑纹稳定不随个体变化，是野外辨认雄虫最直接的标志；雌虫翅纹则要多变得多，同一物种内能演化出好几种截然不同的花色。","anchor":"hindwing"},{"title":"尾突骗过鸟嘴","body":"后翅末端延伸出细长尾突，配上周围斑纹组成一副「假触角」，天敌扑击时容易误把翅缘当成头部咬下；凤蝶只损失一角翅膀就能脱身逃生，比真被咬中头部划算得多。","anchor":"tail"},{"title":"雌虫为什么装成毒蝶","body":"部分雌虫前翅斑纹会拟态剧毒的红珠凤蝶，这种自己无毒却模仿有毒近亲的策略称为贝氏拟态；鸟类一旦吃过真正的红珠凤蝶记住教训，连带会放过外形相似的玉带凤蝶雌虫。","anchor":"forewing"},{"title":"同窝也能长出不同花色","body":"玉带凤蝶雌虫的拟态型由一小段基因整体决定，同一窝孵化的雌性后代里，有时会同时出现拟态型和非拟态型，是研究昆虫拟态遗传机制的经典材料。"}]', '前足试探寄主', '雌虫产卵前会用前足反复轻叩叶片表面，跗节上的化学感受器能「尝」出汁液是否来自柑橘、花椒等对味的芸香科植物；确认无误后才弯曲腹部把卵产在叶背，这道核对工序决定了幼虫孵化后有没有合适的食物。', '[{"question":"玉带凤蝶部分雌虫翅纹拟态剧毒的红珠凤蝶，这种拟态属于什么类型？","options":["自身也有毒，双方互相提醒天敌","自身无毒，靠模仿有毒近亲让天敌误判","只是巧合，两个物种并无演化关联"],"answer":1,"explain":"这属于贝氏拟态：玉带凤蝶雌虫本身无毒，靠模仿剧毒的红珠凤蝶外观，让吃过苦头的天敌误以为它也危险而放过它。"},{"question":"玉带凤蝶后翅的尾突和周围斑纹组合，在防御天敌时起什么作用？","options":["模拟假触角和假头，把攻击引向翅缘而非要害","储存飞行时用的额外脂肪","帮助在快速转弯时保持空气动力平衡"],"answer":0,"explain":"尾突配斑纹组成「假头」，容易让天敌误咬翅缘；凤蝶损失一角翅膀仍能飞行逃生，比头部真被咬中划算得多。"}]', '果园与城市庭园', '幼虫离不开柑橘、花椒等芸香科植物，因此城市庭园、行道树和柑橘果园都能见到它的身影，适应人类居住环境的能力较强；成虫访花范围更广，庭园花圃与果树花期都是常见的取食地点。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'swallowtail');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'silk-moth', '[{"title":"眼斑吓退天敌","body":"前后翅各有一枚透明眼斑，平时被翅面遮住不起眼，一旦被鸟类等天敌触碰惊扰，蛾体猛地展翅让眼斑骤然显露，形似两只瞪大的眼睛，足以让偷袭者一瞬迟疑、错失下手时机。","anchor":"eyespot"},{"title":"羽状触角怎么找对象","body":"雄蛾触角呈宽大的栉齿状，表面密布嗅觉感受器，能在夜间捕捉到数百米外雌蛾释放的性信息素分子；这套感官比它的复眼灵敏得多，是黑暗中寻找配偶的主要依据。","anchor":"antenna"},{"title":"飞行前先热身","body":"胸部密生绒毛包裹着发达的飞行肌，起飞前需要先靠肌肉自身的振动摩擦升温，让胸腔温度达到一定阈值后才能真正振翅起飞，这一点在体形硕大的蛾类中相当常见。","anchor":"thorax"},{"title":"成虫这一生不吃饭","body":"从蛹中羽化出来，柞蚕蛾的口器已经退化到几乎失去取食功能，终生不再进食；短短一周左右的成虫生命里，全部力气都只用来完成交配和产卵这一件事。"}]', '结茧封存的准备', '老熟幼虫在化蛹前会寻找合适的枝叶，用丝腺吐出的丝线把自己一层层裹住，先固定丝盘再以「8」字形来回摆动头部编织茧壁；茧丝连绵不断可达千米以上，正是这层坚韧的外壳，让蛹能安然度过变态期。', '[{"question":"柞蚕蛾成虫为什么羽化后终生不再进食？","options":["口器已经退化，失去了取食能力","周围食物不足，被迫节食","取食会影响释放性信息素的效果"],"answer":0,"explain":"柞蚕蛾成虫口器已经退化，不具备正常取食能力，一生只靠幼虫期积累的养分维持交配和产卵，因此寿命通常只有一周左右。"},{"question":"柞蚕蛾前后翅上的透明眼斑，主要功能是什么？","options":["帮助在夜间发光吸引配偶","受惊时突然显露，威慑或迟滞天敌的攻击","调节体温，吸收阳光热量"],"answer":1,"explain":"眼斑平时被翅面遮住，受惊展翅时骤然显露形似瞪大的眼睛，能让偷袭的天敌一瞬迟疑，为逃跑争取时间，并非用于发光或调温。"}]', '柞树林里的两代人', '幼虫整个发育期都伏在柞树等壳斗科植物的枝叶上取食，很少远离寄主树；结茧化蛹后附着于枝条，经历整个冬天等待来年羽化，成虫寿命虽短，仍要飞回同一片柞树林寻找配偶、把下一代交给这片树林。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'silk-moth');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'hornet', '[{"title":"上颚是猎杀利器","body":"粗大的镰刀状上颚是它猎杀蜜蜂的主要武器，攻入蜂巢时能在极短时间内咬断一只只工蜂的头胸部，数十只金环胡蜂协同突袭，几个小时便可能让一整窝蜜蜂全军覆没。","anchor":"mandible"},{"title":"螫针能反复用","body":"螫针表面光滑没有倒钩，蜇刺后可以直接抽出再次使用，不像蜜蜂螫针那样一蜇即亡；单次注入的毒液量也远超普通胡蜂，是它对付大型天敌和人类时最危险的武器。","anchor":"sting"},{"title":"细腰撑起空中缠斗","body":"连接胸部与腹部的细长腹柄让身体能灵活弯折转向，配合发达飞行肌，使它在追击猎物或与蜂巢守卫周旋时能快速变向、贴身缠斗，飞行时速可达约40公里。","anchor":"waist"},{"title":"一年只活一季","body":"蜂群不像蜜蜂那样常年延续，而是由受精蜂后独自越冬后于春季重新建巢；蜂群随夏秋壮大，入冬前新一代蜂后交配后各自散去越冬，旧巢连同大部分成员当年便走向终结。"}]', '蜂球烫死入侵者', '日本蜜蜂发现金环胡蜂侦查蜂后，数百只工蜂会一拥而上将其团团裹住，在蜂球中心剧烈振翅产热；核心温度可逼近45摄氏度，高于胡蜂耐受上限却仍在蜜蜂承受范围内，足以把入侵者活活闷热而死。', '[{"question":"金环胡蜂的螫针与蜜蜂螫针相比，最大的不同是什么？","options":["胡蜂螫针不含毒液，只是物理刺痛","胡蜂螫针光滑无倒钩，蜇刺后能抽出反复使用","胡蜂根本没有螫针，只靠上颚攻击"],"answer":1,"explain":"金环胡蜂螫针光滑不带倒钩，蜇刺后可直接抽出反复使用，且毒液量更大；蜜蜂螫针带倒钩，蜇一次就会因内脏撕裂而死。"},{"question":"面对金环胡蜂的侦查突袭，日本蜜蜂如何自卫？","options":["集体飞离蜂巢躲避，放弃巢穴","工蜂一拥而上把入侵者团团围住振翅产热，闷热致死","靠螫针一次性围攻蜇死入侵者"],"answer":1,"explain":"日本蜜蜂会用数百只工蜂裹住入侵的胡蜂并剧烈振翅产热，蜂球核心温度逼近45摄氏度，足以热死胡蜂又不会伤到蜜蜂自身。"}]', '地下与树洞的巢穴', '蜂巢多筑于低山丘陵林地的地下空腔或树洞内，位置隐蔽不易被发现；工蜂活动范围可延伸到蜂巢周边数公里，既捕食蜂类等昆虫补充蛋白质，也取食树液和熟透落果获取糖分，是森林与果园之间都会出没的强势捕食者。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'hornet');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'tiger-beetle', '[{"title":"上颚剪刀式捕猎","body":"一对镰刀状上颚左右交叉如剪刀，静止时错位闭合，扑向猎物的瞬间猛然合拢，能在一眨眼间夹碎蚂蚁等小型猎物的外骨骼，配上极快的奔跑速度，让它几乎是林间小径上的顶级掠食者。","anchor":"mandible"},{"title":"跑太快反而看不清","body":"硕大的复眼是锁定猎物的主要依据，但奔跑速度太快时，复眼来不及连续处理快速变化的影像，会短暂陷入「视觉失明」；因此虎甲常表现出冲刺几步、骤然停下、片刻后再冲刺的间歇式追击节奏。","anchor":"eye"},{"title":"腿撑起昆虫短跑纪录","body":"细长的三对步足支撑着极快的奔跑速度，若换算成相对体长的移动倍数，虎甲在昆虫里名列前茅；这份速度既用来追猎，也用来在天敌逼近时迅速脱离险境。","anchor":"leg"},{"title":"幼虫在地道里守株待兔","body":"幼虫阶段并不四处奔跑，而是躲在自己挖掘的垂直洞穴里，用腹部背面的倒钩把身体固定住，头部堵在洞口伪装成地面的一部分，一旦有小虫经过便猛然探身捕获，与成虫的奔跑型捕食策略截然不同。"}]', '洞口伏击的一钩', '幼虫头部和前胸嵌合成一个略高于地面的塞子，昼夜堵在垂直洞口守候；猎物一靠近，它便以背部倒钩为支点猛然弹身而出，用上颚钳住对方，再借助倒钩的反作用力把猎物拖拽入洞取食，整个突袭往往不到一秒完成。', '[{"question":"中华虎甲奔跑时为什么会频繁冲刺几步就突然停下？","options":["体力消耗太快需要频繁休息","奔跑速度超出复眼处理连续影像的能力，短暂看不清前方","停下是在等待猎物自己靠近"],"answer":1,"explain":"虎甲奔跑太快时，复眼来不及处理连续影像会短暂「视觉失明」，因此要停下重新看清猎物位置，并非体力不支或守株待兔。"},{"question":"关于中华虎甲幼虫的捕食方式，下列说法正确的是？","options":["和成虫一样在地面快速奔跑追击猎物","蛰伏在垂直洞穴中，伪装洞口伏击路过的猎物","结网守候，靠丝线粘住猎物"],"answer":1,"explain":"幼虫并不奔跑追猎，而是蛰伏垂直洞穴，以腹部倒钩固定身体，头部堵洞口伪装，猎物靠近便突然出击，与成虫奔跑捕食完全不同。"}]', '裸地与小径的领地', '成虫偏好向阳、少植被遮挡的沙土小径和河滩空地，这类开阔地形既方便高速追猎，也便于第一时间发现天敌靠近；幼虫则整段发育期都困守在自己挖掘的洞穴里，一旦离开垂直洞穴便难以自保。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'tiger-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'stag-beetle', '[{"title":"大颚怎么打斗","body":"内侧排列着尖锐齿突的大颚，是雄虫争夺树液取食点与配偶的主要武器；格斗时双方大颚交叉夹住对方身体用力上抬，把对手掀离树干摔下，占住有利位置的一方往往能独享这处树液伤口。","anchor":"mandible"},{"title":"头够宽才咬得动","body":"头部宽扁，两侧藏着发达的肌肉群，专门为大颚提供强大的咬合力；头部越宽壮，通常意味着这只雄虫能夹得更紧、耗得更久，在拉锯式的格斗中占据优势。","anchor":"head"},{"title":"爪子怎么扛住拉力","body":"足部跗节末端具锐利的爪，紧紧扣住粗糙的树皮纹理；格斗中被对方夹住身体用力拖拽时，正是这几枚小爪提供的抓附力，决定了它能不能稳住阵脚不被掀翻。","anchor":"leg"},{"title":"同窝也能长出天壤之别","body":"幼虫期的营养摄入直接决定成年后大颚的长度，同一窝兄弟若营养差异悬殊，羽化后雄虫大颚长度可能相差一倍以上；颚小的个体正面打不过对手，往往会改走偷袭或趁乱抢占树液的迂回战术。"}]', '夹举掀翻的角力', '两只雄虫头部相对，大颚交叉卡住对方身体后同时用力上抬，一方若被成功举起并甩离树干便算落败；这类打斗大多是仪式化的力量较量，很少咬穿对方鞘翅致命，落败一方脱身后多转往别处寻找新的树液伤口。', '[{"question":"同一窝中华大锹甲幼虫，为什么羽化后雄虫大颚长度可能相差很大？","options":["完全由基因决定，营养好坏不影响颚型","幼虫期营养摄入量不同，直接影响成年后大颚发育程度","只是测量误差，实际差异并不大"],"answer":1,"explain":"大颚长度深受幼虫期营养状况影响，同一窝幼虫若取食条件差异悬殊，羽化后雄虫大颚长度可能相差一倍以上，并非单纯由基因决定。"},{"question":"大颚偏小的雄性中华大锹甲，面对大颚粗壮的对手时通常怎么做？","options":["硬拼到底，直到咬穿对方鞘翅分出生死","转而采用偷袭或迂回战术，趁机抢占树液取食点","干脆放弃这片树液，此后终生不再争夺"],"answer":1,"explain":"颚小的个体正面格斗处于劣势，往往改走偷袭或迂回策略，趁对手不备抢占树液取食点，而不是硬拼到底或从此彻底放弃。"}]', '朽木与树液伤口', '幼虫深居阔叶林中腐朽的倒木或树洞内取食分解木质纤维，几乎终生不见光；成虫夜间才活跃于树干伤流处舔食渗出的树液，常与近缘锹甲、独角仙共用同一处伤口，白天重新躲回树皮缝隙或落叶堆中休息。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'stag-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'jewel-beetle', '[{"title":"颜色不是画出来的","body":"鞘翅表面看似涂着一层金绿泛红的釉彩，其实体壁下堆叠着数十层纳米级薄膜，光线在层与层之间反复反射干涉后才呈现出这种颜色，与色素染色完全是两回事，换个角度看颜色也会跟着微微偏移。","anchor":"elytra"},{"title":"两道纵纹最扎眼","body":"鞘翅上两道红紫色纵纹是结构色堆叠最密集的区域，反光效果比周围更强烈醒目；这两道纹路也是野外和标本鉴别日本吉丁与其近缘种时最直观的参照。","anchor":"stripe"},{"title":"复眼小，靠什么找树","body":"相比奔跑或伏击型甲虫，日本吉丁的复眼并不算大，但仍是它锁定寄主树冠位置的主要感官；成虫多在向阳的树冠顶端活动，复眼负责远距离发现衰弱木或同类个体的轮廓。","anchor":"eye"},{"title":"为什么标本千年不褪色","body":"色素做的颜色会随时间氧化分解逐渐褪去，但结构色只取决于薄膜的物理厚度和排列，只要标本躯壳完好颜色就不会老化；法隆寺「玉虫厨子」上镶嵌的吉丁鞘翅至今仍能看见虹彩。"}]', '飞行中变色的鞘翅', '日本吉丁振翅起飞时，鞘翅随身体摆动和光线入射角度不断变化，绿、红、紫等色调随之流动闪烁，仿佛整只甲虫在空中变色；这种效果只属于结构色，若换成色素上色，无论怎么转动角度，颜色都不会有这种流动感。', '[{"question":"日本吉丁鞘翅那身金属光泽的颜色，是怎么产生的？","options":["体壁分泌的色素沉积在鞘翅表层","体壁下多层薄膜对光线的干涉效应，与色素无关","长期晒太阳后氧化形成的表面锈色"],"answer":1,"explain":"这是结构色：鞘翅体壁下堆叠多层纳米级薄膜，光线在层间干涉后呈现颜色，角度不同色泽也变化，与色素染色是两套不同机制。"},{"question":"为什么日本吉丁的鞘翅标本历经上百年仍不褪色？","options":["古人给标本涂了特殊防腐漆","颜色来自薄膜结构而非色素，躯壳完好色泽就不会氧化消退","鞘翅材质本身会随时间变得更鲜艳"],"answer":1,"explain":"色素会随时间氧化分解而褪色，但结构色只取决于薄膜的物理厚度和排列，只要鞘翅躯壳保持完整，颜色便不会像色素那样老化消退。"}]', '树冠顶端的阳光客', '成虫多活动在低山阔叶林朴树、樟树等寄主树种的向阳树冠顶端，很少下到阴暗林下层；幼虫则相反，深藏在同一片林地衰弱木或枯木内部蛀食木质部，两代之间几乎不共享同一垂直空间，却始终依附着同一棵寄主树。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'jewel-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'katydid', '[{"title":"耳朵长在前腿上","body":"蝈螽的听觉器官不在腹部，而是开在前足胫节靠近膝盖处，一对薄膜状鼓膜能感知同类翅摩擦发出的声波；这与蝗虫听器长在腹部第一节两侧不同，是分辨两类相似直翅目昆虫最可靠的差异。","anchor":"tympanum"},{"title":"触角比身体还长","body":"丝状触角常常远超体长两三倍，垂在身前不停摆动探路；相比之下蝗虫触角短粗得多，仅从触角长短就能大致分辨出眼前这只是蝈螽还是蝗虫。","anchor":"antenna"},{"title":"前翅怎么拉出声音","body":"雄虫左前翅基部有一片带齿的发音锉，右前翅边缘则是一道硬化的刮片，两翅交叠摩擦时齿片刮过刮片发出连续洪亮的鸣声，既用来求偶，也用来向邻近雄虫宣示自己占据的这片草丛。","anchor":"wing"},{"title":"会跳但不太会飞","body":"后足腿节发达，蹬地一跃能跳出相当远的距离，是它躲避天敌的第一反应；但翅膀发育不如跳跃能力突出，长距离移动主要靠跳跃和爬行，很少看到它像蝗虫那样持续飞行很远。","anchor":"hindleg"}]', '此起彼伏的占域鸣叫', '入夜后草丛间常有多只雄虫轮流鸣叫，一只率先开腔，邻近雄虫会紧接着回应甚至压过对方音量，形成此起彼伏的声浪；这既是雄虫向雌虫展示自己活力的求偶广告，也是划定各自领域、避免正面冲突的一种声音较量。', '[{"question":"蝈螽（优雅蝈螽）的听觉器官（鼓膜）长在身体的什么位置？","options":["腹部第一节两侧，与蝗虫相同","前足胫节上，与蝗虫的位置不同","头部触角基部附近"],"answer":1,"explain":"蝈螽的听器开在前足胫节上，这一点与蝗虫（听器在腹部第一节两侧）明显不同，是区分这两类相似直翅目昆虫最可靠的特征之一。"},{"question":"仅凭触角长短，怎样大致分辨蝈螽和蝗虫？","options":["蝈螽触角远长于体长，蝗虫触角明显短粗","两者触角长度其实差不多，无法靠这个区分","蝗虫触角更长，蝈螽的更短"],"answer":0,"explain":"蝈螽的丝状触角常远超体长两三倍，蝗虫的触角则短粗得多，触角长短是野外快速区分两类相似直翅目昆虫的直观特征。"}]', '豆棚草丛的歌手', '白天多隐伏在豆科、禾本科等植物茂密的茎叶间取食嫩叶花果，体色与环境融为一体；入夜后才在原地或附近植株上高声鸣叫，很少远距离迁移，同一片农田草地往往能年复一年听到蝈螽的鸣声。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'katydid');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'mole-cricket', '[{"title":"前足是铲子不是弹簧","body":"前足特化得宽扁如铲，胫节边缘还长着一排硬齿，专门用来刨松土壤向前推进；正因为全部力量都点在了挖掘上，它的跳跃能力反而很弱，遇到危险时靠的是钻进松土逃生，而不是远距离弹跳。","anchor":"foreleg"},{"title":"盾形前胸怎么推进","body":"前胸背板膨大呈圆盾状，紧贴着覆盖颈部和前足基部，在狭窄洞道里前进时能像顶盖一样把松动的泥土顶开推向两侧，减少身体和洞壁之间的摩擦阻力。","anchor":"pronotum"},{"title":"尾须怎么感知震动","body":"柔软的腹部末端长着一对尾须，对地面和洞壁传来的细微震动格外敏感；即便身处完全黑暗的地道深处，蝼蛄也能靠这对尾须提前察觉到天敌靠近或猎物经过的动静。","anchor":"abdomen"},{"title":"偶尔也会飞","body":"多数时候蝼蛄一辈子都在土里活动，很少露面；但到了夜间迁散时期，它会展开折叠在鞘翅下的后翅短距离飞行寻找新的栖息地，且趋光性很强，这也是它偶尔出现在灯光附近的原因。","anchor":"wing"}]', '土中蛙泳式掘进', '在松软湿润的土壤里，蝼蛄以左右前足交替刨土，动作神似蛙泳划水：一足向外拨开泥土的同时，另一足已经收回准备下一次发力，配合宽扁的前胸背板把碎土顶向两侧，短短几分钟就能在地下开凿出一条新的通道。', '[{"question":"遇到天敌时，东方蝼蛄通常靠什么方式脱险？","options":["和蝗虫一样，靠强壮后足跳出老远","前足并非跳跃足，靠迅速钻入松软土壤逃生","展开双翅立刻起飞逃离"],"answer":1,"explain":"蝼蛄前足特化成挖掘足而非跳跃足，跳跃能力很弱，遇险时主要靠钻入松软土壤脱身，常被误以为它和蝗虫、蟋蟀一样擅长跳跃。"},{"question":"东方蝼蛄常年在农田土壤中掘穴取食，对土壤本身有什么客观影响？","options":["挖掘活动会压实土壤，不利于植物根系生长","挖掘活动客观上疏松了土壤，促进空气和水分渗透","蝼蛄的挖掘对土壤结构没有任何影响"],"answer":1,"explain":"蝼蛄取食植物根系幼苗被视为农田害虫，但它频繁掘穴客观上疏松了土壤，有利于空气和水分渗透，是危害之外容易被忽视的一面。"}]', '松软土层里的通道', '偏好栖居在松软潮湿、便于挖掘的农田、菜地和河岸土壤中，白天多蛰伏在地下通道深处，夜间才靠近地表活动取食；土壤板结坚硬或长期干燥的地块很少见到它的踪迹，湿润疏松的土质才是它能自由掘进的必要条件。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'mole-cricket');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'water-strider', '[{"title":"站在水上靠的不是轻","body":"体表覆盖一层蜡质疏水绒毛，中后足上更密布着微小疏水刚毛，这些结构把水面张力放大到足以支撑体重，让它能立于水面不被浸湿下沉；这不是因为体重轻，剪掉绒毛后同样重量的水黾会立刻沉没。","anchor":"body"},{"title":"中足是船桨","body":"三对足里中足最长，划动时像船桨一样左右对称摆动，是推动身体在水面滑行的主要动力来源；水黾能借助中足的划水瞬间加速冲刺，躲避水面下鱼类的偷袭。","anchor":"midleg"},{"title":"怎么发现落水的猎物","body":"复眼向身体两侧突出，视野宽阔便于侦测水面因猎物挣扎而扩散开的细微波纹；一旦锁定波纹来源，水黾会迅速滑行过去，用短小有力的前足将猎物钳住固定。","anchor":"eye"},{"title":"后足负责刹车转弯","body":"中足负责主要推进，后足则更多承担转向和刹车的角色，两对足协同配合，让水黾能在水面上快速变向或骤然停住，这套分工让它在开阔水面上的机动性远胜许多水生昆虫。","anchor":"hindleg"}]', '水面上垂直起跳', '遇到水下鱼类突袭时，水黾能直接从水面垂直跃起逃生，这个动作看似简单，实则暗藏精细控制：起跳时六足必须逐渐加力下压而不能过猛，力道稍大就会戳破水膜让自己瞬间陷落，全靠长期演化出的力道拿捏才能全身而退。', '[{"question":"水黾能站在水面上而不下沉，最主要靠的是什么？","options":["体重特别轻，几乎不会压破水面","足上密布疏水刚毛，把表面张力放大到能撑起体重","分泌油脂让身体完全不沾水"],"answer":1,"explain":"水黾中后足密生疏水微毛，靠这些结构把水面张力放大到足以支撑体重，剪掉绒毛后同样重量的水黾会立刻下沉，靠的不是体重轻。"},{"question":"水黾三对足的分工，下列描述正确的是？","options":["前足负责划水推进，中后足用来抓握猎物","中足负责主要划水推进，前足抓握猎物，后足负责转向刹车","三对足功能完全相同，可以互相替代"],"answer":1,"explain":"水黾中足最长，是划水推进的主要动力；前足短小有力专用于抓握落水猎物；后足则负责转向和刹车，三对足分工明确，并非功能相同。"}]', '静水面上的浮世界', '偏好栖息在少风浪的池塘、缓流溪面等开阔水域，借水面张力自由滑行巡猎；水质污染会破坏水面张力的稳定性，因此水黾的种群密度也常被当作判断水体清洁程度的一项直观参考。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'water-strider');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'hoverfly', '[{"title":"只有一对翅却能悬停","body":"双翅目昆虫的后翅早已特化，食蚜蝇实际只靠一对前翅飞行，却能做到许多带四片翅的蜂类都做不到的悬停、倒退和瞬间转向；这份飞控能力主要归功于极高的振翅频率和灵活的翅根肌肉调控。","anchor":"wing"},{"title":"平衡棒怎么保持稳定","body":"后翅退化成一对棒状构造，飞行时高速摆动感知身体的旋转和倾斜，把姿态信息实时反馈给神经系统调整飞行姿势；少了这对平衡棒，双翅目昆虫的飞行会像失去陀螺仪一样东倒西歪。","anchor":"haltere"},{"title":"黑黄条纹是虚张声势","body":"腹部黑黄相间的斑纹模仿蜂类的警戒配色，让天敌误以为它会蜇人而不敢轻易下口；但它体内根本没有螫针，也没有蜇刺能力，这身「战袍」纯属演化出来的伪装，并无实际攻击性。","anchor":"abdomen"},{"title":"复眼怎么分辨雌雄","body":"雄虫两只复眼在头顶几乎相接成一线，雌虫两眼之间则留有明显间隔，这是野外快速区分食蚜蝇雌雄最直观的方法，比观察体色斑纹的差异更可靠。","anchor":"eye"}]', '幼虫夜猎蚜虫群', '食蚜蝇幼虫体态如小蛆，行动却很敏捷：它用口钩探到蚜虫后猛地抬起前端将其刺穿悬空吸干体液，一晚上能连续捕食数十只蚜虫；蚜虫群体越密集，幼虫捕食效率反而越高，是蚜虫暴发期最有效的天敌之一。', '[{"question":"黑带食蚜蝇体色黑黄相间、外形很像蜂类，但它实际上？","options":["和蜂类一样有螫针，只是很少蜇人","根本没有螫针，蜇人的能力也不存在，配色只是伪装","螫针已经退化成攻击力较弱的针状口器"],"answer":1,"explain":"食蚜蝇是双翅目昆虫，体内没有螫针，不具备蜇人能力；黑黄相间的配色只是模仿蜂类警戒色的伪装，用来吓退天敌，并无攻击性。"},{"question":"食蚜蝇能像蜂类一样飞行灵活，甚至悬停自如，靠的是什么？","options":["和蜂类一样拥有两对翅膀协同飞行","只有一对翅膀，配合退化成棒状的后翅（平衡棒）感知姿态","翅膀虽只有一对，但比蜂类的翅膀大得多"],"answer":1,"explain":"食蚜蝇是双翅目昆虫，只有一对膜质前翅飞行，后翅特化成棒状平衡棒感知身体姿态、辅助稳定，并非依靠两对翅膀或更大翅面积。"}]', '花丛与蚜虫群之间', '成虫活跃于花园、农田和林缘的开花植物间访花取食花蜜花粉，兼具传粉功能；雌虫产卵时却专门寻找蚜虫密集的枝叶，好让孵化的幼虫一出壳就能就地捕食，成虫栖息地和幼虫觅食地常常连在同一丛植物上。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'hoverfly');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'lacewing', '[{"title":"金铜色复眼最好认","body":"复眼泛着金铜色光泽，在阳光或灯光下格外醒目，是野外辨认草蛉最直接的特征；这对复眼虽然显眼，视力却并不算突出，主要负责感知光线明暗和大致的移动轮廓。","anchor":"eye"},{"title":"网状翅脉撑起薄翅","body":"两对狭长的膜质翅上翅脉纵横交错如网，既加固了原本脆弱的薄翅结构，又让阳光透过时泛出淡淡的虹彩光泽；这种脉纹样式也是「脉翅目」这个名字的由来。","anchor":"wing"},{"title":"幼虫怎么伪装成垃圾堆","body":"幼虫细长多毛的足更适合快速追捕蚜虫，捕食后会把吸空的蚜虫空壳背在背上堆成一小堆，混在真正的蚜虫尸体残骸间悄悄靠近下一个目标，靠伪装躲开猎物和天敌的警觉，因此得名「蚜狮」。","anchor":"leg"},{"title":"卵挂在丝柄上","body":"雌虫产卵时会先吐出一根细丝再把卵产在丝柄顶端，让卵悬空立在叶片上方；这样即便新孵化的幼虫食性凶猛、可能互相残食，也能避免同一批卵挤在一起被首先孵出的幼虫就地吃掉。"}]', '震动求偶的暗语', '求偶时雄虫和雌虫并不靠鸣叫，而是用腹部有节奏地抖动身体，把振动经足传入停栖的枝叶或茎秆，对方能通过同一段植物感受到这段「震动暗语」并抖动回应；一问一答确认彼此是同种个体后，才会靠近完成交配。', '[{"question":"草蛉幼虫把吸空的蚜虫空壳堆在背上，这样做的主要作用是什么？","options":["壳里残留的汁液可以当备用食物","混在猎物残骸间伪装接近下一个目标，也躲开天敌","空壳能反射阳光帮助调节体温"],"answer":1,"explain":"草蛉幼虫背负蚜虫空壳是为了伪装成猎物残骸，既能悄悄接近还未察觉的蚜虫，也能降低被天敌发现的概率，并非储存食物或调温。"},{"question":"中华草蛉所属的「脉翅目」，这个目名主要是因为它们具备什么特征？","options":["翅膀布满粗大网状翅脉，如同精细的叶脉","翅膀完全退化，只剩下翅脉状的痕迹","幼虫和成虫的血管系统格外发达"],"answer":0,"explain":"脉翅目得名于其翅膀上纵横交错、纹路细密如网的翅脉，草蛉正是典型代表，翅脉既加固薄翅结构，也是辨认这一类群的重要依据。"}]', '枝叶间的猎手基地', '成虫栖息在农田、果园与林缘植被的枝叶间，白天多静伏不动，夜晚才活跃觅食并容易被灯光吸引；雌虫产卵地点往往就选在蚜虫密集的枝条附近，幼虫孵化后不必远行便能立刻投入捕食。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'lacewing');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'earwig', '[{"title":"尾铗用来做什么","body":"腹部末端一对弯曲的尾铗是它最显眼的武器，雄虫的铗明显弯曲不对称，雌虫则相对平直，两者都用于抵御天敌和制服体型相近的小型猎物；民间传说它会钻进人耳，其实与尾铗的真实用途毫无关系。","anchor":"forceps"},{"title":"鞘翅为什么这么短","body":"前翅特化成一对极短的革质鞘片，只够盖住腹部最前面几节，后半段柔软的腹部完全裸露在外；这与鞘翅目甲虫那种严实包裹全腹的鞘翅形成鲜明对比，是辨认革翅目昆虫的直观线索。","anchor":"elytra"},{"title":"扁头能钻多窄的缝","body":"头部扁平，宽度经过压缩后能贴着地面钻进石块与浮木之间极狭窄的缝隙；白天多蛰伏在这类隐蔽空间里躲避阳光暴晒和天敌视线，直到夜幕降临才外出活动觅食。","anchor":"head"},{"title":"腹部为什么能反弓","body":"腹部末尾几节柔韧灵活，可以向背侧大幅弯曲甚至反卷过头顶，让尾铗能从身体上方或侧后方直接钳向逼近的天敌；这种灵活性弥补了它奔跑速度不算快、又不能飞行的防御短板。","anchor":"abdomen"}]', '雌虫护卵到孵化', '雌虫产卵后会守在卵堆旁，不时舔舐卵粒清除霉菌孢子，还会把散落的卵拢回一处；卵堆被破坏时，雌虫甚至会把卵一颗颗衔回重新码好，这种持续照料在昆虫中并不常见，要守到若虫孵化后才逐渐放手。', '[{"question":"民间流传「蠼螋会钻进人耳朵」的说法，实际情况是什么？","options":["确有其事，蠼螋常趁人熟睡钻入耳道","纯属讹传，蠼螋并无钻入耳道的习性，对人几乎没有威胁","只有少数大型蠼螋种类会这样做"],"answer":1,"explain":"这是流传已久的民间讹传，蠼螋并没有主动钻入耳道的习性，它的尾铗只用来御敌和制服小型猎物，对人体基本没有威胁。"},{"question":"海滨蠼螋的「短鞘翅」与常见甲虫的鞘翅相比，最大的区别是什么？","options":["同样能完整覆盖整个腹部，只是颜色更深","只覆盖腹部前段，其后各腹节完全裸露在外","根本不是翅，而是特化增厚的触角"],"answer":1,"explain":"海滨蠼螋前翅特化成极短的革质鞘片，只能盖住腹部前段，后面各腹节完全裸露，这正是革翅目区别于鞘翅目（甲虫）鞘翅的特征。"}]', '潮间带石缝的家', '栖息在海岸潮间带的石块下方、漂浮木缝隙及潮湿的腐殖质堆积处，涨潮线附近湿度稳定的隐蔽空间是它白天蛰伏的首选；退潮后裸露的礁石缝隙里也常能翻找到它的踪迹，是滨海岩岸生境中低调却随处可见的常住居民。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'earwig');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'dung-beetle', '[{"title":"铲状唇基","body":"头部前缘扁平延展成半圆形的推土铲，边缘平直坚硬。它先用这把铲切开粪块、刮下需要的部分，再向下掘穿土层，是整套「掘穴埋粪」动作的第一件工具。","anchor":"clypeus"},{"title":"前足带锯齿","body":"前足胫节外侧生着三到四枚粗壮齿突，如同带齿的锄头；挖掘泥土或翻动粪堆时全靠这排齿突发力，配合唇基把散落的粪便迅速聚拢成团。","anchor":"foreleg"},{"title":"头角为格斗","body":"雄虫头部生有一支上翘的角，雌虫头部并不存在这个结构；与其他雄性争夺优质粪源或配偶时，双方以角相抵角力，占上风的一方才能独享这坨粪球。","anchor":"horn"},{"title":"粪球是育儿室","body":"它在粪堆正下方向地里挖一条竖井，把切下的粪块一趟趟拖进洞底，再在地下搓成紧实的球；雌虫把卵产进球心，幼虫从孵化起就被食物包围，粪球既是产房也是全部口粮。"}]', '就地掘穴埋粪', '常被当作蜣螂招牌的「地面滚球」其实是另一类蜣螂的做法。神农洁蜣螂属于掘穴型：它落在新鲜粪堆上，直接向正下方挖一条深达数十厘米的竖井，用铲状唇基切下粪块，靠带齿的前足一趟趟往洞底拖，全程不在地面滚动。', '[{"question":"神农洁蜣螂把粪便拖入地下洞穴，主要是为了什么？","options":["单纯为了清理环境，没有其他用途","主要是为了产卵育幼并留作幼虫的食物","是雄虫之间争夺配偶的展示行为"],"answer":1,"explain":"把粪埋进洞底搓成球，是为了把卵产在球心当作食物储备，幼虫孵化后直接以粪球为食，兼具育幼与储粮。"},{"question":"神农洁蜣螂在草地生态系统中扮演的角色主要是？","options":["与植物竞争养分的消耗者","加快粪便分解、促进养分回归土壤的分解者","主要传播植物花粉的传粉者"],"answer":1,"explain":"蜣螂大量取食并掩埋粪便，显著加快了粪便的分解速度，把其中的养分更快地送回土壤，是草地和牧场里重要的分解者。"}]', '牧场与粪堆', '常见于牧场、林缘草地等食草动物粪便丰富的开阔环境，靠灵敏的嗅觉在数十米外就能定位新鲜粪源；土质是否松软湿润、掘得动竖井，是它选择落脚点的另一项硬条件。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'dung-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'weevil', '[{"title":"细长的喙","body":"喙管细长笔直，长度可达体长的三分之一，几乎占去半个身子；末端才是真正的咀嚼式口器，平时探入笋壳缝隙，一点点啃穿抵达嫩笋深处的通道。","anchor":"rostrum"},{"title":"触角长在喙上","body":"多数昆虫触角长在头顶，竹象膝状的触角却生在喙的中段，这是象甲区别于其他甲虫最直接的识别标志；触角能弯折收拢，探路时才向前展开。","anchor":"antenna"},{"title":"庞大的象甲家族","body":"象甲科是整个动物界已知物种数量最多的一个科，全球记载的种类数以万计，形态与体型跨度极大，竹象只是其中专门取食竹笋的一个成员。"},{"title":"幼虫蛀笋为害","body":"雌虫把卵产在笋壳缝隙里，孵化的幼虫顺着母亲预先咬开的通道钻入笋肉深处取食，等笋农挖笋时才发现内部早已被蛀空，是竹林常见害虫。"}]', '喙钻笋壳产卵', '雌虫选定嫩笋后，先用细长的喙像钻头一样反复旋转刺入笋壳与笋肉钻出小孔；确认深度足够后调转身体，把产卵器伸入孔洞产下一枚卵，再退出寻找下一处继续操作。', '[{"question":"竹象的触角与常见昆虫相比，最特别的地方是什么？","options":["触角长在细长喙的中段，而非头顶","触角完全退化、几乎看不见","触角比身体还长出好几倍"],"answer":0,"explain":"多数昆虫触角长在头部，竹象膝状的触角却生在喙的中段，这是象甲科区别于其他甲虫最直接的识别特征。"},{"question":"象甲科（竹象所属的科）在整个动物界中处于什么地位？","options":["是昆虫纲里体型最大的科","是整个动物界已知物种数量最多的科","是一个种类稀少的小科"],"answer":1,"explain":"象甲科是整个动物界物种数量最多的一个科，全球已记载的种类数以万计，竹象只是其中专门为害竹笋的一支。"}]', '竹林里的笋期客', '成虫多活动在竹林新笋抽出的季节，围着嫩笋和竹梢取食活动；幼虫则完全在笋体内部度过，随笋长成竹竿被迫困死其中或提前咬穿笋壁脱出，是笋用竹林常见的蛀食性害虫。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'weevil');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'click-beetle', '[{"title":"后角两尖刺","body":"前胸背板后角向两侧延伸出尖锐的刺突，配上细长如舟的体形，让它静止时的轮廓明显区别于圆胖体形的多数甲虫，是野外识别叩甲科的重要线索。","anchor":"pronotum"},{"title":"弹跳不靠腿","body":"前胸腹面有一根突起能猛然弹入中胸凹槽，靠这一瞬间释放的机械弹力使身体腾空翻转；被天敌碰翻仰面朝天时，也是靠这声「咔哒」弹跳自行翻正。","anchor":"clickSpine"},{"title":"触角贴身藏好","body":"锯齿状触角平时收拢贴在前胸下方的凹槽里几乎看不见，只有缓慢爬行或感知气味时才略微伸展，这样的收纳方式能减少行动时的额外阻碍。","anchor":"antenna"},{"title":"幼虫叫金针虫","body":"幼虫体细长、体壁坚硬呈金黄色，通称金针虫，常年潜伏在土壤里咬食农作物的种子与幼根，是让不少农户头疼的地下害虫。"}]', '仰翻后的一跃', '沟叩头虫被天敌碰翻仰面朝天时，先把头胸稍稍抬起，让前胸腹面的突起对准中胸凹槽卡住，随后猛然收缩肌肉使突起滑脱弹出，带动整个身体腾空翻转，落地时多半已恢复正面朝上。', '[{"question":"沟叩头虫仰面朝天时能够翻正身体，主要依靠的是什么？","options":["六条腿用力蹬地翻身","前胸腹突猛然弹入凹槽释放的机械弹力","鞘翅快速振动产生的反作用力"],"answer":1,"explain":"叩甲的翻正动作靠前胸腹面突起弹入中胸凹槽时释放的机械弹力完成，弹跳瞬间几乎不靠六足发力，是它区别于多数甲虫的独特机关。"},{"question":"沟叩头虫的幼虫俗称什么，有什么习性？","options":["蛴螬，取食腐殖质","金针虫，潜伏土中取食种子和幼根","孑孓，生活在水中"],"answer":1,"explain":"叩甲幼虫体细长坚硬呈金黄色，通称金针虫，常年潜伏土壤中咬食农作物的种子与幼根，是常见的地下害虫。"}]', '草丛与作物根际', '成虫夜间常出没于草地、农田周边的低矮植被间，白天多隐伏在土表落叶或杂草根部；幼虫则终年生活在土壤里靠近作物根系的位置，土质疏松、有机质丰富的地块最容易发现它们的踪迹。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'click-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'diving-beetle', '[{"title":"流线形躯体","body":"卵圆形躯体表面光滑，鞘翅边缘不长任何多余的棘刺或突起，整体轮廓贴合水流阻力最小的流线型，游动时几乎不会产生多余的紊流阻力。","anchor":"body"},{"title":"后足似船桨","body":"后足极度侧扁变宽，外缘密生一排硬毛，划水时像船桨一样左右对称摆动推进身体；前中足仍保留抓握功能，唯有这对后足专职负责游泳。","anchor":"hindleg"},{"title":"气管代替鱼鳃","body":"黄缘龙虱终生用气管呼吸并不靠鳃，潜水前把腹部末端伸出水面，在鞘翅与腹背之间带下一泡空气随身携带，氧气耗尽前须再浮出水面补充。","anchor":"airStore"},{"title":"幼虫也是猛将","body":"成虫和幼虫都是水下凶猛的捕食者，幼虫的镰刀状上颚中空如注射器，咬住蝌蚪或小鱼后能直接注入消化液，把猎物体内组织化开后吸食。"}]', '腹部末端换气', '黄缘龙虱游至水面后，先把腹部末端探出水面，收拢的鞘翅边缘随之微微掀起一道缝隙，新鲜空气顺着缝隙灌入鞘翅下方的储气腔；换气完成后重新潜入水底，带着这泡气继续巡游猎食。', '[{"question":"黄缘龙虱长期生活在水中，靠什么方式呼吸？","options":["用鳃从水中直接摄取溶解氧","用气管呼吸，靠随身携带的气泡供氧","皮肤直接吸收水中的氧气"],"answer":1,"explain":"黄缘龙虱终生用气管呼吸而非用鳃，潜水前会在鞘翅与腹部之间带下一泡空气，靠这泡随身携带的空气维持水下呼吸。"},{"question":"黄缘龙虱的后足在游泳时发挥什么作用？","options":["和前中足一样主要用来抓握猎物","侧扁如桨、密生游泳毛，是主要的游泳动力","收拢在鞘翅下几乎不参与游泳"],"answer":1,"explain":"龙虱后足极度侧扁变宽、外缘密生游泳毛，划水时像船桨一样对称摆动，是三对足里唯一专职提供游泳推力的一对。"}]', '静水塘里的猎手', '栖息在水草丰富、水流平缓的池塘、稻田和沟渠中，成虫与幼虫都活跃在水层中下部伏击蝌蚪、小鱼和其他水生昆虫；水质清洁、溶氧充足的静水环境更适合它长期定居繁殖。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'diving-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'rove-beetle', '[{"title":"极短的鞘翅","body":"鞘翅只覆盖胸部后一小段，长度远不及体长的一半，收拢的膜质后翅和大半个腹部完全裸露在外，这在甲虫里相当少见，也是隐翅虫科名字的由来。","anchor":"elytra"},{"title":"腹部能翘起","body":"裸露的腹部分节明显、十分柔韧，受到惊扰时会像蝎子一样向背侧翘起弯曲，摆出警戒姿态；这只是恐吓动作，它腹部末端并没有真正的螫针。","anchor":"abdomen"},{"title":"伤害另有来源","body":"上颚虽小却能咬碎猎物，但它对人体的伤害其实与上颚无关——体内血淋巴含有隐翅虫素，只有被拍碎压在皮肤上时毒液渗出才会引发皮炎。","anchor":"mandible"},{"title":"实为稻田卫士","body":"红黑相间只是警戒色的一种，它实际以稻飞虱等农田害虫为食，是水稻种植区里不会主动伤人、反而在暗中压制虫害的重要天敌。"}]', '受惊翘尾示警', '隐翅虫受到触碰惊扰时，会先停住不动，把裸露的腹部末端向背侧高高翘起弯曲，摆出类似蝎子的警戒姿态威慑对方；若骚扰不停止，它会转身迅速爬开躲藏，全程并不会主动扑咬或蜇刺。', '[{"question":"皮肤上不慎沾到隐翅虫后，正确的处理方式是什么？","options":["直接用手拍死它，避免被咬伤","轻轻吹走或抖落，避免拍碎压在皮肤上","用力按压将其捏死后再冲洗"],"answer":1,"explain":"隐翅虫并不叮咬，皮炎源于体液中的隐翅虫素接触皮肤；拍死会让毒液渗出接触伤口，正确做法是吹走或抖落而不要拍打。"},{"question":"隐翅虫虽因皮炎问题让人心生畏惧，但它在生态角色上实际是？","options":["捕食稻飞虱等害虫的天敌","专门传播疾病的卫生害虫","取食水稻叶片的植食性害虫"],"answer":0,"explain":"隐翅虫的伤害只是体液接触引发的皮炎，它本身是捕食性昆虫，以稻飞虱等农田害虫为食，对水稻种植其实是有益的天敌。"}]', '稻田水岸的夜行者', '常栖息在稻田、沟渠边潮湿的草丛与泥土表面，夜间受灯光吸引会飞入居所；白天多蛰伏在植株基部或落叶下侧，捕食蚜虫、稻飞虱等小型农田害虫，是稻作区里数量可观却常被误解的益虫。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'rove-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'flower-chafer', '[{"title":"扁平不隆起","body":"鞘翅呈扁平状几乎不隆起，与背部高高拱起的双叉犀金龟正相反；铜绿色底色上散布着不规则的白色斑点，是野外辨认它的第一眼特征。","anchor":"elytra"},{"title":"侧缘留了缺口","body":"鞘翅靠近肩部的侧缘各留有一个凹口，膜质后翅能直接从凹口探出扇动，不必像其他金龟那样先完全掀开鞘翅，起飞前的准备动作因此快得多。","anchor":"notch"},{"title":"前胸背板贴合","body":"前胸背板宽大微隆，边缘与鞘翅贴合紧密，飞行时能护住翅基关节；静止时它与鞘翅共同组成一层几乎无缝的铠甲，把柔软的背部完全盖住。","anchor":"pronotum"},{"title":"蛴螬与熟果客","body":"幼虫是常见的蛴螬，蜷曲在腐殖土里取食腐烂的植物质；成虫则常见于树干伤流处和熟透的落果上，靠吸食糖分较高的汁液补充能量。"}]', '不掀鞘翅起飞', '白星花金龟准备起飞时不必像多数金龟那样先完全掀起鞘翅，膜质后翅直接从鞘翅侧缘的凹口探出展开扇动，鞘翅几乎保持原位不动，整个起飞动作因此显得干净利落、反应更快。', '[{"question":"白星花金龟起飞时与多数金龟子相比，有什么不同？","options":["不必掀开鞘翅，后翅从鞘翅侧缘凹口伸出即可起飞","需要先助跑一段距离才能起飞","起飞前必须先脱去鞘翅"],"answer":0,"explain":"白星花金龟鞘翅侧缘近肩部处有凹口，后翅能直接从凹口探出扇动，不必先完全掀开鞘翅，这是它区别于其他金龟的重要特征。"},{"question":"白星花金龟的体形与双叉犀金龟相比，有什么明显区别？","options":["同样背部高高拱起","体形扁平，背部不隆起","完全没有鞘翅"],"answer":1,"explain":"白星花金龟体形扁平，与背部高高拱起的双叉犀金龟形成鲜明对比，铜绿色鞘翅上散布的不规则白斑也是野外辨认它的重要线索。"}]', '树液与腐殖土之间', '成虫常见于阔叶林、果园里流淌树液的伤口处及熟透落果周围，取食糖分丰富的汁液；幼虫（蛴螬）则蜷曲生活在腐殖土、堆肥堆或朽木碎屑中，取食腐烂的植物质完成发育。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'flower-chafer');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'burying-beetle', '[{"title":"截短的鞘翅","body":"鞘翅明显短于腹部，末端平齐截断，露出后面两三节裸露的腹背；黑色鞘翅上还横贯着两道橙红色波浪状条纹，是野外识别它最直接的标志。","anchor":"elytra"},{"title":"球棒触角嗅尸","body":"触角末端膨大成球棒状，表面密布嗅觉感受器，能在很远距离外嗅到动物尸体腐败散发的气味，循着气味浓度的变化方向逐渐逼近目标。","anchor":"antenna"},{"title":"合力掘土埋尸","body":"找到合适的小型尸体后，一对配偶合力用上颚清除周围泥土，把尸体一点点拖拽下沉，直至整具尸体埋入土中做成球形的育儿室。","anchor":"mandible"},{"title":"反刍喂养幼虫","body":"孵化后的幼虫聚在尸体球周围乞食，亲代埋葬虫会将预先消化的食物反刍喂给它们；这种持续照料幼虫的行为在昆虫里十分罕见。"}]', '协力埋葬全程', '一对配偶发现合适尸体后，先合力清除周围杂物和覆盖的落叶，再从尸体下方掘松泥土，靠身体不断拱动把尸体一点点顶入下沉的土坑；不到一夜就能把整具尸体埋入地下，做成球形的育儿室。', '[{"question":"日本埋葬虫最特别的行为是什么，这在昆虫里是否常见？","options":["亲代共同埋尸并反刍喂养幼虫，在昆虫中十分少见","成群围攻天敌，是常见的群居行为","把卵产在水中，和多数甲虫一样常见"],"answer":0,"explain":"日本埋葬虫夫妻共同把尸体埋入土中做成育儿球，还会反刍喂食幼虫，这种持续的亲代抚育行为在昆虫里相当罕见。"},{"question":"日本埋葬虫的触角末端呈球棒状，主要作用是什么？","options":["储存备用的营养物质","密布嗅觉感受器，用于远距离嗅探尸体气味","感知声音振动、寻找配偶"],"answer":1,"explain":"球棒状触角末端密布嗅觉感受器，能在很远的距离外嗅到动物尸体腐败散发的气味，是它定位食物和产卵地点的主要感官。"}]', '林地腐殖层的清道夫', '常出没于林地、灌丛的落叶层与松软土壤间，靠嗅觉搜寻小型鸟类或啮齿动物的尸体；找到合适的尸体后就地掘穴掩埋繁殖，客观上加快了动物遗骸的分解速度，是森林生态系统里重要的清道夫。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'burying-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'tortoise-beetle', '[{"title":"透明的裙边","body":"鞘翅与前胸背板边缘大幅向外扩展，形成一圈几乎透明的薄檐，把头部和六足严严实实盖在下面，俯视时像扣着一顶小小的玻璃罩。","anchor":"margin"},{"title":"头部藏在罩下","body":"头部缩在扩展的裙边下方，从正上方几乎看不见，只有从侧面或掀开裙边才能瞧见；这样的隐蔽让天敌很难找到可以下嘴的边角部位。","anchor":"head"},{"title":"中央的金属光泽","body":"鞘翅中央拱起的部分保留着较强的金属光泽，阳光下泛出淡淡的金绿色反光，与边缘那圈哑光的透明薄檐形成明显对比，像镶了一圈毛玻璃边框。","anchor":"elytra"},{"title":"幼虫背着粪伞","body":"幼虫会把自己蜕下的旧皮和排出的粪便顶在背部，堆成一把「粪伞」举在身体上方遮住自己，天敌靠近时挥动粪伞驱赶，是相当另类的防御装备。"}]', '幼虫举粪伞防身', '幼虫取食蜕皮后并不丢弃旧皮，而是把它和陆续排出的粪便一同顶在背部的分叉突起上，层层堆叠成一把「粪伞」；天敌靠近时，幼虫摆动身体把粪伞挥向对方，用气味和遮挡共同吓退攻击。', '[{"question":"甘薯腊龟甲成虫鞘翅与前胸背板边缘大幅扩展，形成的透明薄檐有什么作用？","options":["纯粹是装饰，没有实际功能","把头部和六足罩住藏好，增加隐蔽性","用来在空中滑翔延长飞行距离"],"answer":1,"explain":"这圈透明薄檐把头和足全部罩在下面，俯视时不易看出是昆虫的轮廓，起到隐蔽保护的作用，并不用于滑翔或单纯装饰。"},{"question":"甘薯腊龟甲幼虫背上顶着的「粪伞」，是用什么材料做成的？","options":["吐丝黏合的植物碎屑","自己蜕下的旧皮与排出的粪便","从叶片上咬下的碎片"],"answer":1,"explain":"幼虫把蜕下的旧皮和排出的粪便顶在背部突起上堆叠成伞状结构，既能遮挡自己，天敌靠近时挥动粪伞还能起到驱赶作用。"}]', '甘薯叶背的伪装者', '成虫和幼虫都常年栖息在甘薯等旋花科植物的叶片上，取食叶肉留下透光的斑痕；扁平透明的体形贴伏在叶背几乎与叶脉融为一体，农田与荒地边缘的旋花科杂草丛也是它常见的栖身之处。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'tortoise-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'hercules-beetle', '[{"title":"钳夹如何制敌","body":"极长的胸角与较短的头角上下相对，合成一把钳子，把对手连身体一起夹起摔下树干；胸角内缘还密生绒毛防止打滑，这套借力打法比直接刺杀更稳妥有效。","anchor":"thoracicHorn"},{"title":"头角怎么助攻","body":"头角明显短于胸角，末端微微上翘，交锋时从下方顶住对手腹面，与胸角对握形成合力点；缺了这支角，胸角单独发力很难把对手稳稳掀离树干摔下。","anchor":"headHorn"},{"title":"鞘翅为何变色","body":"鞘翅颜色不固定，干燥时偏黄绿，潮湿环境下会转成深褐甚至发黑；这是因为鞘翅是多孔结构，吸水后透出下方的黑色体壁，湿度一降颜色又会慢慢转浅回来。","anchor":"elytra"},{"title":"世界最长的甲虫","body":"加上头胸两角，雄虫体长可达十七厘米左右，是已知体长最长的甲虫，原产中南美洲雨林；这项纪录只算总长，若论体重，它其实比不少体形粗壮的兜虫轻。"}]', '钳夹掀翻的较量', '两只雄虫头角相抵试探，一旦寻到机会便合拢双角把对手整个夹住抬起，用力上挑将其连同六足一起甩离树干摔下；胸角内缘的绒毛在这一刻发挥关键作用，牢牢咬住对手鞘翅不至打滑脱手。', '[{"question":"长戟大兜虫雄虫打斗时，胸角与头角的主要作用是什么？","options":["像匕首一样刺穿对手鞘翅","上下配合成钳，把对手夹起摔下树干","摩擦发声，吓退对手"],"answer":1,"explain":"两角上下相对配合发力如钳，把对手连身体一起夹起再摔下树干，靠杠杆借力取胜，并不会真的刺穿对方鞘翅。"},{"question":"长戟大兜虫的鞘翅颜色会随什么发生变化？","options":["环境湿度，吸水后颜色变深","昼夜温差，白天偏浅夜间偏深","进食量，吃得越多颜色越鲜艳"],"answer":0,"explain":"鞘翅是多孔结构，潮湿环境下吸水后会透出下方黑色体壁使颜色变深，干燥时又转回黄绿色，与温差或进食量无关。"}]', '雨林树冠的独居者', '分布于中南美洲热带雨林，成虫多栖息在树冠层伤流树液或熟透落果附近，昼伏夜出；幼虫则蛰伏地表腐殖土或朽木堆里取食腐木碎屑长达一年多，与成虫的树栖生活几乎不重叠。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'hercules-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'whirligig-beetle', '[{"title":"复眼一分为二","body":"复眼在水面线处完全分成上下两对，上方一对专门盯着空中动静，下方一对同时监视水下的情况；这样即使停在水面打转，也能同时兼顾天上和水里两个方向的危险与猎物。","anchor":"upperEye"},{"title":"水下的另一双眼","body":"下方那对复眼视线朝下探入水中，专门用来发现水面下游动的小鱼或天敌；它和上方复眼各自独立成像、互不干扰，让豉甲成为少有的能同时看清空中与水下两个世界的昆虫。","anchor":"lowerEye"},{"title":"中足变成快桨","body":"中足和后足特化得又短又扁，桨面宽大边缘还密生硬毛，划动频率极快；与龙虱那种细长的划水足不同，豉甲的短桨更适合在水面做高速回旋，而不是深潜追逐猎物。","anchor":"midleg"},{"title":"靠波纹判断方向","body":"豉甲游动时会在水面激起一圈圈细小波纹，触角基部的感受器能接收波纹被障碍物或猎物反弹回来的信号，据此判断前方状况，如同一套水面声呐，即使快速打转也不会互相碰撞。","anchor":"antenna"}]', '水面旋游的声呐', '豉甲常成群聚在平静水面上高速打转，看似杂乱却极少相撞：中后足如快桨般连续划水推进，同时不断在水面激起细波，触角感受器实时接收波纹反射回来的信号，据此躲开同伴与障碍物，越是拥挤转得反而越灵活。', '[{"question":"豉甲的复眼分成上下两对，这样的构造有什么作用？","options":["上下两对复眼分别负责白天和夜晚视觉","同时监视水面上方与水下的动静","一对复眼用来看，另一对只用来调节体温"],"answer":1,"explain":"豉甲复眼在水面线处完全分成上下两对，上方盯着空中、下方探入水中，让它停在水面时能同时兼顾天上与水里两侧的情况。"},{"question":"豉甲在拥挤的水面群体中高速打转却很少相撞，主要依靠什么？","options":["提前商量好各自的路线","触角感受水面波纹反射，判断障碍与同伴位置","靠上方复眼死死盯住每一个同伴"],"answer":1,"explain":"豉甲游动时会激起细小波纹，触角基部的感受器能接收波纹被同伴或障碍物反弹回来的信号，据此实时调整路线，相当于一套水面声呐。"}]', '静水面上的社群', '常成群聚集在池塘、水沟等平静少扰动的水面，白天最为活跃，一旦水面泛起较大波浪或水质变差便会转移；豉甲体型微小却是水面昆虫群落里数量可观的常住居民，也是不少鱼类和水鸟捕食的对象。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'whirligig-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'ground-beetle', '[{"title":"鞘翅上的雕刻纹","body":"鞘翅表面遍布纵向隆起的脊线，脊线间还排着一行行细小凹点，如同精细雕刻过的纹理；这种沟脊交错的构造既加固鞘翅本身，也是野外辨认步甲科昆虫最直接的标志。","anchor":"elytra"},{"title":"为什么飞不起来","body":"多数大型步甲后翅已经退化，鞘翅在中缝处愈合无法掀开，天生就飞不起来；它们靠一双细长有力的步足在地面疾走弥补这个缺陷，捕猎和躲避天敌全靠奔跑速度，而非翅膀。","anchor":"leg"},{"title":"上颚怎样制敌","body":"发达的上颚呈镰刀状，前端尖锐锋利，捕猎时用来钳住蜗牛壳口或鳞翅目幼虫的身体，硬生生咬开对方外壳或表皮；成虫和幼虫都靠这副上颚主动出击，是农田里效率很高的天敌。","anchor":"mandible"},{"title":"触角怎么找猎物","body":"步甲多在夜间或阴湿环境下活动，视觉之外更依赖细长的丝状触角探测气味与地面震动；循着蜗牛爬行留下的黏液痕迹或幼虫活动的气息，触角能引导它精准找到藏身处的猎物。","anchor":"antenna"}]', '疾走突袭蜗牛', '中华金星步甲发现猎物后不会远距离扑击，而是靠细长有力的步足快速逼近，趁蜗牛来不及缩回壳内时用镰刀状上颚钳住其柔软的头足部；蜗牛壳这时反成了累赘，步甲甚至能把猎物一路拖拽到隐蔽处慢慢取食。', '[{"question":"多数大型步甲（包括中华金星步甲）为什么不能飞行？","options":["翅膀被鞘翅上的绒毛缠住无法展开","后翅退化、鞘翅在中缝愈合，天生失去飞行能力","体重过大，飞行肌力量跟不上"],"answer":1,"explain":"多数大型步甲后翅已经退化，鞘翅在中缝处愈合无法掀开，因而丧失飞行能力，只能依靠发达的步足在地面快速奔走。"},{"question":"中华金星步甲对付蜗牛、鳞翅目幼虫这类猎物，主要依靠什么？","options":["分泌消化液，在体外把猎物溶解后吸食","镰刀状上颚直接钳咬猎物身体或壳口","结网缠住猎物后再慢慢取食"],"answer":1,"explain":"步甲发达的上颚呈镰刀状，捕猎时直接钳咬猎物的壳口或身体，成虫与幼虫都是主动出击的捕食者，并不靠体外消化或结网。"}]', '落叶层下的夜行客', '白天多蛰伏在农田、林缘潮湿的落叶层、石块或土缝下躲避阳光与天敌，夜幕降临后才出来疾走猎食；蜗牛和鳞翅目幼虫密集的菜地、果园边缘是它们最常出没的猎场，也是重要的农业益虫栖身地。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'ground-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'blister-beetle', '[{"title":"头大颈细的轮廓","body":"头部宽大明显宽过前胸，中间收成一段细颈，前胸背板又窄又长，三段体节粗细分明；这副头大颈细、前胸收窄的独特轮廓，是野外一眼认出芫菁科昆虫的最直接线索。","anchor":"head"},{"title":"鞘翅为何软塌塌","body":"鞘翅质地柔软不硬化，末端也不能完全闭合覆盖腹部，静止时能看到腹背边缘微微露出；这与多数甲虫坚硬密合的鞘翅明显不同，摸起来也缺乏硬壳甲虫那种脆硬的触感。","anchor":"elytra"},{"title":"黄色体液有剧毒","body":"受到触碰惊扰时，中华豆芫菁会从足关节缝隙渗出一滴黄色体液，这是一种反射性出血；体液中含有斑蝥素，沾到皮肤会起水疱红肿，这正是英文名「blister beetle」的由来。","anchor":"leg"},{"title":"幼虫要换好几副模样","body":"幼虫要经历复变态：一龄幼虫身形纤细、行动活跃，四处寻找蝗虫卵块或蜂巢；找到食物来源后蜕皮变成臃肿迟钝、几乎不再移动的取食型，同一物种幼年时期竟像换了好几种昆虫。"}]', '受惊时的反射出血', '中华豆芫菁遇到惊扰时不会主动咬人蜇人，而是让足关节处的体液压力骤然升高，从关节缝隙渗出一滴黄色液体，这个动作叫反射性出血；液体里的斑蝥素接触皮肤后会引发红肿起疱，是它对付天敌最有效的化学防线。', '[{"question":"中华豆芫菁受到惊扰时，最典型的防御反应是什么？","options":["用上颚狠狠咬住对方皮肤","从足关节渗出含斑蝥素的黄色体液","腹部末端喷出酸性气体"],"answer":1,"explain":"受惊时它从足关节渗出黄色体液，这是反射性出血，体液中的斑蝥素接触皮肤会引发红肿起疱，并非靠撕咬或喷酸防身。"},{"question":"中华豆芫菁的幼虫在发育过程中，形态变化有什么特别之处？","options":["各龄幼虫形态几乎不变，只是体型逐渐变大","经历复变态，不同龄期形态和习性截然不同","幼虫阶段完全不取食，靠卵黄维持发育"],"answer":1,"explain":"这类幼虫经历复变态：一龄活跃寻找蝗虫卵块或蜂巢，之后蜕皮成臃肿不动的取食型，形态习性差异很大，并非等比例长大。"}]', '农田草地的季节客', '常见于农田、荒地和路边草丛，成虫多聚集在豆科等植物的花叶上取食，偶尔会成群暴发啃食叶片；由于体液有毒，触碰后可能引发皮肤红肿，采摘农作物或除草时若遇到成群芫菁需格外留意，避免直接用手捏碎。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'blister-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'hister-beetle', '[{"title":"截短的方形鞘翅","body":"体形紧凑近似方块，表面像上过漆一样光滑发亮；鞘翅末端截断变短，并不完全覆盖腹部末节，露出的那一小截腹背是阎甲区别于多数圆胖鞘翅甲虫的显著特征。","anchor":"elytra"},{"title":"装死时的收纳术","body":"遇到惊扰时，阎甲能把细足和触角完全收进体下的凹槽里，缩成一块紧实的黑色小方砖，几乎看不出是活物；这套装死本领比单纯静止不动更彻底，天敌很难从中找到下嘴的缝隙。","anchor":"tuckedLeg"},{"title":"猎物是蝇蛆不是尸体","body":"阎甲常年出没在腐尸与粪堆中，但它对腐肉本身或粪便都没有兴趣，真正猎食的是聚集在其中的蝇类幼虫（蛆虫）；靠这个隐藏身份，它在腐败环境里扮演的其实是捕食者而非清道夫的角色。","anchor":"head"},{"title":"破案线索里的常客","body":"因为在尸体不同腐败阶段出现的先后顺序相对固定，阎甲是法医昆虫学重点参考的类群之一；调查人员通过记录尸体上出现的阎甲种类和阶段，有时能据此推算死亡经过的大致时间。"}]', '全身收纳装死', '阎甲遇到威胁时不逃跑也不反击，而是瞬间把六足和触角收进体下凹槽，紧贴腹面缩成一块光滑坚硬的小方砖；天敌若咬不开坚硬的鞘翅外壳，往往很快便会放弃这块「石头」转而寻找别的猎物。', '[{"question":"阎甲经常出没在腐尸和粪堆中，它真正的食物是什么？","options":["腐烂的尸体组织本身","聚集在腐尸或粪便中的蝇类幼虫（蛆虫）","粪便中未消化的植物纤维"],"answer":1,"explain":"阎甲对腐肉和粪便本身并不感兴趣，它真正捕食的是聚集其中的蝇类幼虫，是腐败环境里的捕食者而非直接取食腐质的清道夫。"},{"question":"阎甲受到惊扰时的典型反应是什么？","options":["迅速展翅飞离现场","把足与触角收进体下凹槽，缩成紧实的小方砖装死","从鞘翅缝隙喷出刺激性气体"],"answer":1,"explain":"阎甲能把细足和触角完全收进体下凹槽，缩成一块紧实光滑的黑色小方砖静止不动，靠这种彻底的装死本领而非飞逃或喷气来躲避天敌。"}]', '腐尸粪堆的常住客', '常见于动物尸体、粪堆及腐烂的植物堆积处，靠嗅觉锁定这些蝇类幼虫聚集的临时栖地；随着尸体腐败阶段更替，出没的阎甲种类也会随之变化，这种规律的先后顺序正是它被法医昆虫学重视的原因。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'hister-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'treehopper', '[{"title":"头盔其实是前胸","body":"那顶造型夸张的「头盔」既不是头部也不是翅膀，而是前胸背板向后向上极度延伸出来的构造；不同种类角蝉的盔形千变万化，常拟态成植物的尖刺，用来迷惑鸟类等天敌的视线。","anchor":"helmet"},{"title":"喙管怎么吸汁","body":"头部下方伸出细长的刺吸式口器，取食时刺入植物茎秆表皮，直接吸食其中流动的汁液；角蝉终生以此为食，不具备咀嚼植物组织的能力，这点与蝗虫等咀嚼式口器昆虫完全不同。","anchor":"rostrum"},{"title":"蜜露换来的保镖","body":"多数角蝉的腹部末端会排出富含糖分的蜜露，蚂蚁对这份「甜头」十分买账，会守在附近驱赶瓢虫、寄生蜂等天敌；角蝉提供食物、蚂蚁提供保护，是昆虫界常见的互利共生关系。","anchor":"abdomen"},{"title":"受惊说走就走","body":"角蝉平时静伏在枝条上一动不动，靠拟态躲避天敌注意；一旦被真正触碰或逼近到危险距离，发达的后足会猛然蹬地，把身体弹射出去瞬间脱离原地，随后展翅补一段短距离飞行落到别处。","anchor":"hindleg"}]', '蚂蚁按时来挤奶', '蚂蚁会定时巡视角蝉聚集的枝条，用触角有节奏地拍打角蝉腹部末端催促排蜜，角蝉随即排出一滴蜜露供蚂蚁舔食；作为回报，蚂蚁群会持续驱赶靠近的瓢虫和寄生蜂，这种互动能持续一整个若虫生长期。', '[{"question":"角蝉头顶那个造型夸张的「头盔」，实际上是身体的哪个部位？","options":["特化增大的头部","前胸背板向后延伸形成的构造","折叠收拢起来的前翅"],"answer":1,"explain":"这顶「头盔」既非头部也非翅膀，而是前胸背板极度延伸的产物，常拟态植物尖刺，用来迷惑天敌，容易被误认成头部的一部分。"},{"question":"角蝉与蚂蚁之间常见的互动关系是什么？","options":["蚂蚁捕食角蝉，是它的天敌之一","角蝉分泌蜜露喂养蚂蚁，蚂蚁则帮忙驱赶天敌","两者只是偶然共享同一根枝条，没有实际往来"],"answer":1,"explain":"多数角蝉会分泌富含糖分的蜜露供蚂蚁取食，蚂蚁则驻守枝条驱赶瓢虫、寄生蜂等天敌，双方形成互利共生关系，蚂蚁并不捕食角蝉。"}]', '嫩枝上的伪装者', '常栖息在灌木或幼树带嫩皮的枝条上，体色和盔形贴合所在植物的刺或芽苞，静伏不动时很难与背景区分开；蚂蚁频繁巡视的枝条往往聚集着较多角蝉，蜜露交换关系让两者的分布常常紧密重叠。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'treehopper');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'ichneumon-wasp', '[{"title":"产卵器长得夸张","body":"雌蜂的产卵器可达体长的好几倍，细如发丝却极为坚韧，由中间一根真正的产卵管和两侧包裹保护的鞘丝共同构成；这套三丝结构既柔韧到能弯曲探入树干，又硬朗到能顶开粗糙木质。","anchor":"ovipositor"},{"title":"触角怎么听回声","body":"雌蜂用触角末端反复轻敲树皮表面，靠回传的震动判断内部是否有蛀道和幼虫存在，这套「听诊」本领帮它在完全看不见内部情况下精确定位寄主藏身的深度和位置。","anchor":"antenna"},{"title":"细腰撑住长产卵管","body":"姬蜂胸腹之间那处细窄的腰能大幅度弯折，产卵时身体几乎立起，把又长又细的产卵器精准对准树皮上选定的位置；没有这处灵活的细腰，体长有限的姬蜂根本无法操控数倍于己身的产卵器。","anchor":"waist"},{"title":"它其实不蜇人","body":"姬蜂那根醒目的长丝常被误认成巨大的螫针而让人心生畏惧，其实它只是产卵器，专门用来把卵送进树干深处的寄主体内，姬蜂本身既不主动攻击人，也没有蜇刺能力。"}]', '穿透木头去产卵', '雌蜂确定寄主位置后，把细长的产卵器垂直顶在树皮上，靠不断轻微旋转和身体重量施压，一点点顶穿数厘米厚的木质部；管道打通后，卵沿着产卵管滑入蛀道深处，精准产在天牛等蛀木幼虫的体表或体内。', '[{"question":"姬蜂那根醒目的长「丝」，很多人误以为是巨大的螫针，它实际上是什么？","options":["用来蜇刺天敌的加长螫针","用于把卵产入树干深处寄主体内的产卵器","感知气味和震动的加长触角"],"answer":1,"explain":"这根长丝是产卵器，由中间产卵管和两侧保护鞘丝构成，专门用来把卵产入树干深处的寄主体内；姬蜂并没有螫针，也不会主动蜇人。"},{"question":"姬蜂靠什么方法确定树干内部是否藏有可以寄生的寄主幼虫？","options":["用触角敲击树皮，根据回传震动判断内部情况","直接用产卵器反复试探整根树干","靠复眼透视树皮看清内部"],"answer":0,"explain":"雌蜂用触角反复轻敲树皮，根据回传震动判断内部是否有蛀道和寄主幼虫，这套「听诊」本领让它不必看见内部就能精确定位。"}]', '老树干里的寄主库', '常出没于生长天牛等蛀木性昆虫幼虫的衰老阔叶树干周围，雌蜂反复在树皮表面搜寻震动信号确定产卵地点；由于寄主资源集中在少数受害树木上，同一根枯木上常能见到多只姬蜂先后光顾产卵。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'ichneumon-wasp');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'dobsonfly', '[{"title":"长颚用来做什么","body":"雄虫头部两侧各生一支细长镰状大颚，静止时左右交叉伸出很远，望之如利刃；但这对长颚主要用于争夺配偶时的展示与抱持，实际咬合力反而并不算强，很少用来搏斗致伤。","anchor":"mandible"},{"title":"雌虫的颚更管用","body":"雌虫大颚明显比雄虫短小，形状也不那么夸张，但咬合力却更强；雄虫的长颚更像一件炫耀道具，真正需要防御或捕食时，反倒是雌虫那副其貌不扬的短颚更派得上用场。","anchor":"mandible"},{"title":"幼虫是水质哨兵","body":"幼虫俗称爬沙虫或水蜈蚣，长期栖息在水底石缝间捕食其他水生小动物，对水质极为敏感，只能生活在溶氧充足的清洁溪流中；哪里能找到它，往往意味着这段水域的水质相对洁净未受污染。"},{"title":"复眼怎么找同类","body":"成虫复眼发达，夜间活动时主要靠复眼感知同类和潜在配偶的动静；由于成虫寿命很短、多数个体几乎不再取食，复眼与长颚在这短暂的成虫期里几乎全部用于求偶和交配，而非觅食。","anchor":"eye"}]', '长颚交锁的求偶', '雄虫求偶时会用那对细长的镰状大颚小心地扣住雌虫的翅基或胸背，双方交叉缠绕却很少真正用力咬合；这个动作更像一场耐力比拼中的把持与试探，力道拿捏失当反而可能伤到本该争取的对象，因此雄虫格外克制。', '[{"question":"关于中华齿蛉雄虫那对夸张的长颚，下列说法正确的是？","options":["长颚咬合力极强，是它捕猎的主要武器","长颚主要用于求偶展示与抱持，实际咬合力反而较弱","长颚只在幼虫阶段存在，成虫会脱落"],"answer":1,"explain":"雄虫长颚看似凶猛，实际咬合力较弱，主要用于求偶展示与抱持；反倒是雌虫颚型短小，咬合力更强，与外观印象正相反。"},{"question":"中华齿蛉幼虫（俗称爬沙虫）常被用来判断什么？","options":["判断附近森林的树种构成","判断所在溪流水质是否洁净、溶氧是否充足","判断当地气温的季节变化幅度"],"answer":1,"explain":"齿蛉幼虫只能生活在溶氧充足的清洁溪流中，对水质极为敏感，因此它的出现常被当作判断一段水域是否洁净未受污染的指示信号。"}]', '溪流石缝间的幼年期', '幼虫栖息在山间溪流水底的石缝间，昼伏夜出捕食其他水生无脊椎动物，离不开水流湍急、溶氧充足的清洁环境；成虫羽化后飞离水面栖身溪边林地，寿命很短，多数个体一生几乎只为求偶交配这一件事忙碌。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'dobsonfly');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'goliath-beetle', '[{"title":"沉甸甸的巨体","body":"雄性体长可达11厘米，体重最重的个体超过80克，是世界上最重的昆虫之一；幼虫阶段体重甚至比成虫还要沉，堪称昆虫界的重量级选手。","anchor":"elytra"},{"title":"花纹为伪装","body":"鞘翅和前胸背板覆着一层丝绒质感的斑驳花纹，深浅交错如同树皮上的地衣，静伏枝干时能有效打破身体轮廓，让天敌不易第一时间发现它。","anchor":"elytra"},{"title":"头上的Y角","body":"雄虫头部前端伸出一支分叉的Y形角，与近缘的犀金龟异曲同工：食物丰富的树液伤口有限，雄虫间常以角相抵较力，把对手掀离取食点。","anchor":"headHorn"},{"title":"幼虫的隐忧","body":"幼虫栖身腐殖土和朽木碎屑间，却对蛋白质的需求远超多数金龟幼虫，营养跟不上便发育迟滞甚至夭折，是人工饲养里出了名难养的一类。"}]', '沉重却能飞', '论体重它称得上甲虫里的重量级，但鞘翅肩部同样留有侧向缺口，膜质后翅能不必掀开鞘翅直接探出扇动，起飞反应比外表看起来敏捷得多。', '[{"question":"大王花金龟的幼虫，人工饲养时最容易在哪个环节出问题？","options":["湿度太低，幼虫容易脱水","蛋白质需求远高于多数金龟幼虫，稍不注意就发育不良","幼虫畏光，必须全程避光饲养"],"answer":1,"explain":"大王花金龟幼虫对蛋白质需求远超多数金龟幼虫，供给跟不上会发育迟滞甚至死亡，这正是它格外难人工繁育的原因。"},{"question":"大王花金龟成虫的主要食物是什么？","options":["捕食其他昆虫补充蛋白质","吸食树液与熟透的果实","专门取食植物的根系"],"answer":1,"explain":"大王花金龟成虫主要吸食树液和熟透落果的糖分，并不捕食其他昆虫；真正需要高蛋白食物的是幼虫，两代食性截然不同。"}]', '雨林树冠营地', '分布于赤道非洲的热带雨林，成虫多活动在高大乔木的树液伤口和熟透落果附近，雨季气温回暖时最为活跃；野外若想遇见，多留意林间空地边缘渗着树液的老树干。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'goliath-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'bombardier-beetle', '[{"title":"橙黑双色","body":"头部与前胸背板多呈橙红色，鞘翅颜色深暗，体形细长擅长快速奔跑，是步甲家族里最广为人知的化学武器专家，遇到威胁时从不手软。","anchor":"elytra"},{"title":"两间反应室","body":"腹部末端藏着两间小室：外层存着过氧化氢和对苯二酚溶液，内层贴着催化用的酶；平时互不相通，受到威胁时才由括约肌控制混合。","anchor":"sprayTip"},{"title":"喷嘴会转向","body":"喷射口开在腹部最末节，构造灵活可朝各个方向转动，无论天敌从哪个角度咬住它的腿或身体，都能精准调转喷口对准来袭方向还击。","anchor":"sprayTip"},{"title":"夜行的猎手","body":"多数步甲科昆虫夜间活动、捕食小型无脊椎动物为生，屁步甲也不例外；强大的化学武器让它在天敌环伺的地面世界里几乎无需惧怕近身偷袭。"}]', '爆炸式怒喷', '腹端储液室里的过氧化氢与对苯二酚被推入反应室，遇到酶催化瞬间爆发化学反应，喷出接近100摄氏度的苯醌蒸汽，以每秒数百次的高频脉冲连续喷出而非持续直流，可旋转的喷嘴能精准对准袭击者。', '[{"question":"屁步甲能瞬间喷出灼热气体却不会伤到自己，关键在哪里？","options":["体表覆盖一层耐高温的蜡质，能隔热","两种化学物质平时分储两室，遇袭才混合催化反应","喷发前会先分泌黏液包裹住自己的腹部"],"answer":1,"explain":"两种化学物质平时分别储存在不同小室里互不接触，只有遇到威胁时才被推入内层反应室，经酶催化瞬间反应放热，因此不会伤到自己。"},{"question":"屁步甲喷出的高温气体，实际是以什么方式释放的？","options":["持续不断地直流喷出，像水管一样","以极高频率分成许多次脉冲喷出，而非连续直流","只喷一次就耗尽存货，之后靠恐吓"],"answer":1,"explain":"高速摄影揭示，屁步甲的喷射其实是每秒数百次的连续脉冲，而非持续直流，这种间歇式喷发也让灼热气体不会在体内滞留太久。"}]', '落叶下的据点', '白天多潜伏在落叶堆、石块或朽木下方阴湿处，夜间才出来活动觅食；夏季雨后翻开林缘湿润的落叶层或搬开石块，常能遇见它疾走或喷出白烟自卫。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'bombardier-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'darkling-beetle', '[{"title":"圆鼓的甲壳","body":"体色乌黑，背部拱得很高像一枚倒扣的小碗，鞘翅表面常有细密的瘤突或纵纹，是荒漠拟步甲家族里体形敦实的一员。","anchor":"pronotum"},{"title":"鞘翅已焊死","body":"左右两片鞘翅在背中线彻底愈合成一整块硬壳，翅下的后翅严重退化，这意味着它这辈子都无法飞行，只能靠六足在沙地上慢慢跋涉。","anchor":"fusedElytra"},{"title":"壳下的气室","body":"愈合的鞘翅与背部之间留出一层密闭空气夹层，如同一件贴身背心，既缓冲沙漠里昼夜悬殊的温差，也减少体表水分被干燥气流带走。","anchor":"abdomen"},{"title":"昼伏夜出","body":"白天沙漠地表温度过高，它多蛰伏在浮沙下或石块阴影里；等到黄昏气温回落才出来活动，靠取食干枯植物残屑和其他小型腐殖质为生。"}]', '长腿踏地降温', '沙地正午地表温度极高，它靠细长的六足把身体高高撑离滚烫的沙面，减少身体与地表直接接触的面积；这种「踮脚」姿势能明显降低从地面传来的热量。', '[{"question":"甘肃鳖甲的鞘翅在背部彻底愈合，这意味着什么？","options":["它这一生都失去了飞行能力","它的鞘翅其实比翅膀更硬更重","它只能在幼虫期短暂飞行"],"answer":0,"explain":"鞘翅愈合、后翅退化后便无法复原，甘肃鳖甲从成虫羽化那一刻起就注定终生不能飞，只能靠双足在地面活动觅食。"},{"question":"甘肃鳖甲鞘翅下方密闭的空气夹层，主要作用是什么？","options":["缓冲昼夜温差、减少水分散失，是荒漠保水的适应","收集清晨雾气凝结的水滴供自己饮用","储存空气供它短暂潜入沙层呼吸"],"answer":0,"explain":"这层空气夹层缓冲昼夜温差、减少体表水分蒸发，是荒漠保水适应；纳米布沙漠靠鞘翅倒立收集雾水的是另一类拟步甲，不能混为一谈。"}]', '戈壁沙地深处', '分布在中国西北甘肃一带的荒漠戈壁，白天蛰伏于沙丘背阴处或稀疏植丛根部；清晨或傍晚气温适中时最容易见到它顶着乌黑的鞘翅缓缓爬行。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'darkling-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'net-winged-beetle', '[{"title":"鲜红的鞘翅","body":"整个身体多呈鲜红或橙红色，鞘翅扁平柔软，表面纵向隆起的脊线纵横交错，织成网眼一样的纹理，红萤之名正是由此而来。","anchor":"elytra"},{"title":"鞘翅软不硬","body":"与多数硬壳鞘翅不同，红萤的鞘翅质地柔韧接近皮质，用手轻捏能感觉到明显的弹性，这也是野外区分它和萤火虫、瓢虫的一个小窍门。","anchor":"ridge"},{"title":"红色是警告","body":"鲜艳的红色并非为了好看，而是明确的警戒色：体内含有令天敌不快的化学物质，鸟类啄食一次尝到苦头，往往会牢牢记住这身配色并从此回避。","anchor":"pronotum"},{"title":"并不会发光","body":"红萤和萤火虫常因名字或外形被混为一谈，其实它并不能发光；真正会发光的萤火虫属鞘翅目萤科，红萤则是完全不同的一个科，习性也不相通。"}]', '拟态的样板', '红萤本身并不特别凶猛，真正厉害的是它这身配色被大量借用：不少无毒昆虫演化出几乎相同的红黑或红黄花纹冒充它，天敌避开红萤时也顺带放过了这些冒牌货。', '[{"question":"红萤浑身鲜红显眼，这种体色的主要作用是什么？","options":["帮助在花丛间隐藏自己","警告天敌自己不好吃，体内有防御性物质","求偶炫耀，与警戒无关"],"answer":1,"explain":"红萤鲜红的体色是典型的警戒色，对应体内让天敌不快的化学物质；天敌吃过一次苦头后会记住这身配色，此后主动回避而非靠近。"},{"question":"关于红萤，下列说法正确的是？","options":["红萤和萤火虫一样，夜间腹部会发光","红萤不会发光，常被无毒昆虫拟态其配色骗过天敌","红萤靠发光信号在夜间寻找配偶"],"answer":1,"explain":"红萤本身并不会发光，这一点常与萤火虫混淆；它的红黑配色是警戒色的经典模板，不少无毒昆虫演化出相似花纹蹭它的安全招牌。"}]', '阔叶林缘', '成虫常栖息在阔叶林缘、灌丛或花丛间，白天活动，靠鲜艳体色大摇大摆示警而不必躲藏；幼虫则潜伏在朽木和落叶层下取食腐殖质，很少露面。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'net-winged-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'leaf-beetle', '[{"title":"黄底黑纹","body":"体形椭圆而扁平，底色黄绿，鞘翅上纵贯着几道清晰的黑色条纹，前胸背板还缀着几枚黑斑，是榆树叶片上最常见的黄黑相间小甲虫。","anchor":"elytra"},{"title":"触角找寄主","body":"丝状触角能敏锐地捕捉榆树叶片散发的气味分子，成虫循着这股气味准确找到附近的榆树，很少在其他树种上停留取食。","anchor":"antenna"},{"title":"成虫啃穿叶","body":"成虫上颚咀嚼力强，直接把叶片啃出一个个不规则的圆孔，严重时整片榆树叶被吃得千疮百孔，是榆树最主要的食叶害虫之一。","anchor":"head"},{"title":"幼虫啃叶肉","body":"幼虫口器较弱，只啃食叶片背面柔软的叶肉，留下网状的叶脉不吃，被啃过的叶片透光如纱；老熟后幼虫成群爬向树干基部聚集化蛹。"}]', '集体爬树干', '老熟幼虫吃光叶肉后不会就地化蛹，而是成群结队顺着树干向下爬，行动虽不整齐却方向一致；聚在基部树皮缝隙或松土里化蛹，能避开天敌频繁光顾的树冠层。', '[{"question":"榆蓝叶甲成虫和幼虫啃食榆树叶片的方式，有什么不同？","options":["成虫啃穿整片叶肉留孔，幼虫只啃叶背叶肉留下网状叶脉","两者吃法完全一样，只是幼虫食量更大","成虫只喝汁液不咬叶片，幼虫才啃食叶肉"],"answer":0,"explain":"成虫上颚有力，直接把叶片啃出穿孔；幼虫口器较弱，只刮食叶背柔软的叶肉，留下网状叶脉不吃，两种为害痕迹在野外很容易区分。"},{"question":"老熟的榆蓝叶甲幼虫，化蛹前通常会怎么做？","options":["就地在原来取食的叶片背面结蛹","成群爬下树干，聚集在基部化蛹","钻入土壤深处独自化蛹"],"answer":1,"explain":"老熟幼虫吃光叶肉后会离开树冠，成群顺着树干向下爬，聚集在基部树皮缝隙或松土中化蛹，虫害严重时常能看到树干上满是幼虫。"}]', '榆树的叶片', '几乎哪里种榆树，哪里就能见到它，城市行道树、公园和苗圃的榆树是主要栖身地；春夏之交新叶展开时虫口数量常明显上升，仔细翻看叶背常能发现聚集的幼虫。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'leaf-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'damselfly', '[{"title":"细如线的腹","body":"腹部极细极长，几乎和一根线一样纤细，这是它和体形粗壮的蜻蜓最容易区分的地方；停栖枝头时纤细的腹部常随微风轻轻摆动。","anchor":"abdomen"},{"title":"歇息时收翅","body":"停栖不飞时，豆娘的四片翅膀会竖直合拢立在背脊上方；同样停栖的蜻蜓则是两对翅平展向两侧张开，这一姿势是野外分辨两者最直接的方法。","anchor":"wing"},{"title":"两眼离得远","body":"左右两只复眼分得很开，中间隔着明显的空隙；蜻蜓的两只复眼则几乎在头顶碰在一起，这处头部特征同样是区分二者的可靠标志。","anchor":"eye"},{"title":"尾部有鳃","body":"稚虫阶段生活在水中，尾端长着三片薄片状的尾鳃用来呼吸，外形与蜻蜓稚虫明显不同；成虫飞行能力也偏弱，很少像蜻蜓那样长时间悬停或高速追击。"}]', '贴草丛短飞', '豆娘前后翅形状与大小几乎相同，飞行肌不如蜻蜓发达，很少长时间巡航；它更擅长在水草间做短距离的振翅悬停或轻盈起落，捕食蚊蚋等小型飞虫也多在这类近距离出击。', '[{"question":"豆娘和蜻蜓停栖不飞时，翅膀姿态有什么明显区别？","options":["豆娘四翅竖立合拢，蜻蜓两对翅平展向两侧","两者停栖姿态完全相同，无法区分","豆娘翅膀完全收进身体内部"],"answer":0,"explain":"豆娘停栖时四片翅膀竖直合拢立在背上，蜻蜓则是两对翅平展向两侧张开；这一停栖姿态的差异，是野外快速分辨两者最直接的方法。"},{"question":"仅从复眼的排列方式，怎样区分豆娘和蜻蜓？","options":["豆娘两眼分得很开，蜻蜓两眼几乎相接","豆娘只有一只复眼，蜻蜓有两只","两者复眼位置和距离完全一样"],"answer":0,"explain":"豆娘左右复眼分得很开、中间有明显空隙，蜻蜓的两只复眼则几乎在头顶碰到一起；这是区分二者最可靠的头部特征之一。"}]', '水边草丛', '栖息在池塘、溪流和沼泽边缘长满水草的地方，成虫白天沿岸低飞或停栖在草茎上晒太阳；夏季晴朗的清晨最容易见到它停在水边植物上，等翅膀上的露水晒干才起飞活动。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'damselfly');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'orchid-mantis', '[{"title":"粉白似花瓣","body":"身体以粉白为主色调，四片延展的腿节边缘圆润、色泽剔透，乍看与兰花的花瓣几乎没有分别，是昆虫界数一数二的拟态高手。","anchor":"abdomen"},{"title":"腿节变花瓣","body":"中足和后足的腿节向两侧扁平延展，边缘勾出圆弧形的轮廓，远远看去就像一朵花伸出的四片花瓣，这一伪装只出现在中后足，前足并不参与。","anchor":"petalLeg"},{"title":"前足仍捕猎","body":"伪装成花瓣的只是中后足，前足依旧保留着典型的螳螂捕捉足构造，内侧列刺锋利，静静等在花心位置，随时准备钳住送上门的猎物。","anchor":"raptorialLeg"},{"title":"幼体更艳丽","body":"初孵若虫体色往往比成虫更鲜艳夺目，随着一次次蜕皮逐渐调整深浅，好让自己的颜色始终贴近所栖息花朵的色调，伪装效果并非一成不变。"}]', '主动引蝶来', '多数拟态昆虫靠伪装躲避天敌，兰花螳螂却反其道而行：它并非藏在花丛里，而是自己站在枝头充当一朵假花，主动吸引蜜蜂、蝴蝶靠近；研究发现，它对传粉昆虫的吸引力甚至超过附近真正的花朵。', '[{"question":"兰花螳螂身体上，哪个部位负责伪装成花瓣？","options":["全部三对足都变成了花瓣状","中足和后足的腿节，前足仍是捕捉足","只有翅膀边缘模仿花瓣形状"],"answer":1,"explain":"兰花螳螂的伪装只集中在中足和后足的腿节，扁平延展成花瓣形状；前足依然是典型的捕捉足，负责在猎物靠近时出击捕获。"},{"question":"关于兰花螳螂的拟态方式，下列说法正确的是？","options":["它躲在真花后面，趁蜂蝶采蜜时偷袭","它本身模拟成一朵花，主动吸引传粉昆虫靠近，实验显示吸引力甚至超过真花","它模仿花朵的气味，靠嗅觉而非外形吸引猎物"],"answer":1,"explain":"兰花螳螂不是躲藏在花中，而是自身外形就是一朵假花，主动吸引蜂蝶靠近；研究测得它对传粉昆虫的吸引力甚至超过真花。"}]', '热带雨林花间', '原产东南亚热带雨林，多栖息在开花灌木或藤本植物的花序附近，静伏不动等候访花的蜂蝶自投罗网；野外想遇见它，最好在花期茂盛的林下灌丛中细细寻找那朵不会动的花。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'orchid-mantis');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'dead-leaf-butterfly', '[{"title":"翅尖似叶尖","body":"双翅合拢竖立时，前翅顶角向外拉出一个尖角，恰好对应树叶顶端那个尖尖的叶尖，整体轮廓一下子就有了叶片的模样。","anchor":"forewing"},{"title":"尾突是叶柄","body":"后翅末端延伸出一根细细的尾突，正好落在整片叶形的另一端，看起来活脱脱就是叶柄，连这个细节都没有被落下。","anchor":"tail"},{"title":"主脉与霉斑","body":"翅腹面呈枯黄或褐色，一道深色纵纹贯穿全翅像极了叶片中央的主脉，四周还散布着深浅不一的斑点，如同叶片上的霉斑和虫蛀痕迹。","anchor":"underwing"},{"title":"翅色因地而变","body":"同一物种不同个体的枯叶纹路深浅、斑点位置都不尽相同，如同真正落叶也各有差异；这种个体间的差异让天敌很难总结出一套统一的识别规律。"}]', '飞时突然变色', '枯叶蛱蝶双翅一旦张开飞行，翅面立刻露出鲜蓝与橙红交织的背面，与静止时的枯叶模样判若两只蝶；一旦停落收拢双翅，艳色瞬间消失，天敌常因这突兀的一亮一暗而失去追踪目标。', '[{"question":"枯叶蛱蝶双翅合拢竖立时，露在外面的是翅膀的哪一面？","options":["背面，也就是鲜艳的蓝橙配色那一面","腹面，褐色带主脉纹和斑点，酷似枯叶","侧面，一条细缝完全看不见花纹"],"answer":1,"explain":"双翅竖直合拢时露出的其实是翅膀腹面，褐色打底、主脉纹与斑点俱全；鲜艳的蓝橙色其实长在背面，只有张开双翅飞行时才会显露。"},{"question":"枯叶蛱蝶张开双翅飞行、露出鲜艳背面时，对躲避天敌有什么作用？","options":["没有作用，纯粹是求偶炫耀","骤然显露的鲜艳色彩会让追击的天敌一时错愕，落地收翅后又瞬间隐身不见","鲜艳色彩能反射紫外线灼伤天敌的眼睛"],"answer":1,"explain":"这是一种闪色防御：飞行时突然亮出鲜艳翅面让天敌一时失神，一旦停落收翅立刻变回枯叶模样，追击者常因此跟丢目标。"}]', '林下落叶间', '常栖息在常绿阔叶林林下阴凉处，停落时多选枯枝败叶堆积的地方隐蔽；成虫不常访花，更爱聚在腐烂落果和树干伤流处吸食汁液，秋冬季林间小径上格外容易与它撞个正着。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'dead-leaf-butterfly');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'hawk-moth', '[{"title":"流线型身体","body":"身体粗壮呈纺锤形，前窄后宽像一枚小小的鱼雷，翅膀狭长而坚挺，这样的体形正是它能够快速振翅、悬停取蜜的基础。","anchor":"abdomen"},{"title":"振翅像蜂鸟","body":"两对狭长的翅膀每秒能振动约70到90次，扇动时几乎看不清轮廓，只留下一团模糊的翅影和嗡嗡声，悬停取食的姿态与蜂鸟极为相似。","anchor":"forewing"},{"title":"喙比身体长","body":"细长的喙管平时卷曲如发条藏在头下，取食时才伸展开来，展开后的长度甚至超过自己的身体，能探入花冠深处吸取藏得很深的花蜜。","anchor":"proboscis"},{"title":"常被认错","body":"悬停取蜜的姿态和体形常让人误以为看到了蜂鸟，但中国本土并没有蜂鸟分布，眼前这只小型飞行者其实是一只货真价实的蛾子。"}]', '悬停如蜂鸟', '取食时它能在花朵前方原地悬停不动，靠双翅高速划出小幅度的8字形轨迹，每秒振动约70到90次带来持续升力；这套飞行方式与蜂鸟高度趋同，却是完全独立演化而来的结果。', '[{"question":"有人说在中国花丛边看到了「蜂鸟」，最可能的真相是什么？","options":["中国确实分布着极少数蜂鸟","看到的其实是悬停吸蜜的天蛾，中国并无蜂鸟分布","是蜂鸟迁徙路过时的偶然一瞥"],"answer":1,"explain":"中国本土并没有蜂鸟分布，蜂鸟只生活在美洲；悬停取蜜、快速振翅的其实是天蛾，二者外形和行为高度趋同却是不同的动物类群。"},{"question":"小豆长喙天蛾取食时展开的喙管，长度大致如何？","options":["远短于身体，只有体长的十分之一左右","接近或超过自己的身体长度","喙管无法伸展，始终卷曲不用"],"answer":1,"explain":"小豆长喙天蛾的喙管平时卷曲藏在头下，取食时伸展开来，长度往往接近甚至超过自身体长，能探入花冠深处吸取花蜜。"}]', '花丛间访花', '常见于花园、田埂和向阳的野花丛，白天尤其是阳光充足的午后活动最活跃；只要蹲守一片盛开的忍冬或百日草，很有机会看到它悬停在花前伸喙取蜜的场面。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'hawk-moth');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'termite-soldier', '[{"title":"头大颚更大","body":"头部比例明显大于其他工蚁和幼蚁，一对镰刀状大颚几乎占去半张脸，浅色柔软的身躯与厚重的头部形成鲜明反差，一眼就能认出这是负责防卫的兵蚁。","anchor":"head"},{"title":"没有复眼","body":"头部两侧本该长复眼的位置光秃秃一片，兵蚁其实是瞎子，终生生活在完全黑暗的巢道里，全靠触角感知气味、震动和同伴留下的信息素。","anchor":"head"},{"title":"大颚不能吃","body":"这对发达的大颚构造只适合刺穿和撕咬，用来对付入侵蚁类或其他天敌，却完全没有咀嚼研磨食物的功能，兵蚁靠它打仗，却吃不了饭。","anchor":"mandible"},{"title":"工蚁来喂饭","body":"自己咬不动、也嚼不烂食物，兵蚁的一日三餐全靠工蚁口对口反刍喂食；离开工蚁的照顾，就算食物近在眼前，兵蚁也无法独自进食维生。"}]', '开颚就是战斗', '巢穴通道一旦被入侵者攻破，兵蚁会第一时间冲到缺口处，张开大颚死死咬住来犯的蚂蚁或其他天敌，靠这记近乎反射的一咬为身后的工蚁和蚁后争取撤退时间。', '[{"question":"白蚁和蚂蚁看起来很像，分类上二者的关系是什么？","options":["白蚁其实是蚂蚁的一个亚科，属于膜翅目","白蚁属蜚蠊目，与蟑螂近缘；蚂蚁属膜翅目，两者只是趋同演化","两者是同一个目，只是体色和体形不同"],"answer":1,"explain":"白蚁在分类上属于蜚蠊目，与蟑螂是近亲；蚂蚁则属于膜翅目，和蜜蜂胡蜂更接近，两者的相似之处只是长期趋同演化的结果。"},{"question":"关于黑翅土白蚁兵蚁，下列说法正确的是？","options":["复眼发达、视力极佳，靠视觉指挥工蚁作战","没有复眼，是瞎子；大颚只能咬不能嚼，得靠工蚁喂食","会自己咀嚼食物，但需要工蚁帮忙寻找食物来源"],"answer":1,"explain":"兵蚁头部没有复眼、完全看不见，全靠触角感知环境；大颚只能撕咬防御却不能咀嚼，进食全靠工蚁口对口反刍喂养才能维持生命。"}]', '地下的巢道', '黑翅土白蚁营巢于地下，蚁巢向外延伸出泥质的行军甬道贴在树干或墙面上，兵蚁常驻守在甬道破损处和巢穴入口；夏季闷热潮湿的雨后，最容易在墙根或枯木附近见到这些泥道。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'termite-soldier');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'water-scavenger', '[{"title":"触角怎么换气","body":"浮近水面时它把头微微一侧，短棒状触角穿破水膜，空气顺着触角上的疏水绒毛一路导入腹面的银色气膜——整套换气动作全程不必把身体探出水面，天敌很难察觉。","anchor":"antenna"},{"title":"下颚须当触角","body":"触角改了行，探路的差事便交给一对细长的下颚须：它们比触角还长，游动时不停摆动，负责嗅出水草与腐屑的气味——别的甲虫用触角干的活，它用「胡须」来干。","anchor":"palp"},{"title":"腹下的龙骨刺","body":"翻过身看，胸部腹面有一条纵贯的龙骨状中脊，末端拖出一根锐利长刺；被鱼类含进口中时，这根刺会狠狠抵住对方口腔，逼它把到嘴的猎物再吐出来。","anchor":"keel"},{"title":"幼虫才是猛兽","body":"成虫性情温和，以水草与腐殖碎屑为主食；幼虫却是凶猛的猎手，一对弯钩状大颚专捕水中的螺类和小虫——同一种虫的两个阶段，食性几乎调了个个儿。"}]', '交替划水的桨', '游泳时它的中后足左右交替蹬水，像人走路一样一下一下轮着来，身体因此微微左右摇摆；同为水生甲虫的龙虱则是双足同步齐划、箭一般窜出——看游姿，就能把这两类水中甲虫分开。', '[{"question":"大黑水龟虫和龙虱都在水下生活，怎样从游姿上把它们分开？","options":["水龟虫左右足交替划水，龙虱双足同步齐划","水龟虫双足同步齐划，龙虱左右足交替划水","两者游姿完全相同，只能靠体色区分"],"answer":0,"explain":"水龟虫游泳像走路，左右足轮流蹬水、身体微摇；龙虱双足同步齐划、迅捷流畅——游姿是野外区分两者的好线索。"},{"question":"大黑水龟虫短棒状的触角，主要用来做什么？","options":["像多数甲虫一样负责嗅觉探路","穿破水膜，把空气导入腹面气膜来换气","格斗时当作武器互相抽打"],"answer":1,"explain":"它的触角改行管换气：穿破水面把空气沿疏水毛导入腹面气膜；嗅触探路的差事，则交给了那对细长的下颚须。"}]', '静水草塘里', '水草茂密的池塘、稻田和静缓沟渠是它的家，夏季最活跃；白天贴着水草缓缓爬游，腹面裹一层银亮气膜像镀了水银，夜里还会起飞趋光——路灯下捡到的大黑甲虫，常常就是它。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'water-scavenger');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'checkered-beetle', '[{"title":"红黑横带警告","body":"鞘翅蓝黑打底，横贯三道鲜红宽带，停在花朵上远远就能认出；这身高调配色是警戒色，宣告自己滋味不佳，让鸟类掂量掂量再决定要不要下嘴。","anchor":"band"},{"title":"一身竖立硬毛","body":"全身密生竖立的长硬毛，在花间钻进钻出时会挂上一层花粉，转场时顺路替植物完成授粉；这层毛同时也是触觉哨兵，身后的风吹草动都逃不过它。","anchor":"fuzz"},{"title":"花上的猎手","body":"成虫守在伞形花序上，既啃花粉充饥，也顺手捕食前来访花的小甲虫和蝇类，荤素搭配来者不拒；发达的复眼帮它盯住花面上往来的每一位访客。","anchor":"eye"},{"title":"幼虫钻进蜂巢","body":"雌虫把卵产在蜂类出没的花上或巢址附近，孵出的幼虫伺机钻进独居蜂的巢室，以蜂的幼虫和蛹为食长大——成虫在花上吃素为主，幼虫却在蜂巢里开荤。"}]', '疾走突袭访客', '它在花序上跑动极快，六足交替发力、忽停忽进，看准落在花面的小蝇小甲虫便猛冲过去一口按住；这份短距离爆发力配上花丛做掩护，让它成了伞形花序上最敏捷的伏击者。', '[{"question":"中华郭公虫的幼虫靠吃什么长大？","options":["花粉与花蜜，和成虫一样以素食为主","钻进蜂巢，捕食蜂的幼虫和蛹","腐烂的落叶与朽木碎屑"],"answer":1,"explain":"成虫常在花上取食花粉兼捕小虫，幼虫却钻进独居蜂等蜂类的巢室，以蜂的幼虫和蛹为食——两个阶段食性大不相同。"},{"question":"郭公虫鞘翅上红黑相间的鲜艳横带，最主要的作用是？","options":["警戒色，警告天敌自己滋味不佳","求偶信号，颜色越艳越受青睐","拟态花瓣，方便在花丛中隐身"],"answer":0,"explain":"红黑横带是典型警戒色，高调宣示自己不好吃，让鸟类见色却步；许多有毒或味劣的昆虫都采用类似的醒目配色。"}]', '初夏伞形花上', '初夏到盛夏的晴天，去田埂和山坡找盛开的伞形花序——胡萝卜花、蛇床都行，常能看到这身红黑条纹在花面上疾走；养壁蜂授粉的果园巢箱边，偶尔也能遇见来产卵的雌虫。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'checkered-beetle');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'shining-chafer', '[{"title":"铜绿从哪来","body":"鞘翅的铜绿色泽并非颜料染成，而是表层微观结构让光波干涉的结果：换个角度看去绿中泛金、金里透红，像一层会流动的金属光——颜色藏在结构里，不在色素里。","anchor":"elytra"},{"title":"唇基小护板","body":"头部前缘有一块宽扁上翘的唇基，像给嘴巴装了个小护板，取食时护住收拢的口器；它的轮廓形状在丽金龟家族里因种而异，是分类学家认种的重要依据。","anchor":"clypeus"},{"title":"触角开合扇","body":"触角末端由三枚薄片组成，能像折扇一样开合：收拢时是根小短棒，张开时薄片彼此分离，接收气味的面积成倍放大，帮它在夜色里循味找到成片的杨树与果树。","anchor":"antenna"},{"title":"幼虫叫蛴螬","body":"幼虫就是农人熟知的蛴螬，白胖的身体弯成C形，藏在土里啃食花生、玉米等作物的根；成虫夏夜上树群聚啃叶——一生的两个阶段，分别祸害植物的地下与地上。"}]', '一碰就装死', '取食中的成虫一旦察觉枝条震动，会立刻收拢六足从叶面滚落，坠进草丛后一动不动地装死，几秒到几十秒后再翻身爬走或起飞；这套假死坠落，是金龟子家族对付天敌的看家本领。', '[{"question":"把一只铜绿丽金龟泡进酒精，它的铜绿色会怎样？","options":["很快褪成灰白，颜色被酒精溶走","不会褪色——颜色来自微观结构而非色素","变成红色，酒精改变了色素成分"],"answer":1,"explain":"铜绿色是微观结构让光波干涉出的结构色，不溶于酒精所以不褪；若是色素色，才会被溶剂溶去而褪色。"},{"question":"农田害虫「蛴螬」与铜绿丽金龟是什么关系？","options":["蛴螬正是它的幼虫，在土中啃食作物根系","蛴螬是一类蛾的幼虫，与它并无关系","蛴螬是它的天敌，专门取食金龟子卵"],"answer":0,"explain":"蛴螬是金龟子类幼虫的统称：铜绿丽金龟幼虫在土中啃根，成虫上树啃叶，地下地上都是农业防治的重点对象。"}]', '夏夜杨树梢', '六七月的闷热夜晚最容易见到：路灯下常有撞灯的铜绿身影，杨树、榆树和苹果园的枝叶间成群聚食，一树叶子几夜就能被啃成网状；白天钻回浅土或伏在叶背，很少露面。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'shining-chafer');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'assassin-bug', '[{"title":"弯喙怎么开饭","body":"粗壮的喙平时弯折收在头下，出击时向前挺直刺入猎物体壁，先注入含消化酶的唾液把内部组织化成汁，再反向抽吸——把消化搬到猎物体内去完成，这就是体外消化。","anchor":"rostrum"},{"title":"喙还会拉琴","body":"前胸腹面有一道刻满细横纹的纵沟，遇袭时它把喙尖插进沟里来回刮动，发出吱吱声响示警；徒手捉它常会挨上痛痛的一刺，野外观察最好只看不碰。","anchor":"pronotum"},{"title":"前足像老虎钳","body":"前足粗壮，胫节内侧密生短刺，末端还有黏着力强的毛垫，扑住猎物的瞬间像钳子一样收紧压制，让喙从容选好下针的位置；出手前它还常用前足轻触试探对方虚实。","anchor":"foreleg"},{"title":"蝽里的异类","body":"同为半翅目，多数蝽把喙插进植物茎叶里吸汁，猎蝽却把同一套刺吸口器改造成猎枪，专门对付昆虫；从蚜虫到毛虫都在食谱上，果园菜地里它是受欢迎的天然卫士。"}]', '一刺定乾坤', '猎蝽悄悄逼近后猛然出手，前足按住猎物、喙同步下刺，专挑头胸间的柔软缝隙下针；注入的唾液几秒内便让猎物瘫软，此后它举着这份「战利品」慢慢吸食，全程不慌不忙。', '[{"question":"猎蝽吃掉一只毛虫的方式，是下面哪一种？","options":["用大颚把毛虫嚼碎后吞下","注入消化液把毛虫内部化成汁再吸回","整只吞进肚里慢慢消化"],"answer":1,"explain":"猎蝽没有咀嚼式口器，靠喙注入含消化酶的唾液，在猎物体内先完成消化再吸回汁液——这套吃法叫体外消化。"},{"question":"猎蝽遇袭时发出的吱吱声，是怎么发出来的？","options":["振动双翅摩擦腹部边缘","喙尖在前胸腹面的横纹沟里来回刮动","从气门快速排气吹出哨音"],"answer":1,"explain":"它把喙尖插进前胸腹面刻满细横纹的纵沟里，来回刮动如拉琴弓——蟋蟀用翅摩擦发声，猎蝽拉响的则是自己的喙。"}]', '草丛伏击场', '灌丛、草地、果园和农田都有它的伏击点，夏秋季白天在叶面枝头缓步搜猎最易观察，部分种类夜里还会趋光飞到路灯下；记住只看不碰——被猎蝽刺一下，比蜂蜇还要疼。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'assassin-bug');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'bumblebee', '[{"title":"绒毛是棉袄","body":"全身覆盖浓密长绒毛，既能兜住花粉，更是一件保温棉袄：配合飞行肌产热，让熊蜂在接近零度的清晨照样出勤——高山与早春的花，多半只等得到它这一位授粉客。","anchor":"fuzz"},{"title":"振的是胸腔","body":"飞行肌并不直接拉动翅根，而是反复压缩、回弹整个胸腔，让翅膀被胸壁的高频形变甩动起来；这套「间接飞行肌」，是蜂、蝇等高频振翅昆虫共用的发动机方案。","anchor":"wing"},{"title":"花粉篮在后腿","body":"后足胫节外侧光滑微凹，四周围着一圈长毛，构成一只挂在腿上的货篮；工蜂把绒毛兜住的花粉刷拢、蘸花蜜压实进篮，满载时两条后腿各挂一团鲜黄的花粉球。","anchor":"pollenBasket"},{"title":"抖出花粉雨","body":"番茄、蓝莓的花药像只扎了小孔的胡椒瓶，花粉藏在管里不肯出来；熊蜂咬住花药，让飞行肌脱开翅膀空转，用高频震动把花粉筛得纷纷洒落——这手声波授粉，蜜蜂学不会。","anchor":"abdomen"}]', '颤抖预热起飞', '低温的清晨，熊蜂起飞前先让飞行肌脱开翅膀空转颤抖，像发动机原地热车，把胸温抬到约30摄氏度才升空；靠这手颤抖生热加一身绒毛，它能在蜜蜂还缩在巢里的天气出门采蜜。', '[{"question":"「按流体力学计算，熊蜂根本不该会飞」，这个说法的真相是？","options":["熊蜂确实违反流体力学，科学至今无法解释","早期简化模型算不出升力，考虑翅面涡流后完全解释得通","熊蜂并不真正飞行，只是长距离滑翔"],"answer":1,"explain":"那是把固定机翼的稳态公式硬套到振翅上的早期算法；翅膀高速拍动产生的涡流能提供充足升力，熊蜂飞行毫不神秘。"},{"question":"温室里种番茄，为什么偏偏要请熊蜂来授粉？","options":["熊蜂比蜜蜂飞得更快，授粉效率更高","番茄花药要靠高频震动才肯放粉，熊蜂会声波授粉而蜜蜂不会","番茄花蜜对蜜蜂有毒，只有熊蜂不怕"],"answer":1,"explain":"番茄花药孔裂、花粉藏在管内，须高频振动才摇得出来；熊蜂咬住花药空振飞行肌完成声波授粉，这项绝技蜜蜂并不具备。"}]', '早春花丛里', '从平原花田到高山草甸都有分布，越冷的地方越显身手；早春最先出巢的是越冬蜂王，趁果树柳树开花独自筹建新巢——清晨低温时守着一丛盛花，胖乎乎嗡嗡作响的多半就是它。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'bumblebee');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'cricket', '[{"title":"右翅压左翅","body":"雄虫两枚前翅不对称地叠放，右翅在上压住左翅：右翅腹面有一列细齿组成的音锉，左翅背面边缘有坚硬刮器，两翅一开一合相互刮擦，便奏出清亮的鸣声。","anchor":"stridulator"},{"title":"耳朵长在前腿","body":"蟋蟀的「耳朵」不在头上，而在前足胫节上——两片椭圆形鼓膜正对着声源方向；两条前腿相当于分开安装的双耳，靠声音到达的细微先后差，为同伴或对手定位。"},{"title":"尾须探背后","body":"腹末一对细长尾须密布感觉毛，能捕捉身后极微弱的气流扰动；天敌从背后扑来带起的那阵风，会在电光石火间触发后腿蹬地弹跳——这套预警，比回头看快得多。","anchor":"cercus"},{"title":"后腿弹射器","body":"后足腿节粗壮如鼓槌，里面塞满弹跳肌，受惊时先蓄力锁定、再瞬间释放，把身体弹出相当于体长数十倍的距离；斗败的一方，也多靠这双后腿跳走脱身。","anchor":"hindleg"}]', '振翅鸣秋', '鸣叫时雄虫把两枚前翅斜举起来，右翅音锉在左翅刮器上快速开合刮擦，每秒数十次，翅面同时充当共鸣板把声音放大；求偶、宣示领地与得胜宣告，用的是节奏各异的三种曲子。', '[{"question":"蟋蟀和螽斯都用前翅摩擦发声，两者的差别在哪？","options":["蟋蟀右翅在上覆左翅，螽斯左翅在上覆右翅","蟋蟀左翅在上覆右翅，螽斯右翅在上覆左翅","两者都是右翅在上，构造完全相同"],"answer":0,"explain":"蟋蟀鸣叫时右翅在上、音锉长在右翅腹面；螽斯恰好相反，左翅覆右翅——同是摩擦发声，两家的持琴手势互为镜像。"},{"question":"想找到迷卡斗蟋的「耳朵」，应该看哪个部位？","options":["头部两侧、复眼的后方","前足胫节上的椭圆形鼓膜","触角基部的一对小圆孔"],"answer":1,"explain":"蟋蟀的鼓膜听器长在前足胫节上，两条前腿如同分装的双耳，靠声音到达的先后差辨别方向——头上反而没有耳朵。"}]', '墙根石缝间', '立秋后的夜晚最热闹：村舍墙根、田埂石块与砖瓦堆下都是鸣唱台，循声寻去手电一照，洞口振翅的便是雄虫；它就是古人斗蟋文化里的「促织」，白露前后斗性最盛。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'cricket');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'robber-fly', '[{"title":"护脸的髭毛","body":"口器四周环生一圈浓密硬毛，像给脸罩上一层铁丝网；捕回的蜂类猎物会在挣扎中乱蜇乱咬，这圈髭毛正好挡在复眼与口器之前——这是专为空中劫猎配备的防暴面罩。","anchor":"mystax"},{"title":"复眼间的凹谷","body":"两只巨大复眼在头顶隔出一道明显的凹谷，是食虫虻家族的注册商标；宽阔的双眼间距如同加长的测距基线，帮它在高速飞行中精确判断猎物的距离与航向。","anchor":"eye"},{"title":"六足合围成笼","body":"六足长而强壮、密布硬刺，空中撞上猎物的瞬间合围成一只牢笼将其抱死；落回栖枝后，刺吸式口器随即刺入，注入能麻痹神经并溶解组织的双效毒液。","anchor":"foreleg"},{"title":"专挑硬茬下手","body":"蜻蜓、胡蜂、虎甲这些自己就是猎手的硬茬，都在它的食谱上；凭出色的飞行爆发力与刁钻的突袭角度，它专挑对方起飞或巡航的瞬间下手，空中掠食者的名号并不虚传。","anchor":"wing"}]', '栖枝上的截击', '它像战斗机一样停在视野开阔的枝头待命，复眼锁定过路飞虫后腾空而起，从侧后方划一道弧线切入，撞抱住目标再拖回栖枝享用；一次截击往往只需一两秒，得手率极高。', '[{"question":"食虫虻口器周围那圈浓密的髭毛，主要用处是什么？","options":["滤下空气中的花粉充当食物","挡住挣扎猎物的反击，保护头脸","求偶时向雌虫炫耀的装饰"],"answer":1,"explain":"食虫虻常捕蜂类等会蜇咬的危险猎物，髭毛像面罩挡在复眼与口器前，专防对方挣扎时的反刺，是职业猎手的护具。"},{"question":"下列哪类昆虫会出现在食虫虻的食谱上？","options":["只有蚊蝇等小型柔弱的昆虫","蜻蜓、胡蜂这类自身是猎手的昆虫也在其列","它不捕活物，只舔食树干伤流"],"answer":1,"explain":"食虫虻是昆虫界的顶级空中猎手，凭突袭与毒液连蜻蜓、胡蜂、虎甲都敢下手——靠拦截偷袭而非正面缠斗取胜。"}]', '向阳枝头哨位', '盛夏晴天去林缘、河滩或荒草坡，留意向阳的枯枝顶端与篱笆桩：常有一只大眼多毛的「壮汉」停在那里巡视空域，起飞截击后又折返同一根枝头——那就是守着哨位的食虫虻。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'robber-fly');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'crane-fly', '[{"title":"大号蚊子之冤","body":"体形像放大数倍的蚊子，常被当成「巨蚊」喊打；其实它口器退化，既不能叮人也不吸血，成虫多数几乎不进食，顶多舔点花蜜露水，把短短的余生全用来寻偶产卵。","anchor":"abdomen"},{"title":"平衡棒最好认","body":"后翅退化成一对头端带小球的短棒，飞行时高速振动，像陀螺仪一样感知身体的偏转；大蚊的平衡棒在双翅目里出奇地长，肉眼即可看清，是观察这件飞行仪表的最佳教材。","anchor":"haltere"},{"title":"一碰就断的腿","body":"六条腿细长得夸张，关节处却设好了「预定断点」：被天敌或蛛网拽住时，腿会应声脱落好让本体逃生；不过与壁虎断尾不同，大蚊成虫断掉的腿不会再生，少一条就是一条。","anchor":"leg"},{"title":"摇晃的飞行","body":"一对前翅狭长，振翅频率不高，飞起来慢悠悠、晃荡荡，常贴着草丛做短距离转移；这副笨拙飞姿加上零攻击力，让它成了鸟类、蜘蛛与螳螂眼里送上门的点心。","anchor":"wing"}]', '断腿逃生术', '被鸟喙啄住或蛛网黏住一条腿时，大蚊立刻收缩特定肌肉，让那条腿沿预定的关节断面整齐脱落，趁对方一愣的空当振翅逃离；牺牲一条腿换回一条命，这笔账它从不犹豫。', '[{"question":"屋里飞进一只「巨型蚊子」，它会叮人吸血吗？","options":["会，而且吸得比普通蚊子更多","不会——大蚊口器退化，成虫几乎不进食","雄的不吸血，雌的会吸血"],"answer":1,"explain":"大蚊只是长得像放大的蚊子，口器退化到无力叮咬，不吸血也几乎不进食；「大蚊专吃蚊子」的说法同样是讹传。"},{"question":"大蚊断腿逃生之后，那条断掉的腿会怎样？","options":["几天之内重新长出完整的腿","不会再生，此后终生少一条腿","断口处会长出两条更细的新腿"],"answer":1,"explain":"断腿是主动舍弃的保命招，但成虫不再蜕皮，断腿无法再生；野外常能见到只剩四五条腿却照常活动的大蚊。"}]', '潮湿草地上空', '春秋两季的傍晚，房前屋后的草坪与溪边湿地上常见它摇晃低飞，夜里也爱撞纱窗趋灯；幼虫在潮湿土壤和腐叶层里取食腐屑与草根——湿润的草地，装着它完整的一生。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'crane-fly');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'mantidfly', '[{"title":"山寨版螳螂","body":"一对捕捉足和螳螂如出一辙：腿节内缘带刺、镰刀状折叠，同样收在胸前伺机弹出夹住小虫；但它与螳螂并无近亲关系，这身行头是两条演化路线各自独立打造出来的。","anchor":"raptorialLeg"},{"title":"长脖子的用意","body":"前胸拉长成一截「脖子」，把头和捕捉足一起向前送出，出手距离更远、角度更灵活——与螳螂细长的前胸异曲同工；趋同演化在这个部位，又抄了一遍同样的作业。","anchor":"pronotum"},{"title":"翅膀出卖了它","body":"静止时四枚透明膜翅像屋脊一样覆在背上，翅脉细密交织成网——这是脉翅目的家族印记，草蛉正是它的近亲；螳螂的革质前翅平叠背上，一眼便能分出真假「螳螂」。","anchor":"wing"},{"title":"幼虫住进卵囊","body":"许多螳蛉的一龄幼虫会主动寻找蜘蛛卵囊，有的还先爬上蜘蛛「搭便车」，等它产卵时顺势而入；钻进卵囊后便伏在里面吸食蛛卵直到化蛹——成虫像螳螂，幼虫却过着寄生生活。"}]', '小号镰刀出击', '它埋伏在花叶之间，靠细长前胸把上半身悄悄探向目标，捕捉足闪电般弹出、夹住蚜虫或小蝇；出手套路与螳螂一模一样，只是尺寸小了几号——趋同演化连狩猎动作都复刻了。', '[{"question":"螳蛉顶着一对螳螂式捕捉足，它在分类上属于哪一类？","options":["螳螂目，是螳螂家族的小型分支","脉翅目，与草蛉是近亲","半翅目，与猎蝽是近亲"],"answer":1,"explain":"螳蛉属脉翅目，近亲是草蛉；捕捉足与长「脖子」是与螳螂趋同演化的结果，两者亲缘其实相当遥远。"},{"question":"许多螳蛉的幼虫在什么地方长大？","options":["钻进蜘蛛的卵囊里，吸食蛛卵","潜入水中捕食蚊子的孑孓","藏在土里啃食植物根系"],"answer":0,"explain":"螳蛉一龄幼虫会寻找蜘蛛卵囊钻入，有的先爬上蜘蛛搭便车；伏在卵囊里吃蛛卵长大化蛹，是脉翅目里罕见的寄生路线。"}]', '夏夜灯下访客', '白天藏身花丛与灌木叶背，摆出小螳螂的架势伏击蚜虫小蝇，不易发现；夏夜常趋光落在路灯和纱窗上——见到翅脉如纱、脖子细长的「迷你螳螂」，记得看看翅膀验明正身。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'mantidfly');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'caddisfly', '[{"title":"毛翅不是鳞翅","body":"四枚膜翅覆盖的是细毛而非鳞片，毛翅目因此得名；它与蛾蝶同出一源，是鳞翅目的姐妹群——蛾翅一摸掉「粉」，那是鳞片，石蛾翅上只有毛，一字之差分开两个目。","anchor":"hairyWing"},{"title":"触角比身还长","body":"丝状触角常超过体长，停歇时向前平伸并拢，配上收成屋脊状的翅，看上去像一枚细长的小蛾；夜里趋光停在墙上时，这对前指的长触角是认出它的第一线索。","anchor":"antenna"},{"title":"下颚须当餐具","body":"成虫的咀嚼口器已经退化，啃不动任何固体食物，只能靠发达的下颚须蘸舔花蜜和水分度日；短短数周的成虫期近乎绝食——一生的本钱，早在幼虫期就攒足了。","anchor":"palp"},{"title":"水底的建筑师","body":"幼虫「石蚕」生活在水底，吐丝把沙粒、小石子或碎枝叶缀成一只随身巢筒，走到哪背到哪，遇险便整个缩进筒中；不同种类的选材与样式各异，行家看巢就能认出是谁。"}]', '背着房子走', '石蚕在水底爬行时，头胸探出筒口、六足抓地，拖着整座「房子」前进，遇险瞬间缩回、用坚硬的头壳堵门；巢筒随身体长大而不断加建——前端接新料，后端弃旧段。', '[{"question":"石蛾和蛾子长得很像，最可靠的区分标志是什么？","options":["石蛾翅上覆细毛，蛾翅上覆鳞片","石蛾个头都比蛾子大得多","石蛾白天活动，蛾子只在夜间出没"],"answer":0,"explain":"毛翅目（石蛾）翅面覆毛，鳞翅目（蛾与蝶）翅面覆鳞片——两个目的名字与分界都由此而来，二者是同源的姐妹群。"},{"question":"一条溪里石蚕数量很多，通常说明这条溪怎么样？","options":["水质较好，污染程度低","水体已经严重富营养化","水温偏高，不适合鱼类生存"],"answer":0,"explain":"石蛾幼虫对溶氧与污染十分敏感，是经典的水质指示生物，与蜉蝣、石蝇并称——它们的多寡，直接反映溪流洁净程度。"}]', '溪畔黄昏群飞', '清澈的溪流与湖岸是它的家：黄昏时成虫在水面上空成群飞舞，入夜趋光落满岸边路灯；白天翻开水底的石块，常能找到背着沙石小筒缓缓爬行的石蚕——那是它的幼虫时代。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'caddisfly');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'house-fly', '[{"title":"认纹识蝇","body":"看胸背：灰底上四条前后贯通的深色纵纹，是家蝇的「身份证」。世界上的蝇类超过十万种，而落在你家餐桌上的这只，昆虫学家多半就凭这四条纹一眼认出。","anchor":"stripe"},{"title":"只舔不咬","body":"家蝇的口器是一支海绵拖把：短喙下垂，端部两片唇瓣布满细沟。吃糖时它先吐唾液把糖粒泡化，再顺着细沟吸回——它咬不动任何东西，也没有蚊子那样的针。","anchor":"proboscis"},{"title":"倒走天花板","body":"每只脚的末端有一对爪垫，垫面密生末端分叉的细毛，毛尖分泌黏液，靠湿附着吸住玻璃。六只脚轮流起落，任何时刻都有足够的黏附力，天花板于是成了平地。","anchor":"pulvillus"},{"title":"难拍的起飞","body":"它只有一对翅，后翅退化成藏在翅根后的小平衡棒。起飞不用助跑，向后小跳的同时振翅即可离地，复眼又对逼近的阴影极其敏感——拍蝇难，难在它比你的手快得多。","anchor":"wing"}]', '搓手的秘密', '停下来的家蝇总在搓前足、抹头、刷翅，像个忙碌的洗刷工。它的味觉感受器长在足上，落点尝味全靠脚底板；把足上的灰尘与旧气味搓掉，下一脚踩到糖水时才尝得出来。', '[{"question":"家蝇落在食物上时，它是怎么「吃」的？","options":["用针状口器刺进去吸","先吐消化液泡软，再用海绵状唇瓣吸回","用大颚一小口一小口咬碎"],"answer":1,"explain":"家蝇口器是舐吸式，端部是海绵状唇瓣，只能舔吸液体；固体要先用唾液泡软溶解，它没有能刺能咬的口器。"},{"question":"家蝇能倒挂在天花板上，靠的是哪个部位？","options":["不停扇动的翅膀","足端能分泌黏液的爪垫","腹部的吸盘"],"answer":1,"explain":"足端爪垫上的细毛分泌黏液，靠湿附着力抓住光滑表面；六足轮换起落，让它随时保持足够吸附力。"}]', '人类身边', '家蝇几乎不在荒野独立生存，它的一生跟着人走：垃圾桶、粪肥堆、厨房案板都是产卵与取食的场所。温暖季节两周即可完成一代，这份繁殖速度让它成为分布最广的昆虫之一。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'house-fly');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'mosquito', '[{"title":"认出一只库蚊","body":"先看停姿：库蚊停下时身体与停面大致平行，尾部不上翘（按蚊则头低尾高，斜成一条线）；再看腹部：每节基部一圈淡色横带。两条都对上，就是淡色库蚊。","anchor":"band"},{"title":"一支精密针管","body":"刺吸喙不是一根针，而是六根口针外包一层唇鞘：两根细齿刀切开皮肤，两根护针固定，一根注入含抗凝剂的唾液，一根往回抽血——被叮后发痒，就是那口唾液引起的过敏。","anchor":"proboscis"},{"title":"雌雄看触角","body":"雌蚊触角细丝上只有稀疏的短轮毛；雄蚊触角蓬松如两支羽毛掸——那是它的「耳朵」，靠共振听见雌蚊的振翅声。吸血的只有雌蚊，雄蚊的口器连皮肤都刺不动。","anchor":"antenna"},{"title":"翅上的鳞片","body":"蚊翅狭长，翅脉与后缘覆着细小的鳞片——这是蚊科的家徽，其他蝇类的翅面都是光膜。飞行时每秒振翅数百次，恼人的嗡鸣既出卖了它，也是它们彼此相认的信号。","anchor":"wing"}]', '一次完整叮咬', '雌蚊落定后先用喙端探测皮肤，找到毛细血管密集处，六根口针交替切入，边注入抗凝唾液边抽血，几分钟内吸入接近自身体重两倍的血，腹部撑成透亮的红灯笼才拔针离开。', '[{"question":"哪些蚊子会吸血？","options":["所有蚊子都吸血","只有雌蚊吸血，为了让卵发育","只有雄蚊吸血，为了求偶炫耀"],"answer":1,"explain":"吸血是雌蚊产卵前的营养需求，血液蛋白供卵成熟；雄蚊口器纤弱，终生只取食花蜜与植物汁液。"},{"question":"淡色库蚊停在墙上时，姿态是什么样的？","options":["身体与墙面大致平行","头朝下、尾巴高高翘起","始终倒挂着停"],"answer":0,"explain":"库蚊属停歇时体轴与停面平行；头低尾高翘起的是按蚊——这是野外区分两类蚊子最快的一眼。"}]', '积水边的一生', '一只废轮胎里的积水、一个花盆托盘，就够淡色库蚊完成整个童年：卵结成筏浮在水面，孑孓倒悬水下滤食，蛹也在水里翻滚。清掉居家周围的滞水，比任何蚊香都更釜底抽薪。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'mosquito');

INSERT INTO edu_insect_guide (insect_code, guide_lesson, guide_motion_title, guide_motion_body, guide_quiz, guide_habitat_title, guide_habitat_body)
SELECT 'cockroach', '[{"title":"认准两条纹","body":"茶褐色的小个子蟑螂很多，德国小蠊的身份证在前胸背板：两条平行的深色纵纹从前缘画到后缘。若虫虽然没有翅，这两条纹却从小带到大，任何龄期都认得出。","anchor":"stripe"},{"title":"藏起来的头","body":"蟑螂的头并不在「最前面」，而是缩在前胸背板这块盾牌下方、口器朝下——从背面看只露头顶一线。最脆弱的部位始终有甲片遮护，身体也压得更扁，钻缝更利落。","anchor":"head"},{"title":"背后的眼睛","body":"腹部末端一对分节尾须是它的「后视镜」：上面的感觉毛能捕捉身后极微弱的气流。拖鞋带起的风比拖鞋先到，尾须一响，信号直通腿部——这就是蟑螂总能提前逃走的原因。","anchor":"cercus"},{"title":"有翅不飞","body":"德国小蠊的革质前翅完整盖住腹部，看上去随时能起飞，实际几乎从不飞行——它的逃生策略是钻缝：扁平的身体加上多刺的足，几毫米宽的缝隙就是它的安全门。","anchor":"wing"}]', '死里逃生的急转', '受惊的德国小蠊起步只需几十毫秒：尾须感到气流，不经大脑、由腹部末端的神经节直接下令蹬腿，先转身背对威胁再全速冲刺，途中还能贴着墙沿急转——整套动作快过人类眨眼。', '[{"question":"怎么从一群小蟑螂里认出德国小蠊？","options":["看前胸背板上的两条深色纵纹","看它会不会飞","看腹部颜色深浅"],"answer":0,"explain":"前胸背板的两条纵纹是德国小蠊最稳定的认种特征，从若虫到成虫都保持，比大小和颜色都可靠。"},{"question":"拖鞋还没落下，蟑螂就先跑了，它靠什么预警？","options":["复眼看到了拖鞋","尾须感知到拍击带起的气流","听到了脚步声"],"answer":1,"explain":"尾须上的感觉毛对气流极敏感，信号绕过大脑、由腹部神经节直接驱动逃跑，反应只要几十毫秒。"}]', '暖气与缝隙之间', '它挑住处只有三个条件：温暖、潮湿、有缝可钻。厨房水槽下的管道缝、冰箱压缩机旁、微波炉底座，都是五星级巢址；白天挤在缝里休息，入夜后沿着踢脚线出门觅食。'
WHERE NOT EXISTS (SELECT 1 FROM edu_insect_guide WHERE insect_code = 'cockroach');

-- ============================================================
-- 采集器配置种子（只给配置，不导入原始数据）
-- ------------------------------------------------------------
-- 设计意图：全新安装后不导入任何成语/题库原始数据，而是把「采集器配置」随 initdata 落库，
--           任务启用后点「立即执行」或等定时触发即可自行取数入库。
-- 任务配置了 uniqueKey + onConflict=skip，重复执行不会产生重复数据，可安全重跑。
--
-- 关于状态与 cron（重要）：
--   采集任务的 cron 不落在 edu_collect_task 行上，而是登记到 job-starter 运行时
--   （见 EduCollectTaskServiceImpl#ensureJob），因此 SQL 种子无法预置 cron。
--   应用启动时 restoreScheduling() 会给「已启用但无调度行」的任务兜底套用默认 cron
--   （0 */30 * * * ?，即每 30 分钟一次）。若把种子设为启用，全新安装后会自动每半小时
--   访问一次公开数据源，正是此前「自动膨胀」问题的成因。
--   故种子一律以「停用」状态落库：进页面后由使用者设置 cron 并启用，再点「立即执行」取数。
--   同时不写入 collect_task_cron / collect_task_next_run_time 两列——它们只存在于
--   V1.0__init_all_eduai.sql 的新建表结构中，既有数据库因 DDL 脚本「ONCE」模式被跳过而没有这两列，
--   引用它们会导致 initdata 在既有库上执行失败。
-- ============================================================

-- ----------------------------
-- 一、采集任务
-- ----------------------------

-- 成语采集：调用 chinese-xinhua 公开数据集（jsDelivr CDN），前置处理器按 JSONPath 展平，
-- 入库处理器写 edu_idiom，单次上限 500 条，按 idiom_content 去重。
INSERT INTO edu_collect_task (collect_task_name, collect_task_source_type, collect_task_source_config, collect_task_subject, collect_task_grade_level, collect_task_grade, collect_task_difficulty, collect_task_status, collect_task_remark)
SELECT '成语采集·chinese-xinhua', 'API', '{"apiUrl":"https://cdn.jsdelivr.net/gh/pwxcoo/chinese-xinhua@master/data/idiom.json","method":"GET","offset":0,"fallbackUrls":"https://fastly.jsdelivr.net/gh/pwxcoo/chinese-xinhua@master/data/idiom.json,https://gcore.jsdelivr.net/gh/pwxcoo/chinese-xinhua@master/data/idiom.json,https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/idiom.json","preProcessor":{"mapping":"{\\\"$[*].word\\\":\\\"word\\\",\\\"$[*].pinyin\\\":\\\"pinyin\\\",\\\"$[*].explanation\\\":\\\"explanation\\\",\\\"$[*].derivation\\\":\\\"derivation\\\",\\\"$[*].example\\\":\\\"example\\\"}"},"importProcessor":{"table":"edu_idiom","uniqueKey":"idiom_content","onConflict":"skip","mapping":{"idiom_content":"word","idiom_pinyin":"pinyin","idiom_meaning":"explanation","idiom_source":"derivation","idiom_example":"example"},"limit":500,"constants":"{\\\"idiom_difficulty\\\":3,\\\"idiom_tags\\\":\\\"\\\"}","gradeLevel":"小学"}}', '语文', 'PRIMARY', '三年级', 3, 0, '成语采集：公开数据集 chinese-xinhua，按 idiom_content 去重，单次 500 条。默认停用：请在页面设置 cron 后启用，再点「立即执行」取数入库。'
WHERE NOT EXISTS (SELECT 1 FROM edu_collect_task WHERE collect_task_name = '成语采集·chinese-xinhua');

-- 古诗采集：调用 chinese-poetry 开源《全唐诗》数据集（jsDelivr CDN），
-- 入库处理器写 edu_poem，按「标题+作者」去重，单次上限 200 首。
--
-- 注意两点（换源只需改本配置，不用改代码）：
--   1) 该数据集正文为传统汉字（如“函谷壯皇居”），且不含朝代/译文/赏析字段；
--      这里用 constants 固定 poem_dynasty=唐代。若要简体，把 apiUrl 换成简体数据集即可；
--   2) 正文 paragraphs 是数组，前置处理器会输出 JSON 数组字符串，
--      后端 EduPoemServiceImpl 已做归一化（还原为按行分隔的纯文本）后再切句。
INSERT INTO edu_collect_task (collect_task_name, collect_task_source_type, collect_task_source_config, collect_task_subject, collect_task_grade_level, collect_task_grade, collect_task_difficulty, collect_task_status, collect_task_remark)
SELECT '古诗采集·全唐诗', 'API', '{"apiUrl":"https://raw.githubusercontent.com/chinese-poetry/chinese-poetry-zhCN/master/poetry/poet.tang.0.json","method":"GET","offset":0,"fallbackUrls":"https://ghproxy.net/https://raw.githubusercontent.com/chinese-poetry/chinese-poetry-zhCN/master/poetry/poet.tang.0.json","preProcessor":{"mapping":"{\\\"$[*].title\\\":\\\"title\\\",\\\"$[*].author\\\":\\\"author\\\",\\\"$[*].paragraphs\\\":\\\"paragraphs\\\"}"},"importProcessor":{"table":"edu_poem","uniqueKey":"poem_title,poem_author","onConflict":"skip","mapping":{"poem_title":"title","poem_author":"author","poem_content":"paragraphs"},"limit":200,"constants":"{\\\"poem_dynasty\\\":\\\"唐代\\\",\\\"poem_difficulty\\\":3}","gradeLevel":"高中"}}', '语文', 'SENIOR', '高二', 3, 0, '古诗采集：开源《全唐诗》简体镜像（chinese-poetry-zhCN），按 标题+作者 去重，单次 200 首，入 edu_poem。注意：该源的 strains 字段是「平仄标注数组」而非诗体名，故不映射到 poem_tags。数据为唐诗全量语料（宫廷/应制诗居多，非小学必背清单），故学段标为高中。默认停用：请在页面设置 cron 后启用，再点「立即执行」取数。'
WHERE NOT EXISTS (SELECT 1 FROM edu_collect_task WHERE collect_task_name = '古诗采集·全唐诗');

-- ----------------------------
-- 古诗种子（967 首）
-- ----------------------------
-- 数据来源：开源《全唐诗》简体镜像 chinese-poetry-zhCN，经「采集管理 → 古诗采集」入库；
-- 本段由 gen-poem-seed.py 从库中导出生成，文本逐字取自数据源，未经人工转写。
-- 用途：全新安装无需先跑采集即可拥有可玩语料；采集器仍可继续补充其余分片。
-- 幂等：edu_poem 靠唯一索引 uk_edu_poem_title_author，
--       edu_poem_grade 靠 uk_edu_poem_grade_poem_level，均以 INSERT IGNORE 跳过重复。
-- 说明：poem_content 存按行分隔的纯文本（服务侧对采集器写入的 JSON 数组形态
--       会自动归一化，两者读出来一致）。

INSERT IGNORE INTO edu_poem
    (poem_title, poem_author, poem_dynasty, poem_content, poem_tags, poem_difficulty)
VALUES
    ('帝京篇十首 一', '太宗皇帝', '唐代', '秦川雄帝宅，函谷壮皇居。\n绮殿千寻起，离宫百雉余。\n连甍遥接汉，飞观迥凌虚。\n云日隐层阙，风烟出绮疏。', NULL, 3),
    ('帝京篇十首 二', '太宗皇帝', '唐代', '岩廊罢机务，崇文聊驻辇。\n玉匣启龙图，金绳披凤篆。\n韦编断仍续，缥帙舒还卷。\n对此乃淹留，欹案观坟典。', NULL, 3),
    ('帝京篇十首 三', '太宗皇帝', '唐代', '移步出词林，停舆欣武宴。\n雕弓写明月，骏马疑流电。\n惊雁落虚弦，啼猿悲急箭。\n阅赏诚多美，于兹乃忘倦。', NULL, 3),
    ('帝京篇十首 四', '太宗皇帝', '唐代', '鸣笳临乐馆，眺听欢芳节。\n急管韵朱弦，清歌凝白雪。\n彩凤肃来仪，玄鹤纷成列。\n去兹郑卫声，雅音方可悦。', NULL, 3),
    ('帝京篇十首 五', '太宗皇帝', '唐代', '芳辰追逸趣，禁苑信多奇。\n桥形通汉上，峰势接云危。\n烟霞交隐映，花鸟自参差。\n何如肆辙迹？万里赏瑶池。', NULL, 3),
    ('帝京篇十首 六', '太宗皇帝', '唐代', '飞盖去芳园，兰桡游翠渚。\n萍间日彩乱，荷处香风举。\n桂楫满中川，弦歌振长屿。\n岂必汾河曲，方为欢宴所。', NULL, 3),
    ('帝京篇十首 七', '太宗皇帝', '唐代', '落日双阙昏，回舆九重暮。\n长烟散初碧，皎月澄轻素。\n搴幌玩琴书，开轩引云雾。\n斜汉耿层阁，清风摇玉树。', NULL, 3),
    ('帝京篇十首 八', '太宗皇帝', '唐代', '欢乐难再逢，芳辰良可惜。\n玉酒泛云罍，兰殽陈绮席。\n千锺合尧禹，百兽谐金石。\n得志重寸阴，忘怀轻尺璧。', NULL, 3),
    ('帝京篇十首 九', '太宗皇帝', '唐代', '建章欢赏夕，二八尽妖妍。\n罗绮昭阳殿，芬芳玳瑁筵。\n珮移星正动，扇掩月初圆。\n无劳上悬圃，即此对神仙。', NULL, 3),
    ('帝京篇十首 十', '太宗皇帝', '唐代', '以兹游观极，悠然独长想。\n披卷览前踪，抚躬寻既往。\n望古茅茨约，瞻今兰殿广。\n人道恶高危，虚心戒盈荡。\n奉天竭诚敬，临民思惠养。\n纳善察忠谏，明科慎刑赏。\n六五诚难继，四三非易仰。\n广待淳化敷，方嗣云亭响。', NULL, 3),
    ('饮马长城窟行', '太宗皇帝', '唐代', '塞外悲风切，交河冰已结。\n瀚海百重波，阴山千里雪。\n迥戍危烽火，层峦引高节。\n悠悠卷斾旌，饮马出长城。\n寒沙连骑迹，朔吹断边声。\n胡尘清玉塞，羌笛韵金钲。\n绝漠干戈戢，车徒振原隰。\n都尉反龙堆，将军旋马邑。\n扬麾氛雾静，纪石功名立。\n荒裔一戎衣，灵台凯歌入。', NULL, 3),
    ('执契静三边', '太宗皇帝', '唐代', '执契静三边，持衡临万姓。\n玉彩辉关烛，金华流日镜。\n无为宇宙清，有美璇玑正。\n皎佩星连景，飘衣云结庆。\n戢武耀七德，升文辉九功。\n烟波澄旧碧，尘火息前红。\n霜野韬莲剑，关城罢月弓。\n钱缀榆天合，新城柳塞空。\n花销葱岭雪，縠尽流沙雾。\n秋驾转兢怀，春冰弥轸虑。\n书绝龙庭羽，烽休凤穴戍。\n衣宵寝二难，食旰餐三惧。\n翦暴兴先废，除凶存昔亡。\n圆盖归天壤，方舆入地荒。\n孔海池京邑，双河沼帝乡。\n循躬思励己，抚俗愧时康。\n元首伫盐梅，股肱惟辅弼。\n羽贤崆岭四，翼圣襄城七。\n浇俗庶反淳，替文聊就质。\n已知隆至道，共欢区宇一。', NULL, 3),
    ('正日临朝', '太宗皇帝', '唐代', '条风开献节，灰律动初阳。\n百蛮奉遐賮，万国朝未央。\n虽无舜禹迹，幸欣天地康。\n车轨同八表，书文混四方。\n赫奕俨冠盖，纷纶盛服章。\n羽旄飞驰道，钟鼓震岩廊。\n组练辉霞色，霜戟耀朝光。\n晨宵怀至理，终愧抚遐荒。', NULL, 3),
    ('幸武功庆善宫', '太宗皇帝', '唐代', '寿丘惟旧迹，酆邑乃前基。\n粤予承累圣，悬弧亦在兹。\n弱龄逢运改，提剑郁匡时。\n指麾八荒定，怀柔万国夷。\n梯山咸入款，驾海亦来思。\n单于陪武帐，日逐卫文{木|囟/比}。\n端扆朝四岳，无为任百司。\n霜节明秋景，轻冰结水湄。\n芸黄遍原隰，禾颖积京畿。\n共乐还乡宴，欢比大风诗。', NULL, 3),
    ('重幸武功', '太宗皇帝', '唐代', '代马依朔吹，惊禽愁昔丛。\n况兹承眷德，怀旧感深衷。\n积善忻馀庆，畅武悦成功。\n垂衣天下治，端拱车书同。\n白水巡前迹，丹陵幸旧宫。\n列筵欢故老，高宴聚新丰。\n驻跸抚田畯，回舆访牧童。\n瑞气萦丹阙，祥烟散碧空。\n孤屿含霜白，遥山带日红。\n于焉欢击筑，聊以咏南风。', NULL, 3),
    ('经破薛举战地', '太宗皇帝', '唐代', '昔年怀壮气，提戈初仗节。\n心随朗日高，志与秋霜洁。\n移锋惊电起，转战长河决。\n营碎落星沈，阵卷横云裂。\n一挥氛沴静，再举鲸鲵灭。\n于兹俯旧原，属目驻华轩。\n沈沙无故迹，减灶有残痕。\n浪霞穿水净，峰雾抱莲昏。\n世途亟流易，人事殊今昔。\n长想眺前踪，抚躬聊自适。', NULL, 3),
    ('过旧宅二首 一', '太宗皇帝', '唐代', '新丰停翠辇，谯邑驻鸣笳。\n园荒一径断，苔古半阶斜。\n前池消旧水，昔树发今花。\n一朝辞此地，四海遂为家。', NULL, 3),
    ('过旧宅二首 二', '太宗皇帝', '唐代', '金舆巡白水，玉辇驻新丰。\n纽落藤披架，花残菊破丛。\n叶铺荒草蔓，流竭半池空。\n纫珮兰凋径，舒圭叶翦桐。\n昔地一蕃内，今宅九围中。\n架海波澄镜，韬戈器反农。\n八表文同轨，无劳歌大风。', NULL, 3),
    ('还陕述怀', '太宗皇帝', '唐代', '慨然抚长剑，济世岂邀名。\n星旗纷电举，日羽肃天行。\n遍野屯万骑，临原驻五营。\n登山麾武节，背水纵神兵。\n在昔戎戈动，今来宇宙平。', NULL, 3),
    ('入潼关', '太宗皇帝', '唐代', '崤函称地险，襟带壮两京。\n霜峰直临道，冰河曲绕城。\n古木参差影，寒猿断续声。\n冠盖往来合，风尘朝夕惊。\n高谈先马度，伪晓预鸡鸣。\n弃𦈡怀远志，封泥负壮情。\n别有真人气，安知名不名。', NULL, 3),
    ('于北平作', '太宗皇帝', '唐代', '翠野驻戎轩，卢龙转征斾。\n遥山丽如绮，长流萦似带。\n海气百重楼，岩松千丈盖。', NULL, 3),
    ('辽城望月', '太宗皇帝', '唐代', '玄兔月初明，澄辉照辽碣。\n映云光暂隐，隔树花如缀。\n魄满桂枝圆，轮亏镜彩缺。\n临城却影散，带晕重围结。\n驻跸俯九都，停观妖氛灭。', NULL, 3),
    ('春日登陕州城楼俯眺原野回丹碧缀烟霞密翠斑红芳菲花柳即目川岫聊以命篇', '太宗皇帝', '唐代', '碧原开雾隰，绮岭峻霞城。\n烟峰高下翠，日浪浅深明。\n斑红妆橤树，圆青压溜荆。\n迹岩劳傅想，窥野访莘情。\n巨川何以济，舟楫伫时英。', NULL, 3),
    ('春日玄武门宴群臣', '太宗皇帝', '唐代', '韶光开令序，淑气动芳年。\n驻辇华林侧，高宴柏梁前。\n紫庭文珮满，丹墀衮绂连。\n九夷簉瑶席，五狄列琼筵。\n娱宾歌湛露，广乐奏钧天。\n清尊浮绿醑，雅曲韵朱弦。\n粤余君万国，还惭抚八埏。\n庶几保贞固，虚己厉求贤。', NULL, 3),
    ('登三台言志', '太宗皇帝', '唐代', '未央初壮汉，阿房昔侈秦。\n在危犹骋丽，居奢遂役人。\n岂如家四海，日宇罄朝伦。\n扇天裁户旧，砌地翦基新。\n引月擎宵桂，飘云逼曙鳞。\n露除光炫玉，霜阙映雕银。\n舞接花梁燕，歌迎鸟路尘。\n镜池波太液，庄苑丽宜春。\n作异甘泉日，停非路寝辰。\n念劳惭逸己，居旷返劳神。\n所欣成大厦，宏材伫渭滨。', NULL, 3),
    ('出猎', '太宗皇帝', '唐代', '楚王云梦泽，汉帝长杨宫。\n岂若因农暇，阅武出轘嵩。\n三驱陈锐卒，七萃列材雄。\n寒野霜氛白，平原烧火红。\n雕戈夏服箭，羽骑绿沈弓。\n怖兽潜幽壑，惊禽散翠空。\n长烟晦落景，灌木振严风。\n所为除民瘼，非是悦林丛。', NULL, 3),
    ('冬狩', '太宗皇帝', '唐代', '烈烈寒风起，惨惨飞云浮。\n霜浓凝广隰，冰厚结清流。\n金鞍移上苑，玉勒骋平畴。\n旌旗四望合，罝罗一面求。\n楚踣争兕殪，秦亡角鹿愁。\n兽忙投密树，鸿惊起砾洲。\n骑敛原尘静，戈回岭日收。\n心非洛汭逸，意在渭滨游。\n禽荒非所乐，抚辔更招忧。', NULL, 3),
    ('春日望海', '太宗皇帝', '唐代', '披襟眺沧海，凭轼玩春芳。\n积流横地纪，疏派引天潢。\n仙气凝三岭，和风扇八荒。\n拂潮云布色，穿浪日舒光。\n照岸花分彩，迷云雁断行。\n怀卑运深广，持满守灵长。\n有形非易测，无源讵可量。\n洪涛经变野，翠岛屡成桑。\n之罘思汉帝，碣石想秦皇。\n霓裳非本意，端拱且图王。', NULL, 3),
    ('临洛水', '太宗皇帝', '唐代', '春搜驰骏骨，总辔俯长河。\n霞处流萦锦，风前瀁卷罗。\n水花翻照树，堤兰倒插波。\n岂必汾阴曲，秋云发棹歌。', NULL, 3),
    ('望终南山', '太宗皇帝', '唐代', '重峦俯渭水，碧嶂插遥天。\n出红扶岭日，入翠贮岩烟。\n叠松朝若夜，复岫阙疑全。\n对此恬千虑，无劳访九仙。', NULL, 3),
    ('元日', '太宗皇帝', '唐代', '高轩暧春色，邃阁媚朝光。\n彤庭飞彩斾，翠幌曜明珰。\n恭己临四极，垂衣驭八荒。\n霜戟列丹陛，丝竹韵长廊。\n穆矣熏风茂，康哉帝道昌。\n继文遵后轨，循古鉴前王。\n草秀故春色，梅艳昔年妆。\n巨川思欲济，终以寄舟航。', NULL, 3),
    ('初春登楼即目观作述怀', '太宗皇帝', '唐代', '凭轩俯兰阁，眺瞩散灵襟。\n绮峰含翠雾，照日蕊红林。\n镂丹霞锦岫，残素雪斑岑。\n拂浪堤垂柳，娇花鸟续吟。\n连甍岂一拱，众干如千寻。\n明非独材力，终藉栋梁深。\n弥怀矜乐志，更惧戒盈心。\n愧制劳居逸，方规十产金。', NULL, 3),
    ('首春', '太宗皇帝', '唐代', '寒随穷律变，春逐鸟声开。\n初风飘带柳，晚雪间花梅。\n碧林青旧竹，绿沼翠新苔。\n芝田初雁去，绮树巧莺来。', NULL, 3),
    ('初晴落景', '太宗皇帝', '唐代', '晚霞聊自怡，初晴弥可喜。\n日晃百花色，风动千林翠。\n池鱼跃不同，园鸟声还异。\n寄言博通者，知予物外志。', NULL, 3),
    ('初夏', '太宗皇帝', '唐代', '一朝春夏改，隔夜鸟花迁。\n阴阳深浅叶，晓夕重轻烟。\n哢莺犹响殿，横丝正网天。\n珮高兰影接，绶细草纹连。\n碧鳞惊櫂侧，玄燕舞檐前。\n何必汾阳处，始复有山泉。', NULL, 3),
    ('度秋', '太宗皇帝', '唐代', '夏律昨留灰，秋箭今移晷。\n峨嵋岫初出，洞庭波渐起。\n桂白发幽岩，菊黄开灞涘。\n运流方可叹，含毫属微理。', NULL, 3),
    ('仪鸾殿早秋', '太宗皇帝', '唐代', '寒惊蓟门叶，秋发小山枝。\n松阴背日转，竹影避风移。\n提壶菊花岸，高兴芙蓉池。\n欲知凉气早，巢空燕不窥。', NULL, 3),
    ('秋日即目', '太宗皇帝', '唐代', '爽气浮丹阙，秋光澹紫宫。\n衣碎荷疏影，花明菊点丛。\n袍轻低草露，盖侧舞松风。\n散岫飘云叶，迷路飞烟鸿。\n砌冷兰凋佩，闺寒树陨桐。\n别鹤栖琴里，离猿啼峡中。\n落野飞星箭，弦虚半月弓。\n芳菲夕雾起，暮色满房栊。', NULL, 3),
    ('山阁晚秋', '太宗皇帝', '唐代', '山亭秋色满，岩牖凉风度。\n疏兰尚染烟，残菊犹承露。\n古石衣新苔，新巢封古树。\n历览情无极，咫尺输光暮。', NULL, 3),
    ('秋暮言志', '太宗皇帝', '唐代', '朝光浮烧野，霜华净碧空。\n结浪冰初镜，在迳菊方丛。\n约岭烟深翠，分旗霞散红。\n抽思滋泉侧，飞想傅岩中。', NULL, 3),
    ('喜雪', '太宗皇帝', '唐代', '碧昏朝合雾，丹卷暝韬霞。\n结叶繁云色，凝璚遍雪华。\n光楼皎若粉，映幕集疑沙。\n泛柳飞飞絮，妆梅片片花。\n照璧台圆月，飘珠箔穿露。\n瑶洁短长阶，玉藂高下树。\n映桐珪累白，萦峰莲抱素。\n断续气将沈，徘徊岁云暮。\n怀珍愧隐德，表瑞伫丰年。\n橤间飞禁苑，鹤处舞伊川。\n傥咏幽兰曲，同懽黄竹篇。', NULL, 3),
    ('秋日𢽾庾信体', '太宗皇帝', '唐代', '岭衔宵月桂，珠穿晓露丛。\n蝉啼觉树冷，萤火不温风。\n花生圆菊橤，荷尽戏鱼通。\n晨浦鸣飞雁，夕渚集栖鸿。\n飒飒高天吹，氛澄下炽空。', NULL, 3),
    ('赋尚书', '太宗皇帝', '唐代', '崇文时驻步，东观还停辇。\n辍膳玩三坟，晖灯披五典。\n寒心睹肉林，飞魄看沈湎。\n纵情昏主多，克己明君鲜。\n灭身资累恶，成名由积善。\n既承百王末，战兢随岁转。', NULL, 3),
    ('咏司马彪续汉志', '太宗皇帝', '唐代', '二仪初创象，三才乃分位。\n非惟树司牧，固亦垂文字。\n绵代更膺期，芳图无辍记。\n炎汉承君道，英谟纂神器。\n潜龙既可跃，逵兔奚难致。\n前史殚妙词，后昆沈雅思。\n书言扬盛迹，补阙兴洪志。\n川谷犹旧途，郡国开新意。\n梅山未觉朽，穀水谁云异。\n车服随名表，文物因时置。\n凤戟翼康衢，銮舆总柔辔。\n清浊必能澄，洪纤幸无弃。\n观仪不失序，遵礼方由事。\n政宣竹律和，时平玉条备。\n文囿雕奇彩，艺门蕴深致。\n云飞星共流，风扬月兼至。\n类禋遵令典，坛壝资良地。\n五胜竟无违，百司诚有庇。\n粤予承暇景，谈丛引泉秘。\n讨论穷义府，看核披经笥。\n大辨良难仰，小学终先匮。\n闻道谅知荣，含毫孰忘愧。', NULL, 3),
    ('咏风', '太宗皇帝', '唐代', '萧条起关塞，摇飏下蓬瀛。\n拂林花乱彩，响谷鸟分声。\n披云罗影散，泛水织文生。\n劳歌大风曲，威加四海清。', NULL, 3),
    ('咏雨', '太宗皇帝', '唐代', '罩云飘远岫，喷雨泛长河。\n低飞昏岭腹，斜足洒岩阿。\n泫丛珠缔叶，起溜镜图波。\n蒙柳添丝密，含吹织空罗。', NULL, 3),
    ('咏雪', '太宗皇帝', '唐代', '洁野凝晨曜，装墀带夕晖。\n集条分树玉，拂浪影泉玑。\n色洒妆台粉，花飘绮席衣。\n入扇萦离匣，点素皎残机。', NULL, 3),
    ('赋得夏首启节', '太宗皇帝', '唐代', '北阙三春晚，南荣九夏初。\n黄莺弄渐变，翠林花落余。\n瀑流还响谷，猿啼自应虚。\n早荷向心卷，长杨就影舒。\n此时欢不极，调轸坐相于。', NULL, 3),
    ('赋得白日半西山', '太宗皇帝', '唐代', '红轮不暂驻，乌飞岂复停。\n岑霞渐渐落，溪阴寸寸生。\n藿叶随光转，葵心逐照倾。\n晚烟含树色，栖鸟杂流声。', NULL, 3),
    ('置酒坐飞阁', '太宗皇帝', '唐代', '高轩临碧渚，飞檐迥架空。\n余花攒镂槛，残柳散雕栊。\n岸菊初含橤，园梨始带红。\n莫虑昆山暗，还共尽杯中。', NULL, 3),
    ('采芙蓉', '太宗皇帝', '唐代', '结伴戏方塘，携手上雕航。\n船移分细浪，风散动浮香。\n游莺无定曲，惊凫有乱行。\n莲稀钏声断，水广櫂歌长。\n栖乌还密树，泛流归建章。', NULL, 3),
    ('赋得樱桃', '太宗皇帝', '唐代', '华林满芳景，洛阳遍阳春。\n朱颜含远日，翠色影长津。\n乔柯啭娇鸟，低枝映美人。\n昔作园中实，今来席上珍。', NULL, 3),
    ('赋得李', '太宗皇帝', '唐代', '玉衡流桂圃，成蹊正可寻。\n莺啼密叶外，蝶戏脆花心。\n丽景光朝彩，轻霞散夕阴。\n暂顾晖章侧，还眺灵山林。', NULL, 3),
    ('赋得浮桥', '太宗皇帝', '唐代', '岸曲非千里，桥斜异七星。\n暂低逢辇度，还高值浪惊。\n水摇文鹢动，缆转锦花萦。\n远近随轮影，轻重应人行。', NULL, 3),
    ('谒幷州大兴国寺诗', '太宗皇帝', '唐代', '回銮游福地，极目玩芳晨。\n梵钟交二响，法日转双轮。\n宝刹遥承露，天花近足春。\n未佩兰犹小，无丝柳尚新。\n圆光低月殿，碎影乱风筠。\n对此留余想，超然离俗尘。', NULL, 3),
    ('咏兴国寺佛殿前幡', '太宗皇帝', '唐代', '拂霞疑电落，腾虚状写虹。\n屈伸烟雾里，低举白云中。\n纷披乍依迥，掣曳或随风。\n念兹轻薄质，无翅强摇空。', NULL, 3),
    ('望送魏徵葬', '太宗皇帝', '唐代', '阊阖总金鞍，上林移玉辇。\n野郊怆新别，河桥非旧饯。\n惨日映峰沈，愁云随盖转。\n哀笳时断续，悲旌乍舒卷。\n望望情何极，浪浪泪空泫。\n无复昔时人，芳春共谁遣。', NULL, 3),
    ('伤辽东战亡', '太宗皇帝', '唐代', '凿门初奉律，仗战始临戎。\n振鳞方跃浪，骋翼正凌风。\n未展六奇术，先亏一篑功。\n防身岂乏智，殉命有余忠。', NULL, 3),
    ('月晦', '太宗皇帝', '唐代', '晦魄移中律，凝暄起丽城。\n罩云朝盖上，穿露晓珠呈。\n笑树花分色，啼枝鸟合声。\n披襟欢眺望，极目畅春情。', NULL, 3),
    ('秋日翠微宫', '太宗皇帝', '唐代', '秋光凝翠岭，凉吹肃离宫。\n荷疏一盖缺，树冷半帷空。\n侧阵移鸿影，圆花钉菊丛。\n摅怀俗尘外，高眺白云中。', NULL, 3),
    ('初秋夜坐', '太宗皇帝', '唐代', '斜廊连绮阁，初月照宵帏。\n塞冷鸿飞疾，园秋蝉噪迟。\n露结林疏叶，寒轻菊吐滋。\n愁心逢此节，长叹独含悲。', NULL, 3),
    ('秋日二首 一', '太宗皇帝', '唐代', '菊散金风起，荷疏玉露圆。\n将秋数行雁，离夏几林蝉。\n云凝愁半岭，霞碎缬高天。\n还似成都望，直见峨眉前。', NULL, 3),
    ('秋日二首 二', '太宗皇帝', '唐代', '爽气澄兰沼，秋风动桂林。\n露凝千片玉，菊散一丛金。\n日岫高低影，云空点缀阴。\n蓬瀛不可望，泉石且娱心。', NULL, 3),
    ('冬宵各为四韵', '太宗皇帝', '唐代', '雕宫静龙漏，绮阁宴公侯。\n珠帘烛焰动，绣柱月光浮。\n云起将歌发，风停与管遒。', NULL, 3),
    ('冬日临昆明池', '太宗皇帝', '唐代', '石鲸分玉溜，劫烬隐平沙。\n柳影冰无叶，梅心冻有花。\n寒野凝朝雾，霜天散夕霞。\n欢情犹未极，落景遽西斜。', NULL, 3),
    ('望雪', '太宗皇帝', '唐代', '冻云宵遍岭，素雪晓凝华。\n入牖千重碎，迎风一半斜。\n不妆空散粉，无树独飘花。\n萦空惭夕照，破彩谢晨霞。', NULL, 3),
    ('守岁', '太宗皇帝', '唐代', '暮景斜芳殿，年华丽绮宫。\n寒辞去冬雪，暖带入春风。\n阶馥舒梅素，盘花卷烛红。\n共欢新故岁，迎送一宵中。', NULL, 3),
    ('除夜', '太宗皇帝', '唐代', '岁阴穷暮纪，献节启新芳。\n冬尽今宵促，年开明日长。\n冰消出镜水，梅散入风香。\n对此欢终宴，倾壶待曙光。', NULL, 3),
    ('赋得含峰云', '太宗皇帝', '唐代', '翠楼含晓雾，莲峰带晚云。\n玉叶依岩聚，金枝触石分。\n横天结阵影，逐吹起罗文。\n非复阳台下，空将惑楚君。', NULL, 3),
    ('三层阁上置音声', '太宗皇帝', '唐代', '绮筵移暮景，紫阁引宵烟。\n隔栋歌尘合，分阶舞影连。\n声流三处管，响乱一重弦。\n不似秦楼上，吹箫空学仙。', NULL, 3),
    ('远山澄碧雾', '太宗皇帝', '唐代', '残云收翠岭，夕雾结长空。\n带岫凝全碧，障霞隐半红。\n髣髴分初月，飘飖度晓风。\n还因三里处，冠盖远相通。', NULL, 3),
    ('赋得花庭雾', '太宗皇帝', '唐代', '兰气已熏宫，新橤半妆丛。\n色含轻重雾，香引去来风。\n拂树浓舒碧，萦花薄蔽红。\n还当杂行雨，髣髴隐遥空。', NULL, 3),
    ('春池柳', '太宗皇帝', '唐代', '年柳变池台，隋堤曲直回。\n逐浪丝阴去，迎风带影来。\n疏黄一鸟弄，半翠几眉开。\n萦雪临春岸，参差间早梅。', NULL, 3),
    ('芳兰', '太宗皇帝', '唐代', '春晖开紫苑，淑景媚兰场。\n映庭含浅色，凝露泫浮光。\n日丽参差影，风传轻重香。\n会须君子折，佩里作芬芳。', NULL, 3),
    ('咏桃', '太宗皇帝', '唐代', '禁苑春晖丽，花蹊绮树妆。\n缀条深浅色，点露参差光。\n向日分千笑，迎风共一香。\n如何仙岭侧，独秀隐遥芳。', NULL, 3),
    ('赋帘', '太宗皇帝', '唐代', '参差垂玉阙，舒卷映兰宫。\n珠光摇素月，竹影乱清风。\n彩散银钩上，文斜桂户中。\n惟当杂罗绮，相与媚房栊。', NULL, 3),
    ('咏乌代陈师道', '太宗皇帝', '唐代', '凌晨丽城去，薄暮上林栖。\n辞枝枝暂起，停树树还低。\n向日终难托，迎风讵肯迷。\n只待纤纤手，曲里作宵啼。', NULL, 3),
    ('咏饮马', '太宗皇帝', '唐代', '骏骨饮长泾，奔流洒络缨。\n细纹连喷聚，乱荇绕蹄萦。\n水光鞍上侧，马影溜中横。\n翻似天池里，腾波龙种生。', NULL, 3),
    ('赋得残菊', '太宗皇帝', '唐代', '阶兰凝曙霜，岸菊照晨光。\n露浓晞晚笑，风劲浅残香。\n细叶凋轻翠，圆花飞碎黄。\n还持今岁色，复结后年芳。', NULL, 3),
    ('赋秋日悬清光赐房玄龄', '太宗皇帝', '唐代', '秋露凝高掌，朝光上翠微。\n参差丽双阙，照耀满重闱。\n仙驭随轮转，灵乌带影飞。\n临波无定彩，入隙有圆晖。\n还当葵藿志，倾叶自相依。', NULL, 3),
    ('琵琶', '太宗皇帝', '唐代', '半月无双影，全花有四时。\n摧藏千里态，掩抑几重悲。\n促节萦红袖，清音满翠帷。\n驶弹风响急，缓曲钏声迟。\n空余关陇恨，因此代相思。', NULL, 3),
    ('宴中山', '太宗皇帝', '唐代', '驱马出辽阳，万里转旗常。\n对敌六奇举，临戎八阵张。\n斩鲸澄碧海，卷雾扫扶桑。\n昔去兰萦翠，今来桂染芳。\n云芝浮碎叶，冰镜上朝光。\n回首长安道，方欢宴柏梁。', NULL, 3),
    ('饯中书侍郎来济', '太宗皇帝', '唐代', '暧暧去尘昏灞岸，飞飞轻盖指河梁。\n云峰衣结千重叶，雪岫花开几树妆。\n深悲黄鹤孤舟远，独叹青山别路长。\n聊将分袂霑巾泪，还用持添离席觞。', NULL, 3),
    ('于太原召侍臣赐宴守岁', '太宗皇帝', '唐代', '四时运灰琯，一夕变冬春。\n送寒余雪尽，迎岁早梅新。', NULL, 3),
    ('咏烛二首 一', '太宗皇帝', '唐代', '焰听风来动，花开不待春。\n镇下千行泪，非是为思人。', NULL, 3),
    ('咏烛二首 二', '太宗皇帝', '唐代', '九龙蟠焰动，四照逐花生。\n即此流高殿，堪持待月明。', NULL, 3),
    ('咏弓', '太宗皇帝', '唐代', '上弦明月半，激箭流星远。\n落雁带书惊，啼猿映枝转。', NULL, 3),
    ('赋得早雁出云鸣', '太宗皇帝', '唐代', '初秋玉露清，早雁出空鸣。\n隔云时乱影，因风乍含声。', NULL, 3),
    ('赋得临池柳', '太宗皇帝', '唐代', '岸曲丝阴聚，波移带影疏。\n还将眉里翠，来就镜中舒。', NULL, 3),
    ('赋得临池竹', '太宗皇帝', '唐代', '贞条障曲砌，翠叶贯寒霜。\n拂牖分龙影，临池待凤翔。', NULL, 3),
    ('赋得弱柳鸣秋蝉', '太宗皇帝', '唐代', '散影玉阶柳，含翠隐鸣蝉。\n微形藏叶里，乱响出风前。', NULL, 3),
    ('探得李', '太宗皇帝', '唐代', '盘根直盈渚，交干横倚天。\n舒华光四海，卷叶荫三川。', NULL, 3),
    ('咏小山', '太宗皇帝', '唐代', '近谷交萦橤，遥峰对出莲。\n径细无全磴，松小未含烟。', NULL, 3),
    ('赐萧瑀', '太宗皇帝', '唐代', '疾风知劲草，板荡识诚臣。\n勇夫安识义，智者必怀仁。', NULL, 3),
    ('赐房玄龄', '太宗皇帝', '唐代', '太液仙舟迥，西园隐上才。\n未晓征车度，鸡鸣关早开。', NULL, 3),
    ('辽东山夜临秋', '太宗皇帝', '唐代', '烟生遥岸隐，月落半崖阴。\n连山惊鸟乱，隔岫断猿吟。', NULL, 3),
    ('赐魏徵诗', '太宗皇帝', '唐代', '醽醁胜兰生，翠涛过玉{睿/八|又/韭}。\n千日醉不醒，十年味不败。', NULL, 3),
    ('两仪殿赋柏梁体', '太宗皇帝', '唐代', '绝域降附天下平，八表无事悦圣情。\n云披雾敛天地明，登封日观禅云亭。\n太常具礼方告成。', NULL, 3),
    ('句', '太宗皇帝', '唐代', '雪耻酬百王，除凶报千古。\n昔乘匹马去，今驱万乘来。\n近日毛虽暖闻弦心已惊。', NULL, 3),
    ('太子纳妃太平公主出降', '高宗皇帝', '唐代', '龙楼光曙景，鲁馆启朝扉。\n艳日浓妆影，低星降婺辉。\n玉庭浮瑞色，银榜藻祥徽。\n云转花萦盖，霞飘叶缀旗。\n雕轩回翠陌，宝驾归丹殿。\n鸣珠佩晓衣，镂璧轮开扇。\n华冠列绮筵，兰醑申芳宴。\n环阶凤乐陈，玳席珍羞荐。\n蝶舞袖香新，歌分落素尘。\n欢凝欢懿戚，庆叶庆初姻。\n暑阑炎气息，凉早吹疏频。\n方期六合泰，共赏万年春。', NULL, 3),
    ('七夕宴悬圃二首 一', '高宗皇帝', '唐代', '羽盖飞天汉，凤驾越层峦。\n俱叹三秋阻，共叙一宵欢。\n璜亏夜月落，靥碎晓星残。\n谁能重操杼，纤手濯清澜。', NULL, 3),
    ('七夕宴悬圃二首 二', '高宗皇帝', '唐代', '霓裳转云路，凤驾俨天潢。\n亏星凋夜靥，残月落朝璜。\n促欢今夕促，长离别后长。\n轻梭聊驻织，掩泪独悲伤。', NULL, 3),
    ('过温汤', '高宗皇帝', '唐代', '温渚停仙跸，丰郊驻晓旌。\n路曲回输影，岩虚传漏声。\n暖溜惊湍驶，寒空碧雾轻。\n林黄疏叶下，野白曙霜明。\n眺听良无已，烟霞断续生。', NULL, 3),
    ('九月九日', '高宗皇帝', '唐代', '端居临玉扆，初律启金商。\n凤阙澄秋色，龙闱引夕凉。\n野净山气敛，林疏风露长。\n砌兰亏半影，岩桂发全香。\n满盖荷凋翠，圆花菊散黄。\n挥鞭争电烈，飞羽乱星光。\n柳空穿石碎，弦虚侧月张。\n怯猿啼落岫，惊雁断分行。\n斜轮低夕景，归斾拥通庄。', NULL, 3),
    ('谒慈恩寺题奘法师房', '高宗皇帝', '唐代', '停轩观福殿，游目眺皇畿。\n法轮含日转，花盖接云飞。\n翠烟香绮阁，丹霞光宝衣。\n幡虹遥合彩，定水迥分晖。\n萧然登十地，自得会三归。', NULL, 3),
    ('谒大慈恩寺', '高宗皇帝', '唐代', '日宫开万仞，月殿耸千寻。\n花盖飞团影，幡虹曳曲阴。\n绮霞遥笼帐，丛珠细网林。\n寥廓烟云表，超然物外心。', NULL, 3),
    ('守岁', '高宗皇帝', '唐代', '今宵冬律尽，来朝丽景新。\n花余凝地雪，条含煖吹分。\n绶吐芽犹嫩，冰□已镂津。\n薄红梅色冷，浅绿柳轻春。\n送迎交两节，暄寒变一辰。', NULL, 3),
    ('咸亨殿宴近臣诸亲柏梁体', '高宗皇帝', '唐代', '屏欲除奢政返淳。', NULL, 3),
    ('九月九日幸临渭亭登高得秋字', '中宗皇帝', '唐代', '九月正乘秋，三杯兴已周。\n泛桂迎尊满，吹花向酒浮。\n长房萸早熟，彭泽菊初收。', NULL, 3),
    ('登骊山高顶寓目', '中宗皇帝', '唐代', '四郊秦汉国，八水帝王都。\n阊阖雄里闬，城阙壮规模。\n贯渭称天邑，含岐实奧区。\n金门披玉馆，因此识皇图。', NULL, 3),
    ('幸秦始皇陵', '中宗皇帝', '唐代', '眷言君失德，骊邑想秦余。\n政烦方改篆，愚俗乃焚书。\n阿房久已灭，阁道遂成墟。\n欲厌东南气，翻伤掩鲍车。', NULL, 3),
    ('立春日游苑迎春', '中宗皇帝', '唐代', '神皋福地三秦邑，玉台金阙九仙家。\n寒光犹恋甘泉树，淑景偏临建始花。\n彩蝶黄莺未歌舞，梅香柳色已矜夸。\n迎春正启流霞席，暂嘱曦轮勿遽斜。', NULL, 3),
    ('十月诞辰内殿宴群臣效柏梁体联句', '中宗皇帝', '唐代', '润色鸿业寄贤才，叨居右弼愧盐梅。\n运筹帷幄荷时来，职掌图籍滥蓬莱。\n两司谬忝谢锺裴，礼乐铨管效涓埃。\n陈师振旅清九垓，欣承顾问侍天杯。\n衔恩献寿梁柏台，黄缣青简奉康哉。\n鲰生侍从忝王枚，右掖司言实不才。\n宗伯秩礼天地开，帝歌难续仰昭回。\n微臣捧日变寒灰，远惭班左愧游陪。', NULL, 3),
    ('景龙四年正月五日移仗蓬莱宫御大明殿会吐蕃骑马之戏因重为柏梁体联句', '中宗皇帝', '唐代', '大明御宇临万方，顾惭内政翊陶唐。\n鸾鸣凤舞向平阳，秦楼鲁馆沐恩光。\n无心为子辄求郎，雄才七步谢陈王。\n当熊让辇愧前芳，再司铨筦恩可忘。\n文江学海思济航，万邦考绩臣所详。\n著作不休出中肠，权豪屏迹肃严霜。\n铸鼎开岳造明堂，玉醴由来献寿觞。', NULL, 3),
    ('石淙', '中宗皇帝', '唐代', '三阳本是标灵纪，二室由来独擅名。\n霞衣霞锦千般状，云峰云岫百重生。\n水炫珠光遇泉客，岩悬石镜厌山精。\n永愿乾坤符睿算，长居膝下属欢情。', NULL, 3),
    ('石淙', '睿宗皇帝', '唐代', '奇峰嶾嶙箕山北，秀崿岹峣嵩镇南。\n地首地肺何曾拟，天目天台倍觉惭。\n树影蒙茏鄣叠岫，波深汹涌落悬潭。\n□愿紫宸居得一，永欣丹扆御通三。', NULL, 3),
    ('过晋阳宫', '明皇帝', '唐代', '缅想封唐处，实惟建国初。\n俯察伊晋野，仰观乃参虚。\n井邑龙斯跃，城池凤翔余。\n林塘犹沛泽，台榭宛旧居。\n运革祚中否，时迁命兹符。\n顾循承丕构，怵惕多忧虞。\n尚恐威不逮，复虑化未孚。\n岂徒劳辙迹，所期训戎车。\n习俗问黎人，亲巡慰里闾。\n永言念成功，颂德临康衢。\n长怀经纶日，叹息履庭隅。\n艰难安可忘，欲去良踟蹰。', NULL, 3),
    ('行次成皋途经先圣擒建德之所缅思功业感而赋诗', '明皇帝', '唐代', '有隋政昏虐，群雄已交争。\n先圣按剑起，叱咤风云生。\n饮马河洛竭，作气嵩华惊。\n克敌睿图就，擒俘帝道亨。\n顾惭嗣宝历，恭承天下平。\n幸过翦鲸地，感慕神且英。', NULL, 3),
    ('校猎义成喜逢大雪率题九韵以示群官', '明皇帝', '唐代', '弧矢威天下，旌旗游近县。\n一面施鸟罗，三驱教人战。\n暮云成积雪，晓色开行殿。\n皓然原隰同，不觉林野变。\n北风勇士马，东日华组练。\n触地银麞出，连山缟鹿见。\n月兔落高矰，星狼下急箭。\n既欣盈尺兆，复忆磻谿便。\n岁丰将遇贤，俱荷皇天眷。', NULL, 3),
    ('赐诸州刺史以题座右', '明皇帝', '唐代', '眷言思共理，鉴梦想维良。\n猗欤此推择，声绩著周行。\n贤能既俟进，黎献实伫康。\n视人当如子，爱人亦如伤。\n讲学试诵论，阡陌劝耕桑。\n虚誉不可饰，清知不可忘。\n求名迹易见，安贞德自彰。\n讼狱必以情，教民贵有常。\n恤惸且存老，抚弱复绥强。\n勉哉各祗命，知予眷万方。', NULL, 3),
    ('送忠州太守康昭远等', '明皇帝', '唐代', '端拱临中枢，缅怀共予理。\n不有台阁英，孰振循良美。\n分符侯甸内，拜手明庭里。\n誓节期饮冰，调人方导水。\n嘉声驰九牧，惠化光千祀。\n时雨侔昔贤，芳猷贯前史。\n伫尔颂中和，吾将令卿士。', NULL, 3),
    ('送李邕之任滑台', '明皇帝', '唐代', '汉家重东郡，宛彼白马津。\n黎庶既蕃殖，临之劳近臣。\n远别初首路，今行方及春。\n课成应第一，良牧尔当仁。', NULL, 3),
    ('端午三殿宴群臣探得神字', '明皇帝', '唐代', '五月符天数，五音调夏钧。\n旧来传五日，无事不称神。\n穴枕通灵气，长丝续命人。\n四时花竞巧，九子粽争新。\n方殿临华节，圆宫宴雅臣。\n进对一言重，遒文六义陈。\n股肱良足咏，风化可还淳。', NULL, 3),
    ('温汤对雪', '明皇帝', '唐代', '北风吹同云，同云飞白雪。\n白雪乍回散，同云何惨烈。\n未见温泉冰，宁知火井灭。\n表瑞良在兹，庶几可怡悦。', NULL, 3),
    ('登蒲州逍遥楼', '明皇帝', '唐代', '长榆息烽火，高柳静风尘。\n卜征巡九洛，展豫出三秦。\n昔是潜龙地，今为上理辰。\n时平乘道泰，聊赏遇年春。\n黄河分地络，飞观接天津。\n一览遗芳翰，千载肃如神。', NULL, 3),
    ('经河上公庙', '明皇帝', '唐代', '昔闻有耆叟，河上独遗荣。\n迹与尘嚻隔，心将道德幷。\n讵以天地累，宁为宠辱惊。\n矫然翔寥廓，如何屈坚贞。\n玄玄妙门启，肃肃祠宇清。\n冥漠无先后，那能纪姓名。', NULL, 3),
    ('过王濬暮', '明皇帝', '唐代', '吴国分牛斗，晋室命龙骧。\n受任敌已灭，策勋名不彰。\n居美未尽善，矜功徒自伤。\n长戟今何在？孤坟此路傍。\n不观松柏茂，空余荆棘场。\n叹嗟悬剑陇，谁识梦刀祥。', NULL, 3),
    ('初入秦川路逢寒食', '明皇帝', '唐代', '洛阳芳树映天津，灞岸垂杨窣地新。\n直为经过行处乐，不知虚度两京春。\n去年余闰今春早，曙色和风着花草。\n可怜寒食与清明，光辉幷在长安道。\n自从关路入秦川，争道何人不戏鞭。\n公子途中妨蹴踘，佳人马上废秋千。\n渭水长桥今欲渡，葱葱渐见新丰树。\n远看骊岫入云霄，预想汤池起烟雾。\n烟雾氛氲水殿开，暂拂香轮归去来。\n今岁清明行已晚，明年寒食更相陪。', NULL, 3),
    ('春台望', '明皇帝', '唐代', '暇景属三春，高台聊四望。\n目极千里际，山川一何壮。\n太华见重岩，终南分叠嶂。\n郊原纷绮错，参差多异状。\n佳气满通沟，迟步入绮楼。\n初莺一一鸣红树，归雁双双去绿洲。\n太液池中下黄鹤，昆明水上映牵牛。\n闻道汉家全盛日，别馆离宫趣非一。\n甘泉逶迤亘明光，五柞连延接未央。\n周庐徼道纵横转，飞阁回轩左右长。\n须念作劳居者逸，勿言我后焉能恤。\n为想雄豪壮柏梁，何如俭陋卑茅室。\n阳乌黯黯向山沈，夕鸟喧喧入上林。\n薄暮赏余回步辇，还念中人罢百金。', NULL, 3),
    ('过大哥宅探得歌字韵', '明皇帝', '唐代', '鲁卫情先重，亲贤爱转多。\n冕旒丰暇日，乘景暂经过。\n戚里申高宴，平台奏雅歌。\n复寻为善乐，方验保山河。', NULL, 3),
    ('同玉真公主过大哥山池', '明皇帝', '唐代', '地有招贤处，人传乐善名。\n鹜池临九达，龙岫对层城。\n桂月先秋冷，蘋风向晚清。\n凤楼遥可见，仿佛玉箫声。', NULL, 3),
    ('经邹鲁祭孔子而叹之', '明皇帝', '唐代', '夫子何为者？栖栖一代中。\n地犹鄹氏邑，宅即鲁王宫。\n叹凤嗟身否，伤麟怨道穷。\n今看两楹奠，当与梦时同。', NULL, 3),
    ('惟此温泉是称愈疾岂予独受其福思与兆人共之乘暇巡游乃言其志', '明皇帝', '唐代', '桂殿与山连，兰汤涌自然。\n阴崖含秀色，温谷吐潺湲。\n绩为蠲邪著，功因养正宣。\n愿言将亿兆，同此共昌延。', NULL, 3),
    ('旋师喜捷', '明皇帝', '唐代', '边服胡尘起，长安汉将飞。\n龙蛇开阵法，貔虎振军威。\n诈虏脑涂地，征夫血染衣。\n今朝书奏入，明日凯歌归。', NULL, 3),
    ('过老子庙', '明皇帝', '唐代', '仙居怀圣德，灵庙肃神心。\n草合人踪断，尘浓鸟迹深。\n流沙丹灶没，关路紫烟沈。\n独伤千载后，空余松柏林。', NULL, 3),
    ('途次陕州', '明皇帝', '唐代', '境出三秦外，途分二陕中。\n山川入虞虢，风俗限西东。\n树古棠阴在，耕余让畔空。\n鸣笳从此去，行见洛阳宫。', NULL, 3),
    ('野次喜雪', '明皇帝', '唐代', '拂曙辟行宫，寒皋野望通。\n繁云低远岫，飞雪舞长空。\n赋象恒依物，萦回屡逐风。\n为知勤恤意，先此示年丰。', NULL, 3),
    ('送贺知章归四明', '明皇帝', '唐代', '遗荣期入道，辞老竟抽簪。\n岂不惜贤达，其如高尚心。\n寰中得秘要，方外散幽襟。\n独有青门饯，群僚怅别深。', NULL, 3),
    ('轩游宫十五夜', '明皇帝', '唐代', '行迈离秦国，巡方赴洛师。\n路逢三五夜，春色暗中期。\n关外长河转，宫中淑气迟。\n歌钟对明月，不减旧游时。', NULL, 3),
    ('观拔河俗戏', '明皇帝', '唐代', '壮徒恒贾勇，拔拒抵长河。\n欲练英雄志，须明胜负多。\n噪齐山岌嶪，气作水腾波。\n预期年岁稔，先此乐时和。', NULL, 3),
    ('同刘晃喜雨', '明皇帝', '唐代', '节变寒初尽，时和气已春。\n繁云先合寸，膏雨自依旬。\n飒飒飞平野，霏霏静暗尘。\n悬知花叶意，朝夕望中新。', NULL, 3),
    ('千秋节赐群臣镜', '明皇帝', '唐代', '铸得千秋镜，光生百炼金。\n分将赐群后，遇象见清心。\n台上冰华澈，窗中月影临。\n更衔长绶带，留意感人深。', NULL, 3),
    ('赐道士邓紫阳', '明皇帝', '唐代', '太乙三门诀，元君六甲符。\n下传金版术，上刻玉清书。\n有美探真士，囊中得秘书。\n自知三醮后，翊我灭残胡。', NULL, 3),
    ('幸蜀西至剑门', '明皇帝', '唐代', '剑阁横云峻，銮舆出狩回。\n翠屏千仞合，丹嶂五丁开。\n灌木萦旗转，仙云拂马来。\n乘时方在德，嗟尔勒铭才。', NULL, 3),
    ('答司马承祯上剑镜', '明皇帝', '唐代', '宝照含天地，神剑合阴阳。\n日月丽光景，星斗裁文章。\n写鉴表容质，佩服为身防。\n从兹一赏玩，永德保龄长。', NULL, 3),
    ('送赵法师还蜀因名山奠简', '明皇帝', '唐代', '道家奠灵简，自昔仰神仙。\n真子今将命，苍生福可传。\n江山寻故国，城郭信依然。\n二室遥相望，云回洞里天。', NULL, 3),
    ('送道士薛季昌还山', '明皇帝', '唐代', '洞府修真客，衡阳念旧居。\n将成金阙要，愿奉玉清书。\n云路三天近，松溪万籁虚。\n犹期传秘诀，来往候仙舆。', NULL, 3),
    ('送玄同真人李抱朴谒灊山仙祠', '明皇帝', '唐代', '城阙天中近，蓬瀛海上遥。\n归期千载鹤，春至一来朝。\n采药逢三秀，餐霞卧九霄。\n参同如有旨，金鼎待君烧。', NULL, 3),
    ('春日出苑游瞩', '明皇帝', '唐代', '三阳丽景早芳辰，四序佳园物候新。\n梅花百树障去路，垂柳千条暗回津。\n鸟飞直为惊风叶，鱼没都由怯岸人。\n惟愿圣主南山寿，何愁不赏万年春。', NULL, 3),
    ('春晚宴两相及礼官丽正殿学士探得风字', '明皇帝', '唐代', '乾道运无穷，恒将人代工。\n阴阳调历象，礼乐报玄穹。\n介胄清荒外，衣冠佐域中。\n言谈延国辅，词赋引文雄。\n野霁伊川绿，郊明巩树红。\n冕旒多暇景，诗酒会春风。', NULL, 3),
    ('首夏花萼楼观群臣宴宁王山亭回楼下又申之以赏乐赋诗', '明皇帝', '唐代', '今年通闰月，入夏展春辉。\n楼下风光晚，城隅宴赏归。\n九歌扬政要，六舞散朝衣。\n天喜时相合，人和事不违。\n礼中推意厚，乐处感心微。\n别赏阳台乐，前旬暮雨飞。', NULL, 3),
    ('同二相已下群官乐游园宴', '明皇帝', '唐代', '撰日岩廊暇，需云宴乐初。\n万方朝玉帛，千品会簪裾。\n地入南山近，城分北斗余。\n池塘垂柳密，原隰野花疏。\n帟幕看逾暗，歌钟听自虚。\n兴阑归骑转，还奏弼违书。', NULL, 3),
    ('集贤书院成送张说上集贤学士赐宴得珍字', '明皇帝', '唐代', '广学开书院，崇儒引席珍。\n集贤招衮职，论道命台臣。\n礼乐沿今古，文章革旧新。\n献酬尊俎列，宾主位班陈。\n节变云初夏，时移气尚春。\n所希光史册，千载仰兹晨。', NULL, 3),
    ('王屋山送道士司马承祯还天台', '明皇帝', '唐代', '紫府求贤士，清谿祖逸人。\n江湖与城阙，异迹且殊伦。\n间有幽栖者，居然厌俗尘。\n林泉先得性，芝桂欲调神。\n地道逾稽岭，天台接海滨。\n音徽从此间，万古一芳春。', NULL, 3),
    ('早度蒲津关', '明皇帝', '唐代', '钟鼓严更曙，山河野望通。\n鸣銮下蒲坂，飞斾入秦中。\n地险关逾壮，天平镇尚雄。\n春来津树合，月落戍楼空。\n马色分朝景，鸡声逐晓风。\n所希常道泰，非复候𦈡同。', NULL, 3),
    ('途经华岳', '明皇帝', '唐代', '饬驾去京邑，鸣鸾指洛川。\n循途经太华，回跸暂周旋。\n翠崿留斜影，悬岩冒夕烟。\n四方皆石壁，五位配金天。\n仿佛看高掌，依稀听子先。\n终当铭岁月，从此记灵仙。', NULL, 3),
    ('喜雪', '明皇帝', '唐代', '日观卜先征，时巡顺物情。\n风行未备礼，云密遽飘霙。\n委树寒花发，萦空落絮轻。\n朝如玉已会，庭似月犹明。\n既睹肤先合，还欣尺有盈。\n登封何以报？因此谢功成。', NULL, 3),
    ('幸凤泉汤', '明皇帝', '唐代', '西狩观周俗，南山历汉宫。\n荐鲜知路近，省敛觉年丰。\n阴谷含神爨，汤泉养圣功。\n益龄仙井合，愈疾醴源通。\n不重鸣岐凤，谁矜陈宝雄。\n愿将无限泽，霑沐众心同。', NULL, 3),
    ('南出雀鼠谷答张说', '明皇帝', '唐代', '雷出应乾象，风行顺国人。\n川途犹在晋，车马渐归秦。\n背陕关山险，横汾鼓吹频。\n草依阳谷变，花待北岩春。\n闻有鹓鸾客，清词雅调新。\n求音思欲报，心迹竟难陈。', NULL, 3),
    ('赐崔日知往潞州', '明皇帝', '唐代', '潞国开新府，壶关宠旧林。\n妙旌循吏德，持悦庶氓心。\n礼乐中朝贵，神明列郡钦。\n扬风非赠扇，易俗是张琴。\n藩镇讴谣满，行宫雨露深。\n会书丞相策，先赐颍川金。', NULL, 3),
    ('为赵法师别造精院过院赋诗', '明皇帝', '唐代', '宗师心物外，为道运虚舟。\n不恋岩泉赏，来从宫禁游。\n探玄知几岁，习静更宜秋。\n烟树辨朝色，风湍闻夜流。\n坐朝繁听览，寻胜在清幽。\n欲广无为化，因兹庶可求。', NULL, 3),
    ('端午', '明皇帝', '唐代', '端午临中夏，时清日复长。\n盐梅已佐鼎，曲糵且传觞。\n事古人留迹，年深缕积长。\n当轩知槿茂，向水觉芦香。\n亿兆同归寿，群公共保昌。\n忠贞如不替，贻厥后昆芳。', NULL, 3),
    ('春中兴庆宫酺宴', '明皇帝', '唐代', '九达长安道，三阳别馆春。\n还将听朝暇，回作豫游晨。\n不战要荒服，无刑礼乐新。\n合酺覃土宇，欢宴接群臣。\n玉斝飞千日，琼筵荐八珍。\n舞衣云曳影，歌扇月开轮。\n伐鼓鱼龙杂，撞钟角抵陈。\n曲终酣兴晚，须有醉归人。', NULL, 3),
    ('千秋节宴', '明皇帝', '唐代', '兰殿千秋节，称名万寿觞。\n风传率土庆，日表继天祥。\n玉宇开花萼，宫县动会昌。\n衣冠白鹭下，帟幕翠云长。\n献遗成新俗，朝仪入旧章。\n月衔花绶镜，露缀彩丝囊。\n处处祠田祖，年年宴杖乡。\n深思一德事，小获万人康。', NULL, 3),
    ('左丞相说右丞相璟太子少傅乾曜同日上官命宴东堂赐诗', '明皇帝', '唐代', '赤帝收三杰，黄轩举二臣。\n由来丞相重，分掌国之钧。\n我有握中璧，双飞席上珍。\n子房推道要，仲子讶风神。\n复辍台衡老，将为调护人。\n鹓鸾同拜日，车骑拥行尘。\n乐聚南宫宴，觞连北斗醇。\n俾予成百揆，垂拱问彝伦。', NULL, 3),
    ('早登太行山中言志', '明皇帝', '唐代', '清跸度河阳，凝笳上太行。\n火龙明鸟道，铁骑绕羊肠。\n白雾埋阴壑，丹霞助晓光。\n涧泉含宿冻，山木带余霜。\n野老茅为屋，樵人薜作裳。\n宣风问耆艾，敦俗劝耕桑。\n凉德惭先哲，徽猷慕昔皇。', NULL, 3),
    ('平胡', '明皇帝', '唐代', '杂虏忽猖狂，无何敢乱常。\n羽书朝继入，烽火夜相望。\n将出凶门勇，兵因死地强。\n蒙轮皆突骑，按剑尽鹰扬。\n鼓角雄山野，龙蛇入战场。\n流膏润沙漠，溅血染锋铓。\n雾扫清玄塞，云开静朔方。\n武功今已立，文德愧前王。', NULL, 3),
    ('游兴庆宫作', '明皇帝', '唐代', '代邸青门右，离宫紫陌陲。\n庭如过沛日，水若渡江时。\n绮观连鸡岫，朱楼接雁池。\n从来敦棣萼，今此茂荆枝。\n万叶传馀庆，千年志不移。\n凭轩聊属目，轻辇共追随。\n务本方崇训，相辉保羽仪。\n时康俗易渐，德薄政难施。\n鼓吹迎飞盖，弦歌送羽卮。\n所希覃率土，孝弟一同规。', NULL, 3),
    ('送张说巡边', '明皇帝', '唐代', '端拱复垂裳，长怀御远方。\n股肱申教义，戈剑靖要荒。\n命将绥边服，雄图出庙堂。\n三台入武帐，八座起文昌。\n宝胄匡韩主，华宗辅汉王。\n茂先惭博物，平子谢文章。\n尽节恢时佐，输诚御寇场。\n三军临朔野，驷马即戎行。\n鼓吹威夷狄，旌轩溢洛阳。\n云台先著美，今日更贻芳。', NULL, 3),
    ('饯王晙巡边', '明皇帝', '唐代', '振武威荒服，扬文肃远墟。\n金坛申将礼，玉节授军符。\n免胄三方外，衔刀万里余。\n昔时吴会静，今日虏庭虚。\n分阃仍推毂，援桴且训车。\n风扬旌斾远，雨洗甲兵初。\n坐见台阶谧，行闻祅祲除。\n檄来虽插羽，箭去亦飞书。\n舟檝功须著，盐梅望匪疏。\n不应陈七德，欲使化先敷。', NULL, 3),
    ('巡省途次上党旧宫赋', '明皇帝', '唐代', '三千初击浪，九万欲抟空。\n天地犹惊否，阴阳始遇蒙。\n存贞期历试，佐贰伫昭融。\n多谢时康理，良惭实赖功。\n长怀问鼎气，夙负拔山雄。\n不学刘琨舞，先歌汉祖风。\n英髦既包括，豪杰自牢笼。\n人事一朝异，讴歌四海同。\n如何昔朱邸，今此作离宫？雁沼澄澜翠，猿岩落照红。\n小山秋桂馥，长坂旧兰丛。\n即是淹留处，乘欢乐未穷。', NULL, 3),
    ('潼关口号', '明皇帝', '唐代', '河曲回千里，关门限二京。\n所嗟非恃德，设险到天平。', NULL, 3),
    ('续薛令之题壁', '明皇帝', '唐代', '啄木觜距长，凤皇羽毛短。\n苦嫌松桂寒，任逐桑榆煖。', NULL, 3),
    ('送胡真师还西山', '明皇帝', '唐代', '仙客厌人间，孤云比性闲。\n话离情未已，烟水万重山。', NULL, 3),
    ('过大哥山池题石壁', '明皇帝', '唐代', '澄潭皎镜石崔巍，万壑千岩暗绿苔。\n林亭自有幽贞趣，况复秋深爽气来。', NULL, 3),
    ('题梅妃画真', '明皇帝', '唐代', '忆昔娇妃在紫宸，铅华不御得天真。\n霜绡虽似当时态，争奈娇波不顾人。', NULL, 3),
    ('鹡鸰颂', '明皇帝', '唐代', '伊我轩宫，奇树青葱。\n蔼周庐兮，冒霜停雪，以茂以悦。\n恣卷舒兮，连枝同荣，吐绿含英。\n曜春初兮，蓐收御节，寒露微结。\n气清虚兮，桂宫兰殿，唯所息宴。\n栖雍渠兮，行摇飞鸣，急难有情。\n情有余兮，顾惟德凉，夙夜兢惶。\n惭化疏兮，上之所教，下之所效。\n实在予兮，天伦之性，鲁卫分政。\n亲贤居兮，爰游爰处，爰笑爰语。\n巡庭除兮，观此翔禽，以悦我心，良史书兮。', NULL, 3),
    ('傀儡吟', '明皇帝', '唐代', '刻木牵丝作老翁，鸡皮鹤发与真同。\n须臾弄罢寂无事，还似人生一梦中。', NULL, 3),
    ('句', '明皇帝', '唐代', '昔见漳滨卧，言将人事违。\n今逢庆诞日，犹谓学仙归。\n棠棣花重发，鸰原鸟再飞。\n德比代云布，心如晋水清。', NULL, 3),
    ('延英殿玉灵芝诗三章章八句 一', '肃宗皇帝', '唐代', '玉殿肃肃，灵芝煌煌。\n重英发秀，连叶分房。\n宗庙之福，垂其耿光。', NULL, 3),
    ('延英殿玉灵芝诗三章章八句 二', '肃宗皇帝', '唐代', '元气产芝，明神合德。\n紫微间采，白蕣呈色。\n载启瑞图，庶符皇极。\n天心有眷，王道惟直。', NULL, 3),
    ('延英殿玉灵芝诗三章章八句 三', '肃宗皇帝', '唐代', '幸生芳本，当我扆旒。\n效此灵质，贲其王猷。\n神惟不爱，道亦无求。\n端拱思惟，永荷天休。', NULL, 3),
    ('赐棃李泌与诸王联句', '肃宗皇帝', '唐代', '先生年几许，颜色似童儿。\n夜抱九仙骨，朝披一品衣。\n不食千锺粟，唯餐两颗棃。\n天生此间气，助我化无为。', NULL, 3),
    ('中和节日宴百僚赐诗', '德宗皇帝', '唐代', '韶年启仲序，初吉谐良辰。\n肇兹中和节，式庆天地春。\n欢酣朝野同，生德区宇均。\n云开洒膏露，草疏芳河津。\n岁华今载阳，东作方肆勤。\n惭非熏风唱，曷用慰吾人。', NULL, 3),
    ('中和节赐百官燕集因示所怀', '德宗皇帝', '唐代', '至化恒在宥，保和兹息人。\n推诚抚诸夏，与物长为春。\n仲月风景暖，禁城花柳新。\n芳时协金奏，赐宴同群臣。\n丝竹岂云乐，忠贤惟所亲。\n庶洽朝野意，旷然天地均。', NULL, 3),
    ('重阳日赐宴曲江亭赋六韵诗用清字', '德宗皇帝', '唐代', '早衣对庭燎，躬化勤意诚。\n时此万机暇，适与佳节幷。\n曲池洁寒流，芳菊舒金英。\n乾坤爽气满，台殿秋光清。\n朝野庆年丰，高会多欢声。\n永怀无荒戒，良士同斯情。', NULL, 3),
    ('九月十八赐百僚追赏因书所怀', '德宗皇帝', '唐代', '雨霁霜气肃，天高云日明。\n繁林已坠叶，寒菊仍舒荣。\n懿此秋节时，更延追赏情。\n池台列广宴，丝竹传新声。\n至乐非外奖，浃欢同中诚。\n庶敦朝野意，永使风化清。', NULL, 3),
    ('送徐州张建封还镇', '德宗皇帝', '唐代', '牧守寄所重，才贤生为时。\n宣风自淮甸，授钺膺藩维。\n入觐展遐恋，临轩慰来思。\n忠诚在方寸，感激陈情词。\n报国尔所向，恤人予是资。\n欢宴不尽怀，车马当还期。\n谷雨将应候，行春犹未迟。\n勿以千里遥，而云无己知。', NULL, 3),
    ('麟德殿宴百僚', '德宗皇帝', '唐代', '忧勤承圣绪，开泰喜时康。\n恭己临群后，垂衣御八荒。\n务闲春向暮，朝罢日犹长。\n紫殿初筵列，彤庭广乐张。\n成功归辅弼，致理赖忠良。\n共此欢娱事，千秋乐未央。', NULL, 3),
    ('元日退朝观军仗归营', '德宗皇帝', '唐代', '献岁视元朔，万方咸在庭。\n端旒揖群后，回辇阅师贞。\n彩仗宿华殿，退朝归禁营。\n分行左右出，转斾风云生。\n历历趋复道，容容映层城。\n勇余矜捷技，令肃无喧声。\n眷此戎旅节，载嘉良士诚。\n顺时倾宴赏，亦以助文经。', NULL, 3),
    ('中和节赐群臣宴赋七韵', '德宗皇帝', '唐代', '东风变梅柳，万汇生春光。\n中和纪月令，方与天地长。\n耽乐岂予尚，懿兹时景良。\n庶遂亭育恩，同致寰海康。\n君臣永终始，交泰符阴阳。\n曲沼水新碧，华林桃稍芳。\n胜赏信多欢，戒之在无荒。', NULL, 3),
    ('三日书怀因示百僚', '德宗皇帝', '唐代', '佳节上元巳，芳时属暮春。\n流觞想兰亭，捧剑得金人。\n风轻水初绿，日晴花更新。\n天文信昭回，皇道颇敷陈。\n恭己每从俭，清心常保真。\n戒兹游衍乐，书以示群臣。', NULL, 3),
    ('重阳日中外同欢以诗言志因示群官', '德宗皇帝', '唐代', '[爽]节在重九，物华新雨余。\n清秋黄叶下，菊散金潭初。\n万实行就稔，百工欣所如。\n欢心畅遐迩，殊俗同车书。\n至化自敦睦，佳辰宜宴胥。\n锵锵间丝竹，济济罗簪裾。\n此乐匪足耽，此诚期永孚。', NULL, 3),
    ('重阳日即事', '德宗皇帝', '唐代', '令节晓澄霁，四郊烟霭空。\n天清白露洁，菊散黄金丛。\n寡德荷天贶，顺时休百工。\n岂怀歌钟乐，思为君臣同。\n至化在亭育，相成资始终。\n未知康衢咏，所仰惟年丰。', NULL, 3),
    ('丰年多庆九日示怀', '德宗皇帝', '唐代', '爽气肃时令，早衣闻朔鸿。\n重阳有佳节，具物欣年丰。\n皎洁暮潭色，芬敷新菊丛。\n芳尊满衢室，繁吹凝烟空。\n惠合信吾道，保和惟尔同。\n推诚至玄化，天下期为公。', NULL, 3),
    ('七月十五日题章敬寺', '德宗皇帝', '唐代', '招提迩皇邑，复道连重城。\n法筵会早秋，驾言访禅扃。\n尝闻大仙教，清净宗无生。\n七物匪吾宝，万行先求成。\n名相既双寂，繁华奚所荣。\n金风扇微凉，远烟凝翠晶。\n松院静苔色，竹房深磬声。\n境幽真虑恬，道胜外物轻。\n意适本非说，含毫空复情。', NULL, 3),
    ('中春麟德殿会百僚观新乐诗一章章十六句', '德宗皇帝', '唐代', '芳岁肇佳节，物华当仲春。\n乾坤既昭泰，烟景含氤氲。\n德浅荷玄贶，乐成思治人。\n前庭列钟鼓，广殿延群臣。\n八卦随舞意，五音转曲新。\n顾非咸池奏，庶协南风熏。\n式宴礼所重，浃欢情必均。\n同和谅在兹，万国希可亲。', NULL, 3),
    ('九日绝句', '德宗皇帝', '唐代', '禁苑秋来爽气多，昆明风动起沧波。\n中流箫鼓诚堪赏，讵假横汾发櫂歌。', NULL, 3),
    ('暮春喜雨诗', '文宗皇帝', '唐代', '风云喜际会，雷雨遂流滋。\n荐币虚陈礼，动天实精思。\n渐侵九夏节，复在三春时。\n霡霂垂朱阙，飘飖入绿墀。\n郊坰既霑足，黍稷有丰期。\n百辟同康乐，万方伫雍熙。', NULL, 3),
    ('题程修己竹障', '文宗皇帝', '唐代', '良工运精思，巧极似有神。\n临窗忽睹繁阴合，再盼真假殊未分。', NULL, 3),
    ('宫中题', '文宗皇帝', '唐代', '辇路生春草，上林花发时。\n凭高何限意，无复侍臣知。', NULL, 3),
    ('上巳日赐裴度', '文宗皇帝', '唐代', '注想待元老，识君恨不早。\n我家柱石衰，忧来学丘祷。', NULL, 3),
    ('上元日二首 一', '文宗皇帝', '唐代', '上元高会集群仙，心斋何事欲祈年。\n丹诚傥彻玉帝座，且共吾人庆大田。', NULL, 3),
    ('上元日二首 二', '文宗皇帝', '唐代', '蓂生三五叶初齐，上元羽客出桃蹊。\n不爱仙家登真诀，愿蒙四海福黔黎。', NULL, 3),
    ('夏日联句', '文宗皇帝', '唐代', '人皆苦炎热，我爱夏日长。\n熏风自南来，殿阁生微凉。', NULL, 3),
    ('百丈山', '宣宗皇帝', '唐代', '大雄真迹枕危峦，梵宇层楼耸万般。\n日月每从肩上过，山河长在掌中看。\n仙峰不间三春秀，灵境何时六月寒？更有上方人罕到，暮钟朝磬碧云端。', NULL, 3),
    ('吊白居易', '宣宗皇帝', '唐代', '缀玉联珠六十年，谁教冥路作诗仙。\n浮云不系名居易，造化无为字乐天。\n童子解吟《长恨曲》，胡儿能唱《琵琶篇》。\n文章已满行人耳，一度思卿一怆然。', NULL, 3),
    ('幸华严寺', '宣宗皇帝', '唐代', '云散晴山几万重，烟收春色更冲融。\n帐殿出空登碧汉，遐川俯望色蓝笼。\n林光入户低韶景，岭气通宵展霁风。\n今日追游何所似，莫惭汉武赏汾中。', NULL, 3),
    ('重阳锡宴群臣', '宣宗皇帝', '唐代', '款塞旋征骑，和戎委庙贤。\n倾心方倚注，叶力共安边。', NULL, 3),
    ('题泾县水西寺', '宣宗皇帝', '唐代', '大殿连云接爽溪，钟声还与鼓声齐。\n长安若问江南事，说道风光在水西。', NULL, 3),
    ('瀑布联句', '宣宗皇帝', '唐代', '千岩万壑不辞劳，远看方知出处高。\n溪涧岂能留得住，终归大海作波涛。', NULL, 3),
    ('句', '宣宗皇帝', '唐代', '海岳宴咸通，七载秉钧调四序。\n一方狱市获来苏。', NULL, 3),
    ('咏雷句', '昭宗皇帝', '唐代', '只解劈牛兼劈树，不能诛恶与诛凶。', NULL, 3),
    ('春游曲', '文德皇后', '唐代', '上苑桃花朝日明，兰闺艳妾动春情。\n井上新桃偷面色，檐边嫩柳学身轻。\n花中来去看舞蝶，树上长短听啼莺。\n林下何须远借问，出众风流旧有名。', NULL, 3),
    ('曳鼎歌', '则天皇后', '唐代', '羲农首出，轩昊膺期。\n唐虞继踵，汤禹乘时。\n天下光宅，海内雍熙。\n上玄降鉴，方建隆基。', NULL, 3),
    ('唐享昊天乐 第一', '则天皇后', '唐代', '太阴凝至化，真耀蕴轩仪。\n德迈娥台敞，仁高[姒]幄披。\n扪天遂启极，梦日乃升曦。', NULL, 3),
    ('唐享昊天乐 第二', '则天皇后', '唐代', '瞻紫极，望玄穹。\n翘至恳，罄深衷。\n听虽远，诚必通。\n垂厚泽，降云宫。', NULL, 3),
    ('唐享昊天乐 第三', '则天皇后', '唐代', '乾仪混成冲邃，天道下济高明。\n闿阳晨披紫阙，太一晓降黄庭。\n圜坛敢申昭报，方璧冀展虔情。\n丹襟式敷衷恳，玄鉴庶察微诚。', NULL, 3),
    ('唐享昊天乐 第四', '则天皇后', '唐代', '巍巍叡业广，赫赫圣基隆。\n菲德承先顾，祯符萃眇躬。\n铭开武岩侧，图荐洛川中。\n微诚讵幽感，景命忽昭融。\n有怀惭紫极，无以谢玄穹。', NULL, 3),
    ('唐享昊天乐 第五', '则天皇后', '唐代', '朝坛雾卷，曙岭烟沈。\n爰设筐币，式表诚心。\n筵辉丽璧，乐畅和音。\n仰惟灵鉴，俯察翘襟。', NULL, 3),
    ('唐享昊天乐 第六', '则天皇后', '唐代', '昭昭上帝，穆穆下临。\n礼崇备物，乐奏锵金。\n兰羞委荐，桂醑盈斟。\n敢希明德，幸罄庄心。', NULL, 3),
    ('唐享昊天乐 第七', '则天皇后', '唐代', '尊浮九酝，礼备三周。\n陈诚菲奠，契福神猷。', NULL, 3),
    ('唐享昊天乐 第八', '则天皇后', '唐代', '奠璧郊坛昭大礼，锵金拊石表虔诚。\n始奏承云娱帝赏，复歌调露畅韶英。', NULL, 3),
    ('唐享昊天乐 第九', '则天皇后', '唐代', '荷恩承顾托，执契恭临抚。\n庙略静边荒，天兵曜神武。\n有截资先化，无为遵旧矩。\n祯符降昊穹，大业光寰宇。', NULL, 3),
    ('唐享昊天乐 第十', '则天皇后', '唐代', '肃肃祀典，邕邕礼秩。\n三献已周，九成斯毕。\n爰撤其俎，载迁其实。\n或升或降，惟诚惟质。', NULL, 3),
    ('唐享昊天乐 第十一', '则天皇后', '唐代', '礼终肆类，乐阕九成。\n仰惟明德，敢荐非馨。\n顾惭菲奠，久驰云𫐌。\n瞻荷灵泽，悚恋兼盈。', NULL, 3),
    ('唐享昊天乐 第十二', '则天皇后', '唐代', '式干路，辟天扉。\n回日驭，动云衣。\n登金阙，入紫微。\n望仙驾，仰恩徽。', NULL, 3),
    ('唐明堂乐章 外办将出', '则天皇后', '唐代', '总章陈昔典，衢室礼惟神。\n宏规则天地，神用叶陶钧。\n负扆三春旦，充庭万宇宾。\n顾己诚虚薄，空惭驭兆人。', NULL, 3),
    ('唐明堂乐章 皇帝行', '则天皇后', '唐代', '仰膺历数，俯顺讴歌。\n远安迩肃，俗阜时和。\n化光玉镜，讼息金科。\n方兴典礼，永戢干戈。', NULL, 3),
    ('唐明堂乐章 皇嗣出入升降', '则天皇后', '唐代', '至人光俗，大孝通神。\n谦以表性，恭惟立身。\n洪规载启，茂典方陈。\n誉隆三善，祥开万春。', NULL, 3),
    ('唐明堂乐章 迎送王公', '则天皇后', '唐代', '千官肃事，万国朝宗。\n载延百辟，爰集三宫。\n君臣德合，鱼水斯同。\n睿图方永，周历长隆。', NULL, 3),
    ('唐明堂乐章 登歌', '则天皇后', '唐代', '礼崇宗祀，志表严禋。\n笙镛合奏，文物惟新。\n敬遵茂典，敢择良辰。\n絜诚斯著，奠谒方申。', NULL, 3),
    ('唐明堂乐章 配飨', '则天皇后', '唐代', '笙镛间玉宇，文物昭清辉。\n晬影临芳奠，休光下太微。\n孝思期有感，明絜庶无违。', NULL, 3),
    ('唐明堂乐章 宫音', '则天皇后', '唐代', '履艮包群望，居中冠百灵。\n万方资广运，庶品荷财成。\n神功谅匪测，盛德实难名。\n藻奠申诚敬，恭祀表惟馨。', NULL, 3),
    ('唐明堂乐章 角音', '则天皇后', '唐代', '出震位，开平秩。\n扇条风，乘甲乙。\n龙德盛，鸟星出。\n荐珪篚，陈诚实。', NULL, 3),
    ('唐明堂乐章 征音', '则天皇后', '唐代', '赫赫离精御炎陆，滔滔炽景开隆暑。\n冀延神鉴俯兰尊，式表虔襟陈桂俎。', NULL, 3),
    ('唐明堂乐章 商音', '则天皇后', '唐代', '律中夷则，序应收成。\n功宣建武，义表惟明。\n爰申礼奠，庶展翘诚。\n九秋是式，百谷斯盈。', NULL, 3),
    ('唐明堂乐章 羽音', '则天皇后', '唐代', '葭律肇启隆冬，蘋藻攸陈飨祭。\n黄锺既陈玉烛，红粒方殷稔岁。', NULL, 3),
    ('唐大飨拜洛乐章 昭和', '则天皇后', '唐代', '九玄眷命，三圣基隆。\n奉成先旨，明台毕功。\n宗祀展敬，冀表深衷。\n永昌帝业，式播淳风。', NULL, 3),
    ('唐大飨拜洛乐章 致和', '则天皇后', '唐代', '神功不测兮运阴阳，包藏万宇兮孕八荒。\n天符既出兮帝业昌，愿临明祀兮降祯祥。', NULL, 3),
    ('唐大飨拜洛乐章 咸和', '则天皇后', '唐代', '坎泽祠容备举，坤坛祭典爰申。\n灵眷遥行秘躅，嘉贶荐委殊珍。\n肃礼恭禋载展，翘襟邈志逾殷。\n方期交际悬应。', NULL, 3),
    ('唐大飨拜洛乐章 九和', '则天皇后', '唐代', '祗荷坤德，钦若乾灵。\n惭惕罔寘，兴居匪宁。\n恭崇礼则，肃奉仪形。\n惟凭展敬，敢荐非馨。', NULL, 3),
    ('唐大飨拜洛乐章 拜洛', '则天皇后', '唐代', '菲躬承睿顾，薄德忝坤仪。\n乾乾遵后命，翼翼奉先规。\n抚俗勤虽切，还淳化尚亏。\n未能弘至道，何以契明祇。', NULL, 3),
    ('唐大飨拜洛乐章 显和', '则天皇后', '唐代', '顾德有惭虚菲，明祇屡降祯符。\n汜水初呈秘象，温洛荐表昌图。\n玄泽流恩载洽，丹襟荷渥增愉。', NULL, 3),
    ('唐大飨拜洛乐章 敬和', '则天皇后', '唐代', '兰俎既升，蘋羞可荐。\n金石载设，咸英已变。\n林泽斯总，山川是遍。\n敢用敷诚，实惟忘倦。', NULL, 3),
    ('唐大飨拜洛乐章 齐和', '则天皇后', '唐代', '沈潜演贶分三极，广大凝祯总万方。\n既荐羽旌文化启，还呈干戚武威扬。', NULL, 3),
    ('唐大飨拜洛乐章 德和', '则天皇后', '唐代', '夕惕同龙契，晨兢当凤扆。\n崇儒习旧规，偃伯循先旨。\n绝壤飞冠盖，遐区丽山水。\n幸承三圣余，忻属千年始。', NULL, 3),
    ('唐大飨拜洛乐章 禋和', '则天皇后', '唐代', '百礼崇容，千官肃事。\n灵降舞兆，神凝有粹。\n奠享咸周，威仪毕备。\n奏夏登列，歌雍撤肆。', NULL, 3),
    ('唐大飨拜洛乐章 通和', '则天皇后', '唐代', '皇皇灵眷，穆穆神心。\n暂动凝质，还归积阴。\n功玄枢纽，理寂高深。\n衔恩佩德，耸志翘襟。', NULL, 3),
    ('唐大飨拜洛乐章 归和', '则天皇后', '唐代', '言旋云洞兮蹑烟涂，永宁中宇兮安下都。\n包涵动植兮顺荣枯，长贻宝贶兮赞璇图。', NULL, 3),
    ('唐武氏享先庙乐章', '则天皇后', '唐代', '先德谦㧑冠昔，严规节素超今。\n奉国忠诚每竭，承家至孝纯深。\n追崇惧乖尊意，显号恐玷徽音。\n既迫王公屡请，方乃俯遂群心。\n有限无由展敬，奠醑每阙亲斟。\n大礼虔申典册，蘋藻敬荐翘襟。', NULL, 3),
    ('早春夜宴', '则天皇后', '唐代', '九春开上节，千门敞夜扉。\n兰灯吐新焰，桂魄朗圆辉。\n送酒惟须满，流杯不用稀。\n务使霞浆兴，方乘泛洛归。', NULL, 3),
    ('游九龙潭', '则天皇后', '唐代', '山窗游玉女，涧户对琼峰。\n岩顶翔双凤，潭心倒九龙。\n酒中浮竹叶，杯上写芙蓉。\n故验家山赏，惟有风入松。', NULL, 3),
    ('赠胡天师', '则天皇后', '唐代', '高人叶高志，山服往山家。\n迢迢间风月，去去隔烟霞。\n碧岫窥玄洞，玉灶炼丹砂。\n今日星津上，延首望灵槎。', NULL, 3),
    ('从驾幸少林寺', '则天皇后', '唐代', '陪銮游禁苑，侍赏出兰闱。\n云偃攒峰盖，霞低插浪旗。\n日宫疏涧户，月殿启岩扉。\n金轮转金地，香阁曳香衣。\n铎吟轻吹发，幡摇薄雾霏。\n昔遇焚芝火，山红连野飞。\n花台无半影，莲塔有全辉。\n实赖能仁力，攸资善世威。\n慈缘兴福绪，于此罄归依。\n风枝不可静，泣血竟何追。', NULL, 3),
    ('石淙', '则天皇后', '唐代', '三山十洞光玄箓，玉峤金峦镇紫微。\n均露均霜标胜壤，交风交雨列皇畿。\n万仞高岩藏日色，千寻幽涧浴云衣。\n且驻欢筵赏仁智，雕鞍薄晚杂尘飞。', NULL, 3),
    ('腊日宣诏幸上苑', '则天皇后', '唐代', '明朝游上苑，火急报春知。\n花须连夜发，莫待晓风吹。', NULL, 3),
    ('如意娘', '则天皇后', '唐代', '看朱成碧思纷纷，憔悴支离为忆君。\n不信比来长下泪，开箱验取石榴裙。', NULL, 3),
    ('制袍字赐狄仁杰', '则天皇后', '唐代', '敷政术，守清勤。\n升显位，励相臣。', NULL, 3),
    ('拟小山篇', '徐贤妃', '唐代', '仰幽岩而流盼，抚桂枝以凝想。\n将千龄兮此遇，荃何为兮独往。', NULL, 3),
    ('长门怨', '徐贤妃', '唐代', '旧爱柏梁台，新宠昭阳殿。\n守分辞芳辇，含情泣团扇。\n一朝歌舞荣，夙昔诗书贱。\n颓恩诚已矣，覆水难重荐。', NULL, 3),
    ('秋风函谷应诏', '徐贤妃', '唐代', '秋风起函谷，劲气动河山。\n偃松千岭上，杂雨二陵间。\n低云愁广隰，落日惨重关。\n此时飘紫气，应验真人还。', NULL, 3),
    ('赋得北方有佳人', '徐贤妃', '唐代', '由来称独立，本自号倾城。\n柳叶眉间发，桃花脸上生。\n腕摇金钏响，步转玉环鸣。\n纤腰宜宝袜，红衫艳织成。\n悬知一顾重，别觉舞腰轻。', NULL, 3),
    ('进太宗', '徐贤妃', '唐代', '朝来临镜台，妆罢暂裴回。\n千金始一笑，一召讵能来。', NULL, 3),
    ('奉和圣制立春日侍宴内殿出翦彩花应制', '上官昭容', '唐代', '密叶因裁吐，新花逐翦舒。\n攀条虽不谬，摘橤讵知虚。\n春至由来发，秋还未肯疏。', NULL, 3),
    ('九月九日上幸慈恩寺登浮图群臣上菊花寿酒', '上官昭容', '唐代', '帝里重阳节，香园万乘来。\n却邪萸入佩，献寿菊传杯。\n塔类承天涌，门疑待佛开。\n睿词悬日月，长得仰昭回。', NULL, 3),
    ('彩书怨', '上官昭容', '唐代', '叶下洞庭初，思君万里余。\n露浓香被冷，月落锦屏虚。\n欲奏江南曲，贪封蓟北书。\n书中无别意，惟怅久离居。', NULL, 3),
    ('驾幸三会寺应制', '上官昭容', '唐代', '释子谈经处，轩臣刻字留。\n故台遗老识，残简圣皇求。\n驻跸怀千古，开襟望九州。\n四山缘塞合，二水夹城流。\n宸翰陪瞻仰，天杯接献酬。\n太平词藻盛，长愿纪鸿休。', NULL, 3),
    ('驾幸新丰温泉宫献诗三首 一', '上官昭容', '唐代', '三冬季月景[龙]年，万乘观风出灞川。\n遥看电跃龙为马，回瞩霜原玉作田。', NULL, 3),
    ('驾幸新丰温泉宫献诗三首 二', '上官昭容', '唐代', '鸾旗掣曳拂空回，羽骑骖驔蹑景来。\n隐隐骊山云外耸，迢迢御帐日边开。', NULL, 3),
    ('驾幸新丰温泉宫献诗三首 三', '上官昭容', '唐代', '翠幕珠帏敞月营，金罍玉斝泛兰英。\n岁岁年年常扈跸，长长久久乐升平。', NULL, 3),
    ('游长宁公主流杯池二十五首 一', '上官昭容', '唐代', '逐仙赏，展幽情。\n逾昆阆，迈蓬瀛。', NULL, 3),
    ('游长宁公主流杯池二十五首 二', '上官昭容', '唐代', '游鲁馆，陟秦台。\n污山壁，愧琼瓌。', NULL, 3),
    ('游长宁公主流杯池二十五首 三', '上官昭容', '唐代', '檀栾竹影，飙{风日}松声。\n不烦歌吹，自足娱情。', NULL, 3),
    ('游长宁公主流杯池二十五首 四', '上官昭容', '唐代', '仰循茅宇，俯眄乔枝。\n烟霞问讯，风月相知。', NULL, 3),
    ('游长宁公主流杯池二十五首 五', '上官昭容', '唐代', '枝条郁郁，文质彬彬。\n山林作伴，松桂为邻。', NULL, 3),
    ('游长宁公主流杯池二十五首 六', '上官昭容', '唐代', '清波汹涌，碧树冥蒙。\n莫怪留步，因攀桂丛。', NULL, 3),
    ('游长宁公主流杯池二十五首 七', '上官昭容', '唐代', '莫论圆峤，休说方壶。\n何如鲁馆，即是仙都。', NULL, 3),
    ('游长宁公主流杯池二十五首 八', '上官昭容', '唐代', '玉环腾远创，金埒荷殊荣。\n弗玩珠玑饰，仍留仁智情。\n凿山便作室，凭树即为楹。\n公输与班尔，从此遂韬声。', NULL, 3),
    ('游长宁公主流杯池二十五首 九', '上官昭容', '唐代', '登山一长望，正遇九春初。\n结驷填街术，闾阎满邑居。\n鬬雪梅先吐，惊风柳未舒。\n直愁斜日落，不畏酒尊虚。', NULL, 3),
    ('游长宁公主流杯池二十五首 十', '上官昭容', '唐代', '霁晓气清和，披襟赏薜萝。\n玳瑁凝春色，琉璃漾水波。\n跂石聊长啸，攀松乍短歌。\n除非物外者，谁就此经过。', NULL, 3),
    ('游长宁公主流杯池二十五首 十一', '上官昭容', '唐代', '暂尔游山第，淹留惜未归。\n霞窗明月满，涧户白云飞。\n书引藤为架，人将薜作衣。\n此真攀玩所，临睨赏光辉。', NULL, 3),
    ('游长宁公主流杯池二十五首 十二', '上官昭容', '唐代', '放旷出烟云，萧条自不群。\n漱流清意府，隐几避嚻氛。\n石画妆苔色，风梭织水文。\n山室何为贵？唯余兰桂熏。', NULL, 3),
    ('游长宁公主流杯池二十五首 十三', '上官昭容', '唐代', '策杖临霞岫，危步下霜蹊。\n志逐深山静，途随曲涧迷。\n渐觉心神逸，俄看云雾低。\n莫怪人题树，祗为赏幽栖。', NULL, 3),
    ('游长宁公主流杯池二十五首 十四', '上官昭容', '唐代', '攀藤招逸客，偃桂协幽情。\n水中看树影，风里听松声。', NULL, 3),
    ('游长宁公主流杯池二十五首 十五', '上官昭容', '唐代', '携琴侍叔夜，负局访安期。\n不应题石壁，为记赏山时。', NULL, 3),
    ('游长宁公主流杯池二十五首 十六', '上官昭容', '唐代', '泉石多仙趣，岩壑写奇形。\n欲知堪悦耳，唯听水泠泠。', NULL, 3),
    ('游长宁公主流杯池二十五首 十七', '上官昭容', '唐代', '岩壑恣登临，莹目复怡心。\n风篁类长笛，流水当鸣琴。', NULL, 3),
    ('游长宁公主流杯池二十五首 十八', '上官昭容', '唐代', '懒步天台路，惟登地肺山。\n幽岩仙桂满，今日恣情攀。', NULL, 3),
    ('游长宁公主流杯池二十五首 十九', '上官昭容', '唐代', '暂游仁智所，萧然松桂情。\n寄言栖遁客，勿复访蓬瀛。', NULL, 3),
    ('游长宁公主流杯池二十五首 二十', '上官昭容', '唐代', '瀑溜晴疑雨，丛篁昼似昏。\n山中真可玩，暂请报王孙。', NULL, 3),
    ('游长宁公主流杯池二十五首 二十一', '上官昭容', '唐代', '傍池聊试笔，倚石旋题诗。\n豫弹山水调，终拟从锺期。', NULL, 3),
    ('游长宁公主流杯池二十五首 二十二', '上官昭容', '唐代', '横铺豹皮褥，侧带鹿胎巾。\n借问何为者，山中有逸人。', NULL, 3),
    ('游长宁公主流杯池二十五首 二十三', '上官昭容', '唐代', '沁水田园先自多，齐城楼观更无过。\n倩语张骞莫辛苦，人今从此识天河。', NULL, 3),
    ('游长宁公主流杯池二十五首 二十四', '上官昭容', '唐代', '参差碧岫耸莲花，潺湲绿水莹金沙。\n何须远访三山路，人今已到九仙家。', NULL, 3),
    ('游长宁公主流杯池二十五首 二十五', '上官昭容', '唐代', '凭高瞰险足怡心，菌阁桃源不暇寻。\n余雪依林成玉树，残霙点岫即瑶岑。', NULL, 3),
    ('句', '上官昭容', '唐代', '势如连璧友，心似臭兰人。', NULL, 3),
    ('赠张云容舞', '杨贵妃', '唐代', '罗袖动香香不已，红蕖袅袅秋烟里。\n轻云岭上乍摇风，嫩柳池边初拂水。', NULL, 3),
    ('谢赐珍珠', '江妃', '唐代', '桂叶双眉久不描，残妆和泪污红绡。\n长门尽日无梳洗，何必珍珠慰寂寥。', NULL, 3),
    ('黄台瓜辞', '章怀太子', '唐代', '种瓜黄台下，瓜熟子离离。\n一摘使瓜好，再摘使瓜稀。\n三摘犹自可，摘绝抱蔓归。', NULL, 3),
    ('奉和同太子监守违恋', '韩王元嘉', '唐代', '乾象开层构，离明启少阳。\n卜征从献吉，守器属元良。\n逖矣凌周诵，遥哉掩汉庄。\n好士倾南洛，多才盛北场。\n地分丹鹫岭，途间白云乡。\n储诚虔晓夕，宸爱积炎凉。\n珠璧连霄汉，万物仰重光。', NULL, 3),
    ('奉和圣制过温汤', '越王贞', '唐代', '凤辇腾宸驾，骊籞次干游。\n坎德疏温液，山隈派暖流。\n寒氛空外拥，蒸气沼中浮。\n林凋帷影散，云敛盖阴收。\n霜郊畅玄览，参差落景遒。', NULL, 3),
    ('石桥', '信安王祎', '唐代', '别有经行所，迥跨重峦侧。\n粤因求瘼余，倏想寻真域。\n放情恣披拂，杖策聊□□。\n□□□□□，□□□□色。\n乱幡雾中见，雁塔云间识。\n薄烟幂远郊，遥峰没归翼。\n仙桥危石架，幽洞乘□□。\n□□□□□，□□□易测。\n二教无先后，一相平而直。\n冀兹捐俗心，永怀依妙力。', NULL, 3),
    ('虚池驿题屏风', '宜芬公主', '唐代', '出嫁辞乡国，由来此别难。\n圣恩愁远道，行路泣相看。\n沙塞容颜尽，边隅粉黛残。\n妾心何所断，他日望长安。', NULL, 3),
    ('嘲陆畅', '女学士宋氏若华', '唐代', '十二层楼倚翠空，凤鸾相对立梧桐。\n双成走报监门卫，莫使吴歈入汉宫。', NULL, 3),
    ('奉和御制麟德殿宴百僚应制', '尚宫宋氏若昭', '唐代', '垂衣临八极，肃穆四门通。\n自是无为化，非关辅弼功。\n修文招隐伏，尚武殄妖凶。\n德炳韶光炽，恩霑雨露浓。\n衣冠陪御宴，礼乐盛朝宗。\n万寿称觞举，千年信一同。', NULL, 3),
    ('奉和御制麟德殿宴百官', '尚宫宋氏若宪', '唐代', '端拱承休命，时清荷圣皇。\n四聪闻受谏，五服远朝王。\n景媚莺初啭，春残日更长。\n命筵多济济，盛乐复锵锵。\n酆镐谁将敌，横汾未可方。\n愿齐山岳寿，祉福永无疆。', NULL, 3),
    ('奉和麟德殿宴百僚应[制]', '鲍氏君徽', '唐代', '睿泽先寰海，功成展武韶。\n戈鋋清外垒，文物盛中朝。\n圣祚山河固，宸章日月昭。\n玉筵鸾鹄集，仙管凤皇调。\n御柳新低绿，宫莺乍啭娇。\n愿将[亿]兆庆，千祀奉神尧。', NULL, 3),
    ('关山月', '鲍氏君徽', '唐代', '高高秋月明，北照辽阳城。\n塞迥光初满，风多晕更生。\n征人望乡思，战马闻鼙惊。\n朔风悲边草，胡沙暗虏营。\n霜凝匣中剑，风惫原上旌。\n早晚谒金阙，不闻刁斗声。', NULL, 3),
    ('惜花吟', '鲍氏君徽', '唐代', '枝上花，花下人，可怜颜色俱青春。\n昨日看花花灼灼，今朝看花花欲落。\n不如尽此花下欢，莫待春风总吹却。\n莺歌蝶舞韶光长，红炉煮茗松花香。\n妆成罢吟恣游后，独把芳枝归洞房。', NULL, 3),
    ('东亭茶宴', '鲍氏君徽', '唐代', '闲朝向晓出帘栊，茗宴东亭四望通。\n远眺城池山色里，俯聆弦管水声中。\n幽篁引沼新抽翠，芳槿低檐欲吐红。\n坐久此中无限兴，更怜团扇起清风。', NULL, 3),
    ('夜梦', '萧妃', '唐代', '昨日梦君归，贱妾下鸣机。\n极知意气薄，不着去时衣。\n故言如梦里，赖得雁书飞。', NULL, 3),
    ('咏灯', '南唐先主李昪', '唐代', '一点分明值万金，开时惟怕冷风侵。\n主人若也勤挑拨，敢向尊前不尽心。', NULL, 3),
    ('游后湖赏莲花', '嗣主璟', '唐代', '蓼花蘸水火不灭，水鸟惊鱼银梭投。\n满目荷花千万顷，红碧相杂敷清流。\n孙武已斩吴宫女，琉璃池上佳人头。', NULL, 3),
    ('保大五年元日大雪同太弟景遂汪王景逷齐王景逵进士李建勋中书徐铉勤政殿学士张义方登楼赋', '嗣主璟', '唐代', '珠帘高卷莫轻遮，往往相逢隔岁华。\n春气昨宵飘律管，东风今日放梅花。\n素姿好把芳姿掩，落势还同舞势斜。\n坐有宾朋尊有酒，可怜清味属侬家。', NULL, 3),
    ('句', '嗣主璟', '唐代', '灵槎思浩荡，老鹤倚崆峒。\n苍苔迷古道，红叶乱朝霞。\n栖凤枝梢犹软弱，化龙形状已依稀。', NULL, 3),
    ('九月十日偶书', '后主煜', '唐代', '晚雨秋阴酒乍醒，感时心绪杳难平。\n黄花冷落不成艳，红叶飕飗竞鼓声。\n背世返能厌俗态，偶缘犹未忘多情。\n自从双鬓斑斑白，不学安仁却自惊。', NULL, 3),
    ('秋莺', '后主煜', '唐代', '残莺何事不知秋，横过幽林尚独游。\n老舌百般倾耳听，深黄一点入烟流。\n栖迟背世同悲鲁，浏亮如笙碎在缑。\n莫更留连好归去，露华凄冷蓼花愁。', NULL, 3),
    ('病起题山舍壁', '后主煜', '唐代', '山舍初成病乍轻，杖藜巾褐称闲情。\n炉开小火深回暖，沟引新流几曲声。\n暂约彭涓安朽质，终期宗远问无生。\n谁能役役尘中累，贪合鱼龙构强名。', NULL, 3),
    ('送邓王二十弟从益牧宣城', '后主煜', '唐代', '且维轻舸更迟迟，别酒重倾惜解携。\n浩浪侵愁光荡漾，乱山凝恨色高低。\n君驰桧楫情何极，我凭阑干日向西。\n咫尺烟江几多地，不须怀抱重凄凄。', NULL, 3),
    ('渡中江望石城泣下', '后主煜', '唐代', '江南江北旧家乡，三十年来梦一场。\n吴苑宫闱今冷落，广陵台殿已荒凉。\n云笼远岫愁千片，雨打归舟泪万行。\n兄弟四人三百口，不堪闲坐细思量。', NULL, 3),
    ('挽辞 一', '后主煜', '唐代', '珠碎眼前珍，花凋世外春。\n未销心里恨，又失掌中身。\n玉笥犹残药，香奁已染尘。\n前哀将后感，无泪可霑巾。', NULL, 3),
    ('挽辞 二', '后主煜', '唐代', '艳质同芳树，浮危道略同。\n正悲春落实，又苦雨伤丛。\n秾丽今何在？飘零事已空。\n沈沈无问处，千载谢东风。', NULL, 3),
    ('悼诗', '后主煜', '唐代', '永念难消释，孤怀痛自嗟。\n雨深秋寂莫，愁引病增加。\n咽绝风前思，昏蒙眼上花。\n空王应念我，穷子正迷家。', NULL, 3),
    ('感怀 一', '后主煜', '唐代', '又见桐花发旧枝，一楼烟雨暮凄凄。\n凭阑惆怅人谁会，不觉澘然泪眼低。', NULL, 3),
    ('感怀 二', '后主煜', '唐代', '层城无复见娇姿，佳节缠哀不自持。\n空有当年旧烟月，芙蓉城上哭蛾眉。', NULL, 3),
    ('梅花', '后主煜', '唐代', '殷勤移植地，曲槛小阑边。\n共约重芳日，还忧不盛妍。\n阻风开步障，乘月溉寒泉。\n谁料花前后，蛾眉却不全。', NULL, 3),
    ('书灵筵手巾', '后主煜', '唐代', '浮生共憔悴，壮岁失婵娟。\n汗手遗香渍，痕眉染黛烟。', NULL, 3),
    ('书琵琶背', '后主煜', '唐代', '侁自肩如削，难胜数缕绦。\n天香留凤尾，余煖在檀槽。', NULL, 3),
    ('病中感怀', '后主煜', '唐代', '憔悴年来甚，萧条益自伤。\n风威侵病骨，雨气咽愁肠。\n夜鼎唯煎药，朝髭半染霜。\n前缘竟何似，谁与问空王。', NULL, 3),
    ('病中书事', '后主煜', '唐代', '病身坚固道情深，宴坐清香思自任。\n月照静居唯𢭏药，门扃幽院只来禽。\n庸医懒听词何取，小婢将行力未禁。\n赖问空门知气味，不然烦恼万涂侵。', NULL, 3),
    ('赐宫人庆奴', '后主煜', '唐代', '风情渐老见春羞，到处消魂感旧游。\n多谢长条似相识，强垂烟态拂人头。', NULL, 3),
    ('题金楼子后', '后主煜', '唐代', '牙签万轴裹红绡，王粲书同付火烧。\n不于祖龙留面目，遗篇那得到今朝。', NULL, 3),
    ('句', '后主煜', '唐代', '迢迢牵牛星，杳在河之阳。\n粲粲黄姑女，耿耿遥相望。\n莺狂应有恨，蝶舞已无多。\n揖让月在手，动摇风满怀。\n病态如衰弱，厌厌向五年。\n衰颜一病难牵复，晓殿君临颇自羞。\n冷笑秦皇经远略，静怜姬满苦时巡。\n鬓从今日添新白，菊是去年依旧黄。\n万古到头归一死，醉乡葬地有高原。\n人生不满百，刚作千年画。\n日暎仙云薄，秋高天碧深。\n乌照始潜辉，龙烛便争秉。\n凝珠满露枝，游飏日已西。\n肃穆寒初至，九重开扇鹄。\n四牖炳灯鱼，羽觞无算酌。\n倾碗更为寿，深卮递酬宾。', NULL, 3),
    ('蔷薇诗一首十八韵呈东海侍郎徐铉', '韩王从善', '唐代', '绿影覆幽池，芳菲四月时。\n管弦朝夕兴，组绣百千枝。\n盛引墙看遍，高烦架屡移。\n露轻濡彩笔，蜂误拂吟髭。\n日照玲珑幔，风摇翡翠帷。\n早红飘藓地，狂蔓挂蛛丝。\n嫩刺牵衣细，新条窣草垂。\n晚香难暂舍，娇态自相窥。\n深浅分前后，荣华互盛衰。\n尊前留客久，月下欲归迟。\n何处繁临砌？谁家密映篱。\n绛罗房灿烂，碧玉叶参差。\n分得殷勤种，开来远近知。\n晶荧歌袖袂，柔弱舞腰支。\n膏麝谁将比，庭萱自合嗤。\n匀妆低水鉴，泣泪滴烟{雨/斯}。\n画拟凭梁广，名宜亚楚姬。\n寄君十八韵，思拙愧新奇。', NULL, 3),
    ('观棋', '吉王从谦', '唐代', '竹林二君子，尽日竟沈吟。\n相对终无语，争先各有心。\n恃强斯有失，守分固无侵。\n若算机筹处，沧沧海未深。', NULL, 3),
    ('赠别唐太师道袭', '蜀高祖王建', '唐代', '丱岁便将为肘腋，二纪何曾离一日。\n更深犹尚立案前，敷奏柔和不伤物。\n今朝荣贵慰我心，双旌引向重城出。\n褒斜旧地委勋贤，从此生灵永泰息。', NULL, 3),
    ('幸秦川上梓潼山', '后主衍', '唐代', '乔岩簇冷烟，幽径上寒天。\n下瞰峨眉岭，上窥华岳巅。\n驱驰非取乐，按幸为忧边。\n此去如登陟，歌楼路几千。', NULL, 3),
    ('题剑门', '后主衍', '唐代', '缓辔逾双剑，行行蹑石棱。\n作千寻壁垒，为万祀依凭。\n道德虽无取，江山粗可矜。\n回看城阙路，云叠树层层。', NULL, 3),
    ('过白卫岭和韩昭', '后主衍', '唐代', '先朝神武力开边，画断封疆四五千。\n前望陇山屯剑戟，后凭巫峡锁烽烟。\n轩皇尚自亲平寇，嬴政徒劳爱学仙。\n想到隗宫寻胜处，正应莺语暮春天。', NULL, 3),
    ('宫词', '后主衍', '唐代', '辉辉赫赫浮玉云，宣华池上月华新。\n月华如水浸宫殿，有酒不醉真痴人。', NULL, 3),
    ('醉妆词', '后主衍', '唐代', '者边走，那边走，只是寻花柳。\n那边走，者边走，莫厌金杯酒。', NULL, 3),
    ('句', '后主衍', '唐代', '不缘朝阙去，好此结茅庐。', NULL, 3),
    ('巡衣锦军制还乡歌', '吴越王钱镠', '唐代', '三节还乡兮挂锦衣，碧天朗朗兮爱日晖。\n功成道上兮列旌旗，父老远来兮相追随。\n家山乡眷兮会时稀，今朝设宴兮觥散飞。\n斗牛无孛兮民无欺，吴越一王兮驷马归。', NULL, 3),
    ('没了期歌 一', '吴越王钱镠', '唐代', '没了期，没了期，营基才了又仓基。', NULL, 3),
    ('没了期歌 二', '吴越王钱镠', '唐代', '没了期，没了期，春衣才了又冬衣。', NULL, 3),
    ('句', '吴越王钱镠', '唐代', '须将一片地，付与有心人。\n黄河信有澄清日，后代应难继此才。\n传语龙王幷水府，钱塘借与筑钱城。', NULL, 3),
    ('宫中作', '后王钱俶', '唐代', '廊庑周遭翠幕遮，禁林深处绝喧哗。\n界开日影怜窗纸，穿破苔痕恶笋芽。\n西第晚宜供露茗，小池寒欲结冰花。\n谢公未是深沈量，犹把输赢局上夸。', NULL, 3),
    ('避暑摩诃池上作', '后蜀嗣主孟昶', '唐代', '冰肌玉骨清无汗，水殿风来暗香暖。\n帘开明月独窥人，敧枕钗横云鬓乱。\n起来琼户寂无声，时见疏星渡河汉。\n屈指西风几时来，只恐流年暗中换。', NULL, 3),
    ('批叶翘谏书纸尾', '闽王王继鹏', '唐代', '春色曾看紫陌头，乱红飞尽不禁愁。\n人情自厌芳华歇，一叶随风落御沟。', NULL, 3),
    ('丈人观', '蜀太后徐氏', '唐代', '早与元妃慕至化，同跻灵岳访真仙。\n当时信有壶中景，今日亲来洞里天。\n仪仗影空寥廓外，金丝声揭翠微巅。\n惟惭未致华胥理，徒卜升平万万年。', NULL, 3),
    ('玄都观', '蜀太后徐氏', '唐代', '千寻绿嶂夹流溪，登眺因知海岳低。\n瀑布迸舂青石碎，轮茵横翦翠峰齐。\n步黏苔藓龙桥滑，日闭烟罗鸟径迷。\n莫道穹天无路到，此山便是碧云梯。', NULL, 3),
    ('丈人观谒先帝御容', '蜀太后徐氏', '唐代', '圣帝归梧野，躬来谒圣颜。\n旋登三径路，似陟九嶷山。\n日照堆岚迥，云横积翠间。\n期修封禅礼，方俟再跻攀。', NULL, 3),
    ('题金华宫', '蜀太后徐氏', '唐代', '再到金华顶，玄都访道回。\n云披分景象，黛锁显楼台。\n雨涤前山净，风吹去路开。\n翠屏夹流水，何必羡蓬莱。', NULL, 3),
    ('丹景山至德寺', '蜀太后徐氏', '唐代', '周回云水游丹景，因与真妃眺上方。\n晴日晓升金晃曜，寒泉夜落玉丁当。\n松梢月转琴栖影，柏径风牵麝食香。\n虔煠六铢宜铸祝，惟祈圣祉保遐昌。', NULL, 3),
    ('题彭州阳平化', '蜀太后徐氏', '唐代', '寻真游胜境，巡礼到阳平。\n水远波澜碧，山高气象清。\n殿严孙氏貌，碑暗系师名。\n夜月登坛醮，松风森磬声。', NULL, 3),
    ('三学山夜看圣灯', '蜀太后徐氏', '唐代', '虔祷游灵境，元妃夙志同。\n玉香焚静夜，银烛炫辽空。\n泉漱云根月，钟敲桧杪风。\n印金标圣迹，飞石显神功。\n满望天涯极，平临日脚红。\n猿来斋石上，僧集讲筵中。\n顿作超三界，浑疑证六通。\n愿成修偃化，社稷保延洪。', NULL, 3),
    ('题天回驿', '蜀太后徐氏', '唐代', '周游灵境散幽情，千里江山暂得行。\n所恨风光看未足，却驱金翠入龟城。', NULL, 3),
    ('丈人观', '蜀太妃徐氏', '唐代', '获陪翠辇喜殊常，同涉仙坛岂厌长。\n不羡乘鸾入烟雾，此中便是五云乡。', NULL, 3),
    ('玄都观', '蜀太妃徐氏', '唐代', '登寻丹壑到玄都，接日红霞照座隅。\n即向周回岩下看，似看曾进画图无。', NULL, 3),
    ('游丈人观谒先帝御容', '蜀太妃徐氏', '唐代', '共谒御容仪，还同在禁闱。\n笙歌喧宝殿，彩仗耀金徽。\n清泪霑罗袂，红霞拂绣衣。\n九疑山水远，无路继湘妃。', NULL, 3),
    ('题金华宫', '蜀太妃徐氏', '唐代', '碧烟红雾漾人衣，宿雾苍苔石径危。\n风巧解吹松上曲，蝶娇频采脸边脂。\n同寻僻境思携手，暗指遥山学画眉。\n好把身心清净处，角冠霞帔事希夷。', NULL, 3),
    ('和题丹景山至德寺', '蜀太妃徐氏', '唐代', '丹景山头宿梵宫，玉轮金辂驻虚空。\n军持无水注寒碧，兰若有花开晚红。\n武士尽排青嶂下，内人皆在讲筵中。\n我家帝子传王业，积善终期四海同。', NULL, 3),
    ('题彭州阳平化', '蜀太妃徐氏', '唐代', '云浮翠辇届阳平，真似骖鸾到上清。\n风起半厓闻虎啸，雨来当面见龙行。\n晚寻水涧听松韵，夜上星坛看月明。\n长恐前身居此境，玉皇教向锦城生。', NULL, 3),
    ('三学山夜看圣灯', '蜀太妃徐氏', '唐代', '圣灯千万炬，旋向碧空生。\n细雨湿不暗，好风吹更明。\n磬敲金地响，僧唱梵天声。\n若说无心法，此光如有情。', NULL, 3),
    ('题天回驿', '蜀太妃徐氏', '唐代', '翠驿红亭近玉京，梦魂犹是在青城。\n比来出看江山景，却被江山看出行。', NULL, 3),
    ('郊庙歌辞 祀圜丘乐章 豫和', '褚亮', '唐代', '上灵眷命膺会昌，盛德殷荐叶辰良。\n景福降兮圣德远，玄化穆兮天历长。', NULL, 3),
    ('郊庙歌辞 祀圜丘乐章 太和', '褚亮', '唐代', '穆穆我后，道应千龄。\n登三处大，得一居贞。\n礼惟崇德，乐以和声。\n百神仰止，天下文明。', NULL, 3),
    ('郊庙歌辞 祀圜丘乐章 肃和', '褚亮', '唐代', '闿阳播气，甄曜垂明。\n有赫圜宰，深仁曲成。\n日丽苍璧，烟开紫营。\n聿遵干享，式降鸿祯。', NULL, 3),
    ('郊庙歌辞 祀圜丘乐章 雍和', '褚亮', '唐代', '钦惟大帝，[载]仰皇穹。\n始命田烛，爰启郊宫。\n云门骇听，雷鼓鸣空。\n神其介祀，景祚斯融。', NULL, 3),
    ('郊庙歌辞 祀圜丘乐章 寿和', '褚亮', '唐代', '八音斯奏，三献毕陈。\n宝祚惟永，晖光日新。', NULL, 3),
    ('郊庙歌辞 祀圜丘乐章 舒和', '褚亮', '唐代', '叠璧凝影皇坛路，编珠流彩帝郊前。\n已奏黄锺歌大吕，还符宝历祚昌年。', NULL, 3),
    ('郊庙歌辞 祀圜丘乐章 凯安', '褚亮', '唐代', '昔在炎运终，中华乱无象。\n酆郊赤乌见，邙山黑云上。\n大赉下周车，禁暴开殷网。\n幽明同叶赞，鼎祚齐天壤。', NULL, 3),
    ('郊庙歌辞 郊天旧乐章 豫和', '不详', '唐代', '蘋蘩礼著，黍稷诚微。\n音盈凤管，彩驻龙旗。\n洪歆式就，介福攸归。\n送乐有阕，灵驭遄飞。', NULL, 3),
    ('郊庙歌辞 武后大享昊天乐章 一', '不详', '唐代', '大阴凝至化，真耀蕴轩仪。\n德迈娥台敞，仁高姒幄披。\n扪天遂启极，梦日乃升曦。', NULL, 3),
    ('郊庙歌辞 武后大享昊天乐章 二', '不详', '唐代', '瞻紫极，望玄穹。\n翘至恳，罄深衷。\n听虽远，诚必通。\n垂厚泽，降云宫。', NULL, 3),
    ('郊庙歌辞 武后大享昊天乐章 三', '不详', '唐代', '乾仪混成冲邃，天道下济高明。\n闿阳晨披紫阙，太一晓降黄庭。\n圆坛敢[申]昭报，方璧冀展虔情。\n丹禁式敷衷恳，玄鉴庶察微诚。', NULL, 3),
    ('郊庙歌辞 武后大享昊天乐章 四', '不详', '唐代', '巍巍睿业广，赫赫圣基隆。\n菲德承先顾，祯符萃眇躬。\n铭开武岩侧，图荐洛川中。\n微诚讵幽感，景命忽昭融。\n有怀惭紫极，无以谢玄穹。', NULL, 3),
    ('郊庙歌辞 武后大享昊天乐章 五', '不详', '唐代', '朝坛雾卷，曙岭烟沈。\n爰设筐币，式表诚心。\n筵辉丽璧，乐畅和音。\n仰惟灵鉴，俯察翘襟。', NULL, 3),
    ('郊庙歌辞 武后大享昊天乐章 六', '不详', '唐代', '昭昭上帝，穆穆下临。\n礼崇备物，乐奏锵金。\n兰羞委荐，桂醑盈斟。\n敢希[明]德，聿罄庄心。', NULL, 3),
    ('郊庙歌辞 武后大享昊天乐章 七', '不详', '唐代', '樽浮九酝，礼备三周。\n陈诚菲奠，契福神猷。', NULL, 3),
    ('郊庙歌辞 武后大享昊天乐章 八', '不详', '唐代', '奠璧郊坛昭大礼，锵金拊石表虔诚。\n始奏承云娱帝赏，复歌调露畅韶英。', NULL, 3),
    ('郊庙歌辞 武后大享昊天乐章 九', '不详', '唐代', '荷恩承顾托，执契恭临抚。\n庙略静边荒，天兵耀神武。\n有截资先化，无为遵旧矩。\n祯符降昊穹，大业光寰宇。', NULL, 3),
    ('郊庙歌辞 武后大享昊天乐章 十', '不详', '唐代', '肃肃祀典，邕邕礼秩。\n三献已周，九成斯毕。\n爰撤其俎，载迁其实。\n或升或降，唯诚唯质。', NULL, 3),
    ('郊庙歌辞 武后大享昊天乐章 十一', '不详', '唐代', '礼终肆类，乐阕九成。\n仰惟明德，敢荐非馨。\n顾惭菲奠，久驻云𫐌。\n瞻荷灵泽，悚恋兼盈。', NULL, 3),
    ('郊庙歌辞 武后大享昊天乐章 十二', '不详', '唐代', '式干路，辟天扉。\n回日驭，动云衣。\n登金阙，入紫微。\n望仙驾，仰恩徽。', NULL, 3),
    ('郊庙歌辞 中宗祀昊天乐章 豫和', '不详', '唐代', '天之历数归睿唐，顾惟菲德钦昊苍。\n撰吉日兮表殷荐，冀神鉴兮降闿阳。', NULL, 3),
    ('郊庙歌辞 中宗祀昊天乐章 太和', '不详', '唐代', '恭临宝位，肃奉瑶图。\n恒思解网，每轸泣辜。\n德惭巢燧，化劣唐虞。\n期我良弼，式赞嘉谟。', NULL, 3),
    ('郊庙歌辞 中宗祀昊天乐章 告谢', '不详', '唐代', '得一流玄泽，通三御紫宸。\n远叶千龄运，遐销九域尘。\n绝瑞骈阗集，殊祥络绎臻。\n登年庆栖亩，稔岁贺盈囷。', NULL, 3),
    ('郊庙歌辞 中宗祀昊天乐章 肃和', '不详', '唐代', '悠哉广覆，大矣曲成。\n九玄著象，七曜贞明。\n珪璧是奠，酝酎斯盈。\n作乐崇德，爰畅咸英。', NULL, 3),
    ('郊庙歌辞 中宗祀昊天乐章 雍和', '不详', '唐代', '郊坛展敬，严配因心。\n孤竹箫管，空桑瑟琴。\n肃穆大礼，铿锵八音。\n恭惟上帝，希降灵歆。', NULL, 3),
    ('郊庙歌辞 中宗祀昊天乐章 福和', '不详', '唐代', '九成爰奏，三献式陈。\n钦承景福，恭托明禋。', NULL, 3),
    ('郊庙歌辞 中宗祀昊天乐章 中宫助祭升坛', '不详', '唐代', '坤元光至德，柔训阐皇风。\n芣苡芳声远，螽斯美化隆。\n睿范超千载，嘉猷备六宫。\n肃恭陪盛典，钦若荐禋宗。', NULL, 3),
    ('郊庙歌辞 中宗祀昊天乐章 亚献', '不详', '唐代', '三灵降飨，三后配神。\n虔敷藻奠，敬展郊禋。', NULL, 3),
    ('郊庙歌辞 中宗祀昊天乐章 舒和', '不详', '唐代', '已陈粢盛敷严祀，更奏笙镛协雅声。\n璇图宝历欣宁谧，晏俗淳风乐太平。', NULL, 3),
    ('郊庙歌辞 中宗祀昊天乐章 凯安', '不详', '唐代', '堂堂圣祖兴，赫赫昌基泰。\n戎车盟津偃，玉帛涂山会。\n舜日启祥晖，尧云卷征斾。\n风猷被有截，声教覃无外。', NULL, 3),
    ('郊庙歌辞 明皇祀圜丘乐章 豫和', '不详', '唐代', '至矣丕构，烝哉太平。\n授牺膺箓，复禹继明。\n草木仁化，凫鹥颂声。\n祀宗陈德，无愧斯诚。', NULL, 3),
    ('郊庙歌辞 明皇祀圜丘乐章 太和', '不详', '唐代', '郊坛斋帝，礼乐祀天。\n丹青寰宇，宫征山川。\n神祇毕降，行止重旋。\n融融穆穆，纳祉洪延。', NULL, 3),
    ('郊庙歌辞 明皇祀圜丘乐章 肃和', '不详', '唐代', '止奏潜聆，登仪宿啭。\n太玉躬奉，参锺首奠。\n簠簋聿升，牺牲遰荐。\n昭事颙若，存存以伣。', NULL, 3),
    ('郊庙歌辞 明皇祀圜丘乐章 雍和', '不详', '唐代', '烂云普洽，律风无外。\n千品其凝，九宾斯会。\n禋尊晋烛，纯牺涤汰。\n玄覆攸广，鸿休汪濊。', NULL, 3),
    ('郊庙歌辞 明皇祀圜丘乐章 寿和', '不详', '唐代', '六变爰阕，八阶载虔。\n祐我皇祚，于万斯年。', NULL, 3),
    ('郊庙歌辞 明皇祀圜丘乐章 舒和', '不详', '唐代', '祝史正辞，人神叶庆。\n福以德招，享以诚应。\n六变云备，百礼斯浃。\n祀事孔明，祚流万叶。', NULL, 3),
    ('郊庙歌辞 明皇祀圜丘乐章 凯安', '不详', '唐代', '馨香惟后德，明命光天保。\n肃和崇圣灵，陈信表皇道。\n玉戚初蹈厉，金匏既静好。', NULL, 3),
    ('郊庙歌辞 封泰山乐章 豫和 一', '张说', '唐代', '款泰坛，柴泰清。\n受天命，报天成。\n竦皇心，荐乐声。\n志上达，歌下迎。', NULL, 3),
    ('郊庙歌辞 封泰山乐章 豫和 二', '张说', '唐代', '亿上帝，临下庭。\n骑日月，陪列星。\n嘉祝信，大糦馨。\n澹神心，醉皇灵。', NULL, 3),
    ('郊庙歌辞 封泰山乐章 豫和 三', '张说', '唐代', '相百辟，贡八荒。\n九歌叙，万舞翔。\n肃振振，铿皇皇。\n帝欣欣，福穰穰。', NULL, 3),
    ('郊庙歌辞 封泰山乐章 豫和 四', '张说', '唐代', '高在上，道光明。\n物资始，德难名。\n承眷命，牧苍生。\n寰宇谧，太阶平。', NULL, 3),
    ('郊庙歌辞 封泰山乐章 豫和 五', '张说', '唐代', '天道无亲，至诚与邻。\n山川遍礼，宫征维新。\n玉帛非盛，聪明会真。\n正斯一德，通乎百神。', NULL, 3),
    ('郊庙歌辞 封泰山乐章 豫和 六', '张说', '唐代', '飨帝飨亲，维孝维圣。\n缉熙懿德，敷扬成命。\n华夷志同，笙镛礼盛。\n明灵降止，感此诚敬。', NULL, 3),
    ('郊庙歌辞 封泰山乐章 太和', '张说', '唐代', '孝敬中发，和容外彰。\n腾华照宇，如升太阳。\n贞璧就奠，玄灵垂光。\n礼乐具举，济济洋洋。', NULL, 3),
    ('郊庙歌辞 封泰山乐章 肃和', '张说', '唐代', '奠祖配天，承天享帝。\n百灵咸秩，四海来祭。\n植我苍璧，布我玄制。\n华日裴回，神烟容裔。', NULL, 3),
    ('郊庙歌辞 封泰山乐章 雍和', '张说', '唐代', '俎豆有馥，粢盛洁丰。\n亦有和羹，既戒既平。\n钟鼓管磬，肃唱和鸣。\n皇皇后祖，来我思成。', NULL, 3),
    ('郊庙歌辞 封泰山乐章 寿和', '张说', '唐代', '烝烝我后，享献惟夤。\n躬酌郁鬯，跪奠明神。\n孝莫孝乎，配上帝亲。\n敬莫敬乎，教天下臣。', NULL, 3),
    ('郊庙歌辞 封泰山乐章 舒和', '张说', '唐代', '六钟翕协六变成，八佾倘佯八风生。\n乐九[韶]兮人神感，美七德兮天地清。', NULL, 3),
    ('郊庙歌辞 封泰山乐章 凯安', '张说', '唐代', '烈祖顺三灵，文宗威四海。\n黄钺诛群盗，朱旗扫多罪。\n戢兵天下安，约法人心改。\n大哉干羽意，长见风云在。', NULL, 3),
    ('郊庙歌辞 封泰山乐章 豫和', '张说', '唐代', '乐已终，[烟]燎上。\n怀灵惠，结皇想。\n归风疾，回风爽。\n百[福]来，众神往。', NULL, 3),
    ('郊庙歌辞 祈谷乐章 肃和', '不详', '唐代', '履艮斯绳，居中体正。\n龙运垂祉，昭符启圣。\n式事严禋，聿怀嘉庆。\n惟帝永锡，时皇休命。', NULL, 3),
    ('郊庙歌辞 祈谷乐章 雍和', '不详', '唐代', '殷荐乘春，太坛临曙。\n八簋盈和，六瑚登御。\n嘉稷匪歆，德馨斯饫。\n祝嘏无易，灵心有豫。', NULL, 3),
    ('郊庙歌辞 祈谷乐章 舒和', '不详', '唐代', '玉帛牺牲申敬享，金丝戚羽盛音容。\n庶俾亿龄禔景福，长欣万宇洽时邕。', NULL, 3),
    ('郊庙歌辞 明堂乐章 肃和', '褚亮', '唐代', '象天御宇，乘时布政。\n严配申虔，宗禋展敬。\n樽罍盈列，树羽交映。\n玉币通诚，祚隆皇圣。', NULL, 3),
    ('郊庙歌辞 明堂乐章 雍和', '褚亮', '唐代', '八牖晨披，五精朝奠。\n雾凝璇篚，风清金县。\n神涤备全，明粢丰衍。\n载絜彝俎，陈诚以荐。', NULL, 3),
    ('郊庙歌辞 明堂乐章 舒和', '褚亮', '唐代', '御扆合宫承宝历，席图重馆奉明灵。\n偃武修文九围泰，沈烽静柝八荒宁。', NULL, 3),
    ('郊庙歌辞 武后明堂乐章 外办将出', '武则天', '唐代', '总章陈昔典，衢室礼惟神。\n宏规则天地，神用叶陶钧。\n负扆三春旦，充庭万宇宾。\n顾己诚虚薄，空惭亿兆人。', NULL, 3),
    ('郊庙歌辞 武后明堂乐章 皇帝行', '武则天', '唐代', '仰膺历数，俯顺讴歌。\n远安迩肃，俗阜时和。\n化光玉镜，讼息金科。\n方兴典礼，永戢干戈。', NULL, 3),
    ('郊庙歌辞 武后明堂乐章 皇嗣出入升降', '武则天', '唐代', '至人光俗，大孝通神。\n谦以表性，恭惟立身。\n洪规载启，茂典方陈。\n誉隆三善，祥开万春。', NULL, 3),
    ('郊庙歌辞 武后明堂乐章 迎送王公', '武则天', '唐代', '千官肃事，万国朝宗。\n载延百辟，爰集三宫。\n君臣德合，鱼水斯同。\n睿图方永，周历长隆。', NULL, 3),
    ('郊庙歌辞 武后明堂乐章 登歌', '武则天', '唐代', '礼崇宗祀，志表严禋。\n笙镛合奏，文物维新。\n敬遵茂典，敢择良辰。\n絜诚斯著，奠谒方申。', NULL, 3),
    ('郊庙歌辞 武后明堂乐章 配飨', '武则天', '唐代', '笙镛间玉宇，文物昭清晖。\n睟影临芳奠，休光下太微。\n孝思期有感，明絜庶无违。', NULL, 3),
    ('郊庙歌辞 武后明堂乐章 宫音', '武则天', '唐代', '履艮包群望，居中冠百灵。\n万方资广运，庶品荷财成。\n神功谅匪测，盛德实难名。\n藻奠申诚敬，恭祀表惟馨。', NULL, 3),
    ('郊庙歌辞 武后明堂乐章 角音', '武则天', '唐代', '出震位，开平秩。\n扇条风，乘甲乙。\n龙德盛，鸟星出。\n荐珪篚，陈诚实。', NULL, 3),
    ('郊庙歌辞 武后明堂乐章 征音', '武则天', '唐代', '赫赫离精御炎陆，滔滔炽景开隆暑。\n冀延神鉴俯兰樽，式表虔襟陈桂俎。', NULL, 3),
    ('郊庙歌辞 武后明堂乐章 商音', '武则天', '唐代', '律中夷则，序应收成。\n功宣建武，义表惟明。\n爰申礼奠，庶展翘诚。\n九秋是式，百谷斯盈。', NULL, 3),
    ('郊庙歌辞 武后明堂乐章 羽音', '武则天', '唐代', '葭律肇启隆冬，蘋藻攸陈飨祭。\n黄锺既陈玉烛，红粒方殷稔岁。', NULL, 3),
    ('郊庙歌辞 雩祀乐章 肃和', '褚亮', '唐代', '朱鸟开辰，苍龙启映。\n大帝昭飨，群生展敬。\n礼备怀柔，功宣舞咏。\n旬液应序，年祥协庆。', NULL, 3),
    ('郊庙歌辞 雩祀乐章 雍和', '褚亮', '唐代', '绀筵分彩，珤图吐绚。\n风管晨凝，云歌晓啭。\n肃事兰羞，虔申桂奠。\n百谷斯登，万箱攸荐。', NULL, 3),
    ('郊庙歌辞 雩祀乐章 舒和', '褚亮', '唐代', '凤曲登歌调令序，龙雩集舞泛祥风。\n彩旞云回昭睿德，朱干电发表神功。', NULL, 3),
    ('郊庙歌辞 雩祀乐章 豫和', '不详', '唐代', '鸟纬迁序，龙星见辰。\n纯阳在律，明德崇禋。\n五方降帝，万宇安人。\n恭以致享，肃以迎神。', NULL, 3),
    ('郊庙歌辞 五郊乐章 黄帝宫音', '魏徵', '唐代', '黄中正位，含章居贞。\n既长六律，兼和五声。\n毕陈万舞，乃荐斯牲。\n神其下降，永祚休平。', NULL, 3),
    ('郊庙歌辞 五郊乐章 黄帝宫音 肃和', '魏徵', '唐代', '眇眇方舆，苍苍圜盖。\n至哉枢纽，宅中图大。\n气调四序，风和万籁。\n祚我明德，时雍道泰。', NULL, 3),
    ('郊庙歌辞 五郊乐章 黄帝宫音 雍和', '魏徵', '唐代', '金县夕肆，玉俎朝陈。\n飨荐黄道，芬流紫宸。\n迺诚迺敬，载享载禋。\n崇荐斯在，惟皇是宾。', NULL, 3),
    ('郊庙歌辞 五郊乐章 黄帝宫音 舒和', '魏徵', '唐代', '御征乘宫出郊甸，安歌率舞递将迎。\n自有云门符帝赏，犹持雷鼓答天成。', NULL, 3),
    ('郊庙歌辞 五郊乐章 青帝角音', '魏徵', '唐代', '鹤云旦起，鸟星昏集。\n律候新风，阳开初蛰。\n至德可飨，行潦斯挹。\n锡以无疆，蒸人乃粒。', NULL, 3),
    ('郊庙歌辞 五郊乐章 青帝角音 肃和', '魏徵', '唐代', '玄鸟司春，苍龙登岁。\n节物变柳，光风转蕙。\n瑶席降神，朱弦飨帝。\n诚备祝嘏，礼殚珪币。', NULL, 3),
    ('郊庙歌辞 五郊乐章 青帝角音 雍和', '魏徵', '唐代', '大乐稀音，至诚简礼。\n文物棣棣，声明济济。\n六变有成，三登无体。\n乃眷丰洁，恩覃恺悌。', NULL, 3),
    ('郊庙歌辞 五郊乐章 青帝角音 舒和', '魏徵', '唐代', '笙歌籥舞属年韶，鹭鼓凫钟展时豫。\n调露初迎绮春节，承云遽践苍霄驭。', NULL, 3),
    ('郊庙歌辞 五郊乐章 赤帝征音', '魏徵', '唐代', '青阳告谢，朱明戒序。\n延长是祈，敬陈椒醑。\n博硕斯荐，笙镛备举。\n庶尽肃恭，非馨稷黍。', NULL, 3),
    ('郊庙歌辞 五郊乐章 赤帝征音 肃和', '魏徵', '唐代', '离位克明，火中宵见。\n峰云暮起，景风晨扇。\n木槿初荣，含桃可荐。\n芬馥百品，铿锵三变。', NULL, 3),
    ('郊庙歌辞 五郊乐章 赤帝征音 雍和', '魏徵', '唐代', '昭昭丹陆，奕奕炎方。\n礼陈牲币，乐备篪簧。\n琼羞溢俎，玉醑浮觞。\n恭惟正直，歆此馨香。', NULL, 3),
    ('郊庙歌辞 五郊乐章 赤帝征音 舒和', '魏徵', '唐代', '千里温风飘绛羽，十枝炎景賸朱干。\n陈觞荐俎歌二献，拊石𪭢金会七盘。', NULL, 3),
    ('郊庙歌辞 五郊乐章 白帝商音', '魏徵', '唐代', '白藏应节，天高气清。\n岁功既阜，庶类收成。\n万方静谧，九土和平。\n馨香是荐，受祚聪明。', NULL, 3),
    ('郊庙歌辞 五郊乐章 白帝商音 肃和', '魏徵', '唐代', '金行在节，素灵居正。\n气肃霜严，林雕草劲。\n豺祭隼击，潦收川镜。\n九谷已登，万箱流咏。', NULL, 3),
    ('郊庙歌辞 五郊乐章 白帝商音 雍和', '魏徵', '唐代', '律应西成，气躔南吕。\n珪币咸列，笙竽备举。\n苾苾兰羞，芬芬桂醑。\n式资宴贶，用调霜序。', NULL, 3),
    ('郊庙歌辞 五郊乐章 白帝商音 舒和', '魏徵', '唐代', '璿仪气爽惊缇籥，玉吕灰飞含素商。\n鸣鞞奏管芳羞荐，会舞安歌葆眊扬。', NULL, 3),
    ('郊庙歌辞 五郊乐章 黑帝羽音', '魏徵', '唐代', '严冬季月，星回风厉。\n享祀报功，方祚来岁。', NULL, 3),
    ('郊庙歌辞 五郊乐章 黑帝羽音 肃和', '魏徵', '唐代', '律回玉琯，星周金度。\n次极阳乌，纪穷阴兔。\n火林霰雪，阳泉凝冱。\n八蜡已登，三农息务。', NULL, 3),
    ('郊庙歌辞 五郊乐章 黑帝羽音 雍和', '魏徵', '唐代', '阳月斯纪，应锺在候。\n载洁牲牷，爰登俎豆。\n既高既远，无声无臭。\n静言格思，惟神保祐。', NULL, 3),
    ('郊庙歌辞 五郊乐章 黑帝羽音 舒和', '魏徵', '唐代', '执籥持羽初终曲，朱干玉戚始分行。\n七德九功咸已畅，明灵降福具穰穰。', NULL, 3),
    ('郊庙歌辞 五郊乐章 黄郊迎神', '不详', '唐代', '朱明季序，黄郊王辰。\n厚以载物，甘以养人。\n毓金为体，禀火成身。\n宫音式奏，奏以迎神。', NULL, 3),
    ('郊庙歌辞 五郊乐章 送神', '不详', '唐代', '春末冬暮，徂夏杪秋。\n土王四月，时季一周。\n黍稷已享，笾豆宜收。\n送神有乐，神其赐休。', NULL, 3),
    ('郊庙歌辞 五郊乐章 青郊迎神', '不详', '唐代', '缇幕移候，青郊启蛰。\n淑景迟迟，和风习习。\n璧玉宵备，旌旄曙立。\n张乐以迎，帝神其入。', NULL, 3),
    ('郊庙歌辞 五郊乐章 赤郊迎神', '不详', '唐代', '青阳节谢，朱明候改。\n靡草雕华，含桃流彩。\n虡列钟磬，筵陈脯醢。\n乐以迎神，神其如在。', NULL, 3),
    ('郊庙歌辞 五郊乐章 白郊迎神', '不详', '唐代', '序移玉律，节应金商。\n天严杀气，吹警秋方。\n槱燎既积，稷奠并芳。\n乐以迎奏，庶降神光。', NULL, 3),
    ('郊庙歌辞 五郊乐章 黑郊迎神', '不详', '唐代', '玄英戒序，黑郊临候。\n掌礼陈彝，司筵执豆。\n寒氛敛色，沍泉凝漏。\n乐以迎神，八音斯奏。', NULL, 3),
    ('郊庙歌辞 朝日乐章 肃和', '不详', '唐代', '惟圣格天，惟明飨日。\n帝郊肆类，王宫戒吉。\n珪奠春舒，钟歌晓溢。\n礼云克备，斯文有秩。', NULL, 3),
    ('郊庙歌辞 朝日乐章 雍和', '不详', '唐代', '晨仪式荐，明祀惟光。\n神物爰止，灵晖载扬。\n玄端肃事，紫幄兴祥。\n福履攸假，于昭允王。', NULL, 3),
    ('郊庙歌辞 朝日乐章 舒和', '不详', '唐代', '崇牙树羽延调露，旋宫扣律掩承云。\n诞敷懿德昭神武，载集丰功表睿文。', NULL, 3),
    ('郊庙歌辞 朝日乐章 迎神', '不详', '唐代', '太阳朝序，王宫有仪。\n蟠桃彩驾，细柳光驰。\n轩祥表合，汉历彰奇。\n礼和乐备，神其降斯。', NULL, 3),
    ('郊庙歌辞 朝日乐章 送神', '不详', '唐代', '五齐兼酌，百羞具陈。\n乐终广奏，礼毕崇禋。\n明鉴万宇，照临兆人。\n永流洪庆，式动曦轮。', NULL, 3),
    ('郊庙歌辞 夕月乐章 肃和', '不详', '唐代', '测妙为神，通微曰圣。\n坎祀贻则，郊禋展敬。\n璧荐登光，金歌动映。\n以载嘉德，以流曾庆。', NULL, 3),
    ('郊庙歌辞 夕月乐章 雍和', '不详', '唐代', '朏晨争举，天宗礼辟。\n夜典凉秋，阴明湛夕。\n有{酉齐}斯旨，有牲斯硕。\n穆穆其晖，穰穰是积。', NULL, 3),
    ('郊庙歌辞 夕月乐章 舒和', '不详', '唐代', '合吹八风金奏动，分容万舞玉鞘惊。\n词昭茂典光前列，夕曜乘功表盛明。', NULL, 3),
    ('郊庙歌辞 祀九宫贵神乐章 豫和 一', '不详', '唐代', '于昭上穹，临下有光。\n羽翼五佐，周流八荒。\n谁其飨之，时文对敭。\n虞经夏典，兹礼未遑。', NULL, 3),
    ('郊庙歌辞 祀九宫贵神乐章 豫和 二', '不详', '唐代', '黑帝旋驭，青躔导日。\n金箓上玄，玉堂初吉。\n钩陈夕次，銮和先跸。\n蔼蔼群灵，昭昭咸秩。', NULL, 3),
    ('郊庙歌辞 祀九宫贵神乐章 豫和 三', '不详', '唐代', '帝临中坛，受厘元神。\n皇灵萃止，羽旄肃陈。\n摄提运衡，招摇移轮。\n光光宇宙，电耀雷震。', NULL, 3),
    ('郊庙歌辞 祀九宫贵神乐章 豫和 四', '不详', '唐代', '夜如何其，明星煌煌。\n天清容卫，露结坛场。\n树羽幢幢，佩玉锵锵。\n凝精驻目，瞻望神光。', NULL, 3),
    ('郊庙歌辞 祀九宫贵神乐章 豫和 五', '不详', '唐代', '九位既肃，万灵毕会。\n天门启扄，日驭飞盖。\n焕兮棽离，傧兮暗霭。\n如山之福，惟圣时对。', NULL, 3),
    ('郊庙歌辞 祀九宫贵神乐章 豫和 六', '不详', '唐代', '崇崇泰坛，灵具临兮。\n铿锽大乐，振动心兮。\n神之降矣，卿云郁兮。\n神之至止，清风肃兮。', NULL, 3),
    ('郊庙歌辞 祀九宫贵神乐章 太和', '不详', '唐代', '帝在灵坛，大明登光。\n天回云粹，穆穆皇皇。\n金奏九夏，圭陈八芗。\n旷哉动植，如熙春阳。', NULL, 3),
    ('郊庙歌辞 祀九宫贵神乐章 肃和', '不详', '唐代', '歌工既奏，神位既秩。\n天符众星，运行太一。\n声和十管，气应中律。\n肃肃明廷，介兹元吉。', NULL, 3),
    ('郊庙歌辞 祀九宫贵神乐章 雍和', '不详', '唐代', '俎豆有践，黄流在尊。\n九宫之祀，三代莫存。\n乐变六宫，坛开八门。\n圣皇昭对，祐我黎元。', NULL, 3),
    ('郊庙歌辞 祀九宫贵神乐章 寿和', '不详', '唐代', '时文哲后，肃事严禋。\n馨我明德，享于贵神。\n大庖载盈，旨酒斯醇。\n精意所属，期于利人。', NULL, 3),
    ('郊庙歌辞 祀九宫贵神乐章 福和', '不详', '唐代', '祀既云毕，明灵告旋。\n礼洽和应，神歆福延。\n动植咸若，阴阳不愆。\n锡兹纯嘏，天子万年。', NULL, 3),
    ('郊庙歌辞 祀九宫贵神乐章 舒和', '不详', '唐代', '羽籥既阕干戚陈，八音克谐六变新。\n愉贵神兮般以乐，保皇祚兮万斯春。', NULL, 3),
    ('郊庙歌辞 祀九宫贵神乐章 凯安', '不详', '唐代', '盛德陈万舞，威棱畅九垓。\n风云交律候，日月丽昭回。\n行庆休祥发，乘和春气来。\n百神肃临享，荡荡天门开。', NULL, 3),
    ('郊庙歌辞 祀九宫贵神乐章 豫和', '不详', '唐代', '享申百礼，庆洽百灵。\n上排阊阖，洞入杳冥。\n奠玉高坛，燔柴广庭。\n神之降福，万国咸宁。', NULL, 3),
    ('郊庙歌辞 祀风师乐章 迎神', '包佶', '唐代', '太皞御气，句芒肇功。\n苍龙青旗，爰候祥风。\n律以和应，神以感通。\n鼎俎修蚃，时惟礼崇。', NULL, 3),
    ('郊庙歌辞 祀风师乐章 奠币登歌', '包佶', '唐代', '旨酒告絜，青蘋应候。\n礼陈瑶币，乐献金奏。\n弹弦自昔，解冻惟旧。\n仰瞻肸蚃，群祥来凑。', NULL, 3),
    ('郊庙歌辞 祀风师乐章 迎俎酌献', '包佶', '唐代', '德盛昭临，迎拜巽方。\n爰候发生，式荐馨香。\n酌醴具举，工歌载扬。\n神歆六律，恩降百祥。', NULL, 3),
    ('郊庙歌辞 祀风师乐章 亚献终献', '包佶', '唐代', '膋芗备，玉帛陈。\n风动物，乐感神。\n三献终，百神臻。\n草木荣，天下春。', NULL, 3),
    ('郊庙歌辞 祀风师乐章 送神', '包佶', '唐代', '微穆敷华能应节，飘扬发彩宜行庆。\n送迎灵驾神心享，跪拜灵坛礼容盛。\n气和草木发萌芽，德畅禽鱼遂翔泳。\n永望翠盖逐流云，自兹率土调春令。', NULL, 3),
    ('郊庙歌辞 祀雨师乐章 迎神', '包佶', '唐代', '陟降左右，诚达幽圜。\n作解之功，乐惟有年。\n云𫐌戾止，洒雾飘烟。\n惟馨展礼，爰列豆笾。', NULL, 3),
    ('郊庙歌辞 祀雨师乐章 奠币登歌', '包佶', '唐代', '岁正朱明，礼布元制。\n惟乐能感，与神合契。\n阴雾离翍，灵驭摇裔。\n膏泽之庆，期于稔岁。', NULL, 3),
    ('郊庙歌辞 祀雨师乐章 迎俎酌献', '包佶', '唐代', '阳开幽蛰，躬奉郁鬯。\n礼备节应，震来灵降。\n动植求声，飞沈允望。\n时康气茂，惟神之贶。', NULL, 3),
    ('郊庙歌辞 祀雨师乐章 亚献终献', '包佶', '唐代', '奠既备，献将终。\n神行令，瑞飞空。\n迎乾德，祈岁功。\n乘烟燎，俨从风。', NULL, 3),
    ('郊庙歌辞 祀雨师乐章 送神', '包佶', '唐代', '整驾升车望寥廓，垂阴荐祉荡昏氛。\n飨时灵贶僾如在，乐罢余声遥可闻。\n饮福陈诚礼容备，撤俎终献曙光分。\n跪拜临坛结空想，年年应节候油云。', NULL, 3),
    ('郊庙歌辞 祭方丘乐章 顺和', '褚亮', '唐代', '万方资以化，交泰属升平。\n易从业惟简，得一道斯宁。\n具仪光玉帛，送舞变咸英。\n黍稷良非贵，明德信惟馨。', NULL, 3),
    ('郊庙歌辞 祭方丘乐章 肃和', '褚亮', '唐代', '至矣坤德，皇哉地祇。\n开元统纽，合大承规。\n九宫肃列，六典相仪。\n永言配命，长保无亏。', NULL, 3),
    ('郊庙歌辞 祭方丘乐章 雍和', '褚亮', '唐代', '柔而能方，直而能敬。\n载厚以德，大亨以正。\n有涤斯牷，有馨斯盛。\n介兹景福，祚我休庆。', NULL, 3),
    ('郊庙歌辞 祭方丘乐章 舒和', '褚亮', '唐代', '玉币牲牷分荐享，羽旄干戚遰成容。\n一德惟宁两仪泰，三才保合四时邕。', NULL, 3),
    ('郊庙歌辞 武后大享拜洛乐章 昭和', '不详', '唐代', '九玄眷命，三圣基隆。\n奉承先旨，明台毕功。\n宗祀展敬，冀表深衷。\n永昌帝业，式播淳风。', NULL, 3),
    ('郊庙歌辞 武后大享拜洛乐章 致和', '不详', '唐代', '神功不测兮运阴阳，包藏万宇兮孕八荒。\n天符既出兮帝业昌，愿临明祀兮降祯祥。', NULL, 3),
    ('郊庙歌辞 武后大享拜洛乐章 咸和', '不详', '唐代', '坎泽祠容备举，坤坛祭典爰申。\n灵眷遥行秘躅，嘉贶荐委殊珍。\n肃礼恭禋载展，翘襟邈志逾殷。\n方期交际悬应。', NULL, 3),
    ('郊庙歌辞 武后大享拜洛乐章 九和', '不详', '唐代', '祗荷坤德，钦若乾灵。\n惭惕罔寘，兴居匪宁。\n恭崇礼则，肃奉仪形。\n惟凭展敬，敢荐非馨。', NULL, 3),
    ('郊庙歌辞 武后大享拜洛乐章 显和', '不详', '唐代', '菲躬承睿顾，薄德忝坤仪。\n乾乾遵后命，翼翼奉先规。\n抚俗勤虽切，还淳化尚亏。\n未能弘至道，何以契明祇。', NULL, 3),
    ('郊庙歌辞 武后大享拜洛乐章 敬和', '不详', '唐代', '兰俎既升，蘋羞可荐。\n金石载设，咸英已变。\n林泽斯总，山川是遍。\n敢用敷诚，实惟忘倦。', NULL, 3),
    ('郊庙歌辞 武后大享拜洛乐章 齐和', '不详', '唐代', '沈潜演贶分三极，广大凝祯总万方。\n既荐羽旌文化启，还呈干戚武威扬。', NULL, 3),
    ('郊庙歌辞 武后大享拜洛乐章 德和', '不详', '唐代', '夕惕司龙契，晨兢当凤扆。\n崇儒习旧规，偃伯循先旨。\n绝壤飞冠盖，遐区丽山水。\n幸承三圣余，忻属千年始。', NULL, 3),
    ('郊庙歌辞 武后大享拜洛乐章 禋和', '不详', '唐代', '百礼崇容，千官肃事。\n灵降无兆，神凝有粹。\n奠享咸周，威仪毕备。\n奏夏登列，歌雍彻肆。', NULL, 3),
    ('郊庙歌辞 武后大享拜洛乐章 通和', '不详', '唐代', '皇皇灵眷，穆穆神心。\n暂动凝质，还归积阴。\n功玄枢纽，理寂高深。\n衔恩佩德，耸志翘襟。', NULL, 3),
    ('郊庙歌辞 武后大享拜洛乐章 归和', '不详', '唐代', '言旋云洞兮蹑烟途，永宁中宇兮安下都。\n包涵动植兮顺荣枯，长贻宝贶兮赞璇图。', NULL, 3),
    ('郊庙歌辞 武后大享拜洛乐章 又归和', '不详', '唐代', '调云阕兮神座兴，骖云驾兮俨将升。\n腾绛霄兮垂景祐，翘丹恳兮荷休征。', NULL, 3),
    ('郊庙歌辞 祭方丘乐章 顺和', '不详', '唐代', '坤厚载物，德柔垂祉。\n九域咸雍，四溟为纪。\n敬因良节，虔修阴祀。\n广乐式张，灵其降止。', NULL, 3),
    ('郊庙歌辞 祭方丘乐章 金奏', '不详', '唐代', '坤元至德，品物资生。\n神凝博厚，道协高明。\n列镇五岳，环流四瀛。\n于何不载，万宝斯成。', NULL, 3),
    ('郊庙歌辞 祭汾阴乐章 顺和', '林锺宫', '唐代', '大乐和畅，殷荐明神。\n一降通感，八变必臻。\n有求斯应，无德不亲。\n降灵醉止，休征万人。', NULL, 3),
    ('郊庙歌辞 祭汾阴乐章 太簇角', '卢从愿', '唐代', '坤元载物，阳乐发生。\n播殖资始，品汇咸亨。\n列俎棋布，方坛砥平。\n神歆禋祀，后德惟明。', NULL, 3),
    ('郊庙歌辞 祭汾阴乐章 姑洗征', '刘晃', '唐代', '大君出震，有事郊禋。\n斋戒既肃，馨香毕陈。\n乐和礼备，候暖风春。\n恭惟降福，实赖明神。', NULL, 3),
    ('郊庙歌辞 祭汾阴乐章 南吕羽', '韩休', '唐代', '于穆濬哲，维清缉熙。\n肃事昭配，永言孝思。\n涤濯静嘉，馨香在兹。\n神之听之，用受福厘。', NULL, 3),
    ('郊庙歌辞 祭汾阴乐章 太和', '王晙', '唐代', '于穆圣皇，六叶重光。\n太原刻颂，后土疏场。\n宝鼎呈符，歊云孕祥。\n礼乐备矣，降福穰穰。', NULL, 3),
    ('郊庙歌辞 祭汾阴乐章 肃和', '崔玄童', '唐代', '聿修严配，展事禋宗。\n祥符宝鼎，礼备黄琮。\n[祝]词以信，明德惟聪。\n介兹景福，永永无穷。', NULL, 3),
    ('郊庙歌辞 祭汾阴乐章 雍和', '贾曾', '唐代', '蠲我饎餴，絜我膋芗。\n有豆孔硕，为羞既臧。\n至诚无昧，精意惟芳。\n神其醉止，欣欣乐康。', NULL, 3),
    ('郊庙歌辞 祭汾阴乐章 寿和', '苏颋', '唐代', '礼物斯具，乐章乃陈。\n谁其作主，皇考圣真。\n对越在天，圣明佐神。\n窅然汾上，厚泽如春。', NULL, 3),
    ('郊庙歌辞 祭汾阴乐章 舒和', '何鸾', '唐代', '乐奏云阕，礼章载虔。\n禋宗于地，昭假于天。\n惟馨荐矣，既醉歆焉。\n神之降福，永永万年。', NULL, 3),
    ('郊庙歌辞 祭汾阴乐章 凯安', '蒋挺', '唐代', '维岁之吉，维辰之良。\n圣君绂冕，肃事坛场。\n大礼已备，大乐斯[张]。\n神其醉止，降福无疆。', NULL, 3),
    ('郊庙歌辞 祭汾阴乐章 顺和', '源光裕', '唐代', '方丘既膳，嘉飨载谧。\n齐敬毕诚，陶匏贵质。\n秀毕丰荐，芳俎盈实。\n永永福流，其升如日。', NULL, 3),
    ('郊庙歌辞 禅社首乐章 顺和', '贺知章、源乾曜', '唐代', '至哉含柔德，万物资以生。\n常顺称厚载，流谦通变盈。\n圣心事能察，增广陈厥诚。\n黄祇僾如在，泰折俟咸亨。', NULL, 3),
    ('郊庙歌辞 禅社首乐章 太和', '贺知章、源乾曜', '唐代', '肃我成命，于昭黄祇。\n裘冕而祀，陟降在斯。\n五音克备，八音聿施。\n缉熙肆靖，厥心匪离。', NULL, 3),
    ('郊庙歌辞 禅社首乐章 肃和', '贺知章、源乾曜', '唐代', '黄祇是[祗]，我其夙夜。\n寅畏诚絜，匪遑宁舍。\n礼以琮玉，荐厥茅藉。\n念兹降康，胡宁克暇。', NULL, 3),
    ('郊庙歌辞 禅社首乐章 雍和', '贺知章、源乾曜', '唐代', '夙夜宥密，不敢宁宴。\n五齐既陈，八音在县。\n粢盛以洁，房俎斯荐。\n惟德惟馨，尚兹克遍。', NULL, 3),
    ('郊庙歌辞 禅社首乐章 寿和', '贺知章、源乾曜', '唐代', '惟以明发，有怀载殷。\n乐盈而反，礼顺其禋。\n立清以献，荐欲是亲。\n于穆不已，裒对斯臻。', NULL, 3),
    ('郊庙歌辞 禅社首乐章 福和', '贺知章、源乾曜', '唐代', '穆穆天子，告成岱宗。\n大裘如濡，执珽有颙。\n乐以平志，礼以和容。\n上帝临我，云胡肃邕。', NULL, 3),
    ('郊庙歌辞 禅社首乐章 灵具醉', '贺知章、源乾曜', '唐代', '灵具醉，杳熙熙。\n灵将往，眇禗禗。\n顾明德，吐正辞。\n烂遗光，流祯祺。', NULL, 3),
    ('郊庙歌辞 祭神州乐章 肃和', '褚亮', '唐代', '大矣坤仪，至哉神县。\n包含日域，牢笼月竁。\n露絜三清，风调六变。\n皇祇介祉，式歆恭荐。', NULL, 3),
    ('郊庙歌辞 祭神州乐章 雍和', '褚亮', '唐代', '泰折严享，阴郊展敬。\n礼以导神，乐以和性。\n黝牲在列，黄琮俯暎。\n九土既平，万邦贻庆。', NULL, 3),
    ('郊庙歌辞 祭神州乐章 舒和', '褚亮', '唐代', '坤道降祥和庶品，灵心载德厚群生。\n水土既调三极泰，文武毕备九区平。', NULL, 3),
    ('郊庙歌辞 祭神州乐章 迎神', '不详', '唐代', '黄舆厚载，赤寰归德。\n含育九区，保安万国。\n诚敬无怠，禋祀有则。\n乐以迎神，其仪不忒。', NULL, 3),
    ('郊庙歌辞 祭神州乐章 送神', '不详', '唐代', '神州阴祀，洪恩广济。\n草树霑和，飞沈沐惠。\n礼修鼎俎，奠歆瑶币。\n送乐有章，灵轩其逝。', NULL, 3),
    ('郊庙歌辞 祭[太]社乐章 肃和', '褚亮', '唐代', '后土凝德，神功协契。\n九域底平，两仪交际。\n戊期应序，阴墉展币。\n灵车少留，俯歆樽桂。', NULL, 3),
    ('郊庙歌辞 祭[太]社乐章 雍和', '褚亮', '唐代', '美报崇本，严恭展事。\n受露疏坛，承风启地。\n絜粢登俎，醇牺入馈。\n介福远流，群生毕遂。', NULL, 3),
    ('郊庙歌辞 祭[太]社乐章 舒和', '褚亮', '唐代', '神道发生敷九稼，阴极乘仁畅八埏。\n纬武经文隆景化，登祥荐祉启丰年。', NULL, 3),
    ('郊庙歌辞 祭大社乐章 迎神', '不详', '唐代', '烈山有子，后土有臣。\n播种百谷，济育兆人。\n春官缉礼，宗伯司禋。\n戊为吉日，迎享兹辰。', NULL, 3),
    ('郊庙歌辞 祭大社乐章 送神', '不详', '唐代', '吉祥式就，酬功载毕。\n亲地尊天，礼文经术。\n贶征令序，福流初日。\n神驭爰归，祠官其出。', NULL, 3),
    ('郊庙歌辞 蜡百神乐章 肃和', '不详', '唐代', '序迫岁阴，日躔星纪。\n爰稽茂典，聿崇清祀。\n绮币霞舒，瑞珪虹起。\n百禩垂裕，万灵荐祉。', NULL, 3),
    ('郊庙歌辞 蜡百神乐章 雍和', '不详', '唐代', '缇籥劲序，玄英晚候。\n姬蜡开仪，豳歌入奏。\n蕙馥雕俎，兰芬玉酎。\n大享明祇，永绥多祐。', NULL, 3),
    ('郊庙歌辞 蜡百神乐章 舒和', '不详', '唐代', '经纬两仪文化洽，削平方域武功成。\n瑶弦自乐乾坤泰，玉戚长欢区宇宁。', NULL, 3),
    ('郊庙歌辞 蜡百神乐章 迎神', '不详', '唐代', '八蜡开祭，万物合祀。\n上极天维，下穷坤纪。\n鼎俎流芬，樽彝荐美。\n有灵有祇，咸希来止。', NULL, 3),
    ('郊庙歌辞 蜡百神乐章 送神', '不详', '唐代', '十旬欢洽，一日祠终。\n澄彝拂俎，报德酬功。\n虑虔容肃，礼缛仪丰。\n神其降祉，整驭随风。', NULL, 3),
    ('郊庙歌辞 享先农乐章 咸和', '褚亮', '唐代', '粒食伊始，农之所先。\n古今攸赖，是曰人天。\n耕斯帝籍，播厥公田。\n式崇明祀，神其福焉。', NULL, 3),
    ('郊庙歌辞 享先农乐章 肃和', '褚亮', '唐代', '樽彝既列，瑚簋方荐。\n歌工载登，币礼斯奠。\n肃肃享祀，颙颙缨弁。\n神之听之，福流寰县。', NULL, 3),
    ('郊庙歌辞 享先农乐章 雍和', '褚亮', '唐代', '前夕视牲，质明奉俎。\n沐芳整弁，其仪式序。\n盛礼毕陈，嘉乐备举。\n歆我懿德，非馨稷黍。', NULL, 3),
    ('郊庙歌辞 享先农乐章 舒和', '褚亮', '唐代', '羽籥低昂文缀已，干戚蹈厉武行初。\n望岁祈农神所听，延祥介福岂云虚。', NULL, 3),
    ('郊庙歌辞 享先农乐章 咸和', '不详', '唐代', '三推礼就，万庾祈凝。\n夤宾志远，藨衮惟兴。\n降歆肃荐，垂祐祗膺。\n送神有乐，神其上升。', NULL, 3),
    ('郊庙歌辞 享先蚕乐章 永和', '不详', '唐代', '芳春开令序，韶苑畅和风。\n惟灵申广祐，利物表神功。\n绮会周天宇，黼黻藻寰中。\n庶几承庆节，歆奠下帷宫。', NULL, 3),
    ('郊庙歌辞 享先蚕乐章 肃和', '不详', '唐代', '明灵光至德，深功掩百神。\n祥源应节启，福绪逐年新。\n万宇承恩覆，七庙伫恭禋。\n于兹申至恳，方期远庆臻。', NULL, 3),
    ('郊庙歌辞 享先蚕乐章 展敬', '不详', '唐代', '霞庄列宝卫，云集动和声。\n金卮荐绮席，玉币委芳庭。\n因心罄丹款，先己励苍生。\n所冀延明福，于兹享至诚。', NULL, 3),
    ('郊庙歌辞 享先蚕乐章 絜诚', '不详', '唐代', '桂筵开玉俎，兰圃荐琼芳。\n八音调凤历，三献奉鸾觞。\n絜粢申大享，庭宇冀降祥。\n神其覃有庆，契福永无疆。', NULL, 3),
    ('郊庙歌辞 享先蚕乐章 昭庆', '不详', '唐代', '仙坛礼既毕，神驾俨将升。\n伫属深祥起，方期庶绩凝。\n虔诚资宇内，务本勖黎蒸。\n灵心昭备享，率土洽休征。', NULL, 3),
    ('郊庙歌辞 释奠文宣王乐章 诚和', '不详', '唐代', '圣道日用，神几不测。\n金石以陈，弦歌载陟。\n爰释其菜，匪馨于稷。\n来顾来享，是宗是极。', NULL, 3),
    ('郊庙歌辞 释奠文宣王乐章 承和', '不详', '唐代', '万国以贞光上嗣，三善茂德表重轮。\n视膳寝门尊要道，高辟崇贤引正人。', NULL, 3),
    ('郊庙歌辞 释奠文宣王乐章 肃和', '不详', '唐代', '粤惟上圣，有纵自天。\n傍周万物，俯应千年。\n旧章允著，嘉贽孔虔。\n王化兹首，儒风是宣。', NULL, 3),
    ('郊庙歌辞 释奠文宣王乐章 雍和', '不详', '唐代', '堂献瑶篚，庭敷璆县。\n礼备其容，乐和其变。\n肃肃亲享，雍雍执奠。\n明德惟馨，蘋蘩可荐。', NULL, 3),
    ('郊庙歌辞 释奠文宣王乐章 舒和', '不详', '唐代', '隼集龟开昭圣烈，龙蹲凤跱肃神仪。\n尊儒敬业宏图阐，纬武经文盛德施。', NULL, 3),
    ('郊庙歌辞 享孔子庙乐章 迎神', '不详', '唐代', '通吴表圣，问老探真。\n三千弟子，五百贤人。\n亿龄规法，万载嗣禋。\n洁诚以祭，奏乐迎神。', NULL, 3),
    ('郊庙歌辞 享孔子庙乐章 送神', '不详', '唐代', '醴溢牺象，羞陈俎豆。\n鲁壁类闻，泗川入觏。\n里校覃福，胄筵承祐。\n雅乐清音，送神具奏。', NULL, 3),
    ('郊庙歌辞 释奠武成王乐章 迎神', '于邵', '唐代', '卜畋不从，兆发非熊。\n乃倾荒政，爰佐一戎。\n盛烈载垂，命祀惟崇。\n日练上戊，宿严閟宫。\n迎奏嘉至，感而遂通。', NULL, 3),
    ('郊庙歌辞 释奠武成王乐章 奠币登歌', '于邵', '唐代', '管磬升，膻芗集。\n上公进，嘉币执。\n信以通，僾如及。\n恢帝功，锡后邑。\n四维张，百度立。\n绵亿载，邈难挹。', NULL, 3),
    ('郊庙歌辞 释奠武成王乐章 迎俎酌献', '于邵', '唐代', '五齐洁，九牢硕。\n梡橛循，罍斝涤。\n进具物，扬鸿绩。\n和奏发，高灵寂。\n虔告终，繁祉锡。\n昭秩祀，永无易。', NULL, 3),
    ('郊庙歌辞 释奠武成王乐章 亚献终献', '于邵', '唐代', '贰觞以献，三变其终。\n顾此非馨，尚达斯衷。\n茅缩可致，神歆载融。\n始神翊周，拯溺除凶。\n时维降祐，永绝兴戎。', NULL, 3),
    ('郊庙歌辞 释奠武成王乐章 送神', '于邵', '唐代', '明祀方终，备乐斯阕。\n黝𫄸就瘗，豆笾告撤。\n肸蚃尚余，光景云灭。\n返归虚极，神心则悦。', NULL, 3),
    ('郊庙歌辞 享龙池乐章 第一章', '姚崇', '唐代', '恭闻帝里生灵沼，应报明君鼎业新。\n既协翠泉光宝命，还符白水出真人。\n此时舜海潜龙跃，此地尧河带马巡。\n独有前池一小雁，叨承旧惠入天津。', NULL, 3),
    ('郊庙歌辞 享龙池乐章 第二章', '蔡孚', '唐代', '帝宅王家大道边，神马龙龟涌圣泉。\n昔日昔时经此地，看来看去渐成川。\n歌台舞榭宜正月，柳岸梅洲胜往年。\n莫言波上春云少，祗为从龙直上天。', NULL, 3),
    ('郊庙歌辞 享龙池乐章 第三章', '沈佺期', '唐代', '龙池跃龙龙已飞，龙德[光]天天不违。\n池开天汉分黄道，龙向天门入紫微。\n邸第楼台多气色，君王凫雁有光辉。\n为报寰中百川水，来朝此地莫东归。', NULL, 3),
    ('郊庙歌辞 享龙池乐章 第四章', '卢怀慎', '唐代', '代邸东南龙跃泉，清漪碧浪远浮天。\n楼台影就波中出，日月光疑镜里悬。\n雁沼回流成舜海，龟书荐祉应尧年。\n大川既济惭为檝，报德空思奉细涓。', NULL, 3),
    ('郊庙歌辞 享龙池乐章 第五章', '姜皎', '唐代', '龙池初出此龙山，常经此地谒龙颜。\n日日芙蓉生夏水，年年杨柳变春湾。\n尧坛宝匣余烟雾，舜海渔舟尚往还。\n愿似飘飖五云影，从来从去九天间。', NULL, 3),
    ('郊庙歌辞 享龙池乐章 第六章', '崔日用', '唐代', '龙兴白水汉兴符，圣主时乘运斗枢。\n岸上丰茸五花树，波中的皪千金珠。\n操环昔闻迎夏启，发匣先来瑞有虞。\n风色云光随隐见，赤云神化象江湖。', NULL, 3),
    ('郊庙歌辞 享龙池乐章 第七章', '苏颋', '唐代', '西京凤邸跃龙泉，佳气休光镇在天。\n轩后雾图今已得，秦王水剑昔常传。\n恩鱼不似昆明钓，瑞鹤长如太液仙。\n愿侍巡游同旧里，更闻箫鼓济楼船。', NULL, 3),
    ('郊庙歌辞 享龙池乐章 第八章', '李乂', '唐代', '星分邑里四人居，水洊源流万顷余。\n魏国君王称象处，晋家藩邸化龙初。\n青蒲暂似游梁马，绿藻还疑宴镐鱼。\n自有神灵滋液地，年年云物史官书。', NULL, 3),
    ('郊庙歌辞 享龙池乐章 第九章', '姜皎', '唐代', '灵沼萦回邸第前，浴日涵天写曙天。\n始见龙台升凤阙，应如霄汉起神泉。\n石匮渚傍还启圣，桃李初生更有仙。\n欲化帝图从此受，正同河变一千年。', NULL, 3),
    ('郊庙歌辞 享龙池乐章 第十章', '裴璀', '唐代', '乾坤启圣吐龙泉，泉水年年胜一年。\n始看鱼跃方成海，即睹龙飞利在天。\n洲渚遥将银汉接，楼台直与紫微连。\n休气荣光常不散，悬知此地是神仙。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 永和', '魏徵、褚亮', '唐代', '于穆烈祖，弘此丕基。\n永言配命，子孙保之。\n百神既洽，万国在兹。\n是用孝享，神其格思。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 肃和', '魏徵、褚亮', '唐代', '大哉至德，允兹明圣。\n格于上下，聿遵诚敬。\n嘉乐斯登，鸣球以咏。\n神其降止，式隆景命。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 雍和', '魏徵、褚亮', '唐代', '崇兹享祀，诚敬兼至。\n乐以感灵，礼以昭事。\n粢盛咸絜，牲牷孔备。\n永言孝思，庶几不匮。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 长发舞', '魏徵、褚亮', '唐代', '睿哲维唐，长发其祥。\n帝命斯祐，王业克昌。\n配天载德，就日重光。\n本支百代，申锡无疆。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 大基舞', '魏徵、褚亮', '唐代', '猗与祖业，皇矣帝先。\n翦商德厚，封唐庆延。\n在姬犹稷，方晋喻宣。\n基我鼎运，于万斯年。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 大成舞', '魏徵、褚亮', '唐代', '周称王季，晋美帝文。\n明明盛德，穆穆齐芬。\n藏用四履，屈道三分。\n铿锵锺石，载纪鸿勋。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 大明舞', '魏徵、褚亮', '唐代', '五纪更运，三正递升。\n勋华既没，禹汤勃兴。\n神武命代，灵眷是膺。\n望云彰德，察纬告征。\n上纽天维，下安地轴。\n征师涿野，万国咸服。\n偃伯灵台，九官允穆。\n殊域委賮，怀生介福。\n大礼既饰，大乐已和。\n黑章扰囿，赤字浮河。\n功宣载籍，德被咏歌。\n克昌厥后，百禄是荷。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 寿和', '魏徵、褚亮', '唐代', '八音斯奏，三献毕陈。\n宝祚惟永，晖光日新。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 舒和', '魏徵、褚亮', '唐代', '圣敬通神光七庙，灵心荐祚和万方。\n严禋克配鸿基远，明德惟馨凤历昌。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 崇德舞', '不详', '唐代', '五运改卜，千龄启圣。\n彤云晓聚，黄星夜映。\n叶阐珠囊，基开玉镜。\n下临万宇，上齐七政。\n雾开三象，尘清九服。\n海溓星晖，远安迩肃。\n天地交泰，华夷辑睦。\n翔泳归仁，中外禔福。\n绩逾黜夏，勋高翦商。\n武陈七德，刑设三章。\n祥禽巢阁，仁兽游梁。\n卜年惟永，景福无疆。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 钧天[舞]', '不详', '唐代', '承天抚箓，纂圣登皇。\n遐清万宇，仰协三光。\n功成日用，道济时康。\n璇图载永，宝历斯昌。\n日月扬晖，烟云烂色。\n河岳修贡，神祇效职。\n舜风攸偃，尧曦先就。\n睿感通寰，孝思浃宙。\n奉扬先德，虔遵曩狩。\n展义天扄，飞英云岫。\n化逸王表，神凝帝先。\n乘云厌俗，驭日登玄。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 太和舞', '不详', '唐代', '广乐既备，嘉荐既新。\n述先惟德，孝飨惟亲。\n七献具举，五齐毕陈。\n锡兹祚福，于万斯春。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 景云舞', '不详', '唐代', '惟睿作圣，惟圣登皇。\n精感耀魄，时膺会昌。\n舜惭大孝，尧推让王。\n能事斯极，振古谁方。\n文明履运，车书同轨。\n巍巍赫赫，尽善尽美。\n衢室凝旒，大庭端扆。\n释负之寄，事光复子。\n脱屣高天，登遐上玄。\n龙湖超忽，象野芊绵。\n游衣复道，荐果初年。\n新庙奕奕，明德配天。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 光大舞', '不详', '唐代', '大业龙祉，徽音骏尊。\n潜居皇德，赫嗣天昆。\n展仪宗祖，重诚孝孙。\n春秋无极，享奏存存。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 迎神', '不详', '唐代', '七庙观德，百灵攸仰。\n俗荷财成，物资含养。\n道光执契，化笼提象。\n肃肃雍雍，神其来飨。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 金奏', '不详', '唐代', '肃肃清庙，巍巍盛唐。\n配天立极，累圣重光。\n乐和管磬，礼备烝尝。\n永惟来格，降福无疆。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 送神', '不详', '唐代', '五声备奏，三献终祠。\n车移凤辇，斾转虹旗。\n礼周笾豆，诚效虔祗。\n皇灵徙跸，簪绅拜辞。', NULL, 3),
    ('郊庙歌辞 武后享清庙乐章十首 第一', '不详', '唐代', '建清庙，赞玄功。\n择吉日，展禋宗。\n乐已变，礼方崇。\n望神驾，降仙宫。', NULL, 3),
    ('郊庙歌辞 武后享清庙乐章十首 第二', '不详', '唐代', '隆周创业，宝命惟新。\n敬宗茂典，爰表虔禋。\n声明已备，文物斯陈。\n肃容如在，恳志方申。', NULL, 3),
    ('郊庙歌辞 武后享清庙乐章十首 第三登歌', '不详', '唐代', '肃敷大礼，上谒尊灵。\n敬[陈]筐币，载表丹诚。', NULL, 3),
    ('郊庙歌辞 武后享清庙乐章十首 第四迎神', '不详', '唐代', '敬奠蘋藻，式罄虔襟。\n洁诚斯展，伫降灵歆。', NULL, 3),
    ('郊庙歌辞 武后享清庙乐章十首 第五饮福', '不详', '唐代', '爰陈玉醴，式奠琼浆。\n灵心有穆，介福无疆。', NULL, 3),
    ('郊庙歌辞 武后享清庙乐章十首 第六送文舞', '不详', '唐代', '帝图草创，皇业初开。\n功高佐命，业赞云雷。', NULL, 3),
    ('郊庙歌辞 武后享清庙乐章十首 第七迎武舞', '不详', '唐代', '赫赫玄功被穹壤，皇皇至德洽生灵。\n开基拨乱妖氛廓，佐命宣威海内清。', NULL, 3),
    ('郊庙歌辞 武后享清庙乐章十首 第八武舞作', '不详', '唐代', '荷恩承顾托，执契恭临抚。\n庙略静边荒，天兵耀神武。', NULL, 3),
    ('郊庙歌辞 武后享清庙乐章十首 第九彻俎', '不详', '唐代', '登歌已阕，献礼方周。\n钦承景福，肃奉鸿休。', NULL, 3),
    ('郊庙歌辞 武后享清庙乐章十首 第十送神', '不详', '唐代', '大礼言毕，仙卫将归。\n莫申丹恳，空瞻紫微。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 严和', '不详', '唐代', '肃肃清庙，赫赫玄猷。\n功高万古，化奄十洲。\n中兴丕业，上荷天休。\n祗奉先构，礼备怀柔。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 升和', '不详', '唐代', '顾惟菲薄，纂历应期。\n中外同轨，夷狄来思。\n乐用崇德，礼以陈词。\n夕惕若厉，钦奉弘基。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 虔和', '不详', '唐代', '礼标荐鬯，肃事祠庭。\n敬申如在，敢托非馨。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 歆和', '不详', '唐代', '崇禋已备，粢盛聿修。\n洁诚斯展，锺石方遒。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 承光舞', '不详', '唐代', '金相载穆，玉裕重辉。\n养德清禁，承光紫微。\n干宫候色，震象增威。\n监国方永，宾天不归。\n孝友自衷，温文性与。\n龙楼正启，鹤驾斯举。\n丹扆流念，鸿名式序。\n中兴考室，永陈彝俎。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 延和', '不详', '唐代', '巍巍累圣，穆穆重光。\n奄有区夏，祚启隆唐。\n百蛮饮泽，万国来王。\n本枝亿载，鼎祚逾长。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 同和', '不详', '唐代', '惟圣配天敷盛礼，惟天为大阐洪名。\n恭禋展敬光先德，蘋藻申虔表志诚。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 宁和', '不详', '唐代', '炎驭失天纲，土德承天命。\n英猷被寰宇，懿躅隆邦政。\n七德已绥边，九夷咸底定。\n景化覃遐迩，深仁洽翔泳。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 恭和', '不详', '唐代', '礼周三献，乐阕九成。\n肃承灵福，悚惕兼盈。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 通和', '不详', '唐代', '祠容既毕，仙座爰兴。\n停停凤举，霭霭云升。\n长隆宝运，永锡休征。\n福覃贻厥，恩被黎蒸。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 昭和', '不详', '唐代', '道洽二仪交泰，时休四宇和平。\n环珮肃于庭实，锺石扬乎颂声。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 诚敬', '不详', '唐代', '顾惟菲质，忝位椒宫。\n虔奉蘋藻，肃事神宗。\n敢申诚絜，庶罄深衷。\n睟容有裕，灵享无穷。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 肃和', '不详', '唐代', '月礼已周，云和将变。\n爰献其醑，载迁其奠。\n明德逾隆，非馨是荐。\n泽霑动植，仁覃宇县。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 昭感', '不详', '唐代', '铿锵韶濩，肃穆神容。\n洪规赫赫，祠典雍雍。\n已周三献，将乘六龙。\n虔诚有托，恳志无从。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 永和三章 一', '张说', '唐代', '肃九室，谐八音。\n歌皇慕，动神心。\n礼宿设，乐妙寻。\n声明备，[祼]奠临。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 永和三章 二', '张说', '唐代', '律迓气，音入玄。\n依玉几，御黼筵。\n聆忾息，僾周旋。\n九韶遍，百福传。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 永和三章 三', '张说', '唐代', '信工祝，永颂声。\n来祖考，听和平。\n相百辟，贡九瀛。\n神休委，帝孝成。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 太和', '张说', '唐代', '时文圣后，清庙肃邕。\n致诚勤荐，在貌思恭。\n玉节肆夏，金锵五锺。\n绳绳云步，穆穆天容。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 肃和', '张说', '唐代', '天子享孝，工歌溥将。\n躬[祼]郁鬯，乃焚膋芗。\n臭以达阴，声以求阳。\n奉时烝尝，永代不忘。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 雍和二章 一', '张说', '唐代', '在涤嘉豢，丽碑敬牲。\n角握之牡，色纯之骍。\n火传阳燧，水溉阴精。\n太公胖俎，[傅]说和羹。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 雍和二章 二', '张说', '唐代', '俎豆有馥，粢盛絜丰。\n亦有和羹，既戒既平。\n鼓钟管磬，肃唱和鸣。\n皇皇后祖，来我思成。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 文舞', '张说', '唐代', '圣謩九德，真言五千。\n庆集昌胄，符开帝先。\n高文杖钺，克配彼天。\n三宗握镜，六合涣然。\n帝其承祀，率礼罔愆。\n图书雾出，日月清悬。\n舞形德类，咏谂功传。\n黄龙蜿蟺，彩云蹁跹。\n五行气顺，八佾风宣。\n介此百禄，于皇万年。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 光大舞', '张说', '唐代', '肃肃艺祖，滔滔濬源。\n有雄玉剑，作镇金门。\n玄王贻绪，后稷谋孙。\n肇禋九庙，四海来尊。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 长发舞', '张说', '唐代', '具礼崇德，备乐承风。\n魏推幢主，周赠司空。\n不行而至，无成有终。\n神兴王业，天归帝功。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 大政舞', '张说', '唐代', '于赫元命，权舆帝文。\n天齐八柱，地半三分。\n宗庙观德，笙镛乐勋。\n封唐之兆，成天下君。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 大成舞', '张说', '唐代', '帝舞季历，龙圣生昌。\n后歌有𫊸，胎炎孕黄。\n天地合德，日月齐光。\n肃雝孝享，祚我万方。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 大明舞', '张说', '唐代', '赤精乱德，四海困穷。\n黄旗举义，三灵会同。\n旱望春雨，云披大风。\n溥天来祭，高祖之功。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 崇德舞', '张说', '唐代', '皇合一德，朝宗百神。\n削平天地，大拯生人。\n上帝配食，单于入臣。\n戎歌陈舞，晔晔震震。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 钧天舞', '张说', '唐代', '高皇迈道，端拱无为。\n化怀獯鬻，兵赋句骊。\n礼尊封禅，乐盛来仪。\n合位娲后，同称伏羲。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 大和舞', '张说', '唐代', '退居江水，郁起丹陵。\n礼物还旧，朝章中兴。\n龙图友及，骏命恭膺。\n鸣球香瓒，大糦是承。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 景云舞', '张说', '唐代', '景云霏烂，告我帝符。\n噫帝冲德，与天为徒。\n笙镛遥远，俎豆虚无。\n春秋孝献，回复此都。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 福和', '张说', '唐代', '备礼用乐，崇亲致尊。\n诚通慈降，敬彻爱存。\n献怀称寿，啐感承恩。\n皇帝孝德，子孙千亿。\n大包天域，长亘不极。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 舒和', '张说', '唐代', '六钟翕协六变成，八佾徜徉八风生。\n乐九韶兮人神感，美七德兮天地清。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 凯安四章 一', '张说', '唐代', '瑟彼瑶爵，亚惟上公。\n室如屏气，门不容躬。\n礼殷其本，乐执其中。\n圣皇永慕，天地幽通。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 凯安四章 二', '张说', '唐代', '礼匝三献，乐遍九成。\n降循轩陛，仰欷皇情。\n福与仁合，德因孝明。\n百年[神畏]，四海风行。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 凯安四章 三', '张说', '唐代', '总总干戚，填填鼓钟。\n奋扬争气，坐作为容。\n离若鸷鸟，合如战龙。\n万方观德，肃肃雝雝。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 凯安四章 四', '张说', '唐代', '烈祖顺三灵，文宗威四海。\n黄钺诛群盗，朱旗扫多罪。\n戢兵天下安，约法人心改。\n大哉干羽意，长见风云在。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 登歌', '张说', '唐代', '止笙磬，撤豆笾。\n廓无响，窅入玄。\n主在室，神在天。\n情余慕，礼罔愆。\n喜黍稷，屡丰年。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 永和', '张说', '唐代', '眇嘉乐，授灵爽。\n感若来，思如往。\n休气散，回风上。\n返寂寞，还惚恍。\n怀灵驾，结空想。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 广运舞', '郭子仪', '唐代', '于赫皇祖，昭明有融。\n惟文之德，惟武之功。\n河海静谧，车书混同。\n虔恭孝飨，穆穆玄风。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 惟新舞', '刘晏', '唐代', '汉祚惟永，神功中兴。\n风驱氛祲，天覆黎蒸。\n三光再朗，庶绩其凝。\n重熙累洽，景命是膺。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 保大舞', '郭子仪', '唐代', '于穆文考，圣神昭章。\n肃勺群慝，含光远方。\n万物茂遂，九夷宾王。\n愔愔云韶，德音不忘。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 文明舞', '郑馀庆', '唐代', '开邸除暴，时迈勋尊。\n三元告命，四极骏奔。\n金枝翠叶，辉烛瑶琨。\n象德亿载，贻庆汤孙。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 大顺舞', '郑絪', '唐代', '于穆时文，受天明命。\n允恭玄默，化成理定。\n出震嗣德，应干传圣。\n猗欤缉熙，千亿流庆。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 象德舞', '段文昌', '唐代', '肃肃清庙，登显至德。\n泽周八荒，兵定四极。\n生物咸遂，群盗灭息。\n明圣钦承，子孙千亿。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 和宁舞', '牛僧孺', '唐代', '湜湜颀颀，融昭德辉。\n不纽不舒，贯成九围。\n武烈文经，敷施当宜。\n纂尧付启，亿万熙熙。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 大定舞', '李回', '唐代', '受天明命，敷佑下土。\n化时以俭，卫文以武。\n氛消夷夏，俗臻往古。\n亿万斯年，形于律吕。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 宣宗室舞', '夏侯孜', '唐代', '于铄令主，圣祚重昌。\n兴起教义，申明典章。\n俗尚素朴，人皆乐康。\n积德可报，流庆无疆。', NULL, 3),
    ('郊庙歌辞  懿宗室舞', '萧仿', '唐代', '圣祚无疆，庆传乐章。\n金枝繁茂，玉叶延长。\n海渎常[晏]，波涛不扬。\n汪汪美化，垂范今王。', NULL, 3),
    ('郊庙歌辞 享太庙乐章 咸宁舞', '不详', '唐代', '于铄丕嗣，惟帝之光。\n羽籥象德，金石荐祥。\n圣系无极，景命永昌。\n神降上哲，维天配长。', NULL, 3),
    ('郊庙歌辞 太清宫乐章 煌煌', '不详', '唐代', '煌煌道宫，肃肃太清。\n礼光尊祖，乐备充庭。\n罄竭诚至，希夷降灵。\n云凝翠盖，风焰红旌。\n众真以从，九奏初迎。\n永惟休祐，是锡和平。', NULL, 3),
    ('郊庙歌辞 太清宫乐章 冲和', '不详', '唐代', '虚无结思，钟磬和音。\n歌以颂德，香以达心。\n礼殊祼鬯，义感昭临。\n云车至止，庆垂愔愔。', NULL, 3),
    ('郊庙歌辞 太清宫乐章 香初上', '不详', '唐代', '肃肃我祖，绵绵道宗。\n至感潜达，灵心暗通。\n云𫐌御气，芝盖随风。\n四时禋祀，万国来同。', NULL, 3),
    ('郊庙歌辞 太清宫乐章 再上', '不详', '唐代', '仙宗绩道，我李承天。\n庆深虚极，符光象先。\n俗登仁寿，化阐蟺涓。\n五千贻范，亿万斯年。', NULL, 3),
    ('郊庙歌辞 太清宫乐章 终上', '不详', '唐代', '不宰元功，无为上圣。\n洪源长发，诞受天命。\n金奏迎真，璇宫展盛。\n备礼周乐，垂光储庆。', NULL, 3),
    ('郊庙歌辞 太清宫乐章 紫极舞', '不详', '唐代', '至道生元气，重圆法混成。\n无为观大象，冲用体常名。\n仙乐临丹阙，云车出玉京。\n灵符百代应，瑞节九贞迎。\n宝运开皇极，天临映太清。\n长垂一德庆，永庇万方宁。', NULL, 3),
    ('郊庙歌辞 太清宫乐章 序入破第一奏', '不详', '唐代', '真宗开妙理，冲教统清虚。\n化演无为日，言昭有象初。\n瑶台肃灵瑞，金阙映仙居。\n一奏三清乐，长回八景舆。', NULL, 3),
    ('郊庙歌辞 太清宫乐章 序入破第二奏', '不详', '唐代', '虚极仙宗本，希夷象帝先。\n百灵朝太上，万法祖重圆。\n善贷惟冲德，功成谓自然。\n云门达和气，思用合钧天。', NULL, 3),
    ('郊庙歌辞 太清宫乐章 序入破第三奏', '不详', '唐代', '元符传紫极，宝祚启高真。\n道德先垂裕，冲和已化淳。\n人风齐太古，天瑞叶惟新。\n仙乐清都上，长明交泰辰。', NULL, 3),
    ('郊庙歌辞 太清宫乐章 登歌', '不详', '唐代', '严禋展事，理洁烝尝。\n皇矣圣祖，德惟馨香。\n盛荐既撤，工歌载扬。\n大来之庆，降福穰穰。', NULL, 3),
    ('郊庙歌辞 太清宫乐章 真和', '不详', '唐代', '玉磬含香，金炉既馥。\n风驭泠泠，云坛肃肃。\n杳归大象，霈流嘉福。\n俾宁万邦，无思不服。', NULL, 3),
    ('郊庙歌辞 德明兴圣庙乐章 迎神', '李舒', '唐代', '元尊九德，佐尧光宅。\n烈祖太宗，方周作伯。\n响怀霜露，乐变金石。\n白云清风，髣髴来格。', NULL, 3),
    ('郊庙歌辞 德明兴圣庙乐章 登歌奠币', '李舒', '唐代', '四时有典，百事来祭。\n尊祖奉宗，严禋大帝。\n礼先苍璧，奠币黝制。\n于斯万年，熙成帝系。', NULL, 3),
    ('郊庙歌辞 德明兴圣庙乐章 迎俎', '李舒', '唐代', '盛牲实俎，涓选休成。\n鼎煁阳燧，玉盥阴精。\n有𫗣嘉豆，既和大羹。\n侑以清乐，细齐人情。', NULL, 3),
    ('郊庙歌辞 德明兴圣庙乐章 德明酌献', '李舒', '唐代', '清庙奕奕，和乐雍雍。\n器尊牺象，礼属宗公。\n白水方祼，黄流在中。\n谟明之德，万古清风。', NULL, 3),
    ('郊庙歌辞 德明兴圣庙乐章 兴圣酌献', '李舒', '唐代', '閟宫静谧，合乐周张。\n泰尊始献，百末重觞。\n震澹存诚，庶几迪尝。\n遥源之祚，天汉灵长。', NULL, 3),
    ('郊庙歌辞 德明兴圣庙乐章 亚献终献', '李舒', '唐代', '惟清惟肃，靡闻靡见。\n举备九成，俛终三献。\n庆彰曼寿，胙彻嘉荐。\n瘗玉埋牲，礼神斯遍。', NULL, 3),
    ('郊庙歌辞 德明兴圣庙乐章 送神', '李舒', '唐代', '元精回复，灵贶繁滋。\n风洒兰路，云摇桂旗。\n高丘缅邈，凉部逶迟。\n瞻望靡及，缠绵永思。', NULL, 3),
    ('郊庙歌辞 仪坤庙乐章 永和', '徐彦伯', '唐代', '猗若清庙，肃肃荧荧。\n国荐严祀，坤兴淑灵。\n有几在室，有乐在庭。\n临兹孝享，百禄惟宁。', NULL, 3),
    ('郊庙歌辞 仪坤庙乐章 金奏', '不详', '唐代', '阴灵曜祉，轩曜降精。\n祥符淑气，庆集柔明。\n瑶俎既列，雕桐发声。\n徽猷永远，比德皇英。', NULL, 3),
    ('郊庙歌辞 仪坤庙乐章 太和', '丘说', '唐代', '孝哉我后，冲乎迺圣。\n道映重华，德辉文命。\n慕深视箧，情殷抚镜。\n万国移风，兆人承庆。', NULL, 3),
    ('郊庙歌辞 仪坤庙乐章 肃和', '张齐贤', '唐代', '祼圭既濯，郁鬯既陈。\n画幕云举，黄流玉醇。\n仪充献酌，礼盛众禋。\n地察惟孝，愉焉飨亲。', NULL, 3),
    ('郊庙歌辞 仪坤庙乐章 雍和', '郑善玉', '唐代', '酌郁既灌，芗萧方𦶟。\n笾豆静器，簠簋芬𫗣。\n鱼腊荐美，牲牷表洁。\n是戢是将，载迎载列。', NULL, 3),
    ('郊庙歌辞 仪坤庙乐章 昭升', '薛稷', '唐代', '阳灵配德，阴魄昭升。\n尧坛凤下，汉室龙兴。\n伣天作对，前旒是凝。\n化行南国，道盛西陵。\n造舟集灌，无德而称。\n我粢既絜，我醴既澄。\n阴阴灵庙，光灵若凭。\n德馨惟享，孝思烝烝。', NULL, 3),
    ('郊庙歌辞 仪坤庙乐章 坤贞', '不详', '唐代', '乾道既亨，坤元以贞。\n肃雍攸在，辅佐斯成。\n外睦九族，内光一庭。\n克生睿哲，祚我休明。\n钦若徽范，悠哉淑灵。\n建兹清宫，于彼上京。\n缩茅以献，絜秬惟馨。\n实受其福，期乎亿龄。', NULL, 3),
    ('郊庙歌辞 仪坤庙乐章 寿和', '徐坚', '唐代', '于穆清庙，肃雍严祀。\n合福受厘，介以繁[祉]。', NULL, 3),
    ('郊庙歌辞 仪坤庙乐章 舒和', '胡雄', '唐代', '送文迎武递参差，一始一终光圣仪。\n四海生人歌有庆，千龄孝享肃无亏。', NULL, 3),
    ('郊庙歌辞 仪坤庙乐章 安和', '刘子玄', '唐代', '妙筭申帷幄，神谋及庙庭。\n两阶文物备，七德武功成。\n校猎长杨苑，屯军细柳营。\n将军献凯入，歌舞溢重城。', NULL, 3),
    ('郊庙歌辞 仪坤庙乐章 雍和', '员半千', '唐代', '孝享云毕，惟彻有章。\n云感玄羽，风凄素商。\n瞻望神座，祗恋匪遑。\n礼终乐阕，肃雍锵锵。', NULL, 3),
    ('郊庙歌辞 仪坤庙乐章 永和', '祝钦明', '唐代', '閟宫实实，清庙微微。\n降格无象，馨香有依。\n式昭纂庆，方融嗣徽。\n明禋是享，神保聿归。', NULL, 3),
    ('郊庙歌辞 仪坤庙乐章 迎神', '不详', '唐代', '月灵降德，坤元授光。\n娥英比秀，任姒均芳。\n瑶台荐祉，金屋延祥。\n迎神有乐，歆此嘉芗。', NULL, 3),
    ('郊庙歌辞 仪坤庙乐章 送神', '不详', '唐代', '玉帛仪大，金丝奏广。\n灵应有孚，冥征不爽。\n降彼休福，歆兹禋享。\n送乐有章，神麾其上。', NULL, 3),
    ('郊庙歌辞 昭德皇后庙乐章 永和', '不详', '唐代', '穆清庙，荐严禋。\n昭礼备，和乐新。\n望灵光，集元辰。\n祚无极，享万春。', NULL, 3),
    ('郊庙歌辞 昭德皇后庙乐章 肃和', '不详', '唐代', '诚心达，娱乐分。\n升箫膋，郁氛氲。\n茅既缩，鬯既薰。\n后来思，福如云。', NULL, 3),
    ('郊庙歌辞 昭德皇后庙乐章 雍和', '不详', '唐代', '我将我享，尽明而诚。\n载芬黍稷，载涤牺牲。\n懿矣元良，万邦以贞。\n心乎爱敬，若睹容声。', NULL, 3),
    ('郊庙歌辞 昭德皇后庙乐章 坤元', '不详', '唐代', '于穆先后，丽圣称崇。\n母临万宇，道[被]六宫。\n昌时协庆，理内成功。\n殷荐明德，传芳国风。', NULL, 3),
    ('郊庙歌辞 昭德皇后庙乐章 寿和', '不详', '唐代', '工祝致告，徽音不遐。\n酒醴咸旨，馨香具嘉。\n受厘献祉，永庆邦家。', NULL, 3),
    ('郊庙歌辞 昭德皇后庙乐章 舒和', '不详', '唐代', '金枝羽部彻清歌，瑶台肃穆笙磬罗。\n谐音遍响合明意，万类昭融灵应多。', NULL, 3),
    ('郊庙歌辞 昭德皇后庙乐章 凯安', '不详', '唐代', '辰位列四星，帝功参十乱。\n进贤勤内辅，扈跸清多难。\n承天厚载均，并耀宵光璨。\n留徽蔼前躅，万古披图焕。', NULL, 3),
    ('郊庙歌辞 让皇帝庙乐章 迎神', '李舒', '唐代', '皇矣天宗，德先王季。\n因心则友，克让以位。\n爰命有司，式遵前志。\n神其降灵，昭飨祀事。', NULL, 3),
    ('郊庙歌辞 让皇帝庙乐章 奠币', '李舒', '唐代', '惟帝时若，去而上仙。\n祀用商舞，乐备宫悬。\n白璧加荐，玄𫄸告虔。\n子孙拜后，承兹吉蠲。', NULL, 3),
    ('郊庙歌辞 让皇帝庙乐章 迎俎', '李舒', '唐代', '祀盛体荐，礼协粢盛。\n方周假庙，用鲁纯牲。\n捧彻祗敬，击拊和鸣。\n受厘归胙，既戒而平。', NULL, 3),
    ('郊庙歌辞 让皇帝庙乐章 酌献', '李舒', '唐代', '八音具举，三寿既盥。\n洁兹宗彝，瑟彼圭瓒。\n兰肴重错，椒醑飘散。\n降胙维城，永为藩翰。', NULL, 3),
    ('郊庙歌辞 让皇帝庙乐章 亚献终献', '李舒', '唐代', '秩礼有序，和音既同。\n九仪不忒，三揖将终。\n孝感藩后，相维辟公。\n四时之典，永永无穷。', NULL, 3),
    ('郊庙歌辞 让皇帝庙乐章 送神', '李舒', '唐代', '奠献已事，昏昕载分。\n风摇雨散，灵卫絪缊。\n龙驾帝服，上腾五云。\n泮宫复閟，寂寞无闻。', NULL, 3),
    ('郊庙歌辞 享隐太子庙乐章 诚和', '不详', '唐代', '道閟鹤关，运缠鸠里。\n门集大命，俾歆嘉祀。\n礼亚六瑚，诚殚二簋。\n有诚颙若，神斯戾止。', NULL, 3),
    ('郊庙歌辞 享隐太子庙乐章 肃和', '不详', '唐代', '岁肇春宗，干开震长。\n瑶山既寂，戾园斯享。\n玉肃其事，物昭其象。\n弦诵成风，笙歌合响。', NULL, 3),
    ('郊庙歌辞 享隐太子庙乐章 雍和', '不详', '唐代', '明典肃陈，神居邃启。\n春伯联事，秋官相礼。\n有来雍雍，登歌济济。\n缅维主鬯，庶歆芳醴。', NULL, 3),
    ('郊庙歌辞 享隐太子庙乐章 舒和', '不详', '唐代', '三县已判歌钟列，六佾将开羽戚分。\n尚想燕飞来蔽日，终疑鹤影降凌云。', NULL, 3),
    ('郊庙歌辞 享隐太子庙乐章 凯安', '不详', '唐代', '天步昔将开，商郊初欲践。\n抚戎金阵廓，贰极瑶图阐。\n鸡戟遂崇仪，龙楼期好善。\n弄兵隳震业，启圣隆嗣典。', NULL, 3),
    ('郊庙歌辞 隐太子庙乐章 迎神', '不详', '唐代', '苍震有位，黄离蔽明。\n江充祸结，戾据灾成。\n衔冤昔痛，赠典今荣。\n享灵有秩，奉乐以迎。', NULL, 3),
    ('郊庙歌辞 隐太子庙乐章 送神', '不详', '唐代', '皇情悼往，祀议增设。\n钟鼓铿锵，羽旄昭晰。\n掌礼云备，司筵告彻。\n乐以送神，灵其鉴阕。', NULL, 3),
    ('郊庙歌辞 享章怀太子庙乐章 迎神', '不详', '唐代', '副君昭象，道应黄离。\n铜楼备德，玉裕成规。\n仙气霭霭，灵从师师。\n前驱戾止，控鹤来仪。', NULL, 3),
    ('郊庙歌辞 享章怀太子庙乐章 登歌酌鬯', '不详', '唐代', '忠孝本着，羽翼先成。\n寝门昭德，驰道为程。\n币帛有典，容卫无声。\n司存既肃，庙享惟清。', NULL, 3),
    ('郊庙歌辞 享章怀太子庙乐章 迎俎酌献', '不详', '唐代', '通三锡胤，明两承英。\n太山比赫，伊水闻笙。\n宗祧是寄，礼乐其亨。\n嘉辰荐俎，以发声明。', NULL, 3),
    ('郊庙歌辞 享章怀太子庙乐章 送文舞迎武舞', '不详', '唐代', '羽籥崇文礼以毕，干戚奋武事将行。\n用舍繇来其有致，壮志宣威乐太平。', NULL, 3),
    ('郊庙歌辞 享章怀太子庙乐章 武舞作', '不详', '唐代', '绿林炽炎历，黄虞格有苗。\n沙尘惊塞外，帷幄命嫖姚。\n七德干戈止，三边云雾消。\n宝祚长无极，歌舞盛今朝。', NULL, 3),
    ('郊庙歌辞 享懿德太子庙乐章 迎神', '不详', '唐代', '甲观昭祥，画堂升位。\n礼绝群后，望尊储贰。\n启诵惭德，庄丕掩粹。\n伊浦凤翔，缑峰鹤至。', NULL, 3),
    ('郊庙歌辞 享懿德太子庙乐章 登歌酌鬯', '不详', '唐代', '誉阐元储，寄崇明两。\n玉裕虽晦，铜楼可想。\n弦诵辍音，笙歌罢响。\n币帛言设，礼容无爽。', NULL, 3),
    ('郊庙歌辞 享懿德太子庙乐章 迎俎酌献', '不详', '唐代', '雍雍盛典，肃肃灵祠。\n宾天有圣，对日无期。\n飘飖羽服，掣曳云旗。\n眷言主鬯，心乎怆兹。', NULL, 3),
    ('郊庙歌辞 享懿德太子庙乐章 送文舞迎武舞', '不详', '唐代', '八音协奏陈金石，六佾分行整礼容。\n沧溟赴海还称少，素月开轮即是重。', NULL, 3),
    ('郊庙歌辞 享懿德太子庙乐章 武舞作', '不详', '唐代', '隋季昔云终，唐年初启圣。\n纂戎将禁暴，崇儒更敷政。\n威略静三边，仁恩覃万姓。', NULL, 3),
    ('郊庙歌辞 享节愍太子庙乐章 迎神', '不详', '唐代', '储后望崇，元良寄切。\n寝门是仰，驰道不绝。\n仙袂云会，灵旗电晰。\n煌煌而来，礼物攸设。', NULL, 3),
    ('郊庙歌辞 享节愍太子庙乐章 登歌酌鬯', '不详', '唐代', '灼灼重明，仰承元首。\n既贤且哲，惟孝与友。\n惟孝虽遥，灵规不朽。\n礼因诚致，备絜玄酒。', NULL, 3),
    ('郊庙歌辞 享节愍太子庙乐章 迎俎酌献', '不详', '唐代', '嘉荐有典，至诚莫骞。\n画梁云亘，雕俎星连。\n乐器周列，礼容备宣。\n依稀如在，若未宾天。', NULL, 3),
    ('郊庙歌辞 享节愍太子庙乐章 送文舞出迎武舞入', '不详', '唐代', '邕邕阐化凭文德，赫赫宣威藉武功。\n既执羽旄先拂吹，还持玉戚更挥空。', NULL, 3),
    ('郊庙歌辞 享节愍太子庙乐章 武舞作', '不详', '唐代', '武德谅雍雍，由来扫寇戎。\n剑光挥作电，旗影列成虹。\n雾廓三边静，波澄四海同。\n睿图今已盛，相共舞皇风。', NULL, 3),
    ('郊庙歌辞 享文敬太子庙乐章 请神', '许孟容', '唐代', '觞牢具品，管磬有节。\n祝道夤恭，神仪昭晰。\n桐圭早贵，象辂追设。\n声达乐成，降歆丰洁。', NULL, 3),
    ('郊庙歌辞 享文敬太子庙乐章 登歌', '陈京', '唐代', '歌以德发，声以乐贵。\n乐善名存，追仙礼异。\n鸾旌拱修，凤鸣合吹。\n神听皇慈，仲月皆至。', NULL, 3),
    ('郊庙歌辞 享文敬太子庙乐章 迎俎酌献', '冯伉', '唐代', '撰日瞻景，诚陈乐张。\n礼容秩秩，羽舞煌煌。\n肃将涤濯，祗荐芬芳。\n永锡繁祉，思深飨尝。', NULL, 3),
    ('郊庙歌辞 享文敬太子庙乐章 送文舞迎武舞', '不详', '唐代', '干旄羽籥相亏蔽，一进一退殊行缀。\n昔献三雍盛礼容，今陈六佾崇仪制。', NULL, 3),
    ('郊庙歌辞 享文敬太子庙乐章 亚献终献', '崔邠', '唐代', '醴齐泛尊彝，轩县动干戚。\n入室僾如在，升阶虔所历。\n奋疾合威容，定利舒皦绎。\n方崇庙貌礼，永被君恩锡。', NULL, 3),
    ('郊庙歌辞 享文敬太子庙乐章 送神', '张荐', '唐代', '三献具举，九旗将旋。\n追劳表德，罢享宾天。\n风引仙管，堂虚画筵。\n芳馨常在，瞻望悠然。', NULL, 3),
    ('郊庙歌辞 享惠昭太子庙乐章 请神', '归登', '唐代', '嘉荐既陈，祀事孔明。\n闲歌在堂，万舞在庭。\n外则尽物，内则尽诚。\n凤笙如闻，歌其洁精。', NULL, 3),
    ('郊庙歌辞 享惠昭太子庙乐章 登歌', '杜羔', '唐代', '因心克孝，位震遗芬。\n宾天道茂，轸怀气分。\n发祗乃祀，咳叹如闻。\n二歌斯升，以咏德薰。', NULL, 3),
    ('郊庙歌辞 享惠昭太子庙乐章 迎俎酌献', '李逢吉', '唐代', '既洁酒醴，聿陈熟腥。\n肃将震念，昭格储灵。\n展矣礼典，薰然德馨。\n愔愔管磬，亦具是听。', NULL, 3),
    ('郊庙歌辞 享惠昭太子庙乐章 送文舞出迎武舞入', '孟简', '唐代', '喧喧金石容既缺，肃肃羽驾就行列。\n缑山遗响昔所闻，庙庭进旅今攸设。', NULL, 3),
    ('郊庙歌辞 享惠昭太子庙乐章 亚献终献', '裴度', '唐代', '重轮始发祥，齿胄方兴学。\n冥然升紫府，铿尔荐清乐。\n奠斝致馨香，在庭纷羽籥。\n礼成神既醉，髣髴缑山鹤。', NULL, 3),
    ('郊庙歌辞 享惠昭太子庙乐章 送神', '王涯', '唐代', '威仪毕陈，备乐将阕。\n苞茅酒缩，膋萧香彻。\n宫臣展事，肃雍在列。\n迎精送往，厥鉴昭晰。', NULL, 3),
    ('郊庙歌辞 武后崇先庙乐章', '不详', '唐代', '先德谦㧑冠昔，严规节素超今。\n奉国忠诚每竭，承家至孝纯深。\n追崇惧乖尊意，显号恐玷徽音。\n既迫王公屡请，方乃俯遂群心。\n有限无由展敬，奠醑每阙亲斟。\n大礼虔申典册，蘋藻敬荐翘襟。', NULL, 3),
    ('郊庙歌辞 褒德庙乐章 昭德', '不详', '唐代', '道赫梧宫，悲盈蒿里。\n爰赐徽烈，载敷嘉祀。\n享洽四时，规陈二簋。\n灵应昭格，神其戾止。', NULL, 3),
    ('郊庙歌辞 褒德庙乐章 进德', '不详', '唐代', '涂山懿戚，妫汭崇姻。\n祠筵肇启，祭典方申。\n礼以备物，乐以感神。\n用隆敦叙，载穆彝伦。', NULL, 3),
    ('郊庙歌辞 褒德庙乐章 褒德', '不详', '唐代', '家著累仁，门昭积善。\n瑶篚既列，金县式展。', NULL, 3),
    ('郊庙歌辞 褒德庙乐章 武舞作', '不详', '唐代', '昭昭竹殿开，奕奕兰宫启。\n懿范隆丹掖，殊荣辟朱邸。\n六佾荐徽容，三簋陈芳醴。\n万石覃贻厥，分珪崇祖祢。', NULL, 3),
    ('郊庙歌辞 褒德庙乐章 彰德', '不详', '唐代', '名隆五岳，秩映三台。\n严祠已备，[睟]影方回。', NULL, 3),
    ('舞曲歌辞 功成庆善乐舞词', '不详', '唐代', '寿丘惟旧迹，酆邑乃前基。\n粤余承累圣，悬弧亦在兹。\n弱龄逢运改，提剑郁匡时。\n指麾八荒定，怀柔万国夷。\n梯山盛入款，驾海亦来思。\n单于陪武帐，日逐卫文{木|囟/比}。\n端扆朝四岳，无为任百司。\n霜节明秋景，轻冰结水湄。\n芸黄遍原隰，禾颕积京坻。\n共乐还谯宴，欢此大风诗。', NULL, 3),
    ('舞曲歌辞 中和乐舞词', '不详', '唐代', '芳岁肇佳节，物华当仲春。\n乾坤既昭泰，烟景含氤氲。\n德浅荷玄贶，乐成思治人。\n前庭列钟鼓，广殿延群臣。\n八卦随舞意，五音转曲新。\n顾非咸池奏，庶协南风薰。\n式宴礼所重，浃欢情必均。\n同和谅在兹，万国希可亲。', NULL, 3),
    ('舞曲歌辞 凯乐歌辞 破阵乐', '不详', '唐代', '受律辞元首，相将讨叛臣。\n咸歌破阵乐，共赏太平人。', NULL, 3),
    ('舞曲歌辞 凯乐歌辞 应圣期', '不详', '唐代', '圣德期昌运，雍熙万宇清。\n乾坤资化育，海岳共休明。\n辟土欣耕稼，销戈遂偃兵。\n殊方歌帝泽，执贽贺升平。', NULL, 3),
    ('舞曲歌辞 凯乐歌辞 贺圣欢', '不详', '唐代', '四海皇风被，千年德水清。\n戎衣更不着，今日告功成。', NULL, 3),
    ('舞曲歌辞 凯乐歌辞 君臣同庆乐', '不详', '唐代', '主圣开昌历，臣忠奉大猷。\n君看偃革后，便是太平秋。', NULL, 3),
    ('郊庙歌辞 梁郊祀乐章 庆和 一', '赵光逢、张衮', '唐代', '就阳位，升圆丘。\n佩双玉，御大裘。\n膺天命，拥神休。\n万灵感，百禄遒。', NULL, 3),
    ('郊庙歌辞 梁郊祀乐章 庆和 二', '赵光逢、张衮', '唐代', '秉黄钺，建朱旗。\n震八表，清二仪。\n帝业显，王道夷。\n受景命，启皇基。', NULL, 3),
    ('郊庙歌辞 梁郊祀乐章 庆和 三', '赵光逢、张衮', '唐代', '开九门，怀百神。\n通肸蚃，接氤氲。\n明粢荐，广乐陈。\n奠嘉璧，燎芳薪。', NULL, 3),
    ('郊庙歌辞 梁郊祀乐章 庆和 四', '赵光逢、张衮', '唐代', '膺宝图，执左契。\n德应天，圣飨帝。\n荐表衷，荷灵惠。\n寿万年，祚百世。', NULL, 3),
    ('郊庙歌辞 梁郊祀乐章 庆和 五', '赵光逢、张衮', '唐代', '惟德动天，有感必通。\n秉兹一德，禋于六宗。\n钦夤宝命，恭肃礼容。\n来顾来享，永穆皇风。', NULL, 3),
    ('郊庙歌辞 梁郊祀乐章 庆和 六', '赵光逢、张衮', '唐代', '天惟佑德，辟乃奉天。\n交感斯在，昭事罔愆。\n岁功已就，王道无偏。\n于焉报本，是用告虔。', NULL, 3),
    ('郊庙歌辞 梁郊祀乐章 庆顺', '赵光逢、张衮', '唐代', '圣皇戾止，天步舒迟。\n乾乾睿相，穆穆皇仪。\n进退必肃，陟降是祗。\n六变克协，万灵协随。', NULL, 3),
    ('郊庙歌辞 梁郊祀乐章 庆平', '赵光逢、张衮', '唐代', '天命降鉴，帝德惟馨。\n享祀不忒，礼容孔明。\n奠璧布币，荐神献精。\n神佑以答，敷锡永宁。', NULL, 3),
    ('郊庙歌辞 梁郊祀乐章 庆肃', '赵光逢、张衮', '唐代', '笾豆簠簋，黍稷非馨。\n懿兹彝器，厥德惟明。\n金石匏革，以和以平。\n繇此无体，期乎永宁。', NULL, 3),
    ('郊庙歌辞 梁郊祀乐章 庆熙', '赵光逢、张衮', '唐代', '哲后躬享，旨酒斯陈。\n王恭无斁，严祀维夤。\n皇祖以配，大孝以振。\n宜锡景福，永休下民。', NULL, 3),
    ('郊庙歌辞 梁郊祀乐章 庆隆', '赵光逢、张衮', '唐代', '恭祀上帝，于国之阳。\n爵醴是荷，鸿基永昌。', NULL, 3),
    ('郊庙歌辞 梁郊祀乐章 庆融', '赵光逢、张衮', '唐代', '道和气兮袭氤氲，宣皇规兮彰圣神。\n服遐裔兮敷质文，格苗扈兮息烟尘。', NULL, 3),
    ('郊庙歌辞 梁郊祀乐章 庆休', '赵光逢、张衮', '唐代', '大业来四夷，仁风和万国。\n白日体无私，皇天辅有德。\n七旬罪已服，六月师方克。\n伟哉帝道隆，终始常作则。', NULL, 3),
    ('郊庙歌辞 梁郊祀乐章 庆和', '赵光逢、张衮', '唐代', '烟燎升，礼容彻。\n诚感达，人神悦。\n灵贶彰，圣情结。\n玉座寂，金炉歇。', NULL, 3),
    ('郊庙歌辞 周郊祀乐章 昭顺乐', '不详', '唐代', '五兵勿用，万国咸安。\n告功圆盖，受命云坛。\n乐鸣凤律，礼备鸡竿。\n神光欲降，众目遐观。', NULL, 3),
    ('郊庙歌辞 周郊祀乐章 治顺乐', '不详', '唐代', '羽卫离丹阙，金轩赴泰坛。\n珠旗明月色，玉佩晓霜寒。\n黼黻龙衣备，琮璜宝器完。\n百神将受职，宗社保长安。', NULL, 3),
    ('郊庙歌辞 周郊祀乐章 感顺乐', '不详', '唐代', '明君陈大礼，展币祀圆丘。\n雅乐声其发，祥云色正浮。', NULL, 3),
    ('郊庙歌辞 周郊祀乐章 禋顺乐', '不详', '唐代', '黄彝将献，特牲预迎。\n既修昭事，潜达明诚。', NULL, 3),
    ('郊庙歌辞 周郊祀乐章 福顺乐', '不详', '唐代', '相承五运，取法三才。\n大礼爰展，率土咸来。\n卿云秘室，甘泉宝台。\n象樽初酌，受福不回。', NULL, 3),
    ('郊庙歌辞 周郊祀乐章 忠顺乐', '不详', '唐代', '木铎敷音文德昌，朱干成列武功彰。\n雷鼗鹭羽今休用，玉戚相参正发扬。', NULL, 3),
    ('郊庙歌辞 周郊祀乐章 武舞乐', '不详', '唐代', '圭瓒方陈礼，干旄乃象功。\n成文非羽籥，猛势若罴熊。', NULL, 3),
    ('郊庙歌辞 梁太庙乐舞辞 开平舞', '不详', '唐代', '黍稷馨，醴{酉齐}清。\n牲牷洁，金石铿。\n恭祀事，结皇情。\n神来格，歌颂声。', NULL, 3),
    ('郊庙歌辞 梁太庙乐舞辞 皇帝行', '不详', '唐代', '莫高者天，攀跻弗克。\n隮天有方，累仁积德。\n祖宗隆之，子孙履之。\n配天明祀，永永孝思。', NULL, 3),
    ('郊庙歌辞 梁太庙乐舞辞 帝盥', '不详', '唐代', '庄肃莅事，周旋礼容。\n裸鬯严洁，穆穆雍雍。', NULL, 3),
    ('郊庙歌辞 梁太庙乐舞辞 登歌', '不详', '唐代', '于赫我皇，建中立极。\n动以武功，静以文德。\n昭事上帝，欢心万国。\n大报严禋，四海述职。', NULL, 3),
    ('郊庙歌辞 梁太庙乐舞辞 大合舞', '不详', '唐代', '于穆皇祖，濬哲雍熙。\n美溢中夏，化被南陲。\n后稷累德，公刘创基。\n肇兴九庙，乐合来仪。', NULL, 3),
    ('郊庙歌辞 梁太庙乐舞辞 象功舞', '不详', '唐代', '天地合德，睿圣昭彰。\n累赠太傅，俄登魏王。\n雄名不朽，奕叶而光。\n建国之兆，君临万方。', NULL, 3),
    ('郊庙歌辞 梁太庙乐舞辞 来仪舞', '不详', '唐代', '于赫帝命，应天顺人。\n亭育品汇，宾礼百神。\n洪基永固，景命惟新。\n肃恭孝享，祚我生民。', NULL, 3),
    ('郊庙歌辞 梁太庙乐舞辞 昭德舞', '不详', '唐代', '肃肃文考，源濬派长。\n汉称诞季，周实生昌。\n奄有四海，超彼百王。\n笙镛迭奏，礼物荧煌。', NULL, 3),
    ('郊庙歌辞 梁太庙乐舞辞 饮福', '不详', '唐代', '戛玉𪭢金永颂声，檿丝孤竹和且清。\n灵歆醉止牺象盈，自天降福千万龄。', NULL, 3),
    ('郊庙歌辞 梁太庙乐舞辞 撤豆', '不详', '唐代', '笙镛洋洋，庭燎煌煌。\n明星有烂，祝史下堂。\n[笾]豆斯撤，礼容有章。\n克勤克俭，无怠无荒。', NULL, 3),
    ('郊庙歌辞 梁太庙乐舞辞 送神', '不详', '唐代', '其降无从，其往无踪。\n黍稷非馨，有感必通。\n赫奕令德，髣髴睟容。\n再拜慌忽，遐想昊穹。', NULL, 3),
    ('郊庙歌辞 后唐宗庙乐舞辞 昭德舞', '崔居俭、卢文纪', '唐代', '懿彼明德，赫赫煌煌。\n名高阃域，功著旗常。\n道符休泰，运叶祺祥。\n庆传万祀，以播耿光。', NULL, 3),
    ('郊庙歌辞 后唐宗庙乐舞辞 文明舞', '崔居俭、卢文纪', '唐代', '帝业光扬，皇图翕赫。\n圣德孔彰，神功不测。\n信及豚鱼，恩霑动植。\n懿范鸿名，传之万亿。', NULL, 3),
    ('郊庙歌辞 后唐宗庙乐舞辞 应天舞', '崔居俭、卢文纪', '唐代', '晋国肇兴，雄图再固。\n黼黻帝道，金玉王度。\n皇天无亲，惟德是辅。\n载诞英明，永光圣祚。', NULL, 3),
    ('郊庙歌辞 后唐宗庙乐舞辞 永平舞', '崔居俭、卢文纪', '唐代', '庆传瓒祚，位正瑶图。\n功宣四海，化被八区。\n静彰帝道，动合乾符。\n千秋万祀，永荷昭苏。', NULL, 3),
    ('郊庙歌辞 后唐宗庙乐舞辞 武成舞', '崔居俭、卢文纪', '唐代', '艰难王业，返正皇唐。\n先天再造，却日重光。\n汉绍世祖，夏资少康。\n功成德茂，率祀无疆。', NULL, 3),
    ('郊庙歌辞 后唐宗庙乐舞辞 雍煕舞', '崔居俭、卢文纪', '唐代', '仁君御宇，寰海谧清。\n运符武德，道协文明。\n九成式叙，百度惟贞。\n金门积庆，玉叶传荣。', NULL, 3),
    ('郊庙歌辞 汉宗庙乐舞辞 武德舞', '张昭', '唐代', '明明我祖，天集休明。\n神母夜哭，彤云昼兴。\n笾豆有践，管籥斯登。\n孝孙致告，神其降灵。', NULL, 3),
    ('郊庙歌辞 汉宗庙乐舞辞 灵长舞', '张昭', '唐代', '天降祥，汉祚昌。\n火炎上，水灵长。\n建庙社，洁蒸尝。\n罗钟石，俨珩璜。\n陈玉豆，酌金觞。\n气昭感，德馨香。\n祗洛汭，瞻晋阳。\n降吾祖，福穰穰。', NULL, 3),
    ('郊庙歌辞 汉宗庙乐舞辞 积善舞', '张昭', '唐代', '黍稷斯馨，祖德惟明。\n蛇告赤帝，龟谋大横。\n云行雨施，天成地平。\n造我家邦，斡我璿衡。\n陶匏在御，醍盎惟精。\n或戛或击，载炮载烹。\n饮福受胙，舞降歌迎。\n滔滔不竭，洪惟水行。', NULL, 3),
    ('郊庙歌辞 汉宗庙乐舞辞 显仁舞', '张昭', '唐代', '运极金行谢，天资水德隆。\n礼神鄜畤馆，布政未央宫。\n诘旦备明祀，登歌荅茂功。\n云轩临降久，星俎荐陈丰。\n霭霭沈檀雾，铿锵环佩风。\n荧煌升藻藉，肸蚃转珠栊。\n尊祖咸韶备，贻孙书轨同。\n京坻长有积，宗社享无穷。', NULL, 3),
    ('郊庙歌辞 汉宗庙乐舞辞 章庆舞', '张昭', '唐代', '罘罳晓唱鸡人，三牲八簋斯陈。\n雾集瑶阶琐闼，香生绮席华茵。\n珠佩貂珰熠爚，羽旄干戚纷纶。\n酌鬯既终三献，凝旒何止千春。\n阿阁长栖彩凤，郊宫叠奏祥麟。\n赤伏英灵未泯，玄圭运祚重新。\n玉斝牺樽潋滟，龙旗凤辖逡巡。\n瞻望月游冠冕，犹疑苍野回轮。', NULL, 3),
    ('郊庙歌辞 汉宗庙乐舞辞 观德舞', '张昭', '唐代', '高庙明灵再启图，金根玉辂幸神都。\n巢阿丹凰衔书命，入昴飞星献宝符。\n正换熏弦娱赤子，忽登仙驾泣苍梧。\n荐樱鹤馆笳箫咽，酌鬯金楹剑佩趋。\n星俎云罍兼鲁礼，朱干象箾杂巴渝。\n氤氲龙麝交青琐，髣髴[锡]銮下蘂珠。\n荐豆奉觞亲玉几，配天合祖耀璿枢。\n受厘饮酒皇欢洽，仰俟余灵泰九区。', NULL, 3),
    ('郊庙歌辞 周宗庙乐舞辞 肃顺', '不详', '唐代', '我后至孝，祗谒祖先。\n仰瞻庙貌，夙设宫悬。\n朱弦疏越，羽舞回旋。\n神其来格，明祀惟虔。', NULL, 3),
    ('郊庙歌辞 周宗庙乐舞辞 治顺', '不详', '唐代', '清庙将入，衮服是依。\n载行载止，令行令仪。\n永终就养，空极孝思。\n瞻望如在，顾复长违。', NULL, 3),
    ('郊庙歌辞 周宗庙乐舞辞 肃雍舞', '不详', '唐代', '周道载兴，象日之明。\n万邦咸庆，百谷用成。\n于穆圣祖，祗荐鸿名。\n祀于庙社，陈其牺牲。\n进旅退旅，皇武之形。\n一倡三叹，朱弦之声。\n以妥以侑，既和且平。\n至诚潜达，介福攸宁。', NULL, 3),
    ('郊庙歌辞 周宗庙乐舞辞 章德舞', '不详', '唐代', '清庙新，展严禋。\n恭祖德，厚人伦。\n雅乐荐，礼器陈。\n俨皇尸，列虞宾。\n神如在，声不闻。\n享必信，貌惟夤。\n想龙服，奠牺樽。\n礼既备，庆来臻。', NULL, 3),
    ('郊庙歌辞 周宗庙乐舞辞 善庆舞', '不详', '唐代', '卜世长，帝祚昌。\n定中国，服四方。\n修明祀，从旧章。\n奏激楚，转清商。\n罗俎豆，列簪裳。\n歌累累，容皇皇。\n望来格，降休祥。\n祝敢告，寿无疆。', NULL, 3),
    ('郊庙歌辞 周宗庙乐舞辞 观成舞', '不详', '唐代', '穆穆王国，奕奕神功。\n毖祀载展，明德有融。\n彝樽斯满，簠簋斯丰。\n纷𫄨旄羽，锵洋磬钟。\n或升或降，克和克同。\n孔惠之礼，必肃之容。\n锡以纯嘏，祚其允恭。\n神保是飨，万世无穷。', NULL, 3),
    ('郊庙歌辞 周宗庙乐舞辞 明德舞', '不详', '唐代', '惟彼岐阳，德大流光。\n载造周室，泽及遐荒。\n于铄圣祖，上帝是皇。\n迺圣迺神，知微知彰。\n新庙奕奕，丰年穰穰。\n取彼血膋，以往蒸尝。\n黍稷惟馨，笾豆大房。\n工祝致告，受福无疆。', NULL, 3),
    ('郊庙歌辞 周宗庙乐舞辞 咸顺', '不详', '唐代', '万舞咸列，三阶克清。\n贯珠一倡，击石九成。\n盈觞虽酌，灵坐无形。\n永怀我祖，达其孝诚。', NULL, 3),
    ('郊庙歌辞 周宗庙乐舞辞 禋顺', '不详', '唐代', '旨酒既献，嘉殽乃迎。\n振其鼗鼓，洁以铏羹。\n肇禋肇祀，或炮或烹。\n皇尸俨若，保飨是明。', NULL, 3),
    ('郊庙歌辞 周宗庙乐舞辞 福顺', '不详', '唐代', '新庙奕奕，金奏洋洋。\n享于祖考，循彼典章。\n清酤特满，嘉玉腾光。\n神醉既告，帝祉无疆。', NULL, 3),
    ('郊庙歌辞 周宗庙乐舞辞 忠顺', '不详', '唐代', '称文既表温柔德，示武须成蹈厉容。\n缀兆疾舒皆应节，明明我祖乐无穷。', NULL, 3),
    ('郊庙歌辞  善胜舞', '不详', '唐代', '圣祖累功，福锺来裔。\n持羽执干，舞文不废。', NULL, 3),
    ('郊庙歌辞  禋顺', '不详', '唐代', '礼毕祀先，香散几筵。\n罢舞干戚，收撤豆笾。', NULL, 3),
    ('郊庙歌辞  肃顺', '不详', '唐代', '乐奏四顺，福受万年。\n神归碧天，庭余瑞烟。', NULL, 3),
    ('郊庙歌辞 晋朝飨乐章 初举酒文同乐', '不详', '唐代', '赫矣昌运，明哉圣王。\n文兴坠典，礼复旧章。\n鸳鸾济济，鸟兽跄跄。\n一人有庆，万福无疆。', NULL, 3),
    ('郊庙歌辞 晋朝飨乐章 举酒', '不详', '唐代', '大明御宇，至德动天。\n君臣庆会，礼乐昭宣。\n剑佩成列，金石在县。\n椒觞再献，宝历万年。', NULL, 3),
    ('郊庙歌辞 晋朝飨乐章 再举酒', '不详', '唐代', '朝野无事，寰瀛大康。\n圣人有作，盛礼重光。\n万国执玉，千官奉觞。\n南山永固，地久天长。', NULL, 3),
    ('郊庙歌辞 晋朝飨乐章 四举酒', '不详', '唐代', '八表欢无事，三秋贺有成。\n照临同日远，渥泽并云行。\n河变千年色，山呼万岁声。\n愿修封岱礼，方以称文明。', NULL, 3),
    ('郊庙歌辞 晋朝飨乐章 群臣酒行歌 一', '不详', '唐代', '剑佩俨如林，齐倾拱北心。\n渥恩颁美禄，咸濩听和音。\n一德君臣合，重瞳日月临。\n歌时兼乐圣，唯待赞泥金。', NULL, 3),
    ('郊庙歌辞 晋朝飨乐章 群臣酒行歌 二', '不详', '唐代', '万国咸归禹，千官共祝尧。\n拜恩瞻凤扆，倾耳听云韶。\n运启金行远，时和玉烛调。\n酒酣齐抃舞，同贺圣明朝。', NULL, 3),
    ('郊庙歌辞 晋朝飨乐章 群臣酒行歌 三', '不详', '唐代', '令节陈高会，群臣侍御筵。\n玉墀留爱景，金殿霭祥烟。\n振鹭涵天泽，灵禽下乐悬。\n圣明无一事，何处让尧年。', NULL, 3),
    ('郊庙歌辞 周朝飨乐章 忠顺', '不详', '唐代', '岁迎更始，节及朝元。\n冕旒仰止，冠剑相连。\n八音合奏，万物齐宣。\n常陈盛礼，愿永千年。', NULL, 3),
    ('郊庙歌辞 周朝飨乐章 治顺', '不详', '唐代', '庭陈大乐，作当太微。\n凝旒负扆，端拱垂衣。\n鸳鸾成列，簪组相辉。\n御炉香散，郁郁霏霏。', NULL, 3),
    ('郊庙歌辞 周朝飨乐章 福顺', '不详', '唐代', '圣皇端拱，多士输忠。\n蛮觞共献，臣心毕同。\n声齐嵩岳，祝比华封。\n千龄万祀，常保时雍。', NULL, 3),
    ('郊庙歌辞 周朝飨乐章 康顺', '不详', '唐代', '鸿钧广运，嘉节良辰。\n列辟在位，万国来宾。\n干旌屡舞，金石咸陈。\n礼容既备，帝履长春。', NULL, 3),
    ('郊庙歌辞 晋昭德成功舞歌 昭德舞歌二首 一', '不详', '唐代', '圣代修文德，明庭举旧章。\n两阶陈羽籥，万舞合宫商。\n剑佩森鸳鹭，箫韶下凤凰。\n我朝青史上，千古有辉光。', NULL, 3),
    ('郊庙歌辞 晋昭德成功舞歌 昭德舞歌二首 二', '不详', '唐代', '淮海干戈戢，朝廷礼乐施。\n白驹皆就絷，丹凤复来仪。\n德备三苗格，风行万国随。\n小臣同百兽，率舞贺昌期。', NULL, 3),
    ('郊庙歌辞 晋昭德成功舞歌 武功舞歌二首 一', '不详', '唐代', '拨乱资英主，开基自晋阳。\n一戎成大业，七德焕前王。\n炎汉提封远，姬周世祚长。\n朱干将玉戚，全象武功扬。', NULL, 3),
    ('郊庙歌辞 晋昭德成功舞歌 武功舞歌二首 二', '不详', '唐代', '睿算超前古，神功格上圆。\n百川留禹迹，万国戴尧天。\n既已櫜弓矢，诚宜播管弦。\n跄跄随鸟兽，共乐太平年。', NULL, 3),
    ('鼓吹曲辞 朱鹭', '张籍', '唐代', '翩翩兮朱鹭，来泛春塘栖绿树。\n羽毛如翦色如染，远飞欲下双翅敛。\n避人引子入深壍，动处水纹开滟滟。\n谁知豪家网尔躯，不如饮啄江海隅。', NULL, 3),
    ('鼓吹曲辞 艾如张', '李贺', '唐代', '锦襜褕，绣裆襦，强强饮啄哺尔雏。\n陇东卧穟满风雨，莫信龙媒陇西去。\n齐人织网如素空，张在野春平碧中。\n网丝漠漠无形影，误尔触之伤首红。\n艾叶绿花谁翦刻，中藏祸机不可测。', NULL, 3),
    ('鼓吹曲辞 上之回', '卢照邻', '唐代', '回中道路险，萧关烽候多。\n五营屯北地，万乘出西河。\n单于拜玉玺，天子按雕戈。\n振旅汾川曲，秋风横大歌。', NULL, 3),
    ('鼓吹曲辞 上之回', '李白', '唐代', '三十六离宫，楼台与天通。\n阁道步行月，美人愁烟空。\n恩疏宠不及，桃李伤春风。\n淫乐意何极，金舆向回中。\n万乘出黄道，千旗扬彩虹。\n前军细柳北，后骑甘泉东。\n岂问渭川老，宁邀襄野童。\n秋暮瑶池宴，归来乐未穷。', NULL, 3),
    ('鼓吹曲辞 上之回', '李贺', '唐代', '上之回，大旗喜。\n悬虹彗，挞凤尾。\n剑匣破，舞蛟龙。\n蚩尤死，鼓逢逢。\n天高庆雷齐坠地，地无惊烟海千里。', NULL, 3),
    ('鼓吹曲辞 战城南', '卢照邻', '唐代', '将军出紫塞，冒顿在乌贪。\n笳喧雁门北，阵翼龙城南。\n雕弓夜宛转，铁骑晓参潭。\n应须驻白日，为待战方酣。', NULL, 3),
    ('鼓吹曲辞 战城南', '李白', '唐代', '去年战桑干源，今年战葱河道。\n洗兵条支海上波，放马天山雪中草。\n万里长征战，三军尽衰老。\n匈奴以杀戮为耕作，古来唯见白骨黄沙田。\n秦家筑城备胡处，汉家还有烽火然。\n烽火然不息，征战无已时。\n野战格鬬死，败马号鸣向天悲。\n乌鸢啄人肠，衔飞上挂枯树枝。\n士卒涂草莽，将军空尔为。\n乃知兵者是凶器，圣人不得已而用之。', NULL, 3),
    ('鼓吹曲辞 战城南', '刘驾', '唐代', '城南征战多，城北无饥鸦。\n白骨马蹄下，谁言皆有家。\n城前水声苦，倏忽流万古。\n莫争城外地，城里有闲土。', NULL, 3),
    ('鼓吹曲辞 战城南二首 一', '僧贯休', '唐代', '万里桑干傍，茫茫古蕃壤。\n将军貌憔悴，抚剑悲年长。\n胡兵尚陵逼，久住亦非强。\n邯郸少年辈，个个有伎俩。\n拖枪半夜去，雪片大如掌。', NULL, 3),
    ('鼓吹曲辞 战城南二首 二', '僧贯休', '唐代', '碛中有阴兵，战马时惊蹶。\n轻猛李陵心，摧残苏武节。\n黄金锁子甲，风吹色如铁。\n十载不封侯，茫茫向谁说。', NULL, 3),
    ('鼓吹曲辞 巫山高', '郑世翼', '唐代', '巫山凌太清，岧峣类削成。\n霏霏暮雨合，霭霭朝云生。\n危峰入鸟道，深谷泻猿声。\n别有幽栖客，淹留攀桂情。', NULL, 3),
    ('鼓吹曲辞 巫山高二首 一', '沈佺期', '唐代', '巫山峰十二，环合隐昭回。\n俯眺琵琶峡，平看云雨台。\n古槎天外倚，瀑水日边来。\n何忽啼猿夜，荆王枕席开。', NULL, 3),
    ('鼓吹曲辞 巫山高二首 二', '沈佺期', '唐代', '神女向高唐，巫山下夕阳。\n裴回作行雨，婉娈逐荆王。\n电影江前落，雷声峡外长。\n霁云无处所，台馆晓苍苍。', NULL, 3),
    ('鼓吹曲辞 巫山高', '卢照邻', '唐代', '巫山望不极，望望下朝雰。\n莫辨啼猿树，徒看神女云。\n惊涛乱水脉，骤雨暗峰文。\n霑裳即此地，况复远思君。', NULL, 3),
    ('鼓吹曲辞 巫山高', '张循之', '唐代', '巫山高不极，沓沓状奇新。\n暗谷疑风雨，幽岩若鬼神。\n月明三峡曙，潮满二江春。\n为问阳台夕，应知入梦人。', NULL, 3),
    ('鼓吹曲辞 巫山高', '刘方平', '唐代', '楚国巫山秀，清猿日夜啼。\n万重春树合，十二碧峰齐。\n峡出朝云下，江来暮雨西。\n阳台归路直，不畏向家迷。', NULL, 3),
    ('鼓吹曲辞 巫山高', '皇甫冉', '唐代', '巫峡见巴东，迢迢半出空。\n云藏神女馆，雨到楚王宫。\n朝暮泉声落，寒暄树色同。\n清猿不可听，偏在九秋中。', NULL, 3),
    ('鼓吹曲辞 巫山高', '李端', '唐代', '巫山十二峰，皆在碧虚中。\n回合云藏日，霏微雨带风。\n猿声寒过水，树色暮连空。\n愁向高唐望，清秋见楚宫。', NULL, 3),
    ('鼓吹曲辞 巫山高', '于𣸣', '唐代', '何山无朝云，彼云亦悠扬。\n何山无暮雨，彼雨亦苍茫。\n宋玉恃才者，凭云构高唐。\n自重文赋名，荒淫归楚襄。\n峨峨十二峰，永作妖鬼乡。', NULL, 3),
    ('鼓吹曲辞 巫山高二首 一', '孟郊', '唐代', '巴山上峡重复重，阳台碧峭十二峰。\n荆王猎时逢暮雨，夜卧高丘梦神女。\n轻红流烟湿艳姿，行云飞去明星稀。\n目极魂断望不见，猿啼三声泪霑衣。', NULL, 3),
    ('鼓吹曲辞 巫山高二首 二', '孟郊', '唐代', '见尽数万里，不闻三声猿。\n但飞萧萧雨，中有亭亭魂。\n千载楚襄恨，遗文宋玉言。\n至今青冥里，云结深闺门。', NULL, 3),
    ('鼓吹曲辞 巫山高', '李贺', '唐代', '碧丛丛，高插天，大江翻澜神曳烟。\n楚魂寻梦风飔然，晓风飞雨生苔钱。\n瑶姬一去一千年，丁香笻竹啼老猿。\n古祠近月蟾桂寒，椒花坠红湿云间。', NULL, 3),
    ('鼓吹曲辞 巫山高', '僧齐己', '唐代', '巫山高，巫女妖。\n雨为暮兮云为朝，楚王憔悴魂欲销。\n秋猿嗥嗥日将夕，红霞紫烟凝老壁。\n千岩万壑花皆坼，但恐芳菲无正色。\n不知今古行人行，几人经此无秋情。\n云深庙远不可觅，十二峰头插天碧。', NULL, 3),
    ('鼓吹曲辞 将进酒', '李白', '唐代', '君不见黄河之水天上来，奔流到海不复回。\n君不见高堂明镜悲白发，朝如青丝暮成雪。\n人生得意须尽欢，莫使金尊空对月。\n天生我材必有用，千金散尽还复来。\n烹羊宰牛且为乐，会须一饮三百杯。\n岑夫子，丹丘生，将进酒，杯莫停。\n与君歌一曲，请君为我侧耳听。\n钟鼓馔玉不足贵，但愿长醉不复醒。\n古来圣贤皆寂寞，惟有饮者留其名。\n陈王昔时宴平乐，斗酒十千恣欢谑。\n主人何为言少钱，径须酤取对君酌。\n五花马，千金裘，呼儿将出换美酒，与尔同销万古愁。', NULL, 3),
    ('鼓吹曲辞 将进酒', '李贺', '唐代', '将进酒，将进酒。\n酒中有毒鸩主父，言之主父伤主母。\n母为妾地父妾天，仰天俯地不忍言。\n佯为僵踣主父前，主父不知加妾鞭。\n旁人知妾为主说，主将泪洗鞭头血。\n推摧主母牵下堂，扶妾遣升堂上床。\n将进酒，酒中无毒令主寿。\n愿主回思归主母，遣妾如此事主父。\n妾为此事人偶知，自惭不密方自悲。\n主今颠倒安置妾，贪天僭地谁不为。', NULL, 3),
    ('鼓吹曲辞 君马黄', '李白', '唐代', '君马黄，我马白。\n马色虽不同，人心本无隔。\n共作游冶盘，双行洛阳陌。\n长剑既照曜，高冠何赩赫。\n各有千金裘，俱为五侯客。\n猛虎落陷阱，壮夫时屈厄。\n相知在急难，独好亦何益。', NULL, 3),
    ('鼓吹曲辞 芳树', '沈佺期', '唐代', '何地早芳菲？宛在长门殿。\n夭桃色若绶，秾李光如练。\n啼鸟弄花疏，游蜂饮香遍。\n叹息春风起，飘零君不见。', NULL, 3),
    ('鼓吹曲辞 芳树', '卢照邻', '唐代', '芳树本多奇，年华复在斯。\n结翠成新幄，开红满旧枝。\n风归花历乱，日度影参差。\n容色朝朝落，思君君不知。', NULL, 3),
    ('鼓吹曲辞 芳树', '徐彦伯', '唐代', '玉花珍簟上，金缕画屏开。\n晓月怜筝柱，春风忆镜台。\n筝柱春风吹晓月，芳树落花朝暝歇。\n藳砧刀头未有时，攀条拭泪坐相思。', NULL, 3),
    ('鼓吹曲辞 芳树', '韦应物', '唐代', '迢迢芳园树，列映清池曲。\n对此伤人心，还如故时绿。\n风条洒余霭，露叶承新旭。\n佳人不再攀，下有往来躅。', NULL, 3),
    ('鼓吹曲辞 芳树', '元稹', '唐代', '芳树已寥落，孤英尤可嘉。\n可怜团团叶，盖覆深深花。\n游蜂竞攒刺，鬬雀亦纷拏。\n天生细碎物，不爱好光华。\n非无歼殄法，念尔有生涯。\n春雷一声发，惊燕亦惊蛇。\n清池养神蔡，已复长虾蟆。\n雨露贵平施，吾其春草芽。', NULL, 3),
    ('鼓吹曲辞 芳树', '罗隐', '唐代', '细蘂慢逐风，暖香闲破鼻。\n青帝固有心，时时动人意。\n去年高枝犹压地，今年低枝已憔悴。\n吾所以见造化之权，变通之理。\n春夏作头，秋冬为尾。\n循环反复无穷已，今生长短同一轨。\n若使威可以制，力可以止，秦皇不肯敛手下沙丘，孟贲不合低头入蒿里。\n伊人彊猛犹如此，顾我劳生何足恃？但愿开素袍，倾绿蚁，陶陶兀兀大醉于青冥白昼间。\n任他上是天，下是地。', NULL, 3),
    ('鼓吹曲辞 有所思', '沈佺期', '唐代', '君子事行役，再空芳岁期。\n美人旷延伫，万里浮云思。\n园槿绽红艳，郊桑柔绿滋。\n坐看长夏晚，秋月生罗帏。', NULL, 3),
    ('鼓吹曲辞 有所思', '李白', '唐代', '我思仙人，乃在碧海之东隅。\n海寒多天风，白波连山倒蓬壶。\n长鲸喷涌不可涉，抚心茫茫泪如珠。\n西来青鸟东飞去，愿寄一书谢麻姑。', NULL, 3),
    ('鼓吹曲辞 有所思', '孟郊', '唐代', '桔槔烽火昼不灭，客路迢迢信难越。\n古镇刀攒万片霜，寒江浪起千堆雪。\n此时西去定如何，空使南心远凄切。', NULL, 3),
    ('鼓吹曲辞 有所思', '卢仝', '唐代', '当时我醉美人家，美人颜色娇如花。\n今日美人弃我去，青楼珠箔天之涯。\n天涯娟娟常娥月，三五二八盈又缺。\n翠眉蝉鬓生别离，一望不见心断绝。\n心断绝，几千里，梦中醉卧巫山云。\n觉来泪滴湘江水，湘江两岸花木深。\n美人不见愁人心，含愁更奏绿绮琴，调高弦绝无知音。\n美人兮美人！不知为暮雨兮为朝云。\n相思一夜梅花发，忽到窗前疑是君。', NULL, 3),
    ('鼓吹曲辞 有所思', '韦应物', '唐代', '借问江上柳，青青为谁春。\n空游昨日地，不见昨日人。\n缭绕万家井，往来车马尘。\n莫道无相识，要非心所亲。', NULL, 3),
    ('鼓吹曲辞 有所思', '刘氏云', '唐代', '朝亦有所思，暮亦有所思。\n登楼望君处，蔼蔼浮云飞。\n浮云遮却阳关道，向晚谁知妾怀抱。\n玉井苍苔春院深，桐花落地无人扫。', NULL, 3),
    ('鼓吹曲辞 雉子班', '李白', '唐代', '辟邪伎作鼓吹惊，雉子班之奏曲成。\n喔咿振迅欲飞鸣，扇锦翼，雄风生。\n双雌同饮啄，趫悍谁能争。\n乍向草中耿介死，不求黄金笼下生。\n天地至广大，何惜遂物情。\n善卷让天子，务光亦逃名。\n所贵旷士怀，朗然合太清。', NULL, 3),
    ('鼓吹曲辞 临高台', '褚亮', '唐代', '高台暂俯临，飞翼耸轻音。\n浮光随日度，漾影逐波深。\n迥瞰周平野，开怀畅远襟。\n独此三休上，还伤千岁心。', NULL, 3),
    ('鼓吹曲辞 临高台', '王勃', '唐代', '临高台，高台迢递绝浮埃。\n瑶轩绮构何崔嵬，鸾歌凤吹清且哀。\n俯瞰长安道，萋萋御沟草。\n斜对甘泉路，苍苍茂陵树。\n高台四望同，帝乡佳气郁葱葱。\n紫阁丹楼纷照曜，璧房锦殿相玲珑。\n东弥长乐观，西指未央宫。\n赤城映朝日，绿树摇春风。\n旗亭百队开新市，甲第千甍分戚里。\n朱轮翠盖不胜春，叠树层楹相对起。\n复有青楼大道中，绣户文窗雕绮栊。\n锦衣昼不襞，罗帏夕未空。\n歌屏朝揜翠，妆镜晚窥红。\n为吾安宝髻，蛾眉罢花丛。\n狭路尘间黯将暮，云间月色明如素。\n鸳鸯池上两两飞，凤皇楼下双双度。\n物色正如此，佳期那不顾。\n银鞍绣毂盛繁华，可怜今夜宿倡家。\n倡家少妇不须嚬，东园桃李片时春。\n君看旧日高台处，柏梁铜雀生黄尘。', NULL, 3),
    ('鼓吹曲辞 临高台', '僧贯休', '唐代', '凉风吹远念，使我升高台。\n宁知数片云，不是旧山来。\n故人天一涯，久客殊未回。\n雁来不得书，空寄声哀哀。', NULL, 3),
    ('鼓吹曲辞 黄雀行', '庄南杰', '唐代', '穿屋穿墙不知止，争树争巢入营死。\n林间公子挟弹弓，一丸致毙花丛里。\n小雏黄口未有知，青天不解高高飞。\n虞人设网当要路，白日啾嘲祸万机。', NULL, 3),
    ('鼓吹曲辞 钓竿篇', '沈佺期', '唐代', '朝日敛红烟，垂竿向绿川。\n人疑天上坐，鱼似镜中悬。\n避檝时惊透，猜钩每误牵。\n湍危不理辖，潭静欲留船。\n钓玉君徒尚，征金我未贤。\n为看芳饵下，贪得会无全。', NULL, 3),
    ('鼓吹曲辞 凯歌六首 一', '岑参', '唐代', '汉将承恩西破戎，捷书先奏未央宫。\n天子预开麟阁待，祗今谁数贰师功。', NULL, 3),
    ('鼓吹曲辞 凯歌六首 二', '岑参', '唐代', '官军西出过楼兰，营幕傍临月窟寒。\n蒲海晓霜凝剑尾，葱山夜雪扑旌竿。', NULL, 3),
    ('鼓吹曲辞 凯歌六首 三', '岑参', '唐代', '鸣笳擂鼓拥回军，破国平蕃昔未闻。\n大夫鹊印摇边月，天将龙旗掣海云。', NULL, 3),
    ('鼓吹曲辞 凯歌六首 四', '岑参', '唐代', '月落辕门鼓角鸣，千群面缚出蕃城。\n洗兵鱼海云迎阵，秣马龙堆月照营。', NULL, 3),
    ('鼓吹曲辞 凯歌六首 五', '岑参', '唐代', '蕃军遥见汉家营，满谷连山遍哭声。\n万箭千刀一夜杀，平明流血浸空城。', NULL, 3),
    ('鼓吹曲辞 凯歌六首 六', '岑参', '唐代', '暮雨旌旗湿未干，胡尘白草日光寒。\n昨日将军连晓战，蕃军只见马空鞍。', NULL, 3),
    ('鼓吹曲辞 鼓吹铙歌 晋阳武', '柳宗元', '唐代', '晋阳武，奋义威。\n炀之渝，德焉归。\n氓毕屠，绥者谁。\n皇烈烈，专天机。\n号以仁，扬其旗。\n日之升，九土晞。\n斥田圻，流洪辉。\n有其二，翼余隋。\n斮枭骜，连熊螭。\n枯以肉，勍者羸。\n后土荡，玄穹弥。\n合之育，莽然施。\n惟德辅，庆无期。', NULL, 3),
    ('鼓吹曲辞 鼓吹铙歌 兽之穷', '柳宗元', '唐代', '兽之穷，奔大麓。\n天厚黄德，狙犷服。\n甲之櫜弓，弭矢箙。\n皇旅靖，敌逾蹙。\n自亡其徒，匪予戮。\n屈{虣/贝}猛，虔栗栗。\n縻以尺组，啖以秩。\n黎之阳，土茫茫。\n富兵戎，盈仓箱。\n乏者德，莫能享。\n驱豺兕，授我疆。', NULL, 3),
    ('鼓吹曲辞 鼓吹铙歌 战武牢', '柳宗元', '唐代', '战武牢，动河朔。\n逆之助，图掎角。\n怒鷇麛，抗乔岳。\n翘萌牙，傲霜雹。\n王谋内定，申掌握。\n铺施芟夷，二主缚。\n惮华戎，廓封略。\n命之瞢，卑以斮。\n归有德，唯先觉。', NULL, 3),
    ('鼓吹曲辞 鼓吹铙歌 泾水黄', '柳宗元', '唐代', '泾水黄，陇野茫。\n负太白，腾天狼。\n有鸟鸷立，羽翼张。\n钩喙决前，钜趯傍。\n怒飞饥啸，翾不可当。\n老雄死，子复良。\n巢岐饮渭，肆翱翔。\n顿地纮，提天纲。\n列缺掉帜，招摇耀铓。\n鬼神来助，梦嘉祥。\n脑涂原野，魂飞扬。\n星辰复，恢一方。', NULL, 3),
    ('鼓吹曲辞 鼓吹铙歌 奔鲸沛', '柳宗元', '唐代', '奔鲸沛，荡海垠。\n吐霓翳日，腥浮云。\n帝怒下顾，哀垫昏。\n[授]以神柄，推元臣。\n手援天矛，截修鳞。\n披攘蒙霿，开海门。\n地平水静，浮天垠。\n羲和显耀，乘清氛。\n赫炎溥畅，融大钧。', NULL, 3),
    ('鼓吹曲辞 鼓吹铙歌 苞枿', '柳宗元', '唐代', '苞枿{黑对}矣，惟根之蟠。\n弥巴蔽荆，负南极以安。\n曰我旧梁氏，辑绥艰难。\n江汉之阻，都邑固以完。\n圣人作，神武用。\n有臣勇智，奋不以众。\n投迹死地，谋猷纵。\n化敌为家，虑则中。\n浩浩海裔，不威而同。\n系缧降王，定厥功。\n澶漫万里，宣唐风。\n蛮夷九译，咸来从。\n凯旋金奏，象形容。\n震赫万国，罔不龚。', NULL, 3),
    ('鼓吹曲辞 鼓吹铙歌 河右平', '柳宗元', '唐代', '河右澶漫，顽为之魁。\n王师如雷震，昆仑以颓。\n上聋下聪，骜不可回。\n助雠抗有德，惟人之灾。\n乃溃乃奋，执缚归厥命。\n万室蒙其仁，一夫则病。\n濡以鸿泽，皇之圣。\n威畏德怀，功以定。\n顺之于理，物咸遂厥性。', NULL, 3),
    ('鼓吹曲辞 鼓吹铙歌 铁山碎', '柳宗元', '唐代', '铁山碎，大漠舒。\n二虏劲，连穹庐。\n背北海，专坤隅。\n岁来侵边，或傅于都。\n天子命元帅，奋其雄图。\n破定襄，降魁渠。\n穷竟窟宅，斥余吾。\n百蛮破胆，边氓苏。\n威武辉耀，明鬼区。\n利泽弥万祀，功不可逾。\n官臣拜手，惟帝之谟。', NULL, 3),
    ('鼓吹曲辞 鼓吹铙歌 靖本邦', '柳宗元', '唐代', '本邦伊晋，惟时不靖。\n根柢之摇，枝叶攸病。\n守臣不任，勚于神圣。\n惟钺之兴，翦焉则定。\n洪惟我理，式和以敬。\n群顽既夷，庶绩咸正。\n皇谟载大，惟人之庆。', NULL, 3),
    ('鼓吹曲辞 鼓吹铙歌 吐谷浑', '柳宗元', '唐代', '吐谷浑盛彊，背西海以夸。\n岁侵扰我疆，退匿险且遐。\n帝谓神武师，往征靖皇家。\n烈烈斾其旗，熊虎杂龙蛇。\n王旅千万人，衔枚默无哗。\n束刃逾山徼，张翼纵漠沙。\n一举刈膻腥，尸骸积如麻。\n除恶务本根，况敢遗萌芽。\n洋洋西海水，威命穷天涯。\n系虏来王都，犒乐穷休嘉。\n登高望还师，竟野如春华。\n行者靡不归，亲戚讙要遮。\n凯旋献清庙，万国思无邪。', NULL, 3),
    ('鼓吹曲辞 鼓吹铙歌 高昌', '柳宗元', '唐代', '麹氏雄西北，别绝臣外区。\n既恃远且险，纵傲不我虞。\n烈烈王者师，熊螭以为徒。\n龙旗翻海浪，驲骑驰坤隅。\n贲育搏婴儿，一扫不复余。\n平沙际天极，但见黄云驱。\n臣靖执长缨，智勇伏囚拘。\n文皇南面坐，夷狄千群趋。\n咸称天子神，往古不得俱。\n献号天可汗，以覆我国都。\n兵戎不交害，各保性与躯。', NULL, 3),
    ('鼓吹曲辞 鼓吹铙歌 东蛮', '柳宗元', '唐代', '东蛮有谢氏，冠带理海中。\n自言我异世，虽圣莫能通。\n王卒如飞翰，鹏鶱骇群龙。\n轰然自天坠，乃信神武功。\n系虏君臣人，累累来自东。\n无思不服从，唐业如山崇。\n百辟拜稽首，咸愿图形容。\n如周王会书，永永传无穷。\n睢盱万状乖，咿嗢九译重。\n广轮抚四海，浩浩知皇风。\n歌诗铙鼓间，以壮我元戎。', NULL, 3),
    ('横吹曲辞 陇头', '张籍', '唐代', '陇头已断人不行，胡骑夜入凉州城。\n汉家处处格鬬死，一朝尽没陇西地。\n驱我边人胡中去，散放牛羊食禾黍。\n去年中国养子孙，今著毡裘学胡语。\n谁能更使李轻车，收取凉州属汉家。', NULL, 3),
    ('横吹曲辞 陇头吟', '王维', '唐代', '长安少年游侠客，夜上戍楼看太白。\n陇头明月迥临关，陇上行人夜吹笛。\n关西老将不胜愁，驻马听之双泪流。\n身经大小百余战，麾下偏裨万户侯。\n苏武才为典属国，节旄空尽海西头。', NULL, 3),
    ('横吹曲辞 陇头吟', '翁绶', '唐代', '陇水潺湲陇树黄，征人陇上尽思乡。\n马嘶斜日朔风急，雁过寒云边思长。\n残月出林明剑戟，平沙隔水见牛羊。\n横行俱足封侯者，谁斩楼兰献未央。', NULL, 3),
    ('横吹曲辞 陇头水', '杨师道', '唐代', '陇头秋月明，陇水带关城。\n笳添离别曲，风送断肠声。\n映雪峰犹暗，乘冰马屡惊。\n雾中寒雁至，沙上转蓬轻。\n天山传羽檄，汉地急征兵。\n阵开都护道，剑聚伏波营。\n于兹觉无度，方共濯胡缨。', NULL, 3),
    ('横吹曲辞 陇头水', '卢照邻', '唐代', '陇坂高无极，征人一望乡。\n关河别去水，沙塞断归肠。\n马系千年树，旌悬九月霜。\n从来共呜咽，皆是为勤王。', NULL, 3),
    ('横吹曲辞 陇头水', '王建', '唐代', '陇水何年陇头别，不在山中亦呜咽。\n征人塞耳马不行，未到陇头闻水声。\n谓是西流入蒲海，还闻北海绕龙城。\n陇东陇西多屈曲，野麋饮水长簇簇。\n胡兵夜回水傍住，忆著来时磨剑处。\n向前无井复无泉，放马回看陇头树。', NULL, 3),
    ('横吹曲辞 陇头水', '于𣸣', '唐代', '借问陇头水，终年恨何事。\n深疑呜咽声，中有征人泪。\n昨日上山下，达曙不能寐。\n何处接长波？东流入清渭。', NULL, 3),
    ('横吹曲辞 陇头水 一', '僧皎然', '唐代', '陇头心欲绝，陇水不堪闻。\n碎影摇枪垒，寒声咽帐军。\n素从盐海积，绿带柳城分。\n日落天边望，逶迤入塞云。', NULL, 3),
    ('横吹曲辞 陇头水 二', '僧皎然', '唐代', '秦陇逼氐羌，征人去未央。\n如何幽咽水，幷欲断君肠。\n西注悲穷漠，东分忆故乡。\n旅魂声搅乱，无梦到辽阳。', NULL, 3),
    ('横吹曲辞 陇头水', '鲍溶', '唐代', '陇头水，千古不堪闻。\n生归苏属国，死别李将军。\n细响风凋草，清哀雁落云。', NULL, 3),
    ('横吹曲辞 陇头水', '罗隐', '唐代', '借问陇头水，年年恨何事。\n全疑呜咽声，中有征人泪。\n自古无长策，况我非深智。\n何计谢潺湲，一宵空不寐。', NULL, 3),
    ('横吹曲辞 出关', '魏徵', '唐代', '中原还逐鹿，投笔事戎轩。\n纵横计不就，慷慨志犹存。\n策杖谒天子，驱马出关门。\n请缨羁南越，凭轼下东藩。\n郁纡陟高岫，出没望平原。\n古木吟寒鸟，空山啼夜猿。\n既伤千里目，还惊九折魂。\n岂不惮艰险，深怀国士恩。\n季布无二诺，侯嬴重一言。\n人生感意气，功名谁复论。', NULL, 3),
    ('横吹曲辞 入关', '贾驰', '唐代', '河上微风来，关头树初湿。\n今朝关城吏，又见孤客入。\n上国谁与期，西来徒自急。', NULL, 3),
    ('横吹曲辞 入关', '张祜', '唐代', '都城连百二，雄险北回环。\n地势遥尊岳，河流侧让关。\n秦皇曾虎视，汉祖亦龙颜。\n何事枭凶辈，干戈自不闲。', NULL, 3),
    ('横吹曲辞 出塞', '窦威', '唐代', '匈奴屡不平，汉将欲纵横。\n看云方结阵，却月始连营。\n潜军度马邑，扬斾掩龙城。\n会勒燕然石，方传车骑名。', NULL, 3),
    ('横吹曲辞 出塞', '陈子昂', '唐代', '忽闻天上将，关塞重横行。\n始返楼兰国，还向朔方城。\n黄金装战马，白羽集神兵。\n星月开天阵，山川列地营。\n晚风吹画角，春色耀飞旌。\n宁知班定远，独是一书生。', NULL, 3),
    ('横吹曲辞 出塞', '张易之', '唐代', '侠客重恩光，骢马饰金装。\n瞥闻传羽檄，驰突救边荒。\n转战磨筓地，横行戴斗乡。\n将军占太白，小妇怨流黄。\n騕袅青丝骑，娉婷红粉妆。\n一春莺度曲，八月雁成行。\n谁堪坐秋思，罗袖拂空床。', NULL, 3),
    ('横吹曲辞 出塞', '沈佺期', '唐代', '十年通大漠，万里出长平。\n寒日生戈剑，阴云摇斾旌。\n饥乌啼旧垒，疲马恋空城。\n辛苦[皋兰]北，胡霜损汉兵。', NULL, 3),
    ('横吹曲辞 出塞', '王维', '唐代', '居延城外猎天骄，白草连天野火烧。\n暮云空碛时驱马，秋日平原好射雕。\n护羌校尉朝乘障，破虏将军夜渡辽。\n玉靶角弓珠勒马，汉家将赐霍嫖姚。', NULL, 3),
    ('横吹曲辞 出塞 一', '王昌龄', '唐代', '秦时明月汉时关，万里长征人未还。\n但使龙城飞将在，不教胡马度阴山。', NULL, 3),
    ('横吹曲辞 出塞 二', '王昌龄', '唐代', '白花垣上望京师，黄河水流无尽时。\n穷秋旷野行人绝，马首东来知是谁。', NULL, 3),
    ('横吹曲辞 出塞', '马戴', '唐代', '金带连环束战袍，马头冲雪度临洮。\n卷旗夜劫单于帐，乱斫胡兵缺宝刀。', NULL, 3),
    ('横吹曲辞 前出塞九首 一', '杜甫', '唐代', '戚戚去故里，悠悠赴交河。\n公家有程期，亡命婴祸罗。\n君已富土境，开边一何多？弃绝父母恩，吞声行负戈。', NULL, 3),
    ('横吹曲辞 前出塞九首 二', '杜甫', '唐代', '出门日已远，不受徒旅欺。\n骨肉恩岂断，男儿死无时。\n走马脱辔头，手中挑青丝。\n捷下万仞冈，俯身试搴旗。', NULL, 3),
    ('横吹曲辞 前出塞九首 三', '杜甫', '唐代', '磨刀呜咽水，水赤刃伤手。\n欲轻肠断声，心绪乱已久。\n丈夫誓许国，愤惋复何有？功名图麒麟，战骨当速朽。', NULL, 3),
    ('横吹曲辞 前出塞九首 四', '杜甫', '唐代', '送徒既有长，远戍亦有身。\n生死向前去，不劳吏怒嗔。\n路逢相识人，附书与六亲。\n哀哉两决绝，不复同苦辛。', NULL, 3),
    ('横吹曲辞 前出塞九首 五', '杜甫', '唐代', '迢迢万余里，领我赴三军。\n军中异苦乐，主将宁尽闻。\n隔河见胡骑，倏忽数百群。\n我始为奴仆，几时树功勋。', NULL, 3),
    ('横吹曲辞 前出塞九首 六', '杜甫', '唐代', '挽弓当挽彊，用箭当用长。\n射人先射马，擒贼先擒王。\n杀人亦有限，列国自有疆。\n茍能制侵陵，岂在多杀伤。', NULL, 3),
    ('横吹曲辞 前出塞九首 七', '杜甫', '唐代', '驱马天雨雪，军行入高山。\n迳危抱寒石，指落曾冰间。\n已去汉月远，何时筑城还？浮云暮南征，可望不可攀。', NULL, 3),
    ('横吹曲辞 前出塞九首 八', '杜甫', '唐代', '单于寇我垒，百里风尘昏。\n雄剑四五动，彼军为我奔。\n虏其名王归，系颈授辕门。\n潜身备行列，一胜何足论。', NULL, 3),
    ('横吹曲辞 前出塞九首 九', '杜甫', '唐代', '从军十年余，能无分寸功。\n众人贵茍得，欲语羞雷同。\n中原有鬬争，况在狄与戎。\n丈夫四方志，安可辞固穷。', NULL, 3),
    ('横吹曲辞 后出塞五首 一', '杜甫', '唐代', '男儿生世间，及壮当封侯。\n战伐有功业，焉能守旧丘。\n召募赴蓟门，军动不可留。\n千金买马鞭，百金装刀头。\n闾里送我行，亲戚拥道周。\n斑白居上列，酒酣进庶羞。\n少年别有赠，含笑看吴钩。', NULL, 3),
    ('横吹曲辞 后出塞五首 二', '杜甫', '唐代', '朝进东门营，暮上河阳桥。\n落日照大旗，马鸣风萧萧。\n平沙列万幕，部伍各见招。\n中天悬明月，令严夜寂寥。\n悲笳数声动，壮士惨不骄。\n借问大将谁，恐是霍嫖姚。', NULL, 3),
    ('横吹曲辞 后出塞五首 三', '杜甫', '唐代', '古人重守边，今人重高勋。\n岂知英雄主，出师亘长云。\n六合已一家，四夷且孤军。\n遂使貔虎士，奋身勇所闻。\n拔剑击大荒，日牧胡马群。\n誓开玄冥北，持以奉吾君。', NULL, 3),
    ('横吹曲辞 后出塞五首 四', '杜甫', '唐代', '献凯日继踵，两蕃静无虞。\n渔阳豪侠地，击鼓吹笙竽。\n云帆转辽海，粳稻来东吴。\n越罗与楚练，照耀舆台躯。\n主将位益崇，气骄陵上都。\n边人不敢议，议者死路衢。', NULL, 3),
    ('横吹曲辞 后出塞五首 五', '杜甫', '唐代', '我本良家子，出师亦多门。\n将骄益愁思，身贵不足论。\n跃马二十年，恐辜明主恩。\n坐见幽州骑，长驱河洛昏。\n中夜间道归，故里但空村。\n恶名幸脱免，穷老无儿孙。', NULL, 3),
    ('横吹曲辞 出塞', '皇甫冉', '唐代', '吹角出塞门，前瞻即胡地。\n三军尽回首，皆洒望乡泪。\n转念关山长，行看风景异。\n由来征戍客，各负轻生义。', NULL, 3),
    ('横吹曲辞 出塞', '王之涣', '唐代', '黄砂直上白云间，一片孤城万仞山。\n羌笛何须怨杨柳，春风不度玉门关。', NULL, 3),
    ('横吹曲辞 出塞', '耿[𣲗]', '唐代', '汉家边事重，窦宪出临戎。\n绝漠秋山在，阳关旧路通。\n列营依茂草，吹角向高风。\n更就燕然石，看铭破虏功。', NULL, 3),
    ('横吹曲辞 出塞', '张籍', '唐代', '秋塞雪初下，将军远出师。\n分营长记火，放马不收旗。\n月冷边帐湿，沙昏夜探迟。\n征人皆白首，谁见灭胡时。', NULL, 3),
    ('横吹曲辞 出塞', '刘驾', '唐代', '胡风不开花，四气多作雪。\n北人尚冻死，况我本南越。\n古来犬羊地，巡狩无遗辙。\n九土耕不尽，武皇犹征伐。\n中天有高阁，图画何时歇？坐恐塞上山，低于砂中骨。', NULL, 3),
    ('横吹曲辞 出塞曲', '刘济', '唐代', '将军在重围，音信绝不通。\n羽书如流星，飞人甘泉宫。\n倚是幷州儿，少年心胆雄。\n一朝随召募，百战争王公。\n去年桑干北，今年桑乾东。\n死是征人死，功是将军功。\n汗马牧秋月，疲兵卧霜风。\n仍闻左贤王，更欲图云中。', NULL, 3),
    ('横吹曲辞 出塞曲 一', '于鹄', '唐代', '微雪将军出，吹笳天未明。\n观兵登古戍，斩将对双旌。\n分阵瞻山势，潜军制马鸣。\n如今新史上，已有灭胡名。', NULL, 3),
    ('横吹曲辞 出塞曲 二', '于鹄', '唐代', '单于骄爱猎，放火到军城。\n待月调新弩，防秋置远营。\n空山朱戟影，寒碛铁衣声。\n逢著降胡说，阴山有伏兵。', NULL, 3),
    ('横吹曲辞 出塞曲 一', '僧贯休', '唐代', '埽尽狂胡迹，回戈望故关。\n相逢唯死鬬，岂易得生还。\n纵宴参胡乐，收兵过雪山。\n不封十万户，此事亦应闲。', NULL, 3),
    ('横吹曲辞 出塞曲 二', '僧贯休', '唐代', '玉帐将军意，殷勤把酒论。\n功高宁在我，阵没与招魂。\n塞色干戈束，军容喜气屯。\n男儿今始是，敢出玉关门。', NULL, 3),
    ('横吹曲辞 出塞曲 三', '僧贯休', '唐代', '回首陇山头，连天草木秋。\n圣君应入梦，半路遣封侯。\n水不担阴雪，柴令倒戍楼。\n归来麟阁上，春色满皇州。', NULL, 3),
    ('横吹曲辞 入塞', '刘希夷', '唐代', '将军陷虏围，边务息戎机。\n霜雪交河尽，旌旗入塞飞。\n晓光随马度，春色伴人归。\n课绩朝明主，临轩拜武威。', NULL, 3),
    ('横吹曲辞 入塞曲', '耿[𣲗]', '唐代', '将军带十围，重锦制戎衣。\n猿臂销弓力，虬须长剑威。\n首登平乐宴，新破大宛归。\n楼上姝姬笑，门前问客稀。\n暮烽玄菟急，秋草紫骝肥。\n未奉君主诏，高槐昼掩扉。', NULL, 3),
    ('横吹曲辞 入塞曲 一', '僧贯休', '唐代', '单于烽火动，都护去天涯。\n别赐黄金甲，亲临白玉墀。\n塞垣须静谧，师旅审安危。\n定远条支宠，如今胜古时。', NULL, 3),
    ('横吹曲辞 入塞曲 二', '僧贯休', '唐代', '方见将军贵，分明对冕旒。\n圣恩如远被，狂虏不难收。\n臣节唯期死，功勋敢望侯。\n终辞修里第，从此出皇州。', NULL, 3),
    ('横吹曲辞 入塞曲 三', '僧贯休', '唐代', '百里精兵动，参差便渡辽。\n如何好白日，亦照此天骄。\n远树深疑贼，惊蓬迥似雕。\n凯歌何日唱？碛路共天遥。', NULL, 3),
    ('横吹曲辞 入塞曲 一', '沈彬', '唐代', '欲为皇王服远戎，万人金甲鼓鼙中。\n阵云暗塞三边黑，兵血愁天一片红。\n半夜翻营旗搅月，深秋防戍剑磨风。\n谤书未及明君𦶟，卧骨将军已殁功。', NULL, 3),
    ('横吹曲辞 入塞曲 二', '沈彬', '唐代', '苦战沙间卧箭痕，戍楼闲上望星文。\n生希国泽分偏将，死夺河源荅圣君。\n鸢覰败兵眠白草，马惊边鬼哭阴云。\n功多地远无人纪，汉阁笙歌日又曛。', NULL, 3),
    ('横吹曲辞 折杨柳', '卢照邻', '唐代', '倡楼启曙扉，园柳正依依。\n鸟鸣知岁隔，条变识春归。\n露叶疑啼脸，风花乱舞衣。\n攀折聊将寄，军中书信稀。', NULL, 3),
    ('横吹曲辞 折杨柳', '沈佺期', '唐代', '玉窗朝日映，罗帐春风吹。\n拭泪攀杨柳，长条宛地垂。\n白花飞历乱，黄鸟思参差。\n妾自肝肠断，旁人那得知。', NULL, 3),
    ('横吹曲辞 折杨柳', '乔知之', '唐代', '可怜濯濯春杨柳，攀折将来就纤手。\n妾容与此同盛衰，何必君恩独能久。', NULL, 3),
    ('横吹曲辞 折杨柳', '刘宪', '唐代', '沙塞三河道，金闺二月春。\n碧烟杨柳色，红粉绮罗人。\n露叶怜啼脸，风花思舞巾。\n攀持君不见，为听曲中新。', NULL, 3),
    ('横吹曲辞 折杨柳', '崔湜', '唐代', '二月风光半，三边戍不还。\n年华妾自惜，杨柳为君攀。\n落絮缘衫袖，垂条拂髻鬟。\n那堪音信断，流涕望阳关。', NULL, 3),
    ('横吹曲辞 折杨柳', '韦承庆', '唐代', '万里边城地，三春杨柳节。\n叶似镜中眉，花如关外雪。\n征人远乡思，倡妇高楼别。\n不忍掷年华，含情寄攀折。', NULL, 3),
    ('横吹曲辞 折杨柳', '欧阳瑾', '唐代', '垂柳拂妆台，葳蕤叶半开。\n年华枝上见，边思曲中来。\n嫩色宜新雨，轻花伴落梅。\n朝朝倦攀折，征戍几时回。', NULL, 3),
    ('横吹曲辞 折杨柳', '张祜', '唐代', '红粉青楼曙，垂杨仲月春。\n怀君重攀折，非妾妒腰身。\n舞带萦丝断，娇娥向叶嚬。\n横吹凡几曲，独自最愁人。', NULL, 3),
    ('横吹曲辞 折杨柳', '张九龄', '唐代', '纤纤折杨柳，持此寄情人。\n一枝何足贵，怜是故园春。\n迟景那能久，流芳不及新。\n更愁征戍客，鬓老边城尘。', NULL, 3),
    ('横吹曲辞 折杨柳', '余延寿', '唐代', '大道连国门，东西种杨柳。\n葳蕤君不见，袅娜垂来久。\n缘枝栖暝禽，雄去雌独吟。\n余花怨春尽，微月起秋阴。\n坐望窗中蝶，起攀枝上叶。\n好风吹长条，妸娜何如妾。\n妾见柳园新，高楼四五春。\n莫吹胡塞曲，愁杀陇头人。', NULL, 3),
    ('横吹曲辞 折杨柳', '李白', '唐代', '垂杨拂绿水，摇艳东风年。\n花明玉关雪，叶暖金窗烟。\n美人结长恨，相对心凄然。\n攀条折春色，远寄龙庭前。', NULL, 3),
    ('横吹曲辞 折杨柳 一', '孟郊', '唐代', '杨柳多短枝，短枝多别离。\n赠远累攀折，柔条安得垂。\n青春有定节，离别无定时。\n但恐人别促，不怨来迟迟。\n莫言短枝条，中有长相思。\n朱颜与绿杨，幷在别离期。', NULL, 3),
    ('横吹曲辞 折杨柳 二', '孟郊', '唐代', '楼上春风过，风前杨柳歌。\n枝疏缘别苦，曲怨为年多。\n花惊燕地雪，叶映楚池波。\n谁堪别离此，征戍在交河。', NULL, 3),
    ('横吹曲辞 折杨柳', '李端', '唐代', '东城攀柳叶，柳叶低着草。\n少壮莫轻年，轻年有人老。\n柳发遍川岗，登高堪断肠。\n雨烟轻漠漠，何树近君乡？赠君折杨柳，颜色岂能久。\n上客莫霑巾，佳人正回首。\n新柳送君行，古柳伤君情。\n突兀临荒渡，婆娑出旧营。\n隋家两岸尽，陶宅五株平。\n日暮偏愁望，春山有鸟声。', NULL, 3),
    ('横吹曲辞 折杨柳', '翁绶', '唐代', '紫陌金堤映绮罗，游人处处动离歌。\n阴移古戍迷荒草，花带残阳落远波。\n台上少年吹白雪，楼中思妇敛青蛾。\n殷勤攀折赠行客，此去关山雨雪多。', NULL, 3),
    ('横吹曲辞 望行人', '王建', '唐代', '自从江树秋，日日上江楼。\n梦见离珠浦，书来在桂州。\n不同鱼比目，终恨水分流。\n久不开明镜，多应是白头。', NULL, 3),
    ('横吹曲辞 望行人', '张籍', '唐代', '秋风窗下起，旅雁向南飞。\n日日出门望，家家行客归。\n无因见边使，空待寄寒衣。\n独闭青楼暮，烟深鸟雀稀。', NULL, 3),
    ('横吹曲辞 关山月', '卢照邻', '唐代', '塞垣通碣石，虏障抵祈连。\n相思在万里，明月正孤悬。\n影移金岫北，光断玉门前。\n寄书谢中妇，时看鸿雁天。', NULL, 3),
    ('横吹曲辞 关山月', '沈佺期', '唐代', '汉月生辽海，曈昽出半晖。\n合昏玄兔郡，中夜白登围。\n晕落关山迥，光合霜霰微。\n将军听晓角，战马欲南归。', NULL, 3),
    ('横吹曲辞 关山月', '李白', '唐代', '明月出天山，苍茫云海间。\n长风几万里，吹度玉门关。\n汉下白登道，胡窥青海湾。\n由来征战地，不见有人还。\n戍客望边色，思归多苦颜。\n高楼当此夜，叹息未应闲。', NULL, 3),
    ('横吹曲辞 关山月', '长孙佐辅', '唐代', '凄凄还切切，戍客多离别。\n何处最伤心？关山见秋月。\n关月竟如何？由来远近过。\n始经玄兔塞，终绕白狼河。\n忽忆秦楼妇，流光应共有。\n已得并蛾眉，还知揽纤手。\n去岁照同行，比翼复连形。\n今宵照独立，顾影自茕茕。\n余晖渐西落，夜夜看如昨。\n借问映旌旗，何如鉴帷幕。\n拂晓朔风悲，蓬惊雁不飞。\n几时征戍罢，还向月中归。', NULL, 3),
    ('横吹曲辞 关山月', '耿[𣲗]', '唐代', '月明边徼静，戍客望乡时。\n塞古柳衰尽，关寒榆发迟。\n苍苍万里道，戚戚十年悲。\n今夜青楼上，还应照所思。', NULL, 3),
    ('横吹曲辞 关山月二首 一', '戴叔伦', '唐代', '月出照关山，秋风人未还。\n清光无远近，乡泪半书间。', NULL, 3),
    ('横吹曲辞 关山月二首 二', '戴叔伦', '唐代', '一雁过连营，繁霜覆古城。\n胡笳在何处？半夜起边声。', NULL, 3),
    ('横吹曲辞 关山月', '崔融', '唐代', '月生西海上，气逐边风壮。\n万里度关山，苍茫非一状。\n汉兵开郡国，胡马窥亭障。\n夜夜闻悲笳，征人起南望。', NULL, 3),
    ('横吹曲辞 关山月', '李端', '唐代', '露湿月苍苍，关头榆叶黄。\n回轮照海远，分彩上楼长。\n水冻频移幕，兵疲数望乡。\n祗应城影外，万里共如霜。', NULL, 3),
    ('横吹曲辞 关山月', '王建', '唐代', '关山月，营开道白前军发。\n冻轮当碛光悠悠，照见三堆两堆骨。\n边风割面天欲明，金莎岭西看看没。', NULL, 3),
    ('横吹曲辞 关山月', '张籍', '唐代', '秋月朗朗关山上，山中行人马蹄响。\n关山秋来雨雪多，行人见月唱边歌。\n海边漠漠天气白，胡儿夜度黄龙碛。\n军中探骑暮出城，伏兵暗处低旌戟。\n溪水连地霜草平，野𫘞寻水碛中鸣。\n陇头风急雁不下，沙场苦战多流星。\n可怜万国关山道，年年战骨多秋草。', NULL, 3),
    ('横吹曲辞 关山月', '翁绶', '唐代', '裴回汉月满边州，照尽天涯到陇头。\n影转银河寰海静，光分玉塞古今愁。\n笳吹远戍孤烽灭，雁下平沙万里秋。\n况是故园摇落夜，那堪少妇独登楼。', NULL, 3),
    ('横吹曲辞 关山月', '鲍氏君[徽]微', '唐代', '高高秋月明，北照辽阳城。\n塞迥光初满，风多晕更生。\n征人望乡思，战马闻鞞惊。\n朔风悲边草，胡沙昏虏营。\n霜疑匣中剑，风惫原上旌。\n早晚谒金阙，不闻刁斗声。', NULL, 3),
    ('横吹曲辞 洛阳道', '于武陵', '唐代', '浮世若浮云，千回故复新。\n旋添青草冢，更有白头人。\n岁暮客将老，雪晴山欲春。\n行行车与马，不尽洛阳尘。', NULL, 3),
    ('横吹曲辞 洛阳道', '郑渥', '唐代', '客亭门外路东西，多少喧腾事不齐。\n杨柳惹鞭公子醉，纻麻掩泪鲁人迷。\n通宵尘土飞山月，是处经营夹御堤。\n顷刻知音几存殁，半回依约认轮蹄。', NULL, 3);

-- 学段映射：学段取采集任务登记的「高中」，年级为「高二」
-- （这批是《全唐诗》语料，宫廷/应制诗居多，非小学必背清单，故不标小学）
-- 用行构造器 (title, author) 精确匹配：若写成 title IN (..) AND author IN (..)，
-- 那是笛卡尔积，会误命中「标题命中且作者也命中」的其它诗。
-- 作者为 NULL 的行用 COALESCE 归一，否则行构造器比较结果为 NULL 而匹配不上。
INSERT IGNORE INTO edu_poem_grade (poem_id, grade_level, `grade`)
SELECT p.poem_id, '高中', '高二'
FROM edu_poem p
WHERE (p.poem_title, COALESCE(p.poem_author, '')) IN (
    ('七夕宴悬圃二首 一', '高宗皇帝'),
    ('七夕宴悬圃二首 二', '高宗皇帝'),
    ('七月十五日题章敬寺', '德宗皇帝'),
    ('丈人观', '蜀太后徐氏'),
    ('丈人观', '蜀太妃徐氏'),
    ('丈人观谒先帝御容', '蜀太后徐氏'),
    ('三学山夜看圣灯', '蜀太后徐氏'),
    ('三学山夜看圣灯', '蜀太妃徐氏'),
    ('三层阁上置音声', '太宗皇帝'),
    ('三日书怀因示百僚', '德宗皇帝'),
    ('上元日二首 一', '文宗皇帝'),
    ('上元日二首 二', '文宗皇帝'),
    ('上巳日赐裴度', '文宗皇帝'),
    ('东亭茶宴', '鲍氏君徽'),
    ('两仪殿赋柏梁体', '太宗皇帝'),
    ('中和节日宴百僚赐诗', '德宗皇帝'),
    ('中和节赐百官燕集因示所怀', '德宗皇帝'),
    ('中和节赐群臣宴赋七韵', '德宗皇帝'),
    ('中春麟德殿会百僚观新乐诗一章章十六句', '德宗皇帝'),
    ('丰年多庆九日示怀', '德宗皇帝'),
    ('临洛水', '太宗皇帝'),
    ('丹景山至德寺', '蜀太后徐氏'),
    ('为赵法师别造精院过院赋诗', '明皇帝'),
    ('九日绝句', '德宗皇帝'),
    ('九月九日', '高宗皇帝'),
    ('九月九日上幸慈恩寺登浮图群臣上菊花寿酒', '上官昭容'),
    ('九月九日幸临渭亭登高得秋字', '中宗皇帝'),
    ('九月十八赐百僚追赏因书所怀', '德宗皇帝'),
    ('九月十日偶书', '后主煜'),
    ('书灵筵手巾', '后主煜'),
    ('书琵琶背', '后主煜'),
    ('于北平作', '太宗皇帝'),
    ('于太原召侍臣赐宴守岁', '太宗皇帝'),
    ('从驾幸少林寺', '则天皇后'),
    ('仪鸾殿早秋', '太宗皇帝'),
    ('伤辽东战亡', '太宗皇帝'),
    ('保大五年元日大雪同太弟景遂汪王景逷齐王景逵进士李建勋中书徐铉勤政殿学士张义方登楼赋', '嗣主璟'),
    ('傀儡吟', '明皇帝'),
    ('元日', '太宗皇帝'),
    ('元日退朝观军仗归营', '德宗皇帝'),
    ('入潼关', '太宗皇帝'),
    ('关山月', '鲍氏君徽'),
    ('冬宵各为四韵', '太宗皇帝'),
    ('冬日临昆明池', '太宗皇帝'),
    ('冬狩', '太宗皇帝'),
    ('出猎', '太宗皇帝'),
    ('初入秦川路逢寒食', '明皇帝'),
    ('初夏', '太宗皇帝'),
    ('初春登楼即目观作述怀', '太宗皇帝'),
    ('初晴落景', '太宗皇帝'),
    ('初秋夜坐', '太宗皇帝'),
    ('制袍字赐狄仁杰', '则天皇后'),
    ('十月诞辰内殿宴群臣效柏梁体联句', '中宗皇帝'),
    ('千秋节宴', '明皇帝'),
    ('千秋节赐群臣镜', '明皇帝'),
    ('南出雀鼠谷答张说', '明皇帝'),
    ('句', '上官昭容'),
    ('句', '后主煜'),
    ('句', '后主衍'),
    ('句', '吴越王钱镠'),
    ('句', '嗣主璟'),
    ('句', '太宗皇帝'),
    ('句', '宣宗皇帝'),
    ('句', '明皇帝'),
    ('吊白居易', '宣宗皇帝'),
    ('同二相已下群官乐游园宴', '明皇帝'),
    ('同刘晃喜雨', '明皇帝'),
    ('同玉真公主过大哥山池', '明皇帝'),
    ('和题丹景山至德寺', '蜀太妃徐氏'),
    ('咏乌代陈师道', '太宗皇帝'),
    ('咏兴国寺佛殿前幡', '太宗皇帝'),
    ('咏司马彪续汉志', '太宗皇帝'),
    ('咏小山', '太宗皇帝'),
    ('咏弓', '太宗皇帝'),
    ('咏桃', '太宗皇帝'),
    ('咏灯', '南唐先主李昪'),
    ('咏烛二首 一', '太宗皇帝'),
    ('咏烛二首 二', '太宗皇帝'),
    ('咏雨', '太宗皇帝'),
    ('咏雪', '太宗皇帝'),
    ('咏雷句', '昭宗皇帝'),
    ('咏风', '太宗皇帝'),
    ('咏饮马', '太宗皇帝'),
    ('咸亨殿宴近臣诸亲柏梁体', '高宗皇帝'),
    ('唐享昊天乐 第一', '则天皇后'),
    ('唐享昊天乐 第七', '则天皇后'),
    ('唐享昊天乐 第三', '则天皇后'),
    ('唐享昊天乐 第九', '则天皇后'),
    ('唐享昊天乐 第二', '则天皇后'),
    ('唐享昊天乐 第五', '则天皇后'),
    ('唐享昊天乐 第八', '则天皇后'),
    ('唐享昊天乐 第六', '则天皇后'),
    ('唐享昊天乐 第十', '则天皇后'),
    ('唐享昊天乐 第十一', '则天皇后'),
    ('唐享昊天乐 第十二', '则天皇后'),
    ('唐享昊天乐 第四', '则天皇后'),
    ('唐大飨拜洛乐章 九和', '则天皇后'),
    ('唐大飨拜洛乐章 咸和', '则天皇后'),
    ('唐大飨拜洛乐章 归和', '则天皇后'),
    ('唐大飨拜洛乐章 德和', '则天皇后'),
    ('唐大飨拜洛乐章 拜洛', '则天皇后'),
    ('唐大飨拜洛乐章 敬和', '则天皇后'),
    ('唐大飨拜洛乐章 昭和', '则天皇后'),
    ('唐大飨拜洛乐章 显和', '则天皇后'),
    ('唐大飨拜洛乐章 禋和', '则天皇后'),
    ('唐大飨拜洛乐章 致和', '则天皇后'),
    ('唐大飨拜洛乐章 通和', '则天皇后'),
    ('唐大飨拜洛乐章 齐和', '则天皇后'),
    ('唐明堂乐章 商音', '则天皇后'),
    ('唐明堂乐章 外办将出', '则天皇后'),
    ('唐明堂乐章 宫音', '则天皇后'),
    ('唐明堂乐章 征音', '则天皇后'),
    ('唐明堂乐章 登歌', '则天皇后'),
    ('唐明堂乐章 皇嗣出入升降', '则天皇后'),
    ('唐明堂乐章 皇帝行', '则天皇后'),
    ('唐明堂乐章 羽音', '则天皇后'),
    ('唐明堂乐章 角音', '则天皇后'),
    ('唐明堂乐章 迎送王公', '则天皇后'),
    ('唐明堂乐章 配飨', '则天皇后'),
    ('唐武氏享先庙乐章', '则天皇后'),
    ('喜雪', '太宗皇帝'),
    ('喜雪', '明皇帝'),
    ('嘲陆畅', '女学士宋氏若华'),
    ('夏日联句', '文宗皇帝'),
    ('夜梦', '萧妃'),
    ('太子纳妃太平公主出降', '高宗皇帝'),
    ('奉和同太子监守违恋', '韩王元嘉'),
    ('奉和圣制立春日侍宴内殿出翦彩花应制', '上官昭容'),
    ('奉和圣制过温汤', '越王贞'),
    ('奉和御制麟德殿宴百僚应制', '尚宫宋氏若昭'),
    ('奉和御制麟德殿宴百官', '尚宫宋氏若宪'),
    ('奉和麟德殿宴百僚应[制]', '鲍氏君徽'),
    ('如意娘', '则天皇后'),
    ('守岁', '太宗皇帝'),
    ('守岁', '高宗皇帝'),
    ('宫中作', '后王钱俶'),
    ('宫中题', '文宗皇帝'),
    ('宫词', '后主衍'),
    ('宴中山', '太宗皇帝'),
    ('山阁晚秋', '太宗皇帝'),
    ('巡省途次上党旧宫赋', '明皇帝'),
    ('巡衣锦军制还乡歌', '吴越王钱镠'),
    ('左丞相说右丞相璟太子少傅乾曜同日上官命宴东堂赐诗', '明皇帝'),
    ('帝京篇十首 一', '太宗皇帝'),
    ('帝京篇十首 七', '太宗皇帝'),
    ('帝京篇十首 三', '太宗皇帝'),
    ('帝京篇十首 九', '太宗皇帝'),
    ('帝京篇十首 二', '太宗皇帝'),
    ('帝京篇十首 五', '太宗皇帝'),
    ('帝京篇十首 八', '太宗皇帝'),
    ('帝京篇十首 六', '太宗皇帝'),
    ('帝京篇十首 十', '太宗皇帝'),
    ('帝京篇十首 四', '太宗皇帝'),
    ('平胡', '明皇帝'),
    ('幸凤泉汤', '明皇帝'),
    ('幸华严寺', '宣宗皇帝'),
    ('幸武功庆善宫', '太宗皇帝'),
    ('幸秦始皇陵', '中宗皇帝'),
    ('幸秦川上梓潼山', '后主衍'),
    ('幸蜀西至剑门', '明皇帝'),
    ('度秋', '太宗皇帝'),
    ('延英殿玉灵芝诗三章章八句 一', '肃宗皇帝'),
    ('延英殿玉灵芝诗三章章八句 三', '肃宗皇帝'),
    ('延英殿玉灵芝诗三章章八句 二', '肃宗皇帝'),
    ('彩书怨', '上官昭容'),
    ('悼诗', '后主煜'),
    ('惜花吟', '鲍氏君徽'),
    ('惟此温泉是称愈疾岂予独受其福思与兆人共之乘暇巡游乃言其志', '明皇帝'),
    ('感怀 一', '后主煜'),
    ('感怀 二', '后主煜'),
    ('执契静三边', '太宗皇帝'),
    ('批叶翘谏书纸尾', '闽王王继鹏'),
    ('拟小山篇', '徐贤妃'),
    ('挽辞 一', '后主煜'),
    ('挽辞 二', '后主煜'),
    ('探得李', '太宗皇帝'),
    ('旋师喜捷', '明皇帝'),
    ('早度蒲津关', '明皇帝'),
    ('早春夜宴', '则天皇后'),
    ('早登太行山中言志', '明皇帝'),
    ('春中兴庆宫酺宴', '明皇帝'),
    ('春台望', '明皇帝'),
    ('春日出苑游瞩', '明皇帝'),
    ('春日望海', '太宗皇帝'),
    ('春日玄武门宴群臣', '太宗皇帝'),
    ('春日登陕州城楼俯眺原野回丹碧缀烟霞密翠斑红芳菲花柳即目川岫聊以命篇', '太宗皇帝'),
    ('春晚宴两相及礼官丽正殿学士探得风字', '明皇帝'),
    ('春池柳', '太宗皇帝'),
    ('春游曲', '文德皇后'),
    ('景龙四年正月五日移仗蓬莱宫御大明殿会吐蕃骑马之戏因重为柏梁体联句', '中宗皇帝'),
    ('暮春喜雨诗', '文宗皇帝'),
    ('曳鼎歌', '则天皇后'),
    ('月晦', '太宗皇帝'),
    ('望终南山', '太宗皇帝'),
    ('望送魏徵葬', '太宗皇帝'),
    ('望雪', '太宗皇帝'),
    ('校猎义成喜逢大雪率题九韵以示群官', '明皇帝'),
    ('梅花', '后主煜'),
    ('横吹曲辞 入关', '张祜'),
    ('横吹曲辞 入关', '贾驰'),
    ('横吹曲辞 入塞', '刘希夷'),
    ('横吹曲辞 入塞曲', '耿[𣲗]'),
    ('横吹曲辞 入塞曲 一', '僧贯休'),
    ('横吹曲辞 入塞曲 一', '沈彬'),
    ('横吹曲辞 入塞曲 三', '僧贯休'),
    ('横吹曲辞 入塞曲 二', '僧贯休'),
    ('横吹曲辞 入塞曲 二', '沈彬'),
    ('横吹曲辞 关山月', '卢照邻'),
    ('横吹曲辞 关山月', '崔融'),
    ('横吹曲辞 关山月', '张籍'),
    ('横吹曲辞 关山月', '李白'),
    ('横吹曲辞 关山月', '李端'),
    ('横吹曲辞 关山月', '沈佺期'),
    ('横吹曲辞 关山月', '王建'),
    ('横吹曲辞 关山月', '翁绶'),
    ('横吹曲辞 关山月', '耿[𣲗]'),
    ('横吹曲辞 关山月', '长孙佐辅'),
    ('横吹曲辞 关山月', '鲍氏君[徽]微'),
    ('横吹曲辞 关山月二首 一', '戴叔伦'),
    ('横吹曲辞 关山月二首 二', '戴叔伦'),
    ('横吹曲辞 出关', '魏徵'),
    ('横吹曲辞 出塞', '刘驾'),
    ('横吹曲辞 出塞', '张易之'),
    ('横吹曲辞 出塞', '张籍'),
    ('横吹曲辞 出塞', '沈佺期'),
    ('横吹曲辞 出塞', '王之涣'),
    ('横吹曲辞 出塞', '王维'),
    ('横吹曲辞 出塞', '皇甫冉'),
    ('横吹曲辞 出塞', '窦威'),
    ('横吹曲辞 出塞', '耿[𣲗]'),
    ('横吹曲辞 出塞', '陈子昂'),
    ('横吹曲辞 出塞', '马戴'),
    ('横吹曲辞 出塞 一', '王昌龄'),
    ('横吹曲辞 出塞 二', '王昌龄'),
    ('横吹曲辞 出塞曲', '刘济'),
    ('横吹曲辞 出塞曲 一', '于鹄'),
    ('横吹曲辞 出塞曲 一', '僧贯休'),
    ('横吹曲辞 出塞曲 三', '僧贯休'),
    ('横吹曲辞 出塞曲 二', '于鹄'),
    ('横吹曲辞 出塞曲 二', '僧贯休'),
    ('横吹曲辞 前出塞九首 一', '杜甫'),
    ('横吹曲辞 前出塞九首 七', '杜甫'),
    ('横吹曲辞 前出塞九首 三', '杜甫'),
    ('横吹曲辞 前出塞九首 九', '杜甫'),
    ('横吹曲辞 前出塞九首 二', '杜甫'),
    ('横吹曲辞 前出塞九首 五', '杜甫'),
    ('横吹曲辞 前出塞九首 八', '杜甫'),
    ('横吹曲辞 前出塞九首 六', '杜甫'),
    ('横吹曲辞 前出塞九首 四', '杜甫'),
    ('横吹曲辞 后出塞五首 一', '杜甫'),
    ('横吹曲辞 后出塞五首 三', '杜甫'),
    ('横吹曲辞 后出塞五首 二', '杜甫'),
    ('横吹曲辞 后出塞五首 五', '杜甫'),
    ('横吹曲辞 后出塞五首 四', '杜甫'),
    ('横吹曲辞 折杨柳', '乔知之'),
    ('横吹曲辞 折杨柳', '余延寿'),
    ('横吹曲辞 折杨柳', '刘宪'),
    ('横吹曲辞 折杨柳', '卢照邻'),
    ('横吹曲辞 折杨柳', '崔湜'),
    ('横吹曲辞 折杨柳', '张九龄'),
    ('横吹曲辞 折杨柳', '张祜'),
    ('横吹曲辞 折杨柳', '李白'),
    ('横吹曲辞 折杨柳', '李端'),
    ('横吹曲辞 折杨柳', '欧阳瑾'),
    ('横吹曲辞 折杨柳', '沈佺期'),
    ('横吹曲辞 折杨柳', '翁绶'),
    ('横吹曲辞 折杨柳', '韦承庆'),
    ('横吹曲辞 折杨柳 一', '孟郊'),
    ('横吹曲辞 折杨柳 二', '孟郊'),
    ('横吹曲辞 望行人', '张籍'),
    ('横吹曲辞 望行人', '王建'),
    ('横吹曲辞 洛阳道', '于武陵'),
    ('横吹曲辞 洛阳道', '郑渥'),
    ('横吹曲辞 陇头', '张籍'),
    ('横吹曲辞 陇头吟', '王维'),
    ('横吹曲辞 陇头吟', '翁绶'),
    ('横吹曲辞 陇头水', '于𣸣'),
    ('横吹曲辞 陇头水', '卢照邻'),
    ('横吹曲辞 陇头水', '杨师道'),
    ('横吹曲辞 陇头水', '王建'),
    ('横吹曲辞 陇头水', '罗隐'),
    ('横吹曲辞 陇头水', '鲍溶'),
    ('横吹曲辞 陇头水 一', '僧皎然'),
    ('横吹曲辞 陇头水 二', '僧皎然'),
    ('正日临朝', '太宗皇帝'),
    ('没了期歌 一', '吴越王钱镠'),
    ('没了期歌 二', '吴越王钱镠'),
    ('渡中江望石城泣下', '后主煜'),
    ('温汤对雪', '明皇帝'),
    ('游丈人观谒先帝御容', '蜀太妃徐氏'),
    ('游九龙潭', '则天皇后'),
    ('游兴庆宫作', '明皇帝'),
    ('游后湖赏莲花', '嗣主璟'),
    ('游长宁公主流杯池二十五首 一', '上官昭容'),
    ('游长宁公主流杯池二十五首 七', '上官昭容'),
    ('游长宁公主流杯池二十五首 三', '上官昭容'),
    ('游长宁公主流杯池二十五首 九', '上官昭容'),
    ('游长宁公主流杯池二十五首 二', '上官昭容'),
    ('游长宁公主流杯池二十五首 二十', '上官昭容'),
    ('游长宁公主流杯池二十五首 二十一', '上官昭容'),
    ('游长宁公主流杯池二十五首 二十三', '上官昭容'),
    ('游长宁公主流杯池二十五首 二十二', '上官昭容'),
    ('游长宁公主流杯池二十五首 二十五', '上官昭容'),
    ('游长宁公主流杯池二十五首 二十四', '上官昭容'),
    ('游长宁公主流杯池二十五首 五', '上官昭容'),
    ('游长宁公主流杯池二十五首 八', '上官昭容'),
    ('游长宁公主流杯池二十五首 六', '上官昭容'),
    ('游长宁公主流杯池二十五首 十', '上官昭容'),
    ('游长宁公主流杯池二十五首 十一', '上官昭容'),
    ('游长宁公主流杯池二十五首 十七', '上官昭容'),
    ('游长宁公主流杯池二十五首 十三', '上官昭容'),
    ('游长宁公主流杯池二十五首 十九', '上官昭容'),
    ('游长宁公主流杯池二十五首 十二', '上官昭容'),
    ('游长宁公主流杯池二十五首 十五', '上官昭容'),
    ('游长宁公主流杯池二十五首 十八', '上官昭容'),
    ('游长宁公主流杯池二十五首 十六', '上官昭容'),
    ('游长宁公主流杯池二十五首 十四', '上官昭容'),
    ('游长宁公主流杯池二十五首 四', '上官昭容'),
    ('潼关口号', '明皇帝'),
    ('瀑布联句', '宣宗皇帝'),
    ('玄都观', '蜀太后徐氏'),
    ('玄都观', '蜀太妃徐氏'),
    ('王屋山送道士司马承祯还天台', '明皇帝'),
    ('琵琶', '太宗皇帝'),
    ('病中书事', '后主煜'),
    ('病中感怀', '后主煜'),
    ('病起题山舍壁', '后主煜'),
    ('登三台言志', '太宗皇帝'),
    ('登蒲州逍遥楼', '明皇帝'),
    ('登骊山高顶寓目', '中宗皇帝'),
    ('百丈山', '宣宗皇帝'),
    ('石桥', '信安王祎'),
    ('石淙', '中宗皇帝'),
    ('石淙', '则天皇后'),
    ('石淙', '睿宗皇帝'),
    ('秋日二首 一', '太宗皇帝'),
    ('秋日二首 二', '太宗皇帝'),
    ('秋日即目', '太宗皇帝'),
    ('秋日翠微宫', '太宗皇帝'),
    ('秋日𢽾庾信体', '太宗皇帝'),
    ('秋暮言志', '太宗皇帝'),
    ('秋莺', '后主煜'),
    ('秋风函谷应诏', '徐贤妃'),
    ('立春日游苑迎春', '中宗皇帝'),
    ('端午', '明皇帝'),
    ('端午三殿宴群臣探得神字', '明皇帝'),
    ('答司马承祯上剑镜', '明皇帝'),
    ('经河上公庙', '明皇帝'),
    ('经破薛举战地', '太宗皇帝'),
    ('经邹鲁祭孔子而叹之', '明皇帝'),
    ('续薛令之题壁', '明皇帝'),
    ('置酒坐飞阁', '太宗皇帝'),
    ('腊日宣诏幸上苑', '则天皇后'),
    ('舞曲歌辞 中和乐舞词', '不详'),
    ('舞曲歌辞 凯乐歌辞 君臣同庆乐', '不详'),
    ('舞曲歌辞 凯乐歌辞 应圣期', '不详'),
    ('舞曲歌辞 凯乐歌辞 破阵乐', '不详'),
    ('舞曲歌辞 凯乐歌辞 贺圣欢', '不详'),
    ('舞曲歌辞 功成庆善乐舞词', '不详'),
    ('芳兰', '太宗皇帝'),
    ('蔷薇诗一首十八韵呈东海侍郎徐铉', '韩王从善'),
    ('虚池驿题屏风', '宜芬公主'),
    ('行次成皋途经先圣擒建德之所缅思功业感而赋诗', '明皇帝'),
    ('观拔河俗戏', '明皇帝'),
    ('观棋', '吉王从谦'),
    ('谒大慈恩寺', '高宗皇帝'),
    ('谒幷州大兴国寺诗', '太宗皇帝'),
    ('谒慈恩寺题奘法师房', '高宗皇帝'),
    ('谢赐珍珠', '江妃'),
    ('赋尚书', '太宗皇帝'),
    ('赋帘', '太宗皇帝'),
    ('赋得临池柳', '太宗皇帝'),
    ('赋得临池竹', '太宗皇帝'),
    ('赋得北方有佳人', '徐贤妃'),
    ('赋得含峰云', '太宗皇帝'),
    ('赋得夏首启节', '太宗皇帝'),
    ('赋得弱柳鸣秋蝉', '太宗皇帝'),
    ('赋得早雁出云鸣', '太宗皇帝'),
    ('赋得李', '太宗皇帝'),
    ('赋得樱桃', '太宗皇帝'),
    ('赋得残菊', '太宗皇帝'),
    ('赋得浮桥', '太宗皇帝'),
    ('赋得白日半西山', '太宗皇帝'),
    ('赋得花庭雾', '太宗皇帝'),
    ('赋秋日悬清光赐房玄龄', '太宗皇帝'),
    ('赐宫人庆奴', '后主煜'),
    ('赐崔日知往潞州', '明皇帝'),
    ('赐房玄龄', '太宗皇帝'),
    ('赐棃李泌与诸王联句', '肃宗皇帝'),
    ('赐萧瑀', '太宗皇帝'),
    ('赐诸州刺史以题座右', '明皇帝'),
    ('赐道士邓紫阳', '明皇帝'),
    ('赐魏徵诗', '太宗皇帝'),
    ('赠别唐太师道袭', '蜀高祖王建'),
    ('赠张云容舞', '杨贵妃'),
    ('赠胡天师', '则天皇后'),
    ('轩游宫十五夜', '明皇帝'),
    ('辽东山夜临秋', '太宗皇帝'),
    ('辽城望月', '太宗皇帝'),
    ('过大哥宅探得歌字韵', '明皇帝'),
    ('过大哥山池题石壁', '明皇帝'),
    ('过旧宅二首 一', '太宗皇帝'),
    ('过旧宅二首 二', '太宗皇帝'),
    ('过晋阳宫', '明皇帝'),
    ('过温汤', '高宗皇帝'),
    ('过王濬暮', '明皇帝'),
    ('过白卫岭和韩昭', '后主衍'),
    ('过老子庙', '明皇帝'),
    ('还陕述怀', '太宗皇帝'),
    ('进太宗', '徐贤妃'),
    ('远山澄碧雾', '太宗皇帝'),
    ('送张说巡边', '明皇帝'),
    ('送徐州张建封还镇', '德宗皇帝'),
    ('送忠州太守康昭远等', '明皇帝'),
    ('送李邕之任滑台', '明皇帝'),
    ('送玄同真人李抱朴谒灊山仙祠', '明皇帝'),
    ('送胡真师还西山', '明皇帝'),
    ('送贺知章归四明', '明皇帝'),
    ('送赵法师还蜀因名山奠简', '明皇帝'),
    ('送道士薛季昌还山', '明皇帝'),
    ('送邓王二十弟从益牧宣城', '后主煜'),
    ('途次陕州', '明皇帝'),
    ('途经华岳', '明皇帝'),
    ('避暑摩诃池上作', '后蜀嗣主孟昶'),
    ('郊庙歌辞  善胜舞', '不详'),
    ('郊庙歌辞  懿宗室舞', '萧仿'),
    ('郊庙歌辞  禋顺', '不详'),
    ('郊庙歌辞  肃顺', '不详'),
    ('郊庙歌辞 中宗祀昊天乐章 中宫助祭升坛', '不详'),
    ('郊庙歌辞 中宗祀昊天乐章 亚献', '不详'),
    ('郊庙歌辞 中宗祀昊天乐章 凯安', '不详'),
    ('郊庙歌辞 中宗祀昊天乐章 告谢', '不详'),
    ('郊庙歌辞 中宗祀昊天乐章 太和', '不详'),
    ('郊庙歌辞 中宗祀昊天乐章 福和', '不详'),
    ('郊庙歌辞 中宗祀昊天乐章 肃和', '不详'),
    ('郊庙歌辞 中宗祀昊天乐章 舒和', '不详'),
    ('郊庙歌辞 中宗祀昊天乐章 豫和', '不详'),
    ('郊庙歌辞 中宗祀昊天乐章 雍和', '不详'),
    ('郊庙歌辞 五郊乐章 白帝商音', '魏徵'),
    ('郊庙歌辞 五郊乐章 白帝商音 肃和', '魏徵'),
    ('郊庙歌辞 五郊乐章 白帝商音 舒和', '魏徵'),
    ('郊庙歌辞 五郊乐章 白帝商音 雍和', '魏徵'),
    ('郊庙歌辞 五郊乐章 白郊迎神', '不详'),
    ('郊庙歌辞 五郊乐章 赤帝征音', '魏徵'),
    ('郊庙歌辞 五郊乐章 赤帝征音 肃和', '魏徵'),
    ('郊庙歌辞 五郊乐章 赤帝征音 舒和', '魏徵'),
    ('郊庙歌辞 五郊乐章 赤帝征音 雍和', '魏徵'),
    ('郊庙歌辞 五郊乐章 赤郊迎神', '不详'),
    ('郊庙歌辞 五郊乐章 送神', '不详'),
    ('郊庙歌辞 五郊乐章 青帝角音', '魏徵'),
    ('郊庙歌辞 五郊乐章 青帝角音 肃和', '魏徵'),
    ('郊庙歌辞 五郊乐章 青帝角音 舒和', '魏徵'),
    ('郊庙歌辞 五郊乐章 青帝角音 雍和', '魏徵'),
    ('郊庙歌辞 五郊乐章 青郊迎神', '不详'),
    ('郊庙歌辞 五郊乐章 黄帝宫音', '魏徵'),
    ('郊庙歌辞 五郊乐章 黄帝宫音 肃和', '魏徵'),
    ('郊庙歌辞 五郊乐章 黄帝宫音 舒和', '魏徵'),
    ('郊庙歌辞 五郊乐章 黄帝宫音 雍和', '魏徵'),
    ('郊庙歌辞 五郊乐章 黄郊迎神', '不详'),
    ('郊庙歌辞 五郊乐章 黑帝羽音', '魏徵'),
    ('郊庙歌辞 五郊乐章 黑帝羽音 肃和', '魏徵'),
    ('郊庙歌辞 五郊乐章 黑帝羽音 舒和', '魏徵'),
    ('郊庙歌辞 五郊乐章 黑帝羽音 雍和', '魏徵'),
    ('郊庙歌辞 五郊乐章 黑郊迎神', '不详'),
    ('郊庙歌辞 享先农乐章 咸和', '不详'),
    ('郊庙歌辞 享先农乐章 咸和', '褚亮'),
    ('郊庙歌辞 享先农乐章 肃和', '褚亮'),
    ('郊庙歌辞 享先农乐章 舒和', '褚亮'),
    ('郊庙歌辞 享先农乐章 雍和', '褚亮'),
    ('郊庙歌辞 享先蚕乐章 展敬', '不详'),
    ('郊庙歌辞 享先蚕乐章 昭庆', '不详'),
    ('郊庙歌辞 享先蚕乐章 永和', '不详'),
    ('郊庙歌辞 享先蚕乐章 絜诚', '不详'),
    ('郊庙歌辞 享先蚕乐章 肃和', '不详'),
    ('郊庙歌辞 享太庙乐章 严和', '不详'),
    ('郊庙歌辞 享太庙乐章 保大舞', '郭子仪'),
    ('郊庙歌辞 享太庙乐章 光大舞', '不详'),
    ('郊庙歌辞 享太庙乐章 光大舞', '张说'),
    ('郊庙歌辞 享太庙乐章 凯安四章 一', '张说'),
    ('郊庙歌辞 享太庙乐章 凯安四章 三', '张说'),
    ('郊庙歌辞 享太庙乐章 凯安四章 二', '张说'),
    ('郊庙歌辞 享太庙乐章 凯安四章 四', '张说'),
    ('郊庙歌辞 享太庙乐章 升和', '不详'),
    ('郊庙歌辞 享太庙乐章 同和', '不详'),
    ('郊庙歌辞 享太庙乐章 和宁舞', '牛僧孺'),
    ('郊庙歌辞 享太庙乐章 咸宁舞', '不详'),
    ('郊庙歌辞 享太庙乐章 大和舞', '张说'),
    ('郊庙歌辞 享太庙乐章 大基舞', '魏徵、褚亮'),
    ('郊庙歌辞 享太庙乐章 大定舞', '李回'),
    ('郊庙歌辞 享太庙乐章 大成舞', '张说'),
    ('郊庙歌辞 享太庙乐章 大成舞', '魏徵、褚亮'),
    ('郊庙歌辞 享太庙乐章 大政舞', '张说'),
    ('郊庙歌辞 享太庙乐章 大明舞', '张说'),
    ('郊庙歌辞 享太庙乐章 大明舞', '魏徵、褚亮'),
    ('郊庙歌辞 享太庙乐章 大顺舞', '郑絪'),
    ('郊庙歌辞 享太庙乐章 太和', '张说'),
    ('郊庙歌辞 享太庙乐章 太和舞', '不详'),
    ('郊庙歌辞 享太庙乐章 宁和', '不详'),
    ('郊庙歌辞 享太庙乐章 宣宗室舞', '夏侯孜'),
    ('郊庙歌辞 享太庙乐章 寿和', '魏徵、褚亮'),
    ('郊庙歌辞 享太庙乐章 崇德舞', '不详'),
    ('郊庙歌辞 享太庙乐章 崇德舞', '张说'),
    ('郊庙歌辞 享太庙乐章 广运舞', '郭子仪'),
    ('郊庙歌辞 享太庙乐章 延和', '不详'),
    ('郊庙歌辞 享太庙乐章 恭和', '不详'),
    ('郊庙歌辞 享太庙乐章 惟新舞', '刘晏'),
    ('郊庙歌辞 享太庙乐章 承光舞', '不详'),
    ('郊庙歌辞 享太庙乐章 文明舞', '郑馀庆'),
    ('郊庙歌辞 享太庙乐章 文舞', '张说'),
    ('郊庙歌辞 享太庙乐章 昭和', '不详'),
    ('郊庙歌辞 享太庙乐章 昭感', '不详'),
    ('郊庙歌辞 享太庙乐章 景云舞', '不详'),
    ('郊庙歌辞 享太庙乐章 景云舞', '张说'),
    ('郊庙歌辞 享太庙乐章 歆和', '不详'),
    ('郊庙歌辞 享太庙乐章 永和', '张说'),
    ('郊庙歌辞 享太庙乐章 永和', '魏徵、褚亮'),
    ('郊庙歌辞 享太庙乐章 永和三章 一', '张说'),
    ('郊庙歌辞 享太庙乐章 永和三章 三', '张说'),
    ('郊庙歌辞 享太庙乐章 永和三章 二', '张说'),
    ('郊庙歌辞 享太庙乐章 登歌', '张说'),
    ('郊庙歌辞 享太庙乐章 福和', '张说'),
    ('郊庙歌辞 享太庙乐章 肃和', '不详'),
    ('郊庙歌辞 享太庙乐章 肃和', '张说'),
    ('郊庙歌辞 享太庙乐章 肃和', '魏徵、褚亮'),
    ('郊庙歌辞 享太庙乐章 舒和', '张说'),
    ('郊庙歌辞 享太庙乐章 舒和', '魏徵、褚亮'),
    ('郊庙歌辞 享太庙乐章 虔和', '不详'),
    ('郊庙歌辞 享太庙乐章 诚敬', '不详'),
    ('郊庙歌辞 享太庙乐章 象德舞', '段文昌'),
    ('郊庙歌辞 享太庙乐章 迎神', '不详'),
    ('郊庙歌辞 享太庙乐章 送神', '不详'),
    ('郊庙歌辞 享太庙乐章 通和', '不详'),
    ('郊庙歌辞 享太庙乐章 金奏', '不详'),
    ('郊庙歌辞 享太庙乐章 钧天[舞]', '不详'),
    ('郊庙歌辞 享太庙乐章 钧天舞', '张说'),
    ('郊庙歌辞 享太庙乐章 长发舞', '张说'),
    ('郊庙歌辞 享太庙乐章 长发舞', '魏徵、褚亮'),
    ('郊庙歌辞 享太庙乐章 雍和', '魏徵、褚亮'),
    ('郊庙歌辞 享太庙乐章 雍和二章 一', '张说'),
    ('郊庙歌辞 享太庙乐章 雍和二章 二', '张说'),
    ('郊庙歌辞 享孔子庙乐章 迎神', '不详'),
    ('郊庙歌辞 享孔子庙乐章 送神', '不详'),
    ('郊庙歌辞 享惠昭太子庙乐章 亚献终献', '裴度'),
    ('郊庙歌辞 享惠昭太子庙乐章 登歌', '杜羔'),
    ('郊庙歌辞 享惠昭太子庙乐章 请神', '归登'),
    ('郊庙歌辞 享惠昭太子庙乐章 迎俎酌献', '李逢吉'),
    ('郊庙歌辞 享惠昭太子庙乐章 送文舞出迎武舞入', '孟简'),
    ('郊庙歌辞 享惠昭太子庙乐章 送神', '王涯'),
    ('郊庙歌辞 享懿德太子庙乐章 武舞作', '不详'),
    ('郊庙歌辞 享懿德太子庙乐章 登歌酌鬯', '不详'),
    ('郊庙歌辞 享懿德太子庙乐章 迎俎酌献', '不详'),
    ('郊庙歌辞 享懿德太子庙乐章 迎神', '不详'),
    ('郊庙歌辞 享懿德太子庙乐章 送文舞迎武舞', '不详'),
    ('郊庙歌辞 享文敬太子庙乐章 亚献终献', '崔邠'),
    ('郊庙歌辞 享文敬太子庙乐章 登歌', '陈京'),
    ('郊庙歌辞 享文敬太子庙乐章 请神', '许孟容'),
    ('郊庙歌辞 享文敬太子庙乐章 迎俎酌献', '冯伉'),
    ('郊庙歌辞 享文敬太子庙乐章 送文舞迎武舞', '不详'),
    ('郊庙歌辞 享文敬太子庙乐章 送神', '张荐'),
    ('郊庙歌辞 享章怀太子庙乐章 武舞作', '不详'),
    ('郊庙歌辞 享章怀太子庙乐章 登歌酌鬯', '不详'),
    ('郊庙歌辞 享章怀太子庙乐章 迎俎酌献', '不详'),
    ('郊庙歌辞 享章怀太子庙乐章 迎神', '不详'),
    ('郊庙歌辞 享章怀太子庙乐章 送文舞迎武舞', '不详'),
    ('郊庙歌辞 享节愍太子庙乐章 武舞作', '不详'),
    ('郊庙歌辞 享节愍太子庙乐章 登歌酌鬯', '不详'),
    ('郊庙歌辞 享节愍太子庙乐章 迎俎酌献', '不详'),
    ('郊庙歌辞 享节愍太子庙乐章 迎神', '不详'),
    ('郊庙歌辞 享节愍太子庙乐章 送文舞出迎武舞入', '不详'),
    ('郊庙歌辞 享隐太子庙乐章 凯安', '不详'),
    ('郊庙歌辞 享隐太子庙乐章 肃和', '不详'),
    ('郊庙歌辞 享隐太子庙乐章 舒和', '不详'),
    ('郊庙歌辞 享隐太子庙乐章 诚和', '不详'),
    ('郊庙歌辞 享隐太子庙乐章 雍和', '不详'),
    ('郊庙歌辞 享龙池乐章 第一章', '姚崇'),
    ('郊庙歌辞 享龙池乐章 第七章', '苏颋'),
    ('郊庙歌辞 享龙池乐章 第三章', '沈佺期'),
    ('郊庙歌辞 享龙池乐章 第九章', '姜皎'),
    ('郊庙歌辞 享龙池乐章 第二章', '蔡孚'),
    ('郊庙歌辞 享龙池乐章 第五章', '姜皎'),
    ('郊庙歌辞 享龙池乐章 第八章', '李乂'),
    ('郊庙歌辞 享龙池乐章 第六章', '崔日用'),
    ('郊庙歌辞 享龙池乐章 第十章', '裴璀'),
    ('郊庙歌辞 享龙池乐章 第四章', '卢怀慎'),
    ('郊庙歌辞 仪坤庙乐章 坤贞', '不详'),
    ('郊庙歌辞 仪坤庙乐章 太和', '丘说'),
    ('郊庙歌辞 仪坤庙乐章 安和', '刘子玄'),
    ('郊庙歌辞 仪坤庙乐章 寿和', '徐坚'),
    ('郊庙歌辞 仪坤庙乐章 昭升', '薛稷'),
    ('郊庙歌辞 仪坤庙乐章 永和', '徐彦伯'),
    ('郊庙歌辞 仪坤庙乐章 永和', '祝钦明'),
    ('郊庙歌辞 仪坤庙乐章 肃和', '张齐贤'),
    ('郊庙歌辞 仪坤庙乐章 舒和', '胡雄'),
    ('郊庙歌辞 仪坤庙乐章 迎神', '不详'),
    ('郊庙歌辞 仪坤庙乐章 送神', '不详'),
    ('郊庙歌辞 仪坤庙乐章 金奏', '不详'),
    ('郊庙歌辞 仪坤庙乐章 雍和', '员半千'),
    ('郊庙歌辞 仪坤庙乐章 雍和', '郑善玉'),
    ('郊庙歌辞 后唐宗庙乐舞辞 应天舞', '崔居俭、卢文纪'),
    ('郊庙歌辞 后唐宗庙乐舞辞 文明舞', '崔居俭、卢文纪'),
    ('郊庙歌辞 后唐宗庙乐舞辞 昭德舞', '崔居俭、卢文纪'),
    ('郊庙歌辞 后唐宗庙乐舞辞 武成舞', '崔居俭、卢文纪'),
    ('郊庙歌辞 后唐宗庙乐舞辞 永平舞', '崔居俭、卢文纪'),
    ('郊庙歌辞 后唐宗庙乐舞辞 雍煕舞', '崔居俭、卢文纪'),
    ('郊庙歌辞 周宗庙乐舞辞 咸顺', '不详'),
    ('郊庙歌辞 周宗庙乐舞辞 善庆舞', '不详'),
    ('郊庙歌辞 周宗庙乐舞辞 忠顺', '不详'),
    ('郊庙歌辞 周宗庙乐舞辞 明德舞', '不详'),
    ('郊庙歌辞 周宗庙乐舞辞 治顺', '不详'),
    ('郊庙歌辞 周宗庙乐舞辞 禋顺', '不详'),
    ('郊庙歌辞 周宗庙乐舞辞 福顺', '不详'),
    ('郊庙歌辞 周宗庙乐舞辞 章德舞', '不详'),
    ('郊庙歌辞 周宗庙乐舞辞 肃雍舞', '不详'),
    ('郊庙歌辞 周宗庙乐舞辞 肃顺', '不详'),
    ('郊庙歌辞 周宗庙乐舞辞 观成舞', '不详'),
    ('郊庙歌辞 周朝飨乐章 康顺', '不详'),
    ('郊庙歌辞 周朝飨乐章 忠顺', '不详'),
    ('郊庙歌辞 周朝飨乐章 治顺', '不详'),
    ('郊庙歌辞 周朝飨乐章 福顺', '不详'),
    ('郊庙歌辞 周郊祀乐章 忠顺乐', '不详'),
    ('郊庙歌辞 周郊祀乐章 感顺乐', '不详'),
    ('郊庙歌辞 周郊祀乐章 昭顺乐', '不详'),
    ('郊庙歌辞 周郊祀乐章 武舞乐', '不详'),
    ('郊庙歌辞 周郊祀乐章 治顺乐', '不详'),
    ('郊庙歌辞 周郊祀乐章 禋顺乐', '不详'),
    ('郊庙歌辞 周郊祀乐章 福顺乐', '不详'),
    ('郊庙歌辞 夕月乐章 肃和', '不详'),
    ('郊庙歌辞 夕月乐章 舒和', '不详'),
    ('郊庙歌辞 夕月乐章 雍和', '不详'),
    ('郊庙歌辞 太清宫乐章 再上', '不详'),
    ('郊庙歌辞 太清宫乐章 冲和', '不详'),
    ('郊庙歌辞 太清宫乐章 序入破第一奏', '不详'),
    ('郊庙歌辞 太清宫乐章 序入破第三奏', '不详'),
    ('郊庙歌辞 太清宫乐章 序入破第二奏', '不详'),
    ('郊庙歌辞 太清宫乐章 煌煌', '不详'),
    ('郊庙歌辞 太清宫乐章 登歌', '不详'),
    ('郊庙歌辞 太清宫乐章 真和', '不详'),
    ('郊庙歌辞 太清宫乐章 紫极舞', '不详'),
    ('郊庙歌辞 太清宫乐章 终上', '不详'),
    ('郊庙歌辞 太清宫乐章 香初上', '不详'),
    ('郊庙歌辞 封泰山乐章 凯安', '张说'),
    ('郊庙歌辞 封泰山乐章 太和', '张说'),
    ('郊庙歌辞 封泰山乐章 寿和', '张说'),
    ('郊庙歌辞 封泰山乐章 肃和', '张说'),
    ('郊庙歌辞 封泰山乐章 舒和', '张说'),
    ('郊庙歌辞 封泰山乐章 豫和', '张说'),
    ('郊庙歌辞 封泰山乐章 豫和 一', '张说'),
    ('郊庙歌辞 封泰山乐章 豫和 三', '张说'),
    ('郊庙歌辞 封泰山乐章 豫和 二', '张说'),
    ('郊庙歌辞 封泰山乐章 豫和 五', '张说'),
    ('郊庙歌辞 封泰山乐章 豫和 六', '张说'),
    ('郊庙歌辞 封泰山乐章 豫和 四', '张说'),
    ('郊庙歌辞 封泰山乐章 雍和', '张说'),
    ('郊庙歌辞 德明兴圣庙乐章 亚献终献', '李舒'),
    ('郊庙歌辞 德明兴圣庙乐章 兴圣酌献', '李舒'),
    ('郊庙歌辞 德明兴圣庙乐章 德明酌献', '李舒'),
    ('郊庙歌辞 德明兴圣庙乐章 登歌奠币', '李舒'),
    ('郊庙歌辞 德明兴圣庙乐章 迎俎', '李舒'),
    ('郊庙歌辞 德明兴圣庙乐章 迎神', '李舒'),
    ('郊庙歌辞 德明兴圣庙乐章 送神', '李舒'),
    ('郊庙歌辞 明堂乐章 肃和', '褚亮'),
    ('郊庙歌辞 明堂乐章 舒和', '褚亮'),
    ('郊庙歌辞 明堂乐章 雍和', '褚亮'),
    ('郊庙歌辞 明皇祀圜丘乐章 凯安', '不详'),
    ('郊庙歌辞 明皇祀圜丘乐章 太和', '不详'),
    ('郊庙歌辞 明皇祀圜丘乐章 寿和', '不详'),
    ('郊庙歌辞 明皇祀圜丘乐章 肃和', '不详'),
    ('郊庙歌辞 明皇祀圜丘乐章 舒和', '不详'),
    ('郊庙歌辞 明皇祀圜丘乐章 豫和', '不详'),
    ('郊庙歌辞 明皇祀圜丘乐章 雍和', '不详'),
    ('郊庙歌辞 昭德皇后庙乐章 凯安', '不详'),
    ('郊庙歌辞 昭德皇后庙乐章 坤元', '不详'),
    ('郊庙歌辞 昭德皇后庙乐章 寿和', '不详'),
    ('郊庙歌辞 昭德皇后庙乐章 永和', '不详'),
    ('郊庙歌辞 昭德皇后庙乐章 肃和', '不详'),
    ('郊庙歌辞 昭德皇后庙乐章 舒和', '不详'),
    ('郊庙歌辞 昭德皇后庙乐章 雍和', '不详'),
    ('郊庙歌辞 晋昭德成功舞歌 昭德舞歌二首 一', '不详'),
    ('郊庙歌辞 晋昭德成功舞歌 昭德舞歌二首 二', '不详'),
    ('郊庙歌辞 晋昭德成功舞歌 武功舞歌二首 一', '不详'),
    ('郊庙歌辞 晋昭德成功舞歌 武功舞歌二首 二', '不详'),
    ('郊庙歌辞 晋朝飨乐章 举酒', '不详'),
    ('郊庙歌辞 晋朝飨乐章 再举酒', '不详'),
    ('郊庙歌辞 晋朝飨乐章 初举酒文同乐', '不详'),
    ('郊庙歌辞 晋朝飨乐章 四举酒', '不详'),
    ('郊庙歌辞 晋朝飨乐章 群臣酒行歌 一', '不详'),
    ('郊庙歌辞 晋朝飨乐章 群臣酒行歌 三', '不详'),
    ('郊庙歌辞 晋朝飨乐章 群臣酒行歌 二', '不详'),
    ('郊庙歌辞 朝日乐章 肃和', '不详'),
    ('郊庙歌辞 朝日乐章 舒和', '不详'),
    ('郊庙歌辞 朝日乐章 迎神', '不详'),
    ('郊庙歌辞 朝日乐章 送神', '不详'),
    ('郊庙歌辞 朝日乐章 雍和', '不详'),
    ('郊庙歌辞 梁太庙乐舞辞 大合舞', '不详'),
    ('郊庙歌辞 梁太庙乐舞辞 帝盥', '不详'),
    ('郊庙歌辞 梁太庙乐舞辞 开平舞', '不详'),
    ('郊庙歌辞 梁太庙乐舞辞 撤豆', '不详'),
    ('郊庙歌辞 梁太庙乐舞辞 昭德舞', '不详'),
    ('郊庙歌辞 梁太庙乐舞辞 来仪舞', '不详'),
    ('郊庙歌辞 梁太庙乐舞辞 登歌', '不详'),
    ('郊庙歌辞 梁太庙乐舞辞 皇帝行', '不详'),
    ('郊庙歌辞 梁太庙乐舞辞 象功舞', '不详'),
    ('郊庙歌辞 梁太庙乐舞辞 送神', '不详'),
    ('郊庙歌辞 梁太庙乐舞辞 饮福', '不详'),
    ('郊庙歌辞 梁郊祀乐章 庆休', '赵光逢、张衮'),
    ('郊庙歌辞 梁郊祀乐章 庆和', '赵光逢、张衮'),
    ('郊庙歌辞 梁郊祀乐章 庆和 一', '赵光逢、张衮'),
    ('郊庙歌辞 梁郊祀乐章 庆和 三', '赵光逢、张衮'),
    ('郊庙歌辞 梁郊祀乐章 庆和 二', '赵光逢、张衮'),
    ('郊庙歌辞 梁郊祀乐章 庆和 五', '赵光逢、张衮'),
    ('郊庙歌辞 梁郊祀乐章 庆和 六', '赵光逢、张衮'),
    ('郊庙歌辞 梁郊祀乐章 庆和 四', '赵光逢、张衮'),
    ('郊庙歌辞 梁郊祀乐章 庆平', '赵光逢、张衮'),
    ('郊庙歌辞 梁郊祀乐章 庆熙', '赵光逢、张衮'),
    ('郊庙歌辞 梁郊祀乐章 庆肃', '赵光逢、张衮'),
    ('郊庙歌辞 梁郊祀乐章 庆融', '赵光逢、张衮'),
    ('郊庙歌辞 梁郊祀乐章 庆隆', '赵光逢、张衮'),
    ('郊庙歌辞 梁郊祀乐章 庆顺', '赵光逢、张衮'),
    ('郊庙歌辞 武后享清庙乐章十首 第一', '不详'),
    ('郊庙歌辞 武后享清庙乐章十首 第七迎武舞', '不详'),
    ('郊庙歌辞 武后享清庙乐章十首 第三登歌', '不详'),
    ('郊庙歌辞 武后享清庙乐章十首 第九彻俎', '不详'),
    ('郊庙歌辞 武后享清庙乐章十首 第二', '不详'),
    ('郊庙歌辞 武后享清庙乐章十首 第五饮福', '不详'),
    ('郊庙歌辞 武后享清庙乐章十首 第八武舞作', '不详'),
    ('郊庙歌辞 武后享清庙乐章十首 第六送文舞', '不详'),
    ('郊庙歌辞 武后享清庙乐章十首 第十送神', '不详'),
    ('郊庙歌辞 武后享清庙乐章十首 第四迎神', '不详'),
    ('郊庙歌辞 武后大享拜洛乐章 九和', '不详'),
    ('郊庙歌辞 武后大享拜洛乐章 又归和', '不详'),
    ('郊庙歌辞 武后大享拜洛乐章 咸和', '不详'),
    ('郊庙歌辞 武后大享拜洛乐章 归和', '不详'),
    ('郊庙歌辞 武后大享拜洛乐章 德和', '不详'),
    ('郊庙歌辞 武后大享拜洛乐章 敬和', '不详'),
    ('郊庙歌辞 武后大享拜洛乐章 昭和', '不详'),
    ('郊庙歌辞 武后大享拜洛乐章 显和', '不详'),
    ('郊庙歌辞 武后大享拜洛乐章 禋和', '不详'),
    ('郊庙歌辞 武后大享拜洛乐章 致和', '不详'),
    ('郊庙歌辞 武后大享拜洛乐章 通和', '不详'),
    ('郊庙歌辞 武后大享拜洛乐章 齐和', '不详'),
    ('郊庙歌辞 武后大享昊天乐章 一', '不详'),
    ('郊庙歌辞 武后大享昊天乐章 七', '不详'),
    ('郊庙歌辞 武后大享昊天乐章 三', '不详'),
    ('郊庙歌辞 武后大享昊天乐章 九', '不详'),
    ('郊庙歌辞 武后大享昊天乐章 二', '不详'),
    ('郊庙歌辞 武后大享昊天乐章 五', '不详'),
    ('郊庙歌辞 武后大享昊天乐章 八', '不详'),
    ('郊庙歌辞 武后大享昊天乐章 六', '不详'),
    ('郊庙歌辞 武后大享昊天乐章 十', '不详'),
    ('郊庙歌辞 武后大享昊天乐章 十一', '不详'),
    ('郊庙歌辞 武后大享昊天乐章 十二', '不详'),
    ('郊庙歌辞 武后大享昊天乐章 四', '不详'),
    ('郊庙歌辞 武后崇先庙乐章', '不详'),
    ('郊庙歌辞 武后明堂乐章 商音', '武则天'),
    ('郊庙歌辞 武后明堂乐章 外办将出', '武则天'),
    ('郊庙歌辞 武后明堂乐章 宫音', '武则天'),
    ('郊庙歌辞 武后明堂乐章 征音', '武则天'),
    ('郊庙歌辞 武后明堂乐章 登歌', '武则天'),
    ('郊庙歌辞 武后明堂乐章 皇嗣出入升降', '武则天'),
    ('郊庙歌辞 武后明堂乐章 皇帝行', '武则天'),
    ('郊庙歌辞 武后明堂乐章 羽音', '武则天'),
    ('郊庙歌辞 武后明堂乐章 角音', '武则天'),
    ('郊庙歌辞 武后明堂乐章 迎送王公', '武则天'),
    ('郊庙歌辞 武后明堂乐章 配飨', '武则天'),
    ('郊庙歌辞 汉宗庙乐舞辞 显仁舞', '张昭'),
    ('郊庙歌辞 汉宗庙乐舞辞 武德舞', '张昭'),
    ('郊庙歌辞 汉宗庙乐舞辞 灵长舞', '张昭'),
    ('郊庙歌辞 汉宗庙乐舞辞 积善舞', '张昭'),
    ('郊庙歌辞 汉宗庙乐舞辞 章庆舞', '张昭'),
    ('郊庙歌辞 汉宗庙乐舞辞 观德舞', '张昭'),
    ('郊庙歌辞 祀九宫贵神乐章 凯安', '不详'),
    ('郊庙歌辞 祀九宫贵神乐章 太和', '不详'),
    ('郊庙歌辞 祀九宫贵神乐章 寿和', '不详'),
    ('郊庙歌辞 祀九宫贵神乐章 福和', '不详'),
    ('郊庙歌辞 祀九宫贵神乐章 肃和', '不详'),
    ('郊庙歌辞 祀九宫贵神乐章 舒和', '不详'),
    ('郊庙歌辞 祀九宫贵神乐章 豫和', '不详'),
    ('郊庙歌辞 祀九宫贵神乐章 豫和 一', '不详'),
    ('郊庙歌辞 祀九宫贵神乐章 豫和 三', '不详'),
    ('郊庙歌辞 祀九宫贵神乐章 豫和 二', '不详'),
    ('郊庙歌辞 祀九宫贵神乐章 豫和 五', '不详'),
    ('郊庙歌辞 祀九宫贵神乐章 豫和 六', '不详'),
    ('郊庙歌辞 祀九宫贵神乐章 豫和 四', '不详'),
    ('郊庙歌辞 祀九宫贵神乐章 雍和', '不详'),
    ('郊庙歌辞 祀圜丘乐章 凯安', '褚亮'),
    ('郊庙歌辞 祀圜丘乐章 太和', '褚亮'),
    ('郊庙歌辞 祀圜丘乐章 寿和', '褚亮'),
    ('郊庙歌辞 祀圜丘乐章 肃和', '褚亮'),
    ('郊庙歌辞 祀圜丘乐章 舒和', '褚亮'),
    ('郊庙歌辞 祀圜丘乐章 豫和', '褚亮'),
    ('郊庙歌辞 祀圜丘乐章 雍和', '褚亮'),
    ('郊庙歌辞 祀雨师乐章 亚献终献', '包佶'),
    ('郊庙歌辞 祀雨师乐章 奠币登歌', '包佶'),
    ('郊庙歌辞 祀雨师乐章 迎俎酌献', '包佶'),
    ('郊庙歌辞 祀雨师乐章 迎神', '包佶'),
    ('郊庙歌辞 祀雨师乐章 送神', '包佶'),
    ('郊庙歌辞 祀风师乐章 亚献终献', '包佶'),
    ('郊庙歌辞 祀风师乐章 奠币登歌', '包佶'),
    ('郊庙歌辞 祀风师乐章 迎俎酌献', '包佶'),
    ('郊庙歌辞 祀风师乐章 迎神', '包佶'),
    ('郊庙歌辞 祀风师乐章 送神', '包佶'),
    ('郊庙歌辞 祈谷乐章 肃和', '不详'),
    ('郊庙歌辞 祈谷乐章 舒和', '不详'),
    ('郊庙歌辞 祈谷乐章 雍和', '不详'),
    ('郊庙歌辞 祭[太]社乐章 肃和', '褚亮'),
    ('郊庙歌辞 祭[太]社乐章 舒和', '褚亮'),
    ('郊庙歌辞 祭[太]社乐章 雍和', '褚亮'),
    ('郊庙歌辞 祭大社乐章 迎神', '不详'),
    ('郊庙歌辞 祭大社乐章 送神', '不详'),
    ('郊庙歌辞 祭方丘乐章 肃和', '褚亮'),
    ('郊庙歌辞 祭方丘乐章 舒和', '褚亮'),
    ('郊庙歌辞 祭方丘乐章 金奏', '不详'),
    ('郊庙歌辞 祭方丘乐章 雍和', '褚亮'),
    ('郊庙歌辞 祭方丘乐章 顺和', '不详'),
    ('郊庙歌辞 祭方丘乐章 顺和', '褚亮'),
    ('郊庙歌辞 祭汾阴乐章 凯安', '蒋挺'),
    ('郊庙歌辞 祭汾阴乐章 南吕羽', '韩休'),
    ('郊庙歌辞 祭汾阴乐章 太和', '王晙'),
    ('郊庙歌辞 祭汾阴乐章 太簇角', '卢从愿'),
    ('郊庙歌辞 祭汾阴乐章 姑洗征', '刘晃'),
    ('郊庙歌辞 祭汾阴乐章 寿和', '苏颋'),
    ('郊庙歌辞 祭汾阴乐章 肃和', '崔玄童'),
    ('郊庙歌辞 祭汾阴乐章 舒和', '何鸾'),
    ('郊庙歌辞 祭汾阴乐章 雍和', '贾曾'),
    ('郊庙歌辞 祭汾阴乐章 顺和', '林锺宫'),
    ('郊庙歌辞 祭汾阴乐章 顺和', '源光裕'),
    ('郊庙歌辞 祭神州乐章 肃和', '褚亮'),
    ('郊庙歌辞 祭神州乐章 舒和', '褚亮'),
    ('郊庙歌辞 祭神州乐章 迎神', '不详'),
    ('郊庙歌辞 祭神州乐章 送神', '不详'),
    ('郊庙歌辞 祭神州乐章 雍和', '褚亮'),
    ('郊庙歌辞 禅社首乐章 太和', '贺知章、源乾曜'),
    ('郊庙歌辞 禅社首乐章 寿和', '贺知章、源乾曜'),
    ('郊庙歌辞 禅社首乐章 灵具醉', '贺知章、源乾曜'),
    ('郊庙歌辞 禅社首乐章 福和', '贺知章、源乾曜'),
    ('郊庙歌辞 禅社首乐章 肃和', '贺知章、源乾曜'),
    ('郊庙歌辞 禅社首乐章 雍和', '贺知章、源乾曜'),
    ('郊庙歌辞 禅社首乐章 顺和', '贺知章、源乾曜'),
    ('郊庙歌辞 蜡百神乐章 肃和', '不详'),
    ('郊庙歌辞 蜡百神乐章 舒和', '不详'),
    ('郊庙歌辞 蜡百神乐章 迎神', '不详'),
    ('郊庙歌辞 蜡百神乐章 送神', '不详'),
    ('郊庙歌辞 蜡百神乐章 雍和', '不详'),
    ('郊庙歌辞 褒德庙乐章 彰德', '不详'),
    ('郊庙歌辞 褒德庙乐章 昭德', '不详'),
    ('郊庙歌辞 褒德庙乐章 武舞作', '不详'),
    ('郊庙歌辞 褒德庙乐章 褒德', '不详'),
    ('郊庙歌辞 褒德庙乐章 进德', '不详'),
    ('郊庙歌辞 让皇帝庙乐章 亚献终献', '李舒'),
    ('郊庙歌辞 让皇帝庙乐章 奠币', '李舒'),
    ('郊庙歌辞 让皇帝庙乐章 迎俎', '李舒'),
    ('郊庙歌辞 让皇帝庙乐章 迎神', '李舒'),
    ('郊庙歌辞 让皇帝庙乐章 送神', '李舒'),
    ('郊庙歌辞 让皇帝庙乐章 酌献', '李舒'),
    ('郊庙歌辞 郊天旧乐章 豫和', '不详'),
    ('郊庙歌辞 释奠文宣王乐章 承和', '不详'),
    ('郊庙歌辞 释奠文宣王乐章 肃和', '不详'),
    ('郊庙歌辞 释奠文宣王乐章 舒和', '不详'),
    ('郊庙歌辞 释奠文宣王乐章 诚和', '不详'),
    ('郊庙歌辞 释奠文宣王乐章 雍和', '不详'),
    ('郊庙歌辞 释奠武成王乐章 亚献终献', '于邵'),
    ('郊庙歌辞 释奠武成王乐章 奠币登歌', '于邵'),
    ('郊庙歌辞 释奠武成王乐章 迎俎酌献', '于邵'),
    ('郊庙歌辞 释奠武成王乐章 迎神', '于邵'),
    ('郊庙歌辞 释奠武成王乐章 送神', '于邵'),
    ('郊庙歌辞 隐太子庙乐章 迎神', '不详'),
    ('郊庙歌辞 隐太子庙乐章 送神', '不详'),
    ('郊庙歌辞 雩祀乐章 肃和', '褚亮'),
    ('郊庙歌辞 雩祀乐章 舒和', '褚亮'),
    ('郊庙歌辞 雩祀乐章 豫和', '不详'),
    ('郊庙歌辞 雩祀乐章 雍和', '褚亮'),
    ('醉妆词', '后主衍'),
    ('采芙蓉', '太宗皇帝'),
    ('重幸武功', '太宗皇帝'),
    ('重阳日中外同欢以诗言志因示群官', '德宗皇帝'),
    ('重阳日即事', '德宗皇帝'),
    ('重阳日赐宴曲江亭赋六韵诗用清字', '德宗皇帝'),
    ('重阳锡宴群臣', '宣宗皇帝'),
    ('野次喜雪', '明皇帝'),
    ('长门怨', '徐贤妃'),
    ('除夜', '太宗皇帝'),
    ('集贤书院成送张说上集贤学士赐宴得珍字', '明皇帝'),
    ('题剑门', '后主衍'),
    ('题天回驿', '蜀太后徐氏'),
    ('题天回驿', '蜀太妃徐氏'),
    ('题彭州阳平化', '蜀太后徐氏'),
    ('题彭州阳平化', '蜀太妃徐氏'),
    ('题梅妃画真', '明皇帝'),
    ('题泾县水西寺', '宣宗皇帝'),
    ('题程修己竹障', '文宗皇帝'),
    ('题金华宫', '蜀太后徐氏'),
    ('题金华宫', '蜀太妃徐氏'),
    ('题金楼子后', '后主煜'),
    ('饮马长城窟行', '太宗皇帝'),
    ('饯中书侍郎来济', '太宗皇帝'),
    ('饯王晙巡边', '明皇帝'),
    ('首夏花萼楼观群臣宴宁王山亭回楼下又申之以赏乐赋诗', '明皇帝'),
    ('首春', '太宗皇帝'),
    ('驾幸三会寺应制', '上官昭容'),
    ('驾幸新丰温泉宫献诗三首 一', '上官昭容'),
    ('驾幸新丰温泉宫献诗三首 三', '上官昭容'),
    ('驾幸新丰温泉宫献诗三首 二', '上官昭容'),
    ('鹡鸰颂', '明皇帝'),
    ('麟德殿宴百僚', '德宗皇帝'),
    ('黄台瓜辞', '章怀太子'),
    ('鼓吹曲辞 上之回', '卢照邻'),
    ('鼓吹曲辞 上之回', '李白'),
    ('鼓吹曲辞 上之回', '李贺'),
    ('鼓吹曲辞 临高台', '僧贯休'),
    ('鼓吹曲辞 临高台', '王勃'),
    ('鼓吹曲辞 临高台', '褚亮'),
    ('鼓吹曲辞 凯歌六首 一', '岑参'),
    ('鼓吹曲辞 凯歌六首 三', '岑参'),
    ('鼓吹曲辞 凯歌六首 二', '岑参'),
    ('鼓吹曲辞 凯歌六首 五', '岑参'),
    ('鼓吹曲辞 凯歌六首 六', '岑参'),
    ('鼓吹曲辞 凯歌六首 四', '岑参'),
    ('鼓吹曲辞 君马黄', '李白'),
    ('鼓吹曲辞 将进酒', '李白'),
    ('鼓吹曲辞 将进酒', '李贺'),
    ('鼓吹曲辞 巫山高', '于𣸣'),
    ('鼓吹曲辞 巫山高', '僧齐己'),
    ('鼓吹曲辞 巫山高', '刘方平'),
    ('鼓吹曲辞 巫山高', '卢照邻'),
    ('鼓吹曲辞 巫山高', '张循之'),
    ('鼓吹曲辞 巫山高', '李端'),
    ('鼓吹曲辞 巫山高', '李贺'),
    ('鼓吹曲辞 巫山高', '皇甫冉'),
    ('鼓吹曲辞 巫山高', '郑世翼'),
    ('鼓吹曲辞 巫山高二首 一', '孟郊'),
    ('鼓吹曲辞 巫山高二首 一', '沈佺期'),
    ('鼓吹曲辞 巫山高二首 二', '孟郊'),
    ('鼓吹曲辞 巫山高二首 二', '沈佺期'),
    ('鼓吹曲辞 战城南', '刘驾'),
    ('鼓吹曲辞 战城南', '卢照邻'),
    ('鼓吹曲辞 战城南', '李白'),
    ('鼓吹曲辞 战城南二首 一', '僧贯休'),
    ('鼓吹曲辞 战城南二首 二', '僧贯休'),
    ('鼓吹曲辞 有所思', '刘氏云'),
    ('鼓吹曲辞 有所思', '卢仝'),
    ('鼓吹曲辞 有所思', '孟郊'),
    ('鼓吹曲辞 有所思', '李白'),
    ('鼓吹曲辞 有所思', '沈佺期'),
    ('鼓吹曲辞 有所思', '韦应物'),
    ('鼓吹曲辞 朱鹭', '张籍'),
    ('鼓吹曲辞 艾如张', '李贺'),
    ('鼓吹曲辞 芳树', '元稹'),
    ('鼓吹曲辞 芳树', '卢照邻'),
    ('鼓吹曲辞 芳树', '徐彦伯'),
    ('鼓吹曲辞 芳树', '沈佺期'),
    ('鼓吹曲辞 芳树', '罗隐'),
    ('鼓吹曲辞 芳树', '韦应物'),
    ('鼓吹曲辞 钓竿篇', '沈佺期'),
    ('鼓吹曲辞 雉子班', '李白'),
    ('鼓吹曲辞 黄雀行', '庄南杰'),
    ('鼓吹曲辞 鼓吹铙歌 东蛮', '柳宗元'),
    ('鼓吹曲辞 鼓吹铙歌 兽之穷', '柳宗元'),
    ('鼓吹曲辞 鼓吹铙歌 吐谷浑', '柳宗元'),
    ('鼓吹曲辞 鼓吹铙歌 奔鲸沛', '柳宗元'),
    ('鼓吹曲辞 鼓吹铙歌 战武牢', '柳宗元'),
    ('鼓吹曲辞 鼓吹铙歌 晋阳武', '柳宗元'),
    ('鼓吹曲辞 鼓吹铙歌 河右平', '柳宗元'),
    ('鼓吹曲辞 鼓吹铙歌 泾水黄', '柳宗元'),
    ('鼓吹曲辞 鼓吹铙歌 苞枿', '柳宗元'),
    ('鼓吹曲辞 鼓吹铙歌 铁山碎', '柳宗元'),
    ('鼓吹曲辞 鼓吹铙歌 靖本邦', '柳宗元'),
    ('鼓吹曲辞 鼓吹铙歌 高昌', '柳宗元')
);
