# Recursos externos — atribuição e licenças

**Fonte dos modelos/texturas:** IceAndFire Community Edition, <https://github.com/IAFEnvoy/IceAndFire-CE>, ramo 1.21.1, commit `aa2f11a6b79acb48a1094765d610cdaca79e3d18`.

O repositório publica seu código sob LGPL v3 ou posterior. Repositório e direitos dos recursos originais devem ser respeitados. Este projeto não reivindica autoria sobre os recursos do Ice and Fire.

**Arquivos incluídos no jar por tarefa de build:**

- `models/tabula/lightningdragon/lightningdragon_ground.tbl`
- `models/tabula/lightningdragon/lightningdragon_sitting_shoulder.tbl`
- `textures/entity/lightningdragon/black_1.png`
- `textures/entity/lightningdragon/black_5.png`

Os caminhos dos arquivos são remapeados ao namespace `aurenminecraft` no build. O downloader confere o identificador Git SHA-1 de cada arquivo. Não alteramos os bytes dos modelos/texturas.

**Dependência embutida:** Uranus 3.0-beta.1, <https://github.com/IAFEnvoy/Uranus>, sob LGPL-3.0; o JAR original de Uranus é incluído intacto em `META-INF/jarjar/` usando o mecanismo oficial do NeoForge. O usuário não precisa instalá-lo separadamente. Distribuir apenas a versão compatível para NeoForge 1.21.1. Código-fonte e licença disponíveis no upstream. O texto da LGPL está dentro do nosso JAR em `META-INF/licenses/LGPL-3.0.txt`.

**Identificação adicional:** a cópia embutida do Uranus possui seu próprio `META-INF/neoforge.mods.toml` com `modId="uranus"`, versão `3.0-beta.1`. O GitHub Actions verifica sua presença e a licença incluída antes da publicação do artefato.

**Licença do código próprio:** LGPL-3.0-or-later (escolhida para simplificar a compatibilidade entre componentes). Ao redistribuir, manter créditos e cumprir todos os avisos/licenças aplicáveis. A origem de recursos previamente derivados do Ice and Fire original deve ser revisada antes de publicar versões estáveis.
