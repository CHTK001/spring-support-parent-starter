package com.chua.starter.common.support.test;

import com.chua.common.support.utils.group.AddGroup;
import com.chua.starter.common.support.api.annotations.ApiVersion;
import com.chua.starter.common.support.api.annotations.ApiGray;
import com.chua.starter.common.support.api.annotations.ApiInternal;
import com.chua.starter.common.support.api.annotations.ApiMock;
import com.chua.starter.common.support.api.annotations.ApiProfile;
import com.chua.starter.common.support.api.annotations.ApiFieldCrypto;
import com.chua.starter.common.support.api.annotations.ApiFieldIgnore;
import com.chua.starter.common.support.api.annotations.ApiFieldPrivacyEncrypt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/test")
public class TestController {

    /**
     * 字段级注解（加密/忽略/脱敏）演示载体
     */
    public static class TestUser {
        @ApiFieldCrypto(key = "test-aes-key")
        private String password;

        @ApiFieldIgnore(AddGroup.class)
        private String internalData;

        @ApiFieldPrivacyEncrypt(prefixNoMaskLen = 3, suffixNoMaskLen = 4)
        private String mobile;

        public TestUser(String password, String internalData, String mobile) {
            this.password = password;
            this.internalData = internalData;
            this.mobile = mobile;
        }

        public String getPassword() {
            return password;
        }

        public String getInternalData() {
            return internalData;
        }

        public String getMobile() {
            return mobile;
        }
    }

    @GetMapping("/version")
    @ApiVersion("1.0.0")
    public String version() {
        return "v1";
    }

    @GetMapping("/gray")
    @ApiGray("test-gray")
    public String gray() {
        return "gray";
    }

    @GetMapping("/internal")
    @ApiInternal
    public String internal() {
        return "internal";
    }

    @GetMapping("/mock")
    @ApiMock
    public String mock() {
        return "mock";
    }

    @GetMapping("/profile")
    @ApiProfile("dev")
    public String profile() {
        return "profile";
    }

    @PostMapping("/crypto")
    public TestUser crypto(@RequestBody String body) {
        return new TestUser(body, "", "");
    }

    @PostMapping("/ignore")
    public TestUser ignore(@RequestBody String body) {
        return new TestUser("", body, "");
    }

    @PostMapping("/privacy")
    public TestUser privacy(@RequestBody String body) {
        return new TestUser("", "", body);
    }
}
