package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.eduai.model.EduInsectGuide;
import com.chua.starter.eduai.model.dto.InsectGuideSaveDTO;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 昆虫深度讲解与测验服务接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduInsectGuideService extends IService<EduInsectGuide> {

    /**
     * 取指定物种的讲解与测验，JSON 列已解析成数组
     *
     * @param insectCode 物种英文标识（slug）
     * @return 讲解视图：lesson / motion / quiz / habitat；无数据时返回 null
     */
    Map<String, Object> getGuideView(String insectCode);

    /**
     * 保存（新建或更新）指定物种的讲解与测验
     *
     * <p>按 {@code insectCode} 一对一覆盖：同一物种只有一份讲解，
     * 已存在则更新，不存在则插入。</p>
     *
     * @param dto 讲解内容；{@code insectCode} 必填
     * @return 是否保存成功
     */
    boolean saveGuide(InsectGuideSaveDTO dto);

    /**
     * 全部已有讲解的物种标识
     *
     * <p>名录接口用它标出「哪些物种已配讲解」，维护页据此筛出漏配的物种，
     * 避免逐个点开才知道缺哪几个。</p>
     *
     * @return 物种 slug 集合
     */
    Set<String> listGuidedCodes();

    /**
     * 删除指定物种的讲解
     *
     * <p>删除物种时调用，避免留下悬挂的讲解记录。</p>
     *
     * @param insectCode 物种英文标识（slug）
     * @return 是否删除了记录
     */
    boolean removeByCode(String insectCode);

    /**
     * 批量删除指定物种的讲解
     *
     * @param insectCodes 物种英文标识集合
     * @return 删除条数
     */
    int removeByCodes(List<String> insectCodes);
}
