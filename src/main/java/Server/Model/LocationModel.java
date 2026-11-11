package Server.Model;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.annotation.PostConstruct;


@Repository 
public class LocationModel {

    record City(String name, int pop, double latitude, double longitude) {}

    private static final List<City> cities = new ArrayList<>();

    @PostConstruct
    void loadCities() {
        // Load Cities w/ population > 10005
        try (var in = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("/USCities1000.txt"), StandardCharsets.UTF_8))) {

            String line;
            while ((line = in.readLine()) != null) {
                String[] l = line.split("\t");
                String city = l[1];
                String state = l[10];
                int pop = Integer.parseInt(l[14]);
                double latitude = Double.parseDouble(l[4]);
                double longitude = Double.parseDouble(l[5]);
                cities.add(new City(city + ", "+ state, pop, latitude, longitude));
            }
        } catch (Exception e) {
            System.out.println("Failed to load Cities1000... " + e);
        }
        cities.sort(Comparator.comparingInt(City::pop).reversed());
    }

    public static List<String> filterCities(String q) {
        return cities.stream()
            .filter(c -> c.name().toLowerCase().contains(q.toLowerCase()))
            .limit(8)
            .map(City::name)
            .toList();
    }

    // Fall back if api fails to get a loaction
    public static String nearestCity(double latitude, double longitude) {
        City closestCity = null;
        double closestDist = Double.MAX_VALUE;
        
        for (City c : cities) {
           double changeLat = Math.toRadians(c.latitude() - latitude);
           double changeLong = Math.toRadians(c.longitude() - longitude);
           double dist = 3958.8 * 2 * Math.asin(Math.sqrt(
                Math.pow(Math.sin(changeLat / 2), 2) +
                Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(c.latitude())) *
                Math.pow(Math.sin(changeLong / 2), 2)
           ));

           if (dist < closestDist) {
                closestDist = dist;
                closestCity = c;            
           }
        }
        return closestCity == null ? "" : closestCity.name();
    }
}