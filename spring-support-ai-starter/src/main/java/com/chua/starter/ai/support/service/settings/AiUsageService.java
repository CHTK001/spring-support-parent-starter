package com.chua.starter.ai.support.service.settings;

import com.chua.starter.ai.support.entity.SysAiUsageDaily;
import com.chua.starter.ai.support.model.AiUsageDailyImportResult;
import com.chua.starter.ai.support.model.AiUsageDTO;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用量统计服务接口
 *
 * @since 2026-08-03
 * @author CH
 */
public interface AiUsageService {

    /**
     * 按 分组+服务商+模型 维度查询用量汇总。
     *
     * @param model     模型名称（可选）
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @param userId    归属用户（可为空，为空时统计全部用户）
     * @return 用量 DTO 列表，按 令牌 消耗降序
     */
    List<AiUsageDTO> query(String model, LocalDateTime startTime, LocalDateTime endTime, String userId);

    /**
     * 查询日汇总统计（导出用）。
     *
     * <p>直接读日汇总表 {@code sys_ai_usage_daily}，粒度为
     * 「日期 + 分组 + 服务商 + 模型」，即每次调用一条统计、不含逐次调用明细。</p>
     *
     * @param startDate 开始日期（可选，含）
     * @param endDate   结束日期（可选，含）
     * @param model     模型名称（可选）
     * @return 日汇总行列表，按 日期/分组/服务商/模型 升序
     */
    List<SysAiUsageDaily> queryDaily(LocalDate startDate, LocalDate endDate, String model);

    /**
     * 导入用量统计 CSV（与 {@link #queryDaily} 导出的格式一致）。
     *
     * <p>CSV 表头固定为：
     * {@code statDate,group,provider,model,calls,promptTokens,completionTokens,cachedTokens,totalTokens,cost}。
     * 校验先行：任一行非法则整文件拒绝入库；全部合法时按
     * 「日期 + 分组 + 服务商 + 模型」维度覆盖写入日汇总表，
     * 归属账号落 {@link SysAiUsageService#ORPHAN_OWNER}（导入数据不带用户维度）。</p>
     *
     * @param in CSV 输入流（UTF-8，允许带 BOM）
     * @return 导入结果（总行数 / 写入行数 / 失败行数 / 失败明细）
     * @throws IOException 读取输入流失败
     */
    AiUsageDailyImportResult importDailyCsv(InputStream in) throws IOException;
}
