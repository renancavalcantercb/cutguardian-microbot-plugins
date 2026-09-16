# Tomato — 0.5.0

Selecionar **Crop to plant → Tomato** depois de carregar o novo JAR. A opção permite plantar tomate nos quatro allotments conhecidos de Falador e Ardougne. Reutiliza limpeza, compostagem opcional, cura, colheita, notas, proteção e revisitas do executor, incluindo a correção de aproximação da 0.4.1.

| Campo | Valor |
|---|---|
| Nível | 12 Farming |
| Sementes por patch | 3 tomato seeds, ID 5322 |
| Colheita / nota | Tomato 1982 / 1983 |
| Proteção por patch | 2 sacks completos de 10 cabbages, 5478 / nota 5479 |
| Crescimento normal | 27–30 |
| Crescimento regado | 91–94 |
| Coletável | 31–33 |
| Doente | 156–158 |
| Morto | 220–222 |
| Previsão | 4 ciclos de 10 minutos; confirmação local ao revisitar |

Estados e ciclos conferidos no código local `timetracking/farming/PatchImplementation.java` e `Produce.java`. Nível e semente conferidos em `Microbot-Hub/.../herbrun/AllotmentSeedType.java`; IDs base em `runelite-api/.../ItemID.java`. O pagamento também consta na [tabela de sementes da OSRS Wiki](https://oldschool.runescape.wiki/w/Seeds), consultada durante a implementação de cabbage.

O pagamento exige dois sacks cheios por patch. Um sack, sacks incompletos ou sacks de onions não atendem ao pré-requisito. Os materiais de proteção precisam estar no inventário quando a opção estiver ligada; não há enchimento automático de sacks. Uma plantação saudável de cabbage é preservada e continua usando o pagamento correspondente a cabbage até terminar seu ciclo.

Observações antigas de tomato salvas como estado desconhecido recuperam a previsão a partir do horário original. A proteção persiste por perfil como TOMATO, permanece durante crescimento/rega e é invalidada ao colher ou mudar de cultura.

Os 71 testes passaram com JDK 11 e cliente local 2.6.22. Os casos novos cobrem estados, migração e proteção, nível 12, quantidade de sementes e sacks, consumo exato no plantio/pagamento, notas, cura/limpeza e preservação de cabbage.

O Agent Server confirmou 100 tomato seeds e 20 sacks anotados de cabbages no inventário. O personagem estava no Grand Exchange com o OC Farming desligado. O ciclo de tomato ainda precisa de validação em jogo; não houve deslocamento nem alteração da cultura selecionada durante esta implementação.
