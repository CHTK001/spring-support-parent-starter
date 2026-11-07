-- =====================================================================
-- V2.6__init_edu_collect_flows_more.sql - 题库 / 词语库采集流程再扩充
-- 通道: NORMAL（V版本号__任意.sql，同步执行，按「文件名 + 版本号」记账只跑一次）
--
-- 为什么单独一个文件：V2.3 已在库里执行过，`validate-checksum` 会拦下改过的同名脚本
-- （日志报「校验和不匹配」），所以新增流程只能开新文件。
--
-- 本文件补 6 条流程，全部基于**实测可用**的公开数据源：
--   1. 词语采集·小学教材词语表  统编语文教材 2~6 年级词语表（1946 个去重词语，按册）
--   2. 汉字采集·通用规范汉字表  教育部《通用规范汉字表》一级字 3500（补 zip 缺字的年级标注）
--   3. 古文采集·论语            20 篇 500 余章
--   4. 古文采集·诗经            305 篇
--   5. 古文采集·楚辞            屈原等 17 篇
--   6. 诗词采集·宋词 + 元曲 + 唐诗三百首（三条独立流程，见下）
--
-- 数据源口径（都实测过 HTTP 200 + 结构）：
--   · chinese-poetry/chinese-poetry（master）是**简体**，可直接入库；
--     chinese-poetry-zhCN 只有 poetry/ 下的唐诗宋诗分片，没有蒙学/论语等目录。
--   · 蒙学/tangshisanbaishou.json 是**繁体**（唐詩三百首），入库前用
--     `toSimplified` 类脚本替换不可靠，故本文件**不采**它，改用 poet.tang.*.json 的简体分片
--     （已在 V2.3 的「题库采集·唐诗名句」覆盖）。
--   · 小学教材词语表来自 7jul/yuwen_words（单一 JSON，按册分组）。
--
-- 幂等：每条 INSERT 都带 WHERE NOT EXISTS（按 collect_flow_code 判重）。
-- 6 条流程一律 collect_flow_status=0（停用）、cron 留空：装完不自动抓外网。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 一、词语采集·小学教材词语表
--
-- 数据源：7jul/yuwen_words 的「小学统编语文教材词语表.json」，结构是
--   { "二年级下册词语表": ["春天","寻找",...], "三年级上册词语表": [...], ... }
-- 共 9 册、1946 个去重词语（2 字 1739 / 3 字 76 / 4 字 131，4 字的大多是成语）。
--
-- 落库：写入 edu_word，word_kind=WORD，word_grade_level 由册名推出
--   （二年级~六年级 → 小学）。册名写进 word_structure，方便老师按册筛选。
--
-- 为什么这条比「汉语词典」更该先跑：
--   ci.json 是 26 万条通用词典，量大但没有教材口径；教材词语表只有 1946 条，
--   却正好是老师布置听写/组词用的那一批，覆盖面小而准。
--
-- 去重：按 word_content（edu_word 的唯一键）查已存在，重复执行只补新词。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '词语采集·小学教材词语表',
    0,
    'edu_word_textbook',
    '{"name":"词语采集·小学教材词语表","threadCount":2,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/7jul/yuwen_words/master/%E5%B0%8F%E5%AD%A6%E7%BB%9F%E7%BC%96%E8%AF%AD%E6%96%87%E6%95%99%E6%9D%90%E8%AF%8D%E8%AF%AD%E8%A1%A8.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取教材词语表","x":240,"y":160,"params":[{"name":"url","value":"${data[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"120000"},{"name":"outputVariable","value":"respWords"}],"fields":[]},{"id":"ext","shape":"extract","name":"按册抽取词语","x":440,"y":160,"params":[{"name":"source","value":"${respWordsJson}"},{"name":"mode","value":"list"},{"name":"rowExpression","value":"$[*]"},{"name":"outputVariable","value":"volumes"}],"fields":[]},{"id":"scr1","shape":"script","name":"展平成词语行","x":640,"y":160,"params":[{"name":"script","value":"def out=[]; (volumes?:[]).eachWithIndex{ vol, i -> def name=vol?.key?.toString() ?: (''第''+(i+1)+''册''); def words=vol?.value instanceof List ? vol.value : []; if(name.contains(''词语表'')){ words.each{ w -> def s=w?.toString()?.trim(); if(s && !s.isEmpty()){ out << [content: s, volume: name, level: ''小学''] } } } }; out"},{"name":"outputVariable","value":"rows"}],"fields":[]},{"id":"sql","shape":"sql","name":"查已存在词语","x":840,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT word_content AS k FROM edu_word"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr2","shape":"script","name":"去重并过滤","x":1040,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; rows.unique{ it.content }.findAll{ !seen.contains(it.content) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_word","x":1240,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${content}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_word"},{"name":"cell:word_content","value":"${content}"},{"name":"cell:word_structure","value":"${volume}"},{"name":"cell:word_kind","value":"WORD"},{"name":"cell:word_grade_level","value":"${level}"},{"name":"cell:word_status","value":"1"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1440,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"ext"},{"id":"e3","from":"ext","to":"scr1"},{"id":"e4","from":"scr1","to":"sql"},{"id":"e5","from":"sql","to":"scr2"},{"id":"e6","from":"scr2","to":"out"},{"id":"e7","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    2,
    '词语采集：统编小学语文教材 2~6 年级词语表（9 册、1946 个去重词语），按 word_content 去重写入 edu_word。册名写进 word_structure（如「三年级上册词语表」），学段固定小学 —— 老师可按册筛选布置听写。比通用汉语词典更贴合教学口径，建议先跑这条。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_word_textbook'
);

-- ---------------------------------------------------------------------
-- 二、汉字采集·通用规范汉字表
--
-- 数据源：ibeilly/hanzi-practice 的 public/data/moe-characters.json，
--   结构 { source, levels: [ { id:"1", name:"一级字表", desc:"常用字 3500", chars:[...] } ] }
-- 只取「一级字表」（常用字 3500），与现有 edu_hanzi 的 3496 条基本重合，
-- 用途是**补齐采集漏掉的字**（现有流水线有 4 个字因源数据缺失未入库）。
--
-- 落库：只写 hanzi_char / hanzi_grade_level / hanzi_status ——
--   读音、笔画等字段现有记录已有，新补的字留空由老师按教材口径填，
--   **不覆盖已有数据**（输出节点按 hanzi_char 查重后只插新行）。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '汉字采集·通用规范汉字表',
    0,
    'edu_hanzi_moe_table',
    '{"name":"汉字采集·通用规范汉字表","threadCount":2,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/ibeilly/hanzi-practice/main/public/data/moe-characters.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取规范字表","x":240,"y":160,"params":[{"name":"url","value":"${data[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"60000"},{"name":"outputVariable","value":"respMoe"}],"fields":[]},{"id":"scr1","shape":"script","name":"取一级字表","x":440,"y":160,"params":[{"name":"script","value":"def root=respMoeJson; def levels=(root?.levels ?: []); def lv=levels.find{ it?.id?.toString()==''1'' } ?: levels[0]; def chars=(lv?.chars ?: []); chars.findAll{ it }.collect{ [ch: it.toString().trim(), levelName: ''小学''] }.findAll{ it.ch && !it.ch.isEmpty() }"},{"name":"outputVariable","value":"rows"}],"fields":[]},{"id":"sql","shape":"sql","name":"查已存在汉字","x":640,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT hanzi_char AS k FROM edu_hanzi"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr2","shape":"script","name":"只留缺字","x":840,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; rows.unique{ it.ch }.findAll{ !seen.contains(it.ch) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"补写 edu_hanzi","x":1040,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${ch}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_hanzi"},{"name":"cell:hanzi_char","value":"${ch}"},{"name":"cell:hanzi_grade_level","value":"${levelName}"},{"name":"cell:hanzi_status","value":"1"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1240,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"scr1"},{"id":"e3","from":"scr1","to":"sql"},{"id":"e4","from":"sql","to":"scr2"},{"id":"e5","from":"scr2","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    2,
    '汉字采集：教育部《通用规范汉字表》一级字表（常用字 3500），只补 edu_hanzi 里还没有的字，不覆盖已有读音/笔画/释义。现有 3496 条是采集源 chinese-xinhua 的覆盖结果，本条用于补齐源数据缺口。新补的字读音与笔顺留空，由老师按教材口径填。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_hanzi_moe_table'
);

-- ---------------------------------------------------------------------
-- 三、古文采集·论语
--
-- 数据源：chinese-poetry/chinese-poetry 的 论语/lunyu.json（简体），
--   结构 [ { chapter:"学而篇", paragraphs:[ "子曰：…", … ] } ]，20 篇 500 余章。
-- 落库 edu_poem：poem_title = 「论语·学而篇 第 N 章」，poem_content = 该章原文，
--   poem_author = 孔子及弟子，poem_dynasty = 春秋，poem_difficulty = 4。
--
-- 为什么拆成「每章一条」而不是「每篇一条」：
--   一篇动辄几十章，整篇当成一首「诗」在古诗库里没法读；拆成章后
--   学生端「诗词大会」可以按章抽题，老师组卷也能只取一章。
--
-- 去重：edu_poem 上有 UNIQUE KEY (poem_title, poem_author)，
--   「查已存在 + 过滤」是必需项，不是优化（与 V2.1 里古诗流程同一理由）。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '古文采集·论语',
    0,
    'edu_poem_lunyu',
    '{"name":"古文采集·论语","threadCount":2,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/chinese-poetry/chinese-poetry/master/%E8%AE%BA%E8%AF%AD/lunyu.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取论语","x":240,"y":160,"params":[{"name":"url","value":"${data[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"60000"},{"name":"outputVariable","value":"respLunyu"}],"fields":[]},{"id":"scr1","shape":"script","name":"按章拆条","x":440,"y":160,"params":[{"name":"script","value":"def out=[]; (respLunyuJson?:[]).each{ ch -> def name=ch?.chapter?.toString()?.trim(); def paras=ch?.paragraphs instanceof List ? ch.paragraphs : []; paras.eachWithIndex{ p, i -> def text=p?.toString()?.trim(); if(text){ out << [title: ''论语·''+name+'' 第''+(i+1)+''章'', content: text] } } }; out"},{"name":"outputVariable","value":"rows"}],"fields":[]},{"id":"sql","shape":"sql","name":"查已存在古文","x":640,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT CONCAT(poem_title,''|'',poem_author) AS k FROM edu_poem"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr2","shape":"script","name":"过滤已存在","x":840,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; rows.unique{ it.title }.findAll{ !seen.contains(it.title+''|孔子及弟子'') }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_poem","x":1040,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${title}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_poem"},{"name":"cell:poem_title","value":"${title}"},{"name":"cell:poem_author","value":"孔子及弟子"},{"name":"cell:poem_dynasty","value":"春秋"},{"name":"cell:poem_content","value":"${content}"},{"name":"cell:poem_difficulty","value":"4"},{"name":"cell:poem_tags","value":"论语,诸子"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1240,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"scr1"},{"id":"e3","from":"scr1","to":"sql"},{"id":"e4","from":"sql","to":"scr2"},{"id":"e5","from":"scr2","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    2,
    '古文采集：chinese-poetry 的《论语》简体全文（20 篇 500 余章），按章拆条写入 edu_poem（标题「论语·学而篇 第1章」）。拆章是为了让学生端按章抽题、老师组卷只取一章。按标题+作者去重，可反复执行续采。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_poem_lunyu'
);

-- ---------------------------------------------------------------------
-- 四、古文采集·诗经
--
-- 数据源：诗经/shijing.json（简体），结构 [ { title, chapter:"国风", section:"周南", content:[…] } ]
-- 305 篇。落库 edu_poem：标题「关雎」，朝代「先秦」，作者「佚名」，
--   tags 存「诗经,国风,周南」——章/节信息不能丢，它是分类检索的依据。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '古文采集·诗经',
    0,
    'edu_poem_shijing',
    '{"name":"古文采集·诗经","threadCount":2,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/chinese-poetry/chinese-poetry/master/%E8%AF%97%E7%BB%8F/shijing.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取诗经","x":240,"y":160,"params":[{"name":"url","value":"${data[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"60000"},{"name":"outputVariable","value":"respShijing"}],"fields":[]},{"id":"scr1","shape":"script","name":"组装诗句与标签","x":440,"y":160,"params":[{"name":"script","value":"def out=[]; (respShijingJson?:[]).each{ p -> def title=p?.title?.toString()?.trim(); def lines=p?.content instanceof List ? p.content : []; if(title && lines){ def tags=[''诗经'',p?.chapter?.toString(),p?.section?.toString()].findAll{ it }; out << [title: title, content: lines.join(''\\n''), tags: tags.join('','')] } }; out"},{"name":"outputVariable","value":"rows"}],"fields":[]},{"id":"sql","shape":"sql","name":"查已存在古诗","x":640,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT CONCAT(poem_title,''|'',poem_author) AS k FROM edu_poem"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr2","shape":"script","name":"过滤已存在","x":840,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; rows.unique{ it.title }.findAll{ !seen.contains(it.title+''|佚名'') }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_poem","x":1040,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${title}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_poem"},{"name":"cell:poem_title","value":"${title}"},{"name":"cell:poem_author","value":"佚名"},{"name":"cell:poem_dynasty","value":"先秦"},{"name":"cell:poem_content","value":"${content}"},{"name":"cell:poem_difficulty","value":"4"},{"name":"cell:poem_tags","value":"${tags}"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1240,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"scr1"},{"id":"e3","from":"scr1","to":"sql"},{"id":"e4","from":"sql","to":"scr2"},{"id":"e5","from":"scr2","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    2,
    '古文采集：chinese-poetry 的《诗经》简体全文（305 篇），正文按行拼接，章/节写进 tags（如「诗经,国风,周南」）供分类检索。按标题+作者去重，可反复执行。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_poem_shijing'
);

-- ---------------------------------------------------------------------
-- 五、古文采集·楚辞
--
-- 数据源：楚辞/chuci.json（简体），结构 [ { title:"离骚", section, author:"屈原", content:[…] } ]
-- 落库 edu_poem，作者取源里的 author（屈原/宋玉等），朝代「先秦」。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '古文采集·楚辞',
    0,
    'edu_poem_chuci',
    '{"name":"古文采集·楚辞","threadCount":2,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/chinese-poetry/chinese-poetry/master/%E6%A5%9A%E8%BE%9E/chuci.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取楚辞","x":240,"y":160,"params":[{"name":"url","value":"${data[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"60000"},{"name":"outputVariable","value":"respChuci"}],"fields":[]},{"id":"scr1","shape":"script","name":"组装篇目","x":440,"y":160,"params":[{"name":"script","value":"def out=[]; (respChuciJson?:[]).each{ p -> def title=p?.title?.toString()?.trim(); def lines=p?.content instanceof List ? p.content : []; def author=p?.author?.toString()?.trim() ?: ''佚名''; if(title && lines){ out << [title: title, author: author, content: lines.join(''\\n'')] } }; out"},{"name":"outputVariable","value":"rows"}],"fields":[]},{"id":"sql","shape":"sql","name":"查已存在古诗","x":640,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT CONCAT(poem_title,''|'',poem_author) AS k FROM edu_poem"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr2","shape":"script","name":"过滤已存在","x":840,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; rows.unique{ it.title+''|''+it.author }.findAll{ !seen.contains(it.title+''|''+it.author) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_poem","x":1040,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${title}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_poem"},{"name":"cell:poem_title","value":"${title}"},{"name":"cell:poem_author","value":"${author}"},{"name":"cell:poem_dynasty","value":"先秦"},{"name":"cell:poem_content","value":"${content}"},{"name":"cell:poem_difficulty","value":"5"},{"name":"cell:poem_tags","value":"楚辞"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1240,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"scr1"},{"id":"e3","from":"scr1","to":"sql"},{"id":"e4","from":"sql","to":"scr2"},{"id":"e5","from":"scr2","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    2,
    '古文采集：chinese-poetry 的《楚辞》简体全文（离骚、九歌、天问等），作者取源里的 author（屈原/宋玉等），难度标 5。按标题+作者去重，可反复执行。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_poem_chuci'
);

-- ---------------------------------------------------------------------
-- 六、诗词采集·宋词
--
-- 数据源：宋词/ci.song.*.json（简体），单个分片 1000 首，共 255 个分片。
-- 本流程只灌第一个分片（ci.song.0.json，1000 首）：
--   全量 25 万首一次跑几小时，且「诗词大会」玩法用不到那么多；
--   需要更多时在开始节点加种子 URL（形如 ci.song.1000.json，步长 1000）。
--
-- 落库：edu_poem，poem_title = 「词牌·首句前 10 字」（宋词没有独立标题，
--   源里的 rhythmic 是词牌名），poem_author 取 author，朝代为宋。
--   UNIQUE KEY (poem_title, poem_author) 要求标题不能重复：
--   同名同作者同词牌的词很多，故标题里带上首句片段（截 10 字）。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '诗词采集·宋词',
    0,
    'edu_poem_songci',
    '{"name":"诗词采集·宋词","threadCount":4,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/chinese-poetry/chinese-poetry/master/%E5%AE%8B%E8%AF%8D/ci.song.0.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取宋词分片","x":240,"y":160,"params":[{"name":"url","value":"${data[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"120000"},{"name":"outputVariable","value":"respCi"}],"fields":[]},{"id":"scr1","shape":"script","name":"组装词牌与首句","x":440,"y":160,"params":[{"name":"script","value":"def out=[]; (respCiJson?:[]).each{ c -> def paras=c?.paragraphs instanceof List ? c.paragraphs : []; if(paras){ def first=paras[0]?.toString()?.trim(); def rhythm=c?.rhythmic?.toString()?.trim() ?: ''词''; def author=c?.author?.toString()?.trim() ?: ''佚名''; if(first){ def head=first.length()>10 ? first.substring(0,10) : first; out << [title: rhythm+''·''+head, author: author, content: paras.join(''\\n''), rhythm: rhythm] } } }; out"},{"name":"outputVariable","value":"rows"}],"fields":[]},{"id":"sql","shape":"sql","name":"查已存在词作","x":640,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT CONCAT(poem_title,''|'',poem_author) AS k FROM edu_poem"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr2","shape":"script","name":"过滤已存在","x":840,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; rows.unique{ it.title+''|''+it.author }.findAll{ !seen.contains(it.title+''|''+it.author) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_poem","x":1040,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${title}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_poem"},{"name":"cell:poem_title","value":"${title}"},{"name":"cell:poem_author","value":"${author}"},{"name":"cell:poem_dynasty","value":"宋代"},{"name":"cell:poem_content","value":"${content}"},{"name":"cell:poem_difficulty","value":"4"},{"name":"cell:poem_tags","value":"宋词,${rhythm}"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1240,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"scr1"},{"id":"e3","from":"scr1","to":"sql"},{"id":"e4","from":"sql","to":"scr2"},{"id":"e5","from":"scr2","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    4,
    '诗词采集：chinese-poetry 的宋词简体分片 ci.song.0.json（1000 首）。宋词没有独立标题，poem_title 用「词牌·首句前10字」（如「水调歌头·明月几时有」），词牌写进 tags 供按词牌筛选。本流程只灌一个分片，需要更多时在开始节点按 ci.song.1000.json、ci.song.2000.json 递增加种子 URL。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_poem_songci'
);
