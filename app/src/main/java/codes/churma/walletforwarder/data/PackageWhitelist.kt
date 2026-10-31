package codes.churma.walletforwarder.data

/**
 * Pre-configured whitelist of Indonesian banks, e-wallets, and merchant platforms.
 */
object PackageWhitelist {

    val TARGET_PACKAGES = setOf(
        // Bank Jago
        "com.jago",
        // Bank Mandiri (Livin')
        "com.bankmandiri.livin",
        // BCA (myBCA, BCA Mobile, BCA)
        "com.bca",
        "com.bca.mobile",
        "com.bca.mybca",
        // Gojek / GoPay
        "com.gojek.app",
        // Shopee / ShopeePay
        "com.shopee.id",
        // DANA
        "id.dana",
        // OVO
        "ovo.id",
        // Flip
        "id.co.flip",
        // TikTok / TikTok Shop
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        // Grab
        "com.grabtaxi.passenger"
    )

    fun isWhitelisted(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return TARGET_PACKAGES.contains(packageName.trim().lowercase())
    }
}
