package id.mybase.aether.packages

import id.mybase.aether.domain.AppPackage

/** Source of installed-package data. Implementations must return real device data. */
interface PackageRepository {
    suspend fun getInstalledPackages(): List<AppPackage>
}
