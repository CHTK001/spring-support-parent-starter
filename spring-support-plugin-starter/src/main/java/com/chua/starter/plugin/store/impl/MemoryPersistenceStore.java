package com.chua.starter.plugin.store.impl;

import com.chua.starter.plugin.store.*;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 内存持久化存储实现
 * 
 * @author CH
 * @since 2025/1/16
 */
@Slf4j
public class MemoryPersistenceStore<T, ID> implements PersistenceStore<T, ID> {

    private final Map<ID, T> dataMap = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    private final Class<T> entityClass;
    private final String storeName;
    private volatile boolean initialized = false;

    public MemoryPersistenceStore(Class<T> entityClass) {
        this.entityClass = entityClass;
        this.storeName = "Memory-" + entityClass.getSimpleName();
    }

    @Override
    public void initialize() {
        if (!initialized) {
            log.info("Initializing memory store for {}", entityClass.getSimpleName());
            initialized = true;
        }
    }

    @Override
    public void destroy() {
        if (initialized) {
            dataMap.clear();
            initialized = false;
            log.info("Destroyed memory store for {}", entityClass.getSimpleName());
        }
    }

    @Override
    public boolean isAvailable() {
        return initialized;
    }

    @Override
    public String getName() {
        return storeName;
    }

    @Override
    public T save(T entity) {
        if (entity == null) {
            return null;
        }

        try {
            // 如果ID为空，生成新ID
            ID id = getId(entity);
            if (id == null) {
                id = generateId();
                setId(entity, id);
            }

            // 调用onCreate或onUpdate
            if (dataMap.containsKey(id)) {
                callOnUpdate(entity);
            } else {
                callOnCreate(entity);
            }

            dataMap.put(id, entity);
            log.debug("Saved entity with id: {}", id);
            return entity;
        } catch (Exception e) {
            log.error("Failed to save entity", e);
            return null;
        }
    }

    @Override
    public List<T> saveAll(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return new ArrayList<>();
        }

        List<T> savedEntities = new ArrayList<>();
        for (T entity : entities) {
            T saved = save(entity);
            if (saved != null) {
                savedEntities.add(saved);
            }
        }
        return savedEntities;
    }

    @Override
    public Optional<T> findById(ID id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(dataMap.get(id));
    }

    @Override
    public List<T> findAll() {
        return new ArrayList<>(dataMap.values());
    }

    @Override
    public List<T> findByCondition(QueryCondition condition) {
        if (condition == null) {
            return findAll();
        }

        return dataMap.values().stream()
                .filter(entity -> matchesCondition(entity, condition))
                .sorted((e1, e2) -> compareByOrderBy(e1, e2, condition.getOrderBys()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean deleteById(ID id) {
        if (id == null) {
            return false;
        }
        T removed = dataMap.remove(id);
        if (removed != null) {
            log.debug("Deleted entity with id: {}", id);
            return true;
        }
        return false;
    }

    @Override
    public boolean delete(T entity) {
        if (entity == null) {
            return false;
        }
        try {
            ID id = getId(entity);
            return deleteById(id);
        } catch (Exception e) {
            log.error("Failed to delete entity", e);
            return false;
        }
    }

    @Override
    public int deleteByIds(List<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        int deleted = 0;
        for (ID id : ids) {
            if (deleteById(id)) {
                deleted++;
            }
        }
        return deleted;
    }

    @Override
    public int deleteByCondition(QueryCondition condition) {
        List<T> toDelete = findByCondition(condition);
        int deleted = 0;
        for (T entity : toDelete) {
            if (delete(entity)) {
                deleted++;
            }
        }
        return deleted;
    }

    @Override
    public boolean existsById(ID id) {
        return id != null && dataMap.containsKey(id);
    }

    @Override
    public long count() {
        return dataMap.size();
    }

    @Override
    public long countByCondition(QueryCondition condition) {
        return findByCondition(condition).size();
    }

    @Override
    public boolean update(T entity) {
        if (entity == null) {
            return false;
        }

        try {
            ID id = getId(entity);
            if (id != null && dataMap.containsKey(id)) {
                callOnUpdate(entity);
                dataMap.put(id, entity);
                log.debug("Updated entity with id: {}", id);
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to update entity", e);
        }
        return false;
    }

    @Override
    public int updateByCondition(UpdateData updateData, QueryCondition condition) {
        List<T> toUpdate = findByCondition(condition);
        int updated = 0;

        for (T entity : toUpdate) {
            try {
                // 应用更新数据
                for (Map.Entry<String, Object> entry : updateData.getFields().entrySet()) {
                    setFieldValue(entity, entry.getKey(), entry.getValue());
                }
                callOnUpdate(entity);
                updated++;
            } catch (Exception e) {
                log.error("Failed to update entity", e);
            }
        }

        return updated;
    }

    @Override
    public PageResult<T> findPage(QueryCondition condition, int page, int size) {
        List<T> allResults = findByCondition(condition);
        long total = allResults.size();

        int startIndex = (page - 1) * size;
        int endIndex = Math.min(startIndex + size, allResults.size());

        List<T> pageContent;
        if (startIndex >= allResults.size()) {
            pageContent = new ArrayList<>();
        } else {
            pageContent = allResults.subList(startIndex, endIndex);
        }

        return PageResult.of(pageContent, page, size, total);
    }

    @Override
    public StoreType getStoreType() {
        return StoreType.MEMORY;
    }

    // 私有辅助方法

    @SuppressWarnings("unchecked")
    private ID generateId() {
        return (ID) idGenerator.getAndIncrement();
    }

    @SuppressWarnings("unchecked")
    private ID getId(T entity) throws Exception {
        Field idField = findIdField(entity.getClass());
        if (idField != null) {
            idField.setAccessible(true);
            return (ID) idField.get(entity);
        }
        return null;
    }

    private void setId(T entity, ID id) throws Exception {
        Field idField = findIdField(entity.getClass());
        if (idField != null) {
            idField.setAccessible(true);
            idField.set(entity, id);
        }
    }

    private Field findIdField(Class<?> clazz) {
        // 查找名为"id"的字段
        try {
            return clazz.getDeclaredField("id");
        } catch (NoSuchFieldException e) {
            // 如果没有找到，查找父类
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null && superClass != Object.class) {
                return findIdField(superClass);
            }
        }
        return null;
    }

    private void callOnCreate(T entity) {
        try {
            entity.getClass().getMethod("onCreate").invoke(entity);
        } catch (Exception e) {
            // 忽略，实体可能没有onCreate方法
        }
    }

    private void callOnUpdate(T entity) {
        try {
            entity.getClass().getMethod("onUpdate").invoke(entity);
        } catch (Exception e) {
            // 忽略，实体可能没有onUpdate方法
        }
    }

    private boolean matchesCondition(T entity, QueryCondition condition) {
        for (QueryCondition.Condition cond : condition.getConditions()) {
            if (!matchesSingleCondition(entity, cond)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesSingleCondition(T entity, QueryCondition.Condition condition) {
        try {
            Object fieldValue = getFieldValue(entity, condition.getField());
            return compareValues(fieldValue, condition.getOperator(), condition.getValue());
        } catch (Exception e) {
            log.warn("Failed to match condition for field: {}", condition.getField(), e);
            return false;
        }
    }

    private Object getFieldValue(T entity, String fieldName) throws Exception {
        Field field = findField(entity.getClass(), fieldName);
        if (field != null) {
            field.setAccessible(true);
            return field.get(entity);
        }
        return null;
    }

    private void setFieldValue(T entity, String fieldName, Object value) throws Exception {
        Field field = findField(entity.getClass(), fieldName);
        if (field != null) {
            field.setAccessible(true);
            field.set(entity, value);
        }
    }

    private Field findField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null && superClass != Object.class) {
                return findField(superClass, fieldName);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private boolean compareValues(Object fieldValue, QueryCondition.Operator operator, Object conditionValue) {
        switch (operator) {
            case EQ:
                return Objects.equals(fieldValue, conditionValue);
            case NE:
                return !Objects.equals(fieldValue, conditionValue);
            case GT:
                return fieldValue instanceof Comparable && conditionValue instanceof Comparable &&
                        ((Comparable<Object>) fieldValue).compareTo(conditionValue) > 0;
            case GE:
                return fieldValue instanceof Comparable && conditionValue instanceof Comparable &&
                        ((Comparable<Object>) fieldValue).compareTo(conditionValue) >= 0;
            case LT:
                return fieldValue instanceof Comparable && conditionValue instanceof Comparable &&
                        ((Comparable<Object>) fieldValue).compareTo(conditionValue) < 0;
            case LE:
                return fieldValue instanceof Comparable && conditionValue instanceof Comparable &&
                        ((Comparable<Object>) fieldValue).compareTo(conditionValue) <= 0;
            case LIKE:
                return fieldValue != null && conditionValue != null &&
                        fieldValue.toString().contains(conditionValue.toString());
            case IN:
                return conditionValue instanceof List &&
                        ((List<?>) conditionValue).contains(fieldValue);
            case IS_NULL:
                return fieldValue == null;
            case IS_NOT_NULL:
                return fieldValue != null;
            default:
                return false;
        }
    }

    @SuppressWarnings("unchecked")
    private int compareByOrderBy(T e1, T e2, List<QueryCondition.OrderBy> orderBys) {
        for (QueryCondition.OrderBy orderBy : orderBys) {
            try {
                Object v1 = getFieldValue(e1, orderBy.getField());
                Object v2 = getFieldValue(e2, orderBy.getField());

                int result = 0;
                if (v1 instanceof Comparable && v2 instanceof Comparable) {
                    result = ((Comparable<Object>) v1).compareTo(v2);
                } else if (v1 != null && v2 != null) {
                    result = v1.toString().compareTo(v2.toString());
                } else if (v1 == null && v2 != null) {
                    result = -1;
                } else if (v1 != null && v2 == null) {
                    result = 1;
                }

                if (result != 0) {
                    return orderBy.getDirection() == QueryCondition.OrderDirection.DESC ? -result : result;
                }
            } catch (Exception e) {
                log.warn("Failed to compare by field: {}", orderBy.getField(), e);
            }
        }
        return 0;
    }
}
