-- Um rastreio passa a ter várias queries (os grupos escrevem o nome do anime de jeitos diferentes);
-- cada rastreio existente vira uma lista com a query que já tinha.
ALTER TABLE tracked_anime
      ADD COLUMN queries JSONB NULL;

UPDATE tracked_anime
SET queries = jsonb_build_array(query);

ALTER TABLE tracked_anime
      ALTER COLUMN queries SET NOT NULL;

ALTER TABLE tracked_anime
      DROP COLUMN query;
