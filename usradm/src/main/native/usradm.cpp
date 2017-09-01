// UsrAdm

#define UNICODE
#define _UNICODE

#include <windows.h>
#include <wchar.h>
#include <string.h>
#include <tchar.h>
#include <stdio.h>
#include <lmaccess.h>
#include <lmapibuf.h>
#include <lmerr.h>
#include <stdlib.h>
#include <malloc.h>

// Par�metros
// usradm -d usuario [Flags]
// usradm -a usuario [Flags]
// usradm -i usuario [Flags]
// usradm -u usuario [Flags]

// Explicaci�n flags
// n NombreUsuario
// S server
// c Comentario
// p Password
// H HomeDrive
// h Homedir
// p Profile
// s ScriptPath
// w Workstations
// g GrupoPrimario
// G GruposSecundarios
// q = Quite
// Q diskquota
// U = Privilegios usuario
//   u - user
//   g - guest
//   a - admin
//
// F = Flags
//   s - script (Requerido)
//   d - disable
//   e - enable
//   r - password not required
//   c - password can't change
//   p - password dont expire
//   x - expire password
//
// Variables correspondientes a las contrase�as
int iArgs;
LPWSTR *lpArgs;

LPWSTR lpszCurrentUserName = NULL;
LPWSTR lpszCurrentPassword = NULL;
LPWSTR lpszUserName = NULL;
LPWSTR lpszGroupName = NULL;
LPWSTR lpszFullName = NULL;
LPWSTR lpszPassword = NULL;
LPWSTR lpszHomeDir = NULL;
LPWSTR lpszHomeDrive = NULL;
LPWSTR lpszComment = NULL;
LPWSTR lpszScriptPath = NULL;
LPWSTR lpszWorkStations = NULL;
LPWSTR lpszGlobalGroups = NULL;
LPWSTR lpszLocalGroups = NULL;
LPWSTR lpszProfile = NULL;
LPWSTR lpszServer = NULL;
DWORD dwQuota = USER_MAXSTORAGE_UNLIMITED;
DWORD dwUserId = NULL;
DWORD dwPrimaryGroup = DOMAIN_GROUP_RID_USERS;
BOOL bExpired = FALSE;
BOOL bSetExpired = FALSE;
BOOL bSetNoExpired = FALSE;
int iAuthFlags = NULL;
int iPriv = USER_PRIV_USER;
int iFlags = UF_SCRIPT;
int iExpires = TIMEQ_FOREVER;
TCHAR achUserLocalGroups [1024];
TCHAR achUserGlobalGroups [1024];
enum {MODO_UPDATE = 1, MODO_ADD, MODO_DELETE, MODO_INFO, MODO_HELP, MODO_LIST_USERS,
	MODO_LIST_GROUPS, MODO_GROUP_INFO, MODO_GROUP_UPDATE, MODO_GROUP_DELETE, MODO_GROUP_ADD};
int iModo;
BOOL bQuiet = FALSE;

void FillUserInfo ( USER_INFO_3 &ui3)
{
	ui3.usri3_name = lpszUserName;
    ui3.usri3_password = lpszPassword;
    ui3.usri3_password_age = NULL;
    ui3.usri3_priv = USER_PRIV_USER;
    ui3.usri3_home_dir = lpszHomeDir;
    ui3.usri3_comment = lpszComment;
    ui3.usri3_flags = iFlags;
    ui3.usri3_script_path = lpszScriptPath;
//    ui3.usri3_auth_flags = iAuthFlags;
    ui3.usri3_full_name = lpszFullName;
    ui3.usri3_usr_comment = lpszComment;
    ui3.usri3_parms = NULL;
    ui3.usri3_workstations = lpszWorkStations;
//    ui3.usri3_last_logon = NULL;
//    ui3.usri3_last_logoff = NULL;
    ui3.usri3_acct_expires = iExpires;
    ui3.usri3_max_storage = dwQuota;
    ui3.usri3_units_per_week = NULL;
    ui3.usri3_logon_hours = NULL;
//    ui3.usri3_bad_pw_count = NULL;
//    ui3.usri3_num_logons = NULL;
//    ui3.usri3_logon_server = NULL;
//    ui3.usri3_country_code = NULL;
//    ui3.usri3_code_page = NULL;
    ui3.usri3_user_id = dwUserId;
    ui3.usri3_primary_group_id = dwPrimaryGroup;
    ui3.usri3_profile = lpszProfile;
    ui3.usri3_home_dir_drive = lpszHomeDrive;
    ui3.usri3_password_expired = bSetExpired ? TRUE : bSetNoExpired ? FALSE: bExpired;
}

void ExtractUserInfo ( USER_INFO_3 &ui3)
{
	lpszUserName = ui3.usri3_name;
    lpszPassword = NULL;
    lpszHomeDir = ui3.usri3_home_dir;
    lpszComment = ui3.usri3_comment;
    iFlags = ui3.usri3_flags;
    lpszScriptPath = ui3.usri3_script_path;
    lpszFullName = ui3.usri3_full_name;
    lpszComment = ui3.usri3_usr_comment;
    lpszWorkStations = ui3.usri3_workstations;
    iExpires = ui3.usri3_acct_expires;
    dwQuota = ui3.usri3_max_storage;
    dwUserId = ui3.usri3_user_id;
    dwPrimaryGroup = ui3.usri3_primary_group_id;
    lpszProfile = ui3.usri3_profile;
    lpszHomeDrive = ui3.usri3_home_dir_drive;
    bExpired = ui3.usri3_password_expired;
}

BOOL ProcessCommandLine (int iArgs, LPWSTR *lpArgs, BOOL bReparse)
{
	int i;
	for (i = 0; i < iArgs; i++)
	{
		if (wcsncmp (lpArgs[i], L"-", 1) != 0)
		{
			return FALSE;
		}
		else if (lpArgs[i][1] != L'\0' && lpArgs[i][1] != L'-')
		{
			switch (lpArgs[i][1])
			{
			case L'u':
				if (iModo == NULL || bReparse)
					iModo = MODO_UPDATE;
				else
					return FALSE;
				i++;
				if ( i < iArgs)
					lpszUserName = lpArgs [i];
				else
					return FALSE;
				break;
			case L'd':
				if (iModo == NULL || bReparse)
					iModo = MODO_DELETE;
				else
					return FALSE;
				i++;
				if ( i < iArgs)
					lpszUserName = lpArgs [i];
				else
					return FALSE;
				break;
			case L'a':
				if (iModo == NULL || bReparse)
					iModo = MODO_ADD;
				else
					return FALSE;
				i++;
				if ( i < iArgs)
					lpszUserName = lpArgs [i];
				else
					return FALSE;
				break;
			case L'?':
				if (iModo == NULL || bReparse)
					iModo = MODO_HELP;
				else
					return FALSE;
				break;
			case L'i':
				if (iModo == NULL || bReparse)
					iModo = MODO_INFO;
				else
					return FALSE;
				i++;
				if ( i < iArgs)
					lpszUserName = lpArgs [i];
				else
					return FALSE;
				break;
			case L'U':
				if (lpArgs[i][2] == L'g')
				{
					iPriv = USER_PRIV_GUEST;
				}
				else if (lpArgs[i][2] == L'u')
				{
					iPriv = USER_PRIV_USER;
				}
				else if (lpArgs[i][2] == L'a')
				{
					iPriv = USER_PRIV_ADMIN;
				}
				else
					return FALSE;
				break;
			case L'p':
				if (! bSetExpired)
					bSetNoExpired = TRUE;
				i++;
				if ( i < iArgs)
					lpszPassword = lpArgs [i];
				else
					return FALSE;
				break;
			case L'F':
			{
				UINT j;
				iFlags = UF_SCRIPT;
				for ( j = 2; j < wcslen (lpArgs[i]); j ++)
				{
					switch (lpArgs[i][j])
					{
					case 's':
						iFlags = iFlags | UF_SCRIPT;
						break;
					case 'd':
						iFlags = iFlags | UF_ACCOUNTDISABLE;
						break;
					case 'e':
						iFlags = iFlags | UF_LOCKOUT;
						break;
					case 'c':
						iFlags = iFlags | UF_PASSWD_CANT_CHANGE;
						break;
					case 'r':
						iFlags = iFlags | UF_PASSWD_NOTREQD;
						break;
					case 'p':
						iFlags = iFlags | UF_DONT_EXPIRE_PASSWD;
						break;
					case 'x':
						bSetExpired = TRUE;
						bSetNoExpired = FALSE;
						break;
					default:
						return FALSE;
					}
				}
				break;
			}
			case L'S':
				i++;
				if ( i < iArgs)
					lpszServer = lpArgs [i];
				else
					return FALSE;
				break;
			case L'h':
				i++;
				if ( i < iArgs)
					lpszHomeDir = lpArgs [i];
				else
					return FALSE;
				break;
			case L'H':
				i++;
				if ( i < iArgs)
					lpszHomeDrive = lpArgs [i];
				else
					return FALSE;
				break;
			case L'P':
				i++;
				if ( i < iArgs)
					lpszProfile = lpArgs [i];
				else
					return FALSE;
				break;
			case L'n':
				i++;
				if ( i < iArgs)
					lpszFullName = lpArgs [i];
				else
					return FALSE;
				break;
			case L's':
				i++;
				if ( i < iArgs)
					lpszScriptPath = lpArgs [i];
				else
					return FALSE;
				break;
			case L'c':
				i++;
				if ( i < iArgs)
					lpszComment = lpArgs [i];
				else
					return FALSE;
				break;
			case L'w':
				i++;
				if ( i < iArgs)
					lpszWorkStations = lpArgs [i];
				else
					return FALSE;
				break;
			case L'G':
				i++;
				if ( i < iArgs)
					lpszGlobalGroups = lpArgs [i];
				else
					return FALSE;
				break;
			case L'g':
				i++;
				if ( i < iArgs)
					lpszLocalGroups = lpArgs [i];
				else
					return FALSE;
				break;
			case L'Q':
				i++;
				if ( i < iArgs)
					dwQuota = _wtol (lpArgs [i]);
				else
					return FALSE;
				break;
			case L'q':
				bQuiet = TRUE;
				break;
			default:
				return FALSE;
			}
		}
		else if (wcscmp (lpArgs[i], L"--list-users") == 0) {
			if (iModo == NULL || bReparse)
				iModo = MODO_LIST_USERS;
			else
				return FALSE;
		}
		else if (wcscmp (lpArgs[i], L"--list-groups") == 0) {
			if (iModo == NULL || bReparse)
				iModo = MODO_LIST_GROUPS;
			else
				return FALSE;
		}
		else if (wcscmp (lpArgs[i], L"--group-info") == 0) {
			if (iModo == NULL || bReparse)
				iModo = MODO_GROUP_INFO;
			else
				return FALSE;
			i++;
			if ( i < iArgs)
				lpszGroupName = lpArgs [i];
			else
				return FALSE;
		}
		else if (wcscmp (lpArgs[i], L"--group-update") == 0) {
			if (iModo == NULL || bReparse)
				iModo = MODO_GROUP_UPDATE;
			else
				return FALSE;
			i++;
			if ( i < iArgs)
				lpszGroupName = lpArgs [i];
			else
				return FALSE;
		}
		else if (wcscmp (lpArgs[i], L"--group-add") == 0) {
			if (iModo == NULL || bReparse)
				iModo = MODO_GROUP_ADD;
			else
				return FALSE;
			i++;
			if ( i < iArgs)
				lpszGroupName = lpArgs [i];
			else
				return FALSE;
		}
		else if (wcscmp (lpArgs[i], L"--group-delete") == 0) {
			if (iModo == NULL || bReparse)
				iModo = MODO_GROUP_DELETE;
			else
				return FALSE;
			i++;
			if ( i < iArgs)
				lpszGroupName = lpArgs [i];
			else
				return FALSE;
		}
		else if (wcscmp (lpArgs[i], L"--user") == 0) {
			i++;
			if ( i < iArgs)
				lpszCurrentUserName = lpArgs [i];
			else
				return FALSE;
		} else if (wcscmp (lpArgs[i], L"--password") == 0) {
			i++;
			if ( i < iArgs)
				lpszCurrentPassword = lpArgs [i];
			else
				return FALSE;
		} else {
			return FALSE;
		}
	}
	return TRUE;
}

void DisplayErrorMessage (LPTSTR lpszOperacion, DWORD dwResult, DWORD dwParam)
{
	_TCHAR achMsg [200];
	LPCTSTR lpMsg;
	BOOL bFree = FALSE;

	if (dwResult == ERROR_INVALID_PARAMETER)
	{
		_stprintf (achMsg, _T("Error en par�metro %d"), dwParam);
		lpMsg = achMsg;
	}
	else if (dwResult == ERROR_ACCESS_DENIED)
	{
		lpMsg = _T("Acceso denegado");
	}
	else if (dwResult == NERR_InvalidComputer)
	{
		lpMsg = _T("Servidor err�neo");
	}
	else if (dwResult == NERR_GroupNotFound)
	{
		lpMsg = _T("Grupo err�neo");
	}
	else if (dwResult == NERR_BufTooSmall)
	{
		lpMsg = _T("Buffer demasiado peque�o");
	}
	else if (dwResult == NERR_NotPrimary)
	{
		lpMsg = _T("Operaci�n s�lamente permitida en el PDC");
	}
	else if (dwResult == NERR_GroupExists)
	{
		lpMsg = _T("El grupo ya existe");
	}
	else if (dwResult == NERR_UserExists)
	{
		lpMsg = _T("El usuario ya existe");
	}
	else if (dwResult == NERR_PasswordTooShort)
	{
		lpMsg = _T("Contrase�a demasiado corta");
	}
	else if (dwResult == NERR_UserNotFound)
	{
		lpMsg = _T("Usuario desconocido");
	}
	else
	{
		DWORD dwBuffer ;
		dwBuffer = FormatMessage( 
			FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM,
			NULL,
			dwResult,
			MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT), // Default language
			(LPTSTR) &lpMsg,
			0,
			NULL 
		);
		if (dwBuffer == 0)
		{
			_stprintf (achMsg, _T("Error desconocido %d"), dwResult);
			lpMsg = achMsg;
		}
		else
			bFree = TRUE;

	}
	// Display the string.
	if ( bQuiet)
		_tprintf (_T("ERR-%04d: %s: %s\n"), dwResult,lpszOperacion, lpMsg);
	else
		MessageBox( NULL, lpMsg, lpszOperacion, MB_OK|MB_ICONWARNING );
	// Free the buffer.
	if (bFree)
		LocalFree( (HLOCAL)  lpMsg );
}

DWORD GetUserGlobalGroups (USER_INFO_3 &ui3)
{
	GROUP_USERS_INFO_0 *grui = NULL;
	GROUP_INFO_2 *gi;
	DWORD dwResult;
	DWORD entriesread = 0, totalentries = 0;
	DWORD i;
	dwResult = NetUserGetGroups(
		lpszServer,
		lpszUserName,
		0,
		(LPBYTE *) &grui,
		64000,
		&entriesread,
		&totalentries);
	if (dwResult != NERR_Success)
	{
		return dwResult;
	}
	else
	{
		_tcscpy (achUserGlobalGroups, _T(""));
		for (i = 0; i < entriesread; i++)
		{
			dwResult = NetGroupGetInfo (lpszServer, grui[i].grui0_name, 2, (LPBYTE *) &gi);
			if (dwResult == NERR_Success)
			{
				NetApiBufferFree (gi);

				if (_tcslen (achUserGlobalGroups) > 0)
				{
					_tcscat (achUserGlobalGroups,_T(","));
					_tcscat (achUserGlobalGroups, grui[i].grui0_name);
				}
				else
				{
					_tcscpy (achUserGlobalGroups, grui[i].grui0_name);
				}
			}
		}
	}
	NetApiBufferFree ((LPBYTE)grui);
	lpszGlobalGroups = achUserGlobalGroups;
	return NERR_Success;
}

DWORD SetUserGlobalGroups (USER_INFO_3 &ui3)
{
	GROUP_USERS_INFO_0 *grui = NULL;
	DWORD entriesread = 0, totalentries = 0;
	DWORD i;
	DWORD dwResult;
	LPTSTR lptstr;
	// En el caso de Global Groups no es necesario obtener el SID.
	// Basta con tener el nombre del usuario.
	// Lo primero, enumerar y borrar
	dwResult = NetUserGetGroups(
		lpszServer,
		lpszUserName,
		0,
		(LPBYTE *) &grui,
		64000,
		&entriesread,
		&totalentries);
	if (dwResult != NERR_Success)
	{
		return dwResult;
	}
	else
	{
		// Eliminar de cuantos grupos pertenezca
		for (i = 0; i < totalentries; i++)
		{
			dwResult = NetGroupDelUser (
				lpszServer,
				grui[i].grui0_name,
				lpszUserName);
			if (dwResult != NERR_Success &&
				dwResult != NERR_SpeGroupOp)
			{
				return dwResult;
			}
		}
	}
	NetApiBufferFree ((LPBYTE)grui);
	_tcscpy (achUserGlobalGroups, lpszGlobalGroups);
	lptstr = _tcstok (achUserLocalGroups, _T(","));
	while (lptstr != NULL)
	{
		dwResult = NetGroupAddUser (
			lpszServer,
			lptstr,
			lpszUserName);
		if (dwResult != NERR_Success &&
			dwResult != NERR_SpeGroupOp)
		{
			return dwResult;
		}
		lptstr =_tcstok (NULL, _T(","));
	}
	return NERR_Success;
}

DWORD GetUserLocalGroups (USER_INFO_3 &ui3)
{
	LOCALGROUP_USERS_INFO_0 *lgrui = NULL;
	DWORD dwResult;
	DWORD entriesread = 0, totalentries = 0;
	DWORD i;
	dwResult = NetUserGetLocalGroups(
		lpszServer,
		lpszUserName,
		0,
		NULL,
		(LPBYTE *) &lgrui,
		64000,
		&entriesread,
		&totalentries);
	if (dwResult != NERR_Success)
	{
		return dwResult;
	}
	else
	{
		_tcscpy (achUserLocalGroups, _T(""));
		for (i = 0; i < entriesread; i++)
		{
			if (lgrui[i].lgrui0_name != NULL)
			{
				if (_tcslen (achUserLocalGroups) > 0)
				{
					_tcscat (achUserLocalGroups,_T(","));
					_tcscat (achUserLocalGroups, lgrui[i].lgrui0_name);
				}
				else
				{
					_tcscpy (achUserLocalGroups, lgrui[i].lgrui0_name);
				}
			}
		}
	}
	NetApiBufferFree ((LPBYTE)lgrui);
	lpszLocalGroups = achUserLocalGroups;
	return NERR_Success;
}

DWORD SetUserLocalGroups (USER_INFO_3 &ui3)
{
	LOCALGROUP_USERS_INFO_0 *lgrui = NULL;
	DWORD entriesread = 0, totalentries = 0;
	DWORD i;
	LOCALGROUP_MEMBERS_INFO_0 lgm;
	DWORD dwResult;
	LPTSTR lptstr;
	PSID psid = NULL;
	DWORD cbSid = 0;
	_TCHAR achDomainName[100];
	DWORD cbDomainName = sizeof achDomainName;
	SID_NAME_USE snu;
	// Lo primero es obtener el SID del usuario
	if (! LookupAccountName(
		lpszServer,
		lpszUserName,
		psid,
		&cbSid,
		achDomainName,
		&cbDomainName,
		&snu))
	{
		if (GetLastError () == ERROR_INSUFFICIENT_BUFFER)
		{
			psid = (PSID) malloc (cbSid);
			if (! LookupAccountName(
				lpszServer,
				lpszUserName,
				psid,
				&cbSid,
				achDomainName,
				&cbDomainName,
				&snu))
			{
				free ((void*)psid);
				return GetLastError ();
			}
		}
		else
			return GetLastError ();
	}
	// Lo primero, enumerar y borrar
	dwResult = NetUserGetLocalGroups(
		lpszServer,
		lpszUserName,
		0,
		NULL,
		(LPBYTE *) &lgrui,
		64000,
		&entriesread,
		&totalentries);
	if (dwResult != NERR_Success)
	{
		free (psid);
		return dwResult;
	}
	else
	{
		// Eliminar de cuantos grupos pertenezca
		for (i = 0; i < totalentries; i++)
		{
			lgm. lgrmi0_sid = psid;
			dwResult = NetLocalGroupDelMembers (
				lpszServer,
				lgrui[i].lgrui0_name,
				0,
				(LPBYTE) &lgm,
				1);
			if (dwResult != NERR_Success)
			{
				free (psid);
				return dwResult;
			}
		}
	}
	NetApiBufferFree ((LPBYTE)lgrui);
	_tcscpy (achUserLocalGroups, lpszLocalGroups);
	lptstr = _tcstok (achUserLocalGroups, _T(","));
	while (lptstr != NULL)
	{
		lgm. lgrmi0_sid = psid;
		dwResult = NetLocalGroupAddMembers (
			lpszServer,
			lptstr,
			0,
			(LPBYTE) &lgm,
			1);
		if (dwResult != NERR_Success)
		{
			free (psid);
			return dwResult;
		}
		lptstr =_tcstok (NULL, _T(","));
	}
	free (psid);
	return NERR_Success;
}

bool connectUser ()
{
	if (lpszServer != NULL && lpszCurrentPassword != NULL && lpszCurrentUserName != NULL)
	{
		wchar_t * wch = (wchar_t*) malloc (sizeof (wchar_t) * ( wcslen(lpszServer)+20) );
		wcscpy (wch, L"\\\\");
		wcscat (wch, lpszServer);
		wcscat (wch, L"\\IPC$");
		NETRESOURCE nr;
		nr.dwType = RESOURCETYPE_ANY;
		nr.lpLocalName = NULL;
		nr.lpRemoteName = wch;
		nr.lpProvider = NULL;
		DWORD result;
		WNetCancelConnectionW(wch, true);
		result = WNetAddConnection2W(&nr, lpszCurrentPassword, lpszCurrentUserName, CONNECT_TEMPORARY);
		if (result != NO_ERROR)
		{
			DisplayErrorMessage (_T("WNetAddConnection2"), result, 0);
			return false;
		}
		free (wch);
	}
	return true;
}


BOOL ListUsers ()
{
	if (! connectUser () )
		return false;

	DWORD dwResult;
	LPBYTE data;
	DWORD entriesread, totalentries;
	dwResult = NetUserEnum(lpszServer, 0, FILTER_NORMAL_ACCOUNT, &data, MAX_PREFERRED_LENGTH, &entriesread, &totalentries, NULL);
	if (dwResult == NERR_Success)
	{
		USER_INFO_0 *ui = (USER_INFO_0*) data;
		for (int i = 0; i < entriesread; i++)
		{
			_tprintf (_T("%s\n"), ui[i].usri0_name);
		}
		NetApiBufferFree (data);
		return TRUE;
	}
	else
	{
		DisplayErrorMessage (_T("ListUsers"), dwResult, 0);
		return FALSE;
	}
}

BOOL ListGroups ()
{
	if (! connectUser () )
		return false;

	DWORD dwResult;
	LPBYTE data;
	DWORD entriesread, totalentries;
	dwResult = NetLocalGroupEnum(lpszServer, 0, &data, MAX_PREFERRED_LENGTH, &entriesread, &totalentries, NULL);
	if (dwResult == NERR_Success)
	{
		LOCALGROUP_INFO_0 *lgi = (LOCALGROUP_INFO_0*) data;
		for (int i = 0; i < entriesread; i++)
		{
			_tprintf (_T("%s\n"), lgi[i].lgrpi0_name);
		}
		NetApiBufferFree (data);
		return TRUE;
	}
	else
	{
		DisplayErrorMessage (_T("ListGroups"), dwResult, 0);
		return FALSE;
	}
}

BOOL UpdateGroup ()
{
	if (! connectUser () )
		return false;

	LOCALGROUP_INFO_1 *lgi1 = NULL;
	DWORD dwResult, dwParam = 0;
	dwResult = NetLocalGroupGetInfo(lpszServer, lpszGroupName, 1, (LPBYTE *) &lgi1);
	if (dwResult == NERR_Success)
	{
		if (lpszComment == NULL)
			return TRUE;
		else
		{
			lgi1->lgrpi1_comment = lpszComment;
			dwResult = NetLocalGroupSetInfo(lpszServer, lpszGroupName, 1, (LPBYTE) lgi1, &dwParam);
			NetApiBufferFree ((LPBYTE)lgi1);
			if (dwResult != NERR_Success)
			{
				return TRUE;
			}
		}
	}
	DisplayErrorMessage (_T("UpdateGroup"), dwResult, dwParam);
	return FALSE;
}

BOOL AddGroup ()
{
	if (! connectUser () )
		return false;

	LOCALGROUP_INFO_1 lgi1 ;
	lgi1.lgrpi1_name = lpszGroupName;
	lgi1.lgrpi1_comment = lpszComment;
	DWORD dwResult, dwParam = 0;
	dwResult = NetLocalGroupAdd(lpszServer, 1, (LPBYTE) &lgi1, &dwParam);
	if (dwResult == NERR_Success)
	{
		return TRUE;
	}
	DisplayErrorMessage (_T("UpdateGroup"), dwResult, dwParam);
	return FALSE;
}

BOOL UpdateUser ()
{
	if (! connectUser () )
		return false;

	USER_INFO_3 *ui3;
	DWORD dwResult, dwParam;
	memset ( &ui3, 0, sizeof ui3);
	dwResult = NetUserGetInfo (lpszServer, lpszUserName, 3, (LPBYTE *) &ui3);
	if (dwResult == NERR_Success)
	{
		ExtractUserInfo (*ui3);
		dwResult = GetUserLocalGroups (*ui3);
		if (dwResult != NERR_Success)
		{
			NetApiBufferFree ((LPBYTE)ui3);
			DisplayErrorMessage (_T("UpdateUser"), dwResult, dwParam);
			return FALSE;
		}
		dwResult = GetUserGlobalGroups (*ui3);
		if (dwResult != NERR_Success)
		{
			NetApiBufferFree ((LPBYTE)ui3);
			DisplayErrorMessage (_T("UpdateUser"), dwResult, dwParam);
			return FALSE;
		}
		ProcessCommandLine (iArgs, lpArgs, TRUE);
		// Actualizar el usuario
		FillUserInfo (*ui3);
		dwResult = NetUserSetInfo ( lpszServer, lpszUserName, 3,  (LPBYTE) ui3, &dwParam);
		if (dwResult != NERR_Success)
		{
			NetApiBufferFree ((LPBYTE)ui3);
			DisplayErrorMessage (_T("UpdateUser"), dwResult, dwParam);
			return FALSE;
		}
		// Actualizar grupos locales
		if (_tcscmp (achUserLocalGroups, lpszLocalGroups) != 0)
		{
			dwResult = SetUserLocalGroups (*ui3);
			if (dwResult != NERR_Success)
			{
				NetApiBufferFree ((LPBYTE)ui3);
				DisplayErrorMessage (_T("UpdateUser"), dwResult, dwParam);
				return FALSE;
			}
		}
		// Actualizar grupos globales
		if (_tcscmp (achUserGlobalGroups, lpszGlobalGroups) != 0)
		{
			dwResult = SetUserGlobalGroups (*ui3);
			if (dwResult != NERR_Success)
			{
				NetApiBufferFree ((LPBYTE)ui3);
				DisplayErrorMessage (_T("UpdateUser"), dwResult, dwParam);
				return FALSE;
			}
		}
		NetApiBufferFree ((LPBYTE)ui3);
		return TRUE;
	}
	else
	{
		DisplayErrorMessage (_T("UpdateUser"), dwResult, dwParam);
		return FALSE;
	}
}

BOOL DeleteGroup ()
{
	if (! connectUser () )
		return false;

	DWORD dwResult;
	if (!bQuiet)
	{
		_TCHAR achMessage[100];
		_stprintf (achMessage, _T("Do you realy want to delete group %s ?"), lpszGroupName);
		int iResult = MessageBox (NULL, achMessage, _T("Please, confirm"), MB_YESNO|MB_ICONQUESTION);
		if (iResult != IDYES)
			return FALSE;
	}
	dwResult = NetLocalGroupDel( lpszServer, lpszGroupName);
	if (dwResult == NERR_Success)
		return TRUE;
	else
	{
		DisplayErrorMessage (_T("DelUser"), dwResult, 0);
		return FALSE;
	}
}

BOOL DeleteUser ()
{
	if (! connectUser () )
		return false;

	DWORD dwResult;
	if (!bQuiet)
	{
		_TCHAR achMessage[100];
		_stprintf (achMessage, _T("� Seguro que desea eliminar el usuario %s ?"), lpszUserName);
		int iResult = MessageBox (NULL, achMessage, _T("Confirmaci�n"), MB_YESNO|MB_ICONQUESTION);
		if (iResult != IDYES)
			return FALSE;
	}
	dwResult = NetUserDel ( lpszServer, lpszUserName);
	if (dwResult == NERR_Success)
		return TRUE;
	else
	{
		DisplayErrorMessage (_T("DelUser"), dwResult, 0);
		return FALSE;
	}
}

BOOL AddUser ()
{
	if (! connectUser () )
		return false;

	USER_INFO_3 ui3;
	DWORD dwResult, dwParam;
	memset ( &ui3, 0, sizeof ui3);
	FillUserInfo (ui3);
	dwResult = NetUserAdd ( lpszServer, 3,  (LPBYTE) &ui3, &dwParam);
	if (dwResult == NERR_Success)
		return TRUE;
	else
	{
		DisplayErrorMessage (_T("AddUser"), dwResult, dwParam);
		return FALSE;
	}
	return FALSE;
}

BOOL InfoGroup ()
{
	if (! connectUser () )
		return false;

	LOCALGROUP_INFO_1 *lgi1 = NULL;
	DWORD dwResult, dwParam;
	dwResult = NetLocalGroupGetInfo(lpszServer, lpszGroupName, 1, (LPBYTE *) &lgi1);
	if (dwResult == NERR_Success)
	{
		if (bQuiet)
			_tprintf (_T("%ls:%ls\n"),
				lpszGroupName, lgi1->lgrpi1_comment);
		else
		{
			TCHAR achMessage[1000];
			_stprintf (achMessage,
				_T("Group:%ls\n")
				_T("Name:%ls\n"),
				lpszGroupName, lgi1->lgrpi1_comment);
			MessageBox (NULL, achMessage, _T("Informaci�n grupo"),
				MB_OK|MB_ICONINFORMATION);
		}
		NetApiBufferFree ((LPBYTE)lgi1);
		return TRUE;
	}
	DisplayErrorMessage (_T("InfoGroup"), dwResult, dwParam);
	return FALSE;
}

BOOL InfoUser ()
{
	if (! connectUser () )
		return false;

	USER_INFO_3 *ui3;
	DWORD dwResult, dwParam;
	memset ( &ui3, 0, sizeof ui3);
	dwResult = NetUserGetInfo (lpszServer, lpszUserName, 3, (LPBYTE *) &ui3);
	if (dwResult == NERR_Success)
	{
		ExtractUserInfo (*ui3);
		dwResult = GetUserLocalGroups (*ui3);
		if (dwResult == NERR_Success)
		{
			dwResult = GetUserGlobalGroups (*ui3);
			if (dwResult == NERR_Success)
			{
				if (bQuiet)
				{
					_tprintf (_T("%s:%d:%s:%s:%s:%s:%s:%s:%s:%d:%s:%s:%ld:%ld:%ld\n"),
						lpszUserName,dwUserId, lpszFullName,
						lpszComment,
						lpszHomeDrive, lpszHomeDir,
						lpszProfile, lpszScriptPath,
						lpszWorkStations, dwQuota,
						lpszLocalGroups,lpszGlobalGroups,
						(long)ui3->usri3_flags,
						(long)ui3->usri3_password_age,
						(long)ui3->usri3_password_expired);
				}
				else
				{
					TCHAR achMessage[1000];
					_stprintf (achMessage,
						_T("Usuario:%s\nID:%d\n")
						_T("Nombre:%s\n")
						_T("Comentario:%s\n")
						_T("Disco usuario:%s\n")
						_T("Path usuario:%s\n")
						_T("Perfil:%s\n")
						_T("Script inicio:%s\n")
						_T("Estaciones trabajo:%s:\n")
						_T("Cuota disco:%d\n")
						_T("Grupos locales:%s\n")
						_T("Grupos globales:%s"),
						lpszUserName,dwUserId, lpszFullName,
						lpszComment,
						lpszHomeDrive, lpszHomeDir,
						lpszProfile, lpszScriptPath,
						lpszWorkStations, dwQuota,
						lpszLocalGroups,lpszGlobalGroups);
					MessageBox (NULL, achMessage, _T("Informaci�n usuario"),
						MB_OK|MB_ICONINFORMATION);
				}
				NetApiBufferFree ((LPBYTE)ui3);
				return TRUE;
			}
		}

		NetApiBufferFree ((LPBYTE)ui3);
	}
	DisplayErrorMessage (_T("UserInfo"), dwResult, dwParam);
	return FALSE;
}

BOOL Help ()
{
	_tprintf (_T("UsrAdm version 2.0\n\n")
		_T(" usradm --list-users [Flags]: Display user list\n")
		_T(" usradm -a usuario [Flags] : Crea usuario\n")
		_T(" usradm -u usuario [Flags] : Actualiza usuario existente\n")
		_T(" usradm -d usuario [Flags] : Borra usuario existente\n")
		_T(" usradm -i usuario [Flags] : Visualiza informacion del usuario\n")
		_T(" usradm --list-groups [Flags] : Display local groups list\n")
		_T(" usradm --group-add group [Flags] : Create group\n")
		_T(" usradm --group-update group [Flags] : Updates existing group\n")
		_T(" usradm --group-delete group [Flags] : Deletes an existing group\n")
		_T(" usradm --group-info group [Flags] : Displays group information\n")
		_T("\nFlags reconocidos:\n")
		_T(" -S Servidor\n")
		_T(" -n Nombre completo\n")
		_T(" -c Comentario\n")
		_T(" -p Contrase�a\n")
		_T(" -H Disco de usuario\n")
		_T(" -h Ruta de usuario\n")
		_T(" -p Directorio perfil\n")
		_T(" -s Script de inicio\n")
		_T(" -w Estaciones de trabajo permitidas (separadas por comas)\n")
		_T(" -g Grupos locales (separados por comas)\n")
		_T(" -G Grupos globales del dominio (separados por comas)\n")
		_T(" -Q Quota de disco\n")
		_T(" -U [user|guest|admin] (Tipo de usuario)\n")
		_T(" -F alguno de los siguientes flags:\n")
		_T("    s (Ejecutar script de inicio)\n")
		_T("    d (Cuenta deshabilitada)\n")
		_T("    e (Cuenta habilitada)\n")
		_T("    r (No se requiere contrase�a)\n")
		_T("    c (No se puede cambiar la contrase�a)\n")
		_T("    p (La contrase�a no caduca)\n")
		_T("    x (El usuario debe cambiar la contrase�a en el proximo inicio de sesi�n)\n")
		_T(" -q (Modo silencioso)\n"));
	return TRUE;
}


extern "C" int WINAPI wWinMain(
    HINSTANCE hInstance,	// handle to current instance
    HINSTANCE hPrevInstance,	// handle to previous instance
    LPWSTR lpCmdLine,	// pointer to command line
    int nCmdShow 	// show state of window
	)
{
	BOOL bOK;

	lpArgs = CommandLineToArgvW(lpCmdLine,  &iArgs);


	bOK = ProcessCommandLine (iArgs, lpArgs, FALSE);
	if (!bOK || iModo == NULL)
	{
		if (bQuiet)
			_tprintf (_T("Error analizando l�nea de comandos. usradm -? para ayuda\n"));
		else
			MessageBox (NULL, _T("Error analizando l�nea de comandos"),
				_T("usrmgr"), MB_OK|MB_ICONSTOP);
		return 1;
	} 
	else if (iModo == MODO_ADD)
		return AddUser() ? 0: 255;
	else if (iModo == MODO_DELETE)
		return DeleteUser() ? 0 : 255;
	else if (iModo == MODO_UPDATE)
		return UpdateUser() ? 0 : 255;
	else if (iModo == MODO_GROUP_ADD)
		return AddGroup() ? 0: 255;
	else if (iModo == MODO_GROUP_DELETE)
		return DeleteGroup() ? 0 : 255;
	else if (iModo == MODO_GROUP_UPDATE)
		return UpdateGroup() ? 0 : 255;
	else if (iModo == MODO_INFO)
		return InfoUser() ? 0 : 255;
	else if (iModo == MODO_GROUP_INFO)
		return InfoGroup() ? 0 : 255;
	else if (iModo == MODO_LIST_USERS)
		return ListUsers() ? 0 : 255;
	else if (iModo == MODO_LIST_GROUPS)
		return ListGroups() ? 0 : 255;
	else
		return Help () ? 0 : 255;
	return 0;
}
