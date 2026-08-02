# OAuth 鉴权链路压测脚本（Windows PowerShell 5.1，C# 内联多线程）
# 用法示例：
#   登录场景：      .\benchmark.ps1 -Path "/v2/user/login" -Method POST -Body '{"username":"admin","password":"123456","captcha":"","captchaId":"","code":"WEB"}' -Concurrency 50 -TotalRequests 2000
#   鉴权场景(x-oauth-token)： .\benchmark.ps1 -Path "/v2/setting/default" -Method GET -AuthHeader "x-oauth-token" -Concurrency 100 -TotalRequests 5000
#   鉴权场景(Bearer)：       .\benchmark.ps1 -Path "/v2/setting/default" -Method GET -AuthHeader "Authorization" -TokenPrefix "Bearer " -Concurrency 100 -TotalRequests 5000
param(
    [string]$BaseUrl = "http://localhost:18170/system/api",
    [string]$Path = "/v2/setting/default",
    [string]$Method = "GET",
    [string]$Body = "",
    [int]$Concurrency = 100,
    [int]$TotalRequests = 5000,
    [string]$AuthHeader = "x-oauth-token",
    [string]$Token = "",
    [string]$TokenPrefix = "",
    [string]$LoginPath = "/v2/user/login",
    [string]$LoginBody = '{"username":"admin","password":"123456","captcha":"","captchaId":"","code":"WEB"}'
)

$ErrorActionPreference = "Stop"

# 未提供 token 时先登录获取
if (-not $Token) {
    $login = Invoke-RestMethod -Uri "$BaseUrl$LoginPath" -Method Post -ContentType "application/json" -Body $LoginBody -TimeoutSec 30
    if ($null -eq $login.data -or $null -eq $login.data.accessToken) {
        Write-Output "登录失败: $($login | ConvertTo-Json -Depth 5)"
        exit 1
    }
    $Token = $login.data.accessToken
    Write-Output ("登录获取 token，长度: {0}" -f $Token.Length)
}

$csharpCode = @"
using System;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Collections.Concurrent;

public class BenchmarkRunner
{
    private static readonly System.Net.Http.HttpClient SharedClient = new System.Net.Http.HttpClient();

    public static object[] Run(string baseUrl, string path, string method, string header,
        string token, string tokenPrefix, string body, int concurrency, int total)
    {
        var lats = new ConcurrentQueue<double>();
        int success = 0;
        int fail = 0;
        var start = System.Diagnostics.Stopwatch.StartNew();

        Parallel.For(0, total, new ParallelOptions { MaxDegreeOfParallelism = concurrency }, i =>
        {
            var sw = System.Diagnostics.Stopwatch.StartNew();
            try
            {
                var req = new System.Net.Http.HttpRequestMessage(new System.Net.Http.HttpMethod(method), baseUrl + path);
                if (!string.IsNullOrEmpty(token))
                {
                    req.Headers.TryAddWithoutValidation(header, tokenPrefix + token);
                }
                if (!string.IsNullOrEmpty(body))
                {
                    req.Content = new System.Net.Http.StringContent(body, Encoding.UTF8, "application/json");
                }
                using (var resp = SharedClient.SendAsync(req).GetAwaiter().GetResult())
                {
                    int code = (int)resp.StatusCode;
                    sw.Stop();
                    if (code >= 200 && code < 300)
                    {
                        lats.Enqueue(sw.Elapsed.TotalMilliseconds);
                        Interlocked.Increment(ref success);
                    }
                    else
                    {
                        Interlocked.Increment(ref fail);
                    }
                }
            }
            catch
            {
                sw.Stop();
                Interlocked.Increment(ref fail);
            }
        });

        start.Stop();
        return new object[] { success, fail, start.Elapsed.TotalMilliseconds, lats.ToArray() };
    }
}
"@

Add-Type -TypeDefinition $csharpCode -Language CSharp -ReferencedAssemblies "System.Net.Http"

Write-Output ("========== 压测开始 ==========")
Write-Output ("接口: {0}{1}  方法: {2}" -f $BaseUrl, $Path, $Method)
Write-Output ("并发: {0}   总请求: {1}   认证头: {2}" -f $Concurrency, $TotalRequests, $AuthHeader)
Write-Output "------------------------------"

$result = [BenchmarkRunner]::Run($BaseUrl, $Path, $Method, $AuthHeader, $Token, $TokenPrefix, $Body, $Concurrency, $TotalRequests)
$success = $result[0]
$fail = $result[1]
$elapsedSec = $result[2] / 1000.0
$arr = [double[]]$result[3]

$count = $arr.Length
[Array]::Sort($arr)

function Get-Pct {
    param($a, [double]$p)
    if ($a.Length -eq 0) { return 0 }
    $idx = [Math]::Min([int][Math]::Ceiling($p * $a.Length) - 1, $a.Length - 1)
    if ($idx -lt 0) { $idx = 0 }
    return $a[$idx]
}

$avg = if ($count -gt 0) { ($arr | Measure-Object -Average).Average } else { 0 }
$qps = if ($elapsedSec -gt 0) { [Math]::Round($success / $elapsedSec, 1) } else { 0 }

Write-Output ("总耗时: {0:N1}s" -f $elapsedSec)
Write-Output ("成功: {0}   失败: {1}   成功率: {2:N2}%" -f $success, $fail, ($success / $TotalRequests * 100))
Write-Output ("QPS: {0}" -f $qps)
Write-Output "------------------------------"
Write-Output "延迟分布 (ms):"
Write-Output ("  AVG : {0:N2}" -f $avg)
Write-Output ("  Min : {0:N2}" -f $(if ($count -gt 0) { $arr[0] } else { 0 }))
Write-Output ("  P50 : {0:N2}" -f $(Get-Pct $arr 0.50))
Write-Output ("  P90 : {0:N2}" -f $(Get-Pct $arr 0.90))
Write-Output ("  P95 : {0:N2}" -f $(Get-Pct $arr 0.95))
Write-Output ("  P99 : {0:N2}" -f $(Get-Pct $arr 0.99))
Write-Output ("  Max : {0:N2}" -f $(if ($count -gt 0) { $arr[$count - 1] } else { 0 }))
Write-Output ("========== 压测结束 ==========")
