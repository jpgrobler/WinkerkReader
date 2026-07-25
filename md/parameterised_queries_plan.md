# Parameterised Queries for SOEK/FILTER SQL — Implementation Status

> **See also:** [`architecture.md`](architecture.md) §6 (search/filter flow) · `MemberQueryBuilder.kt` · `MemberPagingSource.kt`

This document details the refactoring of raw SQL queries in `MemberViewModel` to use secure, parameterized queries.

---

## 1. Status: COMPLETED ✅

All search strings and filter inputs are now passed safely using the `selectionArgs` parameter of `ContentResolver.query()`, eliminating raw SQL string concatenation for user inputs.

---

## 2. Refactored Architecture

### 1. Updated `MemberViewModel.kt` Signatures & Structures
- **`SqlRequest` Data Class**: Added to group the SQL selection query containing `?` placeholders with their corresponding arguments:
  ```kotlin
  data class SqlRequest(val sql: String, val args: Array<String>)
  ```
- **Query Caching**: Modernized the cache from mapping keys to pure SQL strings, to storing the compiled `SqlRequest` objects:
  ```kotlin
  private val queryCache = HashMap<String, SqlRequest>()
  ```
- **`queryDatabase` Execution**: Accepts parameterized arguments and executes them safely via the ContentResolver:
  ```kotlin
  private fun queryDatabase(
      context: Context, 
      query: String, 
      args: Array<String>? = null,
      sortOrder: String? = null
  ): Cursor? {
      return context.contentResolver.query(
          winkerkEntry.CONTENT_URI, 
          null, 
          query, 
          args, 
          sortOrder
      )
  }
  ```

---

## 3. Parameterized Query Generation

The WHERE clauses are now constructed using `?` placeholders. When inputs are processed, they are appended to a list of arguments instead of being embedded in the query string.

### Search (`SOEK_DATA`)
Instead of appending search variables directly into the query, they are added as parameters:
- **SQL Appended**: `ColumnName LIKE ?`
- **Argument Stored**: `%<search_query>%`

### Filters (`FILTER_DATA`)
All conditional filter branches (e.g., "gelyk aan", "begin met", "bevat", etc.) use placeholders:
- **Equals ("gelyk aan")**: Appends `ColumnName = ?` and adds the filter value.
- **Starts with ("begin met")**: Appends `ColumnName LIKE ?` and adds `<filter_value>%`.
- **Contains ("bevat")**: Appends `ColumnName LIKE ?` and adds `%<filter_value>%`.

---

## 4. Query Validation

The `SQLiteStatementValidator` class remains in place to validate SQL structure and handle edge cases, but now validates the parameterized SQL statement itself. Since `?` placeholders are valid SQL syntax, parameterized queries pass validation natively.

---

## 5. Summary of Benefits
1. **Security**: Entirely blocks SQL injection vectors through user search and custom filter inputs.
2. **Robustness**: Typing characters like `'` or `%` in the search bar no longer breaks SQL parser logic or throws syntax exceptions.
3. **Caching**: Caching `SqlRequest` targets is stable and prevents redundant query reconstructions.
