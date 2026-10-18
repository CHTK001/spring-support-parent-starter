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
