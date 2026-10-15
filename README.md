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

- **Um mod JAR para instalar: `aurenminecraft-0.7.3-rider-pose-alpha.jar`.**
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
