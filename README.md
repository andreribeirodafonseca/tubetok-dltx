# TubTok v2.9 — Build 29

Proteção contra saída acidental durante download, conversão, transcrição, tradução e preparação do modelo.

- Botão Voltar abre diálogo de confirmação quando há tarefas ativas.
- Serviço Android em primeiro plano e notificação enquanto a tarefa roda, para reduzir encerramentos pelo sistema quando a tela é minimizada.
- Sem checkpoint nem retomada de transcrição.
- FFmpeg separado permanece ausente.
- Preservadas as funções da v2.8.

Limites: Android não permite interceptar Forçar parada, encerramento pelo sistema, ou gesto de remover a tarefa dos recentes para exigir confirmação. O serviço aumenta a prioridade do processo, mas não garante execução ininterrupta. O código ainda executa as tarefas na Activity; para resiliência completa ao descarte da Activity seria preciso mover o motor para o Service.

Não foi compilado ou testado em aparelho.


## v2.10 / Build 30
- Logo PNG com transparência no cabeçalho, substituindo o símbolo ▶.
- Versão exibida no cabeçalho e rodapé derivada dos metadados nativos do APK.
- Preservados os demais recursos da v2.9.
