# Etapa 1A (Auren 0.7.2) — controlador de solo nativo do CE

Escopo: **somente um tipo de dragão** (aurenminecraft:auren), um
JAR instalado, sem IceAndFire CE/Jupiter/itens/criaturas extras.

## Fonte original

IceAndFire CE 2.1.4, SHA
`aa2f11a6b79acb48a1094765d610cdaca79e3d18`:
`com/iafenvoy/iceandfire/entity/util/dragon/IafDragonFlightManager.java`
classe interna `GroundMoveHelper`.

Portamos ramos `STRAFE`, `MOVE_TO`, `JUMPING` com o comportamento
original: velocidade de atributo, giro `rotlerp`, verificação do tipo
de caminho sob strafe, salto para alvo mais alto, estado de espera.
O código é atribuído à CE e licenciado sob LGPL-3.0-or-later.

## Diferenças explícitas e necessárias

- `DragonBaseEntity` virou `AurenDragon` (que permanece TamableAnimal).
- A CE calcula limite de curva adulta a partir de idade e hitbox.
  Como o Auren muda de tamanho visual e tem hitbox diferente, adotamos
  estágio de referência CE de 50 dias para forma adulta (11º/tick)
  e os 70º originais da forma pequena.
- Guardas explícitas: NÃO executar movimento de solo enquanto montado,
  em cima do ombro ou voando. Isso evita pisar nos inputs do piloto.
- Não alteramos o controller de voo CE já adaptado. O controlador
  de solo passa a ser a instância salva/restaurada pelo `switchNavigator`
  do Auren. Mesma entidade, mesmo ID NBT, sem quebrar os saves.

## Próxima etapa: voo autônomo nativo, sem reescrever

O atual `AurenIafDragonFlightManager` implementa um port parcial do
controle CE com alterações de seleção de alvo e estabilização vertical
para adaptação à física do TamableAnimal. Ainda diverge do original.

Para migrar voo com fidelidade, mapear a interação de
`IafDragonFlightManager`, `IafDragonLogic`, `DragonBaseEntity.travel`
e `DragonUtils.getBlockInViewEscort`, em especial `update()`,
estado de hovering/flying e seleção de targets. Adicionar dependências
mínimas em um único mod em vez de registrar `iceandfire`.
Testes de compilação não substituem o playtest.
