package com.chua.starter.channel.template;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.channel.template.store.SysAiChannelMessageTemplate;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 短信 / 邮件消息模板管理接口。
 *
 * <p>渠道管理页「模板管理」抽屉与短信、邮箱组件共用同一组接口：</p>
 * <ul>
 *   <li>{@code GET /api/channels/templates?kind=SMS|EMAIL}：模板列表；</li>
 *   <li>{@code POST /api/channels/templates}：新增或更新（带 id 即更新）；</li>
 *   <li>{@code DELETE /api/channels/templates/{id}}：删除；</li>
 *   <li>{@code POST /api/channels/templates/{id}/default}：设为该种类默认模板；</li>
 *   <li>{@code POST /api/channels/templates/render}：变量渲染预览（不落库）。</li>
 * </ul>
 *
 * <p>模板为部署级共享配置：渠道能力（短信接码 / 邮件收信）本身不分用户，
 * 模板也保持一致，避免每个用户维护一份重复的验证码解析规则。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/channels/templates")
@RequiredArgsConstructor
public class MessageTemplateController {

    /**
     * 模板服务
     */
    private final MessageTemplateService templateService;

    /**
     * 模板列表。
     *
     * @param kind 种类（{@code SMS}/{@code EMAIL}，缺省返回全部）
     * @return 模板列表
     */
    @GetMapping
    public ApiResult<List<SysAiChannelMessageTemplate>> list(
            @RequestParam(value = "kind", required = false) String kind) {
        return ApiResult.ok(templateService.list(kind));
    }

    /**
     * 新增或更新模板。
     *
     * @param template 模板内容（{@code templateId} 存在即为更新）
     * @return 落库后的模板
     */
    @PostMapping
    public ApiResult<SysAiChannelMessageTemplate> save(
            @RequestBody SysAiChannelMessageTemplate template) {
        return ApiResult.ok(templateService.save(template));
    }

    /**
     * 删除模板。
     *
     * @param templateId 模板标识
     * @return 是否删除成功
     */
    @DeleteMapping("/{templateId}")
    public ApiResult<Boolean> delete(@PathVariable("templateId") String templateId) {
        return ApiResult.ok(templateService.delete(templateId));
    }

    /**
     * 设为该种类默认模板。
     *
     * @param templateId 模板标识
     * @return 设置后的模板
     */
    @PostMapping("/{templateId}/default")
    public ApiResult<SysAiChannelMessageTemplate> setDefault(
            @PathVariable("templateId") String templateId) {
        return ApiResult.ok(templateService.setDefault(templateId));
    }

    /**
     * 渲染预览：按模板内容与变量生成最终文本，并回报缺失变量。
     *
     * @param request 渲染请求
     * @return {@code {ok, subject, content, missing}}
     */
    @PostMapping("/render")
    public ApiResult<Map<String, Object>> render(@RequestBody RenderRequest request) {
        if (request == null || request.kind() == null || request.kind().isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "kind 不能为空");
        }
        Map<String, Object> rendered = templateService.render(request.kind(),
                request.subject(), request.content(), request.variables());
        Map<String, Object> payload = new LinkedHashMap<>(5);
        payload.put("ok", true);
        payload.put("subject", rendered.get("subject"));
        payload.put("content", rendered.get("content"));
        payload.put("missing", rendered.get("missing"));
        return ApiResult.ok(payload);
    }

    /**
     * 模板内置变量清单。
     *
     * @param kind 种类
     * @return 变量名列表
     */
    @GetMapping("/variables")
    public ApiResult<List<String>> variables(@RequestParam("kind") String kind) {
        return ApiResult.ok(templateService.variablesOf(kind));
    }

    /**
     * 模板渲染请求。
     *
     * @param kind      种类（SMS / EMAIL）
     * @param subject   主题模板
     * @param content   正文模板
     * @param variables 变量值
     */
    public record RenderRequest(String kind, String subject, String content,
            Map<String, Object> variables) {
    }
}
