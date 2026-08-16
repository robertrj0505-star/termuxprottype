package com.example.test.util

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * MockWebServerRule is a JUnit Rule that manages the lifecycle of MockWebServer
 * for unit tests. It automatically starts before tests and shuts down after.
 *
 * Usage in test class:
 * @get:Rule
 * val mockServerRule = MockWebServerRule()
 *
 * fun myTest() {
 *     mockServerRule.enqueueResponse(200, """{"result": "ok"}""")
 *     val service = mockServerRule.createService(MyApiService::class.java)
 *     // test code
 * }
 */
class MockWebServerRule : TestRule {
    private val mockServerManager = MockWebServerManager()

    override fun apply(base: Statement?, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                try {
                    base?.evaluate()
                } finally {
                    mockServerManager.shutdown()
                }
            }
        }
    }

    fun enqueueResponse(statusCode: Int, body: String) {
        mockServerManager.enqueueResponse(statusCode, body)
    }

    fun enqueueDelayedResponse(statusCode: Int, body: String, delayMs: Long) {
        mockServerManager.enqueueDelayedResponse(statusCode, body, delayMs)
    }

    fun enqueueErrorResponse(statusCode: Int, errorMessage: String = "") {
        mockServerManager.enqueueErrorResponse(statusCode, errorMessage)
    }

    fun getBaseUrl(): String = mockServerManager.getBaseUrl()

    fun <T> createService(serviceClass: Class<T>): T {
        return mockServerManager.createService(serviceClass)
    }

    fun assertRequestMade(path: String, method: String = "GET") {
        mockServerManager.assertRequestMade(path, method)
    }
}
