// This plugin accepts the following parameters:
//
// filament_tools_dir
//     Path to the Filament distribution/install directory for desktop.
//     This directory must contain bin/matc.
//
// filament_exclude_vulkan
//     When set, support for Vulkan will be excluded.
//
// Example:
//     ./gradlew -Pfilament_tools_dir=../../dist-release assembleDebug

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.InputFileDetails
import org.gradle.internal.os.OperatingSystem
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

import java.nio.file.Paths

class TaskWithBinary extends DefaultTask {
    private final String binaryName
    private File binaryPath = null

    TaskWithBinary(String name) {
        binaryName = name
    }

    File computeBinary() {
        if (binaryPath == null) {
            def tool = ["/bin/${binaryName}.exe", "/bin/${binaryName}"]
            def fullPath = tool.collect { path ->
                Paths.get(project.ext.filamentToolsPath.absolutePath, path).toFile()
            }

            binaryPath = OperatingSystem.current().isWindows() ? fullPath[0] : fullPath[1]
        }
        return binaryPath
    }
}

class LogOutputStream extends ByteArrayOutputStream {
    private final Logger logger
    private final LogLevel level

    LogOutputStream(Logger logger, LogLevel level) {
        this.logger = logger
        this.level = level
    }

    Logger getLogger() {
        return logger
    }

    LogLevel getLevel() {
        return level
    }

    @Override
    void flush() {
        logger.log(level, toString())
        reset()
    }
}

// Custom task to compile material files using matc
// This task handles incremental builds
abstract class MaterialCompiler extends TaskWithBinary {
    @Incremental
    @InputDirectory
    abstract DirectoryProperty getInputDir()

    @OutputDirectory
    abstract DirectoryProperty getOutputDir()

    MaterialCompiler() {
        super("matc")
    }

    @TaskAction
    void execute(InputChanges inputs) {
        if (!inputs.incremental) {
            project.delete(project.fileTree(outputDir.asFile.get()).matching { include '*.filamat' })
        }

        inputs.getFileChanges(inputDir).each { InputFileDetails change ->
            if (change.fileType == FileType.DIRECTORY) return

            def file = change.file

            if (change.changeType == ChangeType.REMOVED) {
                getOutputFile(file).delete()
            } else {
                def out = new LogOutputStream(logger, LogLevel.LIFECYCLE)
                def err = new LogOutputStream(logger, LogLevel.ERROR)

                def header = ("Compiling material " + file + "\n").getBytes()
                out.write(header)
                out.flush()

                if (!computeBinary().exists()) {
                    throw new GradleException("Could not find ${computeBinary()}." +
                            " Ensure Filament has been built/installed before building this app.")
                }

                def matcArgs = []
                if (!project.hasProperty("filament_exclude_vulkan")) {
                    matcArgs += ['-a', 'vulkan']
                }
                matcArgs += ['-a', 'opengl', '-p', 'mobile', '-o', getOutputFile(file), file]

                project.exec {
                    standardOutput out
                    errorOutput err
                    executable "${computeBinary()}"
                    args matcArgs
                }
            }
        }
    }

    File getOutputFile(final File file) {
        return outputDir.file(file.name[0..file.name.lastIndexOf('.')] + 'filamat').get().asFile
    }
}

class FilamentToolsPluginExtension {
    public DirectoryProperty materialInputDir
    public DirectoryProperty materialOutputDir
}

class FilamentToolsPlugin implements Plugin<Project> {
    void apply(Project project) {
        def extension = project.extensions.create('filamentTools', FilamentToolsPluginExtension)
        extension.materialInputDir = project.objects.directoryProperty()
        extension.materialOutputDir = project.objects.directoryProperty()

        project.ext.filamentToolsPath = project.file("../filament")
        if (project.hasProperty("filament_tools_dir")) {
            project.ext.filamentToolsPath = project.file(project.property("filament_tools_dir"))
        }

        project.tasks.register("filamentCompileMaterials", MaterialCompiler) {
            enabled =
                    extension.materialInputDir.isPresent() &&
                    extension.materialOutputDir.isPresent()
            inputDir.value(extension.materialInputDir.getOrNull())
            outputDir.value(extension.materialOutputDir.getOrNull())
            inputs.dir(inputDir.asFile.get())
            outputs.dir(outputDir.asFile.get())
        }

        project.preBuild.dependsOn "filamentCompileMaterials"
    }
}
