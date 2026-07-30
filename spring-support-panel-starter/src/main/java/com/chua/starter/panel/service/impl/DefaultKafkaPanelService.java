package com.chua.starter.panel.service.impl;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.panel.service.KafkaPanelService;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.errors.TimeoutException;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * 默认 Kafka 面板服务。
 * 使用 Kafka AdminClient API 实现集群管理，不依赖额外 SDK。
 *
 * @author CH
 */
public class DefaultKafkaPanelService implements KafkaPanelService {

    /**
     * Kafka 连接对象。
     */
    private record KafkaConnection(String bootstrapServers, AdminClient adminClient) {
    }

    /**
     * 连接池。
     */
    private final Map<String, KafkaConnection> connectionMap = new ConcurrentHashMap<>();

    private static final Duration ADMIN_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CONSUME_TIMEOUT = Duration.ofSeconds(5);

    @Override
    public String open(String host, int port, String username, String password, String protocol) {
        String scheme = StringUtils.isBlank(protocol) ? "plaintext" : protocol.trim().toLowerCase(Locale.ROOT);
        String bootstrapServers = host + ":" + port;
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) ADMIN_TIMEOUT.toMillis());
        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            String jaasConfig = "org.apache.kafka.common.security.plain.PlainLoginModule required "
                    + "username=\"" + username.trim() + "\" "
                    + "password=\"" + password.trim() + "\";";
            props.put("sasl.jaas.config", jaasConfig);
            props.put("sasl.mechanism", "PLAIN");
            props.put("security.protocol", scheme.toUpperCase(Locale.ROOT));
        }
        AdminClient adminClient = AdminClient.create(props);
        // verify connection
        try {
            adminClient.describeCluster().clusterId().get(ADMIN_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            adminClient.close();
            throw new RuntimeException("Kafka 连接失败: " + e.getMessage(), e);
        }
        String connectionId = UUID.randomUUID().toString();
        connectionMap.put(connectionId, new KafkaConnection(bootstrapServers, adminClient));
        return connectionId;
    }

    @Override
    public void close(String connectionId) {
        KafkaConnection conn = connectionMap.remove(connectionId);
        if (conn != null) {
            conn.adminClient().close();
        }
    }

    @Override
    public Map<String, Object> clusterHealth(String connectionId) {
        KafkaConnection conn = requireConnection(connectionId);
        AdminClient admin = conn.adminClient();
        try {
            DescribeClusterResult cluster = admin.describeCluster();
            String clusterId = cluster.clusterId().get();
            Collection<Node> nodes = cluster.nodes().get();
            Node controller = cluster.controller().get();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("clusterId", clusterId);
            result.put("brokerCount", nodes.size());
            result.put("controller", controller != null ? controller.id() + ":" + controller.host() : "unknown");
            List<Map<String, Object>> brokerList = new ArrayList<>();
            for (Node node : nodes) {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("id", node.id());
                b.put("host", node.host());
                b.put("port", node.port());
                b.put("rack", node.rack() == null ? "" : node.rack());
                brokerList.add(b);
            }
            result.put("brokers", brokerList);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Kafka 集群信息查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> listBrokers(String connectionId) {
        KafkaConnection conn = requireConnection(connectionId);
        try {
            Collection<Node> nodes = conn.adminClient().describeCluster().nodes().get();
            List<Map<String, Object>> result = new ArrayList<>();
            for (Node node : nodes) {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("id", node.id());
                b.put("host", node.host());
                b.put("port", node.port());
                b.put("rack", node.rack() == null ? "" : node.rack());
                result.add(b);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Kafka broker 查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> listTopics(String connectionId) {
        KafkaConnection conn = requireConnection(connectionId);
        try {
            ListTopicsResult topics = conn.adminClient().listTopics(new ListTopicsOptions().listInternal(true));
            Set<String> topicNames = topics.names().get();
            DescribeTopicsResult describe = conn.adminClient().describeTopics(topicNames);
            Map<String, TopicDescription> descriptionMap = describe.all().get();
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<String, TopicDescription> entry : descriptionMap.entrySet()) {
                TopicDescription td = entry.getValue();
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("name", td.name());
                t.put("internal", td.isInternal());
                t.put("partitions", td.partitions().size());
                t.put("replicationFactor", td.partitions().isEmpty() ? 0 :
                        td.partitions().get(0).replicas().size());
                result.add(t);
            }
            result.sort(Comparator.comparing(a -> String.valueOf(a.get("name"))));
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Kafka topic 列表查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean createTopic(String connectionId, String topicName, int numPartitions, short replicationFactor) {
        KafkaConnection conn = requireConnection(connectionId);
        try {
            NewTopic newTopic = new NewTopic(topicName, numPartitions, replicationFactor);
            CreateTopicsResult result = conn.adminClient().createTopics(List.of(newTopic));
            result.all().get();
            return true;
        } catch (ExecutionException e) {
            throw new RuntimeException("Kafka topic 创建失败: " + e.getCause().getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Kafka topic 创建失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteTopic(String connectionId, String topicName) {
        KafkaConnection conn = requireConnection(connectionId);
        try {
            DeleteTopicsResult result = conn.adminClient().deleteTopics(List.of(topicName));
            result.all().get();
            return true;
        } catch (ExecutionException e) {
            throw new RuntimeException("Kafka topic 删除失败: " + e.getCause().getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Kafka topic 删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> topicDetail(String connectionId, String topicName) {
        KafkaConnection conn = requireConnection(connectionId);
        try {
            DescribeTopicsResult result = conn.adminClient().describeTopics(List.of(topicName));
            TopicDescription td = result.topicNameValues().get(topicName).get();
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("name", td.name());
            detail.put("internal", td.isInternal());
            List<Map<String, Object>> partitionList = new ArrayList<>();
            for (TopicPartitionInfo p : td.partitions()) {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("partition", p.partition());
                pm.put("leader", p.leader() != null ? p.leader().id() : -1);
                pm.put("replicas", p.replicas().stream().map(Node::id).toList());
                pm.put("isr", p.isr().stream().map(Node::id).toList());
                partitionList.add(pm);
            }
            detail.put("partitions", partitionList);
            // describe configs
            ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);
            DescribeConfigsResult configResult = conn.adminClient().describeConfigs(List.of(resource));
            Map<String, String> configMap = new LinkedHashMap<>();
            configResult.all().get().values().forEach(cd ->
                    cd.entries().forEach(entry ->
                            configMap.put(entry.name(), entry.value())));
            detail.put("configs", configMap);
            return detail;
        } catch (Exception e) {
            throw new RuntimeException("Kafka topic 详情查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> consumeMessages(String connectionId, String topicName, int partition, long offset, int limit) {
        KafkaConnection conn = requireConnection(connectionId);
        int maxMessages = Math.max(1, Math.min(limit, 500));
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, conn.bootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "panel-consumer-" + UUID.randomUUID().toString().substring(0, 8));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, 1048576);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            if (partition >= 0) {
                consumer.assign(List.of(new org.apache.kafka.common.TopicPartition(topicName, partition)));
                if (offset >= 0) {
                    consumer.seek(new org.apache.kafka.common.TopicPartition(topicName, partition), offset);
                }
            } else {
                consumer.subscribe(List.of(topicName));
            }
            List<Map<String, Object>> messages = new ArrayList<>();
            long deadline = System.currentTimeMillis() + CONSUME_TIMEOUT.toMillis();
            while (messages.size() < maxMessages && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, String> record : records) {
                    if (messages.size() >= maxMessages) break;
                    Map<String, Object> msg = new LinkedHashMap<>();
                    msg.put("partition", record.partition());
                    msg.put("offset", record.offset());
                    msg.put("timestamp", record.timestamp());
                    msg.put("key", record.key());
                    msg.put("value", record.value());
                    messages.add(msg);
                }
            }
            return messages;
        }
    }

    @Override
    public List<Map<String, Object>> listConsumerGroups(String connectionId) {
        KafkaConnection conn = requireConnection(connectionId);
        try {
            ListConsumerGroupsResult result = conn.adminClient().listConsumerGroups();
            Collection<ConsumerGroupListing> groups = result.all().get();
            List<Map<String, Object>> groupList = new ArrayList<>();
            for (ConsumerGroupListing group : groups) {
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("groupId", group.groupId());
                g.put("simple", group.isSimpleConsumerGroup());
                groupList.add(g);
            }
            groupList.sort(Comparator.comparing(a -> String.valueOf(a.get("groupId"))));
            return groupList;
        } catch (Exception e) {
            throw new RuntimeException("Kafka 消费者组列表查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> consumerGroupDetail(String connectionId, String groupId) {
        KafkaConnection conn = requireConnection(connectionId);
        try {
            // Describe consumer group
            DescribeConsumerGroupsResult describeResult = conn.adminClient().describeConsumerGroups(List.of(groupId));
            ConsumerGroupDescription groupDesc = describeResult.describedGroups().get(groupId).get();

            // List group offsets
            ListConsumerGroupOffsetsResult offsetsResult = conn.adminClient().listConsumerGroupOffsets(groupId);
            Map<org.apache.kafka.common.TopicPartition, OffsetAndMetadata> offsets = offsetsResult.partitionsToOffsetAndMetadata().get();

            // Get end offsets for partition lag calculation
            Set<org.apache.kafka.common.TopicPartition> topicPartitions = offsets.keySet();
            Map<String, Map<String, Object>> topicOffsets = new LinkedHashMap<>();
            for (Map.Entry<org.apache.kafka.common.TopicPartition, OffsetAndMetadata> entry : offsets.entrySet()) {
                String topic = entry.getKey().topic();
                topicOffsets.computeIfAbsent(topic, k -> new LinkedHashMap<>());
            }

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("groupId", groupId);
            detail.put("state", groupDesc.state().toString());

            List<Map<String, Object>> memberList = new ArrayList<>();
            for (MemberDescription member : groupDesc.members()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("memberId", member.consumerId());
                m.put("clientId", member.clientId());
                m.put("host", member.host());
                memberList.add(m);
            }
            detail.put("members", memberList);

            // Compute per-partition lag
            List<Map<String, Object>> partitionLagList = new ArrayList<>();
            for (Map.Entry<org.apache.kafka.common.TopicPartition, OffsetAndMetadata> entry : offsets.entrySet()) {
                Map<String, Object> pl = new LinkedHashMap<>();
                pl.put("topic", entry.getKey().topic());
                pl.put("partition", entry.getKey().partition());
                pl.put("currentOffset", entry.getValue().offset());
                partitionLagList.add(pl);
            }
            detail.put("partitions", partitionLagList);
            return detail;
        } catch (Exception e) {
            throw new RuntimeException("Kafka 消费者组详情查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> topicOffsets(String connectionId, String topicName) {
        KafkaConnection conn = requireConnection(connectionId);
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, conn.bootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "panel-offset-" + UUID.randomUUID().toString().substring(0, 8));
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            // 先通过 AdminClient 获取 topic 的所有分区
            DescribeTopicsResult describe = conn.adminClient().describeTopics(List.of(topicName));
            TopicDescription topicDesc = describe.topicNameValues().get(topicName).get();
            int partitionCount = topicDesc.partitions().size();

            // 构建 TopicPartition 列表
            List<TopicPartition> partitions = new ArrayList<>();
            for (int i = 0; i < partitionCount; i++) {
                partitions.add(new TopicPartition(topicName, i));
            }
            consumer.assign(partitions);

            Map<TopicPartition, Long> beginningOffsets = consumer.beginningOffsets(partitions);
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);

            List<Map<String, Object>> result = new ArrayList<>();
            long totalCount = 0;
            for (int i = 0; i < partitionCount; i++) {
                TopicPartition tp = new TopicPartition(topicName, i);
                long beginning = beginningOffsets.getOrDefault(tp, 0L);
                long end = endOffsets.getOrDefault(tp, 0L);
                long count = end - beginning;
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("partition", i);
                pm.put("beginningOffset", beginning);
                pm.put("endOffset", end);
                pm.put("count", Math.max(count, 0));
                result.add(pm);
                totalCount += Math.max(count, 0);
            }
            // 在最后一条附加 total
            if (!result.isEmpty()) {
                result.get(result.size() - 1).put("totalCount", totalCount);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Kafka topic offset 查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean resetConsumerGroupOffset(String connectionId, String groupId, String topicName, String strategy) {
        KafkaConnection conn = requireConnection(connectionId);
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, conn.bootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "panel-reset-" + UUID.randomUUID().toString().substring(0, 8));
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            // 获取 topic 的所有分区
            DescribeTopicsResult describe = conn.adminClient().describeTopics(List.of(topicName));
            TopicDescription topicDesc2 = describe.topicNameValues().get(topicName).get();
            int partitionCount = topicDesc2.partitions().size();

            List<TopicPartition> partitions = new ArrayList<>();
            for (int i = 0; i < partitionCount; i++) {
                partitions.add(new TopicPartition(topicName, i));
            }

            Map<TopicPartition, Long> targetOffsets;
            if ("latest".equalsIgnoreCase(strategy)) {
                targetOffsets = consumer.endOffsets(partitions);
            } else {
                targetOffsets = consumer.beginningOffsets(partitions);
            }

            Map<TopicPartition, OffsetAndMetadata> offsets = new LinkedHashMap<>();
            for (Map.Entry<TopicPartition, Long> entry : targetOffsets.entrySet()) {
                offsets.put(entry.getKey(), new OffsetAndMetadata(entry.getValue()));
            }

            conn.adminClient().alterConsumerGroupOffsets(groupId, offsets).all().get();
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Kafka consumer group offset 重置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean produceMessage(String connectionId, String topicName, String key, String value) {
        KafkaConnection conn = requireConnection(connectionId);
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, conn.bootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            String actualKey = (key == null || "null".equals(key) || key.isBlank()) ? null : key;
            ProducerRecord<String, String> record = new ProducerRecord<>(topicName, actualKey, value);
            producer.send(record).get(10, java.util.concurrent.TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Kafka 消息发送失败: " + e.getMessage(), e);
        }
    }

    private KafkaConnection requireConnection(String connectionId) {
        KafkaConnection connection = connectionMap.get(connectionId);
        if (connection == null) {
            throw new IllegalStateException("Kafka 连接不存在: " + connectionId);
        }
        return connection;
    }
}
