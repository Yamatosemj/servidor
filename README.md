# Auren Minecraft 0.7.16 — Caçada implacável (protótipo escalonado)

**Inspirado em:** Ice and Fire CE `DragonBaseEntity.digWhenStuck/breakBlocks` (interação com obstáculos), e em mods de perseguição como Nightmare/Epic Siege, mas com controle de dano por estágio e guardas de servidor. Não importamos a IA deles integralmente.

1. **Alvo visível:** preserva TACKLE/HOVER_BLAST/SCORCH_STREAM da 0.7.15.
2. **Alvo se esconde:** primeiro procura uma posição aérea real com linha de visão. Se continuar encoberto por 65 ticks, tenta manter o **sopro azul em chamas** sobre o obstáculo que bloqueia o raio, incendiando madeira e acumulando calor sobre a rocha.
3. **Cavernas:** após 110 ticks escondido, se o alvo estiver coberto por pelo menos 6 blocos de terreno e houver condições de aproximação, o Auren pousa, diminui temporariamente para a forma pequena **somente em solo firme** e testa um caminho terrestre com `Path.canReach()`. Ele segue a vítima e morde dentro da passagem. Sem rota, não finge que atravessa paredes; procura outra alternativa. A forma normal é restaurada quando cabe em um espaço carregado sem colisão, mesmo após cancelar a ordem ou recarregar um mundo salvo.
4. **Montanha impenetrável:** só após pelo menos 260 ticks sem visão, **sem nenhum ponto aéreo com linha de visão**, com caverna sem acesso, `mobGriefing=true` e raio de fogo até 27 blocos, tenta uma pequena explosão de fogo na parede. Só rocha natural/magma/cobblestone/carbonizados, sem madeira, minerais, baús, blocos com dados ou aliados a até 5 blocos, e dono a pelo menos 8 blocos. São no máximo 5 cargas espaçadas por 95 ticks, nunca uma chuva instantânea de explosões.
5. **Sem teleporte/cargas em chunks descarregados:** precisa de dono online, alvo vivo, dimensão compartilhada e ordem marcada. Mantém as guardas de alcance já existentes. `Shift+V` cancela a caça.

**Proteções mantidas:** a rajada normal da 0.7.15 não explode e o bombardeio B não usa o novo ataque explosivo. O novo comportamento só existe para **criatura marcada inalcançável**. A transformação temporária não equivale a crescimento real; o sistema continua adaptado, não é o `DragonBaseEntity` completo.

**Testes de jogo imprescindíveis:** presa em casa de madeira, caverna com entrada acessível de 1 bloco, caverna fechada, montanha de pedra, jogador/dono perto da parede, `/gamerule mobGriefing false`, cancelar quando pequeno, salvar/recarregar enquanto pequeno. Testes CI são estruturais, não validam navegação real.

---

# Auren Minecraft 0.7.15 — Rajada de FOGO com rastros irregulares e calor progressivo

- **Fogo primeiro:** o sopro azul de chamas ficou mais denso, turbulento e volumoso; há um reforço de partículas flamejantes no impacto, além de vários **blocos de fogo reais** no terreno onde puderem permanecer. Superfícies já carbonizadas podem manter fogo azul (soul fire); outras superfícies usam fogo vanilla.
- **Nada de quadrado 4×4:** cada impacto gera uma mancha oval de aproximadamente 5–7 blocos com pequenas variações de centro/raios, probabilidade maior de atingir o meio e bordas com lacunas. O formato varia entre rajadas.
- **Rastro em movimento:** impactos consecutivos e próximos são conectados por pequenos trechos, desenhando uma faixa de fogo quando o Auren faz uma passada aérea.
- **Calor progressivo:** pedra/cobblestone → pedra escurecida → magma após repetidas rajadas → pode desaparecer somente após aquecimento bem mais prolongado; grama → solo queimado → pode desaparecer com calor extremo. Não há explosão nem destruição instantânea do terreno.
- **Madeira:** só pega fogo vanilla e queima naturalmente. Nenhuma rotina de dano térmico quebra madeira/portas/baús diretamente.
- **Segurança:** alterações requerem `mobGriefing=true`, respeitam o raio de bombardeio marcado, não carregam chunks distantes nem modificam entidades de bloco. O calor é limitado em memória, esfria/expira e não persiste após reiniciar o mundo. O espalhamento natural do fogo depende da regra `doFireTick`.
- **Preservado:** IA/combate corrigido da 0.7.14, teclas V/Shift+V/B, o controlador de voo e as animações. A direção e o dano do sopro continuam decididos pelo servidor.
- **Validação:** compilação e testes estáticos não substituem teste visual ou de destruição gradual no Minecraft.

---

# Auren Minecraft 0.7.14 — Correção da auditoria de combate

- **Correção prioritária 1:** caminhada aleatória desativada sempre que há uma ordem de ataque ou voo ativo; não disputa navegação com o voo de combate.
- **Correção prioritária 2:** o Auren nunca tenta uma investida contra um waypoint rejeitado pela colisão. Busca aproximação livre e recua para pontos seguros caso a criatura esteja atrás de paredes.
- **Recuperação:** sem visão da criatura, procura uma posição válida no ar. Mantém o ponto de recuperação por um curto intervalo para não recalcular/oscilar a cada tick; só pousa quando não acha espaço seguro.
- **Mordida:** exige sobreposição próxima das caixas de colisão, não apenas distância entre centros.
- **Marcação:** criaturas/centros de bombardeio precisam estar a até 64 blocos do Auren, coerente com o limite que já existia durante execução da ordem. Mensagens explicam falhas por alcance.
- **Transições:** após uma investida acertada, reavalia a próxima tática no fim do recuo, sem alterar o controlador original de voo.
- **Preservação:** não alteramos `AurenFireBreath`, `AurenScorchTerrain`, animação de cabeça/voo, teclas, marcação por V, montaria nem o comportamento do bombardeio já aprovado.
- **Limites:** testes automatizados de compilação, asserts e presença do JAR não substituem testes visuais em jogo. O adaptador ainda não é a IA completa da `DragonBaseEntity` do Ice and Fire CE.

---

# Auren Minecraft 0.7.13 — inteligência de combate a criaturas

## Melhorias focadas exclusivamente em alvos marcados

- **Combate com os três padrões CE:** `TACKLE`, `HOVER_BLAST` e `SCORCH_STREAM` continuam sendo os tipos do Ice and Fire. A escolha agora depende da distância/visibilidade e do ataque anterior, sem sorteio aleatório de manobras durante uma luta.
- **Posição de disparo:** `HOVER_BLAST` seleciona pontos no ar com linha de visão livre até a criatura (checagem server-side), evitando posições atrás de uma parede ou telhado. Só revisa o ponto com motivo ou intervalo limitado.
- **Investida inteligente:** `TACKLE` prevê o movimento horizontal recente do inimigo em vez de seguir exclusivamente as coordenadas antigas, limitado a 5 blocos de antecipação.
- **Passadas CE:** `SCORCH_STREAM` preserva a linha entre atacante e presa definida no começo da manobra e inverte o percurso só ao chegar ao outro lado.
- **Recuperação:** se o dragão perder de vista a presa durante uma investida, muda para busca aérea. Se um destino não for seguro, tenta reposicionar antes de aterrissar.
- **Sem regressão pretendida:** bombardeio de área, incêndio das construções, sopro sem ângulo máximo, animação de baixar a cabeça, V, Shift+V, B e montaria não foram alterados.
- **Limitação:** usa o controlador de voo original do Ice and Fire CE e suas modalidades, mas a IA ainda é um adaptador para `TamableAnimal` — não reproduz todos os sistemas de `DragonBaseEntity`. Compilação não valida o comportamento visual em jogo; teste antes de mergear.

---

# Auren Minecraft 0.7.12 — incêndio, não demolição

## Correções após o teste da 0.7.11

- **Nenhuma rajada de fogo quebra blocos diretamente.** Removida completamente a rotina de demolição `bombard` / `level.destroyBlock` introduzida na 0.7.10. A carbonização CE de terreno continua como efeito visual temporário, sem romper blocos.
- **Sopro autônomo sem qualquer limitação de ângulo**, como pedido. O alvo continua definido exclusivamente pelo servidor; não usamos mais a regra de 35° nem a de 55°.
- **A cabeça realmente baixa durante o sopro:** usa os quadros originais do Ice and Fire CE `attack_stream_charge*` e `attack_stream_breath`, com inclinação suave adicional nos ossos Neck1, Neck2, Neck3 e Head. A posição do bocal do fogo também baixa para acompanhar a pose.
- **Incêndio mais forte:** cria até 16 focos de fogo comum perto de cada impacto, tentando incendiar telhados de escadas e lajes de madeira, cercas, portas, troncos, tábuas, folhas, lã e feno. Rajada visual ainda **azul**; incêndio que se espalha é **laranja**, por ser o comportamento vanilla de madeira. Fogo montado e combate a criatura também podem iniciar incêndios em material inflamável.
- **Voo e controles:** conserva as passadas coordenadas, HUD discreto e marcação por V/Shift+V/B. **Sem novas teclas.**
- **Segurança:** `mobGriefing=false` impede novas ignições/alterações do terreno pelo mod. `doFireTick=true` permite a propagação/consumo vanilla. **O fogo vanilla pode se espalhar naturalmente além do círculo inicialmente marcado** e queimar construções, baús e materiais inflamáveis. Testar apenas em mundo descartável ou cópia de segurança.
- **QA:** compilação automática checa invariantes e ausência de demolição direta; ainda requer teste visual do movimento do pescoço e da continuidade do fogo dentro do Minecraft.

---

## Histórico de versões (as descrições abaixo não substituem o comportamento da 0.7.12)

# Auren Minecraft 0.7.11 — rajada contínua e bombardeio coordenado

## Correção do teste 0.7.10

- **Passadas aéreas coordenadas:** aproximação pela borda, voo em direção ao outro lado do raio e reposicionamento. O plano de voo não é alterado aleatoriamente no meio da rajada; o FlightMoveHelper do Ice and Fire CE continua responsável por mover o dragão.
- **Rajada azul prolongada:** alvo acompanha a direção do dragão, varrendo telhados à frente; 180 ticks máximos por passada (aproximadamente 9 segundos a 20 TPS, não garantia de sopro contínuo se houver obstáculos). O jato conserva o aquecimento durante falhas breves de mira e mantém proteção contra disparos para trás: cone de 55° frontal em vez de 35°. Pluma visual um pouco mais volumétrica.
- **Destruição real de construções e solo:** ampliação para blocos mineráveis com ferramentas, limite de até 20 blocos quebrados por impacto, apenas dentro da área e respeitando `mobGriefing`. Acende **fogo comum** em madeira/lã/feno e folhas próximos ao impacto — chamas do sopro continuam azuis, mas o fogo que se propaga pela casa é o fogo vanilla (laranja), porque soul fire não pega em madeira.
- **Segurança:** não destrói blocos com block entities, imunes (dureza negativa), dureza acima de 8, nem chunks descarregados. Proteção do dono e pets, controles e ataques montados preservados.
- **Importante:** destruição permanente de qualquer construção dentro do círculo, inclusive casas próprias. Testar somente em mundo descartável/backup, com `mobGriefing true` e `doFireTick true` para propagação vanilla. Nenhum teste de build substitui playtest de voo, ataque e incêndio. PR empilhada sobre a 0.7.10.

---

# Auren Minecraft 0.7.10 — fogo direcional e bombardeio destrutivo

- **Fogo autônomo:** respeita um arco horizontal de 35° à frente da cabeça (sem soprar para as costas). Auren carrega o sopro enquanto vira. Os controles do fogo montado (R) continuam como antes.
- **Bombardeio (B + V):** agora mira o topo de construções à frente, não só um Y fixo no chão. Dentro do raio escolhido (5, 10, 15, 20, 25 ou 30), pode destruir até 14 blocos de madeira, vidro, vegetação, feno, lã e alvenaria por impacto. Mantém partículas azuis e solo carbonizado.
- **Combate contra criaturas:** decide entre investidas, sopro à distância e passadas com fogo conforme distância e linha de visão, revendo escolhas mais rapidamente. Ainda é uma adaptação do CE, **não é a IA integral de DragonBaseEntity**.
- **Segurança e perdas de mundo:** destruição de construções é **permanente**, inclusive as suas; teste num mundo descartável ou backup. `/gamerule mobGriefing false` desativa alterações no terreno. O bombardeio ignora chunks não carregados, blocos com inventário e indestrutíveis; não usa explosões, não gera drops por blocos quebrados.
- **Teste:** compilação automática não garante que manobras e mira fiquem perfeitas dentro do jogo. A versão está numa PR separada, em cima da 0.7.9; não mesclar sem playtest.

---

# Auren Minecraft 0.7.9 — combate tático e interface discreta

- **V:** marca qualquer criatura viva válida sob a mira (inclusive animais e jogadores em sobrevivência), ou uma área de bombardeio quando o modo aéreo está ligado
- **Shift+V:** cancela uma ordem existente. Sem ordem, percorre os raios 5/10/15/20/25/30
- **B:** ativa/desativa o **bombardeio aéreo**; quando desligado, cancela a ordem de área, mas a marcação de criaturas continua funcionando
- **HUD:** só texto azul no topo durante uma ordem. Sem ordem desaparece; dicas e marcadores aparecem por ~4 segundos após V/B
- **Combate a criaturas:** padrões do Ice and Fire CE `TACKLE` (investida/mordida), `HOVER_BLAST` (fogo aéreo à distância) e `SCORCH_STREAM` (passadas com fogo), trocados periodicamente. Não é IA idêntica à entidade CE: Auren é TamableAnimal com IA própria, mas mantém o controlador de voo CE portado
- **Bombardeio:** cinco trechos alternando curvas, passadas cruzando o centro, varridas baixas e subida para reposicionar; não fica preso a uma órbita circular. Fogo mira apenas dentro do raio
- **Investida em criaturas:** após uma mordida acertada, Auren tenta se afastar e preparar a próxima investida, mantendo o voo pelo controlador CE

**Segurança:** só o dono marca; posições e entidades não vêm do cliente; dono, Auren, aliados, animais do dono e jogadores invulneráveis são protegidos. As ordens pausam quando necessário por distância/chunks. O modo do botão B persiste no mundo; o raio do HUD volta a 5 após reiniciar o cliente.

**Versão experimental:** teste num mundo com backup. Com `mobGriefing true`, o fogo pode carbonizar terreno de construções. Compilação e testes estáticos NÃO substituem playtest do voo, marcação e animações.

---

# Auren Minecraft 0.7.8 — Controle por V e alvo visual

- **V**: marque o inimigo hostil na mira ou o bloco como centro de ataque; substitui marca anterior.
- **Shift+V** enquanto há ordem ativa: cancela o ataque e remove o alvo.
- **Shift+V** sem ordem ativa: alterna raio 5 → 10 → 15 → 20 → 25 → 30 → 5.
- V pode ser remapeado em Controles do Minecraft.

**Preview (PC):** inimigo recebe contorno azul; região recebe círculo horizontal
no raio escolhido, cruz central e segundo círculo ~9 blocos acima mostrando a
órbita prevista. Marcação confirmada é sincronizada pelo servidor e persiste
ao desviar o olhar; HUD mostra raio e ação atual.

Marcação de alvo/raycast é **validada pelo servidor**, sem coordenadas ou IDs
de entidades enviados pelo cliente. Reaproveita voo/ataques 0.7.7, mantém
comandos antigos como fallback (ex.: mobile), não cria entidades extras.
A ordem sobrevive a recarregar o mundo, mas o raio local volta a 5 ao
reiniciar o cliente. A prévia desenha uma geometria ideal: obstáculos podem
mudar a rota real do dragão.

**Antes de testar**, faça backup do mundo; teste os seis raios, Shift+V,
inimigo, obstáculos e reload. O voo e efeito do fogo requerem playtest.
Com mobGriefing true, solo de construções pode ficar carbonizado.

---

# Auren Minecraft 0.7.7 — marcação de alvos e bombardeio autônomo

## Marcar o que atacar (sem montar)

Com seu **Auren adulto, domesticado e a até 64 blocos de você**, mire
diretamente no alvo e use o comando de chat:

- `/auren atacar inimigo` — mire em um monstro hostil visível a até 48 blocos.
  O Auren persegue o alvo pelo ar com sopro azul, alternando voos circulares
  superiores (*Hover Blast*) com investidas de passagem (*Scorch Stream*).
  São adaptações das **estratégias de ataque** da CE, usando o **mesmo controlador
  de física de voo do Auren**, não a entidade/IA de combate integral da CE.
  Quando o monstro morre, a ordem é removida; se descarregar, aguarda.
- `/auren atacar area 5` (ou `10`, `15`, `20`, `25`, `30`) —
  mire em um **bloco** a até 48 blocos. O valor é o **raio horizontal** em blocos
  a partir do centro marcado. Auren tenta voar em círculo nesse raio, cerca
  de 9 blocos acima do centro, e distribui o sopro entre o centro e a área.
- `/auren status` — mostra a ordem atual.
- `/auren parar` — cancela a marcação e encerra o bombardeio.

Uma marcação nova substitui a anterior. Não requer item extra, GUI ou
comunicação de coordenadas do cliente: o servidor faz a seleção a partir
da direção em que o jogador está olhando. **Não há limite de tempo no modo
área**: a ordem sobrevive a salvar/reabrir o mundo e só é desmarcada pelo
comando. Os ataques, porém, **pausam** quando o dono sai, está em outra
dimensão, monta, transforma o dragão em filhote ou quando a área/chunks estão
indisponíveis; não força o carregamento de áreas remotas. O monstro marcado
é automaticamente desmarcado quando morre.

O modo automático continua usando o sopro azul da v0.7.6, com preparação,
alcance máximo de 30 blocos da cabeça e proteção de dono/animais aliados.
O voo é controlado pela versão existente de `FlightMoveHelper` da CE;
as trajetórias orbitais são novas e **precisam ser observadas dentro do jogo**.
O raio controla movimento e limite de dano/terreno, não aumenta alcance
do fogo ou o raio original de explosão do sopro.

**Cuidado:** com `mobGriefing true`, blocos de pedra, terra ou pedregulho
colocados por você também podem ficar carbonizados, inclusive dentro do
círculo. Use `/gamerule mobGriefing false` para impedir mudanças em blocos
(sem impedir o dano do sopro nas criaturas). Faça **backup do mundo** antes
de testar uma versão experimental.

## Playtest necessário

1. Testar cada raio (5, 10, 15, 20, 25, 30) num mundo de backup e verificar
   se o voo mantém uma órbita em áreas planas e com obstáculos.
2. Verificar se o sopro percorre o interior do círculo, não atravessa paredes,
   respeita raio, alcance e `mobGriefing`.
3. Marcar um zumbi, observar perseguição aérea e cancelamento após morte.
4. Usar `/auren parar` durante o ataque, testar troca de alvo, sair e voltar
   ao mundo, trocar de dimensão, montar/desmontar, ombro e forma filhote.
5. Confirmar desempenho no Zalith/Android e multiplayer. Compilação e
   verificações estáticas **não** substituem teste de jogo.

---

# Auren Minecraft 0.7.6 — proteção do dono, mordida filhote e terreno queimado

## Novidades

- **Auren protege o dono.** Quando o dono ataca uma criatura, o Auren
  corre atrás e morde; também pode defender o dono quando este é atacado.
  A implementação usa objetivos `OwnerHurtTargetGoal`,
  `OwnerHurtByTargetGoal` e `MeleeAttackGoal` da IA vanilla de tameables.
  Depois de lutar, volta ao comportamento anterior de seguir.
- **O filhote agora ataca** enquanto está no chão, com mordida de 3 pontos
  de dano. O adulto morde com 7. As três animações de mordida vêm dos
  modelos originais Lightning Dragon do CE, com hashes do Git fixados.
  No ombro não ataca (forma ombro não tem IA, para não machucar o dono).
- **O chão fica carbonizado como no Ice and Fire** ao receber fogo:
  grama, terra, cascalho, pedra, pedregulho e caminho de terra usam
  **texturas e modelos ORIGINAIS da CE** com namespace Auren.
  São seis blocos de estado de terreno, **SEM BlockItems**, sem receitas,
  sem loot nem abas criativas. No mundo de teste, as marcas somem aos
  poucos por tick aleatório e os blocos naturais reaparecem.
- Respeita `mobGriefing`: se falso, nenhum bloco do mundo é modificado.
  Só transforma tipos de blocos permitidos; não altera diretamente baús,
  plantações, blocos com inventário ou regiões descarregadas. **Atenção:**
  pedra, terra e pedregulho colocados pelo jogador também podem queimar:
  o jogo não distingue esses blocos dos gerados naturalmente.
  O fogo azul pode existir sobre o terreno carbonizado por uma tag de
  suporte do soul fire.
- **R** permanece sopro azul, **G** permanece raio; montaria, voo,
  pescoço, ombro e hitbox mantidos intactos.

### Avisos para seus mundos

**Faça backup:** esta versão introduz seis tipos de blocos novos no save.
Evite abrir um mundo modificado pela 0.7.6 em versões anteriores do Auren
sem um backup. O CE inteiro não está incluso. O relevo não é quebrado por
explosões, mas blocos permitidos podem mudar temporariamente, inclusive
em construções. Para não alterar blocos, use `/gamerule mobGriefing false`.

### O que testar

1. Atingir um zumbi com espada e observar Auren adulto ir mordê-lo.
2. Transformar em filhote, deixar no chão e atacar um zumbi:
   ele deve morder e mostrar animação. Com filhote no ombro, não atacar.
3. Ativar `/gamerule mobGriefing true`, montar, mirar o sopro R no chão.
   Terra/pedra/grama precisam escurecer com textura original, podendo
   ficar com soul fire azul. Em `mobGriefing false` não modifica o chão.
4. Testar se R e G ainda funcionam, voo/montaria/seguir preservados.

**Atenção:** somente CI compilação e verificações estáticas disponíveis;
IA, decaimento do chão e modelo em multiplayer requerem playtest real.

---

# Auren Minecraft 0.7.5 — Fogo azul volumétrico e animação original de ataque

## O que mudou

- **R = fogo azul**: efeito usa as partículas originais do Minecraft
  `SOUL_FIRE_FLAME` em um jato largo, animado e turbulento que cresce ao
  longo da trajetória, em vez de uma fileira estreita de pontos (laser).
- **Abertura da boca/ataque**: acrescentadas quatro poses 3D *originais do
  IceAndFire CE Lightning Dragon*:
  `attack_stream_charge1`, `charge2`, `charge3` e `attack_stream_breath`.
  As poses vêm dos arquivos Tabula originais SHA-validados; a animação é
  sincronizada no servidor e aplicada à cabeça, pescoço e torso, preservando
  as asas e o cavaleiro.
- **G = raios** continua funcionando, sem alterar danos e efeito elétrico.
  Enquanto o Auren prepara e solta raios, a mesma animação abre a boca.
- **Dano, alcance, tempo de preparo, proteção do dono e colisão do sopro
  de fogo estão preservados** do 0.7.4. O novo cone é VISUAL; não inventa
  uma área de dano maior que o raio já validado.
- Quando o `mobGriefing` permite, só coloca **soul fire azul** em
  superfícies onde o Minecraft aceita esse bloco (soul soil/soul sand).
  Em terra comum o jato continua azul, mas não deixa uma chama vermelha
  persistente. Alvos vivos continuam sofrendo o efeito vanilla de queimar.
- Mantém **um único Auren** e Uranus incluído no JAR. Não instala IceAndFire
  CE, Jupiter, itens, monstros nem projéteis adicionais.

## Validação manual em Minecraft 1.21.1

1. Em mundo de teste, substitua somente o JAR antigo pelo
   `aurenminecraft-0.7.6-defend-scorch-alpha.jar`.
2. Monte no Auren, segure **R** por 3 segundos e olhe-o de lado em
   terceira pessoa. Verifique se mandíbula/pescoço realmente animam.
3. Veja se as chamas ficam **azuis e largas**, sem parecer um laser.
4. Segure **G**: o raio anterior deve continuar funcionando com a
   animação de ataque. Solte as teclas: o dragão deve fechar a boca.
5. Confirme fogo/dano em um zumbi, proteção do dono, montaria,
   voo, pouso e transformação em filhote. Teste com backup.

**Limite:** testes automatizados validam assets, código e compilação, mas
não provam que o encaixe visual em todos os ângulos esteja perfeito.
As poses são originais da CE; a seleção/sincronização foi adaptada ao
único Auren porque ele não herda `DragonBaseEntity`.

## Histórico anterior

# Auren Minecraft 0.7.4 — Lightning Dragon CE, ataque controlado

## Novo: raios montado, tecla G

- **R:** sopro de fogo existente (preservado).
- **G:** mantenha pressionado para carregar por 20 ticks (~1 s) e lançar
  o feixe de raios contínuo do Lightning Dragon CE. É necessário estar
  montado na forma adulta domesticada.
- O feixe vai do focinho na direção em que o piloto olha (até 30 blocos).
- Dano original de estágio 3, usando a configuração padrão CE para raio
  (3 x 3.5 = 10.5), área de 3.5 blocos, empurrão original de 0.3.
  O impacto é limitado a uma aplicação a cada 5 ticks para não saturar.
- O visual usa **os próprios LightningBoltData e LightningRenderer
  originais** do IceAndFire CE (LGPL-3.0-or-later, atribuição a
  IAFEnvoy e aidancbrady). Não usa uma linha artificial de partículas.
- O ataque funciona só no servidor do dono montado, sem coordenadas
  enviadas pelo cliente, não acerta o piloto nem seus animais domesticados
  e para diante de blocos/chunks não carregados.
- **Se R e G forem pressionados juntos, G tem prioridade.**
- O feixe contínuo original foi portado; a variante aleatória de
  **projétil elétrico** do CE NÃO foi incluída porque exigiria registrar
  uma segunda entidade. Não importamos itens, mobs, blocos, estruturas,
  Jupiter nem o mod IceAndFire inteiro.

### Teste desta etapa

Em um mundo de backup com somente `aurenminecraft-0.7.6-defend-scorch-alpha.jar`:
domestique e monte, segure G para eletricidade contra um zumbi, solte G
e verifique parada imediata. Teste a direção do olhar, bloco no caminho,
animal domesticado ao lado, uso de R antes/depois de G, voo autônomo após
desmontar e câmera de terceira pessoa. Também configure G no Zalith, se
for usar Android. **O CI valida código e compilação, não visual no jogo.**

## Histórico anterior

# Auren Minecraft — 0.7.3 montaria alinhada ao corpo e controles de dragão

## Correções baseadas no teste real da 0.7.2

1. **Cavaleiro acompanha a ANIMAÇÃO do corpo.** Portamos exclusivamente o
   `DragonRiderFeatureRenderer` original do IceAndFire CE, ancorando o
   jogador aos ossos `BodyUpper` e `Neck1` do próprio Auren. O render do
   jogador solto é cancelado enquanto montado, evitando um avatar rígido
   duplicado. A câmera de primeira pessoa fica independente.
2. **Montaria sem ré ou voo lateral.** Enquanto montado, W avança,
   S interrompe a propulsão para trás e A/D não produzem deslizamento.
   A orientação continua controlada pela direção do olhar. Pular/X/R
   mantêm subida, descida e ataque de fogo.
3. **Colisão adulta proporcional à escala visual.** A referência original
   registra 0.78 largura, 1.2 altura e escala conforme o crescimento.
   O Auren adulto usa 3.25x no renderer: 2.535 blocos de largura e
   3.90 de altura. A forma filhote permanece 0.5x0.7. A hitbox cobre
   o corpo central, NÃO as pontas das asas.
4. **Inclinação sincronizada.** Ao montar, o ângulo de pitch corporal é
   transmitido aos clientes para que modelo e cavaleiro não divirjam
   quando o dragão sobe ou desce.

Tudo isso continua em **um único mod Auren**; sem itens, entidades,
Jupiter nem mod IceAndFire original instalável. Somente Uranus aninhado.

**CI não demonstra visualmente o encaixe.** Antes de mesclar, testar a
terceira pessoa olhando pela lateral, de cima e enquanto voa; primeira
pessoa e câmera; W/S/A/D; forma pequena e ombro; locais estreitos;
decolagem e pouso; mundo de backup.

## Histórico

# Etapa 0.7.2 — movimento CE no chão

## Etapa 1A — caminhada original do IceAndFire CE portada para o único Auren

Novo `AurenCeGroundMoveControl` a partir do `IafDragonFlightManager.GroundMoveHelper`
original CE 2.1.4 (commit `aa2f11a6b79acb48a1094765d610cdaca79e3d18`).
Mantém os ramos de aceleração/strafe, curvas pelo alvo, consulta do
caminho, pulo, estado JUMPING e espera do controlador original.

Única adaptação de porte: o CE calcula curvas mais lentas para dragões
crescidos (no original por idade e hitbox); Auren usa forma adulta
fixa equivalente a 50 dias (11 graus/tick), enquanto a forma pequena
usa o limite de 70 graus/tick original para criaturas pequenas.
É uma escolha explícita e verificável, não uma alegação de física idêntica.

Integração: constrói o controlador na entidade atual, preserva sua
instância durante voo e restaura após pouso. Não interfere em montaria,
ombro, IDs de save, nem introduz qualquer mod/item extra do CE.
Testes de compilação e checagens de fonte/bytes garantem o escopo.
**É a primeira parte da migração CE, NÃO uma migração integral de voo, IA
e combate**; só a caminhada foi portada nesta etapa.

### Teste manual necessário

Em mundo de backup: adulto seguindo com curvas nas ruas, bebê seguindo
em terreno plano, caminho com degraus, monte e desmonte, decolagem
autônoma e pouso, passagem ombro/solto e reload do mundo. Verifique se
o controlador do solo é restaurado após aterrissar.

---
# Auren Minecraft — 0.7.1 SINGLE-DRAGON EXPERIMENT (Minecraft 1.21.1 / NeoForge)

**A versão experimental 0.7.0 anterior tinha um erro de escopo**:
ela instalava o mod IceAndFire CE inteiro junto do Auren para comparar
comportamentos. Isso adicionou TODOS os itens, monstros, dragões, estruturas
e abas do IceAndFire, contrariando o objetivo.

## Este build contém SOMENTE o Auren

- **Um mod JAR para instalar: `aurenminecraft-0.7.6-defend-scorch-alpha.jar`.**
- Registra **exatamente um tipo de criatura**, `aurenminecraft:auren`.
- **Nenhum mod IceAndFire**, nenhuma aba/receita/item/criatura extra do CE.
- **Uranus 3.0-beta.1** incluído dentro do JAR como dependência da
  renderização Tabula. Jupiter **não é necessário** neste protótipo.
- O Auren preserva os controles já existentes: alternar entre grande
  e pequeno com agachar + botão direito, subir no ombro, montar,
  seguir o dono, voo independente e sopro de fogo ao montar.
- O repositório **mantém o código-fonte completo CE como submódulo SOMENTE
  PARA REFERÊNCIA DE DESENVOLVIMENTO**; ele NÃO é compilado, registrado
  nem incluído no JAR que você instala.

## Estado REAL (não confundir com paridade)

**Este é um retorno seguro à única entidade Auren anterior**, com os
mecanismos de voo/montaria parcialmente *adaptados* do IceAndFire.
Ainda **NÃO foi integrado todo o motor nativo `DragonBaseEntity`**,
logo voo, ataques, AI, stamina e animações NÃO são garantidamente
idênticos aos dragões originais. O próximo trabalho é portar
progressivamente os componentes originais necessários para este UM dragão,
com licença LGPL, sem carregar o mod completo.

### Teste

Use um mundo de backup. Retire da pasta `mods/` os JARs experimentais
anteriores `iceandfire-*.jar` e `aurenminecraft-*.jar`, além de duplicatas
de `jupiter` ou `uranus`, antes de colocar **somente** o JAR deste build.
A remoção do IceAndFire CE pode fazer desaparecer dados de criaturas,
itens/blocos CE em mundos criados com a versão 0.7.0 anterior; faça backup
e prefira mundo de teste. A entidade antiga Auren mantém o mesmo registro.

### Para desenvolver os comportamentos originais sem conteúdo extra

```sh
git submodule update --init --recursive
```

`vendor/iceandfire-ce` contém o código original 2.1.4 para auditoria,
mas somente `src/main/java/br/com/auren/minecraft` é compilado no Auren.

**Não mesclar até playtest.**
