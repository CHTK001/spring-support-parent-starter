package com.chua.starter.server.support.ai;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发布管理的 AI 能力接口。
 *
 * <p>前端先问 {@code /capabilities}：没有可用模型就不显示 AI 按钮，
 * 而不是让用户点一个必然失败的入口。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Tag(name = "发布管理-AI", description = "能力探测与日志排查")
@Slf4j
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/release/ai")
public class ReleaseAiController {

    /**
     * 单次排查送进模型的日志上限（字符）
     */
    private static final int MAX_LOG_CHARS = 12000;

    /**
     * AI 能力门面（宿主未装配时为 null）
     */
    private final ObjectProvider<ReleaseAiCapability> capabilityProvider;

    /**
     * 构造
     *
     * @param capabilityProvider 能力门面提供者
     */
    public ReleaseAiController(ObjectProvider<ReleaseAiCapability> capabilityProvider) {
        this.capabilityProvider = capabilityProvider;
    }

    /**
     * 能力探测：是否装了 ai-starter、注册了哪些提供者与模型。
     *
     * @return available / providers / models
     */
    @Operation(summary = "AI 能力探测")
    @GetMapping("/capabilities")
    public ReturnResult<Map<String, Object>> capabilities() {
        ReleaseAiCapability capability = capabilityProvider.getIfAvailable();
        Map<String, Object> data = new LinkedHashMap<>();
        boolean available = capability != null && capability.available();
        data.put("available", available);
        data.put("providers", available ? capability.providers() : List.of());
        data.put("models", available ? capability.models() : List.of());
        return ReturnResult.ok(data);
    }

    /**
     * 日志排查：把最近的日志交给模型，返回结论。
     *
     * <p>只做一次同步对话，不落库、不重试——模型调用有成本，失败让用户自己决定是否再点。</p>
     *
     * @param req 项目名与日志正文
     * @return 结论
     */
    @Operation(summary = "日志 AI 排查")
    @PostMapping("/log-diagnose")
    public ReturnResult<Map<String, Object>> diagnose(@RequestBody DiagnoseRequest req) {
        ReleaseAiCapability capability = capabilityProvider.getIfAvailable();
        if (capability == null || !capability.available()) {
            return ReturnResult.error("AI 未启用：宿主未安装 ai-starter 或没有可用模型");
        }
        String logText = req == null || req.logText() == null ? "" : req.logText();
        if (logText.isBlank()) {
            return ReturnResult.error("日志内容为空");
        }
        if (logText.length() > MAX_LOG_CHARS) {
            // 保留尾部：报错与堆栈在最后，头部多是启动流水
            logText = logText.substring(logText.length() - MAX_LOG_CHARS);
        }
        String app = req.projectName() == null || req.projectName().isBlank() ? "该服务" : req.projectName();
        String prompt = "你是负责线上排障的资深 Java 运维。下面是「" + app + "」最近的运行日志。\n"
                + "请只输出三段，不要客套：\n"
                + "1. 根因：一句话判断最可能的失败点；\n"
                + "2. 证据：引用日志里的关键行（原文片段，最多 3 条）；\n"
                + "3. 建议：按优先级给出 1-3 条可执行动作。\n"
                + "如果日志里看不出明确错误，就直接说「未发现明确错误」，并指出最值得关注的异常信号。\n\n"
                + "日志：\n" + logText;
        try {
            String answer = capability.chat(prompt);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("conclusion", answer);
            return ReturnResult.ok(data);
        } catch (Exception e) {
            log.error("[发布AI] 日志排查失败", e);
            return ReturnResult.error("AI 调用失败: " + e.getMessage());
        }
    }

    /**
     * 日志排查入参。
     *
     * @param projectName 项目名（可空）
     * @param logText     日志正文
     */
    public record DiagnoseRequest(String projectName, String logText) {
    }
}
