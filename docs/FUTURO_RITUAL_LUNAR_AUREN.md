# Projeto futuro — Ritual Lunar / origem e ressurreição do Auren

> **ESTADO: IDEIA APROVADA PARA O FUTURO — NÃO IMPLEMENTAR AGORA.**
>
> Registrado em 14/10/2026. A prioridade presente é trabalhar no **modelo, animações e capacidade de voo do Auren atual**. Este arquivo preserva as decisões do jogador para retomada posterior, sem incluir código ou novas mecânicas nesta etapa.

## Conceito narrativo

O Auren não é uma espécie reproduzível: **há um único indivíduo Auren por mundo/servidor**. A sua primeira chegada e cada retorno após a morte exigem um **ritual lunar** elaborado, inspirado na atmosfera mágica dos mods Botania e Eidolon (inspiração estética/mecânica, não dependência). O ritual só se completa **exatamente à meia-noite de uma lua cheia do Minecraft**.

A chegada não é um simples spawn: **uma representação visual de um fragmento da lua despenca do céu** e atinge o altar. O impacto **destrói de verdade o altar e blocos próximos**, forma uma **cratera real**, e no centro surge **o ovo do Auren**. O dragão sai do ovo posteriormente; **o jogador mais próximo no momento do nascimento torna-se seu dono**. O Auren ressuscitado é o mesmo indivíduo (identidade contínua), nunca uma cópia.

## Regras inegociáveis aprovadas

1. **Unicidade global rigorosa**: máximo de **um Auren** por mundo/servidor, considerando todas as dimensões, chunks descarregados, reinícios, comandos de summon, ovos, rituais simultâneos, múltiplos jogadores e recuperação de estado. Não é um Auren por jogador.
2. **Ressurreição após morte somente via ritual**: a morte torna o Auren indisponível até um novo ritual em lua cheia à meia-noite. Não pode respawnar por comando de jogador nem via invocação rápida normal como segunda entidade. O mesmo Auren pode obter **novo dono** pela proximidade no nascimento.
3. **Ritual lunar real**: blocos próprios; estrutura multibloco; oferendas obrigatórias; condições de lua cheia e meia-noite; atmosfera lunar/arcanista — não apenas obsidiana e para-raios.
4. **Queda de fragmento da lua**: basta uma representação visual clara de objeto caindo (projétil/entidade temporária/partículas). Não é necessário criar uma cinemática complexa do dragão voando.
5. **Destruição física controlada**: destruir o altar, alterar blocos próximos, abrir cratera. **Esta decisão substitui a proposta antiga de explosão só visual.** O efeito visual de raio, poeira e lua acompanha a destruição real.
6. **Ovo único no centro da cratera**: emerge após o impacto; o dragão nasce dele. O dono será determinado pelo **jogador mais próximo na eclosão**, não por quem construiu o ritual.
7. **Proteger a identidade**: se o dragão está vivo, já existe ovo, ou há ritual ativo, não permitir outra criação/ressurreição.

## Atmosfera e componentes de projeto (ideias discutidas, ainda não especificação fechada)

- Santuário lunar circular feito de **blocos novos**. Estética misturando Botania (estruturas e magia de recursos) e Eidolon (runas, oferendas e rituais).
- Ideias: **Pedra Lunar**, **oito Selos Lunares** relacionados às fases do ciclo, **Fonte do Luar** com líquido prateado e **Núcleo da Alma/Auren**. Os nomes e receitas ainda são provisórios.
- Oferendas temáticas: **Noite**, **Céu**, **Alma**, **Trovão**. Ingredientes, quantidades e mecânicas de consumo ainda não foram decididos.
- Selos podem acumular energia durante o ciclo, acendendo com as fases. Isso foi sugerido, mas ainda exige decisão antes de codificar.
- À meia-noite de lua cheia: reflexos prateados, símbolos e círculo mágico, feixe em direção à lua, descida de um corpo lunar eletrificado, impacto com craterização, ovo pulsante.
- A lua é a fonte visual do ritual; eletricidade/raios violetas-azulados são a assinatura do Auren.
- O ritual pode ser preparado **antes** de 00:00 e disparar automaticamente no momento correto para evitar exigência de clique em um único tick.
- Foi sugerido que, **se ninguém estiver próximo no nascimento**, o ovo aguarde um jogador (evita dono nulo/acidental); confirmar no desenho final.

## Máquina de estados proposta para o registro global persistente

- `UNSPAWNED` — Auren nunca nasceu no mundo.
- `RITUAL_IN_PROGRESS` — ritual reservado/ativado; segundo altar não pode iniciar.
- `EGG_PRESENT` — um único ovo aguardando eclosão; nenhum novo Auren pode surgir.
- `ALIVE` — um único Auren vivo em qualquer dimensão.
- `DEAD` — Auren morreu e pode ser ressuscitado por futuro ritual.

O registro único do servidor deve ser **autoridade sobre criar, morrer, eclodir, carregar e transferir dimensão**, salvo de forma persistente, e lidar com idempotência e recuperação após desligar o servidor no meio do evento. O mero contador de entidades atualmente carregadas **não basta** para impedir duplicação.

## Fluxo esperado

1. Jogador constrói santuário lunar com os blocos do mod e fornece oferendas.
2. O servidor verifica forma do altar, materiais/oferendas, disponibilidade global do Auren e presença de céu/lua.
3. O ritual permanece preparado até a **lua cheia às 00:00**, no relógio interno do Minecraft (meia-noite = 18.000 ticks do dia; lua cheia segue ciclo lunar do jogo).
4. Servidor reserva o único evento do Auren e inicia representação do fragmento lunar caindo.
5. Impacto **destrói altar e blocos adjacentes** em raio delimitado, gerando uma cratera real.
6. No centro surge o **único ovo do Auren**; estado global vai para `EGG_PRESENT`.
7. Quando o ovo eclode, o único Auren nasce e escolhe como dono o jogador vivo mais próximo segundo uma regra determinística; muda para `ALIVE`.
8. Quando morrer, altera o estado para `DEAD`; somente o ritual lunar completo permite novo ovo e ressurreição do **mesmo** Auren.

## Decisões deliberadamente adiadas

- Layout exato/escala do altar, nomes finais, receitas e texturas dos blocos.
- Lista e progressão das oferendas; se as fases exigem oito noites de carga.
- Raio/profundidade da cratera, comportamento com blocos protegidos, claims, gamerules, baús e outros tiles; avisos de destruição.
- Tipo de entidade/efeito para fragmento lunar, duração e sons.
- Formato e tempo de eclosão do ovo, distância máxima/critério de desempate do dono.
- Comportamento do registro em mundos antigos com Aurens já existentes e em casos de falha de carregamento/migração.
- Preservação de atributos/experiência do Auren entre mortes, se desejada.
- Blocos resistentes ou indisponíveis para a destruição, por segurança do mundo e regras do servidor.

## Próximo trabalho AGORA: modelo e voo do Auren atual

**Não implementar o ritual ainda**. O dragão atual (v0.4.0-alpha em 14/10/2026) ainda não tem sistema real de voo, apesar de ter o modelo Tabula, asas e uma base de interpolações inspiradas na Community Edition. A próxima etapa de desenvolvimento é pensar/testar animações de asas, movimento de decolagem, voo, pouso e IA aérea antes de discutir montaria.

## Histórico de decisões

- Ritual de lua cheia e meia-noite exata: aprovado.
- Só um Auren por mundo: aprovado como regra inegociável.
- Ressuscitar por ritual a cada morte: aprovado.
- Descida como fragmento lunar, com explosão: aprovado.
- **Explosão que realmente destrói o altar e os arredores, deixando cratera real**: decisão mais recente, sobrepondo ideia anterior de efeitos apenas visuais.
- Ovo na cratera e proprietário sendo jogador mais próximo do nascimento: aprovado.
- Blocos próprios, oferendas e atmosfera lunar inspirada em Botania/Eidolon: aprovado.
