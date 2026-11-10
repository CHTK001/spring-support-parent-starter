package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DeviceWebhookAppKey;
import com.chua.starter.device.support.model.AccessRecordPush;
import com.chua.starter.device.support.model.AlarmPush;
import com.chua.starter.device.support.model.AppKeyCreateRequest;
import com.chua.starter.device.support.model.AppKeyView;
import com.chua.starter.device.support.model.WebhookPushResult;

import java.util.List;

/**
 * 设备 webhook 服务：appKey 生成与校验、门禁记录 / 告警事件推送入库。
 *
 * @since 4.0.0.51
 * @author CH
 */
public interface DeviceWebhookService {

    /**
     * 生成一套 appKey；明文 secret 只在返回值里出现一次。
     *
     * @param request 创建参数
     * @return 含明文 secret 的视图
     */
    AppKeyView createAppKey(AppKeyCreateRequest request);

    /**
     * 列出全部 appKey（不含 secret）。
     *
     * @return 视图列表
     */
    List<AppKeyView> listAppKeys();

    /**
     * 停用一套 appKey。
     *
     * @param id 主键
     */
    void disableAppKey(String id);

    /**
     * 删除一套 appKey。
     *
     * @param id 主键
     */
    void deleteAppKey(String id);

    /**
     * 校验入站请求凭据与主题权限。
     *
     * <p>优先走 HMAC 签名（X-Timestamp / X-Nonce / X-Signature）；没有签名时
     * 回落到 X-App-Secret 明文校验，便于内网联调。</p>
     *
     * @param appKey    X-App-Key
     * @param secret    X-App-Secret，可空
     * @param timestamp X-Timestamp（秒），可空
     * @param nonce     X-Nonce，可空
     * @param signature X-Signature（HMAC-SHA256 十六进制），可空
     * @param rawBody   原始包体，签名模式下参与计算
     * @param topic     主题：access / alarm
     * @return 校验通过的凭据
     */
    DeviceWebhookAppKey verify(String appKey, String secret, String timestamp, String nonce,
                               String signature, String rawBody, String topic);

    /**
     * 门禁通行记录推送入库（幂等）。
     *
     * @param source  来源（appKey 名称）
     * @param rawBody 原始包体，用于生成缺省幂等键
     * @param items   推送条目
     * @return 入库结果
     */
    WebhookPushResult ingestAccess(String source, String rawBody, List<AccessRecordPush> items);

    /**
     * 告警事件推送入库（幂等），并向生命周期回调广播 ALARM_RECEIVED。
     *
     * @param source  来源（appKey 名称）
     * @param rawBody 原始包体，用于生成缺省幂等键
     * @param items   推送条目
     * @return 入库结果
     */
    WebhookPushResult ingestAlarms(String source, String rawBody, List<AlarmPush> items);
}
