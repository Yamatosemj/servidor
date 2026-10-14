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


## V0.5.5 — correção da postura ereta no ombro confirmada por captura de tela

Na captura de tela real no Zalith v0.5.4, o filhote se mexe corretamente
com idle da CE, mas parece **um dragão em pé atrás do personagem**, asas
semiabertas e cabeça erguida. Inspecionamos os arquivos binários originais:
`sitting_shoulder.tbl` tem `BodyUpper.rotationX=-17.78°`,
`Head.rotationX=80.87°` e asas no ombro ainda abertas; não é uma pose
para se deitar.

Alternativa usando apenas arquivos da Community Edition, sem inventar
animações: **`lightningdragon_sleeping.tbl`** original do mesmo commit
pinned, verificado pelo Git blob SHA-1. Esta pose dobra mais o corpo
(`BodyUpper=-28.7°`), pernas (`LegR=-33°`), asas
(`armL1.rotationX=10°`) e curva a cauda. São mudanças verificadas
pela análise estrutural dos .tbl; render visual é teste posterior.
Aplicamos essa pose só quando no ombro, interpolação 20 ticks,
mais os movimentos de repouso CE já existentes. Retiramos `offsetZ=-12`
exclusivo de `sitting_shoulder` e aproximamos posição central para
lateral 0.30 e altura 1.30.

O filhote fora do ombro não muda. O adulto e o teleporte/chunkloader
foram confirmados pelo jogador e não foram modificados.

A verdadeira forma de cachecol em volta do pescoço (cabeça em um ombro,
cauda no outro) ainda exigirá pose/asset próprio; a CE não contém isso.

 
## V0.5.6 — pose de pescoço ilustrada pelo usuário (14/10/2026)

A ficha frontal/traseira do Auren estabelece a pose-alvo: cabeça/pescoço
sobre ombro esquerdo, corpo atrás da nuca, asas fechadas, cauda sobre o
ombro direito. V0.5.5 aparentava dormir; v0.4 girava 90 graus e ficava
torto apesar da distância confortável ao jogador.

Retomada a curvatura customizada anterior do pescoço/cauda (invertendo
o yaw do pescoço para alcançar o lado esquerdo), mas **sem rotação do
modelo inteiro** e sem keyframes de asas feitos à mão. O renderizador
usa `sitting_shoulder.tbl` da CE para corpo acordado, `sleeping.tbl`
somente para os quatro ossos principais das asas, e mantém a animação
idle original da CE sobre essa composição. A posição no pescoço volta
à da v0.4 (lateral=0.075, atrás=0.18, altura=0.76 da altura do player),
e o blend de 20 ticks continua.

Não foram alterados o Auren adulto, escalas, IA, voo, andar, teleporte
de emergência a 50 blocos nem tickets de chunk. Teste visual de frente
e costas no Zalith é obrigatório antes de aprovar aparência.


## V0.6.0 — voo autônomo real e quadros CE vinculados ao estado (14/10/2026)

O modelo da v0.5.6 já continha todos os seis `flight1..6.tbl`,
`hovering.tbl` e `diving.tbl` da Community Edition, mas nenhum controlador
de voo real. Adotamos a divisão arquitetural usada no `IafDragonFlightManager`
(estado, objetivo, movimento), **sem importar o mod IceAndFire CE completo**
e suas dependências. A Uranus já embarcada continua sendo a única dependência
de animação.

- `AurenFlightController` aplica `deltaMovement` REAL no servidor, com
  decolagem em duas fases, piloto automático em direção ao dono, verificação
  preventiva de colisão e chunks carregados, tentativa de desvio para cima,
  pouso por gravidade e cancelamento de estados transitórios.
- `AurenAdultFollowOwnerGoal` agora trabalha com forma adulta (15/8) ou
  pequena (4.5/1.7), preservando exatamente a lógica de teleporte seguro
  para >=50 blocos; a pequena não usa mais o vanilla FollowOwnerGoal
  de 12 blocos.
- Chunk ticket temporário de 100 ticks estendido ao filhote **fora do ombro**,
  enquanto dono estiver online na mesma dimensão. Estado de ombro libera
  o ticket imediatamente e desativa voo. Não há force-loading permanente.
- `AurenDragon.FLIGHT_MODE` sincronizado ao cliente permite interpolar os
  quadros CE originais por 10 ticks: `hovering` na decolagem/pouso,
  `flight1..6` durante deslocamento, `diving` durante descida acentuada.
  Não é uma animação autoral de bater asas.
- Ao recarregar o mundo, voo não persiste sem alvo; reset seguro para
  gravidade e passeio terrestre. Mudar para ombro, sentar ou teleportar
  cancela alvo/voo.
- Limitações declaradas: *não* há ainda montaria/controle de voo pelo
  jogador, combate, stamina ou pathfinder A* 3D completo do IceAndFire.
  Fuga de áreas muito fechadas requer avaliação real no Zalith.

Escopo independente: PR #15 da pose experimental v0.5.7 permanece aberto
e separado; nenhum ajuste do ombro foi combinado na v0.6.0.


### Complemento: donos em altitude diferente

A primeira revisão considerava somente distância horizontal para pouso em voo.
Isso solucionava o bebê pairando, mas falharia se o jogador estivesse vários
blocos acima/abaixo do dragão (morros/torres/cânions). A condição final
também exige proximidade vertical (<=3.5 blocos filhote; <=6.3 adulto) antes
de encerrar voo e pousar. Os limites são superiores às altitudes alvo
de cruzeiro (2.8 / 5.2) e evitam pousar no fundo da montanha.


## V0.6.1 — usuário pediu importar **o código original**, sem recriar voo

**Problema:** a v0.6.0 importava quadros CE, mas `AurenFlightController`
ainda usava direção, velocidade, desaceleração e curvas inventadas, fazendo
o Auren voar devagar apesar de o IceAndFire ter movimento testado.

**Mudança de arquitetura (isolada da pose do pescoço):**
- Novo `AurenIafDragonFlightManager.java`: porta `FlightMoveHelper` original
  de `IafDragonFlightManager` (yaw em incrementos de 4°, aceleração por
  `approach`, `speedModifier` variando até 1.8, cálculo pitch e velocidade
  angular) e a escolha original de waypoint `DragonUtils.getBlockInViewEscort`.
  Modificações apenas para classe Auren, situação dono, escala, evitar NaN e
  coordenadas/chunks/colisão seguras. Deixa explícita licença LGPL-3.0.
- `AurenDragon`: reproduz `DragonBaseEntity.switchNavigator(1/0)` usando
  `AdvancedPathNavigate(MovementType.FLYING)` da Uranus já embutida, e
  restaura o nav/MoveControl terrestre ao encerrar voo. Handler desabilita
  teleportes internos do pathfinder: só temos o fallback de 50+ blocos.
- `AurenFlightController`: **não decide mais como acelerar/curvar**, apenas
  mantém o ciclo de vida voo/decolagem/pouso/gravidade e chama o manager CE.
- `AurenAdultFollowOwnerGoal`: usa sempre `dragon.getNavigation()` atual,
  evitando ponteiro stale quando se alterna nav terrestre/aérea.
- O movimento CE de escolta originalmente tem tolerâncias/altitudes maiores;
  amostragem foi adaptada para Auren baby/adulto, com verificações de chunks
  carregados e destino com linha de visada.
- Mantidos valores de aproximação adulto 15/8, filhote 4.5/1.7, teleporte de
  emergência >50, tickets temporários, animações CE, postura de ombro v0.5.6.
- Documentar o que **não é** portado: controles de cavalgada, ataque,
  stamina, inventário e outras subclasses e registros do IAF CE.

**Limite de validação:** CI Gradle + testes estruturais confirmam que o código
compila e invoca o manager, mas **não medem velocidade real no Zalith**.
Sem declarar sucesso de velocidade até teste do usuário.


### Revisão antes do CI final — altitude e aceleração de escolta

Após o primeiro build aprovado, a auditoria de regras mostrou que aplicar
literalmente os destinos em altura de 8–24 blocos da CE impediria Auren
de pousar quando atingisse seu raio próprio (filhote 2, adulto 8).
O método original `getBlockInViewEscort` foi mantido, mas parametrizado
em altura `2..3` filhote e `4..6` adulto. A atualização de alvos
também deixa de oscilar a cada 3 blocos de movimento do dono; só muda
alvo ao se afastar ~8 blocos, atingir alvo, obstruí-lo ou após 60 ticks.
A base do movimento original (aproximação, yaw 4 graus, pitch e impulso)
permanece a mesma. Esses dois ajustes são adaptações deliberadas ao
tamanho/dinâmica do Auren, não código idêntico à CE.


## v0.6.2-alpha — preferência terrestre e estabilização vertical (14/10/2026)

**Evidência:** vídeo de jogo: voo visualmente aos saltos e decolagem imediata
ao dono se afastar. O objetivo é tratar o defeito de movimento, sem inventar
novas mecânicas.

**Causas no código:** o `AurenAdultFollowOwnerGoal.tick()` chamava
`flightController.follow()` sempre, mesmo com caminho livre no chão; isso
forçava a troca para navegação aérea. Já o port de
`IafDragonFlightManager.update()` somava +0.1 Y/tick do upstream mesmo com
`noGravity=true` durante o voo do Auren. Essa cópia literal não preservava
as premissas de física da entidade original.

**Decisão:** reutilizar o `FlightMoveHelper` e o `AdvancedPathNavigate`
CE/Uranus, mantendo navegação terrestre como primeira escolha. Após 30 ticks
de falta de progresso, ou perseguição de >=40 ticks ainda longa (>32 blocos
adulto, >14 filhote), pode decolar quando houver espaço. No ar, manter a
direção/aceleração horizontal CE e limitar somente Y com suavização por erro
de altitude do waypoint. Reproduzir os seis quadros de voo em cadência 0.2
pose/tick, a partir de `flightCycle += 2` e `currentIndex = flightCycle /10`
no código original.

**Preservação:** adulto segue 15/8, filhote 4.5/1.7; teleporte emergencial
>=50 com validação de pouso; tickets de chunk e pose de ombro intactos.
Testes automatizados verificam o código e o build, **não comprovam**
qualidade visual nem ausência de oscillação física no jogo. O teste em
Zalith continua obrigatório antes do merge.


## v0.6.3-alpha — montaria CE sem substituir voo autônomo

**Base**: commit `33452de50db3f4345c805df630fe1b1109cbe2b9` da
v0.6.2 aprovada em teste visual. Branch isolada, sem atualizar a `main`.

**Fontes portadas (LGPL-3.0-or-later, CE 1.21.1 no commit imutável
`aa2f11a6b79acb48a1094765d610cdaca79e3d18`):**
`DragonBaseEntity.mobInteract`, `getControllingPassenger`, `canAddPassenger`,
`positionRider`, `travel`, `updateRider`; `ClientEvents`,
`IafKeyMappings`, `DragonControlC2SPayload`, `NetworkManager`,
`ServerNetworkHandlers`.

**Integração:** Auren é `TamableAnimal`, não `DragonBaseEntity`, e usa
colisão menor que o modelo stage 3, portanto reaproveita os algoritmos
terrestre e de voo controlado por `moveRelative`/`MoverType.SELF` da CE,
com escala reduzida e assento sobre o dorso; não implementa o sistema
original de stamina/inventário/combate/todos estágios. A decolagem montada
possui estado separado do waypoint do piloto automático; `FlightController`
não deve chamar `update(owner)` durante a montaria. Voo manual usa
`noGravity` e arrasto isotrópico 0.9 da CE. Mapeamento compatível
teclado/controles vanilla: espaço segura/subida, X descida, Shift sai.

**Validação servidor:** payload C2S identifica a entidade mas não envia
coordenadas; só dono efetivamente montado pode controlar; máscara bit 0/1.
Não altera o teleporte emergencial original de 50 blocos ou tickets.

**Regressões a testar manualmente em Zalith**: render do assento, rotação,
movimento cliente/servidor, sustentação no ar, altura de voo, colisão,
desmontagem e gravidade, câmera terceira pessoa e controles virtuais.

## Revisão de fidelidade da montaria v0.6.3 após teste do jogador

O usuário confirmou que a primeira build 0.6.3 tinha voo radicalmente diferente
da CE, apesar de estar compilando. O método `travel` inicialmente alterava
múltiplos coeficientes, não tinha `glidingSpeedBonus`, deixava sempre FLYING
depois de 12 ticks e usava assento constante.

A implementação foi novamente confrontada com CE 1.21.1 no commit
`aa2f11a6b79acb48a1094765d610cdaca79e3d18`.
Referências: `DragonBaseEntity.travel`, `updateRider`, `updatePitch`,
`getRideHeightBase`, `getRideHorizontalBase`, `getRiderPosition`,
`IafDragonLogic.updateDragonServer`, `LightningDragonEntity` (min speed
0.15, max speed 0.4). Agora incluem coeficientes, planagem, gliding bonus,
decolagem, estados hovering/flying e matemática da posição do piloto.
O `riderAirTicks >=12 -> FLYING` simplificado foi removido.

**Diferenças que permanecem**: não há `DragonBaseEntity` completo e
`DragonTabulaModelAnimator` usa buffers de pitch/roll/cauda próprios,
que ainda não foram portados integralmente. Os controles continuam C2S
apenas up/down; dados de direção do passageiro via interface vanilla.
A compatibilidade touch, a altura do assento e a suavidade visual ainda
dependem de teste dentro do jogo, não apenas GitHub Actions.



## v0.6.4-alpha — escopo do FireDragon.riderShootFire

Foco: ataque contínuo do `FireDragonEntity`, solicitado pelo jogador; não
importar o mod inteiro, não criar sistema de combate paralelo não necessário.
Implementação em `AurenFireBreath.java` diretamente proveniente de
`FireDragonEntity.riderShootFire`, `DragonBaseEntity.rayTraceRider`,
`performNormalBreathAttack`, `getHeadPosition`, e
`IafDragonDestructionManager.destroyAreaBreath` no commit CE
`aa2f11a6b79acb48a1094765d610cdaca79e3d18` (LGPL-3.0-or-later).

Port necessário porque `AurenDragon extends TamableAnimal`, não
`DragonBaseEntity`; métodos com `DragonBaseEntity` e registries Iaf não
podem ser chamados diretamente sem importar toda árvore de dependências.
Estado/progresso separados do voo autônomo. A entrada usa bit CE
`CONTROL_STATE.strike=8`, registro R original, encaminhado no pacote
existente com validação de dono montado; mira é calculada no servidor
a partir do jogador. Sem pacotes novos, entidades de projétil ou
dependências extras.

Desvios limitados e explícitos: `ParticleTypes.FLAME`/`SoundEvents.BLAZE_SHOOT`
para assets não importados, sem `FireDragonChargeEntity`, sem blocos especiais
carbonizados/Dragon Forge; terrenos somente incendiáveis com mobGriefing.
O dano da área `stage*attackDamageFire = 3*2=6` tem janela de 5 ticks,
para reduzir risco de múltiplos hits em Zalith. Protege dono, montaria,
domesticados do mesmo dono, aliados e chunks não carregados.

O cálculo de `getHeadPosition` foi portado, mas adaptado à escala fixa
3.25 de renderização do Auren. Necessário teste de alinhamento visual
em jogo; não afirmar equivalência completa de geometria/partículas/sons.
Voo autônomo v0.6.2, voo montado v0.6.3, ombro e teleporte intactos.
