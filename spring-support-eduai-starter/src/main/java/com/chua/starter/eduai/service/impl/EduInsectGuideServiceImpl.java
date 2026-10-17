package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.eduai.mapper.EduInsectGuideMapper;
import com.chua.starter.eduai.model.EduInsectGuide;
import com.chua.starter.eduai.model.dto.InsectGuideSaveDTO;
import com.chua.starter.eduai.service.IEduInsectGuideService;
import com.chua.starter.eduai.support.util.EduUserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 昆虫深度讲解与测验服务实现
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduInsectGuideServiceImpl extends ServiceImpl<EduInsectGuideMapper, EduInsectGuide>
        implements IEduInsectGuideService {

    /**
     * JSON 解析器，用于 lesson / quiz 的读写
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> getGuideView(String insectCode) {
        if (insectCode == null || insectCode.isBlank()) {
            return null;
        }
        EduInsectGuide guide = baseMapper.selectByCode(insectCode);
        if (guide == null) {
            return null;
        }
        Map<String, Object> view = new HashMap<>();
        view.put("insectCode", guide.getInsectCode());
        // 讲解步骤是对象数组（含 title/body/anchor）
        view.put("lesson", parseObjectArray(guide.getGuideLesson()));
        Map<String, Object> motion = new HashMap<>();
        motion.put("title", guide.getGuideMotionTitle());
        motion.put("body", guide.getGuideMotionBody());
        view.put("motion", motion);
        // 测验题含 options 字符串数组，整体仍为对象数组
        view.put("quiz", parseObjectArray(guide.getGuideQuiz()));
        Map<String, Object> habitat = new HashMap<>();
        habitat.put("title", guide.getGuideHabitatTitle());
        habitat.put("body", guide.getGuideHabitatBody());
        view.put("habitat", habitat);
        return view;
    }

    @Override
    public boolean saveGuide(InsectGuideSaveDTO dto) {
        if (dto == null || dto.insectCode() == null || dto.insectCode().isBlank()) {
            return false;
        }
        String code = dto.insectCode().trim();
        EduInsectGuide existing = baseMapper.selectByCode(code);
        EduInsectGuide row = existing == null ? new EduInsectGuide() : existing;
        row.setInsectCode(code);
        row.setGuideLesson(writeJson(dto.lesson()));
        row.setGuideQuiz(writeJson(dto.quiz()));
        row.setGuideMotionTitle(dto.motion() == null ? null : dto.motion().title());
        row.setGuideMotionBody(dto.motion() == null ? null : dto.motion().body());
        row.setGuideHabitatTitle(dto.habitat() == null ? null : dto.habitat().title());
        row.setGuideHabitatBody(dto.habitat() == null ? null : dto.habitat().body());

        if (existing == null) {
            row.setOwnerUserId(EduUserContext.requireUserId());
            return save(row);
        }
        EduUserContext.checkWritable(existing.getOwnerUserId());
        row.setOwnerUserId(existing.getOwnerUserId());
        // 刻意不用 updateById / saveOrUpdate：它们按实体拼列清单，会带上本库不存在的
        // SysBase 列（create_name / create_by / update_name / update_by）而报错。
        return baseMapper.updateByCode(row) > 0;
    }

    @Override
    public Set<String> listGuidedCodes() {
        String userId = EduUserContext.isAdmin() ? null : EduUserContext.currentUserId();
        List<String> codes = baseMapper.selectAllCodes(userId);
        return codes == null ? new HashSet<>() : new HashSet<>(codes);
    }

    @Override
    public boolean removeByCode(String insectCode) {
        if (insectCode == null || insectCode.isBlank()) {
            return false;
        }
        return remove(new QueryWrapper<EduInsectGuide>().eq("insect_code", insectCode.trim()));
    }

    @Override
    public int removeByCodes(List<String> insectCodes) {
        if (insectCodes == null || insectCodes.isEmpty()) {
            return 0;
        }
        return baseMapper.delete(new QueryWrapper<EduInsectGuide>().in("insect_code", insectCodes));
    }

    /**
     * 把对象序列化成 JSON 文本落库
     *
     * @param value 待序列化对象，可为 null
     * @return JSON 文本；null 或序列化失败时返回 null
     */
    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("昆虫讲解序列化失败，该字段置空: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 把 JSON 文本列解析成对象数组
     *
     * @param raw JSON 文本
     * @return 解析后的对象列表，空值或失败时为空列表
     */
    private List<Map<String, Object>> parseObjectArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            log.warn("昆虫讲解 JSON 列解析失败，返回空列表: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
