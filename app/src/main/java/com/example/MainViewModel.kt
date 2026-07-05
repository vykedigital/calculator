package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppTab {
    Calculator,
    Convert,
    Units
}

data class Currency(
    val code: String,
    val name: String,
    val flag: String,
    val symbol: String
)

sealed interface NetworkState {
    object Idle : NetworkState
    object Loading : NetworkState
    data class Success(val lastUpdated: String) : NetworkState
    data class Error(val message: String) : NetworkState
}

enum class UnitCategory(val displayName: String, val icon: String) {
    LENGTH("Length", "📏"),
    WEIGHT("Weight", "⚖️"),
    TEMPERATURE("Temperature", "🌡️"),
    AREA("Area", "📐"),
    VOLUME("Volume", "🧪")
}

data class UnitType(val name: String, val suffix: String, val factorToAnchor: Double)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // --- Global Theme & Navigation ---
    private val _isDarkTheme = MutableStateFlow(true) // Default to modern premium dark
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _currentTab = MutableStateFlow(AppTab.Calculator)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    // --- Calculator State ---
    private val _calcExpression = MutableStateFlow("")
    val calcExpression: StateFlow<String> = _calcExpression.asStateFlow()

    private val _calcResult = MutableStateFlow("")
    val calcResult: StateFlow<String> = _calcResult.asStateFlow()

    private val _isScientificMode = MutableStateFlow(false)
    val isScientificMode: StateFlow<Boolean> = _isScientificMode.asStateFlow()

    private val _calcHistory = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val calcHistory: StateFlow<List<Pair<String, String>>> = _calcHistory.asStateFlow()

    private var isResultEvaluated = false

    fun toggleScientificMode() {
        _isScientificMode.value = !_isScientificMode.value
    }

    fun loadHistory() {
        val count = sharedPrefs.getInt("history_count", 0)
        val list = mutableListOf<Pair<String, String>>()
        for (i in 0 until count) {
            val expr = sharedPrefs.getString("history_expr_$i", "") ?: ""
            val res = sharedPrefs.getString("history_res_$i", "") ?: ""
            if (expr.isNotEmpty()) {
                list.add(Pair(expr, res))
            }
        }
        _calcHistory.value = list
    }

    fun addHistoryItem(expr: String, result: String) {
        if (result.isEmpty() || result == "Error" || result == "Can't divide by 0") return
        val currentList = _calcHistory.value.toMutableList()
        if (currentList.isNotEmpty() && currentList.first().first == expr) return

        currentList.add(0, Pair(expr, result))
        if (currentList.size > 50) {
            currentList.removeAt(currentList.lastIndex)
        }
        _calcHistory.value = currentList

        sharedPrefs.edit().apply {
            putInt("history_count", currentList.size)
            for (i in currentList.indices) {
                putString("history_expr_$i", currentList[i].first)
                putString("history_res_$i", currentList[i].second)
            }
            apply()
        }
    }

    fun clearHistory() {
        _calcHistory.value = emptyList()
        sharedPrefs.edit().apply {
            putInt("history_count", 0)
            apply()
        }
    }

    fun selectHistoryItem(item: Pair<String, String>) {
        _calcExpression.value = item.first
        _calcResult.value = item.second
        isResultEvaluated = true
    }

    fun onCalculatorAction(action: String) {
        when (action) {
            "AC" -> {
                _calcExpression.value = ""
                _calcResult.value = ""
                isResultEvaluated = false
            }
            "⌫" -> {
                if (isResultEvaluated) {
                    _calcExpression.value = ""
                    _calcResult.value = ""
                    isResultEvaluated = false
                } else if (_calcExpression.value.isNotEmpty()) {
                    _calcExpression.value = _calcExpression.value.dropLast(1)
                }
            }
            "=" -> {
                if (_calcExpression.value.isNotEmpty()) {
                    evaluateCalculator()
                }
            }
            "√" -> {
                if (isResultEvaluated) {
                    _calcExpression.value = "√(" + _calcResult.value + ")"
                    _calcResult.value = ""
                    isResultEvaluated = false
                } else {
                    _calcExpression.value += "√("
                }
            }
            "+", "−", "×", "÷", "%" -> {
                if (isResultEvaluated) {
                    _calcExpression.value = _calcResult.value + getOperatorChar(action)
                    _calcResult.value = ""
                    isResultEvaluated = false
                } else {
                    val current = _calcExpression.value
                    if (current.isNotEmpty()) {
                        val lastChar = current.last()
                        if (isOperator(lastChar)) {
                            // Replace last operator
                            _calcExpression.value = current.dropLast(1) + getOperatorChar(action)
                        } else {
                            _calcExpression.value += getOperatorChar(action)
                        }
                    }
                }
            }
            "sin", "cos", "tan", "log", "ln" -> {
                if (isResultEvaluated) {
                    _calcExpression.value = "$action(" + _calcResult.value + ")"
                    _calcResult.value = ""
                    isResultEvaluated = false
                } else {
                    _calcExpression.value += "$action("
                }
            }
            "π", "e", "(", ")" -> {
                if (isResultEvaluated) {
                    _calcExpression.value = action
                    _calcResult.value = ""
                    isResultEvaluated = false
                } else {
                    _calcExpression.value += action
                }
            }
            "^" -> {
                if (isResultEvaluated) {
                    _calcExpression.value = _calcResult.value + "^"
                    _calcResult.value = ""
                    isResultEvaluated = false
                } else {
                    _calcExpression.value += "^"
                }
            }
            "." -> {
                if (isResultEvaluated) {
                    _calcExpression.value = "0."
                    _calcResult.value = ""
                    isResultEvaluated = false
                } else {
                    val current = _calcExpression.value
                    if (current.isEmpty() || isOperator(current.last()) || current.last() == '(') {
                        _calcExpression.value += "0."
                    } else {
                        // Check if current number token already contains a decimal
                        val lastNumberToken = current.split('+', '−', '×', '÷', '%', '(', ')', '^').lastOrNull()
                        if (lastNumberToken != null && !lastNumberToken.contains('.')) {
                            _calcExpression.value += "."
                        }
                    }
                }
            }
            else -> { // Digits 0-9
                if (isResultEvaluated) {
                    _calcExpression.value = action
                    _calcResult.value = ""
                    isResultEvaluated = false
                } else {
                    // Prevent multiple leading zeros
                    val current = _calcExpression.value
                    if (current == "0") {
                        _calcExpression.value = action
                    } else {
                        _calcExpression.value += action
                    }
                }
            }
        }
    }

    private fun isOperator(c: Char): Boolean {
        return c == '+' || c == '−' || c == '×' || c == '÷' || c == '%'
    }

    private fun getOperatorChar(action: String): String {
        return when (action) {
            "+" -> "+"
            "−" -> "−"
            "×" -> "×"
            "÷" -> "÷"
            "%" -> "%"
            else -> action
        }
    }

    private fun evaluateCalculator() {
        viewModelScope.launch {
            val expression = _calcExpression.value
            // Auto close trailing open parenthesis for user ease
            val openParenCount = expression.count { it == '(' }
            val closeParenCount = expression.count { it == ')' }
            var balancedExpression = expression
            if (openParenCount > closeParenCount) {
                balancedExpression += ")".repeat(openParenCount - closeParenCount)
            }

            try {
                // Replace characters for mathematical evaluator
                val cleanedExpr = balancedExpression
                    .replace("×", "*")
                    .replace("÷", "/")
                    .replace("−", "-")

                val resultVal = withContext(Dispatchers.Default) {
                    val parser = MathParser(cleanedExpr)
                    parser.parse()
                }

                // Format double nicely
                val formatted = formatResult(resultVal)
                _calcResult.value = formatted
                isResultEvaluated = true
                addHistoryItem(balancedExpression, formatted)
            } catch (e: ArithmeticException) {
                _calcResult.value = e.message ?: "Math Error"
                isResultEvaluated = true
            } catch (e: Exception) {
                _calcResult.value = "Error"
                isResultEvaluated = true
            }
        }
    }

    private fun formatResult(value: Double): String {
        if (value.isNaN()) return "Error"
        if (value.isInfinite()) return "Can't divide by 0"
        
        // If it's a whole number, format as integer
        return if (value % 1.0 == 0.0) {
            if (value >= Long.MAX_VALUE || value <= Long.MIN_VALUE) {
                value.toString()
            } else {
                value.toLong().toString()
            }
        } else {
            // Maximum 8 decimal places, strip trailing zeros
            val format = SimpleDateFormat("###.########", Locale.US)
            val result = String.format(Locale.US, "%.8f", value)
            var trimmed = result
            while (trimmed.endsWith("0")) {
                trimmed = trimmed.dropLast(1)
            }
            if (trimmed.endsWith(".")) {
                trimmed = trimmed.dropLast(1)
            }
            trimmed
        }
    }

    // --- Currency Converter State ---
    val initialCurrencies = listOf(
        Currency("DZD", "Algerian Dinar", "🇩🇿", "د.ج"),
        Currency("ARS", "Argentine Peso", "🇦🇷", "$"),
        Currency("AUD", "Australian Dollar", "🇦🇺", "$"),
        Currency("EUR", "Euro (Austria)", "🇦🇹", "€"),
        Currency("AZN", "Azerbaijani Manat", "🇦🇿", "₼"),
        Currency("BHD", "Bahraini Dinar", "🇧🇭", "د.ب"),
        Currency("BDT", "Bangladeshi Taka", "🇧🇩", "৳"),
        Currency("BYN", "Belarusian Ruble", "🇧🇾", "Br"),
        Currency("EUR", "Euro (Belgium)", "🇧🇪", "€"),
        Currency("BOB", "Bolivian Boliviano", "🇧🇴", "Bs."),
        Currency("BAM", "Bosnia Convertible Mark", "🇧🇦", "KM"),
        Currency("BRL", "Brazilian Real", "🇧🇷", "R$"),
        Currency("BGN", "Bulgarian Lev", "🇧🇬", "лв"),
        Currency("KHR", "Cambodian Riel", "🇰🇭", "៛"),
        Currency("CAD", "Canadian Dollar", "🇨🇦", "$"),
        Currency("CLP", "Chilean Peso", "🇨🇱", "$"),
        Currency("COP", "Colombian Peso", "🇨🇴", "$"),
        Currency("CRC", "Costa Rican Colón", "🇨🇷", "₡"),
        Currency("EUR", "Euro (Croatia)", "🇭🇷", "€"),
        Currency("EUR", "Euro (Cyprus)", "🇨🇾", "€"),
        Currency("CZK", "Czech Koruna", "🇨🇿", "Kč"),
        Currency("DKK", "Danish Krone", "🇩🇰", "kr"),
        Currency("DOP", "Dominican Peso", "🇩🇴", "RD$"),
        Currency("USD", "US Dollar (Ecuador)", "🇪🇨", "$"),
        Currency("EGP", "Egyptian Pound", "🇪🇬", "E£"),
        Currency("USD", "US Dollar (El Salvador)", "🇸🇻", "$"),
        Currency("EUR", "Euro (Estonia)", "🇪🇪", "€"),
        Currency("EUR", "Euro (Finland)", "🇫🇮", "€"),
        Currency("EUR", "Euro (France)", "🇫🇷", "€"),
        Currency("GEL", "Georgian Lari", "🇬🇪", "₾"),
        Currency("EUR", "Euro (Germany)", "🇩🇪", "€"),
        Currency("GHS", "Ghanaian Cedi", "🇬🇭", "₵"),
        Currency("EUR", "Euro (Greece)", "🇬🇷", "€"),
        Currency("GTQ", "Guatemalan Quetzal", "🇬🇹", "Q"),
        Currency("HNL", "Honduran Lempira", "🇭🇳", "L"),
        Currency("HKD", "Hong Kong Dollar", "🇭🇰", "$"),
        Currency("HUF", "Hungarian Forint", "🇭🇺", "Ft"),
        Currency("ISK", "Icelandic Króna", "🇮🇸", "kr"),
        Currency("INR", "Indian Rupee", "🇮🇳", "₹"),
        Currency("IDR", "Indonesian Rupiah", "🇮🇩", "Rp"),
        Currency("IQD", "Iraqi Dinar", "🇮🇶", "ع.د"),
        Currency("EUR", "Euro (Ireland)", "🇮🇪", "€"),
        Currency("ILS", "Israeli Shekel", "🇮🇱", "₪"),
        Currency("EUR", "Euro (Italy)", "🇮🇹", "€"),
        Currency("JMD", "Jamaican Dollar", "🇯🇲", "J$"),
        Currency("JPY", "Japanese Yen", "🇯🇵", "¥"),
        Currency("JOD", "Jordanian Dinar", "🇯🇴", "د.ا"),
        Currency("KZT", "Kazakhstani Tenge", "🇰🇿", "₸"),
        Currency("KES", "Kenyan Shilling", "🇰🇪", "KSh"),
        Currency("KWD", "Kuwaiti Dinar", "🇰🇼", "د.ك"),
        Currency("LAK", "Laotian Kip", "🇱🇦", "₭"),
        Currency("EUR", "Euro (Latvia)", "🇱🇻", "€"),
        Currency("LBP", "Lebanese Pound", "🇱🇧", "ل.ل"),
        Currency("LYD", "Libyan Dinar", "🇱🇾", "ل.د"),
        Currency("CHF", "Swiss Franc (Liechtenstein)", "🇱🇮", "Fr"),
        Currency("EUR", "Euro (Lithuania)", "🇱🇹", "€"),
        Currency("EUR", "Euro (Luxembourg)", "🇱🇺", "€"),
        Currency("MKD", "Macedonian Denar", "🇲🇰", "ден"),
        Currency("MYR", "Malaysian Ringgit", "🇲🇾", "RM"),
        Currency("EUR", "Euro (Malta)", "🇲🇹", "€"),
        Currency("MXN", "Mexican Peso", "🇲🇽", "$"),
        Currency("MDL", "Moldovan Leu", "🇲🇩", "L"),
        Currency("EUR", "Euro (Montenegro)", "🇲🇪", "€"),
        Currency("MAD", "Moroccan Dirham", "🇲🇦", "د.م."),
        Currency("NPR", "Nepalese Rupee", "🇳🇵", "रू"),
        Currency("EUR", "Euro (Netherlands)", "🇳🇱", "€"),
        Currency("NZD", "New Zealand Dollar", "🇳🇿", "$"),
        Currency("NIO", "Nicaraguan Córdoba", "🇳🇮", "C$"),
        Currency("NGN", "Nigerian Naira", "🇳🇬", "₦"),
        Currency("NOK", "Norwegian Krone", "🇳🇴", "kr"),
        Currency("OMR", "Omani Rial", "🇴🇲", "ر.ع."),
        Currency("PKR", "Pakistani Rupee", "🇵🇰", "₨"),
        Currency("PAB", "Panamanian Balboa", "🇵🇦", "B/."),
        Currency("PGK", "Papua New Guinean Kina", "🇵🇬", "K"),
        Currency("PYG", "Paraguayan Guaraní", "🇵🇾", "₲"),
        Currency("PEN", "Peruvian Sol", "🇵🇪", "S/."),
        Currency("PHP", "Philippine Peso", "🇵🇭", "₱"),
        Currency("PLN", "Polish Złoty", "🇵🇱", "zł"),
        Currency("EUR", "Euro (Portugal)", "🇵🇹", "€"),
        Currency("USD", "US Dollar (Puerto Rico)", "🇵🇷", "$"),
        Currency("QAR", "Qatari Riyal", "🇶🇦", "ر.ق"),
        Currency("RON", "Romanian Leu", "🇷🇴", "lei"),
        Currency("RUB", "Russian Ruble", "🇷🇺", "₽"),
        Currency("SAR", "Saudi Riyal", "🇸🇦", "ر.س"),
        Currency("XOF", "West African CFA Franc (Senegal)", "🇸🇳", "CFA"),
        Currency("RSD", "Serbian Dinar", "🇷🇸", "дин."),
        Currency("SGD", "Singapore Dollar", "🇸🇬", "$"),
        Currency("EUR", "Euro (Slovakia)", "🇸🇰", "€"),
        Currency("EUR", "Euro (Slovenia)", "🇸🇮", "€"),
        Currency("ZAR", "South African Rand", "🇿🇦", "R"),
        Currency("KRW", "South Korean Won", "🇰🇷", "₩"),
        Currency("EUR", "Euro (Spain)", "🇪🇸", "€"),
        Currency("LKR", "Sri Lankan Rupee", "🇱🇰", "Rs"),
        Currency("SEK", "Swedish Krona", "🇸🇪", "kr"),
        Currency("CHF", "Swiss Franc (Switzerland)", "🇨🇭", "Fr"),
        Currency("TWD", "New Taiwan Dollar", "🇹🇼", "NT$"),
        Currency("TZS", "Tanzanian Shilling", "🇹🇿", "TSh"),
        Currency("THB", "Thai Baht", "🇹🇭", "฿"),
        Currency("TND", "Tunisian Dinar", "🇹🇳", "د.ت"),
        Currency("TRY", "Turkish Lira", "🇹🇷", "₺"),
        Currency("UGX", "Ugandan Shilling", "🇺🇬", "USh"),
        Currency("UAH", "Ukrainian Hryvnia", "🇺🇦", "₴"),
        Currency("AED", "UAE Dirham", "🇦🇪", "د.إ"),
        Currency("GBP", "British Pound (UK)", "🇬🇧", "£"),
        Currency("USD", "US Dollar (USA)", "🇺🇸", "$"),
        Currency("UYU", "Uruguayan Peso", "🇺🇾", "\$U"),
        Currency("VES", "Venezuelan Bolívar", "🇻🇪", "Bs.S"),
        Currency("VND", "Vietnamese Đồng", "🇻🇳", "₫"),
        Currency("YER", "Yemeni Rial", "🇾🇪", "﷼"),
        Currency("ZWL", "Zimbabwean Dollar", "🇿🇼", "Z$")
    )

    private val _availableCurrencies = MutableStateFlow<List<Currency>>(initialCurrencies)
    val availableCurrencies: StateFlow<List<Currency>> = _availableCurrencies.asStateFlow()

    // --- Unit Converter State ---
    val unitCategories = mapOf(
        UnitCategory.LENGTH to listOf(
            UnitType("Meter", "m", 1.0),
            UnitType("Kilometer", "km", 1000.0),
            UnitType("Centimeter", "cm", 0.01),
            UnitType("Millimeter", "mm", 0.001),
            UnitType("Inch", "in", 0.0254),
            UnitType("Foot", "ft", 0.3048),
            UnitType("Yard", "yd", 0.9144),
            UnitType("Mile", "mi", 1609.344)
        ),
        UnitCategory.WEIGHT to listOf(
            UnitType("Kilogram", "kg", 1.0),
            UnitType("Gram", "g", 0.001),
            UnitType("Milligram", "mg", 0.000001),
            UnitType("Pound", "lb", 0.45359237),
            UnitType("Ounce", "oz", 0.0283495231)
        ),
        UnitCategory.TEMPERATURE to listOf(
            UnitType("Celsius", "°C", 1.0),
            UnitType("Fahrenheit", "°F", 1.0),
            UnitType("Kelvin", "K", 1.0)
        ),
        UnitCategory.AREA to listOf(
            UnitType("Square Meter", "m²", 1.0),
            UnitType("Square Kilometer", "km²", 1000000.0),
            UnitType("Square Centimeter", "cm²", 0.0001),
            UnitType("Square Inch", "in²", 0.00064516),
            UnitType("Square Foot", "ft²", 0.09290304),
            UnitType("Acre", "ac", 4046.85642),
            UnitType("Hectare", "ha", 10000.0)
        ),
        UnitCategory.VOLUME to listOf(
            UnitType("Liter", "L", 1.0),
            UnitType("Milliliter", "mL", 0.001),
            UnitType("Gallon", "gal", 3.78541178),
            UnitType("Quart", "qt", 0.946352946),
            UnitType("Pint", "pt", 0.473176473),
            UnitType("Cup", "cup", 0.236588236),
            UnitType("Cubic Meter", "m³", 1000.0)
        )
    )

    private val _currentUnitCategory = MutableStateFlow(UnitCategory.LENGTH)
    val currentUnitCategory: StateFlow<UnitCategory> = _currentUnitCategory.asStateFlow()

    private val _fromUnit = MutableStateFlow(unitCategories[UnitCategory.LENGTH]!![0])
    val fromUnit: StateFlow<UnitType> = _fromUnit.asStateFlow()

    private val _toUnit = MutableStateFlow(unitCategories[UnitCategory.LENGTH]!![1])
    val toUnit: StateFlow<UnitType> = _toUnit.asStateFlow()

    private val _fromUnitValue = MutableStateFlow("1.0")
    val fromUnitValue: StateFlow<String> = _fromUnitValue.asStateFlow()

    private val _toUnitValue = MutableStateFlow("1.0")
    val toUnitValue: StateFlow<String> = _toUnitValue.asStateFlow()

    fun selectUnitCategory(category: UnitCategory) {
        _currentUnitCategory.value = category
        val units = unitCategories[category] ?: emptyList()
        if (units.isNotEmpty()) {
            _fromUnit.value = units[0]
            _toUnit.value = if (units.size > 1) units[1] else units[0]
        }
        updateUnitConversion()
    }

    fun setFromUnit(unit: UnitType) {
        _fromUnit.value = unit
        updateUnitConversion()
    }

    fun setToUnit(unit: UnitType) {
        _toUnit.value = unit
        updateUnitConversion()
    }

    fun setFromUnitValue(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' || it == '-' }
        if (filtered.count { it == '.' } <= 1 && filtered.count { it == '-' } <= 1) {
            _fromUnitValue.value = filtered
            updateUnitConversion()
        }
    }

    fun swapUnits() {
        val temp = _fromUnit.value
        _fromUnit.value = _toUnit.value
        _toUnit.value = temp
        updateUnitConversion()
    }

    fun updateUnitConversion() {
        val amountStr = _fromUnitValue.value
        if (amountStr.isEmpty() || amountStr == "." || amountStr == "-") {
            _toUnitValue.value = "0"
            return
        }

        val amount = amountStr.toDoubleOrNull() ?: 0.0
        val cat = _currentUnitCategory.value
        val from = _fromUnit.value
        val to = _toUnit.value

        if (cat == UnitCategory.TEMPERATURE) {
            val converted = convertTemperature(amount, from.name, to.name)
            _toUnitValue.value = formatUnitResult(converted)
        } else {
            val inAnchor = amount * from.factorToAnchor
            val converted = inAnchor / to.factorToAnchor
            _toUnitValue.value = formatUnitResult(converted)
        }
    }

    private fun convertTemperature(value: Double, from: String, to: String): Double {
        val celsius = when (from) {
            "Celsius" -> value
            "Fahrenheit" -> (value - 32) * 5 / 9
            "Kelvin" -> value - 273.15
            else -> value
        }
        return when (to) {
            "Celsius" -> celsius
            "Fahrenheit" -> (celsius * 9 / 5) + 32
            "Kelvin" -> celsius + 273.15
            else -> celsius
        }
    }

    private fun formatUnitResult(value: Double): String {
        if (value.isNaN()) return "0"
        if (value.isInfinite()) return "0"
        if (value % 1.0 == 0.0) {
            return value.toLong().toString()
        }
        return String.format(Locale.US, "%.6f", value).trimEnd('0').let {
            if (it.endsWith(".")) it.dropLast(1) else it
        }
    }

    val currencyNames = mapOf(
        "USD" to "US Dollar", "EUR" to "Euro", "GBP" to "British Pound", "JPY" to "Japanese Yen",
        "AUD" to "Australian Dollar", "CAD" to "Canadian Dollar", "CHF" to "Swiss Franc", "CNY" to "Chinese Yuan",
        "SEK" to "Swedish Krona", "NZD" to "New Zealand Dollar", "MXN" to "Mexican Peso", "SGD" to "Singapore Dollar",
        "HKD" to "Hong Kong Dollar", "NOK" to "Norwegian Krone", "KRW" to "South Korean Won", "TRY" to "Turkish Lira",
        "INR" to "Indian Rupee", "RUB" to "Russian Ruble", "BRL" to "Brazilian Real", "ZAR" to "South African Rand",
        "AED" to "UAE Dirham", "ILS" to "Israeli Shekel", "AFN" to "Afghan Afghani", "ALL" to "Albanian Lek",
        "AMD" to "Armenian Dram", "ANG" to "Netherlands Antillean Guilder", "AOA" to "Angolan Kwanza", "ARS" to "Argentine Peso",
        "AWG" to "Aruban Florin", "AZN" to "Azerbaijani Manat", "BAM" to "Bosnia Convertible Mark", "BBD" to "Barbadian Dollar",
        "BDT" to "Bangladeshi Taka", "BGN" to "Bulgarian Lev", "BHD" to "Bahraini Dinar", "BIF" to "Burundian Franc",
        "BMD" to "Bermudian Dollar", "BND" to "Brunei Dollar", "BOB" to "Bolivian Boliviano", "BSD" to "Bahamian Dollar",
        "BTN" to "Bhutanese Ngultrum", "BWP" to "Botswanan Pula", "BYN" to "Belarusian Ruble", "BZD" to "Belize Dollar",
        "CDF" to "Congolese Franc", "CLP" to "Chilean Peso", "COP" to "Colombian Peso", "CRC" to "Costa Rican Colón",
        "CUP" to "Cuban Peso", "CVE" to "Cape Verdean Escudo", "CZK" to "Czech Koruna", "DJF" to "Djiboutian Franc",
        "DKK" to "Danish Krone", "DOP" to "Dominican Peso", "DZD" to "Algerian Dinar", "EGP" to "Egyptian Pound",
        "ERN" to "Eritrean Nakfa", "ETB" to "Ethiopian Birr", "FJD" to "Fijian Dollar", "FKP" to "Falkland Islands Pound",
        "FOK" to "Faroese Króna", "GEL" to "Georgian Lari", "GGP" to "Guernsey Pound", "GHS" to "Ghanaian Cedi",
        "GIP" to "Gibraltar Pound", "GMD" to "Gambian Dalasi", "GNF" to "Guinean Franc", "GTQ" to "Guatemalan Quetzal",
        "GYD" to "Guyanese Dollar", "HNL" to "Honduran Lempira", "HRK" to "Croatian Kuna", "HTG" to "Haitian Gourde",
        "HUF" to "Hungarian Forint", "IDR" to "Indonesian Rupiah", "IMP" to "Manx Pound", "IQD" to "Iraqi Dinar",
        "IRR" to "Iranian Rial", "ISK" to "Icelandic Króna", "JEP" to "Jersey Pound", "JMD" to "Jamaican Dollar",
        "JOD" to "Jordanian Dinar", "KES" to "Kenyan Shilling", "KGS" to "Kyrgystani Som", "KHR" to "Cambodian Riel",
        "KID" to "Kiribati Dollar", "KMF" to "Comorian Franc", "KPW" to "North Korean Won", "KWD" to "Kuwaiti Dinar",
        "KYD" to "Cayman Islands Dollar", "KZT" to "Kazakhstani Tenge", "LAK" to "Laotian Kip", "LBP" to "Lebanese Pound",
        "LKR" to "Sri Lankan Rupee", "LRD" to "Liberian Dollar", "LSL" to "Lesotho Loti", "LYD" to "Libyan Dinar",
        "MAD" to "Moroccan Dirham", "MDL" to "Moldovan Leu", "MGA" to "Malagasy Ariary", "MKD" to "Macedonian Denar",
        "MMK" to "Myanmar Kyat", "MNT" to "Mongolian Tögrög", "MOP" to "Macanese Pataca", "MRU" to "Mauritanian Ouguiya",
        "MUR" to "Mauritian Rupee", "MVR" to "Maldivian Rufiyaa", "MWK" to "Malawian Kwacha", "MYR" to "Malaysian Ringgit",
        "MZN" to "Mozambican Metical", "NAD" to "Namibian Dollar", "NGN" to "Nigerian Naira", "NIO" to "Nicaraguan Córdoba",
        "NPR" to "Nepalese Rupee", "OMR" to "Omani Rial", "PAB" to "Panamanian Balboa", "PEN" to "Peruvian Sol",
        "PGK" to "Papua New Guinean Kina", "PHP" to "Philippine Peso", "PKR" to "Pakistani Rupee", "PLN" to "Polish Złoty",
        "PYG" to "Paraguayan Guaraní", "QAR" to "Qatari Riyal", "RON" to "Romanian Leu", "RSD" to "Serbian Dinar",
        "RWF" to "Rwandan Franc", "SAR" to "Saudi Riyal", "SBD" to "Solomon Islands Dollar", "SCR" to "Seychellois Rupee",
        "SDG" to "Sudanese Pound", "SHP" to "St. Helena Pound", "SLE" to "Sierra Leonean Leone", "SLL" to "Sierra Leonean Leone",
        "SOS" to "Somali Shilling", "SRD" to "Surinamese Dollar", "SSP" to "South Sudanese Pound", "STN" to "São Tomé Dobra",
        "SYP" to "Syrian Pound", "SZL" to "Swazi Lilangeni", "THB" to "Thai Baht", "TJS" to "Tajikistani Somoni",
        "TMT" to "Turkmenistani Manat", "TND" to "Tunisian Dinar", "TOP" to "Tongan Paʻanga", "TTD" to "Trinidad Dollar",
        "TVD" to "Tuvaluan Dollar", "TWD" to "New Taiwan Dollar", "TZS" to "Tanzanian Shilling", "UAH" to "Ukrainian Hryvnia",
        "UGX" to "Ugandan Shilling", "UYU" to "Uruguayan Peso", "UZS" to "Uzbekistani Som", "VES" to "Venezuelan Bolívar",
        "VND" to "Vietnamese Đồng", "VUV" to "Vanuatu Vatu", "WST" to "Samoan Tālā", "XAF" to "Central African Franc",
        "XCD" to "East Caribbean Dollar", "XOF" to "West African Franc", "XPF" to "CFP Franc", "YER" to "Yemeni Rial",
        "ZMW" to "Zambian Kwacha", "ZWL" to "Zimbabwean Dollar"
    )

    fun getFlagEmoji(currencyCode: String): String {
        val specialFlags = mapOf(
            "EUR" to "🇪🇺", "BTC" to "🪙", "XOF" to "🌍", "XAF" to "🌍",
            "XCD" to "🏝️", "XPF" to "🇵🇫", "ANG" to "🇸🇽", "XDR" to "🌐"
        )
        if (specialFlags.containsKey(currencyCode)) {
            return specialFlags[currencyCode]!!
        }
        if (currencyCode.length < 2) return "🏳️"
        val countryCode = currencyCode.substring(0, 2).uppercase()
        return try {
            val firstChar = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
            val secondChar = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
            String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
        } catch (e: Exception) {
            "🏳️"
        }
    }

    fun getCurrencySymbol(code: String): String {
        return when (code) {
            "USD", "CAD", "AUD", "NZD", "MXN", "HKD", "SGD", "ARS", "CLP", "COP", "FJD" -> "$"
            "EUR" -> "€"
            "GBP", "FKP", "GIP", "SHP" -> "£"
            "JPY", "CNY" -> "¥"
            "INR" -> "₹"
            "KRW" -> "₩"
            "RUB" -> "₽"
            "BRL" -> "R$"
            "TRY" -> "₺"
            "ILS" -> "₪"
            "ZAR" -> "R"
            "AED" -> "د.إ"
            "CHF" -> "Fr"
            "SEK", "NOK", "DKK" -> "kr"
            else -> code
        }
    }

    // Fallback static rate table relative to USD
    private val fallbackRates = mapOf(
        "USD" to 1.0, "EUR" to 0.92, "GBP" to 0.78, "JPY" to 158.5, "AUD" to 1.50,
        "CAD" to 1.37, "CHF" to 0.90, "CNY" to 7.25, "SEK" to 10.50, "NZD" to 1.63,
        "MXN" to 18.20, "SGD" to 1.35, "HKD" to 7.80, "NOK" to 10.60, "KRW" to 1380.0,
        "TRY" to 32.50, "INR" to 83.50, "RUB" to 88.0, "BRL" to 5.40, "ZAR" to 18.0,
        "AED" to 3.67, "ILS" to 3.70, "DZD" to 134.5, "ARS" to 915.0, "AZN" to 1.70,
        "BHD" to 0.38, "BDT" to 117.5, "BYN" to 3.27, "BOB" to 6.90, "BAM" to 1.80,
        "BGN" to 1.80, "KHR" to 4100.0, "CLP" to 930.0, "COP" to 4150.0, "CRC" to 525.0,
        "CZK" to 23.2, "DKK" to 6.90, "DOP" to 59.0, "EGP" to 47.8, "GEL" to 2.72,
        "GHS" to 14.9, "GTQ" to 7.76, "HNL" to 24.7, "HUF" to 367.0, "ISK" to 139.0,
        "IDR" to 16350.0, "IQD" to 1310.0, "JMD" to 156.0, "JOD" to 0.71, "KZT" to 460.0,
        "KES" to 129.0, "KWD" to 0.31, "LAK" to 21900.0, "LBP" to 89500.0, "LYD" to 4.85,
        "MKD" to 56.6, "MYR" to 4.71, "MDL" to 17.8, "MAD" to 9.95, "NPR" to 133.5,
        "NIO" to 36.8, "NGN" to 1490.0, "OMR" to 0.39, "PKR" to 278.0, "PAB" to 1.0,
        "PGK" to 3.89, "PYG" to 7520.0, "PEN" to 3.80, "PHP" to 58.7, "PLN" to 4.02,
        "QAR" to 3.64, "RON" to 4.58, "SAR" to 3.75, "XOF" to 605.0, "RSD" to 108.0,
        "LKR" to 305.0, "TWD" to 32.4, "TZS" to 2620.0, "THB" to 36.7, "TND" to 3.12,
        "UGX" to 3730.0, "UAH" to 40.6, "UYU" to 39.3, "VES" to 36.4, "VND" to 25450.0,
        "YER" to 250.0, "ZWL" to 30.0
    )

    private val _rates = MutableStateFlow<Map<String, Double>>(fallbackRates)
    val rates: StateFlow<Map<String, Double>> = _rates.asStateFlow()

    private val _fromCurrency = MutableStateFlow(initialCurrencies[0]) // USD
    val fromCurrency: StateFlow<Currency> = _fromCurrency.asStateFlow()

    private val _toCurrency = MutableStateFlow(initialCurrencies[1]) // EUR
    val toCurrency: StateFlow<Currency> = _toCurrency.asStateFlow()

    private val _fromAmount = MutableStateFlow("1.00")
    val fromAmount: StateFlow<String> = _fromAmount.asStateFlow()

    private val _toAmount = MutableStateFlow("0.92")
    val toAmount: StateFlow<String> = _toAmount.asStateFlow()

    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Idle)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("currency_prefs", Context.MODE_PRIVATE)

    init {
        // Correct the HKD flag emoji in the map list (HKD is 🇭🇰, the list above has a typo in flag string 🇬🇰, let's make sure it is correct 🇭🇰)
        loadCachedRates()
        fetchLiveRates()
    }

    fun setFromCurrency(currency: Currency) {
        _fromCurrency.value = currency
        updateConversion()
    }

    fun setToCurrency(currency: Currency) {
        _toCurrency.value = currency
        updateConversion()
    }

    fun setFromAmount(amount: String) {
        // Prevent typing non-numeric things, multiple decimals
        val filtered = amount.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } <= 1) {
            _fromAmount.value = filtered
            updateConversion()
        }
    }

    fun swapCurrencies() {
        val temp = _fromCurrency.value
        _fromCurrency.value = _toCurrency.value
        _toCurrency.value = temp
        updateConversion()
    }

    private fun updateConversion() {
        val amountStr = _fromAmount.value
        if (amountStr.isEmpty() || amountStr == ".") {
            _toAmount.value = "0.00"
            return
        }

        val amount = amountStr.toDoubleOrNull() ?: 0.0
        val currentRates = _rates.value

        val rateFrom = currentRates[_fromCurrency.value.code] ?: fallbackRates[_fromCurrency.value.code] ?: 1.0
        val rateTo = currentRates[_toCurrency.value.code] ?: fallbackRates[_toCurrency.value.code] ?: 1.0

        // Amount / rateFrom = USD. USD * rateTo = target currency
        val converted = amount * (rateTo / rateFrom)
        _toAmount.value = String.format(Locale.US, "%.4f", converted)
            .trimEnd('0')
            .let {
                if (it.endsWith(".")) it + "00"
                else if (it.substringAfter('.', "").length < 2) it + "0"
                else it
            }
    }

    fun fetchLiveRates() {
        _networkState.value = NetworkState.Loading
        viewModelScope.launch {
            try {
                val fetchedJson = withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val request = Request.Builder()
                        .url("https://open.er-api.com/v6/latest/USD")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("Unexpected response code: $response")
                        response.body?.string() ?: throw Exception("Response body is empty")
                    }
                }

                // Parse rates
                val jsonObject = JSONObject(fetchedJson)
                val ratesObj = jsonObject.getJSONObject("rates")
                val keys = ratesObj.keys()
                val dynamicCurrencies = mutableListOf<Currency>()
                val newRates = mutableMapOf<String, Double>()

                while (keys.hasNext()) {
                    val key = keys.next() as String
                    val rate = ratesObj.getDouble(key)
                    newRates[key] = rate

                    val name = currencyNames[key] ?: "$key Currency"
                    val flag = getFlagEmoji(key)
                    val symbol = getCurrencySymbol(key)
                    dynamicCurrencies.add(Currency(key, name, flag, symbol))
                }
                dynamicCurrencies.sortBy { it.code }

                val now = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date())

                // Save to cache
                sharedPrefs.edit().apply {
                    putString("last_updated_time", now)
                    putBoolean("has_cache", true)
                    putString("raw_rates_json", fetchedJson)
                    apply()
                }

                _rates.value = newRates
                _availableCurrencies.value = dynamicCurrencies
                _isOfflineMode.value = false
                _networkState.value = NetworkState.Success("Updated: $now")
                
                // Align selected from/to currencies if they aren't in the new list (which is rare but safe)
                val fromCode = _fromCurrency.value.code
                val toCode = _toCurrency.value.code
                val matchedFrom = dynamicCurrencies.find { it.code == fromCode }
                val matchedTo = dynamicCurrencies.find { it.code == toCode }
                if (matchedFrom != null) _fromCurrency.value = matchedFrom
                if (matchedTo != null) _toCurrency.value = matchedTo

                updateConversion()

            } catch (e: Exception) {
                // Network call failed, attempt to fall back to cached rates or fallback rates
                val hasCache = sharedPrefs.getBoolean("has_cache", false)
                if (hasCache) {
                    val cachedTime = sharedPrefs.getString("last_updated_time", "") ?: "Unknown"
                    _isOfflineMode.value = false
                    _networkState.value = NetworkState.Success("Cached: $cachedTime")
                } else {
                    _isOfflineMode.value = true
                    _rates.value = fallbackRates
                    _availableCurrencies.value = initialCurrencies
                    _networkState.value = NetworkState.Error("Failed to load live rates. Using offline rates.")
                }
                updateConversion()
            }
        }
    }

    private fun loadCachedRates() {
        val hasCache = sharedPrefs.getBoolean("has_cache", false)
        val cachedJson = sharedPrefs.getString("raw_rates_json", "") ?: ""
        if (hasCache && cachedJson.isNotEmpty()) {
            try {
                val jsonObject = JSONObject(cachedJson)
                val ratesObj = jsonObject.getJSONObject("rates")
                val keys = ratesObj.keys()
                val dynamicCurrencies = mutableListOf<Currency>()
                val cachedRates = mutableMapOf<String, Double>()

                while (keys.hasNext()) {
                    val key = keys.next() as String
                    val rate = ratesObj.getDouble(key)
                    cachedRates[key] = rate

                    val name = currencyNames[key] ?: "$key Currency"
                    val flag = getFlagEmoji(key)
                    val symbol = getCurrencySymbol(key)
                    dynamicCurrencies.add(Currency(key, name, flag, symbol))
                }
                dynamicCurrencies.sortBy { it.code }

                _rates.value = cachedRates
                _availableCurrencies.value = dynamicCurrencies

                val cachedTime = sharedPrefs.getString("last_updated_time", "") ?: "Unknown"
                _isOfflineMode.value = false
                _networkState.value = NetworkState.Success("Cached: $cachedTime")
                
                // Align current selections
                val fromCode = _fromCurrency.value.code
                val toCode = _toCurrency.value.code
                val matchedFrom = dynamicCurrencies.find { it.code == fromCode }
                val matchedTo = dynamicCurrencies.find { it.code == toCode }
                if (matchedFrom != null) _fromCurrency.value = matchedFrom
                if (matchedTo != null) _toCurrency.value = matchedTo

                updateConversion()
                return
            } catch (e: Exception) {
                // fall through if parsing fails
            }
        }

        _rates.value = fallbackRates
        _availableCurrencies.value = initialCurrencies
        _isOfflineMode.value = true
        _networkState.value = NetworkState.Idle
        updateConversion()
    }
}


// --- Mathematical Expression Parser ---
class MathParser(private val str: String) {
    private var pos = -1
    private var ch = 0

    private fun nextChar() {
        ch = if (++pos < str.length) str[pos].code else -1
    }

    private fun eat(charToEat: Int): Boolean {
        while (ch == ' '.code) nextChar()
        if (ch == charToEat) {
            nextChar()
            return true
        }
        return false
    }

    fun parse(): Double {
        nextChar()
        val x = parseExpression()
        if (pos < str.length) throw RuntimeException("Unexpected character: " + ch.toChar())
        return x
    }

    private fun parseExpression(): Double {
        var x = parseTerm()
        while (true) {
            if (eat('+'.code)) x += parseTerm() // addition
            else if (eat('-'.code)) x -= parseTerm() // subtraction
            else break
        }
        return x
    }

    private fun parseTerm(): Double {
        var x = parsePower()
        while (true) {
            if (eat('*'.code)) x *= parsePower() // multiplication
            else if (eat('/'.code)) {
                val next = parsePower()
                if (next == 0.0) throw ArithmeticException("Can't divide by 0")
                x /= next // division
            } else if (eat('%'.code)) {
                val next = parsePower()
                if (next == 0.0) throw ArithmeticException("Can't divide by 0")
                x %= next // Modulo
            } else break
        }
        return x
    }

    private fun parsePower(): Double {
        var x = parseFactor()
        while (true) {
            if (eat('^'.code)) x = java.lang.Math.pow(x, parseFactor())
            else break
        }
        return x
    }

    private fun parseFactor(): Double {
        if (eat('+'.code)) return parseFactor() // unary plus
        if (eat('-'.code)) return -parseFactor() // unary minus
        if (eat('√'.code)) {
            val v = parseFactor()
            if (v < 0.0) throw ArithmeticException("Negative root")
            return kotlin.math.sqrt(v)
        }

        var x: Double
        val startPos = this.pos
        if (eat('('.code)) { // parentheses
            x = parseExpression()
            eat(')'.code)
        } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) { // numbers
            while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
            val numStr = str.substring(startPos, this.pos)
            x = numStr.toDouble()
        } else if ((ch >= 'a'.code && ch <= 'z'.code) || ch == 'π'.code || ch == 'e'.code) {
            // Letters for functions, or constants
            while ((ch >= 'a'.code && ch <= 'z'.code)) nextChar()
            val name = str.substring(startPos, this.pos)
            if (name.isEmpty() && ch == 'π'.code) {
                nextChar()
                x = kotlin.math.PI
            } else if (name.isEmpty() && ch == 'e'.code) {
                nextChar()
                x = kotlin.math.E
            } else if (name == "π" || name == "pi") {
                x = kotlin.math.PI
            } else if (name == "e") {
                x = kotlin.math.E
            } else {
                // Must be a function, check if followed by '('
                if (eat('('.code)) {
                    val arg = parseExpression()
                    eat(')'.code)
                    x = when (name) {
                        "sin" -> kotlin.math.sin(Math.toRadians(arg))
                        "cos" -> kotlin.math.cos(Math.toRadians(arg))
                        "tan" -> {
                            val rad = Math.toRadians(arg)
                            if (kotlin.math.abs(kotlin.math.cos(rad)) < 1e-10) throw ArithmeticException("Tangent undefined")
                            kotlin.math.tan(rad)
                        }
                        "log" -> kotlin.math.log10(arg)
                        "ln" -> kotlin.math.ln(arg)
                        else -> throw RuntimeException("Unknown function: $name")
                    }
                } else {
                    throw RuntimeException("Unknown identifier: $name")
                }
            }
        } else {
            throw RuntimeException("Unexpected character: " + ch.toChar())
        }

        return x
    }
}
