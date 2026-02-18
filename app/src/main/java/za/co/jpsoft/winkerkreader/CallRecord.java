package za.co.jpsoft.winkerkreader;


import android.content.Context;

/* loaded from: classes.dex */
public class CallRecord {
    public int duur;
    public long endTime;
    public String beskrywing;
    public String titel;
    public String naam;
    public String nommer;
    public int nommerType;
    public long startTime;
    public int type;

    public CallRecord(String num, String naam, int type, int duur, long date, int nommerType, Context context) {
        this.nommer = num;
        this.type = type;
        this.naam = naam;
        this.duur = duur;
        this.nommerType = nommerType;
        if (type == 3) {
            this.titel = context.getString(R.string.missed_call_pref);
        } else if (type == 1) {
            this.titel = context.getString(R.string.incoming_call_pref);
        } else {
            this.titel = context.getString(R.string.outgoing_call_pref);
        }
        String[] nommerTypes = {"Work", "Home", "Mobile", "Other", "Other", "Other"};
        if (naam != null && !naam.equals("")) {
            this.titel = String.valueOf(this.titel) + " " + naam;
            if (nommerType >= 0 && nommerType < nommerTypes.length) {
                this.beskrywing = String.valueOf(this.titel) + " " + nommerTypes[nommerType];
            } else {
                this.beskrywing = this.titel;
            }
        } else {
            if (this.nommer != null && this.nommer.length() < 3) {
                this.titel = String.valueOf(this.titel) + " Unknown nommer";
            } else if (this.nommer != null) {
                this.titel = String.valueOf(this.titel) + " " + this.nommer;
            } else {
                this.titel = String.valueOf(this.titel) + " Unknown nommer";
            }
            this.beskrywing = this.titel;
        }
        this.beskrywing = this.beskrywing.concat("\nnommer: ").concat(num).concat("\n").concat("duur: ").concat(String.valueOf(duur)).concat(" seconds");
        this.startTime = date;
        this.endTime = (duur * 1000) + date;
    }
}

