package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chua.starter.eduai.mapper.EduPoemGradeMapper;
import com.chua.starter.eduai.mapper.EduPoemMapper;
import com.chua.starter.eduai.model.EduPoem;
import com.chua.starter.eduai.model.EduPoemGrade;
import com.chua.starter.eduai.service.IEduPoemService;
import com.chua.starter.eduai.support.util.EduUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 古诗服务实现
 *
 * <p>趣味游戏「补全下句」：取一首诗的相邻两句，给出上一句，让玩家从 4 个选项里选出下一句。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class EduPoemServiceImpl extends ServiceImpl<EduPoemMapper, EduPoem>
        implements IEduPoemService {

    /**
     * 句末分隔符：按句切分正文
     */
    private static final String SENTENCE_DELIMITERS = "[。！？!?；;]";

    /**
     * 游戏选项个数（1 个正确 + 3 个干扰）
     */
    private static final int OPTION_SIZE = 4;

    /**
     * 用于把 JSON 数组形态的正文还原为纯文本
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final EduPoemGradeMapper poemGradeMapper;

    @Override
    public List<EduPoem> listByGradeLevel(String gradeLevel, String grade, int count) {
        int limit = Math.max(1, count);
        QueryWrapper<EduPoem> wrapper = new QueryWrapper<>();
        applyOwnerScope(wrapper);

        if (StringUtils.hasText(gradeLevel)) {
            List<Long> ids = poemIdsOfLevel(gradeLevel.trim(), StringUtils.hasText(grade) ? grade.trim() : null);
            if (ids.isEmpty()) {
                return List.of();
            }
            wrapper.in("poem_id", ids);
        }
        // ORDER BY RAND() 在 MySQL / MariaDB 上均可用，保证每次取题不重复
        wrapper.last("ORDER BY RAND() LIMIT " + limit);
        return baseMapper.selectList(wrapper);
    }

    /**
     * 查询某学段（可再限定年级）下的古诗 ID 集合。
     *
     * <p>年级为空或与库中记录一致时参与匹配，即：记录年级为 NULL（学段通用）的也能被选中。</p>
     *
     * @param gradeLevel 学段（小学/初中/高中）
     * @param grade      年级，可为空
     * @return 古诗 ID 列表
     */
    private List<Long> poemIdsOfLevel(String gradeLevel, String grade) {
        QueryWrapper<EduPoemGrade> wrapper = new QueryWrapper<EduPoemGrade>()
                .eq("grade_level", gradeLevel);
        String userId = EduUserContext.isAdmin() ? null : EduUserContext.currentUserId();
        if (userId != null) {
            wrapper.and(w -> w.eq("poem_grade_owner_user_id", userId).or().isNull("poem_grade_owner_user_id"));
        }
        if (grade != null) {
            wrapper.and(w -> w.eq("grade", grade).or().isNull("grade"));
        }
        return poemGradeMapper.selectList(wrapper).stream()
                .map(EduPoemGrade::getPoemId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * 归属范围：管理员看全部，普通用户看「自己 + 系统内置（归属为 NULL）」。
     *
     * @param wrapper 查询条件
     */
    private void applyOwnerScope(QueryWrapper<EduPoem> wrapper) {
        if (EduUserContext.isAdmin()) {
            return;
        }
        String userId = EduUserContext.currentUserId();
        wrapper.and(w -> w.eq("poem_owner_user_id", userId).or().isNull("poem_owner_user_id"));
    }

    @Override
    public Map<String, Object> generatePoemGame(String gradeLevel, String grade, int count) {
        int target = Math.max(1, count);
        // 多取一些，保证有足够句子充当干扰项
        List<EduPoem> poems = listByGradeLevel(gradeLevel, grade, target * 3);

        List<Map<String, Object>> items = new ArrayList<>();
        List<String> distractorPool = new ArrayList<>();
        Random random = new Random();

        for (EduPoem poem : poems) {
            List<String> sentences = splitSentences(poem.getPoemContent());
            if (sentences.size() >= 2) {
                distractorPool.addAll(sentences);
            }
        }

        for (EduPoem poem : poems) {
            if (items.size() >= target) {
                break;
            }
            List<String> sentences = splitSentences(poem.getPoemContent());
            if (sentences.size() < 2) {
                continue;
            }
            int index = random.nextInt(sentences.size() - 1);
            String question = sentences.get(index);
            String answer = sentences.get(index + 1);

            Set<String> options = new LinkedHashSet<>();
            options.add(answer);
            List<String> shuffled = new ArrayList<>(distractorPool);
            Collections.shuffle(shuffled, random);
            for (String candidate : shuffled) {
                if (options.size() >= OPTION_SIZE) {
                    break;
                }
                if (!candidate.equals(question) && !candidate.equals(answer)) {
                    options.add(candidate);
                }
            }
            List<String> optionList = new ArrayList<>(options);
            Collections.shuffle(optionList, random);

            Map<String, Object> item = new HashMap<>();
            item.put("poemId", poem.getPoemId());
            item.put("title", poem.getPoemTitle());
            item.put("author", poem.getPoemAuthor());
            item.put("dynasty", poem.getPoemDynasty());
            item.put("question", question + "，");
            item.put("answer", answer);
            item.put("options", optionList);
            item.put("answerIndex", optionList.indexOf(answer));
            item.put("difficulty", poem.getPoemDifficulty());
            items.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("totalItems", items.size());
        result.put("gradeLevel", gradeLevel);
        result.put("grade", grade);
        return result;
    }

    /**
     * 把正文按句切成句列表（去除空白与空句）。
     *
     * <p>采集源（如全唐诗 dataset）的正文常以 JSON 数组形态给出，
     * 例如 {@code ["秦川雄帝宅，函谷壯皇居。","綺殿千尋起，離宮百雉餘。"]}；
     * 这里先还原成按行分隔的纯文本，再按句末标点切分，避免句子里混入 JSON 引号与方括号。</p>
     *
     * @param content 正文
     * @return 句列表
     */
    private List<String> splitSentences(String content) {
        List<String> sentences = new ArrayList<>();
        String plain = normalizeContent(content);
        if (!StringUtils.hasText(plain)) {
            return sentences;
        }
        for (String part : plain.split(SENTENCE_DELIMITERS)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    /**
     * 把 JSON 数组形态的正文还原为按行分隔的纯文本；非数组形态原样返回。
     *
     * @param content 原始正文
     * @return 归一化后的正文
     */
    private String normalizeContent(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return content;
        }
        try {
            List<String> parts = JSON.readValue(trimmed, new TypeReference<List<String>>() {
            });
            return String.join("\n", parts);
        } catch (Exception ignored) {
            // 不是合法字符串数组时按原文本处理
            return content;
        }
    }
}
