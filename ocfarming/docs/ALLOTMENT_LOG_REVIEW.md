# Evidências dos logs de allotments

Análise das gravações manuais fornecidas pelo usuário em 11/09/2026. A conta está em progressão inicial de Farming; o último XP registrado chega ao nível 4. Allotments permitem validar as operações comuns enquanto trees ainda não estão disponíveis. Os registros não são execução do futuro OC Farming.

Fontes: `C:\Users\Renan\.roelite\osrs\actionlogger\farming-2026-09-11_01-50-23.jsonl` (51 ticks) e `farming_2-2026-09-11_01-53-25.jsonl` (89 ticks). Ticks reiniciam em cada arquivo; ambos têm compressão por diferenças. Ausência de campo não significa valor zero ou evento de sucesso.

Recortes estruturados: [farming.events.json](evidence/farming.events.json) e [farming_2.events.json](evidence/farming_2.events.json). Contêm somente eventos selecionados de farming, referências aos NPCs relevantes e hashes dos originais. Metadados da conta e eventos de outros jogadores foram omitidos. Os recortes não são snapshots completos nem substituem os arquivos originais.

## O que foi confirmado

| Operação | Evidência |
|---|---|
| Coleta de batatas | Primeiro arquivo: 14 batatas em dois patches. Segundo: 12 batatas em dois patches. Total observado: 26 |
| Inventário cheio | `farming_2`, tick 28: mensagem de falta de espaço; patch ainda não esgotado |
| Conversão em notas | `farming_2`, tick 45: item 1942 diminui 22; item 1943 aumenta 22; diálogo confirma banknotes |
| Retomada da coleta | Após notas, clique no tick 50; coleta final e patch vazio no tick 53 |
| Plantio norte | Sementes usadas no tick 55; item 5318 diminui 3 no tick 57; varbit 4771 passa a 6 e mensagem de plantio aparece no tick 60 |
| Proteção norte | `Pay (north)` nos ticks 60/61; item 6033 diminui 2 e mensagem/diálogo confirmam proteção no tick 64 |
| Plantio sul | Sementes usadas no tick 82; item 5318 diminui 3 no tick 83; varbit 4772 passa a 6 e mensagem de plantio aparece no tick 86 |

O pagamento do sul não aparece até o final do segundo arquivo. Não é possível afirmar que foi ou não realizado depois da gravação. Há apenas um pagamento confirmado nesses registros.

## Patches e varbits

Posições e NPCs situam o primeiro arquivo nos allotments de Falador e o segundo nos de Ardougne. A associação de nomes/regiões foi conferida no catálogo local `timetracking.farming.FarmingWorld`.

| Local | Objeto base nos cliques | Varbit | Sequência observada de valores |
|---|---:|---:|---|
| Falador noroeste, região 12083 | 8550 | 4771 / A | 11 (tick 8), 12 (14), 3 (20) |
| Falador sudeste, região 12083 | 8551 | 4772 / B | 11 (33), 12 (39), 3 (48) |
| Ardougne norte, região 10548 | 8554 | 4771 / A | 11 (10), 12 (28), 3 (53), 6 (60) |
| Ardougne sul, região 10548 | 8555 | 4772 / B | 11 (69), 12 (72), 3 (75), 6 (86) |

Pelo decoder local de allotments: 10–12 representam batatas coletáveis; 3 corresponde ao patch limpo sem cultura; 6 é o início do crescimento da batata. O valor inicial 10 não é emitido como delta nesses recortes e não deve ser inventado como evento observado. A ação `Harvest` e os produtos recebidos comprovam a coleta inicial.

As mudanças 11/12 durante a coleta não são maturação de uma nova plantação. O estado permanece coletável até o esgotamento. Esses valores não devem ser reutilizados para interpretar trees: o decoder depende do tipo do patch.

Os objetos aparecem em múltiplos tiles do mesmo canteiro. Não contar cada entrada de objeto como um patch separado. Identificar o canteiro por região, varbit, tipo e área; reconsultar sua entidade antes de agir.

## Correção do levantamento: leprechaun ID 0 é válido

A conclusão anterior de que o ID 0 era incorreto foi um erro de análise. Os dois registros contêm `Tool Leprechaun` com ID de definição 0. A API local confirma `NpcID.FARMING_TOOLS_LEPRECHAUN = 0`.

| NPC no segundo arquivo | ID da definição | Índice temporário na cena |
|---|---:|---:|
| Tool Leprechaun | 0 | 16904 |
| Kragen | 2665 | 16899 |

O `id` de um clique em NPC é o índice de cena. O `id` de `nS` é o ID de definição e `idx` é o índice. Assim, `16904` não deve virar uma constante de leprechaun no plugin. Os métodos que recebem um ID de definição devem receber o ID apropriado, inclusive zero quando aplicável.

O teste confirma a troca manual com o leprechaun padrão, sem provar o funcionamento completo do helper do tree runner em todos os destinos. Permanecem necessários seleção local do NPC e confirmação da troca pelo inventário.

Nos ticks 33 e 78 do segundo arquivo, batatas foram usadas em Kragen. Ele respondeu sobre compostagem e não trocou os produtos por notas. É um caso útil de alvo incorreto: abrir um diálogo não comprova a conclusão da tarefa.

## Pagamento e compostagem são operações diferentes

O item consumido no pagamento foi **6033**, que a API identifica como `ItemID.Cert.BUCKET_COMPOST`: compostagem em nota. O item normal é 6032. Portanto, este registro confirma que o pagamento de proteção observado aceitou duas unidades em nota.

A mensagem do tick 64 confirma o propósito: pagar ao jardineiro para proteger o patch. Não houve registro de aplicar compostagem diretamente ao solo. O executor deve manter estados independentes para proteção paga e solo tratado.

`Pay (north)` é `NPC_THIRD_OPTION` neste registro. Kragen também expõe `Pay (south)`; não selecionar genericamente a primeira opção `Pay` ao tratar um NPC com mais de um patch. O clique foi repetido antes do resultado; o plugin deve aguardar confirmação e reconsultar a pendência antes de repetir.

## Confirmação de ações para o executor

- **Plantar:** consumo de três sementes, seguido de estado de crescimento/mensagem. Nos dois plantios o consumo antecede a confirmação do patch em três ticks. Não exigir XP como única evidência: não há evento de XP de plantio nos trechos observados.
- **Proteger:** associar o NPC e a opção ao patch alvo; aguardar consumo do pagamento e confirmação positiva. Diálogo fechado sozinho é insuficiente.
- **Notar:** conferir a redução do item normal e aumento correspondente da nota. O caso observado trocou a quantidade inteira de 22 unidades em uma ação.
- **Coletar:** distinguir inventário cheio de patch esgotado. Após liberar espaço, retomar o patch que ainda estiver coletável.
- **Finalizar patch:** esperar estado/mensagem de vazio ou novo plantio, conforme objetivo; não concluir apenas porque a animação parou.

Os intervalos observados descrevem estes logs. Devem orientar esperas por condições com timeout, sem virar sleeps fixos ou garantias de duração.

## Próximas gravações úteis

Priorizar o retorno aos mesmos allotments plantados: registrar estado na chegada, horário/previsão do Time Tracking e maturação/coleta observada. Os dois arquivos atuais não cobrem o tempo entre plantio e maturidade, pois já começam com culturas prontas.

Também faltam aplicação de compostagem ao solo, weeds/rake, doença/cura, morto/clear, confirmação da proteção sul, falta de pagamento e reinício/retomada do cliente. Coletar esses casos conforme surgirem naturalmente. Trees/fruit trees continuam pendentes e exigirão seus próprios testes de health check, poda, frutos e remoção paga.

O primeiro monitor pode usar os allotments de Falador/Ardougne para validar região + varbit, estado e confirmação das ações comuns. Essa etapa aproveita o nível atual da conta e preserva o objetivo de expandir para tree/fruit tree.
