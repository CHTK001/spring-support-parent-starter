package com.chua.starter.server.support.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 免费代理（每次获取免费代理时清空重录——页面从本表读取）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ms_proxy")
public class MsProxy extends SysBase {

    /** 代理主机 */
    @TableField("host")
    private String host;

    /** 代理端口 */
    @TableField("port")
    private Integer port;

    /** 协议（Http/Https/Socks4/Socks5） */
    @TableField("protocol")
    private String protocol;

    /** 来源 */
    @TableField("source")
    private String source;
}
