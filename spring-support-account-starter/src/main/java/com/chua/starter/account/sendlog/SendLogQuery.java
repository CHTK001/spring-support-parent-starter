package com.chua.starter.account.sendlog;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发送日志查询条件。
 *
 * @param kind      账号种类（EMAIL / SMS，可空）
 * @param success   发送结果（{@code true} 成功 / {@code false} 失败，可空表示全部）
 * @param accountId 发信账号 ID（可空）
 * @param source    调用来源（可空）
 * @param target    收件人模糊匹配（可空）
 * @param keyword   关键字（主题 / 正文 / 失败原因模糊匹配，可空）
 * @param startTime 发送时间下界（可空）
 * @param endTime   发送时间上界（可空）
 * @param page      页码（1 起）
 * @param pageSize  每页条数
 * @author CH
 * @since 4.0.0.59
 */
public record SendLogQuery(String kind,
                           Boolean success,
                           String accountId,
                           String source,
                           String target,
                           String keyword,
                           LocalDateTime startTime,
                           LocalDateTime endTime,
                           Integer page,
                           Integer pageSize) {

    /**
     * 无参构造：全部条件取默认值（分页由 {@link SendLogService} 兜底）。
     */
    public SendLogQuery() {
        this(null, null, null, null, null, null, null, null, null, null);
    }
}
