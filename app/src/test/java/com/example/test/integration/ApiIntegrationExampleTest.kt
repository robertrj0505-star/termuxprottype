package com.example.test.integration

import com.example.test.util.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import com.google.common.truth.Truth.assertThat

/**
 * Example Integration Test demonstrating how to use the test utilities
 * for mocking backend API responses and testing Retrofit clients.
 *
 * This test class shows best practices for:
 * - Setting up mock server responses
 * - Creating test data fixtures
 * - Asserting request/response behavior
 */
class ApiIntegrationExampleTest {
    
    @get:Rule
    val mockServerRule = MockWebServerRule()

    @Test
    fun testGetUserSuccess() {
        // Arrange: Create test data and mock response
        val testUser = TestDataBuilder.createUser(
            id = "123",
            name = "John Doe",
            email = "john@example.com"
        )
        val responseJson = """
            {
                "id": "${testUser.id}",
                "name": "${testUser.name}",
                "email": "${testUser.email}"
            }
        """.trimIndent()
        
        mockServerRule.enqueueResponse(200, responseJson)
        
        // Act: Create service and make request
        val apiService = mockServerRule.createService(ApiService::class.java)
        // Note: In a real test, you'd call the actual API method
        // val result = apiService.getUser(testUser.id)
        
        // Assert: Verify the response
        mockServerRule.assertRequestMade("/user/123", "GET")
    }

    @Test
    fun testUserNotFound() {
        // Arrange: Queue error response
        mockServerRule.enqueueErrorResponse(404, "User not found")
        
        // Act & Assert
        val apiService = mockServerRule.createService(ApiService::class.java)
        mockServerRule.assertRequestMade("/users", "GET")
    }

    @Test
    fun testCreateUserSuccess() {
        // Arrange: Create test user and mock response
        val newUser = TestDataBuilder.createUser(
            name = "Jane Doe",
            email = "jane@example.com"
        )
        val responseJson = """
            {
                "id": "456",
                "name": "${newUser.name}",
                "email": "${newUser.email}"
            }
        """.trimIndent()
        
        mockServerRule.enqueueResponse(201, responseJson)
        
        // Act
        val apiService = mockServerRule.createService(ApiService::class.java)
        // val result = apiService.createUser(newUser)
        
        // Assert
        mockServerRule.assertRequestMade("/users", "POST")
    }

    @Test
    fun testGetMultipleUsers() {
        // Arrange: Create multiple test users
        val users = TestDataBuilder.createUserList(count = 3)
        val responseJson = "[" + users.joinToString(",") { user ->
            """
                {
                    "id": "${user.id}",
                    "name": "${user.name}",
                    "email": "${user.email}"
                }
            """.trimIndent()
        } + "]"
        
        mockServerRule.enqueueResponse(200, responseJson)
        
        // Act
        val apiService = mockServerRule.createService(ApiService::class.java)
        
        // Assert
        assertThat(users).hasSize(3)
        mockServerRule.assertRequestMade("/users", "GET")
    }

    @Test
    fun testSlowResponseHandling() {
        // Arrange: Mock a delayed response (e.g., 2 second delay)
        mockServerRule.enqueueDelayedResponse(
            statusCode = 200,
            body = """{"status": "processing"}""",
            delayMs = 2000
        )
        
        // Act & Assert
        val apiService = mockServerRule.createService(ApiService::class.java)
        // In a real test, verify timeout handling
    }
}
