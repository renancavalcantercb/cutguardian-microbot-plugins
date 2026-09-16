# Árvores — OC Farming 0.6.0

Implementado em 11/09/2026. A seleção de árvores é independente dos allotments: `crop=TOMATO` pode permanecer enquanto `tree=OAK`. `autoTrees` começa desligado; `autoVisit` permite a primeira visita e as revisitas. Cada um dos seis locais tem um interruptor próprio, inicialmente selecionado. Sem `autoVisit`, cuida somente dos tree patches locais habilitados.

## Saplings e pagamentos

| Árvore | Nível | Sapling sem nota | Proteção por patch | ID / ID em nota | Ciclos de 40 min no Time Tracking |
| --- | ---: | ---: | --- | --- | ---: |
| Oak | 15 | 5370 | 1 Tomatoes(5) | 5968 / 5969 | 4 |
| Willow | 30 | 5371 | 1 Apples(5) | 5386 / 5387 | 6 |
| Maple | 45 | 5372 | 1 Oranges(5) | 5396 / 5397 | 8 |
| Yew | 60 | 5373 | 10 cactus spines | 6016 / 6017 | 10 |
| Magic | 75 | 5374 | 25 coconuts | 5974 / 5975 | 12 |

Os IDs sugeridos foram confirmados no `gameval/ItemID.java`, no enum `TreeEnums` do Hub e no cache do cliente em execução. `5375` é Spirit sapling, para um patch diferente e fora deste executor. Os saplings anotados de oak até magic são 12941–12945: não podem ser plantados diretamente. Não se aceita acorn no lugar de oak sapling. Baskets incompletos e tomates soltos também não substituem Tomatoes(5).

A tabela fornecida dizia cinco ciclos/3h20 para oak. O `Produce.OAK` local tem cinco estágios contando a maturidade, e `PatchImplementation.TREE` transmite 8–11 crescendo e 12 pronto para check-health: quatro ciclos de 40 minutos. A estimativa segue essa implementação (janela inicial 2h–2h40, sem conhecer o offset). A página [Tree patch da OSRS Wiki](https://oldschool.runescape.wiki/w/Tree_patch) também apresenta 2h40. O vencimento continua sendo **Due — revisit to confirm**, nunca uma autorização para remover uma árvore ainda crescendo.

## Locais

Todos usam `FARMING_TRANSMIT_A = 4771`. O contexto regional identifica qual árvore está sendo transmitida. As regiões alternativas são as usadas em `FarmingWorld.java`; não são seis varbits globais independentes.

| Local | Objeto base | Jardineiro / NPC | Região canônica e alternativas | Destino do walker |
| --- | ---: | --- | --- | --- |
| Lumbridge, oeste do castelo | 8391 | Fayeth / 2681 | 12594; 12850 | 3195,3228,0 |
| Varrock, courtyard | 8390 | Treznor / 11957 | 12854; 12853 | 3226,3458,0 |
| Falador Park | 8389 | Heskel / 2679 | 11828; 12084 | 3001,3374,0 |
| Taverley | 8388 | Alain / 2678 | 11573; 11829 | 2936,3440,0 |
| Gnome Stronghold | 19147 | Prissy Scilla / 2687 | 9781; 9782,9526,9525 | 2437,3417,0 |
| Nemus Retreat / Auburnvale | 56953 | Aub / 14514 | 5427; 5428,5684 | 1365,3320,0 |

Coordenadas e objetos vêm de `FarmTreeRunScript.Patch`; nomes de jardineiros, ações `Pay`, footprints 3×3 e varbits foram confirmados pelo cache. O jardineiro de Varrock é 11957 no cliente atual, não o antigo 2680. Falador Park tem chave separada dos allotments de Falador. Nemus pode ser desligado caso a rota/acesso de Varlamore não esteja disponível. O walker decide os transportes conforme sua configuração; o plugin não libera acessos.

## Ciclo e confirmações

1. A primeira visita observa os patches habilitados ainda desconhecidos; não inventa um estado vazio. Visitas e ações compartilham um worker.
2. Rake até leitura vazia. Plantio requer spade, nível, um sapling sem nota e pagamento suficiente quando proteção está ligada.
3. Plantio só confirma com consumo de um sapling e leitura recente da mesma árvore crescendo. Descarta o empty plant pot resultante.
4. Usa `Pay` no jardineiro específico do patch. Confirmação segue o mecanismo existente: diálogo de proteção associado ao NPC ou consumo exato com mensagem de pagamento. Preserva uma árvore crescendo mesmo se a seleção de próxima muda mudar.
5. Estima a maturidade usando os estágios TREE, separadamente dos allotments. Histórico usa prefixo `T1` e perfil do personagem; uma observação de allotment não pode ser restaurada como árvore.
6. Uma leitura de árvore pronta causa `Check-health`; a mudança para árvore verificada confirma a etapa. Só então pode iniciar remoção.
7. Usa `Pay` e confirma a remoção com leitura vazia/weeds e consumo de exatamente 200 coins. Não corta árvores para obter logs; este é um ciclo de replantio para XP.
8. Árvores doentes usam `Prune`, exigindo secateurs normais ou magic secateurs no inventário. Árvores mortas e stumps usam `Clear` com spade. Estados sem decodificação ficam bloqueados, sem tentativa de plantio ou remoção.

A aproximação considera toda a área 3×3 do objeto, permitindo chegar por qualquer borda acessível, e mantém o handoff do web walker para caminhada pela tela nos últimos seis tiles. Se a leitura regional chegar antes de o objeto entrar na cena, primeiro caminha até poder localizar uma borda e então faz a aproximação final. Cancelamento e leituras recentes mantêm as proteções das correções anteriores.

## Validação

- Build Java 11 / Microbot 2.6.22 passou com **96 testes** (73 existentes e 23 novos).
- Cobertura nova: cinco árvores e níveis, estados normais/doentes/mortos/health/stump, gaps desconhecidos, pagamentos completos e em nota, consumo de um sapling, moedas de remoção, poda, persistência e isolamento, primeira visita, locais desligados, regiões alternativas e borda de árvore 3×3.
- Teste compara todos os estados reconhecidos de árvore com o decoder TREE do JAR real do cliente, incluindo o estágio de crescimento.
- Teste do executor simula check-health → remoção → plantio → proteção, com recibos separados; também cobre cancelamento, falta de materiais, leitura atrasada e confirmação de NPC incorreto.
- [Consulta somente de leitura pelo Agent Server](evidence/tree-cache-2026-09-11.json), em 11/09/2026 às 19:09 UTC: IDs, nomes, notas, varbit, footprint, jardineiros e nível 15 confirmados. Probe temporário removido após a consulta.
- **Ainda não validado em jogo:** primeiro plantio/proteção de oak, crescimento até check-health, remoção paga e percurso dos seis locais com esta versão. Os testes simulados não comprovam cliques nem o diálogo real de remoção.

## Primeiro teste em jogo

Reiniciar o cliente para carregar 0.6.0. Para isolar a primeira tentativa, pode habilitar apenas Lumbridge e desligar os allotments; depois habilitar os demais locais. Não é obrigatório: a execução conjunta é suportada.

Para seis patches: 6 oak saplings sem nota, 6 Tomatoes(5) em nota ou sem nota, rake e spade. Secateurs são necessárias se houver árvore doente. Levar 1.200 moedas para remover seis árvores já verificadas e os recursos dos transportes. Patches vazios não gastam essas moedas. Repor materiais manualmente para as próximas voltas.

O escopo não inclui banco automático de suprimentos, fabricação de saplings, enchimento de baskets, compostagem de tree patches, fruit trees ou spirit trees.
