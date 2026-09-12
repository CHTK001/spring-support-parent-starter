package com.chua.starter.server.support.service;

import com.chua.common.support.lang.code.ReturnResult;

import java.util.List;
import java.util.Map;

/**
 * 邮箱账号(MAILBOX 身份)读取服务
 * <p>基于 {@code utils-support-email-starter} 的 EmailClient，通过 IMAP 读取邮箱收件箱。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface MailboxService {

    /**
     * 读取邮箱身份对应的收件箱邮件列表（不含正文全文，供列表展示）。
     *
     * @param identityId 邮箱身份 标识
     * @param folder     文件夹（默认 INBOX）
     * @param limit      最大条数（默认 20）
     * @return 邮件列表；字段 主题/从/日期/大小/内容(截断)
     */
    ReturnResult<List<Map<String, Object>>> list(Long identityId, String folder, int limit);

    /**
      * 读取单封邮件正文全文（可选按 主题 或序号定位；此处返回最新 限制 内的首条 detail 供查看）。
     *
     * @param identityId 邮箱身份 标识
     * @param folder     文件夹（默认 INBOX）
     * @param subject    邮件主题关键字（按包含匹配定位）
     * @return 邮件详情（含完整 内容）
     */
    ReturnResult<Map<String, Object>> detail(Long identityId, String folder, String subject);
}