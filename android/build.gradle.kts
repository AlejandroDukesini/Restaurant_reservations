// Proyecto Android independiente del backend (que es Java + Spring Boot en la raiz del repo).

// Parches para el classpath de los plugins (no llega al APK): AGP 8.13.2 fija Netty 4.1.110,
// BouncyCastle 1.79, commons-compress 1.21, jose4j 0.9.5 y jdom2 2.0.6, con avisos publicados.
// Quitar cuando una version de AGP ya traiga estas versiones o posteriores.
buildscript {
    dependencies {
        classpath(platform("io.netty:netty-bom:4.1.139.Final"))
        constraints {
            classpath("org.bouncycastle:bcprov-jdk18on:1.85")
            classpath("org.bouncycastle:bcpkix-jdk18on:1.85")
            classpath("org.bouncycastle:bcutil-jdk18on:1.85")
            classpath("org.apache.commons:commons-compress:1.26.2")
            classpath("org.apache.commons:commons-lang3:3.18.0")
            classpath("org.bitbucket.b_c:jose4j:0.9.6")
            classpath("org.jdom:jdom2:2.0.6.1")
        }
    }
}

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
}
