# Fruit trees — 0.9.0

Árvores comuns e frutíferas compartilham preparo, walker, confirmações de ações e proteção. Possuem seleção de sapling, ativação e proteção independentes. A configuração anterior de willow/árvores comuns permanece válida; `Automatically tend fruit trees` começa desligada e `Fruit tree to plant` começa em Apple.

## Saplings e pagamento por patch

| Frutífera | Nível | Sapling | Nota do sapling | Proteção | ID item / nota |
|---|---:|---:|---:|---|---|
| Apple | 27 | 5496 | 12946 | 9 Sweetcorn | 5986 / 5987 |
| Banana | 33 | 5497 | 12947 | 4 Apples(5) | 5386 / 5387 |
| Orange | 39 | 5498 | 12948 | 3 Strawberries(5) | 5406 / 5407 |
| Curry | 42 | 5499 | 12949 | 5 Bananas(5) | 5416 / 5417 |
| Pineapple | 51 | 5500 | 12950 | 10 Watermelon | 5982 / 5983 |
| Papaya | 57 | 5501 | 12951 | 10 Pineapple | 2114 / 2115 |
| Palm | 68 | 5502 | 12952 | 15 Papaya fruit | 5972 / 5973 |
| Dragonfruit | 81 | 22866 | 22867 | 15 Coconut | 5974 / 5975 |

Saplings são plantados sem nota. Pagamentos podem ser usados em nota. Valores conferidos no `FruitTreeEnum` do Hub e no cache do cliente via Agent Server, incluindo os vínculos de notas.

## Patches solicitados

| Local | Destino | Região / aliases | Varbit | Objeto | Jardineiro |
|---|---|---|---:|---:|---|
| Gnome Stronghold | 2473, 3446 | 9781 / 9782, 9526, 9525 | 4772 | 7962 | Bolongo, 2682 |
| East of Catherby | 2858, 3432 | 11317 | 4771 | 7965 | Ellena, 2670 |
| West of Tree Gnome maze | 2490, 3181 | 9777 / 10033 | 4771 | 7963 | Gileth, 2683 |
| North of Brimhaven | 2765, 3213 | 11058 / 11057 | 4771 | 7964 | Garth, 2669 |
| Kastori | 1349, 3058 | 5423 / 5167, 5424 | 4772 | 56955 | Ehecatl, 14516 |

Todos os destinos são no plano 0. Em Catherby, a área `x < 2840 && y >= 3440` é excluída: ali o mesmo varbit transmite allotments. Em Gnome Stronghold, a árvore comum usa 4771 e a frutífera usa 4772; ambas são amostradas e atendidas antes de liberar a navegação. Os objetos de fruit tree têm footprint 2×2, confirmado no cache; a aproximação reutiliza o cálculo de borda do patch.

Os quatro patches antigos usam Tool Leprechaun ID 0; Kastori usa 12765. O NPC é escolhido pelo ID e proximidade ao patch, evitando outro leprechaun na mesma região. Todos os cinco jardineiros têm a ação `Pay` no cache.

## Ciclo e estimativas

Rake → plantar sapling → pagar proteção → aguardar → revisitar → check-health → coletar frutos → converter em notas → pagar 200 coins para remover → replantar. Árvores doentes usam Prune; mortas e stumps usam Clear com spade. Plantios existentes são preservados enquanto crescem, mesmo que sejam de uma espécie diferente da escolhida para replantar.

Cada frutífera usa seis ciclos de 160 minutos. A previsão inicial fica entre 13h20 e 16h porque a fase do tick de crescimento é desconhecida. `Due - revisit to confirm` continua sendo uma estimativa; apenas leitura regional recente confirma maturidade. Não é agendada uma segunda colheita por regeneração: este modo coleta os frutos atuais e replanta.

O decoder FRUIT_TREE distingue crescimento, check-health, zero a seis frutos, doença, morte e stump. Os valores de fallback que representam weeds não são interpretados como patch limpo. Histórico de fruit trees usa `F1`; árvores comuns mantêm `T1`. Um histórico do tipo errado é rejeitado ao restaurar um patch.

Coleta exige diminuição dos frutos no varbit e entrada do produto na mochila. Conversão em nota exige perda da quantidade original e ganho da mesma quantidade em notas. Coletas parciais com mochila cheia permitem anotar e retomar. O preparo reserva duas vagas em runs com fruit trees para não travar na criação da primeira pilha de notas.

## Preparo da run

O banco calcula um sapling por patch habilitado, separadamente para cada tipo de árvore, e soma pagamentos compartilhados. Exemplo: seis willows mais cinco bananas exigem 26 Apples(5), não apenas seis ou vinte. Ferramentas são compartilhadas. Moedas seguem 5.000 por patch: 25.000 para as cinco frutíferas; 55.000 com os seis patches comuns também habilitados. Runas seguem opcionais.

Para o nível 30, com Apple nas cinco frutíferas: **5 Apple saplings, 45 Sweetcorn, rake, spade e 25.000 coins**. Se os seis willows também estiverem habilitados: acrescentar 6 Willow saplings e 6 Apples(5), elevando a reserva total a 55.000 coins. O preparo continua usando todos os patches selecionados, não somente os que já estão maduros.

## Evidência e limites da validação

Build Java 11 / Microbot 2.6.22: **93 testes passaram**, sem falhas ou erros.

- [Cache de itens, notas, NPCs, ações, varbits e dimensões](evidence/fruit-cache-2026-09-13.json), consultado pelo Agent Server.
- [Preparo bloqueado por ausência de apple saplings](evidence/fruit-supplies-missing-2026-09-13.json): uma cópia temporária com somente as cinco fruit trees habilitadas abriu o banco e informou `Missing 5 Apple sapling in bag + bank`. A consulta ao estoque também não encontrou sweetcorn. Nenhum material foi retirado; a cópia foi descarregada e o banco fechado.
- A comparação automática de todos os 256 valores com o decoder FRUIT_TREE do JAR local do Time Tracking passou.
- Testes exercitam previsão, persistência por tipo, fronteira de Catherby, região compartilhada de Gnome Stronghold, pagamentos somados, níveis, saplings em nota, espaço para coleta e ciclo completo com recibos de inventário.
- Plantio, proteção, colheita e remoção de fruit trees ainda precisam de validação em jogo com materiais e árvore madura. O ciclo completo nos testes usa interações simuladas.

Fontes locais: Hub `farmtreerun/FarmTreeRunScript.java` e `enums/FruitTreeEnum.java`; cliente `timetracking/farming/FarmingWorld.java`, `PatchImplementation.java`, `Produce.java`; gameval `ItemID`, `ObjectID`, `NpcID`. Não há dependência de execução do plugin Time Tracking nem do Agent Server para usar OC Farming.
