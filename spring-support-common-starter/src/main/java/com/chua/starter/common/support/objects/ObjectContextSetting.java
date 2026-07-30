package com.chua.starter.common.support.objects;

/**
 * 对象上下文配置。
 */
public class ObjectContextSetting {

    private boolean spiEnabled = true;
    private boolean annotationScanEnabled = true;
    private final java.util.List<String> scanPackages = new java.util.ArrayList<>();

    public static Builder builder() {
        return new Builder();
    }

    public boolean isSpiEnabled() {
        return spiEnabled;
    }

    public ObjectContextSetting setSpiEnabled(boolean spiEnabled) {
        this.spiEnabled = spiEnabled;
        return this;
    }

    public boolean isAnnotationScanEnabled() {
        return annotationScanEnabled;
    }

    public ObjectContextSetting setAnnotationScanEnabled(boolean annotationScanEnabled) {
        this.annotationScanEnabled = annotationScanEnabled;
        return this;
    }

    public java.util.List<String> getScanPackages() {
        return scanPackages;
    }

    public ObjectContextSetting addScanPackage(String scanPackage) {
        this.scanPackages.add(scanPackage);
        return this;
    }

    public static final class Builder {

        private final ObjectContextSetting setting = new ObjectContextSetting();

        public Builder spiEnabled(boolean spiEnabled) {
            setting.setSpiEnabled(spiEnabled);
            return this;
        }

        public Builder annotationScanEnabled(boolean annotationScanEnabled) {
            setting.setAnnotationScanEnabled(annotationScanEnabled);
            return this;
        }

        public Builder scanPackages(java.util.List<String> scanPackages) {
            setting.getScanPackages().addAll(scanPackages);
            return this;
        }

        public ObjectContextSetting build() {
            return setting;
        }
    }
}
