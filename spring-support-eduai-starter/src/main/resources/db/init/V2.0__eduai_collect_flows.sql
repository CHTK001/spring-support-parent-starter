-- =====================================================================
-- V2.0__eduai_collect_flows.sql - 采集平台流程定义 / 旧采集任务下线
-- 通道: NORMAL（V版本号__任意.sql）
--
-- ⚠️ 重要：本文件里的 8 条 collect_flow 语句**在 eduai 库里从来不会执行成功**，
--    这批流程定义数据**目前没有送达采集平台**。原因与后续动作见下。
--
-- 为什么这三个文件不并入 initdata：
--   1) 本文件里的语句操作的是 collect_flow 表 —— 那是采集平台（另一个后端）的表，
--      本仓没有它的建表脚本，属于跨模块数据；
--   2) eduai 的 application.yml 里 plugin.datasource.script.data-scan-mode=ONCE，
--      而 DataSourceScriptConfiguration.filterDataScripts 在 ONCE 且「库已有业务数据」时
--      会把**全部** INITDATA 脚本整体跳过（不是按文件判重）。eduai 库本来就非空，
--      所以写成 initdata_ 的话这些语句永远不会被执行。
--      NORMAL 走 filterMigrationScripts，RepeatableScanMode 默认 ALWAYS，
--      再由 needsExecution（按「文件名+版本」查执行记录）保证只跑一次。
--
-- 幂等：collect_flow 的每条 INSERT 都带 WHERE NOT EXISTS（按 collect_flow_code 判重），
--   采集平台 collect_flow.collect_flow_code 上有唯一索引，重复执行也不会产生第二条。
--
-- ---------------------------------------------------------------------
-- 跨库事实（2026-10 核实）：eduai 与采集平台**不共用数据库**
--   eduai          -> jdbc:mysql://127.0.0.1:3306/eduai
--   采集平台        -> jdbc:mysql://127.0.0.1:3306/collect
--   本仓各模块同样一库一系统（monitor -> report、software -> softdb …）。
--
--   所以本文件里往 collect_flow 插/改的 8 条语句，在 eduai 库里必然因「表不存在」失败：
--   跨库插不进另一个系统的表。这批流程定义**从来没有落到采集平台**。
--
-- 当前处理：框架在执行前会查元数据判断目标表是否存在，
--   表不存在则**跳过该语句**（不记失败、不刷错误日志），
--   脚本仍标记为执行成功。见 DataSourceFlyway#executeScript 的「目标表不存在 → 跳过」。
--   于是本文件在 eduai 库里只执行那条针对本仓表的 DELETE（edu_collect_task），
--   8 条 collect_flow 语句安静跳过。
--
-- 仍需人工处理（数据送达）：把这 8 条语句拿到**采集平台那个库**里执行，
--   流程定义才会真正生效。可行做法二选一：
--   a) 导出本文件里 collect_flow 相关的语句，交给采集平台侧作为其 db/init 种子执行；
--   b) 采集平台侧提供「导入流程定义」的接口/页面，由本仓调用。
--
--   【a) 已经做完了】
--   交付件：D:\ch\_collect-deploy\collect-flow-seed.sql
--     —— 三条流程定义的最终状态（纯 INSERT + ON DUPLICATE KEY UPDATE，
--        不含本文件里那 4 条依赖执行顺序的 UPDATE），在采集平台的库里执行一次即到位。
--     配套说明见 D:\ch\_flyway-verify\deliverables\README.md。
--     已实测：干净的 collect 库上执行 3 条成功、二次执行仍 0 失败，
--     落库的 DSL 与本文件算出的最终结果逐字节相同。
--     尚未验证的是「流程能否真正跑通」（需要采集平台的采集引擎与外网访问）。
--
--   在此之前，采集平台里不会有这三条流程（成语采集 / 全唐诗 / 常用字3500）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 原 V1.15__eduai_collect_flows.sql （已并入本文件）
-- ---------------------------------------------------------------------
-- =====================================================================
-- V1.15__eduai_collect_flows.sql - 教育 AI 采集例子（迁移到采集平台）
--
-- 背景：eduai 的「采集管理」已收敛到采集平台（@pages/collect + /v2/collect/**），
-- 这里把原先以 edu_collect_task 行形式存在的采集例子，改写成采集平台的流程定义。
--
-- 通道：NORMAL（V版本号__任意.sql）。选它而不是 initdata_ 的原因见文件末尾「为什么是 NORMAL」。
--
-- 幂等：每条 INSERT 都带 WHERE NOT EXISTS，按 collect_flow_code 判重。
--       采集平台 collect_flow.collect_flow_code 上有唯一索引，重复执行也不会产生第二条。
--
-- 状态：三条流程一律 collect_flow_status=0（停用）、cron 留空，与原 edu_collect_task
--       的「默认停用，请先设置 cron 或点立即执行」口径一致。装完不会自动去抓外网。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 一、成语采集·chinese-xinhua
--
-- 数据源：公开数据集 chinese-xinhua（jsDelivr CDN），根节点是 JSON 数组。
-- 节点链：开始 → 抓取 → 抽取 → 查已存在 → 过滤 → 写入 edu_idiom → 结束
--
-- 几点与原实现的差异，都是采集平台节点体系的口径决定的，不是等价改写：
--
--  1) 抽取解析器用 JSONPATH 而不是 JSON。
--     CollectExtractExecutor 把 JSON 与 JSONPATH 归到同一个实现（CollectJson.path），
--     行为完全一致。之所以不写 JSON，是因为它是**承载语义**的那一处：
--     utils-support-common-starter 的 DefaultScriptConverter 对 h2 方言做整篇 SQL 的
--     replaceAll("(?i)\bJSON\b","CLOB")，一旦解析器类型被改写成 CLOB，
--     CollectExtractExecutor 会因「无法识别的解析器类型」直接报错——它自己的注释记着
--     这个真实事故（曾经变成「抽取成功、每格全是 null、任务照样 SUCCESS」）。
--     \bJSON\b 匹配不到 JSONPATH，所以这一处是安全的。
--
--     但要说清楚：URL 里的 ".json"（idiom.json / poet.tang.0.json）**躲不掉**这个替换，
--     点号两侧都是非单词字符，\bJSON\b 照样命中，H2 上部署会把地址改坏成 .clob。
--     这是既有事实而非本次引入——db/init/V1.1__initdata_all_eduai.sql 里同一条地址
--     早就躺在 sourceConfig 字符串里了。eduai 当前是 MySQL
--     （application.yml 的 driver-class-name 为 com.mysql.cj.jdbc.Driver），
--     转换器的 json→clob 分支只在 h2 方言生效，本次不受影响。
--     若将来要支持 H2/Oracle 部署，得先改转换器让类型名替换跳过字符串字面量，
--     光改这里的 DSL 没用。
--
--  2) 行选择器留空。原实现用 JSONPath 通配 "$[*].word" 展平，
--     但 CollectJson.path 明确只支持 JSONPath 的一个子集，**不支持通配 [*]**（实测返回 null）。
--     根节点是数组时，抽取节点留空行选择器即按数组元素逐行拆分，等价且更稳。
--
--  3) 去重改由「查已存在 + 脚本过滤」两步完成，采集平台没有逐行迭代节点
--     （EACH 参数只有 CollectOutputExecutor 一个有），dedup 节点是按分支判重、
--     放在抽取与输出之间对每一行都不生效；而 output 节点写业务表走的是纯 INSERT
--     （CollectJdbcSupport.insert，不是 upsert），edu_idiom 又没有唯一约束，
--     重跑会直接灌出重复行。这一步是把原 onConflict=skip 的语义补回来。
--
--  4) 原实现的单次 limit=500 没有了。采集平台按单任务记录上限截断
--     （plugin.collect.max-record-per-task，默认 100000），成语数据集约 3 万条，
--     一次即可灌完。若要限流请在流程里加条件节点，不要指望这个上限。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '成语采集·chinese-xinhua',
    0,
    'edu_idiom_chinese_xinhua',
    '{"name":"成语采集·chinese-xinhua","threadCount":4,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://cdn.jsdelivr.net/gh/pwxcoo/chinese-xinhua@master/data/idiom.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取成语数据集","x":260,"y":160,"params":[{"name":"url","value":"${url[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"60000"}],"fields":[]},{"id":"ext","shape":"extract","name":"抽取成语字段","x":480,"y":160,"params":[{"name":"source","value":"${respJson}"},{"name":"mode","value":"list"},{"name":"outputVariable","value":"rows"}],"fields":[{"name":"word","parserType":"JSONPATH","expression":"word"},{"name":"pinyin","parserType":"JSONPATH","expression":"pinyin"},{"name":"explanation","parserType":"JSONPATH","expression":"explanation"},{"name":"derivation","parserType":"JSONPATH","expression":"derivation"},{"name":"example","parserType":"JSONPATH","expression":"example"}]},{"id":"sql","shape":"sql","name":"查已存在成语","x":700,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT idiom_content AS k FROM edu_idiom"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr","shape":"script","name":"过滤已存在","x":920,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; rows.findAll{ !seen.contains(it.word) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_idiom","x":1140,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${word}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_idiom"},{"name":"cell:idiom_content","value":"${word}"},{"name":"cell:idiom_pinyin","value":"${pinyin}"},{"name":"cell:idiom_meaning","value":"${explanation}"},{"name":"cell:idiom_source","value":"${derivation}"},{"name":"cell:idiom_example","value":"${example}"},{"name":"cell:idiom_difficulty","value":"3"},{"name":"cell:idiom_tags","value":""}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1360,"y":160,"params":[],"fields":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"ext"},{"id":"e3","from":"ext","to":"sql"},{"id":"e4","from":"sql","to":"scr"},{"id":"e5","from":"scr","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    4,
    '成语采集：公开数据集 chinese-xinhua，按 idiom_content 去重后写入 edu_idiom。与原 edu_collect_task 行的等价物，源与字段映射一一对应。默认停用：请在采集平台设置 cron 后启用，或点「立即执行」取数。注意「试运行」不落库，去重判断不生效，重跑请用正式执行，否则会在 edu_idiom 灌出重复行。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_idiom_chinese_xinhua'
);

-- ---------------------------------------------------------------------
-- 二、古诗采集·全唐诗
--
-- 数据源：开源《全唐诗》简体镜像 chinese-poetry-zhCN（poet.tang.0.json），根节点是 JSON 数组。
-- 节点链：开始 → 抓取 → 抽取 → 查已存在 → 过滤 → 写入 edu_poem → 结束
--
-- 比成语那条多一处关键处理：正文 paragraphs 是**数组**。
-- output 节点的字段值最终经 CollectFunctions.stringify 落库，直接给数组会被写成
-- JSON 数组文本（["白日依山尽","黄河入海流"]），而 edu_poem.poem_content 要的是按行分隔的纯文本。
-- 这里用表达式 ${join(paragraphs, '\n')} 在落库前拼好。
--
-- 转义层级（已实测核对，不要随手改反斜杠个数）：
--   表达式里要拿到 1 个反斜杠 + n
--   → DSL 的 JSON 文本里写 2 个（JSON 转义）
--   → 本文件是 MySQL 单引号字面量，再翻倍成 4 个
-- 若写成 2 个，落库后表达式会拿到字面量 n，正文会全部挤成一行且无换行。
--
-- 保留原实现的两点口径：
--   1) poem_dynasty 固定「唐代」——该数据集不含朝代字段；
--   2) 仍不映射 strains——源里的 strains 是「平仄标注数组」不是诗体名，
--      映射到 poem_tags 会写进一堆噪声。
--
-- edu_poem 上有 UNIQUE KEY (poem_title, poem_author)，且 V1.2 建表注释记过一个实案：
-- 长标题变体会让前缀唯一索引「既查不到、又插不进」，触发 DuplicateKeyException 令整批中止。
-- 所以这里的「查已存在 + 过滤」不是可选项，去掉它第二次执行必然整批失败。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '古诗采集·全唐诗',
    0,
    'edu_poem_chinese_poetry_tang',
    '{"name":"古诗采集·全唐诗","threadCount":4,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://cdn.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.0.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取全唐诗分片","x":260,"y":160,"params":[{"name":"url","value":"${url[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"60000"}],"fields":[]},{"id":"ext","shape":"extract","name":"抽取诗词字段","x":480,"y":160,"params":[{"name":"source","value":"${respJson}"},{"name":"mode","value":"list"},{"name":"outputVariable","value":"rows"}],"fields":[{"name":"title","parserType":"JSONPATH","expression":"title"},{"name":"author","parserType":"JSONPATH","expression":"author"},{"name":"paragraphs","parserType":"JSONPATH","expression":"paragraphs"}]},{"id":"sql","shape":"sql","name":"查已存在古诗","x":700,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT CONCAT(poem_title,''|'',poem_author) AS k FROM edu_poem"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr","shape":"script","name":"过滤已存在","x":920,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; rows.unique{ it.title+''|''+it.author }.findAll{ !seen.contains(it.title+''|''+it.author) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_poem","x":1140,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${title}|${author}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_poem"},{"name":"cell:poem_title","value":"${title}"},{"name":"cell:poem_author","value":"${author}"},{"name":"cell:poem_content","value":"${join(paragraphs, ''\\\\n'')}"},{"name":"cell:poem_dynasty","value":"唐代"},{"name":"cell:poem_difficulty","value":"3"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1360,"y":160,"params":[],"fields":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"ext"},{"id":"e3","from":"ext","to":"sql"},{"id":"e4","from":"sql","to":"scr"},{"id":"e5","from":"scr","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    4,
    '古诗采集：开源《全唐诗》简体镜像 chinese-poetry-zhCN 的 poet.tang.0.json 分片，按 标题+作者 去重后写入 edu_poem。正文 paragraphs 数组已用 join 拼成按行分隔的纯文本。本仓库只灌了这一个分片，其余分片需自行在开始节点加种子 URL。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_poem_chinese_poetry_tang'
);

-- ---------------------------------------------------------------------
-- 三、汉字采集·常用字3500
--
-- 数据源：三个公开数据集交叉关联，缺一不可——
--   字表  3500 常用汉字，决定采集顺序
--   IDS   makemeahanzi dictionary.txt（JSONL），部首补缺与间架结构的唯一来源
--   释义  chinese-xinhua word.json，拼音/笔画/部首/释义的来源
--
-- 节点链：开始 → 抓字表 → 抓 IDS → 抓字典 → 抽取 → 查已存在 → 关联清洗 → 写入 edu_hanzi → 结束
--
-- 与成语/古诗两条的差异，都是这条流程本身的形状决定的：
--
--   1) 三个源串成一条链，而不是从开始节点分三路并发。
--      CollectNode 有 inDegree、汇聚节点据此判断是否所有前驱都已到达，
--      机制上是支持的，但多前驱时变量如何合并没有可执行的验证手段
--      （本轮拿不到能跑起来的采集引擎）。线性链只有一个前驱，
--      变量在同一条作用域里顺序累积，是唯一不必赌的拓扑。
--      代价是三次抓取串行，总耗时相加——数据量不大，可以接受。
--
--   2) 字表和 IDS 走 ${respXxx.html} 而不是 ${respXxxJson}。
--      CollectRequestExecutor 只在响应正文以 { 或 [ 开头时才额外产出 respJson。
--      IDS 是 JSONL（每行一个 JSON 对象），整份文本以 { 开头，
--      于是它会拿 40MB 去调 CollectJson.parseAny，解析失败后 respJson 根本不存在；
--      字表是纯文本，连尝试都不会发生。只有 word.json 是真正的 JSON 数组，respDictJson 可用。
--
--   3) 三个抓取节点都设 saveDocument=0。字表约 3500 字、IDS 约 40MB、word.json 约 27MB，
--      建 jsoup 文档纯属浪费内存；下游只用 resp.html 和 respDictJson，都不依赖文档对象。
--
--   4) 「查已存在 + 过滤」同样是必需，不是优化。edu_hanzi 上有
--      UNIQUE KEY uk_edu_hanzi_char (hanzi_char)（见 db/init/V1.9__init_all_eduai.sql），
--      而 output 节点写业务表走的是纯 INSERT（CollectJdbcSupport.insert，不是 upsert），
--      没有这一步第二次执行必然 DuplicateKeyException 整批中止。
--
--   5) 解析器仍用 JSONPATH 而不是 JSON，理由与成语那条完全相同：
--      DefaultScriptConverter 对 h2 方言做整篇 SQL 的 replaceAll("(?i)\\bJSON\\b","CLOB")，
--      解析器类型被改写会让 CollectExtractExecutor 直接报错。详见第一节的注释。
--
-- 关于脚本节点里的那段 Groovy：
--
--   逻辑是原 HanziQuestionSourceProvider 的逐函数移植（radicalOf 的三级回落、
--   structureOf 的 IDS 首字符判定、normalizePinyin 的 -ng 修正、
--   condenseMeaning 的 90 字截断加组词、cleanText/usable 的清洗与空壳剔除），
--   不是重写。迁移前做了两轮验证，结论是它与原实现等价：
--     - 函数级对拍：反射调用 eduai 生产代码的私有静态方法作 oracle，
--       78 组样本逐条比对全部一致；
--     - 组装段端到端：按 script 节点的真实执行方式（GroovyShell + 变量绑定）跑完整脚本，
--       校验三源关联、跨源回落、查重过滤、9 行产出。
--   样本里刻意包含了真值分支——只挑期望 false 的样本会让「判据恒为假」这类 bug 蒙混过关
--   （第一次就是栽在这：对拍全绿但所有释义都算成了空）。
--
--   脚本刻意不含任何正则：strip 首尾标点用的是字符扫描，等价性由上面的对拍覆盖。
--   原因很实际——脚本存在 SQL 的单引号字面量里，正则里每个反斜杠都要在文件里写成 4 个
--   才能落库后还原；去掉正则后整段脚本零反斜杠，可读性和可核对性都更好。
--
--   改动这份脚本时请重跑对拍，不要凭肉眼判断。
--
-- 旧路径已清理：edu_collect_task 相关的控制器、服务、模型、mapper、provider、
-- JobHandler、11 个 Groovy 脚本，以及对应的前端页面与 API 均已删除，
-- META-INF/extensions 下那份 QuestionSourceProvider 的 SPI 注册文件也一并删除。
-- 所以本文件里的流程是汉字采集**唯一**的入口，没有并存的老路径。
--
-- 注意：edu_collect_task / edu_collect_log 两张表本身仍然建着（建表语句在
-- db/init/V1.2__init_all_eduai.sql），种子行也仍在 V1.1 与 V1.12 里。
-- 本仓库没有 DROP TABLE 的先例，且这两张表已无任何代码引用，
-- 故按现状保留——表留着无害，留着的目的是让回退代码时有据可依。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '汉字采集·常用字3500',
    0,
    'edu_hanzi_3500',
    '{"name":"汉字采集·常用字3500","threadCount":4,"headers":[],"cookies":[],"variables":[{"name":"urlChars","value":"https://raw.githubusercontent.com/elephantnose/characters/master/3500%E5%B8%B8%E7%94%A8%E6%B1%89%E5%AD%97.txt","description":"现代汉语常用字表一级 3500 字，决定采集顺序"},{"name":"urlIds","value":"https://raw.githubusercontent.com/skishore/makemeahanzi/master/dictionary.txt","description":"makemeahanzi 部件分解 JSONL，部首补缺与间架结构来源"},{"name":"urlDict","value":"https://ghfast.top/https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/word.json","description":"chinese-xinhua 单字字典，拼音/笔画/部首/释义来源"}],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"${urlChars}\\n${urlIds}\\n${urlDict}","description":"三源种子，节点本身不发请求，只写变量"}],"fields":[]},{"id":"reqChars","shape":"request","name":"抓取常用字表","x":260,"y":160,"params":[{"name":"url","value":"${urlChars}","description":"常用字表"},{"name":"method","value":"GET","description":"方法"},{"name":"saveDocument","value":"0","description":"纯文本，不建 jsoup 文档省内存"},{"name":"timeout","value":"60000","description":"超时毫秒"},{"name":"outputVariable","value":"respChars","description":"出参变量名"}],"fields":[]},{"id":"reqIds","shape":"request","name":"抓取 IDS 部件分解","x":480,"y":160,"params":[{"name":"url","value":"${urlIds}","description":"IDS 源"},{"name":"method","value":"GET","description":"方法"},{"name":"saveDocument","value":"0","description":"纯文本，不建 jsoup 文档省内存"},{"name":"timeout","value":"300000","description":"超时毫秒，源文件约 40MB"},{"name":"outputVariable","value":"respIds","description":"出参变量名"}],"fields":[]},{"id":"reqDict","shape":"request","name":"抓取单字字典","x":700,"y":160,"params":[{"name":"url","value":"${urlDict}","description":"单字字典"},{"name":"method","value":"GET","description":"方法"},{"name":"saveDocument","value":"0","description":"纯文本，不建 jsoup 文档省内存"},{"name":"timeout","value":"600000","description":"超时毫秒，源文件约 27MB"},{"name":"retry","value":"4","description":"ghfast 偶发 502，重试 4 次"},{"name":"retryInterval","value":"2000","description":"重试间隔毫秒"},{"name":"outputVariable","value":"respDict","description":"出参变量名"}],"fields":[]},{"id":"extDict","shape":"extract","name":"抽取字典字段","x":920,"y":160,"params":[{"name":"source","value":"${respDictJson}","description":"根节点是 JSON 数组，留空行选择器即逐元素拆行"},{"name":"mode","value":"list","description":"逐行"},{"name":"outputVariable","value":"dictRows","description":"出参变量名"}],"fields":[{"name":"word","parserType":"JSONPATH","expression":"word"},{"name":"pinyin","parserType":"JSONPATH","expression":"pinyin"},{"name":"strokes","parserType":"JSONPATH","expression":"strokes"},{"name":"radicals","parserType":"JSONPATH","expression":"radicals"},{"name":"more","parserType":"JSONPATH","expression":"more"},{"name":"explanation","parserType":"JSONPATH","expression":"explanation"}]},{"id":"sql","shape":"sql","name":"查已存在汉字","x":1140,"y":160,"params":[{"name":"datasource","value":"app","description":"数据源"},{"name":"statement","value":"SELECT hanzi_char AS k FROM edu_hanzi","description":"已入库的字"},{"name":"outputVariable","value":"existing","description":"出参变量名"}],"fields":[]},{"id":"scr","shape":"script","name":"三源关联与字段清洗","x":1360,"y":160,"params":[{"name":"script","value":"// 汉字采集：从字表 + IDS(JSONL) + 单字字典(word.json) 三源关联出 edu_hanzi 行。\\n//\\n// 与 eduai HanziQuestionSourceProvider 的 toRow/radicalOf/structureOf/normalizePinyin/\\n// condenseMeaning/cleanText/usable 逐条等价，由 HanziDiff 对拍验证。\\n//\\n// 输入绑定（由上游节点产出）：\\n//   respChars : Map   抓取响应，respChars.html 为字表纯文本\\n//   respIds   : Map   抓取响应，respIds.html 为 IDS JSONL 文本\\n//   dictRows  : List  抽取节点从 word.json 切出的行，每行含 word/pinyin/strokes/radicals/more/explanation\\n//   existing  : List  sql 节点查出的已入库字，元素含 k\\n// 输出：List<Map>，写到 outputVariable\\n\\ndef HEAD_LIMIT = 90\\ndef MIN_MEANING_CORE_CHARS = 8\\ndef MARKERS = [''同本义'', ''又如'', ''--《'']\\n// Groovy 里 90 / 2 得 BigDecimal，Math.intDiv 又被 GDK 拦走，这里显式取整\\ndef MARKER_MIN = (HEAD_LIMIT / 2).intValue()\\ndef CUT_MIN = (HEAD_LIMIT / 3).intValue()\\n\\n// 码点归一。Groovy 遍历 char[] 时拿到的是 Character，用带类型的闭包参数（如 { int c -> }）\\n// 会直接失配抛 MissingMethodException，所以统一走这个入口取 int。\\ndef toInt = { v ->\\n    if (v instanceof Number) return ((Number) v).intValue()\\n    if (v instanceof Character) return ((Character) v).charValue()\\n    return Integer.parseInt(v.toString())\\n}\\n\\n// 基本区汉字判定（对齐 Java isHanzi 的 0x4E00..0x9FFF）\\ndef isHanzi = { v -> int c = toInt(v); c >= 0x4E00 && c <= 0x9FFF }\\n\\n// 部首/结构用到的 IDS 首字符映射（对齐 Java structureOf 的 switch）\\ndef STRUCTURE_MAP = [\\n    ''⿰'': ''左右'', ''⿱'': ''上下'', ''⿲'': ''左中右'', ''⿳'': ''上中下'',\\n    ''⿴'': ''全包围'', ''⿵'': ''半包围'', ''⿶'': ''半包围'', ''⿷'': ''半包围'',\\n    ''⿹'': ''半包围'', ''⿺'': ''半包围''\\n]\\n\\ndef trim8 = { Object s ->\\n    if (s == null) return null\\n    def t = s.toString().trim()\\n    t.length() <= 8 ? t : t.substring(0, 8)\\n}\\n\\n// 归一化读音：-ng 的 g 写作 U+0261，不修会得到 dīnɡ；非拉丁字符一律丢弃\\ndef LATIN = ''abcdefghijklmnopqrstuvwxyz''.toSet()\\ndef TONAL = ''üàáâãäåèéêëìíîïòóôõöùúûüāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ''.toSet()\\ndef normalizePinyin = { Object raw ->\\n    if (raw == null) return null\\n    def text = raw.toString()\\n    if (text.isBlank()) return null\\n    def sb = new StringBuilder(text.length())\\n    for (int i = 0; i < text.length(); i++) {\\n        int c = Character.toLowerCase(text.charAt(i))\\n        // 注意：Groovy 里 `''g'' as int` 会走 String->Integer 数值转换而抛异常，必须按字符处理\\n        String s = (c == 0x0261) ? ''g'' : Character.toString((char) c)\\n        if (LATIN.contains(s) || TONAL.contains(s)) {\\n            sb.append(s)\\n        }\\n    }\\n    def cleaned = sb.toString().trim()\\n    cleaned.isEmpty() ? null : cleaned\\n}\\n\\n// 排版分隔符（长横线、破折号、省略号、间隔号）\\ndef isSeparator = { v -> int c = toInt(v); (c >= 0x2500 && c <= 0x257F) || c == 0x2014 || c == 0x2026 || c == 0x00B7 }\\n\\ndef cleanText = { Object raw ->\\n    if (raw == null) return null\\n    def text = raw.toString()\\n    if (text.isBlank()) return null\\n    def normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)\\n    def sb = new StringBuilder(normalized.length())\\n    boolean space = false\\n    for (int i = 0; i < normalized.length(); i++) {\\n        char ch = normalized.charAt(i)\\n        if (Character.isWhitespace(ch) || isSeparator(ch)) {\\n            space = true\\n            continue\\n        }\\n        if (space && sb.length() > 0) sb.append('' '' as char)\\n        space = false\\n        sb.append(ch)\\n    }\\n    def cleaned = sb.toString().trim()\\n    cleaned.isEmpty() ? null : cleaned\\n}\\n\\n// 释义是否可用：排除谚文/假名条目，以及去掉标点后几乎没有汉字的空壳。\\n// 这里刻意用下标循环而不是 char[].count{}——Groovy 对原始 char[] 的闭包元素派发\\n// 在 true 分支上不可靠（实测 count{} 恒返回 0），会让所有释义被判为不可用。\\ndef usable = { String text ->\\n    if (text == null) return false\\n    int core = 0\\n    for (int i = 0; i < text.length(); i++) {\\n        int c = toInt(text.charAt(i))\\n        if ((c >= 0xAC00 && c <= 0xD7AF) || (c >= 0x3040 && c <= 0x30FF)) return false\\n        if (c >= 0x4E00 && c <= 0x9FFF) core++\\n    }\\n    return core >= MIN_MEANING_CORE_CHARS\\n}\\n\\n// 去掉首尾的标点与空白。\\n// 原实现用一条 replaceAll 正则去掉首尾的标点与空白，这里改成等价的字符扫描（正则见 git 历史）：\\n// DSL 的 script 存在 SQL 的单引号字面量里，正则的每个反斜杠都要翻倍成 4 个才能落库后还原，\\n// 去掉正则后整段脚本零转义，可读性和可核对性都更好。\\ndef EDGE_PUNCT = ''，,、;；''.toSet()\\ndef stripEdges = { String s ->\\n    int b = 0\\n    int e = s.length()\\n    while (b < e && (EDGE_PUNCT.contains(s.substring(b, b + 1)) || Character.isWhitespace(s.charAt(b)))) b++\\n    while (e > b && (EDGE_PUNCT.contains(s.substring(e - 1, e)) || Character.isWhitespace(s.charAt(e - 1)))) e--\\n    return s.substring(b, e)\\n}\\n\\ndef condenseMeaning = { Object rawExplanation ->\\n    def text = cleanText(rawExplanation)\\n    if (text == null || text.isEmpty()) return null\\n    if (text.length() <= HEAD_LIMIT) return usable(text) ? text : null\\n    def window = text.substring(0, HEAD_LIMIT)\\n    int cut = -1\\n    for (marker in MARKERS) {\\n        int idx = window.indexOf(marker)\\n        if (idx >= MARKER_MIN && (cut < 0 || idx < cut)) cut = idx\\n    }\\n    if (cut < 0) {\\n        cut = Math.max(Math.max(window.lastIndexOf(''。''), window.lastIndexOf(''；'')),\\n                Math.max(window.lastIndexOf(''，''), window.lastIndexOf('','')))\\n    }\\n    if (cut < CUT_MIN) cut = HEAD_LIMIT\\n    def head = window.substring(0, Math.min(cut, window.length())).trim()\\n    int wordIdx = text.indexOf(''又如'')\\n    if (wordIdx >= 0) {\\n        int end = wordIdx + 2\\n        while (end < text.length() && end - wordIdx < 62 && !''。；;''.contains(text.substring(end, end + 1))) end++\\n        def words = text.substring(wordIdx + 2, Math.min(end, text.length())).trim()\\n        if (!words.isEmpty()) head = head + ''；组词：'' + words\\n    }\\n    head = stripEdges(head)\\n    return usable(head) ? head : null\\n}\\n\\n// 由 IDS 部件分解推导间架结构；不可靠时留空（宁可留空也不给错数据）\\ndef structureOf = { Object rawDecomp ->\\n    if (rawDecomp == null) return null\\n    def decomp = rawDecomp.toString().trim()\\n    if (decomp.isEmpty()) return null\\n    if (decomp.indexOf(''？'') >= 0) return null\\n    if (decomp.length() == 1 && isHanzi(decomp.charAt(0))) return ''独体''\\n    return STRUCTURE_MAP[decomp.substring(0, 1)]\\n}\\n\\n// 部首三级回落：word.json radicals -> word.json more 里的「部首 X」-> IDS radical\\ndef radicalOf = { Map entry, Map idsEntry ->\\n    def r = entry?.get(''radicals'')\\n    if (r != null && !r.toString().isBlank()) return trim8(r)\\n    def more = entry?.get(''more'')\\n    if (more != null) {\\n        def moreText = more.toString()\\n        int idx = moreText.indexOf(''部首'')\\n        if (idx >= 0) {\\n            def tail = moreText.substring(idx + 2).trim()\\n            if (!tail.isEmpty()) {\\n                int end = 0\\n                while (end < tail.length() && !Character.isWhitespace(tail.charAt(end))) end++\\n                if (end > 0) return trim8(tail.substring(0, end))\\n            }\\n        }\\n    }\\n    def ir = idsEntry?.get(''radical'')\\n    if (ir != null && !ir.toString().isBlank()) return trim8(ir)\\n    return null\\n}\\n\\n// ==== END_LIB ====\\n// 以上为纯函数区（无副作用、可单测）。HanziDiff 以本行为界切出这一段做与\\n// eduai HanziQuestionSourceProvider 的对拍；生产路径整段执行。\\n\\n// ---- 组装 ----\\n\\n// 字表：去重、保序\\ndef wanted = []\\ndef seenChar = [] as Set\\ndef charsText = respChars?.get(''html'')?.toString() ?: ''''\\nfor (int i = 0; i < charsText.length(); i++) {\\n    char ch = charsText.charAt(i)\\n    if (!isHanzi(ch) || seenChar.contains(ch)) continue\\n    seenChar.add(ch)\\n    wanted.add(Character.toString(ch))\\n}\\n\\n// IDS：JSONL，一行一个对象\\ndef slurper = new groovy.json.JsonSlurper()\\ndef idsMap = [:]\\ndef idsText = respIds?.get(''html'')?.toString() ?: ''''\\n// 用 readLines 而不是按换行符切分：既能正确吃掉 CRLF（源文件是 Windows 换行也不怕），\\n// 也让整段脚本不含任何反斜杠——脚本存在 SQL 单引号字面量里，每个反斜杠都要翻倍成 4 个才能还原\\nidsText.readLines().each { line ->\\n    def t = line.trim()\\n    if (t.isEmpty()) return\\n    try {\\n        def node = slurper.parseText(t)\\n        if (!(node instanceof Map)) return\\n        def character = node.get(''character'')\\n        if (character == null || character.toString().length() != 1) return\\n        def ch = character.toString()\\n        if (!isHanzi(ch.charAt(0)) || !seenChar.contains(ch.charAt(0)) || idsMap.containsKey(ch)) return\\n        def pinyin = node.get(''pinyin'')\\n        def first = (pinyin instanceof List) ? (pinyin.isEmpty() ? null : pinyin.get(0)) : pinyin\\n        def strokes = node.get(''strokes'')\\n        def strokeText = (strokes == null) ? '''' : strokes.toString()\\n        idsMap.put(ch, [radical: node.get(''radical''), decomposition: node.get(''decomposition''),\\n                        pinyin: first, strokes: (strokeText.isInteger() ? strokeText.toInteger() : null)])\\n    } catch (Exception ignored) {\\n        // 源文件里混有非 JSON 行，跳过即可\\n    }\\n}\\n\\n// 单字字典：只留字表要的字\\ndef dictMap = [:]\\n(dictRows ?: []).each { row ->\\n    def w = row?.get(''word'')\\n    if (w == null) return\\n    def word = w.toString()\\n    if (word.length() != 1) return\\n    if (!isHanzi(word.charAt(0)) || !seenChar.contains(word.charAt(0)) || dictMap.containsKey(word)) return\\n    dictMap.put(word, row)\\n}\\n\\ndef existingKeys = ((existing ?: []).collect { it?.get(''k'') }).findAll { it != null }.collect { it.toString() } as Set\\n\\ndef rows = []\\nwanted.each { ch ->\\n    if (existingKeys.contains(ch)) return\\n    def entry = dictMap.get(ch)\\n    if (entry == null) return\\n    def ids = idsMap.get(ch)\\n\\n    def pinyin = normalizePinyin(entry.get(''pinyin''))\\n    if (pinyin == null || pinyin.isBlank()) pinyin = normalizePinyin(ids?.get(''pinyin''))\\n\\n    def strokes = entry.get(''strokes'')\\n    def strokeText = (strokes == null) ? '''' : strokes.toString()\\n    if (strokeText.isInteger()) {\\n        strokes = strokeText.toInteger()\\n    } else {\\n        strokes = ids?.get(''strokes'')\\n    }\\n\\n    rows.add([\\n        hanzi_char: ch,\\n        hanzi_pinyin: pinyin,\\n        hanzi_strokes: strokes,\\n        hanzi_radical: radicalOf(entry, ids),\\n        hanzi_structure: structureOf(ids?.get(''decomposition'')),\\n        hanzi_meaning: condenseMeaning(entry.get(''explanation'')),\\n        hanzi_grade_level: ''小学'',\\n        hanzi_status: 1\\n    ])\\n}\\nrows\\n","description":"由 eduai HanziQuestionSourceProvider 逐函数对拍迁移而来，勿随手改"},{"name":"outputVariable","value":"fresh","description":"出参变量名"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_hanzi","x":1580,"y":160,"params":[{"name":"each","value":"${fresh}","description":"逐行写入的数据"},{"name":"key","value":"${hanzi_char}","description":"采集结果表去重键"},{"name":"datasource","value":"app","description":"数据源"},{"name":"targetTable","value":"edu_hanzi","description":"目标业务表"},{"name":"cell:hanzi_char","value":"${hanzi_char}","description":"汉字"},{"name":"cell:hanzi_pinyin","value":"${hanzi_pinyin}","description":"读音"},{"name":"cell:hanzi_strokes","value":"${hanzi_strokes}","description":"笔画数"},{"name":"cell:hanzi_radical","value":"${hanzi_radical}","description":"部首"},{"name":"cell:hanzi_structure","value":"${hanzi_structure}","description":"间架结构"},{"name":"cell:hanzi_meaning","value":"${hanzi_meaning}","description":"释义"},{"name":"cell:hanzi_grade_level","value":"小学","description":"适用学段，源里没有，固定小学"},{"name":"cell:hanzi_status","value":"1","description":"状态，固定启用"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1800,"y":160,"params":[],"fields":[]}],"edges":[{"id":"e1","from":"start","to":"reqChars"},{"id":"e2","from":"reqChars","to":"reqIds"},{"id":"e3","from":"reqIds","to":"reqDict"},{"id":"e4","from":"reqDict","to":"extDict"},{"id":"e5","from":"extDict","to":"sql"},{"id":"e6","from":"sql","to":"scr"},{"id":"e7","from":"scr","to":"out"},{"id":"e8","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    4,
    '汉字采集：常用字3500 字表 + makemeahanzi IDS 部件分解 + chinese-xinhua 单字字典三源关联，写入 edu_hanzi（字/读音/笔画/部首/间架结构/释义/学段）。部首三级回落、拼音归一（含 -ng 的 U+0261）、释义 90 字截断加组词、结构由 IDS 首字符判定，均由 eduai 原 HanziQuestionSourceProvider 逐函数对拍迁移。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_hanzi_3500'
);
-- ---------------------------------------------------------------------
-- 为什么是 NORMAL 通道而不是 initdata_
--
-- eduai 的 application.yml 里 plugin.datasource.script.data-scan-mode=ONCE，
-- 而 DataSourceScriptConfiguration.filterDataScripts 在 ONCE 且「库已有业务数据」时
-- 会把**全部** INITDATA 脚本整体跳过（不是按文件判重）。eduai 库本来就非空，
-- 所以写成 V1.15__initdata_xxx.sql 的话，这个文件永远不会被执行。
--
-- NORMAL 走 filterMigrationScripts，RepeatableScanMode 默认 ALWAYS，
-- 再由 needsExecution（按「文件名+版本」查执行记录）保证只跑一次——
-- 与本目录里既有的 V1.14__eduai_teacher_class_and_config.sql 同一套路。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 原 V1.16__eduai_retire_legacy_collect_tasks.sql （已并入本文件）
-- ---------------------------------------------------------------------
-- =====================================================================
-- V1.16__eduai_retire_legacy_collect_tasks.sql - 下线 eduai 旧采集任务行
--
-- 背景：采集管理已收敛到采集平台（@pages/collect + /v2/collect/**）。
--       三个采集例子已在 V1.15 改写成 collect_flow 流程定义，原 edu_collect_task
--       里的三行种子数据失去了唯一读它们的代码（控制器/服务/provider/JobHandler/
--       Groovy 脚本/SPI 注册与前端页面均已删除），成为悬空数据。
--
-- 通道：NORMAL（V版本号__任意.sql）。
--
-- 幂等：按 collect_task_name 精确匹配三个已迁移的任务名，重复执行结果一致。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 一、删除三条已迁移的采集任务种子行
--
-- 三个任务名与 V1.15 里的流程一一对应，删的正是当初建的那三行，
-- 不含任何用户后来手工新建的任务（那些任务没有对应的迁移流程，本脚本不碰）。
-- ---------------------------------------------------------------------
DELETE FROM `edu_collect_task`
WHERE `collect_task_name` IN (
        '成语采集·chinese-xinhua',   -- → V1.15 collect_flow_code = edu_idiom_chinese_xinhua
        '古诗采集·全唐诗',           -- → V1.15 collect_flow_code = edu_poem_chinese_poetry_tang
        '汉字采集·常用字3500'        -- → V1.15 collect_flow_code = edu_hanzi_3500
      );

-- ---------------------------------------------------------------------
-- 二、为什么保留 edu_collect_task / edu_collect_log 两张表，不 DROP
--
-- 这一节不是保守，是执行顺序决定的硬约束：
--
--   datasource 的脚本分三步跑（DataSourceScriptConfiguration「执行顺序」）：
--     1. 同步 INIT（表结构）
--     2. 同步 ADD 和普通脚本
--     3. 异步 INITDATA（初始化数据）      ← 在最后
--
-- 本文件属于第 2 步，而那三行种子数据是第 3 步的
-- V1.1__initdata_all_eduai.sql / V1.12__initdata_all_eduai.sql 插入的。
-- 于是：
--
--   · 若在本文件里 DROP TABLE edu_collect_task
--     → 全新安装时，第 3 步的 INSERT 必然报 1146 表不存在。
--       （eduai 配了 continue-on-error: true，启动不会被阻断，但初始化数据会残缺。）
--
--   · 若改成删除 V1.1/V1.12 里的 INSERT 语句来「从源头掐断」
--     → 这两个脚本在存量库上早已记录为执行成功，needsExecution() 会拦住不重跑，
--       存量库反而清不掉；而改已执行的脚本还会动到版本表的 checksum。
--
--   · 本文件（DELETE）则是唯一在存量库上真正生效、又不动已执行迁移的做法：
--       存量库 data-scan-mode: ONCE 判定库非空 → 第 3 步整批跳过，不会再插回来；
--       全新安装上第 3 步仍会插入那三行，但它们是惰性的（无任何代码读取），
--       且正是这个全新安装从零复刻了「旧版长什么样」，留着无害。
--
-- 另：edu_collect_log 是执行历史，删任务不删日志——日志是已经发生过的审计痕迹，
--     且这些行现在没有任何外键牵连（V1.2 未声明 FOREIGN KEY），留着不影响运行。
-- ---------------------------------------------------------------------

-- ---------------------------------------------------------------------
-- 原 V1.19__eduai_collect_examples_fix.sql （已并入本文件）
-- ---------------------------------------------------------------------
-- 修正 eduai 采集示例流程
--
-- 背景（承接 V1.15__eduai_collect_flows.sql）：
--   1. 古诗示例原先只写了 1 个种子 URL（poet.tang.0.json），流程名叫「全唐诗」却只采
--      1/58 的数据；且抓取节点用 ${url[0]} 硬取第一条，加多少分片都只跑第一片。
--   2. 原源站 cdn.jsdelivr.net 的 A 记录里存在被拒连的节点，而 JDK HttpClient 不会像
--      curl 那样换 IP 重试，于是任务直接以 IO 异常: ConnectException 失败。
--   3. 输出节点的正文表达式换行写成了多重转义，Groovy/求值器把 \\n 原样写入库，
--      诗的内容里没有换行而是可见的反斜杠。这里统一改成 ${join(paragraphs, '\n')}。
--
-- 这里做的修正（幂等，按 collect_flow_code 定位，重复执行结果一致）：
--   * 开始节点列出全部分片（0..57000，共 58 个）；
--   * 抓取节点改为 ${item} + loopCount=${url}，一片一轮迭代；
--   * 源换成 fastly.jsdelivr.net 镜像，并开 retry=3 / retryInterval=2000 / timeout=120000；
--   * 正文按段落用真正的换行连接。
--
-- 不改 V1.15 本体：那个版本已经应用过，改它会破坏 Flyway 校验和。

UPDATE collect_flow
SET collect_flow_dsl = '{"name":"古诗采集·全唐诗","threadCount":4,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.0.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.1000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.2000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.3000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.4000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.5000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.6000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.7000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.8000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.9000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.10000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.11000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.12000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.13000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.14000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.15000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.16000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.17000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.18000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.19000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.20000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.21000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.22000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.23000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.24000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.25000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.26000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.27000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.28000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.29000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.30000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.31000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.32000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.33000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.34000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.35000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.36000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.37000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.38000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.39000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.40000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.41000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.42000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.43000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.44000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.45000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.46000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.47000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.48000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.49000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.50000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.51000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.52000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.53000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.54000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.55000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.56000.json\\nhttps://fastly.jsdelivr.net/gh/chinese-poetry/chinese-poetry-zhCN@master/poetry/poet.tang.57000.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取全唐诗分片","x":260,"y":160,"params":[{"name":"url","value":"${item}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"120000"},{"name":"loopCount","value":"${url}"},{"name":"loopVariableName","value":"shardIndex"},{"name":"loopItemVariableName","value":"item"},{"name":"retry","value":"3"},{"name":"retryInterval","value":"2000"}],"fields":[]},{"id":"ext","shape":"extract","name":"抽取诗词字段","x":480,"y":160,"params":[{"name":"source","value":"${respJson}"},{"name":"mode","value":"list"},{"name":"outputVariable","value":"rows"}],"fields":[{"name":"title","parserType":"JSONPATH","expression":"title"},{"name":"author","parserType":"JSONPATH","expression":"author"},{"name":"paragraphs","parserType":"JSONPATH","expression":"paragraphs"}]},{"id":"sql","shape":"sql","name":"查已存在古诗","x":700,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT CONCAT(poem_title,''|'',poem_author) AS k FROM edu_poem"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr","shape":"script","name":"过滤已存在","x":920,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; rows.unique{ it.title+''|''+it.author }.findAll{ !seen.contains(it.title+''|''+it.author) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_poem","x":1140,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_poem"},{"name":"cell:poem_title","value":"${title}"},{"name":"cell:poem_author","value":"${author}"},{"name":"cell:poem_content","value":"${join(paragraphs, ''\\\\n'')}"},{"name":"cell:poem_dynasty","value":"唐代"},{"name":"cell:poem_difficulty","value":"3"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1360,"y":160,"params":[],"fields":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"ext"},{"id":"e3","from":"ext","to":"sql"},{"id":"e4","from":"sql","to":"scr"},{"id":"e5","from":"scr","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    collect_flow_remark = '采集全唐诗：开始节点列出 58 个分片（poet.tang.0..57000.json），抓取节点按 ${url} 循环逐片采集；源走 fastly.jsdelivr.net 镜像并开重试；正文按段落换行。',
    update_time = NOW()
WHERE collect_flow_code = 'edu_poem_chinese_poetry_tang';

-- ---------------------------------------------------------------------
-- 原 V1.20__eduai_collect_hanzi_retry.sql （已并入本文件）
-- ---------------------------------------------------------------------
-- 汉字采集示例：源站换成 jsdelivr 镜像 + 两个请求节点补重试
--
-- 背景（承接 V1.15__eduai_collect_flows.sql、V1.19__eduai_collect_examples_fix.sql）：
--   1. reqChars（3500 字表，17KB）与 reqIds（makemeahanzi dictionary.txt，2.4MB）
--      走 raw.githubusercontent.com。服务器到该域名常年只有 30KB/s 量级，
--      且会整段超时（实测 3 次重试全失败：189044ms 后 HttpTimeoutException），
--      导致整个任务 FAILURE；reqIds 慢时还会一直占着任务直到 30 分钟硬超时。
--      同内容在 fastly.jsdelivr.net 实测 2.4MB/1.0s（约 2.5MB/s），相差约 100 倍。
--   2. 三个请求节点里只有 reqDict（27MB、走 ghfast 代理）配了 retry=4；
--      reqChars / reqIds 没有重试，一次抖动就整轮失败。
--
-- 这里做的修正（幂等，按 collect_flow_code 定位，重复执行结果一致）：
--   * urlChars / urlIds 的源换成 fastly.jsdelivr.net（gh 镜像写法 owner/repo@branch）；
--   * reqChars / reqIds 各补 retry=3、retryInterval=3000；
--   * word.json 仍走 ghfast.top —— 27MB 超过 jsdelivr 的 20MB 单文件上限（403）。
--
-- 幂等方式：URL 用 REPLACE 旧前缀（替换后旧串不存在，天然幂等）；
--   重试用「锚点+追加项」是否已存在判断，且**每节点各判各的** ——
--   不能用全局的 '%"retry","value":"3"%' 判断，第一条执行后全局条件即成立，
--   第二条会被整体跳过（本迁移的第一版就踩过这个坑，reqIds 漏加）。
--
-- 为什么重试用 UNHEX 而不是字面量：
--   该 DSL 内含 262 个反斜杠（Groovy 脚本的转义与正则），直接写 SQL 字符串字面量
--   会被 MySQL 的反斜杠转义吃掉一层，极易写坏脚本。
--
-- 不改 V1.15 本体：那个版本已经应用过，改它会破坏校验和。

UPDATE collect_flow
SET collect_flow_dsl = REPLACE(collect_flow_dsl, 'https://raw.githubusercontent.com/elephantnose/characters/master/', 'https://fastly.jsdelivr.net/gh/elephantnose/characters@master/'),
    update_time = NOW()
WHERE collect_flow_code = 'edu_hanzi_3500'
  AND INSTR(collect_flow_dsl, 'https://raw.githubusercontent.com/elephantnose/characters/master/') > 0;

UPDATE collect_flow
SET collect_flow_dsl = REPLACE(collect_flow_dsl, 'https://raw.githubusercontent.com/skishore/makemeahanzi/master/', 'https://fastly.jsdelivr.net/gh/skishore/makemeahanzi@master/'),
    update_time = NOW()
WHERE collect_flow_code = 'edu_hanzi_3500'
  AND INSTR(collect_flow_dsl, 'https://raw.githubusercontent.com/skishore/makemeahanzi/master/') > 0;

UPDATE collect_flow
SET collect_flow_dsl = REPLACE(
        collect_flow_dsl,
        CONVERT(UNHEX('226f75747075745661726961626c65222c2276616c7565223a22726573704368617273222c226465736372697074696f6e223a22e587bae58f82e58f98e9878fe5908d227d') USING utf8mb4),
        CONVERT(UNHEX('226f75747075745661726961626c65222c2276616c7565223a22726573704368617273222c226465736372697074696f6e223a22e587bae58f82e58f98e9878fe5908d227d2c7b226e616d65223a227265747279222c2276616c7565223a2233222c226465736372697074696f6e223a22e6ba90e7ab99e581b6e58f91e8bf9ee68ea5e8b685e697b6efbc8ce9878de8af95203320e6aca1227d2c7b226e616d65223a227265747279496e74657276616c222c2276616c7565223a2233303030222c226465736372697074696f6e223a22e9878de8af95e997b4e99a94e6afabe7a792227d') USING utf8mb4)
    ),
    collect_flow_remark = '汉字常用字 3500 采集：字表与部件分解走 fastly.jsdelivr.net 镜像、字典走 ghfast（27MB 超 jsdelivr 单文件上限）；三个请求节点均开重试；仅保留字典中有释义的字（源站缺 4 字，上限 3496）。',
    update_time = NOW()
WHERE collect_flow_code = 'edu_hanzi_3500'
  AND INSTR(collect_flow_dsl, CONVERT(UNHEX('226f75747075745661726961626c65222c2276616c7565223a22726573704368617273222c226465736372697074696f6e223a22e587bae58f82e58f98e9878fe5908d227d2c7b226e616d65223a227265747279222c2276616c7565223a2233222c226465736372697074696f6e223a22e6ba90e7ab99e581b6e58f91e8bf9ee68ea5e8b685e697b6efbc8ce9878de8af95203320e6aca1227d2c7b226e616d65223a227265747279496e74657276616c222c2276616c7565223a2233303030222c226465736372697074696f6e223a22e9878de8af95e997b4e99a94e6afabe7a792227d') USING utf8mb4)) = 0;

UPDATE collect_flow
SET collect_flow_dsl = REPLACE(
        collect_flow_dsl,
        CONVERT(UNHEX('226f75747075745661726961626c65222c2276616c7565223a2272657370496473222c226465736372697074696f6e223a22e587bae58f82e58f98e9878fe5908d227d') USING utf8mb4),
        CONVERT(UNHEX('226f75747075745661726961626c65222c2276616c7565223a2272657370496473222c226465736372697074696f6e223a22e587bae58f82e58f98e9878fe5908d227d2c7b226e616d65223a227265747279222c2276616c7565223a2233222c226465736372697074696f6e223a22e6ba90e7ab99e581b6e58f91e8bf9ee68ea5e8b685e697b6efbc8ce9878de8af95203320e6aca1227d2c7b226e616d65223a227265747279496e74657276616c222c2276616c7565223a2233303030222c226465736372697074696f6e223a22e9878de8af95e997b4e99a94e6afabe7a792227d') USING utf8mb4)
    ),
    update_time = NOW()
WHERE collect_flow_code = 'edu_hanzi_3500'
  AND INSTR(collect_flow_dsl, CONVERT(UNHEX('226f75747075745661726961626c65222c2276616c7565223a2272657370496473222c226465736372697074696f6e223a22e587bae58f82e58f98e9878fe5908d227d2c7b226e616d65223a227265747279222c2276616c7565223a2233222c226465736372697074696f6e223a22e6ba90e7ab99e581b6e58f91e8bf9ee68ea5e8b685e697b6efbc8ce9878de8af95203320e6aca1227d2c7b226e616d65223a227265747279496e74657276616c222c2276616c7565223a2233303030222c226465736372697074696f6e223a22e9878de8af95e997b4e99a94e6afabe7a792227d') USING utf8mb4)) = 0;

