package com.chua.starter.console.scan;

import com.chua.starter.common.support.api.annotations.ApiFeature;
import com.chua.starter.common.support.api.annotations.ApiGray;
import com.chua.starter.common.support.api.feature.HandlerAnnotationScanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 注解开关扫描器：扫描 Spring MVC 全部处理器上的 {@code @ApiFeature}/{@code @ApiGray}，
 * 生成规范化清单与整体指纹（SHA-256）。
 *
 * <p>指纹计算：所有注解的规范化描述（{@code canonical()}）排序后拼接再哈希——
 * 与扫描顺序无关；"注解更新"（新增/修改/删除注解）必然改变指纹，从而触发启动重置。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@Slf4j
public class AnnotationSwitchScanner {

    private final RequestMappingHandlerMapping requestMappingHandlerMapping; // 请求mapping处理器mapping

    public AnnotationSwitchScanner(
            @Lazy RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    }

    /**
     * 扫描全部处理器注解并计算指纹
     *
     * @return 开关清单
     */
    public SwitchInventory scan() {
        List<SwitchInventory.FeatureDescriptor> features = new ArrayList<>();
        List<SwitchInventory.GrayDescriptor> grays = new ArrayList<>();

        HandlerAnnotationScanner scanner = new HandlerAnnotationScanner(requestMappingHandlerMapping);

        for (HandlerAnnotationScanner.ScannedHandler<ApiFeature> item
                : scanner.scan(ApiFeature.class)) {
            ApiFeature apiFeature = item.annotation();
            features.add(SwitchInventory.FeatureDescriptor.builder()
                    .featureId(apiFeature.value())
                    .defaultEnabled(apiFeature.defaultEnabled())
                    .disabledMessage(apiFeature.disabledMessage())
                    .disabledStatus(apiFeature.disabledStatus())
                    .description(apiFeature.description())
                    .group(apiFeature.group())
                    .patterns(item.patterns())
                    .build());
        }

        for (HandlerAnnotationScanner.ScannedHandler<ApiGray> item
                : scanner.scan(ApiGray.class)) {
            ApiGray apiGray = item.annotation();
            grays.add(SwitchInventory.GrayDescriptor.builder()
                    .pattern(apiGray.value())
                    .percentage(apiGray.percentage())
                    .roles(apiGray.roles())
                    .users(apiGray.users())
                    .headers(apiGray.headers())
                    .rule(apiGray.rule())
                    .forceGray(apiGray.forceGray())
                    .patterns(item.patterns())
                    .build());
        }

        // 规范化 + 排序 + 哈希：稳定指纹
        List<String> canonicals = new ArrayList<>();
        features.stream().map(SwitchInventory.FeatureDescriptor::canonical).forEach(canonicals::add);
        grays.stream().map(SwitchInventory.GrayDescriptor::canonical).forEach(canonicals::add);
        canonicals.sort(Comparator.naturalOrder());

        String fingerprint = sha256(String.join("\n", canonicals));
        log.info("[Console] 注解开关扫描完成: @ApiFeature {} 个, @ApiGray {} 个, 指纹 {}", features.size(), grays.size(), fingerprint);
        return SwitchInventory.builder()
                .features(features)
                .grays(grays)
                .fingerprint(fingerprint)
                .build();
    }

    /**
     * sha256。
     * @param input 输入
     * @return sha256的结果
     */
    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param "指纹计算失败" "指纹计算失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("指纹计算失败", e);
        }
    }
}
