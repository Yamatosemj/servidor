# Auren Minecraft — 0.7.1 SINGLE-DRAGON EXPERIMENT (Minecraft 1.21.1 / NeoForge)

**A versão experimental 0.7.0 anterior tinha um erro de escopo**:
ela instalava o mod IceAndFire CE inteiro junto do Auren para comparar
comportamentos. Isso adicionou TODOS os itens, monstros, dragões, estruturas
e abas do IceAndFire, contrariando o objetivo.

## Este build contém SOMENTE o Auren

- **Um mod JAR para instalar: `aurenminecraft-0.7.1-single-auren-alpha.jar`.**
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
