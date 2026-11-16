[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidSdkRoot,
    [string]$BuildToolsDir,
    [string]$PlatformJar,
    [string]$WorkRoot
)

$ErrorActionPreference = 'Stop'

function Invoke-Checked([string]$Program, [string[]]$Options) {
    & $Program @Options
    if ($LASTEXITCODE -ne 0) {
        throw "$(Split-Path -Leaf $Program) exited with code $LASTEXITCODE."
    }
}

function Resolve-RequiredFile([string]$File) {
    if (!(Test-Path -LiteralPath $File -PathType Leaf)) {
        throw "Required tool or file not found: $File"
    }
    return (Resolve-Path -LiteralPath $File).ProviderPath
}

if (!$JavaHome) {
    throw 'Set JAVA_HOME to your JDK 17 installation or pass -JavaHome.'
}
if (!$AndroidSdkRoot) {
    $AndroidSdkRoot = $env:ANDROID_HOME
    if (!$AndroidSdkRoot) {
        $AndroidSdkRoot = $env:ANDROID_SDK_ROOT
    }
}
if (!$AndroidSdkRoot -and (!$BuildToolsDir -or !$PlatformJar)) {
    throw 'Set ANDROID_HOME or pass both -BuildToolsDir and -PlatformJar.'
}
if (!$BuildToolsDir) {
    $BuildToolsDir = Join-Path $AndroidSdkRoot 'build-tools\36.0.0'
}
if (!$PlatformJar) {
    $PlatformJar = Join-Path $AndroidSdkRoot 'platforms\android-36\android.jar'
}

$java = Resolve-RequiredFile (Join-Path $JavaHome 'bin\java.exe')
$javac = Resolve-RequiredFile (Join-Path $JavaHome 'bin\javac.exe')
$jar = Resolve-RequiredFile (Join-Path $JavaHome 'bin\jar.exe')
$keytool = Resolve-RequiredFile (Join-Path $JavaHome 'bin\keytool.exe')
$aapt = Resolve-RequiredFile (Join-Path $BuildToolsDir 'aapt2.exe')
$zipalign = Resolve-RequiredFile (Join-Path $BuildToolsDir 'zipalign.exe')
$d8 = Resolve-RequiredFile (Join-Path $BuildToolsDir 'lib\d8.jar')
$signer = Resolve-RequiredFile (Join-Path $BuildToolsDir 'lib\apksigner.jar')
$platform = Resolve-RequiredFile $PlatformJar

$project = Split-Path -Parent $PSScriptRoot
$main = Join-Path $PSScriptRoot 'app\src\main'
$delivery = Join-Path $project 'build'
$sourceManifest = Resolve-RequiredFile (Join-Path $main 'AndroidManifest.xml')
[xml]$manifest = Get-Content -LiteralPath $sourceManifest -Raw -Encoding utf8
$androidNamespace = 'http://schemas.android.com/apk/res/android'
$version = $manifest.manifest.GetAttribute('versionName', $androidNamespace)
$minSdk = $manifest.manifest.'uses-sdk'.GetAttribute('minSdkVersion', $androidNamespace)
if ($version -notmatch '^\d+\.\d+\.\d+([-+][A-Za-z0-9.-]+)?$' -or $minSdk -notmatch '^\d+$') {
    throw 'AndroidManifest.xml must declare a semantic version name and numeric minSdkVersion.'
}

if (!$WorkRoot) {
    $WorkRoot = Join-Path $delivery 'work'
    if ($WorkRoot -match '[^\x00-\x7F]') {
        throw 'This checkout path contains non-ASCII characters. Pass -WorkRoot with an ASCII path for the Android command-line tools.'
    }
}
New-Item -ItemType Directory -Path $WorkRoot -Force | Out-Null
$work = (Resolve-Path -LiteralPath $WorkRoot).ProviderPath
if ($work -match '[^\x00-\x7F]') {
    throw 'Pass -WorkRoot with an ASCII path for the Android command-line tools.'
}
$prefix = $work.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
$build = Join-Path $work ('english-spark-' + [Guid]::NewGuid().ToString('N'))
$gen = Join-Path $build 'gen'
$classes = Join-Path $build 'classes'
$dex = Join-Path $build 'dex'
$signing = Join-Path $project '.signing'
$keystore = Join-Path $signing 'english-spark.p12'
$protectedPassword = Join-Path $signing 'english-spark.password'

try {
    New-Item -ItemType Directory -Path $gen,$classes,$dex,$signing,$delivery -Force | Out-Null
    $source = Join-Path $build 'main'
    Copy-Item -LiteralPath $main -Destination $source -Recurse
    $resources = Join-Path $build 'resources.zip'
    $base = Join-Path $build 'unsigned.apk'
    $aligned = Join-Path $build 'aligned.apk'
    $signed = Join-Path $build 'signed.apk'
    $classJar = Join-Path $build 'classes.jar'

    Invoke-Checked $aapt @('compile', '--dir', (Join-Path $source 'res'), '-o', $resources)
    Invoke-Checked $aapt @('link', '-o', $base, '-I', $platform, '--manifest', (Join-Path $source 'AndroidManifest.xml'), '--java', $gen, '-0', 'mp3', $resources)
    $javaFiles = @(Get-ChildItem -LiteralPath (Join-Path $source 'java'),$gen -Recurse -File -Filter '*.java' | ForEach-Object FullName)
    Invoke-Checked $javac (@('-encoding', 'UTF-8', '-source', '8', '-target', '8', '-Xlint:-options', '-classpath', $platform, '-d', $classes) + $javaFiles)
    Invoke-Checked $jar @('cf', $classJar, '-C', $classes, '.')
    Invoke-Checked $java @('-cp', $d8, 'com.android.tools.r8.D8', '--release', '--min-api', $minSdk, '--lib', $platform, '--output', $dex, $classJar)
    Invoke-Checked $jar @('uf', $base, '-C', $dex, 'classes.dex')
    Invoke-Checked $zipalign @('-f', '4', $base, $aligned)

    # Keep one local key for upgrade-compatible builds. Keep public releases on a trusted signing machine.
    $lockPath = Join-Path $signing 'key.lock'
    $signingLock = [IO.File]::Open($lockPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    $priorPassword = [Environment]::GetEnvironmentVariable('ENGLISH_SPARK_SIGNING_PASSWORD', 'Process')
    try {
        if (!(Test-Path -LiteralPath $protectedPassword -PathType Leaf)) {
            if (Test-Path -LiteralPath $keystore -PathType Leaf) {
                throw 'The existing local signing key has no password file. Restore the password file to retain upgrade compatibility.'
            }
            $bytes = [byte[]]::new(32)
            $random = [Security.Cryptography.RandomNumberGenerator]::Create()
            try { $random.GetBytes($bytes) } finally { $random.Dispose() }
            ConvertTo-SecureString -String ([Convert]::ToBase64String($bytes)) -AsPlainText -Force |
                ConvertFrom-SecureString | Set-Content -LiteralPath $protectedPassword -Encoding ascii
        }
        $password = (Get-Content -Raw -LiteralPath $protectedPassword).Trim() | ConvertTo-SecureString
        $env:ENGLISH_SPARK_SIGNING_PASSWORD = [Net.NetworkCredential]::new('', $password).Password
        $buildKey = Join-Path $build 'signing.p12'
        if (!(Test-Path -LiteralPath $keystore -PathType Leaf)) {
            Invoke-Checked $keytool @('-genkeypair', '-keystore', $buildKey, '-storetype', 'PKCS12', '-storepass:env', 'ENGLISH_SPARK_SIGNING_PASSWORD', '-alias', 'english-spark', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-dname', 'CN=English Spark', '-noprompt')
            Copy-Item -LiteralPath $buildKey -Destination $keystore
        } else {
            Copy-Item -LiteralPath $keystore -Destination $buildKey
        }
        Invoke-Checked $java @('-jar', $signer, 'sign', '--ks', $buildKey, '--ks-pass', 'env:ENGLISH_SPARK_SIGNING_PASSWORD', '--ks-key-alias', 'english-spark', '--v4-signing-enabled', 'false', '--out', $signed, $aligned)
    } finally {
        [Environment]::SetEnvironmentVariable('ENGLISH_SPARK_SIGNING_PASSWORD', $priorPassword, 'Process')
        $signingLock.Dispose()
    }

    Invoke-Checked $java @('-jar', $signer, 'verify', '--verbose', $signed)
    Invoke-Checked $zipalign @('-c', '4', $signed)
    $apk = Join-Path $delivery "english-spark-v$version.apk"
    Copy-Item -LiteralPath $signed -Destination $apk -Force
    Get-Item -LiteralPath $apk | Select-Object FullName,Length | Format-List
    Get-FileHash -LiteralPath $apk -Algorithm SHA256 | Format-List
} finally {
    if (Test-Path -LiteralPath $build -PathType Container) {
        $target = (Resolve-Path -LiteralPath $build).ProviderPath
        if ($target.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) -and
            (Split-Path -Leaf $target) -match '^english-spark-[0-9a-f]{32}$') {
            Remove-Item -LiteralPath $target -Recurse -Force
        }
    }
}
