package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.mapper.EduHanziMapper;
import com.chua.starter.eduai.model.EduHanzi;
import com.chua.starter.eduai.service.IEduHanziService;
import com.chua.starter.eduai.support.util.EduUserContext;
import com.chua.starter.eduai.support.util.GradeLevels;
import com.chua.starter.eduai.support.util.PageBounds;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 汉字基础数据服务实现。
 *
 * <p>归属口径与其它 eduai 资源一致：管理员可见全部，普通用户可见「本人 + 系统预置（归属为空）」。
 * 唯一键是汉字本身，因此「按字查重后更新」保证重复提交不会撞唯一键。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Service
public class EduHanziServiceImpl extends ServiceImpl<EduHanziMapper, EduHanzi> implements IEduHanziService {

    @Override
    public PageResult<EduHanzi> pageQuery(String keyword, String gradeLevel, Integer pageNum, Integer pageSize) {
        EduUserContext.OwnerScope scope = EduUserContext.ownerScope();
        Page<EduHanzi> page = new Page<>(PageBounds.pageNum(pageNum), PageBounds.pageSize(pageSize));
        QueryWrapper<EduHanzi> wrapper = new QueryWrapper<>();
        if (scope.ownerPredicate() != null) {
            wrapper.and(w -> w.isNull("hanzi_owner_user_id").or().eq("hanzi_owner_user_id", scope.ownerPredicate()));
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like("hanzi_char", keyword)
                    .or().like("hanzi_pinyin", keyword)
                    .or().like("hanzi_meaning", keyword));
        }
        if (StringUtils.hasText(gradeLevel)) {
            // 前端下拉传 PRIMARY/JUNIOR 码值，库里存中文名，不归一化筛选恒为 0 条
            wrapper.eq("hanzi_grade_level", GradeLevels.normalize(gradeLevel));
        }
        wrapper.orderByDesc("hanzi_id");
        IPage<EduHanzi> result = baseMapper.selectPage(page, wrapper);
        return PageResult.<EduHanzi>builder()
                .pageNo((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .totalPages((int) result.getPages())
                .total(result.getTotal())
                .data(result.getRecords())
                .build();
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
            return updateExisting(exists, entity);
        }
        // 按字查重：唯一键保证一个字一条，重复提交同一个字则更新而不是新增
        EduHanzi same = findByChar(ch);
        if (same != null) {
            return updateExisting(same, entity);
        }
        entity.setOwnerUserId(EduUserContext.requireUserId());
        try {
            save(entity);
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
            return updateExisting(raced, entity);
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
        item.put("strokes", hanzi.getHanziStrokes());
        item.put("radical", hanzi.getHanziRadical());
        item.put("structure", hanzi.getHanziStructure());
        item.put("strokeOrder", hanzi.getHanziStrokeOrder());
        item.put("meaning", hanzi.getHanziMeaning());
        item.put("image", hanzi.getHanziImage());
        return item;
    }
}
