package za.co.jpsoft.winkerkreader;

import android.widget.TextView;

import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry;

/**
 * Handles navigation between different data views via swipe gestures
 */
public class NavigationHandler {

    public static void handleLeftSwipe(MainActivity2 activity, TextView sortOrderView) {
        switch (winkerkEntry.SORTORDER) {
            case "HUWELIK":
                switchTo(activity, sortOrderView, "VAN", "VAN");
                break;
            case "VAN":
                switchTo(activity, sortOrderView, "GESINNE", "GESINNE");
                break;
            case "GESINNE":
                switchTo(activity, sortOrderView, "WYK", "WYK");
                break;
            case "WYK":
                switchTo(activity, sortOrderView, "OUDERDOM", "OUDERDOM");
                break;
            case "OUDERDOM":
                switchTo(activity, sortOrderView, "ADRES", "ADRES");
                break;
            case "ADRES":
                switchTo(activity, sortOrderView, "VERJAAR", "VERJAAR");
                break;
            case "VERJAAR":
                switchTo(activity, sortOrderView, "HUWELIK", "HUWELIK");
                break;
        }
    }

    public static void handleRightSwipe(MainActivity2 activity, TextView sortOrderView) {
        switch (winkerkEntry.SORTORDER) {
            case "HUWELIK":
                switchTo(activity, sortOrderView, "VERJAAR", "VERJAAR");
                break;
            case "VERJAAR":
                switchTo(activity, sortOrderView, "ADRES", "ADRES");
                break;
            case "ADRES":
                switchTo(activity, sortOrderView, "OUDERDOM", "OUDERDOM");
                break;
            case "OUDERDOM":
                switchTo(activity, sortOrderView, "WYK", "WYK");
                break;
            case "WYK":
                switchTo(activity, sortOrderView, "GESINNE", "GESINNE");
                break;
            case "GESINNE":
                switchTo(activity, sortOrderView, "VAN", "VAN");
                break;
            case "VAN":
                switchTo(activity, sortOrderView, "HUWELIK", "HUWELIK");
                break;
        }
    }

    private static void switchTo(MainActivity2 activity, TextView sortOrderView, String sortOrder, String layout) {
        sortOrderView.setBackground(null);
        winkerkEntry.SORTORDER = sortOrder;
        winkerkEntry.DEFLAYOUT = layout;
        winkerkEntry.SOEKLIST = false;
        activity.observeDataset();
    }
}
