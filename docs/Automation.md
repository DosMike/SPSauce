Automatic Artifacts
=====

While i think doing fully automatic releases might be a bit much, you're free to do so. But in this example I'll only pack up the compiled plugin with necessary files like translations and gamedata. The Archive will then be attached to the release.

First, let's write the sp.sauce script to compile and pack the plugin:
```spsauce
# specify the sourcemod version to use
sourcemod 1.10

# compile the plugin
spcomp plugin.sp -oplugin.smx

# get the plugin version (this is usually a define like this)
set %{PLUGIN_VERSION} as \1 from plugin.sp ^#define\s+PLUGIN_VERSION\s+"([^"]+)"

# authenticate against github, the token is passed as argument
auth github ${GITHUB_TOKEN}

# packs files in a zip, folder names will be recursed automatically
# intendation is optional but makes things more readable
with files
 plugin.smx
 translations
 gamedata
 scripting/include
:release zip Plugin_%{PLUGIN_VERSION}.zip auto
# the auto keyword in the line above tells the plugin to restructure
# the archive, ready to be dropped into the servers mod folder.

# patch meta files for `Updater` and `Plugin Update Checker`
with files
 plugin.smx
 translations
 gamedata
 plugins
:release updater www/static/updater.cfg %{PLUGIN_VERSION}
pucpatch www/static/versions.txt : version_convar %{PLUGIN_VERSION}

# attach the zip file to the release that triggered this action
with files
 Plugin_%{PLUGIN_VERSION}.zip
:release github ${GITHUB_REPOSITORY} ${GITHUB_REF_NAME}
```

Now the GitHub Actions file:
```yaml
name: Plugin Release Archive
on:
  release:
    types: [published]
jobs:
  pack-plugin:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-java@v2
        with:
          distribution: 'adopt'
          java-version: '8'
      - name: SPSauce
        run: ./sps release.sauce --GITHUB_TOKEN ${{secrets.GITHUB_TOKEN}}
```