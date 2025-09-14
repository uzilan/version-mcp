#!/bin/bash

# Maven Version MCP Server Startup Script
# This script starts the Maven Version MCP Server with proper configuration

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
JAR_FILE=""
CONFIG_FILE=""
LOG_LEVEL="INFO"
WORKING_DIR=""
ENABLE_METRICS=false
ENABLE_HEALTH_CHECK=true

# Function to print usage
print_usage() {
    echo -e "${BLUE}Maven Version MCP Server Startup Script${NC}"
    echo ""
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  -j, --jar FILE          Path to the JAR file (required)"
    echo "  -c, --config FILE       Path to configuration file (optional)"
    echo "  -l, --log-level LEVEL   Set log level (DEBUG, INFO, WARN, ERROR)"
    echo "  -w, --working-dir DIR   Set working directory"
    echo "  -m, --enable-metrics    Enable metrics collection"
    echo "  -h, --enable-health     Enable health check endpoint"
    echo "  --help                  Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 -j maven-version-mcp-server.jar"
    echo "  $0 -j maven-version-mcp-server.jar -c config.json -l DEBUG"
    echo "  $0 -j maven-version-mcp-server.jar -w /opt/mcp-server -m -h"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -j|--jar)
            JAR_FILE="$2"
            shift 2
            ;;
        -c|--config)
            CONFIG_FILE="$2"
            shift 2
            ;;
        -l|--log-level)
            LOG_LEVEL="$2"
            shift 2
            ;;
        -w|--working-dir)
            WORKING_DIR="$2"
            shift 2
            ;;
        -m|--enable-metrics)
            ENABLE_METRICS=true
            shift
            ;;
        -h|--enable-health)
            ENABLE_HEALTH_CHECK=true
            shift
            ;;
        --help)
            print_usage
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            print_usage
            exit 1
            ;;
    esac
done

# Validate required parameters
if [[ -z "$JAR_FILE" ]]; then
    echo -e "${RED}Error: JAR file is required${NC}"
    print_usage
    exit 1
fi

# Check if JAR file exists
if [[ ! -f "$JAR_FILE" ]]; then
    echo -e "${RED}Error: JAR file not found: $JAR_FILE${NC}"
    exit 1
fi

# Check if Java is available
if ! command -v java &> /dev/null; then
    echo -e "${RED}Error: Java is not installed or not in PATH${NC}"
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [[ "$JAVA_VERSION" -lt 21 ]]; then
    echo -e "${YELLOW}Warning: Java 21 or higher is recommended. Current version: $JAVA_VERSION${NC}"
fi

# Build command arguments
ARGS=()

if [[ -n "$CONFIG_FILE" ]]; then
    if [[ -f "$CONFIG_FILE" ]]; then
        echo -e "${GREEN}Using configuration file: $CONFIG_FILE${NC}"
    else
        echo -e "${YELLOW}Warning: Configuration file not found: $CONFIG_FILE${NC}"
    fi
fi

ARGS+=("--log-level" "$LOG_LEVEL")

if [[ -n "$WORKING_DIR" ]]; then
    ARGS+=("--working-dir" "$WORKING_DIR")
fi

if [[ "$ENABLE_METRICS" == true ]]; then
    ARGS+=("--enable-metrics" "true")
fi

if [[ "$ENABLE_HEALTH_CHECK" == true ]]; then
    ARGS+=("--enable-health-check" "true")
fi

# Set working directory if specified
if [[ -n "$WORKING_DIR" ]]; then
    cd "$WORKING_DIR"
fi

# Create logs directory if it doesn't exist
mkdir -p logs

# Start the server
echo -e "${GREEN}Starting Maven Version MCP Server...${NC}"
echo -e "${BLUE}JAR: $JAR_FILE${NC}"
echo -e "${BLUE}Arguments: ${ARGS[*]}${NC}"
echo ""

# Run the server
java -jar "$JAR_FILE" "${ARGS[@]}"
