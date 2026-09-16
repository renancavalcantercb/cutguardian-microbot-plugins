# OC Farming 0.7.0 — somente árvores

A pedido do usuário, o projeto fica voltado a tree e fruit tree. O executor atual suporta árvores comuns nos seis locais da 0.6.0; fruit trees ficam para a próxima implementação.

## Remoção

Removidos os quatro patches de allotment, seus decoders e culturas, plantio de sementes, colheita, conversão de vegetais em notas, plant cure e compostagem de allotment. Removidas as opções `autoFarm`, `crop`, `payProtection` e `soilCompost`, além das classes `FarmingCrop` e `FarmingCompost`. O monitor e o planner percorrem somente o catálogo de árvores. O grupo `ocfarming` continua o mesmo e as chaves de árvore não mudaram: o histórico `T1` da 0.6.0 continua válido; dados antigos `1` de allotments não são interpretados como árvores.

Os testes exclusivos de allotments foram removidos. Testes de comportamento compartilhado (estabilização, isolamento por perfil, proteção, previsões, visitas e aproximação) foram convertidos para árvores. A documentação anterior permanece como histórico dos experimentos.

## Relato: limpa, mas não planta

Consulta pelo Agent Server em 11/09/2026: inventário com dez oak saplings sem nota, spade, rake e mais de 1,5 milhão de coins; nenhuma cesta Tomatoes(5), nem seu item anotado. Log da 0.6.0:

```text
16:24:45 Varrock tree: RAKE confirmed
16:24:54 Varrock tree: Need protection payment for Oak (retry after 60s)
```

O bloqueio é uma precondição de `Protect trees`: o pagamento de oak é uma cesta cheia de tomates, não coins. A configuração de proteção foi preservada. A mensagem agora especifica `Need 1 Tomatoes(5) to protect Oak (notes accepted)`. Um teste reproduz o inventário com moedas e sapling sem cesta: bloqueia com proteção ligada e permite plantar com proteção desligada.

O plugin foi desligado via Agent Server durante a atualização, para interromper viagens enquanto os materiais faltavam. Após reiniciar com a 0.7.0, levar uma Tomatoes(5) por patch (5968 ou nota 5969) e reativar o plugin. Também é possível desligar Protect trees por escolha do usuário.

Ainda não foi confirmado plantio de oak em jogo: faltava o pagamento no momento do diagnóstico. Não se confunde build/testes bem-sucedidos com validação do diálogo real de plantio e proteção.

Build da versão 0.7.0 aprovado com 50 testes, sem falhas. A redução em relação aos 96 testes anteriores corresponde à remoção dos casos específicos de allotments e à conversão dos testes compartilhados para árvores.
