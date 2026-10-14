# Auren v0.3 — Pose enrolado no pescoço

## Referência aprovada

O jogador aprovou uma ilustração conceitual com visão frontal, traseira e detalhes:

- Corpo do filhote repousando **atrás da nuca**, atravessando a parte alta das costas;
- Cabeça e pescoço se curvam para ficar no **ombro esquerdo** do jogador;
- Cauda contorna a região do **ombro direito** e acompanha as costas;
- Asas recolhidas contra o corpo, não abertas lateralmente;
- Patas abraçam/seguram os ombros, como um cachecol vivo;
- **Sem mudar o tamanho do filhote atual**, já aprovado em jogo.

Este documento traduz a ilustração para ajustes de ossos Tabula. A imagem de conceito é referência, não uma pose 3D pronta.

## Implementação

- `AurenNeckPose.apply(...)`: gira ossos nomeados de pescoço (Neck1..3), cauda (Tail1..5), corpo, asas (armL1..2 / armR1..2) e patas. Aplica respiração discreta.
- `AurenDragonRenderer`: ao detectar `isSmallForm && isOnShoulder`, usa o Tabula original `lightningdragon_sitting_shoulder` com pose nova, vira o modelo em 90° e mantém escala **0.42**.
- `AurenDragon.tick()`: posiciona o corpo no centro/atrás do pescoço, seguindo a orientação do jogador. A lógica de domar, transformar e seguir não muda.
- Nenhuma nova entidade, dependência ou modelo de origem. Uranus continua embutido no JAR.
- `scripts/verify_neck_pose.py`: garante que todos os nomes de ossos usados existem no modelo Tabula empacotado; a build continua checando Uranus e recursos externos.

## Teste visual obrigatório (Android / Zalith)

1. Instalar `aurenminecraft-0.3.0-alpha.jar` **no lugar** da v0.2 e confirmar jogo entra.
2. Domar o filhote (ou utilizar o mesmo já domesticado num mundo de teste).
3. Usar `/auren ombro` ou clicar no filhote com mão vazia, sem agachar.
4. Usar F5 para ver **frente, costas e perfil** e comparar com a imagem aprovada.
5. Confirmar a cabeça no ombro esquerdo e cauda sobre o direito; verificar se as asas ficam dobradas e não atravessam a cabeça.
6. Caminhar, girar, agachar para soltar e invocar novamente. Verificar se o dragão permanece centrado atrás da nuca e se nada treme ou fica flutuando.
7. Desativar ombro e checar que o filhote andando no chão e o adulto mantêm tamanho e pose anteriores.

**Possíveis ajustes após capturas:** sinal do giro de 90° (lado correto), inclinação da cabeça, abertura residual das asas, curvatura da cauda, deslocamento lateral e altura da ancoragem. Esses são valores pontuais isolados na pose; não implicam reescrever o modelo.

## Escopo e limitações

- A pose foi feita por **cinemática de ossos existente**, sem remodelar a malha ou criar novos ossos. A anatomia cúbica e as asas longas podem impedir correspondência perfeita à ilustração.
- **Nenhum teste visual automatizado prova a pose 3D dentro do Minecraft.** Build/CI apenas valida código e estrutura.
- Esta tarefa não adiciona voo, combate, ferramenta, IA, novas formas ou mecânicas não relacionadas.
