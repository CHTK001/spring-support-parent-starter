package com.chua.report.server.starter.job.route.strategy;


import com.chua.common.support.annotations.Spi;
import com.chua.common.support.discovery.Discovery;
import com.chua.report.client.starter.job.TriggerParam;
import com.chua.report.server.starter.job.route.ExecutorRouter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 分组下机器地址相同，不同JOB均匀散列在不同机器上，保证分组下机器分配JOB平均；且每个JOB固定调度其中一台机器；
 *      a、virtual node：解决不均衡问题
 *      b、hash method replace hashCode：String的hashCode可能重复，需要进一步扩大hashCode的取值范围
 * Created by xuxueli on 17/3/10.
 */
@Spi("hash")
public class ExecutorRouteConsistentHash extends ExecutorRouter {

    private static final int VIRTUAL_NODE_NUM = 100;

    /**
     * get hash code on 2^32 ring (md5散列的方式计算hash值)
     * @param key
     * @return
     */
    private static long hash(String key) {

        // md5 byte
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not supported", e);
        }
        md5.reset();
        byte[] keyBytes = null;
        keyBytes = key.getBytes(StandardCharsets.UTF_8);

        md5.update(keyBytes);
        byte[] digest = md5.digest();

        // hash code, Truncate to 32-bits
        long hashCode = ((long) (digest[3] & 0xFF) << 24)
                | ((long) (digest[2] & 0xFF) << 16)
                | ((long) (digest[1] & 0xFF) << 8)
                | (digest[0] & 0xFF);

        long truncateHashCode = hashCode & 0xffffffffL;
        return truncateHashCode;
    }

    public Discovery hashJob(int jobId, Set<Discovery> addressList) {

        // ------A1------A2-------A3------
        // -----------J1------------------
        TreeMap<Long, Discovery> addressRing = new TreeMap<>();
        for (Discovery address: addressList) {
            for (int i = 0; i < VIRTUAL_NODE_NUM; i++) {
                long addressHash = hash("SHARD-" + address + "-NODE-" + i);
                addressRing.put(addressHash, address);
            }
        }

        long jobHash = hash(String.valueOf(jobId));
        SortedMap<Long, Discovery> lastRing = addressRing.tailMap(jobHash);
        if (!lastRing.isEmpty()) {
            return lastRing.get(lastRing.firstKey());
        }
        return addressRing.firstEntry().getValue();
    }

    @Override
    public Set<Discovery> route(TriggerParam triggerParam, Set<Discovery> addressList) {
        return Collections.singleton(hashJob(triggerParam.getJobId(), addressList));
    }

}
