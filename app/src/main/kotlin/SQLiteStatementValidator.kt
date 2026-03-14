// SQLiteStatementValidator.kt
package za.co.jpsoft.winkerkreader

import java.util.*
import java.util.regex.Pattern

object SQLiteStatementValidator {

    // SQLite keywords that should be followed by specific patterns
    private val SQLITE_KEYWORDS = setOf(
        "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
        "DELETE", "CREATE", "TABLE", "ALTER", "DROP", "INDEX", "VIEW", "TRIGGER",
        "ORDER", "BY", "GROUP", "HAVING", "LIMIT", "OFFSET", "JOIN", "INNER",
        "LEFT", "RIGHT", "FULL", "OUTER", "ON", "AS", "AND", "OR", "NOT",
        "BETWEEN", "IN", "EXISTS", "IS", "NULL", "DISTINCT", "UNION",
        "INTERSECT", "EXCEPT", "CASE", "WHEN", "THEN", "ELSE", "END"
    )

    // Characters that are typically illegal immediately after keywords
    // Removed '%' as it's valid for LIKE operator
    private val ILLEGAL_CHARS_AFTER_KEYWORDS = setOf(
        ',', '.', ';', ')', '}', ']', '!', '@', '#', '$', '^', '&', '*',
        '+', '|', '\\', ':', '"', '\'', '<', '>', '?', '/', '~', '`'
        // NOTE: '=' is intentionally NOT in this list as it's valid after many keywords
    )

    // Keywords that can be followed by parentheses
    private val KEYWORDS_ALLOWING_PARENTHESES = setOf(
        "VALUES", "IN", "EXISTS", "COUNT", "SUM", "AVG", "MIN", "MAX", "SUBSTR",
        "LENGTH", "UPPER", "LOWER", "TRIM", "COALESCE", "IFNULL", "NULLIF",
        "DATE", "TIME", "DATETIME", "STRFTIME", "CAST", "ROUND", "ABS"
    )

    // Operators that are valid SQL constructs
    private val VALID_OPERATORS = setOf(
        "LIKE", "=", "!=", "<>", ">", "<", ">=", "<=", "||"
    )

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val errorPosition: Int = -1,
        val fixedSql: String? = null,
        val wasFixed: Boolean = false
    ) {
        override fun toString(): String {
            return if (isValid) {
                if (wasFixed) "SQL statement was fixed and is now valid" else "SQL statement is valid"
            } else {
                "Invalid SQL at position $errorPosition: $errorMessage"
            }
        }
    }

    /**
     * Validates and fixes a SQLite statement by removing illegal characters after keywords
     */
    @JvmStatic
    fun validateAndFixSQLiteStatement(sqlStatement: String?): ValidationResult {
        if (sqlStatement.isNullOrBlank()) {
            return ValidationResult(false, "SQL statement cannot be null or empty", 0)
        }

        val originalSql = sqlStatement.trim()
        var wasFixed = false

        try {
            val fixedSql = fixSQLiteStatement(originalSql)
            wasFixed = fixedSql != originalSql
            val validationResult = validateSQLiteStatement(fixedSql)
            return ValidationResult(
                isValid = validationResult.isValid,
                errorMessage = validationResult.errorMessage,
                errorPosition = validationResult.errorPosition,
                fixedSql = fixedSql,
                wasFixed = wasFixed
            )
        } catch (e: Exception) {
            return ValidationResult(false, "Error during validation: ${e.message}", 0, originalSql, false)
        }
    }

    /**
     * Validates a SQLite statement for illegal characters after keywords
     */
    @JvmStatic
    fun validateSQLiteStatement(sqlStatement: String?): ValidationResult {
        if (sqlStatement.isNullOrBlank()) {
            return ValidationResult(false, "SQL statement cannot be null or empty", 0)
        }

        val originalSql = sqlStatement.trim()

        return try {
            val cleanedSql = removeStringLiteralsAndComments(originalSql)
            val keywordValidation = validateKeywordSyntax(cleanedSql, originalSql)
            if (!keywordValidation.isValid) return keywordValidation

            val syntaxValidation = validateBasicSyntax(cleanedSql)
            if (!syntaxValidation.isValid) return syntaxValidation

            ValidationResult(true)
        } catch (e: Exception) {
            ValidationResult(false, "Error during validation: ${e.message}", 0)
        }
    }

    /**
     * Fixes common SQLite statement errors by removing illegal characters
     */
    @JvmStatic
    fun fixSQLiteStatement(sqlStatement: String?): String? {
        if (sqlStatement.isNullOrBlank()) return sqlStatement

        var sql = sqlStatement.trim()
        sql = fixIllegalCharactersAfterKeywords(sql)
        sql = fixTrailingCommas(sql)
        sql = fixConsecutiveCommas(sql)
        sql = cleanUpWhitespace(sql)
        return sql
    }

    private fun removeStringLiteralsAndComments(sql: String): String {
        val result = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var inBracket = false
        var inComment = false

        for (i in sql.indices) {
            val c = sql[i]
            val nextChar = if (i + 1 < sql.length) sql[i + 1] else '\u0000'

            when {
                !inSingleQuote && !inDoubleQuote && !inBracket && !inComment -> {
                    when {
                        c == '-' && nextChar == '-' -> {
                            inComment = true
                            result.append(' ')
                            continue
                        }
                        c == '\'' -> {
                            inSingleQuote = true
                            result.append(' ')
                            continue
                        }
                        c == '"' -> {
                            inDoubleQuote = true
                            result.append(' ')
                            continue
                        }
                        c == '[' -> {
                            inBracket = true
                            result.append(' ')
                            continue
                        }
                    }
                }
            }

            when {
                inComment && (c == '\n' || c == '\r') -> inComment = false
                inSingleQuote && c == '\'' -> {
                    inSingleQuote = false
                    result.append(' ')
                    continue
                }
                inDoubleQuote && c == '"' -> {
                    inDoubleQuote = false
                    result.append(' ')
                    continue
                }
                inBracket && c == ']' -> {
                    inBracket = false
                    result.append(' ')
                    continue
                }
            }

            if (inSingleQuote || inDoubleQuote || inBracket || inComment) {
                result.append(' ')
            } else {
                result.append(c)
            }
        }
        return result.toString()
    }

    private fun validateKeywordSyntax(cleanedSql: String, originalSql: String): ValidationResult {
        val upperCleanedSql = cleanedSql.uppercase(Locale.ROOT)
        val keywordPattern = Pattern.compile("\\b(${SQLITE_KEYWORDS.joinToString("|")})\\b")
        val matcher = keywordPattern.matcher(upperCleanedSql)

        while (matcher.find()) {
            val keyword = matcher.group(1)
            val keywordEnd = matcher.end()
            if (keywordEnd < upperCleanedSql.length) {
                var nextNonSpaceIndex = keywordEnd
                while (nextNonSpaceIndex < upperCleanedSql.length &&
                    upperCleanedSql[nextNonSpaceIndex].isWhitespace()
                ) {
                    nextNonSpaceIndex++
                }
                if (nextNonSpaceIndex < upperCleanedSql.length) {
                    val nextChar = upperCleanedSql[nextNonSpaceIndex]
                    if (isIllegalCharacterAfterKeyword(keyword, nextChar, originalSql, nextNonSpaceIndex)) {
                        return ValidationResult(false, "Illegal character '$nextChar' found after keyword '$keyword'", nextNonSpaceIndex)
                    }
                }
            }
        }
        return ValidationResult(true)
    }

    private fun isIllegalCharacterAfterKeyword(keyword: String, nextChar: Char, originalSql: String, position: Int): Boolean {
        if (nextChar == '(' && (KEYWORDS_ALLOWING_PARENTHESES.contains(keyword) || keyword == "SELECT")) {
            return false
        }
        if (nextChar == '=' || nextChar == '<' || nextChar == '>' || nextChar == '!') {
            return false
        }
        if (keyword == "LIKE") {
            if (nextChar == '\'' || nextChar == '"' || nextChar == '%' || nextChar == '_' || nextChar.isLetterOrDigit()) {
                return false
            }
        }
        return when (keyword) {
            "ORDER", "GROUP" -> nextChar != 'B'
            "IS" -> nextChar != 'N' && !nextChar.isLetter()
            "NOT" -> !nextChar.isLetter() && nextChar != '(' && nextChar != '='
            "AS", "BY", "FROM", "WHERE", "HAVING", "AND", "OR", "ON", "SET", "INTO" ->
                !nextChar.isLetterOrDigit() && nextChar != '\'' && nextChar != '"' && nextChar != '[' && nextChar != '(' && nextChar != '_'
            else -> ILLEGAL_CHARS_AFTER_KEYWORDS.contains(nextChar.lowercaseChar())
        }
    }

    private fun validateBasicSyntax(sql: String): ValidationResult {
        var parenCount = 0
        var bracketCount = 0
        for ((i, c) in sql.withIndex()) {
            when (c) {
                '(' -> parenCount++
                ')' -> parenCount--
                '[' -> bracketCount++
                ']' -> bracketCount--
            }
            if (parenCount < 0) return ValidationResult(false, "Unmatched closing parenthesis", i)
            if (bracketCount < 0) return ValidationResult(false, "Unmatched closing bracket", i)
        }
        if (parenCount > 0) return ValidationResult(false, "Unmatched opening parenthesis", -1)
        if (bracketCount > 0) return ValidationResult(false, "Unmatched opening bracket", -1)
        if (sql.contains(",,")) return ValidationResult(false, "Consecutive commas found", sql.indexOf(",,"))
        val trailingCommaPattern = Pattern.compile(",\\s+(FROM|WHERE|GROUP|ORDER|HAVING|LIMIT)\\b", Pattern.CASE_INSENSITIVE)
        val trailingMatcher = trailingCommaPattern.matcher(sql)
        if (trailingMatcher.find()) {
            return ValidationResult(false, "Trailing comma before ${trailingMatcher.group(1)}", trailingMatcher.start())
        }
        return ValidationResult(true)
    }

    private fun fixIllegalCharactersAfterKeywords(sql: String): String {
        val chars = sql.toCharArray()
        val toRemove = BooleanArray(chars.size)

        for (keyword in SQLITE_KEYWORDS) {
            val keywordPattern = Pattern.compile("\\b$keyword\\b", Pattern.CASE_INSENSITIVE)
            val matcher = keywordPattern.matcher(sql)
            while (matcher.find()) {
                val keywordEnd = matcher.end()
                if (keywordEnd < chars.size) {
                    var nextCharIndex = keywordEnd
                    while (nextCharIndex < chars.size && chars[nextCharIndex].isWhitespace()) {
                        nextCharIndex++
                    }
                    if (nextCharIndex < chars.size) {
                        val nextChar = chars[nextCharIndex].uppercaseChar()
                        if (shouldRemoveCharacterAfterKeyword(keyword, nextChar, sql, nextCharIndex)) {
                            toRemove[nextCharIndex] = true
                        }
                    }
                }
            }
        }

        return buildString {
            for (i in chars.indices) {
                if (!toRemove[i]) append(chars[i])
            }
        }
    }

    private fun shouldRemoveCharacterAfterKeyword(keyword: String, nextChar: Char, sql: String, position: Int): Boolean {
        if (isInsideStringLiteral(sql, position)) return false
        if (nextChar == '=' || nextChar == '<' || nextChar == '>' || nextChar == '!') return false
        if (nextChar == '(' && (KEYWORDS_ALLOWING_PARENTHESES.contains(keyword) || keyword == "SELECT")) return false
        if (keyword.equals("LIKE", ignoreCase = true) &&
            (nextChar == '\'' || nextChar == '"' || nextChar == '%' || nextChar == '_' || nextChar.isLetterOrDigit())
        ) return false

        return when (keyword.uppercase(Locale.ROOT)) {
            "ORDER", "GROUP" -> nextChar != 'B'
            "IS" -> nextChar != 'N' && nextChar != 'U'
            "NOT" -> !nextChar.isLetter() && nextChar != '('
            "AS", "BY", "FROM", "WHERE", "HAVING", "AND", "OR", "ON", "SET", "INTO" ->
                !nextChar.isLetterOrDigit() && nextChar != '\'' && nextChar != '"' && nextChar != '[' && nextChar != '(' && nextChar != '_'
            else -> ILLEGAL_CHARS_AFTER_KEYWORDS.contains(nextChar.lowercaseChar())
        }
    }

    private fun isInsideStringLiteral(sql: String, position: Int): Boolean {
        var inSingleQuote = false
        var inDoubleQuote = false
        var inBracket = false
        for (i in 0 until position.coerceAtMost(sql.length)) {
            val c = sql[i]
            when {
                c == '\'' && !inDoubleQuote && !inBracket -> inSingleQuote = !inSingleQuote
                c == '"' && !inSingleQuote && !inBracket -> inDoubleQuote = !inDoubleQuote
                c == '[' && !inSingleQuote && !inDoubleQuote -> inBracket = !inBracket
                c == ']' && inBracket -> inBracket = false
            }
        }
        return inSingleQuote || inDoubleQuote || inBracket
    }

    private fun fixTrailingCommas(sql: String): String {
        val problematicKeywords = listOf("FROM", "WHERE", "GROUP", "ORDER", "HAVING", "LIMIT", "UNION", "JOIN")
        var result = sql
        for (keyword in problematicKeywords) {
            val pattern = Pattern.compile(",\\s+($keyword)\\b", Pattern.CASE_INSENSITIVE)
            result = pattern.matcher(result).replaceAll(" $1")
        }
        return result
    }

    private fun fixConsecutiveCommas(sql: String): String {
        return sql.replace(Regex(",\\s*,+"), ",")
    }

    private fun cleanUpWhitespace(sql: String): String {
        return sql.replace(Regex("\\s+"), " ").trim()
    }
}