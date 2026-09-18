param(
    [string] $ApiBaseUrl = "http://localhost:8081",
    [string] $RelatorioId,
    [string] $DataReferencia = (Get-Date -Format "yyyy-MM-dd"),
    [ValidateSet("MANHA", "TARDE", "NOITE")]
    [string] $Turno = "NOITE",
    [int] $TimeoutSeconds = 60,
    [int] $PollIntervalSeconds = 2,
    [switch] $PassThru
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

function Invoke-MerendaJson {
    param(
        [string] $Uri,
        [ValidateSet("GET", "POST")]
        [string] $Method,
        [object] $Body
    )

    $params = @{
        Uri = $Uri
        Method = $Method
        Headers = @{ Accept = "application/json" }
        UseBasicParsing = $true
    }

    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 10
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
        $params.ContentType = "application/json; charset=utf-8"
    }

    $response = Invoke-WebRequest @params
    if ($response.RawContentStream.CanSeek) {
        $response.RawContentStream.Position = 0
    }

    $reader = [System.IO.StreamReader]::new($response.RawContentStream, [System.Text.Encoding]::UTF8)
    $content = $reader.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($content)) {
        return $null
    }

    $content | ConvertFrom-Json
}

if ([string]::IsNullOrWhiteSpace($RelatorioId)) {
    $criacao = Invoke-MerendaJson `
        -Uri "$ApiBaseUrl/api/v1/relatorios-ia" `
        -Method POST `
        -Body @{
            dataReferencia = $DataReferencia
            turno = $Turno
        }

    $RelatorioId = $criacao.relatorioId
    Write-Host "Relatorio solicitado: $RelatorioId"
    Write-Host "Status inicial:       $($criacao.status)"
} else {
    Write-Host "Consultando relatorio existente: $RelatorioId"
}

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$statusUrl = "$ApiBaseUrl/api/v1/relatorios-ia/$RelatorioId"

do {
    Start-Sleep -Seconds $PollIntervalSeconds

    $relatorio = Invoke-MerendaJson `
        -Uri $statusUrl `
        -Method GET

    Write-Host "Status atual:         $($relatorio.status)"

    if ($relatorio.status -in @("CONCLUIDO", "FALHOU")) {
        break
    }
} while ((Get-Date) -lt $deadline)

if ($relatorio.status -notin @("CONCLUIDO", "FALHOU")) {
    throw "Timeout aguardando conclusao do relatorio $($criacao.relatorioId). Ultimo status: $($relatorio.status)"
}

Write-Host ""
Write-Host "Resumo do relatorio"
Write-Host "Status:            $($relatorio.status)"
Write-Host "Provedor/modelo:   $($relatorio.provedor) / $($relatorio.modelo)"
Write-Host "Tentativas:        $($relatorio.tentativas)"

if ($relatorio.resultado) {
    Write-Host "Aceitacao:         $($relatorio.resultado.nivelAceitacao)"
    Write-Host "Risco desperdicio: $($relatorio.resultado.riscoDesperdicio)"
    Write-Host "Resumo IA:         $($relatorio.resultado.resumoExecutivo)"
}

if ($relatorio.erro) {
    Write-Host "Erro:              $($relatorio.erro)"
}

if ($PassThru) {
    $relatorio
}
