# Media Downloader Android v0.3 — identificação de versão

Esta versão acrescenta **v0.3** ao cabeçalho e **VERSÃO ANDROID 0.3 • BUILD 3 • 23/10/2026** ao rodapé, e atualiza `versionCode` e `versionName` do APK.

O motor experimental da v0.2 foi preservado. A transcrição Whisper local ainda não foi integrada.

Extraia e envie os arquivos ao GitHub, substituindo os correspondentes, e gere novo APK no Codemagic. Se continuar aparecendo a versão anterior, o APK antigo está sendo executado.


## Versão 0.4 — correção de dependências
- Corrigidas as coordenadas Maven para `io.github.junkfood02.youtubedl-android:library:0.18.1` e `:ffmpeg:0.18.1` conforme documentação oficial do projeto.
- Ativado `android:extractNativeLibs="true"` no manifesto.
- Ajustado callback de progresso para assinatura de dois argumentos documentada.
- Atualizados `versionCode` 4, `versionName` 0.4 e identificação na interface.
- Mantido `codemagic.yaml` já funcional (Gradle global).
- **Não foi possível executar a compilação Android nem testar downloads reais neste ambiente.** Whisper local ainda não integrado.

No GitHub, envie os arquivos extraídos substituindo os arquivos existentes. Não exclua o `codemagic.yaml`.
