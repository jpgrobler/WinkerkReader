package za.co.jpsoft.winkerkreader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class SQLiteStatementValidator {

    // SQLite keywords that should be followed by specific patterns
    private static final Set<String> SQLITE_KEYWORDS = new HashSet<>(Arrays.asList(
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
            "DELETE", "CREATE", "TABLE", "ALTER", "DROP", "INDEX", "VIEW", "TRIGGER",
            "ORDER", "BY", "GROUP", "HAVING", "LIMIT", "OFFSET", "JOIN", "INNER",
            "LEFT", "RIGHT", "FULL", "OUTER", "ON", "AS", "AND", "OR", "NOT",
            "BETWEEN", "IN", "EXISTS", "IS", "NULL", "DISTINCT", "UNION",
            "INTERSECT", "EXCEPT", "CASE", "WHEN", "THEN", "ELSE", "END"
    ));

    // Characters that are typically illegal immediately after keywords
    // Removed '%' as it's valid for LIKE operator
    private static final Set<Character> ILLEGAL_CHARS_AFTER_KEYWORDS = new HashSet<>(Arrays.asList(
            ',', '.', ';', ')', '}', ']', '!', '@', '#', '$', '^', '&', '*',
            '+', '|', '\\', ':', '"', '\'', '<', '>', '?', '/', '~', '`'
            // NOTE: '=' is intentionally NOT in this list as it's valid after many keywords
    ));

    // Keywords that can be followed by parentheses
    private static final Set<String> KEYWORDS_ALLOWING_PARENTHESES = new HashSet<>(Arrays.asList(
            "VALUES", "IN", "EXISTS", "COUNT", "SUM", "AVG", "MIN", "MAX", "SUBSTR",
            "LENGTH", "UPPER", "LOWER", "TRIM", "COALESCE", "IFNULL", "NULLIF",
            "DATE", "TIME", "DATETIME", "STRFTIME", "CAST", "ROUND", "ABS"
    ));

    // Operators that are valid SQL constructs
    private static final Set<String> VALID_OPERATORS = new HashSet<>(Arrays.asList(
            "LIKE", "=", "!=", "<>", ">", "<", ">=", "<=", "||"
    ));

    public static class ValidationResult {
        private final boolean isValid;
        private final String errorMessage;
        private final int errorPosition;
        private final String fixedSql;
        private final boolean wasFixed;

        public ValidationResult(boolean isValid, String errorMessage, int errorPosition, String fixedSql, boolean wasFixed) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
            this.errorPosition = errorPosition;
            this.fixedSql = fixedSql;
            this.wasFixed = wasFixed;
        }

        public ValidationResult(boolean isValid, String errorMessage, int errorPosition) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
            this.errorPosition = errorPosition;
            this.fixedSql = null;
            this.wasFixed = false;
        }

        public boolean isValid() {
            return isValid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public int getErrorPosition() {
            return errorPosition;
        }

        public String getFixedSql() {
            return fixedSql;
        }

        public boolean wasFixed() {
            return wasFixed;
        }

        @Override
        public String toString() {
            if (isValid) {
                return wasFixed ? "SQL statement was fixed and is now valid" : "SQL statement is valid";
            } else {
                return String.format("Invalid SQL at position %d: %s", errorPosition, errorMessage);
            }
        }
    }

    /**
     * Validates and fixes a SQLite statement by removing illegal characters after keywords
     */
    public static ValidationResult validateAndFixSQLiteStatement(String sqlStatement) {
        if (sqlStatement == null || sqlStatement.trim().isEmpty()) {
            return new ValidationResult(false, "SQL statement cannot be null or empty", 0, null, false);
        }

        String originalSql = sqlStatement.trim();
        boolean wasFixed = false;

        try {
            // First, try to fix the SQL statement
            String fixedSql = fixSQLiteStatement(originalSql);
            if (!fixedSql.equals(originalSql)) {
                wasFixed = true;
            }

            // Validate the fixed SQL
            ValidationResult validationResult = validateSQLiteStatement(fixedSql);

            return new ValidationResult(
                    validationResult.isValid(),
                    validationResult.getErrorMessage(),
                    validationResult.getErrorPosition(),
                    fixedSql,
                    wasFixed
            );

        } catch (Exception e) {
            return new ValidationResult(false, "Error during validation: " + e.getMessage(), 0, originalSql, false);
        }
    }

    /**
     * Validates a SQLite statement for illegal characters after keywords
     */
    public static ValidationResult validateSQLiteStatement(String sqlStatement) {
        if (sqlStatement == null || sqlStatement.trim().isEmpty()) {
            return new ValidationResult(false, "SQL statement cannot be null or empty", 0);
        }

        String originalSql = sqlStatement.trim();

        try {
            // Remove string literals and comments to avoid false positives
            String cleanedSql = removeStringLiteralsAndComments(originalSql);

            // Check for illegal characters after keywords
            ValidationResult keywordValidation = validateKeywordSyntax(cleanedSql, originalSql);
            if (!keywordValidation.isValid()) {
                return keywordValidation;
            }

            // Additional syntax checks
            ValidationResult syntaxValidation = validateBasicSyntax(cleanedSql);
            if (!syntaxValidation.isValid()) {
                return syntaxValidation;
            }

            return new ValidationResult(true, null, -1);

        } catch (Exception e) {
            return new ValidationResult(false, "Error during validation: " + e.getMessage(), 0);
        }
    }

    /**
     * Fixes common SQLite statement errors by removing illegal characters
     */
    public static String fixSQLiteStatement(String sqlStatement) {
        if (sqlStatement == null || sqlStatement.trim().isEmpty()) {
            return sqlStatement;
        }

        String sql = sqlStatement.trim();

        // Step 1: Fix illegal characters after keywords
        sql = fixIllegalCharactersAfterKeywords(sql);

        // Step 2: Fix trailing commas
        sql = fixTrailingCommas(sql);

        // Step 3: Fix consecutive commas
        sql = fixConsecutiveCommas(sql);

        // Step 4: Clean up extra whitespace
        sql = cleanUpWhitespace(sql);

        return sql;
    }

    private static String removeStringLiteralsAndComments(String sql) {
        StringBuilder result = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBracket = false; // For [column names]
        boolean inComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char nextChar = (i + 1 < sql.length()) ? sql.charAt(i + 1) : '\0';

            if (!inSingleQuote && !inDoubleQuote && !inBracket && !inComment) {
                if (c == '-' && nextChar == '-') {
                    inComment = true;
                    result.append(' ');
                    continue;
                } else if (c == '\'') {
                    inSingleQuote = true;
                    result.append(' ');
                    continue;
                } else if (c == '"') {
                    inDoubleQuote = true;
                    result.append(' ');
                    continue;
                } else if (c == '[') {
                    inBracket = true;
                    result.append(' ');
                    continue;
                }
            }

            if (inComment && (c == '\n' || c == '\r')) {
                inComment = false;
            } else if (inSingleQuote && c == '\'') {
                inSingleQuote = false;
                result.append(' ');
                continue;
            } else if (inDoubleQuote && c == '"') {
                inDoubleQuote = false;
                result.append(' ');
                continue;
            } else if (inBracket && c == ']') {
                inBracket = false;
                result.append(' ');
                continue;
            }

            if (inSingleQuote || inDoubleQuote || inBracket || inComment) {
                result.append(' ');
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    private static ValidationResult validateKeywordSyntax(String cleanedSql, String originalSql) {
        String upperCleanedSql = cleanedSql.toUpperCase();
        Pattern keywordPattern = Pattern.compile("\\b(" + String.join("|", SQLITE_KEYWORDS) + ")\\b");
        Matcher matcher = keywordPattern.matcher(upperCleanedSql);

        while (matcher.find()) {
            String keyword = matcher.group(1);
            int keywordEnd = matcher.end();

            if (keywordEnd < upperCleanedSql.length()) {
                // Skip whitespace to find the actual next character
                int nextNonSpaceIndex = keywordEnd;
                while (nextNonSpaceIndex < upperCleanedSql.length() &&
                        Character.isWhitespace(upperCleanedSql.charAt(nextNonSpaceIndex))) {
                    nextNonSpaceIndex++;
                }

                if (nextNonSpaceIndex < upperCleanedSql.length()) {
                    char nextNonSpaceChar = upperCleanedSql.charAt(nextNonSpaceIndex);

                    // Check for illegal characters (with context from original SQL)
                    if (isIllegalCharacterAfterKeyword(keyword, nextNonSpaceChar, originalSql, nextNonSpaceIndex)) {
                        return new ValidationResult(false,
                                String.format("Illegal character '%c' found after keyword '%s'",
                                        nextNonSpaceChar, keyword),
                                nextNonSpaceIndex);
                    }
                }
            }
        }

        return new ValidationResult(true, null, -1);
    }

    private static boolean isIllegalCharacterAfterKeyword(String keyword, char nextChar, String originalSql, int position) {
        // Allow parentheses after certain keywords
        if (nextChar == '(' && KEYWORDS_ALLOWING_PARENTHESES.contains(keyword)) {
            return false;
        }

        // Allow opening parenthesis after SELECT for subqueries
        if (keyword.equals("SELECT") && nextChar == '(') {
            return false;
        }

        // Special handling for operators
        if (nextChar == '=' || nextChar == '<' || nextChar == '>' || nextChar == '!') {
            return false; // These are valid operators
        }

        // LIKE keyword can be followed by string literals with wildcards
        if (keyword.equals("LIKE")) {
            if (nextChar == '\'' || nextChar == '"' || nextChar == '%' ||
                    nextChar == '_' || Character.isLetterOrDigit(nextChar)) {
                return false;
            }
        }

        // Special cases for specific keywords
        switch (keyword) {
            case "ORDER":
            case "GROUP":
                // Must be followed by BY
                return nextChar != 'B';
            case "IS":
                // Can be followed by NOT or NULL
                return nextChar != 'N' && !Character.isLetter(nextChar);
            case "NOT":
                // Can be followed by various operators or keywords
                return !Character.isLetter(nextChar) && nextChar != '(' && nextChar != '=';
            case "AS":
                // Can be followed by identifiers, strings, or brackets
                return !Character.isLetterOrDigit(nextChar) && nextChar != '\'' &&
                        nextChar != '"' && nextChar != '[' && nextChar != '_';
            case "BY":
            case "FROM":
            case "WHERE":
            case "HAVING":
            case "AND":
            case "OR":
            case "ON":
            case "SET":
            case "INTO":
                // These should be followed by identifiers or expressions
                return !Character.isLetterOrDigit(nextChar) && nextChar != '\'' &&
                        nextChar != '"' && nextChar != '[' && nextChar != '(' && nextChar != '_';
            default:
                // Check general illegal characters
                return ILLEGAL_CHARS_AFTER_KEYWORDS.contains(nextChar);
        }
    }

    private static ValidationResult validateBasicSyntax(String sql) {
        // Check for unmatched parentheses
        int parenCount = 0;
        int bracketCount = 0;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') parenCount++;
            if (c == ')') parenCount--;
            if (c == '[') bracketCount++;
            if (c == ']') bracketCount--;

            if (parenCount < 0) {
                return new ValidationResult(false, "Unmatched closing parenthesis", i);
            }
            if (bracketCount < 0) {
                return new ValidationResult(false, "Unmatched closing bracket", i);
            }
        }

        if (parenCount > 0) {
            return new ValidationResult(false, "Unmatched opening parenthesis", -1);
        }
        if (bracketCount > 0) {
            return new ValidationResult(false, "Unmatched opening bracket", -1);
        }

        // Check for consecutive commas
        if (sql.contains(",,")) {
            return new ValidationResult(false, "Consecutive commas found", sql.indexOf(",,"));
        }

        // Check for trailing comma before keywords that shouldn't have them
        Pattern trailingCommaPattern = Pattern.compile(",\\s+(FROM|WHERE|GROUP|ORDER|HAVING|LIMIT)\\b",
                Pattern.CASE_INSENSITIVE);
        Matcher trailingCommaMatcher = trailingCommaPattern.matcher(sql);
        if (trailingCommaMatcher.find()) {
            return new ValidationResult(false,
                    "Trailing comma before " + trailingCommaMatcher.group(1),
                    trailingCommaMatcher.start());
        }

        return new ValidationResult(true, null, -1);
    }

    private static String fixIllegalCharactersAfterKeywords(String sql) {
        StringBuilder result = new StringBuilder();
        String upperSql = sql.toUpperCase();

        char[] chars = sql.toCharArray();
        boolean[] toRemove = new boolean[chars.length];

        // Find and mark illegal characters for removal
        for (String keyword : SQLITE_KEYWORDS) {
            Pattern keywordPattern = Pattern.compile("\\b" + keyword + "\\b");
            Matcher matcher = keywordPattern.matcher(upperSql);

            while (matcher.find()) {
                int keywordEnd = matcher.end();

                if (keywordEnd < chars.length) {
                    int nextCharIndex = keywordEnd;
                    while (nextCharIndex < chars.length && Character.isWhitespace(chars[nextCharIndex])) {
                        nextCharIndex++;
                    }

                    if (nextCharIndex < chars.length) {
                        char nextChar = Character.toUpperCase(chars[nextCharIndex]);

                        if (shouldRemoveCharacterAfterKeyword(keyword, nextChar, sql, nextCharIndex)) {
                            toRemove[nextCharIndex] = true;
                        }
                    }
                }
            }
        }

        // Build result string without marked characters
        for (int i = 0; i < chars.length; i++) {
            if (!toRemove[i]) {
                result.append(chars[i]);
            }
        }

        return result.toString();
    }

    private static boolean shouldRemoveCharacterAfterKeyword(String keyword, char nextChar, String sql, int position) {
        // Don't remove if it's part of a string literal
        if (isInsideStringLiteral(sql, position)) {
            return false;
        }

        // Don't remove operators
        if (nextChar == '=' || nextChar == '<' || nextChar == '>' || nextChar == '!') {
            return false;
        }

        // Special handling for keywords that can have parentheses
        if (nextChar == '(' && (KEYWORDS_ALLOWING_PARENTHESES.contains(keyword) || keyword.equals("SELECT"))) {
            return false;
        }

        // LIKE can be followed by string literals with wildcards
        if (keyword.equals("LIKE") && (nextChar == '\'' || nextChar == '"' ||
                nextChar == '%' || nextChar == '_' ||
                Character.isLetterOrDigit(nextChar))) {
            return false;
        }

        // Don't remove valid characters after specific keywords
        switch (keyword) {
            case "ORDER":
            case "GROUP":
                return nextChar != 'B';
            case "IS":
                return nextChar != 'N' && nextChar != 'U';
            case "NOT":
                return !Character.isLetter(nextChar) && nextChar != '(';
            case "AS":
            case "BY":
            case "FROM":
            case "WHERE":
            case "HAVING":
            case "AND":
            case "OR":
            case "ON":
            case "SET":
            case "INTO":
                return !Character.isLetterOrDigit(nextChar) && nextChar != '\'' &&
                        nextChar != '"' && nextChar != '[' && nextChar != '(' && nextChar != '_';
            default:
                return ILLEGAL_CHARS_AFTER_KEYWORDS.contains(Character.toLowerCase(nextChar));
        }
    }

    private static boolean isInsideStringLiteral(String sql, int position) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBracket = false;

        for (int i = 0; i < position && i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !inDoubleQuote && !inBracket) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote && !inBracket) {
                inDoubleQuote = !inDoubleQuote;
            } else if (c == '[' && !inSingleQuote && !inDoubleQuote) {
                inBracket = !inBracket;
            } else if (c == ']' && inBracket) {
                inBracket = false;
            }
        }

        return inSingleQuote || inDoubleQuote || inBracket;
    }

    private static String fixTrailingCommas(String sql) {
        String[] problematicKeywords = {"FROM", "WHERE", "GROUP", "ORDER", "HAVING", "LIMIT", "UNION", "JOIN"};

        for (String keyword : problematicKeywords) {
            Pattern pattern = Pattern.compile(",\\s+(" + keyword + ")\\b", Pattern.CASE_INSENSITIVE);
            sql = pattern.matcher(sql).replaceAll(" $1");
        }

        return sql;
    }

    private static String fixConsecutiveCommas(String sql) {
        return sql.replaceAll(",\\s*,+", ",");
    }

    private static String cleanUpWhitespace(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}