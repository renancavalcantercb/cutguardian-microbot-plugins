# Cabbage — 0.4.0

Selecionar **Crop to plant → Cabbage**. O fluxo existente atende os quatro allotments de Falador e Ardougne, incluindo limpeza, compostagem opcional, plantio, cura, colheita, notas, proteção e revisitas. Culturas saudáveis já crescendo são preservadas; a nova seleção se aplica ao próximo plantio.

## Materiais e dados

| Campo | Valor |
|---|---|
| Nível | 7 Farming |
| Sementes por patch | 3 cabbage seeds, ID 5324 |
| Colheita / nota | Cabbage 1965 / 1966 |
| Proteção por patch | 1 sack completo de 10 onions, 5458 / nota 5459 |
| Crescimento normal | Varbit 20–23 |
| Crescimento regado | 84–87 |
| Coletável | 24–26 |
| Doente | 149–151 |
| Morto | 213–215 |
| Previsão | 4 ciclos de 10 minutos; faixa estimada e confirmação local ao revisitar |

Estados e ciclos conferidos no código local `timetracking/farming/PatchImplementation.java` (ALLOTMENT) e `Produce.java` (CABBAGE). Nível conferido em `Microbot-Hub/.../herbrun/AllotmentSeedType.java`; IDs base em `runelite-api/.../ItemID.java`. Pagamento conferido na [tabela de sementes da OSRS Wiki](https://oldschool.runescape.wiki/w/Seeds). A proteção exige sack cheio; nove onions em sack ou sack de potatoes não servem. Não há enchimento automático de sacks nesta versão.

## Compatibilidade e testes

Observações de cabbage salvas por versões anteriores como `Unsupported state` recuperam a estimativa a partir do horário original, sem reiniciar o timer ao fazer login. A proteção persiste com o identificador CABBAGE e é descartada ao colher ou mudar de cultura, como nas culturas anteriores.

Os 61 testes passaram com JDK 11 e o cliente local 2.6.22. Cobrem ranges/estados regados, previsão e migração, nível/sementes/pagamento, confirmação do plantio e proteção, notas com inventário cheio, cura/limpeza e preservação de onions existentes. O executor reutiliza a aproximação à borda e a recuperação de alcance da 0.3.2.

O Agent Server 8081 estava indisponível durante esta implementação. Plantio, colheita e pagamento de cabbage ainda precisam de validação em jogo. Reiniciar o cliente para carregar o JAR 0.4.0 instalado e selecionar a cultura antes do próximo plantio.

Atualização 0.4.1: plantio e proteção de cabbage foram confirmados em Falador SE pelo Agent Server. [Validação e correção da aproximação](FIX_BOUNCE_0_4_1.md). A colheita de cabbage maduro continua pendente de teste real.
