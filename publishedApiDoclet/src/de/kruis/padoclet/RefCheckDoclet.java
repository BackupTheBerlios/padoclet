/*
 *  PublishedApiDoclet - a filter proxy for any javadoc doclet
 *  
 *  Copyright (C) 2007  Anselm Kruis <a.kruis@science-computing.de>
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package de.kruis.padoclet;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ConstructorDoc;
import com.sun.javadoc.Doc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.Doclet;
import com.sun.javadoc.ExecutableMemberDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.SeeTag;
import com.sun.javadoc.SourcePosition;
import com.sun.javadoc.Type;
import com.sun.tools.javadoc.Main;

import de.kruis.padoclet.util.AbstractOption;


public class RefCheckDoclet {

	public static final String WARNING_FIELD_TYPE = "fieldType";
	public static final String WARNING_THROWN_CLASS = "thrownClass";
	public static final String WARNING_PARAMETER_TYPE = "parameterType";
	public static final String WARNING_OVERRIDDEN_METHOD = "overriddenMethod";
	public static final String WARNING_RETURN_TYPE = "returnType";
	public static final String WARNING_NESTED_CLASS = "nestedClass";
	public static final String WARNING_IMPLEMENTED_INTERFACE = "implementedInterface";
	public static final String WARNING_CONTAINING_PACKAGE = "containingPackage";
	public static final String WARNING_CONTAINING_CLASS = "containingClass";
	public static final String WARNING_SEE_OR_LINK_REFERENCE = "seeOrLinkReference";
	public static final String WARNING_SUPER_CLASS = "superClass";
	public static final String WARNING_ALL = "all";
	private DocErrorReporter errorReporter;
	private Set warnOn = new HashSet();
	private RefCheckDoclet() {
	}
	
	
	
	
	private void checkReference(Doc doc, Type type, String warning) {
		checkReference(doc,type.asClassDoc(),warning);
	}
	private void checkReference(Doc doc, ClassDoc classDoc, String warning) {
		checkReference(doc,(Doc)classDoc,warning);
	}
	
	private void checkReference(Doc doc, Doc referenced, String warning) {
		if (referenced == null)
			return;
		if (! isWarnOn(warning))
			return;
		if (referenced.isIncluded())
			return;
		// test, if source is available
		SourcePosition position = referenced.position();
		if (position == null || position.file() == null || position.line() == 0) {
			// no position, no source, no warning
			// getErrorReporter().printNotice(doc.position(), "reference to "+referenced+" at "+position);
			return;
		}
		// format the warning: change the Camel notation to a more readable variant
		StringBuffer msg = new StringBuffer("reference to undocumented ");
		char[] cs = warning.toCharArray();
		for(int i=0;i<cs.length;i++) {
			if (Character.isUpperCase(cs[i])) {
				msg.append(' ');
				cs[i] = Character.toLowerCase(cs[i]);
			}
			msg.append(cs[i]);
		}
		msg.append(": ").append(referenced.toString());
		getErrorReporter().printWarning(doc.position(), msg.toString());
	}
	
	
    private boolean check(RootDoc root) {
    	ClassDoc[] classDocs = root.classes();
    	for(int i=0; i<classDocs.length; i++) {
    		check(classDocs[i]);
    	}
    	PackageDoc[] packages = root.specifiedPackages();
    	for(int i=0;i<packages.length;i++) {
    		if(! packages[i].isIncluded())
    			continue;
    		checkDoc(packages[i]);
    	}
		return true;
	}

    private void checkDoc(Doc doc) {
    	SeeTag[] tags = doc.seeTags();
    	for(int i=0;i<tags.length;i++) {
    		Doc r = tags[i].referencedMember();
    		if (r == null)
    			r = tags[i].referencedClass();
    		if (r == null)
    			r = tags[i].referencedPackage();
    		checkReference(doc,r,RefCheckDoclet.WARNING_SEE_OR_LINK_REFERENCE);
    	}
    }
    
    private void check(ClassDoc doc) {
    	checkDoc(doc);
    	// check superclass
    	checkReference(doc, doc.superclass(), WARNING_SUPER_CLASS);
    	// check containing class
    	checkReference(doc, doc.containingClass(), RefCheckDoclet.WARNING_CONTAINING_CLASS);
    	// check containing package
    	checkReference(doc, doc.containingPackage(), RefCheckDoclet.WARNING_CONTAINING_PACKAGE);
    	// check interfaces
    	ClassDoc[] interfaces = doc.interfaces();
    	for(int i=0; i<interfaces.length; i++) {
    		checkReference(doc,interfaces[i],RefCheckDoclet.WARNING_IMPLEMENTED_INTERFACE);
    	}
    	// check nested classes
    	ClassDoc[] nestedClasses = doc.innerClasses();
    	for(int i=0;i<nestedClasses.length;i++) {
    		checkReference(doc,nestedClasses[i], RefCheckDoclet.WARNING_NESTED_CLASS);
    	}
    	// check fields
    	FieldDoc[] fields = doc.fields();
    	for(int i=0; i<fields.length; i++) {
    		check(fields[i]);
    	}
    	// check constructors
    	ConstructorDoc[] constructors = doc.constructors();
    	for(int i=0; i<constructors.length; i++) {
    		check(constructors[i]);
    	}
    	// check methods    	
    	MethodDoc[] methods = doc.methods();
    	for(int i=0;i<methods.length;i++) {
    		check(methods[i]);
    	}
	}

    private void check(MethodDoc method) {
    	check((ExecutableMemberDoc)method);
    	// check type
    	checkReference(method,method.returnType(),RefCheckDoclet.WARNING_RETURN_TYPE);
    	// check overriddenMethod
    	checkReference(method,method.overriddenMethod(),RefCheckDoclet.WARNING_OVERRIDDEN_METHOD);
	}

	private void check(ExecutableMemberDoc emember) {
    	checkDoc(emember);
		// check parameters
		Parameter[] parameters = emember.parameters();
		for(int i=0;i<parameters.length;i++) {
			checkReference(emember,parameters[i].type(), RefCheckDoclet.WARNING_PARAMETER_TYPE);
		}
		
		// check exceptions
		ClassDoc[] exceptions = emember.thrownExceptions();
		for(int i=0;i<exceptions.length;i++) {
			checkReference(emember, exceptions[i], RefCheckDoclet.WARNING_THROWN_CLASS);
		}
	}

	private void check(FieldDoc field) {
		checkDoc(field);
		checkReference(field,field.type(),RefCheckDoclet.WARNING_FIELD_TYPE);
	}

	/**
	 * @param warnOn The list of warning conditions to set
	 */
	public final void setWarnOn(String warnOn) {
		StringTokenizer tokenizer = new StringTokenizer(warnOn,", ");
		while(tokenizer.hasMoreElements()) {
			this.warnOn.add(tokenizer.nextToken());
		}
	}	
	/**
	 * Warn on a certain condition?
	 * 
	 * @param condition the condition to test
	 * @return <code>true</code>, if the warnig is active
	 */
	public final boolean isWarnOn(String condition) {
		return this.warnOn.contains(condition) || this.warnOn.contains(RefCheckDoclet.WARNING_ALL);
	}

	/**
     * @return Returns the errorReporter provided by the doclet core.
     */
    public final DocErrorReporter getErrorReporter() {
        return errorReporter;
    }
    /**
     * @param errorReporter The errorReporter to set.
     */
    public final void setErrorReporter(DocErrorReporter errorReporter) {
        this.errorReporter = errorReporter;
    }

    // register the options. The option names must match the setable properties of 
    // the class
   	static {
    		Option.register(new Option("WarnOn",RefCheckDoclet.WARNING_ALL,false,"A comma separated list of conditions, that will cause a warning. Valid conditions are:"+Option.LI
    				+"   \""+RefCheckDoclet.WARNING_SUPER_CLASS+"\"           - the super class of an included class is not documented"+Option.LI
    				+"   \""+RefCheckDoclet.WARNING_FIELD_TYPE+"\"            - a field type is undocumented"+Option.LI
    				+"   \""+RefCheckDoclet.WARNING_THROWN_CLASS+"\"          - a thrown exception or error is undocumented"+Option.LI
    				+"   \""+RefCheckDoclet.WARNING_PARAMETER_TYPE+"\"        - a parameter type is undocumented"+Option.LI
    				+"   \""+RefCheckDoclet.WARNING_RETURN_TYPE+"\"           - a return type is undocumented"+Option.LI
    				+"   \""+RefCheckDoclet.WARNING_OVERRIDDEN_METHOD+"\"     - an overridden method is undocumented"+Option.LI
    				+"   \""+RefCheckDoclet.WARNING_NESTED_CLASS+"\"          - a nested class is undocumented"+Option.LI
    				+"   \""+RefCheckDoclet.WARNING_IMPLEMENTED_INTERFACE+"\" - an implemented interface is undocumented"+Option.LI
    				+"   \""+RefCheckDoclet.WARNING_CONTAINING_PACKAGE+"\"    - the containing package is undocumented"+Option.LI
    				+"   \""+RefCheckDoclet.WARNING_CONTAINING_CLASS+"\"      - the containing class of a nested class is undocumented"+Option.LI
    				+"   \""+RefCheckDoclet.WARNING_SEE_OR_LINK_REFERENCE+"\"   - a @see or @link tag points to an undocumented item"+Option.LI
    				+"   \""+RefCheckDoclet.WARNING_ALL+"\"                  - all of the above"));
   	}

    /**
     * The doclet optionLength method.
     * 
     * @param option the name of the option
     * @return the length or 0, if the option is unknown 
     * @see com.sun.javadoc.Doclet#optionLength(java.lang.String)
     */
    public static int optionLength(String option) {
    	// delegate the work to the helper method
    	int length = Option.optionLength(option);
    	if (length > 0)
    		return length;
        if ("-help".equals(option)) {
            System.out.println(Option.LF+"Provided by "+RefCheckDoclet.class.getName()+" doclet:"
                    +Option.LF+Option.getDescriptions());
        }
        return length;
    }

    /**
     * Implements the doclet validOptions method
     * 
     * @param options the options
     * @param reporter used to emit messages
     * @return <code>true</code>, is everything is ok, <code>false</code> otherwise.
     * @throws java.io.IOException
     * @see com.sun.javadoc.Doclet#validOptions(java.lang.String[][], com.sun.javadoc.DocErrorReporter)
     */
    public static boolean validOptions(String[][] options,
            DocErrorReporter reporter) throws java.io.IOException {
    	boolean showHelp = false;
    	
    	Option.initOptions(options);
    	AbstractOption helpOption = Option.get("Help");
    	if (helpOption != null && helpOption.isSet()) {
    		showHelp = true;
    	}
        if (showHelp) {
        	reporter.printNotice(Option.LF+
        			RefCheckDoclet.class.getName()+ " options:"+
        			Option.LF+Option.getDescriptions());
        	return false;
        }
        return true;
    }

    /**
     * The doclet start method.
     * 
     * @param root the RootDoc object
     * @return <code>true</code>, if everything is ok, otherwise <code>false</code>.
     * @throws java.io.IOException
     * @see com.sun.javadoc.Doclet#start(com.sun.javadoc.RootDoc)
     */
    public static boolean start(RootDoc root) throws java.io.IOException {
        // create the filter doclet instance
        RefCheckDoclet rcd = new RefCheckDoclet();
        
        // process our options
        Option.initOptions(root.options());
        try {
			Option.initJavaBeanProperties(rcd);
		} catch (Throwable e) {
			e.printStackTrace();
			root.printError(e.toString());
			return false;
		}
        rcd.setErrorReporter(root);
        return rcd.check(root);
    }
    
    /**
     * A main method.
     * 
     * This method simply calls the doclet main method.
     * 
     * @param args the command line arguments
     * @see Main#execute(java.lang.String, java.lang.String, java.lang.String[])
     */
    public static void main(String[] args) {
        String name = RefCheckDoclet.class.getName();
        Main.execute(name, name, args);
    }
    
    /**
     * Option handling for doclets.
     * 
     * This class holds static methods and data about the options given 
     * to the doclet. Additionally instances oft the class represent
     * single options.
     * 
     * @author kruis
     *
     */
    protected static class Option extends AbstractOption{
    	
    	/**
    	 * all doclet options start with this string.
    	 */
    	public final static String namePrefix = "-rc";
    	
    	
    	/**
    	 * Create a new option, that has a value.
    	 * 
    	 * @param name the name
    	 * @param defaultValue the default value
    	 * @param isTag set to <code>true</code>, if the value of the option names
    	 * a tag.
    	 * @param description the description of the option
    	 */
    	public Option(String name, String defaultValue, boolean isTag, String description) {
    		super(name, namePrefix, defaultValue, isTag, description);
    	}
    	
    	/**
    	 * Create a new option, that has no value (boolean option).
    	 * 
    	 * @param name the name
    	 * @param description the description of the option.
    	 */
    	public Option(String name, String description) {
    		super(name,namePrefix,description);
    	}
    	
    	/**
    	 * holds a (sorted) map of all known options
    	 */
    	private static Map options = new TreeMap();

    	/**
    	 * Register an option.
    	 * 
    	 * This method is intended to be used from a static initializer. It let
    	 * you define the set of possible option.
    	 * 
    	 * @param option the option to register.
    	 */
    	public static void register(AbstractOption option) {
    		register(option,options);
    	}
    	
    	/**
    	 * Get an option by name.
    	 * 
    	 * @param name the name of the option.
    	 * @return the option object. Returns <code>null</code>, if no option with the given  
    	 * name was registered. 
    	 */
    	public static AbstractOption get(String name) {
    		return get(name, options);
    	}
    	/**
    	 * Get a string made from the descriptions of all registered options.
    	 * 
    	 * @return the compiled descriptions.
    	 */
    	public static String getDescriptions() {
    		return getDescriptions(options);
    	}
    	
    	/**
    	 * Get all tags
    	 * 
    	 * @return a set containing all tag names, that is the values of all
    	 * options where the property <code>isTag</code> is set.
    	 */
    	public static Set getTags() {
    		return getTags(options);
    	}
    	
    	
    	/**
    	 * Get the number of parameters this option takes.
    	 * 
    	 * @param name the name of the option.
    	 * @return 1, if the option takes no parameters, 2, if the option takes a parameter. If the option is unknown, return 0.
    	 */
    	public static int optionLength(String name) {
    		return optionLength(name, options);
    	}
    	/**
    	 * Initialize the option values.
    	 * 
    	 * @param docletoptions the options as provided by the javadoc core.
    	 * @see Doclet#validOptions(java.lang.String[][], com.sun.javadoc.DocErrorReporter)
    	 * @see RootDoc#options()
    	 */
    	public static void initOptions(String [][]docletoptions) {
    		initOptions(docletoptions, options);
    	}
    	
    	/**
    	 * Assign option values to matching bean properties.
    	 * 
    	 * For each setable property of the Java bean, this method looks for an 
    	 * option with the same name. If such an option exists, the property is set to the 
    	 * value of the option. Currently only beans of the types <code>String</code>, <code>boolen</code> and 
    	 * <code>int</code> are supported.
    	 * 
    	 * @param bean a java bean
    	 * @throws Throwable
    	 */
    	public static void initJavaBeanProperties(Object bean) throws Throwable {
    		initJavaBeanProperties(bean, options);
    	}
    	
    	// register default options
    	static {
    		register(new Option("Help","Show this help message."));
    	}
    }

}
