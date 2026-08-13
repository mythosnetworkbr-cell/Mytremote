# Suporte Mythøs — arquitetura

## Objetivo

Aplicativo de suporte remoto entre Androids, inspirado no fluxo de ferramentas como AnyDesk, mas com autorização explícita no aparelho atendido.

## Camadas

- **Android UI:** Kotlin + Jetpack Compose.
- **Captura:** MediaProjection em foreground service.
- **Controle:** AccessibilityService com gestos e ações do sistema, somente depois de o proprietário ativar o serviço.
- **Transporte de mídia:** WebRTC, com SDP/ICE negociados pelo canal de sinalização.
- **Sinalização:** WebSocket/Node.js/TypeScript em `server/`.
- **LAN:** canal TCP existente para testes locais e fallback.
- **Identidade:** ID persistente `MYT-XXXXXXXX`; não depende de Supabase.

## Fluxo de uma sessão

1. O atendente informa o ID do aparelho.
2. O servidor localiza o dispositivo online.
3. O aparelho atendido mostra uma solicitação identificando o solicitante.
4. O proprietário aceita ou recusa.
5. O Android atendido mostra a autorização oficial de captura de tela.
6. Depois das autorizações, os dois lados negociam WebRTC.
7. A tela é enviada como vídeo WebRTC.
8. Toques/gestos são enviados por DataChannel e executados pelo AccessibilityService.
9. Encerrar a sessão fecha WebRTC, o canal de controle e a captura.

## Limites obrigatórios do Android

O aplicativo não pode obter silenciosamente a tela ou controlar outros aplicativos. A captura precisa do consentimento de MediaProjection e o controle por gestos depende da ativação explícita do serviço de acessibilidade pelo proprietário.

## Produção

Para acesso pela Internet, o servidor deve ser publicado com `wss://` atrás de TLS. Para redes NAT difíceis, configure um servidor TURN. O servidor de sinalização não transporta a tela; ele apenas faz o pareamento e troca SDP/ICE.
