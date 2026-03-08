package com.example.calc

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.calc.databinding.ActivityMainBinding
import kotlin.math.ln
import kotlin.math.log10

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // The expression string being built (e.g. "12+34")
    private val expressionBuilder = StringBuilder()

    // When true, the next digit press will start a fresh number after an operator
    private var lastWasResult = false

    // Pending unary function: "log" or "ln", or null
    private var pendingUnary: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
        updateDisplays()
    }

    private fun setupButtons() {
        // Digit buttons
        val digitIds = mapOf(
            R.id.btn0 to "0",
            R.id.btn1 to "1",
            R.id.btn2 to "2",
            R.id.btn3 to "3",
            R.id.btn4 to "4",
            R.id.btn5 to "5",
            R.id.btn6 to "6",
            R.id.btn7 to "7",
            R.id.btn8 to "8",
            R.id.btn9 to "9",
            R.id.btnDot to "."
        )

        for ((id, value) in digitIds) {
            findViewById<Button>(id).setOnClickListener { onDigitOrDot(value) }
        }

        // Operator buttons
        findViewById<Button>(R.id.btnAdd).setOnClickListener { onOperator("+") }
        findViewById<Button>(R.id.btnSub).setOnClickListener { onOperator("-") }
        findViewById<Button>(R.id.btnMul).setOnClickListener { onOperator("×") }
        findViewById<Button>(R.id.btnDiv).setOnClickListener { onOperator("÷") }

        // Unary function buttons
        findViewById<Button>(R.id.btnLog).setOnClickListener { onUnary("log") }
        findViewById<Button>(R.id.btnLn).setOnClickListener { onUnary("ln") }

        // Control buttons
        findViewById<Button>(R.id.btnEquals).setOnClickListener { onEquals() }
        findViewById<Button>(R.id.btnClear).setOnClickListener { onClear() }
        findViewById<Button>(R.id.btnDelete).setOnClickListener { onDelete() }
    }

    private fun onDigitOrDot(value: String) {
        if (lastWasResult) {
            // After a result, a digit starts a fresh expression
            expressionBuilder.clear()
            lastWasResult = false
        }

        // Prevent multiple dots in the current number segment
        if (value == ".") {
            val currentNumber = getCurrentNumberSegment()
            if (currentNumber.contains(".")) return
            // If nothing typed yet or last char is operator, prepend "0"
            if (expressionBuilder.isEmpty() || isOperatorChar(expressionBuilder.last())) {
                expressionBuilder.append("0")
            }
        }

        expressionBuilder.append(value)
        updateDisplays()
    }

    private fun onOperator(op: String) {
        pendingUnary = null

        if (lastWasResult) {
            lastWasResult = false
        }

        if (expressionBuilder.isEmpty()) {
            // Allow leading minus for negative numbers
            if (op == "-") {
                expressionBuilder.append(op)
                updateDisplays()
            }
            return
        }

        // Replace trailing operator if already there
        if (isOperatorChar(expressionBuilder.last())) {
            expressionBuilder.deleteCharAt(expressionBuilder.length - 1)
        }

        // Don't append operator if expression ends with a dot
        if (expressionBuilder.last() == '.') {
            expressionBuilder.append("0")
        }

        expressionBuilder.append(op)
        updateDisplays()
    }

    private fun onUnary(func: String) {
        // Clear previous state and set up for unary input
        expressionBuilder.clear()
        pendingUnary = func
        lastWasResult = false
        binding.tvExpression.text = "$func("
        binding.tvResult.text = ""
    }

    private fun onEquals() {
        val expr = expressionBuilder.toString()
        if (expr.isEmpty()) return

        try {
            val result: Double = if (pendingUnary != null) {
                val arg = expr.toDouble()
                when (pendingUnary) {
                    "log" -> {
                        if (arg <= 0) throw ArithmeticException("log of non-positive")
                        log10(arg)
                    }
                    "ln" -> {
                        if (arg <= 0) throw ArithmeticException("ln of non-positive")
                        ln(arg)
                    }
                    else -> throw IllegalStateException("Unknown function")
                }
            } else {
                evaluateExpression(expr)
            }

            val displayExpr = if (pendingUnary != null) {
                "${pendingUnary}($expr)"
            } else {
                expr
            }

            binding.tvExpression.text = "$displayExpr ="
            binding.tvResult.text = formatResult(result)

            // Prepare for next input starting from result
            expressionBuilder.clear()
            expressionBuilder.append(formatResult(result))
            pendingUnary = null
            lastWasResult = true

        } catch (e: ArithmeticException) {
            binding.tvExpression.text = expr
            binding.tvResult.text = getString(R.string.error)
            expressionBuilder.clear()
            pendingUnary = null
            lastWasResult = false
        } catch (e: Exception) {
            binding.tvExpression.text = expr
            binding.tvResult.text = getString(R.string.error)
            expressionBuilder.clear()
            pendingUnary = null
            lastWasResult = false
        }
    }

    private fun onClear() {
        expressionBuilder.clear()
        pendingUnary = null
        lastWasResult = false
        binding.tvExpression.text = ""
        binding.tvResult.text = "0"
    }

    private fun onDelete() {
        if (lastWasResult) {
            onClear()
            return
        }
        if (expressionBuilder.isNotEmpty()) {
            expressionBuilder.deleteCharAt(expressionBuilder.length - 1)
            updateDisplays()
        }
    }

    private fun updateDisplays() {
        if (pendingUnary != null) {
            binding.tvExpression.text = "${pendingUnary}(${expressionBuilder})"
        } else {
            binding.tvExpression.text = expressionBuilder.toString()
        }
        if (expressionBuilder.isEmpty()) {
            binding.tvResult.text = "0"
        } else {
            // Show a live preview if possible
            try {
                val expr = expressionBuilder.toString()
                // Only preview if expression looks complete (doesn't end with operator)
                if (expr.isNotEmpty() && !isOperatorChar(expr.last()) && expr != "-") {
                    val preview = evaluateExpression(expr)
                    binding.tvResult.text = formatResult(preview)
                } else {
                    binding.tvResult.text = ""
                }
            } catch (e: Exception) {
                binding.tvResult.text = ""
            }
        }
    }

    /**
     * Evaluates a simple infix expression supporting +, -, ×, ÷.
     * Handles operator precedence: × and ÷ before + and -.
     */
    private fun evaluateExpression(expr: String): Double {
        val tokens = tokenize(expr)
        if (tokens.isEmpty()) throw IllegalArgumentException("Empty expression")
        return parseAddSub(tokens, 0).first
    }

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            val ch = expr[i]
            when {
                ch == '-' && (i == 0 || isOperatorChar(expr[i - 1])) -> {
                    // Unary minus: consume number
                    val sb = StringBuilder("-")
                    i++
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
                        sb.append(expr[i++])
                    }
                    tokens.add(sb.toString())
                }
                ch.isDigit() || ch == '.' -> {
                    val sb = StringBuilder()
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
                        sb.append(expr[i++])
                    }
                    tokens.add(sb.toString())
                }
                isOperatorChar(ch) -> {
                    tokens.add(ch.toString())
                    i++
                }
                else -> i++
            }
        }
        return tokens
    }

    private fun parseAddSub(tokens: List<String>, pos: Int): Pair<Double, Int> {
        var (left, idx) = parseMulDiv(tokens, pos)
        while (idx < tokens.size && (tokens[idx] == "+" || tokens[idx] == "-")) {
            val op = tokens[idx]
            val (right, newIdx) = parseMulDiv(tokens, idx + 1)
            left = if (op == "+") left + right else left - right
            idx = newIdx
        }
        return Pair(left, idx)
    }

    private fun parseMulDiv(tokens: List<String>, pos: Int): Pair<Double, Int> {
        var (left, idx) = parseNumber(tokens, pos)
        while (idx < tokens.size && (tokens[idx] == "×" || tokens[idx] == "÷")) {
            val op = tokens[idx]
            val (right, newIdx) = parseNumber(tokens, idx + 1)
            left = if (op == "×") {
                left * right
            } else {
                if (right == 0.0) throw ArithmeticException("Division by zero")
                left / right
            }
            idx = newIdx
        }
        return Pair(left, idx)
    }

    private fun parseNumber(tokens: List<String>, pos: Int): Pair<Double, Int> {
        if (pos >= tokens.size) throw IllegalArgumentException("Expected number")
        val token = tokens[pos]
        return Pair(token.toDouble(), pos + 1)
    }

    private fun getCurrentNumberSegment(): String {
        val expr = expressionBuilder.toString()
        var start = expr.length
        for (i in expr.indices.reversed()) {
            if (isOperatorChar(expr[i])) {
                start = i + 1
                break
            }
            if (i == 0) start = 0
        }
        return expr.substring(start)
    }

    private fun isOperatorChar(ch: Char): Boolean =
        ch == '+' || ch == '-' || ch == '×' || ch == '÷'

    private fun formatResult(value: Double): String {
        return if (value == value.toLong().toDouble() && !value.isInfinite()) {
            value.toLong().toString()
        } else {
            // Trim trailing zeros up to 10 decimal places
            "%.10f".format(value).trimEnd('0').trimEnd('.')
        }
    }
}
