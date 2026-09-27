// 名称: 唐诗三百首采集
// 学科: 语文
// 说明: 从公开唐诗数据生成默写填空题与文学常识单选题，覆盖小学至高中
// 配置: {"times":8,"difficulty":2}
import groovy.json.JsonSlurper

def times = collector.configInt('times', 8)
def difficulty = collector.configInt('difficulty', 2)

/** 唐诗条目 */
class TangPoem {
    String title
    String author
    String dynasty
    List<String> content
}

/** 内置唐诗数据（与 GitHub Chinese-Poetry 对齐的真实诗集） */
def poems = [
    new TangPoem(title: "静夜思", author: "李白", dynasty: "唐代",
        content: ["床前明月光", "疑是地上霜", "举头望明月", "低头思故乡"]),
    new TangPoem(title: "春晓", author: "孟浩然", dynasty: "唐代",
        content: ["春眠不觉晓", "处处闻啼鸟", "夜来风雨声", "花落知多少"]),
    new TangPoem(title: "登鹳雀楼", author: "王之涣", dynasty: "唐代",
        content: ["白日依山尽", "黄河入海流", "欲穷千里目", "更上一层楼"]),
    new TangPoem(title: "望庐山瀑布", author: "李白", dynasty: "唐代",
        content: ["日照香炉生紫烟", "遥看瀑布挂前川", "飞流直下三千尺", "疑是银河落九天"]),
    new TangPoem(title: "早发白帝城", author: "李白", dynasty: "唐代",
        content: ["朝辞白帝彩云间", "千里江陵一日还", "两岸猿声啼不住", "轻舟已过万重山"]),
    new TangPoem(title: "黄鹤楼送孟浩然之广陵", author: "李白", dynasty: "唐代",
        content: ["故人西辞黄鹤楼", "烟花三月下扬州", "孤帆远影碧空尽", "唯见长江天际流"]),
    new TangPoem(title: "江雪", author: "柳宗元", dynasty: "唐代",
        content: ["千山鸟飞绝", "万径人踪灭", "孤舟蓑笠翁", "独钓寒江雪"]),
    new TangPoem(title: "枫桥夜泊", author: "张继", dynasty: "唐代",
        content: ["月落乌啼霜满天", "江枫渔火对愁眠", "姑苏城外寒山寺", "夜半钟声到客船"]),
    new TangPoem(title: "游子吟", author: "孟郊", dynasty: "唐代",
        content: ["慈母手中线", "游子身上衣", "临行密密缝", "意恐迟迟归", "谁言寸草心", "报得三春晖"]),
    new TangPoem(title: "赋得古原草送别", author: "白居易", dynasty: "唐代",
        content: ["离离原上草", "一岁一枯荣", "野火烧不尽", "春风吹又生"]),
    new TangPoem(title: "池上", author: "白居易", dynasty: "唐代",
        content: ["小娃撑小艇", "偷采白莲回", "不解藏踪迹", "浮萍一道开"]),
    new TangPoem(title: "暮江吟", author: "白居易", dynasty: "唐代",
        content: ["一道残阳铺水中", "半江瑟瑟半江红", "可怜九月初三夜", "露似真珠月似弓"]),
    new TangPoem(title: "九月九日忆山东兄弟", author: "王维", dynasty: "唐代",
        content: ["独在异乡为异客", "每逢佳节倍思亲", "遥知兄弟登高处", "遍插茱萸少一人"]),
    new TangPoem(title: "送元二使安西", author: "王维", dynasty: "唐代",
        content: ["渭城朝雨浥轻尘", "客舍青青柳色新", "劝君更尽一杯酒", "西出阳关无故人"]),
    new TangPoem(title: "鹿柴", author: "王维", dynasty: "唐代",
        content: ["空山不见人", "但闻人语响", "返景入深林", "复照青苔上"]),
    new TangPoem(title: "竹里馆", author: "王维", dynasty: "唐代",
        content: ["独坐幽篁里", "弹琴复长啸", "深林人不知", "明月来相照"]),
    new TangPoem(title: "春夜喜雨", author: "杜甫", dynasty: "唐代",
        content: ["好雨知时节", "当春乃发生", "随风潜入夜", "润物细无声"]),
    new TangPoem(title: "绝句", author: "杜甫", dynasty: "唐代",
        content: ["两个黄鹂鸣翠柳", "一行白鹭上青天", "窗含西岭千秋雪", "门泊东吴万万里船"]),
    new TangPoem(title: "江畔独步寻花", author: "杜甫", dynasty: "唐代",
        content: ["黄师塔前江水东", "春光懒困倚微风", "桃花一簇开无主", "可爱深红爱浅红"]),
    new TangPoem(title: "咏鹅", author: "骆宾王", dynasty: "唐代",
        content: ["鹅鹅鹅", "曲项向天歌", "白毛浮绿水", "红掌拨清波"]),
]

def selected = poems.take(times)
def questions = []

selected.each { poem ->
    def lines = poem.content
    // 填空题：挖去最后一句
    if (lines.size() >= 3) {
        def stem = "《${poem.title}》的最后两句是“______，______”。"
        def lastTwo = lines[-2..-1].join('，')
        questions << collector.fill(stem, lastTwo,
                "《${poem.title}》全诗：${lines.join('，')}，${poem.author}作。")
    }
    // 填空题：挖去首句
    if (lines.size() >= 2) {
        def stem = "《${poem.title}》的首句是“______”。"
        questions << collector.fill(stem, lines[0],
                "《${poem.title}》${poem.author}《${poem.title}》")
    }
    // 单选题：作者归属
    def others = poems.findAll { it.author != poem.author }.take(3).collect { it.author }
    def options = [poem.author] + others
    java.util.Collections.shuffle(options)
    questions << collector.choice("《${poem.title}》的作者是哪位诗人？", options,
            options.indexOf(poem.author), "${poem.dynasty} ${poem.author}，《${poem.title}》")
}

collector.log("唐诗 ${selected.size()} 首，生成题目 ${questions.size()} 道")
return questions