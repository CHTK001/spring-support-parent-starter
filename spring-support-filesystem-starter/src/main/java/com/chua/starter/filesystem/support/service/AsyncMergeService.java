package com.chua.starter.filesystem.support.service;

public interface AsyncMergeService {

    void mergePendingTasks();

    void retryFailedTasks();
}
