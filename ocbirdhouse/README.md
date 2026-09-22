# Bird House Runner

Plugin `Bird House Runner`, versão **0.1.0**, para Microbot **2.6.22 local deste workspace**. Segue o mesmo padrão do `Farming Runner (Efficient Walker)`: monitor contínuo com histórico por personagem e visitas automáticas quando as casas vencem.

## Usar

Requisitos: quest **Bone Voyage** completa, um **digsite pendant** (qualquer carga) e o **bank chest construído na Fossil Island** (21 Construction). O acesso à ilha é direto com Rub no pendant → **Fossil Island** (pousa na House on the Hill, com mushtree em frente); Digsite + **barca** (Quick-travel no Barge guard) é o fallback, assim como caminhar com o Efficient Walker até a barca sem pendant na mochila. Como o bot mora na ilha e se reabastece no chest, o pendant só é gasto na primeira viagem (1 carga).

Ativar **Automatically run birdhouses** para permitir o ciclo completo: busca suprimentos no banco, viaja para a ilha e esvazia, constrói e replanta as 4 casas (2 no Verdant Valley, mushtree, 2 no Mushroom Meadow). Depois da primeira viagem o bot mora na ilha e se reabastece no chest; o próximo ciclo sai sozinho ~50 minutos depois.

O tier da birdhouse é **progressivo e automático**: escolhe o maior tier que o nível de Hunter/Crafting permite e que tenha 4+ logs disponíveis, caindo para o próximo da lista quando falta material (Bird → Oak → Willow → Teak → Maple → Mahogany → Yew → Magic → Redwood).

## Setup por run

- 1 digsite pendant, 1 hammer e 1 chisel (permanecem na mochila);
- 4 clockworks (só a primeira run retira o set cheio — esvaziar a casa devolve o clockwork);
- 4 logs do tier selecionado;
- 40 sementes aceitas (qualquer allotment/hop/flower de Farming ≤ 35, 10 por casa).

Ninhos de pássaro recebidos ao esvaziar são abertos com Search automaticamente.

## Estimativas e confirmações

A leitura dos varbits 1626–1629 segue a lógica do `BirdHouseTracker` do Time Tracking (0 vazio, múltiplo de 3 semeado/crescendo, resto construído sem seed), sem depender de o plugin Time Tracking estar ligado. Casas semeadas vencem **50 minutos** após o plantio observado. Os varbits só transmitem na Fossil Island: fora da ilha o monitor mostra o histórico e o runner viaja de volta quando vence. **Due indica previsão vencida** — cada casa confirma com mudança de varp após Empty/Build/Seed, com backoff de 5 minutos por falha.

## Build e testes

```powershell
.\build.cmd -Plugin ocbirdhouse -BuildOnly -Test -Offline `
    -ClientJar '..\Microbot\runelite-client\build\libs\microbot-2.6.22.jar' `
    -ClientVersion '2.6.22'
```

**Validação pendente em jogo:** rota da barca (Quick-travel e ponto de chegada), mushtree Verdant→Meadow, chest da ilha e o ciclo completo de 50 minutos na iron.
