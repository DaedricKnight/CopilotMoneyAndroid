package com.artemkhateev.finance.feature.categories

import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.data.model.CategoryTone

/** Готовая категория из каталога. */
data class SuggestedCategory(
    val name: String,
    val emoji: String,
    val tone: CategoryTone,
    val group: String,
    val kind: CategoryKind = CategoryKind.Expense,
    /** Слова, по которым своя категория считается похожей: «Taxi» похожа на «Taxi & rideshare». */
    val keywords: List<String> = emptyList(),
) {
    fun toCategory() = Category(id = "", name = name, emoji = emoji, tone = tone, kind = kind)
}

private const val FOOD = "Food & drink"
private const val TRANSPORT = "Transportation"
private const val HOME = "Home & bills"
private const val SHOPPING = "Shopping"
private const val FUN = "Subscriptions & fun"
private const val HEALTH = "Health & care"
private const val TRAVEL = "Travel"
private const val FAMILY = "Family & learning"
private const val GIVING = "Giving"
private const val MONEY = "Money & work"
private const val INCOME = "Income"

/**
 * Каталог готовых категорий. Основа — категории Copilot Money (Rent, Groceries, Restaurants, Car,
 * Transport, Entertainment, Clothing, Subscriptions, Utilities, Coffee, Other), дополненные тем,
 * что обычно есть в приложениях для учёта расходов.
 */
val CategoryCatalog: List<SuggestedCategory> = listOf(
    SuggestedCategory("Groceries", "🛒", CategoryTone.Green, FOOD, keywords = listOf("grocer", "supermarket")),
    SuggestedCategory("Restaurants", "🍔", CategoryTone.Yellow, FOOD, keywords = listOf("restaurant", "dining", "fast")),
    SuggestedCategory("Coffee", "☕", CategoryTone.Red, FOOD, keywords = listOf("coffee", "cafe")),
    SuggestedCategory("Food delivery", "🛵", CategoryTone.Orange, FOOD, keywords = listOf("delivery", "takeaway", "takeout")),
    SuggestedCategory("Bars & nightlife", "🍸", CategoryTone.Magenta, FOOD, keywords = listOf("bar", "bars", "pub", "nightlife", "alcohol")),

    SuggestedCategory("Car", "🚙", CategoryTone.Blue, TRANSPORT, keywords = listOf("car", "auto", "vehicle")),
    SuggestedCategory("Gas & fuel", "⛽", CategoryTone.Orange, TRANSPORT, keywords = listOf("fuel", "petrol", "gas")),
    SuggestedCategory("Public transport", "🚌", CategoryTone.Purple, TRANSPORT, keywords = listOf("transport", "transit", "bus", "metro", "train", "tram")),
    SuggestedCategory("Taxi & rideshare", "🚕", CategoryTone.Yellow, TRANSPORT, keywords = listOf("taxi", "uber", "bolt", "rideshare")),
    SuggestedCategory("Parking & tolls", "🅿️", CategoryTone.Gray, TRANSPORT, keywords = listOf("parking", "toll", "tolls")),
    SuggestedCategory("Car maintenance", "🔧", CategoryTone.Gray, TRANSPORT, keywords = listOf("maintenance", "mechanic", "repair")),

    SuggestedCategory("Rent", "🔑", CategoryTone.Orange, HOME, keywords = listOf("rent")),
    SuggestedCategory("Mortgage", "🏡", CategoryTone.Orange, HOME, keywords = listOf("mortgage")),
    SuggestedCategory("Utilities", "💡", CategoryTone.Gray, HOME, keywords = listOf("utilit", "electric", "water", "heating")),
    SuggestedCategory("Internet & phone", "📶", CategoryTone.Teal, HOME, keywords = listOf("internet", "phone", "mobile", "wifi")),
    SuggestedCategory("Home & garden", "🪴", CategoryTone.Green, HOME, keywords = listOf("home", "garden", "furniture", "household")),
    SuggestedCategory("Home repairs", "🔨", CategoryTone.Gray, HOME, keywords = listOf("renovation", "handyman", "plumbing")),
    SuggestedCategory("Insurance", "☂️", CategoryTone.Blue, HOME, keywords = listOf("insurance")),

    SuggestedCategory("Shopping", "🛍️", CategoryTone.Pink, SHOPPING, keywords = listOf("shop", "shops", "shopping", "retail")),
    SuggestedCategory("Clothing", "👕", CategoryTone.Teal, SHOPPING, keywords = listOf("cloth", "apparel", "fashion", "shoes")),
    SuggestedCategory("Electronics", "💻", CategoryTone.Blue, SHOPPING, keywords = listOf("electronic", "gadget", "tech", "computer")),

    SuggestedCategory("Subscriptions", "💳", CategoryTone.Pink, FUN, keywords = listOf("subscription", "streaming")),
    SuggestedCategory("Entertainment", "🎟️", CategoryTone.Magenta, FUN, keywords = listOf("entertain", "cinema", "movie", "concert", "event")),
    SuggestedCategory("Games", "🎮", CategoryTone.Purple, FUN, keywords = listOf("game", "games", "gaming")),
    SuggestedCategory("Hobbies", "🎨", CategoryTone.Teal, FUN, keywords = listOf("hobby", "hobbies", "craft")),
    SuggestedCategory("Sports & fitness", "🏋️", CategoryTone.Red, FUN, keywords = listOf("sport", "gym", "fitness", "workout")),

    SuggestedCategory("Health", "💊", CategoryTone.Red, HEALTH, keywords = listOf("health", "medic", "pharmacy", "doctor", "dental", "hospital")),
    SuggestedCategory("Personal care", "💇", CategoryTone.Pink, HEALTH, keywords = listOf("personal", "beauty", "wellness", "hair", "spa", "cosmetic")),

    SuggestedCategory("Travel", "✈️", CategoryTone.Blue, TRAVEL, keywords = listOf("travel", "flight", "vacation", "trip")),
    SuggestedCategory("Hotels", "🏨", CategoryTone.Purple, TRAVEL, keywords = listOf("hotel", "accommodation", "airbnb")),

    SuggestedCategory("Kids", "🧸", CategoryTone.Yellow, FAMILY, keywords = listOf("kid", "kids", "child", "children", "baby", "childcare")),
    SuggestedCategory("Pets", "🐶", CategoryTone.Orange, FAMILY, keywords = listOf("pet", "pets", "vet")),
    SuggestedCategory("Education", "🎓", CategoryTone.Purple, FAMILY, keywords = listOf("education", "school", "course", "tuition", "university")),
    SuggestedCategory("Books", "📚", CategoryTone.Teal, FAMILY, keywords = listOf("book", "books", "reading")),

    SuggestedCategory("Gifts", "🎁", CategoryTone.Pink, GIVING, keywords = listOf("gift", "gifts", "present")),
    SuggestedCategory("Charity", "💝", CategoryTone.Red, GIVING, keywords = listOf("charity", "donation", "donate")),

    SuggestedCategory("Taxes", "🧾", CategoryTone.Gray, MONEY, keywords = listOf("tax", "taxes")),
    SuggestedCategory("Fees & charges", "💸", CategoryTone.Gray, MONEY, keywords = listOf("fee", "fees", "charge", "charges", "commission")),
    SuggestedCategory("Loan payments", "🏦", CategoryTone.Gray, MONEY, keywords = listOf("loan", "loans", "debt")),
    SuggestedCategory("Cash & ATM", "🏧", CategoryTone.Gray, MONEY, keywords = listOf("atm", "withdrawal")),
    SuggestedCategory("Work expenses", "💼", CategoryTone.Blue, MONEY, keywords = listOf("work", "business", "office")),
    SuggestedCategory("Other", "❔", CategoryTone.Gray, MONEY, keywords = listOf("other", "misc", "miscellaneous", "uncategorized")),

    SuggestedCategory("Salary", "💰", CategoryTone.Green, INCOME, CategoryKind.Income, listOf("salary", "wage", "wages", "paycheck", "payroll")),
    SuggestedCategory("Side income", "🪙", CategoryTone.Yellow, INCOME, CategoryKind.Income, listOf("freelance", "side", "gig")),
    SuggestedCategory("Interest & dividends", "📈", CategoryTone.Teal, INCOME, CategoryKind.Income, listOf("interest", "dividend")),
    SuggestedCategory("Refunds", "↩️", CategoryTone.Blue, INCOME, CategoryKind.Income, listOf("refund", "cashback", "reimburse")),
    SuggestedCategory("Other income", "💵", CategoryTone.Green, INCOME, CategoryKind.Income, listOf("income")),
)

data class CategorySuggestionUi(
    val suggestion: SuggestedCategory,
    /** Категория с тем же именем уже есть. */
    val added: Boolean,
    /** Своя категория, на которую эта похожа; null — похожей нет. */
    val similarTo: String?,
) {
    /** Заранее отмечены только те, что не дублируют уже заведённые. */
    val selectedByDefault: Boolean get() = !added && similarTo == null
}

fun categorySuggestions(existing: List<Category>): List<CategorySuggestionUi> = CategoryCatalog.map { suggestion ->
    val added = existing.any { it.name.equals(suggestion.name, ignoreCase = true) }
    val similar = if (added) {
        null
    } else {
        existing.firstOrNull { category -> nameWords(category.name).any { word -> suggestion.keywords.any { matches(word, it) } } }
    }
    CategorySuggestionUi(suggestion, added, similar?.name)
}

private fun nameWords(name: String): List<String> = name.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotBlank() }

// Короткие ключевые слова — только целым словом: иначе «car» нашлось бы в «card».
private fun matches(word: String, keyword: String) = if (keyword.length >= 4) word.startsWith(keyword) else word == keyword
