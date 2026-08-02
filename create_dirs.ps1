$base = "G:\work\spring-support-parent-starter"
$dirs = @(
    "spring-support-device-starter\src\main\java\com\chua\starter\device\support\configuration",
    "spring-support-device-starter\src\main\java\com\chua\starter\device\support\spi\model",
    "spring-support-device-starter\src\main\java\com\chua\starter\device\support\entity",
    "spring-support-device-starter\src\main\java\com\chua\starter\device\support\mapper",
    "spring-support-device-starter\src\main\java\com\chua\starter\device\support\service\impl",
    "spring-support-device-starter\src\main\java\com\chua\starter\device\support\controller",
    "spring-support-device-starter\src\main\java\com\chua\starter\device\provider\hikvision",
    "spring-support-device-starter\src\main\java\com\chua\starter\device\provider\dahua",
    "spring-support-device-starter\src\main\resources\db\init",
    "spring-support-device-starter\src\main\resources\META-INF\services",
    "spring-support-device-starter\src\main\frontend\pages\device"
)
foreach ($d in $dirs) {
    $full = Join-Path $base $d
    New-Item -ItemType Directory -Force -Path $full | Out-Null
}
Write-Host "Directories created."