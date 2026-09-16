# Saída prematura após notas — 0.3.1

## Relato e observação pelo Agent Server

Em 11/09/2026, o usuário relatou que o executor colheu Falador NW, fez notas, deixou de replantar e ignorou Falador SE para viajar a Ardougne. A inspeção das 11:07 encontrou sementes e pagamento suficientes no inventário. O log registrou a saída de Falador às 11:05:59.

Uma sonda somente de leitura, instalada temporariamente pelo Agent Server 8081, acompanhou região real, região da observação, permissão de navegação, ação pendente e destino. O registro contém somente esses estados e horários: [evidência](evidence/local-work-bug-2026-09-11.json). Os timestamps estão em UTC; horário local = UTC−3.

A sonda começou depois da saída relatada. Na rodada seguinte, a versão 0.3.0 limpou, plantou e protegeu os allotments de Ardougne; voltou a Falador, replantou/protegeu NW e colheu SE. Às 14:11:39.286 UTC confirmou NOTE com `navigationReady=true`, mas `liveRegion=-1`. Logo depois iniciou uma nova visita para `FALADOR_SOUTH_EAST`, ainda estando em Falador. Dessa vez o destino escolhido era local, e conseguiu replantar/proteger SE. Isso captura a janela que permite planejar uma rota indevidamente, sem alegar ter registrado a primeira saída para Ardougne.

## Causa e correção

Fechar um diálogo invalida a amostragem regional durante dois ticks completos. O executor retornava `false` quando `liveRegion < 0`; o runner interpretava esse retorno como ausência de trabalho local. Como a navegação já podia estar liberada, o planner escolhia entre o histórico dos patches. Um patch antigo de Ardougne podia ganhar prioridade sobre Falador recém-colhido.

A versão 0.3.1 verifica a posição real e aguarda uma observação recente, da região correta, para ambos os patches locais. Só libera a viagem depois de avaliar o trabalho de ambos. A ausência ou idade da leitura não conclui uma visita. Fora da área de transmissão, a ausência de leitura local continua permitindo navegar. Bloqueios explícitos por materiais/falha mantêm o comportamento de adiamento existente.

O runner também adia a decisão quando o tracker publica outra visão enquanto o executor obtém o snapshot do jogo, para reavaliar o trabalho local antes de usar o histórico. Início, confirmação e bloqueio de ações agora geram logs com o patch e motivo.

## Validação

Antes da correção, quatro testes novos falharam: janela após notas, segundo patch ausente, leitura local antiga e visão de outra região. Depois da correção, os 49 testes passaram com JDK 11 e o cliente local 2.6.22.

O teste da sequência verifica que nenhuma viagem é liberada entre a confirmação das notas e a atualização dos patches; depois exige replantio/proteção no norte e colheita no sul. Também verifica que a área ao sul do limite de transmissão de Falador não fica presa esperando varbits.

O diagnóstico em jogo foi feito na versão 0.3.0. A validação de uma colheita completa na versão 0.3.1 requer reiniciar o cliente para carregar o JAR instalado e aguardar a próxima maturação. Cura, compostagem, troca de perfil e falhas de transporte continuam precisando de cenários reais específicos.
