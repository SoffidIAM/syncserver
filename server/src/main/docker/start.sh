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
			echo "Missing \$MARIADB_DB environment variable. Exiting"
			exit 1
		fi
		DB_URL="jdbc:mysql://$MARIADB_HOST:$MARIADB_PORT/$MARIADB_DB"
	fi
	
	if [[ "$MARIADB_USER" == "" ]]
	then
	    echo "Missing \$MARIADB_USER environment variable. Exiting"
	    exit 1
	fi
	
	if [[ "$MARIADB_PASS" == "" ]]
	then
	    echo "Missing \$MARIADB_PASS environment variable. Exiting"
	    exit 1
	fi

	if [[ "$SOFFID_HOSTNAME" == "" ]]
	then
		SOFFID_HOSTNAME=$(hostname)
	fi
    
    echo "Configuring as main server"
	/opt/soffid/iam-sync/bin/configure -main -hostname "$SOFFID_HOSTNAME" -dbuser "$MARIADB_USER" -dbpass "$MARIADB_PASS" -dburl "$DB_URL" && touch /opt/soffid/iam-sync/conf/configured
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
    
	if [[ "$SOFFID_TENANT" == "" ]]
	then
		SOFFID_TENANT=master
	fi
    
    echo "Configuring as secondary or proxy server"
	/opt/soffid/iam-sync/bin/configure -hostname "$SOFFID_HOSTNAME" -user "$SOFFID_USER" -pass "$SOFFID_PASS" -server "$SOFFID_SERVER" -tenant "$SOFFID_TENANT"  && touch /opt/soffid/iam-sync/conf/configured
}


function configure {
	if [[ "$SOFFID_MAIN" == "yes" ]]
	then
	    configuremain || exit 1
	elif [[ "$SOFFID_MAIN" == "no" ]]
	then
	    configureproxy || exit 1
	else
	    echo "Missing \$SOFFID_MAIN environment variable."
	    echo "Use SOFFID_MAIN=yes for first server"
	    echo "Use SOFFID_MAIN=no for any other server"
	    echo "Exiting"
	    exit 1
	fi
	true
}

if [[ ! -f /opt/soffid/iam-sync/conf/configured ]]
then
   configure || exit 1
fi


 exec /opt/soffid/iam-sync/bin/standalone
