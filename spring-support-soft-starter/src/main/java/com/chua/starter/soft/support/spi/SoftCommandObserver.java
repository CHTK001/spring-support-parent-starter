package com.chua.starter.soft.support.spi;

import com.chua.starter.soft.support.enums.SoftOperationStage;

public interface SoftCommandObserver {

    default void onStage(SoftOperationStage stage, String message, String detail) {
    }

    default void onStdout(String line) {
    }

    default void onStderr(String line) {
    }
}
