// 名称: 宋词精选采集
// 学科: 语文
// 说明: 从内置宋词数据生成填空与判断题，覆盖小学至高中
// 配置: {"times":6,"difficulty":3}
import java.util.Collections

def times = collector.configInt('times', 6)

class SongCi {
    String title
    String author
    String dynasty
    List<String> content
}

def poems = [
    new SongCi(title: "水调歌头·明月几时有", author: "苏轼", dynasty: "宋代",
        content: ["明月几时有", "把酒问青天", "不知天上宫阙", "今夕是何年", "我欲乘风归去", "又恐琼楼玉宇", "高处不胜寒", "起舞弄清影", "何似在人间", "转朱阁", "低绮户", "照无眠", "不应有恨", "何事长向别时圆", "人有悲欢离合", "月有阴晴圆缺", "此事古难全", "但愿人长久", "千里共婵娟"]),
    new SongCi(title: "念奴娇·赤壁怀古", author: "苏轼", dynasty: "宋代",
        content: ["大江东去", "浪淘尽", "千古风流人物", "故垒西边", "人道是", "三国周郎赤壁", "乱石穿空", "惊涛拍岸", "卷起千堆雪", "江山如画", "一时多少豪杰", "遥想公瑾当年", "小乔初嫁了", "雄姿英发", "羽扇纶巾", "谈笑间", "樯橹灰飞烟灭", "故国神游", "多情应笑我", "早生华发", "人生如梦", "一尊还酹江月"]),
    new SongCi(title: "满江红·写怀", author: "岳飞", dynasty: "宋代",
        content: ["怒发冲冠", "凭栏处", "潇潇雨歇", "抬望眼", "仰天长啸", "壮怀激烈", "三十功名尘与土", "八千里路云和月", "莫等闲", "白了少年头", "空悲切", "靖康耻", "犹未雪", "臣子恨", "何时灭", "驾长车", "踏破贺兰山缺", "壮志饥餐胡虏肉", "笑谈渴饮匈奴血", "待从头", "收拾旧山河", "朝天阙"]),
    new SongCi(title: "如梦令·常记溪亭日暮", author: "李清照", dynasty: "宋代",
        content: ["常记溪亭日暮", "沉醉不知归路", "兴尽晚回舟", "误入藕花深处", "争渡", "争渡", "惊起一滩鸥鹭"]),
    new SongCi(title: "卜算子·咏梅", author: "陆游", dynasty: "宋代",
        content: ["驿外断桥边", "寂寞开无主", "已是黄昏独自愁", "更著风和雨", "无意苦争春", "一任群芳妒", "零落成泥碾作尘", "只有香如故"]),
    new SongCi(title: "西江月·夜行黄沙道中", author: "辛弃疾", dynasty: "宋代",
        content: ["明月别枝惊鹊", "清风半夜鸣蝉", "稻花香里说丰年", "听取蛙声一片", "七八个星天外", "两三点雨山前", "旧时茅店社林边", "路转溪桥忽见"]),
]

def selected = poems.take(times)
def questions = []

selected.each { poem ->
    def lines = poem.content
    // 填空：挖去名句
    if (lines.size() >= 4) {
        def mid = lines[lines.size() / 2]
        def stem = "《${poem.title}》中“______”一句出自词的上阕。"
        questions << collector.fill(stem, mid, "《${poem.title}》${poem.author}，全词：${lines.join('，')}。")
    }
    // 判断题
    def others = poems.findAll { it.title != poem.title }
    if (!others.isEmpty()) {
        def other = others[0]
        questions << collector.judge("《${poem.title}》是${poem.author}的作品。",
                true, "《${poem.title}》为${poem.dynasty} ${poem.author}所作。")
    }
}

collector.log("宋词 ${selected.size()} 首，生成题目 ${questions.size()} 道")
return questions