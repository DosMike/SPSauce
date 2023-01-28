#!/bin/bash
# check if java is installed
if ! which java >/dev/null ; then
  echo 'Could not find Java - SPSauce requires Java 8+'
  echo 'Please install an OpenJDK package (openjdk-8-jre)'
  echo ''
  exit 1
fi

# check if git is installed - depedency clone will try to use system git
if ! which git >/dev/null ; then
  echo 'Could not find git - Some dependencies might fail!'
  echo 'Please install the git package (git)'
  echo ''
fi

# search for the jar file relative to this script
scriptDir=$PWD
cd ${0%/*}
fname="$PWD/spsauce/"$(ls -r1A spsauce | grep '^SPSauce-.*\.jar$' | head -n1)
cd $scriptDir

install() {
  echo Creating MimeType text/x-spsauce
  mkdir -p ~/.local/share/mime/packages/ || exit 1
  cat << EOF > ~/.local/share/mime/packages/spsauce.xml
<?xml version="1.0" encoding="UTF-8"?>
<mime-info xmlns="http://www.freedesktop.org/standards/shared-mime-info">
<mime-type type="text/x-spsauce">
<comment>SPSauce</comment>
<glob pattern="*.sauce"/>
</mime-type>
</mime-info>
EOF
  update-mime-database ~/.local/share/mime
  mkdir -p ~/.local/share/applications/ || exit 1
  echo Creating Desktop Application spsauce.desktop
  cat << EOF > ~/.local/share/applications/spsauce.desktop
[Desktop Entry]
Type=Application
Version=1.0
Name=SPSauce
NoDisplay=true
Comment=Compile Script for SP
Exec=/bin/bash -c "\\"$(realpath -e "$BASH_SOURCE")\\" \\\$1" bash "\$f"
Terminal=true
MimeType=text/x-spsauce
EOF
  echo Associating spsauce.desktop to text/x-spsauce
  xdg-mime default spsauce.desktop text/x-spsauce
  echo DONE
}
uninstall() {
  if [[ -f ~/.local/share/mime/packages/spsauce.xml ]]; then
	echo Removing MimeType text/x-spsauce
    rm -f ~/.local/share/mime/packages/spsauce.xml
    update-mime-database ~/.local/share/mime
  fi
  if [[ -f ~/.local/share/applications/spsauce.desktop ]]; then
	echo Removing Desktop Application spsauce.desktop
    rm -f ~/.local/share/applications/spsauce.desktop
  fi
  echo DONE
}

# run the jar file from the spsauce directory
if [[ -f $fname ]]; then
  if [[ "$1" == "--install" ]]; then install
  elif [[ "$1" == "--uninstall" ]]; then uninstall
  else java -jar $fname $@
  fi
else
  echo 'Could not find any SPSauce jar binary'
fi
