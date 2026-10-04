-- ===========================================
-- 汉字基础数据种子（常用独体字，便于「基础数据维护」页开箱有数据）
--
-- 幂等：按汉字唯一（uk_edu_hanzi_char）+ WHERE NOT EXISTS，重复执行不产生重复行。
-- 笔顺为通行写法（笔画名按书写顺序排列），老师可在维护页按教材口径修正。
-- ===========================================

INSERT INTO edu_hanzi
    (hanzi_char, hanzi_pinyin, hanzi_strokes, hanzi_radical, hanzi_structure, hanzi_stroke_order,
     hanzi_meaning, hanzi_grade_level, hanzi_status)
SELECT '人', 'rén', 2, '人', '独体', '丿㇏', '人类；组词：大人、人们', '小学', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_hanzi WHERE hanzi_char = '人');

INSERT INTO edu_hanzi
    (hanzi_char, hanzi_pinyin, hanzi_strokes, hanzi_radical, hanzi_structure, hanzi_stroke_order,
     hanzi_meaning, hanzi_grade_level, hanzi_status)
SELECT '大', 'dà', 3, '大', '独体', '一丿㇏', '大小；组词：大人、大方', '小学', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_hanzi WHERE hanzi_char = '大');

INSERT INTO edu_hanzi
    (hanzi_char, hanzi_pinyin, hanzi_strokes, hanzi_radical, hanzi_structure, hanzi_stroke_order,
     hanzi_meaning, hanzi_grade_level, hanzi_status)
SELECT '小', 'xiǎo', 3, '小', '独体', '亅丿丶', '细小；组词：小心、小学', '小学', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_hanzi WHERE hanzi_char = '小');

INSERT INTO edu_hanzi
    (hanzi_char, hanzi_pinyin, hanzi_strokes, hanzi_radical, hanzi_structure, hanzi_stroke_order,
     hanzi_meaning, hanzi_grade_level, hanzi_status)
SELECT '上', 'shàng', 3, '一', '独体', '丨一一', '上面；组词：上学、上课', '小学', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_hanzi WHERE hanzi_char = '上');

INSERT INTO edu_hanzi
    (hanzi_char, hanzi_pinyin, hanzi_strokes, hanzi_radical, hanzi_structure, hanzi_stroke_order,
     hanzi_meaning, hanzi_grade_level, hanzi_status)
SELECT '下', 'xià', 3, '一', '独体', '一丨丶', '下面；组词：下午、下课', '小学', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_hanzi WHERE hanzi_char = '下');

INSERT INTO edu_hanzi
    (hanzi_char, hanzi_pinyin, hanzi_strokes, hanzi_radical, hanzi_structure, hanzi_stroke_order,
     hanzi_meaning, hanzi_grade_level, hanzi_status)
SELECT '山', 'shān', 3, '山', '独体', '丨㇗丨', '高山；组词：大山、山峰', '小学', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_hanzi WHERE hanzi_char = '山');

INSERT INTO edu_hanzi
    (hanzi_char, hanzi_pinyin, hanzi_strokes, hanzi_radical, hanzi_structure, hanzi_stroke_order,
     hanzi_meaning, hanzi_grade_level, hanzi_status)
SELECT '水', 'shuǐ', 4, '水', '独体', '丨㇇丿㇏', '水流；组词：喝水、水果', '小学', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_hanzi WHERE hanzi_char = '水');

INSERT INTO edu_hanzi
    (hanzi_char, hanzi_pinyin, hanzi_strokes, hanzi_radical, hanzi_structure, hanzi_stroke_order,
     hanzi_meaning, hanzi_grade_level, hanzi_status)
SELECT '火', 'huǒ', 4, '火', '独体', '丶丿丿㇏', '火焰；组词：火车、火苗', '小学', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_hanzi WHERE hanzi_char = '火');

INSERT INTO edu_hanzi
    (hanzi_char, hanzi_pinyin, hanzi_strokes, hanzi_radical, hanzi_structure, hanzi_stroke_order,
     hanzi_meaning, hanzi_grade_level, hanzi_status)
SELECT '木', 'mù', 4, '木', '独体', '一丨丿㇏', '树木；组词：木头、木马', '小学', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_hanzi WHERE hanzi_char = '木');

INSERT INTO edu_hanzi
    (hanzi_char, hanzi_pinyin, hanzi_strokes, hanzi_radical, hanzi_structure, hanzi_stroke_order,
     hanzi_meaning, hanzi_grade_level, hanzi_status)
SELECT '日', 'rì', 4, '日', '独体', '丨㇕一一', '太阳/日子；组词：日出、生日', '小学', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_hanzi WHERE hanzi_char = '日');

INSERT INTO edu_hanzi
    (hanzi_char, hanzi_pinyin, hanzi_strokes, hanzi_radical, hanzi_structure, hanzi_stroke_order,
     hanzi_meaning, hanzi_grade_level, hanzi_status)
SELECT '月', 'yuè', 4, '月', '独体', '丿㇆一一', '月亮/月份；组词：月亮、月光', '小学', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_hanzi WHERE hanzi_char = '月');

INSERT INTO edu_hanzi
    (hanzi_char, hanzi_pinyin, hanzi_strokes, hanzi_radical, hanzi_structure, hanzi_stroke_order,
     hanzi_meaning, hanzi_grade_level, hanzi_status)
SELECT '天', 'tiān', 4, '大', '独体', '一一丿㇏', '天空/天气；组词：天空、今天', '小学', 1
WHERE NOT EXISTS (SELECT 1 FROM edu_hanzi WHERE hanzi_char = '天');
