// 名称: 每日诗词采集
// 学科: 语文
// 说明: 从「今日诗词」open API 取诗句，生成上下句默写填空题与文学常识单选题
// 配置: {"url":"https://v2.jinrishici.com/one.json","times":1,"distractors":"李白,杜甫,白居易,苏轼,李清照,辛弃疾"}
import groovy.json.JsonSlurper
import java.util.Collections

def url = collector.config('url', 'https://v2.jinrishici.com/one.json')
def times = collector.configInt('times', 1)
def pool = collector.config('distractors', '李白,杜甫,白居易,苏轼,李清照').split(',').collect { it.trim() }.findAll { it }
def questions = []

times.times {
    def json = new JsonSlurper().parseText(collector.http(url))
    if (json.status != 'success') {
        collector.log("接口未返回 success，状态=${json.status}")
        return
    }
    def data = json.data
    def origin = data.origin
    def lines = (origin.content ?: []).collect { tail(it) }.findAll { it }
    def current = tail(data.content as String)
    def title = origin.title
    def author = origin.author
    def dynasty = origin.dynasty

    int index = lines.indexOf(current)
    if (index > 0) {
        questions << collector.fill("${lines[index - 1]}，______。（${dynasty} ${author}《${title}》）",
                current, "全诗：${lines.join('，')}")
    } else if (!lines.isEmpty()) {
        questions << collector.fill("《${title}》（${author}）的首句是“______”。",
                lines[0], "全诗：${lines.join('，')}")
    }

    def options = [author] + pool.findAll { it != author }.take(3)
    Collections.shuffle(options)
    questions << collector.choice("“${current}”出自下列哪位作家之手？", options,
            options.indexOf(author), "${dynasty} ${author}《${title}》")
    collector.log("已生成《${title}》题目 2 道")
}

return questions

/**
 * 去掉句末标点，便于挖空与比对。
 */
static String tail(String line) {
    return line == null ? '' : line.replaceAll(/[，。！？；、,.!?;:]+$/, '').trim()
}
