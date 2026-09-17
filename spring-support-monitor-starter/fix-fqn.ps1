[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$f = 'D:\ch\project\spring-support-parent-starter\spring-support-monitor-starter\src\main\java\com\chua\starter\monitor\support\controller\MonitorController.java'
$t = [System.IO.File]::ReadAllText($f)

$pairs = @(
  @('java.util.List<', 'List<'),
  @('new java.util.ArrayList<>()', 'new ArrayList<>()'),
  @('java.util.LinkedHashMap<>', 'LinkedHashMap<>'),
  @('new java.util.HashSet<>()', 'new HashSet<>()'),
  @('java.time.Duration.ofSeconds', 'Duration.ofSeconds'),
  @('java.nio.charset.StandardCharsets.UTF_8', 'StandardCharsets.UTF_8'),
  @('org.springframework.data.redis.core.ScanOptions.scanOptions()', 'ScanOptions.scanOptions()')
)
foreach ($p in $pairs) {
  $c = ([regex]::Matches($t, [regex]::Escape($p[0]))).Count
  if ($c -eq 0) {
    Write-Output ("MISS: " + $p[0])
  } else {
    $t = $t.Replace($p[0], $p[1])
    Write-Output ("OK x" + $c + " : " + $p[0])
  }
}

[System.IO.File]::WriteAllText($f, $t)
Write-Output "=== FQN done ==="
