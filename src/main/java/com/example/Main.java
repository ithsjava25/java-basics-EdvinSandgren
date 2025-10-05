package com.example;

import com.example.api.ElpriserAPI;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;


public class Main {
    public static void main(String[] args) {
        Locale.setDefault(Locale.of("sv","SE"));

        if(args.length != 0){
            ElpriserAPI elpriserAPI = new ElpriserAPI();
            if(args[0].equals("--zone"))
                determineUsage(args, elpriserAPI);
            else
                printHelp();
        }
        else{
             printHelp();
        }
    }


    private static void printHelp() {
        System.out.println("""
                Usage:
                --zone SE1|SE2|SE3|SE4 (required)
                --date YYYY-MM-DD (optional, defaults to current date)
                --sorted (optional, to display prices in descending order)
                --charging 2h|4h|8h (optional, to find optimal charging windows)""");
    }

    private static void determineUsage(String[] args, ElpriserAPI priceList) {
        if (args.length == 2) {
            ElpriserAPI.Prisklass zone = getZone(args);
            if (zone == null) return;
            printPrices(getMergedList(priceList, zone, LocalDate.now()));
        } else {
            switch (args.length < 5 ? args[2] : args[4]) {
                case "--charging" -> printChargePrice(args, priceList);
                case "--sorted" -> printSortedPrices(args, priceList);
                case "--date" -> printPricesForDate(args, priceList);
                default -> printHelp();
            }
        }
    }

    private static void printPricesForDate(String[] args, ElpriserAPI priceList) {
        LocalDate parsedDate = getParsedDate(args);
        ElpriserAPI.Prisklass zone = getZone(args);
        if (parsedDate == null || zone == null) return;

        printPrices(getMergedList(priceList, zone, parsedDate));
    }

    private static void printSortedPrices(String[] args, ElpriserAPI priceList) {
        LocalDate parsedDate = getParsedDate(args);
        ElpriserAPI.Prisklass zone = getZone(args);
        if (parsedDate == null || zone == null) return;

        List<hourOfQuarters> sortedList = getMergedList(priceList, zone, parsedDate);
        sortedList = sortedList.stream()
                .sorted(Comparator.comparing(hourOfQuarters::price).reversed()
                        .thenComparing(hourOfQuarters::startDate))
                .toList();

        printPrices(sortedList);
    }

    private static void printPrices(List<hourOfQuarters> priceList) {
        if(priceList.isEmpty()){
            System.out.println("Found no data");
        } else {
            double lowestPrice = Double.MAX_VALUE, highestPrice = 0, meanPrice = 0;
            System.out.println("Prislista:");
            for (hourOfQuarters price : priceList) {
                lowestPrice = Math.min(lowestPrice, price.price());
                highestPrice = Math.max(highestPrice, price.price());
                meanPrice += price.price();
                System.out.printf(formattedTime(price) + " %.2f öre\n", price.price() * 100);
            }
            System.out.printf("Lägsta pris: %.02f öre\n", lowestPrice*100);
            System.out.printf("Högsta pris: %.02f öre\n", highestPrice*100);
            System.out.printf("Medelpris: %.02f öre\n", meanPrice/priceList.size()*100);
        }
    }

    private static void printChargePrice(String[] args, ElpriserAPI priceList) {
        ElpriserAPI.Prisklass zone = getZone(args);
        if(zone == null){
            return;
        }

        //sliding window algorithm, 2h 4h 8h
        int chargeDuration = switch(args.length < 5 ? args[3] : args[5]){
            case "2h" -> 2;
            case "4h" -> 4;
            case "8h" -> 8;
            default -> -1;
        };

        if(chargeDuration == -1){
            printHelp();
            return;
        }

        LocalDate parsedDate = getParsedDate(args);
        if (parsedDate == null) return;

        List<hourOfQuarters> prices = getMergedList(priceList, zone, parsedDate);
        List<hourOfQuarters> chargeWindow;
        int indexWindow = chargeDuration;
        double lowestSum = 0;

        for (int i = 0; i < chargeDuration; i++) {
            lowestSum += prices.get(i).price();
        }

        double windowSum = lowestSum;
        for (int i = chargeDuration; i < prices.size(); i++) {
            windowSum += prices.get(i).price() - prices.get(i - chargeDuration).price();
            if(windowSum < lowestSum){
                indexWindow = i+1;
                lowestSum = windowSum;
            }
        }

        chargeWindow = prices.subList(indexWindow - chargeDuration, indexWindow);

        System.out.printf("Medelpris för fönster: %.2f öre\n", lowestSum/chargeDuration*100);
        System.out.printf("Påbörja laddning kl %02d:00\n", chargeWindow.getFirst().startDate.getHour());
    }

    private static List<hourOfQuarters> convertQuartersToHours(List<ElpriserAPI.Elpris> prices) {
        List<hourOfQuarters> hourList = new ArrayList<>();
        if (prices.isEmpty()){
            return hourList;
        }
        if(prices.getFirst().timeEnd().getMinute() != 0 || prices.get(1).timeEnd().getMinute() != 0){
            int index = -1;
            for (ElpriserAPI.Elpris entry : prices) {
                if (entry.timeStart().getMinute() == 0) {
                    hourList.add(new hourOfQuarters(entry.timeStart(), entry.timeEnd(), entry.sekPerKWh()/4));
                    index++;
                } else {
                    hourList.add(new hourOfQuarters(hourList.get(index).startDate, entry.timeEnd(), entry.sekPerKWh()/4 + hourList.get(index).price));
                    hourList.remove(index);
                }
            }
        } else {
            for(ElpriserAPI.Elpris entry : prices){
                hourList.add(new hourOfQuarters(entry.timeStart(), entry.timeEnd(), entry.sekPerKWh()));
            }
        }
        return hourList;
    }

    private static ElpriserAPI.Prisklass getZone(String[] args) {
        ElpriserAPI.Prisklass zone = null;
        try {
            switch (args[1]){
                case "SE1" -> zone = ElpriserAPI.Prisklass.SE1;
                case "SE2" -> zone = ElpriserAPI.Prisklass.SE2;
                case "SE3" -> zone = ElpriserAPI.Prisklass.SE3;
                case "SE4" -> zone = ElpriserAPI.Prisklass.SE4;
                default -> {
                    printHelp();
                    System.out.println("Invalid zone input");
                }
            }
        } catch (IndexOutOfBoundsException e) {
            printHelp();
        }
        return zone;
    }

    private static LocalDate getParsedDate(String[] args) {
        LocalDate parsedDate;
        if(args.length < 4){
            parsedDate = LocalDate.now();
        }else{
            try {
                parsedDate = LocalDate.parse(args[3]);
            } catch (DateTimeParseException | IndexOutOfBoundsException e) {
                printHelp();
                System.out.println("Invalid date");
                return null;
            }
        }
        return parsedDate;
    }

    private static List<hourOfQuarters> getMergedList(ElpriserAPI priceList, ElpriserAPI.Prisklass zone, LocalDate date) {
        List<ElpriserAPI.Elpris> mergedList = priceList.getPriser(date, zone);
        mergedList.addAll(priceList.getPriser(date.plusDays(1), zone));
        return convertQuartersToHours(mergedList);
    }

    private static String formattedTime(hourOfQuarters source){
        String formattedTime;
        formattedTime = String.format("%02d-%02d", source.startDate.getHour(), source.endDate.getHour());
        return formattedTime;
    }

    record hourOfQuarters(ZonedDateTime startDate, ZonedDateTime endDate, double price){}
}