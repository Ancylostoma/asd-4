package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.CatalogItem
import com.example.data.model.Expense
import com.example.data.model.Order
import com.example.data.model.User
import com.example.data.model.AppConfig
import com.example.data.repository.LaundryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class LaundryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = LaundryRepository(
        database.catalogItemDao(),
        database.orderDao(),
        database.expenseDao(),
        database.userDao(),
        database.appConfigDao()
    )

    val catalogItems: StateFlow<List<CatalogItem>> = repository.catalogItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val orders: StateFlow<List<Order>> = repository.orders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val expenses: StateFlow<List<Expense>> = repository.expenses
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Security & Auth States
    private val _loggedInUser = MutableStateFlow<User?>(null)
    val loggedInUser = _loggedInUser.asStateFlow()

    private val _hasAdminAccount = MutableStateFlow<Boolean>(true)
    val hasAdminAccount = _hasAdminAccount.asStateFlow()

    // Licensing States
    private val _isLicensed = MutableStateFlow<Boolean>(false)
    val isLicensed = _isLicensed.asStateFlow()

    private val _isTrialExpired = MutableStateFlow<Boolean>(false)
    val isTrialExpired = _isTrialExpired.asStateFlow()

    private val _installationDateStr = MutableStateFlow<String>("")
    val installationDateStr = _installationDateStr.asStateFlow()

    private val _daysLeft = MutableStateFlow<Int>(30)
    val daysLeft = _daysLeft.asStateFlow()

    // Monthly Subscription States
    private val subscriptionPrefs = application.getSharedPreferences("monthly_subscription_prefs", Context.MODE_PRIVATE)

    private val _isSubscriptionExpired = MutableStateFlow<Boolean>(false)
    val isSubscriptionExpired = _isSubscriptionExpired.asStateFlow()

    private val _storedSubscriptionMonth = MutableStateFlow<Int>(1)
    val storedSubscriptionMonth = _storedSubscriptionMonth.asStateFlow()

    private val _storedSubscriptionYear = MutableStateFlow<Int>(2026)
    val storedSubscriptionYear = _storedSubscriptionYear.asStateFlow()

    init {
        // Initialize monthly subscription tracking first
        checkMonthlySubscription()
        
        viewModelScope.launch {
            // Prepopulate catalog defaults
            repository.checkAndPrepopulateCatalog()

            refreshLicensingAndAuth()
        }
    }

    fun checkMonthlySubscription() {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH) + 1 // 1-12
        val currentYear = calendar.get(Calendar.YEAR)

        // Initialize at install time with current Month and Year (unlocked)
        if (!subscriptionPrefs.contains("active_license_month")) {
            subscriptionPrefs.edit()
                .putInt("active_license_month", currentMonth)
                .putInt("active_license_year", currentYear)
                .apply()
        }

        val storedMonth = subscriptionPrefs.getInt("active_license_month", currentMonth)
        val storedYear = subscriptionPrefs.getInt("active_license_year", currentYear)

        _storedSubscriptionMonth.value = storedMonth
        _storedSubscriptionYear.value = storedYear

        // Strict monthly subscription automatic lock:
        // Lock if current calendar month/year are greater than the stored license variables
        val isExpired = currentYear > storedYear || (currentYear == storedYear && currentMonth > storedMonth)
        _isSubscriptionExpired.value = isExpired
    }

    fun getExpectedSubscriptionCodeForCurrentMonth(): String {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val currentYear = calendar.get(Calendar.YEAR)
        
        // Offline Deterministic formula: Secret Concatenation & Multiplicative Check matching month/year
        // Uses month, year, and a modular check derived from developer passphrase logic
        val passcodeValue = (currentMonth * 137 + currentYear * 421) % 9000 + 1000
        return "ACT-$currentMonth-$currentYear-$passcodeValue"
    }

    fun unlockSubscription(code: String, onResult: (Boolean, String?) -> Unit) {
        val expected = getExpectedSubscriptionCodeForCurrentMonth()
        val enteredCode = code.trim().uppercase()
        if (enteredCode == expected || enteredCode == "LAUNDRY-PRO-2026") {
            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH) + 1
            val currentYear = calendar.get(Calendar.YEAR)

            subscriptionPrefs.edit()
                .putInt("active_license_month", currentMonth)
                .putInt("active_license_year", currentYear)
                .apply()

            checkMonthlySubscription()
            onResult(true, null)
        } else {
            onResult(false, "Código de activación incorrecto. Por favor comuníquese con el desarrollador.")
        }
    }

    fun simulateSubscriptionExpiration() {
        val calendar = Calendar.getInstance()
        // Subtract 1 month to put the stored license in the past relative to today
        calendar.add(Calendar.MONTH, -1)
        val lastMonth = calendar.get(Calendar.MONTH) + 1
        val lastYear = calendar.get(Calendar.YEAR)

        subscriptionPrefs.edit()
            .putInt("active_license_month", lastMonth)
            .putInt("active_license_year", lastYear)
            .apply()

        checkMonthlySubscription()
    }

    fun simulateSubscriptionReset() {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val currentYear = calendar.get(Calendar.YEAR)

        subscriptionPrefs.edit()
            .putInt("active_license_month", currentMonth)
            .putInt("active_license_year", currentYear)
            .apply()

        checkMonthlySubscription()
    }

    suspend fun refreshLicensingAndAuth() {
        // 1. Check if ANY Admin exists
        val allUsers = repository.getAllUsers()
        val admins = allUsers.filter { it.role == "Admin" }
        _hasAdminAccount.value = admins.isNotEmpty()

        // 2. Check installation timestamp
        var instDateStr = repository.getConfig("installation_date")
        if (instDateStr == null) {
            val timestamp = System.currentTimeMillis()
            repository.setConfig("installation_date", timestamp.toString())
            instDateStr = timestamp.toString()
        }
        _installationDateStr.value = instDateStr

        // 3. Check licensing status
        var licenseStatus = repository.getConfig("license_status")
        if (licenseStatus == null) {
            repository.setConfig("license_status", "trial")
            licenseStatus = "trial"
        }
        _isLicensed.value = (licenseStatus == "licensed")

        // 4. Calculate trial days
        val instTime = instDateStr.toLongOrNull() ?: System.currentTimeMillis()
        val timeDiff = System.currentTimeMillis() - instTime
        val daysElapsed = (timeDiff / (1000 * 60 * 60 * 24)).toInt()
        val daysRemaining = (30 - daysElapsed).coerceIn(0, 30)
        _daysLeft.value = daysRemaining

        if (licenseStatus != "licensed" && daysElapsed > 30) {
            _isTrialExpired.value = true
        } else {
            _isTrialExpired.value = false
        }
    }

    // User Operations
    fun login(usernameInput: String, passwordInput: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val user = repository.getUserByUsername(usernameInput)
            if (user != null && user.password == passwordInput) {
                _loggedInUser.value = user
                onResult(true, null)
            } else {
                onResult(false, "Nombre de usuario o contraseña incorrectos")
            }
        }
    }

    fun registerAdmin(usernameInput: String, passwordInput: String) {
        viewModelScope.launch {
            val admin = User(username = usernameInput, password = passwordInput, role = "Admin")
            repository.insertUser(admin)
            _hasAdminAccount.value = true
            _loggedInUser.value = admin
        }
    }

    fun registerWorker(usernameInput: String, passwordInput: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val existing = repository.getUserByUsername(usernameInput)
            if (existing != null) {
                onResult(false, "El nombre de usuario ya existe")
                return@launch
            }
            val worker = User(username = usernameInput, password = passwordInput, role = "Worker")
            repository.insertUser(worker)
            onResult(true, null)
        }
    }

    fun logout() {
        _loggedInUser.value = null
    }

    // Trial Activation
    fun unlockApp(code: String, onResult: (Boolean) -> Unit) {
        val calendar = java.util.Calendar.getInstance()
        val currentMonthNum = calendar.get(java.util.Calendar.MONTH) + 1
        val currentYearNum = calendar.get(java.util.Calendar.YEAR)
        val passcodeValue = (currentMonthNum * 137 + currentYearNum * 421) % 9000 + 1000
        val monthlyCode = "ACT-$currentMonthNum-$currentYearNum-$passcodeValue"

        val enteredCode = code.trim().uppercase()
        val isValid = (enteredCode == "LAUNDRY-PRO-2026" || enteredCode == monthlyCode)

        if (isValid) {
            viewModelScope.launch {
                repository.setConfig("license_status", "licensed")
                _isLicensed.value = true
                _isTrialExpired.value = false
                onResult(true)
            }
        } else {
            onResult(false)
        }
    }

    // Dev helper for quick testing
    fun simulateTrialExpiration() {
        viewModelScope.launch {
            // Set installation timestamp 32 days back
            val fakeTimestamp = System.currentTimeMillis() - (32L * 24 * 60 * 60 * 1000)
            repository.setConfig("installation_date", fakeTimestamp.toString())
            repository.setConfig("license_status", "trial")
            refreshLicensingAndAuth()
        }
    }

    fun simulateTrialReset() {
        viewModelScope.launch {
            // Reset to today
            repository.setConfig("installation_date", System.currentTimeMillis().toString())
            repository.setConfig("license_status", "trial")
            refreshLicensingAndAuth()
        }
    }

    // Catalog Operations
    fun addCatalogItem(name: String, price: Double) {
        viewModelScope.launch {
            repository.insertCatalogItem(CatalogItem(name = name, price = price))
        }
    }

    fun updateCatalogItem(item: CatalogItem) {
        viewModelScope.launch {
            repository.updateCatalogItem(item)
        }
    }

    fun deleteCatalogItem(item: CatalogItem) {
        viewModelScope.launch {
            repository.deleteCatalogItem(item)
        }
    }

    // Order Operations
    fun createOrder(customerName: String, customerPhone: String, items: List<com.example.data.model.SelectedItem>, total: Double) {
        viewModelScope.launch {
            val order = Order(
                customerName = customerName,
                customerPhone = customerPhone,
                status = "Received",
                totalAmount = total,
                items = items
            )
            repository.insertOrder(order)
        }
    }

    fun updateOrderStatus(order: Order, newStatus: String) {
        viewModelScope.launch {
            val updatedOrder = order.copy(
                status = newStatus,
                deliveredDate = if (newStatus == "Delivered") System.currentTimeMillis() else order.deliveredDate
            )
            repository.updateOrder(updatedOrder)
        }
    }

    fun deleteOrder(order: Order) {
        viewModelScope.launch {
            repository.deleteOrder(order)
        }
    }

    // Expense Operations
    fun addExpense(description: String, amount: Double) {
        viewModelScope.launch {
            repository.insertExpense(Expense(description = description, amount = amount))
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    // Daily Wipe
    fun wipeAllData(resetCatalog: Boolean) {
        viewModelScope.launch {
            repository.wipeAllForToday(resetCatalog)
        }
    }
}
