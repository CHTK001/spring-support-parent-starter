-- =====================================================================
-- V2.5__initdata_edu_hanzi_pinyin.sql - 汉字库多音字 / 通假字种子
-- 通道: INITDATA（初始化数据，异步执行）
--
-- 解决什么问题：
--   现有 3496 条汉字是采集源 chinese-xinhua 的 word.json 逐字灌进来的，
--   而该源的 pinyin 字段**只给一个读音**。于是「行」只有 xíng、「重」只有 zhòng、
--   「长」只有 cháng，老师要按 háng / chóng / zhǎng 检索时一条都查不到 ——
--   这不是检索功能缺失，是**数据缺口**。
--
-- 本文件做两件事：
--   1. 把常用多音字的全部读音补进 hanzi_pinyin（逗号分隔）；
--   2. 把教材里高频的通假字关系写进 edu_hanzi_tongjia（本字 → 借字）。
--
-- 幂等：
--   · 多音字用 UPDATE ... WHERE hanzi_char = 'x'，重复执行结果一致；
--   · 通假字用 INSERT ... SELECT ... WHERE NOT EXISTS（按「本字 id + 借字」判重），
--     且以 hanzi_char 关联本字 id，不写死自增主键（换库也不会错位）。
--
-- 为什么不用 INSERT IGNORE：
--   H2 的脚本转换器会把 INSERT IGNORE 剥成 INSERT（见 H2ScriptConverter#INSERT_IGNORE），
--   重复执行会撞唯一键。WHERE NOT EXISTS 在 MySQL 与 H2 上语义一致，是两族库都能用的写法。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 一、多音字：补全读音（逗号分隔，首个读音保持与教材主读音一致）
-- ---------------------------------------------------------------------
UPDATE edu_hanzi SET hanzi_pinyin = 'xíng,háng' WHERE hanzi_char = '行';
UPDATE edu_hanzi SET hanzi_pinyin = 'zhòng,chóng' WHERE hanzi_char = '重';
UPDATE edu_hanzi SET hanzi_pinyin = 'cháng,zhǎng' WHERE hanzi_char = '长';
UPDATE edu_hanzi SET hanzi_pinyin = 'lè,yuè' WHERE hanzi_char = '乐';
UPDATE edu_hanzi SET hanzi_pinyin = 'shuō,shuì,yuè' WHERE hanzi_char = '说';
UPDATE edu_hanzi SET hanzi_pinyin = 'hé,hè,huó,huò,hú' WHERE hanzi_char = '和';
UPDATE edu_hanzi SET hanzi_pinyin = 'hǎo,hào' WHERE hanzi_char = '好';
UPDATE edu_hanzi SET hanzi_pinyin = 'wéi,wèi' WHERE hanzi_char = '为';
UPDATE edu_hanzi SET hanzi_pinyin = 'dé,de,děi' WHERE hanzi_char = '得';
UPDATE edu_hanzi SET hanzi_pinyin = 'dì,de' WHERE hanzi_char = '地';
UPDATE edu_hanzi SET hanzi_pinyin = 'gān,gàn' WHERE hanzi_char = '干';
UPDATE edu_hanzi SET hanzi_pinyin = 'fā,fà' WHERE hanzi_char = '发';
UPDATE edu_hanzi SET hanzi_pinyin = 'zhī,zhǐ' WHERE hanzi_char = '只';
UPDATE edu_hanzi SET hanzi_pinyin = 'shù,shǔ,shuò' WHERE hanzi_char = '数';
UPDATE edu_hanzi SET hanzi_pinyin = 'jué,jiào' WHERE hanzi_char = '觉';
UPDATE edu_hanzi SET hanzi_pinyin = 'zhe,zháo,zhuó,zhāo' WHERE hanzi_char = '着';
UPDATE edu_hanzi SET hanzi_pinyin = 'le,liǎo' WHERE hanzi_char = '了';
UPDATE edu_hanzi SET hanzi_pinyin = 'hái,huán' WHERE hanzi_char = '还';
UPDATE edu_hanzi SET hanzi_pinyin = 'dōu,dū' WHERE hanzi_char = '都';
UPDATE edu_hanzi SET hanzi_pinyin = 'xuè,xiě' WHERE hanzi_char = '血';
UPDATE edu_hanzi SET hanzi_pinyin = 'báo,bó,bò' WHERE hanzi_char = '薄';
UPDATE edu_hanzi SET hanzi_pinyin = 'zhōng,zhòng' WHERE hanzi_char = '中';
UPDATE edu_hanzi SET hanzi_pinyin = 'kōng,kòng' WHERE hanzi_char = '空';
UPDATE edu_hanzi SET hanzi_pinyin = 'jiān,jiàn' WHERE hanzi_char = '间';
UPDATE edu_hanzi SET hanzi_pinyin = 'shǎo,shào' WHERE hanzi_char = '少';
UPDATE edu_hanzi SET hanzi_pinyin = 'jiāo,jiào' WHERE hanzi_char = '教';
UPDATE edu_hanzi SET hanzi_pinyin = 'xiāng,xiàng' WHERE hanzi_char = '相';
UPDATE edu_hanzi SET hanzi_pinyin = 'dān,shàn,chán' WHERE hanzi_char = '单';
UPDATE edu_hanzi SET hanzi_pinyin = 'chù,chǔ' WHERE hanzi_char = '处';
UPDATE edu_hanzi SET hanzi_pinyin = 'sǎn,sàn' WHERE hanzi_char = '散';
UPDATE edu_hanzi SET hanzi_pinyin = 'tiáo,diào' WHERE hanzi_char = '调';
UPDATE edu_hanzi SET hanzi_pinyin = 'mó,mú' WHERE hanzi_char = '模';
UPDATE edu_hanzi SET hanzi_pinyin = 'sì,shì' WHERE hanzi_char = '似';
UPDATE edu_hanzi SET hanzi_pinyin = 'bēi,bèi' WHERE hanzi_char = '背';
UPDATE edu_hanzi SET hanzi_pinyin = 'dàn,tán' WHERE hanzi_char = '弹';
UPDATE edu_hanzi SET hanzi_pinyin = 'jǐn,jìn' WHERE hanzi_char = '尽';
UPDATE edu_hanzi SET hanzi_pinyin = 'jiǎ,jià' WHERE hanzi_char = '假';
UPDATE edu_hanzi SET hanzi_pinyin = 'luò,lào,là' WHERE hanzi_char = '落';
UPDATE edu_hanzi SET hanzi_pinyin = 'cáng,zàng' WHERE hanzi_char = '藏';
UPDATE edu_hanzi SET hanzi_pinyin = 'zhāo,cháo' WHERE hanzi_char = '朝';
UPDATE edu_hanzi SET hanzi_pinyin = 'gěi,jǐ' WHERE hanzi_char = '给';
UPDATE edu_hanzi SET hanzi_pinyin = 'xì,jì' WHERE hanzi_char = '系';
UPDATE edu_hanzi SET hanzi_pinyin = 'shěng,xǐng' WHERE hanzi_char = '省';
UPDATE edu_hanzi SET hanzi_pinyin = 'shǔ,shù' WHERE hanzi_char = '属';
UPDATE edu_hanzi SET hanzi_pinyin = 'lù,liù' WHERE hanzi_char = '陆';
UPDATE edu_hanzi SET hanzi_pinyin = 'cān,shēn,cēn' WHERE hanzi_char = '参';
UPDATE edu_hanzi SET hanzi_pinyin = 'chà,chā,chāi,cī' WHERE hanzi_char = '差';
UPDATE edu_hanzi SET hanzi_pinyin = 'hè,hē,hè' WHERE hanzi_char = '喝';
UPDATE edu_hanzi SET hanzi_pinyin = 'bēn,bèn' WHERE hanzi_char = '奔';
UPDATE edu_hanzi SET hanzi_pinyin = 'dǎi,dài' WHERE hanzi_char = '逮';
UPDATE edu_hanzi SET hanzi_pinyin = 'qiáng,qiǎng,jiàng' WHERE hanzi_char = '强';
UPDATE edu_hanzi SET hanzi_pinyin = 'qū,ǒu' WHERE hanzi_char = '曲';
UPDATE edu_hanzi SET hanzi_pinyin = 'pū,pù' WHERE hanzi_char = '铺';
UPDATE edu_hanzi SET hanzi_pinyin = 'fó,fú' WHERE hanzi_char = '佛';
UPDATE edu_hanzi SET hanzi_pinyin = 'shé,zhé' WHERE hanzi_char = '折';
UPDATE edu_hanzi SET hanzi_pinyin = 'chēng,chèn,chèng' WHERE hanzi_char = '称';
UPDATE edu_hanzi SET hanzi_pinyin = 'jìn,jìng' WHERE hanzi_char = '劲';
UPDATE edu_hanzi SET hanzi_pinyin = 'mā,má,mǎ' WHERE hanzi_char = '蚂';
UPDATE edu_hanzi SET hanzi_pinyin = 'jiē,jié' WHERE hanzi_char = '结';
UPDATE edu_hanzi SET hanzi_pinyin = 'jī,jǐ' WHERE hanzi_char = '几';
UPDATE edu_hanzi SET hanzi_pinyin = 'huá,huà' WHERE hanzi_char = '划';
UPDATE edu_hanzi SET hanzi_pinyin = 'dōu,dū' WHERE hanzi_char = '督';

-- ---------------------------------------------------------------------
-- 二、通假字：本字 → 借字（教材高频，按「本字 id + 借字」判重）
--
-- 口径：本字是库里已有的那一条记录，借字写在 edu_hanzi_tongjia。
-- 检索时两个方向都通：搜本字命中本字行，搜借字也能命中本字行。
-- 这里只收**有明确教材出处**的几组，不做大规模臆测。
-- ---------------------------------------------------------------------
INSERT INTO edu_hanzi_tongjia (tongjia_hanzi_id, tongjia_char, tongjia_note)
SELECT h.hanzi_id, '悦', '说，通「悦」，愉快（《论语》「不亦说乎」）'
FROM edu_hanzi h
WHERE h.hanzi_char = '说'
  AND NOT EXISTS (SELECT 1 FROM edu_hanzi_tongjia t
                  WHERE t.tongjia_hanzi_id = h.hanzi_id AND t.tongjia_char = '悦');

INSERT INTO edu_hanzi_tongjia (tongjia_hanzi_id, tongjia_char, tongjia_note)
SELECT h.hanzi_id, '披', '被，通「披」，穿着（《周亚夫军细柳》「军士吏被甲」）'
FROM edu_hanzi h
WHERE h.hanzi_char = '被'
  AND NOT EXISTS (SELECT 1 FROM edu_hanzi_tongjia t
                  WHERE t.tongjia_hanzi_id = h.hanzi_id AND t.tongjia_char = '披');

INSERT INTO edu_hanzi_tongjia (tongjia_hanzi_id, tongjia_char, tongjia_note)
SELECT h.hanzi_id, '智', '知，通「智」，智慧（《两小儿辩日》「孰为汝多知乎」）'
FROM edu_hanzi h
WHERE h.hanzi_char = '知'
  AND NOT EXISTS (SELECT 1 FROM edu_hanzi_tongjia t
                  WHERE t.tongjia_hanzi_id = h.hanzi_id AND t.tongjia_char = '智');

INSERT INTO edu_hanzi_tongjia (tongjia_hanzi_id, tongjia_char, tongjia_note)
SELECT h.hanzi_id, '返', '反，通「返」，返回（《三人成虎》「庞恭从邯郸反」）'
FROM edu_hanzi h
WHERE h.hanzi_char = '反'
  AND NOT EXISTS (SELECT 1 FROM edu_hanzi_tongjia t
                  WHERE t.tongjia_hanzi_id = h.hanzi_id AND t.tongjia_char = '返');

INSERT INTO edu_hanzi_tongjia (tongjia_hanzi_id, tongjia_char, tongjia_note)
SELECT h.hanzi_id, '邀', '要，通「邀」，邀请（《桃花源记》「便要还家」）'
FROM edu_hanzi h
WHERE h.hanzi_char = '要'
  AND NOT EXISTS (SELECT 1 FROM edu_hanzi_tongjia t
                  WHERE t.tongjia_hanzi_id = h.hanzi_id AND t.tongjia_char = '邀');

INSERT INTO edu_hanzi_tongjia (tongjia_hanzi_id, tongjia_char, tongjia_note)
SELECT h.hanzi_id, '着', '著，通「着」，穿（《木兰诗》「著我旧时裳」）'
FROM edu_hanzi h
WHERE h.hanzi_char = '著'
  AND NOT EXISTS (SELECT 1 FROM edu_hanzi_tongjia t
                  WHERE t.tongjia_hanzi_id = h.hanzi_id AND t.tongjia_char = '着');

INSERT INTO edu_hanzi_tongjia (tongjia_hanzi_id, tongjia_char, tongjia_note)
SELECT h.hanzi_id, '贴', '帖，通「贴」，粘贴（《木兰诗》「对镜帖花黄」）'
FROM edu_hanzi h
WHERE h.hanzi_char = '帖'
  AND NOT EXISTS (SELECT 1 FROM edu_hanzi_tongjia t
                  WHERE t.tongjia_hanzi_id = h.hanzi_id AND t.tongjia_char = '贴');

INSERT INTO edu_hanzi_tongjia (tongjia_hanzi_id, tongjia_char, tongjia_note)
SELECT h.hanzi_id, '伙', '火，通「伙」，同伴（《木兰诗》「出门看火伴」）'
FROM edu_hanzi h
WHERE h.hanzi_char = '火'
  AND NOT EXISTS (SELECT 1 FROM edu_hanzi_tongjia t
                  WHERE t.tongjia_hanzi_id = h.hanzi_id AND t.tongjia_char = '伙');

INSERT INTO edu_hanzi_tongjia (tongjia_hanzi_id, tongjia_char, tongjia_note)
SELECT h.hanzi_id, '原', '元，通「原」，本来（《示儿》「死去元知万事空」）'
FROM edu_hanzi h
WHERE h.hanzi_char = '元'
  AND NOT EXISTS (SELECT 1 FROM edu_hanzi_tongjia t
                  WHERE t.tongjia_hanzi_id = h.hanzi_id AND t.tongjia_char = '原');

INSERT INTO edu_hanzi_tongjia (tongjia_hanzi_id, tongjia_char, tongjia_note)
SELECT h.hanzi_id, '才', '裁，通「才」，仅仅（《山市》「裁如星点」）'
FROM edu_hanzi h
WHERE h.hanzi_char = '裁'
  AND NOT EXISTS (SELECT 1 FROM edu_hanzi_tongjia t
                  WHERE t.tongjia_hanzi_id = h.hanzi_id AND t.tongjia_char = '才');

INSERT INTO edu_hanzi_tongjia (tongjia_hanzi_id, tongjia_char, tongjia_note)
SELECT h.hanzi_id, '无', '乌，通「无」，没有（《山市》「一切乌有」）'
FROM edu_hanzi h
WHERE h.hanzi_char = '乌'
  AND NOT EXISTS (SELECT 1 FROM edu_hanzi_tongjia t
                  WHERE t.tongjia_hanzi_id = h.hanzi_id AND t.tongjia_char = '无');

INSERT INTO edu_hanzi_tongjia (tongjia_hanzi_id, tongjia_char, tongjia_note)
SELECT h.hanzi_id, '耶', '邪，通「耶」，语气词（《周亚夫军细柳》「可得而犯邪」）'
FROM edu_hanzi h
WHERE h.hanzi_char = '邪'
  AND NOT EXISTS (SELECT 1 FROM edu_hanzi_tongjia t
                  WHERE t.tongjia_hanzi_id = h.hanzi_id AND t.tongjia_char = '耶');

INSERT INTO edu_hanzi_tongjia (tongjia_hanzi_id, tongjia_char, tongjia_note)
SELECT h.hanzi_id, '轼', '式，通「轼」，车前横木（《周亚夫军细柳》「改容式车」）'
FROM edu_hanzi h
WHERE h.hanzi_char = '式'
  AND NOT EXISTS (SELECT 1 FROM edu_hanzi_tongjia t
                  WHERE t.tongjia_hanzi_id = h.hanzi_id AND t.tongjia_char = '轼');

INSERT INTO edu_hanzi_tongjia (tongjia_hanzi_id, tongjia_char, tongjia_note)
SELECT h.hanzi_id, '垄', '陇，通「垄」，田埂（《兵车行》「禾生陇亩无东西」）'
FROM edu_hanzi h
WHERE h.hanzi_char = '陇'
  AND NOT EXISTS (SELECT 1 FROM edu_hanzi_tongjia t
                  WHERE t.tongjia_hanzi_id = h.hanzi_id AND t.tongjia_char = '垄');

INSERT INTO edu_hanzi_tongjia (tongjia_hanzi_id, tongjia_char, tongjia_note)
SELECT h.hanzi_id, '常', '长，通「常」，经常（《酒泉子》「长忆观潮」）'
FROM edu_hanzi h
WHERE h.hanzi_char = '长'
  AND NOT EXISTS (SELECT 1 FROM edu_hanzi_tongjia t
                  WHERE t.tongjia_hanzi_id = h.hanzi_id AND t.tongjia_char = '常');

INSERT INTO edu_hanzi_tongjia (tongjia_hanzi_id, tongjia_char, tongjia_note)
SELECT h.hanzi_id, '否', '不，通「否」（《陌上桑》「宁可共载不」）'
FROM edu_hanzi h
WHERE h.hanzi_char = '不'
  AND NOT EXISTS (SELECT 1 FROM edu_hanzi_tongjia t
                  WHERE t.tongjia_hanzi_id = h.hanzi_id AND t.tongjia_char = '否');

INSERT INTO edu_hanzi_tongjia (tongjia_hanzi_id, tongjia_char, tongjia_note)
SELECT h.hanzi_id, '撼', '感，通「撼」，动摇（《野有死麕》「无感我帨兮」）'
FROM edu_hanzi h
WHERE h.hanzi_char = '感'
  AND NOT EXISTS (SELECT 1 FROM edu_hanzi_tongjia t
                  WHERE t.tongjia_hanzi_id = h.hanzi_id AND t.tongjia_char = '撼');

INSERT INTO edu_hanzi_tongjia (tongjia_hanzi_id, tongjia_char, tongjia_note)
SELECT h.hanzi_id, '宴', '讌，通「宴」，宴饮（《短歌行》「契阔谈讌」）'
FROM edu_hanzi h
WHERE h.hanzi_char = '讌'
  AND NOT EXISTS (SELECT 1 FROM edu_hanzi_tongjia t
                  WHERE t.tongjia_hanzi_id = h.hanzi_id AND t.tongjia_char = '宴');
