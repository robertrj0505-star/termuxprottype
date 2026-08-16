package com.example.test.util

/**
 * Example API Service interface for reference in tests.
 * Replace with your actual API service definition.
 */
interface ApiService {
    suspend fun getUser(id: String): TestDataBuilder.TestUser
    suspend fun getUsers(): List<TestDataBuilder.TestUser>
    suspend fun createUser(user: TestDataBuilder.TestUser): TestDataBuilder.TestUser
}
