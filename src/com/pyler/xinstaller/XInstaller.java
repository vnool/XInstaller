package com.pyler.xinstaller;

import java.io.File;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Hashtable;

import android.app.ActivityManager;
import android.app.AndroidAppHelper;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.Process;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XInstaller implements IXposedHookZygoteInit,
		IXposedHookLoadPackage {
	public XSharedPreferences prefs;
	public boolean signaturesCheck;
	public boolean signaturesCheckFDroid;
	public boolean keepAppsData;
	public boolean downgradeApps;
	public boolean forwardLock;
	public boolean disableSystemApps;
	public boolean installUnknownApps;
	public boolean verifyApps;
	public boolean installAppsOnExternal;
	public boolean deviceAdmins;
	public boolean autoInstall;
	public boolean autoUninstall;
	public boolean autoCloseUninstall;
	public boolean autoCloseInstall;
	public boolean autoLaunchInstall;
	public boolean permissionsCheck;
	public boolean backupApkFiles;
	public boolean installUnsignedApps;
	public boolean verifyJar;
	public boolean verifySignature;
	public boolean showButtons;
	public boolean appsDebugging;
	public boolean autoBackup;
	public boolean showPackageName;
	public boolean showVersions;
	public boolean deleteApkFiles;
	public boolean moveApps;
	public boolean checkSdkVersion;
	public boolean installBackground;
	public boolean uninstallBackground;
	public XC_MethodHook checkSignaturesHook;
	public XC_MethodHook deletePackageHook;
	public XC_MethodHook installPackageHook;
	public XC_MethodHook systemAppsHook;
	public XC_MethodHook unknownAppsHook;
	public XC_MethodHook verifyAppsHook;
	public XC_MethodHook deviceAdminsHook;
	public XC_MethodHook fDroidInstallHook;
	public XC_MethodHook autoInstallHook;
	public XC_MethodHook autoUninstallHook;
	public XC_MethodHook autoCloseUninstallHook;
	public XC_MethodHook autoCloseInstallHook;
	public XC_MethodHook packageManagerHook;
	public XC_MethodHook checkPermissionsHook;
	public XC_MethodHook activityManagerHook;
	public XC_MethodHook installUnsignedAppsHook;
	public XC_MethodHook verifyJarHook;
	public XC_MethodHook verifySignatureHook;
	public XC_MethodHook showButtonsHook;
	public XC_MethodHook appsDebuggingHook;
	public XC_MethodHook autoBackupHook;
	public XC_MethodHook showPackageNameHook;
	public XC_MethodHook scanPackageHook;
	public XC_MethodHook verifySignaturesHook;
	public XC_MethodHook moveAppsHook;
	public XC_MethodHook checkSdkVersionHook;
	public XC_MethodHook installBackgroundHook;
	public XC_MethodHook uninstallBackgroundHook;
	public boolean JB_MR1_NEWER;
	public boolean JB_MR2_NEWER;
	public boolean KITKAT_NEWER;
	public boolean APIEnabled;
	public boolean signatureCheckOff;
	public Context mContext;
	public Object packageManagerObj;
	public Object activityManagerObj;
	public BroadcastReceiver systemAPI;

	// classes
	public Class<?> packageManagerClass = XposedHelpers.findClass(
			Common.PACKAGEMANAGERSERVICE, null);
	public Class<?> activityManagerClass = ActivityManager.class;
	public Class<?> devicePolicyManagerClass = XposedHelpers.findClass(
			Common.DEVICEPOLICYMANAGERSERVICE, null);
	public Class<?> packageParserClass = XposedHelpers.findClass(
			Common.PACKAGEPARSER, null);
	public Class<?> jarVerifierClass = XposedHelpers.findClass(
			Common.JARVERIFIER, null);
	public Class<?> signatureClass = XposedHelpers.findClass(Common.SIGNATURE,
			null);

	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {
		prefs = new XSharedPreferences(XInstaller.class.getPackage().getName());
		prefs.makeWorldReadable();
		APIEnabled = false;
		signatureCheckOff = true;

		// hooks
		packageManagerHook = new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(final MethodHookParam param)
					throws Throwable {
				packageManagerObj = param.thisObject;
			}
		};

		activityManagerHook = new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(final MethodHookParam param)
					throws Throwable {
				activityManagerObj = param.thisObject;
				Context context = (Context) XposedHelpers.getObjectField(
						param.thisObject, "mContext");
				if (context == null && param.args.length != 0) {
					context = (Context) param.args[0];
				}
				if (context != null) {
					if (isModuleEnabled() && !APIEnabled
							&& Common.ANDROID.equals(context.getPackageName())) {
						mContext = context;
						IntentFilter systemApi = new IntentFilter();
						systemApi
								.addAction(Common.ACTION_DISABLE_SIGNATURE_CHECK);
						systemApi
								.addAction(Common.ACTION_ENABLE_SIGNATURE_CHECK);
						systemApi
								.addAction(Common.ACTION_DISABLE_PERMISSION_CHECK);
						systemApi
								.addAction(Common.ACTION_ENABLE_PERMISSION_CHECK);
						systemApi.addAction(Common.ACTION_INSTALL_PACKAGE);
						systemApi.addAction(Common.ACTION_CLEAR_APP_DATA);
						systemApi.addAction(Common.ACTION_FORCE_STOP_PACKAGE);
						systemApi.addAction(Common.ACTION_DELETE_PACKAGE);
						systemApi.addAction(Common.ACTION_CLEAR_APP_CACHE);
						systemApi.addAction(Common.ACTION_MOVE_PACKAGE);
						systemApi.addAction(Common.ACTION_RUN_XINSTALLER);
						systemApi.addAction(Common.ACTION_REMOVE_TASK);
						systemApi.addAction(Common.ACTION_SET_INSTALL_LOCATION);
						systemApi
								.addAction(Common.ACTION_ENABLE_SDK_VERSION_CHECK);
						systemApi
								.addAction(Common.ACTION_DISABLE_SDK_VERSION_CHECK);
						mContext.registerReceiver(systemAPI, systemApi);
						APIEnabled = true;
					}
				}
			}
		};

		checkSdkVersionHook = new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				checkSdkVersion = prefs.getBoolean(
						Common.PREF_DISABLE_SDK_VERSION_CHECK, false);
				if (isModuleEnabled() && checkSdkVersion) {
					XposedHelpers.setObjectField(param.thisObject,
							"SDK_VERSION", Common.LATEST_ANDROID_RELEASE);
				}
			}
		};

		moveAppsHook = new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				moveApps = prefs.getBoolean(Common.PREF_ENABLE_MOVE_APP, false);
				if (isModuleEnabled() && moveApps) {
					param.setResult(true);
					return;
				}
			}
		};

		verifySignaturesHook = new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				signaturesCheck = prefs.getBoolean(
						Common.PREF_DISABLE_SIGNATURE_CHECK, false);
				if (isModuleEnabled() && signaturesCheck) {
					param.setResult(true);
					return;
				}
			}
		};

		scanPackageHook = new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {
				signatureCheckOff = false;
			}

			@Override
			protected void afterHookedMethod(MethodHookParam param)
					throws Throwable {
				signatureCheckOff = true;
			}
		};

		showPackageNameHook = new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				showPackageName = prefs.getBoolean(
						Common.PREF_ENABLE_SHOW_PACKAGE_NAME, false);
				mContext = AndroidAppHelper.currentApplication();
				PackageInfo pkgInfo = (PackageInfo) param.args[0];
				TextView appVersion = (TextView) XposedHelpers.getObjectField(
						param.thisObject, "mAppVersion");
				String version = appVersion.getText().toString();
				final String packageName = pkgInfo.packageName;
				if (isModuleEnabled() && showPackageName) {
					appVersion.setText(packageName + "\n" + version);
					appVersion.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							ClipboardManager clipboard = (ClipboardManager) mContext
									.getSystemService(Context.CLIPBOARD_SERVICE);
							ClipData clip = ClipData.newPlainText("text",
									packageName);
							clipboard.setPrimaryClip(clip);
						}
					});
				}

			}
		};

		autoBackupHook = new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				autoBackup = prefs.getBoolean(Common.PREF_ENABLE_AUTO_BACKUP,
						false);
				Button mAllowButton = (Button) XposedHelpers.getObjectField(
						param.thisObject, "mAllowButton");
				if (isModuleEnabled() && autoBackup) {
					mAllowButton.performClick();
				}
			}
		};

		appsDebuggingHook = new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				appsDebugging = prefs.getBoolean(
						Common.PREF_ENABLE_APP_DEBUGGING, false);
				int flags = (Integer) param.args[5];
				if (isModuleEnabled()
						&& (flags & Common.DEBUG_ENABLE_DEBUGGER) == 0
						&& appsDebugging) {
					// we dont have this flag, add it
					flags |= Common.DEBUG_ENABLE_DEBUGGER;
				}
				param.args[5] = flags;
			}
		};

		showButtonsHook = new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				showButtons = prefs.getBoolean(Common.PREF_ENABLE_SHOW_BUTTON,
						false);
				if (isModuleEnabled() && showButtons) {
					param.setResult(true);
					return;
				}
			}
		};

		verifyJarHook = new XC_MethodHook() {
			@SuppressWarnings("unchecked")
			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				verifyJar = prefs.getBoolean(Common.PREF_DISABLE_VERIFY_JAR,
						false);
				if (isModuleEnabled() && verifyJar) {
					String name = (String) XposedHelpers.getObjectField(
							param.thisObject, "name");
					Certificate[] certificates = (Certificate[]) XposedHelpers
							.getObjectField(param.thisObject, "certificates");
					Hashtable<String, Certificate[]> verifiedEntries = null;
					try {
						verifiedEntries = (Hashtable<String, Certificate[]>) XposedHelpers
								.findField(param.thisObject.getClass(),
										"verifiedEntries")
								.get(param.thisObject);
					} catch (NoSuchFieldError e) {
						verifiedEntries = (Hashtable<String, Certificate[]>) XposedHelpers
								.getObjectField(XposedHelpers
										.getSurroundingThis(param.thisObject),
										"verifiedEntries");
					}
					verifiedEntries.put(name, certificates);
					param.setResult(null);
				}
			}
		};

		verifySignatureHook = new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				verifySignature = prefs.getBoolean(
						Common.PREF_DISABLE_VERIFY_SIGNATURE, false);
				if (isModuleEnabled() && verifySignature) {
					param.setResult(true);
					return;
				}
			}
		};

		installUnsignedAppsHook = new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				installUnsignedApps = prefs.getBoolean(
						Common.PREF_ENABLE_INSTALL_UNSIGNED_APP, false);
				if (isModuleEnabled() && installUnsignedApps) {
					param.setResult(true);
					return;
				}
			}
		};

		checkPermissionsHook = new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				permissionsCheck = prefs.getBoolean(
						Common.PREF_DISABLE_PERMISSION_CHECK, false);
				if (isModuleEnabled() && permissionsCheck) {
					param.setResult(PackageManager.PERMISSION_GRANTED);
					return;
				}
			}
		};

		checkSignaturesHook = new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				signaturesCheck = prefs.getBoolean(
						Common.PREF_DISABLE_SIGNATURE_CHECK, false);
				if (isModuleEnabled() && signaturesCheck && signatureCheckOff) {
					param.setResult(PackageManager.SIGNATURE_MATCH);
					return;
				}
			}
		};

		installPackageHook = new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				downgradeApps = prefs.getBoolean(
						Common.PREF_ENABLED_DOWNGRADE_APP, false);
				forwardLock = prefs.getBoolean(
						Common.PREF_DISABLE_FORWARD_LOCK, false);
				installAppsOnExternal = prefs.getBoolean(
						Common.PREF_ENABLE_INSTALL_EXTERNAL_STORAGE, false);
				backupApkFiles = prefs.getBoolean(
						Common.PREF_ENABLE_BACKUP_APK_FILE, false);
				installBackground = prefs.getBoolean(
						Common.PREF_DISABLE_INSTALL_BACKGROUND, false);
				int ID = JB_MR1_NEWER ? 2 : 1;
				int flags = (Integer) param.args[ID];
				if (isModuleEnabled()
						&& (flags & Common.INSTALL_ALLOW_DOWNGRADE) == 0
						&& downgradeApps) {
					// we dont have this flag, add it
					flags |= Common.INSTALL_ALLOW_DOWNGRADE;
				}
				if (isModuleEnabled()
						&& (flags & Common.INSTALL_FORWARD_LOCK) != 0
						&& forwardLock) {
					// we have this flag, remove it
					flags &= ~Common.INSTALL_FORWARD_LOCK;
				}
				if (isModuleEnabled() && (flags & Common.INSTALL_EXTERNAL) == 0
						&& installAppsOnExternal) {
					// we dont have this flag, add it
					flags |= Common.INSTALL_EXTERNAL;
				}
				param.args[ID] = flags;

				if (isModuleEnabled() && backupApkFiles) {
					Uri packageUri = (Uri) param.args[0];
					String apkFile = packageUri.getPath();
					backupApkFile(apkFile);
				}

				if (isModuleEnabled() && installBackground) {
					if (Binder.getCallingUid() == Common.ROOT_UID) {
						param.setResult(null);
					}
				}
			}

		};

		deletePackageHook = new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				keepAppsData = prefs.getBoolean(
						Common.PREF_ENABLE_KEEP_APP_DATA, false);
				uninstallBackground = prefs.getBoolean(
						Common.PREF_DISABLE_UNINSTALL_BACKGROUND, false);
				int ID = JB_MR2_NEWER ? 3 : 2;
				int flags = (Integer) param.args[ID];
				if (isModuleEnabled() && (flags & Common.DELETE_KEEP_DATA) == 0
						&& keepAppsData) {
					// we dont have this flag, add it
					flags |= Common.DELETE_KEEP_DATA;
				}
				param.args[ID] = flags;

				if (isModuleEnabled() && uninstallBackground) {
					if (Binder.getCallingUid() == Common.ROOT_UID) {
						param.setResult(null);
					}
				}
			}

		};

		systemAppsHook = new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				disableSystemApps = prefs.getBoolean(
						Common.PREF_ENABLE_DISABLE_SYSTEM_APP, false);
				if (isModuleEnabled() && disableSystemApps) {
					param.setResult(false);
					return;
				}

			}

		};

		unknownAppsHook = new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				installUnknownApps = prefs.getBoolean(
						Common.PREF_ENABLE_INSTALL_UNKNOWN_APP, false);
				if (isModuleEnabled() && installUnknownApps) {
					param.setResult(true);
					return;
				}

			}

		};

		verifyAppsHook = new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				verifyApps = prefs.getBoolean(Common.PREF_DISABLE_VERIFY_APP,
						false);
				if (isModuleEnabled() && verifyApps) {
					param.setResult(false);
					return;
				}

			}

		};

		deviceAdminsHook = new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				deviceAdmins = prefs.getBoolean(
						Common.PREF_ENABLE_UNINSTALL_DEVICE_ADMIN, false);
				if (isModuleEnabled() && deviceAdmins) {
					param.setResult(false);
					return;
				}

			}

		};

		fDroidInstallHook = new XC_MethodHook() {
			String mInstalledSigID = null;

			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				signaturesCheckFDroid = prefs.getBoolean(
						Common.PREF_DISABLE_SIGNATURE_CHECK_FDROID, false);
				mInstalledSigID = (String) XposedHelpers.getObjectField(
						param.thisObject, "mInstalledSigID");
				if (isModuleEnabled() && signaturesCheckFDroid) {
					XposedHelpers.setObjectField(param.thisObject,
							"mInstalledSigID", null);
				}
			}

			@Override
			protected void afterHookedMethod(MethodHookParam param)
					throws Throwable {
				if (isModuleEnabled() && signaturesCheckFDroid) {
					XposedHelpers.setObjectField(param.thisObject,
							"mInstalledSigID", mInstalledSigID);
				}
			}

		};

		autoInstallHook = new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				autoInstall = prefs.getBoolean(Common.PREF_ENABLE_AUTO_INSTALL,
						false);
				showVersions = prefs.getBoolean(
						Common.PREF_ENABLE_SHOW_VERSION, false);
				mContext = AndroidAppHelper.currentApplication();
				Button mOk = (Button) XposedHelpers.getObjectField(
						param.thisObject, "mOk");
				if (isModuleEnabled() && autoInstall) {
					XposedHelpers.setBooleanField(param.thisObject,
							"mOkCanInstall", true);
					mOk.performClick();
				}
				if (isModuleEnabled() && showVersions) {
					Resources res = getXInstallerContext().getResources();
					PackageManager pm = mContext.getPackageManager();
					PackageInfo mPkgInfo = (PackageInfo) XposedHelpers
							.getObjectField(param.thisObject, "mPkgInfo");
					String packageName = mPkgInfo.packageName;
					String versionInfo = res.getString(R.string.new_version)
							+ ": " + mPkgInfo.versionName;
					try {
						PackageInfo pi = pm.getPackageInfo(packageName, 0);
						String currentVersion = pi.versionName;
						versionInfo += "\n"
								+ res.getString(R.string.current_version)
								+ ": " + currentVersion;

					} catch (PackageManager.NameNotFoundException e) {
					}
					Toast.makeText(mContext, versionInfo, Toast.LENGTH_LONG)
							.show();
				}
			}
		};

		autoUninstallHook = new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				autoUninstall = prefs.getBoolean(
						Common.PREF_ENABLE_AUTO_UNINSTALL, false);
				Button mOk = (Button) XposedHelpers.getObjectField(
						param.thisObject, "mOk");
				if (isModuleEnabled() && autoUninstall) {
					mOk.performClick();
				}
			}

		};

		autoCloseUninstallHook = new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				autoCloseUninstall = prefs.getBoolean(
						Common.PREF_ENABLE_AUTO_CLOSE_UNINSTALL, false);
				Button mOk = (Button) XposedHelpers.getObjectField(
						param.thisObject, "mOkButton");
				if (isModuleEnabled() && autoCloseUninstall) {
					mOk.performClick();
				}
			}

		};

		autoCloseInstallHook = new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				autoCloseInstall = prefs.getBoolean(
						Common.PREF_ENABLE_AUTO_CLOSE_INSTALL, false);
				autoLaunchInstall = prefs.getBoolean(
						Common.PREF_ENABLE_LAUNCH_INSTALL, false);
				deleteApkFiles = prefs.getBoolean(
						Common.PREF_ENABLE_DELETE_APK_FILE_INSTALL, false);
				Button mDone = (Button) XposedHelpers.getObjectField(
						XposedHelpers.getSurroundingThis(param.thisObject),
						"mDoneButton");

				if (isModuleEnabled() && autoCloseInstall) {
					mDone.performClick();
				}

				Button mLaunch = (Button) XposedHelpers.getObjectField(
						XposedHelpers.getSurroundingThis(param.thisObject),
						"mLaunchButton");
				if (isModuleEnabled() && autoLaunchInstall) {
					mLaunch.performClick();
				}

				if (isModuleEnabled() && deleteApkFiles) {
					Uri packageUri = (Uri) XposedHelpers.getObjectField(
							XposedHelpers.getSurroundingThis(param.thisObject),
							"mPackageURI");
					String apkFile = packageUri.getPath();
					deleteApkFile(apkFile);
				}
			}

		};

		// system API
		systemAPI = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {

				String action = intent.getAction();
				Bundle extras = intent.getExtras();
				boolean hasExtras = (extras != null) ? true : false;
				if (Common.ACTION_DISABLE_SIGNATURE_CHECK.equals(action)) {
					disableSignatureCheck(true);
				} else if (Common.ACTION_ENABLE_SIGNATURE_CHECK.equals(action)) {
					disableSignatureCheck(false);
				} else if (Common.ACTION_DISABLE_PERMISSION_CHECK
						.equals(action)) {
					disablePermissionCheck(true);
				} else if (Common.ACTION_ENABLE_PERMISSION_CHECK.equals(action)) {
					disablePermissionCheck(false);
				} else if (Common.ACTION_INSTALL_PACKAGE.equals(action)) {
					if (hasExtras) {
						String apkFile = extras.getString(Common.FILE);
						Integer flag = extras.getInt(Common.FLAGS);
						if (apkFile != null) {
							if (flag != null) {
								int flags = flag;
								installPackage(apkFile, flags);
							} else {
								installPackage(apkFile, 0);
							}
						}
					}
				} else if (Common.ACTION_CLEAR_APP_DATA.equals(action)) {
					if (hasExtras) {
						String packageName = extras.getString(Common.PACKAGE);
						if (packageName != null) {
							clearAppData(packageName);
						}
					}
				} else if (Common.ACTION_FORCE_STOP_PACKAGE.equals(action)) {
					if (hasExtras) {
						String packageName = extras.getString(Common.PACKAGE);
						if (packageName != null) {
							forceStopPackage(packageName);
						}
					}
				} else if (Common.ACTION_DELETE_PACKAGE.equals(action)) {
					if (hasExtras) {
						String packageName = extras.getString(Common.PACKAGE);
						Integer flag = extras.getInt(Common.FLAGS);
						if (packageName != null) {
							if (flag != null) {
								int flags = flag;
								deletePackage(packageName, flags);
							} else {
								deletePackage(packageName, 0);
							}
						}
					}
				} else if (Common.ACTION_CLEAR_APP_CACHE.equals(action)) {
					if (hasExtras) {
						String packageName = extras.getString(Common.PACKAGE);
						if (packageName != null) {
							clearAppCache(packageName);
						}
					}
				} else if (Common.ACTION_MOVE_PACKAGE.equals(action)) {
					if (hasExtras) {
						String packageName = extras.getString(Common.PACKAGE);
						Integer flag = extras.getInt(Common.FLAGS);
						if (packageName != null) {
							if (flag != null) {
								int flags = flag;
								movePackage(packageName, flags);
							}
						}
					}
				} else if (Common.ACTION_RUN_XINSTALLER.equals(action)) {
					runXInstaller();
				} else if (Common.ACTION_REMOVE_TASK.equals(action)) {
					if (hasExtras) {
						Integer taskId = extras.getInt(Common.TASK);
						if (taskId != null) {
							int task = taskId;
							removeTask(task);
						}
					}
				} else if (Common.ACTION_SET_INSTALL_LOCATION.equals(action)) {
					if (hasExtras) {
						Integer loc = extras.getInt(Common.LOCATION);
						if (loc != null) {
							int location = loc;
							setInstallLocation(location);
						}
					}
				}
			}

		};

		// checks
		int SDK = Build.VERSION.SDK_INT;
		JB_MR1_NEWER = (SDK >= Build.VERSION_CODES.JELLY_BEAN_MR1) ? true
				: false;
		JB_MR2_NEWER = (SDK >= Build.VERSION_CODES.JELLY_BEAN_MR2) ? true
				: false;
		KITKAT_NEWER = (SDK >= Build.VERSION_CODES.KITKAT) ? true : false;

		// enablers
		try {
			XposedHelpers.findAndHookMethod(packageParserClass, "parsePackage",
					Resources.class, XmlResourceParser.class, int.class,
					String[].class, checkSdkVersionHook);
		} catch (NoSuchMethodError nsm) {
			try {
				XposedHelpers.findAndHookMethod(packageParserClass,
						"parsePackage", Resources.class,
						XmlResourceParser.class, int.class, boolean.class,
						String[].class, checkSdkVersionHook);
			} catch (NoSuchMethodError nsm2) {
			}
		}

		if (JB_MR1_NEWER) {
			XposedHelpers.findAndHookMethod(packageManagerClass,
					"scanPackageLI",
					"android.content.pm.PackageParser$Package", int.class,
					int.class, long.class, "android.os.UserHandle",
					scanPackageHook);
		} else {
			XposedHelpers.findAndHookMethod(packageManagerClass,
					"scanPackageLI",
					"android.content.pm.PackageParser$Package", int.class,
					int.class, long.class, scanPackageHook);
		}

		XposedHelpers.findAndHookMethod(packageManagerClass,
				"verifySignaturesLP", "com.android.server.pm.PackageSetting",
				"android.content.pm.PackageParser$Package",
				verifySignaturesHook);

		if (JB_MR1_NEWER) {
			try {
				XposedHelpers.findAndHookMethod(Process.class, "start",
						String.class, String.class, int.class, int.class,
						int[].class, int.class, int.class, int.class,
						String.class, String[].class, appsDebuggingHook);
			} catch (NoSuchMethodError nsm) {
				try {
					XposedHelpers.findAndHookMethod(Process.class, "start",
							String.class, String.class, int.class, int.class,
							int[].class, int.class, int.class, int.class,
							String.class, boolean.class, String[].class,
							appsDebuggingHook);
				} catch (NoSuchMethodError nsm2) {
				}
			}
		}

		XposedHelpers.findAndHookMethod(MessageDigest.class, "isEqual",
				byte[].class, byte[].class, verifySignatureHook);

		XposedHelpers.findAndHookMethod(View.class,
				"onFilterTouchEventForSecurity", MotionEvent.class,
				showButtonsHook);

		if (JB_MR1_NEWER) {
			XposedHelpers.findAndHookMethod(packageManagerClass,
					"isVerificationEnabled", int.class, verifyAppsHook);
		} else {
			XposedHelpers.findAndHookMethod(packageManagerClass,
					"isVerificationEnabled", verifyAppsHook);
		}

		XposedHelpers.findAndHookMethod(signatureClass, "verify", byte[].class,
				int.class, int.class, verifySignatureHook);

		XposedHelpers.findAndHookMethod(signatureClass, "verify", byte[].class,
				verifySignatureHook);

		XposedHelpers.findAndHookMethod(jarVerifierClass, "verify",
				verifyJarHook);

		XposedHelpers.findAndHookMethod(packageParserClass,
				"collectCertificates",
				"android.content.pm.PackageParser$Package", int.class,
				installUnsignedAppsHook);

		XposedBridge.hookAllConstructors(packageManagerClass,
				packageManagerHook);
		XposedBridge.hookAllConstructors(activityManagerClass,
				activityManagerHook);
		XposedHelpers.findAndHookMethod(packageManagerClass,
				"compareSignatures", Signature[].class, Signature[].class,
				checkSignaturesHook);

		XposedHelpers.findAndHookMethod(packageManagerClass, "checkSignatures",
				String.class, String.class, checkSignaturesHook);

		XposedHelpers
				.findAndHookMethod(packageManagerClass, "checkUidSignatures",
						int.class, int.class, checkSignaturesHook);

		XposedHelpers.findAndHookMethod(packageManagerClass, "checkPermission",
				String.class, String.class, checkPermissionsHook);

		XposedHelpers.findAndHookMethod(packageManagerClass,
				"checkUidPermission", String.class, int.class,
				checkPermissionsHook);

		if (JB_MR1_NEWER) {
			XposedHelpers.findAndHookMethod(packageManagerClass,
					"installPackageWithVerificationAndEncryption", Uri.class,
					"android.content.pm.IPackageInstallObserver", int.class,
					String.class, "android.content.pm.VerificationParams",
					"android.content.pm.ContainerEncryptionParams",
					installPackageHook);
		} else {
			XposedHelpers.findAndHookMethod(packageManagerClass,
					"installPackageWithVerification", Uri.class,
					"android.content.pm.IPackageInstallObserver", int.class,
					String.class, Uri.class,
					"android.content.pm.ManifestDigest",
					"android.content.pm.ContainerEncryptionParams",
					installPackageHook);
		}

		if (JB_MR2_NEWER) {
			XposedHelpers.findAndHookMethod(packageManagerClass,
					"deletePackageAsUser", String.class,
					"android.content.pm.IPackageDeleteObserver", int.class,
					int.class, deletePackageHook);
		} else {
			XposedHelpers.findAndHookMethod(packageManagerClass,
					"deletePackage", String.class,
					"android.content.pm.IPackageDeleteObserver", int.class,
					deletePackageHook);
		}

		if (JB_MR1_NEWER) {
			XposedHelpers.findAndHookMethod(devicePolicyManagerClass,
					"packageHasActiveAdmins", String.class, int.class,
					deviceAdminsHook);
		} else {
			XposedHelpers.findAndHookMethod(devicePolicyManagerClass,
					"packageHasActiveAdmins", String.class, deviceAdminsHook);
		}

	}

	@Override
	public void handleLoadPackage(final LoadPackageParam lpparam)
			throws Throwable {
		if (Common.PACKAGEINSTALLER_PKG.equals(lpparam.packageName)) {
			XposedHelpers.findAndHookMethod(Common.PACKAGEINSTALLERACTIVITY,
					lpparam.classLoader, "isInstallingUnknownAppsAllowed",
					unknownAppsHook);
			if (KITKAT_NEWER) {
				XposedHelpers.findAndHookMethod(
						Common.PACKAGEINSTALLERACTIVITY, lpparam.classLoader,
						"isVerifyAppsEnabled", verifyAppsHook);
			}
			XposedHelpers
					.findAndHookMethod(Common.PACKAGEINSTALLERACTIVITY,
							lpparam.classLoader, "startInstallConfirm",
							autoInstallHook);
			XposedHelpers.findAndHookMethod(Common.UNINSTALLERACTIVITY,
					lpparam.classLoader, "onCreate", Bundle.class,
					autoUninstallHook);
			XposedHelpers.findAndHookMethod(Common.UNINSTALLAPPPROGRESS,
					lpparam.classLoader, "initView", autoCloseUninstallHook);
			XposedHelpers.findAndHookMethod(Common.INSTALLAPPPROGRESS + "$1",
					lpparam.classLoader, "handleMessage", Message.class,
					autoCloseInstallHook);
		}

		if (Common.SETTINGS_PKG.equals(lpparam.packageName)) {
			XposedHelpers
					.findAndHookMethod(Common.INSTALLEDAPPDETAILS,
							lpparam.classLoader, "isThisASystemPackage",
							systemAppsHook);
			XposedHelpers.findAndHookMethod(Common.INSTALLEDAPPDETAILS,
					lpparam.classLoader, "setAppLabelAndIcon",
					PackageInfo.class, showPackageNameHook);
			XposedHelpers.findAndHookMethod(Common.CANBEONSDCARDCHECKER,
					lpparam.classLoader, "check", ApplicationInfo.class,
					moveAppsHook);
		}

		if (Common.FDROID_PKG.equals(lpparam.packageName)) {
			XposedHelpers.findAndHookMethod(Common.FDROIDAPPDETAILS,
					lpparam.classLoader, "install",
					"org.fdroid.fdroid.data.Apk", fDroidInstallHook);
		}

		if (Common.BACKUPCONFIRM_PKG.equals(lpparam.packageName)) {
			XposedHelpers.findAndHookMethod(Common.BACKUPRESTORECONFIRMATION,
					lpparam.classLoader, "onCreate", Bundle.class,
					autoBackupHook);
		}
	}

	// system API
	public void forceStopPackage(String packageName) {
		XposedHelpers.callMethod(activityManagerObj, "forceStopPackage",
				packageName);
	}

	public void clearAppData(String packageName) {
		XposedHelpers.callMethod(activityManagerObj,
				"clearApplicationUserData", packageName, null);
	}

	public void removeTask(int taskId) {
		XposedHelpers.callMethod(activityManagerObj, "removeTask", taskId,
				Common.REMOVE_TASK_KILL_PROCESS);
	}

	public void clearAppCache(String packageName) {
		XposedHelpers.callMethod(packageManagerObj,
				"deleteApplicationCacheFiles", packageName, null);
	}

	public void movePackage(String packageName, int flags) {
		XposedHelpers.callMethod(packageManagerObj, "movePackage", packageName,
				null, flags);
	}

	public void installPackage(String apkFile, int flags) {
		Uri apk = Uri.fromFile(new File(apkFile));
		enableModule(false);
		if ((flags & Common.INSTALL_REPLACE_EXISTING) == 0) {
			flags |= Common.INSTALL_REPLACE_EXISTING;
		}
		XposedHelpers.callMethod(packageManagerObj, "installPackage", apk,
				null, flags);
		enableModule(true);

	}

	public void deletePackage(String packageName, int flags) {
		enableModule(false);
		if (JB_MR2_NEWER) {
			int userId = (Integer) XposedHelpers.callStaticMethod(
					ActivityManager.class, "getCurrentUser");
			XposedHelpers.callMethod(packageManagerObj, "deletePackageAsUser",
					packageName, null, userId, flags);
		} else {
			XposedHelpers.callMethod(packageManagerObj, "deletePackage",
					packageName, null, flags);
		}
		enableModule(true);
	}

	public void setInstallLocation(int location) {
		XposedHelpers.callMethod(packageManagerObj, "setInstallLocation",
				location);

	}

	public void disableSignatureCheck(boolean disabled) {
		Intent disableSignatureCheck = new Intent(Common.ACTION_SET_PREFERENCE);
		disableSignatureCheck.setPackage(Common.PACKAGE_NAME);
		disableSignatureCheck.putExtra(Common.PREFERENCE,
				Common.PREF_DISABLE_SIGNATURE_CHECK);
		disableSignatureCheck.putExtra(Common.VALUE, disabled);
		mContext.sendBroadcast(disableSignatureCheck);

	}

	public void disablePermissionCheck(boolean disabled) {
		Intent disablePermissionCheck = new Intent(Common.ACTION_SET_PREFERENCE);
		disablePermissionCheck.setPackage(Common.PACKAGE_NAME);
		disablePermissionCheck.putExtra(Common.PREFERENCE,
				Common.PREF_DISABLE_PERMISSION_CHECK);
		disablePermissionCheck.putExtra(Common.VALUE, disabled);
		mContext.sendBroadcast(disablePermissionCheck);

	}

	public void runXInstaller() {
		Intent launchIntent = mContext.getPackageManager()
				.getLaunchIntentForPackage(Common.PACKAGE_NAME);
		if (launchIntent != null) {
			launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			mContext.startActivity(launchIntent);
		}
	}

	public boolean isModuleEnabled() {
		prefs.reload();
		boolean enabled = prefs.getBoolean(Common.PREF_ENABLE_MODULE, true);
		return enabled;
	}

	public void enableModule(boolean enabled) {
		Intent enableModule = new Intent(Common.ACTION_SET_PREFERENCE);
		enableModule.setPackage(Common.PACKAGE_NAME);
		enableModule.putExtra(Common.PREFERENCE, Common.PREF_ENABLE_MODULE);
		enableModule.putExtra(Common.VALUE, enabled);
		mContext.sendBroadcast(enableModule);

	}

	public Context getXInstallerContext() {
		Context XInstallerContext = null;
		;
		try {
			XInstallerContext = mContext.createPackageContext(
					Common.PACKAGE_NAME, 0);
		} catch (NameNotFoundException e) {
		}
		return XInstallerContext;
	}

	public void backupApkFile(String apkFile) {
		Intent backupApkFile = new Intent(Common.ACTION_BACKUP_APK_FILE);
		backupApkFile.setPackage(Common.PACKAGE_NAME);
		backupApkFile.putExtra(Common.FILE, apkFile);
		mContext.sendBroadcast(backupApkFile);
	}

	public void deleteApkFile(String apkFile) {
		Intent deleteApkFile = new Intent(Common.ACTION_DELETE_APK_FILE);
		deleteApkFile.setPackage(Common.PACKAGE_NAME);
		deleteApkFile.putExtra(Common.FILE, apkFile);
		mContext.sendBroadcast(deleteApkFile);
	}

}
