// 名称: 英语词义采集
// 学科: 英语
// 说明: 抓取 dict.cn 单词页的词性与中文释义，生成词义单选题与汉译英拼写填空题
// 配置: {"site":"https://dict.cn/","words":"逗号分隔单词表；默认内置 10 个高频词"}
import java.util.Collections

/** 一条单词抓取结果 */
class WordEntry {
    String word
    String meaning
}

def site = collector.config('site', 'https://dict.cn/')
def words = collector.config('words',
        'apple,bridge,village,energy,translate,mountain,weather,danger,ancient,disease')
        .split(',').collect { it.trim().toLowerCase() }.findAll { it }
def entries = []

words.each { word ->
    try {
        def html = collector.http(site + word)
        collector.extractGroups(html,
                '<li>\\s*<span>([^<]+)</span>\\s*<strong>([^<]+)</strong>').each { groups ->
            entries << new WordEntry(word: word,
                    meaning: "${groups[0].trim()} ${groups[1].trim()}".replaceAll(/\s+/, ' '))
        }
    } catch (Exception e) {
        collector.log("抓取 ${word} 失败: ${e.message}")
    }
}

def questions = []
entries.unique { it.word }.each { entry ->
    def distractors = entries.collect { it.meaning }.findAll { it != entry.meaning }.unique()
    if (distractors.size() < 2) {
        collector.log("干扰项不足，跳过 ${entry.word}")
        return
    }
    def options = [entry.meaning] + distractors.take(3)
    Collections.shuffle(options)
    questions << collector.choice("单词“${entry.word}”的中文意思是？", options,
            options.indexOf(entry.meaning), "来源 dict.cn：${entry.meaning}")
    questions << collector.fill("汉译英：${entry.meaning} —— ______。", entry.word,
            "详见 dict.cn/${entry.word}")
}

collector.log("解析单词 ${entries.size()} 条，生成题目 ${questions.size()} 道")
return questions
