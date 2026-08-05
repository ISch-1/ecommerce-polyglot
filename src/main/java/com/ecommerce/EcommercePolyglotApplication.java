package com.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point. {@link Modulithic} tells Spring Modulith to enforce the module
 * boundaries declared in each context's {@code package-info.java}.
 */
@SpringBootApplication
@EnableScheduling
@Modulithic(systemName = "ecommerce-polyglot")
public class EcommercePolyglotApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcommercePolyglotApplication.class, args);
    }
}
