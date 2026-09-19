package com.chua.starter.ai.support.toolmetrics;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.ai.support.entity.SysAiSessionCompression;
import com.chua.starter.ai.support.entity.SysAiUsage;
import com.chua.starter.ai.support.mapper.SysAiSessionCompressionMapper;
import com.chua.starter.ai.support.mapper.SysAiUsageMapper;
import com.chua.common.support.lang.code.ReturnResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 本地工具用量与上下文记录接口。
 *
 * <p>提供：本地用量同步、本地用量列表、压缩上下文记录查询。
 * 供前端「模型指标 / 用量」页面消费。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v2/tool-usage")
public class ToolMetricsController {

    /** 本地用量同步服务 */
    private final LocalToolUsageSyncService localToolUsageSyncService;
    /** 用量 映射器 */
    private final SysAiUsageMapper sysAiUsageMapper;
    /** 压缩记录 映射器 */
    private final SysAiSessionCompressionMapper compressionMapper;

    /**
     * 触发一次本地工具用量同步。
     *
     * @return 扫描数与新增数
     */
    @PostMapping("/sync")
    public ReturnResult<LocalToolUsageSyncService.SyncResult> sync() {
        return ReturnResult.ok(localToolUsageSyncService.syncAll());
    }

    /**
     * 本地用量列表（最新 200 条）。
     *
     * @return 用量记录
     */
    @GetMapping("/list")
    public ReturnResult<List<SysAiUsage>> list() {
        return ReturnResult.ok(sysAiUsageMapper.selectList(
                Wrappers.<SysAiUsage>lambdaQuery()
                        .eq(SysAiUsage::getSysAiUsageGroupName, "local")
                        .orderByDesc(SysAiUsage::getSysAiUsageCallTime)
                        .last("LIMIT 200")));
    }

    /**
     * 压缩上下文记录（最新 100 条）。
     *
     * @return 压缩记录
     */
    @GetMapping("/compressions")
    public ReturnResult<List<SysAiSessionCompression>> compressions() {
        return ReturnResult.ok(compressionMapper.selectList(
                Wrappers.<SysAiSessionCompression>lambdaQuery()
                        .orderByDesc(SysAiSessionCompression::getCreateTime)
                        .last("LIMIT 100")));
    }
}
