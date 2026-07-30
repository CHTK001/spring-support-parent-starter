package com.chua.starter.project.service.impl;

import com.chua.extension.support.lang.mail.Email;
import com.chua.extension.support.lang.mail.MailSender;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.extension.support.lang.mail.MailConfiguration;
import com.chua.common.support.lang.message.MessageCode;
import com.chua.common.support.lang.message.result.PushResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.application.GlobalSettingFactory;
import com.chua.starter.project.pojo.setting.SmtpSetting;
import com.chua.starter.project.service.EmailService;
import com.chua.starter.entity.project.SysEmailTemplate;
import com.chua.starter.entity.project.SysProject;
import com.chua.starter.project.pojo.email.EmailRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * email接口实现
 * @author CH
 * @since 2024/9/4
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {


    @Override
    public ReturnResult<Boolean> send(Email email) {
        if(StringUtils.isBlank(email.getTo())) {
            return ReturnResult.fail("收件人不能为空");
        }
        SmtpSetting smtpSetting = GlobalSettingFactory.getInstance().get("smtp", SmtpSetting.class);
        if(StringUtils.isEmpty(smtpSetting.getFrom())) {
            return ReturnResult.fail("邮件设置 - 发送账号未设置");
        }
        
        MailConfiguration mailConfiguration = new MailConfiguration();
        mailConfiguration.setSmtpHost(smtpSetting.getHost());
        mailConfiguration.setPassword(smtpSetting.getPassword());
        mailConfiguration.setSmtpPort(smtpSetting.getPort());
        MailSender mailSender = ServiceProvider.of(MailSender.class).getNewExtension("html", mailConfiguration);
        
        if(null == mailSender) {
            return ReturnResult.fail("暂不支持邮件发送/邮件设置未设置");
        }
        try {
            mailSender.send(smtpSetting.getFrom(), email);
            return ReturnResult.success(true);
        } catch (Exception e) {
            log.error("邮件发送失败", e);
            String message = e.getMessage();
            if(message.contains("User not found")) {
                return ReturnResult.illegal("收件人邮箱不存在");
            }
            if(message.contains("553") || message.contains("Authentication failed")) {
                return ReturnResult.noAuth("邮箱验证失败，请检查邮箱设置");
            }
            return ReturnResult.fail("发送失败!");
        }
    }

    @Override
    public ReturnResult<List<PushResult>> send(String sysTemplateCode,
                                               SysProject sysProject,
                                               SysEmailTemplate emailTemplate,
                                               EmailRequest request,
                                               Integer traceId) {
        MailConfiguration mailConfiguration = new MailConfiguration();
        mailConfiguration.setSmtpHost(sysProject.getSysProjectSmtpHost());
        mailConfiguration.setPassword(sysProject.getSysProjectSmtpPassword());
        mailConfiguration.setSmtpPort(sysProject.getSysProjectSmtpPort());
        MailSender mailSender = ServiceProvider.of(MailSender.class).getNewExtension("html",mailConfiguration);
        String[] target = request.getTarget();
        List<PushResult> rs = new ArrayList<>(target.length);
        for (String s : target) {
            try {
                mailSender.send(sysProject.getSysProjectSmtpFrom(), Email.builder()
                        .contentType("text/html")
                        .htmlMailCallbackUrl(emailTemplate.getSysEmailTemplateCallbackUrl())
                        .content(emailTemplate.getSysEmailTemplateContent())
                        .templateId(emailTemplate.getSysEmailTemplateId() + "")
                        .traceId(traceId + "")
                        .title(emailTemplate.getSysEmailTemplateName())
                        .maps(request.getParams())
                        .to(s)
                        .build());

                rs.add(PushResult.builder().code(
                        MessageCode.SUCCESS
                ).build());
            } catch (Exception e) {
                rs.add(PushResult.builder().code(
                        MessageCode.FAILURE
                ).message(e.getMessage()).build());
            }
        }
        return ReturnResult.of(rs);
    }
}
