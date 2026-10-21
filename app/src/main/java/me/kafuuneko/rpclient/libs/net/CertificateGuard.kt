package me.kafuuneko.rpclient.libs.net

import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * 拒绝已泄露的中转 CA 签发的证书。
 *
 * 背景：2025 年 eMudhra 的一批中间 CA 出了熵灾难——P-384 私钥实际是单字节值
 * （d = 4、56、115、230……），最多试 255 次就能从公钥反推私钥，耗时以毫秒计。
 * 那 8 张证书又被 Sectigo 的 AAA Certificate Services 根交叉签名过，
 * 于是任何拿到私钥的人都能为任意域名伪造一条「看起来正常」的证书链。
 *
 * 浏览器靠 CRLSet / OneCRL 早就封了，裸奔的是不查吊销的普通 TLS 客户端。
 * 这里在握手之后加一道链检查：只要链上出现 eMudhra / emSign 的签发或主体，
 * 直接断连。
 *
 * 刻意**不**拦截 Comodo AAA / Sectigo AAA 这条根——它是正常在用的公共根，
 * 拦掉会让大量站点连不上。只在链路里出现 emSign 时才拒。
 */
object CertificateGuard {

    /**
     * 不可信的名称片段，大小写不敏感。
     *
     * 这些品牌只属于 eMudhra，正常服务的证书链里不该出现。
     * emSign 排在 eMudhra 之前：含 emSign 的证书通常同时带着公司名，
     * 先匹配更具体的品牌，报错信息才不会指错对象。
     */
    private val BLOCKED_NAMES = listOf(
        "emSign",
        "eMudhra",
        "e-Mudhra"
    )

    /**
     * 判断一张证书是否该被拒绝。
     *
     * 抽成纯函数是为了能直接测，不必构造真实证书。
     *
     * @return 命中的品牌名；null 表示放行
     */
    fun blockedBrandOf(issuer: String, subject: String): String? =
        BLOCKED_NAMES.firstOrNull { name ->
            issuer.contains(name, ignoreCase = true) ||
                subject.contains(name, ignoreCase = true)
        }

    /**
     * 包装系统的 TrustManager。
     *
     * 先让系统完成它那一套校验，再逐张检查链上的名称——
     * 顺序不能反：系统的校验更完整，先放行再补刀，避免替它做判断。
     */
    fun wrap(delegate: X509TrustManager): X509TrustManager = object : X509TrustManager {

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            delegate.checkClientTrusted(chain, authType)
            rejectBlocked(chain)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            delegate.checkServerTrusted(chain, authType)
            rejectBlocked(chain)
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers

        private fun rejectBlocked(chain: Array<X509Certificate>) {
            chain.forEach { certificate ->
                val brand = blockedBrandOf(
                    issuer = certificate.issuerX500Principal.name,
                    subject = certificate.subjectX500Principal.name
                )
                if (brand != null) {
                    throw CertificateException("拒绝 $brand 签发的证书：该 CA 的私钥已泄露")
                }
            }
        }
    }
}
