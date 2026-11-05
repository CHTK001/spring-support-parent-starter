// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.mapper.EduIdiomGradeMapper;
import com.chua.starter.eduai.mapper.EduIdiomMapper;
import com.chua.starter.eduai.model.EduIdiom;
import com.chua.starter.eduai.model.EduIdiomGrade;
import com.chua.starter.eduai.service.IEduIdiomService;
import com.chua.starter.eduai.support.util.EduUserContext;
import com.chua.starter.eduai.support.util.GradeLevels;
import com.chua.starter.eduai.support.util.PageBounds;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 成语服务实现
 *
 * @since 4.0.0.42
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class EduIdiomServiceImpl extends ServiceImpl<EduIdiomMapper, EduIdiom>
        implements IEduIdiomService {

    private final EduIdiomGradeMapper idiomGradeMapper;

    @Override
    public PageResult<EduIdiom> pageQuery(String keyword, String gradeLevel, String grade,
                                         Integer difficulty,
                                         Integer pageNum, Integer pageSize) {
        Page<EduIdiom> page = new Page<>(PageBounds.pageNum(pageNum), PageBounds.pageSize(pageSize));
        QueryWrapper<EduIdiom> wrapper = new QueryWrapper<>();
        if (!EduUserContext.isAdmin()) {
            String userId = EduUserContext.currentUserId();
            wrapper.and(w -> w.isNull("idiom_owner_user_id").or().eq("idiom_owner_user_id", userId));
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like("idiom_content", kw)
                    .or().like("idiom_pinyin", kw)
                    .or().like("idiom_meaning", kw));
        }
        if (difficulty != null) {
            wrapper.eq("idiom_difficulty", difficulty);
        }
        if (gradeLevel != null && !gradeLevel.isBlank()) {
            QueryWrapper<EduIdiomGrade> gw = new QueryWrapper<EduIdiomGrade>()
                    .eq("grade_level", GradeLevels.normalize(gradeLevel.trim()));
            if (grade != null && !grade.isBlank()) {
                gw.and(w -> w.eq("grade", grade.trim()).or().isNull("grade"));
            }
            List<Long> ids = idiomGradeMapper.selectList(gw).stream()
                    .map(EduIdiomGrade::getIdiomId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            if (ids.isEmpty()) {
                return PageResult.<EduIdiom>builder()
                        .pageNo((int) PageBounds.pageNum(pageNum))
                        .pageSize((int) PageBounds.pageSize(pageSize))
                        .totalPages(0).total(0).data(List.of()).build();
            }
            wrapper.in("idiom_id", ids);
        }
        wrapper.orderByDesc("idiom_id");
        IPage<EduIdiom> result = baseMapper.selectPage(page, wrapper);
        return PageResult.<EduIdiom>builder()
                .pageNo((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .totalPages((int) result.getPages())
                .total(result.getTotal())
                .data(result.getRecords())
                .build();
    }

    @Override
    public List<EduIdiom> listByGradeLevel(String gradeLevel, String grade, int count) {
        String userId = EduUserContext.isAdmin() ? null : EduUserContext.currentUserId();
        // 前端下拉/模块配置传 PRIMARY/JUNIOR 这类码值，映射表（edu_idiom_grade）里存的是中文名，
        // 不归一化就会「筛选恒为 0 条」——消消乐与接龙都从这里取数，必须在这一层统一。
        String level = GradeLevels.normalize(gradeLevel);
        if (level == null || level.isEmpty()) {
            return baseMapper.selectRandom(count, userId);
        }
        return baseMapper.selectByGradeLevel(level, grade, count, userId);
    }

    @Override
    public Map<String, Object> generateIdiomGame(String gradeLevel, String grade, int count) {
        int target = Math.max(1, count);
        boolean hasLevel = gradeLevel != null && !gradeLevel.isBlank();
        // 先按学段取题；本学段不够（含「一条都没有」，例如高中目前 0 条）时用全库补齐。
        // 不补的话会产出一盘空局/残局：高中学生点开消消乐什么也玩不了。
        List<EduIdiom> idioms = new ArrayList<>(listByGradeLevel(gradeLevel, grade, target));
        String scope = hasLevel ? "level" : "all";
        if (hasLevel && idioms.size() < target) {
            scope = idioms.isEmpty() ? "all" : "mixed";
            Set<Long> already = idioms.stream()
                    .map(EduIdiom::getIdiomId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            List<EduIdiom> extra = listByGradeLevel(null, null, target * 3).stream()
                    .filter(item -> item.getIdiomId() == null || !already.contains(item.getIdiomId()))
                    .limit(target - idioms.size())
                    .toList();
            idioms.addAll(extra);
        }

        // 为每个成语生成打乱的拼音和释义选项
        List<Map<String, Object>> gameItems = new ArrayList<>();
        for (EduIdiom idiom : idioms) {
            Map<String, Object> item = new HashMap<>();
            item.put("idiomId", idiom.getIdiomId());
            item.put("content", idiom.getIdiomContent());
            item.put("pinyin", idiom.getIdiomPinyin());
            item.put("meaning", idiom.getIdiomMeaning());
            item.put("difficulty", idiom.getIdiomDifficulty());
            item.put("tags", idiom.getIdiomTags());
            gameItems.add(item);
        }

        // 生成干扰项（其他成语的拼音和释义）
        List<EduIdiom> distractors = baseMapper.selectRandom(count * 2,
                EduUserContext.isAdmin() ? null : EduUserContext.currentUserId());
        List<String> distractorPinyin = distractors.stream()
                .map(EduIdiom::getIdiomPinyin)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<String> distractorMeanings = distractors.stream()
                .map(EduIdiom::getIdiomMeaning)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("items", gameItems);
        result.put("distractorPinyin", distractorPinyin);
        result.put("distractorMeanings", distractorMeanings);
        result.put("totalItems", gameItems.size());
        result.put("gradeLevel", gradeLevel);
        result.put("grade", grade);
        // level=全部按学段取材；mixed=本学段不够、用全库补齐；all=本学段无内容、整盘来自全库
        result.put("scope", scope);

        return result;
    }

    @Override
    public boolean validateIdiomPair(Long idiomId, String pinyin, String meaning) {
        EduIdiom idiom = getById(idiomId);
        if (idiom == null) {
            return false;
        }
        // 验证拼音和释义是否匹配
        boolean pinyinMatch = pinyin != null && pinyin.equals(idiom.getIdiomPinyin());
        boolean meaningMatch = meaning != null && meaning.equals(idiom.getIdiomMeaning());
        return pinyinMatch && meaningMatch;
    }

    // ==================== 成语接龙（服务端自动出题） ====================

    @Override
    public Map<String, Object> generateChain(String gradeLevel, String grade, int length) {
        int steps = Math.max(3, Math.min(length, 10));
        boolean hasLevel = gradeLevel != null && !gradeLevel.isBlank();
        // 先按学段出题；该学段没有「可接续」的成语时回落全库。
        // 现实数据就是这样：edu_idiom_grade 目前 830 条小学、8 条初中、0 条高中，
        // 不给回落的话初高中生点开模块只会看到一个错误页。
        Map<String, Object> planned = planChain(gradeLevel, grade, steps);
        String scope = hasLevel ? "level" : "all";
        if (planned == null && hasLevel) {
            planned = planChain(null, null, steps);
            scope = "all";
        }
        if (planned == null) {
            throw new BusinessException("成语库不足以生成接龙（需要首尾字能相接的成语）");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("start", planned.get("start"));
        out.put("length", planned.get("length"));
        // scope=all 表示「本学段没内容，用了全库」：前端据此给出说明，而不是默默换池子
        out.put("scope", scope);
        out.put("rule", "下一个成语的首字必须等于当前成语的末字");
        return out;
    }

    /**
     * 在给定学段内挑一个「可接最长」的起点并算出链长。
     *
     * @param gradeLevel 学段（可空；内部会归一化）
     * @param grade      年级（可空）
     * @param steps      期望步数上限
     * @return 含 start / length 的出题计划；该范围内挑不出可接的起点时返回 null
     */
    private Map<String, Object> planChain(String gradeLevel, String grade, int steps) {
        String userId = ownerFilter();
        // 取样必须大：只取 30 条随机成语时，常常没有一条的末字能接上，
        // 会误报「成语库不足以生成接龙」（库里其实有几千条）。
        List<EduIdiom> pool = listByGradeLevel(gradeLevel, grade, 800);
        if (pool.isEmpty()) {
            return null;
        }
        Set<String> firstChars = new HashSet<>();
        for (EduIdiom item : pool) {
            String first = firstChar(item.getIdiomContent());
            if (first != null) {
                firstChars.add(first);
            }
        }
        EduIdiom start = null;
        int reachable = 0;
        int tried = 0;
        // 只在「末字确有后继」的候选里试算链长，挑最长的一条；
        // 试算次数封顶，避免起手就打一串前缀查询。
        for (EduIdiom seed : pool) {
            String last = lastChar(seed.getIdiomContent());
            if (last == null || !firstChars.contains(last)) {
                continue;
            }
            if (++tried > 25) {
                break;
            }
            int len = previewChainLength(seed.getIdiomContent(), steps, userId);
            if (len > reachable) {
                reachable = len;
                start = seed;
            }
            if (reachable >= steps) {
                break;
            }
        }
        if (start == null || reachable <= 0) {
            return null;
        }
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("start", describeIdiom(start));
        plan.put("length", Math.max(1, reachable));
        return plan;
    }

    @Override
    public Map<String, Object> validateChain(String current, String answer) {
        Map<String, Object> out = new LinkedHashMap<>();
        String last = lastChar(current);
        String input = answer == null ? null : answer.trim();
        if (last == null) {
            out.put("ok", false);
            out.put("message", "当前成语不合法");
            return out;
        }
        if (input == null || input.isEmpty()) {
            out.put("ok", false);
            out.put("message", "请输入要接的成语");
            return out;
        }
        if (!input.startsWith(last)) {
            out.put("ok", false);
            out.put("message", "接龙要以「" + last + "」开头");
            return out;
        }
        if (input.equals(current.trim())) {
            out.put("ok", false);
            out.put("message", "不能接自己");
            return out;
        }
        EduIdiom hit = findExact(input, ownerFilter());
        if (hit == null) {
            out.put("ok", false);
            out.put("message", "成语库里没有「" + input + "」");
            return out;
        }
        out.put("ok", true);
        out.put("content", hit.getIdiomContent());
        out.put("next", lastChar(hit.getIdiomContent()));
        out.put("meaning", hit.getIdiomMeaning());
        out.put("pinyin", hit.getIdiomPinyin());
        return out;
    }

    @Override
    public Map<String, Object> chainHint(String current) {
        String last = lastChar(current);
        String userId = ownerFilter();
        Map<String, Object> out = new LinkedHashMap<>();
        if (last == null) {
            out.put("ok", false);
            return out;
        }
        List<EduIdiom> nexts = findByPrefix(last, userId).stream()
                .filter(x -> !x.getIdiomContent().equals(current == null ? null : current.trim()))
                .toList();
        if (nexts.isEmpty()) {
            out.put("ok", false);
            return out;
        }
        EduIdiom pick = nexts.get(0);
        out.put("ok", true);
        out.put("content", pick.getIdiomContent());
        out.put("pinyin", pick.getIdiomPinyin());
        out.put("meaning", pick.getIdiomMeaning());
        return out;
    }

    /**
     * 归属过滤值：管理员返回 null（不过滤），普通用户返回本人 userId。
     *
     * @return 归属过滤值
     */
    private String ownerFilter() {
        return EduUserContext.isAdmin() ? null : EduUserContext.currentUserId();
    }

    /**
     * 取成语末字（接龙锚点）。
     *
     * @param content 成语
     * @return 末字；为空时返回 null
     */
    private String lastChar(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String s = content.trim();
        return s.substring(s.length() - 1);
    }

    /**
     * 取成语首字（接龙时用来判断「末字有没有后继」）。
     *
     * @param content 成语
     * @return 首字；为空时返回 null
     */
    private String firstChar(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        return content.trim().substring(0, 1);
    }

    /**
     * 预演从某个成语出发能接多少步（用于挑「可接最长」的起点）。
     *
     * @param from   起始成语
     * @param steps  期望步数上限
     * @param userId 归属过滤值
     * @return 实际可接步数
     */
    private int previewChainLength(String from, int steps, String userId) {
        Set<String> used = new HashSet<>();
        used.add(from);
        String current = from;
        int count = 0;
        for (int i = 0; i < steps; i++) {
            String last = lastChar(current);
            List<EduIdiom> nexts = findByPrefix(last, userId).stream()
                    .filter(x -> !used.contains(x.getIdiomContent()))
                    .toList();
            if (nexts.isEmpty()) {
                break;
            }
            current = nexts.get(0).getIdiomContent();
            used.add(current);
            count++;
        }
        return count;
    }

    /**
     * 按首字前缀查成语（排除完全等于前缀的自身）。
     *
     * @param prefix 首字
     * @param userId 归属过滤值，null 表示不过滤
     * @return 命中列表（最多 20）
     */
    private List<EduIdiom> findByPrefix(String prefix, String userId) {
        QueryWrapper<EduIdiom> wrapper = new QueryWrapper<EduIdiom>()
                .likeRight("idiom_content", prefix)
                .ne("idiom_content", prefix)
                .last("LIMIT 20");
        if (userId != null) {
            wrapper.and(w -> w.isNull("idiom_owner_user_id").or().eq("idiom_owner_user_id", userId));
        }
        return baseMapper.selectList(wrapper);
    }

    /**
     * 精确查成语。
     *
     * @param content 成语
     * @param userId  归属过滤值，null 表示不过滤
     * @return 成语；不存在返回 null
     */
    private EduIdiom findExact(String content, String userId) {
        QueryWrapper<EduIdiom> wrapper = new QueryWrapper<EduIdiom>()
                .eq("idiom_content", content)
                .last("LIMIT 1");
        if (userId != null) {
            wrapper.and(w -> w.isNull("idiom_owner_user_id").or().eq("idiom_owner_user_id", userId));
        }
        return baseMapper.selectOne(wrapper);
    }

    /**
     * 成语的前端字段。
     *
     * @param idiom 成语
     * @return 字段表
     */
    private Map<String, Object> describeIdiom(EduIdiom idiom) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idiomId", idiom.getIdiomId());
        m.put("content", idiom.getIdiomContent());
        m.put("pinyin", idiom.getIdiomPinyin());
        m.put("meaning", idiom.getIdiomMeaning());
        return m;
    }
}