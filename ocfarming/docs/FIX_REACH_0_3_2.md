# Alcance dos allotments — 0.3.2

Em 11/09/2026, a versão 0.3.1 colheu e fez notas de Ardougne sul, mas o plantio às 11:52:44 falhou com `I can't reach that!`. O executor aguardou 25 segundos e adiou o patch; as tentativas seguintes repetiram o problema. Weeds cresceram durante a espera e Rake também falhou.

O Agent Server encontrou o jogador em `(2664,3372,0)` e vários objetos de ID 8555 para o mesmo allotment. O screenshot confirmou a mensagem no chat, o patch vazio e sementes disponíveis. A consulta por ID e objeto mais próximo não distinguia tiles internos e de borda. A propriedade genérica `reachable=true` da API também não garantia uma posição adjacente válida para interagir.

`HerbrunScript.hasStandableNeighbor` já aborda essa particularidade. O OC Farming agora seleciona o par objeto de borda/tile para ficar em pé: o tile deve ser adjacente pelos lados, estar fora do patch e constar no mapa local de alcance do `Rs2Tile`. Entre os pares válidos, escolhe o menor custo de caminhada. Usa `Rs2Walker.walkUntil` até o tile exato e reavalia patch/inventário antes de selecionar sementes ou clicar. A seleção é refeita ao executar, evitando usar um objeto interno por empate de distância.

A mensagem de alcance durante uma ação pendente provoca reposicionamento e nova avaliação imediatamente. Não conta como plantio concluído. São permitidas duas recuperações; nova falha adia o patch por um minuto, evitando um ciclo ilimitado. As confirmações por consumo de materiais e leitura do patch continuam obrigatórias.

## Validação

Os 54 testes passaram: os novos casos cobrem exclusão de interior/diagonal, escolha de ponto alcançável, aproximação antes das sementes, recuperação e limite de tentativas.

Para validar no cliente sem reiniciá-lo, o Agent Server pausou o plugin original e carregou temporariamente uma cópia da 0.3.2 com package/nome de plugin distintos. A lógica e o grupo de configuração permaneceram iguais. A consulta inicial confirmou o original inativo e a cópia temporária ativa. Ao remover a cópia, o original já aparecia ativo; o estado enabled foi restaurado para true. Em futuras validações, usar também um `PluginDescriptor.configName` distinto, pois o RuneLite deriva a configuração de ativação do nome simples da classe.

No patch que estava falhando, a versão corrigida confirmou Rake às 11:57:55, plantio às 11:58:02 e proteção às 11:58:06 BRT. O varbit 4772 passou para 13, foram consumidas três sementes (73 → 70) e um sack anotado (11 → 10), e o overlay confirmou a proteção. [Trecho do log real](evidence/reach-fix-2026-09-11.log), filtrado para excluir os logs dos testes unitários.

A seleção corrigida foi validada no cenário real travado. A recuperação após mensagem de alcance foi validada nos testes simulados; a mensagem não reapareceu durante a execução corrigida. A instalação definitiva exige reiniciar o cliente para carregar o JAR 0.3.2. A cópia temporária foi removida ao terminar e o plugin original foi reativado.
