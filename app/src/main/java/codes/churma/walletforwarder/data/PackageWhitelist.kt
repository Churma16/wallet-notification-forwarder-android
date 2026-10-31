package codes.churma.walletforwarder.data

/**
 * Pre-configured whitelist of Indonesian banks, e-wallets, and merchant platforms.
 */
object PackageWhitelist {

    val TARGET_PACKAGES = setOf(
        // Bank Jago
        "com.jago.digital",
        "com.jago",

        // Bank Mandiri (Livin')
        "id.co.bankmandiri.livin",
        "com.bankmandiri.livin",

        // BCA (BCA mobile, myBCA)
        "com.bca",
        "com.bca.mobile",
        "com.bca.mybca",
        "id.co.bca.mybca",

        // BRI (BRImo)
        "id.co.bri.brimo",

        // BNI (BNI Mobile Banking & wondr by BNI)
        "com.bni",
        "co.id.bni.wondr",

        // Blu by BCA Digital
        "com.bcadigital.blu",

        // Jenius
        "com.btpn.jenius",
        "com.btpn.sinaya",

        // SeaBank
        "com.seabank.seabankid",
        "com.seabank.id",

        // CIMB Niaga (OCTO Mobile)
        "id.co.cimbniaga.mobile.android",
        "id.co.cimbniaga.octomobile",

        // Flip
        "id.co.flip",

        // Gojek / GoPay
        "com.gojek.app",
        "com.gojek.gopay",

        // Shopee / ShopeePay
        "com.shopee.id",

        // DANA
        "id.dana",

        // OVO
        "ovo.id",

        // LinkAja
        "com.telkom.mwallet",

        // Tokopedia
        "com.tokopedia.tkpd",

        // Blibli
        "blibli.mobile.commerce",

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
