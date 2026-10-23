# Media Downloader Android v2.0 — Build 20

- Progresso REAL por blocos de 45 segundos de áudio (WAV 16 kHz mono PCM16).
- Sobreposição de 1 segundo nas bordas, com seleção de segmentos por ponto médio.
- Atualização percentual somente quando um bloco termina; cronômetro durante o bloco.
- Uma segunda tentativa quando a biblioteca devolve erro; erro identifica bloco.
- Texto parcial apresentado na interface após cada bloco; TXT e SRT ao terminar.
- Arquivos de áudio recortados por leitura sequencial, sem carregar áudio completo na memória.
- Modelo Whisper armazenado no diretório interno, reaproveitado entre atualizações do APK.

**Limites:** não há callback interno de progresso do Whisper 1.0.0. Um bloco que trave dentro de código nativo pode permanecer em execução; a interface avisa após 180 segundos, mas não promete interrompê-lo. O resultado depende do desempenho do aparelho. Compilação e execução no Android ainda não verificadas.
