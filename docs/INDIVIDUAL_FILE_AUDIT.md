# Auditoria individual, reproduzível, de TODOS os arquivos

## Objetivo e escopo

Esta branch audita **cada arquivo versionado** em Auren 0.7 + o submódulo
completo do IceAndFire CE no SHA
`aa2f11a6b79acb48a1094765d610cdaca79e3d18`.
Nenhum arquivo é excluído por ser binário, documentação ou recurso.

O script `scripts/audit_each_file.py` abre o conteúdo real de cada arquivo,
registra caminho, extensão, bytes, hash SHA-256, resultado e justificativa.
Para cada PNG analisa assinatura, todos os chunks e CRC; cada TBL é tratado
como ZIP binário, verifica CRC e parseia o modelo JSON interno; cada JSON
é parseado; cada Ogg tem cabeçalho examinado; código Java verifica pacote,
UTF-8 e sinais de risco; cada Python é compilado como texto, sem executar.

## Saídas e como ler

O artefato do workflow `Individual File Audit` contém:

- `every_file.csv`: **uma linha por arquivo** (excel/LibreOffice/planilha).
- `every_file.jsonl`: os mesmos dados, uma entrada por arquivo.
- `stats.json`: contagens por categoria e tipo.
- `README.md`: todos os arquivos sinalizados, individualizados.

PASS significa que a **verificação estática aplicável passou**, não que
o Java funciona dentro do jogo. REVIEW indica arquivo que precisa de
avaliação humana ou verificador específico, sem presumir defeito.
ERROR indica formato/caminho/fonte inválido segundo o verificador.

Ambos os projetos também passam por compilação Java no mesmo workflow.
O teste não reproduz voo, hitboxes, pose animada, sincronização multiplayer,
performance Android, gameplay nem dependências dinâmicas dos recursos.

## Riscos sistêmicos já confirmados pelo código

1. O Auren antigo continua uma `TamableAnimal`, não uma
   `LightningDragonEntity`; o CE original e Auren compilam dois mods
   separados. A importação do código-fonte **não faz a migração sozinha**.
2. No Auren antigo, `AurenDragon`, `AurenFlightController`,
   `AurenIafDragonFlightManager` e `AurenFireBreath` têm
   implementações independentes: diferenças de comportamento com CE são
   esperadas.
3. O CE original precisa de Jupiter além de Uranus. O Auren embute
   Uranus no JAR, o que gera um alerta de conflito de dependências se
   outro mod trouxer a mesma biblioteca de outro Maven.
4. O sistema experimental gera duas entidades de espécies distintas,
   fogo e raio, com ids do CE; ainda não há o Auren único híbrido com
   transformação/ombro integrados nessas entidades.
5. O build não cria automaticamente uma migração segura do ID NBT
   `aurenminecraft:auren` para entidades do CE. Trocar o ID sem
   migração pode perder criaturas em mundos salvos.

## Não classificar automaticamente como aprovação

- Arquivos Java exigem análise semântica de fluxo de controle.
- OGG requer decodificação/reprodução e validação de sons registrados.
- JSON válido não prova que todos os IDs, texturas, receitas ou tags
  referenciados existem.
- Binários CRC-válidos não provam que uma animação se posiciona bem.
- Diferentes tipos do dragão original não compartilham automaticamente
  todos os ataques.
- A branch é experimental; não modificar `main` ou mundo salvo.

## Como executar

```sh
git submodule update --init --recursive
python3 scripts/audit_each_file.py --out build/reports/file_audit --fail-on-error
```

No CI, são compilados separadamente Auren e CE; as quatro saídas são
publicadas mesmo se a análise encontrar arquivos inválidos.
