package dev.tonysp.rankedpvp.data;

public class MatchInfo {

    public String name;
    public double rating, deviation;
    public int count = 0;
    public double quality = 0;

    public double getAverageQuality () {
        return quality / count;
    }

    public MatchInfo (String name, double rating, double deviation) {
        this.name = name;
        this.rating = rating;
        this.deviation = deviation;
    }
}
