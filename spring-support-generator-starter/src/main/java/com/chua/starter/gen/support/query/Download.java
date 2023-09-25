package com.chua.starter.gen.support.query;

import lombok.Data;

/**
 * @author CH
 */
@Data
public class Download {
    /**
     * 选项卡ID
     */
    String tabIds;

    /**
     * 程序包名称
     */
    String packageName = "v";

    /**
     * 著者
     */
    String author = "admin";


    /**
     * 函数名称
     */
    String functionName = "";
    /**
     * 开启swagger
     */
    Boolean openSwagger = false;
    /**
     * 版本
     */
    String version;
}
