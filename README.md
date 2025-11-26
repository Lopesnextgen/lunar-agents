# Lunar Client Agents
Conjunto de agentes Java (Java Agents) para modificar o comportamento do Lunar Client/Minecraft em tempo de execução. Estes agentes foram pensados para uso com o launcher lunar-client-qt (https://github.com/Nilsen84/lunar-client-qt), permitindo habilitar/desabilitar recursos, ajustar comportamentos e personalizar textos sem alterar os binários originais.

ATENÇÃO
- O uso destes agentes pode violar os Termos de Uso de servidores e/ou do próprio cliente. Use por sua conta e risco.
- Alguns agentes podem constituir vantagem injusta (unfair advantage), como HitDelayFix e StaffEnable. Não utilize em servidores onde isso não é permitido.
- Projeto focado principalmente na versão 1.8.9 do Minecraft (nomes mapeados/obfuscados dessa linha), podendo não funcionar em outras versões.

Sumário
- Visão geral e requisitos
- Estrutura do projeto
- Como compilar
- Como usar os agentes (Windows e multiplataforma)
- Documentação de cada agente e opções
- Compatibilidade e limitações
- Solução de problemas (Troubleshooting)
- Contribuindo
- Licença

Visão geral e requisitos
- Java: JDK 16 ou superior. O projeto está configurado com source/targetCompatibility = 16.
- Build: Gradle Wrapper incluso (não é necessário instalar Gradle).
- Sistemas: Windows, Linux e macOS (exemplos abaixo priorizam Windows por conveniência).
- Launcher recomendado: lunar-client-qt.

Estrutura do projeto
- Raiz
  - commons: utilitários compartilhados (ASM helpers, Utils, etc.).
  - agents: pasta pai de todos os subprojetos de agentes.
    - CrackedAccount
    - CustomAutoGG
    - CustomLevelHead
    - DamageIndicator
    - FixTargetCalculate
    - HitDelayFix
    - LevelHeadNicks
    - LunarEnable (inclui BukkitTransformer e MetadataTransformer)
    - LunarPacksFix
    - NoJumpDelay
    - NoPinnedServers
    - RemovePlus
    - StaffEnable
    - TeamsAutoGG

Como compilar
1) Clonar este repositório
2) No Windows, abrir um terminal na raiz do projeto e executar:
   - gradlew.bat build
3) Em Linux/macOS:
   - ./gradlew build
4) Os jars “prontos para uso” (com dependências sombreadas pelo Shadow) serão gerados em:
   - agents/<NomeDoAgente>/build/<NomeDoAgente>.jar

Dica: Você pode também construir apenas um agente específico:
- Windows: gradlew.bat :agents:CustomAutoGG:build
- Linux/macOS: ./gradlew :agents:CustomAutoGG:build

Como usar os agentes
Os agentes Java são carregados via parâmetro -javaagent na linha de comando de inicialização da JVM.

- Forma geral
  java -javaagent:"C:\caminho\para\Agente.jar"=opcao -jar SeuLauncher.jar

- Observações
  - A string após = é repassada como argumento único para o método premain(String, Instrumentation) do agente.
  - Se a opção contiver espaços, envolva-a entre aspas. Em Windows, prefira aspas duplas externas e escape de aspas internas quando necessário.
  - Ao usar lunar-client-qt, consulte a documentação do projeto para saber onde injetar parâmetros adicionais de JVM. Em geral, basta acrescentar -javaagent para cada agente desejado.

Exemplos (Windows)
- CustomAutoGG com mensagem personalizada:
  java -javaagent:"C:\agentes\CustomAutoGG.jar"="gg wp" -jar lunar-client-qt.jar

- CrackedAccount com nome de usuário:
  java -javaagent:"C:\agentes\CrackedAccount.jar"=MeuNick -jar lunar-client-qt.jar

- LevelHeadNicks fixando nível 12:
  java -javaagent:"C:\agentes\LevelHeadNicks.jar"=12 -jar lunar-client-qt.jar

Documentação dos agentes
Abaixo um resumo do que cada agente faz e como configurá-lo (quando aplicável):

- CrackedAccount
  - Permite jogar singleplayer e em servidores “cracked” usando o Lunar Client.
  - Opção: nome de usuário a ser utilizado (String). Ex.: =MeuNick

- CustomAutoGG
  - Substitui o texto padrão “/achat gg” por uma mensagem personalizada.
  - Opção: mensagem (String). Ex.: ="gg wp" (use aspas se houver espaços).

- CustomLevelHead
  - Substitui a legenda/phrasing “Level: ” por um texto customizado (ex.: “Nível: ”).
  - Opção: frase (String). Ex.: ="Nível: "

- DamageIndicator
  - Exibe no HUD informações do alvo sob a mira (nome e vida), acima da mira.
  - Sem opções.

- FixTargetCalculate
  - Ajusta o cálculo de mira/seleção de alvo (hook em EntityRenderer#getMouseOver) para melhorar a coerência do objeto “objectMouseOver”.
  - Opção: debug (habilita logs de verificação). Ex.: =debug
  - Como verificar se está funcionando:
    - Inicie o cliente com: -javaagent:"C:\\agentes\\FixTargetCalculate.jar"=debug
    - No console/terminal você deverá ver (entre outras) linhas como:
      - [FixTargetCalculate] Agent loaded (debug=ON)
      - [FixTargetCalculate] Patched EntityRenderer#getMouseOver (name: getMouseOver | func_78473_a)
      - [FixTargetCalculate] Hook active
      - Durante o jogo, ao mirar em jogadores, logs eventuais (limitados a 1/s) como:
        - [FixTargetCalculate] Lock -> <nome>(id=123)
        - [FixTargetCalculate] Keep lock -> <nome>(id=123)
        - [FixTargetCalculate] Override objectMouseOver -> <nome>(id=123)
        - [FixTargetCalculate] No target -> release lock
    - Onde ver os logs: execute o launcher pelo terminal (cmd/PowerShell) para que o output da JVM fique visível.

- HitDelayFix
  - Remove o cooldown aleatório de ataque em 1.8, fazendo a espada “não travar” aleatoriamente.
  - Vantagem injusta em PVP. Use por sua conta e risco.
  - Sem opções.

- LevelHeadNicks
  - Força o valor de nível usado em um trecho que normalmente utilizaria aleatoriedade (ThreadLocalRandom.nextInt(25)).
  - Opção: inteiro. Recomenda-se um valor entre 0 e 24. Ex.: =12

- LunarEnable
  - Reabilita mods desativados pelo cliente (ex.: FreeLook, Auto Text Hotkey). Também inclui ajustes em metadados e integração Bukkit.
  - Sem opções.

- LunarPacksFix
  - Restaura o uso de overlays de texturas do Lunar.
  - Sem opções.

- NoJumpDelay
  - Remove o atraso entre pulos (cooldown de pulo).
  - Sem opções.

- NoPinnedServers
  - Remove servidores “fixados/pinned” da lista.
  - Sem opções.

- RemovePlus
  - Remove o ícone/literal “+” do Lunar+.
  - Sem opções.

- StaffEnable
  - Habilita mods exclusivos para staff (atualmente, xray). Vídeo demonstrativo do xray embutido no Lunar: https://www.youtube.com/watch?v=xWZsFqH9TwQ
  - Vantagem injusta. Use por sua conta e risco.
  - Sem opções.

- TeamsAutoGG
  - Faz o Auto GG funcionar em modos de times.
  - Sem opções.

Compatibilidade e limitações
- Foco em Minecraft 1.8.9. Pode não funcionar (ou funcionar parcialmente) em outras versões.
- Mudanças no cliente/launcher podem quebrar os hooks a qualquer momento. Se um agente “parar de funcionar”, atualizações de nomes/assinaturas podem ser necessárias.

Solução de problemas (Troubleshooting)
- A JVM não inicia ou encerra ao carregar um agente
  - Verifique se o caminho do jar está correto e se você tem permissão de leitura.
  - Confirme que está usando Java 16+.
  - Tente executar sem o agente para isolar o problema.

- O agente “não faz efeito”
  - Certifique-se de estar na versão suportada (ex.: 1.8.9).
  - Confira se o agente está realmente sendo carregado (-javaagent precisa apontar para o jar correto).
  - Em agentes com opções, valide a sintaxe: -javaagent:"...jar"=valor (aspas quando houver espaços).

- Conflitos entre agentes
  - Evite carregar múltiplos agentes que transformam a mesma classe/método de formas incompatíveis.

Contribuindo
- Pull Requests são bem-vindos. Mantenha as alterações tanto quanto possível isoladas por agente.
- Para novos agentes, crie um subprojeto em agents/<NovoAgente> e adicione um gradle.properties com agentClass=<pacote>.Agent.
- Siga o padrão existente: método static premain(String, Instrumentation) e uso de ShadowJar para empacotar dependências.

Licença
- GPLv3 (ver arquivo LICENSE). Ao contribuir, você concorda em licenciar suas contribuições sob os termos da GPLv3.