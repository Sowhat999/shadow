package shadow.tac;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import shadow.output.text.TextOutput;
import shadow.parser.javacc.ShadowException;
import shadow.typecheck.type.ClassType;
import shadow.typecheck.type.InterfaceType;
import shadow.typecheck.type.ModifiedType;
import shadow.typecheck.type.Type;

/**
 * Represents an entire "class" or "unit" in TAC.
 * 
 * Consists of the module's type, references, fields, constants, and methods.
 */
public class TACModule {
    private final Type type;
    private final Set<Type> references = new HashSet<Type>(); // ??? what are these?
    private final Map<String, Type> fields = new LinkedHashMap<String, Type>();
    private final List<TACConstant> constants = new ArrayList<TACConstant>();
    private final List<TACMethod> methods = new ArrayList<TACMethod>();

    public TACModule(Type moduleType) {
        type = moduleType;
        
        if (moduleType instanceof ClassType) {
            for (Entry<String, ? extends ModifiedType> field : ((ClassType) moduleType).orderAllFields())
                // if we have a class, the set all the field types
                fields.put(field.getKey(), field.getValue().getType());
        }
        
        add(moduleType);
        add(Type.BOOLEAN);
        add(Type.UBYTE);
        add(Type.BYTE);
        add(Type.USHORT);
        add(Type.SHORT);
        add(Type.UINT);
        add(Type.INT);
        add(Type.ULONG);
        add(Type.LONG);
        add(Type.CODE);
        add(Type.FLOAT);
        add(Type.DOUBLE);
        add(Type.ARRAY);
        add(Type.EXCEPTION);
        add(Type.GENERIC_CLASS);
        // add(Type.ARRAY_CLASS);
        // add(Type.METHOD_CLASS);
    }

    /**
     * Adds a type to the set of references for this module, recursing through all types.
     * @param type the type to add to the references (Set<Type>) 
     */
    private void add(Type type) {
        boolean newlyAdded;

        /*
         * if( type instanceof InterfaceType ) newlyAdded =
         * references.add(type); else {
         */

        if (type.isFullyInstantiated() || type.isUninstantiated())
            newlyAdded = references.add(type);
        else
            newlyAdded = false;

        if (newlyAdded) {
            if (type instanceof ClassType) {
                final ClassType classType = (ClassType) type;
                
                if (classType.getExtendType() != null)
                    add(classType.getExtendType());
                
                for (Type innerType : classType.getInnerClasses().values())
                    add(innerType);
            }
            
            for (InterfaceType interfaceType : type.getInterfaces())
                add(interfaceType);
            
            if (type.getOuter() != null)
                add(type.getOuter());
            
            for (Type referenced : type.getReferencedTypes())
                add(referenced);
        }
    }

    public Type getType() {
        return type;
    }

    public boolean isClass() {
        return type instanceof ClassType;
    }

    public ClassType getClassType() {
        // ??? would it be wise to call isClass() first and throw some type of exception?
        return (ClassType) type;
    }

    public boolean isInterface() {
        return type instanceof InterfaceType;
    }

    public InterfaceType getInterfaceType() {
        return (InterfaceType) type;
    }

    public String getName() {
        return type.getTypeName();
    }

    public String getQualifiedName() {
        return type.getQualifiedName();
    }

    public Set<Type> getReferences() {
        return references;
    }

    public Set<String> getFieldNames() {
        return fields.keySet();
    }

    public Collection<Type> getFieldTypes() {
        return fields.values();
    }

    public Type getFieldType(String name) {
        return fields.get(name);
    }

    public void addConstant(TACConstant constant) {
        constants.add(constant);
    }

    public List<TACConstant> getConstants() {
        return constants;
    }

    public void addMethod(TACMethod method) {
        methods.add(method);
    }

    public List<TACMethod> getMethods() {
        return methods;
    }

    @Override
    public String toString() {
        final StringWriter writer = new StringWriter();
        
        try {
            new TextOutput(writer).build(this);
        } catch (ShadowException ex) {
            return "Error";
        }
        
        return writer.toString();
    }
}
