package com.chua.starter.common.support.tunnel;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * 映射记录 + 运行态的组合视图，接口返回给页面使用。
 *
 * <p>运行态（是否运行 / 公网地址 / 最近一次错误 / 启动时间）只存在于内存，
 * 不写入 {@link TunnelRecord} 对应的 JSON 文件，避免进程重启后把过期地址当成事实。
 * {@code owner} 例外：它是持久化的实例所有权标记，随记录透传给页面/接口。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class TunnelView extends TunnelRecord {

    /**
     * 当前是否已建立隧道。
     */
    private boolean running;

    /**
     * 公网访问地址（一个隧道可能返回多个）。
     */
    private List<String> publicUrls = new ArrayList<>();

    /**
     * 最近一次启动失败原因；成功后清空。
     */
    private String error;

    /**
     * 本次启动时间（毫秒时间戳），未运行时为 {@code null}。
     */
    private Long startedAt;

    /**
     * 当前运行该隧道的实例控制地址（继承自 {@link TunnelRecord#getOwner()}）。
     *
     * <p>为空表示本实例或没有任何实例在运行；非空且不等于本实例地址时，
     * 表示隧道正由另一实例运行，本实例启动时会自动接管。</p>
     */
    private String owner;
}
