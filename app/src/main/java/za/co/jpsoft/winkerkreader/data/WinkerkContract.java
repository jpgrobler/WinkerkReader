package za.co.jpsoft.winkerkreader.data;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Environment;
import android.provider.BaseColumns;

/**
 * Created by Pieter Grobler on 22/07/2017.
 * Updated: Column names corrected - no brackets in definitions
 */

public final class WinkerkContract {
    private WinkerkContract() {
    }

    public static final String CONTENT_AUTHORITY = "za.co.jpsoft.winkerkreader";
    private static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);

    public static final int SEARCH_LIST_REQUEST = 16895;
    public static final int FILTER_LIST_REQUEST = 16896;
    public static final String PREFS_USER_INFO = "WinkerkReader_UserInfo";
    public static final String PREFS_CALL_LOGGER = "CallLoggerPrefs";

    // Display Settings Keys
    public static final String KEY_LIST_FOTO = "LIST_FOTO";
    public static final String KEY_LIST_EPOS = "LIST_EPOS";
    public static final String KEY_LIST_WHATSAPP = "LIST_WHATSAPP";
    public static final String KEY_LIST_VERJAARBLOK = "LIST_VERJAARBLOK";
    public static final String KEY_LIST_OUDERDOM = "LIST_OUDERDOM";
    public static final String KEY_LIST_HUWELIKBLOK = "LIST_HUWELIKBLOK";
    public static final String KEY_LIST_WYK = "LIST_WYK";
    public static final String KEY_LIST_SELFOON = "LIST_SELFOON";
    public static final String KEY_LIST_TELEFOON = "LIST_TELEFOON";

    // Function Settings Keys
    public static final String KEY_AUTOSTART = "auto_start_enabled";
    public static final String KEY_OPROEPMONITOR = "CallMonitor";
    public static final String KEY_OPROEPLOG = "CallLog";
    public static final String KEY_DEFLAYOUT = "DefLayout";
    public static final String KEY_WHATSAPP1 = "Whatsapp1";
    public static final String KEY_WHATSAPP2 = "Whatsapp2";
    public static final String KEY_WHATSAPP3 = "Whatsapp3";
    public static final String KEY_EPOSHTML = "EposHtml";
    public static final String KEY_SELECTED_CALENDAR_ID = "selected_calendar_id";

    // Widget Settings Keys
    public static final String KEY_WIDGET_DOOP = "Widget_Doop";
    public static final String KEY_WIDGET_BELYDENIS = "Widget_Belydenis";
    public static final String KEY_WIDGET_HUWELIK = "Widget_Huwelik";
    public static final String KEY_WIDGET_STERF = "Widget_Sterwe";

    // Color Settings Keys
    public static final String KEY_GEMEENTE_KLEUR = "Gem1_Kleur";
    public static final String KEY_GEMEENTE2_KLEUR = "Gem2_Kleur";
    public static final String KEY_GEMEENTE3_KLEUR = "Gem3_Kleur";
    public static final String CHANNEL_ID = "winkerkReaderServiceChannel";
    public static final String SEARCH_CHECK_BOX = "SEARCH_CHECK_BOX";
    public static final String FILTER_CHECK_BOX = "FILTER_CHECK_BOX";
    public static final String PATH_LIDMATE = "Lidmate";
    public static final String PATH_ADRES = "Adresse";
    public static final String PATH_GESINNE = "Gesinne";
    public static final String PATH_KODES = "Kodes";
    public static final String PATH_GESIN = "Gesin";
    public static final String PATH_FOON = "Foon";
    public static final String PATH_MYLPALE = "Mylpale";
    public static final String PATH_GROEPE = "Groepe";
    public static final String PATH_GROEPE_LYS = "GroepeLys";
    public static final String PATH_VERJAAR = "Verjaar";
    public static final String PATH_GEMEENTE_NAAM = "GemeenteNaam";
    public static final String PATH_OUDERDOM = "Ouderdom";
    public static final String PATH_FOTO = "Foto";
    public static final String PATH_FOTO_UPDATER = "Foto_Updater";
    public static final String PATH_WKR_GROEPE = "WKR_Groepe";
    public static final String PATH_WKR_GROEPLEDE = "WKR_Groeplede";
    public static final String PATH_MEELEWING = "Meelewing";
    public static final String PATH_HUWELIK = "Huwelik";
    public static final String PATH_ARGIEF = "ARCHIVE";

    // Helper method to wrap column names in brackets for SQL
    public static String col(String columnName) {
        if (columnName == null || columnName.isEmpty()) {
            return columnName;
        }
        if (columnName.startsWith("[") && columnName.endsWith("]")) {
            return columnName;
        }
        return "[" + columnName + "]";
    }

    public static final class winkerkEntry implements BaseColumns {

        public static Boolean OPROEPMONITOR = true;
        public static String DEFLAYOUT = "GESIN";
        public static Boolean EPOSHTML = false;

        public static String SORTORDER = "VAN";
        public static String SOEK = "A";
        public static Boolean DETAIL = false;
        public static int LIDMAATID = 0;
        public static String LIDMAATGUID = "";
        public static String GESINNGUID = "";
        public static Boolean SOEKLIST = false;
        public static String LOADER = "";
        public static String GEMEENTE_NAAM = "";
        public static String GEMEENTE_EPOS = "";
        public static int GEMEENTE_KLEUR = 0;
        public static String GEMEENTE2_NAAM = "";
        public static String GEMEENTE2_EPOS = "";
        public static int GEMEENTE2_KLEUR = 0;
        public static String GEMEENTE3_NAAM = "";
        public static String GEMEENTE3_EPOS = "";
        public static int GEMEENTE3_KLEUR = 0;
        public static String id = "";
        public static String RECORDSTATUS = "0";
        public static String GROEP_SMS_NAAM = "";
        public static String DATA_DATUM = "";

        public static Boolean LIST_FOTO = true;
        public static Boolean LIST_VERJAARBLOK = true;
        public static Boolean LIST_HUWELIKBLOK = true;
        public static Boolean LIST_WYK = true;
        public static Boolean LIST_WHATSAPP = true;
        public static Boolean LIST_EPOS = true;
        public static Boolean LIST_OUDERDOM = true;
        public static Boolean LIST_SELFOON = true;
        public static Boolean LIST_TELEFOON = true;

        public static Boolean WHATSAPP1 = true;
        public static Boolean WHATSAPP2 = true;
        public static Boolean WHATSAPP3 = true;

        public static String KEUSE = "Verjaar";

        public static Boolean WIDGET_DOOP = true;
        public static Boolean WIDGET_BELYDENIS = true;
        public static Boolean WIDGET_HUWELIK = true;
        public static Boolean WIDGET_STERF = true;

        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_LIDMATE);
        public static final Uri ARGIEF_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_ARGIEF);

        public static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_LIDMATE;

        public static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_LIDMATE;

        public static final Uri CONTENT_ADRES_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_ADRES);
        public static final String CONTENT_ADRES_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_ADRES;

        public static final Uri CONTENT_GESINNE_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_GESINNE);
        public static final String CONTENT_GESINNE_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_GESINNE;

        public static final Uri CONTENT_GESIN_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_GESIN);
        public static final String CONTENT_GESIN_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_GESIN;

        public static final Uri CONTENT_FOON_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_FOON);
        public static final String CONTENT_FOON_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_FOON;

        public static final Uri CONTENT_MYLPALE_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_MYLPALE);
        public static final String CONTENT_MYLPALE_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_MYLPALE;

        public static final Uri CONTENT_MEELEWING_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_MEELEWING);
        public static final String CONTENT_MEELEWING_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_MEELEWING;

        public static final Uri CONTENT_GROEPE_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_GROEPE);
        public static final String CONTENT_GROEPE_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_GROEPE;

        public static final Uri CONTENT_GROEPE_LYS_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_GROEPE_LYS);
        public static final String CONTENT_GROEPE_LYS_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_GROEPE_LYS;

        public static final Uri LIDMAAT_LOADER_VERJAAR_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_VERJAAR);
        public static final String LIDMAAT_LOADER_VERJAAR_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_VERJAAR;

        public static final Uri CONTENT_GEMEENTE_NAAM_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_GEMEENTE_NAAM);
        public static final String CONTENT_GEMEENTE_NAAM_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_GEMEENTE_NAAM;

        public static final Uri LIDMAAT_LOADER_OUDERDOM_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_OUDERDOM);
        public static final String LIDMAAT_LOADER_OUDERDOM_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_OUDERDOM;

        public static final Uri INFO_LOADER_FOTO_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_FOTO);
        public static final String INFO_LOADER_FOTO_LIST_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_FOTO;

        public static final Uri FOTO_UPDATER_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_FOTO_UPDATER);
        public static final String FOTO_UPDATER_LIST_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_FOTO_UPDATER;

        public static final Uri INFO_LOADER_WKR_GROEPE_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_WKR_GROEPE);
        public static final String INFO_LOADER_WKR_GROEPE_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_WKR_GROEPE;

        public static final String INFO_LOADER_WKR_GROEPE_LIST_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_WKR_GROEPE;

        public static final Uri INFO_LOADER_WKR_GROEPLEDE_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_WKR_GROEPLEDE);
        public static final String INFO_LOADER_WKR_GROEPE_LEDE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_WKR_GROEPLEDE;

        public static final String INFO_LOADER_ARGIEF =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_ARGIEF;

        // DATABASES
        public final static String WINKERK_DB = "WinkerkReader.sqlite";
        public static String WINKERK_DB_DATUM = "";
        public final static String INFO_DB = "wkr_info.db";

        // Table Names
        public final static String LIDMATE_TABLE_NAME = "Members";
        public final static String ADRESSE_TABLENAME = "Addresses";
        public final static String KODES_TABLENAME = "Codes";

        // ==========================================
        // MEMBERS TABLE COLUMNS (NO BRACKETS!)
        // ==========================================
        public final static String LIDMATE_AANKOMSDATUM = "Aankomsdatum";
        public final static String LIDMATE_AANSLUITMETODE = "AansluitMetode";
        public final static String LIDMATE_AANSLUITMETODEGUID = "JoinMethodGUID";
        public final static String LIDMATE_AANVRAGEMEENTE = "AanvraGemeente";
        public final static String LIDMATE_ADRESGUID = "AddressGUID";
        public final static String LIDMATE_ALERGIEE = "Allergieë";
        public final static String LIDMATE_BANKREKENINGNAAM = "BankRekeningnaam";
        public final static String LIDMATE_BANKREKENINGNOMMER = "BankRekeningnommer";
        public final static String LIDMATE_BANKREKENINGTIPE = "BankRekeningTipe";
        public final static String LIDMATE_BANKTAKKODE = "BankTakKode";
        public final static String LIDMATE_BEDIENNINGSTYL = "Bedienningstyl";
        public final static String LIDMATE_BELYDENISOPMERKING = "Belydenisopmerking";
        public final static String LIDMATE_BELYDENISDATUM = "Belydenisaflegging Date";
        public final static String LIDMATE_BELYDENISDS = "Belydenisaflegging Minister";
        public final static String LIDMATE_BEROEP = "Beroep";
        public final static String LIDMATE_BESKIKBAARHEID = "Beskikbaarheid";
        public final static String LIDMATE_BEWYSSTATUS = "Bewysstatus";
        public final static String LIDMATE_BEWYSSTATUSGUID = "CertificateStatusGUID";
        public final static String LIDMATE_BOODSKAPPER = "Boodskapper";
        public final static String LIDMATE_DATUMAANGEVRA = "DatumAangevra";
        public final static String LIDMATE_DATUMONTVANG = "Datum ontvang";
        public final static String LIDMATE_DATUMVERANDER = "DatumVerander";
        public final static String LIDMATE_HUWELIKSDATUM = "Huwelik Date";
        public final static String LIDMATE_HUWELIKDS = "Huwelik Minister";
        public final static String LIDMATE_HUWELIKOPMERKING = "Huwelik Comment";
        public final static String LIDMATE_HUWELSTATUS = "Huwelikstatus";
        public final static String LIDMATE_DOOPOPMERKING = "Doop Comment";
        public final static String LIDMATE_DOOPDATUM = "Doop Date";
        public final static String LIDMATE_DOOPDS = "Doop Minister";
        public final static String LIDMATE_EPOS = "Epos";
        public final static String LIDMATE_FAKS = "Fax";
        public final static String LIDMATE_GEBOORTEDATUM = "Geboortedatum";
        public final static String LIDMATE_GEBRUIKER = "Gebruiker";
        public final static String LIDMATE_GEBRUIKER1 = "User 1";
        public final static String LIDMATE_GEBRUIKER2 = "User 2";
        public final static String LIDMATE_GEBRUIKER3 = "User 3";
        public final static String LIDMATE_GEBRUIKER4 = "User 4";
        public final static String LIDMATE_GEBRUIKER5 = "User 5";
        public final static String LIDMATE_GEBRUIKER6 = "User 6";
        public final static String LIDMATE_GEBRUIKERFLAG = "GebruikerFlag";
        public final static String LIDMATE_GESINSHOOFGUID = "FamilyHeadGUID";
        public final static String LIDMATE_GESINSROL = "Gesinsrol";
        public final static String LIDMATE_GESLAG = "Geslag";
        public final static String LIDMATE_GROEPSINDIKATOR = "GroepsIndikator";
        public final static String LIDMATE_HUISDOKTERNAAM = "Huisdokter";
        public final static String LIDMATE_HUISDOKTERTELEFOON = "Huisdokter tel";
        public final static String LIDMATE_HUWELIKSTATUS = "Huwelikstatus";
        public final static String LIDMATE_KOMMENTAAR = "Kommentaar";
        public final static String LIDMATE_KRONIESEMEDIKASIE = "Kroniese medikasie";
        public final static String LIDMATE_LIDMAATGUID = "MemberGUID";
        public final static String LIDMATE_LIDMAATSTATUS = "Lidmaatstatus";
        public final static String LIDMATE_LIDMAATSTATUSGUID = "MemberStatusGUID";
        public final static String LIDMATE_MEDIESEFONDSAFHANGKODE = "MedicalAidDependantCode";
        public final static String LIDMATE_MEDIESEFONDSHOOFLID = "MedicalAidMainMember";
        public final static String LIDMATE_MEDIESEFONDSNAAM = "MedicalAidName";
        public final static String LIDMATE_MEDIESEFONDSNOMMER = "MedicalAidNumber";
        public final static String LIDMATE_NOEMNAAM = "Noemnaam";
        public final static String LIDMATE_NOODKONTAKNOMMER = "EmergencyContactNumber";
        public final static String LIDMATE_NOODKONTAKPERSOON = "EmergencyContactName";
        public final static String LIDMATE_NOOIENSVAN = "Nooiensvan";
        public final static String LIDMATE_OU_LIDMATE_ID = "Ou_Lidmate_Id";
        public final static String LIDMATE_OU_LIDNR = "OldMemberGUID";
        public final static String LIDMATE_PICTUREPATH = "Fotostoorplek";
        public final static String LIDMATE_REKORDSTATUS = "Rekordstatus";
        public final static String LIDMATE_REKORDSTATUSDATUM = "RekordStatusDatum";
        public final static String LIDMATE_SELFOON = "Selfoon";
        public final static String LIDMATE_SKYPE = "Skype";
        public final static String LIDMATE_STUUREPOS = "StuurEPos";
        public final static String LIDMATE_STUURSMS = "StuurSMS";
        public final static String LIDMATE_TAG = "TAG";
        public final static String LIDMATE_TITEL = "Titel";
        public final static String LIDMATE_VAN = "Van";
        public final static String LIDMATE_VOORLETTERS = "Voorletters";
        public final static String LIDMATE_VOORNAME = "Naam";
        public final static String LIDMATE_VORIGEGEMEENTE = "PreviousCongregation";
        public final static String LIDMATE_WEB = "web";
        public final static String LIDMATE_WERKFOON = "Werk tel";  // FIXED: removed quotes
        public final static String LIDMATE_WERKGEWER = "Werkgewer";
        public final static String LIDMATE_WYK = "Wyk";
        public final static String LIDMATE_LANDLYN = "Landlyn";
        public final static String LIDMATE_POSADRES = "Posadres";
        public final static String LIDMATE_PREDIKANTSWYKGUID = "Predekantswyk";
        public final static String LIDMATE_STRAATADRES = "Straatadres";
        public final static String LIDMATE_KORTADRES = "ShortAddress";
        public final static String LIDMATE_GEMEENTE = "Gemeente";
        public final static String LIDMATE_GEMEENTE_EPOS = "Gemeente epos";

        // GENDER CONSTANTS
        public static final int GENDER_UNKNOWN = 0;
        public static final int GENDER_MALE = 1;
        public static final int GENDER_FEMALE = 2;

        // ADDRESSES TABLE COLUMNS
        public final static String ADRESSE_ADRESGUID = "AddressGUID";
        public final static String ADRESSE_LANDLYN = "Landlyn";
        public final static String ADRESSE_POSADRES = "PostalAddress";
        public final static String ADRESSE_PREDIKANTSWYK = "PastorDistrictGUID";
        public final static String ADRESSE_STRAATADRES = "StreetAddress";
        public final static String ADRESSE_WYKGUID = "DistrictGUID";
        public final static String ADRESSE_PREDIKANTSWYKGUID = "PastorDistrictGUID";

        // SQL SELECTIONS (Use col() helper for brackets)
        public static final String SELECTION_LIDMAAT_WIDGET =
                "SELECT Members._rowid_ as _id, " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_VAN) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_NOEMNAAM) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_GEBOORTEDATUM) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_REKORDSTATUS) + ", " +
                        "quote (" + LIDMATE_TABLE_NAME + "." + col(LIDMATE_LIDMAATGUID) + ") AS MemberGUID, " +
                        "date(SUBSTR(" + col(LIDMATE_GEBOORTEDATUM) + ", 7) || '-' || SUBSTR(" + col(LIDMATE_GEBOORTEDATUM) + ", 4, 2) || '-' || SUBSTR(" + col(LIDMATE_GEBOORTEDATUM) + ", 1, 2)) AS birthdate, " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_GEMEENTE);

        public static final String SELECTION_LIDMAAT_INFO =
                "SELECT Members._rowid_ as _id, " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_TAG) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_VAN) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_VOORNAME) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_NOEMNAAM) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_GEBOORTEDATUM) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_PICTUREPATH) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_GESLAG) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_LANDLYN) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_WERKFOON) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_SELFOON) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_EPOS) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_REKORDSTATUS) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_GESINSROL) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_AANKOMSDATUM) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_PREDIKANTSWYKGUID) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_WYK) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_STRAATADRES) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_KORTADRES) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_GESINSHOOFGUID) + ", " +
                        "date(SUBSTR(" + col(LIDMATE_GEBOORTEDATUM) + ", 7) || '-' || SUBSTR(" + col(LIDMATE_GEBOORTEDATUM) + ", 4, 2) || '-' || SUBSTR(" + col(LIDMATE_GEBOORTEDATUM) + ", 1, 2)) AS birthdate, " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_GEMEENTE);

        public static final String SELECTION_LIDMAAT_INFO_GESINSHOOF = "";

        public static final String SELECTION_LIDMAAT_DETAIL =
                "SELECT Members._rowid_ as _id, " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_TAG) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_VAN) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_VOORNAME) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_NOEMNAAM) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_NOOIENSVAN) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_GEBOORTEDATUM) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_GESLAG) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_HUWELIKSTATUS) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_BEROEP) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_WERKGEWER) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_LIDMAATSTATUS) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_PICTUREPATH) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_LANDLYN) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_WERKFOON) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_SELFOON) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_EPOS) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_REKORDSTATUS) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_AANKOMSDATUM) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_GESINSROL) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_PREDIKANTSWYKGUID) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_WYK) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_STRAATADRES) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_KORTADRES) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_GESINSHOOFGUID) + ", " +
                        "date(SUBSTR(" + col(LIDMATE_GEBOORTEDATUM) + ", 7) || '-' || SUBSTR(" + col(LIDMATE_GEBOORTEDATUM) + ", 4, 2) || '-' || SUBSTR(" + col(LIDMATE_GEBOORTEDATUM) + ", 1, 2)) AS birthdate, " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_GEMEENTE) + ", " +
                        LIDMATE_TABLE_NAME + "." + col(LIDMATE_GEMEENTE);

        public static final String SELECTION_LIDMAAT_FROM_GESINSHOOF = " Members ";

        public static final String SELECTION_LIDMAAT_FROM = LIDMATE_TABLE_NAME;

        public static String SELECTION_LIDMAAT_WHERE =
                " (" + LIDMATE_TABLE_NAME + "." + col(LIDMATE_REKORDSTATUS) + " = " + RECORDSTATUS + " )";

        public static final String SELECTION_HUWELIK_WHERE =
                col(LIDMATE_HUWELIKSTATUS) + " = 'Getroud' ";

        // Group columns
        public final static String GROEP_AANSLUIT = "JoinDate";
        public final static String GROEP_UITTREE = "ResignDate";
        public final static String GROEPADRES = "Address";
        public final static String GROEPNAAM = "Name";
        public final static String GROEPROL = "GroupRol";
        public final static String GROEPTIPE = "TypeCodeGUID";
        public final static String GROEPETABEL_NAME = "Groups";
        public final static String MEMBERGOURPTABEL = "MemberGroupLinks";

        // KODES TABLE COLUMNS
        public final static String KODES_KodeGUID = KODES_TABLENAME + ".CodeGUID";
        public final static String KODES_KategorieGUID = KODES_TABLENAME + ".CategoryGUID";
        public final static String KODES_Beskrywing = KODES_TABLENAME + ".Description";
        public final static String KODES_Stelsel = KODES_TABLENAME + ".System";

        // MYLPALE TABLE COLUMNS
        public final static String MYLPALE_TABLENAME = "Milestones";
        public final static String MYLPALE_DATUM = "Date";
        public final static String MYLPALE_DATUM2 = "Date2";
        public final static String MYLPALE_MYLPAAL = "Description";
        public final static String MYLPALE_KOMMENTAAR = "Comment";
        public final static String MYLPALE_LERAAR = "Pastor";
        public final static String MYLPALE_GEMEENTE = "Congregation";
        public final static String MYLPALE_GETUIE1 = "Witness1";
        public final static String MYLPALE_GETUIE2 = "Witness2";
        public final static String MYLPALE_LIDMAATGUID = "MemberGUID";
        public final static String MYLPALE_CODEGUID = "CodeGUID";

        // GAWES TABLE COLUMNS
        public final static String GAWES_TABLENAME = "Gifts";
        public final static String GAWES_GAWEGUID = GAWES_TABLENAME + ".GiftGUID";
        public final static String GAWES_LidmaatGUID = GAWES_TABLENAME + ".MemberGUID";
        public final static String GAWES_KodeGUID = GAWES_TABLENAME + ".CodeGUID";

        // PASSIES TABLE COLUMNS
        public final static String PASSIES_TABLENAME = "Passions";
        public final static String PASSIES_PASSIEGUID = PASSIES_TABLENAME + ".PassionGUID";
        public final static String PASSIES_LidmaatGUID = PASSIES_TABLENAME + ".MemberGUID";
        public final static String PASSIES_KodeGUID = PASSIES_TABLENAME + ".CodeGUID";

        // MEELEWING TABLE COLUMNS
        public final static String MEELEWING_TABLENAME = "Involvements";
        public final static String MEELEWING_MEELEWINGGUID = MEELEWING_TABLENAME + ".InvolvementsGUID";
        public final static String MEELEWING_KODEGUID = MEELEWING_TABLENAME + ".CodeGUID";
        public final static String MEELEWING_LIDMAATGUID = MEELEWING_TABLENAME + ".MemberGUID";

        // WKR GROEPE TABLE COLUMNS
        public final static String WKR_GROEPE_TABLENAME = "wkrGroepe";
        public final static String WKR_GROEPE_GROEPGUID = "GroepGUID";
        public final static String WKR_GROEPE_GROEPID = "GroepID";
        public final static String WKR_GROEPE_NAAM = "Naam";
        public final static String WKR_GROEPE_ADRES = "Adres";
        public final static String WKR_GROEPE_TIPE = "Tipe";

        public final static String WKR_LIDMATE2GROEPE_TABLENAME = "wkrLidmate2Groepe";
        public final static String WKR_LIDMATE2GROEPE_LidmaatGroepID = "LidmaatGroepID";
        public final static String WKR_LIDMATE2GROEPE_LidmaatGUID = "LidmaatGUID";
        public final static String WKR_LIDMATE2GROEPE_GROEPID = "GroepID";
        public final static String WKR_LIDMATE2GROEPE_GROEPROL = "GroepRol";
        public final static String WKR_LIDMATE2GROEPE_AANSLUITDATUM = "AansluitDatum";
        public final static String WKR_LIDMATE2GROEPE_UITTREEDATUM = "UittreeDatum";

        // ARGIEF TABLE COLUMNS
        public static final String argief_MemberGUID = "MemberGUID";
        public static final String argief_Surname = "Surname";
        public static final String argief_Name = "Name";
        public static final String argief_MaidenName = "MaidenName";
        public static final String argief_MemberStatus = "MemberStatus";
        public static final String argief_CertificateStatus = "CertificateStatus";
        public static final String argief_PreviousCongregation = "PreviousCongregation";
        public static final String argief_DateReceived = "DateReceived";
        public static final String argief_Comment = "Comment";
        public static final String argief_Reason = "Reason";
        public static final String argief_ResignationDetail = "ResignationDetail";
        public static final String argief_DepartureTo = "DepartureTo";
        public static final String argief_DepartureToGUID = "DepartureToGUID";
        public static final String argief_DepartureDate = "DepartureDate";
        public static final String argief_Document = "Document";
        public static final String argief_NewAddress = "NewAddress";
        public static final String argief_DateOfBirth = "DateOfBirth";
        public static final String argief_Gender = "Gender";
        public static final String argief_MaritalStatus = "MaritalStatus";
        public static final String argief_BaptismDate = "BaptismDate";
        public static final String argief_BaptismMinister = "BaptismMinister";
        public static final String argief_Father = "Father";
        public static final String argief_Mother = "Mother";
        public static final String argief_ConfessionMinister = "ConfessionMinister";
        public static final String argief_ConfessionDate = "ConfessionDate";
        public static final String argief_AcceptanceDate = "AcceptanceDate";
        public static final String argief_User = "User";
        public static final String argief_BaptismRemark = "BaptismRemark";
        public static final String argief_ConfessionRemark = "ConfessionRemark";
        public static final String argief_OldAddress = "OldAddress";
        public static final String argief_ResignationRemark = "ResignationRemark";
        public static final String argief_ArchiveDate = "ArchiveDate";

        // LOCAL LIDMAAT INFO
        public static final String INFO_LIDMAAT_GUID = "Member_GUID";
        public static final String INFO_FOTO_PATH = "Foto_Path";
        public static final String INFO_GROUP = "Group";
        public static final String INFO_TABLENAME = "WKR_Info";

        // CONSTANTS
        public static final int THUMBSIZE = 96;
        public static final int GROEPLIST_LOADER = 500;
        public static final int GROEPLEDE_LOADER = 501;
        public static final int WKR_GROEPLIST_LOADER = 502;
        public static final int WKR_GROEPLEDE_LOADER = 503;
        public static final int MEELEWING_LOADER = 504;
        public static final int MEELEWINGLEDE_LOADER = 505;
        public static final int GAWES_LOADER = 506;
        public static final int GAWESLEDE_LOADER = 507;
        public static final int PASSIES_LOADER = 508;
        public static final int PASSIESLEDE_LOADER = 509;
        public static final int ARGIEF_SOEK_LOADER = 511;
        public static final int ARGIEF_LOADER = 510;
        public static int LISTVIEW = 2;
        public static int GROEPVIEW = GROEPLIST_LOADER;

        public static final String WkrDir = Environment.getExternalStorageDirectory() + "/WinkerkReader/";
        public static final String FotoDir = Environment.getExternalStorageDirectory() + "/WinkerkReader/Fotos/";
        public static final String CacheDir = Environment.getExternalStorageDirectory() + "/WinkerkReader/Fotos/Cache/";
        public static final String KEY_LOG_VOIP = "LOG_VOIP";
        public static boolean isValidGender(int gender) {
            return gender == GENDER_UNKNOWN || gender == GENDER_MALE || gender == GENDER_FEMALE;
        }
    }
}