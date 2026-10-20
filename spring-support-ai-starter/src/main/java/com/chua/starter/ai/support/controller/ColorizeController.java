package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.configuration.image.ColorizeClientManager;
import com.chua.starter.ai.support.util.AiUserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 图像上色控制器。
 *
 * <p>使用 {@code image} 分组的 {@code colorize_model} 配置调用本地图像上色模型。
 * 未配置模型时二进制接口返回 400，与 {@code /face/restore} 等能力未启用口径一致。</p>
 *
 * <p><b>鉴权口径：登录即可用。</b>上色只消费当前登录账号的额度与配置视图，
 * 与页面其它能力（人脸识别 / 以图搜图）一致：不再要求管理员；匿名访问仍被
 * {@code AiUserContext.requireUserId()} 拦下。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v2/ai/colorize")
@Tag(name = "AI 图像上色", description = "上传灰度或线稿图片，使用默认上色模型返回彩色 PNG")
public class ColorizeController {

    /**
     * 图像上色客户端管理器
     */
    private final ColorizeClientManager colorizeClientManager;

    /**
     * 图像上色状态。
     *
     * @return available 与当前上色模型名
     */
    @Operation(summary = "图像上色状态", description = "返回是否已配置上色模型及当前模型名")
    @GetMapping("/status")
    public ReturnResult<Map<String, Object>> status() {
        AiUserContext.requireUserId();
        Map<String, Object> out = new LinkedHashMap<>(3);
        out.put("available", colorizeClientManager.available());
        out.put("model", colorizeClientManager.model());
        return ReturnResult.ok(out);
    }

    /**
     * 上传并执行图像上色。
     *
     * @param file 图片文件
     * @return 上色后的 PNG 字节；未配置模型或处理失败时返回 400
     */
    @Operation(summary = "图像上色", description = "上传图片，按默认上色模型生成彩色结果 PNG")
    @PostMapping("/upload")
    public ResponseEntity<byte[]> upload(@RequestParam("file") MultipartFile file) {
        AiUserContext.requireUserId();
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        long start = System.currentTimeMillis();
        try {
            byte[] result = colorizeClientManager.colorize(file.getBytes());
            if (result == null || result.length == 0) {
                log.warn("[Colorize] 图像上色模型未配置");
                return ResponseEntity.badRequest().build();
            }
            log.info("[Colorize] 图像上色完成 model={} elapsed={}ms",
                    colorizeClientManager.model(), System.currentTimeMillis() - start);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(result);
        } catch (Exception e) {
            log.error("[Colorize] 图像上色失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
}