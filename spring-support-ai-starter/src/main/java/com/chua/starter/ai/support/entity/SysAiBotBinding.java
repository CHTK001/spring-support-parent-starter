package com.chua.starter.ai.support.entity;

import com.chua.starter.mybatis.pojo.SysBase;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 微信 iLink Bot 绑定信息实体。
 *
 * <p>记录扫码绑定的 Bot 凭证、连接状态与元数据，
 * 支持同一系统绑定多个 Bot 实例。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_ai_bot_binding")
public class SysAiBotBinding extends SysBase {

    /**
     * 绑定 ID
     */
    @TableId(type = IdType.AUTO)
    private Long sysAiBotBindingId;

    /**
     * 关联用户 ID
     */
    private Long sysAiBotBindingUserId;

    /**
     * Bot 唯一标识（iLink 分配的 botId）
     */
    private String sysAiBotBindingBotId;

    /**
     * 登录凭证 Token
     */
    private String sysAiBotBindingBotToken;

    /**
     * 平台类型（wechat / wecom）
     */
    private String sysAiBotBindingPlatform;

    /**
     * Bot 昵称（来自微信资料）
     */
    private String sysAiBotBindingNickname;

    /**
     * 头像地址
     */
    private String sysAiBotBindingAvatarUrl;

    /**
     * 独立网关端口号
     */
    private Integer sysAiBotBindingGatewayPort;

    /**
     * 状态：online / offline / expired
     */
    private String sysAiBotBindingStatus;

    /**
     * 绑定来源（qrcode_scan / manual_import）
     */
    private String sysAiBotBindingBindSource;

    /**
     * 最后活跃时间
     */
    private LocalDateTime sysAiBotBindingLastActiveTime;
}
