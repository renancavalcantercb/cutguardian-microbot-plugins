# Retirada de runas e fechamento do banco — 0.8.1

O usuário relatou precisar retirar runas e fechar o banco manualmente após o preparo da 0.8.0.

## Falha encontrada no código

O `Rs2Bank.setWithdrawAs(false)` do cliente local usa `InterfaceID.Bankmain.QUANTITY1_TEXT` (12:30) para voltar ao modo Item. Esse componente pertence ao seletor de quantidade 1. O componente de Item/Note é `InterfaceID.Bankmain.NOTE` (12:25), usado como toggle em ambas as direções.

A 0.8.0 retirava o pagamento em nota e depois chamava o helper para retirar as runas como item. O clique no seletor de quantidade não mudava `BANK_WITHDRAWNOTES`; a verificação de modo falhava antes da retirada. Além disso, o fechamento exigia que `setWithdrawAsItem()` retornasse sucesso antes de chamar `closeBank()`. A mesma falha podia impedir o fechamento, mesmo com os obrigatórios completos e runas opcionais ignoradas.

## Correção

`FarmingBankActions` agora seleciona o toggle correto para Item e Note, espera até dois segundos e verifica o modo antes de retirar. Não clica quando o modo já está correto. Mantém a confirmação da quantidade recebida e o cancelamento antes de operar.

Antes de fechar, tenta restaurar Item como conveniência, mas essa tentativa não é mais uma precondição do fechamento. O resultado continua dependendo de o banco realmente desaparecer; um clique sozinho não confirma sucesso. Pausa/cancelamento continua impedindo novas interações.

A correção fica no OC Farming e funciona com o JAR local do cliente, sem exigir recompilar o Microbot. O helper global do cliente não foi alterado. Logs foram adicionados para falhas de modo, diferenças de quantidade e fechamento sem confirmação.

## Validação

Build Java 11 / Microbot 2.6.22 e 70 testes passaram. Oito testes novos exercitam a classe real `FarmingBankActions`, substituindo apenas as interações com o cliente: pagamento em nota seguido por runas como item, modo já correto, clique sem mudança de varbit, falha na restauração sem impedir fechamento, cancelamento e banco que continua aberto após o clique.

O Agent Server na porta 8081 estava indisponível durante o diagnóstico e o arquivo de token não existia. Portanto, a causa foi identificada pelo código e a correção foi validada em testes, **sem confirmação ao vivo**. Retestar após reiniciar: retirar pagamento em nota, voltar para Item, retirar runas disponíveis e fechar o banco automaticamente.
