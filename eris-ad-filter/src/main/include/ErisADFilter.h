/*
 * ErisADServcie.h
 *
 *  Created on: 11/12/2009
 *      Author: u07286
 */

#ifndef ERISADSERVCIE_H_
#define ERISADSERVCIE_H_

#ifdef __cplusplus
extern "C" {
#endif
#define MAXUSERLENGTH 127
struct PasswordChange {
	WCHAR szUser[128];
	WCHAR szPassword[128];
};

HANDLE getModuleHandle () ;
HANDLE openSpoolFile ();
BOOL readPasswordChange (HANDLE f, struct PasswordChange *change, BOOL skip);
BOOL writePasswordChange (HANDLE f, struct PasswordChange *change);
BOOL sendMessage (struct PasswordChange *change);
void setQueueDebug(BOOL bDebug);
void deletePasswordChange (HANDLE f) ;
BOOL configureEris (LPWSTR f, BOOL allowUnknownCA, LPWSTR d) ;
BOOL reconfigureEris () ;

BOOL APIENTRY InstallLSA();
BOOL APIENTRY UninstallLSA();


#ifndef DECLSPEC_EXPORT
#define DECLSPEC_EXPORT __declspec(dllexport)
#endif

#ifdef __cplusplus
}
#endif
#endif /* ERISADSERVCIE_H_ */
