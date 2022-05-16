package shadow;

import org.apache.logging.log4j.Logger;
import shadow.doctool.tag.TagManager.BlockTagType;
import shadow.interpreter.ConstantFieldInterpreter;
import shadow.output.llvm.LLVMOutput;
import shadow.parse.Context;
import shadow.parse.ParseException;
import shadow.parse.ShadowParser;
import shadow.parse.ShadowParser.VariableDeclaratorContext;
import shadow.tac.TACBuilder;
import shadow.tac.TACModule;
import shadow.tac.analysis.ControlFlowGraph;
import shadow.typecheck.BaseChecker;
import shadow.typecheck.ErrorReporter;
import shadow.typecheck.TypeCheckException;
import shadow.typecheck.TypeChecker;
import shadow.typecheck.type.*;

import java.io.*;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;

/**
 * @author Barry Wittman
 * @author Claude Abounegm
 * @author Brian Stottler
 * @author Jacob Young
 * @author Bill Speirs
 */
public class Main {

  // Version of the Shadow compiler
  public static final String VERSION = "0.8";
  public static final String MINIMUM_LLVM_VERSION = "6.0";
  public static final String MINIMUM_WINDOWS_LLVM_VERSION = "10.0";

  // These are the error codes returned by the compiler
  public static final int NO_ERROR = 0;
  public static final int FILE_NOT_FOUND_ERROR = -1;
  public static final int PARSE_ERROR = -2;
  public static final int TYPE_CHECK_ERROR = -3;
  public static final int COMPILE_ERROR = -4;
  public static final int COMMAND_LINE_ERROR = -5;
  public static final int CONFIGURATION_ERROR = -6;

  private static final Logger logger = Loggers.SHADOW;
  private static Configuration config;
  private static Job currentJob;

  // Metadata related to a Shadow program's main class
  private static String mainClass;
  private static boolean mainArguments;

  private static final Charset UTF8 = StandardCharsets.UTF_8;

  /**
   * This is the starting point of the compiler.
   *
   * @param args Command line arguments to control the compiler
   */
  public static void main(String[] args) {
    try {
      run(args);
    } catch (FileNotFoundException e) {
      System.err.println("FILE NOT FOUND: " + e.getLocalizedMessage());
      System.exit(FILE_NOT_FOUND_ERROR);
    } catch (ParseException e) {
      System.exit(PARSE_ERROR);
    } catch (IOException e) {
      System.err.println("FILE DEPENDENCY ERROR: " + e.getLocalizedMessage());
      System.exit(TYPE_CHECK_ERROR);
    } catch (org.apache.commons.cli.ParseException e) {
      System.err.println("COMMAND LINE ERROR: " + e.getLocalizedMessage());
      Arguments.printHelp();
      System.exit(COMMAND_LINE_ERROR);
    } catch (ConfigurationException e) {
      System.err.println("CONFIGURATION ERROR: " + e.getLocalizedMessage());
      Arguments.printHelp();
      System.exit(CONFIGURATION_ERROR);
    } catch (TypeCheckException e) {
      System.exit(TYPE_CHECK_ERROR);
    } catch (CompileException e) {
      System.exit(COMPILE_ERROR);
    } catch (ShadowException e) {
      System.err.println("ERROR IN FILE: " + e.getLocalizedMessage());
      System.exit(TYPE_CHECK_ERROR);
    }
    System.exit(NO_ERROR);
  }

  // Check LLVM version using lexical comparison
  private static void checkLLVMVersion() throws ConfigurationException {
    String LLVMVersion = Configuration.getLLVMVersion();

    int comparison;
    if (Configuration.isWindows()) // Higher versions of LLVM are needed for Windows because of EH
    comparison = compareVersions(LLVMVersion, MINIMUM_WINDOWS_LLVM_VERSION);
    else comparison = compareVersions(LLVMVersion, MINIMUM_LLVM_VERSION);

    if (comparison < 0) {
      String error =
          "LLVM version "
              + MINIMUM_LLVM_VERSION
              + " or higher is required for Shadow "
              + VERSION
              + ", but ";
      if (LLVMVersion.isEmpty()) error += "no LLVM installation found.";
      else error += "version " + LLVMVersion + " found.";
      throw new ConfigurationException(error);
    }
  }

  private static int versionStringToInt(String number) {
    int start = 0;
    int end = 0;

    // Start at the beginning, since alphabetic characters indicating alpha or similar might be at
    // the end
    while (end < number.length() && Character.isDigit(number.charAt(end))) ++end;

    try {
      return Integer.parseInt(number.substring(start, end));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  public static int compareVersions(String v1, String v2) {
    String[] strings1 = v1.split("\\.");
    String[] strings2 = v2.split("\\.");
    int size = Math.max(strings1.length, strings2.length);
    for (int i = 0; i < size; ++i) {
      int value1, value2;
      if (i < strings1.length) value1 = versionStringToInt(strings1[i].trim());
      else value1 = 0;
      if (i < strings2.length) value2 = versionStringToInt(strings2[i].trim());
      else value2 = 0;
      if (value1 != value2) return value1 - value2;
    }

    return 0;
  }

  public static void run(String[] args)
      throws ShadowException, IOException, org.apache.commons.cli.ParseException,
          ConfigurationException {

    // Detect and establish the current settings and arguments
    Arguments compilerArgs = new Arguments(args);

    // Detect and establish the current settings based on the arguments
    config =
        Configuration.buildConfiguration(
            compilerArgs.getMainFileArg(), compilerArgs.getConfigFileArg(), false);
    currentJob = new Job(compilerArgs);

    // Print help and exit
    if (compilerArgs.hasOption(Arguments.HELP)) {
      Arguments.printHelp();
      return;
    }

    // Print information and exit
    // Must come after building configuration, since configuration helps
    // us find the correct LLVM installation
    if (compilerArgs.hasOption(Arguments.INFORMATION)) {
      Arguments.printInformation();
      return;
    }

    // Important settings
    List<Path> system = config.getSystem();
    Path systemInclude = system.get(Configuration.INCLUDE);
    Path systemSource = system.get(Configuration.SOURCE);
    boolean isCompile = !currentJob.isCheckOnly() && !currentJob.isNoLink();

    if (isCompile) checkLLVMVersion();

    // Begin the checking/compilation process
    long startTime = System.currentTimeMillis();

    List<String> linkCommand = new ArrayList<>(config.getLinkCommand(currentJob));
    List<Path> cFiles = new ArrayList<>();
    Set<String> filesToLink = new LinkedHashSet<>();

    generateObjectFiles(cFiles, filesToLink);

    if (isCompile) {
      // Compile and add the C source files to get linked
      if (!compileCSourceFiles(systemInclude, cFiles, filesToLink)) {
        logger.error("Failed to compile one or more C source files.");
        throw new CompileException("FAILED TO COMPILE");
      }

      logger.info("Building for target \"" + config.getTarget() + "\"");
      Path mainLL;
      if (config.getOs().equals("Windows")) {
        if (mainArguments) mainLL = Paths.get("MainWindows.ll");
        else mainLL = Paths.get("NoArgumentsWindows.ll");
      } else {
        if (mainArguments) mainLL = Paths.get("shadow", "Main.ll");
        else mainLL = Paths.get("NoArguments.ll");
      }

      mainLL = systemSource.resolve(mainLL);
      BufferedReader main = Files.newBufferedReader(mainLL, UTF8);

      // Read main and compile into temporary object
      Map<Path, Path> imports = config.getImport();
      Path parent = getBinaryPath(currentJob.getMainFile(), imports).getParent();
      Path temporaryMain = Files.createTempFile(parent, "main", null);
      StringBuilder builder = new StringBuilder();
      String line = main.readLine();

      while (line != null) {
        line = line.replace("shadow.test..Test", mainClass) + System.lineSeparator();
        builder.append(line);
        line = main.readLine();
      }
      main.close();

      String compiledMain =
          compileLLVMStream(new ByteArrayInputStream(builder.toString().getBytes()), temporaryMain);
      filesToLink.add(compiledMain);

      linkCommand.addAll(filesToLink);

      logger.info("Linking object files...");

      // Usually clang or clang++
      Process link =
          new ProcessBuilder(linkCommand)
              .redirectOutput(Redirect.INHERIT)
              .redirectError(Redirect.INHERIT)
              .start();
      try {
        if (link.waitFor() != 0) throw new CompileException("FAILED TO LINK");
      } catch (InterruptedException ignored) {
      } finally {
        link.destroy();
        Files.delete(temporaryMain);
        Files.delete(Paths.get(compiledMain));
      }

      logger.info("SUCCESS: Built in " + (System.currentTimeMillis() - startTime) + "ms");
    }
  }

  // Assumes compileCommand contains two empty slots at the end:
  // The first is for the output file name
  // The last is for the input file name
  private static boolean compileCSourceFile(
      Path cFile, Path binaryPath, List<String> compileCommand, Set<String> filesToLink)
      throws IOException {
    Path outputPath = BaseChecker.addExtension(binaryPath, ".o");

    String outputFile = outputPath.toString();
    filesToLink.add(outputFile);

    if (currentJob.isForceRecompile()
        || !Files.exists(outputPath)
        || Files.getLastModifiedTime(outputPath).compareTo(Files.getLastModifiedTime(cFile)) < 0) {
      logger.info("Generating assembly code for " + cFile.getFileName());
      compileCommand.set(compileCommand.size() - 2, outputFile);
      compileCommand.set(compileCommand.size() - 1, cFile.toString());
      return runCCompiler(compileCommand, cFile.getParent());
    }

    logger.info("Using pre-existing assembly code for " + outputPath.getFileName());
    return true;
  }

  private static boolean compileCSourceFiles(
      Path includePath, List<Path> cShadowFiles, Set<String> filesToLink)
      throws IOException, ConfigurationException {

    // No need to compile anything if there are no c-source files
    if (!Files.exists(includePath)) {
      logger.error("The include directory was not found and is necessary for the Shadow runtime.");
      return false;
    }

    // Compile the files to assembly, to be ready for linkage
    List<String> compileCommand = new ArrayList<>();

    if (Configuration.getConfiguration().getOs().equals("Mac")) {
      compileCommand.add("clang");
      String[] version = System.getProperty("os.version").split("\\.");
      compileCommand.add("-mmacosx-version-min=" + version[0] + "." + version[1]);
      // compileCommand.add("-Wall");
    } else compileCommand.add("clang");

    compileCommand.add("-m" + Configuration.getConfiguration().getArchitecture());

    compileCommand.add("-O3");

    compileCommand.add("-c");

    // include directories to be in the search path of gcc
    compileCommand.add("-I" + includePath);
    compileCommand.add("-I" + includePath.resolve("platform").resolve(config.getOs()));
    compileCommand.add(
        "-I" + includePath.resolve("platform").resolve("Arch" + config.getArchitecture()));

    compileCommand.add("-o");
    compileCommand.add(null); // Location for output name
    compileCommand.add(null); // Location for .c file

    Map<Path, Path> imports = Configuration.getConfiguration().getImport();

    for (Path cFile : cShadowFiles) {
      Path binaryPath = getBinaryPath(cFile, imports);
      if (!compileCSourceFile(cFile, binaryPath, compileCommand, filesToLink)) return false;
    }

    return true;
  }

  private static boolean runCCompiler(List<String> compileCommand, Path cSourceDirectory)
      throws IOException {
    try {
      return new ProcessBuilder(compileCommand)
              .directory(cSourceDirectory.toFile())
              .redirectError(Redirect.INHERIT)
              .start()
              .waitFor()
          == 0;
    } catch (InterruptedException ignored) {
    }

    return false;
  }

  /*
   * Ensures that LLVM code exists for all dependencies of a main-method-
   * containing class/file. This involves either finding an existing .ll file
   * (which has been updated more recently than the corresponding source file)
   * or building a new one
   */
  private static void generateObjectFiles(List<Path> cFiles, Set<String> filesToLink)
      throws IOException, ShadowException, ConfigurationException {

    List<Path> system = config.getSystem();
    Path systemBinary = system.get(Configuration.BINARY);
    Path systemSource = system.get(Configuration.SOURCE);

    boolean isWindows = Configuration.getConfiguration().getOs().equals("Windows");
    Path unwindSource =
        systemSource.resolve(
            "Unwind" + (isWindows ? "Windows" : "") + config.getArchitecture() + ".ll");
    Path unwindBinary =
        systemBinary.resolve("Unwind" + (isWindows ? "Windows" : "") + config.getArchitecture());
    filesToLink.add(compileLLVMFile(unwindSource, unwindBinary));

    // Add platform-specific system code
    filesToLink.add(
        compileLLVMFile(
            systemSource.resolve(config.getOs() + ".ll"), systemBinary.resolve(config.getOs())));

    // Add shared code
    filesToLink.add(
        compileLLVMFile(systemSource.resolve("Shared.ll"), systemBinary.resolve("Shared")));

    Path mainFile = currentJob.getMainFile();

    ErrorReporter reporter = new ErrorReporter(Loggers.TYPE_CHECKER);

    // TypeChecker generates a list of AST nodes corresponding to
    // classes needing compilation
    TypeChecker.TypeCheckerOutput typecheckerOutput =
        TypeChecker.typeCheck(
            mainFile, currentJob.isForceRecompile(), reporter, currentJob.isCheckOnly());

    ConstantFieldInterpreter.evaluateConstants(
        typecheckerOutput.packageTree, typecheckerOutput.nodes);

    if (!currentJob.isCheckOnly()) {
      // Set data for main class
      Type mainType = typecheckerOutput.main.getType();
      mainClass = mainType.toString(Type.MANGLE);
      SequenceType arguments = new SequenceType(new ArrayType(Type.STRING));
      if (mainType.getMatchingMethod("main", arguments) != null) mainArguments = true;
      else if (mainType.getMatchingMethod("main", new SequenceType()) != null)
        mainArguments = false;
      else
        throw new CompileException(
            "File " + mainFile + " does not contain an appropriate main() method");
    }

    // Put imports on binary search path
    Map<Path, Path> sourceToBinaries = new HashMap<>(config.getImport());
    // Then add system source and binary
    sourceToBinaries.put(systemSource, systemBinary);

    try {
      for (Context node : typecheckerOutput.nodes) {
        // As an optimization, print .meta files for the .shadow files being checked
        if (!node.isFromMetaFile()) TypeChecker.printMetaFile(node);

        Path file = node.getPath();

        if (currentJob.isCheckOnly()) {
          // Performs checks to make sure all paths return, there is
          // no dead code, etc.
          // No need to check interfaces, attributes, or .meta files (no code in
          // those cases)
          if (!node.isFromMetaFile() && !(node.getType() instanceof AttributeType))
            optimizeTAC(new TACBuilder().build(node), reporter);
        } else {
          Path path = BaseChecker.stripExtension(file);
          Path name = path.getFileName();

          Type type = node.getType();
          String className = typeToFileName(type);
          Path cFile = file.resolveSibling(className + ".c");
          if (Files.exists(cFile)) cFiles.add(cFile);

          Path llvmFile = file.resolveSibling(className + ".ll");
          Path binaryPath = getBinaryPath(path, sourceToBinaries);

          Path nativeFile = file.resolveSibling(className + ".native.ll");
          Path nativeBinary = BaseChecker.addExtension(binaryPath, ".native");
          Path nativeObject = BaseChecker.addExtension(nativeBinary, ".o");

          // If the LLVM bitcode didn't exist, the full .shadow file would
          // have been used
          if (node.isFromMetaFile()) {
            logger.info("Using pre-existing LLVM code for " + name);
            Path objectFile = BaseChecker.addExtension(binaryPath, ".o");

            if (Files.exists(objectFile)) filesToLink.add(objectFile.toString());
            else if (Files.exists(llvmFile))
              filesToLink.add(compileLLVMFile(llvmFile, binaryPath));
            else throw new CompileException("File not found: " + objectFile);
          } else {
            logger.info("Generating LLVM code for " + name);
            // Gets top level class
            TACModule module = optimizeTAC(new TACBuilder().build(node), reporter);
            filesToLink.add(compileShadowFile(file, binaryPath, module));
          }

          if (Files.exists(nativeObject)) filesToLink.add(nativeObject.toString());
          else if (Files.exists(nativeFile))
            filesToLink.add(compileLLVMFile(nativeFile, nativeBinary));
        }
      }

      reporter.printAndReportErrors();

    } catch (TypeCheckException e) {
      logger.error(mainFile + " FAILED TO TYPE CHECK");
      throw e;
    }
  }

  public static Path getBinaryPath(Path path, Map<Path, Path> sourceToBinaries)
      throws ConfigurationException {
    Path parent = path;
    while (parent != null && !sourceToBinaries.containsKey(parent)) parent = parent.getParent();

    if (parent == null)
      throw new ConfigurationException("Binary path not found for source file " + path);

    Path binaryParent = sourceToBinaries.get(parent);
    Path fromParentToChild = parent.relativize(path);
    return binaryParent.resolve(fromParentToChild);
  }

  private static Process getCompiler(String objectFile) throws IOException {
    return new ProcessBuilder(
            config.getLlc(),
            "-mtriple",
            config.getTarget(),
            config.getLLVMOptimizationLevel(), /*config.getDataLayout(),*/
            "--filetype=obj",
            "-o",
            objectFile)
        .redirectError(Redirect.INHERIT)
        .start();
  }

  private static Path createPath(Path binaryPath) throws CompileException {
    Path objectPath = BaseChecker.addExtension(binaryPath, ".o");
    Path objectParent = objectPath.getParent();
    if (!Files.isDirectory(objectParent)) {
      try {
        Files.createDirectories(objectParent);
      } catch (IOException e) {
        throw new CompileException("COULD NOT CREATE DIRECTORY " + objectParent);
      }
    }

    return objectPath;
  }

  private static String compileLLVMModule(Path shadowFile, Path binaryPath, TACModule module)
      throws CompileException {
    Path objectPath = createPath(binaryPath);
    String objectFile = objectPath.toString();

    boolean success = false;
    Process compile = null;
    LLVMOutput output;

    try {
      compile = getCompiler(objectFile);
      output = new LLVMOutput(new BufferedOutputStream(compile.getOutputStream()));
      output.build(module);
      output.close();
      if (compile.waitFor() != 0) throw new CompileException("FAILED TO COMPILE " + objectFile);
      success = true;
    } catch (IOException | InterruptedException e) {
      throw new CompileException("FAILED TO COMPILE " + objectFile);
    } catch (ShadowException e) {
      logger.error("FAILED TO COMPILE " + shadowFile);
      throw new CompileException(e.getMessage());
    } finally {
      try {
        if (!success) Files.deleteIfExists(objectPath);
      } catch (IOException ignored) {
      }
      if (compile != null) compile.destroy();
    }
    return objectFile;
  }

  private static String compileLLVMStream(InputStream stream, Path binaryPath)
      throws CompileException {
    Path objectPath = createPath(binaryPath);
    String objectFile = objectPath.toString();

    boolean success = false;
    Process compile = null;

    try {
      compile = getCompiler(objectFile);
      new Pipe(stream, compile.getOutputStream()).start();
      if (compile.waitFor() != 0) throw new CompileException("FAILED TO COMPILE " + objectFile);
      success = true;
    } catch (IOException | InterruptedException e) {
      throw new CompileException("FAILED TO COMPILE " + objectFile);
    } finally {
      if (!success) {
        try {
          Files.deleteIfExists(objectPath);
        } catch (IOException ignored) {
        }
      }
      if (compile != null) compile.destroy();
    }

    return objectFile;
  }

  private static String compileLLVMFile(Path LLVMPath, Path binaryPath) throws CompileException {
    try {
      return compileLLVMStream(Files.newInputStream(LLVMPath), binaryPath);
    } catch (IOException e) {
      throw new CompileException("FAILED TO OPTIMIZE " + LLVMPath);
    }
  }

  private static String compileShadowFile(Path shadowFile, Path binaryPath, TACModule module)
      throws CompileException {

    if (currentJob.isHumanReadable()) {
      try {
        // Generate LLVM
        Path llvmFile = BaseChecker.changeExtension(shadowFile, ".ll");
        OutputStream out = Files.newOutputStream(llvmFile);
        LLVMOutput output = new LLVMOutput(new BufferedOutputStream(out));
        output.build(module);
        output.close();
        out.close();
        return compileLLVMFile(llvmFile, binaryPath);
      } catch (IOException | ShadowException e) {
        logger.error("FAILED TO COMPILE " + shadowFile);
        throw new CompileException(e.getMessage());
      }
    } else return compileLLVMModule(shadowFile, binaryPath, module);
  }

  /*
   * This method contains all the Shadow-specific TAC optimization, including
   * constant propagation, control flow analysis, and data flow analysis.
   */
  public static TACModule optimizeTAC(TACModule module, ErrorReporter reporter) {

    if (!(module.getType() instanceof InterfaceType)) {
      List<TACModule> innerClasses = module.getAllInnerClasses();
      List<TACModule> modules = new ArrayList<>(innerClasses.size() + 1);
      modules.add(module);
      modules.addAll(innerClasses);

      List<ControlFlowGraph> graphs = module.optimizeTAC(reporter);

      // Get all used fields and all used private methods
      Map<Type, Set<String>> allUsedFields = new HashMap<>();
      Set<MethodSignature> allUsedPrivateMethods = new HashSet<>();
      for (ControlFlowGraph graph : graphs) {
        if (!graph.getMethod().getSignature().isCopy()
            && !graph.getMethod().getSignature().isDestroy()) {
          Map<Type, Set<String>> usedFields = graph.getUsedFields();
          for (Entry<Type, Set<String>> entry : usedFields.entrySet()) {
            Set<String> fields =
                allUsedFields.computeIfAbsent(entry.getKey(), k -> new HashSet<>());
            fields.addAll(entry.getValue());
          }

          allUsedPrivateMethods.addAll(graph.getUsedPrivateMethods());
        }
      }

      for (TACModule class_ : modules) {
        // No attribute member checking for now
        if (class_.getType() instanceof AttributeType) continue;

        // Check field initialization
        class_.checkFieldInitialization(reporter, graphs);

        // Give warnings if fields are never used
        Type type = class_.getType();
        Set<String> usedFields = allUsedFields.computeIfAbsent(type, k -> new HashSet<>());
        for (Entry<String, VariableDeclaratorContext> entry : type.getFields().entrySet()) {
          if (!entry.getValue().getModifiers().isConstant()
              && !usedFields.contains(entry.getKey())
              && entry.getValue().getDocumentation().getBlockTags(BlockTagType.UNUSED).isEmpty()) {
            reporter.addWarning(
                entry.getValue().generalIdentifier(),
                TypeCheckException.Error.UNUSED_FIELD,
                "Field " + entry.getKey() + " is never used");
          }
        }

        // Give warnings if private methods are never used
        for (List<MethodSignature> signatures : type.getMethodMap().values()) {
          for (MethodSignature signature : signatures) {
            if (signature.getModifiers().isPrivate()
                && !allUsedPrivateMethods.contains(signature.getSignatureWithoutTypeArguments())
                && !signature.isImport()
                && !signature.isExport()
                && signature.getDocumentation().getBlockTags(BlockTagType.UNUSED).isEmpty()
                && !signature.isDestroy()) {

              Context node = signature.getNode();
              if (node instanceof ShadowParser.MethodDeclarationContext)
                node = ((ShadowParser.MethodDeclarationContext) node).methodDeclarator();
              else if (node instanceof ShadowParser.CreateDeclarationContext)
                node = ((ShadowParser.CreateDeclarationContext) node).createDeclarator();

              reporter.addWarning(
                  node,
                  TypeCheckException.Error.UNUSED_METHOD,
                  "Private method "
                      + signature.getSymbol()
                      + signature.getMethodType()
                      + " is never used");
            }
          }
        }
      }
    }

    return module;
  }

  /*
   * A simple class used to redirect an InputStream into a specified
   * OutputStream
   */
  private static class Pipe extends Thread {
    private final InputStream input;
    private final OutputStream output;

    public Pipe(InputStream inputStream, OutputStream outputStream) {
      input = inputStream;
      output = outputStream;
    }

    @Override
    public void run() {
      try {
        try {
          byte[] buffer = new byte[8096];
          int read = input.read(buffer);
          while (read >= 0) {
            output.write(buffer, 0, read);
            read = input.read(buffer);
          }
        } finally {
          try {
            input.close();
          } catch (IOException ignored) {
          }
          try {
            output.flush();
          } catch (IOException ignored) {
          }
          try {
            output.close();
          } catch (IOException ignored) {
          }
        }
      } catch (IOException ignored) {
      }
    }
  }

  /**
   * Finds the standard file/class name for a type, fixing capitalization for primitive types (e.g.
   * uint toUInt).
   */
  private static String typeToFileName(Type type) {

    String name = type.getTypeName();
    if (type.isPrimitive()) { // hack to produce Int.ll instead of int.ll
      if (name.startsWith("u")) // UShort instead of ushort
      name = name.substring(0, 2).toUpperCase() + name.substring(2);
      else name = name.substring(0, 1).toUpperCase() + name.substring(1);
    }
    return name;
  }

  public static Job getJob() {
    return currentJob;
  }
}
