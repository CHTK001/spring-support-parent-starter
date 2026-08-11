package com.chua.starter.oauth.client.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.oauth.client.support.configuration.WhitelistFileWatcher;
import com.chua.starter.oauth.client.support.filter.AuthFilter;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OAuth 客户端白名单管理控制器。
 *
 * <p>提供白名单规则的动态添加、删除与查询能力，规则变更实时生效，无需重启。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/oauth/whitelist")
@RequiredArgsConstructor
@Tag(name = "OAuth 客户端 - 白名单管理")
public class OAuthWhitelistController {

    /**
     * 认证过滤器（支持动态添加/删除白名单）
     */
    private final AuthFilter authFilter;

    /**
     * 白名单文件监听器（合并配置文件与外部文件白名单）
     */
    private final WhitelistFileWatcher whitelistFileWatcher;

    /**
     * OAuth 客户端配置
     */
    private final AuthClientProperties authClientProperties;

    /**
     * 动态添加白名单规则。
     *
     * @param pattern URL Pattern，支持 Ant 风格，例如 /v2/face/**
     * @return 添加结果
     */
    @Permission("admin")
    @Operation(summary = "动态添加白名单规则")
    @PostMapping
    public ReturnResult<?> add(@RequestParam("pattern") String pattern) {
        if (null == pattern || pattern.isBlank()) {
            return ReturnResult.error("白名单规则不能为空");
        }
        authFilter.addWhitelist(pattern.trim());
        whitelistFileWatcher.addWhitelist(pattern.trim());
        log.info("[OAuth白名单]动态添加白名单规则 - {}", pattern.trim());
        return ReturnResult.ok();
    }

    /**
     * 动态删除白名单规则。
     *
     * @param pattern URL Pattern
     * @return 删除结果
     */
    @Permission("admin")
    @Operation(summary = "动态删除白名单规则")
    @DeleteMapping
    public ReturnResult<?> remove(@RequestParam("pattern") String pattern) {
        authFilter.removeWhitelist(pattern.trim());
        whitelistFileWatcher.removeWhitelist(pattern.trim());
        log.info("[OAuth白名单]动态删除白名单规则 - {}", pattern.trim());
        return ReturnResult.ok();
    }

    /**
     * 查询当前生效的全部白名单规则。
     *
     * @return 白名单规则列表
     */
    @Permission("admin")
    @Operation(summary = "查询生效中的白名单规则")
    @GetMapping
    public List<String> list() {
        return authClientProperties.getEffectiveWhitelist();
    }
}
