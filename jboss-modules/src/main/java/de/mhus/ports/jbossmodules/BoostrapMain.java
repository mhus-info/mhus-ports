package de.mhus.ports.jbossmodules;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.jboss.modules.SecurityActions;

public class BoostrapMain {

	// http://grepcode.com/file/repo1.maven.org/maven2/org.wildfly/wildfly-server/8.0.0.Beta1/org/jboss/as/server/Main.java?av=f
	
	/*
				   ServerEnvironment serverEnvironment = determineEnvironment(args, WildFlySecurityManager.getSystemPropertiesPrivileged(), WildFlySecurityManager.getSystemEnvironmentPrivileged(), ServerEnvironment.LaunchType.STANDALONE);
92                 final Bootstrap bootstrap = Bootstrap.Factory.newInstance();
93                 final Bootstrap.Configuration configuration = new Bootstrap.Configuration(serverEnvironment);
94                 configuration.setModuleLoader(Module.getBootModuleLoader());
95                 bootstrap.bootstrap(configuration, Collections.<ServiceActivator>emptyList()).get();
	 */
	public static void main(String[] args) throws ClassNotFoundException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
		System.out.println("Yo!");

		Class<?> mainClass = WildflyController.getInstance().getClassLoader().loadClass("org.jboss.as.server.Main");
		Class<?> bootstrapFactoryClass = WildflyController.getInstance().getClassLoader().loadClass("org.jboss.as.server.Bootstrap$Factory");
		Class<?> bootstrapConfigurationClass = WildflyController.getInstance().getClassLoader().loadClass("org.jboss.as.server.Bootstrap$Configuration");
				
//		Object determineEnvironment = mainClass.getMethod("determineEnvironment").invoke(null, new String[0], 
//				new Properties((Properties) systemProperties), systemEnvironment);
//		
//		Object config = bootstrapConfigurationClass.getConstructor(determineEnvironment.getClass()).newInstance(determineEnvironment);
//		
//		Object bootstrap = bootstrapFactoryClass.getMethod("newInstance").invoke(null);
//		List<Object> emptyList = Collections.emptyList();
//		Object res = bootstrap.getClass().getMethod("bootstrap", bootstrapConfigurationClass, emptyList.getClass()).invoke(null, config, emptyList);
//		
//		res.getClass().getMethod("get").invoke(res);
		
		System.out.println("YOOOO Clazz found!");
	}

}
