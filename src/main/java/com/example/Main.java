package com.example;

import com.example.api.ElpriserAPI;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;


public class Main {
    public static void main(String[] args) {
        ElpriserAPI elpriserAPI = new ElpriserAPI();

        if(args[0].equals("--zone"))
            determineUsage(args, elpriserAPI);
        else
            printHelp();
    }


    private static void printHelp() {
        System.out.println("""
                Expected Command-Line Arguments:
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
                return;
            }
        }
        if(args.length < 3){
            printPricesForList(priceList.getPriser(LocalDate.now(), zone));
        } else if(args[2].equals("--charging")){
            printChargePrice(priceList.getPriser(LocalDate.now(), zone), args[3]);
        } else if(args[2].equals("--sorted")){
            printSortedPricesForList(priceList.getPriser(LocalDate.now(), zone));
        } else if(args[2].equals("--date")){
            if(args.length < 5){
                printPricesForList(priceList.getPriser(args[3], zone));
            } else if(args[4].equals("--sorted")){
                printSortedPricesForList(priceList.getPriser(args[3], zone));
            } else{
                printHelp();
            }
        } else {
            printHelp();
        }
    }

    private static void printChargePrice(List<ElpriserAPI.Elpris> prices, String args) {
        //sliding window algorithm, 2h 4h 8h
        int chargeDuration = args.charAt(0) - '0';
        List<ElpriserAPI.Elpris> chargeWindow = prices.subList(0,4);
        double lowestSum = 0;

        for (int i = 0; i < chargeDuration; i++) {
            lowestSum += prices.get(i).sekPerKWh();
        }

        double windowSum = lowestSum;
        for (int i = chargeDuration; i < prices.size(); i++) {
            windowSum += prices.get(i).sekPerKWh() - prices.get(i - chargeDuration).sekPerKWh();
            if(Math.min(lowestSum, windowSum) == windowSum){
                chargeWindow = prices.subList(i+1 - chargeWindow.size(), i+1);
            }
            lowestSum = Math.min(lowestSum, windowSum);
        }

        System.out.printf("Medelpris: %.1f öre/KWh\n", lowestSum/chargeDuration*100);
        System.out.println("Ladda mellan " + formattedStartTime(chargeWindow.getFirst()) + " och " + formattedEndTime(chargeWindow.getLast()));
    }

    private static void printSortedPricesForList(List<ElpriserAPI.Elpris> prices) {
        List<ElpriserAPI.Elpris> sortedList = prices.stream()
                .sorted(Comparator.comparing(ElpriserAPI.Elpris::sekPerKWh).reversed()
                        .thenComparing(ElpriserAPI.Elpris::timeStart))
                .toList();

        printPricesForList(sortedList);
    }

    private static void printPricesForList(List<ElpriserAPI.Elpris> pricelist) {
        for (ElpriserAPI.Elpris price : pricelist) {
            //System.out.printf(formattedStartTime(price) + "-" + formattedEndTime(price) + ": %.1f öre\n", price.sekPerKWh()*100);
            System.out.printf("Mellan " + formattedStartTime(price) + " och " + formattedEndTime(price) + ": %.1f öre/KWh\n", price.sekPerKWh()*100);
        }
    }

    private static String formattedStartTime(ElpriserAPI.Elpris source){
        String startTime;
        //startTime = String.format("%02d", source.timeStart().getHour());
        startTime = String.format("%02d", source.timeStart().getHour()) + ":" + String.format("%02d", source.timeStart().getMinute());
        return startTime;
    }

    private static String formattedEndTime(ElpriserAPI.Elpris source){
        String endTime;
        //endTime = String.format("%02d", source.timeEnd().getHour());
        endTime = String.format("%02d", source.timeEnd().getHour()) + ":" + String.format("%02d", source.timeEnd().getMinute());
        return endTime;
    }
}
