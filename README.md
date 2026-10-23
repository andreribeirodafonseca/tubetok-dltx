# Media Downloader Android v1.8 — Build 18

Correção de transcrição: o áudio baixado (M4A/AAC/WebM/Opus, conforme suporte de decodificação do dispositivo) é convertido localmente com MediaExtractor + MediaCodec para WAV PCM16 mono 16 kHz, antes de chamar o Whisper.

O download de MP4 continua em fluxo independente, preservado da v1.7. TXT, SRT, copiar texto e transcrição de arquivo local continuam disponíveis.

A conversão usa arquivo PCM intermediário no cache, evitando acumular áudio inteiro em memória. Implementa downmix mono e reamostragem linear. Para áudio muito longo, o limite WAV RIFF de ~2 GB é respeitado.

**Importante:** código-fonte preparado, mas não compilado nem testado em dispositivo nesta sessão. A compatibilidade de contêineres/codecs depende dos decodificadores do Android do aparelho. A biblioteca Whisper existente não foi alterada.
