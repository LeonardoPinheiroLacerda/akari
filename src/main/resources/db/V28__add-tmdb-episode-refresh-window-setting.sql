-- Janela, a partir da data de exibição, em que os dados TMDB dos episódios pareados são
-- rebuscados periodicamente (o TMDB costuma completar miniatura, nome e sinopse dias depois).
INSERT INTO setting (key, value, secret, updated_at) VALUES
    ('tmdb.episode.refresh-window', 'P30D', FALSE, now());
