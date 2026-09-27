package com.chua.starter.server.support.spi;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.starter.server.support.model.vo.TerminalOnlineVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 远控会话通道注册中心
 * <p>
 * 汇聚 {@link TerminalSessionChannel} 的全部实现，向管理端与定时任务提供统一的
 * "在线列表 / 强制关闭 / 空闲清理"三个动作，调用方不再区分 guacd 中转与自研直连。
 * 同一实现类的多个别名（扩展点登记名 + {@code @Spi} 值）只保留一个实例。
 * </p>
 *
 * <p>
 * 通道列表在首次使用时构建：SPI 实例的依赖由 Spring 装配链注入，
 * 容器就绪前构建会得到半初始化实例。
 * </p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Component
public class TerminalSessionChannelRegistry {

    /**
     * 远控会话通道扩展点
     */
    private final ServiceProvider<TerminalSessionChannel> extensions =
            ServiceProvider.of(TerminalSessionChannel.class);

    /**
     * 通道实现列表，首次使用时构建
     */
    private volatile List<TerminalSessionChannel> channels;

    /**
     * 汇总各通道的在线会话
     *
     * @return 在线会话列表
     */
    public List<TerminalOnlineVO> online() {
        List<TerminalOnlineVO> result = new ArrayList<>();
        for (TerminalSessionChannel channel : channels()) {
            result.addAll(channel.online());
        }
        return result;
    }

    /**
     * 按审计记录标识强制关闭会话，逐个通道尝试直至命中
     *
     * @param recordId 会话审计记录 标识
     * @param reason   关闭原因
     * @return true 表示某个通道持有该会话并已触发关闭
     */
    public boolean closeByRecordId(Long recordId, String reason) {
        for (TerminalSessionChannel channel : channels()) {
            if (channel.closeByRecordId(recordId, reason)) {
                log.info("[terminal] 强制关闭会话 recordId={} channel={}", recordId, channel.channel());
                return true;
            }
        }
        return false;
    }

    /**
     * 各通道空闲扫描次数求和
     *
     * @param idleMinutes 最大空闲分钟数
     * @return 本次关闭的会话总数
     */
    public int sweepIdle(int idleMinutes) {
        int closed = 0;
        for (TerminalSessionChannel channel : channels()) {
            closed += channel.sweepIdle(idleMinutes);
        }
        return closed;
    }

    /**
     * 取通道列表，必要时构建。
     *
     * @return 已注册的会话通道
     */
    private List<TerminalSessionChannel> channels() {
        List<TerminalSessionChannel> current = channels;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (channels == null) {
                channels = build();
            }
            return channels;
        }
    }

    /**
     * 遍历扩展点定义，按实现类去重。
     *
     * @return 通道实现列表
     */
    private List<TerminalSessionChannel> build() {
        List<TerminalSessionChannel> out = new ArrayList<>();
        Set<Class<?>> seen = new HashSet<>();
        for (ServiceDefinition definition : extensions.getPriorityServiceDefinitions()) {
            TerminalSessionChannel channel = definition.getObj(extensions.getServiceAutowire());
            if (channel == null || !seen.add(channel.getClass())) {
                continue;
            }
            out.add(channel);
            log.info("[terminal] 注册会话通道: channel={}, impl={}",
                    channel.channel(), channel.getClass().getSimpleName());
        }
        if (out.isEmpty()) {
            log.warn("[terminal] TerminalSessionChannel 扩展点无任何实现，远控会话不可管理");
        }
        return List.copyOf(out);
    }
}
