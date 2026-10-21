# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.

<#
.SYNOPSIS
    API smoke test for the Micropay Fineract distribution.

.DESCRIPTION
    Hits every GET endpoint listed in api-smoke-endpoints.json (the endpoints the mifos-web-next web-app calls) against
    a running Fineract server and records the HTTP status of each. A 404 on a listed path, any 5xx, or a status not in
    the entry's "allow" list is a failure. With -Baseline, the run is also diffed against a previous results file so
    that status changes after an upstream merge or a module move show up immediately.

    Run from any machine with PowerShell 5.1 or 7+; no Java build needed. Results go to api-smoke-results.json next to
    the script (git-ignored) unless -Output is given. This directory must stay flat: settings.gradle turns every
    custom/<company>/<category>/<module> directory into a Gradle project.

.EXAMPLE
    .\Invoke-ApiSmoke.ps1
    .\Invoke-ApiSmoke.ps1 -BaseUrl https://localhost:8443/fineract-provider/api/v1 -Output $env:TEMP\smoke.json
    .\Invoke-ApiSmoke.ps1 -Baseline .\api-smoke-baseline.json   # fail on any status change vs the committed baseline
#>
[CmdletBinding()]
param(
    [string] $BaseUrl = $(if ($env:FINERACT_API_URL) { $env:FINERACT_API_URL } else { 'https://localhost:8443/fineract-provider/api/v1' }),
    [string] $Tenant = $(if ($env:FINERACT_TENANT_ID) { $env:FINERACT_TENANT_ID } else { 'default' }),
    [string] $User = $(if ($env:FINERACT_USER) { $env:FINERACT_USER } else { 'mifos' }),
    [string] $Password = $(if ($env:FINERACT_PASSWORD) { $env:FINERACT_PASSWORD } else { 'password' }),
    [string] $Endpoints,
    [string] $Output,
    [string] $Baseline,
    [switch] $SkipTlsCheck = $true,
    [int] $TimeoutSec = 60
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $Endpoints) { $Endpoints = Join-Path $scriptDir 'api-smoke-endpoints.json' }
if (-not $Output) { $Output = Join-Path $scriptDir 'api-smoke-results.json' }
$BaseUrl = $BaseUrl.TrimEnd('/')
$isCore = $PSVersionTable.PSVersion.Major -ge 6

if (-not $isCore) {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    [Net.ServicePointManager]::DefaultConnectionLimit = 64
    if ($SkipTlsCheck) {
        # A script-block callback is not reliably invoked from the handshake thread in Windows PowerShell
        # (later requests fail with "unexpected error occurred on a send"); use a compiled delegate instead.
        if (-not ('MicropayApiSmoke.TrustAll' -as [type])) {
            Add-Type -TypeDefinition @'
using System.Net;
using System.Net.Security;
using System.Security.Cryptography.X509Certificates;
namespace MicropayApiSmoke {
    public static class TrustAll {
        public static bool Validate(object sender, X509Certificate certificate, X509Chain chain, SslPolicyErrors errors) { return true; }
        public static void Install() { ServicePointManager.ServerCertificateValidationCallback = new RemoteCertificateValidationCallback(Validate); }
    }
}
'@
        }
        [MicropayApiSmoke.TrustAll]::Install()
    }
}

$spec = Get-Content $Endpoints -Raw | ConvertFrom-Json

function Invoke-Api {
    param([string] $Method, [string] $Path, [hashtable] $Headers, [string] $Body)
    $uri = "$BaseUrl$Path"
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $result = [ordered]@{ status = 0; body = $null; ms = 0; error = $null }
    try {
        $args = @{ Method = $Method; Uri = $uri; Headers = $Headers; TimeoutSec = $TimeoutSec; UseBasicParsing = $true }
        if ($isCore -and $SkipTlsCheck) { $args.SkipCertificateCheck = $true }
        # Windows PowerShell reuses a poisoned keep-alive connection after an error response; one connection per call.
        if (-not $isCore) { $args.DisableKeepAlive = $true }
        if ($Body) { $args.Body = $Body; $args.ContentType = 'application/json' }
        $resp = Invoke-WebRequest @args
        $result.status = [int] $resp.StatusCode
        $result.body = $resp.Content
    } catch {
        $resp = $_.Exception.Response
        if ($resp -and $resp.StatusCode) {
            $result.status = [int] $resp.StatusCode
            try {
                if ($isCore) {
                    $result.body = $_.ErrorDetails.Message
                } else {
                    $stream = $resp.GetResponseStream()
                    $reader = New-Object IO.StreamReader($stream)
                    $result.body = $reader.ReadToEnd()
                    $reader.Close()
                    $resp.Close()
                }
            } catch { $result.body = $null }
        } else {
            $result.error = $_.Exception.Message
        }
    }
    $result.ms = [int] $sw.ElapsedMilliseconds
    return $result
}

function Select-JsonPath {
    param($Object, [string] $Path)
    $current = $Object
    foreach ($token in [regex]::Matches($Path, '\[(\d+)\]|([A-Za-z_][A-Za-z0-9_]*)')) {
        if ($null -eq $current) { return $null }
        if ($token.Groups[1].Success) {
            $idx = [int] $token.Groups[1].Value
            if ($current -is [System.Array] -or $current -is [System.Collections.IList]) {
                if ($current.Count -le $idx) { return $null }
                $current = $current[$idx]
            } else { return $null }
        } else {
            $name = $token.Groups[2].Value
            if ($current.PSObject.Properties[$name]) { $current = $current.$name } else { return $null }
        }
    }
    return $current
}

function Get-ErrorCode {
    param([string] $Body)
    if (-not $Body) { return $null }
    try {
        $j = $Body | ConvertFrom-Json
        if ($j.userMessageGlobalisationCode) { return $j.userMessageGlobalisationCode }
        if ($j.errors -and $j.errors.Count -gt 0) { return $j.errors[0].userMessageGlobalisationCode }
        if ($j.developerMessage) { return ($j.developerMessage -replace '\s+', ' ').Substring(0, [Math]::Min(120, $j.developerMessage.Length)) }
    } catch { }
    return $null
}

# --- 1. authenticate -------------------------------------------------------------------------------------------------
$basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${User}:${Password}"))
$headers = @{ 'Fineract-Platform-TenantId' = $Tenant; 'Authorization' = "Basic $basic"; 'Accept' = 'application/json' }
$loginBody = @{ username = $User; password = $Password } | ConvertTo-Json -Compress
$login = Invoke-Api -Method POST -Path '/authentication' -Headers $headers -Body $loginBody
if ($login.status -ne 200) {
    throw "Authentication against $BaseUrl failed: HTTP $($login.status) $($login.error) $($login.body)"
}
$auth = $login.body | ConvertFrom-Json
if ($auth.base64EncodedAuthenticationKey) {
    $headers['Authorization'] = "Basic $($auth.base64EncodedAuthenticationKey)"
}
Write-Host "Authenticated as $User on $BaseUrl (tenant $Tenant)"

# --- 2. resolve path parameters ----------------------------------------------------------------------------------------
$params = @{ user = $User; password = $Password }
$unresolved = @{}
function Expand-Placeholders {
    param([string] $Path)
    $missing = @()
    $expanded = [regex]::Replace($Path, '\{([A-Za-z_][A-Za-z0-9_]*)\}', {
            param($m)
            $key = $m.Groups[1].Value
            if ($params.ContainsKey($key)) { [string] $params[$key] } else { $script:__missing += $key; $m.Value }
        })
    return $expanded
}

# params may depend on each other (holidayId needs officeId): iterate until stable
$pending = @($spec.params.PSObject.Properties | ForEach-Object { $_.Name })
for ($round = 0; $round -lt 3 -and $pending.Count -gt 0; $round++) {
    $next = @()
    foreach ($name in $pending) {
        $def = $spec.params.$name
        $script:__missing = @()
        $from = Expand-Placeholders $def.from
        if ($script:__missing.Count -gt 0) { $next += $name; continue }
        $r = Invoke-Api -Method GET -Path $from -Headers $headers
        $value = $null
        if ($r.status -eq 200 -and $r.body) {
            try {
                $json = $r.body | ConvertFrom-Json
                foreach ($sel in @($def.select)) {
                    $value = Select-JsonPath -Object $json -Path $sel
                    if ($null -ne $value -and "$value" -ne '') { break }
                }
            } catch { $value = $null }
        }
        if ($null -ne $value -and "$value" -ne '') {
            $params[$name] = $value
        } else {
            $unresolved[$name] = "GET $from -> HTTP $($r.status) (no value for $($def.select -join ' | '))"
        }
    }
    $pending = $next
}
foreach ($name in $pending) { $unresolved[$name] = 'depends on an unresolved parameter' }
Write-Host ("Resolved {0} parameter(s); unresolved: {1}" -f ($params.Count - 2), ($unresolved.Keys -join ', '))

# --- 3. run ------------------------------------------------------------------------------------------------------------
$results = New-Object System.Collections.Generic.List[object]
foreach ($ep in $spec.endpoints) {
    $method = if ($ep.method) { $ep.method } else { 'GET' }
    if ($method -ne 'GET') { continue }   # writes are covered by the integration tests
    $script:__missing = @()
    $path = Expand-Placeholders $ep.path
    $entry = [ordered]@{
        path = $ep.path; resolved = $path; owner = $(if ($ep.owner) { $ep.owner } else { 'upstream' });
        status = $null; ms = 0; errorCode = $null; outcome = $null
    }
    if ($script:__missing.Count -gt 0) {
        $entry.outcome = 'SKIP'
        $entry.errorCode = "unresolved: $($script:__missing -join ',')"
    } else {
        $r = Invoke-Api -Method GET -Path $path -Headers $headers
        $entry.status = $r.status
        $entry.ms = $r.ms
        $entry.errorCode = $(if ($r.error) { $r.error } else { Get-ErrorCode $r.body })
        $allow = @(200, 201, 202, 204) + @($ep.allow | Where-Object { $_ })
        $entry.outcome = $(if ($entry.status -in $allow) { 'PASS' } elseif ($entry.status -eq 404 -or $entry.status -ge 500 -or $entry.status -eq 0) { 'FAIL' } else { 'WARN' })
    }
    $results.Add([pscustomobject] $entry)
    $color = switch ($entry.outcome) { 'PASS' { 'Green' } 'WARN' { 'Yellow' } 'SKIP' { 'DarkGray' } default { 'Red' } }
    Write-Host ("{0,-5} {1,4} {2,6}ms  {3,-8} {4}  {5}" -f $entry.outcome, $entry.status, $entry.ms, $entry.owner, $entry.path, $entry.errorCode) -ForegroundColor $color
}

# --- 4. baseline diff ----------------------------------------------------------------------------------------------------
$changes = @()
if ($Baseline) {
    $base = (Get-Content $Baseline -Raw | ConvertFrom-Json).results
    $baseByPath = @{}
    foreach ($b in $base) { $baseByPath[$b.path] = $b }
    foreach ($r in $results) {
        $b = $baseByPath[$r.path]
        if ($null -eq $b) { $changes += "NEW   $($r.path): $($r.status)"; continue }
        if ("$($b.status)" -ne "$($r.status)") { $changes += "CHANGED $($r.path): $($b.status) -> $($r.status) $($r.errorCode)" }
    }
    foreach ($b in $base) { if (-not ($results | Where-Object path -eq $b.path)) { $changes += "REMOVED $($b.path) (was $($b.status))" } }
}

# --- 5. report -----------------------------------------------------------------------------------------------------------
$summary = [ordered]@{
    baseUrl = $BaseUrl; tenant = $Tenant; ranAt = (Get-Date).ToUniversalTime().ToString('o')
    total = $results.Count
    pass = @($results | Where-Object outcome -eq 'PASS').Count
    warn = @($results | Where-Object outcome -eq 'WARN').Count
    fail = @($results | Where-Object outcome -eq 'FAIL').Count
    skip = @($results | Where-Object outcome -eq 'SKIP').Count
    unresolvedParams = $unresolved
    baselineChanges = $changes
}
# LF + UTF-8 without BOM so a committed baseline passes spotless and diffs cleanly.
$json = ([pscustomobject]@{ summary = $summary; results = $results } | ConvertTo-Json -Depth 6) -replace "`r`n", "`n"
$json = ($json -split "`n" | ForEach-Object { $_.TrimEnd() }) -join "`n"
$outputPath = if ([IO.Path]::IsPathRooted($Output)) { $Output } else { Join-Path (Get-Location).ProviderPath $Output }
[IO.File]::WriteAllText($outputPath, $json + "`n", (New-Object Text.UTF8Encoding $false))

Write-Host ''
Write-Host ("Total {0}: PASS {1}, WARN {2}, FAIL {3}, SKIP {4}  -> {5}" -f $summary.total, $summary.pass, $summary.warn, $summary.fail, $summary.skip, $Output)
if ($unresolved.Count -gt 0) {
    Write-Host 'Unresolved parameters (entries skipped):' -ForegroundColor Yellow
    $unresolved.GetEnumerator() | Sort-Object Name | ForEach-Object { Write-Host ("  {0,-28} {1}" -f $_.Name, $_.Value) -ForegroundColor Yellow }
}
if ($changes.Count -gt 0) {
    Write-Host "Baseline changes ($($changes.Count)):" -ForegroundColor Yellow
    $changes | ForEach-Object { Write-Host "  $_" -ForegroundColor Yellow }
}

$failed = $summary.fail -gt 0 -or $changes.Count -gt 0
exit $(if ($failed) { 1 } else { 0 })
