#define UNICODE
#define _UNICODE

#include <windows.h>
#include <ntsecapi.h>
#include <string.h>
#include <tchar.h>
#include <ErisADFilter.h>
#include <stdio.h>

/* Globals */  
SERVICE_STATUS          gSvcStatus;
SERVICE_STATUS_HANDLE   MyServiceStatusHandle;
HANDLE ghSvcStopEvent = NULL;
BOOL verbose = FALSE;

#define SERVICE_NAME L"ErisADConnector"

void fatalError ()
{
	   LPWSTR pstr;
	   DWORD dw = GetLastError ();
	   FormatMessageW(FORMAT_MESSAGE_ALLOCATE_BUFFER| FORMAT_MESSAGE_FROM_SYSTEM,
			   NULL,
			   dw,
			   0,
			   (LPWSTR)&pstr,
			   0,
			   NULL);
	   if (pstr == NULL)
			wprintf (L"Unknown error: %d\n",
					dw);
	   else
		   wprintf (L"Unable to send message: %s\n",
				pstr);
	   LocalFree(pstr);
	   ExitProcess(1);
}

VOID ReportSvcStatus( DWORD dwCurrentState,
                      DWORD dwWin32ExitCode,
                      DWORD dwWaitHint)
{
    static DWORD dwCheckPoint = 1;

    // Fill in the SERVICE_STATUS structure.

    gSvcStatus.dwCurrentState = dwCurrentState;
    gSvcStatus.dwWin32ExitCode = dwWin32ExitCode;
    gSvcStatus.dwWaitHint = dwWaitHint;

    if (dwCurrentState == SERVICE_START_PENDING)
        gSvcStatus.dwControlsAccepted = 0;
    else
    	gSvcStatus.dwControlsAccepted = SERVICE_ACCEPT_STOP;

    if ( (dwCurrentState == SERVICE_RUNNING) ||
           (dwCurrentState == SERVICE_STOPPED) )
        gSvcStatus.dwCheckPoint = 0;
    else
    	gSvcStatus.dwCheckPoint = dwCheckPoint++;

    // Report the status of the service to the SCM.
    SetServiceStatus( MyServiceStatusHandle, &gSvcStatus );
}

// Operaciones de control sobre el servicio 
VOID ErisServiceCtrlHandler ( IN  DWORD   Opcode)
{ 
    switch(Opcode) { 
    case SERVICE_CONTROL_STOP: 
        ReportSvcStatus(SERVICE_STOP_PENDING, NO_ERROR, 2000);

        // Signal the service to stop.

        SetEvent(ghSvcStopEvent);

        gSvcStatus.dwCurrentState = SERVICE_STOPPED;

		break;
    default:
        ReportSvcStatus(gSvcStatus.dwCurrentState, NO_ERROR, 0);
    	break;
 
    } 
 
    return; 
} 


void runService ()
{
	if (verbose)
		wprintf (L"Opening queue ...\n");
	DWORD lastConfigureHour;
	SYSTEMTIME now;
	GetLocalTime(&now);
	lastConfigureHour = now.wHour;
	reconfigureEris ();
	HANDLE f = openSpoolFile();
	struct PasswordChange pc;
    while(1)
    {
    	GetLocalTime(&now);
    	if (now.wHour != lastConfigureHour)
    	{
    		if (reconfigureEris())
    			lastConfigureHour = now.wHour;
    	}

        // Check whether to stop the service.
    	if (verbose)
    		wprintf (L"Working ...\n");
    	BOOL repeat = TRUE;
    	while (repeat) {
    		if ( !readPasswordChange(f, &pc, FALSE)) {
    			repeat = FALSE;
			} else if (sendMessage(&pc)) {
    			deletePasswordChange (f);
    			repeat =  TRUE;
    	    	if ( WaitForSingleObject(ghSvcStopEvent, 0) == WAIT_OBJECT_0) // Sin espera
    	    		return;
    		} else {
    			repeat = FALSE;
    		}
    		ZeroMemory (&pc, sizeof pc);
    	}
    	if (verbose)
    		wprintf (L"Waiting fore 1 minute...\n");
    	DWORD result = WaitForSingleObject(ghSvcStopEvent, 60000); // Un minuto de espera
    	if (result == WAIT_OBJECT_0)
    	{
			CloseHandle(f);
    		return;
    	}
    }

}
 
// Inicializaci�n del servicio 
void ErisServiceStart (DWORD   argc, LPTSTR  *argv)
{ 
    gSvcStatus.dwServiceType = SERVICE_WIN32_OWN_PROCESS;
    gSvcStatus.dwServiceSpecificExitCode = 0;

    // Report initial status to the SCM

    ReportSvcStatus( SERVICE_START_PENDING, NO_ERROR, 3000 );
 
    MyServiceStatusHandle = RegisterServiceCtrlHandler( 
                            NULL,
                            (LPHANDLER_FUNCTION)ErisServiceCtrlHandler);
 
    if (MyServiceStatusHandle == (SERVICE_STATUS_HANDLE)0) { 
		fatalError ();
    } 
 
 
 
    ghSvcStopEvent = CreateEventW(
                          NULL,    // default security attributes
                          TRUE,    // manual reset event
                          FALSE,   // not signaled
                          NULL);   // no name

    if ( ghSvcStopEvent == NULL)
    {
         ReportSvcStatus( SERVICE_STOPPED, NO_ERROR, 0 );
         return;
    }

    // Initialization complete - report running status
    ReportSvcStatus( SERVICE_RUNNING, NO_ERROR, 3000 );


    runService ();

    // Report running status when initialization is complete.

    ReportSvcStatus( SERVICE_STOPPED, NO_ERROR, 0 );
    return;
} 
 
// Registrar servicios
BOOL RegistrarServicio (HINSTANCE hInstance)
{
    SERVICE_TABLE_ENTRY   dispatchTable[2];
    dispatchTable[0].lpServiceName = (LPWSTR) L"ErisADConnector";
    dispatchTable[0].lpServiceProc = (LPSERVICE_MAIN_FUNCTION) ErisServiceStart;
    dispatchTable[1].lpServiceName = NULL;
    dispatchTable[1].lpServiceProc = NULL;

    if (!StartServiceCtrlDispatcher( dispatchTable)) {
		return FALSE;
    } else {
    	return TRUE;
    }
}

void installService(bool  showUsage)
{
    SC_HANDLE sch, schService;
    WCHAR achProgramName[MAX_PATH];
    GetModuleFileNameW (NULL, achProgramName, MAX_PATH-1);
    // Abrir base de datos de servicios
    sch = OpenSCManager(
			NULL, // Current Machine
			NULL, // Active Services
			SC_MANAGER_CREATE_SERVICE);
    if (sch == NULL)
		{
			fatalError ();
		}
    wprintf(L"Program Name=%s\n", achProgramName);
    // Crear el servicio
    schService = CreateServiceW (sch,
			SERVICE_NAME,
			L"Eris ActiveDirectory Connector",
			SERVICE_ALL_ACCESS,
			SERVICE_WIN32_OWN_PROCESS,
			SERVICE_AUTO_START	,
			SERVICE_ERROR_NORMAL,
			achProgramName,
			NULL,
			NULL,
			NULL,
			NULL,
			NULL);
    wprintf (L"Last error = %d\n", GetLastError());
    if (schService == NULL && GetLastError () != ERROR_DUPLICATE_SERVICE_NAME &&
    		GetLastError() != ERROR_SERVICE_EXISTS)
	{
		fatalError();
	}
    if (schService != NULL)
    {

		SERVICE_DESCRIPTIONW description;
		description.lpDescription = (LPWSTR) L"Synchronizes passwords with ERIS Server";
		ChangeServiceConfig2W (schService, SERVICE_CONFIG_DESCRIPTION, &description);
		CloseServiceHandle(schService);
		StartServiceW(schService, 0, NULL);
		CloseServiceHandle(sch);
    }
    InstallLSA();
    wprintf(L"Installed.\n");

}

void uninstallService()
{
    SC_HANDLE sch, schService;
    // Abrir base de datos de servicios
    sch = OpenSCManager(
			NULL, // Current Machine
			NULL, // Active Services
			SC_MANAGER_ALL_ACCESS);
    if (sch == NULL)
		{
			fatalError ();
		}
    // Localizar el servicio
    schService = OpenServiceW (sch,
			SERVICE_NAME,
			SERVICE_ALL_ACCESS);
    if (schService == NULL)
		{
	 		CloseServiceHandle (sch);
			fatalError ();
		}
    // Borrar el servicio
    SERVICE_STATUS status;
    ControlService(schService, SERVICE_CONTROL_STOP, &status);
    DeleteService(schService);
    CloseServiceHandle(schService);
    CloseServiceHandle(sch);
    UninstallLSA();
}

void runTest(WCHAR **argv)
{
    setQueueDebug (TRUE);
    verbose = TRUE;
    struct PasswordChange change;
    wcscpy(change.szUser, argv[2]);
    wcscpy(change.szPassword, argv[3]);
    if (sendMessage(&change))
		{
			wprintf (L"Message successfully sent\n");
			   ExitProcess(0);
		} else
		{
		   LPWSTR pstr;
		   DWORD dw = GetLastError ();
		   HANDLE h = openSpoolFile();
		   writePasswordChange(h, &change);
		   CloseHandle(h);
		   FormatMessageW(FORMAT_MESSAGE_ALLOCATE_BUFFER| FORMAT_MESSAGE_FROM_SYSTEM,
				   NULL,
				   dw,
				   0,
				   (LPWSTR)&pstr,
				   0,
				   NULL);
		   if (pstr == NULL)
				wprintf (L"Unknown error: %d\n",
						dw);
		   else
			   wprintf (L"Unable to send message: %s\n",
					pstr);
		   LocalFree(pstr);
		   ExitProcess(1);
		}
    ZeroMemory (&change, sizeof change);
}

void dumpQueue()
{
    setQueueDebug (TRUE);
    verbose = TRUE;
    HANDLE h = openSpoolFile();
    struct PasswordChange change;
    while (readPasswordChange(h, &change, TRUE))
		{
			wprintf (L"User: %s Password: %s\n", change.szUser, change.szPassword);
		}
    CloseHandle(h);
}


// Función principal
extern "C"
int WINAPI WinMain(
    HINSTANCE hInstance,	// handle to current instance
    HINSTANCE hPrevInstance,	// handle to previous instance
    LPSTR lpszCmdLine,	// pointer to command line
    int nCmdShow 	// show state of window
	)
{
	BOOL bShowUsage = TRUE;
	int argc;
	WCHAR **argv;

	LPWSTR lpCmdLine = GetCommandLineW();
	argv = CommandLineToArgvW(lpCmdLine,  &argc);
	if ( argc == 2 && _wcsicmp (argv[1], L"INSTALL") == 0 )
	{
	    bShowUsage = FALSE;
	    installService(bShowUsage);
	}
	else if ( argc == 2 &&  _tcsicmp (argv[1], _T("UNINSTALL")) == 0)
	{
	    bShowUsage = FALSE;
	    uninstallService();
	} else if ( argc == 4 &&
				_tcsicmp (argv[1], _T("TEST")) == 0 )
	{
		bShowUsage = FALSE;
	    runTest(argv);
	} else if ( argc == 4 &&				//Canviat argc == 3 per argc == 4
				_tcsicmp (argv[1], _T("CONFIGURE")) == 0 )
	{
		bShowUsage = FALSE;
		setQueueDebug(TRUE);
	    if (!configureEris(argv[2], TRUE, argv[3]))	//Afegit el tercer argument argv[3]
	    {
	    	printf ("Error configurando el sistema");
	    	ExitProcess(1);
	    }
	} else if ( argc == 2 &&
				_wcsicmp(argv[1], L"RUN") == 0 )
	{
		bShowUsage = FALSE;
	    ghSvcStopEvent = CreateEventW(
	                          NULL,    // default security attributes
	                          TRUE,    // manual reset event
	                          FALSE,   // not signaled
	                          NULL);   // no name
		setQueueDebug (TRUE);
		verbose = TRUE;
		runService();
	} else if ( argc == 2 &&
				_wcsicmp(argv[1], L"DUMP") == 0 )
	{
		bShowUsage = FALSE;
	    dumpQueue();
	}
	else if (argc == 1) // Ejecutar como servicio
	{
		wprintf (L"Starting server...\n");
		if (RegistrarServicio (hInstance))
			bShowUsage = FALSE;
	}

	if (bShowUsage)
	{
	    WCHAR achProgramName[MAX_PATH];
	    GetModuleFileNameW (NULL, achProgramName, MAX_PATH-1);
		wprintf (L"usage: %s INSTALL\n"
		          L"       %s CONFIGURE <url> <agentName>\n"
		          L"       %s TEST <user> <password>\n"
		          L"       %s RUN\n"
		          L"       %s DUMP\n"
				  L"       %s UNINSTALL <ServiceName>\n",
				  achProgramName,
				  achProgramName,
				  achProgramName,
				  achProgramName,
				  achProgramName,
				  achProgramName);
		ExitProcess(1);
	}
	else
		ExitProcess(0);
}

