package com.chua.starter.ai.support.toolmetrics;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.ai.support.entity.SysAiSessionCompression;
import com.chua.starter.ai.support.entity.SysAiUsage;
import com.chua.starter.ai.support.mapper.SysAiSessionCompressionMapper;
import com.chua.starter.ai.support.mapper.SysAiUsageMapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.common.support.annotations.Permission;
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
 * <p><b>隔离口径</b>：本帖数据的隔离与 {@code /v2/usage/**} 一致：<br>
 * 无用户归属的本地工具粉尘数据（{@code group = 'local'}）仅管理员可见；
 * 压缩上下文随会话归属用户，按当前登录用户隔离。写操作（触发同步）仅管理员。</p>
 *
 * @author CH
 * @since 4.0.0.42
 * @Revision 4.0.0.51
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v2/tool-usage")
public class ToolMetricsController {

    /**
     * 本地用量同步服务
    */
    private final LocalToolUsageSyncService localToolUsageSyncService;
    /**
     * 用量 映射器
    */
    private final SysAiUsageMapper sysAiUsageMapper;
    /**
     * 压缩记录 映射器
    */
    private final SysAiSessionCompressionMapper compressionMapper;

    /**
     * 触发一次本地工具用量同步。
     *
     * @return 扫描数与新增数
     */
    @Permission("admin")
    @PostMapping("/sync")
    public ReturnResult<LocalToolUsageSyncService.SyncResult> sync() {
        return ReturnResult.ok(localToolUsageSyncService.syncAll());
    }

    /**
     * 本地用量列表（最新 200 条）。
     *
     * <p>本地工具（Claude 编码、Codex++ 等）用量数据不带用户归属（
     * {@code sys_ai_usage_user_id} 为空），属全局限流共用的粉尘数据，与
     * {@code /v2/usage/list} 中「管理员额外可见无主明细」的口径一致，仅管理员可见。</p>
     *
     * @return 用量记录
     */
    @Permission("admin")
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
     * <p>压缩记录随所属会话归属用户（{@code sys_ai_session_compression_user_id}），
     * 普通用户只能拉取属于自己的压缩上下文，管理员也不跨账号（
     * {@code AiUserContext.canAccessOthersData()} 恒为 {@code false}）。</p>
     *
     * @return 压缩记录
     */
    @GetMapping("/compressions")
    public ReturnResult<List<SysAiSessionCompression>> compressions() {
        String userId = AiUserContext.requireUserId();
        return ReturnResult.ok(compressionMapper.selectList(
                Wrappers.<SysAiSessionCompression>lambdaQuery()
                        .eq(SysAiSessionCompression::getUserId, userId)
                        .orderByDesc(SysAiSessionCompression::getCreateTime)
                        .last("LIMIT 100")));
    }
}
