package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.model.Expense
import com.example.data.model.Order
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class ExcelReportManager(private val context: Context) {

    enum class ReportPeriod { DAY, MONTH, YEAR }

    // ─── Entry point ────────────────────────────────────────────────────────────

    fun generateAndShare(
        orders: List<Order>,
        expenses: List<Expense>,
        period: ReportPeriod
    ) {
        val (filteredOrders, filteredExpenses, periodLabel) = filterByPeriod(orders, expenses, period)

        val workbook = XSSFWorkbook()
        createSummarySheet(workbook, filteredOrders, filteredExpenses, periodLabel)
        createOrdersSheet(workbook, filteredOrders)
        createExpensesSheet(workbook, filteredExpenses)
        createAnalysisSheet(workbook, filteredOrders, filteredExpenses)

        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val fileName = "LaundryPro_${periodLabel.replace(" ", "_")}_$stamp.xlsx"

        val cacheDir = File(context.cacheDir, "reports")
        cacheDir.mkdirs()
        val file = File(cacheDir, fileName)
        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()

        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Reporte Lavandería — $periodLabel")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, "Exportar reporte Excel").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    // ─── Period filter ───────────────────────────────────────────────────────────

    private fun filterByPeriod(
        orders: List<Order>,
        expenses: List<Expense>,
        period: ReportPeriod
    ): Triple<List<Order>, List<Expense>, String> {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

        fun dayRange(cal: Calendar): LongRange {
            val start = (cal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }.timeInMillis
            val end = (cal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
            }.timeInMillis
            return start..end
        }

        val now = Calendar.getInstance()

        return when (period) {
            ReportPeriod.DAY -> {
                val range = dayRange(now)
                Triple(
                    orders.filter { it.orderDate in range },
                    expenses.filter { it.timestamp in range },
                    "Hoy_${sdf.format(Date())}"
                )
            }
            ReportPeriod.MONTH -> {
                val start = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                }.timeInMillis
                val end = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                }.timeInMillis
                val label = SimpleDateFormat("MMMM_yyyy", Locale("es")).format(Date())
                Triple(
                    orders.filter { it.orderDate in start..end },
                    expenses.filter { it.timestamp in start..end },
                    label
                )
            }
            ReportPeriod.YEAR -> {
                val year = now.get(Calendar.YEAR)
                val start = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                }.timeInMillis
                val end = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.DAY_OF_YEAR, getActualMaximum(Calendar.DAY_OF_YEAR))
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                }.timeInMillis
                Triple(
                    orders.filter { it.orderDate in start..end },
                    expenses.filter { it.timestamp in start..end },
                    "Año_$year"
                )
            }
        }
    }

    // ─── Styles ──────────────────────────────────────────────────────────────────

    private fun buildStyles(wb: Workbook): Map<String, CellStyle> {
        fun font(bold: Boolean = false, size: Short = 11, color: Short = IndexedColors.BLACK.index): Font =
            wb.createFont().apply {
                this.bold = bold
                fontHeightInPoints = size
                this.color = color
            }

        fun style(block: CellStyle.() -> Unit): CellStyle = wb.createCellStyle().apply(block)

        return mapOf(
            "title" to style {
                setFont(font(bold = true, size = 14, color = IndexedColors.DARK_BLUE.index))
            },
            "header" to style {
                fillForegroundColor = IndexedColors.DARK_BLUE.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                setFont(font(bold = true, size = 11, color = IndexedColors.WHITE.index))
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                setBorderAll(BorderStyle.THIN)
            },
            "subheader" to style {
                fillForegroundColor = IndexedColors.CORNFLOWER_BLUE.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                setFont(font(bold = true, size = 10, color = IndexedColors.WHITE.index))
                alignment = HorizontalAlignment.LEFT
            },
            "label" to style { setFont(font(bold = true, size = 10)) },
            "value" to style { setFont(font(size = 10)) },
            "currency" to style {
                dataFormat = wb.createDataFormat().getFormat("\$#,##0.00")
                alignment = HorizontalAlignment.RIGHT
            },
            "currency_bold" to style {
                dataFormat = wb.createDataFormat().getFormat("\$#,##0.00")
                setFont(font(bold = true, size = 11))
                alignment = HorizontalAlignment.RIGHT
            },
            "green_currency" to style {
                dataFormat = wb.createDataFormat().getFormat("\$#,##0.00")
                setFont(font(bold = true, size = 12, color = IndexedColors.GREEN.index))
                alignment = HorizontalAlignment.RIGHT
            },
            "red_currency" to style {
                dataFormat = wb.createDataFormat().getFormat("\$#,##0.00")
                setFont(font(bold = true, size = 12, color = IndexedColors.RED.index))
                alignment = HorizontalAlignment.RIGHT
            },
            "date_fmt" to style {
                dataFormat = wb.createDataFormat().getFormat("dd/MM/yyyy HH:mm")
            },
            "row_even" to style {
                fillForegroundColor = IndexedColors.LIGHT_CORNFLOWER_BLUE.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
            },
            "row_even_currency" to style {
                fillForegroundColor = IndexedColors.LIGHT_CORNFLOWER_BLUE.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                dataFormat = wb.createDataFormat().getFormat("\$#,##0.00")
                alignment = HorizontalAlignment.RIGHT
            },
            "center" to style { alignment = HorizontalAlignment.CENTER },
            "center_bold" to style {
                setFont(font(bold = true))
                alignment = HorizontalAlignment.CENTER
            }
        )
    }

    private fun CellStyle.setBorderAll(border: BorderStyle) {
        borderTop = border; borderBottom = border
        borderLeft = border; borderRight = border
    }

    // ─── Sheet 1: Resumen ────────────────────────────────────────────────────────

    private fun createSummarySheet(
        wb: XSSFWorkbook,
        orders: List<Order>,
        expenses: List<Expense>,
        periodLabel: String
    ) {
        val sheet = wb.createSheet("Resumen")
        val s = buildStyles(wb)
        var row = 0

        fun r(i: Int = row++) = sheet.createRow(i)
        fun Cell.v(v: String) = setCellValue(v)
        fun Cell.v(v: Double) = setCellValue(v)

        // Title
        r().createCell(0).apply { v("🧺 LAUNDRY PRO — ${periodLabel.replace("_", " ").uppercase()}"); cellStyle = s["title"] }
        r().createCell(0).v("Generado: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}")
        r() // blank

        // KPI section header
        r().apply {
            createCell(0).apply { v("INDICADORES CLAVE (KPIs)"); cellStyle = s["header"] }
            createCell(1).apply { cellStyle = s["header"] }
            sheet.addMergedRegion(CellRangeAddress(rowNum.toInt() - 1, rowNum.toInt() - 1, 0, 1))
        }

        val delivered = orders.filter { it.status == "Delivered" }
        val ingresos = delivered.sumOf { it.totalAmount }
        val gastos = expenses.sumOf { it.amount }
        val neta = ingresos - gastos
        val ticket = if (delivered.isNotEmpty()) ingresos / delivered.size else 0.0

        data class KPI(val label: String, val value: Double, val style: String)
        listOf(
            KPI("💰 Ingresos Totales (órdenes entregadas)", ingresos, "green_currency"),
            KPI("💸 Gastos Operativos", gastos, "red_currency"),
            KPI("📈 Ganancia Neta", neta, if (neta >= 0) "green_currency" else "red_currency"),
            KPI("🧾 Ticket Promedio por Orden", ticket, "currency_bold")
        ).forEach { kpi ->
            r().apply {
                createCell(0).apply { v(kpi.label); cellStyle = s["label"] }
                createCell(1).apply { v(kpi.value); cellStyle = s[kpi.style] }
            }
        }

        r() // blank

        // Order counts
        r().apply {
            createCell(0).apply { v("ÓRDENES POR ESTADO"); cellStyle = s["header"] }
            createCell(1).apply { cellStyle = s["header"] }
            sheet.addMergedRegion(CellRangeAddress(rowNum.toInt() - 1, rowNum.toInt() - 1, 0, 1))
        }

        val statusMap = mapOf(
            "Received" to "📥 Recibida",
            "In Progress" to "🔄 En Proceso",
            "Ready for Pickup" to "✅ Lista para Recoger",
            "Delivered" to "🚀 Entregada"
        )
        val byStatus = orders.groupBy { it.status }
        statusMap.forEach { (key, label) ->
            val list = byStatus[key] ?: emptyList()
            r().apply {
                createCell(0).apply { v(label); cellStyle = s["value"] }
                createCell(1).apply { v("${list.size} órdenes — ${formatCurrency(list.sumOf { it.totalAmount })}"); cellStyle = s["value"] }
            }
        }
        r().apply {
            createCell(0).apply { v("TOTAL"); cellStyle = s["label"] }
            createCell(1).apply { v(orders.size.toDouble()); cellStyle = s["center_bold"] }
        }

        sheet.setColumnWidth(0, 40 * 256)
        sheet.setColumnWidth(1, 28 * 256)
    }

    private val Row.rowNum get() = this.rowNum.toLong()

    // ─── Sheet 2: Órdenes ────────────────────────────────────────────────────────

    private fun createOrdersSheet(wb: XSSFWorkbook, orders: List<Order>) {
        val sheet = wb.createSheet("Ordenes")
        val s = buildStyles(wb)
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        val headers = listOf("ID", "Cliente", "Teléfono", "Estado", "Fecha Orden", "Fecha Entrega", "Servicios", "Total")
        sheet.createRow(0).apply {
            headers.forEachIndexed { i, h ->
                createCell(i).apply { setCellValue(h); cellStyle = s["header"] }
            }
        }

        val statusEs = mapOf(
            "Received" to "Recibida", "In Progress" to "En Proceso",
            "Ready for Pickup" to "Lista", "Delivered" to "Entregada"
        )

        orders.sortedByDescending { it.orderDate }.forEachIndexed { idx, order ->
            val isEven = idx % 2 == 0
            sheet.createRow(idx + 1).apply {
                createCell(0).apply { setCellValue(order.id.toDouble()); if (isEven) cellStyle = s["row_even"] }
                createCell(1).apply { setCellValue(order.customerName); if (isEven) cellStyle = s["row_even"] }
                createCell(2).apply { setCellValue(order.customerPhone); if (isEven) cellStyle = s["row_even"] }
                createCell(3).apply { setCellValue(statusEs[order.status] ?: order.status); if (isEven) cellStyle = s["row_even"] }
                createCell(4).apply { setCellValue(sdf.format(Date(order.orderDate))); if (isEven) cellStyle = s["row_even"] }
                createCell(5).apply { setCellValue(order.deliveredDate?.let { sdf.format(Date(it)) } ?: "—"); if (isEven) cellStyle = s["row_even"] }
                createCell(6).apply {
                    setCellValue(order.items.joinToString(" | ") { "${it.name} ×${it.quantity}" })
                    if (isEven) cellStyle = s["row_even"]
                }
                createCell(7).apply {
                    setCellValue(order.totalAmount)
                    cellStyle = if (isEven) s["row_even_currency"] else s["currency"]
                }
            }
        }

        // Total row
        val totalRow = sheet.createRow(orders.size + 1)
        totalRow.createCell(6).apply { setCellValue("TOTAL"); cellStyle = s["label"] }
        totalRow.createCell(7).apply { setCellValue(orders.sumOf { it.totalAmount }); cellStyle = s["currency_bold"] }

        listOf(8, 22, 14, 14, 18, 18, 40, 13).forEachIndexed { i, w -> sheet.setColumnWidth(i, w * 256) }
        sheet.createFreezePane(0, 1)
    }

    // ─── Sheet 3: Gastos ─────────────────────────────────────────────────────────

    private fun createExpensesSheet(wb: XSSFWorkbook, expenses: List<Expense>) {
        val sheet = wb.createSheet("Gastos")
        val s = buildStyles(wb)
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        val headers = listOf("ID", "Descripción", "Fecha", "Monto")
        sheet.createRow(0).apply {
            headers.forEachIndexed { i, h ->
                createCell(i).apply { setCellValue(h); cellStyle = s["header"] }
            }
        }

        expenses.sortedByDescending { it.timestamp }.forEachIndexed { idx, expense ->
            val isEven = idx % 2 == 0
            sheet.createRow(idx + 1).apply {
                createCell(0).apply { setCellValue(expense.id.toDouble()); if (isEven) cellStyle = s["row_even"] }
                createCell(1).apply { setCellValue(expense.description); if (isEven) cellStyle = s["row_even"] }
                createCell(2).apply { setCellValue(sdf.format(Date(expense.timestamp))); if (isEven) cellStyle = s["row_even"] }
                createCell(3).apply {
                    setCellValue(expense.amount)
                    cellStyle = if (isEven) s["row_even_currency"] else s["currency"]
                }
            }
        }

        val totalRow = sheet.createRow(expenses.size + 1)
        totalRow.createCell(2).apply { setCellValue("TOTAL GASTOS"); cellStyle = s["label"] }
        totalRow.createCell(3).apply { setCellValue(expenses.sumOf { it.amount }); cellStyle = s["red_currency"] }

        listOf(8, 38, 18, 14).forEachIndexed { i, w -> sheet.setColumnWidth(i, w * 256) }
        sheet.createFreezePane(0, 1)
    }

    // ─── Sheet 4: Análisis ───────────────────────────────────────────────────────

    private fun createAnalysisSheet(
        wb: XSSFWorkbook,
        orders: List<Order>,
        expenses: List<Expense>
    ) {
        val sheet = wb.createSheet("Analisis")
        val s = buildStyles(wb)
        val sdfDay = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val sdfMonth = SimpleDateFormat("MM/yyyy", Locale.getDefault())

        var row = 0

        // ── Rendimiento por día ──
        val byDay = orders.groupBy { sdfDay.format(Date(it.orderDate)) }
        val expByDay = expenses.groupBy { sdfDay.format(Date(it.timestamp)) }

        sheet.createRow(row++).apply {
            createCell(0).apply { setCellValue("RENDIMIENTO DIARIO (últimos 30 días)"); cellStyle = s["header"] }
            (1..4).forEach { createCell(it).apply { cellStyle = s["header"] } }
            sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 4))
        }

        sheet.createRow(row++).apply {
            listOf("Fecha", "Órdenes", "Ingresos", "Gastos", "Neto del Día").forEachIndexed { i, h ->
                createCell(i).apply { setCellValue(h); cellStyle = s["subheader"] }
            }
        }

        byDay.entries.sortedByDescending { it.key }.take(30).forEachIndexed { idx, (day, dayOrders) ->
            val dayIngresos = dayOrders.sumOf { it.totalAmount }
            val dayGastos = expByDay[day]?.sumOf { it.amount } ?: 0.0
            val isEven = idx % 2 == 0
            sheet.createRow(row++).apply {
                createCell(0).apply { setCellValue(day); if (isEven) cellStyle = s["row_even"] }
                createCell(1).apply { setCellValue(dayOrders.size.toDouble()); if (isEven) cellStyle = s["row_even"] }
                createCell(2).apply { setCellValue(dayIngresos); cellStyle = if (isEven) s["row_even_currency"] else s["currency"] }
                createCell(3).apply { setCellValue(dayGastos); cellStyle = if (isEven) s["row_even_currency"] else s["currency"] }
                createCell(4).apply { setCellValue(dayIngresos - dayGastos); cellStyle = if (isEven) s["row_even_currency"] else s["currency"] }
            }
        }

        row++ // blank

        // ── Mejores días ──
        sheet.createRow(row++).apply {
            createCell(0).apply { setCellValue("🏆 RANKING DE MEJORES DÍAS"); cellStyle = s["header"] }
            (1..3).forEach { createCell(it).apply { cellStyle = s["header"] } }
            sheet.addMergedRegion(CellRangeAddress(row - 1, row - 1, 0, 3))
        }

        val ranked = byDay.entries
            .sortedByDescending { (_, v) -> v.sumOf { it.totalAmount } }
            .take(10)

        sheet.createRow(row++).apply {
            listOf("Posición", "Fecha", "Órdenes", "Ingresos").forEachIndexed { i, h ->
                createCell(i).apply { setCellValue(h); cellStyle = s["subheader"] }
            }
        }

        ranked.forEachIndexed { idx, (day, dayOrders) ->
            val medal = when (idx) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "#${idx + 1}" }
            sheet.createRow(row++).apply {
                createCell(0).apply { setCellValue(medal); cellStyle = s["center"] }
                createCell(1).setCellValue(day)
                createCell(2).setCellValue(dayOrders.size.toDouble())
                createCell(3).apply { setCellValue(dayOrders.sumOf { it.totalAmount }); cellStyle = s["currency"] }
            }
        }

        row++ // blank

        // ── Servicios más populares ──
        val topItems = orders.flatMap { it.items }
            .groupBy { it.name }
            .mapValues { (_, items) -> Pair(items.sumOf { it.quantity }, items.sumOf { it.price * it.quantity }) }
            .entries.sortedByDescending { it.value.first }.take(10)

        sheet.createRow(row++).apply {
            createCell(0).apply { setCellValue("🥇 SERVICIOS MÁS POPULARES"); cellStyle = s["header"] }
            (1..2).forEach { createCell(it).apply { cellStyle = s["header"] } }
            sheet.addMergedRegion(CellRangeAddress(row - 1, row - 1, 0, 2))
        }
        sheet.createRow(row++).apply {
            listOf("Servicio", "Unidades Vendidas", "Total Generado").forEachIndexed { i, h ->
                createCell(i).apply { setCellValue(h); cellStyle = s["subheader"] }
            }
        }
        topItems.forEach { (name, pair) ->
            sheet.createRow(row++).apply {
                createCell(0).setCellValue(name)
                createCell(1).apply { setCellValue(pair.first.toDouble()); cellStyle = s["center"] }
                createCell(2).apply { setCellValue(pair.second); cellStyle = s["currency"] }
            }
        }

        row++ // blank

        // ── Resumen mensual ──
        val byMonth = orders.groupBy { sdfMonth.format(Date(it.orderDate)) }
        if (byMonth.size > 1) {
            sheet.createRow(row++).apply {
                createCell(0).apply { setCellValue("📅 RESUMEN MENSUAL"); cellStyle = s["header"] }
                (1..2).forEach { createCell(it).apply { cellStyle = s["header"] } }
                sheet.addMergedRegion(CellRangeAddress(row - 1, row - 1, 0, 2))
            }
            sheet.createRow(row++).apply {
                listOf("Mes", "Órdenes", "Ingresos").forEachIndexed { i, h ->
                    createCell(i).apply { setCellValue(h); cellStyle = s["subheader"] }
                }
            }
            byMonth.entries.sortedBy { it.key }.forEach { (month, mOrders) ->
                sheet.createRow(row++).apply {
                    createCell(0).setCellValue(month)
                    createCell(1).apply { setCellValue(mOrders.size.toDouble()); cellStyle = s["center"] }
                    createCell(2).apply { setCellValue(mOrders.sumOf { it.totalAmount }); cellStyle = s["currency"] }
                }
            }
        }

        listOf(22, 14, 16, 16, 16).forEachIndexed { i, w -> sheet.setColumnWidth(i, w * 256) }
        sheet.createFreezePane(0, 1)
    }

    // ─── Helper ──────────────────────────────────────────────────────────────────

    private fun formatCurrency(amount: Double): String =
        NumberFormat.getCurrencyInstance(Locale.US).format(amount)
}

