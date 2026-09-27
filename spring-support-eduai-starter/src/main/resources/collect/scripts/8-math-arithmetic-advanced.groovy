// 名称: 数学口算题生成
// 学科: 数学
// 说明: 按日期+任务ID确定性生成口算题，覆盖小学加减乘除，支持定时采集
// 配置: {"count":20,"maxNumber":100,"grade":"小学","difficulty":1}
import java.security.MessageDigest

def count = collector.configInt('count', 20)
def maxNumber = collector.configInt('maxNumber', 100)
def grade = collector.config('grade', '小学')
def difficulty = collector.configInt('difficulty', 1)

/** 用 taskId+日期做确定性种子，保证同一天同任务结果稳定 */
def seed = "${collector.getContext().taskId}:${System.currentTimeMillis() / 86400000}"
def md5 = MessageDigest.getInstance('MD5').digest(seed.bytes).collect { String.format('%02x', it) }.join('')
def rnd = new java.util.Random(md5.hashCode())

def questions = []

// 加法
count.times { i ->
    def a = rnd.nextInt(maxNumber) + 1
    def b = rnd.nextInt(maxNumber) + 1
    if (difficulty >= 3) { a = rnd.nextInt(maxNumber / 2) + 1; b = rnd.nextInt(maxNumber / 2) + 1 }
    questions << collector.fill("${a} + ${b} = ______", "${a + b}", "口算加法：${a} + ${b} = ${a + b}")
}

// 减法
count.times { i ->
    def a = rnd.nextInt(maxNumber) + 1
    def b = rnd.nextInt(a) + 1
    questions << collector.fill("${a} - ${b} = ______", "${a - b}", "口算减法：${a} - ${b} = ${a - b}")
}

// 乘法（小学低年级）
if (difficulty <= 2) {
    count.times { i ->
        def a = rnd.nextInt(12) + 1
        def b = rnd.nextInt(12) + 1
        questions << collector.fill("${a} × ${b} = ______", "${a * b}", "乘法口诀：${a} × ${b} = ${a * b}")
    }
}

collector.log("数学口算生成 ${grade} 难度${difficulty}，共 ${questions.size()} 道")
return questions