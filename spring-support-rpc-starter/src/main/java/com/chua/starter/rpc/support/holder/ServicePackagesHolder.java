package com.chua.starter.rpc.support.holder;

import java.util.HashSet;
import java.util.Set;

/**
 * 服务包持有者类，用于存储和管理已扫描的服务包和类。
 * <p>
 * <p>
 * 提供单例模式以供全局访问。
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/04/08
 */
public class ServicePackagesHolder {

    public static final String BEAN_NAME = "servicePackagesHolder";

    /**
     * 存储已扫描的包名集合
     */
    private final Set<String> scannedPackages = new HashSet<>();

    /**
     * 存储已扫描的类名集合
     */
    private final Set<String> scannedClasses = new HashSet<>();

    private static final ServicePackagesHolder DEFAULT = new ServicePackagesHolder();

    /**
     * 获取ServicePackagesHolder的单例实例。
     * @return ServicePackagesHolder的单例实例。
     */
    public static ServicePackagesHolder getInstance() {
        return DEFAULT;
    }

    /**
     * 添加一个已扫描的包。
     * @param apackage 要添加的包名。
     */
    public void addScannedPackage(String apackage) {
        apackage = normalizePackage(apackage);
        synchronized (scannedPackages) {
            scannedPackages.add(apackage);
        }
    }

    /**
     * 检查一个包是否已被扫描。
     * @param packageName 要检查的包名。
     * @return 如果包或其子包已被扫描，则返回true；否则返回false。
     */
    public boolean isPackageScanned(String packageName) {
        packageName = normalizePackage(packageName);
        synchronized (scannedPackages) {
            if (scannedPackages.contains(packageName)) {
                return true;
            }
            for (String scannedPackage : scannedPackages) {
                // 检查是否为子包
                if (isSubPackage(packageName, scannedPackage)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 添加一个已扫描的类。
     * @param className 要添加的类名。
     */
    public void addScannedClass(String className) {
        synchronized (scannedClasses) {
            scannedClasses.add(className);
        }
    }

    /**
     * 检查一个类是否已被扫描。
     * @param className 要检查的类名。
     * @return 如果类已被扫描，则返回true；否则返回false。
     */
    public boolean isClassScanned(String className) {
        synchronized (scannedClasses) {
            return scannedClasses.contains(className);
        }
    }

    /**
     * 判断测试包是否为父包的子包。
     * @param testPkg 待测试的包名。
     * @param parent 父包名。
     * @return 如果测试包是父包的子包，则返回true；否则返回false。
     */
    private boolean isSubPackage(String testPkg, String parent) {
        // 检查子包名是否以父包名为前缀
        return testPkg.startsWith(parent);
    }

    /**
     * 标准化包名，确保其以点号结束。
     * @param apackage 要标准化的包名。
     * @return 标准化后的包名。
     */
    private String normalizePackage(String apackage) {
        if (!apackage.endsWith(".")) {
            apackage += ".";
        }
        return apackage;
    }
}

