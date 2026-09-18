package com.chua.starter.filesystem.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
* 服务器目录浏览接口（安全开关控制，默认关闭）。
* <p>
* 提供服务器本地目录树浏览能力，供前端「上传目录」选择器使用。
* 通过 {@code plugin.filesystem.dir-list} 开关控制是否暴露，默认关闭。
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@RestController
@RequestMapping("/dir")
@RequiredArgsConstructor
@Tag(name = "文件目录浏览", description = "服务器本地目录浏览（需开启 dir-list 开关）")
public class FileDirController {

    /**
    * 文件存储配置
    */
    private final FileStorageProperties properties;

    /**
    * 浏览指定目录下的子目录。
    *
    * @param path 目录路径，为空时返回盘符/根目录
    * @return 子目录列表
    */
    @Operation(summary = "目录列表", description = "浏览指定路径下的子目录")
    @GetMapping("/list")
    public ReturnResult<List<Map<String, Object>>> list(
            @RequestParam(required = false) String path) {
        // 安全开关：未开启时拒绝访问
        if (!properties.isDirList()) {
            return ReturnResult.error("目录浏览功能未开启，请配置 plugin.filesystem.dir-list=true");
        }

        Path dir;
        if (path == null || path.isBlank()) {
            dir = Paths.get(System.getProperty("user.dir"));
        } else {
            dir = Paths.get(path);
        }

        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return ReturnResult.error("目录不存在: " + dir);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        /**
        * 尝试。
        * @param Files.newDirectoryStream(dir 文件.新目录流(dir
        */
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", entry.getFileName() != null ? entry.getFileName().toString() : entry.toString());
                item.put("path", entry.toAbsolutePath().normalize().toString());
                result.add(item);
            }
        } catch (IOException e) {
            log.warn("[FileDir] 浏览目录失败: {} - {}", dir, e.getMessage());
            return ReturnResult.error("目录读取失败: " + e.getMessage());
        }

        Map<String, Object> parent = new LinkedHashMap<>();
        parent.put("name", "..");
        parent.put("path", dir.getParent() != null ? dir.getParent().toAbsolutePath().normalize().toString() : "");
        result.add(0, parent);

        return ReturnResult.ok(result);
    }
}
