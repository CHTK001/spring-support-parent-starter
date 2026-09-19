package com.chua.starter.server.support.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 免费代理（每次获取免费代理时清空重录——页面从本表读取）
 * @author CH
 * @since 4.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ms_proxy")
public class MsProxy extends SysBase {

    /** 代理主机 */
    @TableField("host")
    private String host; // 主机

    /** 代理端口 */
    @TableField("port")
    private Integer port; // 端口

    /** 协议（Http/Https/Socks4/Socks5） */
    @TableField("protocol")
    private String protocol; // 协议

    /** 来源 */
    @TableField("source")
    private String source; // 源

    /** 延迟（毫秒） */
    @TableField("delay")
    private Long delay; // 延迟

    /** 是否可达 */
    @TableField("reachable")
    private Integer reachable; // 是否可达

    /** 检测时间 */
    @TableField("check_time")
    private Long checkTime; // 检测时间
}
