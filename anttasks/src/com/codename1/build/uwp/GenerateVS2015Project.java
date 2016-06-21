/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.codename1.build.uwp;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
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
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.taskdefs.Unpack;
import org.apache.tools.ant.taskdefs.Zip;
import org.apache.tools.ant.taskdefs.optional.PropertyFile;
import org.apache.tools.ant.types.Environment;
import org.apache.tools.ant.types.Environment.Variable;
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
        requireProperties(
                "codename1.packageName",
                "codename1.mainName");
        if (p("codename1.arg.uwp.appid", null) == null) {
            getProject().setProperty("codename1.arg.uwp.appid", "XXXXX."+p("codename1.packageName", null));
            log("uwp.appid build hint is not set.  Using "+p("codename1.arg.uwp.appid", null));
        }
        if (p("codename1.arg.uwp.displayName", null) != null) {
            getProject().setProperty("codename1.displayName", p("codename1.arg.uwp.displayName", null));
        }
        
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
            new File(ikvmDir, "bin/IKVM.OpenJDK.Core.dll"),
            new File(ikvmDir, "bin/IKVM.OpenJDK.Text.dll"),
            new File(ikvmDir, "bin/IKVM.OpenJDK.Util.dll"),
            new File(ikvmDir, "bin/IKVM.OpenJDK.Security.dll")
        };
        
        File[] ikvmDllDests = new File[]{
            new File(destDllFile.getParentFile(), "IKVM.Runtime.dll"),
            new File(destDllFile.getParentFile(), "IKVM.OpenJDK.Core.dll"),
            new File(destDllFile.getParentFile(), "IKVM.OpenJDK.Text.dll"),
            new File(destDllFile.getParentFile(), "IKVM.OpenJDK.Util.dll"),
            new File(destDllFile.getParentFile(), "IKVM.OpenJDK.Security.dll")
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
        File uwpAppDir = new File(vsProjectOutput, "UWPApp");
        File resourceDir = new File(uwpAppDir, "res");
        extractResources(resourceDir, distJarFile, codenameOneJarFile);
        
        
        List<File> resourceFiles = listFilesRecursive(resourceDir, new ArrayList<File>());
        List<String> resourcePaths = getRelativePaths(resourceDir.getParentFile(), resourceFiles, new ArrayList<String>());
        log("Found resources: "+resourcePaths);
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
            sb.append("   <Content Include=\"").append(resPath.replace("/", "\\")).append("\">\r\n")
                    .append("       <CopyToOutputDirectory>Always</CopyToOutputDirectory>\r\n")
                    .append("    </Content>");
        }
        sb.append("</ItemGroup><!-- END CN1 RESOURCES -->\n");
        
        
        log("Adding resources to visual studio project");
        try {
            replaceAllInFile(csProjFile, "(?s)<!-- CN1 RESOURCES -->.*<!-- END CN1 RESOURCES -->", "");
            replaceInFileLiteral(csProjFile, "</Project>", sb.toString()+"\n</Project>");
        } catch (IOException ex) {
            throw new BuildException("Failed to add resource to csproj file", ex);
        }
        
        
        sb = new StringBuilder();
        sb.append("<!-- CN1 SOURCES -->");
        
        List<File> sourceFiles = listFilesRecursive(new File(uwpAppDir, "src" ), new ArrayList<File>());
        /*<Compile Include="src\AsyncPictureDecoderExtension.cs" />*/
        List<String> sourcePaths = getRelativePaths(uwpAppDir, sourceFiles, new ArrayList<String>());
        for (String srcPath : sourcePaths) {
            System.out.println("Aeeting src path "+srcPath);
            sb.append("<Compile Include=\"").append(srcPath.replace("/", "\\")).append("\"/>\r\n");
        }
        sb.append("<!-- END CN1 SOURCES -->");
        
        log("Adding sources to visual studio project");
        try {
            replaceAllInFile(csProjFile, "(?s)<!-- CN1 SOURCES -->.*<!-- END CN1 SOURCES -->", "<!-- CN1 SOURCES --><!-- END CN1 SOURCES -->");
            replaceInFileLiteral(csProjFile, "<!-- CN1 SOURCES --><!-- END CN1 SOURCES -->", sb.toString());
            
        } catch (IOException ex) {
            throw new BuildException("Failed to add source to csproj file", ex);
        }
        
        log("Updating bootstrap sources");
        
        File mainCsFile = new File(vsProjectOutput, "UWPApp"+ File.separator+"Main.cs");
        String mainClassName = getProject().getProperty("codename1.mainName");
        String packageName = getProject().getProperty("codename1.packageName");
        String[] mainCsReplacements = new String[] {
            "com\\.codename1\\.tests\\.hellowin\\.HelloWindows", 
            packageName + "."+mainClassName,      
        };
        try {
            replaceAllInFile(mainCsFile, mainCsReplacements);
        } catch (IOException ex) {
            throw new BuildException("Failed to update main class name in Main.cs file.", ex);
        }
        
        File mainPageXamlCsFile = new File(uwpAppDir, "MainPage.xaml.cs");
        try {
            replaceInFile(mainPageXamlCsFile, 
                "public static String BUILD_KEY = \".*\";",
                "public static String BUILD_KEY = \""+p("codename1.build.key", null)+"\";",
                
                "public static String PACKAGE_NAME = \".*\";",
                "public static String PACKAGE_NAME = \""+p("codename1.packageName", null)+"\";",
                
                "public static String BUILT_BY_USER = \".*\";",
                "public static String BUILT_BY_USER = \""+p("codename1.username", null)+"\";",
                
                "public static String APP_NAME = \".*\";",
                "public static String APP_NAME = \""+p("codename1.displayName", null)+"\";",
                
                "public static String APP_VERSION = \".*\";",
                "public static String APP_VERSION = \""+p("codename1.version", "1.0")+"\";"
            );
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new BuildException("Failed to replace strings in "+mainPageXamlCsFile, ex);
        }
        
        File pfxFile = new File(uwpAppDir, "UWPApp_StoreKey.pfx");
        String certPath = getProject().getProperty("codename1.arg.uwp.certificate");
        File certFile = null;
        if (certPath != null && !certPath.isEmpty()) {
            certFile = new File(certPath);
            if (!certFile.exists()) {
                log("Specified certificate "+certPath+" not found.");
                certFile = null;
            }
        } else {
            log("No certificate was specified.  Please set the win.certificate build hint to the path of your certificate file.");
        }
        
        if (certFile == null) {
            log("No certificate file was provided.  Using the default certificate.");
        } else {
            try {
                copy(certFile, pfxFile); 
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new BuildException("Failed to copy certificate file.", ex);
            }
        }
        
        Cert cert = findCertCN(pfxFile);
        String certCN = cert.cn;
        if (certCN == null) {
            throw new BuildException("Invalid key file supplied.  Failed to find the Cert CN");
        }
        if (cert.hash == null) {
            throw new BuildException("Invalid cert hash");
        }
        
        log("Updating csproj file");
        String[] csprojReplacements = new String[]{
            "<PackageCertificateThumbprint>.*</PackageCertificateThumbprint>",
            "<PackageCertificateThumbprint>"+cert.hash+"</PackageCertificateThumbprint>"
        };
        
        try {
            replaceInFile(csProjFile, csprojReplacements);
        } catch (IOException ex) {
            ex.printStackTrace();
            throw new BuildException("Failed to update csproj file.", ex);
        }
        
        log("Updating appxmanifest");
        File appxManifest = new File(uwpAppDir, "Package.appxmanifest");
        
        String[] uapCapabilities = new String[]{
            "musicLibrary",
            "picturesLibrary",
            "videosLibrary",
            "removableStorage",
            "appointments",
            "contacts",
            "phoneCall",
            "userAccountInformation",
            "voipCall",
            "objects3D",
            "chat"
            
            
        };
        
        HashSet<String> uapCapabilitiesSet = new HashSet<String>(Arrays.asList(uapCapabilities));
        
        String[] capabilities = new String[]{
            "codeGeneration",
            "allJoyn",
            "privateNetworkClientServer",
            "internetClient",
            "internetClientServer",
            "phoneCallHistoryPublic",
            "recordedCallsFolder"
            
        };
        
        HashSet<String> capabilitiesSet = new HashSet<String>(Arrays.asList(capabilities));
        
        String[] iotCapabilities = new String[] {
            "lowLevelDevices",
            "systemManagement"
            
        };
        
        HashSet<String> iotCapabilitiesSet = new HashSet<String>(Arrays.asList(iotCapabilities));
        
        String[] deviceCapabilities = {
            "location",
            "microphone",
            "proximity",
            "webcam",
            "usb",
            "humaninterfacedevice",
            "pointOfService",
            "bluetooth",
            "wiFiControl",
            "radios",
            "optical",
            "activity"
        };
        
        
        HashSet<String> deviceCapabilitiesSet = new HashSet<String>(Arrays.asList(deviceCapabilities));
        
        Set<String> enabledCapabilitiesSet = new HashSet<String>();
        for (String s : new String[]{"musicLibrary", "picturesLibrary", "videosLibrary", "internetClient"}) {
            enabledCapabilitiesSet.add(s);
        }
        for (String s : p("uwp.capabilities", "").split(",")) {
            s = s.trim();
            enabledCapabilitiesSet.add(s);
        }
        for (String s : p("uwp.capabilities", "").split(",")) {
            s = s.trim();
            enabledCapabilitiesSet.remove(s);
        }
        
        StringBuilder csb = new StringBuilder();
        for (String s : enabledCapabilitiesSet) {
            if (uapCapabilitiesSet.contains(s)) {
                csb.append("<uap:Capability Name=\"").append(s).append("\"/>");
            } else if (deviceCapabilitiesSet.contains(s)) {
                csb.append("<DeviceCapability Name=\"").append(s).append("\"/>");
            } else if (capabilitiesSet.contains(s)) {
                 csb.append("<Capability Name=\"").append(s).append("\"/>");
            } else if (iotCapabilitiesSet.contains(s)) {
                csb.append("<iot:Capability Name=\"").append(s).append("\"/>");
            } else {
                throw new BuildException("Capabilitiy "+s+" is not currently supported.");
            }
        }
        
        
        String buildVersion = p("codename1.arg.uwp.build.version", "0.0");
        String[] buildVersionParts = buildVersion.split("\\.");
        buildVersion = (Integer.parseInt(buildVersionParts[0])+1) + "." + buildVersionParts[1];
        updateBuildVersion(buildVersion);
        System.out.println("About to do appxmanifest replacements");
        String[] appxManifestReplacements = new String[] {
            "<DisplayName>.*</DisplayName>", 
            "<DisplayName>"+p("codename1.displayName", null)+"</DisplayName>",
            
            "<PublisherDisplayName>.*</PublisherDisplayName>", 
            "<PublisherDisplayName>"+p("codename1.vendor", "Codename One")+"</PublisherDisplayName>",
            
            "<uap:VisualElements DisplayName=\"[^\"]*\"", 
            "<uap:VisualElements DisplayName=\""+p("codename1.displayName", null)+"\"",
            
            "<Identity Name=\"[^\"]*\"", 
            "<Identity Name=\""+p("codename1.arg.uwp.appid", null)+"\"",
            
            "<mp:PhoneIdentity PhoneProductId=\"[^\"]*\"", 
            "<mp:PhoneIdentity PhoneProductId=\""+uuid(p("codename1.arg.uwp.appid", ""))+"\"",
            
            "Publisher=\"CN=[^\"]*\"", 
            "Publisher=\"CN="+certCN+"\"",
            
            "(<Identity [^>]*Version=\")([^\"]+)",
            "$1"+p("codename1.version", "1.0")+"."+buildVersion,
            
            "<Capabilities>.*</Capabilities>",
            "<Capabilities>"+csb.toString()+"</Capabilities>"
                
               
        };
        
        try {
            replaceInFile(appxManifest, appxManifestReplacements);
        } catch (IOException ex) {
            ex.printStackTrace();
            throw new BuildException("Failed to update appxmanifest file.", ex);
        }
        
        log("Updating Package.StoreAssociation.xml");
        File storeAssocXml = new File(uwpAppDir, "Package.StoreAssociation.xml");
        String[] storeAssocReplacements = new String[] {
            "<Publisher>[^<]+?</Publisher>", 
            "<Publisher>CN="+certCN+"</Publisher>",
            
            "<PublisherDisplayName>.*?</PublisherDisplayName>", 
            "<PublisherDisplayName>"+p("codename1.vendor", "Codename One")+"</PublisherDisplayName>",
            
            "<MainPackageIdentityName>.*?</MainPackageIdentityName>",
            "<MainPackageIdentityName>"+p("codename1.arg.uwp.appid", null)+"</MainPackageIdentityName>",
            
            "<ReservedName>.*?</ReservedName>",
            "<ReservedName>"+p("codename1.displayName", null)+"</ReservedName>"
        };
        
        try {
            replaceInFile(storeAssocXml, storeAssocReplacements);
        } catch (IOException ex) {
            ex.printStackTrace();
            throw new BuildException("Failed to update Package.StoreAssociation.xml file.", ex);
        }
        
        log("Copying icon");
        try {
            File resourcesDir = new File(getProject().getBaseDir(), "resources" + File.separator + "uwp");
            File srcAssetsDir = new File(resourcesDir, "Assets");
            File iconFile = new File(getProject().getBaseDir(), "icon.png");
            File storeLogoFile = new File(srcAssetsDir, "StoreLogo.png");
            File storeLogo44x44_24File = new File(srcAssetsDir, "Square44x44Logo.targetsize-24_altform-unplated.png");
            File storeLogo44x44_scale200File = new File(srcAssetsDir, "Square44x44Logo.scale-200.png");
            File storeLogo150x150_scale200File = new File(srcAssetsDir, "Square150x150Logo.png");
            File lockScreenLogo_scale200File = new File(srcAssetsDir, "LockScreenLogo.scale-200.png");
            File wide310x150LogoFile = new File(srcAssetsDir, "Wide310x150Logo.scale-200.png");
            File splashScreenFile = new File(srcAssetsDir, "SplashScreen.scale-200.png");
            
            BufferedImage storeLogo = ImageIO.read(storeLogoFile.exists() ? storeLogoFile : iconFile);
            BufferedImage storeLogo44x44_24 = storeLogo44x44_24File.exists() ? ImageIO.read(storeLogo44x44_24File) : storeLogo;
            BufferedImage storeLogo44x44_scale200 = storeLogo44x44_scale200File.exists() ? ImageIO.read(storeLogo44x44_scale200File) : storeLogo;
            BufferedImage storeLogo150x150_scale200 = storeLogo150x150_scale200File.exists() ? ImageIO.read(storeLogo150x150_scale200File) : storeLogo;
            BufferedImage lockScreenLogo_scale200 = lockScreenLogo_scale200File.exists() ? ImageIO.read(lockScreenLogo_scale200File) : storeLogo;
            
            File assetsDir = new File(uwpAppDir, "Assets");
            
            createIcon(storeLogo, 50, 50, new File(assetsDir, "StoreLogo.png"));
            createIcon(storeLogo44x44_24, 24, 24, new File(assetsDir, "Square44x44Logo.targetsize-24_altform-unplated.png"));
            createIcon(storeLogo44x44_scale200, 88, 88, new File(assetsDir, "Square44x44Logo.scale-200.png"));
            createIcon(storeLogo150x150_scale200, 300, 300, new File(assetsDir, "Square150x150Logo.scale-200.png"));
            createIcon(lockScreenLogo_scale200, 48, 48, new File(assetsDir, "LockScreenLogo.scale-200.png"));
            
            BufferedImage wideLogo = wide310x150LogoFile.exists() ? ImageIO.read(wide310x150LogoFile) : storeLogo;
            wideLogo = fitImage(wideLogo, 620, 300, 25);
            createIcon(wideLogo, 620, 300, new File(assetsDir, "Wide310x150Logo.scale-200.png"));
            
            BufferedImage splashScreen = splashScreenFile.exists() ? ImageIO.read(splashScreenFile) : storeLogo;
            splashScreen = fitImage(splashScreen, 1240, 600, 50);
            createIcon(splashScreen, 1240, 600, new File(assetsDir, "SplashScreen.scale-200.png" ));
            
            
        } catch (IOException ex) {
            Logger.getLogger(GenerateVS2015Project.class.getName()).log(Level.SEVERE, null, ex);
            throw new BuildException("Failed to read project icon", ex);
        }
        //copy(new File(getProject().getBaseDir(), "icon.png"), new File(uwpAppDir, "Assets" + File.separator + "StoreLogo.png"));
        
        super.execute(); 
    }
    
    private String uuid(String seed) {
        try {
            return UUID.nameUUIDFromBytes(seed.getBytes("UTF-8")).toString();
        } catch (Exception ex) {
            return "";
        }
    }
    
    private String p(String propertyName, String defaultValue) {
        String out = getProject().getProperty(propertyName);
        return out == null ? defaultValue : out;
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
        copy(src, dest, false);
    }
    
    private void copy(File src, File dest, boolean overwrite) {
        Copy cp = (Copy)getProject().createTask("copy");
        cp.setFile(src);
        cp.setTofile(dest);
        cp.setOverwrite(overwrite);
        cp.execute();
    }
    
    public void replaceInFile(File sourceFile, String... replacements) throws IOException {
        
        DataInputStream dis = new DataInputStream(new FileInputStream(sourceFile));
        byte[] data = new byte[(int) sourceFile.length()];
        dis.readFully(data);
        dis.close();
        FileWriter fios = new FileWriter(sourceFile);
        String str = new String(data);
        for (int i=0; i<replacements.length; i+=2) {
            System.out.println("Replacing "+replacements[i]+" with "+replacements[i+1]);
            str = str.replaceFirst(replacements[i], replacements[i+1]);
        }
        fios.write(str);
        fios.close();
    }
    
    public void replaceInFileLiteral(File sourceFile, String... replacements) throws IOException {
        
        DataInputStream dis = new DataInputStream(new FileInputStream(sourceFile));
        byte[] data = new byte[(int) sourceFile.length()];
        dis.readFully(data);
        dis.close();
        FileWriter fios = new FileWriter(sourceFile);
        String str = new String(data);
        for (int i=0; i<replacements.length; i+=2) {
            str = str.replace(replacements[i], replacements[i+1]);
        }
        fios.write(str);
        fios.close();
    }

    public void replaceAllInFile(File sourceFile, String... replacements) throws IOException {
        DataInputStream dis = new DataInputStream(new FileInputStream(sourceFile));
        byte[] data = new byte[(int) sourceFile.length()];
        dis.readFully(data);
        dis.close();
        FileWriter fios = new FileWriter(sourceFile);
        String str = new String(data);
        for (int i=0; i<replacements.length; i+=2) {
            str = str.replaceAll(replacements[i], replacements[i+1]);
        }
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
            
            // To work around a bug in the DotNet native toolchain we need to convert 
            // all java classes so that synchronized methods don't contain try/catch
            // blocks.
            
            log("Preprocessing "+combinedJar.getAbsolutePath()+" to work around DotNetNative Toolchain bugs...");
            Java preprocess = (Java)getProject().createTask("java");
            preprocess.setClassname("com.codename1.tools.ikvm.IKVMClassPreprocessor");
            Variable v = new Variable();
            v.setKey("preprocessor.class.path");
            //StringBuilder cpBuilder = new StringBuilder();
            List<String> cpParts = new ArrayList<String>();
            for (File cpPart : inputs) {
                cpParts.add(cpPart.getAbsolutePath());
            }
            String processorCPath = String.join(File.pathSeparator, cpParts);
            v.setValue(processorCPath);
            
            preprocess.addSysproperty(v);
            Variable verifyProp = new Variable();
            verifyProp.setKey("verify");
            verifyProp.setValue("true");
            preprocess.addSysproperty(verifyProp);
            Path preprocessJarPath = new Path(getProject(), new File(ikvmDir, "IKVMClassPreprocessor" + File.separator + "bin" + File.separator + "IKVMClassPreprocessor.jar").getAbsolutePath());
            preprocess.setClasspath(preprocessJarPath);
            preprocess.setArgs(combinedJar.getAbsolutePath());
            preprocess.setFailonerror(true);
            preprocess.setFork(false);
            preprocess.execute();
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

    
    private void requireProperties(String... pnames) {
        for (String p : pnames) {
            if (p(p, null) == null) {
                throw new BuildException("Property "+p+" is required.");
            }
        }
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
                original.close();

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
    
    
    private static class Cert {
        String cn;
        String hash;
    }
    
    private Cert findCertCN(File pfxFile) {
        //File pfxFile = new File(uwpAppDir, "UWPApp_StoreKey.pfx");
        ExecTask certUtilExec = (ExecTask)getProject().createTask("exec");
        certUtilExec.setExecutable("certutil");
        certUtilExec.createArg().setValue("-dump");
        certUtilExec.createArg().setValue(pfxFile.getAbsolutePath());
        certUtilExec.setOutputproperty("cert.contents");
        certUtilExec.execute();
        String certContents = getProject().getProperty("cert.contents");
        if (certContents == null) {
            log("No contents found in cert "+pfxFile);
            return null;
        } else {
            log("Cert Contents: "+certContents);
        }
        Cert cert = new Cert();
        
        Pattern p =Pattern.compile("(?m)^Subject: CN=(.*)$");
        Matcher m = p.matcher(certContents);
        if (m.find()) {
            cert.cn = m.group(1);
        }
        
        p =Pattern.compile("(?m)^Cert Hash\\(sha1\\):(.*)$");
        m = p.matcher(certContents);
        if (m.find()) {
            cert.hash = m.group(1).replaceAll("[^0-9a-f]", "");
        }
        return cert;
    }
    
    private void updateBuildVersion(String version) {
        PropertyFile pf = (PropertyFile)getProject().createTask("propertyfile");
        PropertyFile.Entry e = pf.createEntry();
        e.setKey("codename1.arg.uwp.build.version");
        e.setValue(version);
        pf.setFile(new File(getProject().getBaseDir(), "codenameone_settings.properties"));
        pf.execute();
    }
    
    private void createIcon(BufferedImage icon, int w, int h, File destFile) throws IOException {
        //BufferedImage icon = ImageIO.read(new File(getProject().getBaseDir(), "icon.png"));
        Image icon50 = icon.getScaledInstance(w, h, Image.SCALE_SMOOTH);
        BufferedImage bicon50 = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics imgG = bicon50.getGraphics();
        imgG.drawImage(icon50, 0, 0, null);
        imgG.dispose();
        ImageIO.write(bicon50, "png", destFile);
    }
    
    private BufferedImage fitImage(BufferedImage img, int targetWidth, int targetHeight, int padding) throws IOException {
        int innerWidth = targetWidth - 2 * padding;
        int innerHeight = targetHeight - 2 * padding;
        
        if (img.getWidth() > innerWidth-padding || img.getHeight() > innerHeight) {
            int w = img.getWidth();
            int h = img.getHeight();
            if (w > innerWidth) {
                w = 620;
                h = (int)(img.getHeight() * innerWidth / (double)img.getWidth());
            }
            if (h > innerWidth) {
                w = (int)(w * innerHeight / (double)h);
                h = innerHeight;
            }
            Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
            BufferedImage tmpImg = new BufferedImage(innerWidth, innerHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics g = tmpImg.getGraphics();
            g.drawImage(scaled, (targetWidth - w)/2, (targetHeight-h)/2, null);
            g.dispose();
            img = tmpImg;

        } else {
            BufferedImage tmpImg = new BufferedImage(620, 300, BufferedImage.TYPE_INT_ARGB);
            Graphics g= tmpImg.getGraphics();
            g.drawImage(img, (targetWidth-img.getWidth())/2, (targetWidth-img.getHeight())/2, null);
            g.dispose();
            img = tmpImg;
        }
        return img;
    }
    
    
    private String createList(String included, String excluded) {
        String[] includedArr = included.split(",");
        String[] excludedArr = excluded.split(",");
        String[] resArr = createList(includedArr, excludedArr);
        StringBuilder sb = new StringBuilder();
        for (String s : resArr) {
            sb.append(s).append(",");
        }
        if (sb.length() > 0) {
            return sb.substring(0, sb.length()-1);
        } else {
            return "";
        }
    }
    
    private String[] createList(String[] included, String[] excluded) {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i< included.length; i++) {
            included[i] = included[i].trim();
        }
        for (int i=0; i < excluded.length; i++) {
            excluded[i] = excluded[i].trim();
        }
        
        List<String> excludedList = Arrays.asList(excluded);
        for (String s : included) {
            if (!excludedList.contains(s)) {
                out.add(s);
            }
        }
        return out.toArray(new String[out.size()]);
    }
}
