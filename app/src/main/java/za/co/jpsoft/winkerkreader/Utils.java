package za.co.jpsoft.winkerkreader;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.util.Arrays;
import java.util.List;

public class Utils {
    public static String fixphonenumber(String number){
        if (number!= null && (!number.isEmpty())){
            number = number.replaceAll("[^0-9+]", "").replaceAll("\\s+", "");
            if (number!= null && (!number.isEmpty())) {
                if (number.substring(0, 1).equals("0")) {
                    number = "+27" + number.substring(1, number.length());
                } else if (!number.substring(0, 1).equals("+")) {
                    if (number.substring(0, 2).equals("27")) {
                        number = "+" + number;
                    } else
                        number = "+27" + number;
                }
            } else
                number = " ";
        }
        return number;
    }
    public static DateTime parseDate(String date) {
        List<String> patternList = Arrays.asList("dd/MM/yyyy", "dd.MM.yyyy", "dd MM yyy","dd-MM-yyyy");
        for (String pattern : patternList) {
            try {
                return DateTime.parse(date, DateTimeFormat.forPattern(pattern));
            } catch ( Exception a ) {
            }
        }
        return null;
    }
}
