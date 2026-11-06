package com.chua.starter.collect.engine.spi;

/**
 * 一条自定义函数的运行期视图。
 *
 * @param name       函数名，调用时用的标识符
 * @param script     闭包字面量源码
 * @param parameters 形参个数，仅用于界面与错误提示
 * @author CH
 * @since 4.0.0.42
 */
public record CollectFunctionDef(String name, String script, int parameters) {
}
