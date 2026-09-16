# Reserva para barcos e remoção — 0.8.3

A run saiu com 1.200 coins para os seis patches, valor reservado apenas para remover árvores. O usuário relatou que o barco até Nemus exigia mais dinheiro. O Agent Server confirmou o personagem em (2674, 3144, 0) com 1.200 coins em 11/09/2026.

O preparo agora exige 5.000 coins por patch habilitado (200 × 25), totalizando 30.000 para os seis locais. Esse valor é uma reserva solicitada para viagens e remoção, não um cálculo exato das tarifas do walker.

- Retira somente a diferença: com 1.200 na mochila, busca 28.800.
- Confere dinheiro mesmo quando saplings e ferramentas já estão completos.
- Sem saldo suficiente na mochila mais banco, aguarda e informa a quantidade faltante.
- Falha na retirada de moedas bloqueia o preparo como qualquer material obrigatório.
- Dinheiro ocupa uma pilha; valores excedentes são mantidos.
- A reserva é conferida uma vez por run. Gastos durante a run não provocam reposição a cada patch.
- Runas continuam opcionais. Moedas não substituem o pagamento de proteção ao jardineiro.

Build Java 11 / Microbot 2.6.22 e 78 testes passaram. As regressões verificam reposição com os demais materiais completos, saldo insuficiente, falha de retirada, espaço de uma pilha e manutenção de excedentes. O trajeto de barco com a nova reserva não foi repetido ao vivo nesta alteração.
