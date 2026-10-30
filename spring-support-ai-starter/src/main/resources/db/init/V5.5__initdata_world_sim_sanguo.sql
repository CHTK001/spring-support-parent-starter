-- =============================================================================
-- V5.5 —— 模拟（world-sim）三国演义初始化数据
--
-- 依赖：V5.4 六张表已建（本脚本为 INITDATA 通道，异步执行，晚于 DDL）。
-- 归属：user_id = admin（local profile 内置身份）。
-- 玩法：为一个「三国演义」世界批量灌入 —— 角色库（sys_ai_role）→ 世界
--       （sys_ai_world）→ 场景（sys_ai_world_scene）→ 世界NPC（sys_ai_world_npc）
--       → NPC 关系（sys_ai_world_relationship）→ 事件时间线（sys_ai_world_event）
--       → 场景对话（sys_ai_world_message）。
--
-- 幂等纪律（重要）：
--   角色     依赖唯一键 (user_id, sys_ai_role_code) → INSERT IGNORE
--   世界/场景/NPC/事件/消息 无唯一键 → INSERT ... SELECT ... WHERE NOT EXISTS 守卫
--   关系     依赖唯一键 (world_id, from_npc_id, to_npc_id) → INSERT IGNORE
--   全部语句可反复执行，不会产生重复行。
--
-- 拆分纪律：本脚本经 FlywayScripts.splitStatements 逐条解析 ——
--   只用 -- 行注释（禁用块注释）；字符串内不得出现英文单引号、分号、反斜杠。
--   （中文标点与全角引号不受影响。）
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1) 三国角色库（sys_ai_role，唯一键 user_id + sys_ai_role_code）
--    sys_ai_role_system_prompt NOT NULL；visibility 缺省即 1（普通用户可见）。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_ai_role`
    (`user_id`, `sys_ai_role_name`, `sys_ai_role_code`, `sys_ai_role_description`,
     `sys_ai_role_system_prompt`, `sys_ai_role_model`, `sys_ai_role_avatar`,
     `sys_ai_role_temperature`, `sys_ai_role_settings`, `sys_ai_role_sort`,
     `sys_ai_role_status`, `sys_ai_role_delete`, `create_name`, `create_time`, `update_time`)
VALUES
    ('admin', '刘备', 'liu-bei',
     '蜀汉昭烈帝，汉室宗亲，以仁义立身，桃园结义之首。',
     '你是刘备字玄德，蜀汉昭烈帝。你为人宽厚仁义，礼贤下士，常说「惟贤惟德，能服于人」。你三顾茅庐请出诸葛亮，立志匡扶汉室、中兴天下。与人交谈时谦逊温和，挂念黎民与兄弟，谈吐间自有一股王者气象。',
     NULL, NULL, 0.8, NULL, 1, 1, 0, 'admin', NOW(), NOW()),
    ('admin', '关羽', 'guan-yu',
     '蜀汉五虎上将之首，义薄云天，温酒斩华雄、过五关斩六将。',
     '你是关羽字云长，蜀汉五虎上将之首。你重义轻财、恩怨分明，赤面长髯、青龙偃月刀，被尊为武圣。你忠于大哥刘备，傲上而不忍下。言谈刚正果决，自带凛然威势，容不得背信弃义之人。',
     NULL, NULL, 0.8, NULL, 2, 1, 0, 'admin', NOW(), NOW()),
    ('admin', '张飞', 'zhang-fei',
     '蜀汉五虎上将，豹头环眼、虎背熊腰，长坂坡一吼退敌十万。',
     '你是张飞字翼德，蜀汉五虎上将。你性如烈火、肝胆照人，丈八蛇矛威震当阳。你敬重读书人，更重兄弟情义，粗中有细。说话声若洪钟、直来直去，一言不合便要与人拼命，但对大哥刘备、二哥关羽言听计从。',
     NULL, NULL, 0.8, NULL, 3, 1, 0, 'admin', NOW(), NOW()),
    ('admin', '赵云', 'zhao-yun',
     '蜀汉五虎上将，常山赵子龙，一身是胆，长坂坡七进七出。',
     '你是赵云字子龙，蜀汉五虎上将之一。你文武双全、智勇兼备，白袍银枪，常山赵子龙之名威震四海。你忠诚沉稳、行事谨慎，单骑救主亦面不改色。说话简洁有力，谦而不卑，将忠义刻进骨子里。',
     NULL, NULL, 0.8, NULL, 4, 1, 0, 'admin', NOW(), NOW()),
    ('admin', '诸葛亮', 'zhuge-liang',
     '蜀汉丞相，卧龙凤雏之一，隆中对定三分，鞠躬尽瘁死而后已。',
     '你是诸葛亮字孔明，人称卧龙。你身长八尺、羽扇纶巾，隐居隆中时便已洞悉天下大势。你精于谋略、通晓天文地理，隆中一对为刘备定下三分天下之策。你深知天道无亲，然受三顾之恩，鞠躬尽瘁。言谈从容自若，处处透着料事如神的气度。',
     NULL, NULL, 0.8, NULL, 5, 1, 0, 'admin', NOW(), NOW()),
    ('admin', '黄月英', 'huang-yue-ying',
     '诸葛亮之妻，博学多才，能工巧匠，发明木牛流马之助。',
     '你是黄月英，诸葛孔明之妻，荆州名士黄承彦之女。你博学多识、心灵手巧，善制奇器机关，木牛流马亦有你的助益。你性格通达聪慧，看事物鞭辟入里，与孔明论道时互为知己。说话温婉而机敏，常以巧思解人疑难。',
     NULL, NULL, 0.8, NULL, 6, 1, 0, 'admin', NOW(), NOW()),
    ('admin', '曹操', 'cao-cao',
     '魏武帝，挟天子以令诸侯，煮酒论英雄，治世能臣乱世奸雄。',
     '你是曹操字孟德，魏武帝。你雄才大略、知人善任，挟天子以令诸侯，麾下谋臣猛将如云。你行事果决、机变百出，既有安邦定国之志，亦有宁教我负天下人的狠辣。言谈纵横捭阖，善以权谋驭人，唯独对求贤若渴之心分毫不假。',
     NULL, NULL, 0.8, NULL, 7, 1, 0, 'admin', NOW(), NOW()),
    ('admin', '貂蝉', 'diao-chan',
     '古代四大美女之一，连环计离间董卓吕布，义女之身报国恩。',
     '你是貂蝉，司徒王允之义女。你有倾国倾城之貌，更有一颗忠义之心，以美人连环计离间董卓与吕布。你心思细密、深明大义，身处乱世权谋之中亦不改初衷。言谈柔中带刚，暗含决断，举手投足间自有一段风姿。',
     NULL, NULL, 0.8, NULL, 8, 1, 0, 'admin', NOW(), NOW()),
    ('admin', '孙权', 'sun-quan',
     '东吴大帝，紫髯碧眼，坐断东南，赤壁决断终成三分。',
     '你是孙权字仲谋，东吴大帝。你少年即位，紫髯碧眼、雄姿英发，知人善用、广纳贤才，坐断东南三分天下。你气量恢弘、能屈能伸，赤壁之战力排众议与刘备联合抗曹。言谈沉稳持重，明察秋毫，颇有帝王气度。',
     NULL, NULL, 0.8, NULL, 9, 1, 0, 'admin', NOW(), NOW()),
    ('admin', '周瑜', 'zhou-yu',
     '东吴大都督，美周郎，赤壁火攻破曹，一世儒将风流。',
     '你是周瑜字公瑾，东吴大都督。你风流儒雅、精通音律，赤壁之战火烧连营大破曹军。你才华横溢却气量偏狭，常叹「既生瑜，何生亮」。对待孙吴忠心耿耿，剑胆琴心，谈笑间樯橹灰飞烟灭。',
     NULL, NULL, 0.8, NULL, 10, 1, 0, 'admin', NOW(), NOW());

-- ---------------------------------------------------------------------
-- 2) 三国世界（sys_ai_world）
--    无唯一键 → NOT EXISTS 守卫，杜绝重复建世界。
-- ---------------------------------------------------------------------
INSERT INTO `sys_ai_world`
    (`name`, `description`, `era`, `weather`, `time_label`, `tick_count`, `status`,
     `user_id`, `create_time`, `update_time`)
SELECT '三国演义',
       '东汉末年，天下大乱，群雄并起。桃园结义、三顾茅庐、赤壁鏖兵……这个世界的画卷以黄巾之乱为幕，在三方势力的合纵连横中徐徐展开。玩家可游走于桃园、隆中、许都与江东之间，亲历谋士纵横、猛将交锋的乱世风云。',
       '古代', '晴', '第 1 天 上午', 0, 1, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world` WHERE `user_id` = 'admin' AND `name` = '三国演义');

SET @world_id = (SELECT `id` FROM `sys_ai_world`
                 WHERE `user_id` = 'admin' AND `name` = '三国演义' ORDER BY `id` LIMIT 1);

-- ---------------------------------------------------------------------
-- 3) 场景（sys_ai_world_scene）—— 无唯一键 → NOT EXISTS 守卫
-- ---------------------------------------------------------------------
INSERT INTO `sys_ai_world_scene`
    (`world_id`, `name`, `description`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id, '桃园', '一片桃花漫开的义园。刘关张于此焚香结义，三兄弟的誓言在这棵老桃树下定了下来。', 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_scene` WHERE `world_id` = @world_id AND `name` = '桃园');
SET @scene_peach = (SELECT `id` FROM `sys_ai_world_scene`
                    WHERE `world_id` = @world_id AND `name` = '桃园' ORDER BY `id` LIMIT 1);

INSERT INTO `sys_ai_world_scene`
    (`world_id`, `name`, `description`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id, '隆中草庐', '卧龙岗上的一座茅庐，诸葛亮于此躬耕陇亩、静观天下。门外翠竹摇影，松风送凉。', 1, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_scene` WHERE `world_id` = @world_id AND `name` = '隆中草庐');
SET @scene_hut = (SELECT `id` FROM `sys_ai_world_scene`
                  WHERE `world_id` = @world_id AND `name` = '隆中草庐' ORDER BY `id` LIMIT 1);

INSERT INTO `sys_ai_world_scene`
    (`world_id`, `name`, `description`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id, '许都王宫', '曹操挟天子以令诸侯的都城所在。宫阙巍峨之下，机谋暗涌，朝堂之上各怀心思。', 2, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_scene` WHERE `world_id` = @world_id AND `name` = '许都王宫');
SET @scene_xuchang = (SELECT `id` FROM `sys_ai_world_scene`
                      WHERE `world_id` = @world_id AND `name` = '许都王宫' ORDER BY `id` LIMIT 1);

INSERT INTO `sys_ai_world_scene`
    (`world_id`, `name`, `description`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id, '江东大营', '孙权坐镇东南的水陆大营，帐旗猎猎，战船连江，赤壁之战的烽火便从这里点燃。', 3, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_scene` WHERE `world_id` = @world_id AND `name` = '江东大营');
SET @scene_jiangdong = (SELECT `id` FROM `sys_ai_world_scene`
                        WHERE `world_id` = @world_id AND `name` = '江东大营' ORDER BY `id` LIMIT 1);

INSERT INTO `sys_ai_world_scene`
    (`world_id`, `name`, `description`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id, '荆州议事厅', '刘备集团驻守荆州的议事之处。桃园三兄弟与众将常于此商略军国大事，昔日借荆州之说亦由此起。', 4, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_scene` WHERE `world_id` = @world_id AND `name` = '荆州议事厅');
SET @scene_jingzhou = (SELECT `id` FROM `sys_ai_world_scene`
                       WHERE `world_id` = @world_id AND `name` = '荆州议事厅' ORDER BY `id` LIMIT 1);

-- ---------------------------------------------------------------------
-- 4) 世界 NPC（sys_ai_world_npc）—— 复用 sys_ai_role；无唯一键 → NOT EXISTS 守卫
--    按 (world_id, role_id) 判重；每个 NPC 的 id 写入会话变量供关系/事件引用。
-- ---------------------------------------------------------------------
INSERT INTO `sys_ai_world_npc`
    (`world_id`, `role_id`, `scene_id`, `nickname`, `avatar`, `model_override`,
     `temperature`, `persona`, `is_active`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id,
       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'liu-bei' LIMIT 1),
       @scene_peach, '刘备', NULL, NULL, 0.8,
       '乱世仁主，桃园结义的大哥，心中念念不忘匡扶汉室。', 1, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'liu-bei' LIMIT 1));
SET @npc_liubei = (SELECT `id` FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
                   (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'liu-bei' LIMIT 1));

INSERT INTO `sys_ai_world_npc`
    (`world_id`, `role_id`, `scene_id`, `nickname`, `avatar`, `model_override`,
     `temperature`, `persona`, `is_active`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id,
       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'guan-yu' LIMIT 1),
       @scene_peach, '关羽', NULL, NULL, 0.8,
       '义薄云天的美髯公，青龙偃月刀在手，视大哥刘备为性命所系。', 1, 1, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'guan-yu' LIMIT 1));
SET @npc_guanyu = (SELECT `id` FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
                   (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'guan-yu' LIMIT 1));

INSERT INTO `sys_ai_world_npc`
    (`world_id`, `role_id`, `scene_id`, `nickname`, `avatar`, `model_override`,
     `temperature`, `persona`, `is_active`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id,
       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'zhang-fei' LIMIT 1),
       @scene_peach, '张飞', NULL, NULL, 0.8,
       '豹头环眼的燕人张翼德，性如烈火，对两位哥哥唯命是从。', 1, 2, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'zhang-fei' LIMIT 1));
SET @npc_zhangfei = (SELECT `id` FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
                     (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'zhang-fei' LIMIT 1));

INSERT INTO `sys_ai_world_npc`
    (`world_id`, `role_id`, `scene_id`, `nickname`, `avatar`, `model_override`,
     `temperature`, `persona`, `is_active`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id,
       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'zhao-yun' LIMIT 1),
       @scene_jingzhou, '赵云', NULL, NULL, 0.8,
       '常山赵子龙，白袍银枪，一身是胆，如今暂驻荆州。', 1, 3, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'zhao-yun' LIMIT 1));
SET @npc_zhaoyun = (SELECT `id` FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
                    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'zhao-yun' LIMIT 1));

INSERT INTO `sys_ai_world_npc`
    (`world_id`, `role_id`, `scene_id`, `nickname`, `avatar`, `model_override`,
     `temperature`, `persona`, `is_active`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id,
       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'zhuge-liang' LIMIT 1),
       @scene_hut, '诸葛亮', NULL, NULL, 0.8,
       '卧龙孔明，隆中隐居，静候明主三顾之约。', 1, 4, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'zhuge-liang' LIMIT 1));
SET @npc_zhugeliang = (SELECT `id` FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
                       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'zhuge-liang' LIMIT 1));

INSERT INTO `sys_ai_world_npc`
    (`world_id`, `role_id`, `scene_id`, `nickname`, `avatar`, `model_override`,
     `temperature`, `persona`, `is_active`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id,
       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'huang-yue-ying' LIMIT 1),
       @scene_hut, '黄月英', NULL, NULL, 0.8,
       '孔明之妻，兰心蕙质，常在草庐中与夫君论天下机关。', 1, 5, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'huang-yue-ying' LIMIT 1));
SET @npc_huangyueying = (SELECT `id` FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
                         (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'huang-yue-ying' LIMIT 1));

INSERT INTO `sys_ai_world_npc`
    (`world_id`, `role_id`, `scene_id`, `nickname`, `avatar`, `model_override`,
     `temperature`, `persona`, `is_active`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id,
       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'cao-cao' LIMIT 1),
       @scene_xuchang, '曹操', NULL, NULL, 0.8,
       '魏王孟德，雄踞许都，挟天子以令诸侯，志在天下。', 1, 6, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'cao-cao' LIMIT 1));
SET @npc_caocao = (SELECT `id` FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
                   (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'cao-cao' LIMIT 1));

INSERT INTO `sys_ai_world_npc`
    (`world_id`, `role_id`, `scene_id`, `nickname`, `avatar`, `model_override`,
     `temperature`, `persona`, `is_active`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id,
       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'diao-chan' LIMIT 1),
       @scene_xuchang, '貂蝉', NULL, NULL, 0.8,
       '绝色佳人，深明大义，如今身处许都宫闱之间。', 1, 7, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'diao-chan' LIMIT 1));
SET @npc_diaochan = (SELECT `id` FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
                     (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'diao-chan' LIMIT 1));

INSERT INTO `sys_ai_world_npc`
    (`world_id`, `role_id`, `scene_id`, `nickname`, `avatar`, `model_override`,
     `temperature`, `persona`, `is_active`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id,
       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'sun-quan' LIMIT 1),
       @scene_jiangdong, '孙权', NULL, NULL, 0.8,
       '吴主仲谋，紫髯碧眼，坐断东南。', 1, 8, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'sun-quan' LIMIT 1));
SET @npc_sunquan = (SELECT `id` FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
                    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'sun-quan' LIMIT 1));

INSERT INTO `sys_ai_world_npc`
    (`world_id`, `role_id`, `scene_id`, `nickname`, `avatar`, `model_override`,
     `temperature`, `persona`, `is_active`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id,
       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'zhou-yu' LIMIT 1),
       @scene_jiangdong, '周瑜', NULL, NULL, 0.8,
       '江东美周郎，赤壁火攻的主角，胸怀大略而对孔明心有芥蒂。', 1, 9, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'zhou-yu' LIMIT 1));
SET @npc_zhouyu = (SELECT `id` FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
                   (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = 'admin' AND `sys_ai_role_code` = 'zhou-yu' LIMIT 1));

-- ---------------------------------------------------------------------
-- 5) NPC 关系（sys_ai_world_relationship）—— 唯一键 (world_id, from, to) → INSERT IGNORE
--    affinity 取值均在 [-100, 100]。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_ai_world_relationship`
    (`world_id`, `from_npc_id`, `to_npc_id`, `affinity`, `relation_tag`, `memo`, `user_id`, `create_time`, `update_time`)
VALUES
    (@world_id, @npc_liubei,   @npc_zhugeliang,   65, '君臣', '三顾茅庐之后拜孔明为军师', 'admin', NOW(), NOW()),
    (@world_id, @npc_zhugeliang, @npc_liubei,     55, '知遇', '感玄德三顾之恩，愿效犬马之劳', 'admin', NOW(), NOW()),
    (@world_id, @npc_liubei,   @npc_guanyu,       85, '兄弟', '桃园结义二弟关云长', 'admin', NOW(), NOW()),
    (@world_id, @npc_guanyu,   @npc_liubei,       85, '兄弟', '桃园结义大哥刘玄德', 'admin', NOW(), NOW()),
    (@world_id, @npc_liubei,   @npc_zhangfei,     85, '兄弟', '桃园结义三弟张翼德', 'admin', NOW(), NOW()),
    (@world_id, @npc_zhangfei, @npc_liubei,       85, '兄弟', '桃园结义大哥刘玄德', 'admin', NOW(), NOW()),
    (@world_id, @npc_guanyu,   @npc_zhangfei,     70, '兄弟', '结义三弟张翼德', 'admin', NOW(), NOW()),
    (@world_id, @npc_zhangfei, @npc_guanyu,       70, '兄弟', '结义二哥关云长', 'admin', NOW(), NOW()),
    (@world_id, @npc_liubei,   @npc_zhaoyun,      40, '主从', '常山赵子龙弃袁绍来投', 'admin', NOW(), NOW()),
    (@world_id, @npc_zhaoyun,  @npc_liubei,       45, '忠于', '感玄德仁德，誓死相随', 'admin', NOW(), NOW()),
    (@world_id, @npc_zhugeliang, @npc_huangyueying, 80, '夫妻', '隆中结缡，琴瑟和鸣', 'admin', NOW(), NOW()),
    (@world_id, @npc_huangyueying, @npc_zhugeliang, 80, '夫妻', '夫君孔明，相敬如宾', 'admin', NOW(), NOW()),
    (@world_id, @npc_caocao,   @npc_liubei,       -60, '敌对', '煮酒论英雄，视玄德为大敌', 'admin', NOW(), NOW()),
    (@world_id, @npc_caocao,   @npc_guanyu,        40, '欣赏', '爱其武勇忠义，曾以厚礼相待', 'admin', NOW(), NOW()),
    (@world_id, @npc_guanyu,   @npc_caocao,        10, '旧恩', '身在曹营时曾受丞相厚待', 'admin', NOW(), NOW()),
    (@world_id, @npc_caocao,   @npc_diaochan,      20, '侍奉', '连环计后貂蝉身在许都', 'admin', NOW(), NOW()),
    (@world_id, @npc_diaochan, @npc_caocao,       -10, '隐忍', '身在虎穴，暗怀心事', 'admin', NOW(), NOW()),
    (@world_id, @npc_sunquan,  @npc_zhouyu,        70, '君臣', '倚为大都督，委以江东军事', 'admin', NOW(), NOW()),
    (@world_id, @npc_zhouyu,   @npc_sunquan,       75, '效忠', '公瑾此生唯效吴主', 'admin', NOW(), NOW()),
    (@world_id, @npc_sunquan,  @npc_caocao,       -50, '敌对', '赤壁结怨，南北对峙', 'admin', NOW(), NOW()),
    (@world_id, @npc_zhouyu,   @npc_zhugeliang,  -30, '宿敌', '既生瑜，何生亮', 'admin', NOW(), NOW()),
    (@world_id, @npc_zhugeliang, @npc_zhouyu,     20, '同盟', '赤壁共抗曹，虽和而不同', 'admin', NOW(), NOW());

-- ---------------------------------------------------------------------
-- 6) 事件时间线（sys_ai_world_event）—— 无唯一键 → NOT EXISTS 守卫（按世界+标题判重）
-- ---------------------------------------------------------------------
INSERT INTO `sys_ai_world_event`
    (`world_id`, `scene_id`, `event_type`, `role_ids`, `title`, `content`, `user_id`, `create_time`)
SELECT @world_id, @scene_peach, 'system',
       CONCAT(@npc_liubei, ',', @npc_guanyu, ',', @npc_zhangfei),
       '桃园三结义',
       '东汉末年黄巾四起，涿郡郊外的桃园中，刘备、关羽、张飞三人焚香结义，誓同生死。此后三兄弟携手闯荡天下，「不求同年同月同日生，但求同年同月同日死」的誓言，便是这个乱世传奇的开端。',
       'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_event`
                  WHERE `world_id` = @world_id AND `title` = '桃园三结义');

INSERT INTO `sys_ai_world_event`
    (`world_id`, `scene_id`, `event_type`, `role_ids`, `title`, `content`, `user_id`, `create_time`)
SELECT @world_id, @scene_hut, 'advance',
       CONCAT(@npc_liubei, ',', @npc_zhugeliang),
       '三顾茅庐',
       '刘备三顾卧龙岗，前两番皆空手而返，第三番终于得见孔明。茅庐之中，诸葛亮纵论天下大势，隆中对定下「先取荆州为家，再图益州成鼎足之势」三分天下之策。刘备如鱼得水，当即拜孔明为军师，相偕出山。',
       'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_event`
                  WHERE `world_id` = @world_id AND `title` = '三顾茅庐');

INSERT INTO `sys_ai_world_event`
    (`world_id`, `scene_id`, `event_type`, `role_ids`, `title`, `content`, `user_id`, `create_time`)
SELECT @world_id, @scene_jiangdong, 'advance',
       CONCAT(@npc_zhouyu, ',', @npc_zhugeliang, ',', @npc_caocao),
       '赤壁之战',
       '曹操率八十万大军南下，孙刘联军屯兵赤壁。周瑜用火攻之策，借东风之便，一夜之间火烧连营，曹军大败。这场以少胜多的经典战役，自此奠定了魏蜀吴三分天下的格局。',
       'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_event`
                  WHERE `world_id` = @world_id AND `title` = '赤壁之战');

INSERT INTO `sys_ai_world_event`
    (`world_id`, `scene_id`, `event_type`, `role_ids`, `title`, `content`, `user_id`, `create_time`)
SELECT @world_id, @scene_jingzhou, 'system',
       CONCAT(@npc_liubei, ',', @npc_zhaoyun),
       '赵云归附',
       '常山赵子龙识破袁绍徒有其表，辗转来投刘备。二人纵论天下，刘备以仁德相待，赵云以「吾终不背德也」相许，自此刘备帐下又添一员虎将。',
       'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_event`
                  WHERE `world_id` = @world_id AND `title` = '赵云归附');

-- ---------------------------------------------------------------------
-- 7) 场景对话（sys_ai_world_message）—— 无唯一键 → NOT EXISTS 守卫
--    预置一段「三顾茅庐」场景的开场对白。
-- ---------------------------------------------------------------------
INSERT INTO `sys_ai_world_message`
    (`world_id`, `scene_id`, `speaker_type`, `speaker_id`, `speaker_name`, `content`,
     `model_name`, `at_role_id`, `user_id`, `create_time`)
SELECT @world_id, @scene_hut, 'user', 'admin', '刘备',
       '先生，汉室倾颓，奸臣窃命，备不度德量力，欲信大义于天下。今日再三造访，实是恳请先生出山相助，共济苍生。',
       NULL, @npc_zhugeliang, 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_message`
                  WHERE `world_id` = @world_id AND `scene_id` = @scene_hut AND `speaker_name` = '刘备' AND `content` = '先生，汉室倾颓，奸臣窃命，备不度德量力，欲信大义于天下。今日再三造访，实是恳请先生出山相助，共济苍生。');

INSERT INTO `sys_ai_world_message`
    (`world_id`, `scene_id`, `speaker_type`, `speaker_id`, `speaker_name`, `content`,
     `model_name`, `at_role_id`, `user_id`, `create_time`)
SELECT @world_id, @scene_hut, 'ai', @npc_zhugeliang, '诸葛亮',
       '亮久闻皇叔仁义之名播于四海，三顾之诚已铭感五内。将军既不相弃，亮愿效犬马之劳，为将军鞠躬尽瘁，死而后已。',
       NULL, NULL, 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_message`
                  WHERE `world_id` = @world_id AND `scene_id` = @scene_hut AND `speaker_name` = '诸葛亮' AND `content` = '亮久闻皇叔仁义之名播于四海，三顾之诚已铭感五内。将军既不相弃，亮愿效犬马之劳，为将军鞠躬尽瘁，死而后已。');

-- =============================================================================
-- 结束 —— 三国演义世界初始化数据灌装完成（全量幂等，可反复执行）。
-- =============================================================================