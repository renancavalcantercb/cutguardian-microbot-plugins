# Executor 0.3.0

Atualização 0.3.1: o executor aguarda leituras recentes dos dois patches locais antes de liberar navegação, inclusive após diálogos. [Correção, testes e evidência do Agent Server](FIX_LOCAL_VISIT_0_3_1.md).

## Reaproveitamento

Do Hub, `HerbrunScript` fornece referência para fixar o objeto do allotment, escolher Rake/Clear/Pick/Harvest, usar sementes/compostagem e fazer notas. `FarmTreeRunScript` fornece referência para diálogos de pagamento. O OC Farming chama os utilitários existentes `Rs2Inventory`, `Rs2Npc`, `Rs2Dialogue`, o cache `Rs2TileObjectCache` e `Rs2Walker`. Não instancia nem exige que aqueles plugins estejam ativos.

O executor próprio remove as suposições de sucesso baseadas somente no clique, presença de diálogo ou timeout. O ID base do objeto e a região identificam o patch; objeto ausente gera pendência. Elstan (2663) usa Pay (north-west)/(south-east); Kragen (2665) usa Pay (north)/(south), conforme os actionlogs. Tool leprechaun tipo 0 é válido.

## Componentes

| Arquivo | Função |
|---|---|
| FarmingCrop / FarmingCompost | Cultura escolhida, itens, pagamentos, nível e tratamento opcional |
| FarmingActionPolicy | Precondições e evidência necessária para confirmar uma etapa |
| FarmingGameActions | Leitura no client thread e interação via utilitários do Microbot |
| FarmingActionExecutor | Uma ação pendente, espera por resultado, progresso e bloqueio por patch |
| FarmingProtectionTracker | Proteção observada, associação NPC/patch, persistência e invalidação |
| FarmingVisitRunner | Executor único para ações locais e rotas, sem competição entre ambos |

`autoFarm` começa desligado. Quando ativo, cuida de patches locais com observação recente. Com `autoVisit`, inclui trabalho conhecido na seleção de destinos; prazo vencido continua exigindo leitura regional antes de agir. Quando não houver trabalho conhecido, permanece aguardando.

## Confirmações

| Ação | Confirmação |
|---|---|
| Rake | Observação nova de vazio; inventário cheio permite descartar weeds e retomar |
| Clear | Observação nova de vazio/weeds |
| Harvest | Observação nova de vazio/weeds; inventário cheio pausa para notas, sem concluir o patch |
| Note | Produto diminui e nota correspondente aumenta na mesma quantidade |
| Plant | Três sementes consumidas e observação nova da cultura correta crescendo |
| Cure | Poção consumida e mesma cultura volta a crescer |
| Compost | Bucket consumido com XP de farming, ou confirmação de solo já tratado |
| Pay | Confirmação do jardineiro correto, ou mensagem de pagamento junto ao consumo correspondente |

O executor aguarda enquanto há progresso. Vinte e cinco segundos sem mudança ou três minutos totais sem confirmação bloqueiam o patch por um minuto. A presença de um diálogo só permite avançar o diálogo de uma ação pendente; não constitui recibo. Falha de clique também bloqueia. A pausa global e o desligamento impedem novas ações. O runner aguarda animação/movimento, evitando iniciar uma viagem no meio da ação.

## Proteção e Time Tracking

`timetracking/farming/PaymentTracker.java` usa texto de confirmação em `InterfaceID.ChatLeft.TEXT`, verifica o NPC por `ChatLeft.HEAD` e associa a opção escolhida ao patch. Salva o resultado em configuração do perfil RuneScape. Portanto, “Onion protected” é memória de confirmação observada, não leitura de um varbit de proteção.

OC Farming aplica esse princípio aos quatro allotments e persiste `patch.<region>.<varbit>.protected` no próprio grupo. O menu Pay direto, manual ou automático, seleciona o patch; a confirmação exige região e jardineiro correspondentes. Não captura todas as alternativas de seleção pelo diálogo Talk-to implementadas pelo RuneLite. Proteção anterior à instalação, ou pagamento realizado com o plugin desligado, pode continuar como não registrado até consultar o jardineiro.

Assim como no Time Tracking, a validade depende de observar as mudanças do ciclo. O registro é removido em estado não crescente, mudança de cultura ou retrocesso de estágio. Mudanças externas não observadas durante ausência podem deixar histórico antigo; o overlay informa que se trata de proteção registrada, sem prometer estado remoto atual. Troca de personagem limpa a memória e carrega apenas dados do perfil correspondente.

## Limites e teste ao vivo

Não há reabastecimento de banco/armazenamento, compra automática de cura, rega, fabricação de mudas ou execução de tree/fruit tree nesta versão. Compostagem é opcional e usa buckets não anotados; pagamento é uma operação separada e aceita itens anotados. Culturas não suportadas não são limpas automaticamente.

Depois de reiniciar com o JAR 0.3.0, ativar Automatically tend allotments, escolher onion/potato e levar os materiais. Começar em um dos patches já conhecidos e conferir limpeza, plantio e proteção. Conferir um inventário cheio na colheita e a retomada após notas. Testar cura e compostagem quando houver cenário adequado. Ativar Automatically revisit due patches para integrar deslocamentos. Os testes unitários e com jogo simulado não substituem essa validação.

Referências e licença: [THIRD_PARTY_NOTICES.txt](../THIRD_PARTY_NOTICES.txt).
