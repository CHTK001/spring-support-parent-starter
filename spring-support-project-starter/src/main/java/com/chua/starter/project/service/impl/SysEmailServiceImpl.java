package com.chua.starter.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.message.result.PushResult;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.starter.project.mapper.SysEmailLogMapper;
import com.chua.starter.project.mapper.SysEmailTemplateMapper;
import com.chua.starter.project.service.EmailService;
import com.chua.starter.project.service.SysEmailService;
import com.chua.starter.project.service.SysEmailTemplateService;
import com.chua.starter.project.service.SysProjectService;
import com.chua.starter.entity.project.SysEmailLog;
import com.chua.starter.entity.project.SysEmailTemplate;
import com.chua.starter.entity.project.SysProject;
import com.chua.starter.project.pojo.email.EmailPageV1Request;
import com.chua.starter.project.pojo.email.EmailRequest;
import com.chua.starter.project.pojo.email.EmailSyncV1Request;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 邮件服务
 * @author CH
 * @since 2024/9/6
 */
@Service
@RequiredArgsConstructor
public class SysEmailServiceImpl implements SysEmailService {

    final EmailService emailService;
    final SysProjectService sysProjectService;
    final SysEmailTemplateMapper sysEmailTemplateMapper;
    final SysEmailTemplateService sysEmailTemplateService;
    final SysEmailLogMapper sysEmailLogMapper;

    @Override
    public ReturnResult<List<PushResult>> send(EmailRequest request) {
        SysEmailTemplate emailTemplate = sysEmailTemplateService.getById(request.getSysEmailTemplateId());
        if(null == emailTemplate) {
            return ReturnResult.error("模板不存在");
        }

        if(ArrayUtils.isEmpty(request.getTarget())) {
            return ReturnResult.error("接收人不能为空");
        }
        long startTime = System.currentTimeMillis();
        SysProject sysProject = sysProjectService.getOne(emailTemplate.getSysProjectId());
        String sysTemplateCode = emailTemplate.getSysEmailTemplateCode();
        SysEmailLog sysEmailLog = new SysEmailLog();
        sysEmailLog.setSysEmailLogTarget(request.getTarget()[0]);
        sysEmailLog.setSysEmailRead(0);
        sysEmailLog.setSysEmailLogTemplateId(String.valueOf(emailTemplate.getSysEmailTemplateId()));
        sysEmailLog.setSysEmailLogParam(null == request.getParams() ? null : Json.toJSONString(request.getParams()));
        sysEmailLog.setSysEmailLogContent(emailTemplate.getSysEmailTemplateContent());
        sysEmailLog.setSysEmailAppId(sysProject.getSysProjectAppId());
        sysEmailLog.setSysEmailAppType(sysProject.getSysProjectVender());
        sysEmailLog.setSysEmailAppSign(sysProject.getSysProjectSign());
        sysEmailLog.setSysEmailCost(BigDecimal.ZERO);
        sysEmailLog.setSysProjectId(sysProject.getSysProjectId());
        sysEmailLog.setCreateTime(LocalDateTime.now());
        sysEmailLogMapper.insert(sysEmailLog);

        ReturnResult<List<PushResult>> send = emailService.send(sysTemplateCode, sysProject, emailTemplate, request, sysEmailLog.getSysEmailLogId());
        sysEmailLog.setSysEmailCost(BigDecimal.valueOf(System.currentTimeMillis() - startTime));
        sysEmailLog.setSysEmailCode("SUCCESS");
        try {
            if (!send.isOk()) {
                sysEmailLog.setSysEmailCode("FAILURE");
                return send;
            }
            List<PushResult> data = send.getData();
            if (null == data) {
                sysEmailLog.setSysEmailCode("FAILURE");
                return send;
            }
            List<PushResult> list = data.stream().filter(it -> !it.isSuccess()).toList();
            if (!list.isEmpty()) {
                if (list.size() == data.size()) {
                    sysEmailLog.setSysEmailCode("FAILURE");
                    return ReturnResult.error("发送失败");
                }
                sysEmailLog.setSysEmailCode("FAILURE");
                return ReturnResult.illegal("部分发送失败" + list.stream()
                        .map(PushResult::getTarget).collect(java.util.stream.Collectors.joining(","))
                );
            }
        } finally {
            sysEmailLogMapper.updateById(sysEmailLog);
        }
        return send;
    }

    @Override
    public ReturnResult<Boolean> syncTemplate(EmailSyncV1Request request) {
        return ReturnResult.illegal("不支持同步");
    }

    @Override
    public IPage<SysEmailTemplate> pageFor(EmailPageV1Request request) {
        if(null == request.getSysProjectId()) {
            return new Page<>();
        }
        return sysEmailTemplateMapper.pageFor(new Page<>(request.getPage(), request.getPageSize()), request);
    }

    @Override
    public ReturnResult<Boolean> delete(SysEmailTemplate sysEmailTemplate) {
        return ReturnResult.of(
                sysEmailTemplateMapper.deleteById(sysEmailTemplate)
        );
    }

    @Override
    public ReturnResult<Boolean> update(SysEmailTemplate sysEmailTemplate) {
        sysEmailTemplate.setSysProjectType(null);
        sysEmailTemplate.setSysProjectId(null);
        return ReturnResult.of(sysEmailTemplateMapper.updateById(sysEmailTemplate) > 0);
    }

    @Override
    public ReturnResult<SysEmailTemplate> save(SysEmailTemplate sysEmailTemplate) {
        sysEmailTemplate.setSysEmailTemplateDelete(0);
        sysEmailTemplateMapper.insert(sysEmailTemplate);
        return ReturnResult.of(sysEmailTemplate);
    }

    @Override
    public ResponseEntity<byte[]> callback(String target, String traceId) {
        if (null == target || null == traceId) {
            return null;
        }
        SysEmailLog sysEmailLog = sysEmailLogMapper.selectOne(new LambdaQueryWrapper<SysEmailLog>()
                .eq(SysEmailLog::getSysEmailLogId, traceId)
        );
        if (null == sysEmailLog) {
            return ResponseEntity.ok(new byte[0]);
        }
        sysEmailLog.setUpdateTime(LocalDateTime.now());
        sysEmailLog.setSysEmailRead(1);
        sysEmailLogMapper.updateById(sysEmailLog);

        return ResponseEntity.ok(new byte[0]);
    }

    @Override
    public IPage<SysEmailLog> pageForLog(EmailPageV1Request request) {
        Integer sysProjectId = request.getSysProjectId();
        if (null == sysProjectId) {
            return new Page<>();
        }

        return sysEmailLogMapper.selectPage(
                new Page<>(request.getPage(), request.getPageSize()),
                new LambdaQueryWrapper<SysEmailLog>()
                        .eq(SysEmailLog::getSysProjectId, sysProjectId)
                        .orderByDesc(SysEmailLog::getCreateTime)
        );
    }
}
