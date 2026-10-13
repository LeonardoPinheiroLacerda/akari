# Objetivo do Sistema — akari

> Documento gerado a partir da análise do código-fonte e do contrato OpenAPI
> (`src/main/resources/META-INF/openapi.yaml`). Inferências não confirmadas diretamente pelo
> código estão marcadas como **Hipótese**. Este documento é a base oficial para desenvolvimento
> futuro no modelo Spec-Driven Development — mudanças de produto devem atualizar este arquivo
> junto com o contrato.

## Propósito principal do sistema

akari é um backend de **catálogo e reprodução de vídeo (foco em anime)** que:

1. ingere o conteúdo de um ou mais *storage providers* (disco local ou MEGA via rclone);
2. organiza esse conteúdo num catálogo de pastas (`MediaFolder`) e arquivos de vídeo (`VideoFile`);
3. enriquece esse catálogo com metadata de anime vinda de fontes externas (AniList e TMDB),
   vinculando cada pasta ingerida a uma ficha de anime;
4. serve esse conteúdo para reprodução via transcode HLS sob demanda, com cache local de arquivos
   remotos.

É um sistema de **uso pessoal/self-hosted** (um único backend, sem multi-tenant, sem autenticação
de usuário final visível no contrato) voltado a quem mantém sua própria biblioteca de anime e quer
um catálogo navegável com metadata rica, similar em espírito a servidores de mídia self-hosted,
mas especializado em anime (binding com AniList, mapeamento de episódio absoluto↔temporada via
anibridge, franquias/relações entre animes).

## Problemas que o sistema resolve

- **Dispersão de arquivos de vídeo sem metadata.** Arquivos ingeridos de um storage provider têm
  apenas caminho e nome — o sistema resolve o problema de transformar isso num catálogo navegável
  com título, sinopse, imagens e organização por temporada/episódio.
- **Numeração de episódio inconsistente entre fonte de arquivo e fonte de metadata.** Animes
  frequentemente usam numeração absoluta de episódio nos arquivos, enquanto o TMDB organiza por
  temporada — o domínio `anibridge` resolve esse mapeamento usando um dataset de mapeamento mantido
  externamente (releases do GitHub).
- **Acesso a arquivo remoto sem reprodução nativa de streaming.** Arquivos em um provider remoto
  (MEGA) não podem ser servidos como stream diretamente — o sistema baixa para um cache local
  (fila serial, eviction LRU por espaço) antes de permitir reprodução ou probe de metadados
  técnicos.
- **Formatos de vídeo não reproduzíveis diretamente no cliente.** O domínio `playback` resolve isso
  transcodificando sob demanda para HLS, com seleção de perfil conforme características do arquivo
  de origem (via `mediaprobe`/ffprobe).
- **Custo de chamadas repetidas a APIs externas (AniList/TMDB).** Os domínios de busca mantêm cache
  local ("cache-first com SQLite interno", conforme descrição do contrato) para evitar round-trips
  redundantes ao upstream, com escape hatch (`forceRefresh=true`) quando o cliente precisa de dados
  atualizados.
- **Vazamento de credenciais de storage providers.** Campos sensíveis de configuração (ex.: senha
  do MEGA) são obscurecidos via rclone antes de persistir, e nunca são devolvidos pela API de
  leitura.

## Principais fluxos de negócio

1. **Cadastro de storage provider** (`StorageProvider`) → admin registra um provider `LOCAL` ou
   `MEGA` com configuração tipada; campos sensíveis são obscurecidos antes de persistir.
2. **Ingestão/reconciliação** (`Ingestion`) → o sistema varre o subtree do provider e reconcilia
   com o catálogo: cria pastas/arquivos novos, preserva os existentes, remove os que
   desapareceram do provider. Não há passo manual de "importar" além de disparar essa
   reconciliação.
3. **Navegação do catálogo** (`MediaFolder`/`VideoFile`) → cliente lista pastas e arquivos
   ingeridos; o catálogo em si é agnóstico a metadata de anime.
4. **Busca e binding de metadata** (`AnilistSearch`, `TmdbSearch`, `Anime`, `AnimeTmdb`) → admin
   busca um anime na AniList e/ou um título no TMDB, e faz o *binding* entre uma pasta do catálogo
   e uma ficha de anime (e, opcionalmente, entre o anime e uma ficha TMDB). Pode também curar
   manualmente campos que a fonte externa não cobre bem (overrides de título/sinopse/imagens,
   *franchise root*, relações entre animes).
5. **Pairing de arquivo com episódio** (`AnimeTmdbFilePairing`) → cada `VideoFile` de uma pasta
   vinculada é associado a uma temporada/episódio do TMDB (com apoio do `anibridge` para resolver
   numeração absoluta↔temporada, quando aplicável), permitindo gerar thumbnail por episódio.
6. **Reprodução** (`Playback`) → cliente inicia uma sessão de transcode HLS para um `VideoFile`
   (arquivo `LOCAL` lido direto do disco; arquivo remoto, baixado antes para o cache local via
   `Cache`), reposiciona com *seek*, consome a playlist `m3u8` e os segmentos gerados, e mantém a
   sessão viva com *heartbeats* periódicos até o cliente parar de assistir.
7. **Configuração administrativa** (`Setting`) → preferências chave/valor (ex.: API key do TMDB)
   configuráveis pelo admin; preferências obrigatórias sem valor fazem os fluxos que dependem
   delas responderem `503` (`INTEGRATION_NOT_CONFIGURED`).
8. **Observabilidade** (`Notification`) → eventos relevantes do sistema ficam disponíveis como
   lista consultável e contador de não lidas. Hoje, todas as notificações são emitidas pelo
   domínio `ingestion` (`IngestionService` e `FolderAutoBindService`): falha ao listar o storage
   provider, falha de integração com AniList/TMDB durante o auto-bind, ausência de candidato
   AniList/mapeamento TMDB utilizável, segmentos de numeração ambíguos (`anibridge`) e pareamento
   incompleto de arquivos↔episódio; nenhum outro domínio emite notificação ainda. O push em tempo
   real via SSE em `/v1/notifications/events` é **funcionalidade planejada, ainda não
   implementada** (o único SSE real hoje é o de `cache`, em `/v1/cache/events`).

## Atores envolvidos

- **Administrador do backend** — único ator humano explícito no contrato: cadastra storage
  providers, configura preferências (`Setting`), dispara/acompanha ingestão, cura metadata de
  anime (binding, overrides, relações, franchise root), gerencia o cache e acompanha notificações.
  Não há diferenciação de papéis/permissões no contrato, e **confirmado em código**: não existe
  nenhum mecanismo de autenticação/autorização implementado (sem `@RolesAllowed`,
  `SecurityIdentity` ou configuração de segurança) — é uma lacuna real reconhecida, não uma
  decisão definitiva de escopo. Cobertura futura prevista é *single-tenant* (login simples para
  proteger o admin único existente), sem abrir caminho para múltiplos usuários/perfis.
- **Cliente/aplicação consumidora da API** — qualquer frontend que fala com a API REST do akari
  (portal web, app) para navegar o catálogo, buscar metadata e reproduzir vídeo. O contrato não
  modela esse ator como uma entidade de domínio, apenas como consumidor HTTP.
- **Sistemas externos integrados**: AniList (GraphQL, metadata de anime), TMDB (REST, metadata de
  filme/série), MEGA via rclone (storage remoto), GitHub (dataset de mapeamento de episódios do
  `anibridge`), ffprobe/ffmpeg (binários externos para probe e transcode).
- **Processos automáticos internos do próprio backend** (schedulers): processam a fila de cache,
  encerram sessões de playback ociosas, sincronizam o dataset do `anibridge`, entre outros —
  atuam como "atores" não-humanos que mantêm o sistema consistente sem intervenção do admin.

## Funcionalidades centrais

- Cadastro e health-check de storage providers (`LOCAL`, `MEGA`).
- Reconciliação de catálogo (pastas/arquivos) com o storage provider.
- Cache local de arquivos remotos com fila serial e eviction LRU.
- Probe de metadados técnicos de vídeo (container/streams) via ffprobe.
- Transcode sob demanda para HLS com seleção de perfil, seek e heartbeat de sessão.
- Busca paginada cache-first em AniList e TMDB.
- Binding, sincronização e curadoria de metadata de anime (AniList + TMDB), incluindo relações
  entre animes e pairing de arquivo↔episódio.
- Preferências administrativas chave/valor, com suporte a valores secretos.
- Notificações de observabilidade com push em tempo real.

## Visão de produto

akari se posiciona como o **backend de um servidor de mídia pessoal especializado em anime**: a
diferenciação em relação a um servidor de mídia genérico está na camada de metadata (binding
AniList + TMDB, relações de franquia, mapeamento de numeração de episódio) e no fato de ser
*contract-first* e de módulo único — prioriza simplicidade operacional (um único processo Quarkus,
Postgres, sem orquestração multi-serviço) sobre flexibilidade multi-tenant ou multi-usuário. O
produto é pensado para uso doméstico/pessoal, com um único admin — essa orientação é deliberada e
permanece mesmo quando a lacuna de autenticação (ver "Atores") for coberta: o auth futuro é
*single-tenant* (protege o admin único), não um passo em direção a multi-usuário.

## Contexto operacional do sistema

- Executa como um único serviço Quarkus (JVM ou binário nativo via GraalVM), com Postgres como
  banco relacional (migrações via Flyway, localizadas em `classpath:db`).
- Depende de binários externos resolvidos via `PATH` por padrão: `ffprobe` (probe) e `ffmpeg`
  (transcode) — configuráveis via `akari.mediaprobe.ffprobe.binary` e
  `akari.playback.ffmpeg.binary`.
- Depende de uma instância local do `rclone rcd` (não containerizada — propositalmente, para
  enxergar o mesmo filesystem do host que a aplicação) para operações no storage provider `MEGA`
  e para obscurecer credenciais sensíveis.
- Integra três APIs externas sobre a rede: AniList (GraphQL público, sem autenticação), TMDB
  (REST, autenticado por token vindo de uma preferência administrativa) e a API do GitHub (só para
  descobrir a release mais recente do dataset de mapeamento do `anibridge`).
- Ambiente de desenvolvimento usa H2 em memória (modo de compatibilidade PostgreSQL) para testes;
  Postgres real via `docker-compose.yml` para desenvolvimento/produção.
- Documentação de API é servida via Swagger UI (`quarkus-smallrye-openapi`), com `mp.openapi.
  scan.disable=true` — ou seja, o contrato servido é exatamente o arquivo `openapi.yaml`, não um
  escaneamento automático das anotações do código.
