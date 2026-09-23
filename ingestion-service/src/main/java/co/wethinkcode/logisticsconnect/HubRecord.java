package co.wethinkcode.logisticsconnect;

public class HubRecord {
    String hubId;
    String province;
    String sortingCenter;
    Boolean active;

    public HubRecord(String hubId, String province, String sortingCenter, Boolean active) {
        this.hubId = hubId;
        this.province = province;
        this.sortingCenter = sortingCenter;
        this.active = active;
    }

}
