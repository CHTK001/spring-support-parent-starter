package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.awt.Desktop;
import java.io.File;
import java.util.Map;

/**
 * Shell 操作控制器。
 *
 * <p>提供桌面壳模式下的系统级操作：打开文件、文件夹、外部链接、检查工具安装等。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/shell")
public class ApiShellController {

    /**
     * 打开文件（使用系统默认应用）。
     */
    @PostMapping("/open-file")
    public ApiResult<Void> openFile(@RequestBody Map<String, Object> body) {
        String filePath = String.valueOf(body.getOrDefault("file_path", ""));
        if (filePath.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "file_path 不能为空");
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(new File(filePath));
            }
        } catch (Exception e) {
            log.warn("[shell] 打开文件失败: {}", e.getMessage());
        }
        return ApiResult.ok();
    }

    /**
     * 在文件管理器中显示文件。
     */
    @PostMapping("/show-item-in-folder")
    public ApiResult<Void> showItemInFolder(@RequestBody Map<String, Object> body) {
        String filePath = String.valueOf(body.getOrDefault("file_path", ""));
        if (filePath.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "file_path 不能为空");
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browseFileDirectory(new File(filePath));
            }
        } catch (Exception e) {
            log.warn("[shell] 显示文件失败: {}", e.getMessage());
        }
        return ApiResult.ok();
    }

    /**
     * 使用外部应用打开 URL。
     */
    @PostMapping("/open-external")
    public ApiResult<Void> openExternal(@RequestBody Map<String, Object> body) {
        String url = String.valueOf(body.getOrDefault("url", ""));
        if (url.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "url 不能为空");
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new java.net.URI(url));
            }
        } catch (Exception e) {
            log.warn("[shell] 打开外部链接失败: {}", e.getMessage());
        }
        return ApiResult.ok();
    }

    /**
     * 检查工具是否已安装。
     */
    @PostMapping("/check-tool-installed")
    public ApiResult<Boolean> checkToolInstalled(@RequestBody Map<String, Object> body) {
        String tool = String.valueOf(body.getOrDefault("tool", ""));
        if (tool.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "tool 不能为空");
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("where", tool);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exit = p.waitFor();
            return ApiResult.ok(exit == 0);
        } catch (Exception e) {
            return ApiResult.ok(false);
        }
    }

    /**
     * 使用指定应用打开文件夹。
     */
    @PostMapping("/open-folder-with")
    public ApiResult<Void> openFolderWith(@RequestBody Map<String, Object> body) {
        String filePath = String.valueOf(body.getOrDefault("file_path", ""));
        if (filePath.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "file_path 不能为空");
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(new File(filePath));
            }
        } catch (Exception e) {
            log.warn("[shell] 打开文件夹失败: {}", e.getMessage());
        }
        return ApiResult.ok();
    }
}
