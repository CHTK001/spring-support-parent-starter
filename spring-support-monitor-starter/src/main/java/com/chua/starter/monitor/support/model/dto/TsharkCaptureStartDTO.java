package com.chua.starter.monitor.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 启动抓包会话请求
 *
 * @param name            会话名称，为空时由服务端按网卡与时间生成
 * @param captureInterface 抓包网卡名
 * @param captureInterfaces 抓包网卡组（多网卡同时抓包；为空时回退到 captureInterface）
 * @param captureFilter 过滤器文本，可为空
 * @param filterType     过滤语法：auto（默认，按文本自动判断）/ bpf（捕获过滤）/ display（显示过滤，实时抓包会自动转成等价 BPF）
 * @param count           抓取报文数上限，为空或非正数表示不限
 * @param timeout         抓包时长上限（秒），为空或非正数表示不限
 * @param maxSizeMb       会话文件大小上限（MB），为空或非正数表示不限；达到后 tshark 自动停止
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TsharkCaptureStartDTO(
        String name,
        String captureInterface,
        List<String> captureInterfaces,
        String captureFilter,
        String filterType,
        Integer count,
        Integer timeout,
        Integer maxSizeMb
) {

    /**
     * 实际要抓的网卡列表。
     *
     * @return 网卡列表
     */
    public List<String> interfaces() {
        if (captureInterfaces != null && !captureInterfaces.isEmpty()) {
            return captureInterfaces;
        }
        if (captureInterface == null || captureInterface.isBlank()) {
            return List.of();
        }
        return List.of(captureInterface);
    }
}
