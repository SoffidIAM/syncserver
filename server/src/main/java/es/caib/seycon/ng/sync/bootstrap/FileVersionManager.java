package es.caib.seycon.ng.sync.bootstrap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

public class FileVersionManager {

    private static String FILE_SEPARATOR = File.separator;
    private static String BASE_DIRECTORY = null;

    static {
        String moduleName = System.getProperty("exe4j.moduleName");
        if (moduleName == null) {
            URL url = FileVersionManager.class
                    .getResource("FileVersionManager.class");
            moduleName = url.getFile();
            if (url.getProtocol().equals ("jar")) {
                int excl = moduleName.indexOf("!");
                if (excl > 0)
                    moduleName = moduleName.substring(0, excl);
                try {
                    URL moduleUrl = new URL(moduleName);
                    try {
                        File f = new File (moduleUrl.toURI());
                        moduleName = f.getAbsolutePath();
                    } catch (URISyntaxException e) {
                        File f = new File (new URL(moduleName).getPath());
                        moduleName = f.getAbsolutePath();
                    }
                } catch (MalformedURLException e) {
                    BASE_DIRECTORY = ".";
                    moduleName = null;;
                }
            }
        }
        if (moduleName != null)
        {
            BASE_DIRECTORY = moduleName.substring(0,
                    moduleName.lastIndexOf(FILE_SEPARATOR));
            BASE_DIRECTORY = BASE_DIRECTORY.substring(0,
                    BASE_DIRECTORY.lastIndexOf(FILE_SEPARATOR));
        }
    }

    public boolean isFileInstalled(String pattern) {
        return getInstalledFile(pattern) != null;
    }

    public File getInstalledFile(String pattern) {
        File dir = new File(BASE_DIRECTORY + FILE_SEPARATOR + "lib");
        String[] children = dir.list();
        // Delete old jar files
        for (int i = 0; i < children.length; i++) {
            String filename = children[i];
            if (filename.startsWith(pattern)
                    && (filename.endsWith(".jar") || filename.endsWith(".war"))) {
            	File f = new File(dir, filename);
            	if (f.length() > 0)
            		return f;
            }
        }
        return null;
    }

    public void deleteAllCopies(String pattern) {
        File dir = new File(BASE_DIRECTORY + FILE_SEPARATOR + "lib");
        String[] children = dir.list();
        // Delete old jar files
        for (int i = 0; i < children.length; i++) {
            String filename = children[i];
            if (filename.startsWith(pattern)
                    && (filename.endsWith(".jar") || filename.endsWith(".war"))) {
                File newFile = new File(dir, filename);
                deleteFile(newFile);
            }
        }
    }

    public void deleteOldCopies(String pattern) {
        File dir = new File(BASE_DIRECTORY + FILE_SEPARATOR + "lib");
        String[] children = dir.list();
        File lastDateFile = null;
        // Delete old jar files
        for (int i = 0; i < children.length; i++) {
            String filename = children[i];
            if (filename.startsWith(pattern)
                    && (filename.endsWith(".jar") || filename.endsWith(".war"))) {
                File newFile = new File(dir, filename);
                lastDateFile = deleteIfOlder(lastDateFile, newFile);
            }
        }
    }

    private File deleteIfOlder(File lastDateFile, File newFile) {
        if (lastDateFile == null) {
            lastDateFile = newFile;
        } else {
            if (newFile.lastModified() >= lastDateFile.lastModified()) {
                deleteFile(lastDateFile);
                lastDateFile = newFile;
            } else {
                deleteFile(newFile);
            }
        }
        return lastDateFile;
    }

    private void deleteFile(File toDelete) {
        if (!toDelete.delete()) {
            try {
                new FileOutputStream(toDelete).close();
            } catch (IOException e) {
            }
            toDelete.deleteOnExit();
        }
    }

}
