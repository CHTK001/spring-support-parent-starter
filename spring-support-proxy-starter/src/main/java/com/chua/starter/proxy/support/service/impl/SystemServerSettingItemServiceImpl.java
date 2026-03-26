package com.chua.starter.proxy.support.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.proxy.support.entity.SystemServerSettingItem;
import com.chua.starter.proxy.support.mapper.SystemServerSettingItemMapper;
import com.chua.starter.proxy.support.service.server.SystemServerSettingItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 系统服务器配置项详情表服务实现类
 *
 * @author CH
 * @since 2025/01/07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemServerSettingItemServiceImpl extends ServiceImpl<SystemServerSettingItemMapper, SystemServerSettingItem> implements SystemServerSettingItemService {

    /**
     * AES加密算法
     */
    private static final String ALGORITHM = "AES";
    
    /**
     * AES加密密钥（实际项目中应该从配置文件或环境变量中获取）
     */
    private static final String SECRET_KEY = "MySecretKey12345"; // 16字节密钥

    @Override
    public IPage<SystemServerSettingItem> pageFor(Page<SystemServerSettingItem> page, SystemServerSettingItem entity) {
        return baseMapper.pageFor(page, entity);
    }

    @Override
    public ReturnResult<List<SystemServerSettingItem>> getBySettingId(Integer settingId) {
        try {
            List<SystemServerSettingItem> items = baseMapper.selectBySettingId(settingId);
            log.info("获取配置项成功: settingId={}, 配置项数量={}", settingId, items.size());
            return ReturnResult.ok(items);
        } catch (Exception e) {
            log.error("获取配置项失败: settingId={}", settingId, e);
            return ReturnResult.error("获取配置项失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ReturnResult<Boolean> batchSaveItems(Integer settingId, List<SystemServerSettingItem> items) {
        try {
            // 先删除现有配置项
            deleteBySettingId(settingId);

            // 保存新配置项
            for (int i = 0; i < items.size(); i++) {
                SystemServerSettingItem item = items.get(i);
                item.setSystemServerSettingItemId(null);
                item.setSystemServerSettingItemSettingId(settingId);
                item.setSystemServerSettingItemOrder(i + 1);
                save(item);
            }

            log.info("批量保存配置项成功: settingId={}, 配置项数量={}", settingId, items.size());
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("批量保存配置项失败: settingId={}", settingId, e);
            return ReturnResult.error("批量保存配置项失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ReturnResult<Boolean> updateItemValue(Integer itemId, String value) {
        try {
            SystemServerSettingItem item = getById(itemId);
            if (item == null) {
                return ReturnResult.error("配置项不存在");
            }

            // 验证配置项值
            item.setSystemServerSettingItemValue(value);
            ReturnResult<Boolean> validateResult = validateItemValue(item);
            if (!validateResult.isOk()) {
                return validateResult;
            }

            baseMapper.updateValue(itemId, value);

            log.info("更新配置项值成功: itemId={}, value={}", itemId, value);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("更新配置项值失败: itemId={}, value={}", itemId, value, e);
            return ReturnResult.error("更新配置项值失败: " + e.getMessage());
        }
    }

    /**
     * 重写save方法，在保存前加密敏感数据
     */
    @Override
    public boolean save(SystemServerSettingItem entity) {
        if (entity != null) {
            encryptSensitiveData(entity);
        }
        return super.save(entity);
    }

    /**
     * 重写updateById方法，在更新前加密敏感数据
     */
    @Override
    public boolean updateById(SystemServerSettingItem entity) {
        if (entity != null) {
            encryptSensitiveData(entity);
        }
        return super.updateById(entity);
    }

    /**
     * 重写getById方法，在查询后解密敏感数据
     */
    @Override
    public SystemServerSettingItem getById(Serializable id) {
        SystemServerSettingItem entity = super.getById(id);
        if (entity != null) {
            decryptSensitiveData(entity);
        }
        return entity;
    }

    /**
     * 重写list方法，在查询后解密敏感数据
     */
    @Override
    public List<SystemServerSettingItem> list() {
        List<SystemServerSettingItem> entities = super.list();
        if (entities != null) {
            entities.forEach(this::decryptSensitiveData);
        }
        return entities;
    }


    /**
     * 加密SystemServerSettingItem中的敏感数据
     *
     * @param item 配置项对象
     */
    private void encryptSensitiveData(SystemServerSettingItem item) {
        if (item == null) {
            return;
        }
        
        // 加密配置项值（如果包含敏感信息）
        if (item.getSystemServerSettingItemValue() != null && !item.getSystemServerSettingItemValue().isEmpty()) {
            String itemName = item.getSystemServerSettingItemName();
            // 判断是否为敏感字段
            if (isSensitiveField(itemName)) {
                item.setSystemServerSettingItemValue(encrypt(item.getSystemServerSettingItemValue()));
            }
        }
    }

    /**
     * 解密SystemServerSettingItem中的敏感数据
     *
     * @param item 配置项对象
     */
    private void decryptSensitiveData(SystemServerSettingItem item) {
        if (item == null) {
            return;
        }
        
        // 解密配置项值（如果包含敏感信息）
        if (item.getSystemServerSettingItemValue() != null && !item.getSystemServerSettingItemValue().isEmpty()) {
            String itemName = item.getSystemServerSettingItemName();
            // 判断是否为敏感字段
            if (isSensitiveField(itemName)) {
                item.setSystemServerSettingItemValue(decrypt(item.getSystemServerSettingItemValue()));
            }
        }
    }

    /**
     * 判断字段是否为敏感字段
     *
     * @param fieldName 字段名称
     * @return 是否为敏感字段
     */
    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        
        String lowerFieldName = fieldName.toLowerCase();
        return lowerFieldName.contains("password") || 
               lowerFieldName.contains("secret") || 
               lowerFieldName.contains("key") || 
               lowerFieldName.contains("token") || 
               lowerFieldName.contains("credential");
    }

    /**
     * AES加密
     *
     * @param data 待加密数据
     * @return 加密后的数据
     */
    private String encrypt(String data) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedData);
        } catch (Exception e) {
            log.error("加密失败", e);
            return data; // 加密失败时返回原数据
        }
    }

    /**
     * AES解密
     *
     * @param encryptedData 待解密数据
     * @return 解密后的数据
     */
    private String decrypt(String encryptedData) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decodedData = Base64.getDecoder().decode(encryptedData);
            byte[] decryptedData = cipher.doFinal(decodedData);
            return new String(decryptedData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("解密失败", e);
            return encryptedData; // 解密失败时返回原数据
        }
    }

    @Override
    @Transactional
    public ReturnResult<Boolean> batchUpdateItemValues(List<Map<String, Object>> updates) {
        try {
            for (Map<String, Object> update : updates) {
                Integer itemId = (Integer) update.get("itemId");
                String value = String.valueOf(update.get("value"));

                ReturnResult<Boolean> updateResult = updateItemValue(itemId, value);
                if (!updateResult.isOk()) {
                    return updateResult;
                }
            }

            log.info("批量更新配置项值成功，更新数量: {}", updates.size());
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("批量更新配置项值失败", e);
            return ReturnResult.error("批量更新配置项值失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ReturnResult<Boolean> deleteBySettingId(Integer settingId) {
        try {
            int deletedCount = baseMapper.deleteBySettingId(settingId);
            log.info("删除配置项成功: settingId={}, 删除数量={}", settingId, deletedCount);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("删除配置项失败: settingId={}", settingId, e);
            return ReturnResult.error("删除配置项失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> validateItemValue(SystemServerSettingItem item) {
        try {
            String value = item.getSystemServerSettingItemValue();
            String type = item.getSystemServerSettingItemType();
            String validationRule = item.getSystemServerSettingItemValidationRule();
            Boolean required = item.getSystemServerSettingItemRequired();

            // 检查必填项
            if (Boolean.TRUE.equals(required) && StringUtils.isEmpty(value)) {
                return ReturnResult.error("配置项 " + item.getSystemServerSettingItemName() + " 为必填项");
            }

            // 如果值为空且非必填，则验证通过
            if (StringUtils.isEmpty(value)) {
                return ReturnResult.ok(true);
            }

            // 根据类型验证
            if (StringUtils.isNotEmpty(type)) {
                switch (type.toLowerCase()) {
                    case "number":
                    case "integer":
                        try {
                            Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            return ReturnResult.error("配置项 " + item.getSystemServerSettingItemName() + " 必须为整数");
                        }
                        break;
                    case "double":
                    case "float":
                        try {
                            Double.parseDouble(value);
                        } catch (NumberFormatException e) {
                            return ReturnResult.error("配置项 " + item.getSystemServerSettingItemName() + " 必须为数字");
                        }
                        break;
                    case "boolean":
                        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                            return ReturnResult.error("配置项 " + item.getSystemServerSettingItemName() + " 必须为true或false");
                        }
                        break;
                }
            }

            // 根据验证规则验证
            if (StringUtils.isNotEmpty(validationRule)) {
                try {
                    Pattern pattern = Pattern.compile(validationRule);
                    if (!pattern.matcher(value).matches()) {
                        return ReturnResult.error("配置项 " + item.getSystemServerSettingItemName() + " 格式不正确");
                    }
                } catch (Exception e) {
                    log.warn("验证规则格式错误: itemName={}, rule={}", item.getSystemServerSettingItemName(), validationRule);
                }
            }

            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("验证配置项值失败: itemId={}", item.getSystemServerSettingItemId(), e);
            return ReturnResult.error("验证配置项值失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<String> getItemDefaultValue(Integer settingId, String itemName) {
        try {
            SystemServerSettingItem item = baseMapper.selectBySettingIdAndName(settingId, itemName);
            if (item == null) {
                return ReturnResult.error("配置项不存在");
            }

            String defaultValue = item.getSystemServerSettingItemDefaultValue();
            return ReturnResult.ok(defaultValue);
        } catch (Exception e) {
            log.error("获取配置项默认值失败: settingId={}, itemName={}", settingId, itemName, e);
            return ReturnResult.error("获取默认值失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ReturnResult<Boolean> resetItemToDefault(Integer itemId) {
        try {
            SystemServerSettingItem item = getById(itemId);
            if (item == null) {
                return ReturnResult.error("配置项不存在");
            }

            String defaultValue = item.getSystemServerSettingItemDefaultValue();
            baseMapper.updateValue(itemId, defaultValue);

            log.info("重置配置项为默认值成功: itemId={}, defaultValue={}", itemId, defaultValue);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("重置配置项为默认值失败: itemId={}", itemId, e);
            return ReturnResult.error("重置配置项失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ReturnResult<Boolean> batchResetItemsToDefault(List<Integer> itemIds) {
        try {
            for (Integer itemId : itemIds) {
                ReturnResult<Boolean> resetResult = resetItemToDefault(itemId);
                if (!resetResult.isOk()) {
                    return resetResult;
                }
            }

            log.info("批量重置配置项为默认值成功，重置数量: {}", itemIds.size());
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("批量重置配置项为默认值失败", e);
            return ReturnResult.error("批量重置配置项失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<SystemServerSettingItem>> getBySettingIdAndRequired(Integer settingId, Boolean required) {
        try {
            List<SystemServerSettingItem> items = baseMapper.selectBySettingIdAndRequired(settingId, required);
            return ReturnResult.ok(items);
        } catch (Exception e) {
            log.error("根据配置ID和必填状态查询配置项失败: settingId={}, required={}", settingId, required, e);
            return ReturnResult.error("查询配置项失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> checkRequiredItemsConfigured(Integer settingId) {
        try {
            ReturnResult<List<SystemServerSettingItem>> requiredItemsResult = getBySettingIdAndRequired(settingId, true);
            if (!requiredItemsResult.isOk()) {
                return ReturnResult.error(requiredItemsResult.getMsg());
            }

            List<SystemServerSettingItem> requiredItems = requiredItemsResult.getData();
            for (SystemServerSettingItem item : requiredItems) {
                if (StringUtils.isEmpty(item.getSystemServerSettingItemValue())) {
                    return ReturnResult.error("必填配置项 " + item.getSystemServerSettingItemName() + " 未配置");
                }
            }

            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("检查必填配置项失败: settingId={}", settingId, e);
            return ReturnResult.error("检查必填配置项失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Map<String, String>> getItemsAsMap(Integer settingId) {
        try {
            ReturnResult<List<SystemServerSettingItem>> itemsResult = getBySettingId(settingId);
            if (!itemsResult.isOk()) {
                return ReturnResult.error(itemsResult.getMsg());
            }

            Map<String, String> itemsMap = new HashMap<>();
            List<SystemServerSettingItem> items = itemsResult.getData();
            for (SystemServerSettingItem item : items) {
                itemsMap.put(item.getSystemServerSettingItemName(), item.getSystemServerSettingItemValue());
            }

            return ReturnResult.ok(itemsMap);
        } catch (Exception e) {
            log.error("将配置项转换为Map失败: settingId={}", settingId, e);
            return ReturnResult.error("转换配置项失败: " + e.getMessage());
        }
    }
}




