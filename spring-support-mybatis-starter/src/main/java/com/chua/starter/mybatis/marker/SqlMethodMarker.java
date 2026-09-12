package com.chua.starter.mybatis.marker;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import org.springframework.beans.factory.DisposableBean;

import java.util.List;

/**
 * 方法构建器
 *
 * @author CH
 * @since 4.0.0
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
      * 刷新映射器
     *
     * @param name 名称
     */
    void refresh(String name);

    /**
     * 全部刷新
     */
    void refreshAll();
}
