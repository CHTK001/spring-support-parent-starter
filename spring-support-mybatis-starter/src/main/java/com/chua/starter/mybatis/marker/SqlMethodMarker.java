package com.chua.starter.mybatis.marker;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import org.springframework.beans.factory.DisposableBean;

import java.util.List;

/**
 * 方法构建器
 *
 * @author CH
 */
public interface SqlMethodMarker extends DisposableBean {

    /**
     * 解析器
     *
     * @param source 配置
     * @return 解析器
     */
    List<AbstractMethod> analysis(String source);

    /**
     * 刷新mapper
     *
     * @param name name
     */
    void refresh(String name);
}
