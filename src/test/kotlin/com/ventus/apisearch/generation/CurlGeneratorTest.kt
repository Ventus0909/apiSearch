package com.ventus.apisearch.generation

import com.ventus.apisearch.model.ApiEndpoint
import com.ventus.apisearch.model.Framework
import org.junit.Assert.assertEquals
import org.junit.Test

class CurlGeneratorTest {

    private fun endpoint(method: String = "PUT"): ApiEndpoint = ApiEndpoint(
        framework = Framework.SPRING,
        httpMethod = method,
        path = "/api/update/{id}",
        className = "DemoController",
        methodName = "update",
        comment = "",
        filePath = "/src/DemoController.java",
        offset = 0,
    )

    @Test
    fun `generates postman compatible curl`() {
        val curl = CurlGenerator.build(
            endpoint(),
            "http://localhost:8080/api/update/{id}",
            RequestSpec(
                bodyJson = "{\"name\":\"\"}",
                query = listOf("force" to "false"),
                pathVariables = mapOf("id" to "1"),
            ),
        )
        assertEquals(
            "curl -X PUT 'http://localhost:8080/api/update/1?force=false' " +
                "-H 'Content-Type: application/json' -d '{\"name\":\"\"}'",
            curl,
        )
    }

    @Test
    fun `uses GET when method is ANY`() {
        val curl = CurlGenerator.build(endpoint("ANY"), "http://localhost:8080/api", RequestSpec())
        assertEquals("curl -X GET 'http://localhost:8080/api'", curl)
    }

    @Test
    fun `escapes single quotes`() {
        val curl = CurlGenerator.build(endpoint("POST"), "http://localhost/a", RequestSpec("a'b"))
        assertEquals(
            "curl -X POST 'http://localhost/a' -H 'Content-Type: application/json' -d 'a'\\''b'",
            curl,
        )
    }
}
