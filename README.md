# Mythøs Remote

Aplicativo Android de acesso remoto entre dispositivos, inspirado na experiência de ferramentas como AnyDesk, com autorização explícita no aparelho remoto.

## Estado atual

- Projeto Android/Kotlin inicial criado.
- Interface para informar ID e iniciar conexão.
- Base de captura de tela com MediaProjection.
- Serviço de acessibilidade preparado para gestos autorizados.
- Serviço em primeiro plano para compartilhamento de tela.
- GitHub Actions configurado para gerar `assembleDebug`.

## Próximas etapas

1. Autenticação de usuários.
2. Cadastro e pareamento por ID.
3. Sinalização de sessão.
4. WebRTC para vídeo/áudio e transporte P2P.
5. Canal de comandos para toque, gesto e teclado.
6. Tela de sessão com qualidade/FPS e encerramento.
7. Firebase para conta, dispositivos e notificações.
8. Testes em Android 10–16 e tratamento de permissões.

O controle remoto deve sempre depender das permissões e autorizações exigidas pelo Android; o projeto não implementa acesso oculto.
