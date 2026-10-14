# Auditoria técnica profunda — Auren Minecraft 3D, animações e pescoço
**Data:** 14/10/2026  
**Baseline imutável auditado:** commit `f896e841b7b0f88313cffc927bf15bb2a2020433`, versão **0.5.6-alpha**, Minecraft Java 1.21.1, NeoForge 21.1.248, Uranus 3.0-beta.1.  
**Escopo:** análise de código, hierarquia Tabula, histórico v0.3–v0.5.6, comparação upstream, diagnóstico geométrico e pré-visualização **sem nenhuma alteração no Auren em execução**.  
**Estado do resultado visual:** **NÃO aprovado**. A execução/compilação não substitui teste no Zalith.

> **Conclusão técnica central:** a pose vista nos prints da v0.5.6 não é falha das animações de idle; é consequência de compor poses incompatíveis para o objetivo da arte: `sitting_shoulder` (postura alta), torções aditivas do pescoço/cauda e apenas quatro ossos principais das asas vindos de `sleeping`. Essa composição não cria um corpo apoiado transversalmente na nuca, não garante patas nos ombros e **aumenta** a abertura horizontal das asas. A solução definitiva precisa de avaliação objetiva do esqueleto em coordenadas do jogador e de uma **pose-alvo deliberada e testável**, não de mais mudanças de ângulo às cegas.

## 1. O que foi analisado e o que NÃO foi

Arquivos centrais de `main`:

- `src/main/java/br/com/auren/minecraft/client/AurenDragonRenderer.java`: modelo, ordem das camadas e escala.
- `src/main/java/br/com/auren/minecraft/client/AurenNeckPose.java`: correções aditivas do pescoço/cauda/patas.
- `src/main/java/br/com/auren/minecraft/client/AurenPoseTransition.java`: interpolação de rotações e pivôs.
- `src/main/java/br/com/auren/minecraft/client/AurenOriginalShoulderIdle.java`: idle derivado da CE.
- `src/main/java/br/com/auren/minecraft/client/AurenOriginalAnimations.java`: caminhada e voo *visual*.
- `src/main/java/br/com/auren/minecraft/entity/AurenDragon.java`: estado/posição física no ombro, render interpolado, comportamento do dono.
- `scripts/verify_neck_pose.py`, `scripts/verify_original_animations.py`, `.github/workflows/build.yml`.
- Recurso JAR **exatamente** da compilação oficial da `main` do commit baseline; extraídos arquivos `.tbl` ZIP com `model.json`, sem alterar nenhum byte.

Repositórios upstream examinados:

1. **IceAndFire CE 1.21.1**, `render/model/animator/DragonTabulaModelAnimator.java` (ciclo de caminhada/voo, pose `SIT_ON_PLAYER`, movimento idle), `IceAndFireTabulaModelAnimator.java` (interpolação) e `LightningTabulaDragonAnimator.java`.
2. **IceAndFire CE 1.21.1**, `entity/DragonBaseEntity.java` (dragão pequeno como **passageiro real** do jogador; ancoragem).
3. **Uranus 1.21.1**, `client/model/TabulaModel.java`, `AdvancedModelBox.java` e `util/TabulaModelHandlerHelper.java`.

Códigos fonte/links de referência:
- [IceAndFire CE: animador do dragão](https://github.com/IAFEnvoy/IceAndFire-CE/blob/1.21.1/src/main/java/com/iafenvoy/iceandfire/render/model/animator/DragonTabulaModelAnimator.java)
- [IceAndFire CE: entidade do dragão](https://github.com/IAFEnvoy/IceAndFire-CE/blob/1.21.1/src/main/java/com/iafenvoy/iceandfire/entity/DragonBaseEntity.java)
- [Uranus: TabulaModel](https://github.com/IAFEnvoy/Uranus/blob/1.21.1/src/main/java/com/iafenvoy/uranus/client/model/TabulaModel.java)
- [Uranus: AdvancedModelBox](https://github.com/IAFEnvoy/Uranus/blob/1.21.1/src/main/java/com/iafenvoy/uranus/client/model/AdvancedModelBox.java)
- Assets originais com commit fixado no `build.gradle`: [CE aa2f11a…](https://github.com/IAFEnvoy/IceAndFire-CE/tree/aa2f11a6b79acb48a1094765d610cdaca79e3d18).

**Limites:** não executei Minecraft/Zalith com personagem renderizado nem verifiquei visualmente o modelo final dentro do motor. Qualquer afirmação de «ficou perfeito» seria infundada. Os prints do usuário constituem evidência visual de erro, não demonstração da causa isolada.

## 2. Evidências quantitativas do modelo realmente empacotado

O arquivo `aurenminecraft-0.5.6-alpha.jar` contém **17 arquivos Tabula** do Lightning Dragon (incluindo 4 `walk`, 6 `flight` e 4 poses de repouso/chão), com **113 ossos nomeados** por pose. As quatro poses estáticas `ground`, `sitting`, `sitting_shoulder` e `sleeping` compartilham nomes e relações pai-filho. A hierarquia pode portanto ser interpolada por nome; isso **não** garante uma composição bonita.

**Resultado reproduzível da prévia cinemática**, em escala 0,42; largura horizontal entre os **pivôs dos ossos das asas** (não largura da malha/textura, nem pixels de tela):

| Esqueleto examinado | Extensão X dos pivôs das asas |
|---|---:|
| Original `ground` | ~1,385 bloco |
| Original `sitting_shoulder` | ~1,293 bloco |
| Original `sleeping` | ~1,564 bloco |
| **v0.5.6 composta, sem idle** | **~1,569 bloco** |

Assim, a combinação da v0.5.6 abriu os pivôs das asas **~21% a mais que `sitting_shoulder`**. Não equivale a “asa fechada”. No exemplo de geometria examinado, `sleeping` **não é simétrica**: `armL1` possui rotação X **10°**, enquanto `armR1` tem **41°**. Transferir esses valores para outra postura sem conferir toda a silhueta produz assimetria. Este é um dado da pose original, **não** defeito de importação.

Os dois ramos das asas têm **19 ossos cada** contando o osso principal, dedos, membranas e garras. Comparando `sitting_shoulder` e `sleeping`, quatro ossos em cada ramo apresentam diferenças de rotação; o código da v0.5.6 copia dois em cada ramo (`armL1`, `armL2`, `armR1`, `armR2`), mas **não copia** `ClawL`, `ClawR`, `membraneL6` e `membraneR6`, embora esses quatro também sejam diferentes. **Importante:** os demais ossos continuam sendo transformados indiretamente pela hierarquia. Não confundir “não copiado” com “parado no espaço”.

Esses números são extraídos das poses do JAR auditado, não inventados a partir de uma imagem. O script `scripts/audit_tabula_geometry.py` gera `geometry.json` e `bone_projections.svg` para reproduzir a comparação.

## 3. Como o Ice and Fire realmente funciona

### 3.1 Modelo e transformação

- Os arquivos `.tbl` são ZIPs contendo `model.json`. A Uranus lê `position`, `rotation`, `offset`, `dimensions` e a árvore de `children`.
- O `TabulaModel.createBox` converte a rotação **graus → radianos**. `AdvancedModelBox.translateAndRotate` aplica translação local, depois rotações **Z, Y, X**, herdadas dos pais. A posição final do focinho ou ponta da asa não é o valor de um único osso, mas a **composição acumulada** da árvore.
- Alterar `Neck1` muda a base de `Neck2`, `Neck3`, `Head` e descendentes. A mesma regra vale para `Tail1…Tail5` e para braços/dedos/membranas das asas.
- Os ajustes `AurenNeckPose` entram **depois** de misturar o `sitting_shoulder`. Ou seja, o alvo do usuário não está representado por uma única pose; é um somatório de transformações independentes.

### 3.2 Animação não é pose

- `LightningTabulaDragonAnimator` original configura **4 quadros de andar** e **6 de bater asas em voo**.
- `DragonTabulaModelAnimator` usa progressos como `ridingProgress`, `sitProgress`, `flyProgress`, ciclos e animações por osso (`chainSwing`, `chainWave`, `bob`).
- `sitting_shoulder.tbl` é **uma pose estática**. O movimento de repouso é aplicado separadamente por código.
- O nosso `AurenOriginalShoulderIdle` traz parte desse movimento, razão pela qual a v0.5.4 passou a mexer a cabeça e a cauda; ela **não** corrigiu a forma geral do corpo.
- Diferença significativa em relação à CE: o Auren no ombro chama `setNoAi(true)` e segue o dono por posição manual; o código completo original depende de `DragonBaseEntity`/passageiro, e tem inclusive uma saída antecipada para entidade `isNoAi()`. Portanto o animador original **não pode** ser copiado integralmente sem adaptação.

### 3.3 Âncora física ≠ pose do esqueleto

A versão atual posiciona o dragão diretamente em `AurenDragon.tick()` com:

- `lateral=0.075` bloco,
- `backward=0.18` bloco,
- `Y=0.76 × altura do jogador`,
- orientação seguindo `yBodyRot`,
- escala da malha filhote `0.42`.

Isso posiciona a **origem da entidade**, não a cabeça, o tórax nem as patas. Portanto valores que parecem razoáveis no código podem deixar a cabeça acima do avatar ou atravessar a nuca. Além disso, o jogo interpola posições e rotações entre ticks, e o avatar muda de pose ao agachar/andar; a prévia geométrica não simula essas interações.

Na CE, o filhote pequeno monta **efetivamente** no jogador (`startRiding`) e tem `updateRiding` próprio, incluindo offsets por índice de passageiro. O Auren utiliza `setPos` em vez de veículo real por compatibilidade e simplicidade no controle por toque; essa diferença de arquitetura explica por que copiar **somente** o `sitting_shoulder` não reproduz toda a apresentação original.

## 4. Histórico reconstruído dos erros

| Versão | Mudança | O que explica o resultado |
|---|---|---|
| v0.3 | “cachecol vivo” com `AurenNeckPose` e rotação global | tentativa de construir pose sobre modelo existente; sem validação cinemática da silhueta |
| v0.4 | 20 ticks de interpolação e 90° em `AurenDragonRenderer.scale` | girar o **modelo inteiro** cria postura lateral; não equivale a curvar só o corpo |
| v0.5.0 | quadros CE de andar/voar | animações externas ao ombro foram melhoradas; ombro ainda é outra ramificação do renderizador |
| v0.5.3 | remove 90° e torções manuais, retorna `sitting_shoulder` | filhote continua em pé, porque pose original não equivale ao desenho |
| v0.5.4 | porta `idle` do código CE | dragão se mexe, mas continua com silhueta ereta e asas abertas |
| v0.5.5 | usa `sleeping` inteira | postura fica mais encolhida, porém aparenta estar dormindo |
| v0.5.6 | `sitting_shoulder` acordada + pescoço/cauda aditivos + 4 ossos de asas de `sleeping` | cabeça/cauda torcidas por hierarquia e asas mais largas; prints do usuário confirmam resultado visual inadequado |

**Outra falha histórica confirmada no código original:** em v0.5.3/v0.5.4 o renderizador mexia em `BodyUpper.offsetZ` a cada frame, mas `AdvancedModelBox.resetToDefaultPose()` da Uranus tem o reset de `offsetX/Y/Z` **comentado**. Deslocamentos assim podem persistir entre quadros e tornar a transição não determinística. **A v0.5.6 já removeu esse deslocamento**; isso é explicação histórica, não bug atual que devemos “corrigir” novamente.

## 5. Problemas e severidade na v0.5.6

| ID | Evidência | Classificação |
|---|---|---|
| A-01 | Asas ~1,569 vs 1,293 bloco de pivôs na pose oficial de ombro; screenshot mostra extensão aberta | **Causa confirmada no esqueleto e no jogo** |
| A-02 | A postura-alvo não existe como arquivo único; mistura poses estáticas e ângulos aditivos sobre cadeia articulada | **Causa estrutural confirmada** |
| A-03 | `applyBones` não replica quatro transformações distintas de garras/membranas | **Diferença objetiva**, efeito visual específico ainda a medir |
| A-04 | Âncora descreve origem da entidade, não pontos de contato cabeça/ombros/costas | **Risco confirmado arquiteturalmente**, posicionamento exato depende de runtime |
| A-05 | `TabulaModelHandlerHelper.getModel` cria um novo modelo a cada invocação; ombro chama-o para poses temporárias por frame | **Custo de alocação confirmado pelo código**, impacto em FPS/GC **não mensurado** |
| A-06 | Testes atuais conferem arquivos e strings Java, não o resultado final de 3D/avatar | **Lacuna de cobertura confirmada** |
| A-07 | Documentação histórica contém linguagem como “asas dobradas” na v0.5.6 e descrição anterior de `sleeping` como pose inteira do ombro | **Divergência com o estado atual**, distinguir histórico de estado ativo |

Também preservamos a separação de escopo: IA de seguir, teleporte adulto aos 50 blocos, chunk ticket, tamanho, voo mecânico não implementado e ritual futuro **não são objeto de alteração** nesta auditoria.

## 6. Pré-visualização sem abrir Minecraft — opções reais

### A. Diagnóstico geométrico 2D/SVG reproduzível (protótipo implementado)
**Status: funcionando como investigação, não como render final.** O script em `scripts/audit_tabula_geometry.py` lê os arquivos Tabula *diretamente do JAR oficial* e verifica nomes, ancestrais, rotações e pivôs de **113 ossos**. Gera:

- `geometry.json` — métricas de abertura das asas e diferenças de subárvores;
- `bone_projections.svg` — vistas **frontal/lateral/superior** das poses `ground`, `sitting_shoulder`, `sleeping` e composição v0.5.6 **sem idle**.

Sem dependências Python externas. Reproduz hierarquia, ordem T→Z→Y→X de transformação e graus→radianos da Uranus. **Não inclui** renderização completa de cubos/textura, sombra, player skin, rotações externas do renderer, offsets avançados, câmera ou idle no Zalith. Cada figura é centralizada individualmente: **não** serve para aprovar posicionamento relativo ao jogador.

### B. Blockbench com importador Tabula (melhor visual para autorar pose)
O catálogo de plugins do Blockbench lista o **Mod Utils** com suporte a importação Tabula. Há ainda o repositório **3talonz/Blockbench-Tabula-Importer-Exporter**, que descreve importar `.tbl` em projetos *Modded Entity* pelo arquivo de plugin. O procedimento a validar em uma cópia, sem sobrescrever o original, é:

1. Extrair `lightningdragon_sitting_shoulder.tbl` do JAR.
2. Importar no Blockbench pelo plugin Tabula, checar se mantém os **113 ossos e relações pai-filho** e a UV das texturas.
3. Abrir o modelo do jogador como referência de escala, fixar a origem de ambos, e posicionar tronco/cabeça/cauda/asas com editor visual.
4. Salvar um **novo arquivo de pose experimental**, nunca sobrescrever os originais da CE.
5. Comparar exportação/importação (ida e volta) antes de integrar ao Uranus.

**Riscos reais:** suporte entre versões Tabula e plugins pode falhar, perder pivôs/UV/names ou corromper hierarquia; há histórico de problemas de importação `.tbl` recentes. **Não foi testado o plugin nesta auditoria**. Fontes:
- https://github.com/JannisX11/blockbench-plugins (plugin Mod Utils)
- https://github.com/3talonz/Blockbench-Tabula-Importer-Exporter
- https://github.com/JannisX11/blockbench-plugins/issues/88

### C. Pré-visualizador 3D fiel à Uranus (proposta, NÃO implementado)
Converter o `.tbl` para cubos de um motor de geometria simples, preservando pivôs, matriz local, faces e textura. Incorporar:
- modelo Minecraft player padrão com dimensões corretas;
- posições reais `lateral/backward/Y`, escala 0.42 e transformação do `MobRenderer`;
- slider de `shoulderBlend=0…1`, quadros do idle, visão frontal/costas/lado/superior;
- marcadores de contato: focinho, patas, tronco, asas, cauda;
- testes de interseção com cápsula/caixas aproximadas do avatar.

Recomendação **futura** para correções finas, pois dá visualização determinística fora do jogo. Exige confirmar detalhes de offsets e coordenadas do renderizador, além de validação cruzada com pelo menos uma captura do Zalith.

### D. Teste visual no cliente Minecraft
É o **único teste de integração definitivo**. Capturas com F5 em ângulos padronizados e um mundo de teste, preferencialmente mesma skin e pose, distância da câmera fixada. Comparar com a arte aprovada, depois testar caminhada, giro, agachar, entrar/sair do ombro e reabrir mundo. Não tratar uma compilação Java como aprovação da postura.

## 7. Plano de correção recomendado — sem implementá-lo agora

**Etapa 0 (concluída na auditoria):** diagnóstico, métricas, prévia geométrica e testes de integridade do esqueleto. Não tocar na entidade.

**Etapa 1 — definir um alvo geométrico:** usando a ilustração fornecida, especificar *pontos de contato* em coordenadas relativas ao jogador: peito deitado atrás da nuca, cabeça no ombro esquerdo e abaixo do topo da cabeça, patas dianteiras sobre ambos os ombros, pontas das asas juntas ao dorso e cauda passando ao lado oposto. Testar compatibilidade com proporções reais do modelo (um desenho não equivale a rig).

**Etapa 2 — criar uma pose dedicada, uma única fonte da verdade:** ajustar no editor visual a pose específica para o Auren, sobre *um só* esqueleto, preferencialmente um novo `auren_neck_rest.tbl` versionado; não misturar sucessivamente `sitting_shoulder`, `sleeping` e várias torções em tempo de execução. Conservar o idle CE **sobre a nova pose base**. A animação externa ao ombro continua original CE.

**Etapa 3 — comparação objetiva:** no pré-visualizador, confrontar cabeça, patas, corpo e cauda com o player; conferir largura das asas e ausência de cruzamento óbvio com cabeça. Aprovar com prints padronizados do Zalith (frente/costas/perfil).

**Etapa 4 — apenas após aprovação visual:** integrar o alvo interpolado 20 ticks, com testes de regressão de ombro, transformação e teleporte. Reduzir alocações por frame com cache seguro de modelos respeitando reload de recursos, se benchmark justificar.

**Não fazer:** gerar mais uma v0.5.7 mudando três números aleatórios, misturar features de ritual/voo, trocar o dragão inteiro, alegar que pose Tabula `sleeping` está fechada só porque o arquivo se chama sleeping, alterar o comportamento do adulto aprovado.

## 8. Como reproduzir e interpretar os testes

No repositório, após `gradle build` (Java 21):

```bash
python3 scripts/verify_bundle.py
python3 scripts/verify_neck_pose.py
python3 scripts/verify_original_animations.py
python3 scripts/verify_follow_behavior.py
python3 scripts/audit_tabula_geometry.py --jar build/libs/aurenminecraft-0.5.6-alpha.jar --out build/reports/pose_audit
```

O workflow agora pode entregar um **segundo artefato de auditoria** contendo `geometry.json` e `bone_projections.svg`. O JAR original e o mod **permanecem inalterados**. O PNG local da prévia é apenas uma exportação do SVG; nenhum PNG é necessário para rodar os testes.

**Resultados nesta investigação (baseline 0.5.6):** 17 `.tbl` no JAR; quatro poses-chave com **113 ossos e mesma hierarquia**; ~1,293 bloco de extensão dos pivôs das asas na `sitting_shoulder`, ~1,569 na v0.5.6 composta. As quatro diferenças de asa não copiadas são `ClawL`, `ClawR`, `membraneL6`, `membraneR6`. Não há prova de que copiar somente esses quatro resolveria a postura; pelo contrário, o problema principal é de **pose-alvo e espaço do jogador**.

---

**Próximo passo autorizado somente em tarefa separada:** corrigir a pose a partir de um rig visual, com prévia e aprovação. Esta auditoria **não** autorizou mudanças de jogabilidade, desenho de modelo ou animação de produção.
