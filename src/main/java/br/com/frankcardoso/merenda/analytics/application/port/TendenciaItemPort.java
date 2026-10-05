package br.com.frankcardoso.merenda.analytics.application.port;

import java.util.List;

public interface TendenciaItemPort {

    List<TendenciaItemOutput> calcular(List<TendenciaItemInput> itens);
}
