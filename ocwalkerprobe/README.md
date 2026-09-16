# Walker Probe

Plugin `[OC] Walker Probe`, versão **1.0.0**, para Microbot **2.6.22**. Mede a
qualidade do movimento do personagem — não do walker.

Essa distinção é o ponto: o plugin lê **só a posição**, tick a tick. Não
consulta o `Rs2Walker`, não sabe o destino, não sabe quem clicou. Por isso os
números saem comparáveis entre clientes diferentes: o mesmo trajeto medido no
Microbot e em outro cliente produz duas linhas que podem ser postas lado a
lado.

## O que ele mede

| métrica | o que denuncia |
| --- | --- |
| **efficiency** — tiles andados ÷ distância em linha reta | rota com desvio desnecessário |
| **wrong-way** — passos que terminaram mais longe do destino | o bounce, contado |
| **idle / longest stall** — ticks parado no meio do trajeto | walker que clica e espera em vez de encadear |
| **running %** | pace, para não confundir trajeto lento com rota ruim |

O destino nunca é lido: quando o trajeto acaba, **a última tile é o destino**,
então eficiência e passos errados são calculados de trás para frente sobre as
posições gravadas.

## Usar

Ativar o plugin e andar. Um trajeto começa no primeiro passo e termina depois
de **8 ticks parado** (configurável). Trajetos com menos de **15 tiles** são
descartados, para não registrar o vai-e-vem dentro de um banco.

Cada trajeto sai no log:

```
Walker Probe [microbot]: 143 tiles in 201t | eff 1.12 | idle 9t (longest 4t) | wrong-way 2/156 | run 100%
```

O campo **Run label** entra em toda linha. Trocar o rótulo por cliente é o que
permite separar as amostras depois.

## O experimento

1. Rotular `microbot`, percorrer uma rota longa (Lumbridge → Falador serve)
2. Repetir a mesma rota algumas vezes
3. Repetir do outro lado com o rótulo trocado
4. Comparar a linha de sessão

```
                     trips   avg eff   wrong-way   idle
microbot               6       ?           ?         ?
outro cliente          6       ?           ?         ?
```

**Eficiência de trajeto curto é ruidosa** — um desvio de 3 tiles em 20 pesa
demais. Compare trajetos longos e junte várias amostras; a linha de sessão
existe para isso.

E `efficiency` não é nota absoluta: obstáculo justifica desvio. O que vale é a
diferença entre dois walkers **na mesma rota**.

## Por que isso existe

O `Farming Runner` carrega ~190 linhas (`FarmingApproachProgress`,
`FarmingPatchTarget`, `FarmingGameActions.approach`) que existem só para
limitar o vai-e-vem nos últimos tiles — e que geraram o `FIX_BOUNCE_0_4_1` e o
`FIX_REACH_0_3_2`.

Se `wrong-way` vier zerado nas rotas que interessam, esse subsistema pode ser
simplificado em vez de mantido. Se vier alto, ele está justificado e agora tem
número em vez de suspeita.

## Build

```powershell
.\build.cmd -Plugin ocwalkerprobe -BuildOnly -Test -Offline `
    -ClientJar '..\Microbot\runelite-client\build\libs\microbot-2.6.22.jar' `
    -ClientVersion '2.6.22'
```

Artefato em `dist/`. Sem `-BuildOnly`, instala em `.runelite\microbot-plugins`.

Os 7 testes cobrem a matemática da medição — linha reta, desvio lateral,
backtracking, stall e pace. Ela é pura e não precisa de cliente para rodar.
