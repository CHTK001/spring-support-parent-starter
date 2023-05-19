package com.chua.tools

import com.chua.starter.server.script.TDemoInfo


/**
 * @author CH* @version 1.0.0* @since 2020/10/28
 */
class TDemoInfoImpl1 implements TDemoInfo {
    Integer id
    Date time
    String name
    String title
    String demoTitle
    String test
    private String uuid

    @Override
    String getUuid() {
        return "groovy文件执行方法[getUuid]:" + IdUtils.createUuid()
    }

    @Override
    String getId() {
        return "groovy文件执行方法[getId]: 12223"
    }
}