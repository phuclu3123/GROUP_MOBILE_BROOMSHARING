plugins {
    application
}

repositories { mavenCentral() }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
// Giữ đúng comment và chuỗi tiếng Việt trên cả Windows lẫn Linux.
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
dependencies {
    implementation("com.google.firebase:firebase-admin:9.5.0")
    implementation("vn.payos:payos-java:2.0.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    testImplementation("junit:junit:4.13.2")
}
application { mainClass = "vn.edu.ueh.broomsharing.payment.PaymentServer" }
tasks.test { testLogging { events("failed", "skipped") } }
tasks.register<JavaExec>("liveProviderCheck") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "vn.edu.ueh.broomsharing.payment.LiveProviderCheck"
    workingDir = projectDir
}
tasks.register<JavaExec>("verifyRelay") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "vn.edu.ueh.broomsharing.payment.RelayAcceptanceCheck"
    workingDir = projectDir
}
