package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CatalogItem
import com.example.data.model.Expense
import com.example.data.model.Order
import com.example.data.model.SelectedItem
import com.example.ui.viewmodel.LaundryViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale.US)
    return format.format(amount)
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: LaundryViewModel) {
    val isSubscriptionExpired by viewModel.isSubscriptionExpired.collectAsState()
    if (isSubscriptionExpired) {
        MonthlySubscriptionLockScreen(
            viewModel = viewModel,
            onUnlock = { code, callback ->
                viewModel.unlockSubscription(code, callback)
            }
        )
        return
    }

    val loggedInUser by viewModel.loggedInUser.collectAsState()
    val hasAdminAccount by viewModel.hasAdminAccount.collectAsState()
    val isTrialExpired by viewModel.isTrialExpired.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var showAddOrderDialog by remember { mutableStateOf(false) }

    val orders by viewModel.orders.collectAsState()
    val catalogItems by viewModel.catalogItems.collectAsState()
    val expenses by viewModel.expenses.collectAsState()

    // 1. Force Setup Admin if none exists
    if (!hasAdminAccount) {
        FirstTimeSetupWizard(
            onRegisterAdmin = { username, password ->
                viewModel.registerAdmin(username, password)
            }
        )
        return
    }

    // 2. Force Login Screen if nobody logged in
    if (loggedInUser == null) {
        LoginScreen(
            onLogin = { username, password, callback ->
                viewModel.login(username, password, callback)
            },
            isTrialExpired = isTrialExpired,
            onUnlock = { code, callback ->
                viewModel.unlockApp(code, callback)
            }
        )
        return
    }

    // 3. Force Expiration Block Screen if trial expired
    if (isTrialExpired) {
        TrialLockScreen(
            role = loggedInUser?.role ?: "Worker",
            onUnlock = { code, callback ->
                viewModel.unlockApp(code, callback)
            },
            onLogout = {
                viewModel.logout()
            }
        )
        return
    }

    val activeTab = if (loggedInUser?.role == "Admin") selectedTab else 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalMall,
                            contentDescription = "Laundry Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = "LaundryManager",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                            Text(
                                text = "Usuario: ${loggedInUser?.username} (${if (loggedInUser?.role == "Admin") "Administrador" else "Personal"})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.secondary,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.testTag("logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Logout,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = if (activeTab == 0) Icons.Filled.LocalLaundryService else Icons.Outlined.LocalLaundryService,
                            contentDescription = "Pedidos"
                        )
                    },
                    label = { Text("Pedidos") },
                    modifier = Modifier.testTag("tab_orders")
                )
                if (loggedInUser?.role == "Admin") {
                    NavigationBarItem(
                        selected = activeTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = {
                            Icon(
                                imageVector = if (activeTab == 1) Icons.Filled.Calculate else Icons.Outlined.Calculate,
                                contentDescription = "Contabilidad"
                            )
                        },
                        label = { Text("Contabilidad") },
                        modifier = Modifier.testTag("tab_accounting")
                    )
                    NavigationBarItem(
                        selected = activeTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = {
                            Icon(
                                imageVector = if (activeTab == 2) Icons.Filled.Settings else Icons.Outlined.Settings,
                                contentDescription = "Configuración"
                            )
                        },
                        label = { Text("Ajustes") },
                        modifier = Modifier.testTag("tab_settings")
                    )
                }
            }
        },
        floatingActionButton = {
            if (activeTab == 0) {
                ExtendedFloatingActionButton(
                    text = { Text("Nuevo Pedido", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Filled.Add, "Crear Nuevo Pedido") },
                    onClick = { showAddOrderDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_new_order")
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    fadeIn(animationSpec = spring()) togetherWith fadeOut(animationSpec = spring())
                },
                label = "TabTransition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> OrdersTabContent(
                        orders = orders,
                        expenses = expenses,
                        catalogItems = catalogItems,
                        onUpdateOrder = { o, s -> viewModel.updateOrderStatus(o, s) },
                        onDeleteOrder = { o -> viewModel.deleteOrder(o) }
                    )
                    1 -> AccountingTabContent(
                        orders = orders,
                        expenses = expenses,
                        onAddExpense = { desc, amount -> viewModel.addExpense(desc, amount) },
                        onDeleteExpense = { exp -> viewModel.deleteExpense(exp) }
                    )
                    2 -> SettingsTabContent(
                        catalogItems = catalogItems,
                        onAddCatalogItem = { name, price -> viewModel.addCatalogItem(name, price) },
                        onUpdateCatalogItem = { item -> viewModel.updateCatalogItem(item) },
                        onDeleteCatalogItem = { item -> viewModel.deleteCatalogItem(item) },
                        onWipeData = { wipeCatalog -> viewModel.wipeAllData(wipeCatalog) },
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    if (showAddOrderDialog) {
        CreateOrderDialog(
            catalogItems = catalogItems,
            onDismiss = { showAddOrderDialog = false },
            onSubmit = { customerName, customerPhone, items, total ->
                viewModel.createOrder(customerName, customerPhone, items, total)
                showAddOrderDialog = false
            }
        )
    }
}

// ---------------------- KPI PERFORMANCE METRICS ----------------------

@Composable
fun KPIPerformanceGrid(
    orders: List<Order>,
    expenses: List<Expense>,
    modifier: Modifier = Modifier
) {
    val totalIncome = remember(orders) {
        orders.filter { it.status == "Delivered" }.sumOf { it.totalAmount }
    }
    val totalExpenses = remember(expenses) {
        expenses.sumOf { it.amount }
    }
    val netProfit = totalIncome - totalExpenses

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Income card
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Ingresos".uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
                Text(
                    text = formatCurrency(totalIncome),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Expense card
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Gastos".uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
                Text(
                    text = formatCurrency(totalExpenses),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Profit card (Primary Polish blue background)
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Ganancia".uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.87f)
                )
                Text(
                    text = formatCurrency(netProfit),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------------- ORDERS TAB ----------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OrdersTabContent(
    orders: List<Order>,
    expenses: List<Expense>,
    catalogItems: List<CatalogItem>,
    onUpdateOrder: (Order, String) -> Unit,
    onDeleteOrder: (Order) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Received", "In Progress", "Ready for Pickup", "Delivered")
    var searchQuery by remember { mutableStateOf("") }

    val filteredOrders = remember(orders, selectedFilter, searchQuery) {
        val matchesSearch = if (searchQuery.isBlank()) {
            orders
        } else {
            orders.filter {
                it.customerName.contains(searchQuery, ignoreCase = true) ||
                it.customerPhone.contains(searchQuery, ignoreCase = true)
            }
        }
        if (selectedFilter == "All") matchesSearch else matchesSearch.filter { it.status == selectedFilter }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Daily Performance Metrics
        KPIPerformanceGrid(orders = orders, expenses = expenses)

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar Component
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar por nombre o teléfono...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Icono de búsqueda",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.testTag("clear_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Borrar búsqueda",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("order_search_bar")
        )

        Spacer(modifier = Modifier.height(8.dp))
        // Horizontal filter chips
        val spanishFilterNames = mapOf(
            "All" to "Todos",
            "Received" to "Recibido",
            "In Progress" to "En Proceso",
            "Ready for Pickup" to "Listo p/ Entrega",
            "Delivered" to "Entregado"
        )
        ScrollableTabRow(
            selectedTabIndex = filters.indexOf(selectedFilter).coerceAtLeast(0),
            edgePadding = 0.dp,
            divider = {},
            containerColor = Color.Transparent,
            indicator = {}
        ) {
            filters.forEach { filter ->
                val isSelected = selectedFilter == filter
                Tab(
                    selected = isSelected,
                    onClick = { selectedFilter = filter },
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                ) {
                    SuggestionChip(
                        onClick = { selectedFilter = filter },
                        label = { Text(spanishFilterNames[filter] ?: filter, fontWeight = FontWeight.Bold) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalLaundryService,
                        contentDescription = "Pedidos vacíos",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        modifier = Modifier.size(72.dp)
                    )
                    Text(
                        text = "No se Encontraron Pedidos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (selectedFilter == "All") "Toque 'Nuevo Pedido' abajo para recibir prendas de clientes." else "No hay pedidos con el estado '${spanishFilterNames[selectedFilter] ?: selectedFilter}'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(240.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredOrders, key = { it.id }) { order ->
                    OrderCard(
                        order = order,
                        onUpdateOrder = onUpdateOrder,
                        onDeleteOrder = onDeleteOrder,
                        modifier = Modifier.animateItemPlacement()
                    )
                }
            }
        }
    }
}

@Composable
fun OrderCard(
    order: Order,
    onUpdateOrder: (Order, String) -> Unit,
    onDeleteOrder: (Order) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("order_item_card_${order.id}")
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Name, Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.customerName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = order.customerPhone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = formatDate(order.orderDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            // Body: Selected items listing
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Prendas / Artículos:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                order.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${item.quantity}x ${item.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = formatCurrency(item.price * item.quantity),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            // Footer: price and buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Cobrado / Pendiente:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = formatCurrency(order.totalAmount),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Status Badge/Chip Selector
                val statusColor = when (order.status) {
                    "Received" -> MaterialTheme.colorScheme.primary
                    "In Progress" -> Color(0xFFEF8C22)
                    "Ready for Pickup" -> Color(0xFF00B26A)
                    "Delivered" -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                val statusListSpanish = mapOf(
                    "Received" to "Recibido",
                    "In Progress" to "En Proceso",
                    "Ready for Pickup" to "Listo p/ Entrega",
                    "Delivered" to "Entregado"
                )

                Box {
                    var menuExpanded by remember { mutableStateOf(false) }
                    Surface(
                        color = statusColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clickable { menuExpanded = true }
                            .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = statusListSpanish[order.status] ?: order.status,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = statusColor
                            )
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Cambiar Estado",
                                tint = statusColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        listOf("Received", "In Progress", "Ready for Pickup", "Delivered").forEach { status ->
                            DropdownMenuItem(
                                text = { Text(statusListSpanish[status] ?: status, fontWeight = FontWeight.SemiBold) },
                                onClick = {
                                    onUpdateOrder(order, status)
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Ready for Pickup Call trigger & Administration Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (order.status == "Ready for Pickup") {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${order.customerPhone}")
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00B26A),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("call_customer_button"),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "Llamar Cliente",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Llamar al Cliente", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                IconButton(
                    onClick = { showDeleteConfirm = true },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Filled.Delete, "Eliminar Pedido")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("¿Eliminar este pedido?", fontWeight = FontWeight.Bold) },
            text = { Text("¿Está seguro de que desea eliminar el pedido de ${order.customerName}? Esto eliminará permanentemente la información de facturación y artículos correspondientes.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteOrder(order)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// ---------------------- ACCOUNTING TAB ----------------------

@Composable
fun AccountingTabContent(
    orders: List<Order>,
    expenses: List<Expense>,
    onAddExpense: (String, Double) -> Unit,
    onDeleteExpense: (Expense) -> Unit
) {
    // Math: Income = sum total in Deliver status
    val totalIncome = remember(orders) {
        orders.filter { it.status == "Delivered" }.sumOf { it.totalAmount }
    }

    val totalExpenses = remember(expenses) {
        expenses.sumOf { it.amount }
    }

    val netProfit = totalIncome - totalExpenses

    var expenseDesc by remember { mutableStateOf("") }
    var expenseAmountString by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Daily Performance Metrics Dashboard
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Resumen de Rendimiento Diario",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                KPIPerformanceGrid(orders = orders, expenses = expenses)
            }
        }

        // Add Expense Form Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Registrar Gasto Operativo Diario",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = expenseDesc,
                        onValueChange = {
                            expenseDesc = it
                            formError = ""
                        },
                        label = { Text("Descripción del Gasto (ej. Detergente, Electricidad)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("expense_desc_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = expenseAmountString,
                        onValueChange = {
                            expenseAmountString = it
                            formError = ""
                        },
                        label = { Text("Monto Gastado ($)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("expense_amount_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (formError.isNotEmpty()) {
                        Text(
                            text = formError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            val price = expenseAmountString.toDoubleOrNull()
                            if (expenseDesc.isBlank()) {
                                formError = "¡Por favor describa el gasto!"
                            } else if (price == null || price <= 0.0) {
                                formError = "Por favor ingrese una cantidad de gasto válida."
                            } else {
                                onAddExpense(expenseDesc, price)
                                expenseDesc = ""
                                expenseAmountString = ""
                                formError = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("log_expense_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.PostAdd, "Agregar Gasto")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Registrar Gasto", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // List of logged expenses Header
        item {
            Text(
                text = "Gastos Diarios Recientes",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (expenses.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No se han registrado gastos operativos hoy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            items(expenses, key = { it.id }) { expense ->
                ExpenseRow(expense = expense, onDeleteExpense = onDeleteExpense)
            }
        }
    }
}

@Composable
fun ExpenseRow(expense: Expense, onDeleteExpense: (Expense) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.description,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatDate(expense.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "-" + formatCurrency(expense.amount),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )
                IconButton(
                    onClick = { onDeleteExpense(expense) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete Expense",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ---------------------- SETTINGS & CATALOG TAB ----------------------

@Composable
fun SettingsTabContent(
    catalogItems: List<CatalogItem>,
    onAddCatalogItem: (String, Double) -> Unit,
    onUpdateCatalogItem: (CatalogItem) -> Unit,
    onDeleteCatalogItem: (CatalogItem) -> Unit,
    onWipeData: (Boolean) -> Unit,
    viewModel: LaundryViewModel
) {
    var showWipeConfirm1 by remember { mutableStateOf(false) }
    var showWipeConfirm2 by remember { mutableStateOf(false) }
    var shouldWipeCatalogAlongInfo by remember { mutableStateOf(false) }

    var showAddItemDialog by remember { mutableStateOf(false) }

    // Staff creation states
    var workerUsername by remember { mutableStateOf("") }
    var workerPassword by remember { mutableStateOf("") }
    var workerCreationMessage by remember { mutableStateOf("") }
    var workerCreationSuccess by remember { mutableStateOf(true) }

    val daysLeft by viewModel.daysLeft.collectAsState()
    val isLicensed by viewModel.isLicensed.collectAsState()
    val isTrialExpired by viewModel.isTrialExpired.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Section: Developer & Licensing Simulator (MANDATORY FOR OFFLINE VALIDATION TESTING)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("licensing_simulator_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Lock Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Estado de Licencia y Software",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Estado:", style = MaterialTheme.typography.bodySmall)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isLicensed) Color(0xFFD1FAE5) else if (isTrialExpired) Color(0xFFFEE2E2) else Color(0xFFFEF3C7)
                        ) {
                            Text(
                                text = if (isLicensed) "CON LICENCIA (Offline Permanente)" else if (isTrialExpired) "PRUEBA EXPIRADA" else "PRUEBA ACTIVA (quedan ${daysLeft} días)",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = if (isLicensed) Color(0xFF065F46) else if (isTrialExpired) Color(0xFF991B1B) else Color(0xFF92400E)
                            )
                        }
                    }

                    Text(
                        text = "Verifique el comportamiento instantáneo del período de prueba offline de 30 días adelantando las fechas o reiniciando el contador.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.simulateTrialExpiration() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("simulate_trial_expiration_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Simular Expiración", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                        }

                        Button(
                            onClick = { viewModel.simulateTrialReset() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("reset_trial_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Reiniciar (30 Días)", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }



        // Section: Staff Credentials Registry (Add Worker Role Accounts)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.People,
                            contentDescription = "Staff Icon",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Registrar Cuenta del Personal (Rol Operario)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Text(
                        text = "Registre cuentas de personal que SOLO puedan ver y administrar pedidos. Las cuentas de operarios no podrán acceder a registros contables, ajustar los precios del catálogo ni reiniciar el historial.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = workerUsername,
                        onValueChange = { workerUsername = it },
                        label = { Text("Usuario del Operario") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_worker_username"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = workerPassword,
                        onValueChange = { workerPassword = it },
                        label = { Text("Contraseña del Operario") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_worker_password"),
                        shape = RoundedCornerShape(8.dp),
                        visualTransformation = PasswordVisualTransformation()
                    )

                    if (workerCreationMessage.isNotEmpty()) {
                        Text(
                            text = workerCreationMessage,
                            color = if (workerCreationSuccess) Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("worker_creation_feedback")
                        )
                    }

                    Button(
                        onClick = {
                            if (workerUsername.isBlank() || workerPassword.isBlank()) {
                                workerCreationMessage = "¡Por favor complete todos los campos del operario!"
                                workerCreationSuccess = false
                            } else {
                                viewModel.registerWorker(workerUsername, workerPassword) { success, err ->
                                    workerCreationSuccess = success
                                    if (success) {
                                        workerCreationMessage = "¡Operario registrado con éxito! Cierre la sesión actual para probar el acceso."
                                        workerUsername = ""
                                        workerPassword = ""
                                    } else {
                                        workerCreationMessage = err ?: "Fallo en el registro"
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("add_worker_submit_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.PersonAdd, "Agregar Operario")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Registrar Operario")
                    }
                }
            }
        }
        // Section: Daily Database Maintenance Reset
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RestartAlt,
                            contentDescription = "Reset Icon",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Mantenimiento y Cierre de Jornada",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        text = "Vacíe la lista de clientes, los pedidos activos/entregados y los gastos operativos del día de hoy para inicializar la aplicación completamente limpia mañana por la mañana.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { shouldWipeCatalogAlongInfo = !shouldWipeCatalogAlongInfo }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = shouldWipeCatalogAlongInfo,
                            onCheckedChange = { shouldWipeCatalogAlongInfo = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "También restablecer precios predeterminados del catálogo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Button(
                        onClick = { showWipeConfirm1 = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("wipe_all_data_today_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.CleaningServices, "Wipe Data")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cerrar Jornada / Limpiar Base de Datos", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section: Cloth Catalog Price Management
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Catálogo de Tarifas",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { showAddItemDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Add, "Agregar Prenda", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nueva Prenda", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        if (catalogItems.isEmpty()) {
            item {
                Text(
                    text = "No hay tarifas de prendas creadas. Agregue prendas del catálogo para comenzar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            items(catalogItems, key = { it.id }) { item ->
                CatalogItemRow(
                    item = item,
                    onUpdate = onUpdateCatalogItem,
                    onDelete = onDeleteCatalogItem
                )
            }
        }
    }

    // WIPE DIALOG 1 (First Confirmation)
    if (showWipeConfirm1) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm1 = false },
            title = { Text("¿Confirmar Limpieza Diaria?", fontWeight = FontWeight.Bold) },
            text = { Text("¿Está seguro de que desea finalizar el seguimiento de hoy, borrando los balances de cuentas y pedidos de lavandería actuales? No se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWipeConfirm1 = false
                        showWipeConfirm2 = true
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Proceder", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm1 = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // WIPE DIALOG 2 (Second Confirmation / Double-Confirmation)
    if (showWipeConfirm2) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm2 = false },
            title = { Text("ADVERTENCIA CRÍTICA", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.error) },
            text = { Text("ESTE PROCESO NO SE PUEDE DESHACER. Esta confirmación eliminará de forma permanente los balances, métricas de clientes, comprobantes y registros de gastos. ¿Desea vaciar la base de datos?") },
            confirmButton = {
                Button(
                    onClick = {
                        onWipeData(shouldWipeCatalogAlongInfo)
                        showWipeConfirm2 = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("CONFIRMAR ELIMINACIÓN", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm2 = false }) {
                    Text("Rechazar Limpieza")
                }
            }
        )
    }

    if (showAddItemDialog) {
        AddCatalogItemDialog(
            onDismiss = { showAddItemDialog = false },
            onSubmit = { name, price ->
                onAddCatalogItem(name, price)
                showAddItemDialog = false
            }
        )
    }
}

@Composable
fun CatalogItemRow(
    item: CatalogItem,
    onUpdate: (CatalogItem) -> Unit,
    onDelete: (CatalogItem) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(item.name) }
    var editPriceString by remember { mutableStateOf(item.price.toString()) }
    var inputError by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isEditing) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = {
                            editName = it
                            inputError = false
                        },
                        label = { Text("Nombre del Artículo") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editPriceString,
                        onValueChange = {
                            editPriceString = it
                            inputError = false
                        },
                        label = { Text("Precio de Unidad ($)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    if (inputError) {
                        Text(
                            text = "Entradas inválidas ingresadas",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val p = editPriceString.toDoubleOrNull()
                                if (editName.isBlank() || p == null || p < 0.0) {
                                    inputError = true
                                } else {
                                    onUpdate(item.copy(name = editName, price = p))
                                    isEditing = false
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Guardar", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                isEditing = false
                                editName = item.name
                                editPriceString = item.price.toString()
                                inputError = false
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancelar")
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${formatCurrency(item.price)} por unidad",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Editar Tarifas",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        IconButton(onClick = { onDelete(item) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Eliminar Tarifas",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Dialog: Add Custom Item to Catalog
@Composable
fun AddCatalogItemDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var priceStr by remember { mutableStateOf("") }
    var triggerError by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar Artículo al Catálogo", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Defina la descripción del artículo y el precio de facturación fijo por unidad.")
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        triggerError = ""
                    },
                    label = { Text("Prenda / Artículo (ej. Camisa, Manta)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = {
                        priceStr = it
                        triggerError = ""
                    },
                    label = { Text("Precio por Unidad ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (triggerError.isNotEmpty()) {
                    Text(
                        text = triggerError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = priceStr.toDoubleOrNull()
                    if (name.isBlank()) {
                        triggerError = "¡El nombre no puede estar vacío!"
                    } else if (p == null || p < 0.0) {
                        triggerError = "Por favor ingrese un precio válido."
                    } else {
                        onSubmit(name, p)
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Confirmar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// ---------------------- DIALOG: ORDER CREATION ----------------------

@Composable
fun CreateOrderDialog(
    catalogItems: List<CatalogItem>,
    onDismiss: () -> Unit,
    onSubmit: (String, String, List<SelectedItem>, Double) -> Unit
) {
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf("") }

    // Map tracking item quantities inside dialogue safely
    val quantityMap = remember { mutableStateMapOf<Int, Int>() }

    // Initialize quantities to zero
    LaunchedEffect(catalogItems) {
        catalogItems.forEach { item ->
            if (!quantityMap.containsKey(item.id)) {
                quantityMap[item.id] = 0
            }
        }
    }

    // Dynamic calculations done local reactive
    val selectedItemsList = catalogItems.mapNotNull { item ->
        val qty = quantityMap[item.id] ?: 0
        if (qty > 0) {
            SelectedItem(
                id = item.id,
                name = item.name,
                price = item.price,
                quantity = qty
            )
        } else {
            null
        }
    }

    val totalAmount = selectedItemsList.sumOf { it.price * it.quantity }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalLaundryService,
                    contentDescription = "Laundry Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Nuevo Pedido de Lavandería",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .wrapContentHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = customerName,
                    onValueChange = {
                        customerName = it
                        dialogError = ""
                    },
                    label = { Text("Nombre del Cliente") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Nombre del Cliente",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_name_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = {
                        customerPhone = it
                        dialogError = ""
                    },
                    label = { Text("Número de Teléfono") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Phone,
                            contentDescription = "Número de Teléfono",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_phone_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Text(
                    text = "Seleccione Artículos y Cantidades:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                if (catalogItems.isEmpty()) {
                    Text(
                        text = "Your billing item catalog is empty. Please define prices under Settings first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(catalogItems, key = { it.id }) { item ->
                            val currentQty = quantityMap[item.id] ?: 0
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = if (currentQty > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else Color.Transparent,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (currentQty > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (currentQty > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formatCurrency(item.price),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (currentQty > 0) {
                                                    quantityMap[item.id] = currentQty - 1
                                                }
                                            },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .testTag("remove_qty_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.RemoveCircleOutline,
                                                contentDescription = "Reduce Unit",
                                                tint = if (currentQty > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Text(
                                            text = currentQty.toString(),
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.width(28.dp),
                                            textAlign = TextAlign.Center,
                                            color = if (currentQty > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )

                                        IconButton(
                                            onClick = {
                                                quantityMap[item.id] = currentQty + 1
                                            },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .testTag("add_qty_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.AddCircleOutline,
                                                contentDescription = "Add Unit",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Receipt,
                                contentDescription = "Total Bill",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Total Calculado:",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = formatCurrency(totalAmount),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (dialogError.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Error,
                                contentDescription = "Error Info",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = dialogError,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (customerName.isBlank()) {
                        dialogError = "¡El nombre del cliente es obligatorio!"
                    } else if (customerPhone.isBlank()) {
                        dialogError = "¡El número de teléfono es obligatorio!"
                    } else if (selectedItemsList.isEmpty()) {
                        dialogError = "¡Por favor seleccione al menos una prenda!"
                    } else {
                        onSubmit(customerName, customerPhone, selectedItemsList, totalAmount)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("create_order_submit"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Crear Pedido", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Cancelar", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

// ---------------------- SECURITY AUDIT & LICENSING SCREENS ----------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstTimeSetupWizard(
    onRegisterAdmin: (String, String) -> Unit
) {
    var adminUser by remember { mutableStateOf("") }
    var adminPass by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.AdminPanelSettings,
                    contentDescription = "Shield",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Bienvenido a LaundryManager",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "En el primer inicio, debe crear una cuenta permanente de Administrador. Solo esta cuenta puede ver informes, modificar precios y restablecer la base de datos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = adminUser,
                    onValueChange = { adminUser = it },
                    label = { Text("Usuario de Administrador") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_admin_username")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = adminPass,
                    onValueChange = { adminPass = it },
                    label = { Text("Contraseña de Administrador") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_admin_password")
                )

                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (adminUser.trim().isEmpty() || adminPass.trim().isEmpty()) {
                            errorMessage = "¡El usuario y la contraseña no pueden estar vacíos!"
                        } else {
                            onRegisterAdmin(adminUser.trim(), adminPass.trim())
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("setup_admin_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Inicializar Seguridad y Continuar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLogin: (String, String, (Boolean, String?) -> Unit) -> Unit,
    isTrialExpired: Boolean,
    onUnlock: (String, (Boolean) -> Unit) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.LockPerson,
                    contentDescription = "Lock",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Autenticación del Personal",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Por favor inicie sesión para gestionar pedidos de lavandería, completar transacciones y realizar un seguimiento del flujo contable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Nombre de Usuario") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_username_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password_input")
                )

                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("login_error_text")
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (username.trim().isEmpty() || password.trim().isEmpty()) {
                            errorMessage = "Por favor ingrese ambas credenciales"
                        } else {
                            onLogin(username.trim(), password) { success, err ->
                                if (!success) {
                                    errorMessage = err ?: "Fallo al iniciar sesión"
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("login_submit_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Iniciar Sesión de Forma Segura", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrialLockScreen(
    role: String,
    onUnlock: (String, (Boolean) -> Unit) -> Unit,
    onLogout: () -> Unit
) {
    var activationCode by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf("") }
    var codeSuccessMessage by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.GppBad,
                    contentDescription = "Lock Out Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Período de Prueba Expirado",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "El período de evaluación de 30 días del software LaundryManager ha concluido. Por favor active la licencia de su copia principal para restaurar el acceso de producción.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Ingrese su Código de Activación abajo:",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = activationCode,
                    onValueChange = { activationCode = it },
                    placeholder = { Text("Código de Activación") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trial_activation_code_input")
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (codeError.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = codeError,
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("trial_unlock_error_text"),
                        textAlign = TextAlign.Center
                    )
                }

                if (codeSuccessMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = codeSuccessMessage,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF16A34A)),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (activationCode.trim().isEmpty()) {
                            codeError = "Por favor ingrese un código de activación"
                        } else {
                            onUnlock(activationCode) { success ->
                                if (success) {
                                    codeSuccessMessage = "¡Software desbloqueado exitosamente!"
                                    codeError = ""
                                } else {
                                    codeError = "Código de Activación Inválido"
                                    codeSuccessMessage = ""
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("trial_unlock_submit_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Desbloquear la Licencia Principal", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("trial_logout_button")
                ) {
                    Icon(Icons.Filled.Logout, "Logout Icon")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Volver a la Autenticación de Personal", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MonthlySubscriptionLockScreen(
    viewModel: LaundryViewModel,
    onUnlock: (String, (Boolean, String?) -> Unit) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    val calendar = java.util.Calendar.getInstance()
    val currentMonthNum = calendar.get(java.util.Calendar.MONTH) + 1
    val currentYearNum = calendar.get(java.util.Calendar.YEAR)
    val monthNames = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )
    val monthName = monthNames.getOrNull(calendar.get(java.util.Calendar.MONTH)) ?: "este mes"

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Giant Warning Icon Accent
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Licencia Expirada",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Error Overlay Messaging Table Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "LICENCIA FUERA DE LÍNEA EXPIRADA",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.error
                        )

                        Text(
                            text = "La suscripción ha expirado para $monthName de $currentYearNum. Por favor, póngase en contacto con el desarrollador del software para obtener el código de activación correspondiente.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Registration Unlock Field
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it
                        errorMsg = ""
                    },
                    label = { Text("Código de Activación") },
                    placeholder = { Text("ACT-$currentMonthNum-$currentYearNum-XXXX") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Icono de llave",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("subscription_code_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )



                if (errorMsg.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Error,
                                contentDescription = "Información de Error",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = errorMsg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        onUnlock(code) { success, error ->
                            if (!success) {
                                errorMsg = error ?: "Fallo en la verificación."
                            }
                        }
                    },
                    enabled = code.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("subscription_unlock_submit"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LockOpen,
                        contentDescription = "Activar Clave"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Desbloquear y Validar Licencia Mensual", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

