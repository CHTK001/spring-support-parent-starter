package com.chua.starter.common.support.view;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.value.Value;
import com.chua.common.support.view.ViewConfig;
import com.chua.common.support.view.ViewPreview;
import com.chua.common.support.view.ViewResolver;
import com.chua.starter.common.support.properties.ViewProperties;
import com.google.common.base.Splitter;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.logging.log4j.util.Strings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * view
 *
 * @author CH
 * @since 2022/8/11 13:16
 */
public class ViewFactory {
    private final ViewProperties viewProperties;
    private final String bucket;
    ViewConfig viewConfig;

    private static final Map<String, ViewResolver> CACHE = new ConcurrentHashMap<>();

    public ViewFactory(ViewProperties viewProperties, String bucket) {
        this.viewProperties = viewProperties;
        this.bucket = bucket;
        this.viewConfig = viewProperties.getConfig().get(bucket);
    }

    /**
     * 获取图片
     *
     * @param path 文件路径
     * @param mode 文件模式
     * @return 文件
     */
    public ViewMeta preview(String path, String mode) {

        String type = viewConfig.getType();
        String key = type + "-" + bucket;
        ViewResolver viewResolver = CACHE.computeIfAbsent(key, it -> {
            ViewResolver resolver = ServiceProvider.of(ViewResolver.class).getNewExtension(type);
            if (null == resolver) {
                return null;
            }

            resolver.setConfig(viewConfig);
            return resolver;
        });
        if (null == viewResolver) {
            return new ViewMeta();
        }

        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            ViewPreview preview = viewResolver.preview(bucket, path, mode, stream, Strings.isBlank(viewConfig.getPlugins()) ? Collections.emptySet() : new HashSet<>(Splitter.on(',').omitEmptyStrings().trimResults().splitToList(viewConfig.getPlugins())));
            return new ViewMeta(stream.toByteArray(), preview);
        } catch (IOException e) {
            log.error("", e);
            return new ViewMeta();
        }
    }

    /**
     * 保存信息
     *
     * @param inputStream 流
     * @param name        文件名
     */
    public Value<String> save(InputStream inputStream, String name) {
        ViewResolver viewResolver = ServiceProvider.of(ViewResolver.class).getNewExtension(viewConfig.getType());
        viewResolver.setConfig(viewConfig);
        return viewResolver.storage(inputStream, bucket, name);
    }

    /**
     * meta
     */
    @NoArgsConstructor
    @Data
    public static class ViewMeta {

        private static final String DOWNLOAD = "download";
        private byte[] toByteArray;
        private ViewPreview viewPreview;

        public ViewMeta(byte[] toByteArray, ViewPreview viewPreview) {
            this.toByteArray = toByteArray;
            this.viewPreview = viewPreview;
        }

        /**
         * 文件预览失败
         *
         * @return 文件预览失败
         */
        public boolean isFailure() {
            return null == viewPreview || null == toByteArray || toByteArray.length == 0;
        }

        /**
         * 文件长度
         *
         * @return 文件长度
         */
        public long length() {
            return toByteArray.length;
        }

        /**
         * 媒体类型
         *
         * @return 媒体类型
         */
        public String mediaType() {
            return StringUtils.defaultString(viewPreview.getContentType(), "text/plain");
        }

        /**
         * 是否是下载
         *
         * @return 是否是下载
         */
        public boolean isDownload() {
            return viewPreview.isDownload();
        }
    }
}
