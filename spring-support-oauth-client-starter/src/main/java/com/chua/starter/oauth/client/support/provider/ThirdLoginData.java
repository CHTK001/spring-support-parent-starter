package com.chua.starter.oauth.client.support.provider;

import jakarta.validation.constraints.NotNull;

/**
 * 登录信息
 *
 * <p>第三方授权登录 / 绑定接口 {@code POST /v2/user/third/login/code} 与
 * {@code POST /v2/user/third/bind/code} 的请求体。作为 {@code @RequestBody} 由前端 JSON 反序列化得到，
 * 前三个组件用 {@link NotNull} 做非空校验，校验失败时由控制器取第一条错误消息回给前端。</p>
 *
 * <p>本 record 不定义紧凑构造器，四个组件都不做归一；实际使用前
 * {@code SysSettingServiceImpl#getAuthRequest} 会把 {@link #loginType()} 去空白并转小写后再匹配
 * {@code sys_setting} 配置分组。</p>
 *
 * @param loginCode  第三方平台侧颁发的授权码 / 临时登录码，由前端在用户完成第三方授权后回传。
 *                  不允许为 {@code null}（{@code @NotNull}，消息「loginCode不能为空」）；
 *                  取值会被缓存为 {@code state + "_info"}，供回调接口换取第三方用户身份
 * @param callback   授权完成后的系统侧跳转地址，即用户最终被送回的前端页面地址。
 *                  不允许为 {@code null}（{@code @NotNull}，消息「callback不能为空」），
 *                  但允许空串；取值被缓存为 {@code state + "_system_callback"}，
 *                  第三方回调接口在成功或失败时都重定向到该地址（为空时兜底为 {@code "/"}）。
 *                  注意与配置中的 {@code redirectUri}（第三方回调到本服务后端的地址）区分，两者不是同一个
 * @param loginType  第三方平台标识，同时充当 {@code sys_setting} 的配置分组名，
 *                  如 {@code github} / {@code gitee} / {@code google} / {@code wechat}。
 *                  不允许为 {@code null}（{@code @NotNull}，消息「登录类型不能为空」）；取值为空白时
 *                  {@code getAuthRequest} 直接判定「第三方登录类型为空」并返回 {@code null}
 * @param thirdType  流程标识，区分本次授权走「登录」还是「绑定」：取字符串 {@code "0"} 时
 *                  第三方回调接口转入绑定流程，其余取值（含 {@code null}）走授权登录流程。
 *                  无 {@code @NotNull} 约束，允许为 {@code null}（此时按登录流程处理）；
 *                  取值被缓存为 {@code state + "_thirdType"}
 * @author CH
 */
public record ThirdLoginData(@NotNull(message = "loginCode不能为空") String loginCode,
                              @NotNull(message = "callback不能为空") String callback,
                              @NotNull(message = "登录类型不能为空") String loginType,
                              String thirdType) {
}
