-- =====================================================================
-- V2.7__fix_edu_collect_flows_url_ref.sql - 修正 V2.6 六条流程的种子 URL 引用
--
-- 背景（实测踩到的真坑）：
--   V2.6 的六条流程在 request 节点写的是 `"url": "${url[0]}"`。
--   但请求节点执行完会执行 `variables.put("url", request.getUrl())`，
--   把 Start 节点写的「url 列表」覆盖成一个字符串。于是第二轮起的
--   表达式求值拿到的是字符串索引，URL 变成 `...词语表.CLOB`，
--   下游直接 HTTP 502（实测 task 225 报的就是这个）。
--
--   正确写法是 `${data[0]}`：Start 节点的 outputVariable 默认就是 `data`，
--   它存的是同一份 URL 列表，且不会被请求节点覆盖。
--
-- 为什么另开一个文件而不是改 V2.6：
--   版本表按「文件名 + 版本号」记账，V2.6 已经在库里执行过，
--   `validate-checksum` 会拦下改过的同名脚本（日志报「校验和不匹配」）。
--
-- 做法：按 collect_flow_code 定位，只替换 collect_flow_dsl 这一列，
--   其余字段（状态、备注、并发等）原样保留，不动用户在页面上的改动。
-- 幂等：UPDATE 天然幂等，重复执行结果一致。
-- =====================================================================

-- 词语采集·小学教材词语表
UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, '${url[0]}', '${data[0]}'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` = 'edu_word_textbook'
  AND `collect_flow_dsl` LIKE '%${url[0]}%';

-- 汉字采集·通用规范汉字表
UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, '${url[0]}', '${data[0]}'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` = 'edu_hanzi_moe_table'
  AND `collect_flow_dsl` LIKE '%${url[0]}%';

-- 古文采集·论语
UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, '${url[0]}', '${data[0]}'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` = 'edu_poem_lunyu'
  AND `collect_flow_dsl` LIKE '%${url[0]}%';

-- 古文采集·诗经
UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, '${url[0]}', '${data[0]}'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` = 'edu_poem_shijing'
  AND `collect_flow_dsl` LIKE '%${url[0]}%';

-- 古文采集·楚辞
UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, '${url[0]}', '${data[0]}'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` = 'edu_poem_chuci'
  AND `collect_flow_dsl` LIKE '%${url[0]}%';

-- 诗词采集·宋词
UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, '${url[0]}', '${data[0]}'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` = 'edu_poem_songci'
  AND `collect_flow_dsl` LIKE '%${url[0]}%';
