# Auren Minecraft

Mod experimental de Minecraft para representar o **Auren** como um companheiro dragão com formas alternativas, habilidades e integração futura com o assistente Auren principal.

> **Status:** planejamento inicial. Ainda não existe build jogável ou integração com IA.

## Plataforma definida

- **Minecraft:** Java Edition 1.21.1
- **Mod loader:** NeoForge (ramo 1.21.1; versão exata será fixada ao configurar o projeto)
- **Desenvolvimento:** Java 21 / Gradle
- **Repositório do Auren principal:** [aurenOFC](https://github.com/Yamatosemj/aurenOFC) (projeto independente)

## Conceito

Uma única identidade com diferentes formas, preservando personalidade e memória quando a integração com o Auren estiver pronta.

### Formas planejadas
- **Dragão grande:** voo, montaria e combate.
- **Dragão pequeno:** acompanhamento em construções e locais fechados.
- **Chibi / ombro:** companhia e análise contextual de itens.
- **Mini / inventário:** forma portátil para guardar e invocar o Auren.
- **Ferramenta:** formas utilitárias para mineração ou combate.
- **Braço:** forma equipada com disparos de raios na direção da mira.

## Base de dragões — candidatas para validação

O Ice and Fire original não possui lançamento oficial para Minecraft 1.21.1. Há pelo menos duas alternativas comunitárias para NeoForge 1.21.1:

1. **[IceAndFire Community Edition](https://www.curseforge.com/minecraft/mc-mods/iceandfire-ce)** — versão comunitária ativamente atualizada, com otimizações, reescritas e dragões do Ice and Fire. **Candidata preferencial**, sujeita a testes de dependências, renderização e capacidade de extensão. Lançamento 1.21.1 identificado: `iceandfire-2.1.4.jar` (11/10/2026).
2. **[Ice and Fire: Dragons Port](https://www.curseforge.com/minecraft/mc-mods/ice-and-fire-dragons-port)** — port não oficial do conteúdo original. Alternativa para testar se a integração com Community Edition não atender. Lançamento 1.21.1 identificado: `iceandfire-neoforge-2.1.13-1.21.1-port.1.jar` (12/05/2026).

**Não instalar as duas bases juntas** sem verificar conflitos de IDs e registros. Ainda não há dependência escolhida nem build validada.

## Direção técnica

- Mod Java separado do Auren principal em Python.
- Priorizar dependência do mod de dragões instalado, sem incorporar seus arquivos ao projeto quando isso for evitável.
- Comportamentos locais controlam locomoção, voo, combate e transformações sem exigir chamadas contínuas à IA.
- Uma ponte de comunicação futura permitirá ao assistente interpretar pedidos, observar eventos e comandar ações do mod.
- Confirmar licenças de código e recursos visuais antes de reutilizá-los ou redistribuí-los.

## Próxima etapa

1. Selecionar e validar **uma** base de dragões para NeoForge 1.21.1 e suas dependências.
2. Preparar o projeto NeoForge 1.21.1 em Java 21 com builds e testes automatizados.
3. Implementar o menor protótipo jogável: um único companheiro identificado como Auren que aparece no mundo e executa um comando básico.
4. Acrescentar transformação simples e conexão com a IA somente após o protótipo funcionar.

## Repositório

Este repositório foi reaproveitado do antigo projeto `servidor`. O conteúdo anterior foi removido da branch principal por commit, mantendo o histórico Git.
