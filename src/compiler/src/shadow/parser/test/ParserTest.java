/**
 * 
 */
package shadow.parser.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import shadow.parser.javacc.ParseException;
import shadow.parser.javacc.ShadowParser;
import shadow.parser.javacc.SimpleNode;

/**
 * @author wspeirs
 *
 */
public final class ParserTest {

	boolean dump = true;
	boolean debug = true;
	
	/**
	 * Tests all of the parse test files.
	 * @param args
	 */
	public static void main(String[] args) throws IOException, ParseException {
		ParserTest pt = new ParserTest(false, false);
		
		// no args, we test everything
		if(args.length == 0)
			pt.testAll();
		else {
			for(String arg:args) {
				pt.testAll(new File(arg));
			}
		}	
	}
	
	public ParserTest() {
	}
	
	public ParserTest(boolean debug, boolean dump) {
		this.debug = debug;
		this.dump = dump;
	}
	
	/**
	 * Test everything under this cur directory
	 * @throws IOException
	 */
	private void testAll() throws IOException, ParseException {
		testAll(new File("./src/shadow/parser/test"));
	}
	
	private void testAll(File testDirs) throws IOException, ParseException {
	    ArrayList<File> fileList = new ArrayList<File>();
	    
	    // we now have a list of all files
	    visitDirectory(testDirs, fileList);
	    
	    for(File f:fileList) {
	    	runTest(f);
	    }
	}
	
	private void visitDirectory(File f, ArrayList<File> fileList) throws IOException {
		File[] files = f.listFiles();
		
		for(File cf:files) {
			if(cf.isDirectory())
				visitDirectory(cf, fileList);	// recurse
			else if(cf.getCanonicalPath().endsWith(".shadow"))
				fileList.add(cf);
		}
	}
	
	private void runTest(File sourceFile) throws ParseException {
        try {
          FileInputStream fis = new FileInputStream(sourceFile);
          ShadowParser parser = new ShadowParser(fis);
          
//          if(debug)
//        	  parser.enableDebug();

          System.err.println("Parse Testing File: " + sourceFile.getCanonicalPath());

          SimpleNode n = parser.CompilationUnit();

          if(dump)
        	  n.dump("");
          
          System.err.println("GOOD PARSE");

          } catch (ParseException e) {
              System.err.println("BAD PARSE IN " + sourceFile.getName());
              System.err.println(e.getMessage());
              throw e;
          } catch (Error e) {
              System.err.println("Oops.");
              System.err.println(e.getMessage());
          } catch(FileNotFoundException e) {
              System.err.println(e.getMessage());
          } catch(IOException e) {
        	  System.err.println(e.getMessage());
          }
	}
}