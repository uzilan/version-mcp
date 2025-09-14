@echo off
setlocal enabledelayedexpansion

REM Maven Version MCP Server Startup Script for Windows
REM This script starts the Maven Version MCP Server with proper configuration

set "JAR_FILE="
set "CONFIG_FILE="
set "LOG_LEVEL=INFO"
set "WORKING_DIR="
set "ENABLE_METRICS=false"
set "ENABLE_HEALTH_CHECK=true"

REM Parse command line arguments
:parse_args
if "%~1"=="" goto :validate_args
if "%~1"=="-j" (
    set "JAR_FILE=%~2"
    shift
    shift
    goto :parse_args
)
if "%~1"=="--jar" (
    set "JAR_FILE=%~2"
    shift
    shift
    goto :parse_args
)
if "%~1"=="-c" (
    set "CONFIG_FILE=%~2"
    shift
    shift
    goto :parse_args
)
if "%~1"=="--config" (
    set "CONFIG_FILE=%~2"
    shift
    shift
    goto :parse_args
)
if "%~1"=="-l" (
    set "LOG_LEVEL=%~2"
    shift
    shift
    goto :parse_args
)
if "%~1"=="--log-level" (
    set "LOG_LEVEL=%~2"
    shift
    shift
    goto :parse_args
)
if "%~1"=="-w" (
    set "WORKING_DIR=%~2"
    shift
    shift
    goto :parse_args
)
if "%~1"=="--working-dir" (
    set "WORKING_DIR=%~2"
    shift
    shift
    goto :parse_args
)
if "%~1"=="-m" (
    set "ENABLE_METRICS=true"
    shift
    goto :parse_args
)
if "%~1"=="--enable-metrics" (
    set "ENABLE_METRICS=true"
    shift
    goto :parse_args
)
if "%~1"=="-h" (
    set "ENABLE_HEALTH_CHECK=true"
    shift
    goto :parse_args
)
if "%~1"=="--enable-health" (
    set "ENABLE_HEALTH_CHECK=true"
    shift
    goto :parse_args
)
if "%~1"=="--help" (
    call :print_usage
    exit /b 0
)
echo Unknown option: %~1
call :print_usage
exit /b 1

:validate_args
if "%JAR_FILE%"=="" (
    echo Error: JAR file is required
    call :print_usage
    exit /b 1
)

if not exist "%JAR_FILE%" (
    echo Error: JAR file not found: %JAR_FILE%
    exit /b 1
)

REM Check if Java is available
java -version >nul 2>&1
if errorlevel 1 (
    echo Error: Java is not installed or not in PATH
    exit /b 1
)

REM Build command arguments
set "ARGS=--log-level %LOG_LEVEL%"

if not "%WORKING_DIR%"=="" (
    set "ARGS=%ARGS% --working-dir %WORKING_DIR%"
)

if "%ENABLE_METRICS%"=="true" (
    set "ARGS=%ARGS% --enable-metrics true"
)

if "%ENABLE_HEALTH_CHECK%"=="true" (
    set "ARGS=%ARGS% --enable-health-check true"
)

REM Set working directory if specified
if not "%WORKING_DIR%"=="" (
    cd /d "%WORKING_DIR%"
)

REM Create logs directory if it doesn't exist
if not exist "logs" mkdir logs

REM Start the server
echo Starting Maven Version MCP Server...
echo JAR: %JAR_FILE%
echo Arguments: %ARGS%
echo.

REM Run the server
java -jar "%JAR_FILE%" %ARGS%
exit /b %errorlevel%

:print_usage
echo Maven Version MCP Server Startup Script
echo.
echo Usage: %~nx0 [options]
echo.
echo Options:
echo   -j, --jar FILE          Path to the JAR file (required)
echo   -c, --config FILE       Path to configuration file (optional)
echo   -l, --log-level LEVEL   Set log level (DEBUG, INFO, WARN, ERROR)
echo   -w, --working-dir DIR   Set working directory
echo   -m, --enable-metrics    Enable metrics collection
echo   -h, --enable-health     Enable health check endpoint
echo   --help                  Show this help message
echo.
echo Examples:
echo   %~nx0 -j maven-version-mcp-server.jar
echo   %~nx0 -j maven-version-mcp-server.jar -c config.json -l DEBUG
echo   %~nx0 -j maven-version-mcp-server.jar -w C:\opt\mcp-server -m -h
goto :eof
