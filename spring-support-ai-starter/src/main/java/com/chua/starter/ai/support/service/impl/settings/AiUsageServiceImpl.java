package com.chua.starter.ai.support.service.impl.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.starter.ai.support.entity.SysAiUsageDaily;
import com.chua.starter.ai.support.mapper.SysAiUsageDailyMapper;
import com.chua.starter.ai.support.mapper.SysAiUsageMapper;
import com.chua.starter.ai.support.model.AiUsageDailyImportResult;
import com.chua.starter.ai.support.model.AiUsageDTO;
import com.chua.starter.ai.support.service.SysAiUsageService;
import com.chua.starter.ai.support.service.settings.AiUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用量统计服务实现类——按 分组+服务商+模型 维度返回用量分布。
 *
 * <p><b>数据源分两段</b>：明细表 {@code sys_ai_usage} 是全量明细（百万行级，
 * 「全部时间」按模型 GROUP BY 实测 47 秒），日汇总表 {@code sys_ai_usage_daily}
 * 是同一口径的按天预聚合（几百行，实测 42 毫秒）。因此区间已被聚合覆盖的部分读汇总表，
 * 只有汇总表还没覆盖的尾巴（通常是最近 1~2 天）走明细，最后按键相加合并。</p>
 *
 * @since 2026-08-03
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiUsageServiceImpl implements AiUsageService {

    /**
     * 用量 映射器（明细分段的来源）
     */
    private final SysAiUsageMapper usageMapper;

    /**
     * 用量日汇总 映射器（汇总分段的来源）
     */
    private final SysAiUsageDailyMapper dailyMapper;

    /**
     * 按 分组+服务商+模型 维度查询用量汇总。
     *
     * @param model     模型名称（可选）
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @param userId    归属用户（可为空，为空时统计全部用户）
     * @return 用量 DTO 列表，按令牌降序
     */
    @Override
    public List<AiUsageDTO> query(String model, LocalDateTime startTime, LocalDateTime endTime, String userId) {
        // 无主数据（定时同步的外部工具用量）只对管理员可见，
        // 口径与 SysAiUsageServiceImpl.includeOrphan() 一致。
        boolean includeOrphan = com.chua.starter.ai.support.util.AiUserContext.isAdmin();
        LocalDate coveredEnd = resolveCoveredEnd(startTime, endTime);
        if (coveredEnd == null) {
            return fromDetail(model, startTime, endTime, userId, includeOrphan);
        }
        Map<String, Accumulator> merged = new LinkedHashMap<>();
        collectFromDaily(merged, model, startTime == null ? null : startTime.toLocalDate(), coveredEnd,
                userId, includeOrphan);
        // 汇总表未覆盖的尾巴：从汇总段结束的次日 0 点开始重算
        LocalDateTime tailStart = coveredEnd.plusDays(1).atStartOfDay();
        if (endTime == null || !tailStart.isAfter(endTime)) {
            for (SysAiUsageMapper.UsageSummaryRow row
                    : usageMapper.selectUsageSummary(model, tailStart, endTime, userId, includeOrphan)) {
                accumulate(merged, row.groupName(), row.provider(), row.model(),
                        zero(row.totalCalls()), zero(row.totalPromptTokens()), zero(row.totalCompletionTokens()),
                        zero(row.totalTokens()), zero(row.totalCost()));
            }
        }
        List<AiUsageDTO> result = new ArrayList<>(merged.size());
        for (Accumulator acc : merged.values()) {
            result.add(acc.toDto());
        }
        // 与明细 SQL 的 ORDER BY totalTokens DESC 保持同一顺序
        result.sort((a, b) -> Long.compare(
                b.totalTokens() == null ? 0L : b.totalTokens(),
                a.totalTokens() == null ? 0L : a.totalTokens()));
        return result;
    }

    /**
     * 汇总表可信的最后一天（已减掉一天余量）。
     *
     * <p>为什么不直接用 {@code MAX(stat_date)}：聚合任务按小时重算「当天」，
     * 最后一天随时可能只落了一半的量，读它会少算。留一天余量，把最后一天交给明细段重算。
     * 若汇总表为空、或请求区间整体落在余量之后，返回 {@code null} 表示只能走明细。</p>
     *
     * @param startTime 请求开始时间（可为空）
     * @param endTime   请求结束时间（可为空）
     * @return 汇总段可用的最后一天，不可用时为 {@code null}
     */
    private LocalDate resolveCoveredEnd(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDate maxDaily = dailyMapper.selectMaxStatDate();
        if (maxDaily == null) {
            return null;
        }
        LocalDate coveredEnd = maxDaily.minusDays(1);
        LocalDate endDate = endTime == null ? LocalDate.now() : endTime.toLocalDate();
        if (endDate.isBefore(coveredEnd)) {
            coveredEnd = endDate;
        }
        LocalDate startDate = startTime == null ? null : startTime.toLocalDate();
        if (startDate != null && startDate.isAfter(coveredEnd)) {
            return null;
        }
        return coveredEnd;
    }

    /**
     * 汇总段：读日汇总表并按维度累加（表只有几百行）。
     *
     * @param merged        累加容器
     * @param model         模型名称（可选）
     * @param startDate     开始日期（可选）
     * @param coveredEnd    汇总段最后一天
     * @param userId        归属用户（可为空）
     * @param includeOrphan 是否计入无主明细（管理员口径）
     */
    private void collectFromDaily(Map<String, Accumulator> merged, String model, LocalDate startDate,
                                  LocalDate coveredEnd, String userId, boolean includeOrphan) {
        LambdaQueryWrapper<SysAiUsageDaily> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(SysAiUsageDaily::getGroupName, SysAiUsageDaily::getProvider, SysAiUsageDaily::getModel,
                SysAiUsageDaily::getTotalCalls, SysAiUsageDaily::getTotalPromptTokens,
                SysAiUsageDaily::getTotalCompletionTokens, SysAiUsageDaily::getTotalTokens,
                SysAiUsageDaily::getTotalCost);
        if (model != null && !model.isBlank()) {
            wrapper.eq(SysAiUsageDaily::getModel, model);
        }
        if (startDate != null) {
            wrapper.ge(SysAiUsageDaily::getStatDate, startDate);
        }
        wrapper.le(SysAiUsageDaily::getStatDate, coveredEnd);
        if (userId != null && !userId.isBlank()) {
            if (includeOrphan) {
                wrapper.and(w -> w.eq(SysAiUsageDaily::getUserId, userId)
                        .or().isNull(SysAiUsageDaily::getUserId));
            } else {
                wrapper.eq(SysAiUsageDaily::getUserId, userId);
            }
        }
        for (SysAiUsageDaily row : dailyMapper.selectList(wrapper)) {
            accumulate(merged, row.getGroupName(), row.getProvider(), row.getModel(),
                    zero(row.getTotalCalls()), zero(row.getTotalPromptTokens()), zero(row.getTotalCompletionTokens()),
                    zero(row.getTotalTokens()), zero(row.getTotalCost()));
        }
    }

    /**
     * 明细段：整段都读明细表（汇总表还没覆盖到，或区间太短不值得拆）。
     *
     * @param model         模型名称（可选）
     * @param startTime     开始时间（可选）
     * @param endTime       结束时间（可选）
     * @param userId        归属用户（可为空）
     * @param includeOrphan 是否计入无主明细（管理员口径）
     * @return 用量 DTO 列表
     */
    private List<AiUsageDTO> fromDetail(String model, LocalDateTime startTime, LocalDateTime endTime,
                                        String userId, boolean includeOrphan) {
        List<SysAiUsageMapper.UsageSummaryRow> rows =
                usageMapper.selectUsageSummary(model, startTime, endTime, userId, includeOrphan);
        List<AiUsageDTO> result = new ArrayList<>(rows.size());
        for (SysAiUsageMapper.UsageSummaryRow row : rows) {
            result.add(new AiUsageDTO(row.groupName(), row.provider(), row.model(),
                    zero(row.totalCost()), zero(row.totalCalls()), zero(row.totalPromptTokens()),
                    zero(row.totalCompletionTokens()), zero(row.totalTokens()), null));
        }
        return result;
    }

    /**
     * 把一个分组行累加进容器。
     *
     * @param merged           累加容器
     * @param group            分组名称
     * @param provider         服务商
     * @param model            模型
     * @param totalCalls       调用次数
     * @param promptTokens     输入令牌
     * @param completionTokens 输出令牌
     * @param totalTokens      总令牌
     * @param cost             费用
     */
    private void accumulate(Map<String, Accumulator> merged, String group, String provider, String model,
                            long totalCalls, long promptTokens, long completionTokens,
                            long totalTokens, double cost) {
        String key = String.valueOf(group) + '\u0001' + String.valueOf(provider) + '\u0001' + String.valueOf(model);
        Accumulator acc = merged.computeIfAbsent(key, k -> new Accumulator(group, provider, model));
        acc.calls += totalCalls;
        acc.promptTokens += promptTokens;
        acc.completionTokens += completionTokens;
        acc.totalTokens += totalTokens;
        acc.cost += cost;
    }

    /**
     * 空安全：把包装类型还原成基本类型，{@code null} 记 0。
     *
     * @param value 原始值
     * @return 非空值
     */
    private static long zero(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 空安全：把包装类型还原成基本类型，{@code null} 记 0。
     *
     * @param value 原始值
     * @return 非空值
     */
    private static double zero(Double value) {
        return value == null ? 0D : value;
    }

    // ==================== 日汇总统计 导出 / 导入 ====================

    /**
     * CSV 表头列名（与导出列一致；按表头名取列，不依赖列顺序）。
     */
    private static final List<String> DAILY_CSV_COLUMNS = List.of(
            "statDate", "group", "provider", "model", "calls",
            "promptTokens", "completionTokens", "cachedTokens", "totalTokens", "cost");

    /**
     * 导入失败明细最多返回的条数，超出只计条数，防止响应被撑爆。
     */
    private static final int IMPORT_ERROR_LIMIT = 20;

    /**
     * 查询日汇总统计（导出用）。
     *
     * <p>读日汇总表 {@code sys_ai_usage_daily}，粒度为
     * 「日期 + 分组 + 服务商 + 模型」一次调用一条统计，不含逐次调用明细。
     * 同一维度下多个账号的行在此按 SUM 归并成一行：导出 CSV 的维度即
     * {@link #importDailyCsv} 的覆盖维度，多账号明细直出会让导回时
     * 同维度行互相覆盖、丢掉其余账号的量。</p>
     *
     * @param startDate 开始日期（可选，含）
     * @param endDate   结束日期（可选，含）
     * @param model     模型名称（可选）
     * @return 日汇总行列表，按 日期/分组/服务商/模型 升序
     */
    @Override
    public List<SysAiUsageDaily> queryDaily(LocalDate startDate, LocalDate endDate, String model) {
        QueryWrapper<SysAiUsageDaily> wrapper = new QueryWrapper<>();
        // 列别名必须与实体 @TableField 一致，MyBatis-Plus 才能映射回实体字段
        wrapper.select("sys_ai_usage_daily_stat_date", "sys_ai_usage_daily_group_name",
                        "sys_ai_usage_daily_provider", "sys_ai_usage_daily_model",
                        "SUM(sys_ai_usage_daily_total_calls) AS sys_ai_usage_daily_total_calls",
                        "SUM(sys_ai_usage_daily_total_prompt_tokens) AS sys_ai_usage_daily_total_prompt_tokens",
                        "SUM(sys_ai_usage_daily_total_completion_tokens) AS sys_ai_usage_daily_total_completion_tokens",
                        "SUM(sys_ai_usage_daily_total_cached_tokens) AS sys_ai_usage_daily_total_cached_tokens",
                        "SUM(sys_ai_usage_daily_total_tokens) AS sys_ai_usage_daily_total_tokens",
                        "SUM(sys_ai_usage_daily_total_cost) AS sys_ai_usage_daily_total_cost");
        if (startDate != null) {
            wrapper.ge("sys_ai_usage_daily_stat_date", startDate);
        }
        if (endDate != null) {
            wrapper.le("sys_ai_usage_daily_stat_date", endDate);
        }
        if (model != null && !model.isBlank()) {
            wrapper.eq("sys_ai_usage_daily_model", model);
        }
        wrapper.groupBy("sys_ai_usage_daily_stat_date", "sys_ai_usage_daily_group_name",
                "sys_ai_usage_daily_provider", "sys_ai_usage_daily_model");
        wrapper.orderByAsc("sys_ai_usage_daily_stat_date", "sys_ai_usage_daily_group_name",
                "sys_ai_usage_daily_provider", "sys_ai_usage_daily_model");
        return dailyMapper.selectList(wrapper);
    }

    /**
     * 导入用量统计 CSV（与 {@link #queryDaily} 的导出格式一致）。
     *
     * <p>校验先行：先整表解析校验，任何一行非法都整文件拒绝入库（不写半截）；
     * 全部合法时按「日期 + 分组 + 服务商 + 模型」维度覆盖写入日汇总表，
     * CSV 中同一维度出现多行时先合并累加再写入。
     * 覆盖是整维替换：该维度下所有账号的既有行都会被导入行替换，
     * 因为导入数据不带用户维度，保留旧账号行会与导入行重复计量。</p>
     *
     * @param in CSV 输入流（UTF-8，允许带 BOM）
     * @return 导入结果（总行数 / 写入行数 / 失败行数 / 失败明细）
     * @throws IOException 读取输入流失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiUsageDailyImportResult importDailyCsv(InputStream in) throws IOException {
        String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        // 导出侧前置了 UTF-8 BOM（Excel 兼容），导入侧原样剥掉
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\r\n|\n|\r")) {
            if (!raw.isBlank()) {
                lines.add(raw);
            }
        }
        if (lines.size() < 2) {
            throw new IllegalArgumentException("CSV 至少需要表头和一行数据");
        }
        Map<String, Integer> colIndex = resolveCsvColumns(lines.get(0));
        List<SysAiUsageDaily> parsed = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int failedRows = 0;
        for (int i = 1; i < lines.size(); i++) {
            try {
                parsed.add(parseDailyCsvRow(lines.get(i), colIndex));
            } catch (Exception e) {
                failedRows++;
                if (errors.size() < IMPORT_ERROR_LIMIT) {
                    errors.add("第 " + (i + 1) + " 行: " + e.getMessage());
                }
            }
        }
        if (failedRows > 0) {
            return new AiUsageDailyImportResult(lines.size() - 1, 0, failedRows, List.copyOf(errors));
        }
        // 同维度多行先合并累加：导出侧按维度归并过，这里兜住手改/拼装的重复维度行
        Map<String, SysAiUsageDaily> merged = new LinkedHashMap<>();
        for (SysAiUsageDaily row : parsed) {
            merged.merge(dailyRowKey(row), row, AiUsageServiceImpl::accumulateDaily);
        }
        for (SysAiUsageDaily row : merged.values()) {
            dailyMapper.delete(dailyRowScope(row));
            dailyMapper.insert(row);
        }
        log.info("[AiUsage] 用量统计 CSV 导入完成: 共 {} 行, 写入 {} 行", lines.size() - 1, merged.size());
        return new AiUsageDailyImportResult(lines.size() - 1, merged.size(), 0, List.of());
    }

    /**
     * 维度键：日期 + 分组 + 服务商 + 模型，用于导入时合并同维度重复行。
     *
     * @param row 日汇总行
     * @return 维度键
     */
    private static String dailyRowKey(SysAiUsageDaily row) {
        return row.getStatDate() + "|" + row.getGroupName() + "|" + row.getProvider() + "|" + row.getModel();
    }

    /**
     * 合并同维度两行：数值列累加，维度与归属以第一行为准。
     *
     * @param target 保留并累加的行
     * @param addend 被并入的行
     * @return {@code target}
     */
    private static SysAiUsageDaily accumulateDaily(SysAiUsageDaily target, SysAiUsageDaily addend) {
        target.setTotalCalls(zero(target.getTotalCalls()) + zero(addend.getTotalCalls()));
        target.setTotalPromptTokens(zero(target.getTotalPromptTokens()) + zero(addend.getTotalPromptTokens()));
        target.setTotalCompletionTokens(zero(target.getTotalCompletionTokens()) + zero(addend.getTotalCompletionTokens()));
        target.setTotalCachedTokens(zero(target.getTotalCachedTokens()) + zero(addend.getTotalCachedTokens()));
        target.setTotalTokens(zero(target.getTotalTokens()) + zero(addend.getTotalTokens()));
        target.setTotalCost(zero(target.getTotalCost()) + zero(addend.getTotalCost()));
        return target;
    }

    /**
     * 解析 CSV 表头，返回 列名 -&gt; 列下标；缺少必需列时直接拒绝。
     *
     * @param headerLine 表头行
     * @return 列名到下标的映射
     */
    private static Map<String, Integer> resolveCsvColumns(String headerLine) {
        Map<String, Integer> colIndex = new LinkedHashMap<>();
        List<String> cells = splitCsvLine(headerLine);
        for (int i = 0; i < cells.size(); i++) {
            String name = cells.get(i).trim();
            if (!name.isEmpty()) {
                colIndex.putIfAbsent(name, i);
            }
        }
        for (String col : DAILY_CSV_COLUMNS) {
            if (!colIndex.containsKey(col)) {
                throw new IllegalArgumentException("CSV 缺少列: " + col);
            }
        }
        return colIndex;
    }

    /**
     * 解析并校验一行日汇总统计。
     *
     * @param line     CSV 数据行
     * @param colIndex 列名到下标的映射
     * @return 日汇总实体（不含主键，待 insert 生成）
     */
    private static SysAiUsageDaily parseDailyCsvRow(String line, Map<String, Integer> colIndex) {
        List<String> cells = splitCsvLine(line);
        String statDateRaw = requireCell(cells, colIndex, "statDate");
        LocalDate statDate;
        try {
            statDate = LocalDate.parse(statDateRaw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("statDate 格式应为 yyyy-MM-dd: " + statDateRaw);
        }
        String model = requireCell(cells, colIndex, "model");
        long calls = parseNonNegativeLong(cell(cells, colIndex, "calls"), "calls");
        long promptTokens = parseNonNegativeLong(cell(cells, colIndex, "promptTokens"), "promptTokens");
        long completionTokens = parseNonNegativeLong(cell(cells, colIndex, "completionTokens"), "completionTokens");
        long cachedTokens = parseNonNegativeLong(cell(cells, colIndex, "cachedTokens"), "cachedTokens");
        String totalTokensRaw = cell(cells, colIndex, "totalTokens");
        long totalTokens = totalTokensRaw.isBlank()
                ? promptTokens + completionTokens
                : parseNonNegativeLong(totalTokensRaw, "totalTokens");
        double cost = parseNonNegativeDouble(cell(cells, colIndex, "cost"));

        SysAiUsageDaily daily = new SysAiUsageDaily();
        daily.setStatDate(statDate);
        daily.setGroupName(blankToNull(cell(cells, colIndex, "group")));
        daily.setProvider(blankToNull(cell(cells, colIndex, "provider")));
        daily.setModel(model);
        daily.setUserId(SysAiUsageService.resolveOwner(null));
        daily.setTotalCalls(calls);
        daily.setTotalPromptTokens(promptTokens);
        daily.setTotalCompletionTokens(completionTokens);
        daily.setTotalCachedTokens(cachedTokens);
        daily.setTotalTokens(totalTokens);
        daily.setTotalCost(cost);
        // 导入数据不含耗时/速度维度，落 0 而不是 NULL，保证读取侧均值计算口径稳定
        daily.setTotalLatency(0L);
        daily.setTotalFirstTokenLatency(0L);
        daily.setStandardCost(0D);
        daily.setTotalTokensPerSecond(0D);
        daily.setTpsSamples(0L);
        return daily;
    }

    /**
     * 覆盖范围：同 日期+分组+服务商+模型 的既有行全部被导入行替换。
     *
     * @param row 导入行（决定维度值）
     * @return 删除条件
     */
    private LambdaQueryWrapper<SysAiUsageDaily> dailyRowScope(SysAiUsageDaily row) {
        LambdaQueryWrapper<SysAiUsageDaily> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiUsageDaily::getStatDate, row.getStatDate());
        if (row.getGroupName() == null) {
            wrapper.isNull(SysAiUsageDaily::getGroupName);
        } else {
            wrapper.eq(SysAiUsageDaily::getGroupName, row.getGroupName());
        }
        if (row.getProvider() == null) {
            wrapper.isNull(SysAiUsageDaily::getProvider);
        } else {
            wrapper.eq(SysAiUsageDaily::getProvider, row.getProvider());
        }
        wrapper.eq(SysAiUsageDaily::getModel, row.getModel());
        return wrapper;
    }

    /**
     * 取必填单元格，空白即报错。
     *
     * @param cells    单元格列表
     * @param colIndex 列名到下标的映射
     * @param name     列名
     * @return 非空单元格文本
     */
    private static String requireCell(List<String> cells, Map<String, Integer> colIndex, String name) {
        String value = cell(cells, colIndex, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }

    /**
     * 取单元格文本（越界记空串），已去除首尾空白。
     *
     * @param cells    单元格列表
     * @param colIndex 列名到下标的映射
     * @param name     列名
     * @return 单元格文本，缺列/越界时为空串
     */
    private static String cell(List<String> cells, Map<String, Integer> colIndex, String name) {
        Integer index = colIndex.get(name);
        String value = index == null || index >= cells.size() ? "" : cells.get(index).trim();
        return value == null ? "" : value;
    }

    /**
     * 空白转 {@code null}（分组/服务商允许为空，落库与覆盖匹配都要区分空串与 NULL）。
     *
     * @param value 原始文本
     * @return 空白为 {@code null}，否则原文
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 解析非负整数，空白记 0，负数/非数字报错。
     *
     * @param value 原始文本
     * @param field 列名（报错提示用）
     * @return 非负整数
     */
    private static long parseNonNegativeLong(String value, String field) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        long parsed;
        try {
            parsed = Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " 不是合法整数: " + value);
        }
        if (parsed < 0) {
            throw new IllegalArgumentException(field + " 不能为负数: " + value);
        }
        return parsed;
    }

    /**
     * 解析非负数字（费用），空白记 0，负数/非数字报错。
     *
     * @param value 原始文本
     * @return 非负数字
     */
    private static double parseNonNegativeDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0D;
        }
        double parsed;
        try {
            parsed = Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("cost 不是合法数字: " + value);
        }
        if (!Double.isFinite(parsed) || parsed < 0) {
            throw new IllegalArgumentException("cost 不能为负数或非有限数字: " + value);
        }
        return parsed;
    }

    /**
     * 拆一行 CSV：支持双引号包裹与引号转义（{@code ""} -&gt; {@code "}），
     * 与导出侧的 {@code csvCell} 转义规则对应。
     *
     * @param line 单行 CSV 文本
     * @return 单元格列表（原始文本，未去空白）
     */
    private static List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    /**
     * 分组累加容器：两段合并时先累加，最后一次性生成不可变 DTO。
     */
    private static final class Accumulator {

        /**
         * 分组名称
         */
        private final String group;

        /**
         * 服务商
         */
        private final String provider;

        /**
         * 模型
         */
        private final String model;

        /**
         * 调用次数
         */
        private long calls;

        /**
         * 输入令牌
         */
        private long promptTokens;

        /**
         * 输出令牌
         */
        private long completionTokens;

        /**
         * 总令牌
         */
        private long totalTokens;

        /**
         * 费用（元）
         */
        private double cost;

        /**
         * 构造累加容器。
         *
         * @param group    分组名称
         * @param provider 服务商
         * @param model    模型
         */
        private Accumulator(String group, String provider, String model) {
            this.group = group;
            this.provider = provider;
            this.model = model;
        }

        /**
         * 生成对外的用量 DTO。
         *
         * @return 用量 DTO
         */
        private AiUsageDTO toDto() {
            return new AiUsageDTO(group, provider, model, cost, calls, promptTokens, completionTokens, totalTokens, null);
        }
    }
}
