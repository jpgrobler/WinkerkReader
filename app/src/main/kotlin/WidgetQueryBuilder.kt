package za.co.jpsoft.winkerkreader

import org.joda.time.DateTime
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry

/**
 * Centralised SQL query builder for the home screen widget.
 * All queries retrieve upcoming events (birthdays, baptisms, weddings, confessions, deaths)
 * within the next LOOK_AHEAD_DAYS.
 */
object WidgetQueryBuilder {

    private const val LOOK_AHEAD_DAYS = 15

    /**
     * Builds the complete UNION query that returns all events ordered by month and day.
     */
    fun buildCombinedQuery(): String {
        val today = DateTime.now()
        val futureDate = today.plusDays(LOOK_AHEAD_DAYS)

        val currentDay = today.dayOfMonth
        val currentMonth = today.monthOfYear
        val futureDay = futureDate.dayOfMonth
        val futureMonth = futureDate.monthOfYear

        return """
            SELECT * FROM (
                ${buildBirthdayQuery(currentDay, currentMonth, futureDay, futureMonth)}
                UNION ALL
                ${buildBaptismQuery(currentDay, currentMonth, futureDay, futureMonth)}
                UNION ALL
                ${buildMarriageQuery(currentDay, currentMonth, futureDay, futureMonth)}
                UNION ALL
                ${buildConfessionQuery(currentDay, currentMonth, futureDay, futureMonth)}
                UNION ALL
                ${buildDeathQuery(currentDay, currentMonth, futureDay, futureMonth)}
            ) ORDER BY Month ASC, Day ASC, Van ASC, Noemnaam ASC
        """.trimIndent()
    }

    /**
     * Query for birthdays (Verjaar) from the Members table.
     */
    fun buildBirthdayQuery(
        currentDay: Int,
        currentMonth: Int,
        futureDay: Int,
        futureMonth: Int
    ): String = """
        SELECT Members.Noemnaam, Members.Van, Members.Gemeente, 'Verjaar' AS Rede,
               substr(Members.Geboortedatum,1,2) AS Day, substr(Members.Geboortedatum,4,2) AS Month,
               Members.Geboortedatum as Datum FROM Members
        WHERE Members.Rekordstatus = "0" AND Members.Geboortedatum IS NOT NULL AND LENGTH(Members.Geboortedatum) >= 10
          AND ((CAST(substr(Geboortedatum, 1, 2) AS INTEGER) >= $currentDay AND CAST(substr(Geboortedatum, 4, 2) AS INTEGER) = $currentMonth)
            OR (CAST(substr(Geboortedatum, 1, 2) AS INTEGER) <= $futureDay AND CAST(substr(Geboortedatum, 4, 2) AS INTEGER) = $futureMonth))
    """.trimIndent()

    /**
     * Query for baptisms (Doop) from the Members table.
     */
    fun buildBaptismQuery(
        currentDay: Int,
        currentMonth: Int,
        futureDay: Int,
        futureMonth: Int
    ): String = """
        SELECT Members.Noemnaam, Members.Van, Members.Gemeente, 'Doop' AS Rede,
               substr(Members.[Doop date],1,2) AS Day, substr(Members.[Doop date],4,2) AS Month,
               Members.[Doop date] as Datum FROM Members
        WHERE Members.Rekordstatus = "0" AND Members.[Doop date] IS NOT NULL AND LENGTH(Members.[Doop date]) >= 10
          AND ((CAST(substr(Members.[Doop date], 1, 2) AS INTEGER) >= $currentDay AND CAST(substr(Members.[Doop date], 4, 2) AS INTEGER) = $currentMonth)
            OR (CAST(substr(Members.[Doop date], 1, 2) AS INTEGER) <= $futureDay AND CAST(substr(Members.[Doop date], 4, 2) AS INTEGER) = $futureMonth))
    """.trimIndent()

    /**
     * Query for weddings (Huwelik) from the Members table.
     */
    fun buildMarriageQuery(
        currentDay: Int,
        currentMonth: Int,
        futureDay: Int,
        futureMonth: Int
    ): String = """
        SELECT Members.Noemnaam, Members.Van, Members.Gemeente, 'Huwelik' AS Rede,
               substr(Members.[Huwelik date],1,2) AS Day, substr(Members.[Huwelik date],4,2) AS Month,
               Members.[Huwelik date] as Datum FROM Members
        WHERE Members.Rekordstatus = "0" AND Members.[Huwelik date] IS NOT NULL AND LENGTH(Members.[Huwelik date]) >= 10
          AND ((CAST(substr(Members.[Huwelik date], 1, 2) AS INTEGER) >= $currentDay AND CAST(substr(Members.[Huwelik date], 4, 2) AS INTEGER) = $currentMonth)
            OR (CAST(substr(Members.[Huwelik date], 1, 2) AS INTEGER) <= $futureDay AND CAST(substr(Members.[Huwelik date], 4, 2) AS INTEGER) = $futureMonth))
    """.trimIndent()

    /**
     * Query for confessions (Belydenis) from the Members table.
     */
    fun buildConfessionQuery(
        currentDay: Int,
        currentMonth: Int,
        futureDay: Int,
        futureMonth: Int
    ): String = """
        SELECT Members.Noemnaam, Members.Van, Members.Gemeente, 'Belydenis' AS Rede,
               substr(Members.[Belydenisaflegging Date],1,2) AS Day, substr(Members.[Belydenisaflegging Date],4,2) AS Month,
               Members.[Belydenisaflegging Date] as Datum FROM Members
        WHERE Members.[Belydenisaflegging Date] IS NOT NULL AND LENGTH(Members.[Belydenisaflegging Date]) >= 10
          AND ((CAST(substr(Members.[Belydenisaflegging Date], 1, 2) AS INTEGER) >= $currentDay AND CAST(substr(Members.[Belydenisaflegging Date], 4, 2) AS INTEGER) = $currentMonth)
            OR (CAST(substr(Members.[Belydenisaflegging Date], 1, 2) AS INTEGER) <= $futureDay AND CAST(substr(Members.[Belydenisaflegging Date], 4, 2) AS INTEGER) = $futureMonth))
    """.trimIndent()

    /**
     * Query for recent deaths (Oorlede) from the Argief table.
     * Includes only deaths within the last 2 years.
     */
    fun buildDeathQuery(
        currentDay: Int,
        currentMonth: Int,
        futureDay: Int,
        futureMonth: Int
    ): String = """
        SELECT Argief.Name AS Noemnaam, Argief.Surname as Van, Argief.Gemeente, 'Oorlede' AS Rede,
               substr(Argief.[DepartureDate],1,2) AS Day, substr(Argief.[DepartureDate],4,2) AS Month,
               Argief.[DepartureDate] AS Datum FROM Argief
        WHERE Argief.Reason = 'Oorlede' AND Argief.[DepartureDate] IS NOT NULL AND LENGTH(Argief.[DepartureDate]) >= 10
          AND ((CAST(SUBSTR(Argief.[DepartureDate], 1, 2) AS INTEGER) >= $currentDay AND CAST(SUBSTR(Argief.[DepartureDate], 4, 2) AS INTEGER) = $currentMonth)
            OR (CAST(SUBSTR(Argief.[DepartureDate], 1, 2) AS INTEGER) <= $futureDay AND CAST(SUBSTR(Argief.[DepartureDate], 4, 2) AS INTEGER) = $futureMonth))
          AND (strftime('%Y', 'now') - CAST(SUBSTR(Argief.[DepartureDate], 7, 4) AS INTEGER)) <= 2
    """.trimIndent()
}