-- =====================================================================
-- V2.3__init_edu_collect_flows.sql - 教育语料采集流程扩充
-- 通道: NORMAL（V版本号__任意.sql，同步执行，按「文件名 + 版本号」记账只跑一次）
--
-- ⚠️ 跨库事实（与 V2.1 末尾同一口径，务必先读）：
--   本文件里 6 条 INSERT 操作的是 `collect_flow` —— 采集平台的表。
--   生产部署时 eduai 与采集平台**不共用数据库**（eduai -> eduai 库，采集平台 -> collect 库），
--   框架执行前会查元数据判断目标表是否存在，表不存在则**跳过该语句**
--   （不记失败、不刷错误日志），脚本仍标记为执行成功。
--   本地开发环境是单库（采集 starter 与 eduai 同进程、同库），collect_flow 存在，
--   所以这 6 条流程会在本地库里真正落库、在「采集管理」页可见。
--   若部署到分库环境，把这 6 条拿到 collect 库执行一次即可。
--   与 V2.1 里成语/古诗/汉字三条流程的处置方式完全一致。
--
-- 为什么这批流程要做：
--   采集管理里原来只有「成语 / 古诗 / 汉字」三条教育语料流程，
--   而本轮新增的「词语库」页没有对应采集入口，题库也只有手工录入。
--   本文件补齐：
--     1. 词语采集·汉语词典（ci.json，26MB，约 26 万条）→ edu_word
--     2. 歇后语采集·chinese-xinhua（xiehouyu.json，约 1.4 万条）→ edu_word(kind=XIEHOUYU)
--     3. 题库采集·成语典故（idiom.json，约 3 万条）→ edu_question
--     4. 题库采集·唐诗名句（poet.tang.0.json）→ edu_question
--     5. 题库采集·宋诗名句（poet.song.0.json）→ edu_question
--     6. 成语库补全·近义候选（读库分组回写 edu_idiom.idiom_synonyms）
--
-- 幂等：每条 INSERT 都带 WHERE NOT EXISTS（按 collect_flow_code 判重），
--   collect_flow.collect_flow_code 上有唯一索引，重复执行不会产生第二条。
--   6 条流程一律 collect_flow_status=0（停用）、cron 留空：
--   装完不会自动去抓外网，由用户按需点「立即执行」或设置 cron。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 一、词语采集·汉语词典
--
-- 数据源：chinese-xinhua 的 ci.json（根节点是 JSON 数组，约 26 万条）。
-- 节点链：开始 → 抓取 → 抽取 → 查已存在 → 过滤 → 写入 edu_word → 结束
--
-- 几处必须说清的口径：
--   1) 只用 raw.githubusercontent，不用 jsDelivr：该文件 26MB，
--      jsDelivr 对 >20MB 直接返回 403「File size exceeded the configured limit of 20 MB」。
--   2) 行选择器留空。CollectJson.path 只支持 JSONPath 的子集，**不支持通配 [*]**，
--      根节点是数组时留空即按数组元素逐行拆分（V2.1 里成语流程同一处理）。
--   3) 解析器写 JSONPATH 而不是 JSON：DefaultScriptConverter 对 h2 方言做整篇 SQL 的
--      replaceAll("(?i)\bJSON\b","CLOB")，解析器类型被改写成 CLOB 会让
--      CollectExtractExecutor 直接报错。JSONPATH 匹配不到 \bJSON\b，是安全的。
--   4) 「查已存在 + 过滤」是必需项：output 节点写业务表走纯 INSERT
--      （CollectJdbcSupport.insert，不是 upsert），而 edu_word 上有唯一键
--      uk_edu_word_content，没有这一步第二次执行必然 DuplicateKeyException 整批中止。
--   5) ci.json 不带拼音，故本流程先只写词与释义；拼音由页面手工补齐
--      （没有拼音的词照样能按整词与释义检索）。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '词语采集·汉语词典',
    0,
    'edu_word_ci_xinhua',
    '{"name":"词语采集·汉语词典","threadCount":4,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/ci.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取汉语词典","x":260,"y":160,"params":[{"name":"url","value":"${url[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"600000"},{"name":"retry","value":"3"},{"name":"retryInterval","value":"3000"},{"name":"outputVariable","value":"respCi"}],"fields":[]},{"id":"ext","shape":"extract","name":"抽取词语与释义","x":480,"y":160,"params":[{"name":"source","value":"${respCiJson}"},{"name":"mode","value":"list"},{"name":"outputVariable","value":"rows"}],"fields":[{"name":"ci","parserType":"JSONPATH","expression":"ci"},{"name":"explanation","parserType":"JSONPATH","expression":"explanation"}]},{"id":"sql","shape":"sql","name":"查已存在词语","x":700,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT word_content AS k FROM edu_word"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr","shape":"script","name":"过滤已存在","x":920,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; rows.unique{ it.ci }.findAll{ it.ci && !seen.contains(it.ci) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_word","x":1140,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${ci}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_word"},{"name":"cell:word_content","value":"${ci}"},{"name":"cell:word_meaning","value":"${explanation}"},{"name":"cell:word_kind","value":"WORD"},{"name":"cell:word_grade_level","value":"小学"},{"name":"cell:word_status","value":"1"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1360,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"ext"},{"id":"e3","from":"ext","to":"sql"},{"id":"e4","from":"sql","to":"scr"},{"id":"e5","from":"scr","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    4,
    '词语采集：公开数据集 chinese-xinhua 的 ci.json（约 26 万条词语），按 word_content 去重后写入 edu_word。源里没有拼音与近义/反义/同音词，这几列留空由页面维护；词与释义采集完即可按整词、释义检索。该文件 26MB，只能走 raw.githubusercontent（jsDelivr 对 >20MB 直接 403），一次执行约数分钟。默认停用：请在采集平台设置 cron 后启用，或点「立即执行」取数。注意「试运行」不落库，去重判断不生效。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_word_ci_xinhua'
);

-- ---------------------------------------------------------------------
-- 二、歇后语采集·chinese-xinhua
--
-- 数据源：xiehouyu.json（根节点 JSON 数组，约 1.4 万条），结构是
--   { "riddle": "做砖的坯子、插刀的鞘子", "answer": "框框套套" }
-- 落库口径：整条歇后语拼成「引子——后语」存 word_content（唯一键就是它），
--   word_kind = XIEHOUYU 让词语库页面能按类型过滤；
--   后语单独存进 word_meaning（释义位），引子存 word_structure（结构位）。
--   这样既不改表结构，也不丢原始两段信息。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '歇后语采集·chinese-xinhua',
    0,
    'edu_xiehouyu_xinhua',
    '{"name":"歇后语采集·chinese-xinhua","threadCount":4,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/xiehouyu.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取歇后语数据集","x":260,"y":160,"params":[{"name":"url","value":"${url[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"120000"},{"name":"outputVariable","value":"respXhy"}],"fields":[]},{"id":"ext","shape":"extract","name":"抽取引子与后语","x":480,"y":160,"params":[{"name":"source","value":"${respXhyJson}"},{"name":"mode","value":"list"},{"name":"outputVariable","value":"rows"}],"fields":[{"name":"riddle","parserType":"JSONPATH","expression":"riddle"},{"name":"answer","parserType":"JSONPATH","expression":"answer"}]},{"id":"sql","shape":"sql","name":"查已存在歇后语","x":700,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT word_content AS k FROM edu_word WHERE word_kind = ''XIEHOUYU''"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr","shape":"script","name":"拼整句并过滤","x":920,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; def rows2=rows.findAll{ it.riddle && it.answer }; rows2.collect{ [content: it.riddle + ''——'' + it.answer, riddle: it.riddle, answer: it.answer] }.unique{ it.content }.findAll{ !seen.contains(it.content) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_word（歇后语）","x":1140,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${content}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_word"},{"name":"cell:word_content","value":"${content}"},{"name":"cell:word_meaning","value":"${answer}"},{"name":"cell:word_structure","value":"${riddle}"},{"name":"cell:word_kind","value":"XIEHOUYU"},{"name":"cell:word_grade_level","value":"小学"},{"name":"cell:word_status","value":"1"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1360,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"ext"},{"id":"e3","from":"ext","to":"sql"},{"id":"e4","from":"sql","to":"scr"},{"id":"e5","from":"scr","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    4,
    '歇后语采集：公开数据集 chinese-xinhua 的 xiehouyu.json（约 1.4 万条），把「引子——后语」拼成整句写入 edu_word，word_kind=XIEHOUYU；引子存结构位、后语存释义位，原始两段信息不丢。按整句去重，可反复执行续采。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_xiehouyu_xinhua'
);

-- ---------------------------------------------------------------------
-- 三、题库采集·成语典故
--
-- 数据源：idiom.json（3.6MB，约 3 万条），结构含 word / pinyin / explanation /
--   derivation（出处）/ example（例句）。
-- 落库到 edu_question：每条形成为一道「给释义猜成语」的填空题。
--
-- 为什么用「成语」而不是直接抓某题库网站：
--   公开题库站点多数有反爬与版权限制，抓下来也不保证题目结构完整；
--   成语典故是**结构性完整**的公开语料（释义/出处/例句齐全），
--   转成题后可直接用于组卷与练习，且与既有成语库同一数据源、口径一致。
--
-- 题干与答案：
--   question_stem     = 「下列释义所指的成语是什么？<释义>（出处：<出处>）」
--   question_answer   = 成语本体
--   question_analysis = 例句（有则填）
--   题型 FILL、学科语文、学段小学。
--   注意 question_stem 有唯一键 uk_edu_question_stem，故必须先去重（同题干只留一条）。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '题库采集·成语典故',
    0,
    'edu_question_idiom',
    '{"name":"题库采集·成语典故","threadCount":4,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/idiom.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取成语数据集","x":260,"y":160,"params":[{"name":"url","value":"${url[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"300000"},{"name":"outputVariable","value":"respIdiom"}],"fields":[]},{"id":"ext","shape":"extract","name":"抽取成语字段","x":480,"y":160,"params":[{"name":"source","value":"${respIdiomJson}"},{"name":"mode","value":"list"},{"name":"outputVariable","value":"rows"}],"fields":[{"name":"word","parserType":"JSONPATH","expression":"word"},{"name":"explanation","parserType":"JSONPATH","expression":"explanation"},{"name":"derivation","parserType":"JSONPATH","expression":"derivation"},{"name":"example","parserType":"JSONPATH","expression":"example"}]},{"id":"sql","shape":"sql","name":"查已存在题干","x":700,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT question_stem AS k FROM edu_question WHERE question_source_type = ''CRAWL''"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr","shape":"script","name":"成语转题并过滤","x":920,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; def rows2=rows.findAll{ it.word && it.explanation }; rows2.collect{ def stem=''下列释义所指的成语是什么？'' + it.explanation.toString().take(600) + (it.derivation ? ''（出处：'' + it.derivation.toString().take(200) + ''）'' : ''''); [stem: stem, word: it.word, explanation: it.explanation, example: it.example] }.unique{ it.stem }.findAll{ !seen.contains(it.stem) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_question","x":1140,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${stem}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_question"},{"name":"cell:question_type","value":"FILL"},{"name":"cell:question_subject","value":"语文"},{"name":"cell:question_grade_level","value":"小学"},{"name":"cell:question_grade","value":"一年级"},{"name":"cell:question_difficulty","value":"3"},{"name":"cell:question_score","value":"5"},{"name":"cell:question_stem","value":"${stem}"},{"name":"cell:question_answer","value":"${word}"},{"name":"cell:question_analysis","value":"${example}"},{"name":"cell:question_source","value":"chinese-xinhua/idiom.json"},{"name":"cell:question_source_type","value":"CRAWL"},{"name":"cell:question_audit_status","value":"0"},{"name":"cell:question_status","value":"1"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1360,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"ext"},{"id":"e3","from":"ext","to":"sql"},{"id":"e4","from":"sql","to":"scr"},{"id":"e5","from":"scr","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    4,
    '题库采集：公开数据集 chinese-xinhua 的 idiom.json（约 3 万条成语典故），逐条转成「给释义猜成语」的填空题写入 edu_question（题型 FILL、学科语文、来源类型 CRAWL、审核状态待审核）。题干带出处、解析带例句，老师可在题库管理里逐条审核通过后直接用于组卷。按题干去重（edu_question 的题干有唯一键），可反复执行续采。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_question_idiom'
);

-- ---------------------------------------------------------------------
-- 四、题库采集·唐诗名句
--
-- 数据源：chinese-poetry-zhCN 的 poet.tang.0.json（分片，约 1000 首）。
-- 每首转成一道「补全下句」的填空题：
--   question_stem     = 「补全下句：<第一句> ____（<标题> · <作者>）」
--   question_answer   = 第二句
--   question_analysis = 全诗正文（按行拼接）
--   题型 FILL、学科语文、学段小学。
--
-- 只取段落数 ≥2 的诗（少于两句凑不出「下句」）。
-- 与古诗库流程的区别：古诗库写 edu_poem（供背诵/赏析），本流程写 edu_question（供组卷）。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '题库采集·唐诗名句',
    0,
    'edu_question_tangpoem',
    '{"name":"题库采集·唐诗名句","threadCount":4,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/chinese-poetry/chinese-poetry-zhCN/master/poetry/poet.tang.0.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取全唐诗分片","x":260,"y":160,"params":[{"name":"url","value":"${url[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"300000"},{"name":"outputVariable","value":"respTang"}],"fields":[]},{"id":"ext","shape":"extract","name":"抽取诗词字段","x":480,"y":160,"params":[{"name":"source","value":"${respTangJson}"},{"name":"mode","value":"list"},{"name":"outputVariable","value":"rows"}],"fields":[{"name":"title","parserType":"JSONPATH","expression":"title"},{"name":"author","parserType":"JSONPATH","expression":"author"},{"name":"paragraphs","parserType":"JSONPATH","expression":"paragraphs"}]},{"id":"sql","shape":"sql","name":"查已存在题干","x":700,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT question_stem AS k FROM edu_question WHERE question_source_type = ''CRAWL''"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr","shape":"script","name":"诗句转题并过滤","x":920,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; def rows2=rows.findAll{ it.paragraphs instanceof List && it.paragraphs.size() >= 2 }; rows2.collect{ def first=it.paragraphs[0].toString(); def second=it.paragraphs[1].toString(); def stem=''补全下句：'' + first + '' ____（'' + it.title + '' · '' + it.author + ''）''; [stem: stem, answer: second, full: it.paragraphs.join(''\\\\n'')] }.unique{ it.stem }.findAll{ !seen.contains(it.stem) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_question","x":1140,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${stem}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_question"},{"name":"cell:question_type","value":"FILL"},{"name":"cell:question_subject","value":"语文"},{"name":"cell:question_grade_level","value":"小学"},{"name":"cell:question_grade","value":"一年级"},{"name":"cell:question_difficulty","value":"2"},{"name":"cell:question_score","value":"5"},{"name":"cell:question_stem","value":"${stem}"},{"name":"cell:question_answer","value":"${answer}"},{"name":"cell:question_analysis","value":"${full}"},{"name":"cell:question_source","value":"chinese-poetry/poet.tang.0.json"},{"name":"cell:question_source_type","value":"CRAWL"},{"name":"cell:question_audit_status","value":"0"},{"name":"cell:question_status","value":"1"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1360,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"ext"},{"id":"e3","from":"ext","to":"sql"},{"id":"e4","from":"sql","to":"scr"},{"id":"e5","from":"scr","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    4,
    '题库采集：开源《全唐诗》简体镜像 chinese-poetry-zhCN 的 poet.tang.0.json 分片，把每首前两句转成「补全下句」的填空题写入 edu_question（题型 FILL、学科语文、解析存全诗）。只取段落数 ≥2 的诗。本仓库只灌这一个分片，其余分片自行在开始节点加种子 URL。按题干去重，可反复执行。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_question_tangpoem'
);

-- ---------------------------------------------------------------------
-- 五、题库采集·宋诗名句
--
-- 与唐诗那条同构，换数据源分片 poet.song.0.json（约 2500 首）。
-- 单独一条流程而不是在同一条流程里加两个 URL：
--   两个分片结构一致但体量不同，混在一条里时长与失败面都会互相牵连；
--   分成两条后可以分别「立即执行」，某一条抓失败不影响另一条已入库的数据。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '题库采集·宋诗名句',
    0,
    'edu_question_songpoem',
    '{"name":"题库采集·宋诗名句","threadCount":4,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/chinese-poetry/chinese-poetry-zhCN/master/poetry/poet.song.0.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取宋诗分片","x":260,"y":160,"params":[{"name":"url","value":"${url[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"300000"},{"name":"outputVariable","value":"respSong"}],"fields":[]},{"id":"ext","shape":"extract","name":"抽取诗词字段","x":480,"y":160,"params":[{"name":"source","value":"${respSongJson}"},{"name":"mode","value":"list"},{"name":"outputVariable","value":"rows"}],"fields":[{"name":"title","parserType":"JSONPATH","expression":"title"},{"name":"author","parserType":"JSONPATH","expression":"author"},{"name":"paragraphs","parserType":"JSONPATH","expression":"paragraphs"}]},{"id":"sql","shape":"sql","name":"查已存在题干","x":700,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT question_stem AS k FROM edu_question WHERE question_source_type = ''CRAWL''"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr","shape":"script","name":"诗句转题并过滤","x":920,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; def rows2=rows.findAll{ it.paragraphs instanceof List && it.paragraphs.size() >= 2 }; rows2.collect{ def first=it.paragraphs[0].toString(); def second=it.paragraphs[1].toString(); def stem=''补全下句：'' + first + '' ____（'' + it.title + '' · '' + it.author + ''）''; [stem: stem, answer: second, full: it.paragraphs.join(''\\\\n'')] }.unique{ it.stem }.findAll{ !seen.contains(it.stem) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_question","x":1140,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${stem}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_question"},{"name":"cell:question_type","value":"FILL"},{"name":"cell:question_subject","value":"语文"},{"name":"cell:question_grade_level","value":"小学"},{"name":"cell:question_grade","value":"一年级"},{"name":"cell:question_difficulty","value":"2"},{"name":"cell:question_score","value":"5"},{"name":"cell:question_stem","value":"${stem}"},{"name":"cell:question_answer","value":"${answer}"},{"name":"cell:question_analysis","value":"${full}"},{"name":"cell:question_source","value":"chinese-poetry/poet.song.0.json"},{"name":"cell:question_source_type","value":"CRAWL"},{"name":"cell:question_audit_status","value":"0"},{"name":"cell:question_status","value":"1"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1360,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"ext"},{"id":"e3","from":"ext","to":"sql"},{"id":"e4","from":"sql","to":"scr"},{"id":"e5","from":"scr","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    4,
    '题库采集：开源宋代诗词简体镜像 chinese-poetry-zhCN 的 poet.song.0.json 分片，把每首前两句转成「补全下句」的填空题写入 edu_question（题型 FILL、学科语文、解析存全诗）。只取段落数 ≥2 的诗。与唐诗那条分成两条流程，可分别执行、互不影响。按题干去重，可反复执行。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_question_songpoem'
);

-- ---------------------------------------------------------------------
-- 六、成语库补全·近义候选
--
-- 既有「成语采集·chinese-xinhua」只写了 idiom_content / pinyin / meaning /
-- source / example，近义词与反义词两列一直空着（源里确实没有这两列）。
-- 本流程不动既有流程，改为「读库 → 按释义首句分组 → 回写近义候选」：
--   同组（释义首句相同）的其它成语作为近义候选，最多 3 条。
--   反义不自动推断（语义反义无法可靠推导），留空由老师维护。
--
-- 写库走 UPDATE（需 plugin.collect.sql-allow-write=true），
-- 因此默认停用，由管理员确认后再启用。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '成语库补全·近义候选',
    0,
    'edu_idiom_synonym_fill',
    '{"name":"成语库补全·近义候选","threadCount":1,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"about:blank"}],"fields":[]},{"id":"sql1","shape":"sql","name":"读未配近义的成语","x":260,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT idiom_id AS id, idiom_content AS content, SUBSTRING(idiom_meaning, 1, 12) AS head FROM edu_idiom WHERE (idiom_synonyms IS NULL OR idiom_synonyms = '''') AND idiom_meaning IS NOT NULL AND idiom_meaning <> '''' LIMIT 3000"},{"name":"outputVariable","value":"rows"}],"fields":[]},{"id":"scr","shape":"script","name":"按释义首句分组","x":480,"y":160,"params":[{"name":"script","value":"def groups=(rows?:[]).groupBy{ it.head?.toString()?.trim() }; def out=[]; groups.each{ k, list -> if(list.size() >= 2){ list.each{ r -> def others=list.findAll{ it.id != r.id }.take(3)*.content; if(others){ out << [id: r.id, synonyms: others.join('','')] } } } }; out"},{"name":"outputVariable","value":"updates"}],"fields":[]},{"id":"out","shape":"output","name":"回写近义候选","x":700,"y":160,"params":[{"name":"each","value":"${updates}"},{"name":"key","value":"${id}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_idiom"},{"name":"mode","value":"update"},{"name":"where:idiom_id","value":"${id}"},{"name":"cell:idiom_synonyms","value":"${synonyms}"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":920,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"sql1"},{"id":"e2","from":"sql1","to":"scr"},{"id":"e3","from":"scr","to":"out"},{"id":"e4","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    1,
    '成语库补全：给「近义词」为空、但有释义的成语，按释义首句分组，把同组其它成语作为近义候选回写。只补近义候选，不推断反义（语义反义无法可靠自动推导，反义由老师维护）。写库走 UPDATE，需要 plugin.collect.sql-allow-write=true；默认停用，管理员确认后再启用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_idiom_synonym_fill'
);
