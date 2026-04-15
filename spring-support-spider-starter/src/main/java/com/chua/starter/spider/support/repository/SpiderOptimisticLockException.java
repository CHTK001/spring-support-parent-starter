package com.chua.starter.spider.support.repository;

/**
 * 乐观锁冲突异常。
 *
 * @author CH
 */
public class SpiderOptimisticLockException extends RuntimeException {

    public SpiderOptimisticLockException(String message) {
        super(message);
    }
}
