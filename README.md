# Lucid Dream - versão Android

Port do seu jogo (Java + LWJGL/OpenGL 2.1) para Android (OpenGL ES 2.0).
A lógica do jogo (mundo, criaturas, mentes, música) é a mesma: para a mesma imagem PNG, os hashes de
seed / mundo / áudio são idênticos aos da versão de PC.

## Como gerar o APK sem Android Studio (GitHub Actions)

1. No GitHub: **New repository** (pode ser privado). Nome qualquer, ex.: `lucid-dream-android`.
2. Envie **todo o conteúdo desta pasta** para o repositório (incluindo a pasta oculta `.github`):
   - jeito fácil: *Add file > Upload files* e arraste tudo; ou use o GitHub Desktop / `git push`.
   - Se a aba **Actions** não mostrar o workflow "Build APK", crie o arquivo na mão:
     *Add file > Create new file*, nome `.github/workflows/build.yml`, e cole o conteúdo do arquivo
     `.github/workflows/build.yml` desta pasta.
3. Abra a aba **Actions**. O workflow **Build APK** roda sozinho a cada envio (ou clique em
   *Run workflow*). Leva uns 3 a 6 minutos.
4. Quando ficar verde, clique na execução e, em **Artifacts**, baixe **LucidDream-apk** (um .zip com
   `app-debug.apk` dentro).
5. Passe o `.apk` para o celular, abra e instale (o Android vai pedir para permitir "instalar apps
   desconhecidos" para o app que você usou para abrir o arquivo).

Se o build ficar vermelho, abra o passo que falhou, copie as últimas linhas do log e mande para quem
está te ajudando.

## Controles (tela de toque, celular deitado)

| Ação | Como |
|---|---|
| Andar | Segurar o dedo na metade esquerda e arrastar (joystick) |
| Olhar | Arrastar o dedo na metade direita |
| Pular | Botão redondo **Jump** |
| Correr | Botão **Run** (liga/desliga) |
| Falar com o guia / entrar num pilar (hub) | Botão **Use** |
| Ler a mente de uma criatura (nos sonhos) | Olhe para ela e toque **Mind** |
| Voltar ao hub | Botão **Hub** |
| Trocar a imagem | Botão **Image** (no hub) ou Menu > Choose another image |
| Menu / configurações | Botão **Menu** ou botão Voltar do Android |

Na primeira vez aparece uma tela inicial: **Choose an image** abre a galeria; **Random dream** gera um
mundo sem precisar de imagem. A última imagem escolhida é lembrada na próxima vez.

## Desempenho em celular fraco

O jogo já começa no preset **Low** (2 chunks de distância, pouca vegetação). Se ainda engasgar:
Menu > Settings > baixe *Render distance* para 1 e *Trees and grass* para 0-25%.

## O que mudou em relação ao PC

- `Main` (GLFW) virou `core/Game.java` (sem janela; recebe toques) + `MainActivity` / `GameView`.
- `Gfx`, `GpuMesh`: LWJGL/GL 2.1 -> `GLES20`, shaders GLSL 1.20 -> GLSL ES 1.00; texto via `Canvas`.
- `Features`: `ImageIO` -> leitura por faixas de linhas (`AndroidImage`), para fotos grandes não estourarem a memória.
- `Synth`: `javax.sound` -> `AudioTrack`.
- Menu de configurações refeito com botões grandes, em duas colunas. Sem VSync (o Android controla).
- Passo de física continua em 60 Hz; câmera 0.2 - 300 m de alcance.

Observação: PNG gera exatamente o mesmo mundo que no PC. Em JPEG, o decodificador do Android pode
diferir por poucos valores de pixel do Java, então o mundo pode sair diferente do que você viu no PC.
