=== ":material-linux: Linux / :material-apple: macOS"

    ```shell
    curl -fsSL -o kotlin https://kotl.in/cli.sh && chmod +x kotlin && ./kotlin update -c
    ```

=== ":material-microsoft-windows: Windows"

    ```powershell title="PowerShell"
    Invoke-WebRequest -OutFile kotlin.bat -Uri https://kotl.in/cli.bat; ./kotlin update -c
    ```
    
    ```shell title="cmd.exe"
    curl -fsSL -o kotlin.bat https://kotl.in/cli.bat && call kotlin update -c
    ```