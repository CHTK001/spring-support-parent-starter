// 名称: 理科学科知识点采集
// 学科: 物理/化学/生物
// 说明: 从内置理科学科知识点生成单选题与判断题，覆盖初中至高中
// 配置: {"count":10,"difficulty":3}
import java.util.Collections

def count = collector.configInt('count', 10)

/** 物理知识点 */
def physics = [
    [stem: "牛顿第一定律（惯性定律）的内容是：一切物体在没有受到外力作用的时候，总保持匀速直线运动状态或静止状态。", answer: "正确", type: "JUDGE"],
    [stem: "光的三原色是：", options: ["红、黄、蓝", "红、绿、蓝", "红、白、黑", "黄、绿、蓝"], answer: 1, type: "CHOICE"],
    [stem: "声音的传播需要介质，真空不能传声。", answer: "正确", type: "JUDGE"],
    [stem: "物体的内能与物体的：", options: ["温度、质量、状态有关", "只与温度有关", "只与质量有关", "与温度、质量、状态均无关"], answer: 0, type: "CHOICE"],
    [stem: "欧姆定律公式是：", options: ["I=U/R", "U=IR", "R=UI", "P=UI"], answer: 0, type: "CHOICE"],
    [stem: "浮力产生的原因是液体对物体向上和向下的：", options: ["压力差", "重力差", "密度差", "速度差"], answer: 0, type: "CHOICE"],
    [stem: "磁场对放入其中的磁针有力的作用。", answer: "正确", type: "JUDGE"],
    [stem: "凸透镜成像规律中，当物距大于二倍焦距时，成：", options: ["倒立缩小的实像", "倒立放大的实像", "正立放大的虚像", "正立缩小的虚像"], answer: 0, type: "CHOICE"],
    [stem: "发电机的工作原理是：", options: ["电磁感应", "通电导体在磁场中受力", "电流的磁效应", "自感现象"], answer: 0, type: "CHOICE"],
    [stem: "比热容的单位是：", options: ["J/(kg·℃)", "J/kg", "J/(kg·℃) 或 J/(kg·K)", "W/(kg·℃)"], answer: 0, type: "CHOICE"],
]

/** 化学知识点 */
def chemistry = [
    [stem: "空气的成分按体积计算，氮气约占78%，氧气约占21%。", answer: "正确", type: "JUDGE"],
    [stem: "地壳中含量最多的元素是：", options: ["氧", "硅", "铝", "铁"], answer: 0, type: "CHOICE"],
    [stem: "pH值小于7的溶液呈：", options: ["酸性", "碱性", "中性", "两性"], answer: 0, type: "CHOICE"],
    [stem: "化学反应中，催化剂可以：", options: ["改变反应速率", "增加生成物质量", "减少反应物质量", "改变反应方向"], answer: 0, type: "CHOICE"],
    [stem: "水的化学式是：", options: ["H₂O", "CO₂", "NaCl", "CH₄"], answer: 0, type: "CHOICE"],
    [stem: "燃烧的三个必要条件是可燃物、助燃物和：", options: ["达到着火点", "高温", "高压", "催化剂"], answer: 0, type: "CHOICE"],
    [stem: "金属活动性顺序中，最活泼的金属是：", options: ["钾", "钠", "钙", "镁"], answer: 0, type: "CHOICE"],
    [stem: "酸和碱反应生成盐和水的反应叫中和反应。", answer: "正确", type: "JUDGE"],
    [stem: "元素周期表中，同一周期的元素具有相同的：", options: ["电子层数", "最外层电子数", "化学性质", "原子序数"], answer: 0, type: "CHOICE"],
    [stem: "浓硫酸具有强烈的：", options: ["腐蚀性", "挥发性", "放射性", "导电性"], answer: 0, type: "CHOICE"],
]

/** 生物知识点 */
def biology = [
    [stem: "植物的光合作用是在叶绿体中进行的。", answer: "正确", type: "JUDGE"],
    [stem: "人体最大的器官是：", options: ["皮肤", "肝脏", "心脏", "大脑"], answer: 0, type: "CHOICE"],
    [stem: "DNA的双螺旋结构是由谁发现的？", options: ["沃森和克里克", "孟德尔", "达尔文", "巴斯德"], answer: 0, type: "CHOICE"],
    [stem: "细胞的基本结构包括：", options: ["细胞膜、细胞质、细胞核", "细胞壁、细胞质、细胞核", "细胞膜、细胞壁、细胞核", "细胞膜、细胞质、液泡"], answer: 0, type: "CHOICE"],
    [stem: "人体的血液循环系统由心脏和血管组成。", answer: "正确", type: "JUDGE"],
    [stem: "神经系统的基本单位是：", options: ["神经元", "神经纤维", "突触", "神经递质"], answer: 0, type: "CHOICE"],
    [stem: "绿色植物通过光合作用释放的气体是：", options: ["氧气", "二氧化碳", "氮气", "水蒸气"], answer: 0, type: "CHOICE"],
    [stem: "人的染色体数目是：", options: ["46条（23对）", "44条（22对）", "48条（24对）", "23条"], answer: 0, type: "CHOICE"],
    [stem: "反射弧的五个部分包括感受器、传入神经、神经中枢、传出神经和：", options: ["效应器", "突触", "感受器", "神经元"], answer: 0, type: "CHOICE"],
    [stem: "生物分类的七个等级中，最基本的分类单位是：", options: ["种", "属", "科", "目"], answer: 0, type: "CHOICE"],
]

def allQuestions = []
def rnd = new java.util.Random(collector.getContext().taskId)

// 按配置数量分配
def pCount = Math.min(count / 3, physics.size())
def cCount = Math.min(count / 3, chemistry.size())
def bCount = count - pCount - cCount

// 按配置数量分配（Groovy 无 Kotlin 的 take()，改用 subList）
physics.subList(0, pCount).each { allQuestions << it }
chemistry.subList(0, cCount).each { allQuestions << it }
biology.subList(0, Math.min(bCount, biology.size())).each { allQuestions << it }

java.util.Collections.shuffle(allQuestions)

def questions = []
allQuestions.each { item ->
    if (item.type == "JUDGE") {
        questions << collector.judge(item.stem, item.answer == "正确",
                "理科学科知识点：${item.stem}")
    } else {
        def opts = item.options as List
        questions << collector.choice(item.stem, opts, item.answer,
                "理科学科知识点：正确答案是 ${opts[item.answer]}")
    }
}

collector.log("理科学科知识点 ${allQuestions.size()} 条，生成题目 ${questions.size()} 道")
return questions