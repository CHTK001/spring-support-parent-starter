package com.chua.starter.account.manage.controller;

import com.chua.common.support.mock.MockEnvironment;
import com.chua.common.support.mock.MockString;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.common.ApiResult;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 账号测试数据（mock）草稿接口。
 *
 * <p>给账号表单提供「随机生成」能力：后端调用 common-starter 的
 * {@code user-profile} 画像生成器（可携带虚拟信用卡信息），返回可直接
 * 回填表单的草稿。生成器只产测试数据，不做任何落库。</p>
 *
 * <p><b>口径</b>：本接口是「表单草稿」辅助，不是账号业务——卡号 / 画像
 * 仅供测试填表，真实凭据仍走密钥加密存储；生成器来自底层 common-starter
 * 的 {@code MockString} SPI，本模块不实现生成规则。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/accounts/mock-draft")
public class AccountMockDraftController {

    /**
     * 用户画像生成器 SPI 名称
     */
    private static final String GENERATOR_NAME = "user-profile";

    /**
     * JSON 解析器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 生成测试数据草稿。
     *
     * <p>当前仅支持信用卡：返回 {@code username=卡号、vendor=发卡组织、
     * secret=CVV、config=有效期与持卡人画像}，字段名与账号表单对齐，
     * 前端拿到即可直接回填。</p>
     *
     * @param kind 账号种类
     * @return 表单草稿
     */
    @GetMapping
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ApiResult<Map<String, Object>> draft(@RequestParam("kind") String kind) {
        if (!AccountKind.CREDIT_CARD.equals(kind)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "暂不支持该账号种类的测试数据生成：" + kind);
        }
        MockString generator = ServiceProvider.of(MockString.class).getExtension(GENERATOR_NAME);
        if (null == generator) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "user-profile 生成器未注册");
        }
        MockEnvironment environment = new MockEnvironment();
        environment.setKeyword("creditcard");
        Map<String, Object> profile;
        try {
            profile = MAPPER.readValue(generator.getString(environment), Map.class);
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "测试数据生成失败", e);
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("cardExpire", profile.get("cardExpire"));
        config.put("holderName", profile.get("name"));
        config.put("holderGender", profile.get("gender"));
        config.put("holderBirthday", profile.get("birthday"));
        config.put("holderIdNumber", profile.get("idNumber"));
        config.put("holderPhone", profile.get("phone"));
        config.put("holderEmail", profile.get("email"));
        config.put("holderAddress", profile.get("address"));

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("kind", AccountKind.CREDIT_CARD);
        draft.put("username", profile.get("cardNumber"));
        draft.put("vendor", profile.get("cardVendor"));
        draft.put("secret", profile.get("cardCvv"));
        draft.put("config", config);
        return ApiResult.ok(draft);
    }
}
