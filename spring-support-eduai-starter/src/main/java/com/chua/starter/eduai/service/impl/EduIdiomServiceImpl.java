package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.eduai.mapper.EduIdiomMapper;
import com.chua.starter.eduai.model.EduIdiom;
import com.chua.starter.eduai.service.IEduIdiomService;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 成语服务实现
 *
 * @since 4.0.0.42
 * @author CH
 */
@Service
public class EduIdiomServiceImpl extends ServiceImpl<EduIdiomMapper, EduIdiom>
        implements IEduIdiomService {

    @Override
    public List<EduIdiom> listByGradeLevel(String gradeLevel, String grade, int count) {
        String userId = EduUserContext.isAdmin() ? null : EduUserContext.currentUserId();
        if (gradeLevel == null || gradeLevel.isEmpty()) {
            return baseMapper.selectRandom(count, userId);
        }
        return baseMapper.selectByGradeLevel(gradeLevel, grade, count, userId);
    }

    @Override
    public Map<String, Object> generateIdiomGame(String gradeLevel, String grade, int count) {
        List<EduIdiom> idioms = listByGradeLevel(gradeLevel, grade, count);

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
}
