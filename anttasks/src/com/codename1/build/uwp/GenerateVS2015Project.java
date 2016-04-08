/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.codename1.build.uwp;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.taskdefs.Unpack;
import org.apache.tools.ant.taskdefs.Zip;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.ZipFileSet;

/**
 * An ANT task to generate a Visual Studio 2015 UWP project.  This should be run
 * inside the context of a CodenameOne Project.
 * @author steve
 */
public class GenerateVS2015Project extends Task {
    
    private File vsProjectTemplate;
    private File vsProjectOutput;
    private File ikvmDir;
    
    
    private File getDefaultProjectTemplateZip() {
        return new File(getProject().getBaseDir(), "vsProjectTemplate.zip");
    }
    
    @Override
    public void execute() throws BuildException {
        if (vsProjectOutput == null) {
            throw new BuildException("Please set the vsProjectOutput argument to the directory where the project should be saved");
        }
        
        if (vsProjectOutput.exists()) {
            log("Updating existing project at "+vsProjectOutput);
            generateProject(true);
            return;
        } else {
            log("Project doesn't exist yet.  Generating it now");
            generateProject(false);
        }
        
    }
    
    private void generateProject(boolean onlyUpdate) {
        
        if (ikvmDir == null) {
            String ikvmPath = getProject().getProperty("ikvm.dir");
            if (ikvmPath != null) {
                ikvmDir = new File(ikvmPath);
            }
        }
        
        if (ikvmDir == null || !ikvmDir.exists()) {
            throw new BuildException("Please specify the ikvmDir property and ensure it exists. Current setting "+ikvmDir+" doesn't exist.");
        }
        
        if (!onlyUpdate) {
            if (vsProjectTemplate == null) {

                vsProjectTemplate = getDefaultProjectTemplateZip();
                if (!vsProjectTemplate.exists()) {
                    try {
                        Files.copy(getClass().getResourceAsStream("/vsProjectTemplate.zip"), vsProjectTemplate.toPath());
                    } catch (IOException ex) {
                        throw new BuildException("Failed to copy vsProjectTemplate", ex);
                    }
                }
            }


            if (!vsProjectTemplate.exists()) {
                throw new BuildException("The project template specified at "+vsProjectTemplate+" could not be found.");
            }

            FileSet fs = new FileSet();
            fs.setIncludes("**");
            if (vsProjectTemplate.isDirectory()) {
                fs.setDir(vsProjectTemplate);
            } else {
                // ZipFileSet works differently than I thought.
                throw new BuildException("Zip VSProjectTemplate not supported.  Please extract manually and use a directory as the template");
                /*
                ZipFileSet zfs = new ZipFileSet();
                zfs.setIncludes("**");
                zfs.setFile(vsProjectTemplate);

                fs = zfs;
                */
            }
            Copy copyTask = (Copy)this.getProject().createTask("copy");
            copyTask.setTodir(vsProjectOutput);
            copyTask.addFileset(fs);
            log("Copying project template to "+vsProjectOutput);
            copyTask.execute();
        }
        
        String distJar = getProject().getProperty("dist.jar");
        String path = getProject().getProperty("javac.classpath");
        String codenameOneJar = null;
        for (String p : new Path(getProject(), path).list()) {
            if (p.endsWith("CodenameOne.jar")) {
                log("Found CodenameOne jar at "+p);
                codenameOneJar = p;
                break;
            }
        }
        
        if (codenameOneJar == null) {
            throw new BuildException("Could not find CodenameOne jar");
        }
        
        
        File codenameOneJarFile = new File(codenameOneJar);
        File distJarFile = new File(getProject().getBaseDir(), distJar);
        
        
        if (!codenameOneJarFile.exists()) {
            throw new BuildException("CodenameOne.jar file was not found at "+codenameOneJarFile);
        }
        if (!distJarFile.exists()) {
            throw new BuildException("App jar file was not found at "+distJarFile);
        }
        
        File dllFile = new File(distJarFile.getParentFile(), "CodenameOneUWP.dll");
        
        File implementationFactoryJar = null;
        try {
            implementationFactoryJar = getImplementationFactoryJar();
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new BuildException("Failed to extract the implementation factory jar", ex);
        }
        
        log("Generating application dll file using ikvmc...");
        ikvmc(dllFile, distJarFile, codenameOneJarFile, implementationFactoryJar);
        
        File csProjFile = new File(vsProjectOutput, "UWPApp"+ File.separator + "UWPApp.csproj");
        File destDllFile = new File(vsProjectOutput, "UWPApp" + File.separator + "lib" + File.separator + "CodenameOneUWP.dll");
        if (requiresUpdate(destDllFile, dllFile)) {
            log("Copying application dll to "+destDllFile);
            copy(dllFile, destDllFile);
        } else {
            log("Application dll file has not changed.");
        }
        
        File[] ikvmDlls = new File[]{
            new File(ikvmDir, "bin/IKVM.Runtime.dll"),
            new File(ikvmDir, "bin/IKVM.Reflection.dll"),
            new File(ikvmDir, "bin/IKVM.OpenJDK.Core.dll"),
            new File(ikvmDir, "bin/IKVM.OpenJDK.Text.dll"),
            new File(ikvmDir, "bin/IKVM.OpenJDK.Util.dll")
        };
        
        File[] ikvmDllDests = new File[]{
            new File(destDllFile.getParentFile(), "IKVM.Runtime.dll"),
            new File(destDllFile.getParentFile(), "IKVM.Reflection.dll"),
            new File(destDllFile.getParentFile(), "IKVM.OpenJDK.Core.dll"),
            new File(destDllFile.getParentFile(), "IKVM.OpenJDK.Text.dll"),
            new File(destDllFile.getParentFile(), "IKVM.OpenJDK.Util.dll")
        };
        
        for (int i=0; i<ikvmDlls.length; i++) {
            try {
                log("Copying "+ikvmDlls[i]+" to "+ikvmDllDests[i]);
                copy(ikvmDlls[i], ikvmDllDests[i]);
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new BuildException("Failed to copy IKVM dll file : "+ikvmDlls[i]+ " into project directory");
            }
        }
        
        // Extract resources out of the jar files
        log("Extracting resources from jars...");
        File resourceDir = new File(vsProjectOutput, "UWPApp" + File.separator + "res");
        extractResources(resourceDir, distJarFile, codenameOneJarFile);
        
        
        List<File> resourceFiles = listFilesRecursive(resourceDir, new ArrayList<File>());
        List<String> resourcePaths = getRelativePaths(resourceDir.getParentFile(), resourceFiles, new ArrayList<String>());
        StringBuilder sb = new StringBuilder();
        /*
        <ItemGroup>
    <AppxManifest Include="Package.appxmanifest">
      <SubType>Designer</SubType>
    </AppxManifest>
    <None Include="Package.StoreAssociation.xml" />
    <Content Include="res\material-design-font.ttf">
      <CopyToOutputDirectory>Always</CopyToOutputDirectory>
    </Content>
    <Content Include="res\CN1Resource.res">
      <CopyToOutputDirectory>Always</CopyToOutputDirectory>
    </Content>
    <Content Include="res\winTheme.res">
      <CopyToOutputDirectory>Always</CopyToOutputDirectory>
    </Content>
    <Content Include="res\theme.res">
      <CopyToOutputDirectory>Always</CopyToOutputDirectory>
    </Content>
    <None Include="UWPApp_StoreKey.pfx" />
    <None Include="UWPApp_TemporaryKey.pfx" />
  </ItemGroup>*/
        sb.append("<!-- CN1 RESOURCES --><ItemGroup>\n");
                
        for (String resPath : resourcePaths) {
            sb.append("   <Content Include=\"").append(resPath.replaceAll("/", "\\")).append("\">\n")
                    .append("       <CopyToOutputDirectory>Always</CopyToOutputDirectory>\n")
                    .append("    </Content>");
        }
        sb.append("</ItemGroup><!-- END CN1 RESOURCES -->\n");
        
        log("Adding resources to visual studio project");
        try {
            replaceAllInFile(csProjFile, "(?s)<!-- CN1 RESOURCES -->.*<!-- END CN1 RESOURCES -->", "");
            replaceInFile(csProjFile, "</Project>", sb.toString()+"\n</Project>");
        } catch (IOException ex) {
            throw new BuildException("Failed to add resource to csproj file", ex);
        }
        
        log("Updating bootstrap sources");
        
        File mainCsFile = new File(vsProjectOutput, "UWPApp"+ File.separator+"Main.cs");
        String mainClassName = getProject().getProperty("codename1.mainName");
        String packageName = getProject().getProperty("codename1.packageName");
        try {
            replaceAllInFile(mainCsFile, "com\\.codename1\\.tests\\.hellowin\\.HelloWindows", packageName + "."+mainClassName);
        } catch (IOException ex) {
            throw new BuildException("Failed to update main class name in Main.cs file.", ex);
        }
        
        super.execute(); 
    }
    
    private List<File> listFilesRecursive(File root, List<File> out) {
        for (File f : root.listFiles()) {
            if (f.getName().equals(".")||f.getName().equals("..")) {
                continue;
            }
            if (f.isDirectory()) {
                listFilesRecursive(f, out);
            } else if (f.isFile()) {
                out.add(f);
            }
        }
        return out;
    }
    
    private List<String> getRelativePaths(File root, List<File> files, List<String> out) {
        String basePath = root.getAbsolutePath();
        
        for (File f : files) {
            String path = f.getAbsolutePath();
            if (path.startsWith(basePath)) {
                path = path.substring(basePath.length()+1);
                if (path.startsWith(File.separator)) {
                    path = path.substring(File.separator.length());
                }
                out.add(path);
            }
        }
        return out;
    }
    
    private void copy(File src, File dest) {
        Copy cp = (Copy)getProject().createTask("copy");
        cp.setFile(src);
        cp.setTofile(dest);
        cp.execute();
    }
    
    public void replaceInFile(File sourceFile, String marker, String newValue) throws IOException {
        DataInputStream dis = new DataInputStream(new FileInputStream(sourceFile));
        byte[] data = new byte[(int) sourceFile.length()];
        dis.readFully(data);
        dis.close();
        FileWriter fios = new FileWriter(sourceFile);
        String str = new String(data);
        str = str.replace(marker, newValue);
        fios.write(str);
        fios.close();
    }

    public void replaceAllInFile(File sourceFile, String marker, String newValue) throws IOException {
        DataInputStream dis = new DataInputStream(new FileInputStream(sourceFile));
        byte[] data = new byte[(int) sourceFile.length()];
        dis.readFully(data);
        dis.close();
        FileWriter fios = new FileWriter(sourceFile);
        String str = new String(data);
        str = str.replaceAll(marker, newValue);
        fios.write(str);
        fios.close();
    }
    
    private void extractResources(File destDir, File... sourceJars) {
        UnzipUtility unzip = new UnzipUtility();
        for (File src : sourceJars) {
            try {
                unzip.unzip(src.getAbsolutePath(), destDir.getAbsolutePath(), 
                        null,
                        new String[]{"META-INF/**", "**/package.html", "**/package-info.html", "**/*.class"}
                        , false);
            } catch (IOException ex) {
                ex.printStackTrace();
                throw new BuildException("Failed to extract resource from file "+src, ex);
            }
            
        }
    }
    
    /**
     * Returns new file with the specified suffix, based on the existing file.
     * @param f The source file.
     * @param suffix The suffix to change it to
     * @return A new file with the new suffix.
     * 
     */
    private File getFileWithSuffix(File f, String suffix) {
        File out = new File(f.getParentFile(), f.getName());
        if (out.getName().indexOf(".") != -1) {
            out = new File(out.getParentFile(), out.getName().substring(0, out.getName().indexOf(".")) + suffix);
        } else {
            out = new File(out.getParentFile(), out.getName() + suffix);
        }
        return out;
    }
    
    private boolean requiresUpdate(File f, File... deps) {
        if (!f.exists()) {
            return true;
        }
        for (File dep : deps) {
            if (dep.lastModified() > f.lastModified()) {
                return true;
            }
        }
        return false;
    }
    
    private void ikvmc(File output, File... inputs) {
        if (!requiresUpdate(output, inputs)) {
            return;
        }
        /*
        <zip destfile="dist/CodenameOneUWP.jar">
            <zipgroupfileset dir="." includes="lib/CodenameOne.jar,lib/app.jar"/>
        </zip>
        */
        
        File combinedJar = getFileWithSuffix(output, "-combined.jar");
        if (requiresUpdate(combinedJar, inputs)) {
            try {
                mergeZips(combinedJar, null, inputs);
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new BuildException("Failed to merge jar files.", ex);
            }
            /*
            Zip zip = (Zip)getProject().createTask("zip");
            zip.setDestFile(combinedJar);
            for (File input : inputs) {
                ZipFileSet zfs = new ZipFileSet();
                zfs.setFile(input);
                zfs.setIncludes("**");
                zip.addFileset(zfs);
            }

            zip.execute();
            */
        }
        
        /*
        <exec executable="${ikvmc}">
            <arg value="-debug"/>
            <arg value="-noautoserialization"/>
            <arg value="-target:library"/>
            <arg value="-out:dist/CodenameOneUWP.dll"/>
            <arg value="dist/CodenameOneUWP.jar"/>
        </exec>
        */
        ExecTask e = (ExecTask)getProject().createTask("exec");
        File ikvmcFile = new File(ikvmDir, "bin"+ File.separator + "ikvmc");
        e.setExecutable(ikvmcFile.getAbsolutePath());
        e.createArg().setValue("-debug");
        e.createArg().setValue("-noautoserialization");
        e.createArg().setValue("-target:library");
        e.createArg().setValue("-out:"+output.getAbsolutePath());
        e.createArg().setValue(combinedJar.getAbsolutePath());
        e.execute();
        
        
    }

    /**
     * @return the vsProjectTemplate
     */
    public File getVsprojecttemplate() {
        return vsProjectTemplate;
    }

    /**
     * @param vsProjectTemplate the vsProjectTemplate to set
     */
    public void setVsprojecttemplate(File vsProjectTemplate) {
        this.vsProjectTemplate = vsProjectTemplate;
    }

    /**
     * @return the vsProjectOutput
     */
    public File getVsProjectOutput() {
        return vsProjectOutput;
    }

    /**
     * @param vsProjectOutput the vsProjectOutput to set
     */
    public void setVsProjectOutput(File vsProjectOutput) {
        this.vsProjectOutput = vsProjectOutput;
    }

    /**
     * @return the ikvmDir
     */
    public File getIkvmDir() {
        return ikvmDir;
    }

    /**
     * @param ikvmDir the ikvmDir to set
     */
    public void setIkvmDir(File ikvmDir) {
        this.ikvmDir = ikvmDir;
    }
    
    
    private void mergeZips(File output, FilenameFilter filter, File... inputs) throws IOException {
        ZipOutputStream zOut = new ZipOutputStream(new FileOutputStream(output));
        byte[] buffer = new byte[4096*1024];
        HashSet<String> addedPaths = new HashSet<String>();
        try {
            for (File input : inputs) {
                ZipFile original = new ZipFile(input);
                Enumeration<? extends ZipEntry> entries = original.entries();
                

                ZipEntry entry = null;
                while (entries.hasMoreElements()) {
                    entry = entries.nextElement();
                    if (!addedPaths.contains(entry.getName()) && (filter == null || filter.accept(null, entry.getName()))) {
                        addedPaths.add(entry.getName());
                        zOut.putNextEntry(entry);
                        if (!entry.isDirectory()) {
                            InputStream is = original.getInputStream(entry);
                            try {
                                copy(is, zOut, buffer);
                            } finally {
                                if (is != null) {
                                    try {is.close();} catch (Exception e){}
                                }
                            }
                        }
                        zOut.closeEntry();
                    }

                }
                

            }
        } finally {
            if (zOut != null) {
                try {
                    zOut.close();
                } catch (Exception ex){}
            }
        }
    }
    
    private static void copy(InputStream input, OutputStream output, byte[] buffer) throws IOException {
        int bytesRead;
        while ((bytesRead = input.read(buffer))!= -1) {
            output.write(buffer, 0, bytesRead);
        }
    }
    
    private File getImplementationFactoryJar() throws IOException {
        byte[] buffer = new byte[4096];
        File tmp = File.createTempFile("UWPImplementationFactory", ".jar");
        FileOutputStream fos = new FileOutputStream(tmp);
        InputStream is = getClass().getResourceAsStream("/UWPImplementationFactory.jar");
        try {
           copy(is, fos, buffer);
        } finally {
            if (is != null) {try { is.close();} catch (Exception ex){}}
            if (fos != null) { try {fos.close();} catch (Exception x){}}
            
        }
        tmp.deleteOnExit();
        return tmp;
    }
}
