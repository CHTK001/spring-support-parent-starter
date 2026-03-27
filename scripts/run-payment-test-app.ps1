$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$moduleDir = Join-Path $repoRoot "spring-support-payment-starter"
$classpathFile = Join-Path $moduleDir "target\\test-classpath.txt"

if (!(Test-Path $classpathFile)) {
  throw "Classpath file not found: $classpathFile"
}

$dependencyClasspath = (Get-Content $classpathFile -Raw).Trim()
$classpath = @(
  (Join-Path $moduleDir "target\\test-classes"),
  (Join-Path $moduleDir "target\\classes"),
  $dependencyClasspath
) -join ";"

java -cp $classpath com.chua.payment.support.PaymentTestApplication
