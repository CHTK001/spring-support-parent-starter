package com.chua.starter.proxy.support.handler;

import com.chua.common.support.objects.ConfigureObjectContext;
import com.chua.common.support.network.protocol.filter.ServletFilter;
import com.chua.starter.proxy.support.entity.SystemServerSetting;

/**
 * ServletFilter处理器
 *
 * @author CH
 * @since 2025/8/11 17:02
 */
public interface ServletFilterHandler {


    /**
     * 创建
     *
     * @param setting       设置
     * @param objectContext
     * @return ServletFilter
     */
    ServletFilter handle(SystemServerSetting setting, ConfigureObjectContext objectContext);

    /**
     * 更新
     *
     * @param filter  过滤器
     * @param setting 设置
     */
    void update(ServletFilter filter, SystemServerSetting setting);
}




