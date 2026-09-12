import io

with io.open('MonitorController.java', 'r', encoding='utf-8') as f:
    content = f.read()

insert_code = '''
    // ==================== 外部服务管理（InfluxDB / Neo4j / Nacos / ZooKeeper） ====================

    // ---------- InfluxDB 时序数据库管理 ----------

    @Operation(summary = "InfluxDB 连接测试")
    @PostMapping("/influxdb/test")
    public ReturnResult<Map<String, Object>> testInfluxdbConnection(@RequestBody Map<String, Object> config) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("connected", true);
        result.put("version", "InfluxDB 2.7");
        result.put("message", "连接成功（模拟数据）");
        return ReturnResult.ok(result);
    }

    @Operation(summary = "获取 InfluxDB 存储桶列表")
    @GetMapping("/influxdb/buckets")
    public ReturnResult<java.util.List<Map<String, Object>>> getInfluxdbBuckets(@RequestParam(required = false) Long datasourceId) {
        java.util.List<Map<String, Object>> buckets = new java.util.ArrayList<>();
        String[] names = {"telegraf", "monitoring", "system_metrics", "application_logs", "iot_data"};
        for (int i = 0; i < names.length; i++) {
            Map<String, Object> b = new java.util.LinkedHashMap<>();
            b.put("id", "bucket_" + (i + 1));
            b.put("name", names[i]);
            b.put("retentionHours", 720 + i * 24);
            b.put("seriesCount", 1000 + i * 500);
            b.put("createdAt", "2024-01-" + String.format("%02d", 10 + i) + " 10:00:00");
            buckets.add(b);
        }
        return ReturnResult.ok(buckets);
    }

    @Operation(summary = "获取 InfluxDB 测量列表")
    @GetMapping("/influxdb/measurements")
    public ReturnResult<java.util.List<Map<String, Object>>> getInfluxdbMeasurements(
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String bucket) {
        java.util.List<Map<String, Object>> measurements = new java.util.ArrayList<>();
        String[] names = {"cpu", "memory", "disk", "network", "processes", "system", "docker", "nginx"};
        for (int i = 0; i < names.length; i++) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("name", names[i]);
            m.put("fieldCount", 3 + i);
            m.put("tagCount", 2 + i % 3);
            m.put("lastWriteTime", "2024-01-15 14:" + String.format("%02d", 30 + i) + ":00");
            measurements.add(m);
        }
        return ReturnResult.ok(measurements);
    }

    @Operation(summary = "执行 InfluxDB Flux 查询")
    @PostMapping("/influxdb/query")
    public ReturnResult<Map<String, Object>> executeInfluxdbQuery(@RequestBody Map<String, Object> request) {
        String flux = request.get("flux") != null ? String.valueOf(request.get("flux")) : "";
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("query", flux);
        result.put("executionTimeMs", 45);
        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("time", "2024-01-15T14:" + String.format("%02d", 30 + i) + ":00Z");
            row.put("_measurement", "cpu");
            row.put("_field", "usage_user");
            row.put("_value", 25.5 + i * 3.2);
            row.put("host", "server-" + (i + 1));
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("rowCount", rows.size());
        return ReturnResult.ok(result);
    }

    // ---------- Neo4j 图数据库管理 ----------

    @Operation(summary = "Neo4j 连接测试")
    @PostMapping("/neo4j/test")
    public ReturnResult<Map<String, Object>> testNeo4jConnection(@RequestBody Map<String, Object> config) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("connected", true);
        result.put("version", "Neo4j 5.12");
        result.put("edition", "Community");
        result.put("message", "连接成功（模拟数据）");
        return ReturnResult.ok(result);
    }

    @Operation(summary = "获取 Neo4j 节点标签列表")
    @GetMapping("/neo4j/labels")
    public ReturnResult<java.util.List<Map<String, Object>>> getNeo4jLabels(@RequestParam(required = false) Long datasourceId) {
        java.util.List<Map<String, Object>> labels = new java.util.ArrayList<>();
        String[] names = {"Person", "Company", "Product", "Order", "Category", "Address"};
        for (int i = 0; i < names.length; i++) {
            Map<String, Object> l = new java.util.LinkedHashMap<>();
            l.put("name", names[i]);
            l.put("nodeCount", 1000 + i * 500);
            l.put("propertyCount", 5 + i);
            labels.add(l);
        }
        return ReturnResult.ok(labels);
    }

    @Operation(summary = "获取 Neo4j 关系类型列表")
    @GetMapping("/neo4j/relationships")
    public ReturnResult<java.util.List<Map<String, Object>>> getNeo4jRelationships(@RequestParam(required = false) Long datasourceId) {
        java.util.List<Map<String, Object>> rels = new java.util.ArrayList<>();
        String[] names = {"WORKS_AT", "OWNS", "PURCHASED", "BELONGS_TO", "LIVES_IN", "FRIEND_OF"};
        for (int i = 0; i < names.length; i++) {
            Map<String, Object> r = new java.util.LinkedHashMap<>();
            r.put("type", names[i]);
            r.put("relationshipCount", 500 + i * 200);
            r.put("propertyCount", 2 + i % 3);
            rels.add(r);
        }
        return ReturnResult.ok(rels);
    }

    @Operation(summary = "执行 Neo4j Cypher 查询")
    @PostMapping("/neo4j/query")
    public ReturnResult<Map<String, Object>> executeNeo4jQuery(@RequestBody Map<String, Object> request) {
        String cypher = request.get("cypher") != null ? String.valueOf(request.get("cypher")) : "";
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("query", cypher);
        result.put("executionTimeMs", 38);
        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", i + 1);
            row.put("name", "Node_" + (i + 1));
            row.put("labels", java.util.Collections.singletonList("Person"));
            row.put("properties", java.util.Collections.singletonMap("age", 25 + i * 5));
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("rowCount", rows.size());
        return ReturnResult.ok(result);
    }

    // ---------- Nacos 配置中心管理 ----------

    @Operation(summary = "Nacos 连接测试")
    @PostMapping("/nacos/test")
    public ReturnResult<Map<String, Object>> testNacosConnection(@RequestBody Map<String, Object> config) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("connected", true);
        result.put("version", "Nacos 2.3");
        result.put("message", "连接成功（模拟数据）");
        return ReturnResult.ok(result);
    }

    @Operation(summary = "获取 Nacos 命名空间列表")
    @GetMapping("/nacos/namespaces")
    public ReturnResult<java.util.List<Map<String, Object>>> getNacosNamespaces(@RequestParam(required = false) Long datasourceId) {
        java.util.List<Map<String, Object>> namespaces = new java.util.ArrayList<>();
        String[][] data = {{"public", "public", "默认命名空间", "45"}, {"dev", "dev", "开发环境", "23"}, {"prod", "prod", "生产环境", "67"}};
        for (String[] d : data) {
            Map<String, Object> ns = new java.util.LinkedHashMap<>();
            ns.put("id", d[0]);
            ns.put("namespace", d[1]);
            ns.put("name", d[2]);
            ns.put("configCount", Integer.parseInt(d[3]));
            namespaces.add(ns);
        }
        return ReturnResult.ok(namespaces);
    }

    @Operation(summary = "获取 Nacos 配置列表")
    @GetMapping("/nacos/configs")
    public ReturnResult<Map<String, Object>> getNacosConfigs(
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false, defaultValue = "public") String namespace,
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String dataId,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "10") int pageSize) {
        java.util.List<Map<String, Object>> configs = new java.util.ArrayList<>();
        String[][] data = {
            {"application.yml", "DEFAULT_GROUP", "yaml", "2024-01-15 10:30:00"},
            {"datasource.properties", "DEFAULT_GROUP", "properties", "2024-01-14 16:20:00"},
            {"redis-config.json", "DEFAULT_GROUP", "json", "2024-01-13 09:15:00"}
        };
        for (String[] d : data) {
            Map<String, Object> c = new java.util.LinkedHashMap<>();
            c.put("dataId", d[0]);
            c.put("group", d[1]);
            c.put("format", d[2]);
            c.put("updatedAt", d[3]);
            configs.add(c);
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("list", configs);
        result.put("total", configs.size());
        result.put("page", page);
        result.put("pageSize", pageSize);
        return ReturnResult.ok(result);
    }

    @Operation(summary = "获取 Nacos 配置详情")
    @GetMapping("/nacos/config/{dataId}")
    public ReturnResult<Map<String, Object>> getNacosConfigDetail(
            @PathVariable String dataId,
            @RequestParam(required = false, defaultValue = "public") String namespace,
            @RequestParam(required = false, defaultValue = "DEFAULT_GROUP") String group) {
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("dataId", dataId);
        config.put("group", group);
        config.put("namespace", namespace);
        config.put("format", "yaml");
        config.put("content", "server:\\n  port: 8080\\nspring:\\n  application:\\n    name: monitor-app\\n");
        config.put("createdAt", "2024-01-10 10:00:00");
        config.put("updatedAt", "2024-01-15 10:30:00");
        return ReturnResult.ok(config);
    }

    @Operation(summary = "保存 Nacos 配置")
    @PostMapping("/nacos/config")
    public ReturnResult<Boolean> saveNacosConfig(@RequestBody Map<String, Object> config) {
        return ReturnResult.ok(true);
    }

    @Operation(summary = "删除 Nacos 配置")
    @DeleteMapping("/nacos/config/{dataId}")
    public ReturnResult<Boolean> deleteNacosConfig(
            @PathVariable String dataId,
            @RequestParam(required = false, defaultValue = "public") String namespace,
            @RequestParam(required = false, defaultValue = "DEFAULT_GROUP") String group) {
        return ReturnResult.ok(true);
    }

    @Operation(summary = "获取 Nacos 服务列表")
    @GetMapping("/nacos/services")
    public ReturnResult<java.util.List<Map<String, Object>>> getNacosServices(
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false, defaultValue = "public") String namespace) {
        java.util.List<Map<String, Object>> services = new java.util.ArrayList<>();
        String[][] data = {{"monitor-service", "3", "2", "1"}, {"gateway-service", "5", "5", "0"}, {"user-service", "4", "3", "1"}};
        for (String[] d : data) {
            Map<String, Object> s = new java.util.LinkedHashMap<>();
            s.put("name", d[0]);
            s.put("clusterCount", Integer.parseInt(d[1]));
            s.put("healthyInstanceCount", Integer.parseInt(d[2]));
            s.put("unhealthyInstanceCount", Integer.parseInt(d[3]));
            s.put("triggerFlag", false);
            services.add(s);
        }
        return ReturnResult.ok(services);
    }

    @Operation(summary = "获取 Nacos 集群节点列表")
    @GetMapping("/nacos/cluster/nodes")
    public ReturnResult<java.util.List<Map<String, Object>>> getNacosClusterNodes(@RequestParam(required = false) Long datasourceId) {
        java.util.List<Map<String, Object>> nodes = new java.util.ArrayList<>();
        String[][] data = {{"192.168.1.10", "8848", "UP", "LEADER"}, {"192.168.1.11", "8848", "UP", "FOLLOWER"}, {"192.168.1.12", "8848", "UP", "FOLLOWER"}};
        for (String[] d : data) {
            Map<String, Object> n = new java.util.LinkedHashMap<>();
            n.put("ip", d[0]);
            n.put("port", Integer.parseInt(d[1]));
            n.put("status", d[2]);
            n.put("role", d[3]);
            n.put("weight", 1.0);
            nodes.add(n);
        }
        return ReturnResult.ok(nodes);
    }

    // ---------- ZooKeeper 注册中心管理 ----------

    @Operation(summary = "ZooKeeper 连接测试")
    @PostMapping("/zookeeper/test")
    public ReturnResult<Map<String, Object>> testZookeeperConnection(@RequestBody Map<String, Object> config) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("connected", true);
        result.put("version", "ZooKeeper 3.9");
        result.put("sessionId", "0x10001234567890ab");
        result.put("message", "连接成功（模拟数据）");
        return ReturnResult.ok(result);
    }

    @Operation(summary = "获取 ZooKeeper 节点树")
    @GetMapping("/zookeeper/tree")
    public ReturnResult<java.util.List<Map<String, Object>>> getZookeeperTree(
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false, defaultValue = "/") String path) {
        java.util.List<Map<String, Object>> nodes = new java.util.ArrayList<>();
        String[][] data = {{"/services", "false", "3"}, {"/config", "false", "5"}, {"/registry", "false", "2"}, {"/services/monitor-service", "false", "2"}, {"/services/gateway-service", "true", "0"}};
        for (String[] d : data) {
            Map<String, Object> n = new java.util.LinkedHashMap<>();
            n.put("path", d[0]);
            n.put("name", d[0].substring(d[0].lastIndexOf("/") + 1));
            n.put("ephemeral", Boolean.parseBoolean(d[1]));
            n.put("childrenCount", Integer.parseInt(d[2]));
            nodes.add(n);
        }
        return ReturnResult.ok(nodes);
    }

    @Operation(summary = "获取 ZooKeeper 节点详情")
    @GetMapping("/zookeeper/node")
    public ReturnResult<Map<String, Object>> getZookeeperNodeDetail(
            @RequestParam(required = false) Long datasourceId,
            @RequestParam String path) {
        Map<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("path", path);
        node.put("ephemeral", false);
        node.put("createdAt", "2024-01-10 10:00:00");
        node.put("updatedAt", "2024-01-15 14:30:00");
        node.put("version", 3);
        node.put("numChildren", 2);
        node.put("dataLength", 128);
        node.put("data", "{\\"service\\":\\"monitor-service\\",\\"port\\":8080,\\"version\\":\\"1.0.0\\"}");
        return ReturnResult.ok(node);
    }

    @Operation(summary = "获取 ZooKeeper 子节点列表")
    @GetMapping("/zookeeper/children")
    public ReturnResult<java.util.List<Map<String, Object>>> getZookeeperChildren(
            @RequestParam(required = false) Long datasourceId,
            @RequestParam String path) {
        java.util.List<Map<String, Object>> children = new java.util.ArrayList<>();
        String[][] data = {{"node-1", "false", "{\\"host\\":\\"192.168.1.10\\",\\"port\\":8080}"}, {"node-2", "true", "{\\"host\\":\\"192.168.1.11\\",\\"port\\":8080}"}};
        for (String[] d : data) {
            Map<String, Object> c = new java.util.LinkedHashMap<>();
            c.put("name", d[0]);
            c.put("path", path + "/" + d[0]);
            c.put("ephemeral", Boolean.parseBoolean(d[1]));
            c.put("data", d[2]);
            children.add(c);
        }
        return ReturnResult.ok(children);
    }

    @Operation(summary = "创建 ZooKeeper 节点")
    @PostMapping("/zookeeper/node")
    public ReturnResult<Boolean> createZookeeperNode(@RequestBody Map<String, Object> request) {
        return ReturnResult.ok(true);
    }

    @Operation(summary = "更新 ZooKeeper 节点数据")
    @PutMapping("/zookeeper/node")
    public ReturnResult<Boolean> updateZookeeperNode(@RequestBody Map<String, Object> request) {
        return ReturnResult.ok(true);
    }

    @Operation(summary = "删除 ZooKeeper 节点")
    @DeleteMapping("/zookeeper/node")
    public ReturnResult<Boolean> deleteZookeeperNode(@RequestParam String path) {
        return ReturnResult.ok(true);
    }
'''

last_brace = content.rfind('}')
if last_brace > 0:
    content = content[:last_brace] + insert_code + '\n' + content[last_brace:]
    with io.open('MonitorController.java', 'w', encoding='utf-8') as f:
        f.write(content)
    print('成功添加 4 个外部服务管理接口')
else:
    print('未找到文件末尾')
