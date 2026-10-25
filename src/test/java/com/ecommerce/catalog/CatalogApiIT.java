package com.ecommerce.catalog;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.ecommerce.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * End-to-end integration test for the catalog.
 *
 * <p>Exercises the full write path:
 * REST → CatalogService → Postgres + outbox (same TX) → OutboxPublisher → Kafka.
 * Then exercises the read path with Redis cache: first read misses (loads
 * from Postgres, populates Redis), second read hits the cache.
 */
@DisplayName("Catalog API — end-to-end with Postgres + Redis + Kafka")
class CatalogApiIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper mapper;

    private Consumer<String, String> kafkaConsumer;

    @BeforeEach
    void setUp() {
        var props = KafkaTestUtils.consumerProps(KAFKA.getBootstrapServers(),
                "catalog-it-" + UUID.randomUUID(), "true");
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.put("auto.offset.reset", "earliest");
        kafkaConsumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(props);
        kafkaConsumer.subscribe(java.util.List.of("catalog.products"));
    }

    @AfterEach
    void tearDown() {
        if (kafkaConsumer != null) kafkaConsumer.close();
        // Drop the cache namespace between tests to keep them isolated.
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("POST /products persists, evicts cache, and publishes an event via the outbox")
    void createFlow() {
        Map<String, Object> body = Map.of(
                "sku", "SKU-" + UUID.randomUUID(),
                "name", "Ergonomic keyboard",
                "description", "Split, mechanical, hot-swappable",
                "price", "129.99",
                "stock", 50
        );

        ResponseEntity<JsonNode> res = rest.postForEntity("/api/v1/products", body, JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().get("id").asText()).isNotBlank();
        UUID id = UUID.fromString(res.getBody().get("id").asText());

        // 1. It lives in Postgres — a GET returns it.
        ResponseEntity<JsonNode> fetched = rest.getForEntity("/api/v1/products/" + id, JsonNode.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().get("price").decimalValue()).isEqualByComparingTo(new BigDecimal("129.99"));

        // 2. Redis got populated by the read path.
        Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(redis.keys("ecp:catalog.products::*"))
                        .anySatisfy(key -> assertThat(key).contains(id.toString()))
        );

        // 3. Kafka received this product's ProductCreated event via the outbox publisher.
        //    Other tests publish to the same topic and the consumer reads from the
        //    earliest offset, so match on the record key (the product id) instead
        //    of assuming the first message is ours.
        var received = new ConcurrentLinkedQueue<ConsumerRecord<String, String>>();
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            kafkaConsumer.poll(Duration.ofMillis(300)).forEach(received::add);
            assertThat(received).anySatisfy(record -> {
                assertThat(record.key()).isEqualTo(id.toString());
                assertThat(record.value()).contains("\"sku\"");
                assertThat(mapper.readTree(record.value()).get("id").asText()).isEqualTo(id.toString());
            });
        });
    }

    @Test
    @DisplayName("GET /products/{id} answers a cache hit with the same body as the miss")
    void cacheHitReturnsSameProduct() {
        UUID id = createSampleProduct();
        ResponseEntity<JsonNode> miss = rest.getForEntity("/api/v1/products/" + id, JsonNode.class);
        assertThat(redis.keys("ecp:catalog.products::*"))
                .anySatisfy(key -> assertThat(key).contains(id.toString()));

        ResponseEntity<JsonNode> hit = rest.getForEntity("/api/v1/products/" + id, JsonNode.class);
        assertThat(hit.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(hit.getBody()).isEqualTo(miss.getBody());
    }

    @Test
    @DisplayName("PUT /products/{id} evicts the cached snapshot")
    void updateEvictsCache() {
        // Given a product we've fetched once (cache populated)
        UUID id = createSampleProduct();
        rest.getForEntity("/api/v1/products/" + id, JsonNode.class);
        assertThat(redis.keys("ecp:catalog.products::*")).isNotEmpty();

        // When we update it
        var putBody = Map.of("price", "9999.00", "stock", 1);
        rest.put("/api/v1/products/" + id, putBody);

        // Then the cache key is gone
        assertThat(redis.keys("ecp:catalog.products::*"))
                .noneSatisfy(key -> assertThat(key).contains(id.toString()));

        // Next read reflects the update
        var after = rest.getForEntity("/api/v1/products/" + id, JsonNode.class);
        assertThat(after.getBody().get("price").decimalValue()).isEqualByComparingTo(new BigDecimal("9999.00"));
    }

    @Test
    @DisplayName("DELETE /products/{id} evicts and publishes ProductDeleted")
    void deleteFlow() {
        UUID id = createSampleProduct();
        assertThatCode(() -> rest.delete("/api/v1/products/" + id)).doesNotThrowAnyException();

        var res = rest.getForEntity("/api/v1/products/" + id, JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private UUID createSampleProduct() {
        var body = Map.of(
                "sku", "SKU-" + UUID.randomUUID(),
                "name", "Sample",
                "description", "",
                "price", "10.00",
                "stock", 100
        );
        ResponseEntity<JsonNode> res = rest.postForEntity("/api/v1/products", body, JsonNode.class);
        return UUID.fromString(res.getBody().get("id").asText());
    }
}
