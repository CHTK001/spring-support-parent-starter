package com.chua.starter.oauth.client.support.user;

import lombok.*;

/**
 * 登录结果
 *
 * @author CH
 * @since 2022/7/25 8:45
 */
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LoginAuthResult extends LoginResult {

    /**
     * code
     */
    @NonNull
    private int code;
    /**
     * 错误信息
     */
    private String message;
}
