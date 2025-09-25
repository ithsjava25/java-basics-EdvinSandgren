package com.example;

import com.example.api.ElpriserAPI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;


public class Main {
    public static void main(String[] args) {
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
        ElpriserAPI.Prisklass zone;
        switch (args[1]){
            case "SE1" -> zone = ElpriserAPI.Prisklass.SE1;
            case "SE2" -> zone = ElpriserAPI.Prisklass.SE2;
            case "SE3" -> zone = ElpriserAPI.Prisklass.SE3;
            case "SE4" -> zone = ElpriserAPI.Prisklass.SE4;
            default -> {
                printHelp();
                System.out.println("Invalid zone input");
                return;
            }
        }
        if(args.length < 3){
            printPricesForList(getMergedList(priceList, zone, LocalDate.now()));
        } else if(args[2].equals("--charging")){
            printChargePrice(getMergedList(priceList, zone, LocalDate.now()), args[3]);
        } else if(args[2].equals("--sorted")){
            printSortedPricesForList(getMergedList(priceList, zone, LocalDate.now()));
        } else if(args[2].equals("--date")){
            LocalDate parsedDate;
            try{
                parsedDate = LocalDate.parse(args[3]);
            } catch (DateTimeParseException | IndexOutOfBoundsException e) {
                printHelp();
                System.out.println("Invalid date");
                return;
            }
            if(args.length < 5){
                printPricesForList(getMergedList(priceList, zone, parsedDate));
            } else if(args[4].equals("--sorted")){
                printSortedPricesForList(getMergedList(priceList, zone, parsedDate));
            } else if(args[4].equals("--charging")){
                printChargePrice(getMergedList(priceList, zone, parsedDate), args[5]);
            } else{
                printHelp();
            }
        } else {
            printHelp();
        }
    }

    private static List<ElpriserAPI.Elpris> getMergedList(ElpriserAPI priceList, ElpriserAPI.Prisklass zone, LocalDate date) {
        List<ElpriserAPI.Elpris> mergedList = priceList.getPriser(date, zone);
        mergedList.addAll(priceList.getPriser(date.plusDays(1), zone));
        return mergedList;
    }

    private static void printChargePrice(List<ElpriserAPI.Elpris> prices, String args) {
        //sliding window algorithm, 2h 4h 8h
        int chargeDuration = switch(args){
            case "2h" -> 2;
            case "4h" -> 4;
            case "8h" -> 8;
            default -> -1;
        };

        if(chargeDuration == -1){
            printHelp();
            return;
        }

        List<ElpriserAPI.Elpris> chargeWindow = prices.subList(0,chargeDuration);
        double lowestSum = 0;

        for (int i = 0; i < chargeDuration; i++) {
            lowestSum += prices.get(i).sekPerKWh();
        }

        double windowSum = lowestSum;
        for (int i = chargeDuration; i < prices.size(); i++) {
            windowSum += prices.get(i).sekPerKWh() - prices.get(i - chargeDuration).sekPerKWh();
            if(windowSum < lowestSum){
                chargeWindow = prices.subList(i+1 - chargeWindow.size(), i+1);
                lowestSum = windowSum;
            }
        }

        System.out.printf("Medelpris för fönster: %.2f öre\n", lowestSum/chargeDuration*100);
        System.out.printf("Påbörja laddning kl %02d:00\n", chargeWindow.getFirst().timeStart().getHour());
    }

    private static void printSortedPricesForList(List<ElpriserAPI.Elpris> prices) {
        List<ElpriserAPI.Elpris> sortedList = prices.stream()
                .sorted(Comparator.comparing(ElpriserAPI.Elpris::sekPerKWh).reversed()
                        .thenComparing(ElpriserAPI.Elpris::timeStart))
                .toList();

        printPricesForList(sortedList);
    }

    private static void printPricesForList(List<ElpriserAPI.Elpris> pricelist) {
        if(pricelist.isEmpty()){
            System.out.println("Found no data");
        } else {
            double lowestPrice = Double.MAX_VALUE, highestPrice = 0, meanPrice = 0;
            System.out.println("Prislista:");
            for (ElpriserAPI.Elpris price : pricelist) {
                lowestPrice = Math.min(lowestPrice, price.sekPerKWh());
                highestPrice = Math.max(highestPrice, price.sekPerKWh());
                meanPrice += price.sekPerKWh();
                System.out.printf(formattedTime(price) + " %.2f öre\n", price.sekPerKWh() * 100);
                //System.out.printf("Mellan " + formattedStartTime(price) + " och " + formattedEndTime(price) + ": %.1f öre/KWh\n", price.sekPerKWh()*100);
            }
            System.out.printf("Lägsta pris: %.02f öre\n", lowestPrice*100);
            System.out.printf("Högsta pris: %.02f öre\n", highestPrice*100);
            System.out.printf("Medelpris: %.02f öre\n", meanPrice/pricelist.size()*100);
        }
    }

    private static String formattedTime(ElpriserAPI.Elpris source){
        String formattedTime;
        formattedTime = String.format("%02d-%02d", source.timeStart().getHour(), source.timeEnd().getHour());
        //startTime = String.format("%02d", source.timeStart().getHour()) + ":" + String.format("%02d", source.timeStart().getMinute());
        return formattedTime;
    }
}
