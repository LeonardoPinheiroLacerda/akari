# Imagem LinuxServer do qBittorrent com a WebUI alternativa qbtmud embutida.
FROM lscr.io/linuxserver/qbittorrent:5.2.3_v2.0.14-ls475

ARG QBTMUD_VERSION=3.0.0

RUN apk add --no-cache --virtual .qbtmud-deps curl unzip ca-certificates \
    && mkdir -p /qbtmud \
    && curl -fsSL -o /tmp/qbtmud.zip \
       "https://github.com/lantean-code/qbtmud/releases/download/${QBTMUD_VERSION}/qbt-mud-v${QBTMUD_VERSION}-intl.zip" \
    && unzip -q /tmp/qbtmud.zip -d /qbtmud \
    && rm /tmp/qbtmud.zip \
    && apk del .qbtmud-deps

# Config inicial: a init da imagem copia /defaults/qBittorrent.conf para /config só quando ainda
# não existe nenhuma, então as mudanças feitas depois pela WebUI são preservadas.
COPY qBittorrent.conf /defaults/qBittorrent.conf
