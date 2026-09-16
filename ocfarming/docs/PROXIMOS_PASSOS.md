# Próximos passos — OC Farming

**Atualização 0.3.0:** o [executor de allotments](EXECUTOR_0_3.md) está implementado para batata/onion: limpeza, colheita, notas, cura, compostagem opcional, replantio e proteção. A próxima etapa é validar o ciclo em jogo após reiniciar e ativar Automatically tend allotments. O plano abaixo preserva o histórico das etapas anteriores; banco e tree/fruit tree continuam pendentes.

Plano baseado no [levantamento técnico](FARMING_AUTONOMOUS_SURVEY.md), atualizado para a [implementação independente 0.2.0](IMPLEMENTACAO.md). Batatas, onions e a revisita opcional de Falador a Ardougne foram conferidas em jogo. O executor de colheita/plantio e os cenários de falha/cancelamento continuam pendentes.

Atualização após os [logs de allotments](ALLOTMENT_LOG_REVIEW.md): a conta está em progressão inicial (nível 4 no último evento registrado). Plantio, proteção norte, coleta, notas e retomada após inventário cheio já possuem evidência manual. Usar allotments como primeira etapa de observação; tree/fruit tree seguem como objetivo da automação, quando disponíveis.

A [consulta ao Agent Server](AGENT_SERVER_TRACKING.md) confirmou anteriormente os dois allotments de Ardougne crescendo, no valor 7. O monitor agora lê os varbits dentro do cliente e calcula sua própria faixa de maturidade. Time Tracking e Agent Server são ferramentas opcionais de comparação, não dependências do plugin.

## O que o usuário pode preparar agora

1. Usar os allotments já visitados em Falador/Ardougne para os primeiros testes. A conta iniciante já foi informada; definir acessos de tree/fruit tree quando forem relevantes.
2. Escolher a política inicial: run de XP com replantio, coleta mantendo frutíferas ou coleta seguida de replantio. Sugestão inicial: tree para XP; fruit tree com coleta antes do replantio.
3. Escolher uma muda de tree e uma de fruit tree compatíveis com o nível, e decidir se deseja pagar proteção. Sugestão para o primeiro ciclo: usar mudas já prontas no banco e proteção habilitada; validar a cura separadamente quando houver um patch doente.
4. Separar ferramentas de limpeza/plantio/poda, mudas, moedas, pagamentos de proteção e compostagem caso habilitada. Conferir os materiais de deslocamento exigidos pela rota do walker e reservar espaço para coleta.
5. Instalar e ativar o monitor conforme o README, visitar allotments com batatas e conferir o overlay. Agent Server pode ser ativado para inspeção remota; Time Tracking pode ser usado para comparação. Depois, usar tree e fruit tree de Gnome Stronghold como candidatos à validação das árvores, quando acessíveis.

Não é necessário limpar ou remover árvores antecipadamente. Patches em estados diferentes ajudam a validar o reconhecimento. Não provocar doença nem destruir uma plantação apenas para criar um cenário de teste.

O monitor e o planner estão compilados e seus 21 testes passaram. O Agent Server confirmou os estados locais, preservação do histórico fora da região e atualização na revisita. Em 11/09/2026, a viagem automática usou teleporte, navio e caminhada; confirmou morte/weeds em Ardougne e preservou onions em Falador. A evidência está em [revisit-validation-2026-09-11.json](evidence/revisit-validation-2026-09-11.json). Mudas e política de replantio/proteção serão necessárias para a etapa de execução de árvores.

## Sequência de implementação

| Etapa | Entrega | Critério para avançar |
|---|---|---|
| 1. Modelo e monitor | Allotments de Falador/Ardougne; depois uma tree e uma fruit tree | Estado e observação corretos; desconhecido não vira vazio; varbit não se mistura entre regiões/perfis |
| 2. Tracking independente | Decoder e previsão dentro do OC Farming; primeira versão implementada para batatas | Validar leituras reais, maturidade estimada e persistência por personagem antes de expandir culturas |
| 3. Executor local | Limpar, plantar, podar, check-health, colher, notar, pagar e confirmar resultado | Ciclo completo nos dois patches, com inventário cheio e materiais ausentes tratados sem marcar sucesso indevido |
| 4. Preparação e rota | Banco e Rs2Walker integrados ao planejamento | Chegada e acesso conferidos; recuperação de falha de rota; NPC e diálogo corretos para cada patch |
| 5. Persistência e agendamento | Próxima visita, retomada e registro de bloqueios por patch | Reinício/login recuperam o plano e revalidam o estado; patches crescendo não geram visitas repetidas sem motivo |
| 6. Expansão | Demais patches de tree/fruit tree | Destinos validados individualmente, incluindo Guild, Lletya, Auburnvale e Kastori conforme acesso |
| 7. Build e instalação | Build específico do OC Farming e instruções de uso | JAR compila contra o cliente escolhido e carrega com configuração/overlay; reinício do plugin preserva observações |

O serviço no cliente e o novo endpoint foram descartados. OC Farming 0.2.0 integra o build, calcula previsões próprias e oferece revisitas de confirmação. Ainda não executa o ciclo completo de farming da tabela.

## Validação em jogo

Executar primeiro com observação e conferência manual. Só depois habilitar ações automáticas nos dois patches escolhidos.

| Cenário | Resultado esperado |
|---|---|
| Primeira visita sem histórico | Estado inicialmente desconhecido; observação válida antes de agir |
| Patch com weeds/vazio | Limpeza quando necessária; muda consumida e novo estado confirmado |
| Árvore crescendo | Preservar plantação; proteção conforme política; próxima visita registrada |
| Árvore doente | Prune e confirmação do novo estado; falta de ferramenta explicada |
| Árvore morta/toco | Limpeza e confirmação antes de plantar |
| Check-health pendente | XP/estado confirmado antes de remover |
| Frutífera com frutos | Coleta respeita a política e precede remoção quando configurada |
| Inventário cheio | Notas confirmadas pelo inventário; retomar coleta sem repetição infinita |
| Falta de muda/moeda/pagamento | Reabastecer ou registrar bloqueio; não concluir o patch falsamente |
| Falha de pagamento | Permanecer pendente até confirmação positiva ou limite de tentativas |
| Objeto ausente/modal aberto | Aguardar/reconsultar; não tratar como patch vazio ou concluído |
| Reinício e troca de perfil | Recuperar dados do perfil correto e confirmar situação atual |
| Timer vencido, planta ainda crescendo | Atualizar observação e reagendar; não remover a planta |

Para cada teste, registrar local, espécie, estado inicial, ação, mudança observada e resultado. Marcar como pendente o cenário ainda não encontrado. A aprovação de autonomia exige evidência dos casos relevantes, além de compilação e testes do modelo.

## Primeiro incremento recomendado

Onion e revisita automática já foram observadas nos allotments de Falador/Ardougne. Conferir desligamento durante a rota, falha de deslocamento e retomada por perfil. Avançar com limpeza, colheita, notas e replantio, ampliando o modelo com uma tree e uma fruit tree quando acessíveis.
