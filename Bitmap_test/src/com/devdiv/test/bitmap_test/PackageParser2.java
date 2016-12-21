package com.devdiv.test.bitmap_test;
//
//Cross Reference: PackageParser.java
//xref: /frameworks/base/core/java/android/content/pm/PackageParser.java
//HomeHistoryAnnotateLine#NavigateDownload
//  Search only in PackageParser.java
//1/*
//2 * Copyright (C) 2007 The Android Open Source Project
//3 *
//4 * Licensed under the Apache License, Version 2.0 (the "License");
//5 * you may not use this file except in compliance with the License.
//6 * You may obtain a copy of the License at
//7 *
//8 *      http://www.apache.org/licenses/LICENSE-2.0
//9 *
//10 * Unless required by applicable law or agreed to in writing, software
//11 * distributed under the License is distributed on an "AS IS" BASIS,
//12 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//13 * See the License for the specific language governing permissions and
//14 * limitations under the License.
//15 */
//16
//17package android.content.pm;
//18
//19import static android.content.pm.PackageManager.INSTALL_PARSE_FAILED_BAD_MANIFEST;
//20import static android.content.pm.PackageManager.INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME;
//21import static android.content.pm.PackageManager.INSTALL_PARSE_FAILED_CERTIFICATE_ENCODING;
//22import static android.content.pm.PackageManager.INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES;
//23import static android.content.pm.PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//24import static android.content.pm.PackageManager.INSTALL_PARSE_FAILED_NOT_APK;
//25import static android.content.pm.PackageManager.INSTALL_PARSE_FAILED_NO_CERTIFICATES;
//26import static android.content.pm.PackageManager.INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION;
//27
//28import android.app.ActivityManager;
//29import android.content.ComponentName;
//30import android.content.Intent;
//31import android.content.IntentFilter;
//32import android.content.res.AssetManager;
//33import android.content.res.Configuration;
//34import android.content.res.Resources;
//35import android.content.res.TypedArray;
//36import android.content.res.XmlResourceParser;
//37import android.os.Build;
//38import android.os.Bundle;
//39import android.os.PatternMatcher;
//40import android.os.UserHandle;
//41import android.text.TextUtils;
//42import android.util.ArrayMap;
//43import android.util.ArraySet;
//44import android.util.AttributeSet;
//45import android.util.Base64;
//46import android.util.DisplayMetrics;
//47import android.util.Log;
//48import android.util.Pair;
//49import android.util.Slog;
//50import android.util.TypedValue;
//51
//52import com.android.internal.util.ArrayUtils;
//53import com.android.internal.util.XmlUtils;
//54
//55import libcore.io.IoUtils;
//56
//57import org.xmlpull.v1.XmlPullParser;
//58import org.xmlpull.v1.XmlPullParserException;
//59
//60import java.io.File;
//61import java.io.IOException;
//62import java.io.InputStream;
//63import java.io.PrintWriter;
//64import java.security.GeneralSecurityException;
//65import java.security.KeyFactory;
//66import java.security.NoSuchAlgorithmException;
//67import java.security.PublicKey;
//68import java.security.cert.Certificate;
//69import java.security.cert.CertificateEncodingException;
//70import java.security.spec.EncodedKeySpec;
//71import java.security.spec.InvalidKeySpecException;
//72import java.security.spec.X509EncodedKeySpec;
//73import java.util.ArrayList;
//74import java.util.Arrays;
//75import java.util.Collections;
//76import java.util.Comparator;
//77import java.util.Iterator;
//78import java.util.List;
//79import java.util.Set;
//80import java.util.concurrent.atomic.AtomicReference;
//81import java.util.jar.StrictJarFile;
//82import java.util.zip.ZipEntry;
//83
//84/**
//85 * Parser for package files (APKs) on disk. This supports apps packaged either
//86 * as a single "monolithic" APK, or apps packaged as a "cluster" of multiple
//87 * APKs in a single directory.
//88 * <p>
//89 * Apps packaged as multiple APKs always consist of a single "base" APK (with a
//90 * {@code null} split name) and zero or more "split" APKs (with unique split
//91 * names). Any subset of those split APKs are a valid install, as long as the
//92 * following constraints are met:
//93 * <ul>
//94 * <li>All APKs must have the exact same package name, version code, and signing
//95 * certificates.
//96 * <li>All APKs must have unique split names.
//97 * <li>All installations must contain a single base APK.
//98 * </ul>
//99 *
//100 * @hide
//101 */
//102public class PackageParser {
//103    private static final boolean DEBUG_JAR = false;
//104    private static final boolean DEBUG_PARSER = false;
//105    private static final boolean DEBUG_BACKUP = false;
//106
//107    // TODO: switch outError users to PackageParserException
//108    // TODO: refactor "codePath" to "apkPath"
//109
//110    /** File name in an APK for the Android manifest. */
//111    private static final String ANDROID_MANIFEST_FILENAME = "AndroidManifest.xml";
//112
//113    /** @hide */
//114    public static class NewPermissionInfo {
//115        public final String name;
//116        public final int sdkVersion;
//117        public final int fileVersion;
//118
//119        public NewPermissionInfo(String name, int sdkVersion, int fileVersion) {
//120            this.name = name;
//121            this.sdkVersion = sdkVersion;
//122            this.fileVersion = fileVersion;
//123        }
//124    }
//125
//126    /** @hide */
//127    public static class SplitPermissionInfo {
//128        public final String rootPerm;
//129        public final String[] newPerms;
//130        public final int targetSdk;
//131
//132        public SplitPermissionInfo(String rootPerm, String[] newPerms, int targetSdk) {
//133            this.rootPerm = rootPerm;
//134            this.newPerms = newPerms;
//135            this.targetSdk = targetSdk;
//136        }
//137    }
//138
//139    /**
//140     * List of new permissions that have been added since 1.0.
//141     * NOTE: These must be declared in SDK version order, with permissions
//142     * added to older SDKs appearing before those added to newer SDKs.
//143     * If sdkVersion is 0, then this is not a permission that we want to
//144     * automatically add to older apps, but we do want to allow it to be
//145     * granted during a platform update.
//146     * @hide
//147     */
//148    public static final PackageParser.NewPermissionInfo NEW_PERMISSIONS[] =
//149        new PackageParser.NewPermissionInfo[] {
//150            new PackageParser.NewPermissionInfo(android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
//151                    android.os.Build.VERSION_CODES.DONUT, 0),
//152            new PackageParser.NewPermissionInfo(android.Manifest.permission.READ_PHONE_STATE,
//153                    android.os.Build.VERSION_CODES.DONUT, 0)
//154    };
//155
//156    /**
//157     * List of permissions that have been split into more granular or dependent
//158     * permissions.
//159     * @hide
//160     */
//161    public static final PackageParser.SplitPermissionInfo SPLIT_PERMISSIONS[] =
//162        new PackageParser.SplitPermissionInfo[] {
//163            // READ_EXTERNAL_STORAGE is always required when an app requests
//164            // WRITE_EXTERNAL_STORAGE, because we can't have an app that has
//165            // write access without read access.  The hack here with the target
//166            // target SDK version ensures that this grant is always done.
//167            new PackageParser.SplitPermissionInfo(android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
//168                    new String[] { android.Manifest.permission.READ_EXTERNAL_STORAGE },
//169                    android.os.Build.VERSION_CODES.CUR_DEVELOPMENT+1),
//170            new PackageParser.SplitPermissionInfo(android.Manifest.permission.READ_CONTACTS,
//171                    new String[] { android.Manifest.permission.READ_CALL_LOG },
//172                    android.os.Build.VERSION_CODES.JELLY_BEAN),
//173            new PackageParser.SplitPermissionInfo(android.Manifest.permission.WRITE_CONTACTS,
//174                    new String[] { android.Manifest.permission.WRITE_CALL_LOG },
//175                    android.os.Build.VERSION_CODES.JELLY_BEAN)
//176    };
//177
//178    /**
//179     * @deprecated callers should move to explicitly passing around source path.
//180     */
//181    @Deprecated
//182    private String mArchiveSourcePath;
//183
//184    private String[] mSeparateProcesses;
//185    private boolean mOnlyCoreApps;
//186    private DisplayMetrics mMetrics;
//187
//188    private static final int SDK_VERSION = Build.VERSION.SDK_INT;
//189    private static final String[] SDK_CODENAMES = Build.VERSION.ACTIVE_CODENAMES;
//190
//191    private int mParseError = PackageManager.INSTALL_SUCCEEDED;
//192
//193    private static boolean sCompatibilityModeEnabled = true;
//194    private static final int PARSE_DEFAULT_INSTALL_LOCATION =
//195            PackageInfo.INSTALL_LOCATION_UNSPECIFIED;
//196
//197    static class ParsePackageItemArgs {
//198        final Package owner;
//199        final String[] outError;
//200        final int nameRes;
//201        final int labelRes;
//202        final int iconRes;
//203        final int logoRes;
//204        final int bannerRes;
//205
//206        String tag;
//207        TypedArray sa;
//208
//209        ParsePackageItemArgs(Package _owner, String[] _outError,
//210                int _nameRes, int _labelRes, int _iconRes, int _logoRes, int _bannerRes) {
//211            owner = _owner;
//212            outError = _outError;
//213            nameRes = _nameRes;
//214            labelRes = _labelRes;
//215            iconRes = _iconRes;
//216            logoRes = _logoRes;
//217            bannerRes = _bannerRes;
//218        }
//219    }
//220
//221    static class ParseComponentArgs extends ParsePackageItemArgs {
//222        final String[] sepProcesses;
//223        final int processRes;
//224        final int descriptionRes;
//225        final int enabledRes;
//226        int flags;
//227
//228        ParseComponentArgs(Package _owner, String[] _outError,
//229                int _nameRes, int _labelRes, int _iconRes, int _logoRes, int _bannerRes,
//230                String[] _sepProcesses, int _processRes,
//231                int _descriptionRes, int _enabledRes) {
//232            super(_owner, _outError, _nameRes, _labelRes, _iconRes, _logoRes, _bannerRes);
//233            sepProcesses = _sepProcesses;
//234            processRes = _processRes;
//235            descriptionRes = _descriptionRes;
//236            enabledRes = _enabledRes;
//237        }
//238    }
//239
//240    /**
//241     * Lightweight parsed details about a single package.
//242     */
//243    public static class PackageLite {
//244        public final String packageName;
//245        public final int versionCode;
//246        public final int installLocation;
//247        public final VerifierInfo[] verifiers;
//248
//249        /** Names of any split APKs, ordered by parsed splitName */
//250        public final String[] splitNames;
//251
//252        /**
//253         * Path where this package was found on disk. For monolithic packages
//254         * this is path to single base APK file; for cluster packages this is
//255         * path to the cluster directory.
//256         */
//257        public final String codePath;
//258
//259        /** Path of base APK */
//260        public final String baseCodePath;
//261        /** Paths of any split APKs, ordered by parsed splitName */
//262        public final String[] splitCodePaths;
//263
//264        /** Revision code of base APK */
//265        public final int baseRevisionCode;
//266        /** Revision codes of any split APKs, ordered by parsed splitName */
//267        public final int[] splitRevisionCodes;
//268
//269        public final boolean coreApp;
//270        public final boolean multiArch;
//271
//272        public PackageLite(String codePath, ApkLite baseApk, String[] splitNames,
//273                String[] splitCodePaths, int[] splitRevisionCodes) {
//274            this.packageName = baseApk.packageName;
//275            this.versionCode = baseApk.versionCode;
//276            this.installLocation = baseApk.installLocation;
//277            this.verifiers = baseApk.verifiers;
//278            this.splitNames = splitNames;
//279            this.codePath = codePath;
//280            this.baseCodePath = baseApk.codePath;
//281            this.splitCodePaths = splitCodePaths;
//282            this.baseRevisionCode = baseApk.revisionCode;
//283            this.splitRevisionCodes = splitRevisionCodes;
//284            this.coreApp = baseApk.coreApp;
//285            this.multiArch = baseApk.multiArch;
//286        }
//287
//288        public List<String> getAllCodePaths() {
//289            ArrayList<String> paths = new ArrayList<>();
//290            paths.add(baseCodePath);
//291            if (!ArrayUtils.isEmpty(splitCodePaths)) {
//292                Collections.addAll(paths, splitCodePaths);
//293            }
//294            return paths;
//295        }
//296    }
//297
//298    /**
//299     * Lightweight parsed details about a single APK file.
//300     */
//301    public static class ApkLite {
//302        public final String codePath;
//303        public final String packageName;
//304        public final String splitName;
//305        public final int versionCode;
//306        public final int revisionCode;
//307        public final int installLocation;
//308        public final VerifierInfo[] verifiers;
//309        public final Signature[] signatures;
//310        public final boolean coreApp;
//311        public final boolean multiArch;
//312
//313        public ApkLite(String codePath, String packageName, String splitName, int versionCode,
//314                int revisionCode, int installLocation, List<VerifierInfo> verifiers,
//315                Signature[] signatures, boolean coreApp, boolean multiArch) {
//316            this.codePath = codePath;
//317            this.packageName = packageName;
//318            this.splitName = splitName;
//319            this.versionCode = versionCode;
//320            this.revisionCode = revisionCode;
//321            this.installLocation = installLocation;
//322            this.verifiers = verifiers.toArray(new VerifierInfo[verifiers.size()]);
//323            this.signatures = signatures;
//324            this.coreApp = coreApp;
//325            this.multiArch = multiArch;
//326        }
//327    }
//328
//329    private ParsePackageItemArgs mParseInstrumentationArgs;
//330    private ParseComponentArgs mParseActivityArgs;
//331    private ParseComponentArgs mParseActivityAliasArgs;
//332    private ParseComponentArgs mParseServiceArgs;
//333    private ParseComponentArgs mParseProviderArgs;
//334
//335    /** If set to true, we will only allow package files that exactly match
//336     *  the DTD.  Otherwise, we try to get as much from the package as we
//337     *  can without failing.  This should normally be set to false, to
//338     *  support extensions to the DTD in future versions. */
//339    private static final boolean RIGID_PARSER = false;
//340
//341    private static final String TAG = "PackageParser";
//342
//343    public PackageParser() {
//344        mMetrics = new DisplayMetrics();
//345        mMetrics.setToDefaults();
//346    }
//347
//348    public void setSeparateProcesses(String[] procs) {
//349        mSeparateProcesses = procs;
//350    }
//351
//352    /**
//353     * Flag indicating this parser should only consider apps with
//354     * {@code coreApp} manifest attribute to be valid apps. This is useful when
//355     * creating a minimalist boot environment.
//356     */
//357    public void setOnlyCoreApps(boolean onlyCoreApps) {
//358        mOnlyCoreApps = onlyCoreApps;
//359    }
//360
//361    public void setDisplayMetrics(DisplayMetrics metrics) {
//362        mMetrics = metrics;
//363    }
//364
//365    public static final boolean isApkFile(File file) {
//366        return isApkPath(file.getName());
//367    }
//368
//369    private static boolean isApkPath(String path) {
//370        return path.endsWith(".apk");
//371    }
//372
//373    /*
//374    public static PackageInfo generatePackageInfo(PackageParser.Package p,
//375            int gids[], int flags, long firstInstallTime, long lastUpdateTime,
//376            HashSet<String> grantedPermissions) {
//377        PackageUserState state = new PackageUserState();
//378        return generatePackageInfo(p, gids, flags, firstInstallTime, lastUpdateTime,
//379                grantedPermissions, state, UserHandle.getCallingUserId());
//380    }
//381    */
//382
//383    /**
//384     * Generate and return the {@link PackageInfo} for a parsed package.
//385     *
//386     * @param p the parsed package.
//387     * @param flags indicating which optional information is included.
//388     */
//389    public static PackageInfo generatePackageInfo(PackageParser.Package p,
//390            int gids[], int flags, long firstInstallTime, long lastUpdateTime,
//391            ArraySet<String> grantedPermissions, PackageUserState state) {
//392
//393        return generatePackageInfo(p, gids, flags, firstInstallTime, lastUpdateTime,
//394                grantedPermissions, state, UserHandle.getCallingUserId());
//395    }
//396
//397    /**
//398     * Returns true if the package is installed and not hidden, or if the caller
//399     * explicitly wanted all uninstalled and hidden packages as well.
//400     */
//401    private static boolean checkUseInstalledOrHidden(int flags, PackageUserState state) {
//402        return (state.installed && !state.hidden)
//403                || (flags & PackageManager.GET_UNINSTALLED_PACKAGES) != 0;
//404    }
//405
//406    public static boolean isAvailable(PackageUserState state) {
//407        return checkUseInstalledOrHidden(0, state);
//408    }
//409
//410    public static PackageInfo generatePackageInfo(PackageParser.Package p,
//411            int gids[], int flags, long firstInstallTime, long lastUpdateTime,
//412            ArraySet<String> grantedPermissions, PackageUserState state, int userId) {
//413
//414        if (!checkUseInstalledOrHidden(flags, state)) {
//415            return null;
//416        }
//417        PackageInfo pi = new PackageInfo();
//418        pi.packageName = p.packageName;
//419        pi.splitNames = p.splitNames;
//420        pi.versionCode = p.mVersionCode;
//421        pi.baseRevisionCode = p.baseRevisionCode;
//422        pi.splitRevisionCodes = p.splitRevisionCodes;
//423        pi.versionName = p.mVersionName;
//424        pi.sharedUserId = p.mSharedUserId;
//425        pi.sharedUserLabel = p.mSharedUserLabel;
//426        pi.applicationInfo = generateApplicationInfo(p, flags, state, userId);
//427        pi.installLocation = p.installLocation;
//428        pi.coreApp = p.coreApp;
//429        if ((pi.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM) != 0
//430                || (pi.applicationInfo.flags&ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
//431            pi.requiredForAllUsers = p.mRequiredForAllUsers;
//432        }
//433        pi.restrictedAccountType = p.mRestrictedAccountType;
//434        pi.requiredAccountType = p.mRequiredAccountType;
//435        pi.overlayTarget = p.mOverlayTarget;
//436        pi.firstInstallTime = firstInstallTime;
//437        pi.lastUpdateTime = lastUpdateTime;
//438        if ((flags&PackageManager.GET_GIDS) != 0) {
//439            pi.gids = gids;
//440        }
//441        if ((flags&PackageManager.GET_CONFIGURATIONS) != 0) {
//442            int N = p.configPreferences != null ? p.configPreferences.size() : 0;
//443            if (N > 0) {
//444                pi.configPreferences = new ConfigurationInfo[N];
//445                p.configPreferences.toArray(pi.configPreferences);
//446            }
//447            N = p.reqFeatures != null ? p.reqFeatures.size() : 0;
//448            if (N > 0) {
//449                pi.reqFeatures = new FeatureInfo[N];
//450                p.reqFeatures.toArray(pi.reqFeatures);
//451            }
//452            N = p.featureGroups != null ? p.featureGroups.size() : 0;
//453            if (N > 0) {
//454                pi.featureGroups = new FeatureGroupInfo[N];
//455                p.featureGroups.toArray(pi.featureGroups);
//456            }
//457        }
//458        if ((flags&PackageManager.GET_ACTIVITIES) != 0) {
//459            int N = p.activities.size();
//460            if (N > 0) {
//461                if ((flags&PackageManager.GET_DISABLED_COMPONENTS) != 0) {
//462                    pi.activities = new ActivityInfo[N];
//463                } else {
//464                    int num = 0;
//465                    for (int i=0; i<N; i++) {
//466                        if (p.activities.get(i).info.enabled) num++;
//467                    }
//468                    pi.activities = new ActivityInfo[num];
//469                }
//470                for (int i=0, j=0; i<N; i++) {
//471                    final Activity activity = p.activities.get(i);
//472                    if (activity.info.enabled
//473                        || (flags&PackageManager.GET_DISABLED_COMPONENTS) != 0) {
//474                        pi.activities[j++] = generateActivityInfo(p.activities.get(i), flags,
//475                                state, userId);
//476                    }
//477                }
//478            }
//479        }
//480        if ((flags&PackageManager.GET_RECEIVERS) != 0) {
//481            int N = p.receivers.size();
//482            if (N > 0) {
//483                if ((flags&PackageManager.GET_DISABLED_COMPONENTS) != 0) {
//484                    pi.receivers = new ActivityInfo[N];
//485                } else {
//486                    int num = 0;
//487                    for (int i=0; i<N; i++) {
//488                        if (p.receivers.get(i).info.enabled) num++;
//489                    }
//490                    pi.receivers = new ActivityInfo[num];
//491                }
//492                for (int i=0, j=0; i<N; i++) {
//493                    final Activity activity = p.receivers.get(i);
//494                    if (activity.info.enabled
//495                        || (flags&PackageManager.GET_DISABLED_COMPONENTS) != 0) {
//496                        pi.receivers[j++] = generateActivityInfo(p.receivers.get(i), flags,
//497                                state, userId);
//498                    }
//499                }
//500            }
//501        }
//502        if ((flags&PackageManager.GET_SERVICES) != 0) {
//503            int N = p.services.size();
//504            if (N > 0) {
//505                if ((flags&PackageManager.GET_DISABLED_COMPONENTS) != 0) {
//506                    pi.services = new ServiceInfo[N];
//507                } else {
//508                    int num = 0;
//509                    for (int i=0; i<N; i++) {
//510                        if (p.services.get(i).info.enabled) num++;
//511                    }
//512                    pi.services = new ServiceInfo[num];
//513                }
//514                for (int i=0, j=0; i<N; i++) {
//515                    final Service service = p.services.get(i);
//516                    if (service.info.enabled
//517                        || (flags&PackageManager.GET_DISABLED_COMPONENTS) != 0) {
//518                        pi.services[j++] = generateServiceInfo(p.services.get(i), flags,
//519                                state, userId);
//520                    }
//521                }
//522            }
//523        }
//524        if ((flags&PackageManager.GET_PROVIDERS) != 0) {
//525            int N = p.providers.size();
//526            if (N > 0) {
//527                if ((flags&PackageManager.GET_DISABLED_COMPONENTS) != 0) {
//528                    pi.providers = new ProviderInfo[N];
//529                } else {
//530                    int num = 0;
//531                    for (int i=0; i<N; i++) {
//532                        if (p.providers.get(i).info.enabled) num++;
//533                    }
//534                    pi.providers = new ProviderInfo[num];
//535                }
//536                for (int i=0, j=0; i<N; i++) {
//537                    final Provider provider = p.providers.get(i);
//538                    if (provider.info.enabled
//539                        || (flags&PackageManager.GET_DISABLED_COMPONENTS) != 0) {
//540                        pi.providers[j++] = generateProviderInfo(p.providers.get(i), flags,
//541                                state, userId);
//542                    }
//543                }
//544            }
//545        }
//546        if ((flags&PackageManager.GET_INSTRUMENTATION) != 0) {
//547            int N = p.instrumentation.size();
//548            if (N > 0) {
//549                pi.instrumentation = new InstrumentationInfo[N];
//550                for (int i=0; i<N; i++) {
//551                    pi.instrumentation[i] = generateInstrumentationInfo(
//552                            p.instrumentation.get(i), flags);
//553                }
//554            }
//555        }
//556        if ((flags&PackageManager.GET_PERMISSIONS) != 0) {
//557            int N = p.permissions.size();
//558            if (N > 0) {
//559                pi.permissions = new PermissionInfo[N];
//560                for (int i=0; i<N; i++) {
//561                    pi.permissions[i] = generatePermissionInfo(p.permissions.get(i), flags);
//562                }
//563            }
//564            N = p.requestedPermissions.size();
//565            if (N > 0) {
//566                pi.requestedPermissions = new String[N];
//567                pi.requestedPermissionsFlags = new int[N];
//568                for (int i=0; i<N; i++) {
//569                    final String perm = p.requestedPermissions.get(i);
//570                    pi.requestedPermissions[i] = perm;
//571                    if (p.requestedPermissionsRequired.get(i)) {
//572                        pi.requestedPermissionsFlags[i] |= PackageInfo.REQUESTED_PERMISSION_REQUIRED;
//573                    }
//574                    if (grantedPermissions != null && grantedPermissions.contains(perm)) {
//575                        pi.requestedPermissionsFlags[i] |= PackageInfo.REQUESTED_PERMISSION_GRANTED;
//576                    }
//577                }
//578            }
//579        }
//580        if ((flags&PackageManager.GET_SIGNATURES) != 0) {
//581           int N = (p.mSignatures != null) ? p.mSignatures.length : 0;
//582           if (N > 0) {
//583                pi.signatures = new Signature[N];
//584                System.arraycopy(p.mSignatures, 0, pi.signatures, 0, N);
//585            }
//586        }
//587        return pi;
//588    }
//589
//590    private static Certificate[][] loadCertificates(StrictJarFile jarFile, ZipEntry entry)
//591            throws PackageParserException {
//592        InputStream is = null;
//593        try {
//594            // We must read the stream for the JarEntry to retrieve
//595            // its certificates.
//596            is = jarFile.getInputStream(entry);
//597            readFullyIgnoringContents(is);
//598            return jarFile.getCertificateChains(entry);
//599        } catch (IOException | RuntimeException e) {
//600            throw new PackageParserException(INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION,
//601                    "Failed reading " + entry.getName() + " in " + jarFile, e);
//602        } finally {
//603            IoUtils.closeQuietly(is);
//604        }
//605    }
//606
//607    public final static int PARSE_IS_SYSTEM = 1<<0;
//608    public final static int PARSE_CHATTY = 1<<1;
//609    public final static int PARSE_MUST_BE_APK = 1<<2;
//610    public final static int PARSE_IGNORE_PROCESSES = 1<<3;
//611    public final static int PARSE_FORWARD_LOCK = 1<<4;
//612    public final static int PARSE_ON_SDCARD = 1<<5;
//613    public final static int PARSE_IS_SYSTEM_DIR = 1<<6;
//614    public final static int PARSE_IS_PRIVILEGED = 1<<7;
//615    public final static int PARSE_COLLECT_CERTIFICATES = 1<<8;
//616    public final static int PARSE_TRUSTED_OVERLAY = 1<<9;
//617
//618    private static final Comparator<String> sSplitNameComparator = new SplitNameComparator();
//619
//620    /**
//621     * Used to sort a set of APKs based on their split names, always placing the
//622     * base APK (with {@code null} split name) first.
//623     */
//624    private static class SplitNameComparator implements Comparator<String> {
//625        @Override
//626        public int compare(String lhs, String rhs) {
//627            if (lhs == null) {
//628                return -1;
//629            } else if (rhs == null) {
//630                return 1;
//631            } else {
//632                return lhs.compareTo(rhs);
//633            }
//634        }
//635    }
//636
//637    /**
//638     * Parse only lightweight details about the package at the given location.
//639     * Automatically detects if the package is a monolithic style (single APK
//640     * file) or cluster style (directory of APKs).
//641     * <p>
//642     * This performs sanity checking on cluster style packages, such as
//643     * requiring identical package name and version codes, a single base APK,
//644     * and unique split names.
//645     *
//646     * @see PackageParser#parsePackage(File, int)
//647     */
//648    public static PackageLite parsePackageLite(File packageFile, int flags)
//649            throws PackageParserException {
//650        if (packageFile.isDirectory()) {
//651            return parseClusterPackageLite(packageFile, flags);
//652        } else {
//653            return parseMonolithicPackageLite(packageFile, flags);
//654        }
//655    }
//656
//657    private static PackageLite parseMonolithicPackageLite(File packageFile, int flags)
//658            throws PackageParserException {
//659        final ApkLite baseApk = parseApkLite(packageFile, flags);
//660        final String packagePath = packageFile.getAbsolutePath();
//661        return new PackageLite(packagePath, baseApk, null, null, null);
//662    }
//663
//664    private static PackageLite parseClusterPackageLite(File packageDir, int flags)
//665            throws PackageParserException {
//666        final File[] files = packageDir.listFiles();
//667        if (ArrayUtils.isEmpty(files)) {
//668            throw new PackageParserException(INSTALL_PARSE_FAILED_NOT_APK,
//669                    "No packages found in split");
//670        }
//671
//672        String packageName = null;
//673        int versionCode = 0;
//674
//675        final ArrayMap<String, ApkLite> apks = new ArrayMap<>();
//676        for (File file : files) {
//677            if (isApkFile(file)) {
//678                final ApkLite lite = parseApkLite(file, flags);
//679
//680                // Assert that all package names and version codes are
//681                // consistent with the first one we encounter.
//682                if (packageName == null) {
//683                    packageName = lite.packageName;
//684                    versionCode = lite.versionCode;
//685                } else {
//686                    if (!packageName.equals(lite.packageName)) {
//687                        throw new PackageParserException(INSTALL_PARSE_FAILED_BAD_MANIFEST,
//688                                "Inconsistent package " + lite.packageName + " in " + file
//689                                + "; expected " + packageName);
//690                    }
//691                    if (versionCode != lite.versionCode) {
//692                        throw new PackageParserException(INSTALL_PARSE_FAILED_BAD_MANIFEST,
//693                                "Inconsistent version " + lite.versionCode + " in " + file
//694                                + "; expected " + versionCode);
//695                    }
//696                }
//697
//698                // Assert that each split is defined only once
//699                if (apks.put(lite.splitName, lite) != null) {
//700                    throw new PackageParserException(INSTALL_PARSE_FAILED_BAD_MANIFEST,
//701                            "Split name " + lite.splitName
//702                            + " defined more than once; most recent was " + file);
//703                }
//704            }
//705        }
//706
//707        final ApkLite baseApk = apks.remove(null);
//708        if (baseApk == null) {
//709            throw new PackageParserException(INSTALL_PARSE_FAILED_BAD_MANIFEST,
//710                    "Missing base APK in " + packageDir);
//711        }
//712
//713        // Always apply deterministic ordering based on splitName
//714        final int size = apks.size();
//715
//716        String[] splitNames = null;
//717        String[] splitCodePaths = null;
//718        int[] splitRevisionCodes = null;
//719        if (size > 0) {
//720            splitNames = new String[size];
//721            splitCodePaths = new String[size];
//722            splitRevisionCodes = new int[size];
//723
//724            splitNames = apks.keySet().toArray(splitNames);
//725            Arrays.sort(splitNames, sSplitNameComparator);
//726
//727            for (int i = 0; i < size; i++) {
//728                splitCodePaths[i] = apks.get(splitNames[i]).codePath;
//729                splitRevisionCodes[i] = apks.get(splitNames[i]).revisionCode;
//730            }
//731        }
//732
//733        final String codePath = packageDir.getAbsolutePath();
//734        return new PackageLite(codePath, baseApk, splitNames, splitCodePaths,
//735                splitRevisionCodes);
//736    }
//737
//738    /**
//739     * Parse the package at the given location. Automatically detects if the
//740     * package is a monolithic style (single APK file) or cluster style
//741     * (directory of APKs).
//742     * <p>
//743     * This performs sanity checking on cluster style packages, such as
//744     * requiring identical package name and version codes, a single base APK,
//745     * and unique split names.
//746     * <p>
//747     * Note that this <em>does not</em> perform signature verification; that
//748     * must be done separately in {@link #collectCertificates(Package, int)}.
//749     *
//750     * @see #parsePackageLite(File, int)
//751     */
//752    public Package parsePackage(File packageFile, int flags) throws PackageParserException {
//753        if (packageFile.isDirectory()) {
//754            return parseClusterPackage(packageFile, flags);
//755        } else {
//756            return parseMonolithicPackage(packageFile, flags);
//757        }
//758    }
//759
//760    /**
//761     * Parse all APKs contained in the given directory, treating them as a
//762     * single package. This also performs sanity checking, such as requiring
//763     * identical package name and version codes, a single base APK, and unique
//764     * split names.
//765     * <p>
//766     * Note that this <em>does not</em> perform signature verification; that
//767     * must be done separately in {@link #collectCertificates(Package, int)}.
//768     */
//769    private Package parseClusterPackage(File packageDir, int flags) throws PackageParserException {
//770        final PackageLite lite = parseClusterPackageLite(packageDir, 0);
//771
//772        if (mOnlyCoreApps && !lite.coreApp) {
//773            throw new PackageParserException(INSTALL_PARSE_FAILED_MANIFEST_MALFORMED,
//774                    "Not a coreApp: " + packageDir);
//775        }
//776
//777        final AssetManager assets = new AssetManager();
//778        try {
//779            // Load the base and all splits into the AssetManager
//780            // so that resources can be overriden when parsing the manifests.
//781            loadApkIntoAssetManager(assets, lite.baseCodePath, flags);
//782
//783            if (!ArrayUtils.isEmpty(lite.splitCodePaths)) {
//784                for (String path : lite.splitCodePaths) {
//785                    loadApkIntoAssetManager(assets, path, flags);
//786                }
//787            }
//788
//789            final File baseApk = new File(lite.baseCodePath);
//790            final Package pkg = parseBaseApk(baseApk, assets, flags);
//791            if (pkg == null) {
//792                throw new PackageParserException(INSTALL_PARSE_FAILED_NOT_APK,
//793                        "Failed to parse base APK: " + baseApk);
//794            }
//795
//796            if (!ArrayUtils.isEmpty(lite.splitNames)) {
//797                final int num = lite.splitNames.length;
//798                pkg.splitNames = lite.splitNames;
//799                pkg.splitCodePaths = lite.splitCodePaths;
//800                pkg.splitRevisionCodes = lite.splitRevisionCodes;
//801                pkg.splitFlags = new int[num];
//802
//803                for (int i = 0; i < num; i++) {
//804                    parseSplitApk(pkg, i, assets, flags);
//805                }
//806            }
//807
//808            pkg.codePath = packageDir.getAbsolutePath();
//809            return pkg;
//810        } finally {
//811            IoUtils.closeQuietly(assets);
//812        }
//813    }
//814
//815    /**
//816     * Parse the given APK file, treating it as as a single monolithic package.
//817     * <p>
//818     * Note that this <em>does not</em> perform signature verification; that
//819     * must be done separately in {@link #collectCertificates(Package, int)}.
//820     *
//821     * @deprecated external callers should move to
//822     *             {@link #parsePackage(File, int)}. Eventually this method will
//823     *             be marked private.
//824     */
//825    @Deprecated
//826    public Package parseMonolithicPackage(File apkFile, int flags) throws PackageParserException {
//827        if (mOnlyCoreApps) {
//828            final PackageLite lite = parseMonolithicPackageLite(apkFile, flags);
//829            if (!lite.coreApp) {
//830                throw new PackageParserException(INSTALL_PARSE_FAILED_MANIFEST_MALFORMED,
//831                        "Not a coreApp: " + apkFile);
//832            }
//833        }
//834
//835        final AssetManager assets = new AssetManager();
//836        try {
//837            final Package pkg = parseBaseApk(apkFile, assets, flags);
//838            pkg.codePath = apkFile.getAbsolutePath();
//839            return pkg;
//840        } finally {
//841            IoUtils.closeQuietly(assets);
//842        }
//843    }
//844
//845    private static int loadApkIntoAssetManager(AssetManager assets, String apkPath, int flags)
//846            throws PackageParserException {
//847        if ((flags & PARSE_MUST_BE_APK) != 0 && !isApkPath(apkPath)) {
//848            throw new PackageParserException(INSTALL_PARSE_FAILED_NOT_APK,
//849                    "Invalid package file: " + apkPath);
//850        }
//851
//852        // The AssetManager guarantees uniqueness for asset paths, so if this asset path
//853        // already exists in the AssetManager, addAssetPath will only return the cookie
//854        // assigned to it.
//855        int cookie = assets.addAssetPath(apkPath);
//856        if (cookie == 0) {
//857            throw new PackageParserException(INSTALL_PARSE_FAILED_BAD_MANIFEST,
//858                    "Failed adding asset path: " + apkPath);
//859        }
//860        return cookie;
//861    }
//862
//863    private Package parseBaseApk(File apkFile, AssetManager assets, int flags)
//864            throws PackageParserException {
//865        final String apkPath = apkFile.getAbsolutePath();
//866
//867        mParseError = PackageManager.INSTALL_SUCCEEDED;
//868        mArchiveSourcePath = apkFile.getAbsolutePath();
//869
//870        if (DEBUG_JAR) Slog.d(TAG, "Scanning base APK: " + apkPath);
//871
//872        final int cookie = loadApkIntoAssetManager(assets, apkPath, flags);
//873
//874        Resources res = null;
//875        XmlResourceParser parser = null;
//876        try {
//877            res = new Resources(assets, mMetrics, null);
//878            assets.setConfiguration(0, 0, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
//879                    Build.VERSION.RESOURCES_SDK_INT);
//880            parser = assets.openXmlResourceParser(cookie, ANDROID_MANIFEST_FILENAME);
//881
//882            final String[] outError = new String[1];
//883            final Package pkg = parseBaseApk(res, parser, flags, outError);
//884            if (pkg == null) {
//885                throw new PackageParserException(mParseError,
//886                        apkPath + " (at " + parser.getPositionDescription() + "): " + outError[0]);
//887            }
//888
//889            pkg.baseCodePath = apkPath;
//890            pkg.mSignatures = null;
//891
//892            return pkg;
//893
//894        } catch (PackageParserException e) {
//895            throw e;
//896        } catch (Exception e) {
//897            throw new PackageParserException(INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION,
//898                    "Failed to read manifest from " + apkPath, e);
//899        } finally {
//900            IoUtils.closeQuietly(parser);
//901        }
//902    }
//903
//904    private void parseSplitApk(Package pkg, int splitIndex, AssetManager assets, int flags)
//905            throws PackageParserException {
//906        final String apkPath = pkg.splitCodePaths[splitIndex];
//907        final File apkFile = new File(apkPath);
//908
//909        mParseError = PackageManager.INSTALL_SUCCEEDED;
//910        mArchiveSourcePath = apkPath;
//911
//912        if (DEBUG_JAR) Slog.d(TAG, "Scanning split APK: " + apkPath);
//913
//914        final int cookie = loadApkIntoAssetManager(assets, apkPath, flags);
//915
//916        Resources res = null;
//917        XmlResourceParser parser = null;
//918        try {
//919            res = new Resources(assets, mMetrics, null);
//920            assets.setConfiguration(0, 0, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
//921                    Build.VERSION.RESOURCES_SDK_INT);
//922            parser = assets.openXmlResourceParser(cookie, ANDROID_MANIFEST_FILENAME);
//923
//924            final String[] outError = new String[1];
//925            pkg = parseSplitApk(pkg, res, parser, flags, splitIndex, outError);
//926            if (pkg == null) {
//927                throw new PackageParserException(mParseError,
//928                        apkPath + " (at " + parser.getPositionDescription() + "): " + outError[0]);
//929            }
//930
//931        } catch (PackageParserException e) {
//932            throw e;
//933        } catch (Exception e) {
//934            throw new PackageParserException(INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION,
//935                    "Failed to read manifest from " + apkPath, e);
//936        } finally {
//937            IoUtils.closeQuietly(parser);
//938        }
//939    }
//940
//941    /**
//942     * Parse the manifest of a <em>split APK</em>.
//943     * <p>
//944     * Note that split APKs have many more restrictions on what they're capable
//945     * of doing, so many valid features of a base APK have been carefully
//946     * omitted here.
//947     */
//948    private Package parseSplitApk(Package pkg, Resources res, XmlResourceParser parser, int flags,
//949            int splitIndex, String[] outError) throws XmlPullParserException, IOException,
//950            PackageParserException {
//951        AttributeSet attrs = parser;
//952
//953        // We parsed manifest tag earlier; just skip past it
//954        parsePackageSplitNames(parser, attrs, flags);
//955
//956        mParseInstrumentationArgs = null;
//957        mParseActivityArgs = null;
//958        mParseServiceArgs = null;
//959        mParseProviderArgs = null;
//960
//961        int type;
//962
//963        boolean foundApp = false;
//964
//965        int outerDepth = parser.getDepth();
//966        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
//967                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
//968            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
//969                continue;
//970            }
//971
//972            String tagName = parser.getName();
//973            if (tagName.equals("application")) {
//974                if (foundApp) {
//975                    if (RIGID_PARSER) {
//976                        outError[0] = "<manifest> has more than one <application>";
//977                        mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//978                        return null;
//979                    } else {
//980                        Slog.w(TAG, "<manifest> has more than one <application>");
//981                        XmlUtils.skipCurrentTag(parser);
//982                        continue;
//983                    }
//984                }
//985
//986                foundApp = true;
//987                if (!parseSplitApplication(pkg, res, parser, attrs, flags, splitIndex, outError)) {
//988                    return null;
//989                }
//990
//991            } else if (RIGID_PARSER) {
//992                outError[0] = "Bad element under <manifest>: "
//993                    + parser.getName();
//994                mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//995                return null;
//996
//997            } else {
//998                Slog.w(TAG, "Unknown element under <manifest>: " + parser.getName()
//999                        + " at " + mArchiveSourcePath + " "
//1000                        + parser.getPositionDescription());
//1001                XmlUtils.skipCurrentTag(parser);
//1002                continue;
//1003            }
//1004        }
//1005
//1006        if (!foundApp) {
//1007            outError[0] = "<manifest> does not contain an <application>";
//1008            mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_EMPTY;
//1009        }
//1010
//1011        return pkg;
//1012    }
//1013
//1014    /**
//1015     * Gathers the {@link ManifestDigest} for {@code pkg} if it exists in the
//1016     * APK. If it successfully scanned the package and found the
//1017     * {@code AndroidManifest.xml}, {@code true} is returned.
//1018     */
//1019    public void collectManifestDigest(Package pkg) throws PackageParserException {
//1020        pkg.manifestDigest = null;
//1021
//1022        // TODO: extend to gather digest for split APKs
//1023        try {
//1024            final StrictJarFile jarFile = new StrictJarFile(pkg.baseCodePath);
//1025            try {
//1026                final ZipEntry je = jarFile.findEntry(ANDROID_MANIFEST_FILENAME);
//1027                if (je != null) {
//1028                    pkg.manifestDigest = ManifestDigest.fromInputStream(jarFile.getInputStream(je));
//1029                }
//1030            } finally {
//1031                jarFile.close();
//1032            }
//1033        } catch (IOException | RuntimeException e) {
//1034            throw new PackageParserException(INSTALL_PARSE_FAILED_MANIFEST_MALFORMED,
//1035                    "Failed to collect manifest digest");
//1036        }
//1037    }
//1038
//1039    /**
//1040     * Collect certificates from all the APKs described in the given package,
//1041     * populating {@link Package#mSignatures}. This also asserts that all APK
//1042     * contents are signed correctly and consistently.
//1043     */
//1044    public void collectCertificates(Package pkg, int flags) throws PackageParserException {
//1045        pkg.mCertificates = null;
//1046        pkg.mSignatures = null;
//1047        pkg.mSigningKeys = null;
//1048
//1049        collectCertificates(pkg, new File(pkg.baseCodePath), flags);
//1050
//1051        if (!ArrayUtils.isEmpty(pkg.splitCodePaths)) {
//1052            for (String splitCodePath : pkg.splitCodePaths) {
//1053                collectCertificates(pkg, new File(splitCodePath), flags);
//1054            }
//1055        }
//1056    }
//1057
//1058    private static void collectCertificates(Package pkg, File apkFile, int flags)
//1059            throws PackageParserException {
//1060        final String apkPath = apkFile.getAbsolutePath();
//1061
//1062        StrictJarFile jarFile = null;
//1063        try {
//1064            jarFile = new StrictJarFile(apkPath);
//1065
//1066            // Always verify manifest, regardless of source
//1067            final ZipEntry manifestEntry = jarFile.findEntry(ANDROID_MANIFEST_FILENAME);
//1068            if (manifestEntry == null) {
//1069                throw new PackageParserException(INSTALL_PARSE_FAILED_BAD_MANIFEST,
//1070                        "Package " + apkPath + " has no manifest");
//1071            }
//1072
//1073            final List<ZipEntry> toVerify = new ArrayList<>();
//1074            toVerify.add(manifestEntry);
//1075
//1076            // If we're parsing an untrusted package, verify all contents
//1077            if ((flags & PARSE_IS_SYSTEM) == 0) {
//1078                final Iterator<ZipEntry> i = jarFile.iterator();
//1079                while (i.hasNext()) {
//1080                    final ZipEntry entry = i.next();
//1081
//1082                    if (entry.isDirectory()) continue;
//1083                    if (entry.getName().startsWith("META-INF/")) continue;
//1084                    if (entry.getName().equals(ANDROID_MANIFEST_FILENAME)) continue;
//1085
//1086                    toVerify.add(entry);
//1087                }
//1088            }
//1089
//1090            // Verify that entries are signed consistently with the first entry
//1091            // we encountered. Note that for splits, certificates may have
//1092            // already been populated during an earlier parse of a base APK.
//1093            for (ZipEntry entry : toVerify) {
//1094                final Certificate[][] entryCerts = loadCertificates(jarFile, entry);
//1095                if (ArrayUtils.isEmpty(entryCerts)) {
//1096                    throw new PackageParserException(INSTALL_PARSE_FAILED_NO_CERTIFICATES,
//1097                            "Package " + apkPath + " has no certificates at entry "
//1098                            + entry.getName());
//1099                }
//1100                final Signature[] entrySignatures = convertToSignatures(entryCerts);
//1101
//1102                if (pkg.mCertificates == null) {
//1103                    pkg.mCertificates = entryCerts;
//1104                    pkg.mSignatures = entrySignatures;
//1105                    pkg.mSigningKeys = new ArraySet<PublicKey>();
//1106                    for (int i=0; i < entryCerts.length; i++) {
//1107                        pkg.mSigningKeys.add(entryCerts[i][0].getPublicKey());
//1108                    }
//1109                } else {
//1110                    if (!Signature.areExactMatch(pkg.mSignatures, entrySignatures)) {
//1111                        throw new PackageParserException(
//1112                                INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES, "Package " + apkPath
//1113                                        + " has mismatched certificates at entry "
//1114                                        + entry.getName());
//1115                    }
//1116                }
//1117            }
//1118        } catch (GeneralSecurityException e) {
//1119            throw new PackageParserException(INSTALL_PARSE_FAILED_CERTIFICATE_ENCODING,
//1120                    "Failed to collect certificates from " + apkPath, e);
//1121        } catch (IOException | RuntimeException e) {
//1122            throw new PackageParserException(INSTALL_PARSE_FAILED_NO_CERTIFICATES,
//1123                    "Failed to collect certificates from " + apkPath, e);
//1124        } finally {
//1125            closeQuietly(jarFile);
//1126        }
//1127    }
//1128
//1129    private static Signature[] convertToSignatures(Certificate[][] certs)
//1130            throws CertificateEncodingException {
//1131        final Signature[] res = new Signature[certs.length];
//1132        for (int i = 0; i < certs.length; i++) {
//1133            res[i] = new Signature(certs[i]);
//1134        }
//1135        return res;
//1136    }
//1137
//1138    /**
//1139     * Utility method that retrieves lightweight details about a single APK
//1140     * file, including package name, split name, and install location.
//1141     *
//1142     * @param apkFile path to a single APK
//1143     * @param flags optional parse flags, such as
//1144     *            {@link #PARSE_COLLECT_CERTIFICATES}
//1145     */
//1146    public static ApkLite parseApkLite(File apkFile, int flags)
//1147            throws PackageParserException {
//1148        final String apkPath = apkFile.getAbsolutePath();
//1149
//1150        AssetManager assets = null;
//1151        XmlResourceParser parser = null;
//1152        try {
//1153            assets = new AssetManager();
//1154            assets.setConfiguration(0, 0, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
//1155                    Build.VERSION.RESOURCES_SDK_INT);
//1156
//1157            int cookie = assets.addAssetPath(apkPath);
//1158            if (cookie == 0) {
//1159                throw new PackageParserException(INSTALL_PARSE_FAILED_NOT_APK,
//1160                        "Failed to parse " + apkPath);
//1161            }
//1162
//1163            final DisplayMetrics metrics = new DisplayMetrics();
//1164            metrics.setToDefaults();
//1165
//1166            final Resources res = new Resources(assets, metrics, null);
//1167            parser = assets.openXmlResourceParser(cookie, ANDROID_MANIFEST_FILENAME);
//1168
//1169            final Signature[] signatures;
//1170            if ((flags & PARSE_COLLECT_CERTIFICATES) != 0) {
//1171                // TODO: factor signature related items out of Package object
//1172                final Package tempPkg = new Package(null);
//1173                collectCertificates(tempPkg, apkFile, 0);
//1174                signatures = tempPkg.mSignatures;
//1175            } else {
//1176                signatures = null;
//1177            }
//1178
//1179            final AttributeSet attrs = parser;
//1180            return parseApkLite(apkPath, res, parser, attrs, flags, signatures);
//1181
//1182        } catch (XmlPullParserException | IOException | RuntimeException e) {
//1183            throw new PackageParserException(INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION,
//1184                    "Failed to parse " + apkPath, e);
//1185        } finally {
//1186            IoUtils.closeQuietly(parser);
//1187            IoUtils.closeQuietly(assets);
//1188        }
//1189    }
//1190
//1191    private static String validateName(String name, boolean requiresSeparator) {
//1192        final int N = name.length();
//1193        boolean hasSep = false;
//1194        boolean front = true;
//1195        for (int i=0; i<N; i++) {
//1196            final char c = name.charAt(i);
//1197            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
//1198                front = false;
//1199                continue;
//1200            }
//1201            if (!front) {
//1202                if ((c >= '0' && c <= '9') || c == '_') {
//1203                    continue;
//1204                }
//1205            }
//1206            if (c == '.') {
//1207                hasSep = true;
//1208                front = true;
//1209                continue;
//1210            }
//1211            return "bad character '" + c + "'";
//1212        }
//1213        return hasSep || !requiresSeparator
//1214                ? null : "must have at least one '.' separator";
//1215    }
//1216
//1217    private static Pair<String, String> parsePackageSplitNames(XmlPullParser parser,
//1218            AttributeSet attrs, int flags) throws IOException, XmlPullParserException,
//1219            PackageParserException {
//1220
//1221        int type;
//1222        while ((type = parser.next()) != XmlPullParser.START_TAG
//1223                && type != XmlPullParser.END_DOCUMENT) {
//1224        }
//1225
//1226        if (type != XmlPullParser.START_TAG) {
//1227            throw new PackageParserException(INSTALL_PARSE_FAILED_MANIFEST_MALFORMED,
//1228                    "No start tag found");
//1229        }
//1230        if (!parser.getName().equals("manifest")) {
//1231            throw new PackageParserException(INSTALL_PARSE_FAILED_MANIFEST_MALFORMED,
//1232                    "No <manifest> tag");
//1233        }
//1234
//1235        final String packageName = attrs.getAttributeValue(null, "package");
//1236        if (!"android".equals(packageName)) {
//1237            final String error = validateName(packageName, true);
//1238            if (error != null) {
//1239                throw new PackageParserException(INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME,
//1240                        "Invalid manifest package: " + error);
//1241            }
//1242        }
//1243
//1244        String splitName = attrs.getAttributeValue(null, "split");
//1245        if (splitName != null) {
//1246            if (splitName.length() == 0) {
//1247                splitName = null;
//1248            } else {
//1249                final String error = validateName(splitName, false);
//1250                if (error != null) {
//1251                    throw new PackageParserException(INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME,
//1252                            "Invalid manifest split: " + error);
//1253                }
//1254            }
//1255        }
//1256
//1257        return Pair.create(packageName.intern(),
//1258                (splitName != null) ? splitName.intern() : splitName);
//1259    }
//1260
//1261    private static ApkLite parseApkLite(String codePath, Resources res, XmlPullParser parser,
//1262            AttributeSet attrs, int flags, Signature[] signatures) throws IOException,
//1263            XmlPullParserException, PackageParserException {
//1264        final Pair<String, String> packageSplit = parsePackageSplitNames(parser, attrs, flags);
//1265
//1266        int installLocation = PARSE_DEFAULT_INSTALL_LOCATION;
//1267        int versionCode = 0;
//1268        int revisionCode = 0;
//1269        boolean coreApp = false;
//1270        boolean multiArch = false;
//1271
//1272        for (int i = 0; i < attrs.getAttributeCount(); i++) {
//1273            final String attr = attrs.getAttributeName(i);
//1274            if (attr.equals("installLocation")) {
//1275                installLocation = attrs.getAttributeIntValue(i,
//1276                        PARSE_DEFAULT_INSTALL_LOCATION);
//1277            } else if (attr.equals("versionCode")) {
//1278                versionCode = attrs.getAttributeIntValue(i, 0);
//1279            } else if (attr.equals("revisionCode")) {
//1280                revisionCode = attrs.getAttributeIntValue(i, 0);
//1281            } else if (attr.equals("coreApp")) {
//1282                coreApp = attrs.getAttributeBooleanValue(i, false);
//1283            }
//1284        }
//1285
//1286        // Only search the tree when the tag is directly below <manifest>
//1287        int type;
//1288        final int searchDepth = parser.getDepth() + 1;
//1289
//1290        final List<VerifierInfo> verifiers = new ArrayList<VerifierInfo>();
//1291        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
//1292                && (type != XmlPullParser.END_TAG || parser.getDepth() >= searchDepth)) {
//1293            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
//1294                continue;
//1295            }
//1296
//1297            if (parser.getDepth() == searchDepth && "package-verifier".equals(parser.getName())) {
//1298                final VerifierInfo verifier = parseVerifier(res, parser, attrs, flags);
//1299                if (verifier != null) {
//1300                    verifiers.add(verifier);
//1301                }
//1302            }
//1303
//1304            if (parser.getDepth() == searchDepth && "application".equals(parser.getName())) {
//1305                for (int i = 0; i < attrs.getAttributeCount(); ++i) {
//1306                    final String attr = attrs.getAttributeName(i);
//1307                    if ("multiArch".equals(attr)) {
//1308                        multiArch = attrs.getAttributeBooleanValue(i, false);
//1309                        break;
//1310                    }
//1311                }
//1312            }
//1313        }
//1314
//1315        return new ApkLite(codePath, packageSplit.first, packageSplit.second, versionCode,
//1316                revisionCode, installLocation, verifiers, signatures, coreApp, multiArch);
//1317    }
//1318
//1319    /**
//1320     * Temporary.
//1321     */
//1322    static public Signature stringToSignature(String str) {
//1323        final int N = str.length();
//1324        byte[] sig = new byte[N];
//1325        for (int i=0; i<N; i++) {
//1326            sig[i] = (byte)str.charAt(i);
//1327        }
//1328        return new Signature(sig);
//1329    }
//1330
//1331    /**
//1332     * Parse the manifest of a <em>base APK</em>.
//1333     * <p>
//1334     * When adding new features, carefully consider if they should also be
//1335     * supported by split APKs.
//1336     */
//1337    private Package parseBaseApk(Resources res, XmlResourceParser parser, int flags,
//1338            String[] outError) throws XmlPullParserException, IOException {
//1339        final boolean trustedOverlay = (flags & PARSE_TRUSTED_OVERLAY) != 0;
//1340
//1341        AttributeSet attrs = parser;
//1342
//1343        mParseInstrumentationArgs = null;
//1344        mParseActivityArgs = null;
//1345        mParseServiceArgs = null;
//1346        mParseProviderArgs = null;
//1347
//1348        final String pkgName;
//1349        final String splitName;
//1350        try {
//1351            Pair<String, String> packageSplit = parsePackageSplitNames(parser, attrs, flags);
//1352            pkgName = packageSplit.first;
//1353            splitName = packageSplit.second;
//1354        } catch (PackageParserException e) {
//1355            mParseError = PackageManager.INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME;
//1356            return null;
//1357        }
//1358
//1359        int type;
//1360
//1361        if (!TextUtils.isEmpty(splitName)) {
//1362            outError[0] = "Expected base APK, but found split " + splitName;
//1363            mParseError = PackageManager.INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME;
//1364            return null;
//1365        }
//1366
//1367        final Package pkg = new Package(pkgName);
//1368        boolean foundApp = false;
//1369
//1370        TypedArray sa = res.obtainAttributes(attrs,
//1371                com.android.internal.R.styleable.AndroidManifest);
//1372        pkg.mVersionCode = pkg.applicationInfo.versionCode = sa.getInteger(
//1373                com.android.internal.R.styleable.AndroidManifest_versionCode, 0);
//1374        pkg.baseRevisionCode = sa.getInteger(
//1375                com.android.internal.R.styleable.AndroidManifest_revisionCode, 0);
//1376        pkg.mVersionName = sa.getNonConfigurationString(
//1377                com.android.internal.R.styleable.AndroidManifest_versionName, 0);
//1378        if (pkg.mVersionName != null) {
//1379            pkg.mVersionName = pkg.mVersionName.intern();
//1380        }
//1381        String str = sa.getNonConfigurationString(
//1382                com.android.internal.R.styleable.AndroidManifest_sharedUserId, 0);
//1383        if (str != null && str.length() > 0) {
//1384            String nameError = validateName(str, true);
//1385            if (nameError != null && !"android".equals(pkgName)) {
//1386                outError[0] = "<manifest> specifies bad sharedUserId name \""
//1387                    + str + "\": " + nameError;
//1388                mParseError = PackageManager.INSTALL_PARSE_FAILED_BAD_SHARED_USER_ID;
//1389                return null;
//1390            }
//1391            pkg.mSharedUserId = str.intern();
//1392            pkg.mSharedUserLabel = sa.getResourceId(
//1393                    com.android.internal.R.styleable.AndroidManifest_sharedUserLabel, 0);
//1394        }
//1395
//1396        pkg.installLocation = sa.getInteger(
//1397                com.android.internal.R.styleable.AndroidManifest_installLocation,
//1398                PARSE_DEFAULT_INSTALL_LOCATION);
//1399        pkg.applicationInfo.installLocation = pkg.installLocation;
//1400
//1401        pkg.coreApp = attrs.getAttributeBooleanValue(null, "coreApp", false);
//1402
//1403        sa.recycle();
//1404
//1405        /* Set the global "forward lock" flag */
//1406        if ((flags & PARSE_FORWARD_LOCK) != 0) {
//1407            pkg.applicationInfo.flags |= ApplicationInfo.FLAG_FORWARD_LOCK;
//1408        }
//1409
//1410        /* Set the global "on SD card" flag */
//1411        if ((flags & PARSE_ON_SDCARD) != 0) {
//1412            pkg.applicationInfo.flags |= ApplicationInfo.FLAG_EXTERNAL_STORAGE;
//1413        }
//1414
//1415        // Resource boolean are -1, so 1 means we don't know the value.
//1416        int supportsSmallScreens = 1;
//1417        int supportsNormalScreens = 1;
//1418        int supportsLargeScreens = 1;
//1419        int supportsXLargeScreens = 1;
//1420        int resizeable = 1;
//1421        int anyDensity = 1;
//1422
//1423        int outerDepth = parser.getDepth();
//1424        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
//1425                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
//1426            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
//1427                continue;
//1428            }
//1429
//1430            String tagName = parser.getName();
//1431            if (tagName.equals("application")) {
//1432                if (foundApp) {
//1433                    if (RIGID_PARSER) {
//1434                        outError[0] = "<manifest> has more than one <application>";
//1435                        mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//1436                        return null;
//1437                    } else {
//1438                        Slog.w(TAG, "<manifest> has more than one <application>");
//1439                        XmlUtils.skipCurrentTag(parser);
//1440                        continue;
//1441                    }
//1442                }
//1443
//1444                foundApp = true;
//1445                if (!parseBaseApplication(pkg, res, parser, attrs, flags, outError)) {
//1446                    return null;
//1447                }
//1448            } else if (tagName.equals("overlay")) {
//1449                pkg.mTrustedOverlay = trustedOverlay;
//1450
//1451                sa = res.obtainAttributes(attrs,
//1452                        com.android.internal.R.styleable.AndroidManifestResourceOverlay);
//1453                pkg.mOverlayTarget = sa.getString(
//1454                        com.android.internal.R.styleable.AndroidManifestResourceOverlay_targetPackage);
//1455                pkg.mOverlayPriority = sa.getInt(
//1456                        com.android.internal.R.styleable.AndroidManifestResourceOverlay_priority,
//1457                        -1);
//1458                sa.recycle();
//1459
//1460                if (pkg.mOverlayTarget == null) {
//1461                    outError[0] = "<overlay> does not specify a target package";
//1462                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//1463                    return null;
//1464                }
//1465                if (pkg.mOverlayPriority < 0 || pkg.mOverlayPriority > 9999) {
//1466                    outError[0] = "<overlay> priority must be between 0 and 9999";
//1467                    mParseError =
//1468                        PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//1469                    return null;
//1470                }
//1471                XmlUtils.skipCurrentTag(parser);
//1472
//1473            } else if (tagName.equals("key-sets")) {
//1474                if (!parseKeySets(pkg, res, parser, attrs, outError)) {
//1475                    return null;
//1476                }
//1477            } else if (tagName.equals("permission-group")) {
//1478                if (parsePermissionGroup(pkg, flags, res, parser, attrs, outError) == null) {
//1479                    return null;
//1480                }
//1481            } else if (tagName.equals("permission")) {
//1482                if (parsePermission(pkg, res, parser, attrs, outError) == null) {
//1483                    return null;
//1484                }
//1485            } else if (tagName.equals("permission-tree")) {
//1486                if (parsePermissionTree(pkg, res, parser, attrs, outError) == null) {
//1487                    return null;
//1488                }
//1489            } else if (tagName.equals("uses-permission")) {
//1490                if (!parseUsesPermission(pkg, res, parser, attrs, outError)) {
//1491                    return null;
//1492                }
//1493            } else if (tagName.equals("uses-configuration")) {
//1494                ConfigurationInfo cPref = new ConfigurationInfo();
//1495                sa = res.obtainAttributes(attrs,
//1496                        com.android.internal.R.styleable.AndroidManifestUsesConfiguration);
//1497                cPref.reqTouchScreen = sa.getInt(
//1498                        com.android.internal.R.styleable.AndroidManifestUsesConfiguration_reqTouchScreen,
//1499                        Configuration.TOUCHSCREEN_UNDEFINED);
//1500                cPref.reqKeyboardType = sa.getInt(
//1501                        com.android.internal.R.styleable.AndroidManifestUsesConfiguration_reqKeyboardType,
//1502                        Configuration.KEYBOARD_UNDEFINED);
//1503                if (sa.getBoolean(
//1504                        com.android.internal.R.styleable.AndroidManifestUsesConfiguration_reqHardKeyboard,
//1505                        false)) {
//1506                    cPref.reqInputFeatures |= ConfigurationInfo.INPUT_FEATURE_HARD_KEYBOARD;
//1507                }
//1508                cPref.reqNavigation = sa.getInt(
//1509                        com.android.internal.R.styleable.AndroidManifestUsesConfiguration_reqNavigation,
//1510                        Configuration.NAVIGATION_UNDEFINED);
//1511                if (sa.getBoolean(
//1512                        com.android.internal.R.styleable.AndroidManifestUsesConfiguration_reqFiveWayNav,
//1513                        false)) {
//1514                    cPref.reqInputFeatures |= ConfigurationInfo.INPUT_FEATURE_FIVE_WAY_NAV;
//1515                }
//1516                sa.recycle();
//1517                pkg.configPreferences = ArrayUtils.add(pkg.configPreferences, cPref);
//1518
//1519                XmlUtils.skipCurrentTag(parser);
//1520
//1521            } else if (tagName.equals("uses-feature")) {
//1522                FeatureInfo fi = parseUsesFeature(res, attrs);
//1523                pkg.reqFeatures = ArrayUtils.add(pkg.reqFeatures, fi);
//1524
//1525                if (fi.name == null) {
//1526                    ConfigurationInfo cPref = new ConfigurationInfo();
//1527                    cPref.reqGlEsVersion = fi.reqGlEsVersion;
//1528                    pkg.configPreferences = ArrayUtils.add(pkg.configPreferences, cPref);
//1529                }
//1530
//1531                XmlUtils.skipCurrentTag(parser);
//1532
//1533            } else if (tagName.equals("feature-group")) {
//1534                FeatureGroupInfo group = new FeatureGroupInfo();
//1535                ArrayList<FeatureInfo> features = null;
//1536                final int innerDepth = parser.getDepth();
//1537                while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
//1538                        && (type != XmlPullParser.END_TAG || parser.getDepth() > innerDepth)) {
//1539                    if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
//1540                        continue;
//1541                    }
//1542
//1543                    final String innerTagName = parser.getName();
//1544                    if (innerTagName.equals("uses-feature")) {
//1545                        FeatureInfo featureInfo = parseUsesFeature(res, attrs);
//1546                        // FeatureGroups are stricter and mandate that
//1547                        // any <uses-feature> declared are mandatory.
//1548                        featureInfo.flags |= FeatureInfo.FLAG_REQUIRED;
//1549                        features = ArrayUtils.add(features, featureInfo);
//1550                    } else {
//1551                        Slog.w(TAG, "Unknown element under <feature-group>: " + innerTagName +
//1552                                " at " + mArchiveSourcePath + " " +
//1553                                parser.getPositionDescription());
//1554                    }
//1555                    XmlUtils.skipCurrentTag(parser);
//1556                }
//1557
//1558                if (features != null) {
//1559                    group.features = new FeatureInfo[features.size()];
//1560                    group.features = features.toArray(group.features);
//1561                }
//1562                pkg.featureGroups = ArrayUtils.add(pkg.featureGroups, group);
//1563
//1564            } else if (tagName.equals("uses-sdk")) {
//1565                if (SDK_VERSION > 0) {
//1566                    sa = res.obtainAttributes(attrs,
//1567                            com.android.internal.R.styleable.AndroidManifestUsesSdk);
//1568
//1569                    int minVers = 0;
//1570                    String minCode = null;
//1571                    int targetVers = 0;
//1572                    String targetCode = null;
//1573
//1574                    TypedValue val = sa.peekValue(
//1575                            com.android.internal.R.styleable.AndroidManifestUsesSdk_minSdkVersion);
//1576                    if (val != null) {
//1577                        if (val.type == TypedValue.TYPE_STRING && val.string != null) {
//1578                            targetCode = minCode = val.string.toString();
//1579                        } else {
//1580                            // If it's not a string, it's an integer.
//1581                            targetVers = minVers = val.data;
//1582                        }
//1583                    }
//1584
//1585                    val = sa.peekValue(
//1586                            com.android.internal.R.styleable.AndroidManifestUsesSdk_targetSdkVersion);
//1587                    if (val != null) {
//1588                        if (val.type == TypedValue.TYPE_STRING && val.string != null) {
//1589                            targetCode = minCode = val.string.toString();
//1590                        } else {
//1591                            // If it's not a string, it's an integer.
//1592                            targetVers = val.data;
//1593                        }
//1594                    }
//1595
//1596                    sa.recycle();
//1597
//1598                    if (minCode != null) {
//1599                        boolean allowedCodename = false;
//1600                        for (String codename : SDK_CODENAMES) {
//1601                            if (minCode.equals(codename)) {
//1602                                allowedCodename = true;
//1603                                break;
//1604                            }
//1605                        }
//1606                        if (!allowedCodename) {
//1607                            if (SDK_CODENAMES.length > 0) {
//1608                                outError[0] = "Requires development platform " + minCode
//1609                                        + " (current platform is any of "
//1610                                        + Arrays.toString(SDK_CODENAMES) + ")";
//1611                            } else {
//1612                                outError[0] = "Requires development platform " + minCode
//1613                                        + " but this is a release platform.";
//1614                            }
//1615                            mParseError = PackageManager.INSTALL_FAILED_OLDER_SDK;
//1616                            return null;
//1617                        }
//1618                    } else if (minVers > SDK_VERSION) {
//1619                        outError[0] = "Requires newer sdk version #" + minVers
//1620                                + " (current version is #" + SDK_VERSION + ")";
//1621                        mParseError = PackageManager.INSTALL_FAILED_OLDER_SDK;
//1622                        return null;
//1623                    }
//1624
//1625                    if (targetCode != null) {
//1626                        boolean allowedCodename = false;
//1627                        for (String codename : SDK_CODENAMES) {
//1628                            if (targetCode.equals(codename)) {
//1629                                allowedCodename = true;
//1630                                break;
//1631                            }
//1632                        }
//1633                        if (!allowedCodename) {
//1634                            if (SDK_CODENAMES.length > 0) {
//1635                                outError[0] = "Requires development platform " + targetCode
//1636                                        + " (current platform is any of "
//1637                                        + Arrays.toString(SDK_CODENAMES) + ")";
//1638                            } else {
//1639                                outError[0] = "Requires development platform " + targetCode
//1640                                        + " but this is a release platform.";
//1641                            }
//1642                            mParseError = PackageManager.INSTALL_FAILED_OLDER_SDK;
//1643                            return null;
//1644                        }
//1645                        // If the code matches, it definitely targets this SDK.
//1646                        pkg.applicationInfo.targetSdkVersion
//1647                                = android.os.Build.VERSION_CODES.CUR_DEVELOPMENT;
//1648                    } else {
//1649                        pkg.applicationInfo.targetSdkVersion = targetVers;
//1650                    }
//1651                }
//1652
//1653                XmlUtils.skipCurrentTag(parser);
//1654
//1655            } else if (tagName.equals("supports-screens")) {
//1656                sa = res.obtainAttributes(attrs,
//1657                        com.android.internal.R.styleable.AndroidManifestSupportsScreens);
//1658
//1659                pkg.applicationInfo.requiresSmallestWidthDp = sa.getInteger(
//1660                        com.android.internal.R.styleable.AndroidManifestSupportsScreens_requiresSmallestWidthDp,
//1661                        0);
//1662                pkg.applicationInfo.compatibleWidthLimitDp = sa.getInteger(
//1663                        com.android.internal.R.styleable.AndroidManifestSupportsScreens_compatibleWidthLimitDp,
//1664                        0);
//1665                pkg.applicationInfo.largestWidthLimitDp = sa.getInteger(
//1666                        com.android.internal.R.styleable.AndroidManifestSupportsScreens_largestWidthLimitDp,
//1667                        0);
//1668
//1669                // This is a trick to get a boolean and still able to detect
//1670                // if a value was actually set.
//1671                supportsSmallScreens = sa.getInteger(
//1672                        com.android.internal.R.styleable.AndroidManifestSupportsScreens_smallScreens,
//1673                        supportsSmallScreens);
//1674                supportsNormalScreens = sa.getInteger(
//1675                        com.android.internal.R.styleable.AndroidManifestSupportsScreens_normalScreens,
//1676                        supportsNormalScreens);
//1677                supportsLargeScreens = sa.getInteger(
//1678                        com.android.internal.R.styleable.AndroidManifestSupportsScreens_largeScreens,
//1679                        supportsLargeScreens);
//1680                supportsXLargeScreens = sa.getInteger(
//1681                        com.android.internal.R.styleable.AndroidManifestSupportsScreens_xlargeScreens,
//1682                        supportsXLargeScreens);
//1683                resizeable = sa.getInteger(
//1684                        com.android.internal.R.styleable.AndroidManifestSupportsScreens_resizeable,
//1685                        resizeable);
//1686                anyDensity = sa.getInteger(
//1687                        com.android.internal.R.styleable.AndroidManifestSupportsScreens_anyDensity,
//1688                        anyDensity);
//1689
//1690                sa.recycle();
//1691
//1692                XmlUtils.skipCurrentTag(parser);
//1693
//1694            } else if (tagName.equals("protected-broadcast")) {
//1695                sa = res.obtainAttributes(attrs,
//1696                        com.android.internal.R.styleable.AndroidManifestProtectedBroadcast);
//1697
//1698                // Note: don't allow this value to be a reference to a resource
//1699                // that may change.
//1700                String name = sa.getNonResourceString(
//1701                        com.android.internal.R.styleable.AndroidManifestProtectedBroadcast_name);
//1702
//1703                sa.recycle();
//1704
//1705                if (name != null && (flags&PARSE_IS_SYSTEM) != 0) {
//1706                    if (pkg.protectedBroadcasts == null) {
//1707                        pkg.protectedBroadcasts = new ArrayList<String>();
//1708                    }
//1709                    if (!pkg.protectedBroadcasts.contains(name)) {
//1710                        pkg.protectedBroadcasts.add(name.intern());
//1711                    }
//1712                }
//1713
//1714                XmlUtils.skipCurrentTag(parser);
//1715
//1716            } else if (tagName.equals("instrumentation")) {
//1717                if (parseInstrumentation(pkg, res, parser, attrs, outError) == null) {
//1718                    return null;
//1719                }
//1720
//1721            } else if (tagName.equals("original-package")) {
//1722                sa = res.obtainAttributes(attrs,
//1723                        com.android.internal.R.styleable.AndroidManifestOriginalPackage);
//1724
//1725                String orig =sa.getNonConfigurationString(
//1726                        com.android.internal.R.styleable.AndroidManifestOriginalPackage_name, 0);
//1727                if (!pkg.packageName.equals(orig)) {
//1728                    if (pkg.mOriginalPackages == null) {
//1729                        pkg.mOriginalPackages = new ArrayList<String>();
//1730                        pkg.mRealPackage = pkg.packageName;
//1731                    }
//1732                    pkg.mOriginalPackages.add(orig);
//1733                }
//1734
//1735                sa.recycle();
//1736
//1737                XmlUtils.skipCurrentTag(parser);
//1738
//1739            } else if (tagName.equals("adopt-permissions")) {
//1740                sa = res.obtainAttributes(attrs,
//1741                        com.android.internal.R.styleable.AndroidManifestOriginalPackage);
//1742
//1743                String name = sa.getNonConfigurationString(
//1744                        com.android.internal.R.styleable.AndroidManifestOriginalPackage_name, 0);
//1745
//1746                sa.recycle();
//1747
//1748                if (name != null) {
//1749                    if (pkg.mAdoptPermissions == null) {
//1750                        pkg.mAdoptPermissions = new ArrayList<String>();
//1751                    }
//1752                    pkg.mAdoptPermissions.add(name);
//1753                }
//1754
//1755                XmlUtils.skipCurrentTag(parser);
//1756
//1757            } else if (tagName.equals("uses-gl-texture")) {
//1758                // Just skip this tag
//1759                XmlUtils.skipCurrentTag(parser);
//1760                continue;
//1761
//1762            } else if (tagName.equals("compatible-screens")) {
//1763                // Just skip this tag
//1764                XmlUtils.skipCurrentTag(parser);
//1765                continue;
//1766            } else if (tagName.equals("supports-input")) {
//1767                XmlUtils.skipCurrentTag(parser);
//1768                continue;
//1769
//1770            } else if (tagName.equals("eat-comment")) {
//1771                // Just skip this tag
//1772                XmlUtils.skipCurrentTag(parser);
//1773                continue;
//1774
//1775            } else if (RIGID_PARSER) {
//1776                outError[0] = "Bad element under <manifest>: "
//1777                    + parser.getName();
//1778                mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//1779                return null;
//1780
//1781            } else {
//1782                Slog.w(TAG, "Unknown element under <manifest>: " + parser.getName()
//1783                        + " at " + mArchiveSourcePath + " "
//1784                        + parser.getPositionDescription());
//1785                XmlUtils.skipCurrentTag(parser);
//1786                continue;
//1787            }
//1788        }
//1789
//1790        if (!foundApp && pkg.instrumentation.size() == 0) {
//1791            outError[0] = "<manifest> does not contain an <application> or <instrumentation>";
//1792            mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_EMPTY;
//1793        }
//1794
//1795        final int NP = PackageParser.NEW_PERMISSIONS.length;
//1796        StringBuilder implicitPerms = null;
//1797        for (int ip=0; ip<NP; ip++) {
//1798            final PackageParser.NewPermissionInfo npi
//1799                    = PackageParser.NEW_PERMISSIONS[ip];
//1800            if (pkg.applicationInfo.targetSdkVersion >= npi.sdkVersion) {
//1801                break;
//1802            }
//1803            if (!pkg.requestedPermissions.contains(npi.name)) {
//1804                if (implicitPerms == null) {
//1805                    implicitPerms = new StringBuilder(128);
//1806                    implicitPerms.append(pkg.packageName);
//1807                    implicitPerms.append(": compat added ");
//1808                } else {
//1809                    implicitPerms.append(' ');
//1810                }
//1811                implicitPerms.append(npi.name);
//1812                pkg.requestedPermissions.add(npi.name);
//1813                pkg.requestedPermissionsRequired.add(Boolean.TRUE);
//1814            }
//1815        }
//1816        if (implicitPerms != null) {
//1817            Slog.i(TAG, implicitPerms.toString());
//1818        }
//1819
//1820        final int NS = PackageParser.SPLIT_PERMISSIONS.length;
//1821        for (int is=0; is<NS; is++) {
//1822            final PackageParser.SplitPermissionInfo spi
//1823                    = PackageParser.SPLIT_PERMISSIONS[is];
//1824            if (pkg.applicationInfo.targetSdkVersion >= spi.targetSdk
//1825                    || !pkg.requestedPermissions.contains(spi.rootPerm)) {
//1826                continue;
//1827            }
//1828            for (int in=0; in<spi.newPerms.length; in++) {
//1829                final String perm = spi.newPerms[in];
//1830                if (!pkg.requestedPermissions.contains(perm)) {
//1831                    pkg.requestedPermissions.add(perm);
//1832                    pkg.requestedPermissionsRequired.add(Boolean.TRUE);
//1833                }
//1834            }
//1835        }
//1836
//1837        if (supportsSmallScreens < 0 || (supportsSmallScreens > 0
//1838                && pkg.applicationInfo.targetSdkVersion
//1839                        >= android.os.Build.VERSION_CODES.DONUT)) {
//1840            pkg.applicationInfo.flags |= ApplicationInfo.FLAG_SUPPORTS_SMALL_SCREENS;
//1841        }
//1842        if (supportsNormalScreens != 0) {
//1843            pkg.applicationInfo.flags |= ApplicationInfo.FLAG_SUPPORTS_NORMAL_SCREENS;
//1844        }
//1845        if (supportsLargeScreens < 0 || (supportsLargeScreens > 0
//1846                && pkg.applicationInfo.targetSdkVersion
//1847                        >= android.os.Build.VERSION_CODES.DONUT)) {
//1848            pkg.applicationInfo.flags |= ApplicationInfo.FLAG_SUPPORTS_LARGE_SCREENS;
//1849        }
//1850        if (supportsXLargeScreens < 0 || (supportsXLargeScreens > 0
//1851                && pkg.applicationInfo.targetSdkVersion
//1852                        >= android.os.Build.VERSION_CODES.GINGERBREAD)) {
//1853            pkg.applicationInfo.flags |= ApplicationInfo.FLAG_SUPPORTS_XLARGE_SCREENS;
//1854        }
//1855        if (resizeable < 0 || (resizeable > 0
//1856                && pkg.applicationInfo.targetSdkVersion
//1857                        >= android.os.Build.VERSION_CODES.DONUT)) {
//1858            pkg.applicationInfo.flags |= ApplicationInfo.FLAG_RESIZEABLE_FOR_SCREENS;
//1859        }
//1860        if (anyDensity < 0 || (anyDensity > 0
//1861                && pkg.applicationInfo.targetSdkVersion
//1862                        >= android.os.Build.VERSION_CODES.DONUT)) {
//1863            pkg.applicationInfo.flags |= ApplicationInfo.FLAG_SUPPORTS_SCREEN_DENSITIES;
//1864        }
//1865
//1866        /*
//1867         * b/8528162: Ignore the <uses-permission android:required> attribute if
//1868         * targetSdkVersion < JELLY_BEAN_MR2. There are lots of apps in the wild
//1869         * which are improperly using this attribute, even though it never worked.
//1870         */
//1871        if (pkg.applicationInfo.targetSdkVersion < Build.VERSION_CODES.JELLY_BEAN_MR2) {
//1872            for (int i = 0; i < pkg.requestedPermissionsRequired.size(); i++) {
//1873                pkg.requestedPermissionsRequired.set(i, Boolean.TRUE);
//1874            }
//1875        }
//1876
//1877        return pkg;
//1878    }
//1879
//1880    private FeatureInfo parseUsesFeature(Resources res, AttributeSet attrs)
//1881            throws XmlPullParserException, IOException {
//1882        FeatureInfo fi = new FeatureInfo();
//1883        TypedArray sa = res.obtainAttributes(attrs,
//1884                com.android.internal.R.styleable.AndroidManifestUsesFeature);
//1885        // Note: don't allow this value to be a reference to a resource
//1886        // that may change.
//1887        fi.name = sa.getNonResourceString(
//1888                com.android.internal.R.styleable.AndroidManifestUsesFeature_name);
//1889        if (fi.name == null) {
//1890            fi.reqGlEsVersion = sa.getInt(
//1891                        com.android.internal.R.styleable.AndroidManifestUsesFeature_glEsVersion,
//1892                        FeatureInfo.GL_ES_VERSION_UNDEFINED);
//1893        }
//1894        if (sa.getBoolean(
//1895                com.android.internal.R.styleable.AndroidManifestUsesFeature_required, true)) {
//1896            fi.flags |= FeatureInfo.FLAG_REQUIRED;
//1897        }
//1898        sa.recycle();
//1899        return fi;
//1900    }
//1901
//1902    private boolean parseUsesPermission(Package pkg, Resources res, XmlResourceParser parser,
//1903                                        AttributeSet attrs, String[] outError)
//1904            throws XmlPullParserException, IOException {
//1905        TypedArray sa = res.obtainAttributes(attrs,
//1906                com.android.internal.R.styleable.AndroidManifestUsesPermission);
//1907
//1908        // Note: don't allow this value to be a reference to a resource
//1909        // that may change.
//1910        String name = sa.getNonResourceString(
//1911                com.android.internal.R.styleable.AndroidManifestUsesPermission_name);
//1912/*
//1913        boolean required = sa.getBoolean(
//1914                com.android.internal.R.styleable.AndroidManifestUsesPermission_required, true);
//1915*/
//1916        boolean required = true; // Optional <uses-permission> not supported
//1917
//1918        int maxSdkVersion = 0;
//1919        TypedValue val = sa.peekValue(
//1920                com.android.internal.R.styleable.AndroidManifestUsesPermission_maxSdkVersion);
//1921        if (val != null) {
//1922            if (val.type >= TypedValue.TYPE_FIRST_INT && val.type <= TypedValue.TYPE_LAST_INT) {
//1923                maxSdkVersion = val.data;
//1924            }
//1925        }
//1926
//1927        sa.recycle();
//1928
//1929        if ((maxSdkVersion == 0) || (maxSdkVersion >= Build.VERSION.RESOURCES_SDK_INT)) {
//1930            if (name != null) {
//1931                int index = pkg.requestedPermissions.indexOf(name);
//1932                if (index == -1) {
//1933                    pkg.requestedPermissions.add(name.intern());
//1934                    pkg.requestedPermissionsRequired.add(required ? Boolean.TRUE : Boolean.FALSE);
//1935                } else {
//1936                    if (pkg.requestedPermissionsRequired.get(index) != required) {
//1937                        outError[0] = "conflicting <uses-permission> entries";
//1938                        mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//1939                        return false;
//1940                    }
//1941                }
//1942            }
//1943        }
//1944
//1945        XmlUtils.skipCurrentTag(parser);
//1946        return true;
//1947    }
//1948
//1949    private static String buildClassName(String pkg, CharSequence clsSeq,
//1950            String[] outError) {
//1951        if (clsSeq == null || clsSeq.length() <= 0) {
//1952            outError[0] = "Empty class name in package " + pkg;
//1953            return null;
//1954        }
//1955        String cls = clsSeq.toString();
//1956        char c = cls.charAt(0);
//1957        if (c == '.') {
//1958            return (pkg + cls).intern();
//1959        }
//1960        if (cls.indexOf('.') < 0) {
//1961            StringBuilder b = new StringBuilder(pkg);
//1962            b.append('.');
//1963            b.append(cls);
//1964            return b.toString().intern();
//1965        }
//1966        if (c >= 'a' && c <= 'z') {
//1967            return cls.intern();
//1968        }
//1969        outError[0] = "Bad class name " + cls + " in package " + pkg;
//1970        return null;
//1971    }
//1972
//1973    private static String buildCompoundName(String pkg,
//1974            CharSequence procSeq, String type, String[] outError) {
//1975        String proc = procSeq.toString();
//1976        char c = proc.charAt(0);
//1977        if (pkg != null && c == ':') {
//1978            if (proc.length() < 2) {
//1979                outError[0] = "Bad " + type + " name " + proc + " in package " + pkg
//1980                        + ": must be at least two characters";
//1981                return null;
//1982            }
//1983            String subName = proc.substring(1);
//1984            String nameError = validateName(subName, false);
//1985            if (nameError != null) {
//1986                outError[0] = "Invalid " + type + " name " + proc + " in package "
//1987                        + pkg + ": " + nameError;
//1988                return null;
//1989            }
//1990            return (pkg + proc).intern();
//1991        }
//1992        String nameError = validateName(proc, true);
//1993        if (nameError != null && !"system".equals(proc)) {
//1994            outError[0] = "Invalid " + type + " name " + proc + " in package "
//1995                    + pkg + ": " + nameError;
//1996            return null;
//1997        }
//1998        return proc.intern();
//1999    }
//2000
//2001    private static String buildProcessName(String pkg, String defProc,
//2002            CharSequence procSeq, int flags, String[] separateProcesses,
//2003            String[] outError) {
//2004        if ((flags&PARSE_IGNORE_PROCESSES) != 0 && !"system".equals(procSeq)) {
//2005            return defProc != null ? defProc : pkg;
//2006        }
//2007        if (separateProcesses != null) {
//2008            for (int i=separateProcesses.length-1; i>=0; i--) {
//2009                String sp = separateProcesses[i];
//2010                if (sp.equals(pkg) || sp.equals(defProc) || sp.equals(procSeq)) {
//2011                    return pkg;
//2012                }
//2013            }
//2014        }
//2015        if (procSeq == null || procSeq.length() <= 0) {
//2016            return defProc;
//2017        }
//2018        return buildCompoundName(pkg, procSeq, "process", outError);
//2019    }
//2020
//2021    private static String buildTaskAffinityName(String pkg, String defProc,
//2022            CharSequence procSeq, String[] outError) {
//2023        if (procSeq == null) {
//2024            return defProc;
//2025        }
//2026        if (procSeq.length() <= 0) {
//2027            return null;
//2028        }
//2029        return buildCompoundName(pkg, procSeq, "taskAffinity", outError);
//2030    }
//2031
//2032    private boolean parseKeySets(Package owner, Resources res,
//2033            XmlPullParser parser, AttributeSet attrs, String[] outError)
//2034            throws XmlPullParserException, IOException {
//2035        // we've encountered the 'key-sets' tag
//2036        // all the keys and keysets that we want must be defined here
//2037        // so we're going to iterate over the parser and pull out the things we want
//2038        int outerDepth = parser.getDepth();
//2039        int currentKeySetDepth = -1;
//2040        int type;
//2041        String currentKeySet = null;
//2042        ArrayMap<String, PublicKey> publicKeys = new ArrayMap<String, PublicKey>();
//2043        ArraySet<String> upgradeKeySets = new ArraySet<String>();
//2044        ArrayMap<String, ArraySet<String>> definedKeySets = new ArrayMap<String, ArraySet<String>>();
//2045        ArraySet<String> improperKeySets = new ArraySet<String>();
//2046        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
//2047                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
//2048            if (type == XmlPullParser.END_TAG) {
//2049                if (parser.getDepth() == currentKeySetDepth) {
//2050                    currentKeySet = null;
//2051                    currentKeySetDepth = -1;
//2052                }
//2053                continue;
//2054            }
//2055            String tagName = parser.getName();
//2056            if (tagName.equals("key-set")) {
//2057                if (currentKeySet != null) {
//2058                    Slog.w(TAG, "Improperly nested 'key-set' tag at "
//2059                            + parser.getPositionDescription());
//2060                    return false;
//2061                }
//2062                final TypedArray sa = res.obtainAttributes(attrs,
//2063                        com.android.internal.R.styleable.AndroidManifestKeySet);
//2064                final String keysetName = sa.getNonResourceString(
//2065                    com.android.internal.R.styleable.AndroidManifestKeySet_name);
//2066                definedKeySets.put(keysetName, new ArraySet<String>());
//2067                currentKeySet = keysetName;
//2068                currentKeySetDepth = parser.getDepth();
//2069                sa.recycle();
//2070            } else if (tagName.equals("public-key")) {
//2071                if (currentKeySet == null) {
//2072                    Slog.w(TAG, "Improperly nested 'public-key' tag at "
//2073                            + parser.getPositionDescription());
//2074                    return false;
//2075                }
//2076                final TypedArray sa = res.obtainAttributes(attrs,
//2077                        com.android.internal.R.styleable.AndroidManifestPublicKey);
//2078                final String publicKeyName = sa.getNonResourceString(
//2079                        com.android.internal.R.styleable.AndroidManifestPublicKey_name);
//2080                final String encodedKey = sa.getNonResourceString(
//2081                            com.android.internal.R.styleable.AndroidManifestPublicKey_value);
//2082                if (encodedKey == null && publicKeys.get(publicKeyName) == null) {
//2083                    Slog.w(TAG, "'public-key' " + publicKeyName + " must define a public-key value"
//2084                            + " on first use at " + parser.getPositionDescription());
//2085                    sa.recycle();
//2086                    return false;
//2087                } else if (encodedKey != null) {
//2088                    PublicKey currentKey = parsePublicKey(encodedKey);
//2089                    if (currentKey == null) {
//2090                        Slog.w(TAG, "No recognized valid key in 'public-key' tag at "
//2091                                + parser.getPositionDescription() + " key-set " + currentKeySet
//2092                                + " will not be added to the package's defined key-sets.");
//2093                        sa.recycle();
//2094                        improperKeySets.add(currentKeySet);
//2095                        XmlUtils.skipCurrentTag(parser);
//2096                        continue;
//2097                    }
//2098                    if (publicKeys.get(publicKeyName) == null
//2099                            || publicKeys.get(publicKeyName).equals(currentKey)) {
//2100
//2101                        /* public-key first definition, or matches old definition */
//2102                        publicKeys.put(publicKeyName, currentKey);
//2103                    } else {
//2104                        Slog.w(TAG, "Value of 'public-key' " + publicKeyName
//2105                               + " conflicts with previously defined value at "
//2106                               + parser.getPositionDescription());
//2107                        sa.recycle();
//2108                        return false;
//2109                    }
//2110                }
//2111                definedKeySets.get(currentKeySet).add(publicKeyName);
//2112                sa.recycle();
//2113                XmlUtils.skipCurrentTag(parser);
//2114            } else if (tagName.equals("upgrade-key-set")) {
//2115                final TypedArray sa = res.obtainAttributes(attrs,
//2116                        com.android.internal.R.styleable.AndroidManifestUpgradeKeySet);
//2117                String name = sa.getNonResourceString(
//2118                        com.android.internal.R.styleable.AndroidManifestUpgradeKeySet_name);
//2119                upgradeKeySets.add(name);
//2120                sa.recycle();
//2121                XmlUtils.skipCurrentTag(parser);
//2122            } else if (RIGID_PARSER) {
//2123                Slog.w(TAG, "Bad element under <key-sets>: " + parser.getName()
//2124                        + " at " + mArchiveSourcePath + " "
//2125                        + parser.getPositionDescription());
//2126                return false;
//2127            } else {
//2128                Slog.w(TAG, "Unknown element under <key-sets>: " + parser.getName()
//2129                        + " at " + mArchiveSourcePath + " "
//2130                        + parser.getPositionDescription());
//2131                XmlUtils.skipCurrentTag(parser);
//2132                continue;
//2133            }
//2134        }
//2135        Set<String> publicKeyNames = publicKeys.keySet();
//2136        if (publicKeyNames.removeAll(definedKeySets.keySet())) {
//2137            Slog.w(TAG, "Package" + owner.packageName + " AndroidManifext.xml "
//2138                   + "'key-set' and 'public-key' names must be distinct.");
//2139            return false;
//2140        }
//2141        owner.mKeySetMapping = new ArrayMap<String, ArraySet<PublicKey>>();
//2142        for (ArrayMap.Entry<String, ArraySet<String>> e: definedKeySets.entrySet()) {
//2143            final String keySetName = e.getKey();
//2144            if (e.getValue().size() == 0) {
//2145                Slog.w(TAG, "Package" + owner.packageName + " AndroidManifext.xml "
//2146                        + "'key-set' " + keySetName + " has no valid associated 'public-key'."
//2147                        + " Not including in package's defined key-sets.");
//2148                continue;
//2149            } else if (improperKeySets.contains(keySetName)) {
//2150                Slog.w(TAG, "Package" + owner.packageName + " AndroidManifext.xml "
//2151                        + "'key-set' " + keySetName + " contained improper 'public-key'"
//2152                        + " tags. Not including in package's defined key-sets.");
//2153                continue;
//2154            }
//2155            owner.mKeySetMapping.put(keySetName, new ArraySet<PublicKey>());
//2156            for (String s : e.getValue()) {
//2157                owner.mKeySetMapping.get(keySetName).add(publicKeys.get(s));
//2158            }
//2159        }
//2160        if (owner.mKeySetMapping.keySet().containsAll(upgradeKeySets)) {
//2161            owner.mUpgradeKeySets = upgradeKeySets;
//2162        } else {
//2163            Slog.w(TAG, "Package" + owner.packageName + " AndroidManifext.xml "
//2164                   + "does not define all 'upgrade-key-set's .");
//2165            return false;
//2166        }
//2167        return true;
//2168    }
//2169
//2170    private PermissionGroup parsePermissionGroup(Package owner, int flags, Resources res,
//2171            XmlPullParser parser, AttributeSet attrs, String[] outError)
//2172        throws XmlPullParserException, IOException {
//2173        PermissionGroup perm = new PermissionGroup(owner);
//2174
//2175        TypedArray sa = res.obtainAttributes(attrs,
//2176                com.android.internal.R.styleable.AndroidManifestPermissionGroup);
//2177
//2178        if (!parsePackageItemInfo(owner, perm.info, outError,
//2179                "<permission-group>", sa,
//2180                com.android.internal.R.styleable.AndroidManifestPermissionGroup_name,
//2181                com.android.internal.R.styleable.AndroidManifestPermissionGroup_label,
//2182                com.android.internal.R.styleable.AndroidManifestPermissionGroup_icon,
//2183                com.android.internal.R.styleable.AndroidManifestPermissionGroup_logo,
//2184                com.android.internal.R.styleable.AndroidManifestPermissionGroup_banner)) {
//2185            sa.recycle();
//2186            mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2187            return null;
//2188        }
//2189
//2190        perm.info.descriptionRes = sa.getResourceId(
//2191                com.android.internal.R.styleable.AndroidManifestPermissionGroup_description,
//2192                0);
//2193        perm.info.flags = sa.getInt(
//2194                com.android.internal.R.styleable.AndroidManifestPermissionGroup_permissionGroupFlags, 0);
//2195        perm.info.priority = sa.getInt(
//2196                com.android.internal.R.styleable.AndroidManifestPermissionGroup_priority, 0);
//2197        if (perm.info.priority > 0 && (flags&PARSE_IS_SYSTEM) == 0) {
//2198            perm.info.priority = 0;
//2199        }
//2200
//2201        sa.recycle();
//2202
//2203        if (!parseAllMetaData(res, parser, attrs, "<permission-group>", perm,
//2204                outError)) {
//2205            mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2206            return null;
//2207        }
//2208
//2209        owner.permissionGroups.add(perm);
//2210
//2211        return perm;
//2212    }
//2213
//2214    private Permission parsePermission(Package owner, Resources res,
//2215            XmlPullParser parser, AttributeSet attrs, String[] outError)
//2216        throws XmlPullParserException, IOException {
//2217        Permission perm = new Permission(owner);
//2218
//2219        TypedArray sa = res.obtainAttributes(attrs,
//2220                com.android.internal.R.styleable.AndroidManifestPermission);
//2221
//2222        if (!parsePackageItemInfo(owner, perm.info, outError,
//2223                "<permission>", sa,
//2224                com.android.internal.R.styleable.AndroidManifestPermission_name,
//2225                com.android.internal.R.styleable.AndroidManifestPermission_label,
//2226                com.android.internal.R.styleable.AndroidManifestPermission_icon,
//2227                com.android.internal.R.styleable.AndroidManifestPermission_logo,
//2228                com.android.internal.R.styleable.AndroidManifestPermission_banner)) {
//2229            sa.recycle();
//2230            mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2231            return null;
//2232        }
//2233
//2234        // Note: don't allow this value to be a reference to a resource
//2235        // that may change.
//2236        perm.info.group = sa.getNonResourceString(
//2237                com.android.internal.R.styleable.AndroidManifestPermission_permissionGroup);
//2238        if (perm.info.group != null) {
//2239            perm.info.group = perm.info.group.intern();
//2240        }
//2241
//2242        perm.info.descriptionRes = sa.getResourceId(
//2243                com.android.internal.R.styleable.AndroidManifestPermission_description,
//2244                0);
//2245
//2246        perm.info.protectionLevel = sa.getInt(
//2247                com.android.internal.R.styleable.AndroidManifestPermission_protectionLevel,
//2248                PermissionInfo.PROTECTION_NORMAL);
//2249
//2250        perm.info.flags = sa.getInt(
//2251                com.android.internal.R.styleable.AndroidManifestPermission_permissionFlags, 0);
//2252
//2253        sa.recycle();
//2254
//2255        if (perm.info.protectionLevel == -1) {
//2256            outError[0] = "<permission> does not specify protectionLevel";
//2257            mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2258            return null;
//2259        }
//2260
//2261        perm.info.protectionLevel = PermissionInfo.fixProtectionLevel(perm.info.protectionLevel);
//2262
//2263        if ((perm.info.protectionLevel&PermissionInfo.PROTECTION_MASK_FLAGS) != 0) {
//2264            if ((perm.info.protectionLevel&PermissionInfo.PROTECTION_MASK_BASE) !=
//2265                    PermissionInfo.PROTECTION_SIGNATURE) {
//2266                outError[0] = "<permission>  protectionLevel specifies a flag but is "
//2267                        + "not based on signature type";
//2268                mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2269                return null;
//2270            }
//2271        }
//2272
//2273        if (!parseAllMetaData(res, parser, attrs, "<permission>", perm,
//2274                outError)) {
//2275            mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2276            return null;
//2277        }
//2278
//2279        owner.permissions.add(perm);
//2280
//2281        return perm;
//2282    }
//2283
//2284    private Permission parsePermissionTree(Package owner, Resources res,
//2285            XmlPullParser parser, AttributeSet attrs, String[] outError)
//2286        throws XmlPullParserException, IOException {
//2287        Permission perm = new Permission(owner);
//2288
//2289        TypedArray sa = res.obtainAttributes(attrs,
//2290                com.android.internal.R.styleable.AndroidManifestPermissionTree);
//2291
//2292        if (!parsePackageItemInfo(owner, perm.info, outError,
//2293                "<permission-tree>", sa,
//2294                com.android.internal.R.styleable.AndroidManifestPermissionTree_name,
//2295                com.android.internal.R.styleable.AndroidManifestPermissionTree_label,
//2296                com.android.internal.R.styleable.AndroidManifestPermissionTree_icon,
//2297                com.android.internal.R.styleable.AndroidManifestPermissionTree_logo,
//2298                com.android.internal.R.styleable.AndroidManifestPermissionTree_banner)) {
//2299            sa.recycle();
//2300            mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2301            return null;
//2302        }
//2303
//2304        sa.recycle();
//2305
//2306        int index = perm.info.name.indexOf('.');
//2307        if (index > 0) {
//2308            index = perm.info.name.indexOf('.', index+1);
//2309        }
//2310        if (index < 0) {
//2311            outError[0] = "<permission-tree> name has less than three segments: "
//2312                + perm.info.name;
//2313            mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2314            return null;
//2315        }
//2316
//2317        perm.info.descriptionRes = 0;
//2318        perm.info.protectionLevel = PermissionInfo.PROTECTION_NORMAL;
//2319        perm.tree = true;
//2320
//2321        if (!parseAllMetaData(res, parser, attrs, "<permission-tree>", perm,
//2322                outError)) {
//2323            mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2324            return null;
//2325        }
//2326
//2327        owner.permissions.add(perm);
//2328
//2329        return perm;
//2330    }
//2331
//2332    private Instrumentation parseInstrumentation(Package owner, Resources res,
//2333            XmlPullParser parser, AttributeSet attrs, String[] outError)
//2334        throws XmlPullParserException, IOException {
//2335        TypedArray sa = res.obtainAttributes(attrs,
//2336                com.android.internal.R.styleable.AndroidManifestInstrumentation);
//2337
//2338        if (mParseInstrumentationArgs == null) {
//2339            mParseInstrumentationArgs = new ParsePackageItemArgs(owner, outError,
//2340                    com.android.internal.R.styleable.AndroidManifestInstrumentation_name,
//2341                    com.android.internal.R.styleable.AndroidManifestInstrumentation_label,
//2342                    com.android.internal.R.styleable.AndroidManifestInstrumentation_icon,
//2343                    com.android.internal.R.styleable.AndroidManifestInstrumentation_logo,
//2344                    com.android.internal.R.styleable.AndroidManifestInstrumentation_banner);
//2345            mParseInstrumentationArgs.tag = "<instrumentation>";
//2346        }
//2347
//2348        mParseInstrumentationArgs.sa = sa;
//2349
//2350        Instrumentation a = new Instrumentation(mParseInstrumentationArgs,
//2351                new InstrumentationInfo());
//2352        if (outError[0] != null) {
//2353            sa.recycle();
//2354            mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2355            return null;
//2356        }
//2357
//2358        String str;
//2359        // Note: don't allow this value to be a reference to a resource
//2360        // that may change.
//2361        str = sa.getNonResourceString(
//2362                com.android.internal.R.styleable.AndroidManifestInstrumentation_targetPackage);
//2363        a.info.targetPackage = str != null ? str.intern() : null;
//2364
//2365        a.info.handleProfiling = sa.getBoolean(
//2366                com.android.internal.R.styleable.AndroidManifestInstrumentation_handleProfiling,
//2367                false);
//2368
//2369        a.info.functionalTest = sa.getBoolean(
//2370                com.android.internal.R.styleable.AndroidManifestInstrumentation_functionalTest,
//2371                false);
//2372
//2373        sa.recycle();
//2374
//2375        if (a.info.targetPackage == null) {
//2376            outError[0] = "<instrumentation> does not specify targetPackage";
//2377            mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2378            return null;
//2379        }
//2380
//2381        if (!parseAllMetaData(res, parser, attrs, "<instrumentation>", a,
//2382                outError)) {
//2383            mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2384            return null;
//2385        }
//2386
//2387        owner.instrumentation.add(a);
//2388
//2389        return a;
//2390    }
//2391
//2392    /**
//2393     * Parse the {@code application} XML tree at the current parse location in a
//2394     * <em>base APK</em> manifest.
//2395     * <p>
//2396     * When adding new features, carefully consider if they should also be
//2397     * supported by split APKs.
//2398     */
//2399    private boolean parseBaseApplication(Package owner, Resources res,
//2400            XmlPullParser parser, AttributeSet attrs, int flags, String[] outError)
//2401        throws XmlPullParserException, IOException {
//2402        final ApplicationInfo ai = owner.applicationInfo;
//2403        final String pkgName = owner.applicationInfo.packageName;
//2404
//2405        TypedArray sa = res.obtainAttributes(attrs,
//2406                com.android.internal.R.styleable.AndroidManifestApplication);
//2407
//2408        String name = sa.getNonConfigurationString(
//2409                com.android.internal.R.styleable.AndroidManifestApplication_name, 0);
//2410        if (name != null) {
//2411            ai.className = buildClassName(pkgName, name, outError);
//2412            if (ai.className == null) {
//2413                sa.recycle();
//2414                mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2415                return false;
//2416            }
//2417        }
//2418
//2419        String manageSpaceActivity = sa.getNonConfigurationString(
//2420                com.android.internal.R.styleable.AndroidManifestApplication_manageSpaceActivity,
//2421                Configuration.NATIVE_CONFIG_VERSION);
//2422        if (manageSpaceActivity != null) {
//2423            ai.manageSpaceActivityName = buildClassName(pkgName, manageSpaceActivity,
//2424                    outError);
//2425        }
//2426
//2427        boolean allowBackup = sa.getBoolean(
//2428                com.android.internal.R.styleable.AndroidManifestApplication_allowBackup, true);
//2429        if (allowBackup) {
//2430            ai.flags |= ApplicationInfo.FLAG_ALLOW_BACKUP;
//2431
//2432            // backupAgent, killAfterRestore, and restoreAnyVersion are only relevant
//2433            // if backup is possible for the given application.
//2434            String backupAgent = sa.getNonConfigurationString(
//2435                    com.android.internal.R.styleable.AndroidManifestApplication_backupAgent,
//2436                    Configuration.NATIVE_CONFIG_VERSION);
//2437            if (backupAgent != null) {
//2438                ai.backupAgentName = buildClassName(pkgName, backupAgent, outError);
//2439                if (DEBUG_BACKUP) {
//2440                    Slog.v(TAG, "android:backupAgent = " + ai.backupAgentName
//2441                            + " from " + pkgName + "+" + backupAgent);
//2442                }
//2443
//2444                if (sa.getBoolean(
//2445                        com.android.internal.R.styleable.AndroidManifestApplication_killAfterRestore,
//2446                        true)) {
//2447                    ai.flags |= ApplicationInfo.FLAG_KILL_AFTER_RESTORE;
//2448                }
//2449                if (sa.getBoolean(
//2450                        com.android.internal.R.styleable.AndroidManifestApplication_restoreAnyVersion,
//2451                        false)) {
//2452                    ai.flags |= ApplicationInfo.FLAG_RESTORE_ANY_VERSION;
//2453                }
//2454                if (sa.getBoolean(
//2455                        com.android.internal.R.styleable.AndroidManifestApplication_fullBackupOnly,
//2456                        false)) {
//2457                    ai.flags |= ApplicationInfo.FLAG_FULL_BACKUP_ONLY;
//2458                }
//2459            }
//2460        }
//2461
//2462        TypedValue v = sa.peekValue(
//2463                com.android.internal.R.styleable.AndroidManifestApplication_label);
//2464        if (v != null && (ai.labelRes=v.resourceId) == 0) {
//2465            ai.nonLocalizedLabel = v.coerceToString();
//2466        }
//2467
//2468        ai.icon = sa.getResourceId(
//2469                com.android.internal.R.styleable.AndroidManifestApplication_icon, 0);
//2470        ai.logo = sa.getResourceId(
//2471                com.android.internal.R.styleable.AndroidManifestApplication_logo, 0);
//2472        ai.banner = sa.getResourceId(
//2473                com.android.internal.R.styleable.AndroidManifestApplication_banner, 0);
//2474        ai.theme = sa.getResourceId(
//2475                com.android.internal.R.styleable.AndroidManifestApplication_theme, 0);
//2476        ai.descriptionRes = sa.getResourceId(
//2477                com.android.internal.R.styleable.AndroidManifestApplication_description, 0);
//2478
//2479        if ((flags&PARSE_IS_SYSTEM) != 0) {
//2480            if (sa.getBoolean(
//2481                    com.android.internal.R.styleable.AndroidManifestApplication_persistent,
//2482                    false)) {
//2483                ai.flags |= ApplicationInfo.FLAG_PERSISTENT;
//2484            }
//2485        }
//2486
//2487        if (sa.getBoolean(
//2488                com.android.internal.R.styleable.AndroidManifestApplication_requiredForAllUsers,
//2489                false)) {
//2490            owner.mRequiredForAllUsers = true;
//2491        }
//2492
//2493        String restrictedAccountType = sa.getString(com.android.internal.R.styleable
//2494                .AndroidManifestApplication_restrictedAccountType);
//2495        if (restrictedAccountType != null && restrictedAccountType.length() > 0) {
//2496            owner.mRestrictedAccountType = restrictedAccountType;
//2497        }
//2498
//2499        String requiredAccountType = sa.getString(com.android.internal.R.styleable
//2500                .AndroidManifestApplication_requiredAccountType);
//2501        if (requiredAccountType != null && requiredAccountType.length() > 0) {
//2502            owner.mRequiredAccountType = requiredAccountType;
//2503        }
//2504
//2505        if (sa.getBoolean(
//2506                com.android.internal.R.styleable.AndroidManifestApplication_debuggable,
//2507                false)) {
//2508            ai.flags |= ApplicationInfo.FLAG_DEBUGGABLE;
//2509        }
//2510
//2511        if (sa.getBoolean(
//2512                com.android.internal.R.styleable.AndroidManifestApplication_vmSafeMode,
//2513                false)) {
//2514            ai.flags |= ApplicationInfo.FLAG_VM_SAFE_MODE;
//2515        }
//2516
//2517        owner.baseHardwareAccelerated = sa.getBoolean(
//2518                com.android.internal.R.styleable.AndroidManifestApplication_hardwareAccelerated,
//2519                owner.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.ICE_CREAM_SANDWICH);
//2520
//2521        if (sa.getBoolean(
//2522                com.android.internal.R.styleable.AndroidManifestApplication_hasCode,
//2523                true)) {
//2524            ai.flags |= ApplicationInfo.FLAG_HAS_CODE;
//2525        }
//2526
//2527        if (sa.getBoolean(
//2528                com.android.internal.R.styleable.AndroidManifestApplication_allowTaskReparenting,
//2529                false)) {
//2530            ai.flags |= ApplicationInfo.FLAG_ALLOW_TASK_REPARENTING;
//2531        }
//2532
//2533        if (sa.getBoolean(
//2534                com.android.internal.R.styleable.AndroidManifestApplication_allowClearUserData,
//2535                true)) {
//2536            ai.flags |= ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA;
//2537        }
//2538
//2539        if (sa.getBoolean(
//2540                com.android.internal.R.styleable.AndroidManifestApplication_testOnly,
//2541                false)) {
//2542            ai.flags |= ApplicationInfo.FLAG_TEST_ONLY;
//2543        }
//2544
//2545        if (sa.getBoolean(
//2546                com.android.internal.R.styleable.AndroidManifestApplication_largeHeap,
//2547                false)) {
//2548            ai.flags |= ApplicationInfo.FLAG_LARGE_HEAP;
//2549        }
//2550
//2551        if (sa.getBoolean(
//2552                com.android.internal.R.styleable.AndroidManifestApplication_supportsRtl,
//2553                false /* default is no RTL support*/)) {
//2554            ai.flags |= ApplicationInfo.FLAG_SUPPORTS_RTL;
//2555        }
//2556
//2557        if (sa.getBoolean(
//2558                com.android.internal.R.styleable.AndroidManifestApplication_multiArch,
//2559                false)) {
//2560            ai.flags |= ApplicationInfo.FLAG_MULTIARCH;
//2561        }
//2562
//2563        String str;
//2564        str = sa.getNonConfigurationString(
//2565                com.android.internal.R.styleable.AndroidManifestApplication_permission, 0);
//2566        ai.permission = (str != null && str.length() > 0) ? str.intern() : null;
//2567
//2568        if (owner.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.FROYO) {
//2569            str = sa.getNonConfigurationString(
//2570                    com.android.internal.R.styleable.AndroidManifestApplication_taskAffinity,
//2571                    Configuration.NATIVE_CONFIG_VERSION);
//2572        } else {
//2573            // Some older apps have been seen to use a resource reference
//2574            // here that on older builds was ignored (with a warning).  We
//2575            // need to continue to do this for them so they don't break.
//2576            str = sa.getNonResourceString(
//2577                    com.android.internal.R.styleable.AndroidManifestApplication_taskAffinity);
//2578        }
//2579        ai.taskAffinity = buildTaskAffinityName(ai.packageName, ai.packageName,
//2580                str, outError);
//2581
//2582        if (outError[0] == null) {
//2583            CharSequence pname;
//2584            if (owner.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.FROYO) {
//2585                pname = sa.getNonConfigurationString(
//2586                        com.android.internal.R.styleable.AndroidManifestApplication_process,
//2587                        Configuration.NATIVE_CONFIG_VERSION);
//2588            } else {
//2589                // Some older apps have been seen to use a resource reference
//2590                // here that on older builds was ignored (with a warning).  We
//2591                // need to continue to do this for them so they don't break.
//2592                pname = sa.getNonResourceString(
//2593                        com.android.internal.R.styleable.AndroidManifestApplication_process);
//2594            }
//2595            ai.processName = buildProcessName(ai.packageName, null, pname,
//2596                    flags, mSeparateProcesses, outError);
//2597
//2598            ai.enabled = sa.getBoolean(
//2599                    com.android.internal.R.styleable.AndroidManifestApplication_enabled, true);
//2600
//2601            if (sa.getBoolean(
//2602                    com.android.internal.R.styleable.AndroidManifestApplication_isGame, false)) {
//2603                ai.flags |= ApplicationInfo.FLAG_IS_GAME;
//2604            }
//2605
//2606            if (false) {
//2607                if (sa.getBoolean(
//2608                        com.android.internal.R.styleable.AndroidManifestApplication_cantSaveState,
//2609                        false)) {
//2610                    ai.flags |= ApplicationInfo.FLAG_CANT_SAVE_STATE;
//2611
//2612                    // A heavy-weight application can not be in a custom process.
//2613                    // We can do direct compare because we intern all strings.
//2614                    if (ai.processName != null && ai.processName != ai.packageName) {
//2615                        outError[0] = "cantSaveState applications can not use custom processes";
//2616                    }
//2617                }
//2618            }
//2619        }
//2620
//2621        ai.uiOptions = sa.getInt(
//2622                com.android.internal.R.styleable.AndroidManifestApplication_uiOptions, 0);
//2623
//2624        sa.recycle();
//2625
//2626        if (outError[0] != null) {
//2627            mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2628            return false;
//2629        }
//2630
//2631        final int innerDepth = parser.getDepth();
//2632        int type;
//2633        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
//2634                && (type != XmlPullParser.END_TAG || parser.getDepth() > innerDepth)) {
//2635            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
//2636                continue;
//2637            }
//2638
//2639            String tagName = parser.getName();
//2640            if (tagName.equals("activity")) {
//2641                Activity a = parseActivity(owner, res, parser, attrs, flags, outError, false,
//2642                        owner.baseHardwareAccelerated);
//2643                if (a == null) {
//2644                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2645                    return false;
//2646                }
//2647
//2648                owner.activities.add(a);
//2649
//2650            } else if (tagName.equals("receiver")) {
//2651                Activity a = parseActivity(owner, res, parser, attrs, flags, outError, true, false);
//2652                if (a == null) {
//2653                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2654                    return false;
//2655                }
//2656
//2657                owner.receivers.add(a);
//2658
//2659            } else if (tagName.equals("service")) {
//2660                Service s = parseService(owner, res, parser, attrs, flags, outError);
//2661                if (s == null) {
//2662                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2663                    return false;
//2664                }
//2665
//2666                owner.services.add(s);
//2667
//2668            } else if (tagName.equals("provider")) {
//2669                Provider p = parseProvider(owner, res, parser, attrs, flags, outError);
//2670                if (p == null) {
//2671                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2672                    return false;
//2673                }
//2674
//2675                owner.providers.add(p);
//2676
//2677            } else if (tagName.equals("activity-alias")) {
//2678                Activity a = parseActivityAlias(owner, res, parser, attrs, flags, outError);
//2679                if (a == null) {
//2680                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2681                    return false;
//2682                }
//2683
//2684                owner.activities.add(a);
//2685
//2686            } else if (parser.getName().equals("meta-data")) {
//2687                // note: application meta-data is stored off to the side, so it can
//2688                // remain null in the primary copy (we like to avoid extra copies because
//2689                // it can be large)
//2690                if ((owner.mAppMetaData = parseMetaData(res, parser, attrs, owner.mAppMetaData,
//2691                        outError)) == null) {
//2692                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2693                    return false;
//2694                }
//2695
//2696            } else if (tagName.equals("library")) {
//2697                sa = res.obtainAttributes(attrs,
//2698                        com.android.internal.R.styleable.AndroidManifestLibrary);
//2699
//2700                // Note: don't allow this value to be a reference to a resource
//2701                // that may change.
//2702                String lname = sa.getNonResourceString(
//2703                        com.android.internal.R.styleable.AndroidManifestLibrary_name);
//2704
//2705                sa.recycle();
//2706
//2707                if (lname != null) {
//2708                    lname = lname.intern();
//2709                    if (!ArrayUtils.contains(owner.libraryNames, lname)) {
//2710                        owner.libraryNames = ArrayUtils.add(owner.libraryNames, lname);
//2711                    }
//2712                }
//2713
//2714                XmlUtils.skipCurrentTag(parser);
//2715
//2716            } else if (tagName.equals("uses-library")) {
//2717                sa = res.obtainAttributes(attrs,
//2718                        com.android.internal.R.styleable.AndroidManifestUsesLibrary);
//2719
//2720                // Note: don't allow this value to be a reference to a resource
//2721                // that may change.
//2722                String lname = sa.getNonResourceString(
//2723                        com.android.internal.R.styleable.AndroidManifestUsesLibrary_name);
//2724                boolean req = sa.getBoolean(
//2725                        com.android.internal.R.styleable.AndroidManifestUsesLibrary_required,
//2726                        true);
//2727
//2728                sa.recycle();
//2729
//2730                if (lname != null) {
//2731                    lname = lname.intern();
//2732                    if (req) {
//2733                        owner.usesLibraries = ArrayUtils.add(owner.usesLibraries, lname);
//2734                    } else {
//2735                        owner.usesOptionalLibraries = ArrayUtils.add(
//2736                                owner.usesOptionalLibraries, lname);
//2737                    }
//2738                }
//2739
//2740                XmlUtils.skipCurrentTag(parser);
//2741
//2742            } else if (tagName.equals("uses-package")) {
//2743                // Dependencies for app installers; we don't currently try to
//2744                // enforce this.
//2745                XmlUtils.skipCurrentTag(parser);
//2746
//2747            } else {
//2748                if (!RIGID_PARSER) {
//2749                    Slog.w(TAG, "Unknown element under <application>: " + tagName
//2750                            + " at " + mArchiveSourcePath + " "
//2751                            + parser.getPositionDescription());
//2752                    XmlUtils.skipCurrentTag(parser);
//2753                    continue;
//2754                } else {
//2755                    outError[0] = "Bad element under <application>: " + tagName;
//2756                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2757                    return false;
//2758                }
//2759            }
//2760        }
//2761
//2762        return true;
//2763    }
//2764
//2765    /**
//2766     * Parse the {@code application} XML tree at the current parse location in a
//2767     * <em>split APK</em> manifest.
//2768     * <p>
//2769     * Note that split APKs have many more restrictions on what they're capable
//2770     * of doing, so many valid features of a base APK have been carefully
//2771     * omitted here.
//2772     */
//2773    private boolean parseSplitApplication(Package owner, Resources res, XmlPullParser parser,
//2774            AttributeSet attrs, int flags, int splitIndex, String[] outError)
//2775            throws XmlPullParserException, IOException {
//2776        TypedArray sa = res.obtainAttributes(attrs,
//2777                com.android.internal.R.styleable.AndroidManifestApplication);
//2778
//2779        if (sa.getBoolean(
//2780                com.android.internal.R.styleable.AndroidManifestApplication_hasCode, true)) {
//2781            owner.splitFlags[splitIndex] |= ApplicationInfo.FLAG_HAS_CODE;
//2782        }
//2783
//2784        final int innerDepth = parser.getDepth();
//2785        int type;
//2786        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
//2787                && (type != XmlPullParser.END_TAG || parser.getDepth() > innerDepth)) {
//2788            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
//2789                continue;
//2790            }
//2791
//2792            String tagName = parser.getName();
//2793            if (tagName.equals("activity")) {
//2794                Activity a = parseActivity(owner, res, parser, attrs, flags, outError, false,
//2795                        owner.baseHardwareAccelerated);
//2796                if (a == null) {
//2797                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2798                    return false;
//2799                }
//2800
//2801                owner.activities.add(a);
//2802
//2803            } else if (tagName.equals("receiver")) {
//2804                Activity a = parseActivity(owner, res, parser, attrs, flags, outError, true, false);
//2805                if (a == null) {
//2806                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2807                    return false;
//2808                }
//2809
//2810                owner.receivers.add(a);
//2811
//2812            } else if (tagName.equals("service")) {
//2813                Service s = parseService(owner, res, parser, attrs, flags, outError);
//2814                if (s == null) {
//2815                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2816                    return false;
//2817                }
//2818
//2819                owner.services.add(s);
//2820
//2821            } else if (tagName.equals("provider")) {
//2822                Provider p = parseProvider(owner, res, parser, attrs, flags, outError);
//2823                if (p == null) {
//2824                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2825                    return false;
//2826                }
//2827
//2828                owner.providers.add(p);
//2829
//2830            } else if (tagName.equals("activity-alias")) {
//2831                Activity a = parseActivityAlias(owner, res, parser, attrs, flags, outError);
//2832                if (a == null) {
//2833                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2834                    return false;
//2835                }
//2836
//2837                owner.activities.add(a);
//2838
//2839            } else if (parser.getName().equals("meta-data")) {
//2840                // note: application meta-data is stored off to the side, so it can
//2841                // remain null in the primary copy (we like to avoid extra copies because
//2842                // it can be large)
//2843                if ((owner.mAppMetaData = parseMetaData(res, parser, attrs, owner.mAppMetaData,
//2844                        outError)) == null) {
//2845                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2846                    return false;
//2847                }
//2848
//2849            } else if (tagName.equals("uses-library")) {
//2850                sa = res.obtainAttributes(attrs,
//2851                        com.android.internal.R.styleable.AndroidManifestUsesLibrary);
//2852
//2853                // Note: don't allow this value to be a reference to a resource
//2854                // that may change.
//2855                String lname = sa.getNonResourceString(
//2856                        com.android.internal.R.styleable.AndroidManifestUsesLibrary_name);
//2857                boolean req = sa.getBoolean(
//2858                        com.android.internal.R.styleable.AndroidManifestUsesLibrary_required,
//2859                        true);
//2860
//2861                sa.recycle();
//2862
//2863                if (lname != null) {
//2864                    lname = lname.intern();
//2865                    if (req) {
//2866                        // Upgrade to treat as stronger constraint
//2867                        owner.usesLibraries = ArrayUtils.add(owner.usesLibraries, lname);
//2868                        owner.usesOptionalLibraries = ArrayUtils.remove(
//2869                                owner.usesOptionalLibraries, lname);
//2870                    } else {
//2871                        // Ignore if someone already defined as required
//2872                        if (!ArrayUtils.contains(owner.usesLibraries, lname)) {
//2873                            owner.usesOptionalLibraries = ArrayUtils.add(
//2874                                    owner.usesOptionalLibraries, lname);
//2875                        }
//2876                    }
//2877                }
//2878
//2879                XmlUtils.skipCurrentTag(parser);
//2880
//2881            } else if (tagName.equals("uses-package")) {
//2882                // Dependencies for app installers; we don't currently try to
//2883                // enforce this.
//2884                XmlUtils.skipCurrentTag(parser);
//2885
//2886            } else {
//2887                if (!RIGID_PARSER) {
//2888                    Slog.w(TAG, "Unknown element under <application>: " + tagName
//2889                            + " at " + mArchiveSourcePath + " "
//2890                            + parser.getPositionDescription());
//2891                    XmlUtils.skipCurrentTag(parser);
//2892                    continue;
//2893                } else {
//2894                    outError[0] = "Bad element under <application>: " + tagName;
//2895                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
//2896                    return false;
//2897                }
//2898            }
//2899        }
//2900
//2901        return true;
//2902    }
//2903
//2904    private boolean parsePackageItemInfo(Package owner, PackageItemInfo outInfo,
//2905            String[] outError, String tag, TypedArray sa,
//2906            int nameRes, int labelRes, int iconRes, int logoRes, int bannerRes) {
//2907        String name = sa.getNonConfigurationString(nameRes, 0);
//2908        if (name == null) {
//2909            outError[0] = tag + " does not specify android:name";
//2910            return false;
//2911        }
//2912
//2913        outInfo.name
//2914            = buildClassName(owner.applicationInfo.packageName, name, outError);
//2915        if (outInfo.name == null) {
//2916            return false;
//2917        }
//2918
//2919        int iconVal = sa.getResourceId(iconRes, 0);
//2920        if (iconVal != 0) {
//2921            outInfo.icon = iconVal;
//2922            outInfo.nonLocalizedLabel = null;
//2923        }
//2924
//2925        int logoVal = sa.getResourceId(logoRes, 0);
//2926        if (logoVal != 0) {
//2927            outInfo.logo = logoVal;
//2928        }
//2929
//2930        int bannerVal = sa.getResourceId(bannerRes, 0);
//2931        if (bannerVal != 0) {
//2932            outInfo.banner = bannerVal;
//2933        }
//2934
//2935        TypedValue v = sa.peekValue(labelRes);
//2936        if (v != null && (outInfo.labelRes=v.resourceId) == 0) {
//2937            outInfo.nonLocalizedLabel = v.coerceToString();
//2938        }
//2939
//2940        outInfo.packageName = owner.packageName;
//2941
//2942        return true;
//2943    }
//2944
//2945    private Activity parseActivity(Package owner, Resources res,
//2946            XmlPullParser parser, AttributeSet attrs, int flags, String[] outError,
//2947            boolean receiver, boolean hardwareAccelerated)
//2948            throws XmlPullParserException, IOException {
//2949        TypedArray sa = res.obtainAttributes(attrs,
//2950                com.android.internal.R.styleable.AndroidManifestActivity);
//2951
//2952        if (mParseActivityArgs == null) {
//2953            mParseActivityArgs = new ParseComponentArgs(owner, outError,
//2954                    com.android.internal.R.styleable.AndroidManifestActivity_name,
//2955                    com.android.internal.R.styleable.AndroidManifestActivity_label,
//2956                    com.android.internal.R.styleable.AndroidManifestActivity_icon,
//2957                    com.android.internal.R.styleable.AndroidManifestActivity_logo,
//2958                    com.android.internal.R.styleable.AndroidManifestActivity_banner,
//2959                    mSeparateProcesses,
//2960                    com.android.internal.R.styleable.AndroidManifestActivity_process,
//2961                    com.android.internal.R.styleable.AndroidManifestActivity_description,
//2962                    com.android.internal.R.styleable.AndroidManifestActivity_enabled);
//2963        }
//2964
//2965        mParseActivityArgs.tag = receiver ? "<receiver>" : "<activity>";
//2966        mParseActivityArgs.sa = sa;
//2967        mParseActivityArgs.flags = flags;
//2968
//2969        Activity a = new Activity(mParseActivityArgs, new ActivityInfo());
//2970        if (outError[0] != null) {
//2971            sa.recycle();
//2972            return null;
//2973        }
//2974
//2975        boolean setExported = sa.hasValue(
//2976                com.android.internal.R.styleable.AndroidManifestActivity_exported);
//2977        if (setExported) {
//2978            a.info.exported = sa.getBoolean(
//2979                    com.android.internal.R.styleable.AndroidManifestActivity_exported, false);
//2980        }
//2981
//2982        a.info.theme = sa.getResourceId(
//2983                com.android.internal.R.styleable.AndroidManifestActivity_theme, 0);
//2984
//2985        a.info.uiOptions = sa.getInt(
//2986                com.android.internal.R.styleable.AndroidManifestActivity_uiOptions,
//2987                a.info.applicationInfo.uiOptions);
//2988
//2989        String parentName = sa.getNonConfigurationString(
//2990                com.android.internal.R.styleable.AndroidManifestActivity_parentActivityName,
//2991                Configuration.NATIVE_CONFIG_VERSION);
//2992        if (parentName != null) {
//2993            String parentClassName = buildClassName(a.info.packageName, parentName, outError);
//2994            if (outError[0] == null) {
//2995                a.info.parentActivityName = parentClassName;
//2996            } else {
//2997                Log.e(TAG, "Activity " + a.info.name + " specified invalid parentActivityName " +
//2998                        parentName);
//2999                outError[0] = null;
//3000            }
//3001        }
//3002
//3003        String str;
//3004        str = sa.getNonConfigurationString(
//3005                com.android.internal.R.styleable.AndroidManifestActivity_permission, 0);
//3006        if (str == null) {
//3007            a.info.permission = owner.applicationInfo.permission;
//3008        } else {
//3009            a.info.permission = str.length() > 0 ? str.toString().intern() : null;
//3010        }
//3011
//3012        str = sa.getNonConfigurationString(
//3013                com.android.internal.R.styleable.AndroidManifestActivity_taskAffinity,
//3014                Configuration.NATIVE_CONFIG_VERSION);
//3015        a.info.taskAffinity = buildTaskAffinityName(owner.applicationInfo.packageName,
//3016                owner.applicationInfo.taskAffinity, str, outError);
//3017
//3018        a.info.flags = 0;
//3019        if (sa.getBoolean(
//3020                com.android.internal.R.styleable.AndroidManifestActivity_multiprocess,
//3021                false)) {
//3022            a.info.flags |= ActivityInfo.FLAG_MULTIPROCESS;
//3023        }
//3024
//3025        if (sa.getBoolean(
//3026                com.android.internal.R.styleable.AndroidManifestActivity_finishOnTaskLaunch,
//3027                false)) {
//3028            a.info.flags |= ActivityInfo.FLAG_FINISH_ON_TASK_LAUNCH;
//3029        }
//3030
//3031        if (sa.getBoolean(
//3032                com.android.internal.R.styleable.AndroidManifestActivity_clearTaskOnLaunch,
//3033                false)) {
//3034            a.info.flags |= ActivityInfo.FLAG_CLEAR_TASK_ON_LAUNCH;
//3035        }
//3036
//3037        if (sa.getBoolean(
//3038                com.android.internal.R.styleable.AndroidManifestActivity_noHistory,
//3039                false)) {
//3040            a.info.flags |= ActivityInfo.FLAG_NO_HISTORY;
//3041        }
//3042
//3043        if (sa.getBoolean(
//3044                com.android.internal.R.styleable.AndroidManifestActivity_alwaysRetainTaskState,
//3045                false)) {
//3046            a.info.flags |= ActivityInfo.FLAG_ALWAYS_RETAIN_TASK_STATE;
//3047        }
//3048
//3049        if (sa.getBoolean(
//3050                com.android.internal.R.styleable.AndroidManifestActivity_stateNotNeeded,
//3051                false)) {
//3052            a.info.flags |= ActivityInfo.FLAG_STATE_NOT_NEEDED;
//3053        }
//3054
//3055        if (sa.getBoolean(
//3056                com.android.internal.R.styleable.AndroidManifestActivity_excludeFromRecents,
//3057                false)) {
//3058            a.info.flags |= ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS;
//3059        }
//3060
//3061        if (sa.getBoolean(
//3062                com.android.internal.R.styleable.AndroidManifestActivity_allowTaskReparenting,
//3063                (owner.applicationInfo.flags&ApplicationInfo.FLAG_ALLOW_TASK_REPARENTING) != 0)) {
//3064            a.info.flags |= ActivityInfo.FLAG_ALLOW_TASK_REPARENTING;
//3065        }
//3066
//3067        if (sa.getBoolean(
//3068                com.android.internal.R.styleable.AndroidManifestActivity_finishOnCloseSystemDialogs,
//3069                false)) {
//3070            a.info.flags |= ActivityInfo.FLAG_FINISH_ON_CLOSE_SYSTEM_DIALOGS;
//3071        }
//3072
//3073        if (sa.getBoolean(
//3074                com.android.internal.R.styleable.AndroidManifestActivity_showOnLockScreen,
//3075                false)) {
//3076            a.info.flags |= ActivityInfo.FLAG_SHOW_ON_LOCK_SCREEN;
//3077        }
//3078
//3079        if (sa.getBoolean(
//3080                com.android.internal.R.styleable.AndroidManifestActivity_immersive,
//3081                false)) {
//3082            a.info.flags |= ActivityInfo.FLAG_IMMERSIVE;
//3083        }
//3084
//3085        if (!receiver) {
//3086            if (sa.getBoolean(
//3087                    com.android.internal.R.styleable.AndroidManifestActivity_hardwareAccelerated,
//3088                    hardwareAccelerated)) {
//3089                a.info.flags |= ActivityInfo.FLAG_HARDWARE_ACCELERATED;
//3090            }
//3091
//3092            a.info.launchMode = sa.getInt(
//3093                    com.android.internal.R.styleable.AndroidManifestActivity_launchMode,
//3094                    ActivityInfo.LAUNCH_MULTIPLE);
//3095            a.info.documentLaunchMode = sa.getInt(
//3096                    com.android.internal.R.styleable.AndroidManifestActivity_documentLaunchMode,
//3097                    ActivityInfo.DOCUMENT_LAUNCH_NONE);
//3098            a.info.maxRecents = sa.getInt(
//3099                    com.android.internal.R.styleable.AndroidManifestActivity_maxRecents,
//3100                    ActivityManager.getDefaultAppRecentsLimitStatic());
//3101            a.info.screenOrientation = sa.getInt(
//3102                    com.android.internal.R.styleable.AndroidManifestActivity_screenOrientation,
//3103                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
//3104            a.info.configChanges = sa.getInt(
//3105                    com.android.internal.R.styleable.AndroidManifestActivity_configChanges,
//3106                    0);
//3107            a.info.softInputMode = sa.getInt(
//3108                    com.android.internal.R.styleable.AndroidManifestActivity_windowSoftInputMode,
//3109                    0);
//3110
//3111            a.info.persistableMode = sa.getInteger(
//3112                    com.android.internal.R.styleable.AndroidManifestActivity_persistableMode,
//3113                    ActivityInfo.PERSIST_ROOT_ONLY);
//3114
//3115            if (sa.getBoolean(
//3116                    com.android.internal.R.styleable.AndroidManifestActivity_allowEmbedded,
//3117                    false)) {
//3118                a.info.flags |= ActivityInfo.FLAG_ALLOW_EMBEDDED;
//3119            }
//3120
//3121            if (sa.getBoolean(
//3122                    com.android.internal.R.styleable.AndroidManifestActivity_autoRemoveFromRecents,
//3123                    false)) {
//3124                a.info.flags |= ActivityInfo.FLAG_AUTO_REMOVE_FROM_RECENTS;
//3125            }
//3126
//3127            if (sa.getBoolean(
//3128                    com.android.internal.R.styleable.AndroidManifestActivity_relinquishTaskIdentity,
//3129                    false)) {
//3130                a.info.flags |= ActivityInfo.FLAG_RELINQUISH_TASK_IDENTITY;
//3131            }
//3132
//3133            if (sa.getBoolean(
//3134                    com.android.internal.R.styleable.AndroidManifestActivity_resumeWhilePausing,
//3135                    false)) {
//3136                a.info.flags |= ActivityInfo.FLAG_RESUME_WHILE_PAUSING;
//3137            }
//3138        } else {
//3139            a.info.launchMode = ActivityInfo.LAUNCH_MULTIPLE;
//3140            a.info.configChanges = 0;
//3141        }
//3142
//3143        if (receiver) {
//3144            if (sa.getBoolean(
//3145                    com.android.internal.R.styleable.AndroidManifestActivity_singleUser,
//3146                    false)) {
//3147                a.info.flags |= ActivityInfo.FLAG_SINGLE_USER;
//3148                if (a.info.exported && (flags & PARSE_IS_PRIVILEGED) == 0) {
//3149                    Slog.w(TAG, "Activity exported request ignored due to singleUser: "
//3150                            + a.className + " at " + mArchiveSourcePath + " "
//3151                            + parser.getPositionDescription());
//3152                    a.info.exported = false;
//3153                    setExported = true;
//3154                }
//3155            }
//3156            if (sa.getBoolean(
//3157                    com.android.internal.R.styleable.AndroidManifestActivity_primaryUserOnly,
//3158                    false)) {
//3159                a.info.flags |= ActivityInfo.FLAG_PRIMARY_USER_ONLY;
//3160            }
//3161        }
//3162
//3163        sa.recycle();
//3164
//3165        if (receiver && (owner.applicationInfo.flags&ApplicationInfo.FLAG_CANT_SAVE_STATE) != 0) {
//3166            // A heavy-weight application can not have receives in its main process
//3167            // We can do direct compare because we intern all strings.
//3168            if (a.info.processName == owner.packageName) {
//3169                outError[0] = "Heavy-weight applications can not have receivers in main process";
//3170            }
//3171        }
//3172
//3173        if (outError[0] != null) {
//3174            return null;
//3175        }
//3176
//3177        int outerDepth = parser.getDepth();
//3178        int type;
//3179        while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
//3180               && (type != XmlPullParser.END_TAG
//3181                       || parser.getDepth() > outerDepth)) {
//3182            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
//3183                continue;
//3184            }
//3185
//3186            if (parser.getName().equals("intent-filter")) {
//3187                ActivityIntentInfo intent = new ActivityIntentInfo(a);
//3188                if (!parseIntent(res, parser, attrs, true, intent, outError)) {
//3189                    return null;
//3190                }
//3191                if (intent.countActions() == 0) {
//3192                    Slog.w(TAG, "No actions in intent filter at "
//3193                            + mArchiveSourcePath + " "
//3194                            + parser.getPositionDescription());
//3195                } else {
//3196                    a.intents.add(intent);
//3197                }
//3198            } else if (!receiver && parser.getName().equals("preferred")) {
//3199                ActivityIntentInfo intent = new ActivityIntentInfo(a);
//3200                if (!parseIntent(res, parser, attrs, false, intent, outError)) {
//3201                    return null;
//3202                }
//3203                if (intent.countActions() == 0) {
//3204                    Slog.w(TAG, "No actions in preferred at "
//3205                            + mArchiveSourcePath + " "
//3206                            + parser.getPositionDescription());
//3207                } else {
//3208                    if (owner.preferredActivityFilters == null) {
//3209                        owner.preferredActivityFilters = new ArrayList<ActivityIntentInfo>();
//3210                    }
//3211                    owner.preferredActivityFilters.add(intent);
//3212                }
//3213            } else if (parser.getName().equals("meta-data")) {
//3214                if ((a.metaData=parseMetaData(res, parser, attrs, a.metaData,
//3215                        outError)) == null) {
//3216                    return null;
//3217                }
//3218            } else {
//3219                if (!RIGID_PARSER) {
//3220                    Slog.w(TAG, "Problem in package " + mArchiveSourcePath + ":");
//3221                    if (receiver) {
//3222                        Slog.w(TAG, "Unknown element under <receiver>: " + parser.getName()
//3223                                + " at " + mArchiveSourcePath + " "
//3224                                + parser.getPositionDescription());
//3225                    } else {
//3226                        Slog.w(TAG, "Unknown element under <activity>: " + parser.getName()
//3227                                + " at " + mArchiveSourcePath + " "
//3228                                + parser.getPositionDescription());
//3229                    }
//3230                    XmlUtils.skipCurrentTag(parser);
//3231                    continue;
//3232                } else {
//3233                    if (receiver) {
//3234                        outError[0] = "Bad element under <receiver>: " + parser.getName();
//3235                    } else {
//3236                        outError[0] = "Bad element under <activity>: " + parser.getName();
//3237                    }
//3238                    return null;
//3239                }
//3240            }
//3241        }
//3242
//3243        if (!setExported) {
//3244            a.info.exported = a.intents.size() > 0;
//3245        }
//3246
//3247        return a;
//3248    }
//3249
//3250    private Activity parseActivityAlias(Package owner, Resources res,
//3251            XmlPullParser parser, AttributeSet attrs, int flags, String[] outError)
//3252            throws XmlPullParserException, IOException {
//3253        TypedArray sa = res.obtainAttributes(attrs,
//3254                com.android.internal.R.styleable.AndroidManifestActivityAlias);
//3255
//3256        String targetActivity = sa.getNonConfigurationString(
//3257                com.android.internal.R.styleable.AndroidManifestActivityAlias_targetActivity,
//3258                Configuration.NATIVE_CONFIG_VERSION);
//3259        if (targetActivity == null) {
//3260            outError[0] = "<activity-alias> does not specify android:targetActivity";
//3261            sa.recycle();
//3262            return null;
//3263        }
//3264
//3265        targetActivity = buildClassName(owner.applicationInfo.packageName,
//3266                targetActivity, outError);
//3267        if (targetActivity == null) {
//3268            sa.recycle();
//3269            return null;
//3270        }
//3271
//3272        if (mParseActivityAliasArgs == null) {
//3273            mParseActivityAliasArgs = new ParseComponentArgs(owner, outError,
//3274                    com.android.internal.R.styleable.AndroidManifestActivityAlias_name,
//3275                    com.android.internal.R.styleable.AndroidManifestActivityAlias_label,
//3276                    com.android.internal.R.styleable.AndroidManifestActivityAlias_icon,
//3277                    com.android.internal.R.styleable.AndroidManifestActivityAlias_logo,
//3278                    com.android.internal.R.styleable.AndroidManifestActivityAlias_banner,
//3279                    mSeparateProcesses,
//3280                    0,
//3281                    com.android.internal.R.styleable.AndroidManifestActivityAlias_description,
//3282                    com.android.internal.R.styleable.AndroidManifestActivityAlias_enabled);
//3283            mParseActivityAliasArgs.tag = "<activity-alias>";
//3284        }
//3285
//3286        mParseActivityAliasArgs.sa = sa;
//3287        mParseActivityAliasArgs.flags = flags;
//3288
//3289        Activity target = null;
//3290
//3291        final int NA = owner.activities.size();
//3292        for (int i=0; i<NA; i++) {
//3293            Activity t = owner.activities.get(i);
//3294            if (targetActivity.equals(t.info.name)) {
//3295                target = t;
//3296                break;
//3297            }
//3298        }
//3299
//3300        if (target == null) {
//3301            outError[0] = "<activity-alias> target activity " + targetActivity
//3302                    + " not found in manifest";
//3303            sa.recycle();
//3304            return null;
//3305        }
//3306
//3307        ActivityInfo info = new ActivityInfo();
//3308        info.targetActivity = targetActivity;
//3309        info.configChanges = target.info.configChanges;
//3310        info.flags = target.info.flags;
//3311        info.icon = target.info.icon;
//3312        info.logo = target.info.logo;
//3313        info.banner = target.info.banner;
//3314        info.labelRes = target.info.labelRes;
//3315        info.nonLocalizedLabel = target.info.nonLocalizedLabel;
//3316        info.launchMode = target.info.launchMode;
//3317        info.processName = target.info.processName;
//3318        if (info.descriptionRes == 0) {
//3319            info.descriptionRes = target.info.descriptionRes;
//3320        }
//3321        info.screenOrientation = target.info.screenOrientation;
//3322        info.taskAffinity = target.info.taskAffinity;
//3323        info.theme = target.info.theme;
//3324        info.softInputMode = target.info.softInputMode;
//3325        info.uiOptions = target.info.uiOptions;
//3326        info.parentActivityName = target.info.parentActivityName;
//3327        info.maxRecents = target.info.maxRecents;
//3328
//3329        Activity a = new Activity(mParseActivityAliasArgs, info);
//3330        if (outError[0] != null) {
//3331            sa.recycle();
//3332            return null;
//3333        }
//3334
//3335        final boolean setExported = sa.hasValue(
//3336                com.android.internal.R.styleable.AndroidManifestActivityAlias_exported);
//3337        if (setExported) {
//3338            a.info.exported = sa.getBoolean(
//3339                    com.android.internal.R.styleable.AndroidManifestActivityAlias_exported, false);
//3340        }
//3341
//3342        String str;
//3343        str = sa.getNonConfigurationString(
//3344                com.android.internal.R.styleable.AndroidManifestActivityAlias_permission, 0);
//3345        if (str != null) {
//3346            a.info.permission = str.length() > 0 ? str.toString().intern() : null;
//3347        }
//3348
//3349        String parentName = sa.getNonConfigurationString(
//3350                com.android.internal.R.styleable.AndroidManifestActivityAlias_parentActivityName,
//3351                Configuration.NATIVE_CONFIG_VERSION);
//3352        if (parentName != null) {
//3353            String parentClassName = buildClassName(a.info.packageName, parentName, outError);
//3354            if (outError[0] == null) {
//3355                a.info.parentActivityName = parentClassName;
//3356            } else {
//3357                Log.e(TAG, "Activity alias " + a.info.name +
//3358                        " specified invalid parentActivityName " + parentName);
//3359                outError[0] = null;
//3360            }
//3361        }
//3362
//3363        sa.recycle();
//3364
//3365        if (outError[0] != null) {
//3366            return null;
//3367        }
//3368
//3369        int outerDepth = parser.getDepth();
//3370        int type;
//3371        while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
//3372               && (type != XmlPullParser.END_TAG
//3373                       || parser.getDepth() > outerDepth)) {
//3374            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
//3375                continue;
//3376            }
//3377
//3378            if (parser.getName().equals("intent-filter")) {
//3379                ActivityIntentInfo intent = new ActivityIntentInfo(a);
//3380                if (!parseIntent(res, parser, attrs, true, intent, outError)) {
//3381                    return null;
//3382                }
//3383                if (intent.countActions() == 0) {
//3384                    Slog.w(TAG, "No actions in intent filter at "
//3385                            + mArchiveSourcePath + " "
//3386                            + parser.getPositionDescription());
//3387                } else {
//3388                    a.intents.add(intent);
//3389                }
//3390            } else if (parser.getName().equals("meta-data")) {
//3391                if ((a.metaData=parseMetaData(res, parser, attrs, a.metaData,
//3392                        outError)) == null) {
//3393                    return null;
//3394                }
//3395            } else {
//3396                if (!RIGID_PARSER) {
//3397                    Slog.w(TAG, "Unknown element under <activity-alias>: " + parser.getName()
//3398                            + " at " + mArchiveSourcePath + " "
//3399                            + parser.getPositionDescription());
//3400                    XmlUtils.skipCurrentTag(parser);
//3401                    continue;
//3402                } else {
//3403                    outError[0] = "Bad element under <activity-alias>: " + parser.getName();
//3404                    return null;
//3405                }
//3406            }
//3407        }
//3408
//3409        if (!setExported) {
//3410            a.info.exported = a.intents.size() > 0;
//3411        }
//3412
//3413        return a;
//3414    }
//3415
//3416    private Provider parseProvider(Package owner, Resources res,
//3417            XmlPullParser parser, AttributeSet attrs, int flags, String[] outError)
//3418            throws XmlPullParserException, IOException {
//3419        TypedArray sa = res.obtainAttributes(attrs,
//3420                com.android.internal.R.styleable.AndroidManifestProvider);
//3421
//3422        if (mParseProviderArgs == null) {
//3423            mParseProviderArgs = new ParseComponentArgs(owner, outError,
//3424                    com.android.internal.R.styleable.AndroidManifestProvider_name,
//3425                    com.android.internal.R.styleable.AndroidManifestProvider_label,
//3426                    com.android.internal.R.styleable.AndroidManifestProvider_icon,
//3427                    com.android.internal.R.styleable.AndroidManifestProvider_logo,
//3428                    com.android.internal.R.styleable.AndroidManifestProvider_banner,
//3429                    mSeparateProcesses,
//3430                    com.android.internal.R.styleable.AndroidManifestProvider_process,
//3431                    com.android.internal.R.styleable.AndroidManifestProvider_description,
//3432                    com.android.internal.R.styleable.AndroidManifestProvider_enabled);
//3433            mParseProviderArgs.tag = "<provider>";
//3434        }
//3435
//3436        mParseProviderArgs.sa = sa;
//3437        mParseProviderArgs.flags = flags;
//3438
//3439        Provider p = new Provider(mParseProviderArgs, new ProviderInfo());
//3440        if (outError[0] != null) {
//3441            sa.recycle();
//3442            return null;
//3443        }
//3444
//3445        boolean providerExportedDefault = false;
//3446
//3447        if (owner.applicationInfo.targetSdkVersion < Build.VERSION_CODES.JELLY_BEAN_MR1) {
//3448            // For compatibility, applications targeting API level 16 or lower
//3449            // should have their content providers exported by default, unless they
//3450            // specify otherwise.
//3451            providerExportedDefault = true;
//3452        }
//3453
//3454        p.info.exported = sa.getBoolean(
//3455                com.android.internal.R.styleable.AndroidManifestProvider_exported,
//3456                providerExportedDefault);
//3457
//3458        String cpname = sa.getNonConfigurationString(
//3459                com.android.internal.R.styleable.AndroidManifestProvider_authorities, 0);
//3460
//3461        p.info.isSyncable = sa.getBoolean(
//3462                com.android.internal.R.styleable.AndroidManifestProvider_syncable,
//3463                false);
//3464
//3465        String permission = sa.getNonConfigurationString(
//3466                com.android.internal.R.styleable.AndroidManifestProvider_permission, 0);
//3467        String str = sa.getNonConfigurationString(
//3468                com.android.internal.R.styleable.AndroidManifestProvider_readPermission, 0);
//3469        if (str == null) {
//3470            str = permission;
//3471        }
//3472        if (str == null) {
//3473            p.info.readPermission = owner.applicationInfo.permission;
//3474        } else {
//3475            p.info.readPermission =
//3476                str.length() > 0 ? str.toString().intern() : null;
//3477        }
//3478        str = sa.getNonConfigurationString(
//3479                com.android.internal.R.styleable.AndroidManifestProvider_writePermission, 0);
//3480        if (str == null) {
//3481            str = permission;
//3482        }
//3483        if (str == null) {
//3484            p.info.writePermission = owner.applicationInfo.permission;
//3485        } else {
//3486            p.info.writePermission =
//3487                str.length() > 0 ? str.toString().intern() : null;
//3488        }
//3489
//3490        p.info.grantUriPermissions = sa.getBoolean(
//3491                com.android.internal.R.styleable.AndroidManifestProvider_grantUriPermissions,
//3492                false);
//3493
//3494        p.info.multiprocess = sa.getBoolean(
//3495                com.android.internal.R.styleable.AndroidManifestProvider_multiprocess,
//3496                false);
//3497
//3498        p.info.initOrder = sa.getInt(
//3499                com.android.internal.R.styleable.AndroidManifestProvider_initOrder,
//3500                0);
//3501
//3502        p.info.flags = 0;
//3503
//3504        if (sa.getBoolean(
//3505                com.android.internal.R.styleable.AndroidManifestProvider_singleUser,
//3506                false)) {
//3507            p.info.flags |= ProviderInfo.FLAG_SINGLE_USER;
//3508            if (p.info.exported && (flags & PARSE_IS_PRIVILEGED) == 0) {
//3509                Slog.w(TAG, "Provider exported request ignored due to singleUser: "
//3510                        + p.className + " at " + mArchiveSourcePath + " "
//3511                        + parser.getPositionDescription());
//3512                p.info.exported = false;
//3513            }
//3514        }
//3515
//3516        sa.recycle();
//3517
//3518        if ((owner.applicationInfo.flags&ApplicationInfo.FLAG_CANT_SAVE_STATE) != 0) {
//3519            // A heavy-weight application can not have providers in its main process
//3520            // We can do direct compare because we intern all strings.
//3521            if (p.info.processName == owner.packageName) {
//3522                outError[0] = "Heavy-weight applications can not have providers in main process";
//3523                return null;
//3524            }
//3525        }
//3526
//3527        if (cpname == null) {
//3528            outError[0] = "<provider> does not include authorities attribute";
//3529            return null;
//3530        }
//3531        if (cpname.length() <= 0) {
//3532            outError[0] = "<provider> has empty authorities attribute";
//3533            return null;
//3534        }
//3535        p.info.authority = cpname.intern();
//3536
//3537        if (!parseProviderTags(res, parser, attrs, p, outError)) {
//3538            return null;
//3539        }
//3540
//3541        return p;
//3542    }
//3543
//3544    private boolean parseProviderTags(Resources res,
//3545            XmlPullParser parser, AttributeSet attrs,
//3546            Provider outInfo, String[] outError)
//3547            throws XmlPullParserException, IOException {
//3548        int outerDepth = parser.getDepth();
//3549        int type;
//3550        while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
//3551               && (type != XmlPullParser.END_TAG
//3552                       || parser.getDepth() > outerDepth)) {
//3553            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
//3554                continue;
//3555            }
//3556
//3557            if (parser.getName().equals("intent-filter")) {
//3558                ProviderIntentInfo intent = new ProviderIntentInfo(outInfo);
//3559                if (!parseIntent(res, parser, attrs, true, intent, outError)) {
//3560                    return false;
//3561                }
//3562                outInfo.intents.add(intent);
//3563
//3564            } else if (parser.getName().equals("meta-data")) {
//3565                if ((outInfo.metaData=parseMetaData(res, parser, attrs,
//3566                        outInfo.metaData, outError)) == null) {
//3567                    return false;
//3568                }
//3569
//3570            } else if (parser.getName().equals("grant-uri-permission")) {
//3571                TypedArray sa = res.obtainAttributes(attrs,
//3572                        com.android.internal.R.styleable.AndroidManifestGrantUriPermission);
//3573
//3574                PatternMatcher pa = null;
//3575
//3576                String str = sa.getNonConfigurationString(
//3577                        com.android.internal.R.styleable.AndroidManifestGrantUriPermission_path, 0);
//3578                if (str != null) {
//3579                    pa = new PatternMatcher(str, PatternMatcher.PATTERN_LITERAL);
//3580                }
//3581
//3582                str = sa.getNonConfigurationString(
//3583                        com.android.internal.R.styleable.AndroidManifestGrantUriPermission_pathPrefix, 0);
//3584                if (str != null) {
//3585                    pa = new PatternMatcher(str, PatternMatcher.PATTERN_PREFIX);
//3586                }
//3587
//3588                str = sa.getNonConfigurationString(
//3589                        com.android.internal.R.styleable.AndroidManifestGrantUriPermission_pathPattern, 0);
//3590                if (str != null) {
//3591                    pa = new PatternMatcher(str, PatternMatcher.PATTERN_SIMPLE_GLOB);
//3592                }
//3593
//3594                sa.recycle();
//3595
//3596                if (pa != null) {
//3597                    if (outInfo.info.uriPermissionPatterns == null) {
//3598                        outInfo.info.uriPermissionPatterns = new PatternMatcher[1];
//3599                        outInfo.info.uriPermissionPatterns[0] = pa;
//3600                    } else {
//3601                        final int N = outInfo.info.uriPermissionPatterns.length;
//3602                        PatternMatcher[] newp = new PatternMatcher[N+1];
//3603                        System.arraycopy(outInfo.info.uriPermissionPatterns, 0, newp, 0, N);
//3604                        newp[N] = pa;
//3605                        outInfo.info.uriPermissionPatterns = newp;
//3606                    }
//3607                    outInfo.info.grantUriPermissions = true;
//3608                } else {
//3609                    if (!RIGID_PARSER) {
//3610                        Slog.w(TAG, "Unknown element under <path-permission>: "
//3611                                + parser.getName() + " at " + mArchiveSourcePath + " "
//3612                                + parser.getPositionDescription());
//3613                        XmlUtils.skipCurrentTag(parser);
//3614                        continue;
//3615                    } else {
//3616                        outError[0] = "No path, pathPrefix, or pathPattern for <path-permission>";
//3617                        return false;
//3618                    }
//3619                }
//3620                XmlUtils.skipCurrentTag(parser);
//3621
//3622            } else if (parser.getName().equals("path-permission")) {
//3623                TypedArray sa = res.obtainAttributes(attrs,
//3624                        com.android.internal.R.styleable.AndroidManifestPathPermission);
//3625
//3626                PathPermission pa = null;
//3627
//3628                String permission = sa.getNonConfigurationString(
//3629                        com.android.internal.R.styleable.AndroidManifestPathPermission_permission, 0);
//3630                String readPermission = sa.getNonConfigurationString(
//3631                        com.android.internal.R.styleable.AndroidManifestPathPermission_readPermission, 0);
//3632                if (readPermission == null) {
//3633                    readPermission = permission;
//3634                }
//3635                String writePermission = sa.getNonConfigurationString(
//3636                        com.android.internal.R.styleable.AndroidManifestPathPermission_writePermission, 0);
//3637                if (writePermission == null) {
//3638                    writePermission = permission;
//3639                }
//3640
//3641                boolean havePerm = false;
//3642                if (readPermission != null) {
//3643                    readPermission = readPermission.intern();
//3644                    havePerm = true;
//3645                }
//3646                if (writePermission != null) {
//3647                    writePermission = writePermission.intern();
//3648                    havePerm = true;
//3649                }
//3650
//3651                if (!havePerm) {
//3652                    if (!RIGID_PARSER) {
//3653                        Slog.w(TAG, "No readPermission or writePermssion for <path-permission>: "
//3654                                + parser.getName() + " at " + mArchiveSourcePath + " "
//3655                                + parser.getPositionDescription());
//3656                        XmlUtils.skipCurrentTag(parser);
//3657                        continue;
//3658                    } else {
//3659                        outError[0] = "No readPermission or writePermssion for <path-permission>";
//3660                        return false;
//3661                    }
//3662                }
//3663
//3664                String path = sa.getNonConfigurationString(
//3665                        com.android.internal.R.styleable.AndroidManifestPathPermission_path, 0);
//3666                if (path != null) {
//3667                    pa = new PathPermission(path,
//3668                            PatternMatcher.PATTERN_LITERAL, readPermission, writePermission);
//3669                }
//3670
//3671                path = sa.getNonConfigurationString(
//3672                        com.android.internal.R.styleable.AndroidManifestPathPermission_pathPrefix, 0);
//3673                if (path != null) {
//3674                    pa = new PathPermission(path,
//3675                            PatternMatcher.PATTERN_PREFIX, readPermission, writePermission);
//3676                }
//3677
//3678                path = sa.getNonConfigurationString(
//3679                        com.android.internal.R.styleable.AndroidManifestPathPermission_pathPattern, 0);
//3680                if (path != null) {
//3681                    pa = new PathPermission(path,
//3682                            PatternMatcher.PATTERN_SIMPLE_GLOB, readPermission, writePermission);
//3683                }
//3684
//3685                sa.recycle();
//3686
//3687                if (pa != null) {
//3688                    if (outInfo.info.pathPermissions == null) {
//3689                        outInfo.info.pathPermissions = new PathPermission[1];
//3690                        outInfo.info.pathPermissions[0] = pa;
//3691                    } else {
//3692                        final int N = outInfo.info.pathPermissions.length;
//3693                        PathPermission[] newp = new PathPermission[N+1];
//3694                        System.arraycopy(outInfo.info.pathPermissions, 0, newp, 0, N);
//3695                        newp[N] = pa;
//3696                        outInfo.info.pathPermissions = newp;
//3697                    }
//3698                } else {
//3699                    if (!RIGID_PARSER) {
//3700                        Slog.w(TAG, "No path, pathPrefix, or pathPattern for <path-permission>: "
//3701                                + parser.getName() + " at " + mArchiveSourcePath + " "
//3702                                + parser.getPositionDescription());
//3703                        XmlUtils.skipCurrentTag(parser);
//3704                        continue;
//3705                    }
//3706                    outError[0] = "No path, pathPrefix, or pathPattern for <path-permission>";
//3707                    return false;
//3708                }
//3709                XmlUtils.skipCurrentTag(parser);
//3710
//3711            } else {
//3712                if (!RIGID_PARSER) {
//3713                    Slog.w(TAG, "Unknown element under <provider>: "
//3714                            + parser.getName() + " at " + mArchiveSourcePath + " "
//3715                            + parser.getPositionDescription());
//3716                    XmlUtils.skipCurrentTag(parser);
//3717                    continue;
//3718                } else {
//3719                    outError[0] = "Bad element under <provider>: " + parser.getName();
//3720                    return false;
//3721                }
//3722            }
//3723        }
//3724        return true;
//3725    }
//3726
//3727    private Service parseService(Package owner, Resources res,
//3728            XmlPullParser parser, AttributeSet attrs, int flags, String[] outError)
//3729            throws XmlPullParserException, IOException {
//3730        TypedArray sa = res.obtainAttributes(attrs,
//3731                com.android.internal.R.styleable.AndroidManifestService);
//3732
//3733        if (mParseServiceArgs == null) {
//3734            mParseServiceArgs = new ParseComponentArgs(owner, outError,
//3735                    com.android.internal.R.styleable.AndroidManifestService_name,
//3736                    com.android.internal.R.styleable.AndroidManifestService_label,
//3737                    com.android.internal.R.styleable.AndroidManifestService_icon,
//3738                    com.android.internal.R.styleable.AndroidManifestService_logo,
//3739                    com.android.internal.R.styleable.AndroidManifestService_banner,
//3740                    mSeparateProcesses,
//3741                    com.android.internal.R.styleable.AndroidManifestService_process,
//3742                    com.android.internal.R.styleable.AndroidManifestService_description,
//3743                    com.android.internal.R.styleable.AndroidManifestService_enabled);
//3744            mParseServiceArgs.tag = "<service>";
//3745        }
//3746
//3747        mParseServiceArgs.sa = sa;
//3748        mParseServiceArgs.flags = flags;
//3749
//3750        Service s = new Service(mParseServiceArgs, new ServiceInfo());
//3751        if (outError[0] != null) {
//3752            sa.recycle();
//3753            return null;
//3754        }
//3755
//3756        boolean setExported = sa.hasValue(
//3757                com.android.internal.R.styleable.AndroidManifestService_exported);
//3758        if (setExported) {
//3759            s.info.exported = sa.getBoolean(
//3760                    com.android.internal.R.styleable.AndroidManifestService_exported, false);
//3761        }
//3762
//3763        String str = sa.getNonConfigurationString(
//3764                com.android.internal.R.styleable.AndroidManifestService_permission, 0);
//3765        if (str == null) {
//3766            s.info.permission = owner.applicationInfo.permission;
//3767        } else {
//3768            s.info.permission = str.length() > 0 ? str.toString().intern() : null;
//3769        }
//3770
//3771        s.info.flags = 0;
//3772        if (sa.getBoolean(
//3773                com.android.internal.R.styleable.AndroidManifestService_stopWithTask,
//3774                false)) {
//3775            s.info.flags |= ServiceInfo.FLAG_STOP_WITH_TASK;
//3776        }
//3777        if (sa.getBoolean(
//3778                com.android.internal.R.styleable.AndroidManifestService_isolatedProcess,
//3779                false)) {
//3780            s.info.flags |= ServiceInfo.FLAG_ISOLATED_PROCESS;
//3781        }
//3782        if (sa.getBoolean(
//3783                com.android.internal.R.styleable.AndroidManifestService_singleUser,
//3784                false)) {
//3785            s.info.flags |= ServiceInfo.FLAG_SINGLE_USER;
//3786            if (s.info.exported && (flags & PARSE_IS_PRIVILEGED) == 0) {
//3787                Slog.w(TAG, "Service exported request ignored due to singleUser: "
//3788                        + s.className + " at " + mArchiveSourcePath + " "
//3789                        + parser.getPositionDescription());
//3790                s.info.exported = false;
//3791                setExported = true;
//3792            }
//3793        }
//3794
//3795        sa.recycle();
//3796
//3797        if ((owner.applicationInfo.flags&ApplicationInfo.FLAG_CANT_SAVE_STATE) != 0) {
//3798            // A heavy-weight application can not have services in its main process
//3799            // We can do direct compare because we intern all strings.
//3800            if (s.info.processName == owner.packageName) {
//3801                outError[0] = "Heavy-weight applications can not have services in main process";
//3802                return null;
//3803            }
//3804        }
//3805
//3806        int outerDepth = parser.getDepth();
//3807        int type;
//3808        while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
//3809               && (type != XmlPullParser.END_TAG
//3810                       || parser.getDepth() > outerDepth)) {
//3811            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
//3812                continue;
//3813            }
//3814
//3815            if (parser.getName().equals("intent-filter")) {
//3816                ServiceIntentInfo intent = new ServiceIntentInfo(s);
//3817                if (!parseIntent(res, parser, attrs, true, intent, outError)) {
//3818                    return null;
//3819                }
//3820
//3821                s.intents.add(intent);
//3822            } else if (parser.getName().equals("meta-data")) {
//3823                if ((s.metaData=parseMetaData(res, parser, attrs, s.metaData,
//3824                        outError)) == null) {
//3825                    return null;
//3826                }
//3827            } else {
//3828                if (!RIGID_PARSER) {
//3829                    Slog.w(TAG, "Unknown element under <service>: "
//3830                            + parser.getName() + " at " + mArchiveSourcePath + " "
//3831                            + parser.getPositionDescription());
//3832                    XmlUtils.skipCurrentTag(parser);
//3833                    continue;
//3834                } else {
//3835                    outError[0] = "Bad element under <service>: " + parser.getName();
//3836                    return null;
//3837                }
//3838            }
//3839        }
//3840
//3841        if (!setExported) {
//3842            s.info.exported = s.intents.size() > 0;
//3843        }
//3844
//3845        return s;
//3846    }
//3847
//3848    private boolean parseAllMetaData(Resources res,
//3849            XmlPullParser parser, AttributeSet attrs, String tag,
//3850            Component outInfo, String[] outError)
//3851            throws XmlPullParserException, IOException {
//3852        int outerDepth = parser.getDepth();
//3853        int type;
//3854        while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
//3855               && (type != XmlPullParser.END_TAG
//3856                       || parser.getDepth() > outerDepth)) {
//3857            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
//3858                continue;
//3859            }
//3860
//3861            if (parser.getName().equals("meta-data")) {
//3862                if ((outInfo.metaData=parseMetaData(res, parser, attrs,
//3863                        outInfo.metaData, outError)) == null) {
//3864                    return false;
//3865                }
//3866            } else {
//3867                if (!RIGID_PARSER) {
//3868                    Slog.w(TAG, "Unknown element under " + tag + ": "
//3869                            + parser.getName() + " at " + mArchiveSourcePath + " "
//3870                            + parser.getPositionDescription());
//3871                    XmlUtils.skipCurrentTag(parser);
//3872                    continue;
//3873                } else {
//3874                    outError[0] = "Bad element under " + tag + ": " + parser.getName();
//3875                    return false;
//3876                }
//3877            }
//3878        }
//3879        return true;
//3880    }
//3881
//3882    private Bundle parseMetaData(Resources res,
//3883            XmlPullParser parser, AttributeSet attrs,
//3884            Bundle data, String[] outError)
//3885            throws XmlPullParserException, IOException {
//3886
//3887        TypedArray sa = res.obtainAttributes(attrs,
//3888                com.android.internal.R.styleable.AndroidManifestMetaData);
//3889
//3890        if (data == null) {
//3891            data = new Bundle();
//3892        }
//3893
//3894        String name = sa.getNonConfigurationString(
//3895                com.android.internal.R.styleable.AndroidManifestMetaData_name, 0);
//3896        if (name == null) {
//3897            outError[0] = "<meta-data> requires an android:name attribute";
//3898            sa.recycle();
//3899            return null;
//3900        }
//3901
//3902        name = name.intern();
//3903
//3904        TypedValue v = sa.peekValue(
//3905                com.android.internal.R.styleable.AndroidManifestMetaData_resource);
//3906        if (v != null && v.resourceId != 0) {
//3907            //Slog.i(TAG, "Meta data ref " + name + ": " + v);
//3908            data.putInt(name, v.resourceId);
//3909        } else {
//3910            v = sa.peekValue(
//3911                    com.android.internal.R.styleable.AndroidManifestMetaData_value);
//3912            //Slog.i(TAG, "Meta data " + name + ": " + v);
//3913            if (v != null) {
//3914                if (v.type == TypedValue.TYPE_STRING) {
//3915                    CharSequence cs = v.coerceToString();
//3916                    data.putString(name, cs != null ? cs.toString().intern() : null);
//3917                } else if (v.type == TypedValue.TYPE_INT_BOOLEAN) {
//3918                    data.putBoolean(name, v.data != 0);
//3919                } else if (v.type >= TypedValue.TYPE_FIRST_INT
//3920                        && v.type <= TypedValue.TYPE_LAST_INT) {
//3921                    data.putInt(name, v.data);
//3922                } else if (v.type == TypedValue.TYPE_FLOAT) {
//3923                    data.putFloat(name, v.getFloat());
//3924                } else {
//3925                    if (!RIGID_PARSER) {
//3926                        Slog.w(TAG, "<meta-data> only supports string, integer, float, color, boolean, and resource reference types: "
//3927                                + parser.getName() + " at " + mArchiveSourcePath + " "
//3928                                + parser.getPositionDescription());
//3929                    } else {
//3930                        outError[0] = "<meta-data> only supports string, integer, float, color, boolean, and resource reference types";
//3931                        data = null;
//3932                    }
//3933                }
//3934            } else {
//3935                outError[0] = "<meta-data> requires an android:value or android:resource attribute";
//3936                data = null;
//3937            }
//3938        }
//3939
//3940        sa.recycle();
//3941
//3942        XmlUtils.skipCurrentTag(parser);
//3943
//3944        return data;
//3945    }
//3946
//3947    private static VerifierInfo parseVerifier(Resources res, XmlPullParser parser,
//3948            AttributeSet attrs, int flags) {
//3949        final TypedArray sa = res.obtainAttributes(attrs,
//3950                com.android.internal.R.styleable.AndroidManifestPackageVerifier);
//3951
//3952        final String packageName = sa.getNonResourceString(
//3953                com.android.internal.R.styleable.AndroidManifestPackageVerifier_name);
//3954
//3955        final String encodedPublicKey = sa.getNonResourceString(
//3956                com.android.internal.R.styleable.AndroidManifestPackageVerifier_publicKey);
//3957
//3958        sa.recycle();
//3959
//3960        if (packageName == null || packageName.length() == 0) {
//3961            Slog.i(TAG, "verifier package name was null; skipping");
//3962            return null;
//3963        }
//3964
//3965        final PublicKey publicKey = parsePublicKey(encodedPublicKey);
//3966        if (publicKey == null) {
//3967            Slog.i(TAG, "Unable to parse verifier public key for " + packageName);
//3968            return null;
//3969        }
//3970
//3971        return new VerifierInfo(packageName, publicKey);
//3972    }
//3973
//3974    public static final PublicKey parsePublicKey(final String encodedPublicKey) {
//3975        if (encodedPublicKey == null) {
//3976            Slog.i(TAG, "Could not parse null public key");
//3977            return null;
//3978        }
//3979
//3980        EncodedKeySpec keySpec;
//3981        try {
//3982            final byte[] encoded = Base64.decode(encodedPublicKey, Base64.DEFAULT);
//3983            keySpec = new X509EncodedKeySpec(encoded);
//3984        } catch (IllegalArgumentException e) {
//3985            Slog.i(TAG, "Could not parse verifier public key; invalid Base64");
//3986            return null;
//3987        }
//3988
//3989        /* First try the key as an RSA key. */
//3990        try {
//3991            final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
//3992            return keyFactory.generatePublic(keySpec);
//3993        } catch (NoSuchAlgorithmException e) {
//3994            Log.wtf(TAG, "Could not parse public key because RSA isn't included in build");
//3995            return null;
//3996        } catch (InvalidKeySpecException e) {
//3997            // Not a RSA public key.
//3998        }
//3999
//4000        /* Now try it as a DSA key. */
//4001        try {
//4002            final KeyFactory keyFactory = KeyFactory.getInstance("DSA");
//4003            return keyFactory.generatePublic(keySpec);
//4004        } catch (NoSuchAlgorithmException e) {
//4005            Log.wtf(TAG, "Could not parse public key because DSA isn't included in build");
//4006            return null;
//4007        } catch (InvalidKeySpecException e) {
//4008            // Not a DSA public key.
//4009        }
//4010
//4011        return null;
//4012    }
//4013
//4014    private static final String ANDROID_RESOURCES
//4015            = "http://schemas.android.com/apk/res/android";
//4016
//4017    private boolean parseIntent(Resources res, XmlPullParser parser, AttributeSet attrs,
//4018            boolean allowGlobs, IntentInfo outInfo, String[] outError)
//4019            throws XmlPullParserException, IOException {
//4020
//4021        TypedArray sa = res.obtainAttributes(attrs,
//4022                com.android.internal.R.styleable.AndroidManifestIntentFilter);
//4023
//4024        int priority = sa.getInt(
//4025                com.android.internal.R.styleable.AndroidManifestIntentFilter_priority, 0);
//4026        outInfo.setPriority(priority);
//4027
//4028        TypedValue v = sa.peekValue(
//4029                com.android.internal.R.styleable.AndroidManifestIntentFilter_label);
//4030        if (v != null && (outInfo.labelRes=v.resourceId) == 0) {
//4031            outInfo.nonLocalizedLabel = v.coerceToString();
//4032        }
//4033
//4034        outInfo.icon = sa.getResourceId(
//4035                com.android.internal.R.styleable.AndroidManifestIntentFilter_icon, 0);
//4036
//4037        outInfo.logo = sa.getResourceId(
//4038                com.android.internal.R.styleable.AndroidManifestIntentFilter_logo, 0);
//4039
//4040        outInfo.banner = sa.getResourceId(
//4041                com.android.internal.R.styleable.AndroidManifestIntentFilter_banner, 0);
//4042
//4043        sa.recycle();
//4044
//4045        int outerDepth = parser.getDepth();
//4046        int type;
//4047        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
//4048                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
//4049            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
//4050                continue;
//4051            }
//4052
//4053            String nodeName = parser.getName();
//4054            if (nodeName.equals("action")) {
//4055                String value = attrs.getAttributeValue(
//4056                        ANDROID_RESOURCES, "name");
//4057                if (value == null || value == "") {
//4058                    outError[0] = "No value supplied for <android:name>";
//4059                    return false;
//4060                }
//4061                XmlUtils.skipCurrentTag(parser);
//4062
//4063                outInfo.addAction(value);
//4064            } else if (nodeName.equals("category")) {
//4065                String value = attrs.getAttributeValue(
//4066                        ANDROID_RESOURCES, "name");
//4067                if (value == null || value == "") {
//4068                    outError[0] = "No value supplied for <android:name>";
//4069                    return false;
//4070                }
//4071                XmlUtils.skipCurrentTag(parser);
//4072
//4073                outInfo.addCategory(value);
//4074
//4075            } else if (nodeName.equals("data")) {
//4076                sa = res.obtainAttributes(attrs,
//4077                        com.android.internal.R.styleable.AndroidManifestData);
//4078
//4079                String str = sa.getNonConfigurationString(
//4080                        com.android.internal.R.styleable.AndroidManifestData_mimeType, 0);
//4081                if (str != null) {
//4082                    try {
//4083                        outInfo.addDataType(str);
//4084                    } catch (IntentFilter.MalformedMimeTypeException e) {
//4085                        outError[0] = e.toString();
//4086                        sa.recycle();
//4087                        return false;
//4088                    }
//4089                }
//4090
//4091                str = sa.getNonConfigurationString(
//4092                        com.android.internal.R.styleable.AndroidManifestData_scheme, 0);
//4093                if (str != null) {
//4094                    outInfo.addDataScheme(str);
//4095                }
//4096
//4097                str = sa.getNonConfigurationString(
//4098                        com.android.internal.R.styleable.AndroidManifestData_ssp, 0);
//4099                if (str != null) {
//4100                    outInfo.addDataSchemeSpecificPart(str, PatternMatcher.PATTERN_LITERAL);
//4101                }
//4102
//4103                str = sa.getNonConfigurationString(
//4104                        com.android.internal.R.styleable.AndroidManifestData_sspPrefix, 0);
//4105                if (str != null) {
//4106                    outInfo.addDataSchemeSpecificPart(str, PatternMatcher.PATTERN_PREFIX);
//4107                }
//4108
//4109                str = sa.getNonConfigurationString(
//4110                        com.android.internal.R.styleable.AndroidManifestData_sspPattern, 0);
//4111                if (str != null) {
//4112                    if (!allowGlobs) {
//4113                        outError[0] = "sspPattern not allowed here; ssp must be literal";
//4114                        return false;
//4115                    }
//4116                    outInfo.addDataSchemeSpecificPart(str, PatternMatcher.PATTERN_SIMPLE_GLOB);
//4117                }
//4118
//4119                String host = sa.getNonConfigurationString(
//4120                        com.android.internal.R.styleable.AndroidManifestData_host, 0);
//4121                String port = sa.getNonConfigurationString(
//4122                        com.android.internal.R.styleable.AndroidManifestData_port, 0);
//4123                if (host != null) {
//4124                    outInfo.addDataAuthority(host, port);
//4125                }
//4126
//4127                str = sa.getNonConfigurationString(
//4128                        com.android.internal.R.styleable.AndroidManifestData_path, 0);
//4129                if (str != null) {
//4130                    outInfo.addDataPath(str, PatternMatcher.PATTERN_LITERAL);
//4131                }
//4132
//4133                str = sa.getNonConfigurationString(
//4134                        com.android.internal.R.styleable.AndroidManifestData_pathPrefix, 0);
//4135                if (str != null) {
//4136                    outInfo.addDataPath(str, PatternMatcher.PATTERN_PREFIX);
//4137                }
//4138
//4139                str = sa.getNonConfigurationString(
//4140                        com.android.internal.R.styleable.AndroidManifestData_pathPattern, 0);
//4141                if (str != null) {
//4142                    if (!allowGlobs) {
//4143                        outError[0] = "pathPattern not allowed here; path must be literal";
//4144                        return false;
//4145                    }
//4146                    outInfo.addDataPath(str, PatternMatcher.PATTERN_SIMPLE_GLOB);
//4147                }
//4148
//4149                sa.recycle();
//4150                XmlUtils.skipCurrentTag(parser);
//4151            } else if (!RIGID_PARSER) {
//4152                Slog.w(TAG, "Unknown element under <intent-filter>: "
//4153                        + parser.getName() + " at " + mArchiveSourcePath + " "
//4154                        + parser.getPositionDescription());
//4155                XmlUtils.skipCurrentTag(parser);
//4156            } else {
//4157                outError[0] = "Bad element under <intent-filter>: " + parser.getName();
//4158                return false;
//4159            }
//4160        }
//4161
//4162        outInfo.hasDefault = outInfo.hasCategory(Intent.CATEGORY_DEFAULT);
//4163
//4164        if (DEBUG_PARSER) {
//4165            final StringBuilder cats = new StringBuilder("Intent d=");
//4166            cats.append(outInfo.hasDefault);
//4167            cats.append(", cat=");
//4168
//4169            final Iterator<String> it = outInfo.categoriesIterator();
//4170            if (it != null) {
//4171                while (it.hasNext()) {
//4172                    cats.append(' ');
//4173                    cats.append(it.next());
//4174                }
//4175            }
//4176            Slog.d(TAG, cats.toString());
//4177        }
//4178
//4179        return true;
//4180    }
//4181
//4182    /**
//4183     * Representation of a full package parsed from APK files on disk. A package
//4184     * consists of a single base APK, and zero or more split APKs.
//4185     */
//4186    public final static class Package {
//4187
//4188        public String packageName;
//4189
//4190        /** Names of any split APKs, ordered by parsed splitName */
//4191        public String[] splitNames;
//4192
//4193        // TODO: work towards making these paths invariant
//4194
//4195        /**
//4196         * Path where this package was found on disk. For monolithic packages
//4197         * this is path to single base APK file; for cluster packages this is
//4198         * path to the cluster directory.
//4199         */
//4200        public String codePath;
//4201
//4202        /** Path of base APK */
//4203        public String baseCodePath;
//4204        /** Paths of any split APKs, ordered by parsed splitName */
//4205        public String[] splitCodePaths;
//4206
//4207        /** Revision code of base APK */
//4208        public int baseRevisionCode;
//4209        /** Revision codes of any split APKs, ordered by parsed splitName */
//4210        public int[] splitRevisionCodes;
//4211
//4212        /** Flags of any split APKs; ordered by parsed splitName */
//4213        public int[] splitFlags;
//4214
//4215        public boolean baseHardwareAccelerated;
//4216
//4217        // For now we only support one application per package.
//4218        public final ApplicationInfo applicationInfo = new ApplicationInfo();
//4219
//4220        public final ArrayList<Permission> permissions = new ArrayList<Permission>(0);
//4221        public final ArrayList<PermissionGroup> permissionGroups = new ArrayList<PermissionGroup>(0);
//4222        public final ArrayList<Activity> activities = new ArrayList<Activity>(0);
//4223        public final ArrayList<Activity> receivers = new ArrayList<Activity>(0);
//4224        public final ArrayList<Provider> providers = new ArrayList<Provider>(0);
//4225        public final ArrayList<Service> services = new ArrayList<Service>(0);
//4226        public final ArrayList<Instrumentation> instrumentation = new ArrayList<Instrumentation>(0);
//4227
//4228        public final ArrayList<String> requestedPermissions = new ArrayList<String>();
//4229        public final ArrayList<Boolean> requestedPermissionsRequired = new ArrayList<Boolean>();
//4230
//4231        public ArrayList<String> protectedBroadcasts;
//4232
//4233        public ArrayList<String> libraryNames = null;
//4234        public ArrayList<String> usesLibraries = null;
//4235        public ArrayList<String> usesOptionalLibraries = null;
//4236        public String[] usesLibraryFiles = null;
//4237
//4238        public ArrayList<ActivityIntentInfo> preferredActivityFilters = null;
//4239
//4240        public ArrayList<String> mOriginalPackages = null;
//4241        public String mRealPackage = null;
//4242        public ArrayList<String> mAdoptPermissions = null;
//4243
//4244        // We store the application meta-data independently to avoid multiple unwanted references
//4245        public Bundle mAppMetaData = null;
//4246
//4247        // The version code declared for this package.
//4248        public int mVersionCode;
//4249
//4250        // The version name declared for this package.
//4251        public String mVersionName;
//4252
//4253        // The shared user id that this package wants to use.
//4254        public String mSharedUserId;
//4255
//4256        // The shared user label that this package wants to use.
//4257        public int mSharedUserLabel;
//4258
//4259        // Signatures that were read from the package.
//4260        public Signature[] mSignatures;
//4261        public Certificate[][] mCertificates;
//4262
//4263        // For use by package manager service for quick lookup of
//4264        // preferred up order.
//4265        public int mPreferredOrder = 0;
//4266
//4267        // For use by package manager to keep track of where it needs to do dexopt.
//4268        public final ArraySet<String> mDexOptPerformed = new ArraySet<>(4);
//4269
//4270        // For use by package manager to keep track of when a package was last used.
//4271        public long mLastPackageUsageTimeInMills;
//4272
//4273        // // User set enabled state.
//4274        // public int mSetEnabled = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
//4275        //
//4276        // // Whether the package has been stopped.
//4277        // public boolean mSetStopped = false;
//4278
//4279        // Additional data supplied by callers.
//4280        public Object mExtras;
//4281
//4282        // Whether an operation is currently pending on this package
//4283        public boolean mOperationPending;
//4284
//4285        // Applications hardware preferences
//4286        public ArrayList<ConfigurationInfo> configPreferences = null;
//4287
//4288        // Applications requested features
//4289        public ArrayList<FeatureInfo> reqFeatures = null;
//4290
//4291        // Applications requested feature groups
//4292        public ArrayList<FeatureGroupInfo> featureGroups = null;
//4293
//4294        public int installLocation;
//4295
//4296        public boolean coreApp;
//4297
//4298        /* An app that's required for all users and cannot be uninstalled for a user */
//4299        public boolean mRequiredForAllUsers;
//4300
//4301        /* The restricted account authenticator type that is used by this application */
//4302        public String mRestrictedAccountType;
//4303
//4304        /* The required account type without which this application will not function */
//4305        public String mRequiredAccountType;
//4306
//4307        /**
//4308         * Digest suitable for comparing whether this package's manifest is the
//4309         * same as another.
//4310         */
//4311        public ManifestDigest manifestDigest;
//4312
//4313        public String mOverlayTarget;
//4314        public int mOverlayPriority;
//4315        public boolean mTrustedOverlay;
//4316
//4317        /**
//4318         * Data used to feed the KeySetManagerService
//4319         */
//4320        public ArraySet<PublicKey> mSigningKeys;
//4321        public ArraySet<String> mUpgradeKeySets;
//4322        public ArrayMap<String, ArraySet<PublicKey>> mKeySetMapping;
//4323
//4324        /**
//4325         * The install time abi override for this package, if any.
//4326         *
//4327         * TODO: This seems like a horrible place to put the abiOverride because
//4328         * this isn't something the packageParser parsers. However, this fits in with
//4329         * the rest of the PackageManager where package scanning randomly pushes
//4330         * and prods fields out of {@code this.applicationInfo}.
//4331         */
//4332        public String cpuAbiOverride;
//4333
//4334        public Package(String packageName) {
//4335            this.packageName = packageName;
//4336            applicationInfo.packageName = packageName;
//4337            applicationInfo.uid = -1;
//4338        }
//4339
//4340        public List<String> getAllCodePaths() {
//4341            ArrayList<String> paths = new ArrayList<>();
//4342            paths.add(baseCodePath);
//4343            if (!ArrayUtils.isEmpty(splitCodePaths)) {
//4344                Collections.addAll(paths, splitCodePaths);
//4345            }
//4346            return paths;
//4347        }
//4348
//4349        /**
//4350         * Filtered set of {@link #getAllCodePaths()} that excludes
//4351         * resource-only APKs.
//4352         */
//4353        public List<String> getAllCodePathsExcludingResourceOnly() {
//4354            ArrayList<String> paths = new ArrayList<>();
//4355            if ((applicationInfo.flags & ApplicationInfo.FLAG_HAS_CODE) != 0) {
//4356                paths.add(baseCodePath);
//4357            }
//4358            if (!ArrayUtils.isEmpty(splitCodePaths)) {
//4359                for (int i = 0; i < splitCodePaths.length; i++) {
//4360                    if ((splitFlags[i] & ApplicationInfo.FLAG_HAS_CODE) != 0) {
//4361                        paths.add(splitCodePaths[i]);
//4362                    }
//4363                }
//4364            }
//4365            return paths;
//4366        }
//4367
//4368        public void setPackageName(String newName) {
//4369            packageName = newName;
//4370            applicationInfo.packageName = newName;
//4371            for (int i=permissions.size()-1; i>=0; i--) {
//4372                permissions.get(i).setPackageName(newName);
//4373            }
//4374            for (int i=permissionGroups.size()-1; i>=0; i--) {
//4375                permissionGroups.get(i).setPackageName(newName);
//4376            }
//4377            for (int i=activities.size()-1; i>=0; i--) {
//4378                activities.get(i).setPackageName(newName);
//4379            }
//4380            for (int i=receivers.size()-1; i>=0; i--) {
//4381                receivers.get(i).setPackageName(newName);
//4382            }
//4383            for (int i=providers.size()-1; i>=0; i--) {
//4384                providers.get(i).setPackageName(newName);
//4385            }
//4386            for (int i=services.size()-1; i>=0; i--) {
//4387                services.get(i).setPackageName(newName);
//4388            }
//4389            for (int i=instrumentation.size()-1; i>=0; i--) {
//4390                instrumentation.get(i).setPackageName(newName);
//4391            }
//4392        }
//4393
//4394        public boolean hasComponentClassName(String name) {
//4395            for (int i=activities.size()-1; i>=0; i--) {
//4396                if (name.equals(activities.get(i).className)) {
//4397                    return true;
//4398                }
//4399            }
//4400            for (int i=receivers.size()-1; i>=0; i--) {
//4401                if (name.equals(receivers.get(i).className)) {
//4402                    return true;
//4403                }
//4404            }
//4405            for (int i=providers.size()-1; i>=0; i--) {
//4406                if (name.equals(providers.get(i).className)) {
//4407                    return true;
//4408                }
//4409            }
//4410            for (int i=services.size()-1; i>=0; i--) {
//4411                if (name.equals(services.get(i).className)) {
//4412                    return true;
//4413                }
//4414            }
//4415            for (int i=instrumentation.size()-1; i>=0; i--) {
//4416                if (name.equals(instrumentation.get(i).className)) {
//4417                    return true;
//4418                }
//4419            }
//4420            return false;
//4421        }
//4422
//4423        public String toString() {
//4424            return "Package{"
//4425                + Integer.toHexString(System.identityHashCode(this))
//4426                + " " + packageName + "}";
//4427        }
//4428    }
//4429
//4430    public static class Component<II extends IntentInfo> {
//4431        public final Package owner;
//4432        public final ArrayList<II> intents;
//4433        public final String className;
//4434        public Bundle metaData;
//4435
//4436        ComponentName componentName;
//4437        String componentShortName;
//4438
//4439        public Component(Package _owner) {
//4440            owner = _owner;
//4441            intents = null;
//4442            className = null;
//4443        }
//4444
//4445        public Component(final ParsePackageItemArgs args, final PackageItemInfo outInfo) {
//4446            owner = args.owner;
//4447            intents = new ArrayList<II>(0);
//4448            String name = args.sa.getNonConfigurationString(args.nameRes, 0);
//4449            if (name == null) {
//4450                className = null;
//4451                args.outError[0] = args.tag + " does not specify android:name";
//4452                return;
//4453            }
//4454
//4455            outInfo.name
//4456                = buildClassName(owner.applicationInfo.packageName, name, args.outError);
//4457            if (outInfo.name == null) {
//4458                className = null;
//4459                args.outError[0] = args.tag + " does not have valid android:name";
//4460                return;
//4461            }
//4462
//4463            className = outInfo.name;
//4464
//4465            int iconVal = args.sa.getResourceId(args.iconRes, 0);
//4466            if (iconVal != 0) {
//4467                outInfo.icon = iconVal;
//4468                outInfo.nonLocalizedLabel = null;
//4469            }
//4470
//4471            int logoVal = args.sa.getResourceId(args.logoRes, 0);
//4472            if (logoVal != 0) {
//4473                outInfo.logo = logoVal;
//4474            }
//4475
//4476            int bannerVal = args.sa.getResourceId(args.bannerRes, 0);
//4477            if (bannerVal != 0) {
//4478                outInfo.banner = bannerVal;
//4479            }
//4480
//4481            TypedValue v = args.sa.peekValue(args.labelRes);
//4482            if (v != null && (outInfo.labelRes=v.resourceId) == 0) {
//4483                outInfo.nonLocalizedLabel = v.coerceToString();
//4484            }
//4485
//4486            outInfo.packageName = owner.packageName;
//4487        }
//4488
//4489        public Component(final ParseComponentArgs args, final ComponentInfo outInfo) {
//4490            this(args, (PackageItemInfo)outInfo);
//4491            if (args.outError[0] != null) {
//4492                return;
//4493            }
//4494
//4495            if (args.processRes != 0) {
//4496                CharSequence pname;
//4497                if (owner.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.FROYO) {
//4498                    pname = args.sa.getNonConfigurationString(args.processRes,
//4499                            Configuration.NATIVE_CONFIG_VERSION);
//4500                } else {
//4501                    // Some older apps have been seen to use a resource reference
//4502                    // here that on older builds was ignored (with a warning).  We
//4503                    // need to continue to do this for them so they don't break.
//4504                    pname = args.sa.getNonResourceString(args.processRes);
//4505                }
//4506                outInfo.processName = buildProcessName(owner.applicationInfo.packageName,
//4507                        owner.applicationInfo.processName, pname,
//4508                        args.flags, args.sepProcesses, args.outError);
//4509            }
//4510
//4511            if (args.descriptionRes != 0) {
//4512                outInfo.descriptionRes = args.sa.getResourceId(args.descriptionRes, 0);
//4513            }
//4514
//4515            outInfo.enabled = args.sa.getBoolean(args.enabledRes, true);
//4516        }
//4517
//4518        public Component(Component<II> clone) {
//4519            owner = clone.owner;
//4520            intents = clone.intents;
//4521            className = clone.className;
//4522            componentName = clone.componentName;
//4523            componentShortName = clone.componentShortName;
//4524        }
//4525
//4526        public ComponentName getComponentName() {
//4527            if (componentName != null) {
//4528                return componentName;
//4529            }
//4530            if (className != null) {
//4531                componentName = new ComponentName(owner.applicationInfo.packageName,
//4532                        className);
//4533            }
//4534            return componentName;
//4535        }
//4536
//4537        public void appendComponentShortName(StringBuilder sb) {
//4538            ComponentName.appendShortString(sb, owner.applicationInfo.packageName, className);
//4539        }
//4540
//4541        public void printComponentShortName(PrintWriter pw) {
//4542            ComponentName.printShortString(pw, owner.applicationInfo.packageName, className);
//4543        }
//4544
//4545        public void setPackageName(String packageName) {
//4546            componentName = null;
//4547            componentShortName = null;
//4548        }
//4549    }
//4550
//4551    public final static class Permission extends Component<IntentInfo> {
//4552        public final PermissionInfo info;
//4553        public boolean tree;
//4554        public PermissionGroup group;
//4555
//4556        public Permission(Package _owner) {
//4557            super(_owner);
//4558            info = new PermissionInfo();
//4559        }
//4560
//4561        public Permission(Package _owner, PermissionInfo _info) {
//4562            super(_owner);
//4563            info = _info;
//4564        }
//4565
//4566        public void setPackageName(String packageName) {
//4567            super.setPackageName(packageName);
//4568            info.packageName = packageName;
//4569        }
//4570
//4571        public String toString() {
//4572            return "Permission{"
//4573                + Integer.toHexString(System.identityHashCode(this))
//4574                + " " + info.name + "}";
//4575        }
//4576    }
//4577
//4578    public final static class PermissionGroup extends Component<IntentInfo> {
//4579        public final PermissionGroupInfo info;
//4580
//4581        public PermissionGroup(Package _owner) {
//4582            super(_owner);
//4583            info = new PermissionGroupInfo();
//4584        }
//4585
//4586        public PermissionGroup(Package _owner, PermissionGroupInfo _info) {
//4587            super(_owner);
//4588            info = _info;
//4589        }
//4590
//4591        public void setPackageName(String packageName) {
//4592            super.setPackageName(packageName);
//4593            info.packageName = packageName;
//4594        }
//4595
//4596        public String toString() {
//4597            return "PermissionGroup{"
//4598                + Integer.toHexString(System.identityHashCode(this))
//4599                + " " + info.name + "}";
//4600        }
//4601    }
//4602
//4603    private static boolean copyNeeded(int flags, Package p,
//4604            PackageUserState state, Bundle metaData, int userId) {
//4605        if (userId != 0) {
//4606            // We always need to copy for other users, since we need
//4607            // to fix up the uid.
//4608            return true;
//4609        }
//4610        if (state.enabled != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
//4611            boolean enabled = state.enabled == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
//4612            if (p.applicationInfo.enabled != enabled) {
//4613                return true;
//4614            }
//4615        }
//4616        if (!state.installed || state.hidden) {
//4617            return true;
//4618        }
//4619        if (state.stopped) {
//4620            return true;
//4621        }
//4622        if ((flags & PackageManager.GET_META_DATA) != 0
//4623                && (metaData != null || p.mAppMetaData != null)) {
//4624            return true;
//4625        }
//4626        if ((flags & PackageManager.GET_SHARED_LIBRARY_FILES) != 0
//4627                && p.usesLibraryFiles != null) {
//4628            return true;
//4629        }
//4630        return false;
//4631    }
//4632
//4633    public static ApplicationInfo generateApplicationInfo(Package p, int flags,
//4634            PackageUserState state) {
//4635        return generateApplicationInfo(p, flags, state, UserHandle.getCallingUserId());
//4636    }
//4637
//4638    private static void updateApplicationInfo(ApplicationInfo ai, int flags,
//4639            PackageUserState state) {
//4640        // CompatibilityMode is global state.
//4641        if (!sCompatibilityModeEnabled) {
//4642            ai.disableCompatibilityMode();
//4643        }
//4644        if (state.installed) {
//4645            ai.flags |= ApplicationInfo.FLAG_INSTALLED;
//4646        } else {
//4647            ai.flags &= ~ApplicationInfo.FLAG_INSTALLED;
//4648        }
//4649        if (state.hidden) {
//4650            ai.flags |= ApplicationInfo.FLAG_HIDDEN;
//4651        } else {
//4652            ai.flags &= ~ApplicationInfo.FLAG_HIDDEN;
//4653        }
//4654        if (state.enabled == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
//4655            ai.enabled = true;
//4656        } else if (state.enabled == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) {
//4657            ai.enabled = (flags&PackageManager.GET_DISABLED_UNTIL_USED_COMPONENTS) != 0;
//4658        } else if (state.enabled == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
//4659                || state.enabled == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) {
//4660            ai.enabled = false;
//4661        }
//4662        ai.enabledSetting = state.enabled;
//4663    }
//4664
//4665    public static ApplicationInfo generateApplicationInfo(Package p, int flags,
//4666            PackageUserState state, int userId) {
//4667        if (p == null) return null;
//4668        if (!checkUseInstalledOrHidden(flags, state)) {
//4669            return null;
//4670        }
//4671        if (!copyNeeded(flags, p, state, null, userId)
//4672                && ((flags&PackageManager.GET_DISABLED_UNTIL_USED_COMPONENTS) == 0
//4673                        || state.enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED)) {
//4674            // In this case it is safe to directly modify the internal ApplicationInfo state:
//4675            // - CompatibilityMode is global state, so will be the same for every call.
//4676            // - We only come in to here if the app should reported as installed; this is the
//4677            // default state, and we will do a copy otherwise.
//4678            // - The enable state will always be reported the same for the application across
//4679            // calls; the only exception is for the UNTIL_USED mode, and in that case we will
//4680            // be doing a copy.
//4681            updateApplicationInfo(p.applicationInfo, flags, state);
//4682            return p.applicationInfo;
//4683        }
//4684
//4685        // Make shallow copy so we can store the metadata/libraries safely
//4686        ApplicationInfo ai = new ApplicationInfo(p.applicationInfo);
//4687        if (userId != 0) {
//4688            ai.uid = UserHandle.getUid(userId, ai.uid);
//4689            ai.dataDir = PackageManager.getDataDirForUser(userId, ai.packageName);
//4690        }
//4691        if ((flags & PackageManager.GET_META_DATA) != 0) {
//4692            ai.metaData = p.mAppMetaData;
//4693        }
//4694        if ((flags & PackageManager.GET_SHARED_LIBRARY_FILES) != 0) {
//4695            ai.sharedLibraryFiles = p.usesLibraryFiles;
//4696        }
//4697        if (state.stopped) {
//4698            ai.flags |= ApplicationInfo.FLAG_STOPPED;
//4699        } else {
//4700            ai.flags &= ~ApplicationInfo.FLAG_STOPPED;
//4701        }
//4702        updateApplicationInfo(ai, flags, state);
//4703        return ai;
//4704    }
//4705
//4706    public static ApplicationInfo generateApplicationInfo(ApplicationInfo ai, int flags,
//4707            PackageUserState state, int userId) {
//4708        if (ai == null) return null;
//4709        if (!checkUseInstalledOrHidden(flags, state)) {
//4710            return null;
//4711        }
//4712        // This is only used to return the ResolverActivity; we will just always
//4713        // make a copy.
//4714        ai = new ApplicationInfo(ai);
//4715        if (userId != 0) {
//4716            ai.uid = UserHandle.getUid(userId, ai.uid);
//4717            ai.dataDir = PackageManager.getDataDirForUser(userId, ai.packageName);
//4718        }
//4719        if (state.stopped) {
//4720            ai.flags |= ApplicationInfo.FLAG_STOPPED;
//4721        } else {
//4722            ai.flags &= ~ApplicationInfo.FLAG_STOPPED;
//4723        }
//4724        updateApplicationInfo(ai, flags, state);
//4725        return ai;
//4726    }
//4727
//4728    public static final PermissionInfo generatePermissionInfo(
//4729            Permission p, int flags) {
//4730        if (p == null) return null;
//4731        if ((flags&PackageManager.GET_META_DATA) == 0) {
//4732            return p.info;
//4733        }
//4734        PermissionInfo pi = new PermissionInfo(p.info);
//4735        pi.metaData = p.metaData;
//4736        return pi;
//4737    }
//4738
//4739    public static final PermissionGroupInfo generatePermissionGroupInfo(
//4740            PermissionGroup pg, int flags) {
//4741        if (pg == null) return null;
//4742        if ((flags&PackageManager.GET_META_DATA) == 0) {
//4743            return pg.info;
//4744        }
//4745        PermissionGroupInfo pgi = new PermissionGroupInfo(pg.info);
//4746        pgi.metaData = pg.metaData;
//4747        return pgi;
//4748    }
//4749
//4750    public final static class Activity extends Component<ActivityIntentInfo> {
//4751        public final ActivityInfo info;
//4752
//4753        public Activity(final ParseComponentArgs args, final ActivityInfo _info) {
//4754            super(args, _info);
//4755            info = _info;
//4756            info.applicationInfo = args.owner.applicationInfo;
//4757        }
//4758
//4759        public void setPackageName(String packageName) {
//4760            super.setPackageName(packageName);
//4761            info.packageName = packageName;
//4762        }
//4763
//4764        public String toString() {
//4765            StringBuilder sb = new StringBuilder(128);
//4766            sb.append("Activity{");
//4767            sb.append(Integer.toHexString(System.identityHashCode(this)));
//4768            sb.append(' ');
//4769            appendComponentShortName(sb);
//4770            sb.append('}');
//4771            return sb.toString();
//4772        }
//4773    }
//4774
//4775    public static final ActivityInfo generateActivityInfo(Activity a, int flags,
//4776            PackageUserState state, int userId) {
//4777        if (a == null) return null;
//4778        if (!checkUseInstalledOrHidden(flags, state)) {
//4779            return null;
//4780        }
//4781        if (!copyNeeded(flags, a.owner, state, a.metaData, userId)) {
//4782            return a.info;
//4783        }
//4784        // Make shallow copies so we can store the metadata safely
//4785        ActivityInfo ai = new ActivityInfo(a.info);
//4786        ai.metaData = a.metaData;
//4787        ai.applicationInfo = generateApplicationInfo(a.owner, flags, state, userId);
//4788        return ai;
//4789    }
//4790
//4791    public static final ActivityInfo generateActivityInfo(ActivityInfo ai, int flags,
//4792            PackageUserState state, int userId) {
//4793        if (ai == null) return null;
//4794        if (!checkUseInstalledOrHidden(flags, state)) {
//4795            return null;
//4796        }
//4797        // This is only used to return the ResolverActivity; we will just always
//4798        // make a copy.
//4799        ai = new ActivityInfo(ai);
//4800        ai.applicationInfo = generateApplicationInfo(ai.applicationInfo, flags, state, userId);
//4801        return ai;
//4802    }
//4803
//4804    public final static class Service extends Component<ServiceIntentInfo> {
//4805        public final ServiceInfo info;
//4806
//4807        public Service(final ParseComponentArgs args, final ServiceInfo _info) {
//4808            super(args, _info);
//4809            info = _info;
//4810            info.applicationInfo = args.owner.applicationInfo;
//4811        }
//4812
//4813        public void setPackageName(String packageName) {
//4814            super.setPackageName(packageName);
//4815            info.packageName = packageName;
//4816        }
//4817
//4818        public String toString() {
//4819            StringBuilder sb = new StringBuilder(128);
//4820            sb.append("Service{");
//4821            sb.append(Integer.toHexString(System.identityHashCode(this)));
//4822            sb.append(' ');
//4823            appendComponentShortName(sb);
//4824            sb.append('}');
//4825            return sb.toString();
//4826        }
//4827    }
//4828
//4829    public static final ServiceInfo generateServiceInfo(Service s, int flags,
//4830            PackageUserState state, int userId) {
//4831        if (s == null) return null;
//4832        if (!checkUseInstalledOrHidden(flags, state)) {
//4833            return null;
//4834        }
//4835        if (!copyNeeded(flags, s.owner, state, s.metaData, userId)) {
//4836            return s.info;
//4837        }
//4838        // Make shallow copies so we can store the metadata safely
//4839        ServiceInfo si = new ServiceInfo(s.info);
//4840        si.metaData = s.metaData;
//4841        si.applicationInfo = generateApplicationInfo(s.owner, flags, state, userId);
//4842        return si;
//4843    }
//4844
//4845    public final static class Provider extends Component<ProviderIntentInfo> {
//4846        public final ProviderInfo info;
//4847        public boolean syncable;
//4848
//4849        public Provider(final ParseComponentArgs args, final ProviderInfo _info) {
//4850            super(args, _info);
//4851            info = _info;
//4852            info.applicationInfo = args.owner.applicationInfo;
//4853            syncable = false;
//4854        }
//4855
//4856        public Provider(Provider existingProvider) {
//4857            super(existingProvider);
//4858            this.info = existingProvider.info;
//4859            this.syncable = existingProvider.syncable;
//4860        }
//4861
//4862        public void setPackageName(String packageName) {
//4863            super.setPackageName(packageName);
//4864            info.packageName = packageName;
//4865        }
//4866
//4867        public String toString() {
//4868            StringBuilder sb = new StringBuilder(128);
//4869            sb.append("Provider{");
//4870            sb.append(Integer.toHexString(System.identityHashCode(this)));
//4871            sb.append(' ');
//4872            appendComponentShortName(sb);
//4873            sb.append('}');
//4874            return sb.toString();
//4875        }
//4876    }
//4877
//4878    public static final ProviderInfo generateProviderInfo(Provider p, int flags,
//4879            PackageUserState state, int userId) {
//4880        if (p == null) return null;
//4881        if (!checkUseInstalledOrHidden(flags, state)) {
//4882            return null;
//4883        }
//4884        if (!copyNeeded(flags, p.owner, state, p.metaData, userId)
//4885                && ((flags & PackageManager.GET_URI_PERMISSION_PATTERNS) != 0
//4886                        || p.info.uriPermissionPatterns == null)) {
//4887            return p.info;
//4888        }
//4889        // Make shallow copies so we can store the metadata safely
//4890        ProviderInfo pi = new ProviderInfo(p.info);
//4891        pi.metaData = p.metaData;
//4892        if ((flags & PackageManager.GET_URI_PERMISSION_PATTERNS) == 0) {
//4893            pi.uriPermissionPatterns = null;
//4894        }
//4895        pi.applicationInfo = generateApplicationInfo(p.owner, flags, state, userId);
//4896        return pi;
//4897    }
//4898
//4899    public final static class Instrumentation extends Component {
//4900        public final InstrumentationInfo info;
//4901
//4902        public Instrumentation(final ParsePackageItemArgs args, final InstrumentationInfo _info) {
//4903            super(args, _info);
//4904            info = _info;
//4905        }
//4906
//4907        public void setPackageName(String packageName) {
//4908            super.setPackageName(packageName);
//4909            info.packageName = packageName;
//4910        }
//4911
//4912        public String toString() {
//4913            StringBuilder sb = new StringBuilder(128);
//4914            sb.append("Instrumentation{");
//4915            sb.append(Integer.toHexString(System.identityHashCode(this)));
//4916            sb.append(' ');
//4917            appendComponentShortName(sb);
//4918            sb.append('}');
//4919            return sb.toString();
//4920        }
//4921    }
//4922
//4923    public static final InstrumentationInfo generateInstrumentationInfo(
//4924            Instrumentation i, int flags) {
//4925        if (i == null) return null;
//4926        if ((flags&PackageManager.GET_META_DATA) == 0) {
//4927            return i.info;
//4928        }
//4929        InstrumentationInfo ii = new InstrumentationInfo(i.info);
//4930        ii.metaData = i.metaData;
//4931        return ii;
//4932    }
//4933
//4934    public static class IntentInfo extends IntentFilter {
//4935        public boolean hasDefault;
//4936        public int labelRes;
//4937        public CharSequence nonLocalizedLabel;
//4938        public int icon;
//4939        public int logo;
//4940        public int banner;
//4941        public int preferred;
//4942    }
//4943
//4944    public final static class ActivityIntentInfo extends IntentInfo {
//4945        public final Activity activity;
//4946
//4947        public ActivityIntentInfo(Activity _activity) {
//4948            activity = _activity;
//4949        }
//4950
//4951        public String toString() {
//4952            StringBuilder sb = new StringBuilder(128);
//4953            sb.append("ActivityIntentInfo{");
//4954            sb.append(Integer.toHexString(System.identityHashCode(this)));
//4955            sb.append(' ');
//4956            activity.appendComponentShortName(sb);
//4957            sb.append('}');
//4958            return sb.toString();
//4959        }
//4960    }
//4961
//4962    public final static class ServiceIntentInfo extends IntentInfo {
//4963        public final Service service;
//4964
//4965        public ServiceIntentInfo(Service _service) {
//4966            service = _service;
//4967        }
//4968
//4969        public String toString() {
//4970            StringBuilder sb = new StringBuilder(128);
//4971            sb.append("ServiceIntentInfo{");
//4972            sb.append(Integer.toHexString(System.identityHashCode(this)));
//4973            sb.append(' ');
//4974            service.appendComponentShortName(sb);
//4975            sb.append('}');
//4976            return sb.toString();
//4977        }
//4978    }
//4979
//4980    public static final class ProviderIntentInfo extends IntentInfo {
//4981        public final Provider provider;
//4982
//4983        public ProviderIntentInfo(Provider provider) {
//4984            this.provider = provider;
//4985        }
//4986
//4987        public String toString() {
//4988            StringBuilder sb = new StringBuilder(128);
//4989            sb.append("ProviderIntentInfo{");
//4990            sb.append(Integer.toHexString(System.identityHashCode(this)));
//4991            sb.append(' ');
//4992            provider.appendComponentShortName(sb);
//4993            sb.append('}');
//4994            return sb.toString();
//4995        }
//4996    }
//4997
//4998    /**
//4999     * @hide
//5000     */
//5001    public static void setCompatibilityModeEnabled(boolean compatibilityModeEnabled) {
//5002        sCompatibilityModeEnabled = compatibilityModeEnabled;
//5003    }
//5004
//5005    private static AtomicReference<byte[]> sBuffer = new AtomicReference<byte[]>();
//5006
//5007    public static long readFullyIgnoringContents(InputStream in) throws IOException {
//5008        byte[] buffer = sBuffer.getAndSet(null);
//5009        if (buffer == null) {
//5010            buffer = new byte[4096];
//5011        }
//5012
//5013        int n = 0;
//5014        int count = 0;
//5015        while ((n = in.read(buffer, 0, buffer.length)) != -1) {
//5016            count += n;
//5017        }
//5018
//5019        sBuffer.set(buffer);
//5020        return count;
//5021    }
//5022
//5023    public static void closeQuietly(StrictJarFile jarFile) {
//5024        if (jarFile != null) {
//5025            try {
//5026                jarFile.close();
//5027            } catch (Exception ignored) {
//5028            }
//5029        }
//5030    }
//5031
//5032    public static class PackageParserException extends Exception {
//5033        public final int error;
//5034
//5035        public PackageParserException(int error, String detailMessage) {
//5036            super(detailMessage);
//5037            this.error = error;
//5038        }
//5039
//5040        public PackageParserException(int error, String detailMessage, Throwable throwable) {
//5041            super(detailMessage, throwable);
//5042            this.error = error;
//5043        }
//5044    }
//5045}
//5046
//Indexes created Fri Mar 13 02:32:08 CET 2015