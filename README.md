#Codename One UWP Port (Offline Build Tool)

This project allows you to build Codename One projects as a Universal Windows Platform app.

## Requirements

In order to build UWP Apps, you will require the following:

1. JDK8
2. ANT 1.8 or higher
3. Microsoft Visual Studio 2015

## Installation Instructions

**Step 1**  Check out The Codename One Git Repo

~~~~
> git clone https://github.com/codenameone/CodenameOne.git
~~~~

**Step 2** Check out CN1UWPPort.  (Preferably into the same parent directory that contains the CodenameOne repo).

~~~~
> git clone https://github.com/shannah/CN1UWPPort.git
~~~~

**Step 3** Run `ant setup`

This step will update an existing Codename One Netbeans project to add a build script and build target for uwp.

~~~~
> cd CN1UWPPort
> ant setup -Dcodenameone.project.dir=TestProject
~~~~

In the above example, we used one argument:

* `codenameone.project.dir` => The path to a Codename One Netbeans project that we wish to add UWP build support to.  In this case we specified `TestProject` as this project is included in the repo as a sample.

Additionally, if your CodenameOne directory is located in a different location than `..\CodenameOne` relative to the CN1UWPPort directory, you can add the `cn1.home` argument. E.g. `ant setup -Dcodenameone.project.dir=TestProject -Dcn1.home=..\cn1` if it were located at `..\cn1`.

## Build Instructions

Once a project has been setup to build to UWP, it will include a `build-win.xml` file in its root directory.  This ANT build script includes 
a target named `create-uwp-project` which will generate a Visual Studio Project.

To continue the above exmaple where our TestProject has been setup to build UWP apps:

~~~~
> cd TestProject
> ant -f build-win.xml create-uwp-project
~~~~

This will compile the app, and generate a Visual Studio Project that can be used to build a UWP application.  This Visual Studio project will be 
located in a directory named "UWPProject" inside TestProject.

### Updating Exising UWPProject

The first time you run the `create-uwp-project` target, it will generate the UWPProject.  Each time thereafter it will just update the project.  It won't delete it.

## Building IKVM From Source

This project includes a fork of IKVM that has been modified to run on UWP.  The source for this fork is maintained in a [separate repository](https://github.com/shannah/cn1-ikvm-uwp) and also uses a [fork of the OpenJDK](https://github.com/shannah/cn1-ikvm-openjdk-8-b132), but will be automatically retrieved the first time you try run the build script for this project.  If you need to make changes to IKVM itself, this project includes build targets to do that.  

**Important** There are two ant targets pertaining to building IKVM from source:

1. `compile-ikvm` - Compiles IKVM and OpenJDK from source.  **Only use this if you've made changes outside the ikvm/runtime directory.**  This target takes about 20 minutes as it needs to compile OpenJDK and IKVM.
2. `compile-ikvm-runtime` - Compiles just the IKVM runtime.  This should only take a few seconds.  Prefer this target if you've only made changes inside the ikvm/runtime directory.

**Build Dependencies**

Building IKVM from source requires that you have Nant installed in your path.  You will also require the .Net framework.  For full dependencies, see the general IKVM build instructions on the IKVM website.

**Long Path Names**:

One **VERY** annoying thing about windows is that it only supports paths of 260 characters or less.  If you check out your CN1UWPPort to deep in your files system, then you may run into Pathname too long errors.  The solution for this is to check out the CN1UWPPort directory into a shallow directory.  E.g. perhaps only one or two nested directories below your user home directory.

###Updating the Visual Studio Project Template

If you are building from source, then you'll need to copy some of the DLL files from the IKVM directory into your visual studio project template.  Use the `update-project-template-dlls` target for this:

e.g.

~~~~
> ant update-project-template-dlls
~~~~

**NOTE:** This requires that the `cn1.home` property is set - or that CN1UWPPort has been checked out in the same directory as CodenameOne.

This will copy a few of the IKVM dll files into your `$cn1.home/Ports/UWP/VSProjectTemplate/lib` directory so that they will be included in subsequent projects (resulting from `create-uwp-project`).

 
