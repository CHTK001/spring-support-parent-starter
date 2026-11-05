// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.software.support.realtime;

import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.common.support.text.json.Json;
import com.chua.starter.software.support.entity.SoftInstallRecord;
import com.chua.starter.software.support.enums.InstallStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InstallProgressEvent} 与 {@link SocketInstallProgressPublisher} 单元测试
 *
 * <p>只用 JDK 动态代理 + 真实 {@link DefaultListableBeanFactory}：本仓禁用 mockito；
 * 而「宿主未启用 socket」这一分支本质就是「容器里没有该 Bean」，用真实 BeanFactory
 * 比手写一个假的 ObjectProvider 更贴近线上行为。</p>
 *
 * <p>这两个类不依赖 Spring 容器启动、数据库或真实 socket 服务，纯粹是
 * 「安装记录 → 进度事件 → 前端消息体」的数据变换，桩即可把边界钉住。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
class InstallProgressEventTest {

    /**
     * 记录为空属于调用方编程错误，必须立刻暴露而不是静默返回一条空事件
     */
    @Test
    void ofShouldRejectNullRecord() {
        assertThrows(IllegalArgumentException.class, () -> InstallProgressEvent.of(null, "x"));
    }

    /**
     * 进度越界要收敛：库里是整型列，历史数据或并发更新可能写进 -5 / 150，
     * 直接透给前端会让进度条溢出或倒退
     */
    @Test
    void progressShouldBeNormalized() {
        assertEquals(0, InstallProgressEvent.of(record(InstallStatus.INSTALLING, -5), null).progress());
        assertEquals(100, InstallProgressEvent.of(record(InstallStatus.INSTALLING, 150), null).progress());
        assertEquals(0, InstallProgressEvent.of(record(InstallStatus.INSTALLING, null), null).progress());
        assertEquals(42, InstallProgressEvent.of(record(InstallStatus.INSTALLING, 42), null).progress());
    }

    /**
     * 只有终态才算 finished，前端据此收起进度浮层
     */
    @Test
    void finishedShouldCoverOnlyTerminalStatuses() {
        assertFalse(InstallProgressEvent.of(record(InstallStatus.INSTALLING, 30), null).finished());
        assertFalse(InstallProgressEvent.of(record(InstallStatus.UPDATING, 50), null).finished());
        assertFalse(InstallProgressEvent.of(record(InstallStatus.UNINSTALLING, 50), null).finished());
        assertFalse(InstallProgressEvent.of(record(InstallStatus.NOT_INSTALLED, 0), null).finished());
        assertTrue(InstallProgressEvent.of(record(InstallStatus.INSTALLED, 100), null).finished());
        assertTrue(InstallProgressEvent.of(record(InstallStatus.FAILED, 30), null).finished());
        assertTrue(InstallProgressEvent.of(record(InstallStatus.UNINSTALLED, 100), null).finished());
        assertTrue(InstallProgressEvent.of(record(InstallStatus.UPDATE_FAILED, 50), null).finished());
    }

    /**
     * 未知状态码（历史脏数据）不能抛异常，中文名退化为空串由前端兜底显示
     */
    @Test
    void unknownStatusShouldNotThrow() {
        assertEquals("", InstallProgressEvent.statusText(99));
        assertEquals("", InstallProgressEvent.statusText(-1));
        assertEquals("安装中", InstallProgressEvent.statusText(InstallStatus.INSTALLING.getValue()));

        SoftInstallRecord dirty = record(InstallStatus.INSTALLING, 10);
        dirty.setInstallRecordStatus(99);
        InstallProgressEvent event = InstallProgressEvent.of(dirty, null);
        assertEquals(99, event.status());
        assertEquals("", event.statusText());
    }

    /**
     * 消息体对齐前端 WsMessage：module / event / data / dataId / timestamp
     */
    @Test
    void messageShouldAlignFrontendContract() {
        SocketInstallProgressPublisher publisher = new SocketInstallProgressPublisher(provider(null));
        Map<String, Object> message = publisher.toMessage(
                InstallProgressEvent.of(record(InstallStatus.INSTALLING, 30), "准备安装"));

        assertEquals(SocketInstallProgressPublisher.MODULE, message.get("module"));
        assertEquals(SocketInstallProgressPublisher.EVENT, message.get("event"));
        assertEquals("record-1", message.get("dataId"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) message.get("data");
        assertEquals("record-1", data.get("recordId"));
        assertEquals("software-1", data.get("softwareId"));
        assertEquals("user-1", data.get("userId"));
        assertEquals("device-1", data.get("deviceId"));
        assertEquals("install", data.get("operation"));
        // 通用进度字段（MsgStep 语义）：百分比与状态文本
        assertEquals(30, data.get("percentage"));
        assertEquals(30, data.get("step"));
        assertEquals(100, data.get("total"));
        assertEquals("安装中", data.get("status"));
        assertEquals("准备安装", data.get("message"));
        assertEquals("record-1", data.get("dataId"));
        assertEquals(SocketInstallProgressPublisher.EVENT, data.get("eventId"));
        // 安装特有字段：机器可读的状态码与是否终态
        assertEquals(InstallStatus.INSTALLING.getValue(), data.get("statusCode"));
        assertEquals("安装中", data.get("statusText"));
        assertEquals(30, data.get("progress"));
        assertEquals(Boolean.FALSE, data.get("finished"));
    }

    /**
     * 宿主未启用 socket（容器里没有该 Bean）时必须静默跳过，不能抛异常打断安装
     */    @Test
    void missingTemplateShouldSkipSilently() {
        SocketInstallProgressPublisher publisher = new SocketInstallProgressPublisher(provider(null));
        publisher.publish(InstallProgressEvent.of(record(InstallStatus.INSTALLING, 30), "准备安装"));
    }

    /**
     * 有模板时：先广播（让同屏其他观察者也看到），再向发起用户定向投递一份
     */
    @Test
    void publishShouldBroadcastAndSendToUser() {
        Recorder recorder = new Recorder();
        SocketInstallProgressPublisher publisher = new SocketInstallProgressPublisher(provider(recorder.proxy()));

        publisher.publish(InstallProgressEvent.of(record(InstallStatus.INSTALLING, 30), "准备安装"));

        assertEquals(List.of("broadcast", "sendToUser"), recorder.methods);
        Object[] broadcastArgs = recorder.invocations.get(0);
        assertEquals("message", broadcastArgs[0]);
        Object[] userArgs = recorder.invocations.get(1);
        assertEquals("user-1", userArgs[0]);
        assertEquals("message", userArgs[1]);

        Map<String, Object> parsed = Json.getJsonObject((String) broadcastArgs[1]);
        assertEquals(SocketInstallProgressPublisher.MODULE, parsed.get("module"));
        assertEquals(SocketInstallProgressPublisher.EVENT, parsed.get("event"));
        // 两条通道发的是同一份载荷，前端两个入口看到的进度必须一致
        assertEquals((String) broadcastArgs[1], (String) userArgs[2]);
    }

    /**
     * 广播失败不能连累定向投递：两个通道是独立的，一条断了另一条还得送达
     */
    @Test
    void broadcastFailureShouldNotBlockUserDelivery() {
        Recorder recorder = new Recorder();
        recorder.broadcastFailure = new IllegalStateException("boom");
        SocketInstallProgressPublisher publisher = new SocketInstallProgressPublisher(provider(recorder.proxy()));

        publisher.publish(InstallProgressEvent.of(record(InstallStatus.INSTALLING, 30), "准备安装"));

        assertEquals(List.of("broadcast", "sendToUser"), recorder.methods);
    }

    /**
     * 无用户标识（如系统级后台安装）时不做定向投递，避免按空 userId 到处扫描会话
     */
    @Test
    void blankUserIdShouldSkipUserDelivery() {
        Recorder recorder = new Recorder();
        SocketInstallProgressPublisher publisher = new SocketInstallProgressPublisher(provider(recorder.proxy()));

        SoftInstallRecord noUser = record(InstallStatus.INSTALLING, 30);
        noUser.setInstallRecordUserId("  ");
        publisher.publish(InstallProgressEvent.of(noUser, "准备安装"));

        assertEquals(List.of("broadcast"), recorder.methods);
    }

    /**
     * 用真实 BeanFactory 提供 ObjectProvider：未注册 Bean 时 getIfAvailable() 返回 null，
     * 这正是宿主没引 socket starter 的等价情形
     *
     * @param template 要注册的模板，为 null 则不注册
     * @return 会话模板提供者
     */
    private static ObjectProvider<SocketSessionTemplate> provider(SocketSessionTemplate template) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        if (template != null) {
            beanFactory.registerSingleton("socketSessionTemplate", template);
        }
        return beanFactory.getBeanProvider(SocketSessionTemplate.class);
    }

    /**
     * 构造一条安装记录
     *
     * @param status   状态
     * @param progress 进度，可为 null
     * @return 安装记录
     */
    private static SoftInstallRecord record(InstallStatus status, Integer progress) {
        SoftInstallRecord record = new SoftInstallRecord();
        record.setInstallRecordId("record-1");
        record.setInstallRecordSoftwareId("software-1");
        record.setInstallRecordUserId("user-1");
        record.setInstallRecordDeviceId("device-1");
        record.setInstallRecordOperation("install");
        record.setInstallRecordStatus(status.getValue());
        record.setInstallRecordProgress(progress);
        return record;
    }

    /**
     * 会话模板记录器
     *
     * <p>用 JDK 动态代理而不是手写实现：{@link SocketSessionTemplate} 有十几个方法，
     * 只有 broadcast / sendToUser 两个与本次行为相关，逐一手写实现会把无关样板淹没断言。</p>
     */
    private static final class Recorder implements InvocationHandler {

        /**
         * 调用顺序的方法名
         */
        private final List<String> methods = new ArrayList<>();

        /**
         * 每次调用除代理自身外的实参
         */
        private final List<Object[]> invocations = new ArrayList<>();

        /**
         * 广播时抛出的异常，非空则模拟广播通道故障
         */
        private RuntimeException broadcastFailure;

        /**
         * 创建代理实例
         *
         * @return 会话模板
         */
        private SocketSessionTemplate proxy() {
            return (SocketSessionTemplate) Proxy.newProxyInstance(
                    SocketSessionTemplate.class.getClassLoader(),
                    new Class<?>[]{SocketSessionTemplate.class},
                    this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            if ("toString".equals(name)) {
                return "RecordingSocketSessionTemplate";
            }
            methods.add(name);
            invocations.add(args == null ? new Object[0] : args);
            if ("broadcast".equals(name) && broadcastFailure != null) {
                throw broadcastFailure;
            }
            return null;
        }
    }
}