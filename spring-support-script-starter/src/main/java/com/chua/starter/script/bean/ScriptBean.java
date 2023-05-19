package com.chua.starter.script.bean;

import org.springframework.beans.factory.FactoryBean;

/**
 * @author CH
 */
public interface ScriptBean extends FactoryBean {

    /**
     * 刷新
     *
     * @param source 源码
     */
    void refresh(String source);
}
