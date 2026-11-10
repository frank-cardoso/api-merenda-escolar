package br.com.frankcardoso.merenda.historico.infrastructure;

import static br.com.frankcardoso.merenda.refeicao.domain.RefeicaoPadrao.ALMOCO;
import static br.com.frankcardoso.merenda.refeicao.domain.RefeicaoPadrao.CAFE_DA_MANHA;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HistoricoPorRefeicaoTest {

    private static final LocalDate INICIO = LocalDate.of(2000, 1, 1);
    private static final LocalDate FIM = LocalDate.of(2100, 1, 1);

    @Autowired HistoricoConsumoRepository historicos;
    @Autowired EntityManager em;

    @Test
    void historicoDoLegadoEhMapeadoPorNomeDeRefeicao() {
        assertThat(contarDaRefeicao(ALMOCO.id())).isPositive();
        assertThat(contarDaRefeicao(CAFE_DA_MANHA.id())).isPositive();
        assertThat(historicos.countByTipoRefeicaoIdIsNullAndDataBetween(INICIO, FIM)).isPositive();
        assertThat(em.createQuery("""
            SELECT COUNT(h) FROM HistoricoConsumo h
            WHERE h.refeicao = 'Almoco - EMEIS'
              AND (h.tipoRefeicaoId IS NULL OR h.tipoRefeicaoId <> :almoco)
            """, Long.class).setParameter("almoco", ALMOCO.id()).getSingleResult()).isZero();
    }

    private long contarDaRefeicao(UUID id) {
        return em.createQuery("SELECT COUNT(h) FROM HistoricoConsumo h WHERE h.tipoRefeicaoId = :id",
            Long.class).setParameter("id", id).getSingleResult();
    }
}
