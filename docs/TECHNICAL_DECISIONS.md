# Protótipo — decisões e limitações

## Fontes examinadas (13/10/2026)

- Código da [IceAndFire Community Edition, ramo 1.21.1](https://github.com/IAFEnvoy/IceAndFire-CE/tree/1.21.1)
- [Uranus, ramo 1.21.1](https://github.com/IAFEnvoy/Uranus/tree/1.21.1)
- [NeoForge 1.21.1 MDK oficial](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle)
- [Guia NeoForge 1.21.1](https://docs.neoforged.net/docs/1.21.1/gettingstarted/)
- [Discussão da comunidade sobre modelos Tabula do Ice and Fire](https://www.reddit.com/r/feedthebeast/comments/1dp5oc0/changing_ice_and_fire_dragon_models/)
- [Discussão sobre filhotes no ombro](https://www.reddit.com/r/feedthebeast/comments/hbu8qr/ice_and_fire_rebind_stage_1_dragon_dismount/)

## Por que usar Uranus em vez de copiar todo o Ice and Fire?

Os modelos `.tbl` são arquivos ZIP contendo `model.json`, carregados por `TabulaModelHandlerHelper`. A classe `TabulaModel` converte cubos/transformações Tabula em geometria renderizável. Reimplementar esse pipeline agora teria mais risco do que depender da Uranus, a biblioteca já usada pela Community Edition.

**O Ice and Fire original / Community Edition não é dependência do nosso mod.** O Uranus continua sendo uma dependência técnica de runtime, mas seu JAR original é distribuído *dentro* do arquivo do Auren via NeoForge Jar-in-Jar (sem copiar classes, sem sombrear nem reempacotar código). Assim esperamos que o NeoForge registre o `modId=uranus` automaticamente. **A detecção dentro do cliente Android ainda precisa ser testada.**

## Limitações desta primeira prova

- O mod registra **uma única espécie de entidade** `aurenminecraft:auren`, sem spawn natural; instancie com `/summon aurenminecraft:auren`.
- Interação inicial de mão vazia doma o Auren; agachar + clicar com mão vazia alterna entre forma adulta e filhote; no modo filhote clicar novamente o coloca no ombro. Agachar solta o filhote.
- O modelo adulto e o filhote usam o mesmo Tabula base redimensionado, com texturas dos estágios 5 e 1. No ombro há pose Tabula separada.
- **Não foram portadas as dezenas de animações de caminhada, voo ou ataque**. Ainda falta animador interpolado próprio (ver `DragonTabulaModelAnimator` da CE), além de colisão/navegação adequada para adulto.
- Não há voo, raios, montaria, IA externa, inventário ou ferramentas nesta versão.
- A posição de ombro é uma implementação inicial e precisa de validação visual e de multiplayer em Minecraft.
- Não existe build local validada neste ambiente. GitHub Actions tenta compilar e empacotar o jar.
- Uranus 3.0-beta.1 vem do Curse Maven e agora está embutido via Jar-in-Jar. O artefato do GitHub é verificado estruturalmente: precisa incluir `META-INF/jarjar/metadata.json` e um Uranus válido como mod NeoForge. Não precisa de segundo arquivo na pasta `mods`.

## Licenças e procedência

Os quatro arquivos externos escolhidos são baixados durante o build a partir de um **commit imutável** de `IAFEnvoy/IceAndFire-CE`. Verificamos cada Git blob SHA-1 antes de empacotar. Não colocamos o Ice and Fire completo no mod. Consulte `THIRD_PARTY_ASSETS.md`.

## Próximas validações

1. Build Gradle com download de recursos, verificação do Uranus embutido e um único JAR instalável.
2. Minecraft cliente **sem Uranus separado**: confirmar que NeoForge reconhece a dependência embutida; depois ver o dragão em ambas as escalas sem partes deslocadas, testar pose de ombro e desmonte.
3. Persistência de forma e dono após sair do mundo.
4. Só depois melhorar posicionamento, caminhada, animação, voo e comandos de assistente.

## V0.4 — adaptação das transições da CE (14/10/2026)

Conforme confirmado em `IceAndFireTabulaModelAnimator.transitionTo()` e `DragonTabulaModelAnimator.setRotationsLoop()` (ramo 1.21.1 da CE), o mod original usa blend gradual para a pose SIT_ON_PLAYER_POSE. Adaptamos a interpolação nome-a-nome (rotações pelo ângulo mínimo e pivôs XYZ), 20 ticks de progresso local por entidade, pose da CE como referência e a pose de pescoço do Auren como ajuste aditivo. Não copiamos classes completas ou múltiplas mecânicas do dragão original.

Ao contrário da versão v0.3, o renderizador **não alterna mais imediatamente para o arquivo sitting_shoulder**: mantém o esqueleto normal e interpola para a pose original sobre ele. A geometria pode exigir regulagem no Zalith, especialmente a posição durante o primeiro segundo da transformação. O deslocamento físico para atrás da nuca continua instantâneo; não há animação de escalar no personagem.

### Testes

O teste `scripts/verify_neck_pose.py` verifica nomes de ossos e invariantes da transição. Build e dependência aninhada passam por CI. O visual em Android e Windows permanece teste manual, e não deve ser considerado aprovado só por compilar.


## V0.5 — animações originais da Community Edition, não animações inventadas

Em vez de criar nossas próprias poses de voo e caminhada, importamos os arquivos `LightningDragon` oficiais da CE 1.21.1: quatro quadros `walk1..4`, seis `flight1..6` e as poses `flying`, `hovering` e `diving`. Todos vêm do commit imutável já utilizado em `build.gradle`, com hash Git blob SHA-1 verificado.

O animador completo original **não funciona sem a lógica específica de `DragonBaseEntity` da CE**. Utilizamos apenas sua técnica de interpolação por nome das articulações nos quadros Tabula. A caminhada vanilla aciona o ciclo de caminhada. Durante salto/queda, o renderizador usa poses aéreas originais como demonstração; **não há voo mecânico real** ainda. A pose personalizada do pescoço e sua transição de 20 ticks foram preservadas.

Ajustes de velocidade dos ciclos requerem teste visual no Zalith. Não afirmar que o voo está implementado só porque existem animações.


## V0.5.1 — comportamento de seguimento separado entre filhote/adulto (14/10/2026)

A entidade usava um único `FollowOwnerGoal(this, 1.15D, 4.5F, 1.7F)` para ambas as formas. No adulto isso provocava aproximação excessiva (parava a ~1,7 bloco) e teleporte frequente porque a meta vanilla usa o gatilho interno de distância curta.

Nova regra:
- Filhote: preserva `FollowOwnerGoal` anterior, agora ativado **somente se for pequeno e estiver fora do ombro**; parâmetros intactos.
- Adulto: `AurenAdultFollowOwnerGoal`, que inicia a perseguição apenas acima de 15 blocos e para a 8 blocos, recalculando o caminho a cada 10 ticks. Teleporta apenas quando a distância >=50 blocos, procurando terreno sólido e livre de colisão entre ~8 e 12 blocos do dono. Se não encontrar terreno seguro, permanece no chão e tenta continuar caminhando.
- Se o jogador mudar de forma no meio da perseguição, as duas metas não podem continuar na forma errada.
- `shoulder`, poses Tabula, texturas e animações não são tocados.
- Verificar com teste visual no Zalith: 10, 16, 30 e 55 blocos; terreno plano e com obstáculos. O teste de CI confirma estrutura e constantes do código, não simula IA real do Minecraft.


### Follow-up: escala estágio 3, voo e montaria

- CE estágio 3: render size 7–12,5 × 0,35 → cerca de 2,45–4,375 de escala. Nosso Auren usava 5,8.
- V0.5.1 usa escala adulta **3,25** e colisão 1,05 × 1,3 blocos. Filhote permanece com escala 0,42. Isto é apenas um ajuste das duas formas existentes, não introduz estágio de crescimento.
- Distância de início de seguimento adulto **15 blocos** e teleporte emergencial apenas a partir de **50 blocos**.
- O usuário quer filhote e adulto voando para seguir. **Este patch NÃO implementa voo mecânico**; o filhote mantém a meta vanilla provisoriamente (e seu teleporte) até substituí-la por uma IA aérea funcional.
- O Ice and Fire CE permite montaria desde estágio 3, mas o Auren ainda precisa de programação própria de montaria, posição/controle do passageiro e teste no Android.
