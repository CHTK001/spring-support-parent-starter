package com.chua.starter.monitor.server.job.route.strategy;


import com.chua.common.support.annotations.Spi;
import com.chua.starter.monitor.job.TriggerParam;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.job.route.ExecutorRouter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 单个JOB对应的每个执行器，使用频率最低的优先被选举
 *      a(*)、LFU(Least Frequently Used)：最不经常使用，频率/次数
 *      b、LRU(Least Recently Used)：最近最久未使用，时间
 *
 * Created by xuxueli on 17/3/10.
 */
@Spi("lfu")
public class ExecutorRouteLFU extends ExecutorRouter {

    private static ConcurrentMap<Integer, HashMap<String, Integer>> jobLfuMap = new ConcurrentHashMap<Integer, HashMap<String, Integer>>();
    private static long CACHE_VALID_TIME = 0;

    public List<MonitorRequest> route(int jobId, List<MonitorRequest> addressList) {

        // cache clear
        if (System.currentTimeMillis() > CACHE_VALID_TIME) {
            jobLfuMap.clear();
            CACHE_VALID_TIME = System.currentTimeMillis() + 1000*60*60*24;
        }

        // lfu item init
        HashMap<String, Integer> lfuItemMap = jobLfuMap.get(jobId);     // Key排序可以用TreeMap+构造入参Compare；Value排序暂时只能通过ArrayList；
        if (lfuItemMap == null) {
            lfuItemMap = new HashMap<String, Integer>();
            jobLfuMap.putIfAbsent(jobId, lfuItemMap);   // 避免重复覆盖
        }

        Map<String, MonitorRequest> tpl = new HashMap<>();
        // put new
        for (MonitorRequest address: addressList) {
            String address1 = address.getServerHost() + address.getServerPort();
            tpl.put(address1, address);
            if (!lfuItemMap.containsKey(address1) || lfuItemMap.get(address) >1000000 ) {
                lfuItemMap.put(address1, new Random().nextInt(addressList.size()));  // 初始化时主动Random一次，缓解首次压力
            }
        }
        // remove old
        List<String> delKeys = new ArrayList<>();
        for (String existKey: lfuItemMap.keySet()) {
            if (!addressList.contains(existKey)) {
                delKeys.add(existKey);
            }
        }
        if (!delKeys.isEmpty()) {
            for (String delKey: delKeys) {
                lfuItemMap.remove(delKey);
            }
        }

        // load least userd count address
        List<Map.Entry<String, Integer>> lfuItemList = new ArrayList<>(lfuItemMap.entrySet());
        lfuItemList.sort(Map.Entry.comparingByValue());

        Map.Entry<String, Integer> addressItem = lfuItemList.get(0);
        addressItem.setValue(addressItem.getValue() + 1);

        return Collections.singletonList(tpl.get(addressItem.getKey()));
    }

    @Override
    public List<MonitorRequest> route(TriggerParam triggerParam, List<MonitorRequest> addressList) {
        return route(triggerParam.getJobId(), addressList);
    }

}
