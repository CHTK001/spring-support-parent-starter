package com.chua.starter.httpman.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;

/**
 * 接口调试控制台 - 请求
 * <p>对应数据库表 ms_httpman_request。每个请求保存用于发出的完整报文信息
 * （方法/地址/头/查询/正文/鉴权），集合内的所有请求通过 {@code parentId} 组织成树。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@TableName("ms_httpman_request")
public class MsHttpmanRequest extends SysBase {

    /**
     * 主键标识
     */
    @TableId(value = "ms_httpman_request_id", type = IdType.ASSIGN_UUID)
    /**
     * 请求标识
     */
    private String requestId;

    /**
     * 所属集合标识
     */
    @TableField("ms_httpman_request_collection_id")
    /**
     * 请求集合ID
     */
    private String requestCollectionId;

    /**
     * 父级请求标识（目录/嵌套请求）
     */
    @TableField("ms_httpman_request_parent_id")
    /**
     * 请求父ID
     */
    private String requestParentId;

    /**
     * 请求名称
     */
    @TableField("ms_httpman_request_name")
    /**
     * 请求名称
     */
    private String requestName;

    /**
     * 请求 URL
     */
    @TableField("ms_httpman_request_url")
    /**
     * 请求地址
     */
    private String requestUrl;

    /**
     * 请求方法（GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD）
     */
    @TableField("ms_httpman_request_method")
    /**
     * 请求方法
     */
    private String requestMethod;

    /**
     * 请求头（JSON 数组）
     */
    @TableField("ms_httpman_request_headers")
    /**
     * 请求头JSON
     */
    private String requestHeaders;

    /**
     * 查询参数（JSON 数组）
     */
    @TableField("ms_httpman_request_query")
    /**
     * 查询参数JSON
     */
    private String requestQuery;

    /**
     * 请求正文
     */
    @TableField("ms_httpman_request_body")
    /**
     * 请求正文
     */
    private String requestBody;

    /**
     * 请求正文类型（json/form-data/x-www-form-urlencoded/none）
     */
    @TableField("ms_httpman_request_body_type")
    /**
     * 请求正文格式
     */
    private String requestBodyType;

    /**
     * 鉴权参数（JSON 对象）
     * <p>由前端 "鉴权" 面板填写，格式同 Postman：{"type": "none|basic|bearer|oauth2", "data": {}}。</p>
     */
    @TableField("ms_httpman_request_auth")
    /**
     * 请求认证
     */
    private String requestAuth;
}
