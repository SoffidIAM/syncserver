#include <windows.h>
#include <ntsecapi.h>
#include <string.h>
#include <ErisADFilter.h>
#include <minmax.h>

#ifndef STATUS_SUCCESS
#define STATUS_SUCCESS  ((NTSTATUS)0x00000000L)
#endif

#define minim(a,b) (((a) < (b)) ? (a) : (b))

/*++

 Routine Description:

 This (optional) routine is notified of a password change.

 Arguments:

 UserName - Name of user whose password changed

 RelativeId - RID of the user whose password changed

 NewPassword - Cleartext new password for the user

 Return Value:

 STATUS_SUCCESS only - errors from packages are ignored.

 --*/

extern "C" {
static HANDLE hInstance = NULL;

HANDLE getModuleHandle() {
	return hInstance;
}

NTSTATUS APIENTRY
PasswordChangeNotify(PUNICODE_STRING UserName, ULONG RelativeId,
		PUNICODE_STRING Password) {
	struct PasswordChange p;
	ZeroMemory (&p, sizeof p);
	wcsncpy(p.szUser, (LPWSTR) UserName->Buffer,
			minim(MAXUSERLENGTH-1,UserName->Length/2));
	wcsncpy(p.szPassword, (LPWSTR) Password->Buffer,
			minim(MAXUSERLENGTH-1, Password->Length/2));
	if (!sendMessage(&p)) {
		HANDLE h = openSpoolFile();
		writePasswordChange(h, &p);
		CloseHandle(h);
	}
	ZeroMemory (&p, sizeof p);
	return STATUS_SUCCESS;
}

/*++

 Routine Description:

 This (optional) routine is notified of a password change.

 Arguments:

 UserName - Name of user whose password changed

 FullName - Full name of the user whose password changed

 NewPassword - Cleartext new password for the user

 SetOperation - TRUE if the password was SET rather than CHANGED

 Return Value:

 TRUE if the specified Password is suitable (complex, long, etc).
 The system will continue to evaluate the password update request
 through any other installed password change packages.

 FALSE if the specified Password is unsuitable. The password change
 on the specified account will fail.

 --*/BOOL APIENTRY
PasswordFilter(PUNICODE_STRING UserName, PUNICODE_STRING FullName,
		PUNICODE_STRING Password, BOOL SetOperation) {
		struct PasswordChange p;
		ZeroMemory (&p, sizeof p);
		wcsncpy(p.szUser, (LPWSTR) UserName->Buffer,
				minim(MAXUSERLENGTH-1,UserName->Length/2));
		wcsncpy(p.szPassword, (LPWSTR) Password->Buffer,
				minim(MAXUSERLENGTH-1, Password->Length/2));
		bool bValid = sendTest(&p);
		ZeroMemory (&p, sizeof p);
		return bValid;
}

/*++

 Routine Description:

 This (optional) routine is called when the password change package
 is loaded.

 Arguments:

 Return Value:

 TRUE if initialization succeeded.

 FALSE if initialization failed. This DLL will be unloaded by the
 system.

 --*/BOOL APIENTRY
InitializeChangeNotify(void) {
	return TRUE;
}

BOOL APIENTRY
Test(void) {
	MessageBoxW(NULL, L"Prueba", L"Este es el título", MB_OK);
	return TRUE;
}

DECLSPEC_EXPORT
BOOL APIENTRY
InstallLSA(void) {
	HKEY hKey;
	boolean alreadyInstalled = FALSE;
	WCHAR wchModuleName[_MAX_PATH];
	DWORD dwModuleName = sizeof wchModuleName;
	DWORD result;
	WCHAR mszNotificationPackages[4096];
	DWORD cbNotificationPackages = sizeof mszNotificationPackages;
	DWORD dwType;

	if (RegOpenKeyW(HKEY_LOCAL_MACHINE,
			L"SYSTEM\\CurrentControlSet\\Control\\LSA", &hKey) != ERROR_SUCCESS)
		goto Fail;

	// Obtener el nombre del modulo
	if (dwModuleName == GetModuleFileNameW((HMODULE)getModuleHandle(), wchModuleName,
			dwModuleName))
		goto Fail;

	result = RegQueryValueExW(hKey, L"Notification Packages", NULL, &dwType,
			(LPBYTE) mszNotificationPackages, &cbNotificationPackages);
	if (result == ERROR_FILE_NOT_FOUND) {
		mszNotificationPackages[0] = 0;
		// Gestionar el alta
	} else if (result == ERROR_SUCCESS) {
		LPWSTR pointer = mszNotificationPackages;
		while (!alreadyInstalled && *pointer != 0) {
			if (wcscmp(wchModuleName, pointer) == 0)
				alreadyInstalled = TRUE;
			int len = wcslen(pointer);
			pointer += len + 1;
		}
	} else
		goto Fail;

	if (alreadyInstalled) {
		return TRUE;
	} else {
		LPWSTR pointer = mszNotificationPackages;
		int totalLen = 0;
		while (*pointer != 0) {
			int len = wcslen(pointer);
			totalLen += len + 1;
			pointer += len + 1;
		}
		cbNotificationPackages = (totalLen + wcslen(wchModuleName) + 2)
				* sizeof(WCHAR);

		if (cbNotificationPackages > sizeof mszNotificationPackages) {
			SetLastError(ERROR_BUFFER_OVERFLOW);
			goto Fail;
		}

		wcscpy(pointer, wchModuleName);
		int len = wcslen(wchModuleName);
		pointer[len + 1] = 0;
		if (ERROR_SUCCESS != RegSetValueExW(hKey, L"Notification Packages", 0,
				REG_MULTI_SZ, (LPBYTE) mszNotificationPackages,
				cbNotificationPackages))
			goto Fail;

	}

	return TRUE;

	Fail: {
		LPWSTR pstr;

		FormatMessageW(
				FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM,
				NULL, GetLastError(), 0, (LPWSTR) &pstr, 0, NULL);
		MessageBoxW(NULL, pstr, L"ErisADfilter Error", MB_OK);
		LocalFree(pstr);
		return FALSE;
	}
}

DECLSPEC_EXPORT
BOOL APIENTRY
UninstallLSA(void) {
	HKEY hKey;
	WCHAR wchModuleName[_MAX_PATH];
	DWORD dwModuleName = sizeof wchModuleName;
	WCHAR mszNotificationPackages[4096];
	DWORD cbNotificationPackages = sizeof mszNotificationPackages;
	DWORD dwType;
	DWORD result;
	boolean alreadyInstalled = FALSE;

	if (RegOpenKeyW(HKEY_LOCAL_MACHINE,
			L"SYSTEM\\CurrentControlSet\\Control\\LSA", &hKey) != ERROR_SUCCESS)
		goto Fail;

	// Obtener el nombre del modulo
	if (dwModuleName == GetModuleFileNameW((HMODULE)getModuleHandle(), wchModuleName,
			dwModuleName))
		goto Fail;


	result = RegQueryValueExW(hKey, L"Notification Packages", NULL,
			&dwType, (LPBYTE) mszNotificationPackages, &cbNotificationPackages);
	if (result == ERROR_FILE_NOT_FOUND) {
		mszNotificationPackages[0] = 0;
	} else if (result == ERROR_SUCCESS) {
		int newLen = 0;
		LPWSTR pointer = mszNotificationPackages;
		LPWSTR oldPointer = pointer;
		while (!alreadyInstalled && *pointer != 0) {
			oldPointer = pointer;
			int len = wcslen(pointer);
			if (wcscmp(wchModuleName, pointer) == 0)
				alreadyInstalled = TRUE;
			else
				newLen += len + 1;
			pointer += len + 1;
		}
		while (alreadyInstalled && *pointer != 0) {
			int len = wcslen(pointer);
			wcscpy(oldPointer, pointer);
			pointer += len + 1;
			newLen += len + 1;
		}
		cbNotificationPackages = (newLen + 1) * sizeof(WCHAR);
		pointer[newLen + 1] = 0;
		if (ERROR_SUCCESS != RegSetValueExW(hKey, L"Notification Packages", 0,
				REG_MULTI_SZ, (LPBYTE) mszNotificationPackages,
				cbNotificationPackages))
			goto Fail;
		return TRUE;
	} else
		goto Fail;

	return FALSE;

	Fail: {
		LPWSTR pstr;

		FormatMessageW(
				FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM,
				NULL, GetLastError(), 0, (LPWSTR) &pstr, 0, NULL);
		MessageBoxW(NULL, pstr, L"ErisADfilter Error", MB_OK);
		LocalFree(pstr);
		return FALSE;
	}
}

BOOL APIENTRY DllMain(HANDLE hModule, DWORD ul_reason_for_call,
		LPVOID lpReserved) {
	switch (ul_reason_for_call) {
	case DLL_PROCESS_ATTACH:
		hInstance = hModule;
	case DLL_THREAD_ATTACH:
	case DLL_THREAD_DETACH:
	case DLL_PROCESS_DETACH:
		break;
	}
	return TRUE;
}

}
