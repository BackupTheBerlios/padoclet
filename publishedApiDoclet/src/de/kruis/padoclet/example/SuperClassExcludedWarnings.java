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

package de.kruis.padoclet.example;

/**
 * This class is used to demonstrate some warnings.
 * 
 * @author kruis
 * @pad.exclude sample
 */
public class SuperClassExcludedWarnings {
	
	/**
	 * This method is to be overridden in the subclass.
	 */
	public void method() {
		
	}
	
	/**
	 * This interface is excluded from the documentation.
	 * 
	 * @author kruis
	 *
	 */
	public static interface ExcludedInterface {
		/**
		 * Foo.
		 */
		void foo();
	}
	/**
	 * This interface is also excluded from the documentation.
	 * 
	 * @author kruis
	 *
	 */
	public static interface ExcludedInterface2 {
		/**
		 * Bar.
		 */
		void bar();
	}
	
	/**
	 * This interface is included
	 * 
	 * @author kruis
	 * @pad.include sample
	 */
	public static interface IncludedInterface extends ExcludedInterface {
		/**
		 * Blub.
		 */
		void blub();
	}
	
	/**
	 * This class triggers warnings.
	 * 
	 * The base class is not inclued. The containing class is not included. One interface is not included.
	 * 
	 * @author kruis
	 * @pad.include sample
	 */
	public static class IncludedClass extends SuperClassExcludedWarnings implements IncludedInterface, ExcludedInterface2 {

		/* (non-Javadoc)
		 * @see de.kruis.padoclet.example.SuperClassExcludedWarnings.ExcludedInterface#foo()
		 */
		public void foo() {
		}

		/* (non-Javadoc)
		 * @see de.kruis.padoclet.example.SuperClassExcludedWarnings.ExcludedInterface2#bar()
		 */
		public void bar() {
		}

		/* (non-Javadoc)
		 * @see de.kruis.padoclet.example.SuperClassExcludedWarnings.IncludedInterface#blub()
		 */
		public void blub() {
		}
		
		/**
		 * This method overrides the method of the superclass.
		 * 
		 * @see SuperClassExcludedWarnings#method()
		 */
		public void method() {
			super.method();
			blub();
		}

		/**
		 * This method sould not generate a warning.
		 * 
		 * It overrides a method, that would not be documented by default.
		 *  
		 * @see java.lang.Object#toString()
		 */
		public String toString() {
			return "Hello world";
		}
		
		
	}
	
}
