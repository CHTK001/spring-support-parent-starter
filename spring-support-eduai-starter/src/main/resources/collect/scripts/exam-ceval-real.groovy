// 名称: C-Eval 真题库采集
// 学科: 多学科
// 说明: 下载 C-Eval 公开真题集（阿里云 OSS 上的 ceval-exam.zip，52 个学科、含中高考真题），解析 CSV 生成带四个选项与标准答案的单选题
// 配置: {"archiveUrl":"https://modelscope.oss-cn-beijing.aliyuncs.com/open_data/c-eval/ceval-exam.zip","split":"val","subjects":"high_school_mathematics,high_school_physics,high_school_history","limitPerSubject":20,"timeoutSeconds":120}
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/** 一门学科的落库口径 */
class SubjectMeta {
    String name
    String gradeLevel
    String grade
}

def archiveUrl = collector.config('archiveUrl',
        'https://modelscope.oss-cn-beijing.aliyuncs.com/open_data/c-eval/ceval-exam.zip')
def split = collector.config('split', 'val').trim().toLowerCase()
def subjects = collector.config('subjects',
        'high_school_mathematics,high_school_physics,high_school_chemistry,high_school_biology,' +
                'high_school_history,high_school_geography,high_school_politics,high_school_chinese,' +
                'middle_school_mathematics,middle_school_physics,middle_school_chemistry,middle_school_biology,' +
                'middle_school_history,middle_school_geography,middle_school_politics')
        .split(',').collect { it.trim() }.findAll { it }
def limitPerSubject = collector.configInt('limitPerSubject', 20)

def archive = collector.httpBytes(archiveUrl)
collector.log("真题包下载完成: ${archive.length} 字节，准备解析 ${subjects.size()} 个学科的 ${split} 分片")

def targets = subjects.collectEntries { [(makeEntryName(it, split)): it] }
def bodies = readZipEntries(archive, targets)

def questions = []
subjects.each { code ->
    def entryName = makeEntryName(code, split)
    def text = bodies[entryName]
    if (!text) {
        collector.log("归档内未找到 ${entryName}，跳过")
        return
    }
    def meta = subjectMeta(code)
    def rows = parseCsv(text)
    def cols = headerIndex(rows[0])
    if (cols == null) {
        collector.log("${code} 表头缺少 question/answer 列，跳过")
        return
    }
    def built = rows.tail()
            .collect { row -> toQuestion(collector, row, cols, code, meta, split) }
            .findAll { it != null }
            .take(limitPerSubject)
    questions.addAll(built)
    collector.log("${meta.name}（${code}）取题 ${built.size()} 道")
}

collector.log("真题包 ${archive.length} 字节，命中条目 ${bodies.size()}/${subjects.size()}，生成真题 ${questions.size()} 道")
return questions

/**
 * 归档内的条目名，与 C-Eval 目录约定一致。
 */
static String makeEntryName(String code, String split) {
    return "${split}/${code}_${split}.csv"
}

/**
 * 把学科代号映射为题库口径（学科名 + 学段 + 年级）。
 */
static SubjectMeta subjectMeta(String code) {
    def names = [
            mathematics                 : '数学',
            physics                     : '物理',
            chemistry                   : '化学',
            biology                     : '生物',
            history                     : '历史',
            geography                   : '地理',
            politics                    : '政治',
            chinese                     : '语文',
            chinese_language_and_literature: '汉语言文学',
            advanced_mathematics        : '高等数学',
            discrete_mathematics        : '离散数学',
            probability_and_statistics  : '概率统计',
            college_physics             : '大学物理',
            college_chemistry           : '大学化学',
            college_programming         : '程序设计',
            computer_network            : '计算机网络',
            computer_architecture       : '计算机组成原理',
            operating_system            : '操作系统',
            electrical_engineer         : '电气工程师',
            metrology_engineer          : '计量工程师',
            law                         : '法学',
            marxism                     : '马克思主义',
            mao_zedong_thought          : '毛泽东思想',
            ideological_and_moral_cultivation: '思想道德修养',
            modern_chinese_history      : '中国近代史',
            education_science           : '教育学',
            teacher_qualification       : '教师资格',
            civil_servant               : '公务员',
            accountant                  : '会计',
            tax_accountant              : '税务师',
            business_administration     : '工商管理',
            college_economics           : '经济学',
            urban_and_rural_planner     : '城乡规划',
            environmental_impact_assessment_engineer: '环评工程师',
            fire_engineer               : '消防工程师',
            plant_protection            : '植物保护',
            veterinary_medicine         : '兽医',
            basic_medicine              : '基础医学',
            clinical_medicine           : '临床医学',
            physician                   : '医师',
            legal_professional          : '法律职业资格',
            professional_tour_guide     : '导游资格',
            art_studies                 : '艺术概论',
            logic                       : '逻辑学',
            sports_science              : '运动训练'
    ]
    def tail = code.replaceFirst('^(high_school|middle_school|primary_school|college)_', '')
    def name = names[code] ?: names[tail] ?: '综合'
    def level = 'OTHER'
    def grade = '其他'
    if (code.startsWith('high_school_')) {
        level = 'SENIOR'
        grade = '高中'
    } else if (code.startsWith('middle_school_')) {
        level = 'JUNIOR'
        grade = '初中'
    } else if (code.startsWith('primary_school_')) {
        level = 'PRIMARY'
        grade = '小学'
    } else if (code.startsWith('college_') || ['advanced_mathematics', 'discrete_mathematics',
            'probability_and_statistics', 'marxism', 'mao_zedong_thought',
            'ideological_and_moral_cultivation', 'modern_chinese_history', 'education_science',
            'computer_network', 'computer_architecture', 'operating_system', 'logic',
            'art_studies', 'legal_professional', 'law'].contains(code)) {
        level = 'UNIVERSITY'
        grade = '大学'
    }
    return new SubjectMeta(name: name, gradeLevel: level, grade: grade)
}

/**
 * 逐字节解析 zip 归档，返回目标条目的 UTF-8 文本。
 */
static Map<String, String> readZipEntries(byte[] archive, Map<String, String> targets) {
    def found = [:]
    def buffer = new byte[8192]
    def zin = new ZipInputStream(new ByteArrayInputStream(archive))
    try {
        ZipEntry entry
        while ((entry = zin.nextEntry) != null) {
            if (!targets.containsKey(entry.name)) {
                zin.closeEntry()
                continue
            }
            def out = new ByteArrayOutputStream()
            int read
            while ((read = zin.read(buffer)) > 0) {
                out.write(buffer, 0, read)
            }
            found[entry.name] = new String(out.toByteArray(), 'UTF-8').replace('\uFEFF', '')
            zin.closeEntry()
        }
    } finally {
        zin.close()
    }
    return found
}

/**
 * 解析 CSV 文本（支持双引号包裹、字段内逗号与换行、CRLF）。
 */
static List<List<String>> parseCsv(String text) {
    def rows = []
    def row = []
    def field = new StringBuilder()
    boolean quoted = false
    for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i)
        if (quoted) {
            if (c == ('"' as char)) {
                if (i + 1 < text.length() && text.charAt(i + 1) == ('"' as char)) {
                    field.append('"')
                    i++
                } else {
                    quoted = false
                }
            } else {
                field.append(c)
            }
            continue
        }
        if (c == ('"' as char) && field.length() == 0) {
            quoted = true
        } else if (c == (',' as char)) {
            row << field.toString()
            field.setLength(0)
        } else if (c == ('\n' as char)) {
            row << field.toString()
            field.setLength(0)
            rows << row
            row = []
        } else if (c != ('\r' as char)) {
            field.append(c)
        }
    }
    if (field.length() > 0 || !row.isEmpty()) {
        row << field.toString()
        rows << row
    }
    return rows.findAll { !it.isEmpty() && !(it.size() == 1 && !it[0].trim()) }
}

/**
 * 表头列名到下标，缺必要列时返回 null。
 */
static Map<String, Integer> headerIndex(List<String> header) {
    def cols = [:]
    header.eachWithIndex { String name, int index ->
        cols[name.trim().toLowerCase()] = index
    }
    if (!cols.containsKey('question') || !cols.containsKey('answer')) {
        return null
    }
    return cols
}

/**
 * 一行 CSV 转一道单选题；题干/答案/选项不完整时返回 null。
 */
static def toQuestion(collector, List<String> row, Map<String, Integer> cols,
                      String code, SubjectMeta meta, String split) {
    def cell = { String key ->
        def index = cols[key.toLowerCase()]
        return index == null || row.size() <= index ? '' : row[index].trim()
    }
    def stem = cell('question')
    def answer = cell('answer').toUpperCase()
    if (!stem || answer.length() != 1 || 'ABCDEFGH'.indexOf(answer) < 0) {
        return null
    }
    def letters = 'ABCDEFGH'
    def options = []
    def answerIndex = -1
    for (int i = 0; i < letters.length(); i++) {
        def text = cell(letters[i])
        if (!text) {
            continue
        }
        if (letters[i] == answer) {
            answerIndex = options.size()
        }
        options << text
    }
    if (options.size() < 2 || answerIndex < 0) {
        return null
    }
    def rowId = cell('id')
    def question = collector.choice(stem, options, answerIndex,
            "C-Eval 公开真题（学科代号 ${code}，分片 ${split}${rowId ? '，原题号 ' + rowId : ''}），标准答案 ${answer}。")
    question.subject = meta.name
    question.gradeLevel = meta.gradeLevel
    question.grade = meta.grade
    question.difficulty = 3
    question.source = "ceval:${code}/${split}${rowId ? '#' + rowId : ''}"
    return question
}
