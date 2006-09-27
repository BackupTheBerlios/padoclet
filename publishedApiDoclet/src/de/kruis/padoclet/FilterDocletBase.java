/*
 *  PublishedApiDoclet - a filter proxy for any javadoc doclet
 *  
 *  Copyright (C) 2006  Anselm Kruis <a.kruis@science-computing.de>
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

/*
 * This file is based on a public domain source found at
 * http://www.sixlegs.com/blog/java/exclude-javadoc-tag.html
 * 
 * Many thanks to Chris Nokleberg for this piece of code.
 */

package de.kruis.padoclet;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.Doclet;
import com.sun.javadoc.RootDoc;

import de.kruis.padoclet.HalfDynamicProxy.MessageInterface;

/**
 * This class is a base class for javadoc filter doclets. 
 * 
 *  A filter doclet is plugged between the javadoc core and a second
 *  doclet, that is used to create the output. The filter doclet modifies 
 *  the information about the java code to be documented. It can hide 
 *  java items (as done by the {@link de.kruis.padoclet.PublishedApiDoclet})
 *  or change the information in any other way.
 *  
 *  <p>
 *  This class is not a doclet by itself. It is intended as a base class for 
 *  a doclet. The derived class has to implement the static methods required by 
 *  a doclet and to setup the options ({@link FilterDocletBase.Option#register(Option)})
 *  and the proxy table ({@link de.kruis.padoclet.HalfDynamicProxy#setProxyClassTable(Class[][])}).
 *  See {@link de.kruis.padoclet.PublishedApiDoclet} for an example.
 *  
 * @author kruis
 *
 */
public class FilterDocletBase implements MessageInterface {
    
    /**
     * The name of the systrem property, that contains the name of the
     * delegate doclet. If this system property is unset, the default 
     * doclet (<code>com.sun.tools.doclets.standard.Standard</code>) is used.
     */
    public static final String PAD_DELEGATE_DOCLET_SYSTEM_PROPERTY = "PublishedApiDoclet.delegate";
    
    
    /**
     * This class is used to perform a lazy instantiation of the
     * delegate / formating doclet class.
     * 
     * @author kruis
     * @pad.exclude 
     */
    private static class DH {
		/**
         * holds the delegat doclet
         */
        public static final Class delegateDoclet;
        static {
            String classname = System.getProperty(PAD_DELEGATE_DOCLET_SYSTEM_PROPERTY,"com.sun.tools.doclets.standard.Standard");
            Class clazz = null;
            try {
                clazz = Thread.currentThread().getContextClassLoader().loadClass(classname);
            } catch (Exception e) {
                e.printStackTrace();
            } 
            delegateDoclet = clazz;
        }
    }

    
    /**
     * holds the error reporter provided by the javadoc core
     */
    private DocErrorReporter errorReporter;
   
    /**
     * Create a new <code>FilterDocletBase</code> instance.
     * 
     * This constructor is <code>protected</code>, because this
     * constructor is intended to be called by subclasses only.
     */
    protected FilterDocletBase() {
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


	/* (non-Javadoc)
	 * @see de.kruis.padoclet.HalfDynamicProxy.MessageInterface#recive(java.lang.String)
	 */
	public void emitMessage(String theMessage, int priority) {
		if(priority == MessageInterface.PRIORITY_DEBUG) {
			errorReporter.printNotice(theMessage);		
		} else if (priority == MessageInterface.PRIORITY_WARN) {
			errorReporter.printWarning(theMessage);
		} else {
			errorReporter.printError(theMessage);
		}
	}   
    
    /**
     * Invoke a static method on the delegate doclet.
     * 
     * This method performs a few security checks.
     * 
     * @param name name of the method to be invoked
     * @param par an array, that contains the method parameters
     * @return the return value of the invoked method.
     */
    private static Object delegateDocletInvoke(String name, Object[] par) {
        try{
        Method[] docletmethods = Doclet.class.getMethods();
        for(int i=0;i<docletmethods.length;i++) {
            if(! docletmethods[i].getName().equals(name)) 
                continue;
            int modifiers = docletmethods[i].getModifiers();
            if (! (Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers)))
                continue;
            Class[] partypes = docletmethods[i].getParameterTypes();
            if (partypes.length != (par!=null ? par.length : 0)) 
                continue;
            for(int j=0;j<partypes.length;j++) {
                if (! (par[j] == null || partypes[j].isInstance(par[j]))) 
                    continue;
            }
            // OK, we hav the right method signature
            Method m = DH.delegateDoclet.getMethod(name, partypes);
            modifiers = m.getModifiers();
            if (! (Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers)))
                throw new NoSuchMethodException("Method is not public static: "+m.toString());
            if (! docletmethods[i].getReturnType().isAssignableFrom(m.getReturnType()))
                throw new NoSuchMethodException("Method has incompatible return type: "+m.toString());
            try {
            	return m.invoke(null,par);
            }catch(InvocationTargetException e) {
            	Throwable targetException = e.getTargetException();
            	if (targetException instanceof Exception) {
            		throw (Exception) targetException;
            	} else {
            		throw new RuntimeException(targetException);
            	}
            }
        }
        throw new NoSuchMethodException(name);
        } catch(RuntimeException e) {
            throw e;
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
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
    protected static class Option {
    	/**
    	 * all filter doclet options start with this string.
    	 */
    	public final static String namePrefix = "-pad";
    	
    	/**
    	 * Holds one line.separator character. 
    	 */
    	public final static String LF = System.getProperty("line.separator");
    	/**
    	 * Holds a line.separator and some spaces. Used to format option descriptions.
    	 */
    	public final static String LI = LF+"            ";
    	/**
    	 * the name of the option without the namePrefix.
    	 */
    	public final String name;
    	/**
    	 * the value of the option. For boolean options the values <code>"true"</code>
    	 * and <code>"false"</code> are used.
    	 */
    	public String value;
    	
    	/**
    	 * the default value for the option. This value is used, if the 
    	 * option is not given on the command line. For boolean options the values <code>"true"</code>
    	 * and <code>"false"</code> are used.
    	 */
    	private final String defaultValue;
    	
    	/**
    	 * <code>true</code>, if the option has no value parameter. Otherwise, the 
    	 * option takes one parameter.
    	 */
    	public final boolean isBoolean;
    	/**
    	 * <code>true</code>, if the value of the option names a javadoc tag. 
    	 * This information is used to add <code>-tag tagname:X</code> options to 
    	 * the command line of the formating doclet.
    	 */
    	public final boolean isTag;
    	/**
    	 * a description of the option. Used for the online help message.
    	 */
    	public final String description;
    	
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
    		this.isBoolean = false;
    		this.name = name;
    		this.value = this.defaultValue = defaultValue;
    		this.isTag = isTag;
    		this.description = description;
    	}
    	
    	/**
    	 * Create a new option, that has no value (boolean option).
    	 * 
    	 * @param name the name
    	 * @param description the description of the option.
    	 */
    	public Option(String name, String description) {
    		this.isBoolean = true;
    		this.name = name;
    		this.value = this.defaultValue = Boolean.FALSE.toString();
    		this.isTag = false;
    		this.description = description;
    	}
    	
    	/**
    	 * Is a boolean option given?
    	 * 
    	 * @return <code>true</code>, if a boolean option is set (to
    	 * be exact, if the value of the option is <code>"true"</code>. Otherwise
    	 * returns false.
    	 */
    	public boolean isSet() {
    		return Boolean.valueOf(value).booleanValue();
    	}
		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		public String toString() {
            String strTmp = namePrefix+name+(isBoolean?"":" <value>");  
			return strTmp 
				+("                                  ".substring(strTmp.length()))
				+"Default value is: '"+defaultValue+"'"+LI+description;
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
    	public static void register(Option option) {
    		options.put(Introspector.decapitalize(option.name),option);
    	}
    	/**
    	 * Get an option by name.
    	 * 
    	 * @param name the name of the option.
    	 * @return the option object. Returns <code>null</code>, if no option with the given  
    	 * name was registered. 
    	 */
    	public static Option get(String name) {
    		return (Option) options.get(Introspector.decapitalize(name));
    	}
    	/**
    	 * Get a string made from the descriptions of all registered options.
    	 * 
    	 * @return the compiled descriptions.
    	 */
    	public static String getDescriptions() {
    		StringBuffer sb = new StringBuffer();
    		Iterator iterator = options.values().iterator();
    		while(iterator.hasNext()) {
    			sb.append(iterator.next()).append(LF);
    		}
    		return sb.toString();
    	}
    	
    	/**
    	 * Get all tags
    	 * 
    	 * @return a set containing all tag names, that is the values of all
    	 * options where the property <code>isTag</code> is set.
    	 */
    	public static Set getTags() {
    		Set set = new HashSet();
    		Iterator iterator = options.values().iterator();
    		while(iterator.hasNext()) {
    			Option o = (Option) iterator.next();
    			if(o.isTag) set.add(o.value);
    		}
    		return set;
    	}
    	/**
    	 * Get an option by its prefixed name.
    	 * @param name the name of the option including the prefix.
    	 * @return the option or <code>null</code>, if no matching option exists.
    	 */
    	private static Option getWithPrefix(String name) {
    		if (name == null || ! name.startsWith(namePrefix))
    			return null;
    		return get(name.substring(namePrefix.length()));
    	}
    	
    	
    	/**
    	 * Get the number of parameters this option takes.
    	 * 
    	 * @param name the name of the option.
    	 * @return 1, if the option takes no parameters, 2, if the option takes a parameter. If the option is unknown, return 0.
    	 */
    	public static int optionLength(String name) {
    		Option option = getWithPrefix(name);
    		if (option == null)
    			return 0;
    		return option.isBoolean ? 1 : 2;
    	}
    	/**
    	 * Initialize the option values.
    	 * 
    	 * @param options the options as provided by the javadoc core.
    	 * @see Doclet#validOptions(java.lang.String[][], com.sun.javadoc.DocErrorReporter)
    	 * @see RootDoc#options()
    	 */
    	public static void initOptions(String [][]options) {
    		for(int i=0;i<options.length;i++) {
    	   		Option option = getWithPrefix(options[i][0]);
    	   	    if (option == null)
    	   	    	continue;
    	   	    if (option.isBoolean) {
    	   	    	option.value = Boolean.toString(true);
    	   	    } else {
    	   	    	option.value = options[i][1];
    	   	    }
    		}
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
    		PropertyDescriptor[] pd = 
    			Introspector.getBeanInfo(bean.getClass()).getPropertyDescriptors();
    		for(int i=0;i<pd.length;i++) {
    			Method wm = pd[i].getWriteMethod();
    			if (wm == null)
    				continue;
    			String name = pd[i].getName();
    			//System.err.println("Going to set Property: "+name);
    			Option option = Option.get(name);
    			if (option == null)
    				continue;
    			Class propertyType = pd[i].getPropertyType();
    			Object value = null;
    			if (propertyType.isAssignableFrom(String.class)) {
    				value = option.value;
    			} else if (propertyType.isAssignableFrom(Integer.TYPE)) {
    				value = new Integer(option.value);
    			} else if (propertyType.isAssignableFrom(Boolean.TYPE)) {
    				value = Boolean.valueOf(option.isSet());
    			} else {
    				continue;
    			}
    			try {
    				wm.invoke(bean,new Object[]{ value});
        			//System.err.println("Done with Property: "+name+" new value is: "+value);
    			}catch (InvocationTargetException e) {
    				throw e.getTargetException();
    			}
    		}
    	}
    	
    	// register default options
    	static {
    		register(new Option("NoTagOptions","Do not add -tag-options to the option list of the formating doclet."+LI
    				+"Use this option, if your formating doclet doesn't understand the \"-tag <tagname>:X\" option."));
    		register(new Option("Help","Show this help message."));
    	}
    }
    
   
    
    /**
     * Filter the command line options seen by the formating Doclet.
     * 
     * Remove the options, that are processed by the filter doclet and 
     * add options to suppress warnings about unknown tags.
     * 
     * @param options the options as provided by the javadoc core
     * @return the filtered options
    	 * @see Doclet#validOptions(java.lang.String[][], com.sun.javadoc.DocErrorReporter)
    	 * @see RootDoc#options()
     */
    protected static String[][] filterOptions(String [][] options) {
        // filter our own options
        List filteredOptions = new ArrayList();
        for(int i=0; i<options.length; i++) {
        	if (Option.optionLength(options[i][0]) == 0)
        		filteredOptions.add(options[i]);
        }
        if((! Option.get("NoTagOptions").isSet()) && optionLengthHelper("-tag",FilterDocletBase.class.getName()) == 2) {
        	// the -tag option of the standard doclet seems to be supported
        	Iterator iterator = Option.getTags().iterator();
        	while(iterator.hasNext()) {
        		filteredOptions.add(new String[] {"-tag", iterator.next()+":X"});
        	}
        }
        return (String[][]) filteredOptions.toArray(new String[filteredOptions.size()][]);
    }

    /**
     * Helper method to ease the implementation of the doclet <code>validOptions</code> method.
     * 
     * This method provides all you need in order to implement 
     * {@link Doclet#validOptions(java.lang.String[][], com.sun.javadoc.DocErrorReporter)}.
     * 
     * @param options the options
     * @param reporter the errorReporter
     * @param showHelp if <code>true</code>, the method will show an online help message
     * and return <code>false</code>.
     * @param className the name of the filter doclet
     * @return <code>true</code>, if all options are valid. Otherwise show a help message 
     * and return <code>false</code>.
     * @throws java.io.IOException
     * @see Doclet#validOptions(java.lang.String[][], com.sun.javadoc.DocErrorReporter)
     */
    protected static boolean validOptionsHelper(String[][] options,
            DocErrorReporter reporter, boolean showHelp, String className) throws java.io.IOException {
    	Option.initOptions(options);
    	Option helpOption = Option.get("Help");
    	if (helpOption != null && helpOption.isSet()) {
    		showHelp = true;
    	}
        if (!((Boolean) delegateDocletInvoke("validOptions", new Object[] {filterOptions(options),reporter})).booleanValue()) {
        	showHelp = true;
        }
        if (showHelp) {
        	reporter.printNotice(Option.LF+
        			FilterDocletBase.class.getName()+ " options:"+
        			Option.LF+Option.getDescriptions());
        	return false;
        }
        return true;
    }

    /**
     * Helper method used to implement the doclet <code>optionLength</code>
     * method.
     * 
     * @param option the name of the option
     * @param className the name of the filter doclet
     * @return the length of the option
     * @see Doclet#optionLength(java.lang.String)
     */
    protected static int optionLengthHelper(String option, String className) {
    	int length = Option.optionLength(option);
    	if (length > 0)
    		return length;
        length = ((Integer) delegateDocletInvoke("optionLength",new Object[] {option})).intValue();
        if ("-help".equals(option)) {
            System.out.println(Option.LF+"Provided by "+className+" doclet:"
                    +Option.LF+Option.getDescriptions());
        }
        return length;
    }

    /**
     * Helper method used to implement the doclet <code>start</code> method.
     * 
     * See {@link PublishedApiDoclet#start(RootDoc)} for an example on how to use this
     * method.
     * 
     * @param root the RootDoc object as provided by the javadoc core.
     * @param fd a newly created filter doclet object
     * @return see {@link Doclet#start(com.sun.javadoc.RootDoc)}.
     * @throws java.io.IOException
     */
    protected static boolean startHelper(RootDoc root, FilterDocletBase fd) throws java.io.IOException {
        // process our options
        Option.initOptions(root.options());
        try {
			Option.initJavaBeanProperties(fd);
		} catch (Throwable e) {
			e.printStackTrace();
			root.printError(e.toString());
			return false;
		}
        fd.setErrorReporter(root);
        return ((Boolean) delegateDocletInvoke("start", 
        		new Object[]{ (RootDoc) HalfDynamicProxy.getHDPProxy(root, 
        				RootDoc.class, 
        				HalfDynamicProxy.stateFactory(fd,fd)) })).booleanValue();
    }
	

    /**
     * This class is the base of all the 
     * HalfDynamicProxy classes for the javadoc *Doc interfaces.
     * 
     * All <code>com.sun.javadoc.*Doc</code> interfaces extend the 
     * {@link Comparable} interface. Additionally instances of these interfaces
     * must be comparable by reference. Therefore this class contains a 
     * matching {@link #compareTo(Object)} implementation.
     * 
     * @author kruis
     *
     */
    protected static class ComparableHandler extends HalfDynamicProxy  {
		
		/* (non-Javadoc)
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		public int compareTo(Object o) {
			return ((Comparable)target).compareTo(unwrap(o));
		}

		/**
		 * print a debug message.
		 * 
		 * @param message
		 */
		protected void debug(String message) {
			FilterDocletBase pad = (FilterDocletBase) getHDPStateUserObject();
			pad.errorReporter.printNotice(message);
		}
		
	}
	
	
	
	
}
