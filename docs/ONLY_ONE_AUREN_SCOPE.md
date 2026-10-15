# Contrato de escopo — um único Auren, sem conteúdo CE

O IceAndFire CE original NÃO deve ser instalado como mod durante testes
do Auren final. Importar a CE inteira foi um experimento de referência
(v0.7.0) que introduziu indevidamente itens, entidades e geração de mundo.

## Objetivo técnico final

- Apenas `aurenminecraft:auren` como entidade tipo dragão,
  personalizável com tamanho grande/pequeno, ombro, vínculo, ritual e
  capacidades de voo, montaria e combate do IceAndFire CE.
- Nenhum registro extra de itens, ovos, armaduras, criaturas, estruturas,
  dimensões, loot, blocos ou abas do IceAndFire.
- Retornar à classe original de movimento e AI do CE **sem reescrever física**:
  portar a menor árvore de dependências de código de dragão,
  animadores, recursos e rede, sob a licença LGPL. Separar código
  reutilizável de registros globais do mod. Validar no Minecraft.
- Não mudar o ID `aurenminecraft:auren` sem migração explícita dos
  dados salvos. Não remover recursos usados pelo único dragão.

## Esta branch (v0.7.1-single-auren-alpha)

Reverte a dependência de runtime CE adicionada pelo protótipo 0.6.5,
e Jupiter (que a CE requer). Usa as classes Auren anteriores que
continuavam intactas em 0.7.0. O CE original segue como **git submodule
somente para pesquisa**. A compilação verifica exatamente um tipo
de entidade, sem recursos ou dependências CE e sem `jupiter`.

**LIMITE:** isso não é a migração completa nem possui todos os
comportamentos do original. É uma correção de ESCOPO para permitir
testar um único dragão enquanto fazemos a portagem com segurança.

## Próximo desenvolvimento, um sistema por vez

1. Comparar `DragonBaseEntity`, `LightningDragonEntity`,
   `IafDragonLogic`, `IafDragonFlightManager`, registries, AI e
   animadores originais contra os atuais controladores Auren.
2. Integrar o motor necessário em pacotes específicos Auren, sem
   `@Mod(iceandfire)` nem registries globais CE.
3. Migrar voo e montaria primeiro com comportamento original comprovado;
   depois stamina/combat e animações, preservando shoulder/transformação.
4. Remover apenas classes de suporte sem uso; validar no jogo, em
   save novo e no backup, antes de qualquer merge na main.
