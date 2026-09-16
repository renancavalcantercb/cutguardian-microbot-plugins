# Vai e volta na aproximação — 0.4.1

Em 11/09/2026 às 12:24 BRT, o log registrou cliques repetidos para `(3051,3306,0)` após fazer notas. O personagem alternava principalmente entre `(3052,3306,0)` e `(3052,3307,0)`. A posição final era exigida pelo executor mesmo quando outra borda permitia interagir. [Log original](evidence/bounce-walker-2026-09-11.log).

Uma sonda somente de leitura pelo Agent Server confirmou que o destino tinha flags de colisão zero e era caminhável. Portanto, não era necessário excluir aquele tile como obstáculo. [Leitura da colisão](evidence/bounce-collision-2026-09-11.json).

## Correção

- A condição de término do walker aceita qualquer posição adjacente válida ao patch, usando a mesma seleção de borda do executor.
- Nos últimos seis tiles, usa `Rs2Walker.walkFastCanvas` e aguarda até seis segundos, sem reenviar cliques continuamente. O web walker continua responsável por trajetos maiores e pelo fallback quando a caminhada pela tela não pode ser iniciada.
- Perto do destino (até três tiles), seis segundos sem reduzir a melhor distância encerram a tentativa. Oscilar ou continuar animado não reinicia esse prazo. Detours distantes continuam sujeitos ao limite total existente de 45 segundos.
- Após a caminhada, o executor verifica alcance e reavalia patch/inventário antes de interagir. Falhas continuam adiando o patch por um minuto.

## Validação

Os 65 testes passaram com JDK 11 e cliente local 2.6.22. Os novos casos verificam chegada por outra borda, oscilação sem progresso, progresso real e escolha de caminhada pela tela no último passo.

Na primeira validação, apenas aceitar outra borda/limitar a espera interrompeu o ciclo, mas o walker ainda errava o destino curto pelo minimapa. Após incluir a caminhada pela tela, a cópia temporária corrigida executou em Falador SE:

| Horário BRT | Confirmação |
|---|---|
| 12:30:02 | Colheita de onion concluída |
| 12:30:05 | Conversão em notas concluída |
| 12:30:11 | Cabbage plantado |
| 12:30:15 | Proteção de cabbage confirmada |

O varbit 4772 passou de 17 para 3 e depois 20. O overlay confirmou os dois patches de Falador com cabbage crescendo/proteção registrada. [Log da execução corrigida](evidence/bounce-fixed-2026-09-11.log). A colheita de cabbage maduro ainda depende de aguardar crescimento; este teste colheu a onion anterior e replantou cabbage.

A validação utilizou package, nome e `configName` próprios. O original permaneceu desligado, conforme consulta do Agent Server. Sonda e cópia temporária foram removidas; o plugin original permanece desligado até reiniciar o cliente e ativar o JAR 0.4.1 instalado.
