// Copyright (c) 2000 Govern  de les Illes Balears
package com.soffid.iam.sync.bootstrap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;

import org.json.JSONException;

import com.soffid.iam.sync.bootstrap.impl.Config;
import com.soffid.iam.sync.bootstrap.impl.ConfigurationManager;
import com.soffid.iam.sync.bootstrap.impl.DupOutputStream;
import com.soffid.iam.sync.bootstrap.impl.FileVersionManager;
import com.soffid.iam.sync.bootstrap.impl.HttpConnectionFactory;
import com.soffid.iam.sync.bootstrap.impl.JarExtractor;
import com.soffid.iam.sync.bootstrap.impl.KubernetesConfig;
import com.soffid.iam.sync.bootstrap.impl.Logger;
import com.soffid.iam.sync.bootstrap.impl.SQLConnectionFactory;

/**
 * Sync server bootstrap class
 */

public class SyncLoader extends Object {
	JarExtractor je = new JarExtractor();

    private static String FILE_SEPARATOR = File.separator;
    private static String BASE_DIRECTORY = null;
    private static String LIBRARY_SEPARATOR = FILE_SEPARATOR.compareTo("\\") == 0 ? ";" : ":";
    // private static final String REMOTE_DEBUG = "-Xdebug";
    // private static final String REMOTE_DEBUG_DESCRIPTION_SERVER =
    // "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=4321";
    // private static final String REMOTE_DEBUG_DESCRIPTION_AGENT =
    // "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=1234";
    Process process = null;
    LocalInputReader localInputReader = null;
    LocalInputReader localInputReaderError = null;
    LocalOutputWriter localOutputWriter = null;

	private File modulesDir;

    /**
     * Constructor Generara un archivo temporal dump.zip en el directorio de
     * clases
     * 
     * @param url
     *            URL http de donde descargar las clases del agente SEYCON
     * @param path
     *            Directorio de clases donde dejar el fichero temporal
     * @throws IOException
     *             No debería producirse nunca
     * @throws InternalErrorException
     */
    public SyncLoader() throws IOException {
        // Instancia las clases para evitar su pérdida en el proceso de descarga
        new LocalInputReader(System.in);
        new LocalOutputWriter(System.out);
        new LocalFinalizer();

        Config config = Config.getConfig();
		try {
			new KubernetesConfig().load();
		} catch (Exception e1) {
			new Logger("main").warn("Cannot fetch kubernetes configuration", e1);
		}

        String role = config.getRole();
        if (! "server".equals (role) && ! "agent".equals (role) && !"gateway".equals(role) && !"remote".equals(role))
        {
        	log.warn("Sync server is not configured.");
            do
            {
            	log.info("Waiting ...");
            	try
				{
            		Thread.sleep(60000);
				}
				catch (InterruptedException e)
				{
				}
            	config.reload();
            	role = config.getRole();
            } while (! "server".equals (role) && ! "agent".equals (role) && !"gateway".equals(role) && !"remote".equals(role));
        }
        
        modulesDir = new File(new File(BASE_DIRECTORY), "addons");
        modulesDir.mkdirs();
        for (File module: modulesDir.listFiles())
        {
        	module.delete();
        }

        try {
        	
            if (canUseDatabase()) 
            	waitForDatabase();
            
        	updateConfig ();
        
            if (canUseDatabase()) {
                je.extractModules(modulesDir);
                generateJAR();
            } else {
            	String host = config.getServerList().split("[, ]+")[0];
                String sourceURL = host + "downloadLibrary";
                downloadJAR(sourceURL);
            }
        } catch (Exception e) {
        	System.out.println("Unable to update components");
        	e.printStackTrace();
        }
    }

    private void waitForDatabase() throws InterruptedException {
    	while (true) {
        	try {
				Connection conn = SQLConnectionFactory.getConnection();
				conn.close();
				return;
			} catch (Exception e) {
				log.warn("Database is not available", e);
				Thread.sleep(15_000);
			}
    	}
	}

	/**
     * @throws IOException 
     * @throws FileNotFoundException 
     * @throws ServerRedirectException 
     * @throws SQLException 
     * @throws CertificateException 
     * @throws NoSuchAlgorithmException 
     * @throws KeyStoreException 
     * @throws UnrecoverableKeyException 
     * @throws KeyManagementException 
	 * @throws InterruptedException 
	 * @throws JSONException 
     * @throws InternalErrorException 
	 * 
	 */
	private void updateConfig () throws FileNotFoundException, IOException, SQLException, KeyManagementException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, JSONException, InterruptedException
	{
		Config config = Config.getConfig();
		if (config.getDB() != null )
		{
			Properties prop = new ConfigurationManager().getProperties(config.getHostName());
			config.mergeProperties(prop);
		}
		else
		{
			config.updateFromServer();
		}
		new KubernetesConfig().save();
	}

	private void generateJAR() throws FileNotFoundException, IOException, SQLException {
        FileVersionManager fvm = new FileVersionManager(); 
        Config config = Config.getConfig();
        if (config.isUpdateEnabled()) {
            generateEngineFile();
            downloadDependencies("syncserver");
            fvm.deleteAllCopies("seycon-library");
            generateStandardJar("iam-tomee");
            generateStandardJar("iam-core");
            downloadDependencies("iam-core");
        }
    }

	
	List<String> blackListedDependencies = Arrays.asList(new  String[] {
			"xercesImpl"
	});
	
	private void downloadDependencies (String file) throws IOException,
					FileNotFoundException, SQLException
	{
        FileVersionManager fvm = new FileVersionManager(); 
		File f = fvm.getInstalledFile(file);
		if ( f != null)
		{
			log.info("Parsing dependencies of "+f.toString());
			JarFile jf = new JarFile(f);
			String classPath = jf.getManifest().getMainAttributes().getValue("Class-Path");
			if (classPath != null)
			{
		    	for (String component: classPath.split(" "))
		    	{
		    		int index = 0;
		    		boolean repeat;
		    		do
		    		{
		    			repeat = false;
		    			index = component.indexOf('-', index);
		    			if (index >= 0 && component.length() > index)
		    			{
		    				if (Character.isDigit(component.charAt(index+1)))
		    				{
		    					String componentName = component.substring(0, index);
		    					if (! blackListedDependencies.contains(componentName))
		    						generateStandardJar(componentName);
		    				}
		    				else
		    				{
		    					index ++;
		    					repeat = true;
		    				}
		    			}
		    		} while (repeat);
		    	}
			}
		}
	}

    public void generateStandardJar(String name) throws FileNotFoundException,
        IOException, SQLException {
        generateStandardFile (name, ".jar");
    }
    
    public void generateStandardFile(String name, String extension) throws FileNotFoundException,
            IOException, SQLException {
        Config c = Config.getConfig();
        FileVersionManager fvm = new FileVersionManager(); 
        if (!fvm.isFileInstalled(name) || c.canUpdateComponent(name))
            generateFile("component." + name, name, extension);
        else
            System.out.println("Component [" + name + "] is frozen");
    }

    private void generateFile(String componentName, String jarName, String extension) throws SQLException {
        
        JarExtractor x = new JarExtractor();
        FileVersionManager fvm = new FileVersionManager();

        String fileName;
        try {
        	fileName = getLibraryName(jarName, extension);
        } catch (IOException e) {
        	return;
        }
        File targetFile = new File (fileName);
        // Do not download file as it is already downloaded
        if (targetFile.canRead())
        	return;
        
        OutputStream out = null;
    	File tmpFile = new File(fileName+".tmp");
        try {
            out = new FileOutputStream(tmpFile);
            if (! x.generateJar(componentName, out))
            {
                System.out.println("Not generated "+fileName);
            	out.close ();
            	tmpFile.delete();
            } else {
                out.close();
                tmpFile.renameTo(targetFile);
                System.out.println("Generated "+fileName);
                fvm.deleteOldCopies(jarName);
            }
        } catch (Exception e) {
            if (out != null)
                try {
                    out.close();
                } catch (IOException e1) {
                }
            tmpFile.delete();
            System.out.println("Error generating file " + fileName);
            e.printStackTrace();
        }
    }
    
    private void generateEngineFile() throws SQLException, IOException {
        
        JarExtractor x = new JarExtractor();
        FileVersionManager fvm = new FileVersionManager();

        String fileName;
        try {
            fileName = getBaseLibraryName();
        } catch (FileNotFoundException e) {
        	return;
        }
        File targetFile = new File (fileName);
        // Do not download file as it is already downloaded
        if (targetFile.canRead())
        	return;

        OutputStream out = null;
    	File tmpFile = new File(fileName+".tmp");
        try {
            out = new FileOutputStream(tmpFile);
            if (! x.generateBaseJar(out))
            {
                out.close();
                tmpFile.delete();
            }
            else
            {
                out.close();
                System.out.println("Generated "+fileName);
                tmpFile.renameTo(targetFile);
                fvm.deleteOldCopies("syncserver");
            }
        } catch (Exception e) {
            if (out != null)
                try {
                    out.close();
                } catch (IOException e1) {
                }
            tmpFile.delete();
            System.out.println("Error generating file " + fileName);
            e.printStackTrace();
        }
    }
    
    public void downloadJAR(String sourceURL) throws Exception {
        FileVersionManager fvm = new FileVersionManager();
        System.out.println("Downloading Soffid IAM Server from "+sourceURL);
        Config c = Config.getConfig();
        String fileName;
        if (c.canUpdateComponent("syncserver")) {
            fileName = getBaseLibraryName();
            downloadFile(sourceURL, fileName);
            fvm.deleteOldCopies("syncserver");
            fvm.deleteAllCopies("seycon-library");
            fvm.deleteAllCopies("seycon-common");
            fvm.deleteAllCopies("iam-common");
        }
    }

    public static void downloadFile(String sourceURL, String fileName)
            throws Exception, IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            javax.net.ssl.HttpsURLConnection connection = null;
            try {
                URL url = new URL(sourceURL);
                connection = HttpConnectionFactory.getConnection(url);
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.connect();
            } catch (Exception e) {
                throw e;
            }
            in = connection.getInputStream();
            File f = new File(fileName);
            boolean replacing = f.exists();
            
            out = new FileOutputStream(replacing? fileName+".new.jar": fileName);
            byte buffer[] = new byte[10240];
            int size = in.read(buffer);
            while (size >= 0) {
                out.write(buffer, 0, size);
                size = in.read(buffer);
            }
            out.flush();
            out.close();
            in.close();
            connection.disconnect();
            if (replacing)
            {
            	FileInputStream in2 = new FileInputStream(fileName+".new.jar");
            	FileOutputStream out2 = new FileOutputStream(fileName);
            	int read;
            	while ( (read = in2.read(buffer)) > 0)
            		out2.write(buffer, 0, read);
            	in2.close();
            	out2.close();
            	f.delete();
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
        }
    }

    private String getLibraryName(String component, String extension) throws SQLException, IOException {
    	JarExtractor je = new JarExtractor();
    	String dbVersion = canUseDatabase() ? je.getComponentVersion("component."+component) : "LATEST";
    	return getLibraryName(component, dbVersion, extension);
    }
    
    private String getLibraryName(String component, String dbVersion, String extension) throws SQLException {
    	if (dbVersion == null)
    	{
            Date date = new Date();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yy.MM.dd-HH.mm.ss");
            return BASE_DIRECTORY + FILE_SEPARATOR + "lib" + FILE_SEPARATOR
                    + component + "-" + dateFormat.format(date) + extension;
    	}
    	else if (dbVersion.endsWith("-SNAPSHOT"))
    	{
            Date date = new Date();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yy.MM.dd-HH.mm.ss");
            return BASE_DIRECTORY + FILE_SEPARATOR + "lib" + FILE_SEPARATOR
                            + component + "-" + dbVersion + "-" + dateFormat.format(date) + extension;
    	}
    	else	
    	{
            return BASE_DIRECTORY + FILE_SEPARATOR + "lib" + FILE_SEPARATOR
                            + component + "-" + dbVersion + extension;
    	}
    }

    private boolean canUseDatabase () throws FileNotFoundException, IOException
    {
        return "server".equals(Config.getConfig().getRole()) && 
        		Config.getConfig().getDB() != null;
    }
    
    private String getBaseLibraryName() throws SQLException, IOException {
    	String dbVersion = canUseDatabase() ? new JarExtractor().getBaseJarVersion() : "LATEST";
        String fileName = getLibraryName("syncserver", dbVersion, ".jar");
        return fileName;
    }

    private boolean isWindows() {
        return FILE_SEPARATOR.compareTo("\\") == 0;
    }

    /**
     * Instancia el ServerLoader y descomprime el fichero temporal generado. A
     * continuacion ejecuta el método main de la clase principal
     * 
     * @param url
     *            URL donde se encuentra el fichero .zip original
     * @param path
     *            Directorio donde despositar el fichero .zip temporal y donde
     *            descomprimirlo
     * @param objectType
     *            Clase principal de objetos. Debe pasarse por nombre para
     *            evitar que el cargador de java intente resolverlo antes de que
     *            se haya descargado
     * @param args
     *            Arumentos a pasar a la clase de objetos
     */
    public void load(String[] args) throws Exception {
        try {
            StringBuffer libraryList = new StringBuffer(".");
            File dir = new File(BASE_DIRECTORY + FILE_SEPARATOR + "lib");
            String[] children = dir.list();
            if (children == null) {
                log.warn("No se ha podido encontrar directorio "+
                        BASE_DIRECTORY + FILE_SEPARATOR + "lib");
                System.exit(1);
            } else {
                for (File module: modulesDir.listFiles())
                {
                    libraryList.append(LIBRARY_SEPARATOR);
                    libraryList.append(module.getPath());
                }
                for (int i = 0; i < children.length; i++) {
                    File file = new File(dir, children[i]);
                    libraryList.append(LIBRARY_SEPARATOR);
                    libraryList.append(file.getPath());
                }
            }

            String jreExec = System.getProperty("java.home") + FILE_SEPARATOR
                    + "bin" + FILE_SEPARATOR + "java";
            String mainClass = Config.getConfig().getCustomProperty("startupClass");
            if (mainClass == null)
            	mainClass = "com.soffid.iam.sync.SoffidApplication";
            
            String javaopts = Config.getConfig().getJVMOptions();

            javaopts = javaopts.replaceAll("%d",
                    Long.toString(System.currentTimeMillis()));

            String opts[] = javaopts.trim().split(" +");
            String execArguments[] = new String[4 + opts.length + args.length];
            execArguments[0] = jreExec;
            int argument = 1;
            for (int i = 0; i < opts.length; i++)
                execArguments[argument++] = opts[i];
            execArguments[argument++] = "-classpath";
            execArguments[argument++] = libraryList.toString();
            execArguments[argument++] = mainClass;
            for (int i = 0; i < args.length; i++) {
                execArguments[argument++] = args[i];
            }
            if (isWindows()) {
                boolean pathExists = false;
                String libraryPath = BASE_DIRECTORY + FILE_SEPARATOR + "system";
                Map m = System.getenv();
                String newEnv[] = new String[m.size()];
                int i = 0;
                for (Iterator it = m.keySet().iterator(); it.hasNext();) {
                    String key = (String) it.next();
                    if ("PATH".compareToIgnoreCase(key) == 0) {
                        newEnv[i++] = key + "=" + libraryPath + ";"
                                + (String) m.get(key);
                        pathExists = true;
                    } else {
                        newEnv[i++] = key + "=" + (String) m.get(key);
                    }
                }
                if (!pathExists) {
                    String finalEnvironment[] = new String[newEnv.length + 1];
                    System.arraycopy(newEnv, 0, finalEnvironment, 0,
                            newEnv.length);
                    finalEnvironment[finalEnvironment.length - 1] = "PATH="
                            + libraryPath;
                    newEnv = finalEnvironment;
                }
                process = Runtime.getRuntime().exec(execArguments, newEnv);
            } else {
                String libraryPath = BASE_DIRECTORY + FILE_SEPARATOR + "system";
                Map m = System.getenv();
                int addedEnvirtomentVariables = 0;
                boolean addLdLibraryPath = m.get("LD_LIBRARY_PATH") == null;
                if (addLdLibraryPath) {
                    addedEnvirtomentVariables++;
                }
                boolean addJavaLibraryPath = m.get("java.library.path") == null;
                if (addJavaLibraryPath) {
                    addedEnvirtomentVariables++;
                }
                boolean addPath = m.get("PATH") == null;
                if (addPath) {
                    addedEnvirtomentVariables++;
                }

                String newEnv[] = new String[m.size()
                        + addedEnvirtomentVariables];
                int i = 0;
                for (Iterator it = m.keySet().iterator(); it.hasNext();) {
                    String key = (String) it.next();
                    if ("PATH".compareToIgnoreCase(key) == 0
                            || "LD_LIBRARY_PATH".compareToIgnoreCase(key) == 0
                            || "java.library.path".compareToIgnoreCase(key) == 0) {
                        newEnv[i++] = key + "=" + libraryPath + ":"
                                + (String) m.get(key);
                    } else {
                        newEnv[i++] = key + "=" + (String) m.get(key);
                    }
                }

                int diff = 1;
                if (addLdLibraryPath) {
                    newEnv[newEnv.length - diff] = "LD_LIBRARY_PATH="
                            + libraryPath;
                    diff++;
                }
                if (addJavaLibraryPath) {
                    newEnv[newEnv.length - diff] = "java.library.path="
                            + libraryPath;
                    diff++;
                }
                if (addPath) {
                    newEnv[newEnv.length - diff] = "PATH=" + libraryPath;
                }

                log.info("Executing " + execArguments[0] );
                for (int j = 1 ; j < execArguments.length; j++) {
                    log.info ( execArguments[j] );
                }

                process = Runtime.getRuntime().exec(execArguments, newEnv);
            }
            InputStream inputStream = process.getInputStream();
            localInputReader = new SyncLoader.LocalInputReader(inputStream);
            Thread thread = new Thread(localInputReader);
            thread.setDaemon(true);
            thread.start();
            InputStream inputStreamError = process.getErrorStream();
            localInputReaderError = new SyncLoader.LocalInputReader(
                    inputStreamError);
            Thread threadError = new Thread(localInputReaderError);
            threadError.setDaemon(true);
            threadError.start();
            localOutputWriter = new LocalOutputWriter(process.getOutputStream());
            Thread threadOutput = new Thread(localOutputWriter);
            threadOutput.start();
            Runtime.getRuntime().addShutdownHook(
                    new Thread(new LocalFinalizer()));
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static PrintStream output = null;
    private static String logDate = null;
    private static Logger log;

    public static synchronized PrintStream getLogStream() throws IOException {
        Calendar c = Calendar.getInstance();
        StringBuffer s = new StringBuffer();
        s.append(c.get(Calendar.YEAR));
        s.append("-");
        s.append(c.get(Calendar.MONTH) + 1); // Els mesos comencen en 0 (ups...)
        s.append("-");
        s.append(c.get(Calendar.DAY_OF_MONTH));
        
        String l = Config.getConfig().getCustomProperty("log");
        File logFile = l == null ? Config.getConfig().getLogFile(): new File(l);
        if (output == null) {
            logDate = s.toString();
            try {
                OutputStream out = new FileOutputStream(logFile, true);
                output = new PrintStream (new DupOutputStream(out, System.out));
            } catch (IOException e) {
                System.out.println("Imposible almacenar logs en "+logFile);
                output = System.out;
            }
        } else if (logDate != null && !logDate.equals(s.toString())) {
            output.close();
            File dir = logFile.getParentFile();
            logFile.renameTo(new File(dir, "syncserver.log-" + logDate));
            // Borrar archivos antiguos de hace más de cinco días
            long deleteBefore = System.currentTimeMillis() - 90L * 24L * 60L * 60L
                    * 1000L;
            for (File f : dir.listFiles()) {
                if (f.getName().startsWith("syncserver.log")
                        && f.lastModified() < deleteBefore)
                    f.delete();
            }
            logDate = s.toString();
            OutputStream out = new FileOutputStream(logFile, true);
            output = new PrintStream( new DupOutputStream(out, System.out) );
        }
        return output;
    }

    public class LocalFinalizer implements Runnable {

        LocalFinalizer() {
        }

        public void run() {
            if (process != null) {
                process.destroy();
            }
            if (localInputReader != null) {
                localInputReader.stop();
            }
            if (localInputReaderError != null) {
                localInputReaderError.stop();
            }
            if (localOutputWriter != null) {
                localOutputWriter.stop();
            }
        }
    }

    public class LocalOutputWriter implements Runnable {
        OutputStream localOutputStream = null;
        boolean stop = false;

        LocalOutputWriter(OutputStream outputStream) {
            localOutputStream = outputStream;
        }

        public void stop() {
            stop = true;
        }

        public void run() {
            while (!stop) {
                String message = "Keep alive";
                try {
                    localOutputStream.write(message.getBytes());
                    localOutputStream.flush();
                } catch (IOException e) {
                    // try next
                }
                try {
                    Thread.sleep(1000 * 2);
                } catch (InterruptedException e) {
                }
            }
            try {
				localOutputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
    }

    class LocalInputReader implements Runnable {
        InputStream localInputStream = null;
        boolean stop = false;

        LocalInputReader(InputStream inputStream) {
            localInputStream = inputStream;
        }

        public void stop() {
            stop = true;
        }

        public void run() {
            while (!stop) {
                byte readBytes[] = new byte[4096];
                try {
                    int readedBytes = 0;
                    if ((readedBytes = localInputStream.read(readBytes)) > 0) {
                        SyncLoader.getLogStream().write(readBytes, 0,
                                readedBytes);
                        if (new String(readBytes, 0, readedBytes)
                                .indexOf("java.lang.OutOfMemoryError: ") >= 0) {
                            log.warn("RESTARTING !!!");
                            localOutputWriter.stop();
                        }
                    }
                } catch (IOException e) {
                    // try next
                }
            }
        }
    }

    private int waitProcess() throws InterruptedException {
        int exitValue = process.waitFor();
        localInputReader.stop();
        localInputReaderError.stop();
        return exitValue;
    }

    /**
     * main <LI>Carga las propiedades del fichero seycon.properties</LI> <LI>
     * Invoca al metodo load con las propiedades seycon.class.source y
     * seycon.class.dir</LI>
     * 
     * @param args
     *            Parámetros que se pasarán a la clase
     *            {@link es.caib.seycon.ClientApplication}
     */
    public static void main(String[] args) {
        try {
        	BASE_DIRECTORY = Config.getConfig().getHomeDir().getPath();

            boolean continueIteration = true;
            while (continueIteration) {
              log = new Logger("main");
              log.info("*************************************************");
              log.info("Soffid IAM Sync Server BOOTSTRAP version " + getVersion());
              SyncLoader serverLoader = new SyncLoader();
              serverLoader.load(args);
              Thread.sleep(60000); // Do not restart before 60 seconds
              continueIteration = serverLoader.waitProcess() != 0;
            }
        } catch (Throwable e) {
        	System.err.println("Error starting soffid sync server");
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public static String getVersion() {
        try {
            InputStream in = SyncLoader.class.getResourceAsStream("/META-INF/maven/com.soffid.iam.sync/bootstrap/pom.properties"); //$NON-NLS-1$
            if (in == null)
                return "UNKNOWN"; //$NON-NLS-1$
            else {
                Properties p = new Properties();
                p.load(in);
                return p.getProperty("version"); //$NON-NLS-1$
            }
        } catch (IOException e) {
            return "UNKNOWN"; //$NON-NLS-1$
        }
    }

}
