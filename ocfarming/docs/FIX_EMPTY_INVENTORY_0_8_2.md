# Preparo com mochila vazia — 0.8.2

O plugin ficava em `Waiting to prepare run` apesar de haver um oak em Nemus aguardando check-health.

O Agent Server confirmou duas vezes na versão 0.8.1: personagem conectado, parado, fora de combate e de instância, navegação liberada, mochila vazia e `client.getItemContainer(InventoryID.INVENTORY) == null`. O estado de preparo permanecia sem abrir o banco, sem tentativa e sem cooldown. Evidência: [estado anterior](evidence/empty-inventory-preparation-2026-09-11.json).

`FarmingBankActions.ready()` reutilizava a condição para agir nos patches, que exige um container de inventário. Com a mochila totalmente vazia após login, essa condição impedia até abrir o banco para obter os materiais.

O snapshot agora separa a disponibilidade do personagem para ir ao banco (`bankReady`) da disponibilidade para ações nos patches (`ready`). O banco aceita mochila sem container como vazia; as ações nos patches continuam exigindo container. As condições de login, personagem, mundo, instância, combate e movimento continuam sendo verificadas.

Build Java 11 / Microbot 2.6.22: 74 testes passaram. Quatro regressões adicionais verificam: banco com inventário sem container; personagem indisponível; movimento; e preparo completo da run partindo de mochila vazia, com retirada de materiais, runas e fechamento. O teste do preparo utiliza a condição real de disponibilidade do banco.

## Validação ao vivo

Em 11/09/2026, uma cópia 0.8.2 com pacote e nome exclusivos foi carregada pelo Agent Server após parar a versão 0.8.1. Partindo da mesma mochila vazia no Grand Exchange, o plugin abriu o banco, retirou exatamente seis oak saplings sem nota, seis Tomatoes(5) em nota, uma spade, um rake, 100 de cada rune (air, fire, earth, water e law) e 1.200 coins. Fechou o banco e marcou `prepared=true`, `banking=false`, passando a `Visiting: Nemus Retreat tree`. Isso também validou a troca de Note para Item e o fechamento corrigidos na 0.8.1.

Evidência: [preparo concluído](evidence/empty-inventory-preparation-fixed-2026-09-11.json). O teste foi encerrado durante a viagem; não validou o check-health em Nemus. A cópia temporária e a sonda foram descarregadas. Reiniciar o cliente e ativar `[OC] Farming` para usar o JAR instalado.
