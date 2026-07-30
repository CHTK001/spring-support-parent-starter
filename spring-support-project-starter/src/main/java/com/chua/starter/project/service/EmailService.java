package com.chua.starter.project.service;

import com.chua.extension.support.lang.mail.Email;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.message.result.PushResult;
import com.chua.starter.entity.project.SysEmailTemplate;
import com.chua.starter.entity.project.SysProject;
import com.chua.starter.project.pojo.email.EmailRequest;

import java.util.List;

/**
 * 邮件服务接口
 *
 * @author CH
 * @since 2024/9/4
 */
public interface EmailService {

    /**
     * 发送邮件
     *
     * @param email 邮件对象，包含邮件的详细信息，如收件人、主题、内容等。
     *              示例: Email.builder().to("example@example.com").subject("测试邮件").content("这是一封测试邮件").build()
     * @return ReturnResult<Boolean> 返回操作结果，true表示发送成功，false表示发送失败
     */
    ReturnResult<Boolean> send(Email email);

    /**
     * 根据模板发送邮件
     *
     * @param sysTemplateCode 模板编码，用于标识特定的邮件模板。
     *                        示例: "TEMPLATE_001"
     * @param sysProject      项目信息，包含项目的配置信息，如SMTP设置等。
     *                        示例: SysProject 对象，包含项目相关的配置信息
     * @param emailTemplate   邮件模板配置，包含邮件模板的具体内容和参数。
     *                        示例: SysEmailTemplate 对象，包含模板内容和相关配置
     * @param request         邮件请求参数，包含接收人、模板ID和邮件参数等信息。
     *                        示例: EmailRequest 对象，包含目标用户和动态参数
     * @param traceId         链路追踪ID，用于跟踪邮件发送过程中的日志和调试信息。
     *                        示例: 123456
     * @return ReturnResult<List < PushResult>> 返回操作结果列表，每个元素代表一次邮件发送的结果
     */
    ReturnResult<List<PushResult>> send(String sysTemplateCode, SysProject sysProject, SysEmailTemplate emailTemplate, EmailRequest request, Integer traceId);
}
