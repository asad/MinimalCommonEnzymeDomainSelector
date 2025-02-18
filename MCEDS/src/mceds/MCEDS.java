/*
 The MIT License (MIT)

 Copyright (c) 2015 Syed Asad Rahman <s9asad@gmail.com>

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 */
package mceds;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 *
 * @author Asad
 */
public class MCEDS {

    private static boolean WITHIN_EC_CLASS = false;
    private static boolean DEBUG = false;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        List<String> commandAsList = Arrays.asList(args);
        String fileName = null;
        if (commandAsList.contains("-f")) {
            int index = commandAsList.indexOf("-f") + 1;
            fileName = commandAsList.get(index);
        } else if (commandAsList.contains("-cache")) {
            fileName = "INTERPRO";
        }

        if (commandAsList.contains("-s")) {
            WITHIN_EC_CLASS = true;
        }

        if (commandAsList.contains("-debug")) {
            DEBUG = true;
        }

        if (fileName != null) {
            MCEDS mceds = new MCEDS(fileName);
        } else {
            System.err.println("java -jar MCEDS.jar -f data/ec5_pdb_DOMAINSids.txt -s");
            System.err.println("Note:\t-s for restricting the search within EC classes");
            System.err.println("\tdefault: test against all the ECs present in the input file.");
        }
    }

    private MCEDS(String fileName) {
        final Map<String, TreeMap<Integer, Set<String>>> catalyticSites = new TreeMap<>();

        ECPDBDomainIntegrator ecpdbDomainIntegrator = new ECPDBDomainIntegrator();
        if (fileName != null && !fileName.equals("INTERPRO")) {
            ecpdbDomainIntegrator.parseFile(fileName);
        } else {
            ecpdbDomainIntegrator.PDB2ECAndInterPro();
        }

        Map<String, TreeMap<String, Set<String>>> rawMap = ecpdbDomainIntegrator.getMap();

        /*
         Calculating minimum domain visitedCombinations in EC
         */
        final Set<String> allDomains = conservedDomains(rawMap, catalyticSites);
        final Map<String, TreeMap<String, Set<String>>> refinedMCEDSMap = new TreeMap<>();

        Set<String> uniqueDomians = refineDomains(rawMap, catalyticSites, refinedMCEDSMap);
        catalyticSites.clear();

        if (DEBUG) {
            System.err.println("\t!------------------------------------------------!");
            System.err.println("\tTotal Input Domains Found: " + allDomains.size());
            System.err.println("\tTotal Unique Domains Found: " + uniqueDomians.size());
            System.err.println("\tTotal Confusion Domains Found: " + (allDomains.size() - uniqueDomians.size()));
            System.err.println("\t!------------------------------------------------!");

        }

        if (!DEBUG) {
            System.out.println("!------------------------------------------------!");
            System.out.println("\t\"EC\"" + "\t\"PDB\"" + "\t\"DOMAINS\"" + "\t\"MCEDS\"");
            System.out.println("!------------------------------------------------!");

            refinedMCEDSMap.keySet().stream().forEach((String ec) -> {
                TreeMap<String, Set<String>> map = refinedMCEDSMap.get(ec);
                map.keySet().stream().forEach((String pdbCode) -> {
                    Set<String> commonDomains = map.get(pdbCode);
                    Set<String> allDomainsForPDB = rawMap.get(ec).get(pdbCode);
                    System.out.println("\t" + ec + "\t" + pdbCode + "\t" + allDomainsForPDB + "\t" + commonDomains);
                });
            });
        }

    }

    private Set<String> conservedDomains(final Map<String, TreeMap<String, Set<String>>> rawMap, final Map<String, TreeMap<Integer, Set<String>>> potentialNonRedundentDomains) {
        final Set<String> allDomains = new TreeSet<>();

        /*
         Calculating minimum domain visitedCombinations in EC
         */
        if (DEBUG) {
            System.out.println("\t\"Combinations\"" + "\t\"EC\"" + "\t\"Combinations\"");
        }
        for (String ec : rawMap.keySet()) {
            TreeMap<String, Set<String>> inputDomainCombinations = rawMap.get(ec);
            Map<Integer, Set<String>> seedDomainCombinations = new TreeMap<>();

            int counter = 1;
            for (String pdb : inputDomainCombinations.keySet()) {
                Set<String> common = new TreeSet<>();
                Set<String> domains = inputDomainCombinations.get(pdb);
                allDomains.addAll(domains);
                boolean flag = false;

                /*
                 Select Seed Domains
                 */
                for (Set<String> v : seedDomainCombinations.values()) {
                    Set<String> t = new TreeSet<>(v);
                    t.retainAll(domains);
                    if (!t.isEmpty() && t.size() == v.size()) {
                        flag = true;
                        break;
                    }
                }

                /*
                 Selected Successful Seed Domains
                 */
                if (!flag) {
                    common.addAll(domains);
                    if (WITHIN_EC_CLASS) {

                        inputDomainCombinations.keySet().stream().map((pdbcommon) -> inputDomainCombinations.get(pdbcommon)).map((domainsComb) -> new TreeSet<>(domainsComb)).map((c) -> {
                            c.retainAll(common);
                            return c;
                        }).filter((c) -> (!c.isEmpty())).forEach((c) -> {
                            common.clear();
                            common.addAll(c);
                        });

                        seedDomainCombinations.put(counter, common);
                        counter++;

                    } else {
                        for (String enzymeID : rawMap.keySet()) {
                            TreeMap<String, Set<String>> pdbDomainsCombinationsMap = rawMap.get(enzymeID);
                            pdbDomainsCombinationsMap.keySet().stream().map((pdbcommon) -> pdbDomainsCombinationsMap.get(pdbcommon)).map((domainsComb) -> new TreeSet<>(domainsComb)).map((c) -> {
                                c.retainAll(common);
                                return c;
                            }).filter((c) -> (!c.isEmpty())).forEach((c) -> {
                                common.clear();
                                common.addAll(c);
                            });
                        }
                        seedDomainCombinations.put(counter, common);
                        counter++;
                    }
                }

            }

            if (!potentialNonRedundentDomains.containsKey(ec)) {
                potentialNonRedundentDomains.put(ec, new TreeMap<>());
            }
            /*
             Print Combinations
             */
            if (DEBUG) {
                seedDomainCombinations.entrySet().stream().forEach((m) -> {
                    System.out.println("\t" + m.getKey() + "\t" + ec + "\t" + m.getValue());
                });
            }
            potentialNonRedundentDomains.get(ec).putAll(seedDomainCombinations);
        }
        return allDomains;
    }

    private Set<String> refineDomains(final Map<String, TreeMap<String, Set<String>>> rawMap,
            final Map<String, TreeMap<Integer, Set<String>>> catalyticSites,
            Map<String, TreeMap<String, Set<String>>> refinedMCEDSMap) {
        /*
         Update and fill map of common domains
         */
        Set<String> uniqueDomians = new TreeSet<>();
        rawMap.keySet().stream().map((ec) -> {
            if (!refinedMCEDSMap.containsKey(ec)) {
                refinedMCEDSMap.put(ec, new TreeMap<>());
            }
            return ec;
        }).forEach((ec) -> {
            TreeMap<String, Set<String>> pdb2DomiansMap = rawMap.get(ec);
            pdb2DomiansMap.keySet().stream().forEach((pdbCode) -> {
                Set<String> rawDomains = pdb2DomiansMap.get(pdbCode);
                TreeMap<Integer, Set<String>> cataticDomainMaps = catalyticSites.get(ec);
                cataticDomainMaps.values().stream().forEach((domains) -> {
                    TreeSet<String> commonDomains = new TreeSet<>(domains);
                    commonDomains.retainAll(rawDomains);
                    /*
                     Find the common min domains presence
                     */
                    if (!commonDomains.isEmpty() && commonDomains.size() == domains.size()) {
                        //System.out.println("\t" + ec + "\t" + pdbCode + "\t" + commonDomains);
                        if (!refinedMCEDSMap.get(ec).containsKey(pdbCode)) {
                            refinedMCEDSMap.get(ec).put(pdbCode, new TreeSet<>());
                        }
                        refinedMCEDSMap.get(ec).get(pdbCode).addAll(commonDomains);
                        uniqueDomians.addAll(commonDomains);
                    }
                });
            });
        });
        return uniqueDomians;
    }

}
