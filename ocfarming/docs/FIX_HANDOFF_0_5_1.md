# Aproximação iniciada longe — 0.5.1

Em 11/09/2026, o executor plantou e protegeu tomato em Ardougne norte às 14:14:14–14:14:18 BRT. Colheu o sul às 14:14:33 e fez notas às 14:14:39, mas não replantou: a caminhada iniciou em `(2672,3380,0)` para a borda `(2671,3372,0)`, parou em `(2671,3373,0)` e registrou `Cannot reach patch` às 14:14:50. Depois iniciou a viagem para Falador. Havia sementes, seed dibber e pagamento suficientes. [Log original](evidence/ardougne-handoff-before-2026-09-11.log).

A seleção entre caminhada pela tela e web walker era feita somente no início de `walk`. Como a distância inicial era oito tiles, a chamada seguia no web walker até o fim, sem usar a aproximação pela tela implementada na 0.4.1. Isso deixava uma lacuna específica nas aproximações iniciadas de longe.

Na 0.5.1, o web walker devolve o controle quando a posição entra no raio de seis tiles do destino. O executor verifica cancelamento/alcance novamente e conclui os últimos passos usando `walkFastCanvas`. Continua aceitando qualquer borda utilizável e preserva os limites de tempo e confirmação das ações.

Os 73 testes passaram com JDK 11 e cliente local 2.6.22. A regressão usa as coordenadas reais de Ardougne e exige a transição para caminhada pela tela; outro caso garante que cancelar durante o trajeto não dispara o clique final.

## Validação pelo Agent Server

A cópia temporária usou package/nome/configName distintos, com o original parado. Na aproximação de Ardougne sul, o web walker saiu às 14:25:55 em `(2673,3374,0)` e a caminhada pela tela levou à borda `(2671,3372,0)`. Iniciou Rake às 14:25:59. Houve uma interrupção parcial da limpeza e o adiamento existente de um minuto; na retomada, confirmou limpeza às 14:27:47, plantio de tomato às 14:27:56 e proteção às 14:28:00 BRT. O varbit 4772 passou para 27. [Log da validação](evidence/ardougne-handoff-after-2026-09-11.log).

A sonda e a cópia temporária foram removidas ao terminar. O JAR 0.5.1 foi instalado; o plugin original ficou desligado para carregar a nova versão após reiniciar o cliente. A pausa parcial de Rake continua usando a recuperação por timeout existente; esta mudança corrige a transição de caminhada que impediu o plantio originalmente relatado.
