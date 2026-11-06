package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.mapper.EduHanziMapper;
import com.chua.starter.eduai.mapper.EduHanziTongjiaMapper;
import com.chua.starter.eduai.model.EduHanzi;
import com.chua.starter.eduai.model.EduHanziTongjia;
import com.chua.starter.eduai.service.IEduHanziService;
import com.chua.starter.eduai.support.util.EduUserContext;
import com.chua.starter.eduai.support.util.GradeLevels;
import com.chua.starter.eduai.support.util.PageBounds;
import com.chua.starter.eduai.support.util.Pinyins;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 汉字基础数据服务实现。
 *
 * <p>归属口径与其它 eduai 资源一致：管理员可见全部，普通用户可见「本人 + 系统预置（归属为空）」。
 * 唯一键是汉字本身，因此「按字查重后更新」保证重复提交不会撞唯一键。</p>
 *
 * <p><b>检索口径（本轮补齐的两块能力）</b>：</p>
 * <ul>
 *   <li><b>拼音</b>：带声调、无声调、多音字任一读音都要能命中。
 *       带调与多读音走 {@code hanzi_pinyin} 的包含匹配（该列允许逗号分隔多读音），
 *       无声调走 {@link Pinyins#plain(String)} 归一化后再匹配。</li>
 *   <li><b>通假字</b>：本字与借字双向可查。借字存在 {@code edu_hanzi_tongjia} 关联表里，
 *       查询时先按借字反查本字 id，再并入主查询条件。</li>
 * </ul>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class EduHanziServiceImpl extends ServiceImpl<EduHanziMapper, EduHanzi> implements IEduHanziService {

    /**
     * 通假字关联 Mapper（本字 → 借字，一对多）。
     */
    private final EduHanziTongjiaMapper tongjiaMapper;

    @Override
    public PageResult<EduHanzi> pageQuery(String keyword, String gradeLevel, Integer pageNum, Integer pageSize) {
        EduUserContext.OwnerScope scope = EduUserContext.ownerScope();
        Page<EduHanzi> page = new Page<>(PageBounds.pageNum(pageNum), PageBounds.pageSize(pageSize));
        QueryWrapper<EduHanzi> wrapper = new QueryWrapper<>();
        if (scope.ownerPredicate() != null) {
            wrapper.and(w -> w.isNull("hanzi_owner_user_id").or().eq("hanzi_owner_user_id", scope.ownerPredicate()));
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 拼音检索的三种写法都要通吃（本轮实测缺陷：只 like 带调列，敲 zhong 查不到 zhòng）：
            //   1) 带声调原样：zhòng / měi
            //   2) 无声调：zhong / mei（用 Pinyins 归一化后比较）
            //   3) 多音字任读音：hang / xing（hanzi_pinyin 允许逗号分隔多读音）
            // 另外通假字借字也要能搜到本字：搜「悦」要命中「说」。
            String plain = Pinyins.plain(kw);
            List<Long> tongjiaOwners = findOwnersByTongjia(kw);
            wrapper.and(w -> {
                w.like("hanzi_char", kw)
                        .or().like("hanzi_pinyin", kw)
                        .or().like("hanzi_meaning", kw);
                if (plain != null) {
                    // 无声调匹配：把库里的读音在 SQL 侧归一化后比较。
                    // 只在关键词里含拉丁字母时才加这条 —— 纯汉字关键词归一化后还是它自己，
                    // 加上去只是白白多扫一遍列。
                    if (containsLatin(plain)) {
                        w.or().apply(normalizedPinyinLikeSql(), plain);
                    }
                }
                if (!tongjiaOwners.isEmpty()) {
                    w.or().in("hanzi_id", tongjiaOwners);
                }
            });
        }
        if (StringUtils.hasText(gradeLevel)) {
            // 前端下拉传 PRIMARY/JUNIOR 码值，库里存中文名，不归一化筛选恒为 0 条
            wrapper.eq("hanzi_grade_level", GradeLevels.normalize(gradeLevel));
        }
        wrapper.orderByDesc("hanzi_id");
        IPage<EduHanzi> result = baseMapper.selectPage(page, wrapper);
        List<EduHanzi> records = result.getRecords();
        fillTongjia(records);
        return PageResult.<EduHanzi>builder()
                .pageNo((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .totalPages((int) result.getPages())
                .total(result.getTotal())
                .data(records)
                .build();
    }

    /**
     * 「无声调读音包含匹配」的 SQL 片段。
     *
     * <p>库里 {@code hanzi_pinyin} 存带声调写法，检索键是无声调。两种做法：</p>
     * <ol>
     *   <li>加一列存归一化结果 —— 需要 ALTER，而 H2 与 MariaDB 没有共同的补列写法
     *       （详见 V2.2 脚本头的说明），故放弃；</li>
     *   <li>在 SQL 里现算 —— 用 REPLACE 链把声调符号逐个抹平再 LIKE。
     *       代价是全表扫，但汉字库只有 3500 行、检索是人工触发，完全可接受。</li>
     * </ol>
     *
     * <p>占位符顺序：第一个是「归一化后的关键词」（用于 LIKE），第二个同值
     * （REPLACE 的匹配串由常量给出，不需要第二个参数，这里只保留一个占位）。
     * 用 MyBatis-Plus 的 {@code apply} 而不是 {@code like}，因为需要包一层函数。</p>
     *
     * @return SQL 片段，两个 {@code {0}} 占位由调用方绑定
     */
    private static String normalizedPinyinLikeSql() {
        String normalized = "LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE("
                + "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE("
                + "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE("
                + "REPLACE(hanzi_pinyin,' ','')"
                + ",'ā','a'),'á','a'),'ǎ','a'),'à','a')"
                + ",'ē','e'),'é','e'),'ě','e'),'è','e')"
                + ",'ī','i'),'í','i'),'ǐ','i'),'ì','i')"
                + ",'ō','o'),'ó','o'),'ǒ','o'),'ò','o')"
                + ",'ū','u'),'ú','u'),'ǔ','u'),'ù','u')"
                + ",'ǖ','v'),'ǘ','v'),'ǚ','v'),'ǜ','v')"
                + ",'ü','v'),'ń','n'),'ň','n'),'ǹ','n'))";
        return normalized + " LIKE CONCAT('%', {0}, '%')";
    }

    /**
     * 关键词里是否含拉丁字母（决定要不要走拼音归一化分支）。
     *
     * @param text 关键词
     * @return 含拉丁字母返回 true
     */
    private static boolean containsLatin(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按借字反查本字 id（通假字双向检索）。
     *
     * @param keyword 关键字（可能是借字）
     * @return 本字 id 列表，永不为 null
     */
    private List<Long> findOwnersByTongjia(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        List<EduHanziTongjia> hits = tongjiaMapper.selectList(
                new QueryWrapper<EduHanziTongjia>().like("tongjia_char", keyword.trim()));
        return hits.stream()
                .map(EduHanziTongjia::getTongjiaHanziId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * 给当前页的汉字批量回填通假字（一次 IN 查询，不做 N+1）。
     *
     * @param records 当前页记录
     */
    private void fillTongjia(List<EduHanzi> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> ids = records.stream().map(EduHanzi::getHanziId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return;
        }
        List<EduHanziTongjia> all = tongjiaMapper.selectList(
                new QueryWrapper<EduHanziTongjia>().in("tongjia_hanzi_id", ids));
        Map<Long, List<String>> byOwner = new HashMap<>();
        for (EduHanziTongjia row : all) {
            byOwner.computeIfAbsent(row.getTongjiaHanziId(), k -> new ArrayList<>()).add(row.getTongjiaChar());
        }
        for (EduHanzi hanzi : records) {
            List<String> chars = byOwner.get(hanzi.getHanziId());
            hanzi.setHanziTongjia(chars == null || chars.isEmpty() ? null : String.join(",", chars));
        }
    }

    @Override
    public Long saveHanzi(EduHanzi entity) {
        if (entity == null || !StringUtils.hasText(entity.getHanziChar())) {
            throw new BusinessException("汉字不能为空");
        }
        String ch = entity.getHanziChar().trim();
        if (ch.length() > 1) {
            throw new BusinessException("一次只能维护一个汉字");
        }
        entity.setHanziChar(ch);
        if (entity.getHanziStatus() == null) {
            entity.setHanziStatus(1);
        }
        if (entity.getHanziId() != null && entity.getHanziId() > 0) {
            EduHanzi exists = getById(entity.getHanziId());
            if (exists == null) {
                throw new BusinessException("汉字不存在");
            }
            Long id = updateExisting(exists, entity);
            syncTongjia(id, entity.getHanziTongjia());
            return id;
        }
        // 按字查重：唯一键保证一个字一条，重复提交同一个字则更新而不是新增
        EduHanzi same = findByChar(ch);
        if (same != null) {
            Long id = updateExisting(same, entity);
            syncTongjia(id, entity.getHanziTongjia());
            return id;
        }
        entity.setOwnerUserId(EduUserContext.requireUserId());
        try {
            save(entity);
            syncTongjia(entity.getHanziId(), entity.getHanziTongjia());
            return entity.getHanziId();
        } catch (RuntimeException ex) {
            // 并发竞态：两个请求同时判定「库里没有这个字」，后落库的会撞唯一键。
            // 回查一次，谁先落库谁赢，另一个转为更新 —— 这是「两人同时录入同一个字」
            // 的正常冲突，不该以「系统繁忙」的形式抛给用户。
            //
            // 注意两个已实测的坑：
            // ① 本方法**不能**套 @Transactional：MySQL/MariaDB 默认 REPEATABLE READ，
            //    同一事务的快照看不到并发事务刚提交的行，回查仍为空 → 依旧报错；
            // ② 回查也**不能**用 FOR UPDATE：MariaDB 在快照外的行上做锁定读会抛
            //    「Record has changed since last read in table」（SQLState HY000 / 1020），
            //    实测把失败数从 1 次放大到 4 次。
            // 去掉显式事务后每条语句自动提交，回查即为最新已提交数据，冲突被正常吸收。
            EduHanzi raced = findByChar(ch);
            if (raced == null) {
                throw ex;
            }
            Long id = updateExisting(raced, entity);
            syncTongjia(id, entity.getHanziTongjia());
            return id;
        }
    }

    /**
     * 同步通假字关联表（本字 → 借字，一对多）。
     *
     * <p>口径是「先清后建」：提交里没写的借字会被删掉，与页面表单的整体覆盖语义一致
     * （用户在输入框里删掉一个借字，期望它真的消失，而不是留在库里继续被搜到）。</p>
     *
     * <p>提交值为 {@code null} 时**不动**关联表：那是「只改释义、没碰通假字」的调用方，
     * 清空会让它们无意中丢掉已有通假数据。</p>
     *
     * @param hanziId 本字标识
     * @param tongjia 借字串（逗号分隔），null 表示不改动
     */
    private void syncTongjia(Long hanziId, String tongjia) {
        if (hanziId == null || tongjia == null) {
            return;
        }
        tongjiaMapper.delete(new QueryWrapper<EduHanziTongjia>().eq("tongjia_hanzi_id", hanziId));
        Set<String> chars = new LinkedHashSet<>();
        for (String raw : tongjia.split("[,，;；、/|\\s]+")) {
            String c = raw.trim();
            if (!c.isEmpty()) {
                chars.add(c);
            }
        }
        for (String c : chars) {
            EduHanziTongjia row = new EduHanziTongjia();
            row.setTongjiaHanziId(hanziId);
            row.setTongjiaChar(c);
            tongjiaMapper.insert(row);
        }
    }

    /**
     * 按字查已有记录（唯一键保证最多一条）。
     *
     * @param ch 汉字
     * @return 记录；不存在返回 null
     */
    private EduHanzi findByChar(String ch) {
        return getOne(new QueryWrapper<EduHanzi>().eq("hanzi_char", ch).last("LIMIT 1"), false);
    }

    /**
     * 用本次提交覆盖库中已有记录（先做归属写权限校验）。
     *
     * @param target 库中已有记录
     * @param entity 本次提交
     * @return 汉字标识
     */
    private Long updateExisting(EduHanzi target, EduHanzi entity) {
        EduUserContext.checkWritable(target.getOwnerUserId());
        entity.setHanziId(target.getHanziId());
        entity.setOwnerUserId(target.getOwnerUserId());
        updateById(entity);
        return entity.getHanziId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * delete Hanzi
     * @param id 长整数
     * @return 返回值
    */
    public boolean deleteHanzi(Long id) {
        if (id == null) {
            return false;
        }
        EduHanzi exists = getById(id);
        if (exists == null) {
            return false;
        }
        EduUserContext.checkWritable(exists.getOwnerUserId());
        // 先删通假字关联：本字没了，借字记录没有意义，留着会让「搜借字命中已删本字」永远查不到行
        tongjiaMapper.delete(new QueryWrapper<EduHanziTongjia>().eq("tongjia_hanzi_id", id));
        return removeById(id);
    }

    // ==================== 识字练习（消费「读音 / 笔画 / 笔顺 / 字画」维护字段） ====================

    /** 每题选项个数 */
    private static final int OPTION_COUNT = 4;

    /** 笔顺题允许的最大笔画数（再长点起来太累，不出这种题） */
    private static final int MAX_ORDER_LEN = 6;

    @Override
    public List<Map<String, Object>> generatePractice(String gradeLevel, int count) {
        int target = Math.max(1, Math.min(count, 20));
        List<EduHanzi> pool = loadPool(gradeLevel, target);
        if (pool.isEmpty() && StringUtils.hasText(gradeLevel)) {
            // 该学段还没维护汉字（现状：只有 12 条小学字）：回落全库出题，
            // 而不是把「汉字库为空」的错误页甩给初高中学生。
            // 管理员一旦录入本学段汉字，就会自动按学段取材。
            pool = loadPool(null, target);
        }
        if (pool.isEmpty()) {
            throw new BusinessException("汉字库为空，无法出题（请先在「数据配置 → 汉字库」维护汉字）");
        }
        List<String> pinyins = pool.stream()
                .map(EduHanzi::getHanziPinyin)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        List<Map<String, Object>> items = new ArrayList<>();
        int index = 0;
        for (EduHanzi hanzi : pool) {
            if (items.size() >= target) {
                break;
            }
            Map<String, Object> question = buildQuestion(hanzi, index, pinyins, pool);
            if (question == null) {
                // 读音与笔画都缺失的字不出题，宁可少出也不造假题
                continue;
            }
            items.add(question);
            index++;
        }
        if (items.isEmpty()) {
            throw new BusinessException("汉字库缺少读音/笔画数据，暂时无法出题");
        }
        return items;
    }

    /**
     * 按学段载入候选汉字（停用的除外）。
     *
     * <p>取 4 倍样本：一部分用来出题，剩下的用来当干扰项，避免干扰项只来自同一小批字。</p>
     *
     * @param gradeLevel 学段（可空；空表示全库）
     * @param target     目标题数
     * @return 候选汉字
     */
    private List<EduHanzi> loadPool(String gradeLevel, int target) {
        QueryWrapper<EduHanzi> wrapper = new QueryWrapper<>();
        String userId = EduUserContext.isAdmin() ? null : EduUserContext.currentUserId();
        if (userId != null) {
            wrapper.and(w -> w.isNull("hanzi_owner_user_id").or().eq("hanzi_owner_user_id", userId));
        }
        // 停用的字不出题（0 停用 / 1 启用）
        wrapper.eq("hanzi_status", 1);
        if (StringUtils.hasText(gradeLevel)) {
            wrapper.eq("hanzi_grade_level", GradeLevels.normalize(gradeLevel));
        }
        wrapper.last("ORDER BY RAND() LIMIT " + (target * 4));
        return baseMapper.selectList(wrapper);
    }

    /**
     * 造一道题：三种题型轮转 —— 读音 → 笔画数 → 笔顺（按顺序点笔画）。
     *
     * <p>轮到的题型缺数据时，依次退化为其它题型，而不是把这道题丢掉。</p>
     *
     * @param hanzi   汉字
     * @param index   题目序号（决定题型）
     * @param pinyins 全库读音（干扰项来源）
     * @param pool    本次样本
     * @return 题目；三种题型都出不了时返回 null
     */
    private Map<String, Object> buildQuestion(EduHanzi hanzi, int index, List<String> pinyins, List<EduHanzi> pool) {
        Map<String, Object> question = describe(hanzi);
        switch (index % 3) {
            case 0 -> {
                if (fillPinyinQuestion(question, hanzi, pinyins)) {
                    return question;
                }
            }
            case 1 -> {
                if (fillStrokesQuestion(question, hanzi, pool)) {
                    return question;
                }
            }
            default -> {
                if (fillOrderQuestion(question, hanzi)) {
                    return question;
                }
            }
        }
        // 指定题型的数据缺失 → 退化为其它题型
        if (fillPinyinQuestion(question, hanzi, pinyins) || fillStrokesQuestion(question, hanzi, pool)) {
            return question;
        }
        return null;
    }

    /**
     * 读音题：看字选拼音。
     *
     * @param question 题目（就地写入）
     * @param hanzi    汉字
     * @param pinyins  全库读音
     * @return 是否出题成功
     */
    private boolean fillPinyinQuestion(Map<String, Object> question, EduHanzi hanzi, List<String> pinyins) {
        if (!StringUtils.hasText(hanzi.getHanziPinyin()) || pinyins.size() < 2) {
            return false;
        }
        question.put("type", "pinyin");
        question.put("answer", hanzi.getHanziPinyin());
        question.put("options", pickOptions(hanzi.getHanziPinyin(), pinyins));
        return true;
    }

    /**
     * 笔画数题：看字数笔画。
     *
     * @param question 题目（就地写入）
     * @param hanzi    汉字
     * @param pool     本次样本（干扰项来源）
     * @return 是否出题成功
     */
    private boolean fillStrokesQuestion(Map<String, Object> question, EduHanzi hanzi, List<EduHanzi> pool) {
        if (hanzi.getHanziStrokes() == null) {
            return false;
        }
        String answer = String.valueOf(hanzi.getHanziStrokes());
        List<String> counts = pool.stream()
                .map(EduHanzi::getHanziStrokes)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .distinct()
                .toList();
        question.put("type", "strokes");
        question.put("answer", answer);
        question.put("options", pickOptions(answer, counts));
        return true;
    }

    /**
     * 笔顺题：把笔画打乱后交给前端，要求按书写顺序依次点出来。
     *
     * <p>契约与其它题型一致：{@code options} 是打乱后的笔画、{@code answer} 是正确顺序
     * （拼成一个串，重复笔画也能正确比对）。</p>
     *
     * @param question 题目（就地写入）
     * @param hanzi    汉字
     * @return 是否出题成功
     */
    private boolean fillOrderQuestion(Map<String, Object> question, EduHanzi hanzi) {
        List<String> shuffled = shuffledStrokes(hanzi.getHanziStrokeOrder());
        if (shuffled == null) {
            return false;
        }
        question.put("type", "order");
        question.put("answer", hanzi.getHanziStrokeOrder().trim());
        question.put("options", shuffled);
        return true;
    }

    /**
     * 把笔顺串打散成可点的选项。
     *
     * <p>只在「点得动」的前提下出题：长度 2~{@value #MAX_ORDER_LEN}；打散后必须与原顺序不同，
     * 否则题目无意义。重试若干次仍相同就放弃这道题（返回 null）。</p>
     *
     * @param order 笔顺串
     * @return 打散后的笔画列表；不适合出题时返回 null
     */
    private List<String> shuffledStrokes(String order) {
        if (order == null) {
            return null;
        }
        String target = order.trim();
        if (target.length() < 2 || target.length() > MAX_ORDER_LEN) {
            return null;
        }
        List<String> chars = new ArrayList<>();
        for (int i = 0; i < target.length(); i++) {
            chars.add(String.valueOf(target.charAt(i)));
        }
        for (int attempt = 0; attempt < 8; attempt++) {
            Collections.shuffle(chars);
            if (!String.join("", chars).equals(target)) {
                return chars;
            }
        }
        return null;
    }

    /**
     * 组选项：正确项 + 最多 3 个不重复干扰项，并打乱顺序。
     *
     * @param correct 正确项
     * @param all     候选池
     * @return 选项列表
     */
    private List<String> pickOptions(String correct, List<String> all) {
        List<String> options = new ArrayList<>();
        options.add(correct);
        for (String candidate : all) {
            if (options.size() >= OPTION_COUNT) {
                break;
            }
            if (!options.contains(candidate)) {
                options.add(candidate);
            }
        }
        Collections.shuffle(options);
        return options;
    }

    /**
     * 题目与「学习卡」共用的汉字字段。
     *
     * @param hanzi 汉字
     * @return 字段表
     */
    private Map<String, Object> describe(EduHanzi hanzi) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("hanziId", hanzi.getHanziId());
        item.put("char", hanzi.getHanziChar());
        item.put("pinyin", hanzi.getHanziPinyin());
        item.put("pinyinAll", hanzi.getHanziPinyin());
        item.put("tongjia", hanzi.getHanziTongjia());
        item.put("strokes", hanzi.getHanziStrokes());
        item.put("radical", hanzi.getHanziRadical());
        item.put("structure", hanzi.getHanziStructure());
        item.put("strokeOrder", hanzi.getHanziStrokeOrder());
        item.put("meaning", hanzi.getHanziMeaning());
        item.put("image", hanzi.getHanziImage());
        return item;
    }
}
