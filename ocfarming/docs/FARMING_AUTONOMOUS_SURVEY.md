# Levantamento de farming autônomo: tree e fruit tree

Levantamento em 11/09/2026, por leitura estática do código local. Hub: `45d3b12`, FarmTreeRun `1.2.1`, Herbrun `1.2.0`; cliente Microbot: `e0080f8908`. Conferência complementar nas fontes públicas do RuneLite e Hub. Nenhuma execução em jogo foi feita; presença de código não comprova funcionamento de todas as interações.

Atualização: as gravações manuais posteriores de allotments confirmaram operações comuns e corrigiram a análise sobre o ID do leprechaun. Veja [evidências dos logs](ALLOTMENT_LOG_REVIEW.md). Tree/fruit tree e execução dos runners continuam sem validação presencial neste levantamento.

A implementação é viável reaproveitando a navegação, os utilitários de interação e o conhecimento do Time Tracking. O trabalho principal é integrar observação, previsão, ações confirmadas e retomada persistente. Os runners existentes são referências úteis, mas não implementam esse ciclo contínuo completo.

## Navegação e peças reutilizáveis

- `Rs2Walker.walkTo(WorldPoint)` já é usado pelos dois runners. O tree runner o encapsula em `walkToLocation`; o herb runner chama diretamente. O novo script precisa selecionar o destino, conferir chegada e tratar falha de acesso/rota.
- `Rs2Bank`, `Rs2Inventory`, `Rs2Npc`, `Rs2Dialogue` e os caches de NPCs/objetos fornecem as interações básicas.
- `TreeEnums` e `FruitTreeEnum` contêm mudas, níveis e itens/quantidades de proteção. O banking do tree runner também separa retiradas normais e em nota.
- `Rs2Leprechaun` no Hub fornece retirada de compostagem. `HerbrunScript.noteProduceViaLeprechaun` é referência para converter produtos em notas, com ressalvas abaixo.
- `MKE_WintertodtPlugin` serve como referência de estrutura de plugin, configuração, overlay e eventos. O controle de patches deve ter modelo próprio.

Fontes locais: [tree runner](../../../Microbot-Hub/src/main/java/net/runelite/client/plugins/microbot/farmtreerun/FarmTreeRunScript.java), [herb runner](../../../Microbot-Hub/src/main/java/net/runelite/client/plugins/microbot/herbrun/HerbrunScript.java), [leprechaun](../../../Microbot-Hub/src/main/java/net/runelite/client/plugins/microbot/Rs2Leprechaun.java).

## Como o Time Tracking funciona

`TimeTrackingPlugin.onGameTick` chama `FarmingTracker.updateData`. O tracker escolhe as regiões aplicáveis à posição do jogador, lê os varbits dos patches e persiste o valor observado com horário por perfil RuneScape. A chave do patch combina região canônica e varbit; o valor contém `valor:timestamp`.

Os varbits `FARMING_TRANSMIT_*` são reutilizados entre regiões. Por exemplo, `4771` representa árvores diferentes em Falador e Varrock. Não é possível ler todos os patches do mundo consultando esse varbit de qualquer lugar. Há também limites especiais de área/plano, como em Catherby, que devem ser preservados.

`PatchImplementation.forVarbitValue` decodifica espécie, estado e estágio. `Produce` fornece estágios e duração dos ciclos. `FarmingTracker.predictPatch` usa a observação anterior e o alinhamento dos ciclos para calcular progresso e `doneEstimate`. Ele aprende o alinhamento observando mudanças reais de crescimento. O próprio RuneLite documenta possibilidade de dessincronização dos timers: [Time Tracking](https://github.com/runelite/runelite/wiki/Time-Tracking).

Consequências para automação:

- Usar a previsão para agendar visitas; usar observação local recente para escolher a ação.
- Um patch sem observação deve ser `UNKNOWN`, exigindo uma primeira visita.
- Doença ou morte ocorrida longe do jogador pode não aparecer até nova observação. A previsão não garante saúde.
- Interfaces modais impedem atualização normal desses varbits; o tracker já trata essa condição. Mudanças de região e fechamento de modal também têm cuidados específicos.
- Não interpretar apenas `CropState.GROWING` como árvore imatura: no modelo do Time Tracking, uma árvore pronta para `Check-health` também pode aparecer assim, no último estágio.
- Frutíferas têm crescimento inicial e regeneração dos frutos. Preservar a árvore para produzir e removê-la para replantar são políticas diferentes.
- `PaymentTracker` observa diálogo/menu/chat para registrar proteção. `CompostTracker` observa ações e mensagens. Esses dados são memória de observações, não consultas globais ao servidor. Ausência de registro deve permitir estado desconhecido no modelo do script.

Fontes locais: [FarmingTracker](../../../Microbot/runelite-client/src/main/java/net/runelite/client/plugins/timetracking/farming/FarmingTracker.java), [FarmingWorld](../../../Microbot/runelite-client/src/main/java/net/runelite/client/plugins/timetracking/farming/FarmingWorld.java), [PatchImplementation](../../../Microbot/runelite-client/src/main/java/net/runelite/client/plugins/timetracking/farming/PatchImplementation.java), [PaymentTracker](../../../Microbot/runelite-client/src/main/java/net/runelite/client/plugins/timetracking/farming/PaymentTracker.java), [CompostTracker](../../../Microbot/runelite-client/src/main/java/net/runelite/client/plugins/timetracking/farming/CompostTracker.java). O mecanismo também foi conferido no [tracker upstream](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/timetracking/farming/FarmingTracker.java).

## Catálogo inicial

Os 14 patches abaixo estão no catálogo local do Time Tracking e no enum `FarmTreeRunScript.Patch`. Coordenadas e IDs de objeto são os usados pelo runner, ainda sem validação presencial. A região é a chave canônica de tracking; pode diferir da região da coordenada de aproximação, pois o estado é transmitido em regiões vizinhas.

| Tipo | Local | Região de tracking | Varbit | Objeto base no runner | Coordenada no runner, plano 0 |
|---|---|---:|---:|---:|---|
| Tree | Lumbridge | 12594 | 4771 / A | 8391 | 3195, 3228 |
| Tree | Varrock | 12854 | 4771 / A | 8390 | 3226, 3458 |
| Tree | Falador | 11828 | 4771 / A | 8389 | 3001, 3374 |
| Tree | Taverley | 11573 | 4771 / A | 8388 | 2936, 3440 |
| Tree | Gnome Stronghold | 9781 | 4771 / A | 19147 | 2437, 3417 |
| Tree | Farming Guild | 4922 | 7905 / G | 33732 | 1234, 3734 |
| Tree | Auburnvale | 5427 | 4771 / A | 56953 | 1365, 3320 |
| Fruit tree | Gnome Stronghold | 9781 | 4772 / B | 7962 | 2473, 3446 |
| Fruit tree | Tree Gnome Village | 9777 | 4771 / A | 7963 | 2490, 3181 |
| Fruit tree | Brimhaven | 11058 | 4771 / A | 7964 | 2765, 3213 |
| Fruit tree | Catherby | 11317 | 4771 / A | 7965 | 2858, 3432 |
| Fruit tree | Lletya | 9265 | 4771 / A | 26579 | 2345, 3163 |
| Fruit tree | Farming Guild | 4922 | 7909 / K | 34007 | 1244, 3757 |
| Fruit tree | Kastori | 5423 | 4772 / B | 56955 | 1349, 3058 |

O catálogo upstream também inclui esses locais: [FarmingWorld do RuneLite](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/timetracking/farming/FarmingWorld.java).

O runner valida 65 Farming para a tree da Guild e 85 para a fruit tree, além do nível da muda. Requisitos de acesso por patch devem entrar no catálogo de automação e ser conferidos separadamente. Crystal, calquat, hardwood, spirit, celastrus e redwood precisam de categorias próprias. O enum de navegação `FruitTrees` não é um catálogo taxonômico confiável: inclui Tai Bwo Wannai/Prifddinas e não inclui Lletya, embora Lletya exista no modelo de farming.

## Validação dos scripts existentes

| Capacidade | FarmTreeRun | Herbrun |
|---|---|---|
| Navegação | Usa Rs2Walker | Usa Rs2Walker |
| Preparação no banco | Mudas, ferramentas, pagamentos e compostagem | Setup automático ou Inventory Setup |
| Limpar, plantar, compostar | Implementado | Implementado |
| Check-health | Implementado | Não se aplica a herbs |
| Coletar | Handler de frutos, com prioridade problemática | Herbs; também flowers/allotments opcionais |
| Converter em notas | Implementado; ID 0 do leprechaun é válido, confirmação por destino pendente | Implementado, confirmação incompleta |
| Pagar para remover/proteger | Implementado | Sem política equivalente para trees |
| Curar doença | Sem handler de Prune/Cure | Detecta Diseased, mas não cura |
| Previsão de maturidade | Não integrada ao fluxo ativo | Filtra patches por previsão no início |
| Repetição por crescimento | Encerra ao terminar a rota | Encerra ao terminar a lista |

Achados estáticos no **FarmTreeRunScript**:

1. **Coleta pode ser preterida pela remoção:** `possibleActions` coloca `Chop` antes de `Pick` (linha 678). O decoder documenta frutíferas maduras com ambas as ações. Nesse caso, o fluxo escolhe pagar para limpar antes de colher. É necessário decidir explicitamente se a política exige coleta antes da remoção.
2. **Notas: correção da análise inicial:** o ID 0 é válido (`NpcID.FARMING_TOOLS_LEPRECHAUN`). Os logs manuais de allotments mostram esse NPC e uma troca bem-sucedida de 22 batatas por notas. Portanto, o uso de `leprechaunId = 0` não comprova erro no runner. Ainda é necessário validar seleção local do NPC e resultado da interação em cada destino; a gravação manual não executou o helper do tree runner.
3. **Falta poda de árvores doentes:** a lista de ações e o switch não tratam `Prune`/`Cure`. Uma árvore doente com `Inspect` pode seguir pelo caminho de plantio/proteção sem ser curada.
4. **Null-check tardio:** `treePatch.getId()` é acessado antes de `treePatch == null` (linhas 697 e 708). Se o objeto não for encontrado, pode ocorrer exceção e repetição sem progresso.
5. **Proteção sem confirmação suficiente:** um caminho de `handlePayment` retorna sucesso após clicar diálogo e esperar seu fechamento, sem exigir confirmação positiva do pagamento (linhas 806–814).
6. **Classificação incorreta em expansão hardwood:** `getSaplingToUse` trata apenas os três patches de Fossil Island como hardwood. Avium Savannah pode cair na escolha da muda de fruit tree (linha 1062). É fora do escopo inicial, mas reforça a necessidade de catálogo por tipo.

Achados estáticos no **HerbrunScript**:

1. `getHerbPatchState` retorna `Diseased`, mas `handleHerbPatch` não trata esse estado e termina retornando `true` (linhas 277–380).
2. Objeto ausente é tratado como patch concluído (`obj == null`, linha 281), podendo ocultar falha de chegada/carregamento.
3. `initialized` é definido antes do sucesso do equipamento/estoque. Se o setup falha, a próxima iteração pode pular a preparação (linhas 74–97).
4. `noteProduceViaLeprechaun` ignora o resultado da espera de alteração do inventário e define `notedAny = true` após a tentativa (linhas 643–649).
5. `applyCompost` interpreta ausência de XP como “já compostado” e retorna sucesso (linhas 605–618), embora outras falhas também possam não gerar XP.
6. As previsões são obtidas na população inicial da lista; não há um gerenciador persistente de próximas visitas entre ciclos.

Esses pontos comprovam lacunas de código. Frequência e efeitos exatos em jogo ainda exigem validação de runtime.

## Cuidado com Rs2Farming e o modelo do Quest Helper

`Rs2Farming` usa `microbot.questhelper.helpers.mischelpers.farmruns`, não diretamente os objetos de `timetracking.farming`. Existem duas famílias de `FarmingWorld`, `FarmingPatch`, `PatchImplementation` e `CropState`.

- O catálogo do Quest Helper contém coordenadas e áreas úteis, mas não inclui Auburnvale/Kastori na versão examinada.
- `FarmingHandler.predictPatch` lê a configuração `timetracking`, mas não preserva `DISEASED`/`DEAD`. Esses estados têm tickrate zero e podem resultar em `UNCHECKED`, pois `doneEstimate` permanece zero. Assim, os filtros de `Rs2Farming.getPatchesNeedingAttention` não são confiáveis para esse caso.
- `getReadyPatches` aceita qualquer estado diferente de `GROWING`, inclusive `null`. “Pronto” pode significar apenas “desconhecido”.
- `isFarmingSystemReady` verifica a existência do catálogo; não confirma que o Time Tracking está ligado nem que existem observações recentes.
- `Rs2Farming.getFruitTreePatches` consulta `Tab.TREE`, mas o decoder do Quest Helper registra frutíferas em `Tab.FRUIT_TREE` e o catálogo agrupa por essa aba. Portanto, esse método retorna lista vazia no modelo examinado. É uma incompatibilidade adicional a corrigir antes de reutilizar o utilitário.
- No Time Tracking, `FarmingWorld`, `FarmingPatch`, `PatchState` e `PatchPrediction` têm visibilidade de pacote; `forVarbitValue` também. Injetar `FarmingTracker` em um plugin externo não basta para consumir seu modelo completo.

Fontes: [Rs2Farming](../../../Microbot/runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/farming/Rs2Farming.java), [FarmingHandler](../../../Microbot/runelite-client/src/main/java/net/runelite/client/plugins/microbot/questhelper/helpers/mischelpers/farmruns/FarmingHandler.java), [catálogo do Quest Helper](../../../Microbot/runelite-client/src/main/java/net/runelite/client/plugins/microbot/questhelper/helpers/mischelpers/farmruns/FarmingWorld.java).

## Desenho recomendado

Atualização de decisão: o serviço público descrito abaixo foi uma proposta inicial, substituída pela [implementação independente no OC Farming](IMPLEMENTACAO.md). O monitor 0.1.0 mantém decoder, observações e previsão no próprio plugin; não requer alterações no cliente ou endpoint novo.

Criar um serviço de consulta com retorno público e imutável no cliente, encapsulando o modelo do Time Tracking. O Hub consumiria esse serviço sem reflexão nem duplicação de classes no pacote do RuneLite. Uma implementação restrita ao Hub é possível, mas precisaria manter catálogo/decoder/previsão próprios e compatibilidade com a configuração persistida.

Separar três responsabilidades:

1. **Catálogo e observação:** identidade estável por perfil + região canônica + varbit, tipo, destino de navegação, objeto/área, jardineiro, leprechaun e requisitos. Snapshot com espécie, estado bruto, estágio, última observação, previsão de maturidade, proteção e compostagem conhecidas/desconhecidas.
2. **Planejador:** selecionar patches habilitados e acessíveis; visitar desconhecidos; priorizar doença conhecida; agendar maduros; preparar estoque para as ações previstas. Persistir resultado e próxima visita. Reavaliar após login, troca de perfil e reinício; para árvores sem proteção, uma política de inspeção intermediária pode reduzir o atraso na detecção de doença.
3. **Executor:** máquina de estados usando walker e utilitários existentes, com ações curtas, confirmação de efeitos e limites de tentativa. Após falhas de rota, falta de material ou objeto desconhecido, registrar motivo e reagendar/bloquear aquele patch, sem loop infinito.

| Estado local confirmado | Ação |
|---|---|
| Desconhecido/objeto ausente | Aguardar leitura válida; aproximar/reconsultar; depois registrar falha se necessário |
| Ervas daninhas | Rake e confirmar limpeza |
| Vazio | Compostar se configurado, plantar muda e confirmar consumo + novo estado |
| Crescendo saudável | Garantir proteção conforme política e agendar retorno |
| Doente, tree/fruit tree | Prune com ferramenta adequada; confirmar volta a crescimento saudável |
| Morto | Clear e confirmar patch vazio; replantar |
| Maduro sem health check | Check-health antes da remoção |
| Frutífera com frutos | Coletar conforme política; notar produtos quando necessário |
| Árvore pronta para substituição | Pagar remoção; confirmar vazio; replantar |
| Stump | Limpar toco conforme ação disponível |

As políticas devem distinguir: fazer run de XP/replantio; colher frutos e conservar árvore; colher frutos antes do replantio. Pagamento para remoção e pagamento para proteção são operações distintas. Logs/raízes exigem uma alternativa de corte/limpeza manual, se forem desejados.

Para tree/fruit tree doentes, o decoder identifica a ação `Prune`; a execução com secateurs e a confirmação devem ser validadas em jogo. Herbs usam outra estratégia de cura. Não generalizar um único consumível/ação para todas as culturas.

Leituras e eventos no client thread; execução e esperas fora dele. Reconsultar entidades após mudanças; confirmar ação por estado, inventário ou mensagem correspondente. Não persistir conclusão apenas porque o clique foi enviado.

## Validação necessária antes de considerar autônomo

Primeiro validar um patch de tree e um de fruit tree: vazio, com weeds, crescendo, doente, morto, health check pendente, frutos, stump, inventário cheio, falta de moeda/muda/pagamento e retomada após reinício. Em seguida expandir aos 14 destinos, verificando área de leitura, acesso, NPC correto e variantes de diálogo. Testes do decoder devem preservar doença/morte, distinguir desconhecido de vazio e impedir colisão de varbits entre regiões/perfis.

O primeiro incremento recomendado é catálogo + monitor de estado/previsão, seguido do executor completo em dois patches. Isso permite validar as leituras antes de ampliar a rota e o agendamento contínuo.
