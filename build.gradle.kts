plugins { java }

repositories { mavenCentral() }

val agentVersion = providers.gradleProperty("agentVersion").orElse("2.33.1")
val agent = configurations.create("agent") { isTransitive = false }
val exclusion = sourceSets.create("exclusion")
val withExclusion = providers.gradleProperty("withExclusion").isPresent

dependencies {
    implementation("io.opentelemetry:opentelemetry-api:1.67.0")
    add(agent.name, "io.opentelemetry.javaagent:opentelemetry-javaagent:${agentVersion.get()}")
    add(
        exclusion.compileOnlyConfigurationName,
        "io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api:${agentVersion.get()}-alpha",
    )
}

val exclusionJar = tasks.register<Jar>("exclusionJar") {
    archiveFileName.set("ignore-agent-wrapper.jar")
    from(exclusion.output)
}

for (mode in listOf("direct", "single")) {
    tasks.register<JavaExec>(mode) {
        dependsOn(tasks.classes)
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("Repro")
        args(mode)
        systemProperty("otel.traces.exporter", "none")
        systemProperty("otel.metrics.exporter", "none")
        systemProperty("otel.logs.exporter", "none")
        doFirst { jvmArgs("-javaagent:${agent.singleFile.absolutePath}") }
        if (withExclusion) {
            dependsOn(exclusionJar)
            doFirst {
                jvmArgs("-Dotel.javaagent.extensions=${exclusionJar.get().archiveFile.get().asFile.absolutePath}")
            }
        }
    }
}

tasks.register("reproduce") { dependsOn("direct", "single") }
