package com.chua.common.support.geo;

/**
 * 地理配置
 *
 * @param databaseFile 地理数据库文件路径，用于 IP 地理定位的数据源
 * @author CH
 * @since 4.0.0
 */
public record GeoSetting(String databaseFile) {

    /**
     * 创建并返回一个 geosetting 构建器
     *
     * @return GeoSettingBuilder 实例
     */
    public static GeoSettingBuilder builder() {
        return new GeoSettingBuilder();
    }

    /**
     * geosetting 的构建器内部类，提供流式构建能力
     *
     * @author CH
     * @since 4.0.0
     */
    public static class GeoSettingBuilder {
        private String databaseFile;

        public GeoSettingBuilder databaseFile(String databaseFile) {
            this.databaseFile = databaseFile;
            return this;
        }

        public GeoSetting build() {
            return new GeoSetting(databaseFile);
        }
    }
}
