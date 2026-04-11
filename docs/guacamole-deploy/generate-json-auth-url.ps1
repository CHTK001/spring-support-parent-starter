param(
    [Parameter(Mandatory = $true)][string]$Gateway,
    [Parameter(Mandatory = $true)][string]$Secret,
    [Parameter(Mandatory = $true)][string]$HostName,
    [Parameter(Mandatory = $true)][int]$Port,
    [string]$ConnectionName = "server-console",
    [string]$Protocol = "ssh",
    [string]$Username = "",
    [string]$Password = "",
    [int]$ExpiresSeconds = 300
)

function ConvertFrom-HexString {
    param([string]$Value)
    if (($Value.Length % 2) -ne 0) {
        throw "Secret must be a valid hex string."
    }
    $bytes = New-Object byte[] ($Value.Length / 2)
    for ($i = 0; $i -lt $Value.Length; $i += 2) {
        $bytes[$i / 2] = [Convert]::ToByte($Value.Substring($i, 2), 16)
    }
    return $bytes
}

$key = ConvertFrom-HexString $Secret
if ($key.Length -notin @(16, 24, 32)) {
    throw "Secret must decode to 16/24/32 bytes."
}

$parameters = [ordered]@{
    hostname = $HostName
    port = [string]$Port
    username = $Username
    password = $Password
}
if ($Protocol.ToLowerInvariant() -eq "rdp") {
    $parameters["security"] = "any"
    $parameters["ignore-cert"] = "true"
}

$payload = [ordered]@{
    username = "verify-$ConnectionName"
    expires = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() + ($ExpiresSeconds * 1000)
    connections = [ordered]@{
        $ConnectionName = [ordered]@{
            protocol = $Protocol
            parameters = $parameters
        }
    }
}

$json = $payload | ConvertTo-Json -Depth 6 -Compress
$jsonBytes = [Text.Encoding]::UTF8.GetBytes($json)

$hmac = [System.Security.Cryptography.HMACSHA256]::new($key)
try {
    $signature = $hmac.ComputeHash($jsonBytes)
} finally {
    $hmac.Dispose()
}

$signed = New-Object byte[] ($signature.Length + $jsonBytes.Length)
[Array]::Copy($signature, 0, $signed, 0, $signature.Length)
[Array]::Copy($jsonBytes, 0, $signed, $signature.Length, $jsonBytes.Length)

$aes = [System.Security.Cryptography.Aes]::Create()
try {
    $aes.Mode = [System.Security.Cryptography.CipherMode]::CBC
    $aes.Padding = [System.Security.Cryptography.PaddingMode]::PKCS7
    $aes.Key = $key
    $aes.IV = New-Object byte[] 16
    $encryptor = $aes.CreateEncryptor()
    try {
        $encrypted = $encryptor.TransformFinalBlock($signed, 0, $signed.Length)
    } finally {
        $encryptor.Dispose()
    }
} finally {
    $aes.Dispose()
}

$token = [Convert]::ToBase64String($encrypted)
$baseUrl = $Gateway.TrimEnd("/")
$escaped = [System.Uri]::EscapeDataString($token)
Write-Output "$baseUrl/?data=$escaped"
