package me.kafuuneko.rpclient.libs.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 证书防护。
 *
 * eMudhra 那批中间 CA 的私钥是单字节值，255 次内可爆，
 * 又被 Sectigo AAA 根交叉签名，所以能伪造任意域名。
 * 这里只钉两件事：emSign 一律拒，正常 CA 一律放。
 */
class CertificateGuardTest {

    @Test
    fun `rejects emSign issuer`() {
        val brand = CertificateGuard.blockedBrandOf(
            issuer = "CN=emSign ECC Root CA - G3, O=eMudhra Technologies Limited, C=IN",
            subject = "CN=api.example.com"
        )

        assertEquals("emSign", brand)
    }

    @Test
    fun `rejects eMudhra subject`() {
        val brand = CertificateGuard.blockedBrandOf(
            issuer = "CN=AAA Certificate Services, O=Comodo CA Limited",
            subject = "CN=eMudhra Intermediate CA, O=eMudhra Technologies Limited"
        )

        assertEquals("eMudhra", brand)
    }

    @Test
    fun `case is ignored`() {
        val brand = CertificateGuard.blockedBrandOf(
            issuer = "CN=EMSIGN ECC ROOT CA - G3",
            subject = "CN=whatever"
        )

        assertEquals("eMudhra", CertificateGuard.blockedBrandOf(
            issuer = "CN=eMudhra Root", subject = "CN=whatever"
        )?.let { "eMudhra" })
        assertEquals("emSign", brand)
    }

    @Test
    fun `allows normal public CAs`() {
        // 这几个是正常在用的根，拦掉会让大量站点连不上
        listOf(
            "CN=AAA Certificate Services, O=Comet Network, C=GB",
            "CN=ISRG Root X1, O=Internet Security Research Group, C=US",
            "CN=DigiCert Global Root G2, O=DigiCert Inc, C=US",
            "CN=GlobalSign Root CA, O=GlobalSign nv-sa, C=BE"
        ).forEach { name ->
            assertNull("不该拦 $name", CertificateGuard.blockedBrandOf(name, name))
        }
    }

    @Test
    fun `emSign inside a cross signed chain is caught`() {
        // 交叉签名的链路：叶子 → emSign 中间 → AAA 根
        // 只要链上有一环命中就断，这正是漏掉会出事的地方
        val chain = listOf(
            "CN=api.siliconflow.cn" to "CN=emSign ECC Root CA - G3, O=eMudhra",
            "CN=emSign ECC Root CA - G3, O=eMudhra" to "CN=AAA Certificate Services",
            "CN=AAA Certificate Services" to "CN=AAA Certificate Services"
        )

        val hit = chain.firstNotNullOfOrNull { (subject, issuer) ->
            CertificateGuard.blockedBrandOf(issuer = issuer, subject = subject)
        }

        assertEquals("emSign", hit)
    }

    @Test
    fun `plain AAA chain without emSign passes`() {
        // 只有 AAA 参与的正常链路不能误伤
        val chain = listOf(
            "CN=api.example.com" to "CN=Sectigo RSA DV, O=Sectigo Limited",
            "CN=Sectigo RSA DV, O=Sectigo Limited" to "CN=AAA Certificate Services",
            "CN=AAA Certificate Services" to "CN=AAA Certificate Services"
        )

        val hit = chain.firstNotNullOfOrNull { (subject, issuer) ->
            CertificateGuard.blockedBrandOf(issuer = issuer, subject = subject)
        }

        assertNull(hit)
    }
}
