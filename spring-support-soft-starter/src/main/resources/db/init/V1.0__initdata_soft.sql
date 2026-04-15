SET NAMES utf8mb4;

-- 版本维度字段回填：用于旧数据升级
UPDATE `soft_package_version` v
LEFT JOIN `soft_package` p ON p.`soft_package_id` = v.`soft_package_id`
SET v.`package_name` = COALESCE(NULLIF(v.`package_name`, ''), p.`package_name`),
    v.`os_type` = COALESCE(NULLIF(v.`os_type`, ''), p.`os_type`),
    v.`architecture` = COALESCE(NULLIF(v.`architecture`, ''), p.`architecture`),
    v.`source_kind` = COALESCE(
      NULLIF(v.`source_kind`, ''),
      CASE
        WHEN JSON_EXTRACT(v.`metadata_json`, '$.artifactKind') = '\"UPLOAD\"' THEN 'LOCAL_REPOSITORY'
        WHEN v.`download_urls_json` IS NOT NULL AND LENGTH(TRIM(v.`download_urls_json`)) > 0 THEN 'THIRD_PARTY'
        ELSE 'LINUX_DEFAULT'
      END
    ),
    v.`install_mode` = COALESCE(
      NULLIF(v.`install_mode`, ''),
      CASE
        WHEN JSON_EXTRACT(v.`metadata_json`, '$.artifactKind') = '\"UPLOAD\"' THEN 'LOCAL_UPLOAD'
        WHEN v.`download_urls_json` IS NOT NULL AND LENGTH(TRIM(v.`download_urls_json`)) > 0 THEN 'REMOTE_DOWNLOAD'
        ELSE 'PKG_MANAGER'
      END
    );

-- 仓库主定义迁移到来源子表（幂等）
INSERT INTO `soft_repository_source` (
  `soft_repository_id`,
  `source_name`,
  `source_kind`,
  `source_type`,
  `source_url`,
  `local_directory`,
  `auth_type`,
  `username`,
  `password`,
  `token`,
  `enabled`,
  `sort_order`,
  `source_config`,
  `create_name`,
  `create_by`,
  `create_time`,
  `update_time`,
  `update_name`,
  `update_by`
)
SELECT
  r.`soft_repository_id`,
  COALESCE(r.`repository_name`, CONCAT('repository-', r.`soft_repository_id`)),
  CASE
    WHEN UPPER(COALESCE(r.`repository_type`, '')) = 'RPM_REPO' THEN 'LINUX_DEFAULT'
    WHEN UPPER(COALESCE(r.`repository_type`, '')) = 'LOCAL_DIR' THEN 'LOCAL_REPOSITORY'
    ELSE 'THIRD_PARTY'
  END AS `source_kind`,
  r.`repository_type`,
  r.`repository_url`,
  r.`local_directory`,
  r.`auth_type`,
  r.`username`,
  r.`password`,
  r.`token`,
  r.`enabled`,
  10 AS `sort_order`,
  r.`sync_config`,
  r.`create_name`,
  r.`create_by`,
  r.`create_time`,
  r.`update_time`,
  r.`update_name`,
  r.`update_by`
FROM `soft_repository` r
LEFT JOIN `soft_repository_source` s ON s.`soft_repository_id` = r.`soft_repository_id`
    AND COALESCE(s.`source_type`, '') = COALESCE(r.`repository_type`, '')
    AND COALESCE(s.`source_url`, '') = COALESCE(r.`repository_url`, '')
    AND COALESCE(s.`local_directory`, '') = COALESCE(r.`local_directory`, '')
WHERE s.`soft_repository_source_id` IS NULL
  AND (
    (r.`repository_url` IS NOT NULL AND LENGTH(TRIM(r.`repository_url`)) > 0)
    OR (r.`local_directory` IS NOT NULL AND LENGTH(TRIM(r.`local_directory`)) > 0)
  );
