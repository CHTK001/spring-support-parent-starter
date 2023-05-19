package com.chua.starter.mybatis.reloader;

import org.springframework.beans.factory.InitializingBean;

/**
 * 加载器
 *
 * @author CH
 */
public interface Reload extends InitializingBean {

    /**
     * 重載
     *
     * @param mapperXml xml
     * @return 重載
     */
    String reload(String mapperXml);
}
