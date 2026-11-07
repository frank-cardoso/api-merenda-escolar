# Imagens de teste da demonstração

Fotos para usar em "Usar imagem" na fila de identificação facial. Cada foto é uma imagem
**diferente** da usada no cadastro do aluno de exemplo (o embedding de cadastro está em
`src/main/resources/demo/rostos-demo.json`).

| Arquivo | Aluno de exemplo (nome fictício) | Identidade no LFW |
|---|---|---|
| `ALU-9001-teste.jpg` | ALU-9001 Helena Duarte | Suzanne_Gaudet |
| `ALU-9002-teste.jpg` | ALU-9002 Lívia Campos | Patricia_Heaton |
| `ALU-9003-teste.jpg` | ALU-9003 Rafael Moreira | Stanley_McChrystal |
| `ALU-9004-teste.jpg` | ALU-9004 Otávio Brandão | Lennox_Lewis |
| `ALU-9005-teste.jpg` | ALU-9005 Lucas Ferreira | Lee_Jun |
| `desconhecido.jpg` | sem cadastro | Megan_Mullally |

`desconhecido.jpg` não tem cadastro: a fila deve responder "não reconhecido".

Os nomes dos alunos são fictícios. As fotos são de adultos do dataset público LFW, usadas só
para demonstração; nenhuma imagem de criança é usada.

## Pré-requisito em outra máquina

O detector opencv baixa `haarcascade_frontalface_default.xml` e `haarcascade_eye.xml` de github.com
para `~/.deepface/weights/` no primeiro uso (nesta máquina foi preciso `curl -4`), e os pesos do
Facenet (~90 MB) na primeira requisição. Rode o analytics uma vez com internet antes da apresentação.

## Origem

Dataset Labeled Faces in the Wild (LFW), baixado de https://ndownloader.figshare.com/files/5976018
e processado por `analytics-merenda-escolar/scripts/gerar_rostos_demo.py`.

Gary B. Huang, Manu Ramesh, Tamara Berg, Erik Learned-Miller. "Labeled Faces in the Wild: A
Database for Studying Face Recognition in Unconstrained Environments". University of
Massachusetts, Amherst, Technical Report 07-49, 2007.
