package za.co.jpsoft.winkerkreader;

import java.io.Serializable;

/**
 * Data class for search checkbox state
 */
public class SearchCheckBox implements Serializable {
    private String columnName;
    private String searchValue;
    private String description;
    private boolean isChecked;

    public SearchCheckBox(String columnName, String searchValue) {
        this.columnName = columnName;
        this.searchValue = searchValue;
        this.description = columnName; // Default description to column name
        this.isChecked = false;
    }

    public SearchCheckBox(String columnName, String searchValue, boolean isChecked) {
        this.columnName = columnName;
        this.searchValue = searchValue;
        this.description = columnName; // Default description to column name
        this.isChecked = isChecked;
    }

    public SearchCheckBox(String columnName, String searchValue, String description, boolean isChecked) {
        this.columnName = columnName;
        this.searchValue = searchValue;
        this.description = description;
        this.isChecked = isChecked;
    }

    // Getters
    public String getColumnName() {
        return columnName;
    }

    public String getSearchValue() {
        return searchValue;
    }

    public String getDescription() {
        return description;
    }

    public boolean isChecked() {
        return isChecked;
    }

    // Setters
    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public void setSearchValue(String searchValue) {
        this.searchValue = searchValue;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setChecked(boolean checked) {
        this.isChecked = checked;
    }

    @Override
    public String toString() {
        return "SearchCheckBox{" +
                "columnName='" + columnName + '\'' +
                ", searchValue='" + searchValue + '\'' +
                ", description='" + description + '\'' +
                ", isChecked=" + isChecked +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        SearchCheckBox that = (SearchCheckBox) obj;
        return isChecked == that.isChecked &&
                (columnName != null ? columnName.equals(that.columnName) : that.columnName == null) &&
                (searchValue != null ? searchValue.equals(that.searchValue) : that.searchValue == null) &&
                (description != null ? description.equals(that.description) : that.description == null);
    }

    @Override
    public int hashCode() {
        int result = columnName != null ? columnName.hashCode() : 0;
        result = 31 * result + (searchValue != null ? searchValue.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (isChecked ? 1 : 0);
        return result;
    }
}