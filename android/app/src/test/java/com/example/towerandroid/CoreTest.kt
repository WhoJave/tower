package com.example.towerandroid

import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    private val id = "550e8400-e29b-41d4-a716-446655440000"
    private fun b64(s: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray(StandardCharsets.UTF_8))
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: Core.ParseException) { }
    }

    @Test fun parsesShadowsocksVariants() {
        val user = b64("aes-128-gcm:p@ss")
        val node = Core.parseUri("ss://$user@example.com:443#A%20B")
        assertEquals("p@ss", node.secret)
        assertEquals("A B", node.name)
        assertEquals("aes-128-gcm", node.cipher)
        assertEquals(node, Core.parseUri("ss://${b64("aes-128-gcm:p@ss@example.com:443")}#A%20B"))
        assertEquals("p+q", Core.parseUri("ss://aes-256-gcm:p+q@example.com:80").secret)
    }

    @Test fun parsesTlsAndBase64Subscription() {
        val lines = "trojan://pass@example.com:443?sni=tls.example#T\nvless://$id@example.net:443?security=tls&type=tcp&sni=front.example#V"
        val nodes = Core.parseSubscription(b64(lines))
        assertEquals(2, nodes.size)
        val yaml = Core.exportYaml(nodes)
        assertTrue(yaml.contains("    sni: \"tls.example\""))
        assertTrue(yaml.contains("    servername: \"front.example\""))
        assertTrue(yaml.contains("    tls: true"))
        assertTrue(yaml.contains("    uuid: \"$id\""))
    }

    @Test fun escapesYamlMetacharacters() {
        val n = Core.parseUri("trojan://p%22%5C%23@example.com:443#%22%3A%20%23%20evil")
        val yaml = Core.exportYaml(listOf(n))
        assertTrue(yaml.contains("name: \"\\\": # evil\""))
        assertTrue(yaml.contains("password: \"p\\\"\\\\#\""))
        assertEquals(1, yaml.lines().count { it.startsWith("  - name:") })
    }

    @Test fun rejectsUnsupportedOrSecuritySensitiveOptions() {
        for (u in listOf(
            "trojan://p@example.com:443?allowInsecure=1",
            "trojan://p@example.com:443?type=ws",
            "ss://aes-128-gcm:p@example.com:443?plugin=obfs-local",
            "vless://$id@example.com:443?security=reality&pbk=x",
            "vless://$id@example.com:443?security=none",
            "vless://$id@example.com:443?security=tls&type=ws",
            "vless://$id@example.com:443?security=tls&flow=xtls-rprx-vision",
            "vless://$id@example.com:443?security=tls&sni=a&sni=b",
            "trojan://p@example.com:443/path",
            "ss://rc4:p@example.com:443"
        )) rejects { Core.parseUri(u) }
    }

    @Test fun rejectsBadInputAndPartialSubscriptions() {
        for (u in listOf("trojan://p@example.com:0", "trojan://p@example.com:65536", "trojan://p@example.com:443#%FF", "trojan://p@example.com:443#x%0Ay", "vless://bad@example.com:443?security=tls", "trojan://p@example.com:443?", "trojan://p@example.com:443?x=1")) rejects { Core.parseUri(u) }
        rejects { Core.parseSubscription("trojan://p@example.com:443#same\ntrojan://p@other.com:443#same") }
        rejects { Core.parseSubscription("trojan://p@example.com:443\nhttps://example.com") }
        rejects { Core.parseSubscription("%%%") }
    }

    @Test fun preservesLegacyPasswordsAndUnicode() {
        val legacy = "aes-128-gcm:p@ss%20@example.com:443"
        val node = Core.parseUri("ss://${b64(legacy)}#%F0%9F%9A%80")
        assertEquals("p@ss%20", node.secret)
        assertEquals("🚀", node.name)
        assertTrue(Core.exportYaml(listOf(node)).contains("name: \"🚀\""))
        rejects { Core.parseUri("trojan://p@example.com:443#\uD800") }
        rejects { Core.exportYaml(listOf(node.copy(name = "\uD800"))) }
    }

    @Test fun validatesIpv6WithoutDns() {
        val node = Core.parseUri("trojan://p@[2001:db8::1]:443")
        assertEquals("2001:db8::1", node.server)
        assertTrue(Core.exportYaml(listOf(node)).contains("server: \"2001:db8::1\""))
        rejects { Core.exportYaml(listOf(node.copy(server = "not:ipv6"))) }
        rejects { Core.exportYaml(listOf(node.copy(sni = "1::2::3"))) }
    }

    @Test fun validatesNodesSuppliedDirectly() {
        val valid = Core.parseUri("trojan://p@example.com:443")
        rejects { Core.exportYaml(listOf(valid.copy(name = "x\nproxies:"))) }
        rejects { Core.exportYaml(listOf(valid.copy(server = "example.com\nother: true"))) }
        rejects { Core.exportYaml(listOf(valid.copy(cipher = "ignored"))) }
        rejects { Core.exportYaml(listOf(valid, valid)) }
    }
}
