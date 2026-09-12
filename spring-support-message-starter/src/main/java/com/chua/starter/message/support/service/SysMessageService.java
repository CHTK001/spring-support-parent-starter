package com.chua.starter.message.support.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.task.message.MessageResponse;
import com.chua.starter.message.support.entity.SysMessageLog;
import com.chua.starter.message.support.entity.SysMessageProvider;
import com.chua.starter.message.support.entity.SysMessageTemplate;
import com.chua.starter.message.support.pojo.MessageContentSendRequest;
import com.chua.starter.message.support.pojo.MessageSendRequest;
import com.chua.starter.message.support.pojo.TemplatePreviewRequest;
import com.chua.starter.message.support.pojo.TemplatePreviewResponse;
import com.chua.starter.message.support.pojo.ChannelInfo;
import com.chua.starter.message.support.pojo.SysMessageStationItem;

import java.util.List;

/**
 * 消息推送服务
 * <p>
 * 提供消息提供商与消息模板的管理能力，以及统一的消息发送能力。
 * </p>
 *
 * @since 2026/08/03
 * @author CH
 */
public interface SysMessageService {

    /**
     * 提供商分页查询
     *
     * @param provider 提供商查询条件
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    IPage<SysMessageProvider> pageForProvider(SysMessageProvider provider, long page, long pageSize);

    /**
     * 提供商列表查询
     *
     * @return 提供商列表
     */
    List<SysMessageProvider> listForProvider();

    /**
     * 已注册消息渠道列表
     * <p>
      * 遍历所有通过 SPI 注册的 消息push 渠道，返回其标识、中文名称、类型与支持能力，
     * 用于前端「消息渠道」下拉选择。
     * </p>
     *
     * @return 渠道信息列表
     */
    List<ChannelInfo> listChannels();

    /**
     * 新增提供商
     *
     * @param provider 提供商信息
     * @return 保存结果
     */
    ReturnResult<SysMessageProvider> saveProvider(SysMessageProvider provider);

    /**
     * 更新提供商
     *
     * @param provider 提供商信息
     * @return 更新结果
     */
    ReturnResult<Boolean> updateProvider(SysMessageProvider provider);

    /**
     * 删除提供商
     *
     * @param providerId 提供商标识
     * @return 删除结果
     */
    ReturnResult<Boolean> deleteProvider(Integer providerId);

    /**
     * 模板分页查询
     *
     * @param template 模板查询条件
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    IPage<SysMessageTemplate> pageForTemplate(SysMessageTemplate template, long page, long pageSize);

    /**
     * 模板列表查询
     *
     * @return 模板列表
     */
    List<SysMessageTemplate> listForTemplate();

    /**
     * 新增模板
     *
     * @param template 模板信息
     * @return 保存结果
     */
    ReturnResult<SysMessageTemplate> saveTemplate(SysMessageTemplate template);

    /**
     * 更新模板
     *
     * @param template 模板信息
     * @return 更新结果
     */
    ReturnResult<Boolean> updateTemplate(SysMessageTemplate template);

    /**
     * 删除模板
     *
     * @param templateId 模板标识
     * @return 删除结果
     */
    ReturnResult<Boolean> deleteTemplate(Integer templateId);

    /**
     * 按模板发送消息
     *
     * @param request 发送请求
     * @return 发送结果
     */
    ReturnResult<List<MessageResponse>> send(MessageSendRequest request);

    /**
     * 按内容发送消息
     * <p>
     * 不依赖消息模板，按提供商直接发送指定内容。
     * </p>
     *
     * @param request 按内容发送请求
     * @return 发送结果
     */
    ReturnResult<List<MessageResponse>> sendContent(MessageContentSendRequest request);

    /**
     * 手动同步模板
     * <p>
     * 从指定提供商拉取远端模板列表，写入本地消息模板表。
     * </p>
     *
     * @param providerId 提供商标识
     * @return 同步结果
     */
    ReturnResult<Integer> syncTemplate(Integer providerId);

    /**
     * 推送日志分页查询
     *
     * @param log      日志查询条件
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    IPage<SysMessageLog> pageForLog(SysMessageLog log, long page, long pageSize);

    /**
     * 模板内容渲染预览
     * <p>
     * 根据模板内容与参数渲染最终发送内容，并返回未填充的占位符列表，
     * 用于发送前的预览与校验。
     * </p>
     *
     * @param request 预览请求
     * @return 渲染结果
     */
    ReturnResult<TemplatePreviewResponse> previewTemplate(TemplatePreviewRequest request);

    /**
     * 清理推送日志
     * <p>
     * 按创建时间删除指定日期之前的日志记录。
     * </p>
     *
     * @param beforeTime 截止时间（含该时间之前的日志将被清理）
     * @return 删除条数
     */
    ReturnResult<Integer> cleanLog(java.time.LocalDateTime beforeTime);

    /**
     * 邮件阅读追踪
     * <p>
     * 根据追踪标识将对应日志标记为已读，用于收件人打开邮件时的回执。
     * </p>
     *
     * @param token 追踪标识
     * @return 是否已更新
     */
    ReturnResult<Boolean> markLogRead(String token);

    /**
     * 当前用户站内消息分页查询
     * <p>
     * 查询当前登录用户接收的站内消息（渠道 station），按发送时间倒序分页返回。
     * </p>
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    IPage<SysMessageStationItem> pageForStationMessage(long page, long pageSize);

    /**
     * 当前用户站内消息未读数
     *
     * @return 未读消息数
     */
    ReturnResult<Long> countStationMessageUnread();

    /**
     * 当前用户站内消息标记已读
     * <p>
       * 将当前用户所有未读站内消息标记为已读（最后一个标识 为空时标记全部）。
     * </p>
     *
     * @param lastId 已读游标（消息标识）；为空表示全部已读
     * @return 是否已更新
     */
    ReturnResult<Boolean> markStationMessageRead(Integer lastId);
}
