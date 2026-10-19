<#
.SYNOPSIS
    Print upstream drift for each Micropay service twin between two revisions.

.DESCRIPTION
    For each twin listed in custom/micropay/docs/fork-patch-set.md section 2, prints
    `git diff --stat` of the upstream implementation between -From and -To.
    Handles the JournalEntryWritePlatformService rename (JpaRepositoryImpl deleted,
    replaced by JournalEntryWritePlatformServiceImpl).

.EXAMPLE
    .\custom\micropay\tools\Show-TwinDrift.ps1 -From 0655191138f1 -To origin/develop
    .\custom\micropay\tools\Show-TwinDrift.ps1 -From f188505b8b -To ea0743dc1^1
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $From,
    [Parameter(Mandatory = $true)]
    [string] $To
)

$ErrorActionPreference = 'Stop'

$pairs = @(
    @{ Twin = 'JournalEntryWritePlatformServiceJpaRepositoryImpl'; FromPath = 'fineract-provider/src/main/java/org/apache/fineract/accounting/journalentry/service/JournalEntryWritePlatformServiceJpaRepositoryImpl.java'; ToPath = 'fineract-provider/src/main/java/org/apache/fineract/accounting/journalentry/service/JournalEntryWritePlatformServiceImpl.java' }
    @{ Twin = 'JournalEntryReadPlatformServiceImpl' }
    @{ Twin = 'GLAccountWritePlatformServiceJpaRepositoryImpl' }
    @{ Twin = 'ClientWritePlatformServiceJpaRepositoryImpl' }
    @{ Twin = 'ClientReadPlatformServiceImpl' }
    @{ Twin = 'ClientTemplateReadPlatformServiceImpl' }
    @{ Twin = 'ClientIdentifierWritePlatformServiceJpaRepositoryImpl' }
    @{ Twin = 'ClientIdentifierReadPlatformServiceImpl' }
    @{ Twin = 'ClientFamilyMembersWritePlatformServiceImpl' }
    @{ Twin = 'ClientFamilyMembersReadPlatformServiceImpl' }
    @{ Twin = 'AddressWritePlatformServiceImpl' }
    @{ Twin = 'AddressReadPlatformServiceImpl' }
    @{ Twin = 'AccountNumberFormatWritePlatformServiceJpaRepositoryImpl' }
    @{ Twin = 'AccountNumberFormatReadPlatformServiceImpl' }
    @{ Twin = 'ReadReportingServiceImpl' }
    @{ Twin = 'GenericDataServiceImpl' }
    @{ Twin = 'LoanReadPlatformServiceImpl' }
    @{ Twin = 'LoanProductWritePlatformServiceJpaRepositoryImpl' }
    @{ Twin = 'OfficeReadPlatformServiceImpl' }
    @{ Twin = 'OfficeWritePlatformServiceJpaRepositoryImpl' }
    @{ Twin = 'TellerManagementReadPlatformServiceImpl' }
    @{ Twin = 'TellerWritePlatformServiceJpaImpl' }
    @{ Twin = 'ChargeReadPlatformServiceImpl' }
    @{ Twin = 'ChargeWritePlatformServiceJpaRepositoryImpl' }
    @{ Twin = 'PaymentTypeWriteServiceImpl' }
    @{ Twin = 'SearchReadServiceImpl' }
    @{ Twin = 'SavingsAccountWritePlatformServiceJpaRepositoryImpl' }
    @{ Twin = 'SavingsAccountReadPlatformServiceImpl' }
    @{ Twin = 'SavingsAccountDomainServiceJpa' }
    @{ Twin = 'SavingsProductWritePlatformServiceJpaRepositoryImpl' }
    @{ Twin = 'SavingsProductReadPlatformServiceImpl' }
    @{ Twin = 'DepositProductReadPlatformServiceImpl' }
    @{ Twin = 'FixedDepositProductWritePlatformServiceJpaRepositoryImpl' }
    @{ Twin = 'RecurringDepositProductWritePlatformServiceJpaRepositoryImpl' }
    @{ Twin = 'ShareAccountWritePlatformServiceJpaRepositoryImpl' }
    @{ Twin = 'AppUserReadPlatformServiceImpl' }
    @{ Twin = 'AppUserWritePlatformServiceJpaRepositoryImpl' }
    @{ Twin = 'AuditReadPlatformServiceImpl' }
    @{ Twin = 'TwoFactorServiceImpl' }
    @{ Twin = 'TwoFactorConfigurationServiceImpl' }
)

function Find-UpstreamPath([string] $rev, [string] $simpleName) {
    $matches = git ls-tree -r --name-only $rev | Where-Object { $_ -match "/$simpleName\.java$" -and $_ -notmatch '^custom/' }
    return $matches | Select-Object -First 1
}

Write-Output "Twin drift $From .. $To"
Write-Output ""

foreach ($pair in $pairs) {
    $fromPath = $pair.FromPath
    if (-not $fromPath) {
        $fromPath = Find-UpstreamPath $From $pair.Twin
    }
    $toPath = $pair.ToPath
    if (-not $toPath) {
        $toPath = Find-UpstreamPath $To $pair.Twin
        if (-not $toPath) {
            $toPath = $fromPath
        }
    }
    if (-not $fromPath) {
        Write-Output "$($pair.Twin) : NOT FOUND at $From"
        continue
    }
    $fromExists = git cat-file -e "${From}:${fromPath}" 2>$null; $fromOk = $LASTEXITCODE -eq 0
    $toExists = git cat-file -e "${To}:${toPath}" 2>$null; $toOk = $LASTEXITCODE -eq 0
    if (-not $fromOk -and -not $toOk) {
        Write-Output "$($pair.Twin) : missing on both sides"
        continue
    }
    if (-not $fromOk) {
        Write-Output "$($pair.Twin) : ADDED at $toPath"
        continue
    }
    if (-not $toOk) {
        Write-Output "$($pair.Twin) : DELETED (was $fromPath)"
        continue
    }
    $stat = git diff --numstat "${From}:${fromPath}" "${To}:${toPath}" 2>$null
    if (-not $stat) {
        Write-Output "$($pair.Twin) : unchanged"
    } else {
        $parts = $stat -split '\s+'
        $rename = if ($fromPath -ne $toPath) { "  [renamed $fromPath -> $toPath]" } else { '' }
        Write-Output "$($pair.Twin) : +$($parts[0]) -$($parts[1])$rename"
    }
}
