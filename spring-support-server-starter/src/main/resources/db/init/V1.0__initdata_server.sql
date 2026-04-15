SET NAMES utf8mb4;

INSERT INTO `server_host` (
  `server_name`,
  `server_code`,
  `server_type`,
  `server_os_type`,
  `server_architecture`,
  `server_host`,
  `server_port`,
  `server_username`,
  `server_password`,
  `server_private_key`,
  `server_base_directory`,
  `server_tags`,
  `server_enabled`,
  `server_description`,
  `server_metadata_json`,
  `create_time`,
  `update_time`
)
SELECT
  '本机 Windows',
  'f32a4ba8642dc68c0ff5042a572fcdcf',
  'LOCAL',
  'WINDOWS',
  'AMD64',
  '127.0.0.1',
  0,
  'yemen',
  NULL,
  NULL,
  'H:/workspace/2/tmp/soft-runtime/local',
  'soft,local,windows',
  1,
  'soft-test 本机真实联调入口',
  '{"softEnabled":true,"displayMode":"default","source":"soft-test-bootstrap"}',
  NOW(),
  NOW()
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1
  FROM `server_host`
  WHERE `server_code` = 'f32a4ba8642dc68c0ff5042a572fcdcf'
);

INSERT INTO `server_host` (
  `server_name`,
  `server_code`,
  `server_type`,
  `server_os_type`,
  `server_architecture`,
  `server_host`,
  `server_port`,
  `server_username`,
  `server_password`,
  `server_private_key`,
  `server_base_directory`,
  `server_tags`,
  `server_enabled`,
  `server_description`,
  `server_metadata_json`,
  `create_time`,
  `update_time`
)
SELECT
  '远程 Linux 172.16.0.40',
  '20a860f7f008190a23da131959bb0580',
  'SSH',
  'LINUX',
  'AMD64',
  '172.16.0.40',
  22,
  'root',
  'd57a246d45bbdaa0392dfb33d18d97fa',
  'dff1a552dea2f3164f0f6c7101fe0caa',
  '/opt',
  'soft,remote,linux',
  1,
  'soft-test 远程 Linux 真实联调入口',
  '{"softEnabled":true,"displayMode":"default","source":"soft-test-bootstrap","hostAlias":"remote-linux-17216040"}',
  NOW(),
  NOW()
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1
  FROM `server_host`
  WHERE `server_code` = '20a860f7f008190a23da131959bb0580'
);
