package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.eduai.mapper.EduInsectMapper;
import com.chua.starter.eduai.model.EduInsect;
import com.chua.starter.eduai.service.IEduInsectGuideService;
import com.chua.starter.eduai.service.IEduInsectService;
import com.chua.starter.eduai.support.util.EduUserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 昆虫图鉴服务实现
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduInsectServiceImpl extends ServiceImpl<EduInsectMapper, EduInsect>
        implements IEduInsectService {

    /**
     * JSON 解析器，用于把 facts / hotspots / lifecycle / relatives 还原成前端可直接渲染的结构
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 讲解与测验服务，用于组装展台视图；构造注入避免与本类循环依赖
     */
    private final IEduInsectGuideService guideService;

    @Override
    public List<EduInsect> listByFilter(String order, String metamorphosis, Integer maxDifficulty, int count) {
        return baseMapper.selectByFilter(order, metamorphosis, maxDifficulty, count, currentUserScope());
    }

    @Override
    public List<Map<String, Object>> countByOrder() {
        return baseMapper.countByOrder(currentUserScope());
    }

    @Override
    public List<Map<String, Object>> listLibrary() {
        List<EduInsect> all = baseMapper.selectAll(currentUserScope());
        // 一次取出「已有讲解」的 slug 集合，避免逐行查库
        Set<String> guided = guideService.listGuidedCodes();
        List<Map<String, Object>> list = new ArrayList<>();
        for (EduInsect insect : all) {
            Map<String, Object> row = new HashMap<>();
            row.put("insectId", insect.getInsectId());
            row.put("insectCode", insect.getInsectCode());
            row.put("insectName", insect.getInsectName());
            row.put("insectLatin", insect.getInsectLatin());
            row.put("insectEpithet", insect.getInsectEpithet());
            row.put("insectOrder", insect.getInsectOrder());
            row.put("insectMetamorphosis", insect.getInsectMetamorphosis());
            row.put("insectAccent", insect.getInsectAccent());
            row.put("insectModelCode", insect.getInsectModelCode());
            row.put("insectDifficulty", insect.getInsectDifficulty());
            // 只抽出 anchor 列表：前端「看同样有某部位的虫」要在名录里直接判断
            // 哪几只虫有该部位，逐个再请求详情代价太大（63 次），这里一次带出来。
            row.put("anchors", extractAnchors(insect.getInsectHotspots()));
            // 维护页据此标出「漏配讲解」的物种
            row.put("hasGuide", guided.contains(insect.getInsectCode()));
            list.add(row);
        }
        return list;
    }

    /**
     * 从 hotspots JSON 里抽出全部 anchor
     *
     * @param raw hotspots 的 JSON 文本
     * @return anchor 列表，空值或解析失败时为空列表
     */
    private List<String> extractAnchors(String raw) {
        List<String> anchors = new ArrayList<>();
        for (Map<String, Object> item : parseObjectArray(raw)) {
            Object anchor = item.get("anchor");
            if (anchor != null && !anchor.toString().isBlank()) {
                anchors.add(anchor.toString());
            }
        }
        return anchors;
    }

    @Override
    public Map<String, Object> getStageView(String insectCode) {
        Map<String, Object> result = new HashMap<>();
        if (insectCode == null || insectCode.isBlank()) {
            result.put("insect", null);
            result.put("guide", null);
            return result;
        }
        EduInsect insect = baseMapper.selectByCode(insectCode);
        result.put("insect", insect == null ? null : toDetailView(insect));
        result.put("guide", guideService.getGuideView(insectCode));
        return result;
    }

    /**
     * 物种详情视图：把四个 JSON 文本列解析成数组下发
     *
     * @param insect 物种实体
     * @return 详情视图
     */
    @Override
    public Map<String, Object> toDetailView(EduInsect insect) {
        Map<String, Object> item = new HashMap<>();
        item.put("insectId", insect.getInsectId());
        item.put("insectCode", insect.getInsectCode());
        item.put("insectName", insect.getInsectName());
        item.put("insectLatin", insect.getInsectLatin());
        item.put("insectEpithet", insect.getInsectEpithet());
        item.put("insectOrder", insect.getInsectOrder());
        item.put("insectSummary", insect.getInsectSummary());
        item.put("insectEcology", insect.getInsectEcology());
        item.put("insectTrivia", insect.getInsectTrivia());
        item.put("insectMetamorphosis", insect.getInsectMetamorphosis());
        item.put("insectRange", insect.getInsectRange());
        item.put("insectStatus", insect.getInsectStatus());
        item.put("insectAccent", insect.getInsectAccent());
        item.put("insectModelCode", insect.getInsectModelCode());
        item.put("insectDifficulty", insect.getInsectDifficulty());
        item.put("insectFacts", parseObjectArray(insect.getInsectFacts()));
        item.put("insectHotspots", parseObjectArray(insect.getInsectHotspots()));
        item.put("insectLifecycle", parseStringArray(insect.getInsectLifecycle()));
        item.put("insectRelatives", parseStringArray(insect.getInsectRelatives()));
        return item;
    }

    @Override
    public Map<String, Object> generateInsectGame(String order, String metamorphosis, Integer maxDifficulty,
            int count) {
        List<EduInsect> insects = listByFilter(order, metamorphosis, maxDifficulty, count);
        List<Map<String, Object>> gameItems = new ArrayList<>();
        for (EduInsect insect : insects) {
            Map<String, Object> item = new HashMap<>();
            item.put("insectId", insect.getInsectId());
            item.put("insectCode", insect.getInsectCode());
            item.put("insectName", insect.getInsectName());
            item.put("insectLatin", insect.getInsectLatin());
            item.put("insectEpithet", insect.getInsectEpithet());
            item.put("insectOrder", insect.getInsectOrder());
            item.put("insectSummary", insect.getInsectSummary());
            item.put("insectEcology", insect.getInsectEcology());
            item.put("insectTrivia", insect.getInsectTrivia());
            item.put("insectMetamorphosis", insect.getInsectMetamorphosis());
            item.put("insectRange", insect.getInsectRange());
            item.put("insectStatus", insect.getInsectStatus());
            item.put("insectAccent", insect.getInsectAccent());
            item.put("insectDifficulty", insect.getInsectDifficulty());
            // 下面四项落库时是 JSON 文本，这里解析成数组下发，前端无需再 parse
            item.put("insectFacts", parseObjectArray(insect.getInsectFacts()));
            item.put("insectHotspots", parseObjectArray(insect.getInsectHotspots()));
            item.put("insectLifecycle", parseStringArray(insect.getInsectLifecycle()));
            item.put("insectRelatives", parseStringArray(insect.getInsectRelatives()));
            gameItems.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", gameItems);
        result.put("totalItems", gameItems.size());
        result.put("order", order);
        result.put("metamorphosis", metamorphosis);
        result.put("maxDifficulty", maxDifficulty);
        return result;
    }

    @Override
    public boolean validateInsectPair(Long insectId, String latin) {
        if (insectId == null) {
            return false;
        }
        EduInsect insect = getById(insectId);
        if (insect == null || latin == null) {
            return false;
        }
        return latin.trim().equalsIgnoreCase(insect.getInsectLatin());
    }

    /**
     * 把数据库中的「对象数组」JSON 文本列解析成对象列表
     *
     * <p>用于 insect_facts（[{key,value,icon}]）与 insect_hotspots（[{id,label,note,anchor,tone}]）。
     * 空串、空值或解析失败一律返回空列表：宁可少给字段，也不让整个接口 500。</p>
     *
     * @param raw JSON 文本
     * @return 解析后的对象列表，失败时为空列表
     */
    private List<Map<String, Object>> parseObjectArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            log.warn("昆虫图鉴对象数组列解析失败，返回空列表: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 把数据库中的「字符串数组」JSON 文本列解析成字符串列表
     *
     * <p>用于 insect_lifecycle（["卵","幼虫","成虫"]）与 insect_relatives（["近缘种",...]）。
     * 这两列是纯字符串数组，必须按 List&lt;String&gt; 解析；若错用 List&lt;Map&gt;，
     * Jackson 会抛「no String-argument constructor to deserialize from String value」，
     * 结果整列静默变空。</p>
     *
     * @param raw JSON 文本
     * @return 解析后的字符串列表，失败时为空列表
     */
    private List<String> parseStringArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("昆虫图鉴字符串数组列解析失败，返回空列表: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 取当前用户的归属隔离范围
     *
     * @return 管理员返回 null（看全部），其余返回当前用户 ID
     */
    private String currentUserScope() {
        return EduUserContext.isAdmin() ? null : EduUserContext.currentUserId();
    }
}
