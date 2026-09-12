package com.chua.starter.test.controller;

import com.chua.starter.common.support.annotations.Permission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
* 接口权限/按钮权限演示控制器。
*
* <p>{@code @Permission} 标注在接口方法上：query 需 {@code user:query}，
* 删除 需 {@code user:delete}（写操作），button-添加 需按钮权限码
* {@code btn:user:add}（按钮权限即接口权限码的复用）。</p>
*
* @author CH
* @since 2026/09/04
 */
@RestController
@RequestMapping("/api/permission")
public class PermissionDemoController {

    @GetMapping("/query")
    @Permission("user:query")
    /**
    * 查询。
    * @return 查询的结果
     */
    public Map<String, Object> query() {
        return Map.of("result", "query-ok");
    }

    @PostMapping("/delete")
    @Permission("user:delete")
    /**
    * 删除。
    * @return 删除的结果
     */
    public Map<String, Object> delete() {
        return Map.of("result", "delete-ok");
    }

    @PostMapping("/button-add")
    @Permission("btn:user:add")
    /**
    * button添加。
    * @return button添加的结果
     */
    public Map<String, Object> buttonAdd() {
        return Map.of("result", "add-ok");
    }
}
