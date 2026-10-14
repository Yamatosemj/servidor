# Auren Minecraft

Mod experimental de Minecraft para representar o **Auren** como um companheiro dragão com formas alternativas, habilidades e integração futura com o assistente Auren principal.

> **Status:** planejamento inicial. Ainda não existe build jogável ou integração com IA.

## Conceito

Uma única identidade com diferentes formas, preservando personalidade e memória quando a integração com o Auren estiver pronta.

### Formas planejadas
- **Dragão grande:** voo, montaria e combate.
- **Dragão pequeno:** acompanhamento em construções e locais fechados.
- **Chibi / ombro:** companhia e análise contextual de itens.
- **Mini / inventário:** forma portátil para guardar e invocar o Auren.
- **Ferramenta:** formas utilitárias para mineração ou combate.
- **Braço:** forma equipada com disparos de raios na direção da mira.

## Direção técnica (provisória)

- Mod Java separado do [Auren principal](https://github.com/Yamatosemj/aurenOFC), desenvolvido em Python.
- Avaliar o **Ice and Fire** como dependência para aproveitar comportamentos, modelos e animações compatíveis.
- Comportamentos locais controlam locomoção, voo, combate e transformações sem exigir chamadas contínuas à IA.
- Uma interface de comunicação futura permitirá ao assistente interpretar pedidos, observar eventos e comandar ações do mod.
- Confirmar licenças de código e recursos visuais antes de reutilizá-los ou redistribuí-los.

## Próxima etapa

Definir **versão do Minecraft**, **mod loader** e a versão compatível do Ice and Fire. Depois implementar o menor protótipo possível: um companheiro único que aparece no mundo, reconhece seu jogador e executa um comando básico.

## Repositório

Este repositório foi reaproveitado do antigo projeto `servidor`. O conteúdo anterior foi removido da branch principal por commit, mantendo o histórico Git.
