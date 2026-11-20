package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiPricing;

import java.util.List;

/**
 * AI 定价服务接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface SysAiPricingService {

    /**
     * 人工配置单价来源标记（取价优先级高于同步价与估算价）
     */
    String SOURCE_MANUAL = "manual";

    /**
     * 获取最新定价
     *
     * @param provider 服务商
     * @param model 模型
     * @return 定价信息
     */
    SysAiPricing getLatestPricing(String provider, String model);

    /**
     * 同步官方定价
     */
    void syncOfficialPricing();

    /**
     * 查询人工配置的单价列表（source=manual）。
     *
     * @param keyword 厂商/模型关键字（可空）
     * @return 人工单价列表
     */
    List<SysAiPricing> listManualPricing(String keyword);

    /**
     * 保存人工单价（按 厂商 + 模型 原行覆盖，source 置为 manual）。
     *
     * @param pricing 单价数据（价格单位：币种/千令牌）
     * @return 保存后的单价行
     */
    SysAiPricing saveManualPricing(SysAiPricing pricing);

    /**
     * 删除人工单价（仅允许删除 manual 行），删除后该模型恢复同步价/估算价。
     *
     * @param id sys_ai_pricing 主键
     * @return 是否删除成功
     */
    boolean deleteManualPricing(Long id);
}
