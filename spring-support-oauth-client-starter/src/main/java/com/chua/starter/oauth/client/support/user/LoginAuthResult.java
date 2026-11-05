// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.oauth.client.support.user;

import lombok.*;

/**
 * 登录结果
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LoginAuthResult extends LoginResult {

    /**
     * OK
    */
    public static final LoginAuthResult OK = new LoginAuthResult(
            200, "成功"
    );

    /**
     * 编码
     */
    @NonNull
    /**
     * 代码
    */
    private Integer code;
    /**
     * 错误信息
     */
    private String message;
}