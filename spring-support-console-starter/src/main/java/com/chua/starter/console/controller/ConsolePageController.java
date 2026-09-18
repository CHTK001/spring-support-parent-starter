package com.chua.starter.console.controller;

import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.console.scan.AnnotationSwitchScanner;
import com.chua.starter.console.scan.SwitchInventory;
import com.chua.starter.console.store.ConsoleSqliteStore;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
* 管理控制台 FTL 页面控制器。
*
* <p>页面与调整接口统一以 {@link #CONSOLE_PERMISSION} 权限码鉴权（oauth-client
* {@code @Permission} + PermissionInterceptor）；页面临时调整写入 SQLite 覆盖库，
* 经 {@code ConsoleOverrideProvider} 接入 通用 拦截器判定即时生效。</p>
*
* @author CH
* @since 2026/09/04
 */
@Controller
@RequestMapping("/console")
public class ConsolePageController {

    /** 控制台统一权限码（页面与调整接口均需该权限） */
    public static final String CONSOLE_PERMISSION = "console:manage";

    private final AnnotationSwitchScanner scanner; // scanner
    private final ConsoleSqliteStore store; // 存储

    /**
    * 控制台page控制器。
    * @param scanner scanner
    * @param store 存储
    */
    public ConsolePageController(AnnotationSwitchScanner scanner, ConsoleSqliteStore store) {
        this.scanner = scanner;
        this.store = store;
    }

    @GetMapping("/")
    @Permission(CONSOLE_PERMISSION)
    /**
    * 索引。
    * @return 索引的结果
    */
    public String index() {
        return "redirect:/console/features";
    }

    // ==================== 页面 ====================

    /**
    * @api特征 功能开关管理页
    *
    * @param model 模型
    * @return 特征的结果
    */
    @GetMapping("/features")
    @Permission(CONSOLE_PERMISSION)
    public String features(Model model) {
        SwitchInventory inventory = scanner.scan();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SwitchInventory.FeatureDescriptor f : inventory.getFeatures()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("featureId", f.getFeatureId());
            row.put("defaultEnabled", f.isDefaultEnabled());
            row.put("disabledMessage", f.getDisabledMessage());
            row.put("disabledStatus", f.getDisabledStatus());
            row.put("description", f.getDescription());
            row.put("group", f.getGroup());
            row.put("patterns", String.join(", ", f.getPatterns()));
            ConsoleSqliteStore.FeatureOverrideRow override = store.getFeatureOverride(f.getFeatureId());
            row.put("hasOverride", override != null);
            row.put("overrideEnabled", override != null ? override.enabled() : null);
            row.put("overrideMessage", override != null ? override.disabledMessage() : null);
            row.put("overrideStatus", override != null ? override.disabledStatus() : null);
            row.put("effective", override != null ? override.enabled() : f.isDefaultEnabled());
            rows.add(row);
        }
        model.addAttribute("features", rows);
        model.addAttribute("permission", CONSOLE_PERMISSION);
        return "console/features";
    }

    /**
    * @apigray 灰度规则管理页
    *
    * @param model 模型
    * @return grays的结果
    */
    @GetMapping("/grays")
    @Permission(CONSOLE_PERMISSION)
    public String grays(Model model) {
        SwitchInventory inventory = scanner.scan();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SwitchInventory.GrayDescriptor g : inventory.getGrays()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("pattern", g.getPattern());
            row.put("percentage", g.getPercentage());
            row.put("roles", join(g.getRoles()));
            row.put("users", join(g.getUsers()));
            row.put("headers", join(g.getHeaders()));
            row.put("rule", g.getRule());
            row.put("forceGray", g.isForceGray());
            row.put("patterns", String.join(", ", g.getPatterns()));
            ConsoleSqliteStore.GrayOverrideRow override = store.getGrayOverride(g.getPattern());
            row.put("hasOverride", override != null);
            row.put("overridePercentage", override != null ? override.percentage() : null);
            row.put("overrideRoles", override != null ? join(override.roles()) : null);
            row.put("overrideUsers", override != null ? join(override.users()) : null);
            row.put("overrideHeaders", override != null ? join(override.headers()) : null);
            row.put("overrideRule", override != null ? override.rule() : null);
            rows.add(row);
        }
        model.addAttribute("grays", rows);
        model.addAttribute("permission", CONSOLE_PERMISSION);
        return "console/grays";
    }

    /**
    * 通用键值运行设置管理页
    *
    * @param model 模型
    * @return kv的结果
    */
    @GetMapping("/kv")
    @Permission(CONSOLE_PERMISSION)
    public String kv(Model model) {
        model.addAttribute("kv", store.listKv(""));
        model.addAttribute("permission", CONSOLE_PERMISSION);
        return "console/kv";
    }

    // ==================== 调整接口 ====================

    /** 调整 @api特征 覆盖 */
    @PostMapping("/api/features")
    @Permission(CONSOLE_PERMISSION)
    public String upsertFeature(@RequestParam String featureId,
                                @RequestParam(defaultValue = "true") boolean enabled,
                                @RequestParam(required = false) String disabledMessage,
                                @RequestParam(defaultValue = "-1") int disabledStatus) {
        store.upsertFeatureOverride(featureId, enabled, disabledMessage, disabledStatus);
        return "redirect:/console/features";
    }

    /**
    * 移除 @api特征 覆盖
    *
    * @param featureId 特征标识
    * @return 删除特征的结果
    */
    @PostMapping("/api/features/delete")
    @Permission(CONSOLE_PERMISSION)
    public String deleteFeature(@RequestParam String featureId) {
        store.deleteFeatureOverride(featureId);
        return "redirect:/console/features";
    }

    /** 调整 @apigray 覆盖（percentage/角色/用户/头部/rule 均可选，缺省沿用注解） */
    @PostMapping("/api/grays")
    @Permission(CONSOLE_PERMISSION)
    public String upsertGray(@RequestParam String pattern,
                             @RequestParam(required = false) Integer percentage,
                             @RequestParam(required = false) String roles,
                             @RequestParam(required = false) String users,
                             @RequestParam(required = false) String headers,
                             @RequestParam(required = false) String rule) {
        store.upsertGrayOverride(pattern, percentage,
                split(roles), split(users), split(headers), rule);
        return "redirect:/console/grays";
    }

    /**
    * 移除 @apigray 覆盖
    *
    * @param pattern 模式
    * @return 删除gray的结果
    */
    @PostMapping("/api/grays/delete")
    @Permission(CONSOLE_PERMISSION)
    public String deleteGray(@RequestParam String pattern) {
        store.deleteGrayOverride(pattern);
        return "redirect:/console/grays";
    }

    /**
    * 写入通用键值
    *
    * @param key Key
    * @param value 值
    * @return upsertKv的结果
    */
    @PostMapping("/api/kv")
    @Permission(CONSOLE_PERMISSION)
    public String upsertKv(@RequestParam String key, @RequestParam String value) {
        store.setKv(key, value);
        return "redirect:/console/kv";
    }

    /**
    * 删除通用键值
    *
    * @param key Key
    * @return 删除kv的结果
    */
    @PostMapping("/api/kv/delete")
    @Permission(CONSOLE_PERMISSION)
    public String deleteKv(@RequestParam String key) {
        store.deleteKv(key);
        return "redirect:/console/kv";
    }

    // ==================== 工具 ====================

    /**
    * 连接。
    * @param arr arr
    * @return 连接的结果
    */
    private static String join(String[] arr) {
        if (arr == null || arr.length == 0) {
            return "";
        }
        return String.join(", ", arr);
    }

    /**
    * 分割。
    * @param s s
    * @return 分割的结果
    */
    private static String[] split(String s) {
        if (s == null || s.trim().isEmpty()) {
            return new String[0];
        }
        return s.split("\\s*,\\s*");
    }
}
