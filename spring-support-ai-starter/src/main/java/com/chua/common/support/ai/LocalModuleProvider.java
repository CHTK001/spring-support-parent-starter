package com.chua.common.support.ai;

import java.util.List;

/**
 * 本地模块提供器接口，由实现方暴露本地可用的 AI 模块列表。
 *
 * @author CH
 * @since 4.0.0
 */
public interface LocalModuleProvider {

    /**
     * @return 本地模块定义列表
     */
    List<LocalModuleDefinition> getModules();
}
