-- =====================================================================
-- V1.1__add_cloud_yao_fields.sql
-- 提供商凭据补充云曜平台的产品编码 / 项目 ID。
-- 云曜 OpenAPI 的设备、取流、云台、门禁接口按 productCode + projectId 隔离。
-- =====================================================================

ALTER TABLE device_provider_credential
    ADD COLUMN device_credential_product_code VARCHAR(64) COMMENT '云曜产品编码 ProductCode（云曜平台必填）' AFTER device_credential_app_secret,
    ADD COLUMN device_credential_project_id VARCHAR(64) COMMENT '云曜项目 ID ProjectID（云曜平台必填）' AFTER device_credential_product_code;
