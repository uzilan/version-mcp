package com.mavenversion.mcp.reliability

/**
 * Base exception for MCP-related errors
 */
abstract class MCPException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when MCP connection fails
 */
class MCPConnectionException(message: String, cause: Throwable? = null) : MCPException(message, cause)

/**
 * Exception thrown when MCP tool call fails
 */
class MCPToolException(message: String, cause: Throwable? = null) : MCPException(message, cause)

/**
 * Exception thrown when Playwright MCP operations fail
 */
class PlaywrightMCPException(message: String, cause: Throwable? = null) : MCPException(message, cause)

/**
 * Exception thrown when network operations fail
 */
class NetworkException(message: String, cause: Throwable? = null) : MCPException(message, cause)

/**
 * Exception thrown when parsing operations fail
 */
class ParsingException(message: String, cause: Throwable? = null) : MCPException(message, cause)

/**
 * Exception thrown when rate limiting is triggered
 */
class RateLimitException(message: String, cause: Throwable? = null) : MCPException(message, cause)

/**
 * Exception thrown when timeout occurs
 */
class TimeoutException(message: String, cause: Throwable? = null) : MCPException(message, cause)