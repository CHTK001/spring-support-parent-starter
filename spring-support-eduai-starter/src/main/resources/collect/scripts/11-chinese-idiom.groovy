// 名称: 中华成语采集
// 学科: 语文
// 说明: 从内置常用成语数据生成成语填空与释义单选题，覆盖小学至高中
// 配置: {"count":12,"difficulty":2}
import java.util.Collections

def count = collector.configInt('count', 12)

/** 常用成语（含释义） */
def idioms = [
    [word: "画蛇添足", pinyin: "huà shé tiān zú", meaning: "比喻做多此无益，反而坏事"],
    [word: "守株待兔", pinyin: "shǒu zhū dài tù", meaning: "比喻死守狭隘的经验，不知变通"],
    [word: "掩耳盗铃", pinyin: "yǎn ěr dào líng", meaning: "比喻自己欺骗自己，明明掩盖不住却偏要掩盖"],
    [word: "刻舟求剑", pinyin: "kè zhōu qiú jiàn", meaning: "比喻拘泥成法，不知变通"],
    [word: "亡羊补牢", pinyin: "wáng yáng bǔ láo", meaning: "比喻出了问题以后想办法补救"],
    [word: "杯弓蛇影", pinyin: "bēi gōng shé yǐng", meaning: "比喻因疑神疑鬼而引起恐惧"],
    [word: "狐假虎威", pinyin: "hú jiǎ hǔ wēi", meaning: "比喻依仗别人的势力欺压人"],
    [word: "自相矛盾", pinyin: "zì xiāng máo dùn", meaning: "比喻自己说话或行动前后抵触"],
    [word: "井底之蛙", pinyin: "jǐng dǐ zhī wā", meaning: "比喻眼界狭隘，见识短浅"],
    [word: "叶公好龙", pinyin: "yè gōng hǎo lóng", meaning: "比喻表面上爱好某事物，实际上并不真正爱好"],
    [word: "对牛弹琴", pinyin: "duì niú tán qín", meaning: "比喻对不懂道理的人讲道理，白费口舌"],
    [word: "滥竽充数", pinyin: "làn yú chōng shù", meaning: "比喻没有真才实学的人混在内行人中充数"],
    [word: "买椟还珠", pinyin: "mǎi dú huán zhū", meaning: "比喻没有眼光，取舍不当"],
    [word: "盲人摸象", pinyin: "máng rén mō xiàng", meaning: "比喻对事物只凭片面的了解或局部的经验，就乱加猜测"],
    [word: "惊弓之鸟", pinyin: "jīng gōng zhī niǎo", meaning: "比喻受过惊吓的人，遇到一点动静就异常害怕"],
    [word: "胸有成竹", pinyin: "xiōng yǒu chéng zhú", meaning: "比喻做事之前已有完整的打算"],
    [word: "画龙点睛", pinyin: "huà lóng diǎn jīng", meaning: "比喻写文章或讲话时，在关键处用几句话点明实质"],
    [word: "掩耳盗铃", pinyin: "yǎn ěr dào líng", meaning: "比喻自己欺骗自己"],
    [word: "削足适履", pinyin: "xuē zú shì lǚ", meaning: "比喻不合理地迁就现成条件"],
    [word: "水中捞月", pinyin: "shuǐ zhōng lāo yuè", meaning: "比喻做根本做不到的事，白费力气"],
    [word: "鹤立鸡群", pinyin: "hè lì jī qūn", meaning: "比喻一个人的才能或仪表在一群人里显得很突出"],
    [word: "鸡飞蛋打", pinyin: "jī fēi dàn dǎ", meaning: "比喻两头落空，一无所得"],
    [word: "狼吞虎咽", pinyin: "láng tūn hǔ yàn", meaning: "形容吃东西又猛又急"],
    [word: "手舞足蹈", pinyin: "shǒu wǔ zú dǎo", meaning: "形容手足动作，极其高兴"],
    [word: "张牙舞爪", pinyin: "zhāng yá wǔ zhǎo", meaning: "形容面目狰狞"],
    [word: "眉飞色舞", pinyin: "méi fēi sè wǔ", meaning: "形容人非常高兴"],
    [word: "心旷神怡", pinyin: "xīn kuàng shén yí", meaning: "心情舒畅，精神愉快"],
    [word: "赏心悦目", pinyin: "shǎng xīn yuè mù", meaning: "看到美好的景色而心情舒畅"],
    [word: "络绎不绝", pinyin: "luò yì bù jué", meaning: "形容人、马、车船等连续不断"],
    [word: "川流不息", pinyin: "chuān liú bù xī", meaning: "形容行人、车马等像水流一样连续不断"],
    [word: "小心翼翼", pinyin: "xiǎo xīn yì yì", meaning: "形容谨慎小心，不敢疏忽"],
    [word: "一丝不苟", pinyin: "yī sī bù gǒu", meaning: "形容做事极其认真，不马虎"],
    [word: "聚精会神", pinyin: "jù jīng huì shén", meaning: "形容注意力高度集中"],
    [word: "废寝忘食", pinyin: "fèi qǐn wàng shí", meaning: "形容专心努力，顾不得睡觉，忘记了吃饭"],
    [word: "一丝不苟", pinyin: "yī sī bù gǒu", meaning: "形容做事极其认真"],
    [word: "全神贯注", pinyin: "quán shén guàn zhù", meaning: "形容注意力高度集中"],
    [word: "目不转睛", pinyin: "mù bù zhuǎn jīng", meaning: "形容注意力高度集中"],
    [word: "垂头丧气", pinyin: "chuí tóu sàng qì", meaning: "形容因失败或不顺而情绪低落"],
    [word: "兴高采烈", pinyin: "xìng gāo cǎi liè", meaning: "形容非常高兴"],
    [word: "怒气冲冲", pinyin: "nù qì chōng chōng", meaning: "形容愤怒到极点"],
    [word: "愁眉苦脸", pinyin: "chóu méi kǔ liǎn", meaning: "形容愁苦的样子"],
    [word: "喜笑颜开", pinyin: "xǐ xiào yán kāi", meaning: "形容非常高兴"],
    [word: "面红耳赤", pinyin: "miàn hóng ěr chì", meaning: "形容因激动或羞愧而脸红"],
    [word: "大惊失色", pinyin: "dà jīng shī sè", meaning: "形容非常惊慌"],
    [word: "泰然自若", pinyin: "tài rán zì ruò", meaning: "形容在紧急情况下不慌不忙"],
    [word: "临危不惧", pinyin: "lín wēi bù jù", meaning: "面对危险不害怕"],
    [word: "舍己为人", pinyin: "shě jǐ wèi rén", meaning: "舍弃自己的利益帮助别人"],
    [word: "见义勇为", pinyin: "jiàn yì yǒng wéi", meaning: "看到正义的事情勇敢地去做"],
    [word: "拾金不昧", pinyin: "shí jīn bù mèi", meaning: "捡到东西不藏起来，交还失主"],
    [word: "尊老爱幼", pinyin: "zūn lǎo ài yòu", meaning: "尊敬老人，爱护小孩"],
    [word: "勤学苦练", pinyin: "qín xué kǔ liàn", meaning: "勤奋学习，刻苦训练"],
    [word: "废寝忘食", pinyin: "fèi qǐn wàng shí", meaning: "形容专心努力"],
    [word: "学无止境", pinyin: "xué wú zhǐ jìng", meaning: "学业没有尽头，需要不断学习"],
    [word: "敏而好学", pinyin: "mǐn ér hào xué", meaning: "天资聪敏而喜好学习"],
    [word: "韦编三绝", pinyin: "wéi biān sān jué", meaning: "形容读书勤奋"],
    [word: "囊萤映雪", pinyin: "náng yíng yìng xuě", meaning: "形容刻苦学习"],
    [word: "悬梁刺股", pinyin: "xuán liáng cì gǔ", meaning: "形容刻苦学习"],
    [word: "凿壁偷光", pinyin: "záo bì tōu guāng", meaning: "形容刻苦学习"],
    [word: "程门立雪", pinyin: "chéng mén lì xuě", meaning: "形容尊师重道"],
    [word: "孔融让梨", pinyin: "kǒng róng ràng lí", meaning: "形容谦让"],
    [word: "卧薪尝胆", pinyin: "wò xīn cháng dǎn", meaning: "形容刻苦自励，发愤图强"],
    [word: "闻鸡起舞", pinyin: "wén jī qǐ wǔ", meaning: "形容有志之士及时奋发"],
    [word: "自强不息", pinyin: "zì qiáng bù xī", meaning: "自觉努力向上，永不懈怠"],
    [word: "坚韧不拔", pinyin: "jiān rèn bù bá", meaning: "意志坚定，不可动摇"],
    [word: "持之以恒", pinyin: "chí zhī yǐ héng", meaning: "有恒心地坚持下去"],
    [word: "水滴石穿", pinyin: "shuǐ dī shí chuān", meaning: "比喻力量虽小，只要坚持不懈，事情就能成功"],
    [word: "绳锯木断", pinyin: "shéng jù mù duàn", meaning: "比喻力量虽小，坚持下去就能成功"],
    [word: "铁杵磨针", pinyin: "tiě chǔ mó zhēn", meaning: "比喻有恒心，肯努力"],
    [word: "愚公移山", pinyin: "yú gōng yí shān", meaning: "比喻坚持不懈地改造自然"],
    [word: "精卫填海", pinyin: "jīng wèi tián hǎi", meaning: "比喻有恒心，有毅力"],
    [word: "夸父逐日", pinyin: "kuā fù zhú rì", meaning: "比喻有宏大的志向或不自量力"],
    [word: "后羿射日", pinyin: "hòu yì shè rì", meaning: "比喻战胜困难"],
    [word: "女娲补天", pinyin: "nǚ wā bǔ tiān", meaning: "比喻改造天地"],
    [word: "开天辟地", pinyin: "kāi tiān pì dì", meaning: "比喻创立空前伟大的事业"],
    [word: "钻木取火", pinyin: "zuān mù qǔ huǒ", meaning: "比喻创造条件"],
]

def selected = idioms.take(count)
def questions = []

selected.each { item ->
    // 成语填空（挖去最后一个字）
    def lastChar = item.word[-1]
    def stem = "成语\"${item.word[0..-2]}______\"的最后一个字是："
    questions << collector.fill(stem, lastChar, "${item.word}（${item.pinyin}）：${item.meaning}")
    // 释义单选
    def others = idioms.findAll { it.word != item.word }.take(3).collect { it.meaning }
    if (others.size() >= 2) {
        def options = [item.meaning] + others
        java.util.Collections.shuffle(options)
        questions << collector.choice("成语\"${item.word}\"的意思是：", options,
                options.indexOf(item.meaning), "${item.word}（${item.pinyin}）")
    }
}

collector.log("成语 ${selected.size()} 个，生成题目 ${questions.size()} 道")
return questions