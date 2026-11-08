package com.chua.starter.httpman.model.dto;

import lombok.Data;

/**
 * 接口调试控制台 - 请求保存参数
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
public class HttpmanRequestSave {

    /**
     * 所属集合标识
     */
    private String collectionId;

    /**
     * 所属请求标识（修改时）
     */
    private String requestId;

    /**
     * 父级请求标识（目录）
     */
    private String parentId;

    /**
     * 请求名称
     */
    private String name;

    /**
     * 请求 URL
     */
    private String url;

    /**
     * 请求方法（GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD）
     */
    private String method;

    /**
     * 请求头（JSON 数组：{"name": "ContentType", "value": "application/json"}）
     */
    private String headers;

    /**
     * 查询参数（JSON 数组：{"name": "action", "value": "list"}）
     */
    private String query;

    /**
     * 请求正文
     */
    private String body;

    /**
     * 请求正文类型（json/form-data/x-www-form-urlencoded/none）
     */
    private String bodyType;

    /**
     * 鉴权参数（JSON 对象）
     */
    private String auth;
}
