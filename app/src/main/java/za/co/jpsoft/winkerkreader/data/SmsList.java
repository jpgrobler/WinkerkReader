package za.co.jpsoft.winkerkreader.data;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Pieter Grobler on 15/09/2017.
 */

public class SmsList implements Parcelable {
    private String naam;
    private String van;
    private String nommer;
    private int _id;
    private String guid;

    public SmsList(String naam, String van, String nommer, int id, String guid) {
        this.naam = naam;
        this.van = van;
        this.nommer = nommer;
        this._id = id;
        this.guid = guid;
    }
        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            parcel.writeString(naam);
            parcel.writeString(van);
            parcel.writeString(nommer);
            parcel.writeInt(_id);
            parcel.writeString(guid);
        }

        public static final SmsList.Creator CREATOR = new Parcelable.Creator<SmsList>() {
            public SmsList createFromParcel(Parcel source) {
                return new SmsList(source);
            }
            public SmsList[] newArray(int size) {
                return new SmsList[size];
            }
        };

    private SmsList(Parcel source) {
        naam = source.readString();
        van = source.readString();
        nommer = source.readString();
        _id = source.readInt();
        guid = source.readString();
        }

    public String getNaam() {
        return naam;
    }

    public String getVan() {
        return van;
    }

    public String getNommer() {
        return nommer;
    }

    public int getId() {
        return _id;
    }

    public String getGUID() {
        return guid;
    }

    public void setNaam(String naam) {
        this.naam = naam;
    }

    public void setNommer(String nommer) {
        this.nommer = nommer;
    }

    public void setVan(String van) {
        this.van = van;
    }

    public void setId(int id) {this._id = id; }

    public void setGuid(String guid) {
        this.guid = guid;
    }
}
