// 名称: 名句出处积累
// 学科: 语文
// 说明: 从一言 open API 随机抽取文句，生成下半句默写填空题与出处判断题
// 配置: {"url":"https://v1.hitokoto.cn?c=k&encode=json","times":6}
import groovy.json.JsonSlurper

/** 一条抽取到的文句 */
class Quote {
    String text
    String from
}

def url = collector.config('url', 'https://v1.hitokoto.cn?c=k&encode=json')
def times = collector.configInt('times', 6)
def quotes = []

times.times {
    def json = new JsonSlurper().parseText(collector.http(url))
    if (!json.hitokoto) {
        collector.log('返回缺少 hitokoto 字段')
        return
    }
    quotes << new Quote(text: (json.hitokoto as String).trim(), from: (json.from ?: '佚名') as String)
}

def distinct = quotes.unique { it.text }
def questions = []

distinct.eachWithIndex { quote, index ->
    def halves = quote.text.split(/[，。！？；]/).findAll { it.trim() }
    if (halves.size() >= 2) {
        questions << collector.fill("${halves[0].trim()}，______。（出处：${quote.from}）",
                halves[1].trim(), "原句：${quote.text}")
    }
    if (distinct.size() >= 2) {
        def other = distinct.find { it.text != quote.text }
        def attribution = index % 2 == 0 ? quote.from : other.from
        questions << collector.judge("“${quote.text}”这句话出自《${attribution}》。",
                attribution == quote.from, "实际出处：${quote.from}")
    }
}

collector.log("抽取文句 ${distinct.size()} 条，生成题目 ${questions.size()} 道")
return questions
