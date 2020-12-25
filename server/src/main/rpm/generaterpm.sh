#!/bin/bash

function dump {
   cd "$1"
   local f=""
   for f in *
   do
     if [ "$f" = "." -o "$f" = ".." ]
     then
       true # Ignore
     elif [ -d "$f" ]
     then
         echo "%dir \"$2/$f\""
         dumpFiles "$f" "$2/$f"
         dump "$f" "$2/$f"
     fi
   done
   cd ..
}

function dumpFiles {
   cd "$1"
   local f=""
   for f in *
   do
     if  [ -f "$f" ]
     then
       echo "\"$2/$f\""
     fi
   done
   cd ..
}

dir=$(dirname $0)
base=$(realpath "$dir/../../..")

cd $base/target/rpm/dist

dump "" "" >>../soffid-iamsync.spec
cd $base

fakeroot rpmbuild --target=noarch --buildroot $base/target/rpm/dist -bb $base/target/rpm/soffid-iamsync.spec

