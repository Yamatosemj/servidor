# Auren Minecraft

Mod experimental para representar o **Auren** como um único companheiro dragão no Minecraft, com transformações e futura integração com o assistente Auren principal.

> **Status:** planejamento. Ainda não existe protótipo jogável nem integração com IA.

## Plataforma definida

- **Minecraft:** Java Edition 1.21.1
- **Loader:** NeoForge para 1.21.1 (versão exata a fixar)
- **Desenvolvimento:** Java 21 / Gradle
- **Projeto principal:** [aurenOFC](https://github.com/Yamatosemj/aurenOFC), separado deste repositório

## Identidade e aparências — decisão de escopo

**Auren é uma entidade única**, com memória e personalidade próprias. Não vamos acrescentar os demais dragões, criaturas ou sistemas de progressão do Ice and Fire como conteúdo do mod Auren.

A referência escolhida para **modelos e animações** é a [IceAndFire Community Edition](https://github.com/IAFEnvoy/IceAndFire-CE), ramo **1.21.1**, correspondente a Minecraft 1.21.1 + NeoForge. Arquivo publicado de referência: `iceandfire-2.1.4.jar` (11/10/2026). Nenhum recurso foi copiado ainda.

**Apenas duas apresentações principais do dragão:**

1. **Dragão grande / adulto:** voo, acompanhamento externo, combate, montaria; tamanho inspirado no estágio 5.
2. **Dragão filhote:** acompanhamento em interiores, permanência no ombro e assistente de itens; tamanho inspirado no estágio 1.

No código da Community Edition 1.21.1, o dragão domesticado com estágio inferior a 2 pode subir no jogador ao interagir com a mão vazia, e existem animações `*_sitting_shoulder.tbl` para dragões de fogo, gelo e raios. **Isso comprova a existência da mecânica original, não o funcionamento automático dentro do nosso mod independente**.

O dragão de raios é **a sugestão inicial**, pois combina com a futura forma de braço lança-raios. Elemento, variante e textura final ainda precisam ser escolhidos.

### Demais transformações planejadas

Forma pequena doméstica, forma Chibi/de ombro, forma mini/inventário, ferramenta e forma de braço **não exigem necessariamente novos modelos principais**. Podem reutilizar o modelo de filhote, animações e representações de item/equipamento, se o protótipo demonstrar viabilidade. Habilidades únicas ficam para fases posteriores.

## Estratégia de implementação

**Objetivo final:** um mod Auren enxuto, contendo apenas sua entidade e os sistemas necessários. Há duas abordagens a comparar tecnicamente:

- **Add-on com dependência da Community Edition:** reusa renderização, mecânicas e entidades diretamente, mas exige instalar a Community Edition completa (com todo o conteúdo dela).
- **Mod independente com recursos selecionados e adaptação autorizada:** evita instalar a Community Edition completa, mas exige implementar/adaptar renderizador, animações, controle, IA e transformações; custo técnico maior.

Como o objetivo declarado é **usar apenas o Auren**, investigar prioritariamente a viabilidade da segunda opção, sem supor que basta importar dois arquivos de modelo. Confirmar direitos dos recursos e conformidade com a licença LGPL-3.0 e outros créditos aplicáveis antes de copiar/distribuir assets.

Comportamentos locais devem cuidar de movimento, ombro, combate e transformações sem depender da IA externa. Uma ponte futura enviará eventos e comandos para o Auren principal em Python.

## Primeira prova de conceito

1. Fixar toolchain NeoForge 1.21.1 / Java 21 e levantar os arquivos de modelo, animação e textura estritamente necessários.
2. Provar renderização de **um Auren adulto e um Auren filhote**, usando o mesmo tipo de dragão e sem incluir os outros mobs.
3. Implementar troca manual entre duas formas preservando identidade/estado.
4. Testar filhote no ombro, com entrada/saída e sincronização multiplayer.
5. Só então implementar comandos, comportamento de companheiro e futura ligação com a IA.

## Repositório

Reaproveitado do projeto antigo `servidor`. O conteúdo anterior foi removido da branch principal por commit, preservando o histórico Git.
