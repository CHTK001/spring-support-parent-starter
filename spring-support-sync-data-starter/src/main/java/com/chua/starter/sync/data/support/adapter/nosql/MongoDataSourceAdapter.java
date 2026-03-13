package com.chua.starter.sync.data.support.adapter.nosql;

import com.chua.starter.sync.data.support.adapter.*;
import com.mongodb.client.*;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * MongoDB数据源适配器
 */
@Slf4j
public class MongoDataSourceAdapter implements DataSourceAdapter {
    
    private MongoClient mongoClient;
    private MongoDatabase database;
    private DataSourceConfig config;
    
    @Override
    public void connect(DataSourceConfig config) throws DataSourceException {
        this.config = config;
        try {
            this.mongoClient = MongoClients.create(config.getUrl());
            this.database = mongoClient.getDatabase(config.getDatabase());
            log.info("MongoDB连接成功: {}", config.getUrl());
        } catch (Exception e) {
            throw new DataSourceException("MongoDB连接失败", e);
        }
    }
    
    @Override
    public Stream<Map<String, Object>> read(ReadConfig readConfig) {
        String collection = readConfig.getCollection();
        String filter = readConfig.getFilter() != null ? readConfig.getFilter() : "{}";
        int batchSize = readConfig.getBatchSize() > 0 ? readConfig.getBatchSize() : 1000;
        
        MongoCollection<Document> coll = database.getCollection(collection);
        FindIterable<Document> iterable = coll.find(Document.parse(filter)).batchSize(batchSize);
        
        return StreamSupport.stream(iterable.spliterator(), false)
            .map(doc -> new HashMap<String, Object>(doc));
    }
    
    @Override
    public void write(List<Map<String, Object>> records, WriteConfig writeConfig) {
        if (records == null || records.isEmpty()) {
            return;
        }
        
        String collection = writeConfig.getCollection();
        MongoCollection<Document> coll = database.getCollection(collection);
        
        List<Document> documents = new ArrayList<>(records.size());
        for (Map<String, Object> record : records) {
            documents.add(new Document(record));
        }
        
        coll.insertMany(documents);
        log.debug("批量写入{}条记录到集合: {}", records.size(), collection);
    }
    
    @Override
    public boolean testConnection() {
        try {
            database.listCollectionNames().first();
            return true;
        } catch (Exception e) {
            log.error("测试MongoDB连接失败", e);
            return false;
        }
    }
    
    @Override
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            log.info("MongoDB连接已关闭");
        }
    }
    
    @Override
    public DataSourceMetadata getMetadata() {
        DataSourceMetadata metadata = new DataSourceMetadata();
        metadata.setDatabaseType("MongoDB");
        metadata.setDatabaseVersion(database.runCommand(new Document("buildInfo", 1))
            .getString("version"));
        return metadata;
    }
}
