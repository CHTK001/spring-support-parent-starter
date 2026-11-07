// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.common.support.http;

import com.chua.starter.common.support.channel.ChannelException;

/**
 * 渠道 HTTP 调用失败。
 *
 * <p>继承 {@link ChannelException}：上游 HTTP 失败天然是一种渠道失败，
 * 消费方（AI 模块）在自己的边界上统一把它翻译成模块内的契约异常即可；
 * 账号模块内部也可以直接按 {@link ChannelException} 捕获，不必知道 HTTP 细节。</p>
 *
 * <p>本类不再自带错误码字段：{@link #getCode()} 与 {@link #getHttpStatus()}
 * 来自父类，取值口径保持一致。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public class ChannelHttpException extends ChannelException {

    /**
     * 构造渠道 HTTP 调用异常。
     *
     * @param code       机器可读错误码
     * @param httpStatus 上游 HTTP 状态码，拿不到响应时为 0
     * @param message    人类可读信息
     * @param cause      根因，可为 null
     */
    public ChannelHttpException(String code, int httpStatus, String message, Throwable cause) {
        super(code, httpStatus, message, cause);
    }
}
