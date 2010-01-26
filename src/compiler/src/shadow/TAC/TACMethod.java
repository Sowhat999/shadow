package shadow.TAC;

import java.util.LinkedList;

public class TACMethod extends TACNode {
	
	protected LinkedList<TACNode> body;
	
	public TACMethod(String name, TACNode parent) {
		super(name, parent);
		body = new LinkedList<TACNode>();
	}
	
	public void addNode(TACNode node) {
		body.add(node);
	}
	
}