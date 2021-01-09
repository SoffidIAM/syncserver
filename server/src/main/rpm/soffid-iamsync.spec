Buildroot: /home/gbuades/soffid/console/install/target/soffid-iamsync
Name: soffid-iamsync
Version: ${parsedVersion.majorVersion}.${parsedVersion.minorVersion}.${parsedVersion.incrementalVersion}
Release: 1
Summary: Soffid IAM Sync
License: GPL
Distribution: Soffid IAM
Group: Administration/Netwokr

%define _rpmdir target
%define _rpmfilename %%{NAME}-%%{VERSION}-%%{RELEASE}.%%{ARCH}.rpm
%define _unpackaged_files_terminate_build 0

%post
#!/bin/sh
set -e
if ! getent group soffid >/dev/null 2>&1; then
    groupadd soffid
fi

if ! getent passwd soffid >/dev/null 2>&1; then
    adduser --system --gid soffid --no-create-home --home-dir /opt/soffid soffid
fi
chown -R soffid:soffid /opt/soffid/iam-sync

# This will only remove masks created by d-s-h on package removal.
/opt/soffid/iam-sync/bin/configure || true

systemctl enable 'soffid-iamsync.service' || true 	
systemctl start soffid-iamsync || true 

%preun
#!/bin/bash

systemctl stop 'soffid-iamsync.service' || true

systemctl disable 'soffid-iamsync.service' || true

%description



%files
