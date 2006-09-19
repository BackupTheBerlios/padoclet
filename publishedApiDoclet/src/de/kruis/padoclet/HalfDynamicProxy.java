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


/**
 * 
 */
package de.kruis.padoclet;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.WeakHashMap;


/**
 * @author kruis
 * @pad.include
 */
public class HalfDynamicProxy implements InvocationHandlerWithTarget {
	/**
	 * @author kruis
	 * @pad.exclude
	 */
	public static interface MessageReciver {
		static int PRIORITY_ERROR = 0;
		static int PRIORITY_WARN = 1;
		static int PRIORITY_DEBUG = 2;
		void recive(String theMessage, int priority);
	}

	private static class HDPState {
		private static HalfDynamicProxy.MessageReciver defaultReciver = new MessageReciver() {
			/* (non-Javadoc)
			 * @see de.kruis.padoclet.PublishedApiDoclet.HalfDynamicProxy.MessageReciver#recive(java.lang.String)
			 */
			public void recive(String theMessage, int priority) {
				System.err.println(theMessage);
			}
		};
		private Object userState;
		private Map proxyCache;
		private MessageReciver reciver;
		
		public HDPState(Object userState, MessageReciver reciver) {
			this.userState = userState;
			this.reciver = reciver != null ? reciver : defaultReciver;
			this.proxyCache = new WeakHashMap();
		}

		public void debug(String message) {
			this.reciver.recive(message,MessageReciver.PRIORITY_DEBUG);
		}
		
		public void error(String message) {
			this.reciver.recive(message,MessageReciver.PRIORITY_ERROR);
		}
	}
	
		
	private static Class[][] proxyClassTable;
	
    protected Object target;
    private HalfDynamicProxy.HDPState state;
    
    public static HDPState stateFactory(Object userState, MessageReciver reciver) {
    	return new HDPState(userState, reciver);
    }
    

	/**
	 * @return Returns the proxyClassTable.
	 */
	public static Class[][] getProxyClassTable() {
		return proxyClassTable;
	}

	/**
	 * @param proxyClassTable The proxyClassTable to set.
	 */
	public static void setProxyClassTable(Class[][] proxyClassTable) {
		HalfDynamicProxy.proxyClassTable = proxyClassTable;
	}

	/**
	 * @param target
	 */
	public final void setupInvocationHandler(Object target, Object state) {
		this.target = target;
		this.state = (HDPState) state;
	}

	/**
	 * @return Returns the target.
	 */
	public final Object getInvocationTarget() {
		return target;
	}

	public Proxy dynamicProxyInstance() {
		return (Proxy) state.proxyCache.get(this.target);
	}

	protected Object getHDPStateUserObject() {
		return state.userState;
	}
	
	protected Object getHDPProxy(Object obj, Class expect) {
		return getHDPProxy(obj,expect,state);
	}
	
	public static Object getHDPProxy(Object obj, Class expect, HDPState state) {
		if (obj == null) {
			return null;
		}
		
		// array handling
		if (obj instanceof Object[]) {
			Object[] arr = (Object []) obj;
			Class componentType = expect.getComponentType();
			boolean isProxy = true;
			for(int i=0; i<arr.length; i++) {
				if (arr[i] != getHDPProxy(arr[i], componentType, state))
					isProxy = false;
			}
			if(isProxy) 
				return obj;
			Object[] arr2 = (Object[]) Array.newInstance(componentType,arr.length);
			for(int i=0; i<arr.length; i++) {
				arr2[i] = getHDPProxy(arr[i], componentType, state);
			}
			return arr2;
		}
		
		Class cls = obj.getClass();
		if (Proxy.isProxyClass(cls)) {
			// is already a proxy
			return obj;
		}
		if (cls.isPrimitive() || obj instanceof String) {
			return obj;
		}
		// try to find an existing decorator
		Map decoratorMap = state.proxyCache;
		synchronized (decoratorMap) {

			Object decorator = decoratorMap.get(obj);
			if (decorator != null) {
				return decorator;
			}
			// find the right class
			Class invocationHandlerClass = null;
			for (int i = 0; i < proxyClassTable.length; i++) {
				// assert, that the row is valid
				if (proxyClassTable[i] == null 
						|| proxyClassTable[i].length < 1 
						|| ! InvocationHandlerWithTarget.class.isAssignableFrom(proxyClassTable[i][0]) ) {
					throw new ClassCastException("invalid proxy class at index "+i);
				}
				// loop over all required interfaces
				int j;
				for(j=1;j<proxyClassTable[i].length;j++) {
					if (! proxyClassTable[i][j].isInstance(obj))
						break;
				}
				if (j>=proxyClassTable[i].length) {
					// we got it
					invocationHandlerClass = proxyClassTable[i][0];
					break;
				}
			}
			
			if (invocationHandlerClass == null) {
				//state.debug("no invocation Handler for object of class: "+cls.getName());
				return obj;
			}
			InvocationHandlerWithTarget invokationHandler = null;
			try {
				invokationHandler =
					(InvocationHandlerWithTarget) invocationHandlerClass.newInstance();
				invokationHandler.setupInvocationHandler(obj, state);
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}

			Object proxy =
				Proxy.newProxyInstance(
					cls.getClassLoader(),
					cls.getInterfaces(),
					invokationHandler);
			//state.debug("created proxy: "+invocationHandlerClass.getName()+" "+obj.toString());
			decoratorMap.put(obj, proxy);
			return proxy;
		}
	}

	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		Method m = null;
		Object methodTarget = null;
		boolean isProxyRequired = false;
    	try {
			m = this.getClass().getMethod(method.getName(), method.getParameterTypes());
			methodTarget = this;
			//state.debug("found replacement method "+m);
    	} catch (NoSuchMethodException e) {
    		m = method;
    		methodTarget = this.target;
    		isProxyRequired = true;
			//state.debug("no replacement method "+m);
    	}
		Object result;
		try {
			result = m.invoke(methodTarget, args);
		} catch (InvocationTargetException e) {
			throw e.getTargetException();
		}
    	if (isProxyRequired) {
    		result = getHDPProxy(result, m.getReturnType());
    	}
    	return result;
    }

    protected Object unwrap(Object proxy) {
        if (proxy instanceof Proxy)
            return ((InvocationHandlerWithTarget) Proxy.getInvocationHandler(proxy)).getInvocationTarget();
        return proxy;
    }
    
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		return target.equals(unwrap(obj));
	}

	public int hashCode() {
		return target.hashCode();
	}

	public String toString() {
		return target.toString();
	}

}