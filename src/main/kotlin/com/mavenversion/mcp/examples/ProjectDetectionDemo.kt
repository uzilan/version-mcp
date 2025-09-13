package com.mavenversion.mcp.examples

import com.mavenversion.mcp.files.ProjectFileDetector
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Demonstration of the ProjectFileDetector functionality
 */
fun main() {
    log.info { "Starting Project Detection Demo" }
    
    val detector = ProjectFileDetector()
    
    // Demo 1: Detect current project
    log.info { "Demo 1: Detecting current project" }
    val currentProjectResult = detector.detectProject(".")
    
    if (currentProjectResult.isSuccess) {
        val projectInfo = currentProjectResult.getOrThrow()
        log.info { "Current project type: ${projectInfo.type}" }
        log.info { "Root path: ${projectInfo.rootPath}" }
        log.info { "Build files found: ${projectInfo.buildFiles.size}" }
        
        projectInfo.buildFiles.forEach { buildFile ->
            log.info { "  - ${buildFile.type.fileName} at ${buildFile.path}" }
            log.info { "    Readable: ${buildFile.isReadable}, Writable: ${buildFile.isWritable}" }
        }
        
        // Find primary build file
        val primaryBuildFile = detector.findPrimaryBuildFile(projectInfo)
        if (primaryBuildFile != null) {
            log.info { "Primary build file: ${primaryBuildFile.path}" }
            
            // Validate access
            val accessResult = detector.validateBuildFileAccess(primaryBuildFile)
            if (accessResult.isSuccess) {
                log.info { "Build file access validated successfully" }
            } else {
                log.error { "Build file access validation failed: ${accessResult.exceptionOrNull()?.message}" }
            }
        } else {
            log.warn { "No primary build file found" }
        }
        
        // Check for multiple build files
        ProjectFileDetector.BuildFileType.values().forEach { fileType ->
            if (detector.hasMultipleBuildFiles(projectInfo, fileType)) {
                val files = detector.getBuildFilesByType(projectInfo, fileType)
                log.info { "Multiple ${fileType.fileName} files found: ${files.size}" }
            }
        }
        
    } else {
        log.error { "Failed to detect current project: ${currentProjectResult.exceptionOrNull()?.message}" }
    }
    
    log.info { "Project Detection Demo completed" }
}