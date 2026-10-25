package com.ecommerce.catalog.infrastructure;

import java.time.Duration;

import com.ecommerce.catalog.domain.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PRODUCTS_CACHE = "catalog.products";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory cf,
                                          ObjectMapper mapper,
                                          @Value("${catalog.cache.ttl:PT5M}") Duration ttl,
                                          @Value("${spring.cache.redis.key-prefix:}") String keyPrefix) {
        // Declaring this bean switches off Boot's cache auto-configuration, so the
        // spring.cache.redis key prefix has to be applied here by hand.
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .prefixCacheNameWith(keyPrefix)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(mapper)));

        // The JSON above carries no type information, so a hit would come back as
        // a LinkedHashMap. The products cache reads it back as a Product explicitly.
        RedisCacheConfiguration products = base.serializeValuesWith(
                RedisSerializationContext.SerializationPair
                        .fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, Product.class)));

        return RedisCacheManager.builder(cf)
                .cacheDefaults(base)
                .withCacheConfiguration(PRODUCTS_CACHE, products)
                .build();
    }
}
