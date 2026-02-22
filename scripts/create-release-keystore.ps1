param(
    [string]$KeystorePath = "keystore/uno-release.jks",
    [string]$Alias = "uno_release_key",
    [string]$DName = "CN=UnoAssistant,OU=Mobile,O=UnoAssistant,L=Shanghai,ST=Shanghai,C=CN",
    [int]$ValidityDays = 36500,
    [int]$KeySize = 4096
)

$ErrorActionPreference = "Stop"

function Read-PlainPassword([string]$prompt) {
    $secure = Read-Host -Prompt $prompt -AsSecureString
    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    } finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$keystoreAbsPath = if ([System.IO.Path]::IsPathRooted($KeystorePath)) {
    $KeystorePath
} else {
    Join-Path $repoRoot $KeystorePath
}

$keystoreDir = Split-Path -Parent $keystoreAbsPath
if (-not (Test-Path $keystoreDir)) {
    New-Item -ItemType Directory -Path $keystoreDir -Force | Out-Null
}

if (Test-Path $keystoreAbsPath) {
    throw "目标 keystore 已存在：$keystoreAbsPath。请先备份或删除后重试。"
}

$storePassword = Read-PlainPassword "请输入 keystore 密码"
$keyPassword = Read-PlainPassword "请输入 key 密码（可与 keystore 密码相同）"

$keytool = Get-Command keytool -ErrorAction Stop

& $keytool.Source `
    -genkeypair `
    -v `
    -keystore $keystoreAbsPath `
    -storepass $storePassword `
    -alias $Alias `
    -keyalg RSA `
    -keysize $KeySize `
    -validity $ValidityDays `
    -dname $DName `
    -keypass $keyPassword `
    -noprompt

if ($LASTEXITCODE -ne 0) {
    throw "keytool 执行失败，请检查 JDK 环境与参数。"
}

$storeFileValue = $KeystorePath.Replace("\", "/")
$keystorePropsPath = Join-Path $repoRoot "keystore.properties"

@(
    "storeFile=$storeFileValue"
    "storePassword=$storePassword"
    "keyAlias=$Alias"
    "keyPassword=$keyPassword"
) | Set-Content -Path $keystorePropsPath -Encoding ASCII

Write-Host "已生成 keystore: $keystoreAbsPath"
Write-Host "已生成配置文件: $keystorePropsPath"
Write-Host "下一步执行：.\gradlew.bat :app:assembleRelease"
