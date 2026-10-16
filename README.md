# Auren Minecraft 0.7.27 — Rotinas visíveis e verificáveis

**Motivo da atualização:** o usuário não percebeu mudanças em gameplay após a 0.7.26. A política de utilidade mudou o ranking mas oferecia quase as mesmas atividades visuais; na prática `EXPLORE` podia falhar a escolha de altura/piso e cair para `OBSERVE`, que só mexia a cabeça. Isso não é teste satisfatório de autonomia.

### Outra causa objetiva encontrada
O `GoalSelector` não consulta `canUse()` em todos os ticks. A implementação antiga diminuía `cooldownTicks` cada vez que era consultada, **alongando as pausas entre rotinas** e fazendo o Auren parecer imóvel por períodos maiores que os previstos. Corrigimos usando tempo absoluto `dragon.tickCount`, mantendo pausas reais de 100–230 ticks.

### Correções pontuais
1. **Ação nova `APPROACH_OWNER`:** quando descansando perto do dono mas a mais de ~3,6 blocos na forma grande (2,5 na pequena), o Auren pode se aproximar voluntariamente por uma rota A* para um piso seguro **ao lado do dono**, sem entrar no hitbox dele, sem voar e sem atrapalhar combate/montaria.
2. **Descanso diurno mais perceptível:** a pontuação do descanso foi recalibrada. Quando explorar não é possível, descansar/sentar passa a ser uma alternativa real, em vez de sempre cair para observar invisivelmente.
3. **Caminho mais confiável:** tenta primeiro altura do dono, depois até ±2 blocos; verifica chão sólido, fluido, colisão, chunks e `Path.canReach()`. Limites: 12 amostras horizontais e **6 cálculos A*** por escolha. Se inviável, tenta próxima intenção segura; não força teleporte, destruição ou voo.
4. **Diagnóstico de verdade:** `/auren mente` agora mostra **rotina executada**, total de rotinas iniciadas na sessão, tentativas de caminho inviáveis, motivo que impede rotina, e última interrupção, além das necessidades simuladas e da última intenção. Esses contadores de diagnóstico são transitórios; energia/curiosidade/alerta e última escolha continuam persistentes no NBT.
5. **Sem IA externa** nem funções fora de rotina. O mod continua usando o controlador original CE para voo, ataque, formatação, e as defesas/ordens têm prioridade.

### Como testar no jogo
1. Instale **somente um arquivo Auren JAR**: substitua o anterior, não deixe 0.7.26 junto.
2. Deixe o Auren adulto livre, no **chão**, com você a aproximadamente 5–8 blocos. Fique parado por 60–90 segundos e execute `/auren mente` algumas vezes. A saída deve mostrar rotina, iniciadas e se houve caminhos inviáveis.
3. Observe aproximação, exploração e pose sentada diurna. Se não acontecer, o comando deve explicar por quê (**dono distante**, **sentado por ordem**, **voando**, **em combate**, **dono movendo**, **sem rota**, etc.).
4. `/time set night`: o descanso noturno deve ser mais provável. Se o dono sair, atacar ou montar, a rotina interrompe.
5. Repita o teste em uma construção e num campo plano: resultados diferentes permitem identificar falhas reais de navegação.
6. Marque criaturas, ataque de fogo/raio, monte, abra a caverna: sistemas antigos preservados.

### Estado
**Não atestamos ainda mudança visual em gameplay**; os testes automatizados comprovam o algoritmo, os limites e a compilação. A PR é draft empilhada sobre a 0.7.26.

---

# Auren Minecraft 0.7.26 — Cérebro local offline-first

> **Roadmap permanente:** [docs/ROADMAP_AUREN_AUTONOMO.md](docs/ROADMAP_AUREN_AUTONOMO.md). Esta é a **etapa 1**, não a conclusão de toda a autonomia.

**Regra principal:** o Auren deve poder viver, acompanhar, voar, se defender e seguir rotinas **sem DeepSeek, modelos de linguagem, conexão com internet ou aplicativo externo**.

### Esta versão começa a construir o cérebro autônomo
- **Nova política local em Java puro:** cinco intenções de rotina (observar, explorar, investigar animais, descansar e buscar abrigo). A escolha é pontuada por energia, curiosidade, atenção a perigos, dia/noite, chuva e última decisão, com pequenas variações reproduzíveis.
- **Memória de necessidades simuladas:** energia/curiosidade/alerta de 0 a 100; atualizações leves uma vez por segundo no servidor, apenas quando o dono está no mundo. Salvas no NBT da entidade e restauradas entre sessões, com compatibilidade com mundos da v0.7.25. Não são fome/sono obrigatórios nem sentimentos reais.
- **Mesmo corpo, cérebro melhor:** o `AurenNaturalIdleGoal` recebe alternativas classificadas da mente, mas continua validando os caminhos A* e as colisões. Se um abrigo ou uma exploração não tem rota, tenta a alternativa segura seguinte. Isso não altera o sistema CE de voo, fogo, raio, montaria nem as ordens V.
- **`/auren mente`** (somente dono dentro de 64 blocos): mostra energia, curiosidade, nível de alerta e última escolha, sem HUD intrusivo. Use para observar a evolução das variáveis.
- **Teste executável separado:** `scripts/LocalDecisionPolicyTest.java` é compilado e executado com Java 21 sem Minecraft, testando cenários de chuva, noite, alta curiosidade e alerta. Testes anteriores e inspeção de JAR continuam.
- **Nada de IA externa nesta versão**: não há token, API, chamada de rede, telemetria ou controlador de prompt.

### Limites declarados
- A política inicial **só escolhe rotinas pacíficas**. Prioridades entre combate, seguir, defesa, montaria e ordens do dono ainda são resolvidas pelos `GoalSelector`/controladores atuais; centralizar essas prioridades será a etapa 2.
- Memória de acontecimentos, planejamento em várias etapas e personalidade adaptativa ainda não existem: ver roadmap.
- A validação por CI **não substitui** teste de jogabilidade; manter PR draft até a confirmação do jogador.

### Testar no Minecraft
1. Rode `/auren mente` perto do seu dragão. Anote os valores; espere 2–3 minutos em ambiente seguro e veja mudança de energia/curiosidade.
2. Com noite e Auren com energia mais baixa, deve preferir dormir por mais tempo; na chuva exposto deve priorizar abrigo **se acessível**.
3. Com animais pacíficos próximos, ele pode investigá-los sem atacar.
4. Saia e entre no mundo: `/auren mente` deve conservar os indicadores e última rotina selecionada.
5. Marque inimigo, monte, use ombro, voe e ataque: nenhum conflito com rotina ou perda de defesa.
6. Desligue internet: tudo isso continua funcionando.

---

# Auren Minecraft 0.7.25 — Rotina Natural Contextual

O Auren desenvolve rotina própria sem depender da IA do computador. Esta versão **não altera o combate, o voo, a montaria, as transformações nem os ataques de fogo/raio**.

### Novas rotinas (apenas se estiver manso, próximo e seguro)
- **Observação:** acompanha o que o dono faz e olha ocasionalmente para os arredores. A direção da cabeça varia de forma irregular, evitando varreduras robóticas em intervalos constantes.
- **Passeios curtos:** caminha por rotas terrestres A* para pontos com piso firme, colisão liberada e chunk carregado, mantendo-se perto do dono; não explora áreas descarregadas nem teleporta.
- **Curiosidade pacífica:** ocasionalmente se aproxima com cuidado de animais pacíficos próximos ao dono e observa, **sem selecionar alvos de combate, atacar ou assustar deliberadamente**.
- **Descanso diurno:** porções curtas em pose original de dragão sentado do Ice and Fire CE, interpoladas em 18 ticks; sem alterar o comando real de sentar.
- **Sono noturno:** maior chance de usar a pose original de dragão dormindo quando anoitece, por um tempo limitado. Não é uma rotina fixa nem o deixa indisponível.
- **Chuva:** quando está exposto, tenta encontrar um ponto coberto nas proximidades se houver rota segura. Se não houver, não força teleporte ou voo; continua atento.
- **Interrupção imediata:** a rotina cessa quando o dono começa a se movimentar, sai do alcance, marca alvo, ocorre ameaça direta, monta no dragão, ativa ombro ou dá ordem de sentar.

As fases são sorteadas conforme o contexto, com cooldown de 100–230 ticks e duração delimitada. **Nenhum novo pacote client->server**: o servidor apenas sincroniza um inteiro (0 ativo, 1 sentado, 2 dormindo) e o cliente interpola os quadros originais Tabula. As poses de descanso são suprimidas se montado, voando, atacando ou no ombro.

### Como testar
1. Dia com clima limpo: deixe o Auren próximo e fique parado por 1–3 minutos. Deve variar olhar, curtas caminhadas e alguns descansos sentado.
2. À noite: deixe-o com você em local seguro. Ele deve dormir com maior frequência, sem ficar travado após o nascer do sol.
3. Perto de vacas/ovelhas: observe aproximação e curiosidade, **sem morder nem incendiar** um animal não marcado.
4. Chuva: se existir telhado acessível próximo, pode procurar abrigo; sem abrigo, não deve tentar atravessar paredes.
5. No meio de uma rotina, caminhe para longe, marque um monstro, monte no Auren ou coloque-o no ombro: deve parar a rotina imediatamente.
6. Verifique montagem, animações originais CE, raio do pequeno, sopro azul e ataque aéreo 0.7.22 sem regressões.

**Limite:** ainda não há necessidades simuladas de fome, sono obrigatório, personalidade via LLM ou decisões de combate sem ordens. Compilação e testes estáticos não garantem qualidade visual em game.

---

# Auren Minecraft 0.7.24 — Companhia e movimentos naturais

Sem poderes novos e **sem integração com a IA desktop**. Esta versão melhora apenas as decisões de andar, voar e agir tranquilamente ao lado do dono.

### Seguir pelo chão sem parecer perdido
- O Auren adulto já priorizava andar, mas agora deixa o **A* concluir desvios reais** sem reiniciar a rota em intervalos de 10 ticks.
- Recalcula a cada 25 ticks só quando a navegação terminou ou o dono se deslocou mais de 3,5 blocos.
- Detecta travamento pelo **movimento real do dragão**, não por aproximação em linha reta: afastar-se do dono para contornar uma parede não o obriga a voar.
- Voo de acompanhamento permanece do Ice and Fire CE, como alternativa após 80 ticks de travamento ou viagem longa ao ar livre (120 ticks; distâncias de 44 blocos adulto / 26 bebê).
- **Não tenta decolar automaticamente em caverna nem debaixo de telhado** quando ambos estiverem cobertos. Teleporte de emergência de 50 blocos mantém as proteções antigas.
- As distâncias de conforto permanecem: adulto começa a seguir após 15 blocos e para perto de 8; pequeno 4,5/1,7.

### Comportamento calmo
- Novo `AurenNaturalIdleGoal`: em estado seguro, observa o dono e arredores durante pequenos períodos, com ocasionais explorações de poucos blocos.
- Passeios limitados a área próxima do dono, piso firme, volume de colisão livre, chunks carregados e caminho A* completo. Nenhum teleporte nem movimentação empurrada por velocidade artificial.
- Nunca funciona enquanto o dragão está em combate, montado, no ombro, sentado por ordem, voando, nadando, sem dono ou quando o dono está se movendo.
- Pausas de 100–230 ticks entre sessões para evitar agitação constante; exploração ocorre em aproximadamente um terço das sessões.
- A pose/modelo e as animações permanecem inalterados: **não há animação de deitar/dormir** nesta versão.

### Preservado
Fogo, raios, defesa, caça terrestre/caverna, bombardeio orgânico da 0.7.22, montaria, tamanho, renderização e HUD H. Não mexemos no `FlightMoveHelper` CE nem no `AurenCeGroundMoveControl`.

### Como testar no jogo
1. Ande 15–25 blocos em terreno aberto e veja se acompanha pelo chão, seguindo curvas reais em vez de decolar.
2. Faça um trajeto em L atrás de uma casa, mas com passagem lateral. O Auren deve **contornar andando**.
3. Caminhe dentro de uma caverna; ele não deve bater nas paredes tentando decolar. A mais de 50 blocos de separação, teste o teleporte apenas para destino seguro.
4. Afaste-se por 50+ blocos em campo aberto: voo CE e teleporte de emergência continuam sendo opções.
5. Fique parado perto do Auren por alguns minutos: ele deve olhar ao redor, às vezes caminhar poucos blocos, sem se afastar indefinidamente.
6. Marque um inimigo, monte, ordene sentar e use o ombro: nenhuma rotina ociosa deve disputar os controles.

CI verifica compilação, classes/guardas e invariantes do controle de movimento. **Testes reais no mundo ainda são necessários**.

---

# Auren Minecraft 0.7.23 — Caça terrestre, raio pequeno e terreno aquecido

Pedido: o Auren adulto deve poder **caminhar para alcançar o inimigo e usar o sopro azul no chão**, em vez de decolar automaticamente; o pequeno pode atacar uma presa **marcada** com raios elétricos nas cavernas; o alcance da queimadura deve corresponder melhor à extensão do fogo; terra sem grama deve aquecer e sumir após exposição continuada.

## Melhorias
- **Caça no chão:** no modo inimigo V, o adulto prioriza caminho A* real até a presa quando está a até 35 blocos. Replaneja em intervalos de 35 ticks e não interrompe curvas continuamente. A até 24 blocos com linha de visão, lança seu jato azul normalmente mesmo parado/andando; a até 10 blocos pode parar de caminhar para disparar. Se a presa se esconde, aguarda oportunidade de navegação na caverna antes de escolher o voo; a caça aérea CE continua como alternativa.
- **Entrada primeiro:** o caçador reconhece a entrada da caverna e, quando possível, caminha em tamanho adulto até uma zona externa segura próxima à boca antes de se transformar. O sistema continua sem pathfinding 3D perfeito e requer teste real.
- **Raio na forma pequena:** durante a perseguição de uma criatura **marcada**, o dragão pequeno com visão direta (até 17 blocos) usa uma rajada elétrica curta, com warmup original, até 5 de dano por acerto e raio próximo ao impacto de 1,35 bloco, afetando SOMENTE aquela presa. Não usa comandos arbitrários do cliente, não ataca aliados, não destrói blocos e não substitui R/G do jogador montado. Fora do combate marcado, permanece com a mordida e as interações anteriores.
- **Marca mais extensa:** a área de calor/queima no chão cresceu de aprox. 5–7 para 8–11 blocos irregulares, com densidade decrescente nas bordas; mais chamas reais quando as regras do Minecraft permitem. A lateral de parede também foi ampliada para acompanhar o sopro.
- **Terra aquecida até desaparecer:** dirt, coarse dirt, rooted dirt, dirt path e suas versões carbonizadas acumulam calor e podem sumir após 165 pontos de exposição. Primeiro escurecem, sem explosão instantânea, com limites de segurança e `mobGriefing`; grama e rocha conservam suas progressões térmicas.
- **Mantido:** voo CE, trajetórias orgânicas do bombardeio 0.7.22, animações, montaria, controles V/Shift+V/B/R/G e HUD H.

## QA no jogo
1. Marque zumbi em terreno aberto e observe se o adulto se aproxima **andando** e solta fogo sem decolar.
2. Marque creeper após uma curva numa caverna acessível. Ele deve tentar caminhar para a entrada antes de voar. Se encolher, observar se o raio pequeno aparece e acerta o alvo sem acertar aliados.
3. Deixe o alvo sem linha de visão: não deve lançar raios através das paredes.
4. Queime terra sem grama por vários segundos; ela escurece e só então deve desaparecer.
5. Compare a marca no solo com a largura da chama azul, sem criar quadrados perfeitos.
6. Confirme bombardeio 0.7.22, raio de 5–30 blocos, modo montado e defesa espontânea sem regressão.

**Build/CI atestam integração e verificações estruturais; a IA, dano e o efeito visual precisam de testes no Minecraft.**

---

# Auren Minecraft 0.7.22 — Bombardeio orgânico e cobertura variada

Correção do modo área observada em jogo na 0.7.21: passadas muito retas, simétricas e concentradas nos mesmos blocos.

- **Movimentos mais naturais:** cada saída escolhe direção diferente da anterior, um ponto de interesse, uma entrada, uma curva intermediária e uma saída em outro ângulo. Continua usando o FlightMoveHelper original CE, sem inventar física nova.
- **Alvos variados:** memória de cobertura 5x5 usada *apenas internamente* para lembrar áreas recém-atacadas. Escolhe chamas dentro do círculo marcado e evita atingir sempre os mesmos setores; não cria marcas quadradas.
- **Fogo coerente:** só seleciona pontos à frente da cabeça, até 27,5 blocos, com linha de visão real e dentro do raio marcado. A rajada segue azul, volumosa e com calor.
- **Escopo:** apenas bombardeio de ÁREA (B + V), sem alterações ao combate contra criaturas, caverna, autodefesa, montagem, IA externa, materiais ou animações.
- **Segurança:** preserva limite 5/10/15/20/25/30, regras de griefing, chunks, alinhamento do dragão e cancelamento Shift+V. A memória de cobertura é zerada ao reiniciar a ordem.

## Teste
1. Marque uma aldeia com 20 ou 30 blocos; acompanhe **4 passadas completas**.
2. O dragão precisa mudar orientação e curvar entre passadas; as manchas não devem formar duas retas repetidas.
3. Teste raio 5 e 30; nenhum impacto intencional pode ser selecionado fora do círculo.
4. Cancele com Shift+V e marque outra área; a cobertura começa do zero.
5. Ataque uma criatura com V e monte no dragão; esses comportamentos não devem mudar.

Verificações de fonte/CI são **estruturais**, não substituem gameplay.

---

# Auren Minecraft 0.7.21 — Defesa, fogo lateral e caça pela entrada

Correções guiadas pelo teste em jogo da v0.7.20. Não há integração com IA externa.

- **Imunidade ao fogo:** entidade Auren está marcada como `fireImmune()` no EntityType, protegendo contra fogo/soul fire/magma/lava vanilla. Seu próprio jato já evitava dano direto ao dragão, mas os blocos incendiados eram perigosos.
- **Autodefesa real, sem agressão gratuita:** `HurtByTargetGoal` valida agressor e aliados; se um **dragão adulto** é diretamente ferido por uma criatura válida, cria ordem de caça contra quem o feriu (se não houver uma ordem em andamento). Morder um monstro pelo dono não dispara este mecanismo. O Auren pequeno usa a meta de contra-ataque corpo a corpo.
- **Chamas percebidas sem alertas irritantes:** fireNearby permanece como informação factual em `/auren ambiente`, mas o HUD não considera o próprio incêndio um perigo automático. Avisa caso o **dono esteja realmente pegando fogo**, esteja ferido, ameçado por monstro ou perto de lava.
- **Grama queima mais rápido:** limite de erosão para grama 195 → 115 unidades de calor acumulado. Não há demolição instantânea; o centro de uma rajada contínua deve mudar antes das bordas.
- **Queimadura lateral/parede/teto:** o sopro detecta a face atingida e esquenta uma mancha irregular de blocos **expostos nessa face**. Rocha vira magma, depois ar após calor suficiente; terra e grama/carbonizados avançam pelas regras preexistentes. A área é conservada no modo bombardeio; locais inacessíveis/entidades de bloco não são alterados; fogo real só é colocado quando o vanilla permite sustentá-lo.
- **Caça em caverna:** quando há entrada conectada à presa mas não existe pouso seguro para a caixa adulta, procura posições de voo com raio para **a boca da caverna**, para direcionar o jato ao acesso em vez de escavar o teto. Limite de tentativas antes da destruição rochosa de último recurso. Continua sendo um adaptador de IA; uma rota 3D perfeita não está garantida.

**Regressões preservadas:** voo original inspirado em CE, montaria, marcações V/B, tamanho, câmera, animação e visual do jato azul.

## Testes no Minecraft
1. Ataque o Auren **com um zumbi** enquanto ele não tem alvo marcado: deve reagir ao zumbi. Não deve perseguir um monstro apenas porque está por perto.
2. Caminhe sobre soul fire ou magma com o Auren: não deve receber dano térmico vanilla.
3. Queime um barranco de grama pela lateral por vários segundos, observe escurecimento/erosão sem quadrado perfeito.
4. Queime uma parede de pedra pelo lado até gerar magma, depois até abrir uma falha. O fogo não deve pular paredes para atingir blocos escondidos.
5. Creeper em caverna com boca lateral sem plataforma de pouso: Auren deve procurar posição de voo mirando entrada antes de perfurar o teto, sem garantir que consiga entrar em todos os mapas.
6. `H`: fogo distante não deve emitir alerta; se o DONO está queimando, alertar; manter lava/monstros/vida baixa.
7. Repita montaria, ataque aéreo, Shift+V e fogo azul que já funcionavam.

**CI verifica compilação e invariantes estruturais, não comportamento real em cavernas.**

---

# Auren Minecraft 0.7.20 — Revisão de confiabilidade

Esta versão **não adiciona mais poderes**. Corrige falhas confirmadas na 0.7.19 e reforça o teste dos sistemas existentes.

- **Percepção sem pontos cegos:** leitura completa, em disco de 5 blocos e 5 níveis de altura, incluindo o bloco onde o dono está; não deixa lava/fogo/sinos passarem despercebidos por posições pares.
- **Distância verdadeira:** criaturas consideradas apenas até 24 blocos **reais** de distância do dono. A lista é organizada por distância antes de aplicar o limite de 96, priorizando ameaças e animais próximos.
- **Memórias mais corretas:** distingue surgimento/desaparecimento de monstros e sino de vila de mudanças reais de perigo, sem spam por qualquer flutuação de contagem.
- **Navegação em cavernas:** a busca de entrada não aceita mais qualquer bloco de ar como rota. Verifica um volume desocupado de 0,50×0,70 bloco (o tamanho da forma pequena); corredores com colisões de paredes/lajes não são classificados como livres.
- **Preservação:** nenhuma alteração em fogo azul, carbonização/magma, ataques, montaria, voo original CE, explosões, atalhos existentes, ou modelos/animações.
- **Teste:** novo verificador específico para cobertura de sensores e hitbox de caverna. O script estrutural sozinho não substitui testes de gameplay.

Detalhes de criticidade e de outros riscos observados estão em `docs/AUDIT_0720.md`.

---

# Auren Minecraft 0.7.19 — Percepção do Mundo

Primeira camada de percepção ambiental server-side do Auren, planejada para integrar futuramente com a IA Auren desktop. **Não existe ainda conexão com modelo de linguagem, voz ou memórias da IA do computador.**

## O que percebe
- Até 24 blocos do dono: monstros, animais, outros jogadores, espécie e distância aproximada do monstro mais próximo.
- Bioma, dia/noite, clima e céu aberto/abrigo/caverna; sinais próximos de fogo, lava ou sino de vila.
- Riscos: monstro a menos de dez blocos do dono, vida abaixo de 35%, fogo e lava próximos.
- Pequeno histórico (até 8 mudanças relevantes) apenas durante a sessão; não é memória persistente.

## Interação discreta
- **H** (remapeável): resumo azul por 7 segundos no topo da tela. Não fica aparecendo permanentemente e não envia comandos de combate.
- **/auren ambiente** ou **/auren perceber**: relatório detalhado no chat, validado pelo servidor.
- **/auren lembrar**: últimas mudanças registradas pelo Auren.
- **/auren status** continua informando apenas as ordens.

## Limites de desempenho/segurança
- No máximo uma leitura a cada 40 ticks, apenas se o dono do Auren domesticado estiver online na mesma dimensão.
- Até 96 entidades processadas em raio 24; cerca de 144 amostras de blocos perto do dono, somente em chunks já carregados.
- Sincroniza somente resumo e nível de alerta quando mudam. Um jogador não pode consultar percepção de Auren de outra pessoa via comando.
- **Não ataca automaticamente.** Só coleta observações, não seleciona alvos nem move o dragão.
- Fire Breath, voo/combate, percepção visual de marcação V/B, navegação em cavernas e montaria estão preservados.

## Testes em jogo
1. Aperte H ao ar livre. A linha azul aparece por 7 segundos e some.
2. Traga zumbi perto do dono. Teste /auren ambiente e afaste-o: contagem/alerta devem mudar em 2 segundos.
3. Entre em caverna / volte à superfície, mude chuva e dia/noite. /auren lembrar apresenta transições.
4. Perda/restauração de vida deve alterar aviso de risco; dono sem permissão não vê relatório do Auren de outro usuário.
5. Marque inimigo, voo e sopro funcionam exatamente como antes, sem ataques decorrentes de percepção.

**CI compila e inspeciona invariantes; não substitui jogo.**

---

# Auren Minecraft 0.7.18 — Navegação real em cavernas

**Problema observado na 0.7.17:** o Auren pequeno ainda se dirigia aproximadamente para a presa, sem seguir corretamente a entrada da caverna; ele também abandonava desvios válidos quando se afastava inicialmente do inimigo.

- **Abertura conectada ao interior:** novo `AurenCaveRoutePlanner.findEntrance()` percorre apenas células transitáveis da caverna a partir da presa (máximo de 1600 nós). A entrada precisa estar conectada por corredores reais, e não só geometricamente perto do creeper.
- **Pouso:** procura lugar com espaço para o adulto perto da *entrada identificada*, evitando pousar do outro lado da montanha.
- **Navegação:** forma pequena usa `PathNavigation.createPath` e `moveTo(Path,...)`; tenta rota direta para a presa; se bloqueada, caminho até a entrada conectada pelo algoritmo. Não gera vetor de direção reta como substituto para a navegação.
- **Preserva desvios:** não interrompe a rota a cada 15 ticks; só tenta novo caminho após 40 ticks e se a anterior terminou, o destino mudou ou não há movimento físico.
- **Sem falso travamento:** mover-se *para longe da presa* ao contornar a caverna não equivale a estar preso. Só recupera depois de 100 ticks sem se deslocar ou timeout geral.
- **Custo:** escaneia somente chunks carregados e limita nós/expansões, adequado para a execução em Zalith.
- **Preservado:** fogo azul/pegadas da 0.7.15, voo CE, sons, teclas, câmera e explosões de última instância da 0.7.17.

**Ainda precisa de teste no Minecraft:** corredores estreitos, bifurcações, caverna sem acesso, entrada obstruída e custo com várias cavernas. A busca considera piso sólido e passagens com ar, não atravessa pedra, água ou portas fechadas.

---

# Auren 0.7.17 — Corrigir sopro estacionário e perseguição em cavernas

Relato da 0.7.16: corpo rodava enquanto lançava fogo sem mover-se, ficava pequeno sem andar, e queimava montanha com entrada de caverna.

- **Sopro estacionário:** `AurenFlightController.holdFacing` desativa o waypoint fictício `posição + (0,0.12,0)` que fazia o FlightMoveHelper forçar orientação enquanto estava parado. Mantém voo estabilizado, reduz impulso e gira até 3° por tick em direção ao ponto real de impacto. O FlightMoveHelper também não executa giro 180° por colisão quando não tem alvo de movimento.
- **Entrada da caverna primeiro:** se a presa está coberta, procura um ponto de aterrissagem com espaço para a forma adulta, chão sólido, iluminação do céu e altura próxima da presa, num raio de 5–15 blocos. Aproxima-se voando até a entrada, pousa e só então encolhe; não pousa arbitrariamente sobre o topo da montanha para se transformar.
- **Perseguição pequena:** tenta caminho real até a presa. Quando não consegue rota direta, tenta caminhos parciais até pontos próximos à entrada e reavalia a cada 15 ticks.
- **Não fica parado esperando crescer:** quando não cabe na forma adulta, procura um caminho de volta ao local seguro de pouso; se não for possível, varre pisos próximos com espaço para crescer. Ao cancelar ou recarregar, continua tentando sair ao invés de perder toda navegação.
- **Explosões apenas como último recurso:** se existe entrada acessível localizada (`caveLanding != null`), não dispara cargas explosivas sobre a montanha. Preserva limite de 5 cargas, `mobGriefing`, aliados/owner, raio e whitelist de rochas quando não existir nenhuma entrada.
- **Preservado:** fogo azul e marcas irregulares da 0.7.15, combat mode, atalhos, sons, modelo e piloto. Os testes são automatizados de *fonte + compilação*, não reproduzem visual nem caminho real no Minecraft.

**QA:** marque um creeper dentro de uma caverna com entrada lateral, observe se aterrissa perto da abertura e entra pequeno; bloqueie a passagem e observe se tenta outra rota ou consegue voltar ao tamanho normal; examine se o corpo mantém yaw estável enquanto lança fogo parado.

---

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
