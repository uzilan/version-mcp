package com.mavenversion.mcp.files

import mu.KotlinLogging
import org.dom4j.Document
import org.dom4j.DocumentHelper
import org.dom4j.Element
import org.dom4j.io.OutputFormat
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import java.io.File
import java.io.FileWriter
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.isWritable

private val log = KotlinLogging.logger {}

/**
 * Manages Maven pom.xml file operations including dependency management
 */
class MavenFileManager {

    /**
     * Represents a Maven dependency
     */
    data class MavenDependency(
        val groupId: String,
        val artifactId: String,
        val version: String? = null,
        val scope: String? = null,
        val type: String? = null,
        val classifier: String? = null
    ) {
        /**
         * Get the dependency coordinate string (groupId:artifactId:version)
         */
        fun getCoordinate(): String {
            return if (version != null) {
                "$groupId:$artifactId:$version"
            } else {
                "$groupId:$artifactId"
            }
        }

        /**
         * Check if this dependency matches another by groupId and artifactId
         */
        fun matches(other: MavenDependency): Boolean {
            return groupId == other.groupId && artifactId == other.artifactId
        }
    }

    /**
     * Result of a dependency operation
     */
    data class DependencyOperationResult(
        val success: Boolean,
        val message: String,
        val updatedDependencies: List<MavenDependency> = emptyList(),
        val backupPath: Path? = null
    )

    /**
     * Read and parse a pom.xml file
     */
    fun readPomFile(pomPath: Path): Result<Document> = runCatching {
        log.debug { "Reading pom.xml file: $pomPath" }
        
        if (!pomPath.exists()) {
            throw MavenFileException("POM file does not exist: $pomPath")
        }
        
        if (!pomPath.isReadable()) {
            throw MavenFileException("POM file is not readable: $pomPath")
        }

        val reader = SAXReader()
        val document = reader.read(pomPath.toFile())
        
        // Validate that it's a valid Maven POM
        validatePomStructure(document)
        
        log.debug { "Successfully parsed pom.xml with ${document.rootElement.elements().size} root elements" }
        document
    }.onFailure { error ->
        log.error(error) { "Failed to read pom.xml file: $pomPath" }
    }

    /**
     * Extract all dependencies from a POM document
     */
    fun extractDependencies(document: Document): List<MavenDependency> {
        log.debug { "Extracting dependencies from POM document" }
        
        val dependencies = mutableListOf<MavenDependency>()
        
        // Extract from dependencies section
        val dependenciesElement = document.rootElement.element("dependencies")
        if (dependenciesElement != null) {
            dependencies.addAll(extractDependenciesFromElement(dependenciesElement))
        }
        
        // Extract from dependencyManagement section
        val dependencyManagementElement = document.rootElement.element("dependencyManagement")
        if (dependencyManagementElement != null) {
            val managedDependencies = dependencyManagementElement.element("dependencies")
            if (managedDependencies != null) {
                dependencies.addAll(extractDependenciesFromElement(managedDependencies))
            }
        }
        
        log.debug { "Extracted ${dependencies.size} dependencies from POM" }
        return dependencies
    }

    /**
     * Update a dependency version in the POM document
     */
    fun updateDependencyVersion(
        document: Document,
        groupId: String,
        artifactId: String,
        newVersion: String
    ): Result<DependencyOperationResult> = runCatching {
        log.debug { "Updating dependency version: $groupId:$artifactId to $newVersion" }
        
        val updatedDependencies = mutableListOf<MavenDependency>()
        var updateCount = 0
        
        // Update in dependencies section
        val dependenciesElement = document.rootElement.element("dependencies")
        if (dependenciesElement != null) {
            updateCount += updateDependenciesInElement(dependenciesElement, groupId, artifactId, newVersion, updatedDependencies)
        }
        
        // Update in dependencyManagement section
        val dependencyManagementElement = document.rootElement.element("dependencyManagement")
        if (dependencyManagementElement != null) {
            val managedDependencies = dependencyManagementElement.element("dependencies")
            if (managedDependencies != null) {
                updateCount += updateDependenciesInElement(managedDependencies, groupId, artifactId, newVersion, updatedDependencies)
            }
        }
        
        if (updateCount == 0) {
            return@runCatching DependencyOperationResult(
                success = false,
                message = "Dependency $groupId:$artifactId not found in POM"
            )
        }
        
        log.info { "Updated $updateCount instances of dependency $groupId:$artifactId to version $newVersion" }
        DependencyOperationResult(
            success = true,
            message = "Successfully updated $updateCount dependency instances",
            updatedDependencies = updatedDependencies
        )
    }.onFailure { error ->
        log.error(error) { "Failed to update dependency version: $groupId:$artifactId" }
    }

    /**
     * Add a new dependency to the POM document
     */
    fun addDependency(
        document: Document,
        dependency: MavenDependency
    ): Result<DependencyOperationResult> = runCatching {
        log.debug { "Adding new dependency: ${dependency.getCoordinate()}" }
        
        // Check if dependency already exists
        val existingDependencies = extractDependencies(document)
        if (existingDependencies.any { it.matches(dependency) }) {
            return@runCatching DependencyOperationResult(
                success = false,
                message = "Dependency ${dependency.groupId}:${dependency.artifactId} already exists"
            )
        }
        
        // Ensure dependencies section exists
        var dependenciesElement = document.rootElement.element("dependencies")
        if (dependenciesElement == null) {
            dependenciesElement = document.rootElement.addElement("dependencies")
            log.debug { "Created new dependencies section in POM" }
        }
        
        // Add the dependency
        val dependencyElement = dependenciesElement.addElement("dependency")
        dependencyElement.addElement("groupId").text = dependency.groupId
        dependencyElement.addElement("artifactId").text = dependency.artifactId
        
        if (dependency.version != null) {
            dependencyElement.addElement("version").text = dependency.version
        }
        
        if (dependency.scope != null) {
            dependencyElement.addElement("scope").text = dependency.scope
        }
        
        if (dependency.type != null) {
            dependencyElement.addElement("type").text = dependency.type
        }
        
        if (dependency.classifier != null) {
            dependencyElement.addElement("classifier").text = dependency.classifier
        }
        
        log.info { "Successfully added dependency: ${dependency.getCoordinate()}" }
        DependencyOperationResult(
            success = true,
            message = "Successfully added dependency ${dependency.getCoordinate()}",
            updatedDependencies = listOf(dependency)
        )
    }.onFailure { error ->
        log.error(error) { "Failed to add dependency: ${dependency.getCoordinate()}" }
    }

    /**
     * Write a POM document to a file with proper formatting
     */
    fun writePomFile(document: Document, pomPath: Path, createBackup: Boolean = true): Result<DependencyOperationResult> = runCatching {
        log.debug { "Writing POM document to: $pomPath" }
        
        if (!pomPath.parent.exists()) {
            throw MavenFileException("Parent directory does not exist: ${pomPath.parent}")
        }
        
        if (pomPath.exists() && !pomPath.isWritable()) {
            throw MavenFileException("POM file is not writable: $pomPath")
        }
        
        var backupPath: Path? = null
        
        // Create backup if requested and file exists
        if (createBackup && pomPath.exists()) {
            backupPath = createBackup(pomPath)
            log.debug { "Created backup: $backupPath" }
        }
        
        // Validate document before writing
        validatePomStructure(document)
        
        // Write with proper formatting
        val format = OutputFormat.createPrettyPrint()
        format.encoding = "UTF-8"
        format.isNewlines = true
        format.indent = "  "
        
        val writer = XMLWriter(FileWriter(pomPath.toFile()), format)
        writer.write(document)
        writer.close()
        
        log.info { "Successfully wrote POM file: $pomPath" }
        DependencyOperationResult(
            success = true,
            message = "Successfully wrote POM file",
            backupPath = backupPath
        )
    }.onFailure { error ->
        log.error(error) { "Failed to write POM file: $pomPath" }
    }

    /**
     * Validate POM structure
     */
    private fun validatePomStructure(document: Document) {
        val root = document.rootElement
        
        if (root.name != "project") {
            throw MavenFileException("Root element must be 'project', found: ${root.name}")
        }
        
        // Check for required elements
        val requiredElements = listOf("modelVersion", "groupId", "artifactId", "version")
        for (elementName in requiredElements) {
            if (root.element(elementName) == null) {
                throw MavenFileException("Required element '$elementName' is missing from POM")
            }
        }
        
        log.debug { "POM structure validation passed" }
    }

    /**
     * Extract dependencies from a dependencies element
     */
    private fun extractDependenciesFromElement(dependenciesElement: Element): List<MavenDependency> {
        val dependencies = mutableListOf<MavenDependency>()
        
        dependenciesElement.elements("dependency").forEach { dependencyElement ->
            val groupId = dependencyElement.elementText("groupId")
            val artifactId = dependencyElement.elementText("artifactId")
            val version = dependencyElement.elementText("version")
            val scope = dependencyElement.elementText("scope")
            val type = dependencyElement.elementText("type")
            val classifier = dependencyElement.elementText("classifier")
            
            if (groupId != null && artifactId != null) {
                dependencies.add(
                    MavenDependency(
                        groupId = groupId,
                        artifactId = artifactId,
                        version = version,
                        scope = scope,
                        type = type,
                        classifier = classifier
                    )
                )
            }
        }
        
        return dependencies
    }

    /**
     * Update dependencies in a specific element
     */
    private fun updateDependenciesInElement(
        dependenciesElement: Element,
        groupId: String,
        artifactId: String,
        newVersion: String,
        updatedDependencies: MutableList<MavenDependency>
    ): Int {
        var updateCount = 0
        
        dependenciesElement.elements("dependency").forEach { dependencyElement ->
            val depGroupId = dependencyElement.elementText("groupId")
            val depArtifactId = dependencyElement.elementText("artifactId")
            
            if (depGroupId == groupId && depArtifactId == artifactId) {
                val versionElement = dependencyElement.element("version")
                if (versionElement != null) {
                    versionElement.text = newVersion
                    updateCount++
                    
                    // Add to updated dependencies list
                    updatedDependencies.add(
                        MavenDependency(
                            groupId = depGroupId,
                            artifactId = depArtifactId,
                            version = newVersion,
                            scope = dependencyElement.elementText("scope"),
                            type = dependencyElement.elementText("type"),
                            classifier = dependencyElement.elementText("classifier")
                        )
                    )
                    
                    log.debug { "Updated dependency version: $depGroupId:$depArtifactId to $newVersion" }
                }
            }
        }
        
        return updateCount
    }

    /**
     * Create a backup of the POM file
     */
    private fun createBackup(pomPath: Path): Path {
        val timestamp = System.currentTimeMillis()
        val backupPath = pomPath.parent.resolve("${pomPath.fileName}.backup.$timestamp")
        
        pomPath.toFile().copyTo(backupPath.toFile(), overwrite = true)
        return backupPath
    }
}

/**
 * Exception thrown by MavenFileManager operations
 */
class MavenFileException(message: String, cause: Throwable? = null) : Exception(message, cause)