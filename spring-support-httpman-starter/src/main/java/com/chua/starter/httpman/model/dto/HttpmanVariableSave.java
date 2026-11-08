package com.chua.starter.httpman.model.dto;

import lombok.Data;

/**
 * 接口调试控制台 - 变量保存参数
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
public class HttpmanVariableSave {

    /**
     * 所属作用域：GLOBAL / COLLECTION / REQUEST
     */
    private String scope;

    /**
     * 所属集合标识（COLLECTION 作用域时有效）
     */
    private String collectionId;

    /**
     * 所属请求标识（REQUEST 作用域时有效）
     */
    private String requestId;

    /**
     * 变量名
     */
    private String name;

    /**
     * 变量值
     */
    private String value;

    /**
     * 备注
     */
    private String remark;
}
