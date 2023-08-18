package com.chua.starter.config.server.support.uniform;

import java.util.List;
import java.util.Map;

/**
 * 统一检测中心
 * @author CH
 */
public interface Uniform {

    /**
     * 启动
     */
    void start();

    /**
     * 停止
     */
    void stop();

    List<Map<String, Object>> search(String keyword);
}
