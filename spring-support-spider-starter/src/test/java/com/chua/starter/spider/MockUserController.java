package com.chua.starter.spider;

import com.chua.common.support.lang.code.ReturnResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * 测试专用 Mock 接口。
 * <p>仅用于本地自检，为前端 koi 框架提供 openAuth=false 时初始化所需的
 * 菜单 / 用户信息等占位接口，避免前端初始化报 404/500 并污染控制台。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v2")
@Tag(name = "Mock 接口", description = "测试专用占位接口，模拟平台侧用户/菜单")
public class MockUserController {

    /**
     * 菜单列表占位接口（前端 auth.listRouters 调用，返回空数组即可）。
     *
     * @return 空菜单列表
     */
    @GetMapping("/user/menu")
    public ReturnResult<List<Object>> menu() {
        return ReturnResult.ok(Collections.emptyList());
    }

    /**
     * 当前用户信息占位接口（前端 auth.getLoginUserInfo 调用，返回空对象即可）。
     *
     * @return 空用户信息
     */
    @GetMapping("/user/me")
    public ReturnResult<Object> me() {
        return ReturnResult.ok(Collections.emptyMap());
    }

    /**
     * 系统设置占位接口（前端 config 拉取默认设置调用）。
     *
     * @return 空设置对象
     */
    @GetMapping("/setting/default")
    public ReturnResult<Object> setting() {
        return ReturnResult.ok(Collections.emptyMap());
    }
}
