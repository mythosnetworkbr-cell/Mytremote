# Mythøs Remote

Aplicativo Android de acesso remoto entre dispositivos, inspirado na experiência de ferramentas como AnyDesk, com autorização explícita no aparelho remoto.

## Implementado

- Android/Kotlin + Jetpack Compose.
- Login/cadastro Supabase.
- Registro automático do dispositivo e ID `MYT-XXXXXXXX`.
- Descoberta por ID e criação de solicitação de sessão.
- Banco Supabase com RLS para dispositivos, sessões e sinais.
- MediaProjection para compartilhamento autorizado da tela.
- Serviço de acessibilidade preparado para gestos autorizados.
- Dependência WebRTC incluída para a camada P2P.
- GitHub Actions gera o APK Debug.

## Fluxo

1. Instale o APK nos dois Androids.
2. Crie uma conta/entre nos dois aparelhos.
3. Cada aparelho recebe um ID Mythøs.
4. No controlador, informe o ID do outro aparelho e solicite conexão.
5. No aparelho remoto, autorize a sessão e o compartilhamento de tela.

O controle remoto depende das permissões e autorizações exigidas pelo Android; o projeto não implementa acesso oculto.
