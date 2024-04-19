package com.chua.starter.common.support.mdc;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * MdcTaskDecorator 类用于装饰任务，提供额外的功能或修改任务的行为。
 * <p>
 * <p>
 * 该类目前为空，需要根据实际需求添加方法和实现。
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/04/19
 */
public class MdcTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        return ThreadMdcUtil.wrap(runnable, MDC.getCopyOfContextMap());
    }
}

