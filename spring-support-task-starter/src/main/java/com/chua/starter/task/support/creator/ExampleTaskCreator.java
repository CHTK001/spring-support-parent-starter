package com.chua.starter.task.support.creator;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.annotations.SpiOption;
import com.chua.starter.task.support.pojo.SysTask;

/**
 * 例子
 *
 * @author CH
 */
@Spi("example")
@SpiOption("测试例子")
public class ExampleTaskCreator extends AbstractSimpleTaskCreator<String> {

    @Override
    public void execute(SysTask task) {
        update(task);
    }
}
