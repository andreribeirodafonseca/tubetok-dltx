# Media Downloader Android v0.8 — Atualizador yt-dlp

Correcoes: tentativa de atualizar yt-dlp na inicializacao e por botao; mensagens curtas em portugues; formato MP4 preferido para reduzir dependencias de mesclagem; versao 0.8 Build 8.

**Limites importantes:** TikTok pode exigir impersonation/curl_cffi que nao esta disponivel neste runtime Android. A atualizacao do yt-dlp nao garante que TikTok funcione. Whisper e transcricao local continuam pendentes. Projeto nao compilado nem testado em dispositivo neste ambiente. Use o Codemagic e envie o log se falhar.

Nao contem Cloudflare, chaves ou banco remoto. O app consulta a internet para downloads e atualizacao do yt-dlp.
