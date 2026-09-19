package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图片识别配置控制器。
 *
 * <p>提供 {@code image} 分组全局配置的读取与更新（classify/detect/segment/captioning/depth/pedestrian/quality/device）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/v2/image-recognition/config")
@Tag(name = "AI 图片识别 - 配置管理")
public class ImageRecognitionConfigController extends AbstractCapabilityConfigController {

    /**
     * 图片识别配置分组
     */
    private static final String GROUP = "image";

    /**
     * 镜像认可配置控制器。
     * @param settingService setting服务
     */
    public ImageRecognitionConfigController(SysAiGlobalSettingService settingService) {
        /**
         * 父。
         * @param settingService setting服务
         */
        super(settingService);
    }

    @Override
    protected String group() {
        return GROUP;
    }
}
