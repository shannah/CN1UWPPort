package com.codename1.build.uwp;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
 
/**
 * This utility extracts files and directories of a standard zip file to
 * a destination directory.
 * @author www.codejava.net
 *
 */
class UnzipUtility {
    /**
     * Size of the buffer to read/write data
     */
    private static final int BUFFER_SIZE = 4096;
    
    private static boolean matches(String str, Pattern[] patterns) {
        for (Pattern p : patterns) {
            if (p.matcher(str).matches()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Extracts a zip file specified by the zipFilePath to a directory specified by
     * destDirectory (will be created if does not exists)
     * @param zipFilePath
     * @param destDirectory
     * @throws IOException
     */
    public void unzip(String zipFilePath, String destDirectory, String[] includes, String[] excludes, boolean emptyDirs) throws IOException {
        Pattern[] includePatterns = null;
        if (includes != null) {
            includePatterns = new Pattern[includes.length];
            int i=0;
            for (String pattern : includes) {
                GlobPattern p = new GlobPattern(pattern);
                includePatterns[i++] = p.compiled();
            }
        }
        
        
        Pattern[] excludePatterns = null;
        if (excludes != null) {
            excludePatterns = new Pattern[excludes.length];
            int i=0;
            for (String pattern : excludes) {
                GlobPattern p = new GlobPattern(pattern);
                excludePatterns[i++] = p.compiled();
            }
        }
        
        
        
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            throw new IOException("Destination directory does not exist");
        }
        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
        ZipEntry entry = zipIn.getNextEntry();
        // iterates over entries in the zip file
        while (entry != null) {
            //File f = new File(destDir, entry.getName());
            boolean includeEntry = true;
            if (excludePatterns != null && matches(entry.getName(), excludePatterns)) {
                includeEntry = false;
            }
            if (includePatterns != null && matches(entry.getName(), includePatterns)) {
                includeEntry = true;
            }
            if (includeEntry) {
                String filePath = destDirectory + File.separator + entry.getName();
                if (!entry.isDirectory()) {
                    // if the entry is a file, extracts it
                    extractFile(zipIn, filePath);
                } else if (emptyDirs) {
                    // if the entry is a directory, make the directory
                    File dir = new File(filePath);
                    dir.mkdir();
                }
            }
            
            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }
        zipIn.close();
    }
    /**
     * Extracts a zip entry (file entry)
     * @param zipIn
     * @param filePath
     * @throws IOException
     */
    private void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
        File f = new File(filePath);
        if (!f.getParentFile().exists()) {
            f.getParentFile().mkdirs();
        }
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytesIn = new byte[BUFFER_SIZE];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }
}