# Auren Minecraft — v0.4.0-alpha (NeoForge 1.21.1)

**Auren** é um único dragão companheiro com forma adulta e filhote, modelo Tabula do dragão de raios da IceAndFire Community Edition e biblioteca Uranus embutida no JAR.

## Alteração principal — animações de ombro inspiradas na CE

Na Community Edition, a pose `SIT_ON_PLAYER_POSE` não substitui bruscamente o dragão. O animador `DragonTabulaModelAnimator` usa `transitionTo()` para **misturar as rotações e posições das articulações** com `ridingProgress` (0–20 ticks).

Nesta versão do Auren, adaptamos esse princípio sem trazer o mod inteiro:
- **AurenPoseTransition** interpola cada osso Tabula do modelo normal para a pose original `lightningdragon_sitting_shoulder.tbl`, com rotação pelo caminho mais curto.
- A criatura tem uma progressão visual de **20 ticks** para entrar ou sair do modo de ombro; apenas o estado de ombro é sincronizado na rede.
- A pose personalizada **AurenNeckPose** acrescenta gradualmente o pescoço curvado, a cauda ao redor do ombro direito e as asas recolhidas, em cima da pose da CE.
- A orientação global também faz transição suave; o renderizador não troca mais instantaneamente de modelo.
- **Mesmo tamanho v0.2/v0.3 do filhote (escala 0,42)**, sem afetar o dragão adulto ou o filhote caminhando.

**Importante:** esta é uma adaptação do sistema de *transição de pose* da CE, não uma cópia de seu animador completo (que depende de voo, combate, estados e várias animações ainda inexistentes no Auren). A posição física do dragão continua sendo fixada ao jogador quando o ombro é ativado, e precisa de teste visual para garantir que não haja clipping durante a transição.

## O que já foi confirmado pelo jogador

- NeoForge 1.21.1 no Zalith: inicia com Auren e Uranus embutido.
- Modelo 3D, texturas, domesticação e duas formas funcionam.
- O filhote consegue ficar preso ao jogador.
- A v0.3 ainda parecia empoleirada com as asas abertas; a nova interpolação tem como objetivo corrigir a passagem do repouso normal à pose original e preservar a silhueta de cachecol vivo.

## Como testar (Zalith / Windows)

1. Retire o JAR da versão anterior da pasta `mods` e instale o novo.
2. No mundo, invoque `/summon aurenminecraft:auren`, dome o Auren e coloque na forma filhote.
3. Digite `/auren ombro` e observe por alguns segundos de lado e de costas. A animação deve acontecer ao longo de aproximadamente **1 segundo**.
4. Use `/auren ombro` novamente para sair do ombro e observar a transição reversa.
5. Confira também o Auren adulto e o filhote solto para verificar regressões.

## Escopo atual

**Sem** IA conectada, voo, montaria, ataque de raios ou animações de combate. O modelo não foi remodelado; continuam possíveis pequenos encaixes incorretos entre patas, asas, cauda e pescoço até refinarmos as posições.

## Fonte e créditos

- [IceAndFire Community Edition 1.21.1](https://github.com/IAFEnvoy/IceAndFire-CE/tree/1.21.1) — conceito do `ridingProgress` e `transitionTo`; origem dos arquivos Tabula e texturas sob as condições aplicáveis.
- [Uranus](https://github.com/IAFEnvoy/Uranus/tree/1.21.1) — renderização Tabula e dependência incluída via Jar-in-Jar.
- Confira `THIRD_PARTY_ASSETS.md` e `docs/TECHNICAL_DECISIONS.md`.
