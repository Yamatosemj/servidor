# Auren Minecraft — protótipo 0.2 (NeoForge 1.21.1)

**Um único dragão Auren** com forma adulta e filhote, usando os modelos Tabula de relâmpagos da IceAndFire Community Edition e a biblioteca Uranus incluída no JAR.

## Status

- **Confirmado pelo jogador no Android/Zalith (v0.1):** jogo inicia, modelo/textura aparecem, Auren anda, pode ser domesticado e alterna entre adulto/filhote.
- **Problemas da v0.1:** modelo rígido (não havia animação), interação de ombro não funcionou.
- **Alterações da v0.2:** movimento procedural simples para cabeça, corpo, cauda e patas; ombro por sincronização da posição do Auren ao jogador, sem montaria do Minecraft; feedback na tela e comando alternativo para Android.
- **Não validado no jogo ainda:** as correções da v0.2 e o posicionamento exato sobre o ombro.

## Instalação

- Minecraft Java **1.21.1**
- NeoForge **21.1.248+** (ramo 1.21.1)
- Instale **somente o JAR do Auren**. Uranus **3.0-beta.1 está embutido via Jar-in-Jar**. Ice and Fire não é necessário.
- Substitua o JAR antigo do Auren; não use duas versões ao mesmo tempo.

## Controles para o novo teste

1. Em um mundo com comandos: `/summon aurenminecraft:auren`.
2. Com mão vazia, interaja uma vez para domesticar.
3. Agache e interaja para alternar adulto ↔ filhote.
4. Com filhote e **sem agachar**, interaja para colocá-lo no ombro. Uma mensagem de confirmação deve aparecer.
5. **Agache** para tirar do ombro; ou interaja outra vez.
6. **Alternativa fácil no Android:** use `/auren ombro` para alternar a posição no ombro do Auren domesticado mais próximo (até 16 blocos). O comando pode transformar o Auren em filhote automaticamente.

Enquanto estiver no ombro, o Auren desativa a navegação/gravidade e acompanha a posição do seu dono. Não é um passageiro vanilla. A posição visual **precisa ser testada no Zalith**.

## O que observar

- Cabeça e cauda devem apresentar movimentos discretos em repouso.
- Patas devem se mexer quando o Auren anda; **a animação completa da Community Edition ainda não foi incorporada**.
- O filhote deve se manter perto do ombro durante caminhadas, agachamento e mudanças de direção; verificar se fica visualmente deslocado.
- Verificar se há dano, travamento, sumiço ou descontrole de escala depois de salvar e recarregar mundo.

## Arquivos

- `src/main/java/br/com/auren/minecraft/` — entidade, comando de ombro e renderizador.
- `build.gradle` — Gradle e download verificado de dois modelos e duas texturas; Uranus via Jar-in-Jar.
- `docs/TECHNICAL_DECISIONS.md` — decisões e limitações.
- `THIRD_PARTY_ASSETS.md` — créditos e licenças de terceiros.
- `.github/workflows/build.yml` — build e verificação de JAR.

**Fora do escopo desta versão:** voo, montaria, raios, inteligência artificial, animações complexas de voo e ataques.
