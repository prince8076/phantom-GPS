# Installs a Windows reverse-engineering toolchain.
# Run from an elevated PowerShell prompt:
#   Set-ExecutionPolicy -Scope Process Bypass
#   .\install-re-tools.ps1

$ErrorActionPreference = "Continue"
$failed = [System.Collections.Generic.List[string]]::new()

function Install-WinGetPackage {
    param(
        [Parameter(Mandatory=$true)][string]$Id,
        [Parameter(Mandatory=$true)][string]$Label
    )

    Write-Host "`n==> $Label ($Id)" -ForegroundColor Cyan
    & winget install --id $Id --exact --source winget `
        --accept-source-agreements --accept-package-agreements
    if ($LASTEXITCODE -ne 0) {
        $failed.Add($Label)
        Write-Warning "Could not install $Label automatically. Check the package ID with: winget search $Label"
    }
}

if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
    throw "winget is not available. Install/update App Installer from the Microsoft Store, then run this script again."
}

# Core runtimes and build tools.
Install-WinGetPackage "Git.Git" "Git"
Install-WinGetPackage "Python.Python.3.13" "Python"
Install-WinGetPackage "OpenJS.NodeJS.LTS" "Node.js LTS"
Install-WinGetPackage "Rustlang.Rustup" "Rust toolchain"
Install-WinGetPackage "Microsoft.DotNet.SDK.9" ".NET SDK"
Install-WinGetPackage "EclipseAdoptium.Temurin.21.JDK" "Eclipse Temurin JDK"

# Open-source analysis and observation tools.
Install-WinGetPackage "NationalSecurityAgency.Ghidra" "Ghidra"
Install-WinGetPackage "skylot.jadx" "JADX"
Install-WinGetPackage "WiresharkFoundation.Wireshark" "Wireshark"
Install-WinGetPackage "x64dbg.x64dbg" "x64dbg"
Install-WinGetPackage "Cutter.Cutter" "Cutter"
Install-WinGetPackage "VirusTotal.YARA" "YARA"
Install-WinGetPackage "icsharpcode.ILSpy" "ILSpy"

Write-Host "`n==> Installing command-line tools from official package registries" -ForegroundColor Cyan
if (Get-Command npm -ErrorAction SilentlyContinue) {
    npm install --global rea-agents
    if ($LASTEXITCODE -ne 0) { $failed.Add("REA agents") }
}

if (Get-Command py -ErrorAction SilentlyContinue) {
    py -m pip install --upgrade frida-tools
    if ($LASTEXITCODE -ne 0) { $failed.Add("Frida tools") }
}

Write-Host "`n==> Verification" -ForegroundColor Cyan
$checks = @(
    @{ Name = "java"; Args = @("-version") },
    @{ Name = "node"; Args = @("--version") },
    @{ Name = "python"; Args = @("--version") },
    @{ Name = "dotnet"; Args = @("--info") },
    @{ Name = "cargo"; Args = @("--version") },
    @{ Name = "rea"; Args = @("--version") },
    @{ Name = "frida"; Args = @("--version") }
)

foreach ($check in $checks) {
    if (Get-Command $check.Name -ErrorAction SilentlyContinue) {
        Write-Host "[OK] $($check.Name)" -ForegroundColor Green
    } else {
        Write-Host "[--] $($check.Name) not found in this PowerShell session" -ForegroundColor Yellow
    }
}

if ($failed.Count -gt 0) {
    Write-Warning "Some installations need manual attention: $($failed -join ', ')"
    Write-Host "Use 'winget search <name>' to find the current package ID, then rerun that install." -ForegroundColor Yellow
} else {
    Write-Host "All requested installation steps completed. Open a new PowerShell window before using the tools." -ForegroundColor Green
}
