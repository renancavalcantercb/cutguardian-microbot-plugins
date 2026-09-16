# Implementação do tracker e revisitas

A versão atual é **0.3.0**. Ela acrescenta o [executor de allotments e registro persistido de proteção](EXECUTOR_0_3.md) à base 0.2.0 descrita abaixo. As limitações históricas sobre ausência de ações referem-se à etapa 0.2.0.

Decisão: manter o monitor e sua lógica de previsão dentro do OC Farming. A proposta inicial de criar um serviço no cliente e endpoint novo foi descartada a favor desta implementação independente. O Time Tracking serviu de referência para o catálogo, interpretação dos valores e duração dos ciclos; não é uma dependência de execução.

## Componentes

| Arquivo | Responsabilidade |
|---|---|
| `FarmingPatchData.java` | Quatro allotments, região canônica, varbit, ID base e limites de leitura |
| `FarmingObservation.java` | Snapshot imutável, decoder de batatas/onions, cálculo de faixa e formato persistido |
| `FarmingTracker.java` | Histórico por patch e isolamento da sessão por perfil |
| `FarmingSamplingGate.java` | Estabilização antes de ler canais após região/modal/login |
| `OcFarmingPlugin.java` | Eventos, leitura no client thread, persistência e publicação do snapshot visual |
| `OcFarmingOverlay.java` | Estado, idade da observação e faixa estimada |
| `OcFarmingConfig.java` | Visibilidade do overlay e habilitação de revisitas |
| `FarmingVisitPlanner.java` | Previsão vencida mais antiga, confirmação e espera por região após falha |
| `FarmingVisitRunner.java` | Executor de navegação com Rs2Walker, cancelamento e confirmação pelo tracker |

O monitor recebe `GameTick` e lê o estado no client thread. A navegação opcional roda em um executor próprio de uma thread; seu callback de conclusão lê apenas snapshots imutáveis. Não há executor de colheita/plantio nesta etapa.

## Leitura e persistência

Os canais 4771/4772 só são associados aos patches da região elegível. Falador respeita o limite sul de transmissão do catálogo; leituras são restritas ao plano 0 e não são feitas em instâncias, mundos sazonais ou enquanto houver interface modal. Após interrupção/região nova, são aguardados dois ticks completos antes da leitura.

O armazenamento usa `ConfigManager` no grupo `ocfarming`, perfil RuneScape explícito e chave `patch.<região>.<varbit>`. Cada observação contém versão do formato, valor bruto, horário observado e limites da previsão. Valores ausentes/corrompidos/futuros não viram patch vazio. Dados de outro perfil são removidos da memória na troca; o salvamento usa o perfil ao qual a observação pertence.

O tracker persiste mudanças de estado e salva periodicamente durante observação contínua, além de checkpoints ao interromper a leitura e desligar. O overlay recebe um snapshot imutável; não consulta objetos/varbits durante a renderização.

## Estimativa de batatas e onions

O catálogo local foi conferido em `timetracking/farming/PatchImplementation.java` e `Produce.java`:

| Cultura | Crescendo | Crescendo regada | Colheita | Doença | Morte |
|---|---|---|---|---|---|
| Batata | 6–9 | 70–73 | 10–12 | 135–137 | 199–201 |
| Onion | 13–16 | 77–80 | 17–19 | 142–144 | 206–208 |

Não aplicar o deslocamento de 64 genericamente a todos os valores: existem faixas reutilizadas para outras culturas. Valores não suportados permanecem desconhecidos. Cultura e estágio normalizados permitem preservar calibração durante a rega; trocar de cultura reinicia a faixa.

Para uma observação no horário `t`, ciclo `c = 600 segundos` e `r = 4 - estágio` ciclos restantes (estágio de 0 a 3):

```text
limite inferior = t + (r - 1) × c
limite superior = t + r × c
```

O intervalo expressa a fase desconhecida dentro do ciclo. Em leituras consecutivas, do mesmo estado ou do estágio seguinte, a interseção com a faixa anterior pode estreitar a previsão. Ao perder continuidade, replantar, curar ou encontrar intervalos incompatíveis, o cálculo recomeça a partir da nova observação.

Isso reaproveita o princípio de estágios/ciclos sem reproduzir todo o Time Tracking. Não é a mesma implementação do offset global aprendido pelo RuneLite; essa calibração adicional só deve ser introduzida se a validação demonstrar necessidade. A previsão pressupõe crescimento saudável em condições normais. Doença não observada e mudanças externas continuam exigindo nova visita.

O relógio nunca transforma `GROWING` em `HARVESTABLE` no snapshot. Prazo vencido aparece como pedido de confirmação; apenas uma leitura válida pode confirmar colheita, doença ou morte.

O formato persistido permanece em versão 1. Observações antigas de onion ou plantas regadas, antes desconhecidas e sem previsão, são migradas a partir de `observedAt`. Não são tratadas como plantio novo no horário do login.

## Revisita de confirmação

`autoVisit` começa desligado. Quando habilitado, o planner seleciona somente `GROWING` com `now >= latestReadyAt`, priorizando a previsão mais antiga. Vazio, desconhecido, doença, morte e colheita já confirmada não geram esta visita de verificação.

O runner chama `Rs2Walker.walkUntil` com destino Falador `(3055,3309,0)` ou Ardougne `(2671,3376,0)`, coordenadas observadas nos testes manuais. Usa os transportes configurados no walker, incluindo teletransportes disponíveis. Esse método existe no JAR local usado no build; compatibilidade com outras builds que também anunciam 2.6.22 não foi verificada.

A conclusão exige região correspondente e uma observação posterior àquela que disparou a visita. O retorno booleano do walker não confirma maturação. Uma leitura regional atualiza ambos os patches, evitando duas viagens. Caso a planta ainda cresça, a nova observação gera uma previsão futura. Se não houver confirmação, aguarda até dez segundos após a chamada; falhas geram espera de cinco minutos para a região inteira. A rota tem pedido de cancelamento após três minutos, processado nos checkpoints do walker.

O runner respeita a pausa global e invalida a rota em desligamento ou mudança de sessão/perfil. Não inicia novas rotas sem perfil/logado, em instância, mundo sazonal ou com modal. Loading de teleporte não é confundido com troca de perfil. Desligar o plugin interrompe seu executor sem redefinir configurações globais do walker.

## Escopo validado e pendências

Há 21 testes: 16 de observação/tracker e cinco de política de visitas. Cobrem regiões com varbit compartilhado, sequência dos logs, culturas desconhecidas, onion, rega, doença/morte, faixa inicial, refinamento observado, perda de continuidade, prazo vencido, migração/persistência inválida, perfis independentes, estabilização, replantio/cura, seleção, confirmação regional e espera após falha. O JAR compila contra o Microbot 2.6.22 local.

A validação manual do monitor 0.1.0 ocorreu pelo Agent Server em 11/09/2026: estados distintos em Ardougne; histórico separado de Falador/Ardougne no Grand Exchange com canais zerados; retorno a Falador com 4771/4772 em 10 e overlay de colheita pronto. O sul de Ardougne vazio foi confirmado pelo usuário. Onion em Falador foi observada com 4771/4772 em 13. A precisão temporal não foi medida no instante de maturação.

A revisita 0.2.0 foi validada em 11/09/2026, 10:33:43–10:35:25 BRT: Falador → teleporte de minigame → charter ship → caminhada → confirmação regional de Ardougne. O log da thread `OcFarming-visits` encerrou com `rs2walker:completion-condition-met`, antes de atingir a coordenada exata. O Agent Server confirmou norte morto (199) e sul com weeds (0); o overlay preservou onions de Falador (15). [Evidência reduzida](evidence/revisit-validation-2026-09-11.json).

Próximos testes: interrupção manual ao desligar durante viagem, espera após falha e troca de personagem. A viagem bem-sucedida não valida esses cenários nem mede o instante exato de maturação. O próximo incremento funcional é o executor local de limpeza/colheita/notas/replantio.
