// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.software.support.realtime;

import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.common.support.text.json.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于宿主 Socket 会话模板的安装进度发布器
 *
 * <p>走<b>全局 socket 通道</b>：统一以 Socket.IO 的 {@code message} 事件广播，
 * 载荷对齐前端 {@code WsMessage}：{@code {module, event, data, dataId, timestamp}}，
 * 前端 {@code socket.subscribe("software", "install-progress", handler)} 即可收到。</p>
 *
 * <p><b>为什么广播而不是只推发起人</b>：安装是这台机器上正在发生的事，同一控制台里
 * 多人同时看着列表时都该看到进度条在动；需要按人过滤时前端用 {@code data.userId} 自己筛，
 * 后端不必为每个观察者维护一份任务。<b>同时</b>向发起用户定向投递一份
 * （{@link SocketSessionTemplate#sendToUser}），避免某些部署里房间/会话未能进入广播集合
 * 时，发起人反而收不到自己的任务。</p>
 *
 * <p><b>代价是发起人会收到两份相同载荷</b>（他既在广播受众里，又在定向投递的目标里）。
 * 这里刻意不在后端去重：后端无从知道某条会话是否真的落在广播集合里，一旦猜错就会
 * 把兜底通道也削掉，反而回到「发起人收不到」的老问题。载荷是同一个序列化字符串，
 * 内容完全一致，去重交给前端按内容指纹做即可（见前端
 * {@code pages/server/src/utils/installProgress.ts} 的 {@code frameFingerprint}）。</p>
 *
 * <p>用 {@link ObjectProvider} 延迟解析会话模板：宿主未启用 socket 时自动退化为空操作，
 * 不会因为缺少 Bean 导致启动失败。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class SocketInstallProgressPublisher implements InstallProgressPublisher {

    /**
     * 前端全局 socket 监听的传输事件名
     */
    private static final String TRANSPORT_EVENT = "message";

    /**
     * 前端订阅用的模块标识
     */
    public static final String MODULE = "software";

    /**
     * 前端订阅用的安装进度事件名
     */
    public static final String EVENT = "install-progress";

    /**
     * 会话模板提供者（延迟解析）
     */
    private final ObjectProvider<SocketSessionTemplate> templateProvider;

    /**
     * 创建发布器
     *
     * @param templateProvider 会话模板提供者
     */
    public SocketInstallProgressPublisher(ObjectProvider<SocketSessionTemplate> templateProvider) {
        this.templateProvider = templateProvider;
    }

    @Override
    public void publish(InstallProgressEvent event) {
        if (event == null) {
            return;
        }
        SocketSessionTemplate template = templateProvider.getIfAvailable();
        if (template == null) {
            log.debug("[Software][Progress] 宿主未启用 socket，跳过进度: recordId={}", event.recordId());
            return;
        }
        String payload;
        try {
            payload = Json.toJson(toMessage(event));
        } catch (Exception e) {
            log.warn("[Software][Progress] 进度序列化失败: recordId={}, reason={}",
                    event.recordId(), e.getMessage());
            return;
        }
        // 广播 + 定向各一次：任一失败都不能影响另一条通道，更不能影响安装主流程
        try {
            template.broadcast(TRANSPORT_EVENT, payload);
        } catch (Exception e) {
            log.debug("[Software][Progress] 进度广播失败: recordId={}, reason={}",
                    event.recordId(), e.getMessage());
        }
        if (event.userId() != null && !event.userId().isBlank()) {
            try {
                template.sendToUser(event.userId(), TRANSPORT_EVENT, payload);
            } catch (Exception e) {
                log.debug("[Software][Progress] 进度定向推送失败: recordId={}, userId={}, reason={}",
                        event.recordId(), event.userId(), e.getMessage());
            }
        }
    }

    /**
     * 组装前端可消费的消息体
     *
     * <p>{@code data} 先按统一抽象 {@link com.chua.socket.support.MsgStep} 铺开通用进度字段
     * （eventId / dataId / message / step / total / percentage / status / timestamp），
     * 再把安装自身的上下文（软件、发起人、设备、操作、是否终态）并列进去。
     * 这样前端一套进度组件就能同时消费安装、项目发布、证书申请等所有长任务，
     * 不必为安装单独写一套解析。</p>
     *
     * @param event 进度事件
     * @return 包含 module / event / data / dataId / timestamp 的消息映射
     */
    Map<String, Object> toMessage(InstallProgressEvent event) {
        com.chua.socket.support.MsgStep step = event.toMsgStep();

        Map<String, Object> data = new LinkedHashMap<>(16);
        data.put("eventId", step.getEventId());
        data.put("dataId", step.getDataId());
        data.put("message", step.getMessage());
        data.put("step", step.getStep());
        data.put("total", step.getTotal());
        data.put("percentage", step.getPercentage());
        data.put("status", step.getStatus());
        data.put("timestamp", step.getTimestamp());
        // 安装特有上下文
        data.put("statusText", event.statusText());
        data.put("recordId", event.recordId());
        data.put("softwareId", event.softwareId());
        data.put("userId", event.userId());
        data.put("deviceId", event.deviceId());
        data.put("operation", event.operation());
        data.put("statusCode", event.status());
        data.put("progress", event.progress());
        data.put("finished", event.finished());

        Map<String, Object> message = new LinkedHashMap<>(6);
        message.put("module", MODULE);
        message.put("event", EVENT);
        message.put("data", data);
        message.put("dataId", event.recordId());
        message.put("timestamp", event.timestamp());
        return message;
    }
}