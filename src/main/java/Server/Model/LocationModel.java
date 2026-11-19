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

    record City(String name, int pop) {}

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
                cities.add(new City(city + ", "+ state, pop));
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
}