param(
    [string] $ApiBaseUrl = "http://localhost:8081",
    [string[]] $Alunos = @("ALU-001", "ALU-002", "ALU-003"),
    [ValidateSet("MANHA", "TARDE", "NOITE")]
    [string] $TurnoRelatorio = "NOITE",
    [string] $DataReferencia = (Get-Date -Format "yyyy-MM-dd")
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "== Simulando fila com QR Codes =="
& "$PSScriptRoot/validar-qrcodes.ps1" `
    -ApiBaseUrl $ApiBaseUrl `
    -Alunos $Alunos `
    -MetodoIdentificacao "QR_CODE" `
    -Repeticoes 1 `
    -IntervaloMs 200

Write-Host ""
Write-Host "== Gerando relatorio IA =="
& "$PSScriptRoot/gerar-relatorio.ps1" `
    -ApiBaseUrl $ApiBaseUrl `
    -DataReferencia $DataReferencia `
    -Turno $TurnoRelatorio
