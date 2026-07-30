package com.chua.starter.panel.service.impl;

import com.chua.starter.panel.service.MongoPanelService;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 默认 MongoDB 面板服务（基于 mongodb-driver-sync）。
 *
 * @author CH
 */
public class DefaultMongoPanelService implements MongoPanelService {

    private final Map<String, MongoClient> clientMap = new ConcurrentHashMap<>();

    @Override
    public String open(String host, int port, String username, String password, String authDatabase) {
        String connectionId = UUID.randomUUID().toString();

        MongoClientSettings.Builder builder = MongoClientSettings.builder()
                .applyToClusterSettings(b -> b.hosts(Collections.singletonList(new ServerAddress(host, port))))
                .applyToConnectionPoolSettings(b -> b.maxSize(8).minSize(1))
                .applyToSocketSettings(b -> b.connectTimeout(5, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS));

        if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
            String authDb = (authDatabase != null && !authDatabase.isBlank()) ? authDatabase : "admin";
            builder.credential(MongoCredential.createCredential(username, authDb, password.toCharArray()));
        }

        MongoClient client = MongoClients.create(builder.build());
        // 测试连接
        client.getDatabase("admin").runCommand(new Document("ping", 1));
        clientMap.put(connectionId, client);
        return connectionId;
    }

    @Override
    public void close(String connectionId) {
        MongoClient client = clientMap.remove(connectionId);
        if (client != null) {
            client.close();
        }
    }

    @Override
    public List<String> listDatabases(String connectionId) {
        MongoClient client = getClient(connectionId);
        List<String> names = new ArrayList<>();
        for (Document doc : client.listDatabases()) {
            names.add(doc.getString("name"));
        }
        return names;
    }

    @Override
    public List<String> listCollections(String connectionId, String database) {
        MongoDatabase db = getClient(connectionId).getDatabase(database);
        List<String> names = new ArrayList<>();
        for (String name : db.listCollectionNames()) {
            names.add(name);
        }
        return names;
    }

    @Override
    public List<Map<String, Object>> find(String connectionId, String database, String collection,
                                          String filterJson, String sortJson, int limit, int skip) {
        MongoCollection<Document> col = getCollection(connectionId, database, collection);
        Bson filter = parseDoc(filterJson);
        FindIterable<Document> iterable = col.find(filter);
        if (sortJson != null && !sortJson.isBlank() && !sortJson.equals("{}")) {
            iterable = iterable.sort(parseDoc(sortJson));
        }
        if (skip > 0) iterable = iterable.skip(skip);
        iterable = iterable.limit(limit <= 0 ? 20 : Math.min(limit, 500));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Document doc : iterable) {
            result.add(docToMap(doc));
        }
        return result;
    }

    @Override
    public Map<String, Object> insertOne(String connectionId, String database, String collection, String documentJson) {
        MongoCollection<Document> col = getCollection(connectionId, database, collection);
        Document doc = Document.parse(documentJson);
        col.insertOne(doc);
        return docToMap(doc);
    }

    @Override
    public long updateOne(String connectionId, String database, String collection, String filterJson, String updateJson) {
        MongoCollection<Document> col = getCollection(connectionId, database, collection);
        Document update = Document.parse(updateJson);
        // 如果 update 没有 $set 等操作符，自动包装
        if (update.keySet().stream().noneMatch(k -> k.startsWith("$"))) {
            update = new Document("$set", update);
        }
        return col.updateOne(parseDoc(filterJson), update).getModifiedCount();
    }

    @Override
    public long deleteOne(String connectionId, String database, String collection, String filterJson) {
        MongoCollection<Document> col = getCollection(connectionId, database, collection);
        return col.deleteOne(parseDoc(filterJson)).getDeletedCount();
    }

    @Override
    public List<Map<String, Object>> aggregate(String connectionId, String database, String collection, String pipelineJson) {
        MongoCollection<Document> col = getCollection(connectionId, database, collection);
        List<Document> pipeline = new ArrayList<>();
        // pipelineJson 应为 JSON 数组
        Document wrapper = Document.parse("{\"pipeline\":" + pipelineJson + "}");
        @SuppressWarnings("unchecked")
        List<Document> stages = (List<Document>) wrapper.get("pipeline");
        if (stages != null) pipeline.addAll(stages);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Document doc : col.aggregate(pipeline)) {
            result.add(docToMap(doc));
        }
        return result;
    }

    @Override
    public String createIndex(String connectionId, String database, String collection, String keysJson, boolean unique) {
        MongoCollection<Document> col = getCollection(connectionId, database, collection);
        IndexOptions options = new IndexOptions().unique(unique);
        return col.createIndex(Document.parse(keysJson), options);
    }

    @Override
    public void dropCollection(String connectionId, String database, String collection) {
        getCollection(connectionId, database, collection).drop();
    }

    @Override
    public Map<String, Object> stats(String connectionId, String database, String collection) {
        MongoDatabase db = getClient(connectionId).getDatabase(database);
        Document result = db.runCommand(new Document("collStats", collection));
        return docToMap(result);
    }

    @Override
    public Map<String, Object> dbStats(String connectionId, String database) {
        MongoDatabase db = getClient(connectionId).getDatabase(database);
        Document result = db.runCommand(new Document("dbStats", 1));
        return docToMap(result);
    }

    @Override
    public Object executeCommand(String connectionId, String database, String commandJson) {
        MongoDatabase db = getClient(connectionId).getDatabase(database);
        Document result = db.runCommand(Document.parse(commandJson));
        return docToMap(result);
    }

    // ─── 工具方法 ─────────────────────────────────────────────────────────────

    private MongoClient getClient(String connectionId) {
        MongoClient client = clientMap.get(connectionId);
        if (client == null) throw new IllegalStateException("MongoDB 连接不存在: " + connectionId);
        return client;
    }

    private MongoCollection<Document> getCollection(String connectionId, String database, String collection) {
        return getClient(connectionId).getDatabase(database).getCollection(collection);
    }

    private Bson parseDoc(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) return new Document();
        return Document.parse(json);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> docToMap(Document doc) {
        if (doc == null) return new LinkedHashMap<>();
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : doc.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof Document) {
                val = docToMap((Document) val);
            } else if (val instanceof List) {
                val = convertList((List<?>) val);
            } else if (val instanceof org.bson.types.ObjectId) {
                val = val.toString();
            }
            map.put(entry.getKey(), val);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<Object> convertList(List<?> list) {
        List<Object> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Document) {
                result.add(docToMap((Document) item));
            } else if (item instanceof List) {
                result.add(convertList((List<?>) item));
            } else if (item instanceof org.bson.types.ObjectId) {
                result.add(item.toString());
            } else {
                result.add(item);
            }
        }
        return result;
    }
}
