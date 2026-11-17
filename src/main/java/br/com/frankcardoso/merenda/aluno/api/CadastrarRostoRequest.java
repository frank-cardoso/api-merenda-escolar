package br.com.frankcardoso.merenda.aluno.api;

import jakarta.validation.constraints.NotBlank;

public record CadastrarRostoRequest(@NotBlank String fotoBase64) {
}
