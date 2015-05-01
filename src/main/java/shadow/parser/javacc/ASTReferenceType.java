/* Generated By:JJTree: Do not edit this line. ASTReferenceType.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=true,VISITOR=true,TRACK_TOKENS=false,NODE_PREFIX=AST,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package shadow.parser.javacc;

import org.apache.logging.log4j.Logger;

import shadow.Loggers;

public
@SuppressWarnings("all")
class ASTReferenceType extends DimensionNode {
  private static final Logger logger = Loggers.TYPE_CHECKER;
  
  public ASTReferenceType(int id) {
    super(id);    
 //   allReferences.add(this);
  }

  public ASTReferenceType(ShadowParser p, int id) {
    super(p, id);  
  //  allReferences.add(this);
  }
  
 // public static List<ASTReferenceType> allReferences = new LinkedList<ASTReferenceType>();


  /** Accept the visitor. **/
  public Object jjtAccept(ShadowParserVisitor visitor, Boolean secondVisit) throws ShadowException {
    return visitor.visit(this, secondVisit);
  }  

  public void dump(String prefix) {
  	String className = this.getClass().getSimpleName();
	
  	logger.debug(prefix + className + "(" + line + ":" + column + "): " + getArrayDimensions());
    
	dumpChildren(prefix);
  }

}
/* JavaCC - OriginalChecksum=7da70a4b1bb235d4c492e29ec9ef71d4 (do not edit this line) */
