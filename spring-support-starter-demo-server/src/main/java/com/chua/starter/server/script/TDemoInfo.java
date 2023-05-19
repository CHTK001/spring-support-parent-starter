package com.chua.starter.server.script;


/**
 * @author CH
 * @version 1.0.0
 * @since 2021/2/2
 */
public interface TDemoInfo {

    String getUuid() throws Exception;

    String getId();

    default String getId(int timeoutMs) {
        return "";
    }

}
