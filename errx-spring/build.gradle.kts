plugins {
    java
}

dependencies {
    implementation(project(":errx-core"))

    compileOnly("org.springframework.boot:spring-boot-starter-web:3.4.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.4.1")
}
