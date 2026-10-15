# Jupiter 2.3.7 embutido no Auren (NeoForge Jar-in-Jar)

Em Auren 0.7.0 experimental, a dependência do IceAndFire CE completo é
Jupiter >=2.3. O próprio Auren agora embute **Jupiter 2.3.7** (CurseForge
file 7738312, modId jupiter, NeoForge 1.21.1) e Uranus 3.0-beta.1
(modId uranus) como dois mods completos aninhados dentro do seu único
JAR, usando o mecanismo oficial NeoForge Jar-in-Jar.

O instalador recebe somente dois JARs: Auren (com Uranus+Jupiter) e
IceAndFire CE original (com as correções de recursos). **Não instalar
Jupiter nem Uranus separadamente**, nem versões duplicadas dos dois mods.

O build e o CI verificam o índice META-INF/jarjar/metadata.json,
o conteúdo de ambos os JARs aninhados e o arquivo neoforge.mods.toml com
modId e versão originais. Nenhuma classe é achatada/shadeada, para evitar
conflitos de identidade e colisão de registros.

Esta é uma verificação de composição/compilação de JAR; ainda é necessário
teste de inicialização do NeoForge 1.21.1 e do Zalith Launcher para provar
que mods aninhados são carregados sem conflito. Continua sendo experimento
não mesclado à main.
