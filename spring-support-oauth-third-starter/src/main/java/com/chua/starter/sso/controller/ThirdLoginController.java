package com.chua.starter.sso.controller;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.common.support.annotations.SysLog;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.oauth.client.support.annotation.TokenForIgnore;
import com.chua.starter.oauth.client.support.annotation.UserValue;
import com.chua.starter.oauth.client.support.provider.ThirdLoginData;
import com.chua.starter.sso.service.ThirdPartyLoginOrchestrator;
import com.chua.starter.sso.pojo.ThirdAdminUnbindRequest;
import com.chua.starter.sso.pojo.ThirdBindCompleteRequest;
import com.chua.starter.sso.pojo.ThirdBindPendingRequest;
import com.chua.starter.sso.pojo.ThirdBindPendingVO;
import com.chua.starter.sso.pojo.ThirdBindingVO;
import com.chua.starter.sso.pojo.ThirdBindingsQueryRequest;
import com.chua.starter.sso.pojo.ThirdPartyUnbindRequest;
import com.chua.starter.sso.pojo.ThirdLoginExchangeRequest;
import com.chua.starter.sso.pojo.LoginResult;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.chua.common.support.lang.code.ReturnCode.REQUEST_PARAM_ERROR;
import static com.chua.starter.oauth.client.support.constants.AuthConstant.ADMIN;
import static com.chua.starter.oauth.client.support.constants.AuthConstant.SUPER_ADMIN;

/**
 * 第三方登录
 * @since 2024/9/12
 * @author CH
 */
@RestController
@Slf4j
@Api(tags = "第三方登录接口")
@Tag(name = "第三方登录接口")
@RequestMapping("/v2/user")
@RequiredArgsConstructor
public class ThirdLoginController {

    /**
     * 第三方登录编排器，处理第三方授权登录逻辑
    */
    final ThirdPartyLoginOrchestrator thirdPartyLoginOrchestrator;
    /**
     * 第三方授权登录，将授权码换取用户身份并写入 令牌。
     *
     * @param loginData     第三方登录凭证（包含 提供者 与 编码）
     * @param request       HTTP请求
     * @param response      HTTP响应
     * @param bindingResult 参数校验结果
     * @return 登录结果，包含 access令牌 等信息
     */
    @Operation(summary = "第三方登录码")
    @PostMapping("/third/login/code")
    @TokenForIgnore
    public ReturnResult<String> thirdLoginCode(@Valid @RequestBody ThirdLoginData loginData,
                                               HttpServletRequest request,
                                               HttpServletResponse response,
                                               BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.error(REQUEST_PARAM_ERROR.getCode(), bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }

        return thirdPartyLoginOrchestrator.thirdLoginCode(loginData, request, response);
    }

    /**
     * 三方登录换票：用回调带回的 redirectParam 一次性换取登录结果。
     *
     * <p>{@code redirectParam} 为服务端 AES 密文 + 一次性 nonce，兑换时先核销后解密；
     * 凭证不可重放，重复兑换或伪造密文均返回错误。</p>
     *
     * @param exchangeRequest 换票请求（含 redirectParam）
     * @param bindingResult   参数校验结果
     * @return 登录结果，包含 access令牌 等信息
     */
    @Operation(summary = "三方登录换票", description = "用回调带回的 redirectParam 一次性换取登录结果")
    @PostMapping("/third/login/exchange")
    @TokenForIgnore
    public ReturnResult<LoginResult> thirdLoginExchange(@Valid @RequestBody ThirdLoginExchangeRequest exchangeRequest,
                                                        BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.error(REQUEST_PARAM_ERROR.getCode(), bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }

        return thirdPartyLoginOrchestrator.thirdLoginExchange(exchangeRequest.redirectParam());
    }

    /**
     * 查询三方待绑定身份：用回调带回的 bindToken 回显三方身份，供绑定页展示。
     *
     * <p>凭证仅服务端持有、短时效；此接口只读不核销，绑定完成时才一次性消费。</p>
     *
     * @param pendingRequest 待绑定身份查询请求（含 bindToken）
     * @param bindingResult  参数校验结果
     * @return 待绑定的三方身份
     */
    @Operation(summary = "三方待绑定身份", description = "用回调带回的 bindToken 查询待绑定的三方身份，供绑定页回显")
    @PostMapping("/third/bind/pending")
    @TokenForIgnore
    public ReturnResult<ThirdBindPendingVO> thirdBindPending(@Valid @RequestBody ThirdBindPendingRequest pendingRequest,
                                                             BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.error(REQUEST_PARAM_ERROR.getCode(), bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }

        return thirdPartyLoginOrchestrator.thirdBindPending(pendingRequest.bindToken());
    }

    /**
     * 三方账号绑定完成：核销 bindToken，校验本地账号密码后落绑定关系并签发登录结果。
     *
     * <p>三方账号必须已绑定本地账号才可登录；本接口在绑定完成的同一请求内签发令牌，
     * 不存在「先发 token 再补绑」的窗口。</p>
     *
     * @param bindRequest    绑定完成请求（bindToken + 本地账号密码）
     * @param request        HTTP请求
     * @param response       HTTP响应
     * @param bindingResult  参数校验结果
     * @return 登录结果，包含 access令牌 等信息
     */
    @Operation(summary = "三方账号绑定完成", description = "核销 bindToken，校验本地账号密码后完成绑定并签发登录结果")
    @PostMapping("/third/bind/complete")
    @TokenForIgnore
    public ReturnResult<LoginResult> thirdBindComplete(@Valid @RequestBody ThirdBindCompleteRequest bindRequest,
                                                       HttpServletRequest request,
                                                       HttpServletResponse response,
                                                       BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.error(REQUEST_PARAM_ERROR.getCode(), bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }

        return thirdPartyLoginOrchestrator.thirdBindComplete(bindRequest, request, response);
    }
    /**
     * 将第三方账号绑定到当前登录用户。
     *
     * @param loginData     第三方授权凭证
     * @param request       HTTP请求
     * @param response      HTTP响应
     * @param bindingResult 参数校验结果
     * @return 绑定操作结果
     */
    @Operation(summary = "第三方绑定码")
    @PostMapping("/third/bind/code")
    @SysLog(module = "绑定")
    public ReturnResult<String> thirdBindCode(@Valid @RequestBody ThirdLoginData loginData,
                                              HttpServletRequest request,
                                              HttpServletResponse response,
                                              BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.error(REQUEST_PARAM_ERROR.getCode(), bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }

        return thirdPartyLoginOrchestrator.thirdBindCode(loginData, request, response);
    }
    /**
     * 查询当前用户的第三方账号绑定情况。
     *
     * @param userId 当前登录用户 标识
     * @return 已绑定的第三方平台列表
     * @param @UserValue("userId" @UserValue("userId"
     */
    @Operation(summary = "第三方绑定情况")
    @PostMapping("/third/bind/info")
    public ReturnResult<List<String>> thirdBindInfo(@UserValue("userId") String userId) {
        return thirdPartyLoginOrchestrator.thirdBindInfo(userId);
    }
    /**
     * 解绑指定第三方账号。
     *
     * @param userId                  当前登录用户 标识
     * @param thirdPartyUnbindRequest 解绑请求，包含目标 提供者 标识
     * @return 是否解绑成功
     * @param @UserValue("userId" @UserValue("userId"
     */
    @Operation(summary = "第三方绑定解绑")
    @PostMapping("/third/unbind")
    @SysLog(module = "解绑")
    public ReturnResult<Boolean> thirdUnbindForLogin(@UserValue("userId") String userId,
        @RequestBody ThirdPartyUnbindRequest thirdPartyUnbindRequest) {
        return thirdPartyLoginOrchestrator.thirdUnbind(userId, thirdPartyUnbindRequest);
    }

    /**
     * 批量查询用户第三方绑定（管理端）。
     *
     * @param request 批量查询请求，包含目标用户标识列表
     * @return 绑定记录列表
     */
    @Operation(summary = "批量查询用户第三方绑定")
    @PostMapping("/third/bindings/query")
    @SysLog(module = "绑定")
    @Permission(role = {ADMIN, SUPER_ADMIN})
    public ReturnResult<List<ThirdBindingVO>> thirdBindingsQuery(@RequestBody ThirdBindingsQueryRequest request) {
        return thirdPartyLoginOrchestrator.listBindingsByUserIds(request.userIds());
    }

    /**
     * 管理员解绑指定用户的第三方绑定。
     *
     * @param request 解绑请求，包含目标用户标识与第三方平台类型
     * @return 是否解绑成功
     */
    @Operation(summary = "管理员解绑第三方绑定")
    @PostMapping("/third/admin/unbind")
    @SysLog(module = "解绑")
    @Permission(role = {ADMIN, SUPER_ADMIN})
    public ReturnResult<Boolean> thirdAdminUnbind(@RequestBody ThirdAdminUnbindRequest request) {
        return thirdPartyLoginOrchestrator.adminUnbind(request.userId(), request.loginType());
    }
}
