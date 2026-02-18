package za.co.jpsoft.winkerkreader;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.Toast;
import android.content.ClipData;
import android.content.ClipboardManager;

import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.content.ContentUris;

import za.co.jpsoft.winkerkreader.data.WinkerkContract;

import java.util.ArrayList;
import java.util.Date;

import static za.co.jpsoft.winkerkreader.Utils.fixphonenumber;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.*;

/**
 * Handles member-related actions from popup menus
 */
public class MemberActionHandler {
    private static final String TAG = "MemberActionHandler";

    private final AppCompatActivity activity;
    private final Cursor cursor;

    public MemberActionHandler(AppCompatActivity activity, Cursor cursor) {
        this.activity = activity;
        this.cursor = cursor;
    }

    public boolean handleAction(int actionId) {
        try {
            if (actionId == R.id.kyk_lidmaat_detail) {
                return openMemberDetail();
            } else if (actionId == R.id.bel_selfoon) {
                return callCellPhone();
            } else if (actionId == R.id.bel_landlyn) {
                return callLandline();
            } else if (actionId == R.id.stuur_sms) {
                return sendSms();
            } else if (actionId == R.id.stuur_whatsapp) {
                return sendWhatsApp1();
            } else if (actionId == R.id.stuur_whatsapp2) {
                return sendWhatsApp2();
            } else if (actionId == R.id.stuur_whatsapp3) {
                return sendWhatsApp3();
            } else if (actionId == R.id.stuur_epos) {
                return sendEmail();
            } else if (actionId == R.id.kopieer) {
                return copyToClipboard();
            } else if (actionId == R.id.nota) {
                return createCalendarNote();
            } else if (actionId == R.id.copy_to_contacts) {
                return copyToContacts();
            } else {
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling member action: " + actionId, e);
            Toast.makeText(activity, "Error performing action", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private boolean openMemberDetail() {
        try {
            int idIndex = cursor.getColumnIndex("_id");
            int memberId = cursor.getInt(idIndex);

            Intent intent = new Intent(activity, lidmaat_detail_Activity.class);
            Uri memberUri = ContentUris.withAppendedId(WinkerkContract.winkerkEntry.CONTENT_URI, memberId);
            intent.setData(memberUri);

            LIDMAATID = memberId;
            activity.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error opening member detail", e);
            return false;
        }
    }

    private boolean callCellPhone() {
        String phoneNumber = getFormattedPhoneNumber(LIDMATE_SELFOON);
        return makeCall(phoneNumber);
    }

    private boolean callLandline() {
        String phoneNumber = getFormattedPhoneNumber(LIDMATE_LANDLYN);
        return makeCall(phoneNumber);
    }

    private boolean makeCall(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }

        try {
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber));
            activity.startActivity(callIntent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error making call", e);
            return false;
        }
    }

    private boolean sendSms() {
        String phoneNumber = getString(LIDMATE_SELFOON);
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }

        try {
            Intent smsIntent = new Intent(Intent.ACTION_VIEW);
            smsIntent.setData(Uri.parse("sms:" + phoneNumber));

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                smsIntent.setType("vnd.android-dir/mms-sms");
            }

            // Verify that there's an app to handle this intent
            if (smsIntent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivity(smsIntent);
                return true;
            } else {
                Log.e(TAG, "No SMS app available");
                Toast.makeText(activity, "No SMS app found", Toast.LENGTH_SHORT).show();
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending SMS", e);
            Toast.makeText(activity, "Failed to open SMS app", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private boolean sendWhatsApp1() {
        String phoneNumber = getFormattedPhoneNumber(LIDMATE_SELFOON);
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }

        try {
            Uri uri = Uri.parse("smsto: " + phoneNumber);
            Intent intent = new Intent(Intent.ACTION_SENDTO, uri);
            intent.setPackage("com.whatsapp");
            activity.startActivity(Intent.createChooser(intent, ""));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error sending WhatsApp message (method 1)", e);
            Toast.makeText(activity, "WhatsApp not installed", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private boolean sendWhatsApp2() {
        String phoneNumber = getFormattedPhoneNumber(LIDMATE_SELFOON);
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }

        try {
            PackageManager packageManager = activity.getPackageManager();
            Intent intent = new Intent(Intent.ACTION_VIEW);

            String url = "https://api.whatsapp.com/send?phone=" + phoneNumber;
            intent.setPackage("com.whatsapp");
            intent.setData(Uri.parse(url));

            if (intent.resolveActivity(packageManager) != null) {
                activity.startActivity(intent);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending WhatsApp message (method 2)", e);
            Toast.makeText(activity, "WhatsApp not installed", Toast.LENGTH_SHORT).show();
            return false;
        }

        Toast.makeText(activity, "WhatsApp not available", Toast.LENGTH_SHORT).show();
        return false;
    }

    private boolean sendWhatsApp3() {
        String phoneNumber = getFormattedPhoneNumber(LIDMATE_SELFOON);
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }

        try {
            Intent intent = new Intent("android.intent.action.MAIN");
            intent.setAction(Intent.ACTION_SEND);
            intent.setPackage("com.whatsapp");
            intent.setType("text/plain");
            intent.putExtra("jid", phoneNumber + "@s.whatsapp.net");
            activity.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error sending WhatsApp message (method 3)", e);
            Toast.makeText(activity, "WhatsApp not installed", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private boolean sendEmail() {
        String email = getString(LIDMATE_EPOS);
        if (email == null || email.isEmpty()) {
            return false;
        }

        try {
            Intent emailIntent = new Intent(Intent.ACTION_VIEW);
            emailIntent.setData(Uri.parse("mailto:" + email));
            activity.startActivity(emailIntent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error sending email", e);
            return false;
        }
    }

    private boolean copyToClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                Toast.makeText(activity, "Clipboard not available", Toast.LENGTH_SHORT).show();
                return false;
            }

            String clipData = buildClipboardData();
            ClipData clip = ClipData.newPlainText("Member Info", clipData);
            clipboard.setPrimaryClip(clip);

            Toast.makeText(activity, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error copying to clipboard", e);
            return false;
        }
    }

    private String buildClipboardData() {
        StringBuilder builder = new StringBuilder();

        addToClipData(builder, "Naam", getString(LIDMATE_NOEMNAAM));
        addToClipData(builder, "Van", getString(LIDMATE_VAN));
        addToClipData(builder, "Selfoon", getString(LIDMATE_SELFOON));
        addToClipData(builder, "Landlyn", getFormattedPhoneNumber(LIDMATE_LANDLYN));
        addToClipData(builder, "Epos", getString(LIDMATE_EPOS));
        addToClipData(builder, "Adres", getString(LIDMATE_STRAATADRES));

        return builder.toString().replaceAll("\r\n\r\n", "\r\n");
    }

    private void addToClipData(StringBuilder builder, String label, String value) {
        if (value != null && !value.isEmpty()) {
            builder.append("\r\n").append(label).append(": ").append(value);
        }
    }

    private boolean createCalendarNote() {
        try {
            Intent intent = new Intent();
            intent.setType("vnd.android.cursor.item/event");
            intent.putExtra("beginTime", new Date().getTime());
            intent.putExtra("endTime", new Date().getTime() + DateUtils.HOUR_IN_MILLIS);

            String name = getString(LIDMATE_NOEMNAAM);
            String surname = getString(LIDMATE_VAN);
            intent.putExtra("title", name + " " + surname);
            intent.putExtra("description", buildClipboardData());
            intent.setAction(Intent.ACTION_EDIT);

            activity.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error creating calendar note", e);
            return false;
        }
    }

    private boolean copyToContacts() {
        try {
            String name = getString(LIDMATE_NOEMNAAM);
            String surname = getString(LIDMATE_VAN);
            String cellPhone = getFormattedPhoneNumber(LIDMATE_SELFOON);
            String landline = getFormattedPhoneNumber(LIDMATE_LANDLYN);
            String email = getString(LIDMATE_EPOS);
            String address = getString(LIDMATE_STRAATADRES);
            String birthday = getString(LIDMATE_GEBOORTEDATUM);

            Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);

            // Basic info
            intent.putExtra(ContactsContract.Intents.Insert.NAME, name + ", " + surname);
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, cellPhone);
            intent.putExtra(ContactsContract.Intents.Insert.PHONE_TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);
            intent.putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, landline);
            intent.putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE_TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_HOME);
            intent.putExtra(ContactsContract.Intents.Insert.EMAIL, email);

            if (address != null) {
                intent.putExtra(ContactsContract.Intents.Insert.POSTAL,
                        address.replace("\r\n", ", "));
            }

            // Birthday data
            if (birthday != null && birthday.length() >= 10) {
                ArrayList<ContentValues> data = new ArrayList<>();
                ContentValues birthdayData = new ContentValues();
                birthdayData.put(ContactsContract.CommonDataKinds.Event.MIMETYPE,
                        ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE);
                birthdayData.put(ContactsContract.CommonDataKinds.Event.TYPE,
                        ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY);
                birthdayData.put(ContactsContract.CommonDataKinds.Event.START_DATE,
                        birthday.substring(0, 10));
                data.add(birthdayData);

                // Nickname data
                ContentValues nicknameData = new ContentValues();
                nicknameData.put(ContactsContract.CommonDataKinds.Nickname.MIMETYPE,
                        ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE);
                nicknameData.put(ContactsContract.CommonDataKinds.Nickname.TYPE,
                        ContactsContract.CommonDataKinds.Nickname.TYPE_SHORT_NAME);
                nicknameData.put(ContactsContract.CommonDataKinds.Nickname.NAME, name);
                data.add(nicknameData);

                intent.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, data);
            }

            activity.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error copying to contacts", e);
            return false;
        }
    }

    private String getString(String columnName) {
        try {
            int index = cursor.getColumnIndex(columnName);
            return index != -1 && !cursor.isNull(index) ? cursor.getString(index) : null;
        } catch (Exception e) {
            Log.e(TAG, "Error getting string for column: " + columnName, e);
            return null;
        }
    }

    private String getFormattedPhoneNumber(String columnName) {
        String phoneNumber = getString(columnName);
        return phoneNumber != null && !phoneNumber.isEmpty() ? fixphonenumber(phoneNumber) : null;
    }
}