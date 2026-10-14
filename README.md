# Auren Minecraft — v0.3.0-alpha (NeoForge 1.21.1)

Um único dragão companheiro Auren, com forma adulta, filhote e **pose de pescoço** baseada na ilustração aprovada pelo jogador.

## Funcionando no Android / Zalith (já observado)

- Dragão adulto e filhote renderizam com o modelo de raios da IceAndFire Community Edition.
- Domesticação, acompanhamento e transformação entre duas formas.
- V0.2: filhote aparece sobre o ombro do jogador (fotos e relato confirmados).

## Novo em v0.3 — pose de cachecol de dragão

**Objetivo visual:** Auren se enrola em torno do pescoço, com o corpo sobre a nuca, a cabeça apoiada no **ombro esquerdo**, a cauda curvada para o **ombro direito**, as asas dobradas e as patas apoiadas nas costas/ombros.

- Reaproveita o modelo Tabula `lightningdragon_sitting_shoulder` e posiciona os ossos diretamente no renderizador.
- Ajusta a posição do corpo relativa à rotação do jogador, para não parecer pousado em um único ombro.
- **Mantém exatamente o tamanho atual da forma filhote (escala 0,42)**, conforme solicitado.
- Não altera o visual ou a IA do adulto e do filhote andando no chão.
- Mantém a animação suave de respiração e a cauda mexendo discretamente no pescoço.
- O tamanho das asas não foi reduzido: são dobradas por articulação.

**Status:** pose implementada e dependente de validação visual em jogo; compilação por si só não prova que cada articulação está perfeita. Ver `docs/NECK_POSE_V03.md` para detalhes e testes.

## Requisitos

- Minecraft Java **1.21.1**
- NeoForge **21.1.248+**, na série 1.21.1
- Apenas o JAR do Auren na pasta `mods`; Uranus 3.0-beta.1 está **incluído via Jar-in-Jar**. Ice and Fire não é necessário.

## Teste rápido

1. Remova a versão anterior de `mods` e instale apenas o JAR v0.3.
2. Entre num mundo com comandos e invoque `/summon aurenminecraft:auren`.
3. Com a mão vazia, interaja para domesticar; agache e interaja para transformá-lo em filhote.
4. Sem agachar, interaja com o filhote para ativar a pose de pescoço; ou use `/auren ombro`.
5. Pressione F5 para câmera traseira, caminhe e gire. Confira a cabeça no ombro esquerdo, o corpo atrás da nuca e a cauda atravessando o ombro direito.
6. Agache ou use `/auren ombro` de novo para tirá-lo do pescoço.
7. Veja se o adulto e o filhote fora do ombro continuam com os tamanhos e comportamentos anteriores.

**Limitações atuais:** sem montaria, voo, ataques elétricos, animações completas de voo/combate ou IA externa. A pose específica ainda exige inspeção visual no Android.

## Arquivos principais

- `src/main/java/br/com/auren/minecraft/client/AurenNeckPose.java` — ângulos dos ossos da pose aprovada.
- `src/main/java/br/com/auren/minecraft/client/AurenDragonRenderer.java` — seleciona a pose, orientação e escala.
- `src/main/java/br/com/auren/minecraft/entity/AurenDragon.java` — posição do corpo relativa ao jogador.
- `docs/NECK_POSE_V03.md` — referência visual e roteiro de teste.
- `THIRD_PARTY_ASSETS.md` — créditos e licenças.
