-- 支付模块分布式部署增强
-- 1. merchant_id + business_order_no 唯一，保证业务单号幂等创建
-- 2. timeout 扫描索引，降低多节点超时关单扫描成本

ALTER TABLE `payment_order`
  ADD UNIQUE KEY `uk_payment_order_merchant_business_no` (`merchant_id`, `business_order_no`);

ALTER TABLE `payment_order`
  ADD KEY `idx_payment_timeout_scan` (`status`, `deleted`, `expire_time`);
