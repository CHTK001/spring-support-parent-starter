package com.chua.starter.message.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.task.message.MessageResponse;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.common.support.lang.ReturnPageResult;
import com.chua.starter.message.support.entity.SysMessageLog;
import com.chua.starter.message.support.entity.SysMessageProvider;
import com.chua.starter.message.support.entity.SysMessageTemplate;
import com.chua.starter.message.support.pojo.ChannelInfo;
import com.chua.starter.message.support.pojo.MessageContentSendRequest;
import com.chua.starter.message.support.pojo.MessageSendRequest;
import com.chua.starter.message.support.pojo.SysMessageStationItem;
import com.chua.starter.message.support.pojo.TemplatePreviewRequest;
import com.chua.starter.message.support.pojo.TemplatePreviewResponse;
import com.chua.starter.message.support.service.SysMessageService;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;

/**
 * 消息推送接口
 * <p>
 * 提供消息提供商、消息模板的管理接口，以及统一的消息发送接口。
 * </p>
 *
 * @since 2026/08/03
 * @author CH
 */
@Tag(name = "消息推送接口")
@RestController
@RequestMapping("/v2/message")
@RequiredArgsConstructor
public class SysMessageController {

    /**
     * SYS消息服务
    */
    private final SysMessageService sysMessageService;

    /**
     * 提供商分页查询
     *
     * @param provider 查询条件
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @Operation(summary = "提供商分页查询")
    @GetMapping("/provider/page")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnPageResult<SysMessageProvider> pageForProvider(@ParameterObject SysMessageProvider provider,
                                                                @RequestParam(value = "page", defaultValue = "1") long page,
                                                                @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        return PageResultUtils.ok(sysMessageService.pageForProvider(provider, page, pageSize));
    }

    /**
     * 提供商列表查询
     *
     * @return 提供商列表
     */
    @Operation(summary = "提供商列表查询")
    @GetMapping("/provider/list")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<List<SysMessageProvider>> listForProvider() {
        return ReturnResult.ok(sysMessageService.listForProvider());
    }

    /**
     * 已注册消息渠道列表
     * <p>
     * 返回所有通过 SPI 注册的 消息push 渠道标识，用于前端「厂家编码」下拉选择。
     * </p>
     *
     * @return 渠道信息列表
     */
    @Operation(summary = "已注册消息渠道列表")
    @GetMapping("/channel/list")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<List<ChannelInfo>> listChannels() {
        return ReturnResult.ok(sysMessageService.listChannels());
    }

    /**
     * 新增提供商
     *
     * @param provider 提供商信息
     * @return 保存结果
     */
    @Operation(summary = "新增提供商")
    @PostMapping("/provider/save")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<SysMessageProvider> saveProvider(@RequestBody SysMessageProvider provider) {
        return sysMessageService.saveProvider(provider);
    }

    /**
     * 更新提供商
     *
     * @param provider 提供商信息
     * @return 更新结果
     */
    @Operation(summary = "更新提供商")
    @PutMapping("/provider/update")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Boolean> updateProvider(@RequestBody SysMessageProvider provider) {
        return sysMessageService.updateProvider(provider);
    }

    /**
     * 删除提供商
     *
     * @param providerId 提供商标识
     * @return 删除结果
     */
    @Operation(summary = "删除提供商")
    @DeleteMapping("/provider/delete")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Boolean> deleteProvider(@RequestParam Integer providerId) {
        return sysMessageService.deleteProvider(providerId);
    }

    /**
     * 模板分页查询
     *
     * @param template 查询条件
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @Operation(summary = "模板分页查询")
    @GetMapping("/template/page")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnPageResult<SysMessageTemplate> pageForTemplate(@ParameterObject SysMessageTemplate template,
                                                                @RequestParam(value = "page", defaultValue = "1") long page,
                                                                @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        return PageResultUtils.ok(sysMessageService.pageForTemplate(template, page, pageSize));
    }

    /**
     * 模板列表查询
     *
     * @return 模板列表
     */
    @Operation(summary = "模板列表查询")
    @GetMapping("/template/list")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<List<SysMessageTemplate>> listForTemplate() {
        return ReturnResult.ok(sysMessageService.listForTemplate());
    }

    /**
     * 新增模板
     *
     * @param template 模板信息
     * @return 保存结果
     */
    @Operation(summary = "新增模板")
    @PostMapping("/template/save")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<SysMessageTemplate> saveTemplate(@RequestBody SysMessageTemplate template) {
        return sysMessageService.saveTemplate(template);
    }

    /**
     * 更新模板
     *
     * @param template 模板信息
     * @return 更新结果
     */
    @Operation(summary = "更新模板")
    @PutMapping("/template/update")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Boolean> updateTemplate(@RequestBody SysMessageTemplate template) {
        return sysMessageService.updateTemplate(template);
    }

    /**
     * 删除模板
     *
     * @param templateId 模板标识
     * @return 删除结果
     */
    @Operation(summary = "删除模板")
    @DeleteMapping("/template/delete")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Boolean> deleteTemplate(@RequestParam Integer templateId) {
        return sysMessageService.deleteTemplate(templateId);
    }

    /**
     * 按模板发送消息
     *
     * @param request 发送请求
     * @return 发送结果
     */
    @Operation(summary = "按模板发送消息")
    @PostMapping("/send")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<List<MessageResponse>> send(@RequestBody MessageSendRequest request) {
        return sysMessageService.send(request);
    }

    /**
     * 按内容发送消息
     *
     * @param request 按内容发送请求
     * @return 发送结果
     */
    @Operation(summary = "按内容发送消息")
    @PostMapping("/send/content")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<List<MessageResponse>> sendContent(@RequestBody MessageContentSendRequest request) {
        return sysMessageService.sendContent(request);
    }

    /**
     * 手动同步模板
     *
     * @param providerId 提供商标识
     * @return 同步结果
     */
    @Operation(summary = "手动同步模板")
    @PostMapping("/template/sync")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Integer> syncTemplate(@RequestParam Integer providerId) {
        return sysMessageService.syncTemplate(providerId);
    }

    /**
     * 模板内容渲染预览
     *
     * @param request 预览请求（模板标识 + 参数）
     * @return 渲染结果
     */
    @Operation(summary = "模板内容渲染预览")
    @PostMapping("/template/preview")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<TemplatePreviewResponse> previewTemplate(@RequestBody TemplatePreviewRequest request) {
        return sysMessageService.previewTemplate(request);
    }

    /**
     * 清理推送日志
     *
     * @param beforeTime 截止时间（ISO 时间串，如 2026-01-01T00:00:00）
     * @return 删除条数
     * @param false false
     */
    @Operation(summary = "清理推送日志")
    @PostMapping("/log/clean")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Integer> cleanLog(@RequestParam(required = false) java.time.LocalDateTime beforeTime) {
        return sysMessageService.cleanLog(beforeTime);
    }

    /**
     * 推送日志分页查询
     *
     * @param log      日志查询条件
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @Operation(summary = "推送日志分页查询")
    @GetMapping("/log/page")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnPageResult<SysMessageLog> pageForLog(@ParameterObject SysMessageLog log,
                                                      @RequestParam(value = "page", defaultValue = "1") long page,
                                                      @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        return PageResultUtils.ok(sysMessageService.pageForLog(log, page, pageSize));
    }

    /**
     * 当前用户站内消息分页查询
     * <p>
     * 返回当前登录用户接收的站内消息（渠道 station），按发送时间倒序分页。
     * </p>
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     * @param "1" "1"
     */
    @Operation(summary = "当前用户站内消息分页查询")
    @GetMapping("/sys-message/page")
    public ReturnPageResult<SysMessageStationItem> pageForStationMessage(@RequestParam(value = "page", defaultValue = "1") long page,
                                                                         @RequestParam(value = "pageSize", defaultValue = "20") long pageSize) {
        return PageResultUtils.ok(sysMessageService.pageForStationMessage(page, pageSize));
    }

    /**
     * 当前用户站内消息未读数
     *
     * @return 未读数
     */
    @Operation(summary = "当前用户站内消息未读数")
    @GetMapping("/sys-message/unread-count")
    public ReturnResult<Long> countStationMessageUnread() {
        return sysMessageService.countStationMessageUnread();
    }

    /**
     * 当前用户站内消息标记已读
     * <p>
     * 将当前用户所有未读站内消息标记为已读；传 最后一个标识 时仅标记该游标及之前的消息。
     * </p>
     *
     * @param lastId 已读游标（消息标识），可空
     * @return 是否已更新
     * @param false false
     */
    @Operation(summary = "当前用户站内消息标记已读")
    @PutMapping("/sys-message/read")
    public ReturnResult<Boolean> markStationMessageRead(@RequestParam(required = false) Integer lastId) {
        return sysMessageService.markStationMessageRead(lastId);
    }

    /**
     * 邮件阅读追踪接口
     * <p>
     * 收件人打开邮件时由邮件客户端加载 1x1 透明图触发本接口，
     * 接口将对应日志标记为已读，并返回透明图。
     * </p>
     *
     * @param token 追踪标识
     * @return 1x1 透明 GIF 图片字节
     */
    @Operation(summary = "邮件阅读追踪接口")
    @GetMapping(value = "/track/{token}", produces = MediaType.IMAGE_GIF_VALUE)
    public byte[] track(@PathVariable String token) {
        // 标记已读
        sysMessageService.markLogRead(token);
        // 1x1 透明 GIF
        return Base64.getDecoder().decode(
                "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");
    }
}
