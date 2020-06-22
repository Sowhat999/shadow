package shadow.tac;

import shadow.tac.nodes.TACCatchSwitch;
import shadow.tac.nodes.TACLabel;
import shadow.tac.nodes.TACNode;
import shadow.tac.nodes.TACPad;
import shadow.tac.nodes.TACPhi;
import shadow.typecheck.type.PointerType;
import shadow.typecheck.type.SimpleModifiedType;

/**
 * Represents blocks of Shadow code, usually surrounded by braces.
 * Blocks critically contain references to the many labels that they
 * may branch to.
 * @author Jacob Young
 * @author Barry Wittman 
 */
public class TACBlock
{
	private TACBlock parent;
	private TACLabel breakLabel, continueLabel;
	private TACLabel recoverLabel, doneLabel, catchLabel, catchSwitchLabel;
	// Although it seems like overkill, we need many different items for a cleanup:
	private TACLabel cleanupLabel;		// Label for the actual cleaning up code, which everything will visit, even if not unwinding
	private TACPhi cleanupPhi;			// Phi for returning to wherever we were before doing the cleanup
	
	private TACLabel cleanupUnwindLabel; // Label for the cleanup done when unwinding
	
	private TACMethod method;
	private boolean unwindTarget = false; // Used to see if the block can be reached by unwinding, important for finally code-generation
	private boolean cleanupTarget = false; // Used to see if the block contains a cleanup, important for finally code-generation
	private TACLabel parentPad = null;

	
	public TACBlock(TACMethod method) {
		this(method, null);	
	}
	
	public TACBlock(TACNode node, TACBlock parentBlock) {
		this(node.getMethod(), parentBlock);
		node.setBlock(this);
	}
	
	private TACBlock(TACMethod method, TACBlock parentBlock) {
		parent = parentBlock;
		this.method = method;		
	}
	
	public TACMethod getMethod() {
		return method;
	}

	public TACBlock getParent() {
		return parent;
	}

	public boolean hasBreak()
	{
		return breakLabel != null;
	}
	public TACLabel getBreak()
	{
		return breakLabel;
	}
	public TACBlock getBreakBlock()
	{
		TACBlock block = this;
		while (block != null && !block.hasBreak())
			block = block.getParent();
		return block;
	}
	public TACBlock addBreak()
	{
		if (breakLabel != null)
			throw new IllegalStateException("Break label already added.");
		breakLabel = new TACLabel(method);
		return this;
	}
	public boolean hasContinue()
	{
		return continueLabel != null;
	}
	public TACLabel getContinue()
	{
		return continueLabel;
	}
	public TACBlock getContinueBlock()
	{
		TACBlock block = this;
		while (block != null && !block.hasContinue())
			block = block.getParent();
		return block;
	}

	public TACBlock addContinue()
	{
		if (continueLabel != null)
			throw new IllegalStateException("Continue label already added.");
		continueLabel = new TACLabel(method);
		return this;
	}
	
	
	/*
	public TACCatchSwitch getCatchSwitch()
	{
		if (catchSwitch != null)
			return catchSwitch;
		return parent == null ? null : parent.getCatchSwitch();
	}
	
	public TACBlock addCatchSwitch(TACCatchSwitch catchSwitch)
	{
		if (this.catchSwitch != null)
			throw new IllegalStateException("Catch switch already added.");
		this.catchSwitch = catchSwitch;
		return this;
	}
	public TACCatchPad getCatchPad()
	{
		if (catchPad != null)
			return catchPad;
		return parent == null ? null : parent.getCatchPad();
	}

	public TACBlock addCatchPad(TACCatchPad catchPad)
	{
		if (this.catchPad != null)
			throw new IllegalStateException("Catch pad already added.");
		this.catchPad = catchPad;
		return this;
	}
	
	*/
	public TACLabel getRecover()
	{
		if (recoverLabel != null)
			return recoverLabel;
		return parent == null ? null : parent.getRecover();
	}
	
	public boolean hasRecover()
	{
		return getRecover() != null;
	}
	
	public TACBlock addRecover()
	{
		if (recoverLabel != null)
			throw new IllegalStateException("Recover label already added.");
		recoverLabel = new TACLabel(method);
		return this;
	}

	public TACLabel getDone()
	{
		if (doneLabel != null)
			return doneLabel;
		return parent == null ? null : parent.getDone();
	}
	public TACBlock addDone()
	{
		if (doneLabel != null)
			throw new IllegalStateException("Done label already added.");
		doneLabel = new TACLabel(method);
		return this;
	}

	public boolean hasCleanup()
	{
		//return getCleanup() != null;
		return cleanupLabel != null;
	}
	public TACLabel getCleanup()
	{
		if (cleanupLabel != null)
			return cleanupLabel;
		return parent == null ? null : parent.getCleanup();
	}
	
	public TACLabel getCleanupUnwind()
	{
		if (cleanupUnwindLabel != null)
			return cleanupUnwindLabel;
		return parent == null ? null : parent.getCleanupUnwind();
	}
	public TACPhi getCleanupPhi()
	{
		if (cleanupPhi != null)
			return cleanupPhi;
		return parent == null ? null : parent.getCleanupPhi();
	}
	
	public TACLabel getUnwind() {
		TACBlock currentBlock = this;
		while(currentBlock != null) {
			if(currentBlock.catchSwitchLabel != null)
				return currentBlock.catchSwitchLabel;
			if(currentBlock.cleanupUnwindLabel != null)
				return currentBlock.cleanupUnwindLabel;
			
			currentBlock = currentBlock.getParent();
		}
		
		return null;
	}

	
	public TACPad getParentPad() {
		/*
		 * The only parent pads that matter for funclet generation are 
		 * cleanup unwind pads.  These are the ones where the exception
		 * is still in-flight.  Other catches are caught and dealt with.
		 */
		
		TACBlock current = parent;
		while(current != null) {
			if(current.parentPad != null) {
				// A cleanup switch might have phi nodes after it
				TACNode node = parentPad.getNext();
				while(node.getClass() != TACCatchSwitch.class)
					node = node.getNext();

				// Cleanup switches only have one catchpad
				TACCatchSwitch cleanupSwitch = (TACCatchSwitch)node;
				return cleanupSwitch.getOperand(0);
			}			
			
			current = current.getParent();
		}
			
		return null;
	}
	
	/*
	public TACBlock getCleanupBlock()
	{
		return getCleanupBlock(null);
	}
	public TACBlock getCleanupBlock(TACBlock last)
	{
		return getCleanupBlock(this, last);
	}
	public TACBlock getNextCleanupBlock(TACBlock last)
	{
		return getCleanupBlock(getParent(), last);
	}
	private static TACBlock getCleanupBlock(TACBlock block, TACBlock last)
	{
		while (block != last && block.cleanupLabel == null)
			block = block.getParent();
		return block;
	}
	*/
	public TACBlock addCleanup()
	{
		if (cleanupLabel != null)
			throw new IllegalStateException("Cleanup label already added.");
		cleanupLabel = new TACLabel(method);
		cleanupPhi = new TACPhi(null, method.addTempLocal(new SimpleModifiedType(new PointerType())));
		cleanupUnwindLabel = new TACLabel(method);
		return this;
	}
	
	public TACBlock addCatch()
	{
		if (catchLabel != null)
			throw new IllegalStateException("Catch label already added.");
		catchLabel = new TACLabel(method);
		return this;
	}
	
	public TACBlock addCatchSwitch() {
		if (catchSwitchLabel != null)
			throw new IllegalStateException("Catch switch label already added.");
		catchSwitchLabel = new TACLabel(method);
		return this;		
	}

	public TACLabel getCatchSwitch() {
		if (catchSwitchLabel != null)
			return catchSwitchLabel;
		return parent == null ? null : parent.getCatchSwitch();
	}
	
	public boolean isUnwindTarget() {
		return unwindTarget;
	}
	
	/*
	 * Method calls and throws make it possible to unwind, perhaps all the way.
	 */
	public void addUnwindSource() {
		TACBlock block = this;
		while(block != null) {
			if(block.hasCleanup()) // only finally blocks
				block.unwindTarget = true;
			block = block.getParent();
		}
	}
	
	public TACBlock setCleanupTarget() {
		cleanupTarget = true;
		return this;		
	}
	
	// Used to see if code is inside of a cleanup
	// If it is, we don't report an error for dead code removal
	public boolean isInsideCleanup() {
		TACBlock block = this;
		while(block != null) {
			if(block.cleanupTarget)
				return true;
			
			block = block.getParent();
		}
		
		return false;
	}

	public TACBlock setParentPad(TACLabel parentPad) {
		this.parentPad = parentPad;
		return this;
	}	
}
