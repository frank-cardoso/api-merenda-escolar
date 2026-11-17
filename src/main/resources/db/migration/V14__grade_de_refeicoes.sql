-- Grade de refeicoes: cada refeicao tem uma janela [inicio, fim) e os turnos com direito a ela.
CREATE TABLE tipo_refeicao (
    id UUID PRIMARY KEY,
    nome VARCHAR(60) NOT NULL UNIQUE,
    ordem INTEGER NOT NULL UNIQUE,
    inicio TIME NOT NULL,
    fim TIME NOT NULL,
    CHECK (inicio < fim)
);

CREATE TABLE tipo_refeicao_turno (
    tipo_refeicao_id UUID NOT NULL REFERENCES tipo_refeicao(id),
    turno VARCHAR(20) NOT NULL,
    PRIMARY KEY (tipo_refeicao_id, turno)
);

INSERT INTO tipo_refeicao (id, nome, ordem, inicio, fim) VALUES
    (CAST('5e1a0000-0000-4000-8000-000000000001' AS UUID), 'Café da manhã', 1, TIME '07:00:00', TIME '08:00:00'),
    (CAST('5e1a0000-0000-4000-8000-000000000002' AS UUID), 'Lanche da manhã', 2, TIME '09:30:00', TIME '10:30:00'),
    (CAST('5e1a0000-0000-4000-8000-000000000003' AS UUID), 'Almoço', 3, TIME '11:30:00', TIME '13:00:00'),
    (CAST('5e1a0000-0000-4000-8000-000000000004' AS UUID), 'Lanche da tarde', 4, TIME '15:00:00', TIME '16:00:00'),
    (CAST('5e1a0000-0000-4000-8000-000000000005' AS UUID), 'Lanche da noite', 5, TIME '20:00:00', TIME '21:00:00');

INSERT INTO tipo_refeicao_turno (tipo_refeicao_id, turno) VALUES
    (CAST('5e1a0000-0000-4000-8000-000000000001' AS UUID), 'INTEGRAL'),
    (CAST('5e1a0000-0000-4000-8000-000000000002' AS UUID), 'MANHA'),
    (CAST('5e1a0000-0000-4000-8000-000000000002' AS UUID), 'INTEGRAL'),
    (CAST('5e1a0000-0000-4000-8000-000000000003' AS UUID), 'INTEGRAL'),
    (CAST('5e1a0000-0000-4000-8000-000000000004' AS UUID), 'TARDE'),
    (CAST('5e1a0000-0000-4000-8000-000000000004' AS UUID), 'INTEGRAL'),
    (CAST('5e1a0000-0000-4000-8000-000000000005' AS UUID), 'NOITE');
