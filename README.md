#Codename One UWP Port

This project allows you to build Codename One projects as a Universal Windows Platform app.

## Installation Instructions

**Step 1**  Check out The Codename One Git Repo

~~~~
$ git clone https://github.com/codenameone/CodenameOne.git
~~~~

**Step 2** Check out CN1UWPPort.  (Preferably into the same parent directory that contains the CodenameOne repo).

~~~~
$ git clone https://github.com/shannah/CN1UWPPort.git
~~~~

**Step 3** Run `ant setup`

This step will update an existing Codename One Netbeans project to add a build script and build target for uwp.

~~~~
$ cd CN1UWPPort
$ ant setup -Dcn1.home=..\CodenameOne -Dcodenameone.project.dir=TestProject
~~~~

In the above example, we used two arguments:

1. `cn1.home` => The path to the Codename One github repo that we checked out in step 1.
2. `codenameone.project.dir` => The path to a Codename One Netbeans project that we wish to add UWP build support to.  In this case we specified `TestProject` as this project is included in the repo as a sample.

## Build Instructions

Once a project has been setup to build to UWP, it will include a `build-win.xml` file in its root directory.  This ANT build script includes 
a target named `create-uwp-project` which will generate a Visual Studio Project.

To continue the above exmaple where our TestProject has been setup to build UWP apps:

~~~~
$ cd TestProject
$ ant -f build-win.xml create-uwp-project
~~~~

This will compile the app, and generate a Visual Studio Project that can be used to build a UWP application.  This Visual Studio project will be 
located in a directory named "UWPProject" inside TestProject.

### Updating Exising UWPProject

The first time you run the `create-uwp-project` target, it will generate the UWPProject.  Each time thereafter it will just update the project.  It won't delete it.

 
