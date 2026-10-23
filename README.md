# Media Downloader Android v1.6 — Build 16

Projeto completo baseado na v1.5.

- Link: **Baixar MP4** (sem Whisper) ou **Transcrever fala** (sem baixar MP4).
- Transcrição: mostra textarea, botão copiar e salva TXT/SRT em Downloads/MediaDownloader.
- Arquivo local: área separada para transcrever áudio.
- Modelo Whisper: baixado automaticamente na primeira transcrição e reutilizado. Removido botão manual.
- Rodapé simplificado.
- Corrigida distinção entre erro no download de vídeo e erro na transcrição.

**Atenção:** código ainda não compilado nem validado em aparelho nesta entrega. A extração via FFmpeg e a execução Whisper continuam dependentes de testes reais. O download MP4 reutiliza a implementação validada na v1.2.
