package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.Order
import com.example.data.model.SelectedItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("LaundryManager", appName)
  }

  @Test
  fun testOrderInsertion() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = AppDatabase.getDatabase(context)
    val orderDao = database.orderDao()

    val selectedItems = listOf(
        SelectedItem(id = 1, name = "Shirt", price = 2.00, quantity = 3),
        SelectedItem(id = 2, name = "Pants", price = 3.00, quantity = 2)
    )

    val order = Order(
        customerName = "John Doe",
        customerPhone = "123456789",
        status = "Received",
        totalAmount = 12.00,
        items = selectedItems
    )

    val insertId = orderDao.insertOrder(order)
    assert(insertId > 0)

    val ordersList = orderDao.getAllOrders().first()
    assertEquals(1, ordersList.size)
    val savedOrder = ordersList[0]
    assertEquals("John Doe", savedOrder.customerName)
    assertEquals(2, savedOrder.items.size)
    assertEquals("Shirt", savedOrder.items[0].name)
  }
}
