package com.chua.starter.vuesql.support.channel;

import com.alibaba.fastjson2.JSONArray;
import com.chua.common.support.collection.ImmutableBuilder;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.enums.Action;
import com.chua.starter.vuesql.enums.Type;
import com.chua.starter.vuesql.pojo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.retry.RetryNTimes;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.*;
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
        rs.add(Construct.builder().type(Type.TABLE).icon("DATABASE").id("root").pid(String.valueOf(0))
                .realName("/root")
                .action(Action.OPEN)
                .name("/").build());
        return rs;
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
        tableName = StringUtils.utf8Str(Base64.getDecoder().decode(tableName));
        if ("root".equalsIgnoreCase(tableName) || "/root".equalsIgnoreCase(tableName)) {
            tableName = "/";
        }

        tableName = StringUtils.startWithAppend(tableName, "/");
        List<Construct> rs = new LinkedList<>();
        CuratorFramework curatorFramework = channelFactory.getConnection(config, CuratorFramework.class, this::getCuratorFramework, it -> it.getState() == CuratorFrameworkState.STARTED);
        getNode(curatorFramework, tableName, rs, "root", new AtomicInteger(2));
        OpenResult result = new OpenResult();
        result.setColumns(ImmutableBuilder.builder(Column.class).add(Column.builder().columnName("name").build()).newLinkedList());
        result.setData(Collections.singletonList(ImmutableBuilder.builderOfStringMap(Object.class).put("rs", rs).build()));
        result.setTotal(rs.size());
        return result;
    }

    public void getNode(CuratorFramework curatorFramework, String parentNode, List<Construct> rs, String pid, AtomicInteger index) {
        try {
            List<String> tmpList = curatorFramework.getChildren().forPath(parentNode);
            for (String tmp : tmpList) {
                String childNode = parentNode.equals("/") ? parentNode + tmp : parentNode + "/" + tmp;
                List<String> strings = curatorFramework.getChildren().forPath(childNode);
                rs.add(Construct.builder().icon("DATABASE")
                        .id(childNode)
                        .hasChildren(!strings.isEmpty())
                        .realName(childNode)
                        .name(tmp)
                        .type(Type.TABLE)
                        .action(Action.OPEN)
                        .pid(pid)
                        .build());
            }
        } catch (Exception e) {
            try {
                throw e;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public void doNode(CuratorFramework curatorFramework, String parentNode, List<Map<String, Object>> rs, int pid, AtomicInteger index) {
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
        if ("root".equalsIgnoreCase(table)) {
            table = "/";
        }
        CuratorFramework curatorFramework = channelFactory.getConnection(config, CuratorFramework.class, this::getCuratorFramework, it -> it.getState() == CuratorFrameworkState.STARTED);
        for (Object datum : data) {
            Map<String, Object> map = (Map<String, Object>) datum;
            Map newData = MapUtils.getType(map, "newData", Map.class);
            String name = StringUtils.startWithAppend(MapUtils.getString(newData, "name"), "/").trim();
            String node = table + ("/".equalsIgnoreCase(name) ? "" : name);
            String type = MapUtils.getString(newData, "type", "LS");
            Action action = Action.valueOf(MapUtils.getString(map, "action").toUpperCase());
            if (action == Action.ADD) {
                Stat stat = null;
                try {
                    stat = curatorFramework.checkExists().forPath(node);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                if (stat != null) {
                    throw new RuntimeException("节点已存在");
                }

                try {
                    curatorFramework.create().withMode("LS".equalsIgnoreCase(type) ? CreateMode.EPHEMERAL : CreateMode.PERSISTENT)
                            .forPath(node, MapUtils.getString(map, "value", "").getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return true;
            }

            if (action == Action.DELETE) {
                try {
                    curatorFramework.delete().forPath(node);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return true;
            }

            if (action == Action.UPDATE) {
                Stat stat = null;
                try {
                    stat = curatorFramework.checkExists().forPath(node);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                if (stat == null) {
                    throw new RuntimeException("节点不存在");
                }
                try {
                    curatorFramework.setData().forPath(node, MapUtils.getString(map, "value", "").getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return true;
            }

        }
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
