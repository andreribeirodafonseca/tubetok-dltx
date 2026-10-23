# Media Downloader Android v0.5 — correção AndroidX

Adicionado `gradle.properties` na raiz com `android.useAndroidX=true` e `android.enableJetifier=true`, exigidos pelas dependências AndroidX do youtubedl-android.

Envie também o novo arquivo `gradle.properties` ao GitHub, junto com os demais arquivos alterados. Compile novamente no Codemagic.

Esta versão não integra Whisper local; download Android permanece experimental e não foi validado em dispositivo.
