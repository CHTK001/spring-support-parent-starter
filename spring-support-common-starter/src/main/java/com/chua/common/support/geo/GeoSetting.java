package com.chua.common.support.geo;

/**
 * 地理配置
 *
 * @author CH
 */
public class GeoSetting {

    /**
     * 地理数据库文件路径，用于 IP 地理定位的数据源
     */
    private String databaseFile;

    /**
     * 获取地理数据库文件路径
     *
     * @return 数据库文件路径
     */
    public String getDatabaseFile() {
        return databaseFile;
    }

    /**
     * 设置地理数据库文件路径
     *
     * @param databaseFile 数据库文件路径
     */
    public void setDatabaseFile(String databaseFile) {
        this.databaseFile = databaseFile;
    }

    /**
     * 创建并返回一个 GeoSetting 构建器
     *
     * @return GeoSettingBuilder 实例
     */
    public static GeoSettingBuilder builder() {
        return new GeoSettingBuilder();
    }

    /**
     * GeoSetting 的构建器内部类，提供流式构建能力
     */
    public static class GeoSettingBuilder {
        /**
         * 正在构建的 GeoSetting 实例
         */
        private final GeoSetting setting = new GeoSetting();

        /**
         * 设置地理数据库文件路径
         *
         * @param databaseFile 数据库文件路径
         * @return 当前构建器实例，支持链式调用
         */
        public GeoSettingBuilder databaseFile(String databaseFile) {
            setting.setDatabaseFile(databaseFile);
            return this;
        }

        /**
         * 完成构建并返回 GeoSetting 实例
         *
         * @return 配置完成的 GeoSetting 对象
         */
        public GeoSetting build() {
            return setting;
        }
    }
}
