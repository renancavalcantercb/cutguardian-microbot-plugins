# Preparação de run — 0.8.0

## Referências verificadas antes de implementar

- `Microbot-Hub/.../farmtreerun/FarmTreeRunScript.java`, método `bank`: conta patches selecionados, multiplica saplings/pagamentos, desconta o inventário e agrupa retiradas em item e nota. `FarmingItem` contém ID, quantidade, noted e optional. Foi a principal referência da implementação.
- `Microbot-Hub/.../herbrun/HerbrunScript.java`, `setupAutoInventory`: abre banco e retira ferramentas e runas. Sua política interrompe a run quando faltam runas; esse comportamento não atende ao pedido e não foi adotado.
- `Microbot/.../util/Rs2InventorySetup.java`: carrega um preset nomeado e trata inventário/equipamento. Não foi necessário exigir um preset manual para uma quantidade calculada pelos locais habilitados.
- `Microbot/.../util/bank/Rs2Bank.java`: reutilizados `openBank`, `verifyBankMirrorAfterOpen`, `depositX`, `withdrawX`, `setWithdrawAs` e `closeBank`, com `Rs2Walker` para deslocamento. `withdrawDeficit` já existe, mas a distinção entre ID base no banco e ID recebido em nota requer cálculo explícito no plano.

Não há dependência de instalação dos runners do Hub: os padrões de preparação foram adaptados para as confirmações e o worker do OC Farming.

## Implementação

`FarmingSupplyPlan` calcula a quantidade de uma run completa pelos locais habilitados. Seis locais: seis saplings; quatro locais: quatro. A preparação não depende de todos estarem maduros; árvores em crescimento continuam preservadas pelo executor. O pagamento acompanha a árvore selecionada e a opção Protect trees.

`FarmingRunSupplies` faz uma preparação por run e guarda esse estado até o trabalho pendente terminar. Sessão, seleção de árvore, locais ou configuração de suprimentos alterados invalidam o preparo. Não reabastece a quantidade completa a cada sapling consumido.

`FarmingBankActions` usa o banco existente e confirma a diferença exata de itens no inventário depois de cada operação. O plano só recebe estoque de um banco aberto com snapshot confirmado. Cliques de retirada sozinhos não liberam a run. Navegação, banco e farming compartilham o mesmo worker; a preparação não começa durante uma ação pendente do executor.

Obrigatórios: saplings sem nota, spade, rake e pagamento completo quando proteção está ligada. Conta mochila + banco antes de retirar; falta de estoque bloqueia com mensagem específica e nova tentativa após 60 segundos. Saplings anotados são depositados e retirados como item. Saplings excedentes voltam ao banco. Pagamentos são normalizados para nota durante o preparo no banco, mantendo o que já existe em nota e retirando só o déficit. Sem ida ao banco, um pagamento completo já carregado sem nota também é aceito.

Runas opcionais: air 556, fire 554, earth 557, water 555 e law 563. Reserva configurável de 0 a 10.000, padrão 100 de cada. Só completa a reserva durante uma ida necessária ao banco; aceita estoque parcial, ignora indisponibilidade e não insiste em retirada opcional que falhou. Não busca runas no rune pouch nem ajusta equipamento. Moedas para remoção recebem reposição oportunista de até 200 por patch; não são requisito para preparar patches vazios.

Guarda itens não necessários apenas se precisar abrir espaço para os obrigatórios, reservando uma vaga para weeds. Não deposita equipamento. Fecha o banco e deixa a retirada em modo item antes de liberar o executor.

## Validação

Build Java 11 / cliente local 2.6.22 passou com 62 testes. Os 12 novos casos cobrem seleção de locais, déficit/excesso, estoque insuficiente, saplings anotados, pagamentos mistos, runas parciais/ausentes, espaço, cancelamento, ausência de banco atualizado e confirmação antes de liberar a run. Também verificam que consumo entre patches não dispara reposição completa e que falha opcional não bloqueia.

Limitação: preparação de banco ainda não executada ao vivo nesta versão. A consulta de inventário pelo Agent Server foi somente de leitura. O primeiro teste deverá conferir quantidade final de saplings, pagamentos, modo de retirada e retomada da rota após fechar o banco.
