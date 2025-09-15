package my.edu.utar.taskmate.model;

public class Task {
    public String id, description, address, posterId, posterName, posterAvatar, acceptedBy, status, time, category, title, postedByUid, acceptedByUid;
    public double lat, lng, payment;
    private long createdAt;

    public Task() {
    }

    public Task(String id, String title, String description, double payment,
                String postedByUid, double lat, double lng, String status) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.payment = payment;
        this.postedByUid = postedByUid;
        this.lat = lat;
        this.lng = lng;
        this.createdAt = System.currentTimeMillis();
        this.acceptedByUid = null;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getPayment() {
        return payment;
    }

    public void setPayment(double payment) {
        this.payment = payment;
    }

    public String getPostedByUid() {
        return postedByUid;
    }

    public void setPostedByUid(String postedByUid) {
        this.postedByUid = postedByUid;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getAcceptedByUid() {
        return acceptedByUid;
    }

    public void setAcceptedByUid(String acceptedByUid) {
        this.acceptedByUid = acceptedByUid;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
