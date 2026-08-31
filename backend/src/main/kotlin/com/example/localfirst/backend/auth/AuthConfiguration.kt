package com.example.localfirst.backend.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@Configuration(proxyBeanMethods = false)
class AuthConfiguration {
    @Bean fun authClock(): Clock = Clock.systemUTC()
    @Bean fun objectMapper(): ObjectMapper = jacksonObjectMapper().findAndRegisterModules()
    @Bean fun passwordHasher(): PasswordHasher {
        val encoder = BCryptPasswordEncoder(12)
        return object : PasswordHasher {
            override fun hash(value: String): String = requireNotNull(encoder.encode(value))
            override fun matches(value: String, hash: String) = encoder.matches(value, hash)
        }
    }
}
