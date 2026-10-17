# Auren v0.7.30 — Forma vinculada / Capacete vivo

**ESTADO: protótipo de alto risco. Testar SOMENTE em cópia do mundo.**

- O mesmo Auren é guardado no SavedData do Overworld, indexado pelo dono.
- O sprite é EXATAMENTE o PNG 16x16 que o usuário aprovou.
- O item não guarda dados de invocação; é só uma chave validada pelo servidor.
- Botão V > Interagir, /auren ombro ou botão direito no Auren pequeno com mão vazia: guarda o dragão.
- Botão direito segurando o item: restaura o mesmo UUID, forma pequena, junto ao dono, em local sem colisão.
- No estado vinculado, não existe entidade física para sufocar, ferir ou acertar.
- Imagem 3D na cabeça usa Tabula original do dragão de raios negro e herda o osso da cabeça.
- Tenta bloquear drop, remover cópias ilegítimas e regenerar o item após morte e login.
- Se chunks antigos forem carregados com dragão que está vinculado, a cópia é recusada.
- Os sistemas adultos CE de voo, luta e montaria não foram reescritos.

## LIMITAÇÕES IMPORTANTES
- Os saves de SavedData e chunks **não são uma transação atômica**. Ainda não provamos segurança contra crash durante gravação.
- O ícone pode circular temporariamente por inventários de outros mods; ele é inerte sem estado no servidor.
- O render atual de cabeça consulta inventário local, então visibilidade em multiplayer exige sincronização explícita antes de ficar completo.
- Offset e escala da pose E são estimativas e requerem captura de tela no jogo.
- Testes de CI compilam código e verificam artefato/estruturas, não simulam perda de dados ou condições visuais.

## Testes manuais obrigatórios (mundo copiado)
1. Converter o mesmo Auren em item, verificar PNG e modelo preto/roxo seguindo a cabeça.
2. Passar por teto de dois blocos, agachar, virar o rosto, usar capacete e morrer.
3. Morrer com keepInventory false/true, voltar, mover item entre slots, apertar Q, sair/entrar.
4. Testar chest/baú, inventário cheio, dimensões diferentes, modo creative e servidor.
5. Soltar com botão direito: aparece o mesmo UUID, mesmo dono/vida/memória.
6. Tentar soltar em parede/água/lava sem perder o vínculo; testar reinício do servidor.
7. Teste específico com parada forçada enquanto captura/solta, em backup, para auditar exclusividade.
