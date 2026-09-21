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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
        return new CardapioService(repository, receitas, objectMapper, clock);
    }

    private CardapioRequest requisicao(LocalDate data, Turno turno) {
        return new CardapioRequest(
            data,
            turno,
            "Arroz com frango",
            "Cardapio de teste",
            List.of(new ItemCardapioDto(null, "arroz", "10 kg"),
                new ItemCardapioDto(null, "frango", "15 kg")),
            300
        );
    }

    @Test
    void deveCriarCardapioSerializandoOsItens() {
        when(repository.existsByDataAndTurnoAndAtivoTrue(HOJE, Turno.NOITE)).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var resposta = criarService().criar(requisicao(HOJE, Turno.NOITE));

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
    void naoDeveCriarCardapioParaDataPassada() {
        var service = criarService();
        var request = requisicao(HOJE.minusDays(1), Turno.NOITE);

        assertThatThrownBy(() -> service.criar(request))
            .isInstanceOf(CardapioDataPassadaException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void naoDeveCriarSegundoCardapioAtivoParaMesmaDataETurno() {
        when(repository.existsByDataAndTurnoAndAtivoTrue(HOJE, Turno.MANHA)).thenReturn(true);
        var service = criarService();
        var request = requisicao(HOJE, Turno.MANHA);

        assertThatThrownBy(() -> service.criar(request))
            .isInstanceOf(CardapioDuplicadoException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void deveDesativarSemRemoverALinha() {
        var id = UUID.randomUUID();
        var cardapio = new Cardapio(id, HOJE, Turno.TARDE, "Sopa", null, "[]", 100, true);
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
    void devePermitirAtualizarMantendoMesmaDataETurno() {
        var id = UUID.randomUUID();
        var cardapio = new Cardapio(id, HOJE, Turno.NOITE, "Antigo", null, "[]", 100, true);
        when(repository.findByIdAndAtivoTrue(id)).thenReturn(Optional.of(cardapio));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var resposta = criarService().atualizar(id, requisicao(HOJE, Turno.NOITE));

        assertThat(resposta.nomeRefeicao()).isEqualTo("Arroz com frango");
        assertThat(resposta.quantidadePlanejada()).isEqualTo(300);
        verify(repository, never()).existsByDataAndTurnoAndAtivoTrue(any(), any());
    }

    @Test
    void naoDeveAtualizarParaDataETurnoJaOcupados() {
        var id = UUID.randomUUID();
        var cardapio = new Cardapio(id, HOJE, Turno.NOITE, "Antigo", null, "[]", 100, true);
        when(repository.findByIdAndAtivoTrue(id)).thenReturn(Optional.of(cardapio));
        when(repository.existsByDataAndTurnoAndAtivoTrue(HOJE, Turno.MANHA)).thenReturn(true);
        var service = criarService();
        var request = requisicao(HOJE, Turno.MANHA);

        assertThatThrownBy(() -> service.atualizar(id, request))
            .isInstanceOf(CardapioDuplicadoException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void deveListarApenasCardapiosAtivos() {
        var cardapio = new Cardapio(UUID.randomUUID(), HOJE, Turno.MANHA, "Pao com leite", null,
            "[{\"nome\":\"pao\",\"quantidade\":\"50 un\"}]", 120, true);
        when(repository.findAllByAtivoTrueOrderByDataDescTurnoAsc()).thenReturn(List.of(cardapio));

        var lista = criarService().listar();

        assertThat(lista).hasSize(1);
        assertThat(lista.getFirst().itens()).singleElement()
            .satisfies(item -> assertThat(item.nome()).isEqualTo("pao"));
    }

    @Test
    void deveRecusarItemQueNaoExisteNoCatalogoDeReceitas() {
        lenient().when(receitas.findByNome("peixe")).thenReturn(Optional.empty());
        var request = new CardapioRequest(HOJE, Turno.NOITE, "Peixe", "",
            List.of(new ItemCardapioDto(null, "peixe", "5 kg")), 100);

        assertThatThrownBy(() -> criarService().criar(request))
            .isInstanceOf(ReceitaDesconhecidaException.class)
            .hasMessageContaining("peixe");
    }

    @Test
    void deveRecusarReceitaIdInexistente() {
        var idSolto = UUID.randomUUID();
        lenient().when(receitas.findById(idSolto)).thenReturn(Optional.empty());
        var request = new CardapioRequest(HOJE, Turno.NOITE, "Solto", "",
            List.of(new ItemCardapioDto(idSolto, "qualquer", "5 kg")), 100);

        assertThatThrownBy(() -> criarService().criar(request))
            .isInstanceOf(ReceitaDesconhecidaException.class)
            .hasMessageContaining(idSolto.toString());
    }
}
