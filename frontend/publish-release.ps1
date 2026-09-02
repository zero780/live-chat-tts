[CmdletBinding()]
param(
    [string]$Repository = $(if ($env:GITHUB_REPOSITORY) { $env:GITHUB_REPOSITORY } else { 'zero780/live-chat-tts' }),
    [switch]$KeepDraft
)

$ErrorActionPreference = 'Stop'
$token = if ($env:GH_TOKEN) { $env:GH_TOKEN } else { $env:GITHUB_TOKEN }
if ([string]::IsNullOrWhiteSpace($token)) {
    throw 'Set GH_TOKEN (or GITHUB_TOKEN) before running this script.'
}

$headers = @{
    Authorization = "Bearer $token"
    Accept = 'application/vnd.github+json'
    'X-GitHub-Api-Version' = '2022-11-28'
    'User-Agent' = 'live-chat-tts-release-script'
}
$apiRoot = "https://api.github.com/repos/$Repository"

function Invoke-GitHub {
    param(
        [ValidateSet('GET', 'PATCH')][string]$Method,
        [string]$Uri,
        [object]$Body
    )
    $params = @{ Method = $Method; Uri = $Uri; Headers = $headers }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json'
        $params.Body = $Body | ConvertTo-Json -Depth 5
    }
    return Invoke-RestMethod @params
}

$releases = Invoke-GitHub -Method GET -Uri "$apiRoot/releases?per_page=100"
$draft = @($releases | Where-Object { $_.draft } | Sort-Object { [DateTime]$_.created_at } -Descending | Select-Object -First 1)
if (-not $draft) { throw "No draft release was found in $Repository." }
$release = $draft[0]

$dist = Join-Path $PSScriptRoot 'dist'
$installer = Get-ChildItem -LiteralPath $dist -Filter 'Live-Chat-TTS-Setup-*.exe' -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $installer) { throw "No Windows installer found in $dist. Run the production build first." }

$assets = @($installer)
$blockmap = Get-Item -LiteralPath ($installer.FullName + '.blockmap') -ErrorAction SilentlyContinue
if ($blockmap) { $assets += $blockmap }

foreach ($asset in $assets) {
    $name = [uri]::EscapeDataString($asset.Name)
    $uploadUri = $release.upload_url -replace '\{\?name,label\}', "?name=$name"
    $uploadHeaders = $headers.Clone()
    $uploadHeaders['Content-Type'] = 'application/octet-stream'
    Write-Host "Uploading $($asset.Name) to draft $($release.tag_name)..."
    Invoke-RestMethod -Method PUT -Uri $uploadUri -Headers $uploadHeaders -InFile $asset.FullName -ContentType 'application/octet-stream' | Out-Null
}

if (-not $KeepDraft) {
    Invoke-GitHub -Method PATCH -Uri "$apiRoot/releases/$($release.id)" -Body @{ draft = $false; prerelease = $release.prerelease; make_latest = 'true' } | Out-Null
    Write-Host "Release $($release.tag_name) published successfully."
} else {
    Write-Host "Assets uploaded; release $($release.tag_name) remains a draft (-KeepDraft)."
}

Write-Host "Installer: $($installer.FullName)"
