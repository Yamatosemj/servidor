# Auren Minecraft — Protótipo NeoForge 1.21.1

**Uma única entidade Auren**, representada por um dragão de raios inspirado na IceAndFire Community Edition, com forma adulta e filhote/ombro.

**Estado:** protótipo experimental. O código está em desenvolvimento e precisa de build do GitHub Actions e validação visual no jogo. **Não há IA, voo, montaria ou ataque de raios nesta etapa.**

## Compatibilidade

- Minecraft Java **1.21.1**
- **NeoForge 21.1.248** ou mais recente dentro do ramo 1.21.1
- **Uranus 3.0-beta.1** para NeoForge 1.21.1 ([arquivo correspondente](https://www.curseforge.com/minecraft/mc-mods/uranus/files/8682870))
- **Ice and Fire NÃO é exigido:** baixamos somente 2 modelos Tabula e 2 texturas do dragão de raios durante o build.

## Teste previsto quando o build passar

1. Baixe o `.jar` gerado na aba **Actions** deste repositório e instale junto com Uranus no perfil NeoForge.
2. Entre em um mundo com comandos habilitados.
3. Use `/summon aurenminecraft:auren` para invocar um Auren.
4. Clique com **mão vazia** nele para torná-lo seu companheiro.
5. **Agache e clique com mão vazia** para alternar adulto ↔ filhote.
6. Na forma filhote, **clique com mão vazia** para colocá-lo no ombro; **agache** para soltá-lo.

Esses passos descrevem a intenção do protótipo. **O comportamento ainda precisa ser testado dentro do cliente**; compilação do Java sozinha não garante que os modelos sejam exibidos corretamente.

## Estrutura

- `src/main/java/br/com/auren/minecraft/` — entidade, estado sincronizado e renderizador Tabula.
- `src/main/templates/META-INF/` — metadados e dependências NeoForge.
- `build.gradle` — Gradle/NeoForge e download verificável dos recursos originais.
- `docs/TECHNICAL_DECISIONS.md` — pesquisa, decisões e limitações.
- `THIRD_PARTY_ASSETS.md` — procedência/licenças dos recursos de terceiros.
- `.github/workflows/build.yml` — compila automaticamente e anexa artefatos de build.

## Recursos da Community Edition

Os recursos são recuperados de um commit fixado da [IceAndFire Community Edition 1.21.1](https://github.com/IAFEnvoy/IceAndFire-CE/tree/1.21.1), com verificação de SHA antes do empacotamento.

Assim mantemos **somente o Auren** no jogo, mas usamos Uranus como biblioteca técnica para ler arquivos Tabula. Consulte os créditos e direitos em `THIRD_PARTY_ASSETS.md`.

## Próximas etapas

- Confirmar compilação de um jar NeoForge 1.21.1.
- Validar o dragão adulto/filhote e posição do ombro em jogo.
- Corrigir escala, câmera, sombras, colisões e persistência.
- Só depois implementar as animações completas, voo, ataques elétricos e conexão ao Auren principal.

Este repositório era o antigo `servidor`, cujo histórico foi mantido.
