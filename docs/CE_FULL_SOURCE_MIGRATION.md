# Migração Auren 0.7: primeiro a engine original inteira, depois reduzir

Início: 15/10/2026. Base: CE 2.1.4, NeoForge 21.1.248, Minecraft 1.21.1.
Origem imutável: https://github.com/IAFEnvoy/IceAndFire-CE/tree/aa2f11a6b79acb48a1094765d610cdaca79e3d18

## Entrega desta primeira etapa

Importamos como submódulo Git o **repositório completo**, não fragmentos
adaptados. Compilação independente da CE usa as suas próprias classes,
recursos, modelos, arquivos de dados, registro de entidades, rede, animações
e eventos, sem nenhuma reescrita ou deleção. O build CI fornece os dois
JARs de teste: CE original + Auren. O Auren legado permanece utilizável.

### Sistemas originais que permanecem íntegros na CE

- `DragonBaseEntity` e dragões de fogo, gelo e raio
- Voo `IafDragonFlightManager`, lógica `IafDragonLogic`, IA de escolta,
  navegação, perseguição, vagar, dormir e retornar ao ninho
- Montaria, física, decolagem, pairar, pitch, planeio, stamina, colisão multipart
- Sopro, fogo, raio, gelo, projéteis, ataques aéreos e terrestres,
  `IafDragonDestructionManager`
- Modelos Tabula, animadores, partículas, sons e renderização completa
- Crescimento, idade, fome, equipamentos/inventário, ovos, domar, comandos
- Dependências e registros auxiliares do jogo: deixados no lugar

### O que NÃO foi concluído (não confundir fonte com migração)

O Auren `aurenminecraft:auren` **ainda é uma classe própria** e usa suas
rotinas antigas. A compilação da CE em outro JAR não torna essa classe
automaticamente idêntica ao original. As entidades nativas CE invocadas
pelos comandos da v0.6.5 são a referência comportamental.
As duas têm IDs próprios no mundo. Os recursos originais estão completos
na CE, e as transformações de Auren continuam na entidade legada.
Também não é um JAR único independente: Jupiter 2.3+ é necessário.
NÃO executar migração automática de mundos nem trocar IDs registrados.

## Política de implementação daqui em diante

1. Validar que a CE completa compila, inicia e funciona no cliente/jogo.
   Comparar decolagem, voo, combate e animações dos dragões de referência.
2. Transformar o dragão de raio original em base da nova entidade Auren
   por especialização direta da classe original ou um fork auditado.
   Reutilizar todo o sistema de voo/IA/renderização original sem novos
   controladores e sem copiar equações para `AurenDragon`.
3. Migrar identidade, proprietário, formas adulto/filhote, ombro e
   exclusividade, sem alterar os cálculos físicos básicos do CE.
4. Integrar ataques originais de fogo ao dragão de raio. O híbrido Auren
   será uma extensão consciente, não comportamento literalmente idêntico
   a uma só espécie nativa.
5. **Só depois** auditar o grafo real de dependências para remover
   criaturas/itens não usados, um conjunto por vez, com testes CI e no
   Minecraft após cada remoção. Preservar arquivos/licenças exigidos.
6. Projetar migração explícita de dados: salvar o mundo antes de trocar
   qualquer identificador de entidade; nunca forçar merges na main.

## Reproduzir

```sh
git checkout feat/auren-ce-complete-source-v070
git submodule update --init --recursive
python3 scripts/verify_ce_native_source.py
gradle build --no-daemon
(cd vendor/iceandfire-ce && gradle build --no-daemon)
python3 scripts/verify_ce_native_binary.py
```

Na branch, `.github/workflows/native-engine.yml` executa isso no GitHub
Actions e cria um ZIP de teste com o JAR do Auren e o CE completo original.
`docs/CE_NATIVE_PARITY_AUDIT.md` contém a comparação de códigos anterior.

### Licença e segurança

IceAndFire-CE: LGPL-3.0-or-later, licença original preservada no submódulo e
republicada no artefato. Não retirar atribuições ao podar componentes.
Sem modificar classes originais na etapa zero. Não executar o build em
mundos com progresso sem backup.
