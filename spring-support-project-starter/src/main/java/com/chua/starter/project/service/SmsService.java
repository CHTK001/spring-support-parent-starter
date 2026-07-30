package com.chua.starter.project.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.message.config.PushSetting;
import com.chua.common.support.lang.message.result.PushResult;
import com.chua.common.support.lang.message.result.TemplateResult;
import com.chua.starter.entity.project.SysProject;
import com.chua.starter.project.pojo.sms.SmsRequest;

import java.util.List;

/**
 * 短信服务
 *
 * @author CH
 * @since 2024/9/6
 */
public interface SmsService {

    /**
     * 发送短信
     *
     * @param sysTemplateCode 短信模板
     * @param sysProject      项目
     * @param request         请求
     * @return 发送结果
     */
    ReturnResult<List<PushResult>> send(String sysTemplateCode, SysProject sysProject, SmsRequest request);

    /**
     * 同步短信模板
     *
     * @param vendorCode 厂商编码
     * @param pushSetting     推送设置
     * @return 发送结果
     */
    ReturnResult<TemplateResult> syncTemplate(String vendorCode, PushSetting pushSetting);
}
