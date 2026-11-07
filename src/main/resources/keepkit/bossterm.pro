# ProGuard 7.8 can generate invalid frames in the renderer's pointer handler.
# Preserve the method bodies for the affected BossTerm desktop artifact.
-keep,includecode class ai.rever.bossterm.compose.ui.ProperTerminalKt { *; }
