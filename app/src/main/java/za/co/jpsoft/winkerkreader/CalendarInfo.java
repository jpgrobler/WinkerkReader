package za.co.jpsoft.winkerkreader;

public class CalendarInfo {
    private long id;
    private String name;
    private String displayName;
    private String accountName;

    public CalendarInfo(long id, String name, String displayName, String accountName) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.accountName = accountName;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    @Override
    public String toString() {
        return "CalendarInfo{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", displayName='" + displayName + '\'' +
                ", accountName='" + accountName + '\'' +
                '}';
    }
}