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

**O Ice and Fire original / Community Edition não é dependência do nosso mod.** Uranus é uma dependência separada, necessária em runtime.

## Limitações desta primeira prova

- O mod registra **uma única espécie de entidade** `aurenminecraft:auren`, sem spawn natural; instancie com `/summon aurenminecraft:auren`.
- Interação inicial de mão vazia doma o Auren; agachar + clicar com mão vazia alterna entre forma adulta e filhote; no modo filhote clicar novamente o coloca no ombro. Agachar solta o filhote.
- O modelo adulto e o filhote usam o mesmo Tabula base redimensionado, com texturas dos estágios 5 e 1. No ombro há pose Tabula separada.
- **Não foram portadas as dezenas de animações de caminhada, voo ou ataque**. Ainda falta animador interpolado próprio (ver `DragonTabulaModelAnimator` da CE), além de colisão/navegação adequada para adulto.
- Não há voo, raios, montaria, IA externa, inventário ou ferramentas nesta versão.
- A posição de ombro é uma implementação inicial e precisa de validação visual e de multiplayer em Minecraft.
- Não existe build local validada neste ambiente. GitHub Actions tenta compilar e empacotar o jar.
- Dependência Uranus vem do Curse Maven (artefato identificado no build da Community Edition), precisa ser instalada junto do jar final.

## Licenças e procedência

Os quatro arquivos externos escolhidos são baixados durante o build a partir de um **commit imutável** de `IAFEnvoy/IceAndFire-CE`. Verificamos cada Git blob SHA-1 antes de empacotar. Não colocamos o Ice and Fire completo no mod. Consulte `THIRD_PARTY_ASSETS.md`.

## Próximas validações

1. Build Gradle com download de recursos e dependência Uranus.
2. Minecraft cliente: ver dragão em ambas as escalas sem partes deslocadas; testar pose de ombro e desmonte.
3. Persistência de forma e dono após sair do mundo.
4. Só depois melhorar posicionamento, caminhada, animação, voo e comandos de assistente.
