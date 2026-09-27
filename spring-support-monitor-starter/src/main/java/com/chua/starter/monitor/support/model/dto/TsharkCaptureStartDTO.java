package com.chua.starter.monitor.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 启动抓包会话请求
 *
 * @param name            会话名称，为空时由服务端按网卡与时间生成
 * @param captureInterface 抓包网卡名
 * @param captureFilter   BPF 过滤器，可为空
 * @param count           抓取报文数上限，为空或非正数表示不限
 * @param timeout         抓包时长上限（秒），为空或非正数表示不限
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TsharkCaptureStartDTO(
        String name,
        String captureInterface,
        String captureFilter,
        Integer count,
        Integer timeout
) {
}
