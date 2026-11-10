package br.com.frankcardoso.merenda.cardapio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import br.com.frankcardoso.merenda.cardapio.api.CardapioRequest;
import br.com.frankcardoso.merenda.cardapio.api.ItemCardapioDto;
import br.com.frankcardoso.merenda.cardapio.domain.Cardapio;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.catalogo.domain.Receita;
import br.com.frankcardoso.merenda.catalogo.infrastructure.ReceitaRepository;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.refeicao.application.GradeRefeicoesService;
import br.com.frankcardoso.merenda.refeicao.application.TipoRefeicaoNaoEncontradoException;
import br.com.frankcardoso.merenda.refeicao.domain.TipoRefeicao;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CardapioServiceTest {

    private static final Instant AGORA = Instant.parse("2026-09-03T12:00:00Z");
    private static final LocalDate HOJE = LocalDate.of(2026, 9, 3);

    @Mock
    private CardapioRepository repository;

    @Mock
    private ReceitaRepository receitas;

    @Mock
    private GradeRefeicoesService grade;

    private static final TipoRefeicao CAFE = new TipoRefeicao(UUID.randomUUID(), "Café da manhã", 1,
        LocalTime.of(7, 0), LocalTime.of(8, 0), Set.of(Turno.INTEGRAL));
    private static final TipoRefeicao LANCHE_DA_MANHA = new TipoRefeicao(UUID.randomUUID(),
        "Lanche da manhã", 2, LocalTime.of(9, 30), LocalTime.of(10, 30), Set.of(Turno.MANHA));
    private static final TipoRefeicao ALMOCO = new TipoRefeicao(UUID.randomUUID(), "Almoço", 3,
        LocalTime.of(11, 30), LocalTime.of(13, 0), Set.of(Turno.INTEGRAL));

    private static final UUID ID_ARROZ = UUID.randomUUID();
    private static final UUID ID_FRANGO = UUID.randomUUID();

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);

    private CardapioService criarService() {
        // O cadastro resolve o id da receita pelo nome exato; sem catalogo nada e aceito.
        lenient().when(receitas.findByNome("arroz"))
            .thenReturn(Optional.of(new Receita(ID_ARROZ, "arroz", null)));
        lenient().when(receitas.findByNome("frango"))
            .thenReturn(Optional.of(new Receita(ID_FRANGO, "frango", null)));
        lenient().when(grade.buscar(CAFE.getId())).thenReturn(CAFE);
        lenient().when(grade.buscar(LANCHE_DA_MANHA.getId())).thenReturn(LANCHE_DA_MANHA);
        lenient().when(grade.buscar(ALMOCO.getId())).thenReturn(ALMOCO);
        lenient().when(grade.listar()).thenReturn(List.of(CAFE, LANCHE_DA_MANHA, ALMOCO));
        return new CardapioService(repository, receitas, grade, objectMapper, clock);
    }

    private Cardapio cardapioExistente(UUID id, TipoRefeicao refeicao, String nome) {
        return new Cardapio(id, HOJE, refeicao.getId(), nome, null, "[]", 100, true);
    }

    private CardapioRequest requisicao(LocalDate data, TipoRefeicao refeicao) {
        return new CardapioRequest(
            data,
            refeicao.getId(),
            "Arroz com frango",
            "Cardapio de teste",
            List.of(new ItemCardapioDto(null, "arroz", "10 kg"),
                new ItemCardapioDto(null, "frango", "15 kg")),
            300
        );
    }

    @Test
    void deveCriarCardapioSerializandoOsItens() {
        when(repository.existsByDataAndTipoRefeicaoIdAndAtivoTrue(HOJE, ALMOCO.getId())).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var resposta = criarService().criar(requisicao(HOJE, ALMOCO));

        var captor = ArgumentCaptor.forClass(Cardapio.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getItensJson())
            .isEqualTo(("[{\"receitaId\":\"%s\",\"nome\":\"arroz\",\"quantidade\":\"10 kg\"},"
                + "{\"receitaId\":\"%s\",\"nome\":\"frango\",\"quantidade\":\"15 kg\"}]")
                .formatted(ID_ARROZ, ID_FRANGO));
        assertThat(captor.getValue().isAtivo()).isTrue();
        assertThat(resposta.itens()).hasSize(2);
        assertThat(resposta.itens().getFirst().nome()).isEqualTo("arroz");
    }

    @Test
    void deveCriarCardapioDaRefeicao() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var resposta = criarService().criar(requisicao(HOJE, ALMOCO));

        var captor = ArgumentCaptor.forClass(Cardapio.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTipoRefeicaoId()).isEqualTo(ALMOCO.getId());
        assertThat(resposta.tipoRefeicaoId()).isEqualTo(ALMOCO.getId());
        assertThat(resposta.refeicao()).isEqualTo("Almoço");
    }

    @Test
    void permiteCardapiosDeRefeicoesDiferentesNoMesmoDia() {
        when(repository.existsByDataAndTipoRefeicaoIdAndAtivoTrue(HOJE, LANCHE_DA_MANHA.getId()))
            .thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var resposta = criarService().criar(requisicao(HOJE, LANCHE_DA_MANHA));

        assertThat(resposta.refeicao()).isEqualTo("Lanche da manhã");
    }

    @Test
    void recusaRefeicaoForaDaGrade() {
        var desconhecida = UUID.randomUUID();
        when(grade.buscar(desconhecida)).thenThrow(new TipoRefeicaoNaoEncontradoException(desconhecida));
        var service = criarService();
        var request = new CardapioRequest(HOJE, desconhecida, "Arroz", null,
            List.of(new ItemCardapioDto(null, "arroz", "10 kg")), 100);

        assertThatThrownBy(() -> service.criar(request))
            .isInstanceOf(TipoRefeicaoNaoEncontradoException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void listaPorDataDecrescenteENaOrdemDaGrade() {
        var almoco = cardapioExistente(UUID.randomUUID(), ALMOCO, "Almoço do dia");
        var cafe = cardapioExistente(UUID.randomUUID(), CAFE, "Café do dia");
        var anterior = new Cardapio(UUID.randomUUID(), HOJE.minusDays(1), CAFE.getId(), "Ontem", null,
            "[]", 100, true);
        when(repository.findAllByAtivoTrueOrderByDataDesc()).thenReturn(List.of(almoco, cafe, anterior));

        var lista = criarService().listar();

        assertThat(lista).extracting(item -> item.nomeRefeicao())
            .containsExactly("Café do dia", "Almoço do dia", "Ontem");
    }

    @Test
    void naoDeveCriarCardapioParaDataPassada() {
        var service = criarService();
        var request = requisicao(HOJE.minusDays(1), ALMOCO);

        assertThatThrownBy(() -> service.criar(request))
            .isInstanceOf(CardapioDataPassadaException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void naoDeveCriarSegundoCardapioAtivoParaMesmaDataERefeicao() {
        when(repository.existsByDataAndTipoRefeicaoIdAndAtivoTrue(HOJE, ALMOCO.getId())).thenReturn(true);
        var service = criarService();
        var request = requisicao(HOJE, ALMOCO);

        assertThatThrownBy(() -> service.criar(request))
            .isInstanceOf(CardapioDuplicadoException.class)
            .hasMessage("Já existe um cardápio ativo para 2026-09-03 em Almoço.");

        verify(repository, never()).save(any());
    }

    @Test
    void deveDesativarSemRemoverALinha() {
        var id = UUID.randomUUID();
        var cardapio = cardapioExistente(id, ALMOCO, "Sopa");
        when(repository.findByIdAndAtivoTrue(id)).thenReturn(Optional.of(cardapio));

        criarService().desativar(id);

        assertThat(cardapio.isAtivo()).isFalse();
        verify(repository).save(cardapio);
        verify(repository, never()).delete(any());
    }

    @Test
    void deveFalharAoDesativarCardapioInexistente() {
        var id = UUID.randomUUID();
        when(repository.findByIdAndAtivoTrue(id)).thenReturn(Optional.empty());
        var service = criarService();

        assertThatThrownBy(() -> service.desativar(id))
            .isInstanceOf(CardapioNaoEncontradoPorIdException.class);
    }

    @Test
    void devePermitirAtualizarMantendoMesmaDataERefeicao() {
        var id = UUID.randomUUID();
        var cardapio = cardapioExistente(id, ALMOCO, "Antigo");
        when(repository.findByIdAndAtivoTrue(id)).thenReturn(Optional.of(cardapio));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var resposta = criarService().atualizar(id, requisicao(HOJE, ALMOCO));

        assertThat(resposta.nomeRefeicao()).isEqualTo("Arroz com frango");
        assertThat(resposta.quantidadePlanejada()).isEqualTo(300);
        verify(repository, never()).existsByDataAndTipoRefeicaoIdAndAtivoTrue(any(), any());
    }

    @Test
    void naoDeveAtualizarParaDataERefeicaoJaOcupadas() {
        var id = UUID.randomUUID();
        var cardapio = cardapioExistente(id, ALMOCO, "Antigo");
        when(repository.findByIdAndAtivoTrue(id)).thenReturn(Optional.of(cardapio));
        when(repository.existsByDataAndTipoRefeicaoIdAndAtivoTrue(HOJE, LANCHE_DA_MANHA.getId()))
            .thenReturn(true);
        var service = criarService();
        var request = requisicao(HOJE, LANCHE_DA_MANHA);

        assertThatThrownBy(() -> service.atualizar(id, request))
            .isInstanceOf(CardapioDuplicadoException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void deveListarApenasCardapiosAtivos() {
        var cardapio = new Cardapio(UUID.randomUUID(), HOJE, LANCHE_DA_MANHA.getId(), "Pao com leite", null,
            "[{\"nome\":\"pao\",\"quantidade\":\"50 un\"}]", 120, true);
        when(repository.findAllByAtivoTrueOrderByDataDesc()).thenReturn(List.of(cardapio));

        var lista = criarService().listar();

        assertThat(lista).hasSize(1);
        assertThat(lista.getFirst().itens()).singleElement()
            .satisfies(item -> assertThat(item.nome()).isEqualTo("pao"));
    }

    @Test
    void deveRecusarItemQueNaoExisteNoCatalogoDeReceitas() {
        lenient().when(receitas.findByNome("peixe")).thenReturn(Optional.empty());
        var request = new CardapioRequest(HOJE, ALMOCO.getId(), "Peixe", "",
            List.of(new ItemCardapioDto(null, "peixe", "5 kg")), 100);

        assertThatThrownBy(() -> criarService().criar(request))
            .isInstanceOf(ReceitaDesconhecidaException.class)
            .hasMessageContaining("peixe");
    }

    @Test
    void deveRecusarReceitaIdInexistente() {
        var idSolto = UUID.randomUUID();
        lenient().when(receitas.findById(idSolto)).thenReturn(Optional.empty());
        var request = new CardapioRequest(HOJE, ALMOCO.getId(), "Solto", "",
            List.of(new ItemCardapioDto(idSolto, "qualquer", "5 kg")), 100);

        assertThatThrownBy(() -> criarService().criar(request))
            .isInstanceOf(ReceitaDesconhecidaException.class)
            .hasMessageContaining(idSolto.toString());
    }
}
