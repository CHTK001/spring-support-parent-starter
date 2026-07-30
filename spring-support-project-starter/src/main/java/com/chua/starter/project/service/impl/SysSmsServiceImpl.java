package com.chua.starter.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.message.config.PushSetting;
import com.chua.common.support.lang.message.entity.TemplateInfo;
import com.chua.common.support.lang.message.result.PushResult;
import com.chua.common.support.lang.message.result.TemplateResult;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.starter.project.mapper.SysSmsTemplateMapper;
import com.chua.starter.project.service.*;
import com.chua.starter.entity.project.SysProject;
import com.chua.starter.entity.project.SysSmsLog;
import com.chua.starter.entity.project.SysSmsTemplate;
import com.chua.starter.project.pojo.sms.SmsPageV1Request;
import com.chua.starter.project.pojo.sms.SmsRequest;
import com.chua.starter.project.pojo.sms.SmsSyncV1Request;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 短信服务
 * @author CH
 * @since 2024/9/6
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysSmsServiceImpl implements SysSmsService {

    final SmsService smsService;
    final SysSmsLogService sysSmsLogService;
    final SysProjectService sysProjectService;
    final SysSmsTemplateMapper sysSmsTemplateMapper;
    final SysSmsTemplateService sysSmsTemplateService;
    @Override
    public ReturnResult<List<PushResult>> send(SmsRequest request) {
        SysSmsTemplate smsTemplate = sysSmsTemplateService.getById(request.getSysSmsTemplateId());
        if (null == smsTemplate) {
            return ReturnResult.error("模板不存在");
        }

        if(ArrayUtils.isEmpty(request.getTarget())) {
            return ReturnResult.error("接收人不能为空");
        }
        SysProject sysProject = sysProjectService.getOne(smsTemplate.getSysProjectId());
        String sysTemplateCode = smsTemplate.getSysSmsTemplateCode();
        ReturnResult<List<PushResult>> send = smsService.send(sysTemplateCode, sysProject, request);
        List<PushResult> data = send.getData();
        if(null == data) {
            return send;
        }
        List<SysSmsLog> sysSmsLogs = new ArrayList<>(data.size());
        for (PushResult item : data) {
            SysSmsLog sysSmsLog = new SysSmsLog();
            sysSmsLog.setSysSmsAppSign(sysProject.getSysProjectSign());
            sysSmsLog.setSysSmsAppId(sysProject.getSysProjectAppId());
            sysSmsLog.setSysProjectId(sysProject.getSysProjectId());
            sysSmsLog.setSysSmsLogContent(smsTemplate.getSysSmsTemplateContent());
            sysSmsLog.setSysSmsCode(item.getCode().name());
            sysSmsLog.setSysSmsMessage(item.getMessage());
            sysSmsLog.setSysSmsLogParam(Json.toPrettyJson(request.getParams()));
            sysSmsLog.setSysSmsLogTarget(item.getTarget());
            sysSmsLog.setSysSmsCost(BigDecimal.valueOf(item.getCost()));
            sysSmsLog.setSysSmsLogTemplateId(request.getSysSmsTemplateId());

            sysSmsLogs.add(sysSmsLog);
        }

        try {
            sysSmsLogService.saveBatch(sysSmsLogs);
        } catch (Exception e) {
            log.error("保存短信日志失败", e);
        }
        return send;
    }

    @Override
    public ReturnResult<Boolean> syncTemplate(SmsSyncV1Request request) {
        SysProject sysProject = sysProjectService.getOne(request.getSysProjectId());
        PushSetting pushSetting = PushSetting.builder()
                .accessKeyId(sysProject.getSysProjectAppId())
                .accessKeySecret(sysProject.getSysProjectAppSecret())
                .build();

        ReturnResult<TemplateResult> templateResultReturnResult = smsService.syncTemplate(sysProject.getSysProjectVender(), pushSetting);
        if(templateResultReturnResult.isOk()) {
            TemplateResult templateResult = templateResultReturnResult.getData();
            for (TemplateInfo datum : templateResult.getData()) {
                SysSmsTemplate sysTemplate = new SysSmsTemplate();
                sysTemplate.setSysSmsTemplateStatus(1);
                sysTemplate.setSysSmsTemplateApprove(2);
                sysTemplate.setSysSmsTemplateContent(datum.getTemplateContent());
                sysTemplate.setSysSmsTemplateCode(datum.getTemplateCode());
                sysTemplate.setSysSmsTemplateName(datum.getTemplateName());
                sysTemplate.setSysSmsTemplateSort(2);
                sysTemplate.setSysSmsTemplateDefault(0);
                sysTemplate.setSysSmsTemplateDelete(0);
                sysTemplate.setSysSmsTemplateType(0);
                sysTemplate.setSysSmsTemplateDisabled(1);
                sysTemplate.setSysSmsTemplateOrigin("sms同步");
                sysTemplate.setSysProjectId(sysProject.getSysProjectId());
                sysTemplate.setSysProjectType(null);

                LambdaQueryWrapper<SysSmsTemplate> wrapper = Wrappers.<SysSmsTemplate>lambdaQuery()
                        .eq(SysSmsTemplate::getSysSmsTemplateCode, sysTemplate.getSysSmsTemplateCode())
                        .eq(SysSmsTemplate::getSysProjectId, sysTemplate.getSysProjectId());

                if(sysSmsTemplateService.count(wrapper) > 0) {
                    try {
                        sysSmsTemplateService.update(sysTemplate, wrapper);
                    } catch (Exception e) {
                        continue;
                    }
                    continue;
                }

                try {
                    sysSmsTemplateService.save(sysTemplate);
                } catch (Exception ignored) {
                }
            }

        }
        return ReturnResult.of(templateResultReturnResult);
    }

    @Override
    public IPage<SysSmsTemplate> pageFor(SmsPageV1Request request) {
        if(null == request.getSysProjectId()) {
            return new Page<>();
        }
        return sysSmsTemplateMapper.pageFor(new Page<>(request.getPage(), request.getPageSize()), request);
    }

    @Override
    public ReturnResult<Boolean> delete(SysSmsTemplate sysSmsTemplate) {
        return ReturnResult.of(
                sysSmsTemplateMapper.deleteById(sysSmsTemplate)
        );
    }

    @Override
    public ReturnResult<Boolean> update(SysSmsTemplate sysSmsTemplate) {
        sysSmsTemplate.setSysProjectType(null);
        sysSmsTemplate.setSysProjectId(null);
        return ReturnResult.of(sysSmsTemplateMapper.updateById(sysSmsTemplate) > 0);
    }

    @Override
    public ReturnResult<SysSmsTemplate> save(SysSmsTemplate sysSmsTemplate) {
        sysSmsTemplateMapper.insert(sysSmsTemplate);
        return ReturnResult.of(sysSmsTemplate);
    }

    @Override
    public IPage<SysSmsLog> pageForLog(SmsPageV1Request request) {
        Integer sysProjectId = request.getSysProjectId();
        if (null == sysProjectId) {
            return new Page<>();
        }

        return sysSmsLogService.page(
                new Page<>(request.getPage(), request.getPageSize()),
                new LambdaQueryWrapper<SysSmsLog>()
                        .eq(SysSmsLog::getSysProjectId, sysProjectId)
                        .orderByDesc(SysSmsLog::getCreateTime)
        );
    }
}
