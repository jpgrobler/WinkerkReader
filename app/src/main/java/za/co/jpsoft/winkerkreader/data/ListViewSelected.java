package za.co.jpsoft.winkerkreader.data;

/**
 * Created by Pieter Grobler on 01/09/2017.
 */

class ListViewSelected {
    private int _id;
    private Boolean smsto;

    public ListViewSelected(int _id, Boolean smsto) {
        this._id = _id;
        this.smsto = smsto;
    }

    public void set_id (int _id) {
        this._id = _id;
    }

    public void set_smsto (Boolean smsto) {
        this.smsto = smsto;
    }

    public Boolean get_smsto() {
        return this.smsto;
    }
}
