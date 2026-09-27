// 名称: 口算题生成
// 学科: 数学
// 说明: 按任务与日期播种生成加减法口算题，答案由脚本计算，适合每日定时增量采集
// 配置: {"count":8,"min":10,"max":99}
import java.time.LocalDate
import java.util.Random

def count = collector.configInt('count', 8)
def min = collector.configInt('min', 10)
def max = collector.configInt('max', 99)
def span = Math.max(1, max - min)
def random = new Random(LocalDate.now().toEpochDay() * 31L + (context.taskId ?: 0L))
def questions = []

count.times {
    def a = min + random.nextInt(span)
    def b = min + random.nextInt(span)
    if (random.nextBoolean()) {
        questions << collector.question("口算：${a} + ${b} = ？", String.valueOf(a + b))
    } else {
        def big = Math.max(a, b)
        def small = Math.min(a, b)
        questions << collector.question("口算：${big} - ${small} = ？", String.valueOf(big - small))
    }
}

collector.log("生成口算题 ${questions.size()} 道（播种区间 ${min}~${max}）")
return questions
