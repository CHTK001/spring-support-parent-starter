package com.chua.starter.message.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.task.message.MessagePush;
import com.chua.common.support.task.message.MessageRequest;
import com.chua.common.support.task.message.MessageResponse;
import com.chua.common.support.task.message.TemplateInfo;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.constant.Constant;
import com.chua.starter.message.support.configuration.MessageTrackProperties;
import com.chua.starter.message.support.entity.SysMessageLog;
import com.chua.starter.message.support.entity.SysMessageProvider;
import com.chua.starter.message.support.entity.SysMessageTemplate;
import com.chua.starter.message.support.mapper.SysMessageLogMapper;
import com.chua.starter.message.support.mapper.SysMessageProviderMapper;
import com.chua.starter.message.support.mapper.SysMessageTemplateMapper;
import com.chua.starter.message.support.pojo.ChannelInfo;
import com.chua.starter.message.support.pojo.MessageContentSendRequest;
import com.chua.starter.message.support.pojo.MessageSendRequest;
import com.chua.starter.message.support.pojo.TemplatePreviewRequest;
import com.chua.starter.message.support.pojo.TemplatePreviewResponse;
import com.chua.starter.message.support.service.SysMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 消息推送服务实现
 * <p>
 * 基于 MyBatis-Plus 提供消息提供商与模板的管理，并通过 utils 的 MessagePush SPI 实现统一发送。
 * 发送完成后通过 @Async 异步记录推送日志；开启邮件阅读追踪时，会在 HTML 内容中注入追踪图，
 * 收件人打开邮件后由追踪接口回写已读状态。
 * </p>
 *
 * @author CH
 * @since 2026/08/03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysMessageServiceImpl implements SysMessageService {

    private final SysMessageProviderMapper sysMessageProviderMapper;
    private final SysMessageTemplateMapper sysMessageTemplateMapper;
    private final SysMessageLogMapper sysMessageLogMapper;
    private final MessageTrackProperties messageTrackProperties;

    @Override
    public IPage<SysMessageProvider> pageForProvider(SysMessageProvider provider, long page, long pageSize) {
        LambdaQueryWrapper<SysMessageProvider> wrapper = Wrappers.<SysMessageProvider>lambdaQuery()
                .eq(SysMessageProvider::getSysMessageProviderDelete, 0)
                .eq(provider.getSysMessageProviderId() != null,
                        SysMessageProvider::getSysMessageProviderId, provider.getSysMessageProviderId())
                .eq(StringUtils.isNotBlank(provider.getSysMessageProviderType()),
                        SysMessageProvider::getSysMessageProviderType, provider.getSysMessageProviderType())
                .like(StringUtils.isNotBlank(provider.getSysMessageProviderName()),
                        SysMessageProvider::getSysMessageProviderName, provider.getSysMessageProviderName())
                .like(StringUtils.isNotBlank(provider.getSysMessageProviderCode()),
                        SysMessageProvider::getSysMessageProviderCode, provider.getSysMessageProviderCode())
                .orderByAsc(SysMessageProvider::getSysMessageProviderSort);
        return sysMessageProviderMapper.selectPage(new Page<>(page, pageSize), wrapper);
    }

    @Override
    public List<SysMessageProvider> listForProvider() {
        return sysMessageProviderMapper.selectList(Wrappers.<SysMessageProvider>lambdaQuery()
                .eq(SysMessageProvider::getSysMessageProviderDelete, 0)
                .eq(SysMessageProvider::getSysMessageProviderStatus, 1)
                .orderByAsc(SysMessageProvider::getSysMessageProviderSort));
    }

    @Override
    public List<ChannelInfo> listChannels() {
        List<ChannelInfo> result = new ArrayList<>();
        ServiceProvider.of(MessagePush.class).forDefinitionEach(definition -> result.add(new ChannelInfo(
                definition.getName(),
                definition.getDescribe(),
                definition.getDescribeType(),
                definition.getDescribeDetail(),
                definition.getSupportedTypes()
        )));
        result.sort(Comparator.comparing(ChannelInfo::channel));
        return result;
    }

    @Override
    public ReturnResult<SysMessageProvider> saveProvider(SysMessageProvider provider) {
        if (StringUtils.isBlank(provider.getSysMessageProviderCode())) {
            return ReturnResult.illegal("提供商编码不能为空");
        }
        if (StringUtils.isBlank(provider.getSysMessageProviderName())) {
            return ReturnResult.illegal("提供商名称不能为空");
        }
        // 编码唯一性校验（含逻辑删除的记录，避免重建同编码时重复）
        Long count = sysMessageProviderMapper.selectCount(Wrappers.<SysMessageProvider>lambdaQuery()
                .eq(SysMessageProvider::getSysMessageProviderCode, provider.getSysMessageProviderCode()));
        if (count != null && count > 0) {
            return ReturnResult.illegal("提供商编码已存在: " + provider.getSysMessageProviderCode());
        }
        provider.setSysMessageProviderDelete(0);
        if (provider.getSysMessageProviderStatus() == null) {
            provider.setSysMessageProviderStatus(1);
        }
        sysMessageProviderMapper.insert(provider);
        return ReturnResult.of(provider);
    }

    @Override
    public ReturnResult<Boolean> updateProvider(SysMessageProvider provider) {
        if (provider.getSysMessageProviderId() == null) {
            return ReturnResult.illegal("提供商ID不能为空");
        }
        if (StringUtils.isNotBlank(provider.getSysMessageProviderCode())) {
            // 编码唯一性校验（排除自身）
            Long count = sysMessageProviderMapper.selectCount(Wrappers.<SysMessageProvider>lambdaQuery()
                    .eq(SysMessageProvider::getSysMessageProviderCode, provider.getSysMessageProviderCode())
                    .ne(SysMessageProvider::getSysMessageProviderId, provider.getSysMessageProviderId()));
            if (count != null && count > 0) {
                return ReturnResult.illegal("提供商编码已存在: " + provider.getSysMessageProviderCode());
            }
        }
        return ReturnResult.of(sysMessageProviderMapper.updateById(provider) > 0);
    }

    @Override
    public ReturnResult<Boolean> deleteProvider(Integer providerId) {
        if (providerId == null) {
            return ReturnResult.illegal("提供商信息不存在");
        }
        SysMessageProvider provider = new SysMessageProvider();
        provider.setSysMessageProviderId(providerId);
        provider.setSysMessageProviderDelete(1);
        return ReturnResult.of(sysMessageProviderMapper.updateById(provider) > 0);
    }

    @Override
    public IPage<SysMessageTemplate> pageForTemplate(SysMessageTemplate template, long page, long pageSize) {
        LambdaQueryWrapper<SysMessageTemplate> wrapper = Wrappers.<SysMessageTemplate>lambdaQuery()
                .eq(SysMessageTemplate::getSysMessageTemplateDelete, 0)
                .eq(template.getSysMessageTemplateId() != null,
                        SysMessageTemplate::getSysMessageTemplateId, template.getSysMessageTemplateId())
                .eq(template.getSysMessageProviderId() != null,
                        SysMessageTemplate::getSysMessageProviderId, template.getSysMessageProviderId())
                .eq(StringUtils.isNotBlank(template.getSysMessageTemplateType()),
                        SysMessageTemplate::getSysMessageTemplateType, template.getSysMessageTemplateType())
                .like(StringUtils.isNotBlank(template.getSysMessageTemplateName()),
                        SysMessageTemplate::getSysMessageTemplateName, template.getSysMessageTemplateName())
                .like(StringUtils.isNotBlank(template.getSysMessageTemplateCode()),
                        SysMessageTemplate::getSysMessageTemplateCode, template.getSysMessageTemplateCode())
                .orderByAsc(SysMessageTemplate::getSysMessageTemplateSort);
        return sysMessageTemplateMapper.selectPage(new Page<>(page, pageSize), wrapper);
    }

    @Override
    public List<SysMessageTemplate> listForTemplate() {
        return sysMessageTemplateMapper.selectList(Wrappers.<SysMessageTemplate>lambdaQuery()
                .eq(SysMessageTemplate::getSysMessageTemplateDelete, 0)
                .eq(SysMessageTemplate::getSysMessageTemplateStatus, 1)
                .orderByAsc(SysMessageTemplate::getSysMessageTemplateSort));
    }

    @Override
    public ReturnResult<SysMessageTemplate> saveTemplate(SysMessageTemplate template) {
        if (template.getSysMessageProviderId() == null) {
            return ReturnResult.illegal("请选择提供商");
        }
        if (StringUtils.isBlank(template.getSysMessageTemplateCode())) {
            return ReturnResult.illegal("模板编码不能为空");
        }
        if (StringUtils.isBlank(template.getSysMessageTemplateName())) {
            return ReturnResult.illegal("模板名称不能为空");
        }
        if (StringUtils.isBlank(template.getSysMessageTemplateContent())) {
            return ReturnResult.illegal("模板内容不能为空");
        }
        // 校验模板内容占位符格式，只允许 ${var} 或 {{var}}
        String illegalPlaceholder = findIllegalPlaceholder(template.getSysMessageTemplateContent());
        if (illegalPlaceholder != null) {
            return ReturnResult.illegal("模板内容包含非法占位符: " + illegalPlaceholder + "，仅支持 ${var} 或 {{var}}");
        }
        // 同一提供商下模板编码唯一
        Long count = sysMessageTemplateMapper.selectCount(Wrappers.<SysMessageTemplate>lambdaQuery()
                .eq(SysMessageTemplate::getSysMessageProviderId, template.getSysMessageProviderId())
                .eq(SysMessageTemplate::getSysMessageTemplateCode, template.getSysMessageTemplateCode()));
        if (count != null && count > 0) {
            return ReturnResult.illegal("模板编码已存在: " + template.getSysMessageTemplateCode());
        }
        template.setSysMessageTemplateDelete(0);
        if (template.getSysMessageTemplateStatus() == null) {
            template.setSysMessageTemplateStatus(1);
        }
        sysMessageTemplateMapper.insert(template);
        return ReturnResult.of(template);
    }

    @Override
    public ReturnResult<Boolean> updateTemplate(SysMessageTemplate template) {
        if (template.getSysMessageTemplateId() == null) {
            return ReturnResult.illegal("模板ID不能为空");
        }
        if (StringUtils.isNotBlank(template.getSysMessageTemplateCode())) {
            // 同一提供商下模板编码唯一（排除自身）
            Long count = sysMessageTemplateMapper.selectCount(Wrappers.<SysMessageTemplate>lambdaQuery()
                    .eq(SysMessageTemplate::getSysMessageProviderId, template.getSysMessageProviderId())
                    .eq(SysMessageTemplate::getSysMessageTemplateCode, template.getSysMessageTemplateCode())
                    .ne(SysMessageTemplate::getSysMessageTemplateId, template.getSysMessageTemplateId()));
            if (count != null && count > 0) {
                return ReturnResult.illegal("模板编码已存在: " + template.getSysMessageTemplateCode());
            }
        }
        return ReturnResult.of(sysMessageTemplateMapper.updateById(template) > 0);
    }

    @Override
    public ReturnResult<Boolean> deleteTemplate(Integer templateId) {
        if (templateId == null) {
            return ReturnResult.illegal("模板信息不存在");
        }
        SysMessageTemplate template = new SysMessageTemplate();
        template.setSysMessageTemplateId(templateId);
        template.setSysMessageTemplateDelete(1);
        return ReturnResult.of(sysMessageTemplateMapper.updateById(template) > 0);
    }

    @Override
    public ReturnResult<List<MessageResponse>> send(MessageSendRequest request) {
        if (request.getSysMessageTemplateId() == null) {
            return ReturnResult.illegal("模板ID不能为空");
        }
        if (ArrayUtils.isEmpty(request.getTarget())) {
            return ReturnResult.illegal("接收人不能为空");
        }
        SysMessageTemplate template = sysMessageTemplateMapper.selectById(request.getSysMessageTemplateId());
        if (template == null) {
            return ReturnResult.error("模板不存在");
        }
        if (template.getSysMessageTemplateStatus() != null && template.getSysMessageTemplateStatus() == 0) {
            return ReturnResult.error("模板已停用");
        }
        SysMessageProvider provider = sysMessageProviderMapper.selectById(template.getSysMessageProviderId());
        if (provider == null) {
            return ReturnResult.error("消息提供商未配置");
        }
        if (provider.getSysMessageProviderStatus() != null && provider.getSysMessageProviderStatus() == 0) {
            return ReturnResult.error("消息提供商已停用");
        }
        String channel = resolveChannel(provider);
        MessagePush messagePush = MessagePush.create(channel);
        if (messagePush == null) {
            return ReturnResult.error("暂不支持的消息渠道: " + channel);
        }

        // 是否启用邮件阅读追踪
        boolean trackable = isTrackable(channel, template.getSysMessageTemplateType());

        // 生成批次号，同一次请求的所有接收人共享
        String batchId = UUID.randomUUID().toString().replace("-", "");

        List<MessageResponse> result = new ArrayList<>(request.getTarget().length);
        for (String target : request.getTarget()) {
            String trackToken = trackable ? UUID.randomUUID().toString().replace("-", "") : null;
            // 防抖：间隔内同一接收人相同模板已发送成功则跳过
            if (isDebounced(provider, target, template.getSysMessageTemplateId(), null)) {
                MessageResponse debounced = MessageResponse.builder()
                        .success(true)
                        .errorMessage("间隔内已发送，防抖跳过")
                        .durationMillis(0)
                        .build();
                result.add(debounced);
                saveLogAsync(channel, target, template, provider, request, debounced, trackToken, batchId,
                        request.getSubject());
                continue;
            }
            try {
                MessageRequest messageRequest = MessageRequest.builder()
                        .to(target)
                        .subject(request.getSubject())
                        .content(buildContentWithTrack(trackToken, template.getSysMessageTemplateContent()))
                        .contentType(template.getSysMessageTemplateType())
                        .templateId(template.getSysMessageTemplateCode())
                        .templateParams(toParamMap(request))
                        .build();
                MessageResponse response = messagePush.send(messageRequest);
                result.add(response);
                saveLogAsync(channel, target, template, provider, request, response, trackToken, batchId,
                        request.getSubject());
            } catch (Exception e) {
                log.error("发送消息失败, 接收人: {}", target, e);
                result.add(MessageResponse.failure(e.getMessage()));
                saveLogAsync(channel, target, template, provider, request, MessageResponse.failure(e.getMessage()),
                        trackToken, batchId, request.getSubject());
            }
        }
        return ReturnResult.of(result);
    }

    @Override
    public ReturnResult<List<MessageResponse>> sendContent(MessageContentSendRequest request) {
        if (request.getSysMessageProviderId() == null) {
            return ReturnResult.illegal("提供商ID不能为空");
        }
        if (ArrayUtils.isEmpty(request.getTarget())) {
            return ReturnResult.illegal("接收人不能为空");
        }
        if (StringUtils.isBlank(request.getContent())) {
            return ReturnResult.illegal("消息内容不能为空");
        }
        SysMessageProvider provider = sysMessageProviderMapper.selectById(request.getSysMessageProviderId());
        if (provider == null) {
            return ReturnResult.error("消息提供商未配置");
        }
        if (provider.getSysMessageProviderStatus() != null && provider.getSysMessageProviderStatus() == 0) {
            return ReturnResult.error("消息提供商已停用");
        }
        String channel = resolveChannel(provider);
        MessagePush messagePush = MessagePush.create(channel);
        if (messagePush == null) {
            return ReturnResult.error("暂不支持的消息渠道: " + channel);
        }

        // 是否启用邮件阅读追踪
        boolean trackable = isTrackable(channel, provider.getSysMessageProviderType());

        // 生成批次号，同一次请求的所有接收人共享
        String batchId = UUID.randomUUID().toString().replace("-", "");

        List<MessageResponse> result = new ArrayList<>(request.getTarget().length);
        for (String target : request.getTarget()) {
            String trackToken = trackable ? UUID.randomUUID().toString().replace("-", "") : null;
            // 防抖：间隔内同一接收人相同内容已发送成功则跳过
            if (isDebounced(provider, target, null, request.getContent())) {
                MessageResponse debounced = MessageResponse.builder()
                        .success(true)
                        .errorMessage("间隔内已发送，防抖跳过")
                        .durationMillis(0)
                        .build();
                result.add(debounced);
                saveContentLogAsync(channel, target, provider, request, debounced, trackToken, batchId,
                        request.getSubject());
                continue;
            }
            try {
                MessageRequest messageRequest = MessageRequest.builder()
                        .to(target)
                        .subject(request.getSubject())
                        .content(buildContentWithTrack(trackToken, request.getContent()))
                        .contentType(provider.getSysMessageProviderType())
                        .templateParams(toParamMap(request.getParams()))
                        .build();
                MessageResponse response = messagePush.send(messageRequest);
                result.add(response);
                saveContentLogAsync(channel, target, provider, request, response, trackToken, batchId,
                        request.getSubject());
            } catch (Exception e) {
                log.error("按内容发送消息失败, 接收人: {}", target, e);
                result.add(MessageResponse.failure(e.getMessage()));
                saveContentLogAsync(channel, target, provider, request, MessageResponse.failure(e.getMessage()),
                        trackToken, batchId, request.getSubject());
            }
        }
        return ReturnResult.of(result);
    }

    @Override
    public ReturnResult<Integer> syncTemplate(Integer providerId) {
        if (providerId == null) {
            return ReturnResult.illegal("提供商ID不能为空");
        }
        SysMessageProvider provider = sysMessageProviderMapper.selectById(providerId);
        if (provider == null) {
            return ReturnResult.error("消息提供商不存在");
        }
        if (provider.getSysMessageProviderStatus() != null && provider.getSysMessageProviderStatus() == 0) {
            return ReturnResult.error("消息提供商已停用");
        }
        String channel = resolveChannel(provider);
        MessagePush messagePush = MessagePush.create(channel);
        if (messagePush == null) {
            return ReturnResult.error("暂不支持的消息渠道: " + channel);
        }
        List<TemplateInfo> templates = messagePush.listTemplates();
        if (templates == null || templates.isEmpty()) {
            return ReturnResult.error("提供商未返回可用模板");
        }
        int count = 0;
        for (TemplateInfo templateInfo : templates) {
            if (templateInfo == null || StringUtils.isBlank(templateInfo.id())) {
                continue;
            }
            LambdaQueryWrapper<SysMessageTemplate> wrapper = Wrappers.<SysMessageTemplate>lambdaQuery()
                    .eq(SysMessageTemplate::getSysMessageProviderId, providerId)
                    .eq(SysMessageTemplate::getSysMessageTemplateCode, templateInfo.id());
            SysMessageTemplate exists = sysMessageTemplateMapper.selectOne(wrapper);
            if (exists != null) {
                exists.setSysMessageTemplateName(templateInfo.name());
                exists.setSysMessageTemplateContent(templateInfo.content());
                exists.setSysMessageTemplateType(templateInfo.type());
                exists.setSysMessageTemplateStatus(1);
                sysMessageTemplateMapper.updateById(exists);
            } else {
                SysMessageTemplate template = new SysMessageTemplate();
                template.setSysMessageProviderId(providerId);
                template.setSysMessageTemplateCode(templateInfo.id());
                template.setSysMessageTemplateName(templateInfo.name());
                template.setSysMessageTemplateContent(templateInfo.content());
                template.setSysMessageTemplateType(templateInfo.type());
                template.setSysMessageTemplateStatus(1);
                template.setSysMessageTemplateDelete(0);
                sysMessageTemplateMapper.insert(template);
            }
            count++;
        }
        return ReturnResult.of(count);
    }

    /**
     * 异步记录按内容发送的消息推送日志
     * <p>
     * 使用 @Async 在独立的线程池中执行，不阻塞发送主流程。
     * </p>
     *
     * @param channel    渠道标识
     * @param target     接收人
     * @param provider   消息提供商
     * @param request    按内容发送请求
     * @param response   发送响应
     * @param trackToken 阅读追踪标识（可空）
     * @param batchId    发送批次号
     * @param subject    消息主题（可空）
     */
    @Async(Constant.DEFAULT_EXECUTOR_TASK)
    public void saveContentLogAsync(String channel, String target, SysMessageProvider provider,
                                    MessageContentSendRequest request, MessageResponse response, String trackToken,
                                    String batchId, String subject) {
        try {
            SysMessageLog messageLog = new SysMessageLog();
            messageLog.setSysMessageLogTarget(target);
            messageLog.setSysMessageProviderId(provider.getSysMessageProviderId());
            messageLog.setSysMessageLogChannel(channel);
            messageLog.setSysMessageLogContent(request.getContent());
            messageLog.setSysMessageLogParam(Json.toPrettyJson(request.getParams()));
            messageLog.setSysMessageCost(BigDecimal.valueOf(response.getDurationMillis()));
            messageLog.setSysMessageCode(response.isSuccess() ? "SUCCESS" : "FAILURE");
            messageLog.setSysMessageMessage(response.getErrorMessage());
            messageLog.setSysMessageLogSubject(subject);
            messageLog.setSysMessageLogMessageId(response.getMessageId());
            messageLog.setSysMessageBatchId(batchId);
            messageLog.setSysMessageTrackToken(trackToken);
            messageLog.setSysMessageReadStatus(trackToken == null ? null : 0);
            messageLog.setSysMessageLogDelete(0);
            sysMessageLogMapper.insert(messageLog);
        } catch (Exception e) {
            log.error("记录按内容发送日志失败, 接收人: {}", target, e);
        }
    }

    /**
     * 异步记录消息推送日志
     * <p>
     * 使用 @Async 在独立的线程池中执行，不阻塞发送主流程。
     * </p>
     *
     * @param channel    渠道标识
     * @param target     接收人
     * @param template   消息模板
     * @param provider   消息提供商
     * @param request    发送请求
     * @param response   发送响应
     * @param trackToken 阅读追踪标识（可空）
     * @param batchId    发送批次号
     * @param subject    消息主题（可空）
     */
    @Async(Constant.DEFAULT_EXECUTOR_TASK)
    public void saveLogAsync(String channel, String target, SysMessageTemplate template,
                             SysMessageProvider provider, MessageSendRequest request, MessageResponse response,
                             String trackToken, String batchId, String subject) {
        try {
            SysMessageLog messageLog = new SysMessageLog();
            messageLog.setSysMessageLogTarget(target);
            messageLog.setSysMessageTemplateId(template.getSysMessageTemplateId());
            messageLog.setSysMessageProviderId(provider.getSysMessageProviderId());
            messageLog.setSysMessageLogChannel(channel);
            messageLog.setSysMessageLogContent(template.getSysMessageTemplateContent());
            messageLog.setSysMessageLogParam(Json.toPrettyJson(request.getParams()));
            messageLog.setSysMessageCost(BigDecimal.valueOf(response.getDurationMillis()));
            messageLog.setSysMessageCode(response.isSuccess() ? "SUCCESS" : "FAILURE");
            messageLog.setSysMessageMessage(response.getErrorMessage());
            messageLog.setSysMessageLogSubject(subject);
            messageLog.setSysMessageLogMessageId(response.getMessageId());
            messageLog.setSysMessageBatchId(batchId);
            messageLog.setSysMessageTrackToken(trackToken);
            messageLog.setSysMessageReadStatus(trackToken == null ? null : 0);
            messageLog.setSysMessageLogDelete(0);
            sysMessageLogMapper.insert(messageLog);
        } catch (Exception e) {
            log.error("记录消息推送日志失败, 接收人: {}", target, e);
        }
    }

    @Override
    public IPage<SysMessageLog> pageForLog(SysMessageLog log, long page, long pageSize) {
        LambdaQueryWrapper<SysMessageLog> wrapper = Wrappers.<SysMessageLog>lambdaQuery()
                .eq(SysMessageLog::getSysMessageLogDelete, 0)
                .eq(log.getSysMessageLogId() != null,
                        SysMessageLog::getSysMessageLogId, log.getSysMessageLogId())
                .eq(log.getSysMessageTemplateId() != null,
                        SysMessageLog::getSysMessageTemplateId, log.getSysMessageTemplateId())
                .eq(log.getSysMessageProviderId() != null,
                        SysMessageLog::getSysMessageProviderId, log.getSysMessageProviderId())
                .eq(StringUtils.isNotBlank(log.getSysMessageLogChannel()),
                        SysMessageLog::getSysMessageLogChannel, log.getSysMessageLogChannel())
                .like(StringUtils.isNotBlank(log.getSysMessageLogTarget()),
                        SysMessageLog::getSysMessageLogTarget, log.getSysMessageLogTarget())
                .eq(StringUtils.isNotBlank(log.getSysMessageCode()),
                        SysMessageLog::getSysMessageCode, log.getSysMessageCode())
                .eq(log.getSysMessageReadStatus() != null,
                        SysMessageLog::getSysMessageReadStatus, log.getSysMessageReadStatus())
                .orderByDesc(SysMessageLog::getCreateTime);
        return sysMessageLogMapper.selectPage(new Page<>(page, pageSize), wrapper);
    }

    @Override
    public ReturnResult<TemplatePreviewResponse> previewTemplate(TemplatePreviewRequest request) {
        if (request == null || request.getSysMessageTemplateId() == null) {
            return ReturnResult.illegal("模板ID不能为空");
        }
        SysMessageTemplate template = sysMessageTemplateMapper.selectById(request.getSysMessageTemplateId());
        if (template == null) {
            return ReturnResult.error("模板不存在");
        }
        String content = template.getSysMessageTemplateContent();
        if (StringUtils.isBlank(content)) {
            return ReturnResult.of(TemplatePreviewResponse.builder()
                    .content("")
                    .unresolvedParams(Collections.emptyList())
                    .complete(true)
                    .build());
        }
        Map<String, String> params = toParamMap(request.getParams());
        List<String> unresolved = new ArrayList<>();
        // 支持 ${var} 与 {{var}} 两种占位符
        Pattern pattern = Pattern.compile("\\$\\{(.+?)\\}|\\{\\{(.+?)\\}\\}");
        Matcher matcher = pattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String value = params.get(key);
            if (value == null) {
                unresolved.add(key);
                matcher.appendReplacement(sb, Matcher.quoteReplacement("${" + key + "}"));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
        }
        matcher.appendTail(sb);
        return ReturnResult.of(TemplatePreviewResponse.builder()
                .content(sb.toString())
                .unresolvedParams(unresolved)
                .complete(unresolved.isEmpty())
                .build());
    }

    @Override
    public ReturnResult<Integer> cleanLog(LocalDateTime beforeTime) {
        if (beforeTime == null) {
            return ReturnResult.illegal("截止时间不能为空");
        }
        int deleted = sysMessageLogMapper.delete(Wrappers.<SysMessageLog>lambdaQuery()
                .le(SysMessageLog::getCreateTime, beforeTime));
        return ReturnResult.of(deleted);
    }

    @Override
    public ReturnResult<Boolean> markLogRead(String token) {
        if (StringUtils.isBlank(token)) {
            return ReturnResult.illegal("追踪标识不能为空");
        }
        SysMessageLog messageLog = sysMessageLogMapper.selectOne(Wrappers.<SysMessageLog>lambdaQuery()
                .eq(SysMessageLog::getSysMessageTrackToken, token));
        if (messageLog == null) {
            return ReturnResult.error("追踪标识不存在");
        }
        if (Integer.valueOf(1).equals(messageLog.getSysMessageReadStatus())) {
            // 已标记过，直接返回成功，保持幂等
            return ReturnResult.ok(Boolean.TRUE);
        }
        SysMessageLog update = new SysMessageLog();
        update.setSysMessageLogId(messageLog.getSysMessageLogId());
        update.setSysMessageReadStatus(1);
        update.setSysMessageReadTime(LocalDateTime.now());
        boolean updated = sysMessageLogMapper.updateById(update) > 0;
        return ReturnResult.of(updated);
    }

    /**
     * 解析消息渠道标识
     * <p>
     * 优先使用厂家编码，否则使用提供商类型作为渠道标识。
     * </p>
     *
     * @param provider 消息提供商
     * @return 渠道标识
     */
    private String resolveChannel(SysMessageProvider provider) {
        return StringUtils.isNotBlank(provider.getSysMessageProviderVender())
                ? provider.getSysMessageProviderVender()
                : provider.getSysMessageProviderType();
    }

    /**
     * 判断是否启用邮件阅读追踪
     * <p>
     * 仅邮件渠道、HTML 内容类型且配置开启时返回 true。
     * </p>
     *
     * @param channel     渠道标识
     * @param contentType 内容类型
     * @return 是否可追踪
     */
    private boolean isTrackable(String channel, String contentType) {
        if (!messageTrackProperties.isEnabled()) {
            return false;
        }
        boolean isEmail = "email".equalsIgnoreCase(channel)
                || "smtp".equalsIgnoreCase(channel);
        boolean isHtml = StringUtils.isNotBlank(contentType)
                && ("html".equalsIgnoreCase(contentType) || "text/html".equalsIgnoreCase(contentType)
                || "EMAIL".equalsIgnoreCase(contentType));
        return isEmail && isHtml;
    }

    /**
     * 防抖检查
     * <p>
     * 提供商配置了重复消息间隔（分钟）时，检查间隔内同一接收人的相同模板（或相同内容）
     * 是否已发送成功；已成功则本次不重复发送。失败记录不参与防抖，允许重试。
     * </p>
     *
     * @param provider   消息提供商
     * @param target     接收人
     * @param templateId 模板ID（按模板发送时传入，按内容发送传 null）
     * @param content    消息内容（按内容发送时传入，按模板发送传 null）
     * @return 是否命中防抖（间隔内已发送成功）
     */
    private boolean isDebounced(SysMessageProvider provider, String target, Integer templateId, String content) {
        Integer interval = provider.getSysMessageProviderInterval();
        // 未配置时默认 1 分钟防抖，显式配置 0 表示不限制
        if (interval == null) {
            interval = 1;
        }
        if (interval <= 0) {
            return false;
        }
        LambdaQueryWrapper<SysMessageLog> wrapper = Wrappers.<SysMessageLog>lambdaQuery()
                .eq(SysMessageLog::getSysMessageLogDelete, 0)
                .eq(SysMessageLog::getSysMessageProviderId, provider.getSysMessageProviderId())
                .eq(SysMessageLog::getSysMessageLogTarget, target)
                .eq(SysMessageLog::getSysMessageCode, "SUCCESS")
                .eq(templateId != null, SysMessageLog::getSysMessageTemplateId, templateId)
                .eq(StringUtils.isNotBlank(content), SysMessageLog::getSysMessageLogContent, content)
                .ge(SysMessageLog::getCreateTime, LocalDateTime.now().minusMinutes(interval));
        return sysMessageLogMapper.selectCount(wrapper) > 0;
    }

    /**
     * 构建带追踪图的内容
     * <p>
     * 追踪开启时在内容末尾追加 1x1 透明图，指向追踪接口；追踪图对收件人不可见。
     * </p>
     *
     * @param trackToken 追踪标识（可空）
     * @param content    原始内容
     * @return 追加追踪图后的内容
     */
    private String buildContentWithTrack(String trackToken, String content) {
        if (StringUtils.isBlank(trackToken)) {
            return content;
        }
        String baseUrl = messageTrackProperties.getBaseUrl();
        if (StringUtils.isBlank(baseUrl)) {
            // 未配置基础地址时无法生成可访问的追踪图
            return content;
        }
        String trackUrl = baseUrl.replaceAll("/+$", "") + "/v2/message/track/" + trackToken;
        return content + "\n<img src=\"" + trackUrl + "\" width=\"1\" height=\"1\" style=\"display:none;\" alt=\"\"/>";
    }

    /**
     * 将发送请求中的参数转换为模板参数字符串映射
     *
     * @param request 发送请求
     * @return 模板参数映射
     */
    private Map<String, String> toParamMap(MessageSendRequest request) {
        if (request.getParams() == null || request.getParams().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> params = new HashMap<>(request.getParams().size());
        request.getParams().forEach((key, value) -> params.put(key, value == null ? "" : String.valueOf(value)));
        return params;
    }

    /**
     * 将参数映射转换为模板参数字符串映射
     *
     * @param paramMap 原始参数映射
     * @return 模板参数映射
     */
    private Map<String, String> toParamMap(Map<String, Object> paramMap) {
        if (CollectionUtils.isEmpty(paramMap)) {
            return Collections.emptyMap();
        }
        Map<String, String> params = new HashMap<>(paramMap.size());
        paramMap.forEach((key, value) -> params.put(key, value == null ? "" : String.valueOf(value)));
        return params;
    }

    /**
     * 校验模板内容中的占位符格式
     * <p>
     * 只允许 ${var} 或 {{var}} 两种形式，找到第一个非法占位符返回其原始文本，
     * 未发现非法占位符时返回 null。
     * </p>
     *
     * @param content 模板内容
     * @return 第一个非法占位符文本，无则返回 null
     */
    private String findIllegalPlaceholder(String content) {
        if (StringUtils.isBlank(content)) {
            return null;
        }
        // 非法占位符：以 { 或 $ 开头但不符合 ${var} / {{var}} 规则
        Pattern legal = Pattern.compile("\\$\\{[^}]+}|\\{\\{[^}]+}}");
        Pattern any = Pattern.compile("\\$?\\{[^}]+}");
        Matcher anyMatcher = any.matcher(content);
        while (anyMatcher.find()) {
            String segment = anyMatcher.group();
            if (!legal.matcher(segment).matches()) {
                return segment;
            }
        }
        return null;
    }
}
