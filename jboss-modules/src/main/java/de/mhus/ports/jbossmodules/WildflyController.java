package de.mhus.ports.jbossmodules;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.security.AccessControlContext;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.logging.LogManager;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.modules.DefaultBootModuleLoaderHolder;
import org.jboss.modules.LocalLoader;
import org.jboss.modules.Main;
import org.jboss.modules.ModularContentHandlerFactory;
import org.jboss.modules.ModularURLStreamHandlerFactory;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleNotFoundException;
import org.jboss.modules.ModulesPolicy;
import org.jboss.modules.PreMain;
import org.jboss.modules.PropertyReadAction;
import org.jboss.modules.Resource;
import org.jboss.modules.log.JDKModuleLogger;

import static java.security.AccessController.doPrivileged;
import static org.jboss.modules.SecurityActions.setContextClassLoader;

public class WildflyController {

	private static WildflyController inst = null;
	private File home;
	private String instance;
	private File jbossBaseDir;
	private File jbossLogDir;
	private File jbossConfigDir;
	private File jbossModulesPath;
	private Module module;

	public synchronized static WildflyController getInstance() {
		if (inst == null) inst = new WildflyController();
		return inst;
	}
	
	private WildflyController() {
	}

	public void setHome(File home) throws IOException {
		if (this.home != null) throw new IOException("Home already set");
		this.home = home;
		File modulesJar = new File(home,"jboss-modules.jar");
		if (!modulesJar.exists() || !modulesJar.isFile()) throw new IOException("JBoss Wildfly not found in " + home.getAbsolutePath());
		instance = "standalone";
		jbossBaseDir = new File(home, instance);
		jbossLogDir = new File(jbossBaseDir,"log");
		jbossConfigDir = new File(jbossBaseDir,"configuration");
		jbossModulesPath = new File(home, "modules");
	}
	
	public boolean start() {
		if (module != null) return true;
		try {
			System.setProperty("org.jboss.boot.log.file", new File(jbossLogDir, "server.log").getAbsolutePath());
			System.setProperty("logging.configuration", "file:" + jbossConfigDir.getAbsolutePath() + "/logging.properties");
			System.setProperty("jboss.home.dir", home.getAbsolutePath());
			System.setProperty("jboss.server.base.dir", jbossBaseDir.getAbsolutePath());
			
			String modulePath = jbossModulesPath.getAbsolutePath();
			System.setProperty("module.path", modulePath);
			String moduleName = "org.jboss.as.standalone";
			
			final ModuleLoader loader = DefaultBootModuleLoaderHolder.INSTANCE;
			
	        Module.initBootModuleLoader(loader);
	
	        try {
	            module = loader.loadModule(moduleName);
	        } catch (ModuleNotFoundException e) {
	            e.printStackTrace(System.err);
	            return false;
	        }

	        final String ourJavaVersion = doPrivileged(new PropertyReadAction("java.specification.version", "1.6"));
	        final String requireJavaVersion = module.getProperty("jboss.require-java-version", ourJavaVersion);
	        final Pattern versionPattern = Pattern.compile("(?:1\\.)?(\\d+)");
	        final Matcher requireMatcher = versionPattern.matcher(requireJavaVersion);
	        final Matcher ourMatcher = versionPattern.matcher(ourJavaVersion);
	        if (requireMatcher.matches() && ourMatcher.matches() && Integer.valueOf(requireMatcher.group(1)) > Integer.valueOf(ourMatcher.group(1))) {
	            System.err.printf("This application requires Java specification version %s or later to run (this Java virtual machine implements specification version %s)%n", requireJavaVersion, ourJavaVersion);
	            System.exit(1);
	        }
	
	        ModularURLStreamHandlerFactory.addHandlerModule(module);
	        ModularContentHandlerFactory.addHandlerModule(module);
	
	        // at this point, having a security manager already installed will prevent correct operation.
	
	        final SecurityManager existingSecMgr = System.getSecurityManager();
	        if (existingSecMgr != null) {
	            System.err.println("An existing security manager was detected.  You must use the -secmgr switch to start with a security manager.");
	            return false;
	        }
	
	        try {
	            final Iterator<Policy> iterator = module.loadService(Policy.class).iterator();
	            if (iterator.hasNext()) {
	                Policy.setPolicy(iterator.next());
	            }
	        } catch (Exception ignored) {}
	
	        // configure policy so that if SM is enabled, modules can still function
	        final ModulesPolicy policy = new ModulesPolicy(Policy.getPolicy());
	        Policy.setPolicy(policy);
	
	//        if (secMgrModule != null) {
	//            final Module loadedModule;
	//            try {
	//                loadedModule = loader.loadModule(secMgrModule);
	//            } catch (ModuleNotFoundException e) {
	//                e.printStackTrace(System.err);
	//                System.exit(1);
	//                return;
	//            }
	//            final Iterator<SecurityManager> iterator = ServiceLoader.load(SecurityManager.class, loadedModule.getClassLoaderPrivate()).iterator();
	//            if (iterator.hasNext()) {
	//                System.setSecurityManager(iterator.next());
	//            } else {
	//                System.err.println("No security manager found in module " + secMgrModule);
	//                System.exit(1);
	//            }
	//        }
	
	//        if (defaultSecMgr) {
	//            final Iterator<SecurityManager> iterator = module.loadService(SecurityManager.class).iterator();
	//            if (iterator.hasNext()) {
	//                System.setSecurityManager(iterator.next());
	//            } else {
	//                System.setSecurityManager(new SecurityManager());
	//            }
	//        }
	
	        final ModuleClassLoader bootClassLoader = module.getClassLoaderPrivate();
	        setContextClassLoader(bootClassLoader);
	
	        final String serviceName = Main.getServiceName(bootClassLoader, "java.util.prefs.PreferencesFactory");
	        if (serviceName != null) {
	            final String old = System.setProperty("java.util.prefs.PreferencesFactory", serviceName);
	            try {
	                Preferences.systemRoot();
	            } finally {
	                if (old == null) {
	                    System.clearProperty("java.util.prefs.PreferencesFactory");
	                } else {
	                    System.setProperty("java.util.prefs.PreferencesFactory", old);
	                }
	            }
	        }
	
	        final String logManagerName = Main.getServiceName(bootClassLoader, "java.util.logging.LogManager");
	        if (logManagerName != null) {
	            System.setProperty("java.util.logging.manager", logManagerName);
	            if (LogManager.getLogManager().getClass() == LogManager.class) {
	                System.err.println("WARNING: Failed to load the specified log manager class " + logManagerName);
	            } else {
	                Module.setModuleLogger(new JDKModuleLogger());
	            }
	        }
	
	        final String mbeanServerBuilderName = Main.getServiceName(bootClassLoader, "javax.management.MBeanServerBuilder");
	        if (mbeanServerBuilderName != null) {
	            System.setProperty("javax.management.builder.initial", mbeanServerBuilderName);
	            // Initialize the platform mbean server
	            ManagementFactory.getPlatformMBeanServer();
	        }
	
	        final ServiceLoader<Provider> providerServiceLoader = ServiceLoader.load(Provider.class, bootClassLoader);
	        Iterator<Provider> iterator = providerServiceLoader.iterator();
	        for (;;) try {
	            if (! (iterator.hasNext())) break;
	            final Provider provider = iterator.next();
	            final Class<? extends Provider> providerClass = provider.getClass();
	            // each provider needs permission to install itself
	            doPrivileged((PrivilegedAction<Void>) () -> {
	                Security.addProvider(provider);
	                return null;
	            }, new AccessControlContext(new ProtectionDomain[] { providerClass.getProtectionDomain() }));
	        } catch (ServiceConfigurationError | RuntimeException e) {
	            Module.getModuleLogger().trace(e, "Failed to initialize a security provider");
	        }
	
	        ModuleLoader.installMBeanServer();
	
	        final ArrayList<String> argsList = new ArrayList<>(1);
	//        Collections.addAll(argsList, moduleArgs);
	
	        final ServiceLoader<PreMain> preMainServiceLoader = ServiceLoader.load(PreMain.class, bootClassLoader);
	        for (PreMain preMain : preMainServiceLoader) {
	            preMain.run(argsList);
	        }
	        
	        PrintStream stdout = System.out;
	        PrintStream errout = System.err;

	        module.setFallbackLoader(new LocalLoader() {
				
				@Override
				public List<Resource> loadResourceLocal(String name) {
					return null;
				}
				
				@Override
				public Package loadPackageLocal(String name) {
					return null;
				}
				
				@Override
				public Class<?> loadClassLocal(String name, boolean resolve) {
					try {
						return WildflyController.class.getClassLoader().loadClass(name);
					} catch (ClassNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					return null;
				}
			});
	        try {
	        	module.setMainClass("de.mhus.ports.wildfly.BoostrapMain");
	            module.run(argsList.toArray(new String[argsList.size()]));
	        } catch (InvocationTargetException e) {
	            throw e.getCause();
	        }

	        System.setOut(stdout);
	        System.setErr(errout);
	        
		} catch (Throwable t ) {
			t.printStackTrace();
			return false;
		}
		return true;
	}
	
	public boolean isRunning() {
		return module != null;
	}
	
	public ClassLoader getClassLoader() {
		if (module == null) return null;
		return module.getClassLoaderPrivate();
	}
	
}
