package shadow.typecheck.type;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import shadow.parser.javacc.Node;
import shadow.parser.javacc.SimpleNode;
import shadow.typecheck.Package;

public class InterfaceType extends Type 
{
	public InterfaceType(String typeName) {
		this( typeName, new Modifiers() );
	}
	
	public InterfaceType(String typeName, Modifiers modifiers) {
		super( typeName, modifiers );
	}	

	@Override
	public boolean hasInterface(InterfaceType type)
	{	
		if( this.getTypeWithoutTypeArguments().equals(type.getTypeWithoutTypeArguments() ))
			return true;
				
		for( InterfaceType interfaceType : getInterfaces() )
			if( interfaceType.hasInterface(type) )
				return true;
		
		return false;
	}
	
	@Override
	public HashSet<InterfaceType> getAllInterfaces()
	{	
		HashSet<InterfaceType> allInterfaces = super.getAllInterfaces();		
		allInterfaces.add(this);
		
		return allInterfaces;
		
		
		//allows duplicates and needs not to
		/*
		
		ArrayList<InterfaceType> list = super.getAllInterfaces();
		list.add(this);
		
		return list;
		*/
	}
	
	public boolean isDescendentOf(Type type)
	{			
		for( InterfaceType parent : getInterfaces() )
		{
			if( parent.equals(type) || parent.isDescendentOf( type ))
				return true;			
		}
		return false;
	}
	
	/*
	@Override
	public String getMangledName()
	{
		Package _package = getPackage();
		StringBuilder sb;
		if( _package == null )
			sb = new StringBuilder("_Pdefault");
		else
			sb = new StringBuilder(_package.getMangledName());
		sb.append(super.getMangledName());
		
		
		return sb.toString();		
	}
	
	@Override
	public String getMangledNameWithGenerics()
	{
		Package _package = getPackage();
		StringBuilder sb;
		if( _package == null )
			sb = new StringBuilder("_Pdefault");
		else
			sb = new StringBuilder(_package.getMangledName());
		sb.append(super.getMangledName());
		
		if( isParameterized() )
			sb.append(getTypeParameters().getMangledNameWithGenerics());

		return sb.toString();		
	}
	*/

	public boolean recursivelyContainsMethod( String symbol )
	{
		if( containsMethod(symbol))
			return true;		
		
		//check extends		
		for( InterfaceType parent : getInterfaces() )
			if( parent.recursivelyContainsMethod(symbol ) )
					return true;			 		
		
		return false;
	}

	
	
	protected void recursivelyGetAllMethods( List<MethodSignature> methodList  )
	{	
		for ( InterfaceType parent : getInterfaces() )
			parent.recursivelyGetAllMethods(methodList);

		for ( List<MethodSignature> methods : getMethodMap().values() )
			for ( MethodSignature method : methods )
				methodList.add(method);
	}


	@Override
	protected List<MethodSignature> recursivelyOrderAllMethods( List<MethodSignature> methodList )
	{
		for ( InterfaceType parent : getInterfaces() )
			parent.recursivelyOrderAllMethods(methodList);
		return orderMethods(methodList, false);
	}

	@Override
	protected List<MethodSignature> recursivelyOrderMethods( List<MethodSignature> methodList )
	{
		for ( InterfaceType parent : getInterfaces() )
			parent.recursivelyOrderAllMethods(methodList);
		return orderMethods(methodList, true);
	}

	public List<MethodSignature> orderAllMethods( ClassType implementation )
	{
		List<MethodSignature> methodList = orderAllMethods();
		implementation.orderMethods(methodList, false);
		return methodList;
	}

	public List<MethodSignature> orderMethods( ClassType implementation )
	{
		List<MethodSignature> methodList = orderMethods();
		implementation.orderMethods(methodList, false);
		return methodList;
	}


	protected void addAllMethods(String methodName, List<MethodSignature> list)
	{
		includeMethods( methodName, list );			
		for( InterfaceType _interface : getInterfaces() )				
			_interface.addAllMethods(methodName, list);		
	}
	
	//get methods from interface and ancestors
	public List<MethodSignature> getAllMethods(String methodName)
	{
		List<MethodSignature> list = new ArrayList<MethodSignature>();		
		addAllMethods( methodName, list );	
		
		//get from Object, too
		list.addAll( Type.OBJECT.getAllMethods(methodName));		
		
		return list;
	}
	
	@Override
	public InterfaceType replace(SequenceType values, SequenceType replacements )
	{		
		if( isRecursivelyParameterized() )
		{					
			Type cached = typeWithoutTypeArguments.getInstantiation(replacements);
			if( cached != null )
				return (InterfaceType)cached;
			
			InterfaceType replaced = new InterfaceType( getTypeName(), getModifiers() );
			replaced.setPackage(getPackage());
			
			replaced.typeWithoutTypeArguments = typeWithoutTypeArguments;			
			typeWithoutTypeArguments.addInstantiation(replacements, replaced);
			
			for( InterfaceType _interface : getInterfaces() )
				replaced.addInterface(_interface.replace(values, replacements));		
			
			//only constant non-parameterized fields in an interface
			Map<String, Node> fields = getFields(); 
			
			for( String name : fields.keySet() )
			{
				SimpleNode field = (SimpleNode)(fields.get(name));
				field = field.clone();
				field.setType(field.getType());			
				replaced.addField(name, field );
			}
			
			Map<String, List<MethodSignature> > methods = getMethodMap();
			
			for( String name : methods.keySet() )
			{
				List<MethodSignature> signatures = methods.get(name);
				
				for( MethodSignature signature : signatures )
				{
					MethodSignature replacedSignature = signature.replace(values, replacements);					
					replaced.addMethod(name, replacedSignature);
				}
			}
			
			//should have no inner interfaces in an interface		
			/*
			Map<String, Type> inners = getInnerClasses();
			
			for( String name : inners.keySet() )		
				replaced.addInnerClass(name, inners.get(name).replace(values, replacements));
			*/
			
			//replaced.setTypeArguments( new SequenceType(replacements) );
						
			for( ModifiedType modifiedParameter : getTypeParameters() )
			{
				Type parameter = modifiedParameter.getType();
				replaced.addTypeParameter(new SimpleModifiedType(parameter.replace(values, replacements), modifiedParameter.getModifiers() ));
			}
			
			return replaced;
		}
		
		
		return this;
	}
	
	@Override
	public boolean isRecursivelyParameterized()
	{
		if( isParameterized() )
			return true;
		
		for( InterfaceType parent : getInterfaces() )
			if( parent.isRecursivelyParameterized() )
				return true;
		
		return false;
	}
	
	public boolean isSubtype(Type t)
	{
		if( t == UNKNOWN )
			return false;
	
		if( equals(t) || t == Type.OBJECT || t == Type.VAR )
			return true;		
		
		if( t instanceof InterfaceType )			
			return isDescendentOf(t);
		else
			return false;	
	}
	
	@Override
	public int getWidth()
	{
		return OBJECT.getWidth() * 2;
	}
	
	/*
	@Override
	public int hashCode() {
		String name = getImportName();
		//interfaces must be differentiated by generics
		//since it is possible for a class to implement
		//multiple generic versions of the same interface
		if( isParameterized() )
			name += getTypeParameters().toString("<", ">");
		
		return name.hashCode();
	}
	*/
	
	@Override
	public InterfaceType getTypeWithoutTypeArguments()
	{
		return (InterfaceType)super.getTypeWithoutTypeArguments();
	}
	
	public void printMetaFile(PrintWriter out, String linePrefix )
	{
		printImports(out, linePrefix);		
		
		//modifiers
		out.print("\n" + linePrefix + getModifiers());		
		out.print("interface ");
		
		//type name
		if( getOuter() == null ) //outermost interface		
			out.print(getQualifiedName(true));
		else
		{
			String name = toString(true); 
			out.print(name.substring(name.lastIndexOf(':') + 1));
		}
		
		//extend types		
		if( getInterfaces().size() > 0 )
		{
			out.print("extends " );
			boolean first = true;
			for( InterfaceType _interface : getInterfaces() )
			{
				if(!first)
					out.print(", ");
				else
					first = false;
				out.print(_interface.getQualifiedName());				
			}			
		}
		
		out.println(linePrefix + "{");
		
		String indent = linePrefix + "\t";		
		boolean newLine;
				
		//constants are the only fields in interfaces
		newLine = false;
		for( Map.Entry<String, Node> field : getFields().entrySet() )
			if( field.getValue().getModifiers().isConstant() )
			{
				out.println(indent + "constant " + field.getValue().getType() + " " + field.getKey() + ";");
				newLine = true;
			}
		if( newLine )
			out.println();	

		//methods
		newLine = false;
		for( List<MethodSignature> list: getMethodMap().values() )		
			for( MethodSignature signature : list )
			{
				Modifiers modifiers = signature.getModifiers();
				//hack because interface methods are public inside the compiler but cannot be specified that way
				modifiers.removeModifier(Modifiers.PUBLIC);
				out.println(indent + signature + ";");
				modifiers.addModifier(Modifiers.PUBLIC);
				newLine = true;				
			}
		if( newLine )
			out.println();	
		
		
		out.println(linePrefix + "}\n");	
	}
}