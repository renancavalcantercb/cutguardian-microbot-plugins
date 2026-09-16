# Leitura de farming pelo Agent Server

Nota de arquitetura: a proposta de endpoint no final deste registro foi substituída pelo [tracker independente do OC Farming](IMPLEMENTACAO.md). Este documento preserva a investigação da API existente; nenhum endpoint novo é necessário para executar o monitor atual.

Verificação em 11/09/2026, por consultas GET ao cliente já aberto na porta 8081. Nenhuma ação de jogo ou alteração de configuração foi executada.

## Evidência ao vivo

O cliente respondeu `LOGGED_IN`, Farming nível 4 e 318 XP. A posição consultada foi `(2672, 3372, 0)`, região 10548, junto dos allotments de Ardougne. Às 05:07:09 UTC (02:07:09 em São Paulo), ambos os varbits 4771 e 4772 retornaram 7. Pelo decoder local de allotments, isso corresponde a batatas crescendo. É consistente com os registros anteriores de plantio no valor 6.

O [snapshot selecionado](evidence/agent-server-snapshot.json) contém posição, região, valores e horário, sem token ou identificadores da conta. As requisições foram sequenciais; não se trata de uma leitura atômica. A interpretação usa região + tipo + decoder, não apenas o valor numérico.

Uma consulta de objetos filtrada por `Potato` retornou lista vazia; uma consulta mais ampla excedeu o timeout de cinco segundos. Portanto, esta verificação confirmou valores de varbit e contexto regional, sem confirmação adicional por objeto.

## Consultas disponíveis

| GET | Utilidade |
|---|---|
| `/state` | Login e posição para contextualizar a região |
| `/skills?name=Farming` | Nível e XP de Farming |
| `/varbit?id=4771` | Canal A da região atual |
| `/varbit?id=4772` | Canal B da região atual |
| `/settings/plugin?group=timetracking&key=...` | Configuração comum do plugin, com a limitação de perfil descrita abaixo |

O servidor exige `X-Agent-Token`, usando o arquivo local `C:\Users\Renan\.runelite\.agent-token`. Ler esse arquivo em memória e enviar o cabeçalho apenas ao servidor local; não registrar o valor. Neste cliente, autenticação ausente ou incorreta responde 404, conforme `AgentHandler`, mesmo para rotas existentes.

## O que ainda falta para ler a previsão exata

`TimeTrackingPlugin.onGameTick` alimenta `FarmingTracker.updateData`, com cuidados para pós-login, troca de região e interfaces modais. A observação é persistida por perfil RuneScape. `FarmingTracker.predictPatch` calcula o progresso e `doneEstimate` usando essa observação e o alinhamento dos ciclos.

O `SettingsHandler` existente usa `ConfigManager.getConfiguration(group, key)`, que consulta configuração comum. Os dados dos patches são lidos pelo Time Tracking com a sobrecarga que recebe o perfil, em outra estrutura de configuração. A chamada com `group=timetracking&key=10548.4771` não retornou valor; isso não demonstra que o tracker esteja desligado ou sem dados.

Da mesma forma, `/scripts` filtra plugins Microbot e não é uma consulta confiável para verificar se o Time Tracking está ativo. A ausência de valor em uma chave comum de configuração também não comprova desativação.

Não foi identificado endpoint dedicado de farming/ETA nos handlers registrados no código local. Assim, os varbits atuais já podem ser consultados pelo Agent Server, mas a previsão exata exibida no painel ainda precisa de uma integração específica. Nenhuma ETA foi obtida nesta verificação.

## Próximo incremento

O monitor pode começar pelas leituras existentes. Para obter a mesma previsão do painel, expor uma consulta somente de leitura, por exemplo `GET /farming/patches` (proposta, ainda não implementada), apoiada em um adaptador público no cliente.

O retorno deve distinguir último estado observado de previsão e incluir região canônica, varbit, espécie, estágio, horário da observação, maturidade estimada e proteção/compostagem conhecidas. Informar se o Time Tracking está ativo e se há dados válidos do perfil atual. Para valor local atual, respeitar área de transmissão, carregamento de região e modais; não aplicar os canais atuais a patches distantes.

A implementação deve consultar os dados no client thread e devolver uma cópia imutável, sem sleeps nem ações de jogo. Confirmar compatibilidade com o JAR em execução antes de considerar a API disponível. Essa integração permanece pendente.

Não é necessário pedir ao usuário que transcreva os timers como requisito para iniciar o monitor. A leitura atual já foi comprovada; a integração de previsão é uma tarefa de implementação. Novas gravações manuais continuam úteis para validar ações e comparar a previsão com a maturação real.
