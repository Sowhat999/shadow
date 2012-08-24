package shadow;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.logging.Log;

import shadow.parser.javacc.ShadowException;

public class Configuration implements Iterator<File> {
	
	private Log logger = Loggers.SHADOW;
	
	// these are the single letter command line args
	private static final String CONFIG_FILE = "C";
	private static final String CHECK		= "check";
	private static final String COMPILE		= "compile";

	private String parentConfig = null; /** The parent configuration from a config file */
	private String mainClass = null; /** The file containing the class with the Main method */
	private List<File> shadowFiles = null;
	private int currentShadowFile = 0;
	private File systemImportPath = null;	/** This is the import path for all the system files */
	private List<File> importPaths = null;
	private boolean checkOnly = false;		/** Run only parser & type-checker */
	private boolean compileOnly = false;	/** Only compile the files on the command line */
	private int arch = -1;
	private String os = null;
	
	private static Configuration config = new Configuration();
	
	private Configuration() {
		this.importPaths = new ArrayList<File>();
	}
	
	/**
	 * Get the singleton instance of the Configuration.
	 * @return The singleton instance.
	 */
	public static Configuration getInstance() {
		return config;
	}

	/**
	 * Parses the command line and sets all of the internal variables.
	 * @param cmdLine The command line passed to the compiler.
	 * @throws ShadowException
	 * @throws ParseException 
	 */
	public boolean parse(CommandLine cmdLine) throws ShadowException {
		this.reset();	// resetting the counter in case we parse multiple times
		
		if(cmdLine.hasOption(CONFIG_FILE)){
			// parse the config file on the command line if we have it
			parseConfigFile(new File(cmdLine.getOptionValue(CONFIG_FILE)));
		} else if(System.getenv("SHADOW_SYSTEM_CONFIG") != null){
			// use the system config from the environment
			parseConfigFile(new File(System.getenv("SHADOW_SYSTEM_CONFIG")));
		} else {
			// parse the correct built-in configuration file
			if(System.getProperty("os.name").startsWith("Windows"))
				parseConfigFile(System.class.getResource("/windows_system.xml"));
			else
				parseConfigFile(System.class.getResource("/linux_system.xml"));
		}

		// print the import paths if we're debugging
		if(logger.isDebugEnabled()) {
			for(File i:importPaths) {
				logger.debug("IMPORT: " + i.getAbsolutePath());
			}
		}

		//
		// By the time we get here, all configs & parents have been parsed
		//
		
		// see if all we want is to check the file
		checkOnly = cmdLine.hasOption(CHECK);
		
		// see if we're only compiling files
		compileOnly = cmdLine.hasOption(COMPILE);
		
		// get all of the files to compile
		shadowFiles = new ArrayList<File>();
		for(String shadowFile:cmdLine.getArgs()) {
			shadowFiles.add(new File(shadowFile));
		}
		
		// make sure they're not specifying too many files on the command line
//		if(!compileOnly && shadowFiles.size() > 1) {
//			System.err.println("Only one file  with a main method should be specified on the command line.");
//			return false;
//		}
		
		if(shadowFiles.size() == 0) {
			System.err.println("No source files specified to compile");
			return false;
		}
		
		// set the main class if we're not only compiling
		if(!compileOnly)
			mainClass = shadowFiles.get(0).getAbsolutePath();
		
		//
		// Sanity checks
		//
		if(!checkOnly && mainClass == null) {
			System.err.println("Did not specify a main class");
			return false;
		}
		
		if(arch == -1) {
			System.err.println("Did not specify an architecture");
			return false;
		}
		
		if(os == null) {
			System.err.println("Did not specify an OS");
			return false;
		}
		
		if(this.systemImportPath == null) {
			System.err.println("No system import path specified");
			return false;
		}
		
		return true;
	}
	
	/**
	 * Parse a config file, recursively parsing parents when found.
	 * @param configFile The config file to parse
	 */
	private <T>void parseConfigFile(T configFile) {
		ConfigParser parser = new ConfigParser(this);
		
		if(configFile instanceof File) {
			logger.debug("PARSING: " + ((File)configFile).getAbsolutePath());
			parser.parse((File)configFile);
		} else {
			logger.debug("PARSING: " + ((URL)configFile));
			
			parser.parse((URL)configFile);
		}
		
		// see if we found a parent or not
		if(this.parentConfig != null) {
			File parent = new File(parentConfig);
			
			// reset the parent
			this.parentConfig = null;

			// parse the parent
			parseConfigFile(parent);
		}
	}
	
	/**
	 * Create an Options object to be used to parse the command line.
	 * 
	 * The options are:
	 * --config Specifies the config.xml file to be used
	 * --check Parses and type-checks the files
	 * --compile Compiles the Shadow files
	 * @return Return options used to parse the command line. 
	 */
	public static Options createCommandLineOptions() {
		Options options = new Options();

		// setup the configuration file option
		@SuppressWarnings("static-access")
		Option configOption = OptionBuilder.withLongOpt("config")
										   .hasArg()
										   .withArgName("config.xml")
										   .withDescription("Configuration file\ndefault is shadow_config.xml")
										   .create(CONFIG_FILE);

		// create the check option
		@SuppressWarnings("static-access")
		Option checkOption = OptionBuilder.withLongOpt(CHECK)
										  .withDescription("Parse and type-check the Shadow files")
										  .create();

		// create the compile option
		@SuppressWarnings("static-access")
		Option compileOption = OptionBuilder.withLongOpt(COMPILE)
										    .withDescription("Compile Shadow files")
										    .hasArg()
										    .create();

		// add all the options from above
		options.addOption(configOption);
		options.addOption(checkOption);
		options.addOption(compileOption);

		// add new simple options
		options.addOption(new Option("h", "help", false, "Print this help message"));

		return options;
	}

	public String getMainClass() {
		return mainClass;
	}

	public void setMainClass(String mainClass) {
		this.mainClass = mainClass;
	}

	public int getArch() {
		return arch;
	}

	public void setArch(int arch) {
		if(this.arch == -1)
			this.arch = arch;
	}

	public String getOs() {
		return os;
	}

	public void setOs(String os) {
		if(this.os == null)
			this.os = os;
	}

	public List<File> getImports() {
		return importPaths;
	}

	public void addImport(String importPath) {
		this.importPaths.add(new File(importPath));
	}

	public File getSystemImport() {
		return systemImportPath;
	}

	public void setSystemImport(String systemImportPath) {
		if(this.systemImportPath == null)
			this.systemImportPath = new File(systemImportPath);
	}

	public void setParent(String parentConfig) {
		this.parentConfig = parentConfig;
	}

	public boolean isCheckOnly() {
		return checkOnly;
	}

	/**
	 * Returns true if there is another Shadow file.
	 */
	@Override
	public boolean hasNext() {
		if(currentShadowFile == shadowFiles.size())
			return false;
		else
			return true;
	}

	/**
	 * Gets the next Shadow file to compile.
	 */
	@Override
	public File next() {
		return shadowFiles.get(currentShadowFile++);
	}
	
	/**
	 * Gets the current file to compile.
	 * <b>Must call next() at least once before calling this</b>
	 * @return
	 */
	public File current() {
		if(shadowFiles != null)
			return shadowFiles.get(currentShadowFile == 0 ? currentShadowFile : currentShadowFile - 1);
		else
			return null;
	}

	/**
	 * This does nothing as you're not allowed to remove files.
	 */
	@Override
	public void remove() {
	}

	/**
	 * Resets the internal counter for getting Shadow files
	 */
	public void reset() {
		currentShadowFile = 0;
		importPaths.clear();
	}

}
