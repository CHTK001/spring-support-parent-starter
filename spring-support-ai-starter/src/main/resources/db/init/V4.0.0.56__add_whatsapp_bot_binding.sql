-- 连接器增加 WhatsApp Cloud API 所需的应用密钥与 Webhook 校验令牌。
-- 说明：phone_number_id 复用 sys_ai_bot_binding_bot_id，
--       access_token 复用 sys_ai_bot_binding_bot_token；
--       app_secret 用于 X-Hub-Signature-256 验签，verify_token 用于 Webhook GET 握手。
ALTER TABLE `sys_ai_bot_binding`
    ADD COLUMN `sys_ai_bot_binding_app_secret`  VARCHAR(512) NULL COMMENT '平台应用密钥（WhatsApp App Secret）' AFTER `sys_ai_bot_binding_bot_token`,
    ADD COLUMN `sys_ai_bot_binding_verify_token` VARCHAR(255) NULL COMMENT 'Webhook 校验令牌（WhatsApp Verify Token）' AFTER `sys_ai_bot_binding_app_secret`;