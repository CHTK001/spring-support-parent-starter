package com.chua.starter.soft.support.enums;

public enum SoftOperationStage {
    PREPARE(10),
    DOWNLOAD(35),
    INSTALL(75),
    UNINSTALL(75),
    EXECUTE(75),
    WRITE(75),
    BACKUP(10),
    VERIFY(90),
    FINISH(100);

    private final int progressPercent;

    SoftOperationStage(int progressPercent) {
        this.progressPercent = progressPercent;
    }

    public int progressPercent() {
        return progressPercent;
    }
}
