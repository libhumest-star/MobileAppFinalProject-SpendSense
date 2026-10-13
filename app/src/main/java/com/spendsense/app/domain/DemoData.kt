package com.spendsense.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

/**
 * Realistic sample expenses for the live demo: the whole previous month plus this month up to
 * today. Amounts are defined in THB (plus two USD subscriptions) and converted to the home currency.
 * The same inputs always give the same list (fixed random seed).
 */
object DemoData {

    private data class Template(val title: String, val category: Category, val minThb: Int, val maxThb: Int)

    private data class Bill(val dayOfMonth: Int, val title: String, val amount: Double, val currency: String)

    private val food = listOf(
        Template("Pad Thai lunch", Category.FOOD, 60, 90),
        Template("Iced latte", Category.FOOD, 55, 75),
        Template("Som tam & sticky rice", Category.FOOD, 50, 80),
        Template("7-Eleven snacks", Category.FOOD, 30, 90),
        Template("GrabFood delivery", Category.FOOD, 120, 260),
        Template("Bubble tea", Category.FOOD, 45, 70),
        Template("Khao man gai", Category.FOOD, 50, 70),
        Template("Shabu dinner", Category.FOOD, 250, 400),
    )
    private val transport = listOf(
        Template("BTS Skytrain", Category.TRANSPORT, 30, 60),
        Template("Grab to campus", Category.TRANSPORT, 90, 160),
        Template("Motorbike taxi", Category.TRANSPORT, 20, 40),
        Template("MRT", Category.TRANSPORT, 25, 45),
    )
    private val shopping = listOf(
        Template("Uniqlo shirt", Category.SHOPPING, 390, 790),
        Template("Shopee order", Category.SHOPPING, 150, 600),
        Template("Stationery", Category.SHOPPING, 40, 150),
    )
    private val entertainment = listOf(
        Template("Movie ticket", Category.FUN, 180, 260),
        Template("Karaoke night", Category.FUN, 200, 350),
        Template("Steam game", Category.FUN, 200, 600),
    )
    private val health = listOf(
        Template("Pharmacy", Category.HEALTH, 80, 250),
        Template("Gym day pass", Category.HEALTH, 150, 200),
    )
    private val bills = listOf(
        Bill(1, "Phone bill", 599.0, "THB"),
        Bill(5, "Electricity", 1250.0, "THB"),
        Bill(8, "Netflix", 12.0, "USD"),
        Bill(20, "Spotify", 6.0, "USD"),
    )

    fun generate(
        today: LocalDate,
        homeCurrency: String,
        thbToHome: Double,
        usdToHome: Double,
        seed: Int = 42,
    ): List<Expense> {
        val random = Random(seed)
        val result = mutableListOf<Expense>()
        var date = YearMonth.from(today).minusMonths(1).atDay(1)
        while (!date.isAfter(today)) {
            val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
            val picks = buildList {
                add(food.random(random))
                if (random.nextDouble() < 0.4) add(food.random(random))
                if (!weekend && random.nextDouble() < 0.6) add(transport.random(random))
                if (random.nextDouble() < 0.08) add(shopping.random(random))
                if (random.nextDouble() < if (weekend) 0.3 else 0.05) add(entertainment.random(random))
                if (random.nextDouble() < 0.04) add(health.random(random))
            }
            picks.forEach { template ->
                val thb = random.nextInt(template.minThb, template.maxThb + 1).toDouble()
                result += homeExpense(template.title, template.category, round2(thb * thbToHome), homeCurrency, date)
            }
            bills.filter { it.dayOfMonth == date.dayOfMonth }.forEach { bill ->
                result += if (bill.currency == "USD") {
                    Expense(
                        title = bill.title,
                        category = Category.BILLS,
                        amountHome = round2(bill.amount * usdToHome),
                        originalAmount = bill.amount,
                        originalCurrency = "USD",
                        date = date,
                    )
                } else {
                    homeExpense(bill.title, Category.BILLS, round2(bill.amount * thbToHome), homeCurrency, date)
                }
            }
            date = date.plusDays(1)
        }
        return result
    }

    private fun homeExpense(title: String, category: Category, amount: Double, home: String, date: LocalDate) =
        Expense(
            title = title,
            category = category,
            amountHome = amount,
            originalAmount = amount,
            originalCurrency = home,
            date = date,
        )
}
