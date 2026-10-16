# Auditoria de confiabilidade — Auren 0.7.19 → 0.7.20

## Escopo e método
Revisão estática dirigida das **32 classes Java**, regressões existentes e empacotamento NeoForge. Não foi executada sessão de jogo; o verde do CI não é prova de comportamento de IA no mundo real.

| Sistema | Resultado de triagem | Ação |
|---|---|---|
| Percepção | **Falha confirmada, alta:** varredura [-5,+5] em passos de 2 omitia o centro (0,0) e metade das posições | Corrigido: disco completo e centralizado |
| Percepção | **Falha confirmada, média:** entidades numa caixa de 24 podiam estar até ~42 blocos diagonalmente | Corrigido: raio euclidiano real |
| Percepção | **Risco, médio:** cota de 96 antes de priorizar distância podia esconder os monstros mais próximos | Corrigido: ordenar por distância e só então limitar |
| Memória curta | **Qualidade:** sino não registrado isoladamente, alterações relevantes de presença de monstros nem sempre registradas | Corrigido: eventos separados; histórico limitado |
| Cavernas | **Risco, médio:** BFS marcava células como livres apenas porque havia ar; colisões não eram medidas com a caixa do dragão | Corrigido: AABB 0,50×0,70 no espaço candidato |
| Combate | Tackle/hover/stream e escalada de alvos escondidos preservados | Sem alterações |
| Voo / controle CE | Controladores montado/terrestre/aéreo preservados | Sem alterações |
| Fogo / terreno | Fogo azul e calor acumulado aprovados em jogo anteriormente | Sem alterações |
| Raio / montaria | Autorização do piloto e dano de área preservados | Sem alterações |
| Cliente/HUD | H temporário e V/B sem mudança | Sem alterações |
| Assets/animação | Sem novos modelos/texturas/regravação de poses | Sem alterações |
| Tickets de chunk | Renovação periódica mantém o Auren ativo além da simulação | Observação: avaliar escalabilidade multi-dragão em benchmark |

## Restrições mantidas
- Nenhum alvo, posição ou ordem de fogo vindo do cliente sem confirmação do servidor.
- Griefing governado por `mobGriefing` e regras específicas de fogo.
- Apenas *Auren* registrado como dragão próprio, não há Ice and Fire CE completo.
- A percepção não escolhe alvos nem causa ataques.
- Sem integração com a IA de desktop nesta fase.

## Próximo teste manual prioritário
1. Colocar **fogo ou lava exatamente sob o dono** e observar `/auren ambiente` + H.
2. Posicionar um monstro na diagonal além de 24 blocos e verificar que não conta.
3. Posicionar dezenas de animais distantes e um zumbi perto do dono, confirmar que o zumbi é priorizado.
4. Marcar um creeper em caverna de entrada estreita com laje/fence bloqueando parte do corredor: não aceitar falso caminho.
5. Repetir rajada azul, voo, montaria e ataque aéreo para garantir ausência de regressão.

*Uma auditoria completa de comportamento ainda exigiria testes integrados em mundo real e medições de tick/fps com múltiplos dragões.*
