package com.chua.shell.support.shell.adaptor;

/**
 * 解析器
 *
 * @author CH
 */
public interface CommandAdaptor {

    /**
     * 解析
     *
     * @param file 文件
     * @return 结果
     */
    String handler(String file);
}
