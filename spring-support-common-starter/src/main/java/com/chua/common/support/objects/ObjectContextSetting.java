package com.chua.common.support.objects;

import java.util.ArrayList;
import java.util.List;

/**
 * 对象上下文配置类，用于配置对象容器的初始化参数
 * <p>
 * 提供 SPI 开关、注解扫描开关和扫描包路径等配置项，通过 Builder 模式构建。
 * </p>
 *
 * @author CH
 */
public class ObjectContextSetting {

    /**
     * 是否启用 SPI 扩展机制
     */
    private boolean spiEnabled = true;

    /**
     * 是否启用注解扫描
     */
    private boolean annotationScanEnabled = true;

    /**
     * 扫描包路径列表
     */
    private List<String> scanPackages = new ArrayList<>();

    /**
     * 构造默认配置实例
     */
    public ObjectContextSetting() {
    }

    /**
     * 构造带参数的配置实例
     *
     * @param spiEnabled            是否启用 SPI
     * @param annotationScanEnabled 是否启用注解扫描
     * @param scanPackages          扫描包路径
     */
    public ObjectContextSetting(boolean spiEnabled, boolean annotationScanEnabled, List<String> scanPackages) {
        this.spiEnabled = spiEnabled;
        this.annotationScanEnabled = annotationScanEnabled;
        this.scanPackages = scanPackages;
    }

    /**
     * 获取 SPI 启用状态
     *
     * @return 是否启用 SPI
     */
    public boolean isSpiEnabled() {
        return spiEnabled;
    }

    /**
     * 获取注解扫描启用状态
     *
     * @return 是否启用注解扫描
     */
    public boolean isAnnotationScanEnabled() {
        return annotationScanEnabled;
    }

    /**
     * 获取扫描包路径列表
     *
     * @return 扫描包路径
     */
    public List<String> getScanPackages() {
        return scanPackages;
    }

    /**
     * 创建 Builder 实例
     *
     * @return Builder 构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * ObjectContextSetting 构建器类
     */
    public static class Builder {
        /** SPI是否启用 */
        private boolean spiEnabled = true;
        /** Annotationscan是否启用 */
        private boolean annotationScanEnabled = true;
        /** Scanpackages */
        private List<String> scanPackages = new ArrayList<>();

        /**
         * 设置 SPI 启用状态
         *
         * @param spiEnabled SPI 是否启用
         * @return 当前 Builder 实例
         */
        public Builder spiEnabled(boolean spiEnabled) {
            this.spiEnabled = spiEnabled;
            return this;
        }

        /**
         * 设置注解扫描启用状态
         *
         * @param annotationScanEnabled 是否启用注解扫描
         * @return 当前 Builder 实例
         */
        public Builder annotationScanEnabled(boolean annotationScanEnabled) {
            this.annotationScanEnabled = annotationScanEnabled;
            return this;
        }

        /**
         * 设置扫描包路径
         *
         * @param scanPackages 扫描包路径列表
         * @return 当前 Builder 实例
         */
        public Builder scanPackages(List<String> scanPackages) {
            this.scanPackages = scanPackages;
            return this;
        }

        /**
         * 构建 ObjectContextSetting 实例
         *
         * @return 构建完成的配置实例
         */
        public ObjectContextSetting build() {
            return new ObjectContextSetting(spiEnabled, annotationScanEnabled, scanPackages);
        }
    }
}
