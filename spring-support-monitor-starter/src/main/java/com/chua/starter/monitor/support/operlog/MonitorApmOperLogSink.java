package com.chua.starter.monitor.support.operlog;

import com.chua.starter.apm.support.web.ApmOperLogSink;
import com.chua.starter.apm.support.web.ApmRequest;
import com.chua.starter.apm.support.web.ApmResult;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.monitor.support.entity.MsApmOperLog;
import com.chua.starter.monitor.support.service.ApmOperLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * APM 写操作 → {@code ms_apm_oper_log} 的落点实现。
 *
 * <p><strong>为什么不是监听器</strong>：APM 接口不走 Spring MVC，{@code @SysLog}
 * 切面拦不到它；{@link com.chua.starter.apm.support.web.ApmDispatcher} 在写操作
 * 完成后直接回调本实现，是这条链路上唯一的入口。</p>
 *
 * <p><strong>只收写操作</strong>：dispatcher 只在 POST 上回调，GET 查询不入账。
 * 这不是遗漏 —— 日志的价值在「谁改了设置」，把每次打开页面都记一条只会淹掉时间线。</p>
 *
 * <p><strong>操作人/客户端 IP</strong>：嵌入模式下 ApmServlet 把当前请求绑定到
 * {@code RequestContextHolder}，这里用与业务接口相同的 {@link RequestUtils} 取；
 * 独立模式（不经过宿主容器）取不到，留空而不是编一个「未知」。</p>
 *
 * @author CH
 * @since 4.0.0.56
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorApmOperLogSink implements ApmOperLogSink {

    /**
     * 保存设置的默认操作码
     */
    private static final String ACTION_CONFIG_UPDATE = "CONFIG_UPDATE";

    /**
     * 重新探测引擎的操作码
     */
    private static final String ACTION_DETECT = "DETECT";

    /**
     * 保存设置的中文名
     */
    private static final String NAME_CONFIG_UPDATE = "保存 APM 设置";

    /**
     * 重新探测引擎的中文名
     */
    private static final String NAME_DETECT = "重新探测 APM 引擎";

    /**
     * 不参与「改动了哪些项」的参数：它是路由指令，不是设置值。
     */
    private static final String PARAM_ACTION = "action";

    private final ApmOperLogService operLogService;

    @Override
    public void record(ApmRequest request, ApmResult result, long costMs) {
        try {
            if (request == null) {
                return;
            }
            operLogService.record(toRow(request, result, costMs));
        } catch (RuntimeException e) {
            // 与监听器同一取舍：留痕失败不影响主流程。
            log.warn("[apm-oper-log] 处理 APM 写操作失败 reason={}", e.getMessage());
        }
    }

    /**
     * 请求结果转日志行。
     *
     * @param request 请求
     * @param result  结果
     * @param costMs  耗时（毫秒）
     * @return 日志行
     */
    private MsApmOperLog toRow(ApmRequest request, ApmResult result, long costMs) {
        boolean detect = "detect".equalsIgnoreCase(request.param(PARAM_ACTION));
        MsApmOperLog row = new MsApmOperLog();
        row.setAction(detect ? ACTION_DETECT : ACTION_CONFIG_UPDATE);
        row.setActionName(detect ? NAME_DETECT : NAME_CONFIG_UPDATE);
        row.setTarget(changedKeys(request.params()));
        row.setDetail(buildDetail(request.params(), result));
        row.setSuccess(result != null && result.ok() ? 1 : 0);
        row.setCostMs(costMs);
        row.setOperator(RequestUtils.getUsername());
        HttpServletRequest httpRequest = RequestUtils.getRequest();
        row.setClientIp(httpRequest == null ? null : RequestUtils.getIpAddress(httpRequest));
        row.setRequestUrl(request.path());
        row.setCreateTime(LocalDateTime.now());
        return row;
    }

    /**
     * 本次改动了哪些设置项（不含路由参数 {@code action}）。
     *
     * @param params 请求参数
     * @return 逗号分隔的键名；没有设置项时返回 null
     */
    private String changedKeys(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        StringBuilder keys = new StringBuilder();
        for (String key : params.keySet()) {
            if (PARAM_ACTION.equals(key)) {
                continue;
            }
            if (keys.length() > 0) {
                keys.append(',');
            }
            keys.append(key);
        }
        return keys.length() == 0 ? null : keys.toString();
    }

    /**
     * 明细：把这次提交的设置值按 {@code k=v} 拼起来，失败时补上原因。
     *
     * <p>设置项都是短文本（开关/引擎名/OAP 地址），直接平铺比 JSON 更好读，
     * 也避免把引号转义问题带进日志。超长内容由服务层按列上限截断。</p>
     *
     * @param params 请求参数
     * @param result 结果
     * @return 明细；没有任何内容时返回 null
     */
    private String buildDetail(Map<String, String> params, ApmResult result) {
        Map<String, String> values = new LinkedHashMap<>(params == null ? Map.of() : params);
        StringBuilder detail = new StringBuilder();
        values.forEach((key, value) -> {
            if (detail.length() > 0) {
                detail.append("; ");
            }
            detail.append(key).append('=').append(value);
        });
        if (result != null && !result.ok() && result.message() != null && !result.message().isBlank()) {
            if (detail.length() > 0) {
                detail.append("; ");
            }
            detail.append("失败原因=").append(result.message());
        }
        return detail.length() == 0 ? null : detail.toString();
    }
}
