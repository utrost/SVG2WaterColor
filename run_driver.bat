@echo off
setlocal

REM Check if Python is installed
python --version >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    echo Error: Python is not installed or not in your PATH.
    echo Please install Python from https://www.python.org/
    pause
    exit /b 1
)

REM Check if pyaxidraw is installed (simple check)
python -c "import pyaxidraw" >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    echo Warning: pyaxidraw module not found.
    echo Please install it using: pip install -r driver\requirements.txt
    echo (Note: You might need to install the AxiDraw software or use the direct link in requirements.txt)
    echo.
    echo Falling back to MOCK mode? (Press Y for Mock, N to Exit)
    set /p "choice=>"
    if /i "%choice%"=="Y" (
        set ARGS=--mock
    ) else (
        exit /b 1
    )
)

IF "%~1"=="" (
    echo Usage: run_driver.bat [json_file]
    echo Example: run_driver.bat simpleSampleSVG.json
    exit /b 1
)

echo Running Driver with input: %1 %ARGS%
python driver\driver.py "%~1" %ARGS%
pause
