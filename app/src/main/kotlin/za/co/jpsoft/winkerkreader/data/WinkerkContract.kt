package za.co.jpsoft.winkerkreader.data

import za.co.jpsoft.winkerkreader.WinkerkReader
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.BaseColumns
import java.io.File

/**
 * Created by Pieter Grobler on 22/07/2017.
 * Updated: Column names corrected - no brackets in definitions
 */
object WinkerkContract {
    const val DATABASE_VERSION = 1
    const val CONTENT_AUTHORITY = "za.co.jpsoft.winkerkreader"
    private val BASE_CONTENT_URI = Uri.parse("content://$CONTENT_AUTHORITY")

    const val SEARCH_LIST_REQUEST = 16895
    const val FILTER_LIST_REQUEST = 16896
    const val PREFS_USER_INFO = "WinkerkReader_UserInfo"
    const val PREFS_CALL_LOGGER = "CallLoggerPrefs"

    // Display Settings Keys
    const val KEY_LIST_FOTO = "LIST_FOTO"
    const val KEY_LIST_EPOS = "LIST_EPOS"
    const val KEY_LIST_WHATSAPP = "LIST_WHATSAPP"
    const val KEY_LIST_VERJAARBLOK = "LIST_VERJAARBLOK"
    const val KEY_LIST_OUDERDOM = "LIST_OUDERDOM"
    const val KEY_LIST_HUWELIKBLOK = "LIST_HUWELIKBLOK"
    const val KEY_LIST_WYK = "LIST_WYK"
    const val KEY_LIST_SELFOON = "LIST_SELFOON"
    const val KEY_LIST_TELEFOON = "LIST_TELEFOON"

    // Function Settings Keys
    const val KEY_AUTOSTART = "auto_start_enabled"
    const val KEY_OPROEPMONITOR = "CallMonitor"
    const val KEY_OPROEPLOG = "CallLog"
    const val KEY_DEFLAYOUT = "DefLayout"
    const val KEY_WHATSAPP1 = "Whatsapp1"
    const val KEY_WHATSAPP2 = "Whatsapp2"
    const val KEY_WHATSAPP3 = "Whatsapp3"
    const val KEY_EPOSHTML = "EposHtml"
    const val KEY_SELECTED_CALENDAR_ID = "selected_calendar_id"

    // Widget Settings Keys
    const val KEY_WIDGET_DOOP = "Widget_Doop"
    const val KEY_WIDGET_BELYDENIS = "Widget_Belydenis"
    const val KEY_WIDGET_HUWELIK = "Widget_Huwelik"
    const val KEY_WIDGET_STERF = "Widget_Sterwe"

    // Color Settings Keys
    const val KEY_GEMEENTE_KLEUR = "Gem1_Kleur"
    const val KEY_GEMEENTE2_KLEUR = "Gem2_Kleur"
    const val KEY_GEMEENTE3_KLEUR = "Gem3_Kleur"
    const val CHANNEL_ID = "winkerkReaderServiceChannel"
    const val SEARCH_CHECK_BOX = "SEARCH_CHECK_BOX"
    const val FILTER_CHECK_BOX = "FILTER_CHECK_BOX"
    const val PATH_LIDMATE = "Lidmate"
    const val PATH_ADRES = "Adresse"
    const val PATH_GESINNE = "Gesinne"
    const val PATH_KODES = "Kodes"
    const val PATH_GESIN = "Gesin"
    const val PATH_FOON = "Foon"
    const val PATH_MYLPALE = "Mylpale"
    const val PATH_GROEPE = "Groepe"
    const val PATH_GROEPE_LYS = "GroepeLys"
    const val PATH_VERJAAR = "Verjaar"
    const val PATH_GEMEENTE_NAAM = "GemeenteNaam"
    const val PATH_OUDERDOM = "Ouderdom"
    const val PATH_FOTO = "Foto"
    const val PATH_FOTO_UPDATER = "Foto_Updater"
    const val PATH_WKR_GROEPE = "WKR_Groepe"
    const val PATH_WKR_GROEPLEDE = "WKR_Groeplede"
    const val PATH_MEELEWING = "Meelewing"
    const val PATH_HUWELIK = "Huwelik"
    const val PATH_ARGIEF = "ARCHIVE"

    // Helper method to wrap column names in brackets for SQL
    @JvmStatic
    fun col(columnName: String?): String? {
        if (columnName.isNullOrEmpty()) {
            return columnName
        }
        if (columnName.startsWith("[") && columnName.endsWith("]")) {
            return columnName
        }
        return "[$columnName]"
    }

    object winkerkEntry : BaseColumns {



        @JvmField val CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_LIDMATE)
        @JvmField val ARGIEF_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_ARGIEF)

        @JvmField val CONTENT_LIST_TYPE =
            ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_LIDMATE

        @JvmField val CONTENT_ITEM_TYPE =
            ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_LIDMATE

        @JvmField val CONTENT_ADRES_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_ADRES)
        @JvmField val CONTENT_ADRES_TYPE =
            ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_ADRES

        @JvmField val CONTENT_GESINNE_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_GESINNE)
        @JvmField val CONTENT_GESINNE_TYPE =
            ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_GESINNE

        @JvmField val CONTENT_GESIN_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_GESIN)
        @JvmField val CONTENT_GESIN_LIST_TYPE =
            ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_GESIN

        @JvmField val CONTENT_FOON_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_FOON)
        @JvmField val CONTENT_FOON_LIST_TYPE =
            ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_FOON

        @JvmField val CONTENT_MYLPALE_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_MYLPALE)
        @JvmField val CONTENT_MYLPALE_LIST_TYPE =
            ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_MYLPALE

        @JvmField val CONTENT_MEELEWING_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_MEELEWING)
        @JvmField val CONTENT_MEELEWING_LIST_TYPE =
            ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_MEELEWING

        @JvmField val CONTENT_GROEPE_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_GROEPE)
        @JvmField val CONTENT_GROEPE_LIST_TYPE =
            ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_GROEPE

        @JvmField val CONTENT_GROEPE_LYS_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_GROEPE_LYS)
        @JvmField val CONTENT_GROEPE_LYS_TYPE =
            ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_GROEPE_LYS

        @JvmField val LIDMAAT_LOADER_VERJAAR_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_VERJAAR)
        @JvmField val LIDMAAT_LOADER_VERJAAR_LIST_TYPE =
            ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_VERJAAR

        @JvmField val CONTENT_GEMEENTE_NAAM_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_GEMEENTE_NAAM)
        @JvmField val CONTENT_GEMEENTE_NAAM_LIST_TYPE =
            ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_GEMEENTE_NAAM

        @JvmField val LIDMAAT_LOADER_OUDERDOM_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_OUDERDOM)
        @JvmField val LIDMAAT_LOADER_OUDERDOM_LIST_TYPE =
            ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_OUDERDOM

        @JvmField val INFO_LOADER_FOTO_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_FOTO)
        @JvmField val INFO_LOADER_FOTO_LIST_TYPE =
            ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_FOTO

        @JvmField val FOTO_UPDATER_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_FOTO_UPDATER)
        @JvmField val FOTO_UPDATER_LIST_TYPE =
            ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_FOTO_UPDATER

        @JvmField val INFO_LOADER_WKR_GROEPE_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_WKR_GROEPE)
        @JvmField val INFO_LOADER_WKR_GROEPE_TYPE =
            ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_WKR_GROEPE

        @JvmField val INFO_LOADER_WKR_GROEPE_LIST_TYPE =
            ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_WKR_GROEPE

        @JvmField val INFO_LOADER_WKR_GROEPLEDE_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_WKR_GROEPLEDE)
        @JvmField val INFO_LOADER_WKR_GROEPE_LEDE =
            ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_WKR_GROEPLEDE

        @JvmField val INFO_LOADER_ARGIEF =
            ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_ARGIEF

        // DATABASES
        const val WINKERK_DB = "WinkerkReader.sqlite"
        const val INFO_DB = "wkr_info.db"

        // Table Names
        const val LIDMATE_TABLE_NAME = "Members"
        const val ADRESSE_TABLENAME = "Addresses"
        const val KODES_TABLENAME = "Codes"

        // ==========================================
        // MEMBERS TABLE COLUMNS (NO BRACKETS!)
        // ==========================================
        const val LIDMATE_AANKOMSDATUM = "Aankomsdatum"
        const val LIDMATE_AANSLUITMETODE = "AansluitMetode"
        const val LIDMATE_AANSLUITMETODEGUID = "JoinMethodGUID"
        const val LIDMATE_AANVRAGEMEENTE = "AanvraGemeente"
        const val LIDMATE_ADRESGUID = "AddressGUID"
        const val LIDMATE_ALERGIEE = "Allergieë"
        const val LIDMATE_BANKREKENINGNAAM = "BankRekeningnaam"
        const val LIDMATE_BANKREKENINGNOMMER = "BankRekeningnommer"
        const val LIDMATE_BANKREKENINGTIPE = "BankRekeningTipe"
        const val LIDMATE_BANKTAKKODE = "BankTakKode"
        const val LIDMATE_BEDIENNINGSTYL = "Bedienningstyl"
        const val LIDMATE_BELYDENISOPMERKING = "Belydenisopmerking"
        const val LIDMATE_BELYDENISDATUM = "Belydenisaflegging Date"
        const val LIDMATE_BELYDENISDS = "Belydenisaflegging Minister"
        const val LIDMATE_BEROEP = "Beroep"
        const val LIDMATE_BESKIKBAARHEID = "Beskikbaarheid"
        const val LIDMATE_BEWYSSTATUS = "Bewysstatus"
        const val LIDMATE_BEWYSSTATUSGUID = "CertificateStatusGUID"
        const val LIDMATE_BOODSKAPPER = "Boodskapper"
        const val LIDMATE_DATUMAANGEVRA = "DatumAangevra"
        const val LIDMATE_DATUMONTVANG = "Datum ontvang"
        const val LIDMATE_DATUMVERANDER = "DatumVerander"
        const val LIDMATE_HUWELIKSDATUM = "Huwelik Date"
        const val LIDMATE_HUWELIKDS = "Huwelik Minister"
        const val LIDMATE_HUWELIKOPMERKING = "Huwelik Comment"
        const val LIDMATE_HUWELSTATUS = "Huwelikstatus"
        const val LIDMATE_DOOPOPMERKING = "Doop Comment"
        const val LIDMATE_DOOPDATUM = "Doop Date"
        const val LIDMATE_DOOPDS = "Doop Minister"
        const val LIDMATE_EPOS = "Epos"
        const val LIDMATE_FAKS = "Fax"
        const val LIDMATE_GEBOORTEDATUM = "Geboortedatum"
        const val LIDMATE_GEBRUIKER = "Gebruiker"
        const val LIDMATE_GEBRUIKER1 = "User 1"
        const val LIDMATE_GEBRUIKER2 = "User 2"
        const val LIDMATE_GEBRUIKER3 = "User 3"
        const val LIDMATE_GEBRUIKER4 = "User 4"
        const val LIDMATE_GEBRUIKER5 = "User 5"
        const val LIDMATE_GEBRUIKER6 = "User 6"
        const val LIDMATE_GEBRUIKERFLAG = "GebruikerFlag"
        const val LIDMATE_GESINSHOOFGUID = "FamilyHeadGUID"
        const val LIDMATE_GESINSROL = "Gesinsrol"
        const val LIDMATE_GESLAG = "Geslag"
        const val LIDMATE_GROEPSINDIKATOR = "GroepsIndikator"
        const val LIDMATE_HUISDOKTERNAAM = "Huisdokter"
        const val LIDMATE_HUISDOKTERTELEFOON = "Huisdokter tel"
        const val LIDMATE_HUWELIKSTATUS = "Huwelikstatus"
        const val LIDMATE_KOMMENTAAR = "Kommentaar"
        const val LIDMATE_KRONIESEMEDIKASIE = "Kroniese medikasie"
        const val LIDMATE_LIDMAATGUID = "MemberGUID"
        const val LIDMATE_LIDMAATSTATUS = "Lidmaatstatus"
        const val LIDMATE_LIDMAATSTATUSGUID = "MemberStatusGUID"
        const val LIDMATE_MEDIESEFONDSAFHANGKODE = "MedicalAidDependantCode"
        const val LIDMATE_MEDIESEFONDSHOOFLID = "MedicalAidMainMember"
        const val LIDMATE_MEDIESEFONDSNAAM = "MedicalAidName"
        const val LIDMATE_MEDIESEFONDSNOMMER = "MedicalAidNumber"
        const val LIDMATE_NOEMNAAM = "Noemnaam"
        const val LIDMATE_NOODKONTAKNOMMER = "EmergencyContactNumber"
        const val LIDMATE_NOODKONTAKPERSOON = "EmergencyContactName"
        const val LIDMATE_NOOIENSVAN = "Nooiensvan"
        const val LIDMATE_OU_LIDMATE_ID = "Ou_Lidmate_Id"
        const val LIDMATE_OU_LIDNR = "OldMemberGUID"
        const val LIDMATE_PICTUREPATH = "Fotostoorplek"
        const val LIDMATE_REKORDSTATUS = "Rekordstatus"
        const val LIDMATE_REKORDSTATUSDATUM = "RekordStatusDatum"
        const val LIDMATE_SELFOON = "Selfoon"
        const val LIDMATE_SKYPE = "Skype"
        const val LIDMATE_STUUREPOS = "StuurEPos"
        const val LIDMATE_STUURSMS = "StuurSMS"
        const val LIDMATE_TAG = "Tag"
        const val LIDMATE_TITEL = "Titel"
        const val LIDMATE_VAN = "Van"
        const val LIDMATE_VOORLETTERS = "Voorletters"
        const val LIDMATE_VOORNAME = "Naam"
        const val LIDMATE_VORIGEGEMEENTE = "PreviousCongregation"
        const val LIDMATE_WEB = "web"
        const val LIDMATE_WERKFOON = "Werk tel"
        const val LIDMATE_WERKGEWER = "Werkgewer"
        const val LIDMATE_WYK = "Wyk"
        const val LIDMATE_LANDLYN = "Landlyn"
        const val LIDMATE_POSADRES = "Posadres"
        const val LIDMATE_PREDIKANTSWYKGUID = "Predekantswyk"
        const val LIDMATE_STRAATADRES = "Straatadres"
        const val LIDMATE_KORTADRES = "ShortAddress"
        const val LIDMATE_GEMEENTE = "Gemeente"
        const val LIDMATE_GEMEENTE_EPOS = "Gemeente epos"

        // GENDER CONSTANTS
        const val GENDER_UNKNOWN = 0
        const val GENDER_MALE = 1
        const val GENDER_FEMALE = 2

        // ADDRESSES TABLE COLUMNS
        const val ADRESSE_ADRESGUID = "AddressGUID"
        const val ADRESSE_LANDLYN = "Landlyn"
        const val ADRESSE_POSADRES = "PostalAddress"
        const val ADRESSE_PREDIKANTSWYK = "PastorDistrictGUID"
        const val ADRESSE_STRAATADRES = "StreetAddress"
        const val ADRESSE_WYKGUID = "DistrictGUID"
        const val ADRESSE_PREDIKANTSWYKGUID = "PastorDistrictGUID"

        // SQL SELECTIONS (Use col() helper for brackets)
        @JvmField val SELECTION_LIDMAAT_WIDGET =
            "SELECT Members._rowid_ as _id, " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_VAN) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_NOEMNAAM) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_GEBOORTEDATUM) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_REKORDSTATUS) + ", " +
                    "quote (" + LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_LIDMAATGUID) + ") AS MemberGUID, " +
                    "date(SUBSTR(" + WinkerkContract.col(LIDMATE_GEBOORTEDATUM) + ", 7,4) || '-' || SUBSTR(" + WinkerkContract.col(LIDMATE_GEBOORTEDATUM) + ", 4, 2) || '-' || SUBSTR(" + WinkerkContract.col(LIDMATE_GEBOORTEDATUM) + ", 1, 2)) AS birthdate, " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_GEMEENTE)

        @JvmField val SELECTION_LIDMAAT_INFO =
            "SELECT Members._rowid_ as _id, " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_TAG) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_VAN) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_VOORNAME) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_NOEMNAAM) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_GEBOORTEDATUM) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_PICTUREPATH) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_GESLAG) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_LANDLYN) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_WERKFOON) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_SELFOON) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_EPOS) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_REKORDSTATUS) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_GESINSROL) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_AANKOMSDATUM) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_PREDIKANTSWYKGUID) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_WYK) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_STRAATADRES) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_KORTADRES) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_GESINSHOOFGUID) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_LIDMAATGUID) + ", " +
                    "date(SUBSTR(" + WinkerkContract.col(LIDMATE_GEBOORTEDATUM) + ", 7,4) || '-' || SUBSTR(" + WinkerkContract.col(LIDMATE_GEBOORTEDATUM) + ", 4, 2) || '-' || SUBSTR(" + WinkerkContract.col(LIDMATE_GEBOORTEDATUM) + ", 1, 2)) AS birthdate, " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_GEMEENTE)

        @JvmField val SELECTION_LIDMAAT_INFO_GESINSHOOF = ""

        @JvmField val SELECTION_LIDMAAT_DETAIL =
            "SELECT Members._rowid_ as _id, " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_TAG) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_VAN) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_VOORNAME) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_NOEMNAAM) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_NOOIENSVAN) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_GEBOORTEDATUM) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_GESLAG) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_HUWELIKSTATUS) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_BEROEP) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_WERKGEWER) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_LIDMAATSTATUS) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_PICTUREPATH) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_LANDLYN) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_WERKFOON) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_SELFOON) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_EPOS) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_REKORDSTATUS) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_AANKOMSDATUM) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_GESINSROL) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_PREDIKANTSWYKGUID) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_WYK) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_STRAATADRES) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_KORTADRES) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_GESINSHOOFGUID) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_LIDMAATGUID) + ", " +
                    "date(SUBSTR(" + WinkerkContract.col(LIDMATE_GEBOORTEDATUM) + ", 7,4) || '-' || SUBSTR(" + WinkerkContract.col(LIDMATE_GEBOORTEDATUM) + ", 4, 2) || '-' || SUBSTR(" + WinkerkContract.col(LIDMATE_GEBOORTEDATUM) + ", 1, 2)) AS birthdate, " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_GEMEENTE) + ", " +
                    LIDMATE_TABLE_NAME + "." + WinkerkContract.col(LIDMATE_GEMEENTE)

        const val SELECTION_LIDMAAT_FROM_GESINSHOOF = " Members "
        const val SELECTION_LIDMAAT_FROM = LIDMATE_TABLE_NAME

        const val SELECTION_HUWELIK_WHERE = " [Huwelikstatus] = 'Getroud' "

        // Group columns
        const val GROEP_AANSLUIT = "JoinDate"
        const val GROEP_UITTREE = "ResignDate"
        const val GROEPADRES = "Address"
        const val GROEPNAAM = "Name"
        const val GROEPROL = "GroupRol"
        const val GROEPTIPE = "TypeCodeGUID"
        const val GROEPETABEL_NAME = "Groups"
        const val MEMBERGOURPTABEL = "MemberGroupLinks"

        // KODES TABLE COLUMNS
        const val KODES_KodeGUID = "$KODES_TABLENAME.CodeGUID"
        const val KODES_KategorieGUID = "$KODES_TABLENAME.CategoryGUID"
        const val KODES_Beskrywing = "$KODES_TABLENAME.Description"
        const val KODES_Stelsel = "$KODES_TABLENAME.System"

        // MYLPALE TABLE COLUMNS
        const val MYLPALE_TABLENAME = "Milestones"
        const val MYLPALE_DATUM = "Date"
        const val MYLPALE_DATUM2 = "Date2"
        const val MYLPALE_MYLPAAL = "Description"
        const val MYLPALE_KOMMENTAAR = "Comment"
        const val MYLPALE_LERAAR = "Pastor"
        const val MYLPALE_GEMEENTE = "Congregation"
        const val MYLPALE_GETUIE1 = "Witness1"
        const val MYLPALE_GETUIE2 = "Witness2"
        const val MYLPALE_LIDMAATGUID = "MemberGUID"
        const val MYLPALE_CODEGUID = "CodeGUID"

        // GAWES TABLE COLUMNS
        const val GAWES_TABLENAME = "Gifts"
        const val GAWES_GAWEGUID = "$GAWES_TABLENAME.GiftGUID"
        const val GAWES_LidmaatGUID = "$GAWES_TABLENAME.MemberGUID"
        const val GAWES_KodeGUID = "$GAWES_TABLENAME.CodeGUID"

        // PASSIES TABLE COLUMNS
        const val PASSIES_TABLENAME = "Passions"
        const val PASSIES_PASSIEGUID = "$PASSIES_TABLENAME.PassionGUID"
        const val PASSIES_LidmaatGUID = "$PASSIES_TABLENAME.MemberGUID"
        const val PASSIES_KodeGUID = "$PASSIES_TABLENAME.CodeGUID"

        // MEELEWING TABLE COLUMNS
        const val MEELEWING_TABLENAME = "Involvements"
        const val MEELEWING_MEELEWINGGUID = "$MEELEWING_TABLENAME.InvolvementsGUID"
        const val MEELEWING_KODEGUID = "$MEELEWING_TABLENAME.CodeGUID"
        const val MEELEWING_LIDMAATGUID = "$MEELEWING_TABLENAME.MemberGUID"

        // WKR GROEPE TABLE COLUMNS
        const val WKR_GROEPE_TABLENAME = "wkrGroepe"
        const val WKR_GROEPE_GROEPGUID = "GroepGUID"
        const val WKR_GROEPE_GROEPID = "GroepID"
        const val WKR_GROEPE_NAAM = "Naam"
        const val WKR_GROEPE_ADRES = "Adres"
        const val WKR_GROEPE_TIPE = "Tipe"

        const val WKR_LIDMATE2GROEPE_TABLENAME = "wkrLidmate2Groepe"
        const val WKR_LIDMATE2GROEPE_LidmaatGroepID = "LidmaatGroepID"
        const val WKR_LIDMATE2GROEPE_LidmaatGUID = "LidmaatGUID"
        const val WKR_LIDMATE2GROEPE_GROEPID = "GroepID"
        const val WKR_LIDMATE2GROEPE_GROEPROL = "GroepRol"
        const val WKR_LIDMATE2GROEPE_AANSLUITDATUM = "AansluitDatum"
        const val WKR_LIDMATE2GROEPE_UITTREEDATUM = "UittreeDatum"

        // ARGIEF TABLE COLUMNS
        const val argief_MemberGUID = "MemberGUID"
        const val argief_Surname = "Surname"
        const val argief_Name = "Name"
        const val argief_MaidenName = "MaidenName"
        const val argief_MemberStatus = "MemberStatus"
        const val argief_CertificateStatus = "CertificateStatus"
        const val argief_PreviousCongregation = "PreviousCongregation"
        const val argief_DateReceived = "DateReceived"
        const val argief_Comment = "Comment"
        const val argief_Reason = "Reason"
        const val argief_ResignationDetail = "ResignationDetail"
        const val argief_DepartureTo = "DepartureTo"
        const val argief_DepartureToGUID = "DepartureToGUID"
        const val argief_DepartureDate = "DepartureDate"
        const val argief_Document = "Document"
        const val argief_NewAddress = "NewAddress"
        const val argief_DateOfBirth = "DateOfBirth"
        const val argief_Gender = "Gender"
        const val argief_MaritalStatus = "MaritalStatus"
        const val argief_BaptismDate = "BaptismDate"
        const val argief_BaptismMinister = "BaptismMinister"
        const val argief_Father = "Father"
        const val argief_Mother = "Mother"
        const val argief_ConfessionMinister = "ConfessionMinister"
        const val argief_ConfessionDate = "ConfessionDate"
        const val argief_AcceptanceDate = "AcceptanceDate"
        const val argief_User = "User"
        const val argief_BaptismRemark = "BaptismRemark"
        const val argief_ConfessionRemark = "ConfessionRemark"
        const val argief_OldAddress = "OldAddress"
        const val argief_ResignationRemark = "ResignationRemark"
        const val argief_ArchiveDate = "ArchiveDate"

        // LOCAL LIDMAAT INFO
        const val INFO_LIDMAAT_GUID = "Member_GUID"
        const val INFO_FOTO_PATH = "Foto_Path"
        const val INFO_GROUP = "Group"
        const val INFO_TABLENAME = "WKR_Info"

        // CONSTANTS
        const val THUMBSIZE = 96
        const val GROEPLIST_LOADER = 500
        const val GROEPLEDE_LOADER = 501
        const val WKR_GROEPLIST_LOADER = 502
        const val WKR_GROEPLEDE_LOADER = 503
        const val MEELEWING_LOADER = 504
        const val MEELEWINGLEDE_LOADER = 505
        const val GAWES_LOADER = 506
        const val GAWESLEDE_LOADER = 507
        const val PASSIES_LOADER = 508
        const val PASSIESLEDE_LOADER = 509
        const val ARGIEF_SOEK_LOADER = 511
        const val ARGIEF_LOADER = 510

        const val KEY_LOG_VOIP = "LOG_VOIP"

        @Deprecated("Use getWkrDir(context) for modern Scoped Storage compatibility")
        @JvmField val WkrDir = Environment.getExternalStorageDirectory().toString() + "/WinkerkReader/"
        @Deprecated("Use getFotoDir(context) for modern Scoped Storage compatibility")
        @JvmField val FotoDir = Environment.getExternalStorageDirectory().toString() + "/WinkerkReader/Fotos/"
        @Deprecated("Use getCacheDir(context) for modern Scoped Storage compatibility")
        @JvmField val CacheDir = Environment.getExternalStorageDirectory().toString() + "/WinkerkReader/Fotos/Cache/"

        @JvmStatic
        fun getWkrDir(context: Context): String {
            return (context.getExternalFilesDir(null) ?: context.filesDir).absolutePath + "/"
        }

        @JvmStatic
        fun getFotoDir(context: Context): String {
            val dir = File(getWkrDir(context), "Fotos")
            if (!dir.exists()) dir.mkdirs()
            return dir.absolutePath + "/"
        }

        @JvmStatic
        fun getCacheDir(context: Context): String {
            val dir = File(getFotoDir(context), "Cache")
            if (!dir.exists()) dir.mkdirs()
            return dir.absolutePath + "/"
        }
        @JvmStatic
        fun isValidGender(gender: Int): Boolean {
            return gender == GENDER_UNKNOWN || gender == GENDER_MALE || gender == GENDER_FEMALE
        }
    }
}