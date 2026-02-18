package za.co.jpsoft.winkerkreader.data;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Pieter Grobler on 12/09/2017.
 */

public class FilterBox implements Parcelable {

    private String ftitle = "";
    private String ftext1 = "";
    private String ftext2 = "";
    private String ftext3 = "";
    private boolean checked = false;

    public String getTitle() {
        return ftitle;
    }
    public String getText1() {
        return ftext1;
    }
    public String getText2() {
        return ftext2;
    }
    public String getText3() {
        return ftext3;
    }
    public Boolean checked() {
        return checked;
    }
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(ftitle);
        parcel.writeString(ftext1);
        parcel.writeString(ftext2);
        parcel.writeString(ftext3);
        parcel.writeByte((byte) (checked ? 1 : 0));
    }

    public static final FilterBox.Creator CREATOR = new Parcelable.Creator<FilterBox>() {
        public FilterBox createFromParcel(Parcel source) {
            return new FilterBox(source);
        }

    public FilterBox[] newArray(int size) {
            return new FilterBox[size];
        }
    };

    private FilterBox(Parcel source) {
        ftitle = source.readString();
        ftext1 = source.readString();
        ftext2 = source.readString();
        ftext3 = source.readString();
        checked = source.readByte() != 0;
    }
    public FilterBox(String ftitle, String ftext1, String ftext2, String ftext3, Boolean checked){
        this.ftitle = ftitle;
        this.ftext1 = ftext1;
        this.ftext2 = ftext2;
        this.ftext3 = ftext3;
        this.checked = checked;

    }
}

