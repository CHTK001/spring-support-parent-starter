package com.chua.starter.vuesql.support.channel;

import com.alibaba.fastjson2.JSONArray;
import com.chua.common.support.collection.ImmutableBuilder;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.enums.Action;
import com.chua.starter.vuesql.enums.Type;
import com.chua.starter.vuesql.pojo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.retry.RetryNTimes;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * redis
 *
 * @author CH
 */
@Slf4j
@Component("zookeeper")
public class ZookeeperTableChannel implements TableChannel {

    private final CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder();
    private final AtomicBoolean state = new AtomicBoolean(false);


    @Resource
    private ChannelFactory channelFactory;

    public String createUrl(WebsqlConfig config) {
        return null;
    }

    @Override
    public List<Construct> getDataBaseConstruct(WebsqlConfig config) {
        List<Construct> rs = new LinkedList<>();
        CuratorFramework curatorFramework = channelFactory.getConnection(config, CuratorFramework.class, this::getCuratorFramework, it -> it.getState() == CuratorFrameworkState.STARTED);
        rs.add(Construct.builder().type(Type.DATABASE).icon("DATABASE").id(1).pid(0).name(config.getConfigDatabase()).build());
        getNode(curatorFramework, "/", rs, 1, new AtomicInteger(2));
        return rs;
    }

    public void getNode(CuratorFramework curatorFramework, String parentNode, List<Construct> rs, int pid, AtomicInteger index) {
        try {
            List<String> tmpList = curatorFramework.getChildren().forPath(parentNode);
            for (String tmp : tmpList) {
                String childNode = parentNode.equals("/") ? parentNode + tmp : parentNode + "/" + tmp;
                int andIncrement = index.getAndIncrement();
                rs.add(Construct.builder().icon("DATABASE")
                                .id(andIncrement)
                                .realName(childNode)
                                .name(tmp)
                                .type(Type.TABLE)
                                .action(Action.OPEN)
                                .pid(pid)
                        .build());
                getNode(curatorFramework, childNode, rs, andIncrement, index);
            }
        } catch (Exception e) {
            try {
                throw e;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    public List<Keyword> getKeyword(WebsqlConfig config) {
        List<Keyword> rs = new LinkedList<>();
        return rs;
    }

    @Override
    public SqlResult execute(WebsqlConfig websqlConfig, String sql, Integer pageNum, Integer pageSize, String sortColumn, String sortType) {
        return new SqlResult();
    }

    @Override
    public SqlResult explain(WebsqlConfig websqlConfig, String sql) {
        return new SqlResult();
    }

    @Override
    public OpenResult openTable(WebsqlConfig config, String tableName, Integer pageNum, Integer pageSize) {
        List<Map<String, Object>> rs = new LinkedList<>();
        CuratorFramework curatorFramework = channelFactory.getConnection(config, CuratorFramework.class, this::getCuratorFramework, it -> it.getState() == CuratorFrameworkState.STARTED);
        doNode(curatorFramework, tableName, rs, 1, new AtomicInteger(2));
        OpenResult result = new OpenResult();
        result.setColumns(ImmutableBuilder.builder(Column.class).add(Column.builder().columnName("name").build()).newLinkedList());
        result.setData(rs);
        result.setTotal(rs.size());
        return result;
    }
    public void doNode(CuratorFramework curatorFramework, String parentNode,  List<Map<String, Object>> rs, int pid, AtomicInteger index) {
        try {
            List<String> tmpList = curatorFramework.getChildren().forPath(parentNode);
            for (String tmp : tmpList) {
                String childNode = parentNode.equals("/") ? parentNode + tmp : parentNode + "/" + tmp;
                int andIncrement = index.getAndIncrement();
                Map<String, Object> item = new HashMap<>(1);
                item.put("name", childNode);
                rs.add(item);
                doNode(curatorFramework, childNode, rs, andIncrement, index);
            }
        } catch (Exception e) {
            try {
                throw e;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    public OperatorResult doExecute(WebsqlConfig websqlConfig, String tableName, String action) {
        return new OperatorResult();
    }

    @Override
    @SuppressWarnings("ALL")
    public Boolean update(WebsqlConfig config, String table, JSONArray data) {
        return false;
    }

    public CuratorFramework getCuratorFramework(WebsqlConfig config) {
        builder.connectString(config.getConfigIp() + ":" + config.getConfigPort());
        CuratorFramework curatorFramework = builder.retryPolicy(new RetryNTimes(3, 1000)).build();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        if (curatorFramework.getState() == CuratorFrameworkState.STARTED) {
            state.set(true);
            return curatorFramework;
        }
        curatorFramework.start();
        curatorFramework.getConnectionStateListenable().addListener((client, newState) -> {
            log.info("Zookeeper waiting for connection");
            state.set(newState.isConnected());
            if (newState.isConnected()) {
                log.info("Zookeeper connection succeeded...");
                countDownLatch.countDown();
            }
        });

        if (curatorFramework.getState() != CuratorFrameworkState.STARTED) {
            try {
                boolean await = countDownLatch.await(10, TimeUnit.SECONDS);
                if (!await) {
                    curatorFramework.close();
                    return null;
                }
                log.info(">>>>>>>>>>> ZookeeperFactory connection complete.");
                state.set(true);
            } catch (Exception e) {
                e.printStackTrace();
                log.info(">>>>>>>>>>> ZookeeperFactory connection activation failed.");
            }
        } else {
            state.set(true);
        }
        return curatorFramework;
    }
}
