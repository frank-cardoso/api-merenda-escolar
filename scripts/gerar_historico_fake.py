#!/usr/bin/env python3
"""Gera historico sintetico de consumo de merenda para alimentar a analise de IA.

Por que existe: sem historico, o modelo recebe um unico ponto de dado e toda analise
termina em "historico insuficiente". Este script produz dados que reproduzem os padroes
estatisticos medidos no sistema legado, sem copiar nenhum registro de producao.

Os numeros nas constantes abaixo vieram de queries agregadas rodadas no legado
(documentadas em Educa/Merenda/caracterizacao-padroes-para-faker.md). Cada constante
indica de qual query saiu.

Uso:
    python3 gerar_historico_fake.py
    python3 gerar_historico_fake.py --meses 18 --escolas 20 --seed 42
"""

from __future__ import annotations

import argparse
import csv
import math
import random
import sys
import uuid
from dataclasses import dataclass
from datetime import date, timedelta
from pathlib import Path

# ---------------------------------------------------------------------------
# Padroes medidos em producao
# ---------------------------------------------------------------------------

# Q-A: distribuicao da quantidade servida. Fortemente assimetrica — media (50,2) bem acima
# da mediana (30), cauda longa ate 494. Gerar com distribuicao normal produziria dado irreal,
# por isso a amostragem e por interpolacao de percentis.
PERCENTIS_QUANTIDADE = [
    (0.00, 0),
    (0.10, 0),
    (0.25, 4),
    (0.50, 30),
    (0.75, 67),
    (0.90, 140),
    (1.00, 494),
]
MEDIA_QUANTIDADE_REAL = 50.2

# Q-3: taxa global de execucao (linhas com quantidade preenchida).
TAXA_EXECUCAO_GLOBAL = 0.309

# Q-C: peso de cada turno e escala da quantidade. NOITE tem zero registros em producao;
# INTEGRAL e o mais comum. TARDE tem media bem mais alta que os demais.
TURNOS = [
    # (turno, peso, fator sobre a media global)
    ("INTEGRAL", 2591, 38.7 / MEDIA_QUANTIDADE_REAL),
    ("MANHA", 948, 42.3 / MEDIA_QUANTIDADE_REAL),
    ("TARDE", 549, 88.9 / MEDIA_QUANTIDADE_REAL),
]

# Q-E: quantos itens cada escola-dia registra. Distribuicao bimodal (pico em 3-5 e outro em
# 10-12), usada aqui como distribuicao empirica direta.
ITENS_POR_ESCOLA_DIA = {
    1: 7, 2: 23, 3: 775, 4: 747, 5: 406, 6: 93, 7: 22, 8: 28, 9: 200, 10: 361,
    11: 252, 12: 294, 13: 127, 14: 32, 15: 6, 16: 5, 17: 8, 18: 4, 19: 2,
}

# Q-G: tipos de refeicao, com peso e taxa de execucao propria. A variacao por tipo de refeicao
# (20% a 75%) e o sinal mais forte do dataset — bem maior que a variacao por item.
REFEICOES = [
    # (nome, peso, taxa de execucao)
    ("Lanches manha ou tarde - EMEFS parcial", 5052, 0.288),
    ("Almoco - EMEIS", 4650, 0.213),
    ("Lanches manha e tarde - Jardim EMEIS", 1962, 0.279),
    ("Cafe da manha EMEIS", 1794, 0.332),
    ("Lanche da tarde - EMEIS", 1622, 0.335),
    ("Almoco - Turnos Integrais", 1429, 0.200),
    ("Cafe de manha - Turnos Integrais", 1067, 0.410),
    ("Lanche da tarde - Turnos Integrais", 927, 0.247),
    ("Lanche do turno estendido - EMEIS", 891, 0.425),
    ("Cafe da manha EMEIS ou menores de 1 ano", 823, 0.640),
    ("Lanche da tarde, menores de um ano - EMEIS", 465, 0.617),
    ("Lanche da manha - Jardim EMEIS", 448, 0.324),
    ("Frutas - Lanches", 352, 0.463),
]

# Q-F: taxa de execucao por item, com os valores REAIS medidos.
#
# ATENCAO — limite conhecido: a query Q-F foi ordenada ascendente com limite de 40 linhas,
# entao estes sao os 40 itens de MENOR execucao. Os itens de alta execucao nao foram medidos.
# Como a media global e 31% e todos estes estao abaixo de 13%, existe necessariamente um grupo
# de alta execucao que aqui esta CALIBRADO (nao medido) — ver ITENS_CALIBRADOS.
#
# Para trocar a estimativa por dado real, rodar a Q-F novamente com ORDER BY pct_execucao DESC.
ITENS_MEDIDOS = [
    # (item, taxa de execucao medida)
    ("Carne de panela com batata", 0.000),
    ("Pure de mandioca", 0.021),
    ("Couve com farofa", 0.022),
    ("Mandioca cozida", 0.024),
    ("Molho de carne moida", 0.035),
    ("Pure de batata", 0.036),
    ("Polenta cozida", 0.036),
    ("Beterraba cozida", 0.048),
    ("Ovo cozido", 0.048),
    ("Cenoura ralada", 0.050),
    ("Iscas de frango acebolada", 0.050),
    ("Carne moida tipo pastel", 0.053),
    ("Molho de frango desfiado", 0.054),
    ("Cenoura refogada", 0.057),
    ("Brocolis", 0.058),
    ("Salada de tomate", 0.058),
    ("Coxa e sobrecoxa de frango ao molho de tomate", 0.061),
    ("Chuchu", 0.063),
    ("Carne de panela", 0.066),
    ("Requeijao caseiro", 0.067),
    ("Frango dourado, acebolado", 0.069),
    ("Couve flor", 0.069),
    ("Salada de alface", 0.072),
    ("Beterraba ralada", 0.072),
    ("Repolho", 0.077),
    ("Arroz com galinha", 0.077),
    ("Molho de tomate caseiro", 0.078),
    ("Chuchu com tempero verde", 0.084),
    ("Carne suina acebolada", 0.085),
    ("Cenoura cozida", 0.086),
    ("Ensopado de mandioca", 0.086),
    ("Couve", 0.094),
    ("Pure de moranga", 0.100),
    ("Guisado com moranga", 0.102),
    ("Ovo mexido", 0.121),
    ("Bolo de maca, sem acucar", 0.123),
]

# Itens de alta execucao — CALIBRADOS, nao medidos (ver nota em ITENS_MEDIDOS).
# A taxa e ajustada em tempo de execucao para a media global fechar em TAXA_EXECUCAO_GLOBAL.
#
# Sao os basicos do cardapio. Recebem peso de frequencia maior que os itens medidos porque em
# producao a frequencia varia muito (repolho aparece 505 vezes, "carne de panela com batata"
# apenas 20) — e os itens medidos, todos de baixa execucao, sao os menos frequentes.
ITENS_CALIBRADOS = [
    "Arroz branco",
    "Feijao carioca",
    "Pao com manteiga",
    "Leite com achocolatado",
    "Banana",
    "Maca",
    "Macarrao ao sugo",
    "Sopa de legumes",
    "Bolacha doce",
    "Suco de fruta natural",
    "Iogurte",
    "Cuscuz",
]

# Peso relativo de frequencia: um basico aparece cerca de 8x mais que um acompanhamento.
PESO_ITEM_MEDIDO = 1.0
PESO_ITEM_CALIBRADO = 8.0

DIAS_UTEIS = {0, 1, 2, 3, 4}  # Q-B: producao so tem registro de segunda a sexta.


@dataclass
class Registro:
    id: str
    data: date
    turno: str
    escola_id: int
    refeicao: str
    item: str
    quantidade_planejada: int
    quantidade_servida: int | None
    origem: str = "SINTETICO"


QUANTIDADE_MAXIMA_REAL = PERCENTIS_QUANTIDADE[-1][1]


def amostrar_quantidade(rng: random.Random, fator_turno: float) -> int:
    """Amostra pela curva real de percentis (Q-A), interpolando entre os pontos medidos.

    O resultado e limitado ao maximo observado em producao: sem o teto, o fator do turno TARDE
    (1,77) levaria a cauda bem acima do maximo real de 494.
    """
    u = rng.random()
    for (p_baixo, v_baixo), (p_alto, v_alto) in zip(PERCENTIS_QUANTIDADE, PERCENTIS_QUANTIDADE[1:]):
        if u <= p_alto:
            faixa = p_alto - p_baixo
            posicao = (u - p_baixo) / faixa if faixa else 0.0
            valor = v_baixo + posicao * (v_alto - v_baixo)
            return max(0, min(QUANTIDADE_MAXIMA_REAL, round(valor * fator_turno)))
    return QUANTIDADE_MAXIMA_REAL


def estimar_taxa_resultante(taxa_alta: float, seed: int, amostras: int = 20000) -> float:
    """Estima a taxa global de execucao que uma dada taxa_alta produziria.

    Reproduz exatamente a logica de sorteio da geracao (top-k ponderado + modulador da
    refeicao), porque e justamente essa combinacao que uma conta analitica nao captura: o
    top-k ponderado super-seleciona os itens de peso alto em relacao a um sorteio proporcional.
    """
    rng = random.Random(seed)
    catalogo = [taxa for _, taxa in ITENS_MEDIDOS] + [taxa_alta] * len(ITENS_CALIBRADOS)
    pesos = [PESO_ITEM_MEDIDO] * len(ITENS_MEDIDOS) + [PESO_ITEM_CALIBRADO] * len(ITENS_CALIBRADOS)

    contagens = list(ITENS_POR_ESCOLA_DIA.keys())
    pesos_contagem = [float(v) for v in ITENS_POR_ESCOLA_DIA.values()]
    taxas_refeicao = [taxa for _, _, taxa in REFEICOES]
    pesos_refeicao = [float(peso) for _, peso, _ in REFEICOES]

    soma_chances = 0.0
    total = 0
    while total < amostras:
        quantos = sortear(rng, contagens, pesos_contagem)
        taxa_refeicao = sortear(rng, taxas_refeicao, pesos_refeicao)
        modulador = taxa_refeicao / TAXA_EXECUCAO_GLOBAL
        for taxa_item in sortear_sem_repetir(rng, catalogo, pesos, k=min(quantos, len(catalogo))):
            soma_chances += min(1.0, taxa_item * modulador)
            total += 1

    return soma_chances / total


def calibrar_taxa_alta(seed: int) -> float:
    """Encontra por bissecao a taxa do grupo nao medido que fecha a media global em 31%."""
    baixo, alto = 0.0, 1.0
    for _ in range(24):
        meio = (baixo + alto) / 2
        if estimar_taxa_resultante(meio, seed) < TAXA_EXECUCAO_GLOBAL:
            baixo = meio
        else:
            alto = meio
    return (baixo + alto) / 2


def sortear(rng: random.Random, opcoes: list, pesos: list[float]):
    return rng.choices(opcoes, weights=pesos, k=1)[0]


def sortear_sem_repetir(rng: random.Random, opcoes: list, pesos: list[float], k: int) -> list:
    """Amostragem ponderada sem reposicao (Efraimidis-Spirakis).

    `random.sample` nao aceita pesos e `random.choices` repete itens — um cardapio com "arroz"
    duas vezes seria irreal. Aqui cada opcao recebe a chave u**(1/peso) e ficam as k maiores.
    """
    chaveados = [
        (rng.random() ** (1.0 / peso) if peso > 0 else 0.0, indice)
        for indice, peso in enumerate(pesos)
    ]
    chaveados.sort(reverse=True)
    return [opcoes[indice] for _, indice in chaveados[:k]]


def gerar(meses: int, escolas: int, seed: int, prob_registro: float) -> list[Registro]:
    rng = random.Random(seed)

    taxa_alta = calibrar_taxa_alta(seed)
    catalogo = [(nome, taxa) for nome, taxa in ITENS_MEDIDOS]
    catalogo += [(nome, taxa_alta) for nome in ITENS_CALIBRADOS]
    pesos_catalogo = (
        [PESO_ITEM_MEDIDO] * len(ITENS_MEDIDOS)
        + [PESO_ITEM_CALIBRADO] * len(ITENS_CALIBRADOS)
    )

    nomes_refeicao = [nome for nome, _, _ in REFEICOES]
    pesos_refeicao = [float(peso) for _, peso, _ in REFEICOES]
    taxa_por_refeicao = {nome: taxa for nome, _, taxa in REFEICOES}

    nomes_turno = [nome for nome, _, _ in TURNOS]
    pesos_turno = [float(peso) for _, peso, _ in TURNOS]
    fator_por_turno = {nome: fator for nome, _, fator in TURNOS}

    contagens_itens = list(ITENS_POR_ESCOLA_DIA.keys())
    pesos_itens = [float(v) for v in ITENS_POR_ESCOLA_DIA.values()]

    # Q-D: periodo estavel, sem reproduzir a curva de adocao e abandono do legado.
    fim = date.today()
    inicio = fim - timedelta(days=meses * 30)

    registros: list[Registro] = []
    dia = inicio
    while dia <= fim:
        if dia.weekday() in DIAS_UTEIS:
            for escola_id in range(1, escolas + 1):
                if rng.random() > prob_registro:
                    continue

                total_itens = sortear(rng, contagens_itens, pesos_itens)
                turno = sortear(rng, nomes_turno, pesos_turno)
                fator_turno = fator_por_turno[turno]

                # Os itens do dia se distribuem entre 1 e 3 tipos de refeicao.
                qtd_refeicoes = min(rng.randint(1, 3), total_itens)
                refeicoes_do_dia = sortear_sem_repetir(
                    rng, nomes_refeicao, pesos_refeicao,
                    k=min(qtd_refeicoes, len(nomes_refeicao)),
                )
                itens_do_dia = sortear_sem_repetir(
                    rng, catalogo, pesos_catalogo, k=min(total_itens, len(catalogo)))

                for indice, (item, taxa_item) in enumerate(itens_do_dia):
                    refeicao = refeicoes_do_dia[indice % len(refeicoes_do_dia)]

                    # A chance de execucao combina os dois sinais medidos: o do tipo de
                    # refeicao (Q-G) e o do item (Q-F), este modulado pelo primeiro.
                    modulador = taxa_por_refeicao[refeicao] / TAXA_EXECUCAO_GLOBAL
                    chance = min(1.0, taxa_item * modulador)

                    executou = rng.random() < chance
                    if executou:
                        # A curva de percentis da Q-A foi medida sobre a quantidade SERVIDA,
                        # entao ela e amostrada primeiro e o planejado deriva dela. Amostrar as
                        # duas de forma independente produzia servido acima do planejado (taxa
                        # de execucao passando de 100%, impossivel).
                        servida = amostrar_quantidade(rng, fator_turno)
                        aproveitamento = 0.5 + rng.random() * 0.5
                        planejada = max(1, math.ceil(servida / aproveitamento))
                    else:
                        servida = None
                        planejada = amostrar_quantidade(rng, fator_turno) or 1

                    registros.append(Registro(
                        id=str(uuid.uuid4()),
                        data=dia,
                        turno=turno,
                        escola_id=escola_id,
                        refeicao=refeicao,
                        item=item,
                        quantidade_planejada=planejada,
                        quantidade_servida=servida,
                    ))
        dia += timedelta(days=1)

    return registros


def relatar(registros: list[Registro], taxa_alta: float) -> None:
    """Compara o resultado gerado com os alvos medidos, para conferir a fidelidade."""
    total = len(registros)
    executados = [r for r in registros if r.quantidade_servida is not None]
    quantidades = sorted(r.quantidade_servida for r in executados)

    def percentil(p: float) -> float:
        if not quantidades:
            return 0.0
        return quantidades[min(len(quantidades) - 1, int(p * len(quantidades)))]

    print(f"Registros gerados: {total}")
    print(f"Taxa de execucao : {len(executados) / total:.1%} (alvo {TAXA_EXECUCAO_GLOBAL:.1%})")
    if executados:
        media = sum(quantidades) / len(quantidades)
        print(f"Quantidade media : {media:.1f} (alvo {MEDIA_QUANTIDADE_REAL})")
        print(f"Mediana          : {percentil(0.50):.0f} (alvo 30)")
        print(f"P90              : {percentil(0.90):.0f} (alvo 140)")
        print(f"Maximo           : {quantidades[-1]} (alvo 494)")
    print(f"Taxa calibrada do grupo nao medido: {taxa_alta:.1%}")

    por_turno: dict[str, int] = {}
    for r in registros:
        por_turno[r.turno] = por_turno.get(r.turno, 0) + 1
    distribuicao = ", ".join(f"{t} {c / total:.0%}" for t, c in sorted(por_turno.items()))
    print(f"Distribuicao por turno: {distribuicao}")


def escrever_csv(registros: list[Registro], destino: Path) -> None:
    """Escreve em TSV, nao CSV.

    Varios nomes reais contem virgula ("Bolo de maca, sem acucar", "Lanche da tarde, menores
    de um ano"). Com tabulacao o leitor do lado Java pode fazer split simples, sem precisar de
    um parser de campos com aspas.
    """
    destino.parent.mkdir(parents=True, exist_ok=True)
    with destino.open("w", newline="", encoding="utf-8") as arquivo:
        writer = csv.writer(arquivo, delimiter="\t", lineterminator="\n")
        writer.writerow([
            "id", "data", "turno", "escola_id", "refeicao", "item",
            "quantidade_planejada", "quantidade_servida", "origem",
        ])
        for r in registros:
            writer.writerow([
                r.id, r.data.isoformat(), r.turno, r.escola_id, r.refeicao, r.item,
                r.quantidade_planejada,
                "" if r.quantidade_servida is None else r.quantidade_servida,
                r.origem,
            ])


def escrever_catalogo(destino: Path, taxa_alta: float) -> None:
    """Publica o catalogo de itens com a taxa de execucao de cada um.

    O lado Java usa este arquivo para montar cardapios com o MESMO vocabulario do historico.
    Sem isso os nomes divergem ("arroz" no cardapio, "Arroz branco" no historico) e a analise
    nunca consegue relacionar o cardapio do dia com o padrao historico.
    """
    destino.parent.mkdir(parents=True, exist_ok=True)
    with destino.open("w", newline="", encoding="utf-8") as arquivo:
        writer = csv.writer(arquivo, delimiter="\t", lineterminator="\n")
        writer.writerow(["item", "taxa_execucao", "grupo"])
        for nome, taxa in ITENS_MEDIDOS:
            writer.writerow([nome, f"{taxa:.3f}", "BAIXA_EXECUCAO"])
        for nome in ITENS_CALIBRADOS:
            writer.writerow([nome, f"{taxa_alta:.3f}", "ALTA_EXECUCAO"])


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--meses", type=int, default=12, help="meses de historico (padrao 12)")
    parser.add_argument("--escolas", type=int, default=18, help="escolas ativas (padrao 18)")
    parser.add_argument("--seed", type=int, default=2026, help="semente, para saida reproduzivel")
    parser.add_argument(
        "--prob-registro", type=float, default=0.75,
        help="chance de uma escola registrar em um dia util (padrao 0.75)",
    )
    parser.add_argument(
        "--saida", type=Path,
        default=Path(__file__).resolve().parents[1] / "src/main/resources/dados/historico-fake.tsv",
        help="caminho do TSV de saida",
    )
    args = parser.parse_args()

    registros = gerar(args.meses, args.escolas, args.seed, args.prob_registro)
    if not registros:
        print("Nenhum registro gerado — revise os parametros.", file=sys.stderr)
        return 1

    taxa_alta = calibrar_taxa_alta(args.seed)
    escrever_csv(registros, args.saida)
    escrever_catalogo(args.saida.with_name("itens-catalogo.tsv"), taxa_alta)
    relatar(registros, taxa_alta)
    print(f"Arquivo: {args.saida}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
