package com.artemkhateev.finance.ui.navigation

/**
 * Разделы в верхней ленте-«пилюлях», порядок как в референсе.
 * Дашборд стоит в середине ленты и открывается первым.
 */
enum class AppTab(val title: String) {
    CashFlow("Cash flow"),
    Accounts("Accounts"),
    Investments("Investments"),
    Dashboard("Dashboard"),
    Transactions("Transactions"),
    Categories("Categories"),
    Goals("Goals"),
    Recurrings("Recurrings");

    companion object {
        val Default = Dashboard
    }
}
