package com.chua.starter.soft.support.model;

public record SoftLogWatchHandle(Long watchId, Runnable stopAction) {

    public void stop() {
        if (stopAction != null) {
            stopAction.run();
        }
    }
}
