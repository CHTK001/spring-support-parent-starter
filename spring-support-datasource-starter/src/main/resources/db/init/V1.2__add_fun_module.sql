-- 趣味模块配置表
CREATE TABLE IF NOT EXISTS edu_fun_config (
    config_id INT AUTO_INCREMENT PRIMARY KEY,
    module_code VARCHAR(50) UNIQUE,
    module_name VARCHAR(100),
    module_desc VARCHAR(500),
    module_icon VARCHAR(100),
    module_path VARCHAR(500),
    enabled INT DEFAULT 1,
    sort_order INT DEFAULT 0,
    config_json TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 成语表
CREATE TABLE IF NOT EXISTS edu_idiom (
    idiom_id INT AUTO_INCREMENT PRIMARY KEY,
    idiom_content VARCHAR(100),
    idiom_pinyin VARCHAR(200),
    idiom_meaning VARCHAR(1000),
    idiom_source VARCHAR(500),
    idiom_example VARCHAR(1000),
    idiom_synonyms VARCHAR(500),
    idiom_antonyms VARCHAR(500),
    idiom_difficulty INT DEFAULT 1,
    idiom_tags VARCHAR(200),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 成语学段映射表
CREATE TABLE IF NOT EXISTS edu_idiom_grade (
    map_id INT AUTO_INCREMENT PRIMARY KEY,
    idiom_id INT,
    grade_level VARCHAR(50),
    grade VARCHAR(50),
    semester VARCHAR(10),
    textbook_version VARCHAR(100),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 初始数据：趣味模块配置
INSERT INTO edu_fun_config (module_code, module_name, module_desc, module_icon, module_path, enabled, sort_order, config_json) VALUES
('INSECT', '昆虫识图', '交互式3D昆虫图鉴，认识63种昆虫的身体构造', 'Bug', '/edu/fun/insect', 1, 1, '{"url":"https://insect-world.pages.dev","width":"100%","height":"800px"}'),
('IDIOM_GAME', '成语消消乐', '根据学段生成的成语配对游戏', 'Trophy', '/edu/fun/idiom', 1, 2, '{"defaultCount":10,"showPinyin":true}'),
('IDIOM_BANK', '成语库', '管理成语数据和学段映射', 'Collection', '/edu/fun/idiom-bank', 1, 3, '{}');

-- 初始数据：成语（小学阶段）
INSERT INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_difficulty, idiom_tags) VALUES
('守株待兔', 'shou zhu dai tu', '比喻死守狭隘经验，不知变通', '韩非子·五蠹', '我们不能守株待兔，要主动出击。', 1, '寓言,动物'),
('画蛇添足', 'hua she tian zu', '比喻做多余的事，反而弄巧成拙', '战国策·齐策二', '这样做简直是画蛇添足。', 1, '寓言,动物'),
('亡羊补牢', 'wang yang bu lao', '比喻出了差错后及时补救', '战国策·楚策四', '亡羊补牢，为时未晚。', 1, '寓言,动物'),
('井底之蛙', 'jing di zhi wa', '比喻见识狭隘的人', '庄子·秋水', '不要做井底之蛙，要开阔眼界。', 1, '寓言,动物'),
('狐假虎威', 'hu jia hu wei', '比喻借别人的威势吓唬人', '战国策·楚策一', '他不过是狐假虎威罢了。', 1, '寓言,动物'),
('对牛弹琴', 'dui niu tan qin', '比喻对不懂道理的人讲道理', '理惑论', '跟他说这些简直是对牛弹琴。', 2, '寓言,动物'),
('鹤立鸡群', 'he li ji qun', '比喻一个人的才能或仪表出众', '世说新语·容止', '他在人群中鹤立鸡群。', 2, '动物,外貌'),
('杯弓蛇影', 'bei gong she ying', '比喻疑神疑鬼，自相惊扰', '风俗通义·世间', '不要杯弓蛇影，自己吓自己。', 2, '寓言,动物'),
('马到成功', 'ma dao cheng gong', '比喻事情一开始就取得成功', '元曲选·郑德辉', '祝你马到成功！', 2, '动物,成功'),
('龙飞凤舞', 'long fei feng wu', '形容书法笔势有力，也形容山势蜿蜒', '宋·苏轼', '他的字龙飞凤舞，很有气势。', 2, '动物,艺术'),
('虎头蛇尾', 'hu tou she wei', '比喻做事有始无终', '元·康进之', '做事不能虎头蛇尾。', 3, '动物,做事'),
('鸡飞蛋打', 'ji fei dan da', '比喻一无所得', '民间谚语', '结果鸡飞蛋打，什么都没捞到。', 3, '动物,失败'),
('猴年马月', 'hou nian ma yue', '比喻遥遥无期', '民间谚语', '这事要等到猴年马月啊。', 3, '动物,时间'),
('狗急跳墙', 'gou ji tiao qiang', '比喻坏人在走投无路时豁出去', '敦煌变文集·燕子赋', '小心狗急跳墙。', 3, '动物,处境'),
('螳臂当车', 'tang bi dang che', '比喻不自量力', '庄子·人间世', '他的反对不过是螳臂当车。', 4, '动物,寓言'),
('鱼目混珠', 'yu mu hun zhu', '比喻以假乱真', '玉清经', '市场上鱼目混珠的现象很多。', 4, '动物,真假'),
('鹏程万里', 'peng cheng wan li', '比喻前程远大', '庄子·逍遥游', '祝你鹏程万里！', 4, '动物,前途'),
('塞翁失马', 'sai weng shi ma', '比喻坏事在一定条件下可以变为好事', '淮南子·人间训', '塞翁失马，焉知非福。', 5, '寓言,哲理'),
('叶公好龙', 'ye gong hao long', '比喻口头上说爱好，实际上并不真爱好', '新序·杂事', '他不过是叶公好龙罢了。', 5, '寓言,哲理'),
('掩耳盗铃', 'yan er dao ling', '比喻自己欺骗自己', '吕氏春秋·自知', '这样做简直是掩耳盗铃。', 5, '寓言,哲理');

-- 初始数据：成语学段映射
INSERT INTO edu_idiom_grade (idiom_id, grade_level, grade, semester, textbook_version) VALUES
(1,'小学','一年级','上','人教版'),(2,'小学','一年级','上','人教版'),(3,'小学','一年级','上','人教版'),
(4,'小学','二年级','上','人教版'),(5,'小学','二年级','上','人教版'),(6,'小学','二年级','下','人教版'),
(7,'小学','三年级','上','人教版'),(8,'小学','三年级','下','人教版'),(9,'小学','四年级','上','人教版'),
(10,'小学','四年级','下','人教版'),(11,'小学','五年级','上','人教版'),(12,'小学','五年级','下','人教版'),
(13,'小学','六年级','上','人教版'),(14,'小学','六年级','下','人教版'),
(15,'初中','初一','上','人教版'),(16,'初中','初一','下','人教版'),(17,'初中','初二','上','人教版'),
(18,'初中','初二','下','人教版'),(19,'初中','初三','上','人教版'),(20,'初中','初三','下','人教版');
