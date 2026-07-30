package com.chua.starter.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.message.result.PushResult;
import com.chua.starter.entity.project.SysSmsLog;
import com.chua.starter.entity.project.SysSmsTemplate;
import com.chua.starter.project.pojo.sms.SmsPageV1Request;
import com.chua.starter.project.pojo.sms.SmsRequest;
import com.chua.starter.project.pojo.sms.SmsSyncV1Request;

import java.util.List;

/**
 * 短信服务
 * @author CH
 * @since 2024/9/6
 */
public interface SysSmsService {
    /**
     * 发送短信
     * @param request 请求
     * @return result
     */
    ReturnResult<List<PushResult>> send(SmsRequest request);

    /**
     * 同步短信模板
     * @param request 短信配置
     * @return result
     */
    ReturnResult<Boolean> syncTemplate(SmsSyncV1Request request);

    /**
     * 分页
     * @param request 请求
     * @return result
     */
    IPage<SysSmsTemplate> pageFor(SmsPageV1Request request);

    /**
     * 删除
     * @param sysSmsTemplate 短信模板
     * @return result
     */
    ReturnResult<Boolean> delete(SysSmsTemplate sysSmsTemplate);

    /**
     * 更新
     * @param sysSmsTemplate 短信模板
     * @return result
     */
    ReturnResult<Boolean> update(SysSmsTemplate sysSmsTemplate);

    /**
     * 新增
     * @param sysSmsTemplate 短信模板
     * @return result
     */
    ReturnResult<SysSmsTemplate> save(SysSmsTemplate sysSmsTemplate);

    /**
     * 短信日志分页
     *
     * @param request 请求
     * @return result
     */
    IPage<SysSmsLog> pageForLog(SmsPageV1Request request);
}
