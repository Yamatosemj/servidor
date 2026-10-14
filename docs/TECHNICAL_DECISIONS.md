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


## V0.5.2 — ticket temporário para manter o Auren adulto em funcionamento

**Problema real encontrado no Zalith:** com distância de simulação baixa, a entidade adulta saía da área de chunks em execução antes de atingir/avaliar o limite de teleporte de 50 blocos. A meta de seguir não processa nem teleporta se o chunk do dragão descarregou.

Solução focada: `AurenAdultChunkLoader` usa uma `TicketType<UUID>` temporária com `addRegionTicket(..., distance=2, ..., forceTicks=true)` em **um chunk central** para continuar executando o Auren enquanto seu dono está online e na mesma dimensão. Renova removendo e readicionando a cada 20 ticks (timeout 100). Libera a ticket ao mudar de chunk, de forma (adulto→filhote), sentar, perder o dono online, mudar dimensão ou morrer/remover entidade. Não usa `/forceload` persistente; nenhuma alteração no modo pequeno ou nas animações.

**Limitação importante:** se o chunk do Auren já tinha sido descarregado **antes** da instalação desta versão, o servidor não sabe onde reativar sua entidade. O usuário deve aproximar-se da localização antiga uma vez para que o ticket seja ativado. Persistir índice global e recuperar entidade fria após reinício não está no escopo desta atualização.

Validação: build/compilação e teste estrutural de emissão/renovação/remoção. **Teste efetivo de unload + teleporte exigirá Zalith**. Se falhar por ausência de pouso seguro, melhorar a busca sem permitir teleporte para dentro de blocos.


## V0.5.3 — correção do filhote torto no ombro com pose CE original

Usuário confirmou que as animações originais de caminhada/voo estão aceitáveis,
mas o filhote fica extremamente torto sobre o pescoço.

**Causa identificada:** v0.4–0.5.2 carregava `lightningdragon_sitting_shoulder.tbl`,
mas adicionava `AurenNeckPose` (várias torções manuais em pescoço/asa/cauda),
e girava o dragão inteiro **90 graus**. Também faltava o deslocamento especial
`BodyUpper.offsetZ → -12` aplicado pelo `DragonTabulaModelAnimator` da CE
quando `ridingProgress > 0`.

**Correção:** retiramos torções manuais e rotação global, mantemos interpolação
dos ossos para a pose original CE por 20 ticks, aplicamos offset do torso com
mesmo `shoulderBlend`, movemos o anchor para o primeiro ombro esquerdo
(original CE +0,5 lateral, altura +1,4 e +0,04 de recuo) e travamos a
orientação à rotação do torso do dono, não à rotação da câmera.

**Escopo:** nenhuma alteração no filhote que segue caminhando, no adulto, nos
tickets de chunk, nas poses CE de caminhada/voo e na escala 0,42/3,25.

**Limitação:** a pose oficial CE foi projetada para **empoleirar no ombro**,
não para dar a volta no pescoço como cachecol. Não prometer esse resultado
sem testar o posicionamento no Zalith; se ele ficar alinhado mas ainda
parecer empoleirado, é uma decisão visual separada.


## V0.5.4 — animação original em repouso aplicada à pose original de ombro

O teste no Zalith confirmou que o Auren v0.5.3 ficava rígido no ombro. O motivo:
o arquivo original `lightningdragon_sitting_shoulder.tbl` é uma **pose estática**,
enquanto o `DragonTabulaModelAnimator` do IceAndFire-CE continua aplicando
movimentos de repouso (`chainSwing`, `chainWave`, `walk`, `bob`) sobre os
ossos do modelo a cada frame. Nosso renderizador retornava imediatamente após
aplicar a pose, sem animá-la.

**Correção somente de animação do ombro:** porta as instruções originais da
Community Edition para o repouso acordado (original `speed_idle=0.05F`,
`degree_idle=0.5F`, amplitudes e offsets originais) em
`AurenOriginalShoulderIdle.apply`: movimento sutil da cauda/pescoço e
respiração/oscilações de tronco/patas/asas. A amplitude usa somente o blend
existente de entrada/saída de ombro de 20 ticks. Não cria novas poses próprias.

Preserva anchor, escala 0,42/3,25, animações originais de caminhar/voar,
acompanhamento das formas, limite de teleporte adulto 50 blocos, chunk tickets.
A aprovação visual exige teste pelo jogador no Zalith.


## V0.5.5 — imagem do usuário comprovou postura em pé no ombro; troca para pose CE de repouso curvado

Captura do Zalith (14/10/2026) mostra filhote aparentemente de pé **atrás da
cabeça do jogador**, com asas abertas e patas penduradas, embora a oscilação
de idle funcione. A comparação objetiva dos `.tbl` da IceAndFire-CE 1.21.1
mostrou que `sitting_shoulder` é pouco diferente de `ground` no corpo, enquanto
`sleeping` abaixa torso, curva pescoço e recolhe asas. Importamos `sitting`
e `sleeping` do MESMO commit imutável, com checagem SHA Git no Gradle.

Apenas na forma pequena sobre o ombro, a meta da transição de ossos de 20
ticks muda para a pose `lightningdragon_sleeping` ORIGINAL da CE. Preserva
offset do torso, idle da CE, o tamanho 0.42 e a orientação pelo yaw do
jogador. O anchor físico foi abaixado de 1.4 para 1.16 bloco e deslocado
lateralmente 0.36 bloco + 0.10 para trás, em vez de 0.5 + 0.04.
Não cria nova pose nem toca IA, chunk ticket ou teleporte (confirmado
funcionando no relato anterior).

O nome sleeping indica que a nova pose pode parecer um dragão dormindo,
ao invés de um filhote somente sentado. Visual final aguarda teste no Zalith.
