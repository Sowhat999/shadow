/**
 * 
 */
package shadow.output.C;

import shadow.TAC.TACVariable;
import shadow.TAC.nodes.TACAllocation;
import shadow.TAC.nodes.TACAssign;
import shadow.TAC.nodes.TACBinaryOperation;
import shadow.TAC.nodes.TACBranch;
import shadow.TAC.nodes.TACJoin;
import shadow.TAC.nodes.TACLoop;
import shadow.TAC.nodes.TACNoOp;
import shadow.TAC.nodes.TACNode;
import shadow.TAC.nodes.TACUnaryOperation;
import shadow.output.AbstractTACLinearVisitor;

/**
 * @author wspeirs
 *
 */
public class TACCVisitor extends AbstractTACLinearVisitor {

	private static int tabDepth = 0;
	private static final int COMMENT_WIDTH = 35;
	private static final int TAB_WIDTH = 5;

	/**
	 * @param root
	 */
	public TACCVisitor(TACNode root) {
		super(root);
	}
	
	/**
	 * Pretty print out the C code.
	 * @param str The generated C code.
	 * @param node The node that generated the code.
	 */
	private void print(String str, TACNode node) {
		int lineLength = 0;
		
		// print out the tab depths
		for(int i=0; i < tabDepth; ++i) {
			for(int j=0; j < TAB_WIDTH; ++j)
				System.out.print(" ");
			lineLength += TAB_WIDTH;
		}
		
		// print out the actual code
		System.out.print(str);
		lineLength += str.length();
		
		if(node != null) {
			// print out enough spaces to allign everything
			for(int i=0; i < (COMMENT_WIDTH - lineLength); ++i) {
				System.out.print(" ");
			}
			
			// print out the comment
			System.out.println("/* " + node.getAstNode().getLocation() + " */");
		} else {
			System.out.println();
		}
	}
	
	private void print(String str) {
		print(str, null);
	}


	@Override
	public void start() {
		print("int main(int argc, char **argv) {");
		++tabDepth;
	}

	@Override
	public void end() {
		print("\n");
		print("return 0;");
		--tabDepth;
		print("}");
	}

	public void visit(TACNode node) {
		print(node.toString(), node);
	}
	
	/**
	 * Converts a Shadow type to a C type.
	 * @param shadowType
	 * @return
	 */
	private String type2type(String shadowType) {
		if(shadowType.equals("boolean"))
			return "int";
		else
			return shadowType;
	}
	
	/**
	 * Converts Shadow literals to C literals.
	 * @param shadowLiteral
	 * @return
	 */
	private String lit2lit(String shadowLiteral) {
		if(shadowLiteral.equals("true"))
			return "1";
		else if(shadowLiteral.equals("false"))
			return "0";
		else
			return shadowLiteral;
	}
	
	public void visit(TACAllocation node) {
		TACVariable var = node.getVariable();
		StringBuilder sb = new StringBuilder();
		
		sb.append(type2type(var.getType().toString()));
		sb.append(" ");
		sb.append(var.getSymbol());
		sb.append(";");
		
		print(sb.toString(), node);
	}
	
	public void visit(TACAssign node) {
		StringBuilder sb = new StringBuilder();
		
		sb.append(node.getLHS().getSymbol());
		sb.append(" = ");
		sb.append(lit2lit(node.getRHS().getSymbol()));
		sb.append(";");
		
		print(sb.toString(), node);
	}
	
	public void visit(TACBinaryOperation node) {
		StringBuilder sb = new StringBuilder();
		
		sb.append(node.getLHS().getSymbol());
		sb.append(" = ");
		sb.append(node.getRHS().getSymbol());
		
		switch(node.getOperation()) {
		case ADDITION: sb.append(" + "); break;
		case AND: sb.append(" & "); break;
		case DIVISION: sb.append(" / "); break;
		case LROTATE: sb.append(" SOME MACRO "); break;
		case LSHIFT: sb.append(" << "); break;
		case MOD: sb.append(" % "); break;
		case MULTIPLICATION: sb.append(" * "); break;
		case OR: sb.append(" | "); break;
		case RROTATE: sb.append(" SOME MACRO "); break;
		case RSHIFT: sb.append(">>"); break;
		case SUBTRACTION: sb.append(" - "); break;
		case XOR: sb.append("^"); break;
		}
		
		sb.append(node.getOperand2().getSymbol());
		sb.append(";");
		
		print(sb.toString(), node);
	}
	
	public void visit(TACBranch node) {
		StringBuilder sb = new StringBuilder("if ( ");
		
		sb.append(lit2lit(node.getLhs().getSymbol()));
		
		switch(node.getComparision()) {
		case EQUAL: sb.append(" == "); break;
		case GREATER: sb.append(" > "); break;
		case GREATER_EQUAL: sb.append(" >= "); break;
		case IS: sb.append(" ???? "); break;
		case LESS: sb.append(" < "); break;
		case LESS_EQUAL: sb.append(" <= "); break;
		case NOT_EQUAL: sb.append(" != "); break;
		}
		
		sb.append(lit2lit(node.getRhs().getSymbol()));
		sb.append(" ) { ");
		
		print("");
		print(sb.toString(), node);
		
		++tabDepth;
	}
	
	public void visitElse() {
		print("");
		print("else {");
		++tabDepth;
	}
	
	public void visit(TACLoop node) {
		// TODO: Implement
	}
	
	public void visit(TACJoin node) {
	}
	
	public void visitJoin(TACJoin node) {
		--tabDepth;
		print("} ", node);
	}
	
	public void visit(TACNoOp node) {
	}

	public void visit(TACUnaryOperation node) {
		StringBuilder sb = new StringBuilder();
		
		sb.append(node.getLHS());
		sb.append(" = ");
		sb.append(node.getOperation());
		sb.append(node.getRHS());
		sb.append(";");
		
		print(sb.toString(), node);
	}

}