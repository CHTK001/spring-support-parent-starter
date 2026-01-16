package com.chua.starter.oauth.client.support.user;

import lombok.*;

/**
 * 登录结果
 *
 * @author CH
 */
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LoginAuthResult extends LoginResult {

    public static final LoginAuthResult OK = new LoginAuthResult(
            200, "成功"
    );

    /**
     * code
     */
    @NonNull
    private Integer code;
    /**
     * 错误信息
     */
    private String message;
}
