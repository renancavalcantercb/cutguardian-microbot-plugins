# Farming Runner

Plugin `Farming Runner`, versão **0.9.4**, para Microbot **2.6.22 local deste workspace**. Executa árvores comuns (oak a magic) e frutíferas (apple a dragonfruit), com seleção independente. O suporte experimental a allotments foi removido.

## Usar

Na versão 0.9.3, o nome exibido passou de `[OC] Farming` para **Farming Runner**, pois a execução é autônoma. O identificador interno, as configurações, o histórico dos patches e o nome técnico do JAR foram preservados para atualizar a instalação existente.

Reiniciar o cliente depois de atualizar o JAR. Ativar **Automatically tend trees**, selecionar **Tree to plant** e ligar **Automatically revisit due patches** para permitir viagens. As opções **Tree: ...** selecionam Lumbridge, Varrock, Falador Park, Taverley, Gnome Stronghold e Nemus Retreat/Auburnvale. O walker usa as rotas e transportes disponíveis na configuração do cliente.

Para frutíferas, ativar **Automatically tend fruit trees** e escolher **Fruit tree to plant**. Os cinco locais são Gnome Stronghold, Catherby, Tree Gnome Village, Brimhaven e Kastori. **Protect fruit trees** controla o pagamento de proteção. Pode usar somente frutíferas ou combiná-las com as árvores comuns; configurações de árvores existentes são mantidas.

Frutíferas fazem check-health, coletam os frutos, convertem em notas no leprechaun, pagam a remoção e replantam. No nível 30, selecionar **Apple**: se os cinco patches precisarem de replantio, exigem **5 apple saplings (5496), 45 sweetcorn (5986 ou nota 5987), rake, spade e 25.000 coins**. Se os seis willows também precisarem de replantio: **6 willow saplings, 6 Apples(5)** adicionais e **55.000 coins no total**. O plugin busca no banco apenas o que falta. IDs de todas as espécies estão no [documento de fruit trees](docs/FRUIT_TREES_0_9_0.md).

O ciclo é **rake → plantar um sapling → pagar proteção → aguardar → revisitar → check-health → pagar 200 coins para remover → replantar**. Usa secateurs para podar árvores doentes e spade para limpar árvores mortas e stumps. Descarta weeds e empty plant pots.

Para seis patches de oak que precisem de replantio, levar:

- 6 oak saplings **sem nota**, ID 5370;
- 6 cestas **Tomatoes(5)**, ID 5968 ou nota 5969, se **Protect trees** estiver ligado;
- rake e spade; secateurs se houver árvore doente;
- 30.000 coins de reserva para remoção e transportes pagos: 5.000 por patch com trabalho pendente (200 × 25). A remoção continua custando 200 por árvore; o restante é margem para viagens, incluindo barcos.

Moedas não substituem o pagamento de proteção: **oak exige uma cesta cheia de cinco tomates por patch**. Tomates soltos ou cestas incompletas não servem. Sem o pagamento, a proteção ligada bloqueia o plantio e o overlay informa o item faltante. Desligar **Protect trees** permite plantar sem pagar proteção; o plugin não desliga essa opção automaticamente.

Antes de iniciar uma run, consulta o estado dos locais habilitados e busca no banco os materiais que faltarem para o trabalho pendente. Árvores crescendo e já protegidas ficam fora do preparo. Uma willow para replantar exige uma muda e seu pagamento, mesmo com fruit trees habilitadas crescendo. Uma árvore crescendo sem proteção confirmada exige somente o pagamento da espécie já plantada, sem muda nem nível de Farming para replantio. Árvores doentes exigem secateurs (comuns ou mágicas), sem muda de reposição. Spade e rake entram quando há replantio; a reserva de moedas considera os locais com trabalho.

Locais ainda sem observação e estimativas vencidas reservam materiais de replantio até a visita confirmar o estado. Sem **Automatically revisit due patches**, só considera trabalho na região atual. Desconta os itens já carregados e deposita saplings excedentes das espécies necessárias. Saplings saem sem nota; pagamentos retirados do banco saem em nota. Se faltar estoque necessário, informa o item e aguarda 60 segundos antes de conferir novamente. Uma mudança no trabalho pendente recalcula o preparo sem esperar esse prazo.

**Optional rune reserve** começa em 100 de cada uma: air, fire, earth, water e law. Durante uma ida necessária ao banco, retira até essa quantidade, conforme o estoque; runas ausentes ou falhas de retirada opcional não bloqueiam a run. Zero desliga a reposição. Se todos os obrigatórios já estiverem corretos na mochila, não vai ao banco só por runas. A reserva de moedas é obrigatória no preparo: com seis patches e 1.200 coins na mochila, retira mais 28.800. Se mochila e banco não somarem 30.000, informa quanto falta e aguarda; dinheiro excedente é mantido.

Não repõe materiais consumidos após cada plantio. Se outro local passar a precisar de trabalho durante a run, confere um novo orçamento para o que ainda falta fazer. Ao terminar os trabalhos pendentes, a próxima run terá uma nova conferência. Para caminhar até um banco, habilitar Automatically revisit due patches; sem essa opção, o preparo precisa de um banco próximo/aberto. Itens alheios ao preparo são depositados apenas quando necessário para abrir espaço; mantém uma vaga para árvores comuns e duas em runs com frutíferas, permitindo criar a primeira pilha de notas. Não altera equipamento.

Não fabrica saplings nem enche baskets: esses itens precisam existir na mochila ou no banco. Allotments não são observados, visitados ou cultivados. As antigas configurações e observações de allotments ficam ignoradas, preservando o histórico de árvores da versão 0.6.0.

## Estimativas e confirmações

O monitor compacto mostra **Farming Runner**, um único status da automação e uma linha por patch habilitado. Durante o crescimento, exibe a espécie e o tempo aproximado até o limite superior da estimativa (`Apple - ~15h20m`). Quando vence, mostra `Revisit`. Necessidade de proteção, doença e demais estados aparecem nessa mesma linha. Gnome mantém os nomes `Gnome (tree)` e `Gnome (fruit)` para distinguir os dois locais. Varbits, idade da leitura e linhas separadas de proteção foram removidos do overlay.

O estado é lido somente nas regiões correspondentes, com estabilização após carregamentos e diálogos. Os varbits 4771 e 4772 representam patches diferentes conforme o local. Observações ficam salvas por personagem e tipo de patch. O crescimento usa a lógica dos decoders TREE e FRUIT_TREE do Time Tracking, sem depender de o plugin Time Tracking estar ligado. Frutíferas usam seis ciclos de 160 minutos, com previsão inicial entre 13h20 e 16h; a visita confirma o estado real.

**Due — revisit to confirm** indica uma previsão vencida, não uma árvore pronta. O plugin exige uma leitura recente antes de check-health ou remoção. Plantio confirma com consumo de um sapling e mudança do patch; remoção exige mudança para vazio/weeds e consumo de 200 coins. Proteção exige confirmação do jardineiro correto ou consumo acompanhado da mensagem de pagamento.

## Build e testes

```powershell
.\build.cmd -Plugin ocfarming -BuildOnly -Test -Offline `
    -ClientJar '..\Microbot\runelite-client\build\libs\microbot-2.6.22.jar' `
    -ClientVersion '2.6.22'
```

Artefato: `dist/OcFarmingPlugin-0.9.3.jar`. Sem `-BuildOnly`, o script instala `OcFarmingPlugin.jar` no diretório de plugins do usuário. Os 95 testes cobrem árvores, frutíferas, rotas, persistência, preparo de run mista, recibos de coleta e notas, pagamentos compartilhados, banco e espaço no inventário. O decoder de frutíferas é comparado com o Time Tracking local em todos os 256 valores possíveis. Weeds e vasos vazios são descartados na posição atual, sem caminhar até outro patch da mesma região.

O usuário confirmou funcionamento das árvores comuns, inclusive willow no nível 30. IDs de fruit trees foram conferidos no cache pelo Agent Server, e o preparo detectou ao vivo a falta de apple saplings. **O ciclo de frutíferas foi testado com interações simuladas; plantio, proteção e coleta ainda precisam ser validados em jogo com os materiais necessários.**

## Documentação

- [Idas e voltas em Gnome Stronghold — 0.9.1](docs/GNOME_INVENTORY_WALK_0_9_1.md).
- [Frutíferas: saplings, patches, ciclo e validação — 0.9.0](docs/FRUIT_TREES_0_9_0.md).
- [Reserva para barcos e remoção — 0.8.3](docs/TRAVEL_COINS_0_8_3.md).
- [Preparo com mochila vazia — 0.8.2](docs/FIX_EMPTY_INVENTORY_0_8_2.md).
- [Correção da retirada de runas e fechamento do banco — 0.8.1](docs/FIX_BANK_MODE_0_8_1.md).
- [Preparo de inventário e banco — 0.8.0](docs/SUPPLIES_0_8_0.md).
- [Remoção de allotments e diagnóstico de plantio — 0.7.0](docs/TREE_ONLY_0_7_0.md).
- [Árvores, IDs e detalhes técnicos — 0.6.0](docs/TREES_0_6_0.md): registro histórico da implementação inicial.
- [Dados do cache do cliente](docs/evidence/tree-cache-2026-09-11.json).
- [Levantamento de tree e fruit tree](docs/FARMING_AUTONOMOUS_SURVEY.md).

Os demais documentos da pasta `docs` registram experimentos e versões anteriores, incluindo os allotments usados para validar as interações na conta de nível baixo. Não descrevem o escopo atual.
