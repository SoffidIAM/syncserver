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
         echo "$2<Directory Id='$3.$f' Name='$f'>"
         dumpFiles "$f" "  $2" "$3.$f" "$4/$f" "$5"
         dump "$f" "  $2" "$3.$f" "$4/$f"
         echo "$2</Directory>"
     fi
   done
   cd ..
}

function dumpFiles {
   cd "$1"
   found=0
   local f=""
   for f in *
   do
     if  [[ -f "$f" && "$f" != "configure" && "$f" != soffid-sync && "$f" != cli && "$f" != env.sh ]]
     then
         if [ "$found" = 0 ]
         then
           found=1
		   echo "   <ComponentRef Id='$3_cmp'/>" >>$base/target/wix/feature.wxs
		   echo "$2<Component Id='$3_cmp' Guid='*'>"
         fi
         echo "$2  <File Name='$f' DiskId='1' Id='$3.$f' DefaultVersion='$5' Source='$4/$f'/>"
     fi
   done
   if [ "$found" = 1 ]
   then
	 echo "$2</Component>"
   fi
   cd ..
}

dir=$(dirname $0)
base=$(realpath "$dir/../../..")

echo "BASE DIR $base"

if [ ! -d "$base/target/wix" ]
then
   mkdir $base/target/wix
fi

cd $base/target/dist/opt/soffid/iam-sync
cat $base/target/wix/head.wxs  >$base/target/wix/syncserver.wxs
echo >$base/target/wix/feature.wxs
dump . "" ROOT target/dist/opt/soffid/iam-sync "$1">>$base/target/wix/syncserver.wxs
cat $base/target/wix/middle.wxs  >>$base/target/wix/syncserver.wxs
cat $base/target/wix/feature.wxs  >>$base/target/wix/syncserver.wxs
cat $base/target/wix/tail.wxs >>$base/target/wix/syncserver.wxs

cd $base
wixl -a x64 $base/target/wix/syncserver.wxs
