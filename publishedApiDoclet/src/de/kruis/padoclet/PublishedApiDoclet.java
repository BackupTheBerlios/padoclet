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
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ConstructorDoc;
import com.sun.javadoc.Doc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.Doclet;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.Tag;
import com.sun.javadoc.Type;
import com.sun.tools.javadoc.Main;

import de.kruis.padoclet.HalfDynamicProxy.MessageReciver;

/**
 * This class is a java 1.4 doclet. 
 * 
 * 
 * 
 * @author kruis
 *
 */
public class PublishedApiDoclet implements MessageReciver {
    
    /**
     * The name of the systrem property, that contains the name of the
     * delegate doclet. If this system property is unset, the default 
     * doclet (<code>com.sun.tools.doclets.standard.Standard</code>) is used.
     */
    public static final String PAD_DELEGATE_DOCLET_SYSTEM_PROPERTY = "PublishedApiDoclet.delegate";
    
    
    /**
     * This class is used to perform a lazy instantiation of the
     * delegate doclet class.
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
     * holds the name of the include tag
     * 
     */
    private DocErrorReporter errorReporter;
    private String excludeTag;
    private Pattern excludeFilter;
    private String includeTag;
    private Pattern includeFilter;
    private String forceIncludeTag;
    private Pattern forceIncludeFilter;
    private String excludeChildsTag;
    private Pattern excludeChildsFilter;
    private boolean defaultIsExclude;
    private int defaultPriority;
    private boolean disableJavadocFilter;
    private boolean ignoreJavadocIsIncluded;
   
    private PublishedApiDoclet() {
    }
   
    /**
	 * @return Returns the defaultIsExclude.
	 */
	public final boolean isDefaultIsExclude() {
		return defaultIsExclude;
	}

	/**
	 * @param defaultIsExclude The defaultIsExclude to set.
	 */
	public final void setDefaultIsExclude(boolean defaultIsExclude) {
		this.defaultIsExclude = defaultIsExclude;
	}

	/**
	 * @return Returns the defaultPriority.
	 */
	public final int getDefaultPriority() {
		return defaultPriority;
	}

	/**
	 * @param defaultPriority The defaultPriority to set.
	 */
	public final void setDefaultPriority(int defaultPriority) {
		this.defaultPriority = defaultPriority;
	}

	/**
	 * @return Returns the disableJavadocFilter.
	 */
	public final boolean isDisableJavadocFilter() {
		return disableJavadocFilter;
	}

	/**
	 * @param disableJavadocFilter The disableJavadocFilter to set.
	 */
	public final void setDisableJavadocFilter(boolean disableJavadocFilter) {
		this.disableJavadocFilter = disableJavadocFilter;
	}

	/**
	 * @return Returns the excludeChildsFilter.
	 */
	public final String getExcludeChildsFilter() {
		return excludeChildsFilter.pattern();
	}
	public final Pattern getExcludeChildsFilterPat() {
		return excludeChildsFilter;
	}
	
	
	/**
	 * @param excludeChildsFilter The excludeChildsFilter to set.
	 */
	public final void setExcludeChildsFilter(String excludeChildsFilter) {
		this.excludeChildsFilter = Pattern.compile(excludeChildsFilter);
	}

	/**
	 * @return Returns the excludeChildsTag.
	 */
	public final String getExcludeChildsTag() {
		return excludeChildsTag;
	}

	/**
	 * @param excludeChildsTag The excludeChildsTag to set.
	 */
	public final void setExcludeChildsTag(String excludeChildsTag) {
		this.excludeChildsTag = excludeChildsTag;
	}

	/**
	 * @return Returns the excludeFilter.
	 */
	public final String getExcludeFilter() {
		return excludeFilter.pattern();
	}
	public final Pattern getExcludeFilterPat() {
		return excludeFilter;
	}
	
	/**
	 * @param excludeFilter The excludeFilter to set.
	 */
	public final void setExcludeFilter(String excludeFilter) {
		this.excludeFilter = Pattern.compile(excludeFilter);
	}

	/**
	 * @return Returns the forceIncludeFilter.
	 */
	public final String getForceIncludeFilter() {
		return forceIncludeFilter.pattern();
	}
	public final Pattern getForceIncludeFilterPat() {
		return forceIncludeFilter;
	}

	/**
	 * @param forceIncludeFilter The forceIncludeFilter to set.
	 */
	public final void setForceIncludeFilter(String forceIncludeFilter) {
		this.forceIncludeFilter = Pattern.compile(forceIncludeFilter);
	}

	/**
	 * @return Returns the forceIncludeTag.
	 */
	public final String getForceIncludeTag() {
		return forceIncludeTag;
	}

	/**
	 * @param forceIncludeTag The forceIncludeTag to set.
	 */
	public final void setForceIncludeTag(String forceIncludeTag) {
		this.forceIncludeTag = forceIncludeTag;
	}

	/**
	 * @return Returns the ignoreJavadocIsIncluded.
	 */
	public final boolean isIgnoreJavadocIsIncluded() {
		return ignoreJavadocIsIncluded;
	}

	/**
	 * @param ignoreJavadocIsIncluded The ignoreJavadocIsIncluded to set.
	 */
	public final void setIgnoreJavadocIsIncluded(boolean ignoreJavadocIsIncluded) {
		this.ignoreJavadocIsIncluded = ignoreJavadocIsIncluded;
	}

	/**
	 * @return Returns the includeFilter.
	 */
	public final String getIncludeFilter() {
		return includeFilter.pattern();
	}
	public final Pattern getIncludeFilterPat() {
		return includeFilter;
	}

	/**
	 * @param includeFilter The includeFilter to set.
	 */
	public final void setIncludeFilter(String includeFilter) {
		this.includeFilter = Pattern.compile(includeFilter);
	}

	/**
     * @return Returns the excludeTag.
     */
    public final String getExcludeTag() {
        return excludeTag;
    }
    /**
     * @param excludeTag The excludeTag to set.
     */
    public final void setExcludeTag(String excludeTag) {
        this.excludeTag = excludeTag;
    }
    /**
     * @return Returns the includeTag.
     */
    public final String getIncludeTag() {
        return includeTag;
    }
    /**
     * @param includeTag The includeTag to set.
     */
    public final void setIncludeTag(String includeTag) {
        this.includeTag = includeTag;
    }
    /**
     * @return Returns the errorReporter.
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
	 * @see de.kruis.padoclet.HalfDynamicProxy.MessageReciver#recive(java.lang.String)
	 */
	public void recive(String theMessage, int priority) {
		if(priority == MessageReciver.PRIORITY_DEBUG) {
			errorReporter.printNotice(theMessage);		
		} else if (priority == MessageReciver.PRIORITY_WARN) {
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
    
    
    private static class Option {
    	public final static String namePrefix = "-pad";
    	/**
    	 * Holds one line.separator character. 
    	 */
    	public final static String LF = System.getProperty("line.separator");
    	/**
    	 * Holds a line.separator and some spaces. Used to format option descriptions.
    	 */
    	public final static String LI = LF+"            ";
    	public final String name;
    	public String value;
    	private final String defaultValue;
    	public final boolean isBoolean;
    	public final boolean isTag;
    	public final String description;
    	
    	public Option(String name, String defaultValue, boolean isTag, String description) {
    		this.isBoolean = false;
    		this.name = name;
    		this.value = this.defaultValue = defaultValue;
    		this.isTag = isTag;
    		this.description = description;
    	}
    	
    	public Option(String name, String description) {
    		this.isBoolean = true;
    		this.name = name;
    		this.value = this.defaultValue = Boolean.FALSE.toString();
    		this.isTag = false;
    		this.description = description;
    	}
    	
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
    	
    	private static Map options = new TreeMap();
    	public static void register(Option option) {
    		options.put(Introspector.decapitalize(option.name),option);
    	}
    	public static Option get(String name) {
    		return (Option) options.get(Introspector.decapitalize(name));
    	}
    	public static String getDescriptions() {
    		StringBuffer sb = new StringBuffer();
    		Iterator iterator = options.values().iterator();
    		while(iterator.hasNext()) {
    			sb.append(iterator.next()).append(LF);
    		}
    		return sb.toString();
    	}
    	public static Set getTags() {
    		Set set = new HashSet();
    		Iterator iterator = options.values().iterator();
    		while(iterator.hasNext()) {
    			Option o = (Option) iterator.next();
    			if(o.isTag) set.add(o.value);
    		}
    		return set;
    	}
    	private static Option getWithPrefix(String name) {
    		if (name == null || ! name.startsWith(namePrefix))
    			return null;
    		return get(name.substring(namePrefix.length()));
    	}
    	public static int optionLength(String name) {
    		Option option = getWithPrefix(name);
    		if (option == null)
    			return 0;
    		return option.isBoolean ? 1 : 2;
    	}
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
    	
    	static {
    		register(new Option("FilterDefault",".*",false,"Default regular expression for all filter options."+LI
    				+"PublishedApiDoclet tags are ignored, unless the regular expression matches the text"+LI
    				+"after the tag (that may be the empty string). The default regular expression \".*\" matches"+LI
    				+"everything including the empty string."+LI
    				+"  Every matched portion of the tag gets a priority number and the maximum of all these numbers"+LI
    				+"is taken as the priority for this tag and language element. If a matched portion of the text"+LI
    				+"contains a decimal number (e.g. \"foo123\") then the number is taken as the priority of the"+LI
    				+"match. Otherwise the match priority is 1."+LI
    				+"  The comparision of include- and exclude-priorities determinates, if the language element"+LI
    				+"will be documented."));
    		register(new Option("ExcludeTag","pad.exclude",true,"The name of the exclude tag."+LI
    				+"Use this option to redefine the tag. The empty string disables the tag."));
    		register(new Option("ExcludeFilter","<value of "+namePrefix+"FilterDefault>",false,"The regular expression used to filter excludeTags."));
    		register(new Option("IncludeTag","pad.include",true,"The name of the include tag."+LI
    				+"Use this option to redefine the tag. The empty string disables the tag."));
    		register(new Option("IncludeFilter","<value of "+namePrefix+"FilterDefault>",false,"The regular expression used to filter includeTags."));
    		register(new Option("ForceIncludeTag","pad.forceInclude",true,"The name of the forceInclude tag. This tag is used,"+LI
    				+"to include lenguage elements, that were excluded by the doclet itself (e.g. a private method)."+LI
    				+"This tag has no priority."+LI
    				+"  Use this option to redefine the tag. The empty string disables the tag."));
    		register(new Option("ForceIncludeFilter","<value of "+namePrefix+"FilterDefault>",false,"The regular expression used to filter forceIncludeTags."));
    		register(new Option("ExcludeChildsTag","pad.excludeChilds",true,"The name of the excludeChilds tag. This tag is similar to the"+LI
    				+"@pad.exclude tag, but it influences the exclude priority for childs of the taged element only."+LI
    				+"The effective child exclude priority is the maximum of the @pad.exclude and the"+LI
    				+"@pad.excludeChilds priorities."+LI
    				+"  Use this option to redefine the tag. The empty string disables the tag."));
    		register(new Option("ExcludeChildsFilter","<value of "+namePrefix+"FilterDefault>",false,"The regular expression used to filter excludeChildsTags."));
    		register(new Option("DefaultIsExclude","Exclude all items except explicitly included ones. This option is usefull,"+LI
    				+"if you want to include only a hand selected subset of your API."));
    		register(new Option("DefaultPriority","1",false,"The priority of the default behaviour."));
    		register(new Option("DisableJavadocFilter","Get unfiltered item collections from javadoc. You may need to"+LI
    				+"use this option, if you use the @pad.forceInclude tag."));
    		register(new Option("IgnoreJavadocIsIncluded","Do not call the javadoc isIncluded method."));
    		register(new Option("NoTagOptions","Do not add -tag-options to the option list of the formating doclet."+LI
    				+"Use this option, if your formating doclet doesn't understand the \"-tag <tagname>:X\" option."));
    		register(new Option("Help","Show this help message."));
    	}
    }
    
   
    
    private static String[][] filterOptions(String [][] options) {
        // filter our own options
        List filteredOptions = new ArrayList();
        for(int i=0; i<options.length; i++) {
        	if (Option.optionLength(options[i][0]) == 0)
        		filteredOptions.add(options[i]);
        }
        if((! Option.get("NoTagOptions").isSet()) && optionLength("-tag") == 2) {
        	// the -tag option of the standard doclet seems to be supported
        	Iterator iterator = Option.getTags().iterator();
        	while(iterator.hasNext()) {
        		filteredOptions.add(new String[] {"-tag", iterator.next()+":X"});
        	}
        }
        return (String[][]) filteredOptions.toArray(new String[filteredOptions.size()][]);
    }

    public static boolean validOptions(String[][] options,
            DocErrorReporter reporter) throws java.io.IOException {
    	Option.initOptions(options);
    	boolean showHelp = Option.get("Help").isSet();
    	
    	try{
    		Integer.parseInt(Option.get("DefaultPriority").value);
    	} catch (NumberFormatException e) {
			reporter.printError("Option "+Option.namePrefix+"DefaultPriority"+" requires an integer argument" );
			showHelp = true;
    	}
        if (!((Boolean) delegateDocletInvoke("validOptions", new Object[] {filterOptions(options),reporter})).booleanValue()) {
        	showHelp = true;
        }
        if (showHelp) {
        	reporter.printNotice(Option.LF+
        			PublishedApiDoclet.class.getName()+ " options:"+
        			Option.LF+Option.getDescriptions());
        	return false;
        }
        return true;
    }

    public static int optionLength(String option) {
    	int length = Option.optionLength(option);
    	if (length > 0)
    		return length;
        length = ((Integer) delegateDocletInvoke("optionLength",new Object[] {option})).intValue();
        if ("-help".equals(option)) {
            System.out.println(Option.LF+"Provided by "+PublishedApiDoclet.class.getName()+" doclet:"
                    +Option.LF+Option.getDescriptions());
        }
        return length;
    }

    public static boolean start(RootDoc root) throws java.io.IOException {
        // process our options
        Option.initOptions(root.options()); // first pass: we need FilterDefault
        Option.get("ExcludeFilter").value = 
        	Option.get("ExcludeChildsFilter").value =
        		Option.get("IncludeFilter").value =
        			Option.get("ForceIncludeFilter").value 
        			= Option.get("FilterDefault").value;
        // reinit, now we have valid filter values
        Option.initOptions(root.options());
        PublishedApiDoclet pad = new PublishedApiDoclet();
        try {
			Option.initJavaBeanProperties(pad);
		} catch (Throwable e) {
			e.printStackTrace();
			root.printError(e.toString());
			return false;
		}
        pad.setErrorReporter(root);
        return ((Boolean) delegateDocletInvoke("start", 
        		new Object[]{ (RootDoc) HalfDynamicProxy.getHDPProxy(root, 
        				RootDoc.class, 
        				HalfDynamicProxy.stateFactory(pad,pad)) })).booleanValue();
    }

    /**
     * A main method.
     * 
     * This method simply calls the doclet main method.
     * 
     * @param args
     */
    public static void main(String[] args) {
        String name = PublishedApiDoclet.class.getName();
        Main.execute(name, name, args);
    }

	static { 
		HalfDynamicProxy.setProxyClassTable(
				new Class[][] {
				new Class[] { RootDocHandler.class,RootDoc.class},
				new Class[] { ClassDocHandler.class, ClassDoc.class},
				new Class[] { PackageDocHandler.class, PackageDoc.class},
				new Class[] { DocHandler.class, Doc.class},
				new Class[] { ComparableHandler.class, Tag.class},
				new Class[] { ComparableHandler.class, Type.class},
		}
		);
	}
	

    private static class ComparableHandler extends HalfDynamicProxy  {
		
		/* (non-Javadoc)
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		public int compareTo(Object o) {
			return ((Comparable)target).compareTo(unwrap(o));
		}

		protected void debug(String message) {
			PublishedApiDoclet pad = (PublishedApiDoclet) getHDPStateUserObject();
			pad.errorReporter.printNotice(message);
		}
		
	}
	
	private static class DocHandler extends ComparableHandler {

		private boolean isIncluded;
		/** true, is the inclusion state is known and {@link #isIncluded} is valid */
		private boolean isIncludedValid = false;
		
		private boolean isCheckStarted = false; 
		private int inclusionPriority = 0;
		private int childInclusionPriority = 0;
		
		private boolean inclusionPriorityValid = false;
		
		/* (non-Javadoc)
		 * @see com.sun.javadoc.Doc#isIncluded()
		 */
		public boolean isIncluded() {
			if (isIncludedValid)
				return isIncluded;
			if (isCheckStarted)
				throw new IllegalStateException("unexpected recursion detected");
			try {
				this.isCheckStarted = true;
				PublishedApiDoclet pad = (PublishedApiDoclet) getHDPStateUserObject();
				Doc doc = (Doc)this.getInvocationTarget();
				
				this.isIncluded = pad.isIgnoreJavadocIsIncluded() 
					|| doc.isIncluded() 
					|| tagPriority(doc,pad.getForceIncludeTag(),pad.getForceIncludeFilterPat()) > 0;
				if (! this.isIncluded)
					return false;

				// check localy and upwards
				this.isIncluded = this.calcInclusionPriority(false) >= 0 ;
				
				// if the default is "exclude", check, if the
				// inclusion of just this element is required
				if (! this.isIncluded) {
					boolean inclusionRequired = false;
					if (doc instanceof PackageDoc) {
						// check all classes
						PackageDoc pd = (PackageDoc) dynamicProxyInstance();
						inclusionRequired = pd.allClasses().length > 0;
					} else if (doc instanceof ClassDoc) {
						// check all members
						ClassDoc cd = (ClassDoc) dynamicProxyInstance();
						inclusionRequired = 
							cd.constructors().length > 0 ||
							cd.methods().length > 0 ||
							cd.fields().length > 0;
					}
					if (inclusionRequired) {
						debug("detected required inclusion of: "+doc.toString());
						this.isIncluded = true;
					}
				}	        
				return this.isIncluded;
			} finally {
				this.isIncludedValid = true;
			}
		}

		private static final Pattern digits = Pattern.compile(".*?(\\d+)");
		private static int tagPriority(Doc doc, String tag, Pattern filter) {
			if (tag == null || tag.length() == 0)
				return 0;
			Tag[] tags = doc.tags(tag);
			int prio = 0; int p;
			for(int i=0;i<tags.length;i++) {
				String text = tags[i].text();
				if (text == null)
					text=""; // I'm paranoid
				Matcher matcher = filter.matcher(text);
				while(matcher.find()) {
					// the tag matches, now get its priority
					p = 1;
					// now try to get the priority from the matched 
					matcher = digits.matcher(matcher.group());
					if (matcher.lookingAt()) {
						// the tag has an encoded priority
						p = Integer.parseInt(matcher.group(1));
					}
					// we need the maximum value
					if (prio < p)
						prio = p;
				}
			}
			return prio;
		}
		
		/**
		 * Check, if this item shall be included.
		 * 
		 * This method checks this item and its parents, but not the childs.
		 * 
		 * @param iscallFromChild if <code>true</code> return the inclusion priority
		 * for a child item, otherwise return the inclusion priority for this item.
		 * @return an include (return value is positive) or exclude 
		 * (return value is negative) priority. 
		 */
		private int calcInclusionPriority(boolean iscallFromChild) {
			if (this.inclusionPriorityValid)
				return iscallFromChild ? this.childInclusionPriority : this.inclusionPriority;
			try {
				Doc doc = (Doc) dynamicProxyInstance();
				PublishedApiDoclet pad = (PublishedApiDoclet) getHDPStateUserObject();
				int includePriority = tagPriority(doc,pad.getIncludeTag(),pad.getIncludeFilterPat());
				int excludePriority = tagPriority(doc,pad.getExcludeTag(),pad.getExcludeFilterPat());
				int excludeChildsPriority = tagPriority(doc,pad.getExcludeChildsTag(),pad.getExcludeChildsFilterPat());
				if (excludePriority > excludeChildsPriority) {
					// the exclude childs priority must be at least as high 
					// as the exclude priority, because we do not want childs
					// to be included, and the local node included
					excludeChildsPriority = excludePriority;
				}
				
				int parentPriority = (pad.isDefaultIsExclude()?-1:1)*
						pad.getDefaultPriority();
				// ---- ask the parent, if possible ----
					
				Doc doc2 = null; // holds the parent
				// get the parent
				if (doc instanceof ProgramElementDoc) {
					ProgramElementDoc member = (ProgramElementDoc) doc;
					doc2 = member.containingClass();
					if (doc2 == null) {
						doc2 = member.containingPackage();
					}
				}
				// is parent valid?
				if (doc2 instanceof Proxy && 
						Proxy.getInvocationHandler(doc2) instanceof DocHandler) {
					// doc2 is the parent
					parentPriority = ((DocHandler)Proxy.getInvocationHandler(doc2)).calcInclusionPriority(true);
				}
				
				// now do the calculation for the local inclusion prio
				this.inclusionPriority = includePriority;
				if (excludePriority > includePriority) {
					this.inclusionPriority = -excludePriority;
				}
				if (Math.abs(parentPriority) > Math.abs(this.inclusionPriority)) {
					this.inclusionPriority = parentPriority;
				}
				// now do the calculation for the child inclusion prio
				this.childInclusionPriority = includePriority;
				if (excludeChildsPriority > includePriority) {
					this.childInclusionPriority = -excludeChildsPriority;
				}
				if (Math.abs(parentPriority) > Math.abs(this.childInclusionPriority)) {
					this.childInclusionPriority = parentPriority;
				}
				
				return iscallFromChild ? 
						this.childInclusionPriority : this.inclusionPriority;
			} finally {
				this.inclusionPriorityValid = true;
			}
		}
				
		protected Doc[] filterDocArray(Doc[] array, Class expect, boolean doFilter) {
			if (!doFilter) {
				return (Doc[]) getHDPProxy(array, expect);
			}
			Class componentType = expect.getComponentType();
			List list = new ArrayList(array.length);
			for (int i = 0; i < array.length; i++) {
				Doc entry = (Doc) getHDPProxy(array[i], componentType);
				if (entry != null && ! entry.isIncluded()){
					//debug("Array Excluding: "+entry.getClass().getName()+ " " + entry);
					continue;
				}
				list.add(entry);
			}
			return (Doc[]) list.toArray((Object[]) Array.newInstance(componentType,list.size()));
		}

		protected boolean isFilter(boolean filter) {
			if (! filter)
				return false;
			PublishedApiDoclet ds = (PublishedApiDoclet) getHDPStateUserObject();
			return ! ds.isDisableJavadocFilter();
		}

	}
	
	private static class RootDocHandler extends DocHandler {
		/* (non-Javadoc)
		 * @see com.sun.javadoc.RootDoc#classes()
		 */
		public ClassDoc[] classes() {
			return (ClassDoc[]) filterDocArray(((RootDoc)target).classes(),ClassDoc[].class,true);
		}

		/* (non-Javadoc)
		 * @see com.sun.javadoc.RootDoc#options()
		 */
		public String[][] options() {
			return filterOptions(((RootDoc)target).options());
		}
		
		/* (non-Javadoc)
		 * @see com.sun.javadoc.RootDoc#specifiedClasses()
		 */
		public ClassDoc[] specifiedClasses() {
			PublishedApiDoclet ds = (PublishedApiDoclet) getHDPStateUserObject();
			return (ClassDoc[]) filterDocArray(((RootDoc)target).specifiedClasses() ,ClassDoc[].class, 
					! ds.isDisableJavadocFilter());
		}

		/* (non-Javadoc)
		 * @see com.sun.javadoc.RootDoc#specifiedClasses()
		 */
		public PackageDoc[] specifiedPackages() {
			PublishedApiDoclet ds = (PublishedApiDoclet) getHDPStateUserObject();
			return (PackageDoc[]) filterDocArray(((RootDoc)target).specifiedPackages() ,PackageDoc[].class,
					! ds.isDisableJavadocFilter());
		}

	}

	private static class ClassDocHandler extends DocHandler {
		/* (non-Javadoc)
		 * @see com.sun.javadoc.ClassDoc#constructors()
		 */
		public ConstructorDoc[] constructors() {
			return constructors(true);
		}
		/* (non-Javadoc)
		 * @see com.sun.javadoc.ClassDoc#constructors(boolean)
		 */
		public ConstructorDoc[] constructors(boolean filter) {
			return (ConstructorDoc[]) filterDocArray(((ClassDoc)target).constructors(isFilter(filter)) ,ConstructorDoc[] .class, filter);
		}
		/* (non-Javadoc)
		 * @see com.sun.javadoc.ClassDoc#fields()
		 */
		public FieldDoc[] fields() {
			return fields(true);
		}
		/* (non-Javadoc)
		 * @see com.sun.javadoc.ClassDoc#fields(boolean)
		 */
		public FieldDoc[] fields(boolean filter) {
			return (FieldDoc[]) filterDocArray(((ClassDoc)target).fields(isFilter(filter)) ,FieldDoc[] .class, filter);
		}
		/* (non-Javadoc)
		 * @see com.sun.javadoc.ClassDoc#innerClasses()
		 */
		public ClassDoc[] innerClasses() {
			return innerClasses(true);
		}
		/* (non-Javadoc)
		 * @see com.sun.javadoc.ClassDoc#innerClasses(boolean)
		 */
		public ClassDoc[] innerClasses(boolean filter) {
			return (ClassDoc[]) filterDocArray(((ClassDoc)target).innerClasses(isFilter(filter)) ,ClassDoc[] .class, filter);
		}
		/* (non-Javadoc)
		 * @see com.sun.javadoc.ClassDoc#methods()
		 */
		public MethodDoc[] methods() {
			return methods(true);
		}
		/* (non-Javadoc)
		 * @see com.sun.javadoc.ClassDoc#methods(boolean)
		 */
		public MethodDoc[] methods(boolean filter) {
			return (MethodDoc[]) filterDocArray(((ClassDoc)target).methods(isFilter(filter)) ,MethodDoc[] .class, filter);
		}
		/* (non-Javadoc)
		 * @see com.sun.javadoc.ClassDoc#subclassOf(com.sun.javadoc.ClassDoc)
		 */
		public boolean subclassOf(ClassDoc arg0) {
			return ((ClassDoc)target).subclassOf((ClassDoc) unwrap(arg0));
		}
	}	
	
	private static class PackageDocHandler extends DocHandler {
		/* (non-Javadoc)
		 * @see com.sun.javadoc.PackageDoc#allClasses()
		 */
		public ClassDoc[] allClasses() {
			return allClasses(true);
		}
		/* (non-Javadoc)
		 * @see com.sun.javadoc.PackageDoc#allClasses(boolean)
		 */
		public ClassDoc[] allClasses(boolean filter) {
			return (ClassDoc[]) filterDocArray(((PackageDoc)target).allClasses(isFilter(filter)) ,ClassDoc[] .class, filter);
		}
		/* (non-Javadoc)
		 * @see com.sun.javadoc.PackageDoc#errors()
		 */
		public ClassDoc[] errors() {
			return (ClassDoc[]) filterDocArray(((PackageDoc)target).errors() ,ClassDoc[] .class, true);
		}
		/* (non-Javadoc)
		 * @see com.sun.javadoc.PackageDoc#exceptions()
		 */
		public ClassDoc[] exceptions() {
			return (ClassDoc[]) filterDocArray(((PackageDoc)target).exceptions() ,ClassDoc[] .class, true);
		}
		/* (non-Javadoc)
		 * @see com.sun.javadoc.PackageDoc#interfaces()
		 */
		public ClassDoc[] interfaces() {
			return (ClassDoc[]) filterDocArray(((PackageDoc)target).interfaces() ,ClassDoc[] .class, true);
		}
		/* (non-Javadoc)
		 * @see com.sun.javadoc.PackageDoc#ordinaryClasses()
		 */
		public ClassDoc[] ordinaryClasses() {
			return (ClassDoc[]) filterDocArray(((PackageDoc)target).ordinaryClasses() ,ClassDoc[] .class, true);
		}
	}
	
	
	
}
