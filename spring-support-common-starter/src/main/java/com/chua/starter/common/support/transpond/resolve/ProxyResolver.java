package com.chua.starter.common.support.transpond.resolve;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;

/**
 * 代理解释器
 *
 * @author CH
 */
public interface ProxyResolver extends FactoryBean, DisposableBean {
    /**
     * 获取名称
     *
     * @return 名称
     */
    String getName();
}
