package com.example.data.repository

import com.example.data.local.CatalogItemDao
import com.example.data.local.ExpenseDao
import com.example.data.local.OrderDao
import com.example.data.local.UserDao
import com.example.data.local.AppConfigDao
import com.example.data.model.CatalogItem
import com.example.data.model.Expense
import com.example.data.model.Order
import com.example.data.model.User
import com.example.data.model.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class LaundryRepository(
    private val catalogItemDao: CatalogItemDao,
    private val orderDao: OrderDao,
    private val expenseDao: ExpenseDao,
    private val userDao: UserDao,
    private val appConfigDao: AppConfigDao
) {
    val catalogItems: Flow<List<CatalogItem>> = catalogItemDao.getAllCatalogItems()
    val orders: Flow<List<Order>> = orderDao.getAllOrders()
    val expenses: Flow<List<Expense>> = expenseDao.getAllExpenses()

    suspend fun insertCatalogItem(item: CatalogItem) {
        catalogItemDao.insertCatalogItem(item)
    }

    suspend fun updateCatalogItem(item: CatalogItem) {
        catalogItemDao.updateCatalogItem(item)
    }

    suspend fun deleteCatalogItem(item: CatalogItem) {
        catalogItemDao.deleteCatalogItem(item)
    }

    suspend fun insertOrder(order: Order): Long {
        return orderDao.insertOrder(order)
    }

    suspend fun updateOrder(order: Order) {
        orderDao.updateOrder(order)
    }

    suspend fun deleteOrder(order: Order) {
        orderDao.deleteOrder(order)
    }

    suspend fun insertExpense(expense: Expense): Long {
        return expenseDao.insertExpense(expense)
    }

    suspend fun updateExpense(expense: Expense) {
        expenseDao.updateExpense(expense)
    }

    suspend fun deleteExpense(expense: Expense) {
        expenseDao.deleteExpense(expense)
    }

    suspend fun checkAndPrepopulateCatalog() {
        val currentItems = catalogItemDao.getAllCatalogItems().first()
        if (currentItems.isEmpty()) {
            val defaults = listOf(
                CatalogItem(name = "Camisa", price = 2.00),
                CatalogItem(name = "Pantalón", price = 3.00),
                CatalogItem(name = "Chaqueta", price = 5.00),
                CatalogItem(name = "Manta", price = 10.00),
                CatalogItem(name = "Vestido", price = 6.00),
                CatalogItem(name = "Traje", price = 15.00)
            )
            catalogItemDao.insertAll(defaults)
        }
    }

    suspend fun wipeAllForToday(resetCatalog: Boolean) {
        orderDao.clearOrders()
        expenseDao.clearExpenses()
        
        if (resetCatalog) {
            catalogItemDao.clearCatalogItems()
            val defaults = listOf(
                CatalogItem(name = "Camisa", price = 2.00),
                CatalogItem(name = "Pantalón", price = 3.00),
                CatalogItem(name = "Chaqueta", price = 5.00),
                CatalogItem(name = "Manta", price = 10.00),
                CatalogItem(name = "Vestido", price = 6.00),
                CatalogItem(name = "Traje", price = 15.00)
            )
            catalogItemDao.insertAll(defaults)
        }
    }

    // User Operations
    suspend fun getAllUsers(): List<User> = userDao.getAllUsers()
    fun getAllUsersFlow(): Flow<List<User>> = userDao.getAllUsersFlow()
    suspend fun getUserByUsername(username: String): User? = userDao.getUserByUsername(username)
    suspend fun insertUser(user: User): Long = userDao.insertUser(user)
    suspend fun clearUsers() = userDao.clearUsers()

    // Config Operations
    suspend fun getConfig(key: String): String? = appConfigDao.getConfigByKey(key)?.value
    suspend fun setConfig(key: String, value: String) {
        appConfigDao.setConfig(AppConfig(key, value))
    }
}
