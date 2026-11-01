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
 * 不写入 {@link TunnelRecord} 对应的 JSON 文件，避免进程重启后把过期地址当成事实。</p>
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
}
