package com.chua.starter.eduai.model.vo;

import lombok.Data;

/**
 * 采集页元数据出参。
 *
 * <p>倒计时用的"下次触发时刻"是服务端算出的绝对时间, 客户端时钟一旦有偏差就会显示成
 * "还有 -3 秒"; 这里回一次服务端时刻, 前端据此换算偏移量再走本地秒表。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class CollectMetaVO {

    /**
     * 服务器当前时刻 (毫秒时间戳)
     */
    private Long serverTime;

    /**
     * 未填 cron 时使用的默认调度周期
     */
    private String defaultCron;
}
