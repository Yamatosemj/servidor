# ROADMAP — Auren Minecraft autônomo, com IA externa opcional

**Versão de referência:** 0.7.25 estável em termos de compilação (QA visual da rotina ainda é necessário).
**Princípio permanente:** o dragão deve funcionar com o Minecraft rodando, **sem DeepSeek, internet, servidor externo ou aplicativo aberto**. Modelos de linguagem nunca entram no loop do motor/physics/ticks.

## Arquitetura-alvo

```text
Minecraft (servidor NeoForge) — SEMPRE LIGADO
 ├─ Percepção factual (AurenWorldPerception)
 ├─ Mente local e necessidades simuladas (AurenLocalMind)
 ├─ Memória compacta e persistente (futura)
 ├─ Coordenador de intenções/prioridades (futuro)
 │   prioridade: dono e segurança > defesa > ação marcada > acompanhamento > rotina
 └─ Executor: Goals + A* + CE flight + fogo/raio + transformações
             ↑
    Ponte opcional com IA: propõe objetivos de alto nível
    (entrada validada, tempo limite, confirmação, fallback offline)
```

Cada componente usa **um único Auren** e preserva controles CE existentes. As ações nunca são executadas diretamente por texto gerado. O sistema local é a autoridade.

## Etapas e critério de conclusão

| # | Etapa | Entregável | Validação / status |
|---|---|---|---|
| 0 | Fundação funcional | Fogo, raios, formas, voo, caça, percepção, rotinas 0.7.25 | Feito tecnicamente; alguns casos de QA visual pendentes |
| **1** | **Mente autônoma local — 0.7.26** | **Pontuação por utilidade, energia/curiosidade/alerta simulados, escolha de rotina e NBT** | **Código e CI aprovados; aguardando teste no jogo**. Não considerar a etapa totalmente concluída antes do playtest |
| 2 | Prioridades e arbitragem | Autoridade única para conflitos entre proteger, cumprir ordem, seguir, descansar e reação imediata | Testar preempção, cancelamento, montaria e controle do dono; preservar `GoalSelector` e CE |
| 3 | Memória local significativa | Eventos recentes, locais úteis, falhas de caminho e preferências compactas no save | Limites de tamanho, expiração, isolamento por dono e migração NBT |
| 4 | Planejamento offline simples | Sequências de intenções (aproximar → investigar → retornar), tentativas limitadas e recuperação de falha | Rotas impossíveis não travam o Auren, timeout e alternativa natural |
| 5 | Personalidade estável e adaptação | Traços limitados e hábitos influenciando as decisões, sem aleatoriedade caótica | Consistência entre sessões; comportamento auditável com comando de diagnóstico |
| 6 | Integração opcional de IA | Adaptador DeepSeek/outro modelo, desligado por padrão, somente sugestões de intenção | Testes SEM rede, com latência, falha e mensagens malformadas; nenhuma ação não autorizada |
| 7 | Estabilidade de longo prazo | Multiplayer, saves antigos, mundos grandes, benchmarks de TPS, segurança e regressões | Validar backups, não forçar chunks, não duplicar entidades, não quebrar combate/voo |

## Etapa 1 — entrega planejada e limites

- [x] `AurenLocalDecisionPolicy` é **Java puro**, isolado da API Minecraft e da rede, avaliando cinco opções de rotina.
- [x] `AurenLocalMind` mantém energia/curiosidade/alerta entre 0–100 e só os atualiza periodicamente no servidor enquanto o dono está online.
- [x] O `AurenNaturalIdleGoal` pede opções ordenadas à mente; depois **valida A* e colisões existentes**. Se a primeira opção falhar, tenta a próxima em vez de travar.
- [x] Estados internos são carregados e salvos no NBT da entidade (compatíveis com save 0.7.25 sem essas chaves).
- [x] `/auren mente` revela uma visão limitada, apenas para o dono dentro de 64 blocos, sem HUD constante.
- [x] CI com teste executável do ranking, verificação de JAR e regressões completas. [Execução aprovada](https://github.com/Yamatosemj/servidor/actions/runs/38948553925) (39 etapas).
- [ ] **Playtest**: dia/noite, chuva, animal próximo, dono se afastando, salvar/reentrar, combate e voo CE sem regressão.

### Regras de segurança já nesta etapa

1. A decisão é uma intenção, **não** coordenadas, `velocity`, comando de chat ou permissão de destruir blocos.
2. A mente **não** pode marcar ou trocar alvo, decolar, alterar voos, acionar fogo/raio, usar rede ou escrever arquivos arbitrários.
3. `GoalSelector`, ordens do dono, ataque, montaria e defesa **continuam com prioridade real**; idle sai quando essas condições mudam.
4. Nenhuma necessidade simulada pode causar dano involuntário, morte por falta de sono ou fome de dragão — são apenas pesos de escolha.
5. A memória da percepção 0.7.19 continua curta; não chamá-la de raciocínio ou aprendizado.
6. Não começar a etapa seguinte antes de validar a anterior no jogo.

## Notas para retomar o projeto em um novo chat

- Repositório: **`Yamatosemj/servidor`** (não confundir com desktop `Yamatosemj/aurenOFC`).
- Base da etapa atual: `feat/auren-0725-contextual-daily-routine`, PR #50; nova branch `feat/auren-0726-offline-mind`.
- Ler **este roadmap**, o README, `docs/ONLY_ONE_AUREN_SCOPE.md` e `docs/TECHNICAL_DECISIONS.md` antes de implementar.
- Se o próximo pedido for continuar: confirmar se 0.7.26 passou nos testes e em gameplay, **depois** implementar somente a etapa 2 em outra PR.
- Sem IA externa até o usuário pedir explicitamente a etapa 6. Nenhuma conexão automática nem transmissão de dados pessoais/jogadores.
- Não converter provas estáticas em alegações de teste em jogo. Os PRs da série são drafts empilhados até validação manual.
- Mod alvo: Minecraft **1.21.1**, NeoForge; build por GitHub Actions; preservar jar único, licença CE e arquivos Tabula originais.

## Diário de decisões

- **2026-10-16:** escolhemos arquitetura *offline-first*: cérebro local permanente, integração LLM opcional.
- **0.7.26:** começar por mecanismo de ranking isolado, adaptar somente rotina ociosa; persistência dos valores; comando de observabilidade. Sem alterar controladores de voo/combate.

- **0.7.26, validação técnica:** suíte GitHub Actions aprovada em 16/10/2026; o teste manual de gameplay e o merge continuam pendentes.
