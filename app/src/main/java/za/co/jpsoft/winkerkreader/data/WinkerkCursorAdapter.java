package za.co.jpsoft.winkerkreader.data;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import za.co.jpsoft.winkerkreader.MainActivity2;
import za.co.jpsoft.winkerkreader.R;
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry;
import static za.co.jpsoft.winkerkreader.data.CursorDataExtractor.*;

import org.joda.time.DateTime;
import org.joda.time.Years;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.File;
import java.text.Normalizer;

import static za.co.jpsoft.winkerkreader.R.color.selected_view;
import static za.co.jpsoft.winkerkreader.Utils.fixphonenumber;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.*;
import static za.co.jpsoft.winkerkreader.Utils.parseDate;

import androidx.core.content.ContextCompat;

/**
 * Optimized CursorAdapter with safe data extraction
 * All column names are accessed WITHOUT brackets - they're added by the helper
 */
public class WinkerkCursorAdapter extends CursorAdapter {

    private static final String TAG = "Winkerk_CursorAdaptor";

    private static final int SECTIONED_STATE = 1;
    private static final int SECTIONED_STATE2 = 2;
    private static final int REGULAR_STATE = 3;

    public final int THUMBSIZE = 48;

    private int[] mRowStates;
    private final LruCache<String, Bitmap> imageCache;
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("dd/MM/yyyy");

    private static class MemberData {
        String name = "";
        String surname = "";
        String familyHead = "";
        String gender = "";
        String congregation = "";
        String cellphone = "";
        String landline = "";
        String email = "";
        String ward = "";
        String address = "";
        String birthday = "";
        String weddingDate = "";
        String picturePath = "";
        int tag = 0;
        String age = "?";
        String weddingYears = "?";
        DateTime birthdayDT = null;
        DateTime weddingDT = null;
    }

    public static class ViewHolder {
        public final LinearLayout mainBlock;
        public final ImageView eposImageView;
        public final ImageView fotoImageView;
        public final ImageView koekImageView;
        public final ImageView whatsappImageView;
        public final LinearLayout selBlock;
        public final LinearLayout telBlock;
        public final LinearLayout separatorBlock;
        public final TextView cellTextView;
        public final TextView nameTextView;
        public final TextView ouderdomTextView;
        public final TextView telTextView;
        public final TextView vanTextView;
        public final TextView verjaarTextView;
        public final TextView wykTextView;
        public final FrameLayout fotoFrame;
        public final ImageView fotoFrameOverlay;
        public final TextView spearatorTextView;
        public final TextView spearatorWykTextView;
        public final TextView huwelikTextView;
        public final ImageView ringImageView;

        public ViewHolder(View view) {
            mainBlock = view.findViewById(R.id.itemmain);
            nameTextView = view.findViewById(R.id.list_name);
            vanTextView = view.findViewById(R.id.list_van);
            cellTextView = view.findViewById(R.id.list_cellnumber);
            telTextView = view.findViewById(R.id.list_landlyn);
            wykTextView = view.findViewById(R.id.list_wyk);
            ouderdomTextView = view.findViewById(R.id.list_ouderdom);
            verjaarTextView = view.findViewById(R.id.list_verjaar);
            huwelikTextView = view.findViewById(R.id.list_huwelik);
            koekImageView = view.findViewById(R.id.list_bday);
            eposImageView = view.findViewById(R.id.list_epos);
            whatsappImageView = view.findViewById(R.id.list_whatsapp);
            fotoImageView = view.findViewById(R.id.list_kontak_foto);
            fotoFrameOverlay = view.findViewById(R.id.circle_crop);
            selBlock = view.findViewById(R.id.list_cellBlock);
            telBlock = view.findViewById(R.id.list_telBlock);
            fotoFrame = view.findViewById(R.id.kontak_frame);
            separatorBlock = view.findViewById(R.id.list_seperatorBlok);
            spearatorTextView = view.findViewById(R.id.list_separator);
            spearatorWykTextView = view.findViewById(R.id.list_separatorwyk);
            ringImageView = view.findViewById(R.id.list_ring);
        }
    }

    public WinkerkCursorAdapter(Context context, Cursor cursor) {
        super(context, cursor, 0);

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        imageCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        if (cursor != null) {
            mRowStates = new int[cursor.getCount()];
        }
    }

    @Override
    public Cursor swapCursor(Cursor newCursor) {
        Cursor old = super.swapCursor(newCursor);
        notifyDataSetChanged();
        if (newCursor != null) {
            mRowStates = new int[newCursor.getCount()];
        }
        return old;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        if (cursor == null) {
            return null;
        }
        Log.v(TAG, "newView");
        View view;
        if (LISTVIEW == 1) {
            view = LayoutInflater.from(context).inflate(R.layout.list_item, parent, false);
        } else {
            view = LayoutInflater.from(context).inflate(R.layout.list_item_2, parent, false);
        }
        ViewHolder viewHolder = new ViewHolder(view);
        view.setTag(viewHolder);
        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        Log.v(TAG, "bindView");

        if ((cursor == null) || "GEMEENTENAAM".equals(LOADER) || "DATADATUM".equals(LOADER)) {
            return;
        }

        ViewHolder vh = (ViewHolder) view.getTag();

        // Extract member data using safe methods (NO BRACKETS IN COLUMN NAMES!)
        MemberData member = extractMemberData(cursor);

        // Apply display settings
        applySearchBackground(view);
        applyCongregationColor(view, member.congregation);
        applyVisibilitySettings(vh);
        resetViewState(vh);

        // Bind data to views
        bindPhotoData(vh, member, context);
        bindSelectionState(view, vh, member);
        bindBasicInfo(vh, member);
        bindContactInfo(vh, member);
        bindAgeInfo(vh, member);
        bindWeddingInfo(vh, member);
        bindEmailIndicator(vh, member);

        // Apply search highlighting
        applySearchHighlighting(vh);

        // Handle separators
        handleSeparators(view, vh, cursor, member);
    }

    /**
     * Extract all member data from cursor into MemberData object
     * Uses CursorDataExtractor helper methods for safe access
     */
    private MemberData extractMemberData(Cursor cursor) {
        MemberData member = new MemberData();

        // Extract basic info - NO BRACKETS in column names!
        member.name = getSafeString(cursor, LIDMATE_NOEMNAAM, "");
        member.surname = getSafeString(cursor, LIDMATE_VAN, "");
        member.gender = getSafeString(cursor, LIDMATE_GESLAG, "");
        member.congregation = getSafeString(cursor, LIDMATE_GEMEENTE, "");
        member.familyHead = getSafeString(cursor, LIDMATE_GESINSHOOFGUID, "");

        // Extract contact info
        member.cellphone = getSafeString(cursor, LIDMATE_SELFOON, "");
        member.landline = getSafeString(cursor, LIDMATE_LANDLYN, "");
        member.email = getSafeString(cursor, LIDMATE_EPOS, "");
        member.ward = getSafeString(cursor, LIDMATE_WYK, "");
        member.address = getNonEmptyString(cursor, LIDMATE_STRAATADRES, "GEEN");

        // Extract dates and calculate ages
        member.birthday = getSafeString(cursor, LIDMATE_GEBOORTEDATUM, "");
        if (!member.birthday.isEmpty() && member.birthday.length() >= 10) {
            try {
                member.birthdayDT = parseDate(member.birthday.substring(0, 10));
                Years years = Years.yearsBetween(member.birthdayDT, DateTime.now());
                if (years.getYears() >= 0) {
                    member.age = Integer.toString(years.getYears());
                }
            } catch (Exception e) {
                // Keep as "?"
            }
        }

        member.weddingDate = getSafeString(cursor, LIDMATE_HUWELIKSDATUM, "");
        if (!member.weddingDate.isEmpty() && member.weddingDate.length() >= 10) {
            try {
                member.weddingDT = parseDate(member.weddingDate.substring(0, 10));
                Years years = Years.yearsBetween(member.weddingDT, DateTime.now());
                if (years.getYears() >= 0) {
                    member.weddingYears = Integer.toString(years.getYears());
                }
            } catch (Exception e) {
                // Keep as "?"
            }
        }

        // Extract photo and tag
        member.picturePath = getSafeString(cursor, LIDMATE_PICTUREPATH, "");
        member.tag = getSafeInt(cursor, LIDMATE_TAG, 0);

        return member;
    }

    private void applySearchBackground(View view) {
        if (winkerkEntry.SOEKLIST) {
            view.setBackgroundColor(Color.LTGRAY);
        }
    }

    private void applyCongregationColor(View view, String congregation) {
        if (congregation.equals(GEMEENTE_NAAM)) {
            view.setBackgroundColor(GEMEENTE_KLEUR);
        } else if (congregation.equals(GEMEENTE2_NAAM)) {
            view.setBackgroundColor(GEMEENTE2_KLEUR);
        } else if (congregation.equals(GEMEENTE3_NAAM)) {
            view.setBackgroundColor(GEMEENTE3_KLEUR);
        }
    }

    private void applyVisibilitySettings(ViewHolder vh) {
        vh.fotoFrame.setVisibility(LIST_FOTO ? View.VISIBLE : View.GONE);
        vh.ouderdomTextView.setVisibility(LIST_OUDERDOM ? View.VISIBLE : View.GONE);
        vh.wykTextView.setVisibility(LIST_WYK ? View.VISIBLE : View.GONE);
        vh.huwelikTextView.setVisibility(LIST_HUWELIKBLOK ? View.VISIBLE : View.GONE);
        vh.eposImageView.setVisibility(LIST_EPOS ? View.VISIBLE : View.GONE);

        if (LIST_VERJAARBLOK) {
            vh.koekImageView.setVisibility(View.VISIBLE);
            vh.verjaarTextView.setVisibility(View.VISIBLE);
        } else {
            vh.koekImageView.setVisibility(View.GONE);
            vh.verjaarTextView.setVisibility(View.GONE);
        }
    }

    private void resetViewState(ViewHolder vh) {
        vh.koekImageView.setVisibility(View.GONE);
        vh.selBlock.setVisibility(View.GONE);
        vh.telBlock.setVisibility(View.GONE);
        vh.whatsappImageView.setVisibility(View.GONE);
        vh.huwelikTextView.setText("");
        vh.ringImageView.setVisibility(View.GONE);
        vh.eposImageView.setVisibility(View.GONE);
        vh.wykTextView.setVisibility(View.GONE);
    }

    private void bindPhotoData(ViewHolder vh, MemberData member, Context context) {
        float scale = context.getResources().getDisplayMetrics().density;
        int pixels = (int) (30 * scale + 0.5f);

        if (!member.picturePath.isEmpty() && LISTVIEW == 2) {
            String path = CacheDir + member.picturePath;

            Bitmap cachedBitmap = imageCache.get(path);
            if (cachedBitmap != null) {
                pixels = (int) (50 * scale + 0.5f);
                vh.fotoImageView.getLayoutParams().height = pixels;
                vh.fotoImageView.getLayoutParams().width = pixels;
                vh.fotoImageView.setImageBitmap(cachedBitmap);
                vh.fotoImageView.requestLayout();
            } else {
                File file = new File(path);
                if (file.exists()) {
                    pixels = (int) (50 * scale + 0.5f);
                    vh.fotoImageView.getLayoutParams().height = pixels;
                    vh.fotoImageView.getLayoutParams().width = pixels;
                    vh.fotoImageView.requestLayout();
                    vh.fotoImageView.setTag(path);
                    new LoadImage(vh.fotoImageView, imageCache).execute();
                } else {
                    setDefaultImage(vh, member.gender, pixels);
                }
            }
        } else {
            setDefaultImage(vh, member.gender, pixels);
        }

        if ("2".equals(winkerkEntry.RECORDSTATUS)) {
            vh.fotoFrameOverlay.setImageResource(R.drawable.circle_crop_onaktief);
        } else {
            vh.fotoFrameOverlay.setImageResource(R.drawable.circle_crop);
        }
    }

    private void bindSelectionState(View view, ViewHolder vh, MemberData member) {
        if (member.tag == 1) {
            view.setBackgroundColor(ContextCompat.getColor(view.getContext(), selected_view));
            vh.fotoFrameOverlay.setImageResource(R.drawable.circle_crop_selected);
        } else {
            if ("2".equals(winkerkEntry.RECORDSTATUS)) {
                view.setBackgroundColor(Color.parseColor("#ffd0d0"));
                vh.fotoFrameOverlay.setImageResource(R.drawable.circle_crop_onaktief);
            } else {
                vh.fotoFrameOverlay.setImageResource(R.drawable.circle_crop);
            }
        }
    }

    private void bindBasicInfo(ViewHolder vh, MemberData member) {
        vh.nameTextView.setText(member.name);
        vh.vanTextView.setText(member.surname);
    }

    private void bindContactInfo(ViewHolder vh, MemberData member) {
        // Cellphone
        if (!member.cellphone.isEmpty() && !member.cellphone.isBlank()) {
            String formattedCell = fixphonenumber(member.cellphone);
            if (LIST_SELFOON) {
                vh.selBlock.setVisibility(View.VISIBLE);
                vh.cellTextView.setText(formattedCell);
            } else {
                vh.selBlock.setVisibility(View.GONE);
            }

            // WhatsApp indicator
            if (!MainActivity2.whatsappContacts.isEmpty() && LIST_WHATSAPP) {
                if (MainActivity2.whatsappContacts.contains(formattedCell)) {
                    vh.whatsappImageView.setVisibility(View.VISIBLE);
                }
            }
        } else {
            vh.cellTextView.setText("");
        }

        // Ward
        if (!member.ward.isEmpty() && LIST_WYK &&
                ("VAN".equals(SORTORDER) || "OUDERDOM".equals(SORTORDER) ||
                        "VERJAAR".equals(SORTORDER) || "HUWELIK".equals(SORTORDER))) {
            vh.wykTextView.setVisibility(View.VISIBLE);
            vh.wykTextView.setText(member.ward);
        } else {
            vh.wykTextView.setText("");
        }

        // Landline
        if (!member.landline.isEmpty() && !member.landline.isBlank()) {
            String formattedLandline = fixphonenumber(member.landline);
            if (LIST_TELEFOON) {
                vh.telBlock.setVisibility(View.VISIBLE);
            } else {
                vh.telBlock.setVisibility(View.GONE);
            }
            vh.telTextView.setText(formattedLandline);
        } else {
            vh.telTextView.setText("");
        }
    }

    private void bindAgeInfo(ViewHolder vh, MemberData member) {
        if (!member.birthday.isEmpty() && LIST_OUDERDOM && member.birthday.length() >= 10) {
            vh.ouderdomTextView.setText("(" + member.age + ")");
            vh.ouderdomTextView.setVisibility(View.VISIBLE);

            String day = member.birthday.substring(0, 2);
            String month = member.birthday.substring(3, 5);
            String monthAbbr = getMonthAbbreviation(month);

            vh.verjaarTextView.setText(day + " " + monthAbbr);

            if (member.birthdayDT != null) {
                DateTime today = DateTime.now();
                if (member.birthdayDT.monthOfYear().get() == today.monthOfYear().get()) {
                    if (LIST_VERJAARBLOK && member.birthdayDT.dayOfMonth().get() == today.dayOfMonth().get()) {
                        vh.koekImageView.setVisibility(View.VISIBLE);
                    } else {
                        vh.koekImageView.setVisibility(View.GONE);
                    }
                }
            }
        } else {
            vh.ouderdomTextView.setText("");
        }
    }

    private void bindWeddingInfo(ViewHolder vh, MemberData member) {
        vh.huwelikTextView.setVisibility(View.GONE);

        if (!member.weddingDate.isEmpty() && LIST_HUWELIKBLOK && member.weddingDate.length() > 6) {
            vh.ringImageView.setVisibility(View.VISIBLE);

            String day = member.weddingDate.substring(0, 2);
            String month = member.weddingDate.substring(3, 5);
            String monthAbbr = getMonthAbbreviation(month);

            String display = day + " " + monthAbbr + " (" + member.weddingYears + ")";
            vh.huwelikTextView.setText(display);
            vh.huwelikTextView.setVisibility(View.VISIBLE);
        }
    }

    private void bindEmailIndicator(ViewHolder vh, MemberData member) {
        vh.eposImageView.setVisibility(View.GONE);
        if (!member.email.isEmpty() && !member.email.isBlank() && LIST_EPOS) {
            vh.eposImageView.setVisibility(View.VISIBLE);
        }
    }

    private void applySearchHighlighting(ViewHolder vh) {
        if (winkerkEntry.SOEKLIST) {
            if (vh.vanTextView.getText() != null) {
                vh.vanTextView.setText(highlight(SOEK, vh.vanTextView.getText().toString()));
            }
            if (vh.nameTextView.getText() != null) {
                vh.nameTextView.setText(highlight(SOEK, vh.nameTextView.getText().toString()));
            }
            if (vh.cellTextView.getText() != null) {
                vh.cellTextView.setText(highlight(SOEK, vh.cellTextView.getText().toString()));
            }
            if (vh.telTextView.getText() != null) {
                vh.telTextView.setText(highlight(SOEK, vh.telTextView.getText().toString()));
            }
            if (vh.spearatorTextView.getText() != null) {
                vh.spearatorTextView.setText(highlight(SOEK, vh.spearatorTextView.getText().toString()));
            }
        }
    }

    private void handleSeparators(View view, ViewHolder vh, Cursor cursor, MemberData member) {
        boolean showSeparator = false;
        boolean showSeparator2 = false;
        String previousMaand = "";
        String maand = "";

        if (mRowStates != null) {
            int position = cursor.getPosition();

            switch (mRowStates[position]) {
                case SECTIONED_STATE:
                    showSeparator = true;
                    showSeparator2 = true;
                    break;
                case SECTIONED_STATE2:
                    showSeparator = false;
                    showSeparator2 = true;
                    break;
                case REGULAR_STATE:
                    showSeparator = false;
                    showSeparator2 = false;
                    break;

                default:
                    if (position == 0) {
                        showSeparator = true;
                        showSeparator2 = true;
                    } else {
                        cursor.moveToPosition(position - 1);
                        MemberData prevMember = extractMemberData(cursor);
                        cursor.moveToPosition(position);

                        showSeparator = false;

                        switch (winkerkEntry.SORTORDER) {
                            case "WYK":
                                if (prevMember.ward != null && member.ward != null) {
                                    if (!prevMember.ward.equals(member.ward)) {
                                        showSeparator = true;
                                    }
                                }
                                if (!member.familyHead.equals(prevMember.familyHead)) {
                                    showSeparator2 = true;
                                }
                                break;
                            case "GESINNE":
                                if (!member.familyHead.equals(prevMember.familyHead)) {
                                    showSeparator = true;
                                }
                                break;
                            case "VAN":
                                if (!member.surname.isEmpty() && !prevMember.surname.isEmpty()) {
                                    char[] previousNameArray = prevMember.surname.toCharArray();
                                    char[] nameArray = member.surname.toCharArray();
                                    if (nameArray[0] != previousNameArray[0]) {
                                        showSeparator = true;
                                    }
                                }
                                break;
                            case "ADRES":
                                if (!member.address.equals(prevMember.address)) {
                                    showSeparator = true;
                                }
                                break;
                            case "VERJAAR":
                                if (!prevMember.birthday.isEmpty() && !member.birthday.isEmpty()) {
                                    previousMaand = prevMember.birthday.substring(3, 5);
                                    maand = member.birthday.substring(3, 5);
                                    if (!maand.equals(previousMaand)) {
                                        showSeparator = true;
                                    }
                                }
                                break;
                            case "HUWELIK":
                                if (!prevMember.weddingDate.isEmpty() && !member.weddingDate.isEmpty()) {
                                    if (prevMember.weddingDate.length() >= 7 && member.weddingDate.length() >= 5) {
                                        previousMaand = prevMember.weddingDate.substring(3, 5);
                                        maand = member.weddingDate.substring(3, 5);
                                        if (!maand.equals(previousMaand)) {
                                            showSeparator = true;
                                        }
                                    }
                                }
                                break;
                            case "OUDERDOM":
                                if (!member.age.equals(prevMember.age)) {
                                    showSeparator = true;
                                }
                                break;
                        }
                    }

                    // Cache it
                    if (showSeparator && showSeparator2) mRowStates[position] = SECTIONED_STATE;
                    if (!showSeparator && showSeparator2) mRowStates[position] = SECTIONED_STATE2;
                    if (!showSeparator && !showSeparator2) mRowStates[position] = REGULAR_STATE;

                    break;
            }
        }

        if (showSeparator || showSeparator2) {
            configureSeparatorDisplay(vh, member, showSeparator, showSeparator2);
            vh.separatorBlock.setVisibility(View.VISIBLE);
        } else {
            vh.separatorBlock.setVisibility(View.GONE);
        }
    }

    private void configureSeparatorDisplay(ViewHolder vh, MemberData member,
                                           boolean showSeparator, boolean showSeparator2) {
        switch (SORTORDER) {
            case "WYK":
                String temp2 = member.address.replaceAll("\r", "\n");
                temp2 = temp2.replaceAll("\n\n", "\n");
                while (temp2.endsWith("\n")) {
                    temp2 = temp2.substring(0, temp2.length() - 1);
                }
                SpannableString sp = new SpannableString("");
                if (showSeparator) {
                    sp = new SpannableString(member.ward + "\n" + temp2);
                    int ll = member.ward.length();
                    sp.setSpan(new RelativeSizeSpan(1.5f), 0, ll, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sp.setSpan(new RelativeSizeSpan(0.8f), ll + 1, sp.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                if (showSeparator2 && !showSeparator) {
                    sp = new SpannableString(temp2);
                    sp.setSpan(new RelativeSizeSpan(0.8f), 0, sp.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                vh.spearatorTextView.setText(sp);
                vh.spearatorWykTextView.setText("Wyk: " + member.ward);
                break;
            case "VAN":
                vh.spearatorTextView.setText(member.surname.toCharArray(), 0, 1);
                vh.spearatorWykTextView.setText("");
                break;
            case "GESINNE":
                String temp3 = member.address.replaceAll("\r", "\n");
                temp3 = temp3.replaceAll("\n\n", "\n");
                while (temp3.endsWith("\n")) {
                    temp3 = temp3.substring(0, temp3.length() - 1);
                }
                SpannableString sp2 = new SpannableString(temp3);
                sp2.setSpan(new RelativeSizeSpan(0.8f), 0, sp2.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                vh.spearatorTextView.setText(sp2);
                vh.spearatorWykTextView.setText("Wyk: " + member.ward);
                break;
            case "ADRES":
                String temp = member.address.replaceAll("\r", "\n");
                temp = temp.replaceAll("\n\n", "\n");
                while (temp.endsWith("\n")) {
                    temp = temp.substring(0, temp.length() - 1);
                }
                vh.spearatorWykTextView.setText("Wyk: " + member.ward);
                vh.spearatorTextView.setText(temp);
                break;
            case "VERJAAR":
            case "HUWELIK":
                vh.spearatorWykTextView.setText("");
                String month = "";
                if ("VERJAAR".equals(SORTORDER)) {
                    if (!member.birthday.isEmpty() && member.birthday.length() >= 5)
                        month = member.birthday.substring(3, 5);
                }
                if ("HUWELIK".equals(SORTORDER)) {
                    if (!member.weddingDate.isEmpty() && member.weddingDate.length() >= 5) {
                        month = member.weddingDate.substring(3, 5);
                    }
                }
                String monthName = getMonthFullName(month);
                vh.spearatorTextView.setText(monthName);
                break;
            case "OUDERDOM":
                vh.spearatorTextView.setText(member.age + " jaar");
                vh.spearatorWykTextView.setText("");
                break;
        }
    }

    private void setDefaultImage(ViewHolder viewHolder, String gender, int pixels) {
        if ("Manlik".equals(gender)) {
            viewHolder.fotoImageView.setImageResource(R.drawable.kman);
        } else {
            viewHolder.fotoImageView.setImageResource(R.drawable.kvrou);
        }
        viewHolder.fotoImageView.getLayoutParams().height = pixels;
        viewHolder.fotoImageView.getLayoutParams().width = pixels;
        viewHolder.fotoImageView.requestLayout();
    }

    private String getMonthAbbreviation(String month) {
        switch (month) {
            case "01": return "Jan";
            case "02": return "Feb";
            case "03": return "Mrt";
            case "04": return "Apr";
            case "05": return "Mei";
            case "06": return "Jun";
            case "07": return "Jul";
            case "08": return "Aug";
            case "09": return "Sept";
            case "10": return "Okt";
            case "11": return "Nov";
            case "12": return "Des";
            default: return "";
        }
    }

    private String getMonthFullName(String month) {
        switch (month) {
            case "01": return "Januarie";
            case "02": return "Februarie";
            case "03": return "Maart";
            case "04": return "April";
            case "05": return "Mei";
            case "06": return "Junie";
            case "07": return "Julie";
            case "08": return "Augustus";
            case "09": return "September";
            case "10": return "Oktober";
            case "11": return "November";
            case "12": return "Desember";
            default: return "";
        }
    }

    public static CharSequence highlight(String search, String originalText) {
        if (search == null || originalText == null) {
            return originalText;
        }

        String normalizedText = Normalizer.normalize(originalText, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase();
        int start = normalizedText.indexOf(search.toLowerCase());
        if (start < 0) {
            return originalText;
        } else {
            Spannable highlighted = new SpannableString(originalText);
            while (start > -1) {
                int spanStart = Math.min(start, originalText.length());
                int spanEnd = Math.min(start + search.length(), originalText.length());
                highlighted.setSpan(new BackgroundColorSpan(Color.YELLOW),
                        spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                start = normalizedText.indexOf(search, spanEnd);
            }
            return highlighted;
        }
    }

    static class LoadImage extends AsyncTask<Void, Void, Bitmap> {
        private final ImageView imv;
        private final String path;
        private final LruCache<String, Bitmap> cache;

        public LoadImage(ImageView imv, LruCache<String, Bitmap> cache) {
            this.imv = imv;
            this.path = imv.getTag().toString();
            this.cache = cache;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            if (isCancelled()) {
                return null;
            }

            Bitmap bitmap = null;
            File file = new File(path);

            if (file.exists()) {
                try {
                    bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());

                    if (bitmap != null && !isCancelled()) {
                        int width = 48;
                        int height = 48;
                        bitmap = ThumbnailUtils.extractThumbnail(bitmap, width, height);
                    }
                } catch (Exception e) {
                    Log.e("LoadImage", "Error loading image: " + e.getMessage());
                }
            }

            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if (result != null && imv != null) {
                if (path.equals(imv.getTag())) {
                    imv.setImageBitmap(result);

                    if (cache != null) {
                        cache.put(path, result);
                    }
                }
            }
        }
    }
}