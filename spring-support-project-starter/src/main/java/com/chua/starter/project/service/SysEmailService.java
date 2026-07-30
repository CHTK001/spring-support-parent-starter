package com.chua.starter.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.message.result.PushResult;
import com.chua.starter.entity.project.SysEmailLog;
import com.chua.starter.entity.project.SysEmailTemplate;
import com.chua.starter.project.pojo.email.EmailPageV1Request;
import com.chua.starter.project.pojo.email.EmailRequest;
import com.chua.starter.project.pojo.email.EmailSyncV1Request;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * 邮件服务
 * @author CH
 * @since 2024/9/6
 */
public interface SysEmailService {
    /**
     * 发送邮件
     * @param request 请求
     * @return result
     */
    ReturnResult<List<PushResult>> send(EmailRequest request);

    /**
     * 同步邮件模板
     * @param request 邮件配置
     * @return result
     */
    ReturnResult<Boolean> syncTemplate(EmailSyncV1Request request);

    /**
     * 分页
     * @param request 请求
     * @return result
     */
    IPage<SysEmailTemplate> pageFor(EmailPageV1Request request);

    /**
     * 删除
     * @param sysEmailTemplate 邮件模板
     * @return result
     */
    ReturnResult<Boolean> delete(SysEmailTemplate sysEmailTemplate);

    /**
     * 更新
     * @param sysEmailTemplate 邮件模板
     * @return result
     */
    ReturnResult<Boolean> update(SysEmailTemplate sysEmailTemplate);

    /**
     * 新增
     * @param sysEmailTemplate 邮件模板
     * @return result
     */
    ReturnResult<SysEmailTemplate> save(SysEmailTemplate sysEmailTemplate);

    /**
     * 邮件阅读回调
     *
     * @param target  目标
     * @param traceId 轨迹
     * @return result
     */
    ResponseEntity<byte[]> callback(String target, String traceId);

    /**
     * 邮件日志分页
     *
     * @param request 请求
     * @return result
     */
    IPage<SysEmailLog> pageForLog(EmailPageV1Request request);
}
