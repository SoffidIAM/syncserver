#!/bin/bash

# Test environment variables
function configuremain {
	if [[ "$DB_URL" == "" ]]
	then
		if [[ "$MARIADB_HOST" == "" ]]
		then
			echo "Missing \$MARIADB_HOST or \$DB_URL environment variable. Exiting"
			exit 1
		fi
		MARIADB_PORT=${MARIADB_PORT:-3306}
		if [[ "$MARIADB_DB" == "" ]]
		then
			echo "Missing \$DB_URL environment variable. Exiting"
			exit 1
		fi
		DB_URL="jdbc:mysql://$MARIADB_HOST:$MARIADB_PORT/$MARIADB_DB"
	fi
	
	if [[ "$MARIADB_USER" == "" && "$DB_USER" == "" ]]
	then
	    echo "Missing \$DB_USER environment variable. Exiting"
	    exit 1
	fi
	
	if [[ "$MARIADB_PASS" == "" && "$DB_PASSWORD" == "" ]]
	then
	    echo "Missing \$DB_PASSWORD environment variable. Exiting"
	    exit 1
	fi

	if [[ "$SOFFID_HOSTNAME" == "" ]]
	then
		SOFFID_HOSTNAME=$(hostname)
	fi
    
	if [[ "$SOFFID_PORT" == "" ]]
	then
		SOFFID_PORT=760
	fi
    
    echo "Configuring as main server"
	/opt/soffid/iam-sync/bin/configure -main -hostname "$SOFFID_HOSTNAME" -port "$SOFFID_PORT" -dbuser "${DB_USER:-$MARIADB_USER}" -dbpass "${DB_PASSWORD:-$MARIADB_PASS}" -dburl "$DB_URL" && 
	touch /opt/soffid/iam-sync/conf/configured &&
	echo "broadcast_listen=true" >>/opt/soffid/iam-sync/conf/seycon.properties
	
}


# Test environment variables
function configureproxy {
	if [[ "$SOFFID_SERVER" == "" ]]
	then
	    echo "Missing \$SOFFID_SERVER environment variable. Exiting"
	    exit 1
	fi
	if [[ "$SOFFID_USER" == "" ]]
	then
	    echo "Missing \$SOFFID_USER environment variable. Exiting"
	    exit 1
	fi
	
	if [[ "$SOFFID_PASS" == "" ]]
	then
	    echo "Missing \$SOFFID_PASS environment variable. Exiting"
	    exit 1
	fi

	if [[ "$SOFFID_HOSTNAME" == "" ]]
	then
		SOFFID_HOSTNAME=$(hostname)
	fi
    
	if [[ "$SOFFID_PORT" == "" ]]
	then
		SOFFID_PORT=760
	fi
    
	if [[ "$SOFFID_TENANT" == "" ]]
	then
		SOFFID_TENANT=master
	fi
    
    echo "Configuring as secondary or proxy server"
	/opt/soffid/iam-sync/bin/configure -hostname "$SOFFID_HOSTNAME" -port "$SOFFID_PORT" -user "$SOFFID_USER" -pass "$SOFFID_PASS" -server "$SOFFID_SERVER" -tenant "$SOFFID_TENANT"  && 
	touch /opt/soffid/iam-sync/conf/configured &&
	echo "broadcast_listen=true" >>/opt/soffid/iam-sync/conf/seycon.properties
}

# Configure remote server
function configureremote {
	if [[ "$SOFFID_SERVER" == "" ]]
	then
	    echo "Missing \$SOFFID_SERVER environment variable. Exiting"
	    exit 1
	fi
	if [[ "$SOFFID_USER" == "" ]]
	then
	    echo "Missing \$SOFFID_USER environment variable. Exiting"
	    exit 1
	fi
	
	if [[ "$SOFFID_PASS" == "" ]]
	then
	    echo "Missing \$SOFFID_PASS environment variable. Exiting"
	    exit 1
	fi

	if [[ "$SOFFID_HOSTNAME" == "" ]]
	then
		SOFFID_HOSTNAME=$(hostname)
	fi
    
	if [[ "$SOFFID_TENANT" == "" ]]
	then
		SOFFID_TENANT=master
	fi
    
    echo "Configuring as remote server"
	echo "broadcast_listen=true" >>/opt/soffid/iam-sync/conf/seycon.properties
	/opt/soffid/iam-sync/bin/configure -remote -hostname "$SOFFID_HOSTNAME" -user "$SOFFID_USER" -pass "$SOFFID_PASS" -server "$SOFFID_SERVER" -tenant "$SOFFID_TENANT"  && 
	touch /opt/soffid/iam-sync/conf/configured 
}

function configure {
	if [[ "$SOFFID_MAIN" == "yes" ]]
	then
	    configuremain || (sleep 6000000 ; exit 1)
	elif [[ "$SOFFID_MAIN" == "no" ]]
	then
		if [[ "$SOFFID_REMOTE" == "yes" ]]
		then
		    configureremote || exit 1
		else
		    configureproxy || exit 1
		fi
	else
	    echo "Missing \$SOFFID_MAIN environment variable."
	    echo "Use SOFFID_MAIN=yes for first server"
	    echo "Use SOFFID_MAIN=no for any other server"
	    echo "Exiting"
	    exit 1
	fi
	true
}

function loadconfig {
	java=java
	if [ ! -z "$JAVA_HOME" ]
	then
	  java="$JAVA_HOME/bin/java"
	elif [ ! -z "$JRE_HOME" ] 
	then
	  java="$JRE_HOME/bin/java"
	else
	  java=java
	fi
	
	$java -cp "/opt/soffid/iam-sync/bin/bootstrap.jar" com.soffid.iam.sync.bootstrap.KubernetesLoader
}

loadconfig

if [[ ! -f /opt/soffid/iam-sync/conf/configured ]]
then
   configure || exit 1
fi

cp /opt/soffid/iam-sync/conf/* /tmp

exec /opt/soffid/iam-sync/bin/soffid-sync
