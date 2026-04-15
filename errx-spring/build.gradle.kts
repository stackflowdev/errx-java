plugins {
    java
}

dependencies {
    implementation(project(":errx-core"))

    compileOnly("org.springframework.boot:spring-boot-starter-web:3.4.1")
    compileOnly("org.springframework.boot:spring-boot-starter-validation:3.4.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.springframework.boot:spring-boot-starter-web:3.4.1")
    testImplementation("org.springframework.boot:spring-boot-starter-validation:3.4.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.4.1")
}
