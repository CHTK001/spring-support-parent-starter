package com.chua.starter.sso.pojo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 三方待绑定身份视图
 *
 * <p>绑定页回显用：展示三方平台与身份信息，提示用户输入本地账号密码完成绑定。</p>
 *
 * @since 2026/11/21
 * @author CH
 */
@Schema(description = "三方待绑定身份")
public record ThirdBindPendingVO(
        /**
         * 第三方平台登录类型（小写平台名，如 gitee/github）
         */
        @Schema(description = "第三方平台登录类型")
        String thirdPartyType,
        /**
         * 三方用户名
         */
        @Schema(description = "三方用户名")
        String username,
        /**
         * 三方昵称
         */
        @Schema(description = "三方昵称")
        String nickname,
        /**
         * 三方头像
         */
        @Schema(description = "三方头像")
        String avatar,
        /**
         * 三方邮箱
         */
        @Schema(description = "三方邮箱")
        String email
) {
}
