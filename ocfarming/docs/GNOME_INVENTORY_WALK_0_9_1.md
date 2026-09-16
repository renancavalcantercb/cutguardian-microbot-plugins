# Idas e voltas em Gnome Stronghold — 0.9.1

Depois do rake na frutífera, o executor voltava até a árvore comum para descartar weeds. O deslocamento, somado aos descartes individuais, às vezes dava tempo para nascerem weeds novamente na frutífera. Após finalmente plantar apple, ele também voltava até a árvore comum para descartar o vaso vazio antes de proteger a frutífera.

O Agent Server confirmou willow crescendo e protegido (varbit 4771 = 16), apple crescendo e ainda sem proteção registrada (4772 = 8), um vaso 5350 na mochila e status `Approaching Gnome Stronghold tree` enquanto o personagem se afastava da frutífera. Os logs mostraram rake na frutífera seguido por descarte de weeds associado à árvore comum, repetidamente. [Evidência anterior](evidence/gnome-loop-before-2026-09-13.json).

A causa era a verificação de distância em `FarmingGameActions.near()`: descartes usavam a mesma distância genérica de 20 tiles que as interações no local. O executor avalia a árvore comum primeiro e atribuía a ela o descarte de qualquer lixo na mochila, mesmo que tivesse sido produzido na frutífera, a cerca de 36 tiles de distância.

`DROP_WEEDS` e `DROP_POTS` agora dispensam aproximação. Ainda passam pelas verificações existentes de estado do jogador, região e leitura recente, e exigem confirmação de perda do item na mochila. Plantio, rake, NPCs e demais ações mantêm suas verificações de distância.

Java 11 / Microbot 2.6.22: 95 testes passaram. Duas regressões executam o ciclo de Gnome Stronghold com os dois patches ativos, usando a verificação real de distância para os descartes: rake → descartar weeds → plantar; e plantar → descartar vaso → proteger. Ambas exigem zero chamadas de aproximação para o descarte, mesmo com o plano associado à árvore comum distante.

Uma cópia temporária 0.9.1 executou somente a conclusão do trabalho local já iniciado, sem novo preparo de banco nem visitas a outras regiões. Ela concluiu a proteção da apple, confirmou o pagamento e liberou o trabalho local, sem ação pendente, com willow e apple protegidas. [Evidência após a correção](evidence/gnome-loop-after-2026-09-13.json). Nas últimas amostras o personagem já estava fora de Gnome; elas não demonstram permanência física no patch. O vaso já havia sido descartado pela 0.9.0 antes da parada; portanto, a eliminação da caminhada de descarte foi exercitada nos testes de regressão, não repetida ao vivo. A cópia e a sonda foram descarregadas após a validação.
