package my.edu.utar.taskmate;

public class RatingModel {
    private float stars;
    private String review;
    private String fromUser;
    private long timestamp; // store as milliseconds since epoch

    // Empty constructor required for Firebase
    public RatingModel() {
    }

    public RatingModel(float stars, String review, String fromUser, long timestamp) {
        this.stars = stars;
        this.review = review;
        this.fromUser = fromUser;
        this.timestamp = timestamp;
    }

    public float getStars() {
        return stars;
    }

    public void setStars(float stars) {
        this.stars = stars;
    }

    public String getReview() {
        return review;
    }

    public void setReview(String review) {
        this.review = review;
    }

    public String getFromUser() {
        return fromUser;
    }

    public void setFromUser(String fromUser) {
        this.fromUser = fromUser;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
