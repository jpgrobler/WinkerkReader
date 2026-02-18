package za.co.jpsoft.winkerkreader.data;

import java.io.Serializable;
import java.util.List;

/**
 * Created by Pieter Grobler on 06/09/2017.
 */

public class FilterCheckBox implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String DEFAULT_DESCRIPTION = "N/A";

    private String ftitle = "";
    private List <String> flist1 = null;
    private String ftext1 = "";
    private String ftext2 = "";
    private String ftext3 = "";
    private int fcount = 1;
    private boolean checked = false;

// get functions
    public String getTitle () {
        return ftitle == null ? DEFAULT_DESCRIPTION  : ftitle;
    }
    public String getText1() {
        return ftext1 == null ? DEFAULT_DESCRIPTION  : ftext1;
    }
    public String getText2 () {
        return ftext2 == null ? DEFAULT_DESCRIPTION  : ftext2;
    }
    public String getText3 () {
        return ftext3 == null ? DEFAULT_DESCRIPTION : ftext3;
    }
    public int getCount () {
        return fcount;
    }
    public List getList() {
        return flist1 == null ? null  : flist1;
    }
    public boolean getChecked () {
        return checked;
    }

//Set functions
    public void setTitle(String detail) { this.ftitle = detail; }
    public void setText1(String detail) { this.ftext1 = detail; }
    public void setText2(String detail) { this.ftext2 = detail; }
    public void setText3(String detail) { this.ftext3 = detail; }
    public void setCount(int detail) { this.fcount = detail; }
    public void setList(List<String> list) { this.flist1 = list; }
    public void setChecked ( final boolean checked ) {
        this.checked = checked;
    }



    public FilterCheckBox (final String title, final List <String> list1, final String text1, final String text2, final String text3, final int count) {
        this.ftitle = title;
        this.flist1 = list1;
        this.ftext1 = text1;
        this.ftext2 = text2;
        this.ftext3 = text3;
        this.fcount = count;

    }

}
