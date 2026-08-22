package com.chua.starter.common.support.test;

import com.chua.starter.common.support.api.ApiVersion;
import com.chua.starter.common.support.api.ApiGray;
import com.chua.starter.common.support.api.ApiInternal;
import com.chua.starter.common.support.api.ApiMock;
import com.chua.starter.common.support.api.ApiProfile;
import com.chua.starter.common.support.api.ApiFieldCrypto;
import com.chua.starter.common.support.api.ApiFieldIgnore;
import com.chua.starter.common.support.api.ApiFieldPrivacyEncrypt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/test")
public class TestController {

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
    @ApiFieldCrypto
    public String crypto(@RequestBody String body) {
        return body;
    }

    @PostMapping("/ignore")
    @ApiFieldIgnore
    public String ignore(@RequestBody String body) {
        return body;
    }

    @PostMapping("/privacy")
    @ApiFieldPrivacyEncrypt
    public String privacy(@RequestBody String body) {
        return body;
    }
}