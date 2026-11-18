package com.chua.starter.account.sendlog;

import com.chua.starter.account.sendlog.store.SysAccountSendLog;

import java.util.List;

/**
 * 发送日志分页结果。
 *
 * @param records  当前页数据
 * @param total    总条数
 * @param page     当前页码
 * @param pageSize 每页条数
 * @author CH
 * @since 4.0.0.59
 */
public record SendLogPage(List<SysAccountSendLog> records, long total, int page, int pageSize) {
}
