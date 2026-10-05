param(
    [string] $ApiBaseUrl = "http://localhost:8081",
    [string[]] $Alunos = @("ALU-001", "ALU-002", "ALU-003"),
    [ValidateSet("QR_CODE", "FACIAL")]
    [string] $MetodoIdentificacao = "QR_CODE",
    [int] $Repeticoes = 1,
    [int] $IntervaloMs = 200,
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

function Invoke-ValidacaoQrCode {
    param(
        [string] $AlunoCodigo
    )

    Invoke-MerendaJson `
        -Uri "$ApiBaseUrl/api/v1/fila/validacoes" `
        -Method POST `
        -Body @{
        alunoCodigo = $AlunoCodigo
        metodoIdentificacao = $MetodoIdentificacao
    }
}

$resultados = New-Object System.Collections.Generic.List[object]

for ($rodada = 1; $rodada -le $Repeticoes; $rodada++) {
    foreach ($aluno in $Alunos) {
        try {
            $resposta = Invoke-ValidacaoQrCode -AlunoCodigo $aluno
            $resultados.Add([pscustomobject]@{
                Rodada = $rodada
                AlunoCodigo = $aluno
                Resultado = $resposta.resultado
                Sinal = $resposta.sinal
                Turno = $resposta.turno
                AlunoNome = $resposta.alunoNome
                Motivo = $resposta.motivo
                RegistradoEm = $resposta.registradoEm
            })
        } catch {
            $resultados.Add([pscustomobject]@{
                Rodada = $rodada
                AlunoCodigo = $aluno
                Resultado = "ERRO"
                Sinal = "-"
                Turno = "-"
                AlunoNome = "-"
                Motivo = $_.Exception.Message
                RegistradoEm = $null
            })
        }

        if ($IntervaloMs -gt 0) {
            Start-Sleep -Milliseconds $IntervaloMs
        }
    }
}

$resultados | Format-Table -AutoSize

$autorizados = @($resultados | Where-Object Resultado -eq "AUTORIZADO").Count
$bloqueados = @($resultados | Where-Object Resultado -eq "BLOQUEADO").Count
$erros = @($resultados | Where-Object Resultado -eq "ERRO").Count

Write-Host ""
Write-Host "Resumo da fila"
Write-Host "Autorizados: $autorizados"
Write-Host "Bloqueados:  $bloqueados"
Write-Host "Erros:       $erros"

if ($PassThru) {
    $resultados
}
